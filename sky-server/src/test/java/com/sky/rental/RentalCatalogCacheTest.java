package com.sky.rental;

import com.sky.rental.service.RentalCatalogCache;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalCatalogCacheTest {
    @Test void redisFailureFallsBackToDatabase(){StringRedisTemplate redis=mock(StringRedisTemplate.class);when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));assertEquals("database",new RentalCatalogCache(redis).read("query",String.class,()->"database"));}
    @Test void invalidationWaitsUntilCommit(){StringRedisTemplate redis=mock(StringRedisTemplate.class);ValueOperations<String,String> values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);TransactionSynchronizationManager.initSynchronization();try{new RentalCatalogCache(redis).invalidateAfterCommit();verifyNoInteractions(values);TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());verify(values).set(eq("rental:catalog:{public}:epoch"),anyString());}finally{TransactionSynchronizationManager.clearSynchronization();}}
    @Test void rollbackLeavesPublicGenerationUnchanged(){StringRedisTemplate redis=mock(StringRedisTemplate.class);TransactionSynchronizationManager.initSynchronization();try{new RentalCatalogCache(redis).invalidateAfterCommit();TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(1));verifyNoInteractions(redis);}finally{TransactionSynchronizationManager.clearSynchronization();}}
}
