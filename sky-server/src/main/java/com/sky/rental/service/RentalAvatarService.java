package com.sky.rental.service;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import java.util.Map;

@Service
public class RentalAvatarService {
    private final RentalCustomerMapper customers;
    private final RentalImageService images;
    public RentalAvatarService(RentalCustomerMapper customers,RentalImageService images){this.customers=customers;this.images=images;}
    public Map<String,String> upload(MultipartFile file){
        Long uid=RentalContext.getUserId();
        if(uid==null||!"USER".equals(RentalContext.getRole()))throw new BaseException("请使用租赁用户账号登录");
        if(customers.findById(uid)==null)throw new BaseException("账号不存在，请重新登录");
        String url=images.upload(file);
        try{customers.saveAvatar(uid,url);}
        catch(RuntimeException ex){images.removeUploaded(url);throw new BaseException("头像保存失败，请稍后重试");}
        return Collections.singletonMap("avatarUrl",url);
    }
}
