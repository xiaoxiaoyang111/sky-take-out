package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.model.RentalAssistantModels.Turn;
import com.sky.rental.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="SKY_REDIS_INTEGRATION",matches="true")
class RentalAssistantRedisIntegrationTest {
    @Autowired RentalAssistantStore store;
    @Autowired RentalCatalogCache cache;
    @Autowired StringRedisTemplate redis;
    Long uid;String session,cacheQuery;
    @BeforeEach void init(){uid=80000000000L+Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L);session=UUID.randomUUID().toString();cacheQuery="fixture-"+session;}
    @AfterEach void clean(){Set<String> keys=redis.keys(RentalAssistantStore.prefix(uid)+"*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);Set<String> cacheKeys=redis.keys("rental:catalog:{public}:*:"+DigestUtils.md5DigestAsHex(cacheQuery.getBytes(StandardCharsets.UTF_8)));if(cacheKeys!=null&&!cacheKeys.isEmpty())redis.delete(cacheKeys);}
    Turn turn(String answer){Turn t=new Turn();t.setQuestion("question");t.setAnswer(answer);t.setMode("RULES");return t;}
    @Test void historyBoundedExpiringAndIsolatedByIdentity(){for(int i=0;i<12;i++)store.append(uid,session,turn("answer"+i));assertEquals(10,store.history(uid,session).size());assertEquals("answer2",store.history(uid,session).get(0).getAnswer());assertTrue(store.history(uid+1,session).isEmpty());Long ttl=redis.getExpire(RentalAssistantStore.historyKey(uid,session));assertTrue(ttl>0&&ttl<=1800);store.clear(uid,session);assertTrue(store.history(uid,session).isEmpty());}
    @Test void seventhConsultationRejectedAndQuotaHasExpiry(){for(int i=0;i<6;i++){String lease=store.acquire(uid);store.release(uid,lease);}assertThrows(BaseException.class,()->store.acquire(uid));assertEquals("6",redis.opsForValue().get(RentalAssistantStore.prefix(uid)+"minute"));assertTrue(redis.getExpire(RentalAssistantStore.prefix(uid)+"minute")>0);assertFalse(Boolean.TRUE.equals(redis.hasKey(RentalAssistantStore.prefix(uid)+"busy")));}
    @Test void leaseCannotBeReleasedByAnotherRequest(){String lease=store.acquire(uid);assertThrows(BaseException.class,()->store.acquire(uid));store.release(uid,"wrong-token");assertThrows(BaseException.class,()->store.acquire(uid));store.release(uid,lease);String next=store.acquire(uid);store.release(uid,next);}
    @Test void dailyQuotaCannotBeBypassedByNewSession(){redis.opsForValue().set(RentalAssistantStore.prefix(uid)+"day","100",Duration.ofDays(1));assertThrows(BaseException.class,()->store.acquire(uid));}
    @Test void publicCacheActuallyHitsRedisAndHasBoundedTtl(){AtomicInteger reads=new AtomicInteger();String first=cache.read(cacheQuery,String.class,()->"database-"+reads.incrementAndGet());String second=cache.read(cacheQuery,String.class,()->"database-"+reads.incrementAndGet());assertEquals(first,second);assertEquals(1,reads.get());Set<String> keys=redis.keys("rental:catalog:{public}:*:"+DigestUtils.md5DigestAsHex(cacheQuery.getBytes(StandardCharsets.UTF_8)));assertEquals(1,keys.size());long ttl=redis.getExpire(keys.iterator().next());assertTrue(ttl>0&&ttl<=60);}
    @Test void concurrentInvalidationCannotRepopulateNewGenerationWithOldSnapshot(){String old=cache.read(cacheQuery,String.class,()->{cache.invalidateAfterCommit();return "old snapshot";});assertEquals("old snapshot",old);assertEquals("fresh",cache.read(cacheQuery,String.class,()->"fresh"));assertEquals("fresh",cache.read(cacheQuery,String.class,()->"must not load"));}
}
