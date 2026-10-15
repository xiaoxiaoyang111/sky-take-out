package com.sky.service.impl;

import com.sky.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> values;
    @InjectMocks private ShopServiceImpl service;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "1"})
    void readsStoredStatusAndDefaultsMissingKeyToClosed(String value) {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenReturn(value);
        assertEquals(value == null ? 0 : Integer.valueOf(value), service.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "invalid", ""})
    void rejectsCorruptedStoredStatus(String value) {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenReturn(value);
        assertThrows(BaseException.class, service::getStatus);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void writesStatusAsPlainString(int value) {
        when(redisTemplate.opsForValue()).thenReturn(values);
        service.setStatus(value);
        verify(values).set("SHOP_STATUS", Integer.toString(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 2})
    void rejectsInvalidStatusBeforeWritingRedis(Integer value) {
        assertThrows(BaseException.class, () -> service.setStatus(value));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void readFailureIsNotReportedAsClosed() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertEquals("营业状态服务暂不可用，请稍后重试",
                assertThrows(BaseException.class, service::getStatus).getMessage());
    }

    @Test
    void writeFailureIsReportedAsBusinessError() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("unavailable")).when(values).set("SHOP_STATUS", "1");
        assertThrows(BaseException.class, () -> service.setStatus(1));
    }
}
