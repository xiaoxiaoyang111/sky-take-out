package com.sky.rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.exception.BaseException;
import com.sky.rental.model.RentalAssistantModels.Turn;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;

/** All keys are scoped by authenticated customer; scripts keep quotas and history atomic. */
@Service
public class RentalAssistantStore {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
        "local m=tonumber(redis.call('GET',KEYS[1]) or '0'); local d=tonumber(redis.call('GET',KEYS[2]) or '0'); " +
        "if m>=6 or d>=100 then return 0 end; " +
        "if redis.call('INCR',KEYS[1])==1 then redis.call('EXPIRE',KEYS[1],60) end; " +
        "if redis.call('INCR',KEYS[2])==1 then redis.call('EXPIRE',KEYS[2],86400) end; return 1", Long.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
        "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end; return 0",Long.class);
    private static final DefaultRedisScript<Long> APPEND = new DefaultRedisScript<>(
        "redis.call('RPUSH',KEYS[1],ARGV[1]); redis.call('LTRIM',KEYS[1],-10,-1); redis.call('EXPIRE',KEYS[1],1800); return 1",Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    public RentalAssistantStore(StringRedisTemplate redis) { this.redis=redis; }
    public String acquire(Long user) {
        String token=UUID.randomUUID().toString();
        try {
            if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey(user),token,Duration.ofSeconds(60)))) throw new BaseException("上一条咨询仍在处理中，请稍后再试");
            if(!Long.valueOf(1).equals(redis.execute(LIMIT,Arrays.asList(prefix(user)+"minute",prefix(user)+"day")))) {
                release(user,token); throw new BaseException("咨询次数已达限制，请稍后再试（每分钟6次，每24小时100次）");
            }
            return token;
        } catch(BaseException ex) { throw ex; }
        catch(RuntimeException ex) { release(user,token); throw unavailable(); }
    }
    public void release(Long user,String token) {
        try { redis.execute(UNLOCK,Collections.singletonList(lockKey(user)),token); }
        catch(RuntimeException ignored) { /* bounded lease expires even when Redis is unavailable */ }
    }
    public List<Turn> history(Long user,String session) {
        try {
            List<String> rows=redis.opsForList().range(historyKey(user,session),0,-1);
            List<Turn> turns=new ArrayList<>();
            if(rows!=null) for(String row:rows) turns.add(json.readValue(row,Turn.class));
            return turns;
        } catch(Exception ex) { throw unavailable(); }
    }
    public void append(Long user,String session,Turn turn) {
        try { redis.execute(APPEND,Collections.singletonList(historyKey(user,session)),json.writeValueAsString(turn)); }
        catch(Exception ex) { throw unavailable(); }
    }
    public void clear(Long user,String session) {
        try { redis.delete(historyKey(user,session)); } catch(RuntimeException ex) { throw unavailable(); }
    }
    public static String historyKey(Long user,String session) { return prefix(user)+"history:"+session; }
    public static String prefix(Long user) { return "rental:assistant:{"+user+"}:"; }
    private static String lockKey(Long user) { return prefix(user)+"busy"; }
    private static BaseException unavailable() { return new BaseException("租赁助手暂不可用，请稍后再试；器材预约仍可正常使用"); }
}
