package com.sky.rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.util.DigestUtils;

/** Short-lived public directory cache, never used for stock allocation or pricing decisions. */
@Service
public class RentalCatalogCache {
    private static final String EPOCH="rental:catalog:{public}:epoch";
    private static final DefaultRedisScript<Long> PUT=new DefaultRedisScript<>(
        "if (redis.call('GET',KEYS[1]) or '0')==ARGV[1] then redis.call('SET',KEYS[2],ARGV[2],'EX',60); return 1 end; return 0",Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json=new com.sky.json.JacksonObjectMapper();
    public RentalCatalogCache(StringRedisTemplate redis) { this.redis=redis; }
    public <T> T read(String query,Class<T> type,Supplier<T> database) {
        String epoch,key;
        try {
            epoch=redis.opsForValue().get(EPOCH);if(epoch==null)epoch="0";
            key="rental:catalog:{public}:"+epoch+":"+DigestUtils.md5DigestAsHex(query.getBytes(StandardCharsets.UTF_8));
            String value=redis.opsForValue().get(key);
            if(value!=null) return json.readValue(value,type);
        } catch(Exception ignored) { return database.get(); }
        T value=database.get();
        try { redis.execute(PUT,Arrays.asList(EPOCH,key),epoch,json.writeValueAsString(value)); }
        catch(Exception ignored) { /* database remains usable when cache storage fails */ }
        return value;
    }
    public void invalidateAfterCommit() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(); }
            });
        } else invalidate();
    }
    private void invalidate() {
        try { redis.opsForValue().set(EPOCH,UUID.randomUUID().toString()); }
        catch(RuntimeException ignored) { /* existing entries expire within 60 seconds */ }
    }
}
