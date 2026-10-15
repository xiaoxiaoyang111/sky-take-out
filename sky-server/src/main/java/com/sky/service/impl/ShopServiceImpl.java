package com.sky.service.impl;

import com.sky.service.ShopService;
import com.sky.exception.BaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl implements ShopService {
    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Integer getStatus() {
        try {
            String status = stringRedisTemplate.opsForValue().get(SHOP_STATUS_KEY);
            if (status == null) {
                return 0;
            }
            if (!"0".equals(status) && !"1".equals(status)) {
                throw new BaseException("营业状态数据异常，请重新设置");
            }
            return Integer.valueOf(status);
        } catch (DataAccessException ex) {
            throw new BaseException("营业状态服务暂不可用，请稍后重试");
        }
    }

    @Override
    public void setStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BaseException("营业状态只能为0或1");
        }
        try {
            stringRedisTemplate.opsForValue().set(SHOP_STATUS_KEY, status.toString());
        } catch (DataAccessException ex) {
            throw new BaseException("营业状态服务暂不可用，请稍后重试");
        }
    }
}
