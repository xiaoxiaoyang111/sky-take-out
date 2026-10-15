package com.sky.service.impl;

import com.sky.exception.BaseException;
import com.sky.service.ShopService;
import com.sky.constant.JwtClaimsConstant;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "logging.level.com.sky.interceptor=ERROR")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SKY_REDIS_INTEGRATION", matches = "true")
class ShopRedisIntegrationTest {
    @Autowired private ShopService service;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwtProperties;

    @Test
    void realRedisStoresPlainStringsAndSharesLatestStatus() throws Exception {
        String key = "SHOP_STATUS";
        String previous = redis.opsForValue().get(key);
        Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        long started = System.currentTimeMillis();
        try {
            String token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), 60000,
                    new HashMap<>(Collections.singletonMap(JwtClaimsConstant.EMP_ID, 1L)));
            mvc.perform(get("/admin/shop/status")).andExpect(status().isUnauthorized());
            mvc.perform(put("/admin/shop/1")).andExpect(status().isUnauthorized());
            redis.delete(key);
            assertEquals(0, service.getStatus());
            mvc.perform(get("/user/shop/status")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(0));
            mvc.perform(put("/admin/shop/1").header("token", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
            assertEquals("1", redis.opsForValue().get(key));
            assertEquals(1, service.getStatus());
            for (String path : new String[]{"/admin/shop/status", "/user/shop/status"}) {
                mvc.perform(get(path).header("token", token)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data").value(1));
            }
            mvc.perform(put("/admin/shop/0").header("token", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
            assertEquals("0", redis.opsForValue().get(key));
            assertEquals(0, service.getStatus());
            mvc.perform(get("/user/shop/status")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(0));
            mvc.perform(put("/admin/shop/2").header("token", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            assertThrows(BaseException.class, () -> service.setStatus(2));
            assertEquals("0", redis.opsForValue().get(key));
            redis.opsForValue().set(key, "corrupted");
            assertThrows(BaseException.class, service::getStatus);
        } finally {
            // Restore only this key and preserve its original expiry where applicable.
            if (previous == null || ttl == null || ttl == -2) {
                redis.delete(key);
            } else if (ttl == -1) {
                redis.opsForValue().set(key, previous);
            } else {
                long remaining = ttl - (System.currentTimeMillis() - started);
                if (remaining > 0) {
                    redis.opsForValue().set(key, previous, remaining, TimeUnit.MILLISECONDS);
                } else {
                    redis.delete(key);
                }
            }
        }
    }
}
