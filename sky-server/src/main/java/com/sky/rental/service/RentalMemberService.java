package com.sky.rental.service;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.*;
import com.sky.rental.model.*;
import com.sky.rental.model.RentalMemberModels.*;
import com.sky.result.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RentalMemberService {
    private final RentalMemberMapper mapper;private final RentalCustomerMapper customers;private final RentalCatalogService catalog;
    public RentalMemberService(RentalMemberMapper mapper,RentalCustomerMapper customers,RentalCatalogService catalog) { this.mapper=mapper;this.customers=customers;this.catalog=catalog; }
    public Map<String,Object> summary() { Long uid=user();Map<String,Object> result=new LinkedHashMap<>();result.put("favorites",mapper.favoriteCount(uid));result.put("orders",mapper.orderCount(uid));result.put("reviews",mapper.reviewCount(uid));return result; }
    @Transactional public Map<String,Object> updateProfile(ProfileRequest r) {
        Long uid=user();String name=r==null||r.getName()==null?"":r.getName().trim();
        if(name.isEmpty()||name.length()>32) throw new BaseException("昵称须为1至32字符");
        RentalCustomer customer=customers.findById(uid);if(customer==null) throw new BaseException("账号不存在，请重新登录");
        customers.updateName(uid,name);Map<String,Object> result=new LinkedHashMap<>();result.put("id",uid);result.put("username",customer.getUsername());result.put("name",name);result.put("avatarUrl",customer.getAvatarUrl());result.put("role","USER");return result;
    }
    public boolean favoriteStatus(Long equipmentId) { return mapper.favoriteExists(user(),positive(equipmentId))>0; }
    @Transactional public void favorite(Long equipmentId,boolean add) { Long uid=user();positive(equipmentId);if(add){catalog.equipment(equipmentId,false);mapper.addFavorite(uid,equipmentId);}else mapper.removeFavorite(uid,equipmentId); }
    public PageResult favorites(int page,int size) { Long uid=user();validatePage(page,size);return new PageResult(mapper.favoriteCount(uid),mapper.favorites(uid,((long)page-1)*size,size)); }
    public PageResult reviews(int page,int size) { Long uid=user();validatePage(page,size);return new PageResult(mapper.reviewCount(uid),mapper.reviews(uid,((long)page-1)*size,size)); }
    @Transactional public void review(ReviewRequest r) {
        Long uid=user();if(r==null)throw new BaseException("请填写评价");positive(r.getOrderId());
        String content=r.getContent()==null?"":r.getContent().trim();
        if(r.getRating()==null||r.getRating()<1||r.getRating()>5||content.isEmpty()||content.length()>500)throw new BaseException("评分须为1至5，评价内容须为1至500字符");
        RentalOrder order=mapper.reviewOrder(r.getOrderId());
        if(order==null||!uid.equals(order.getCustomerId()))throw new BaseException("订单不存在或无权评价");
        if(!"COMPLETED".equals(order.getStatus()))throw new BaseException("仅已归还订单可评价");
        try{mapper.addReview(uid,order.getId(),r.getRating(),content);}catch(DuplicateKeyException ex){throw new BaseException("该订单已评价，请查看历史评价");}
    }
    private static Long user(){if(RentalContext.getUserId()==null||!"USER".equals(RentalContext.getRole()))throw new BaseException("请使用租赁用户账号登录");return RentalContext.getUserId();}
    private static Long positive(Long id){if(id==null||id<1)throw new BaseException("编号须为正整数");return id;}
    private static void validatePage(int page,int size){if(page<1||size<1||size>100)throw new BaseException("分页参数无效");}
}
