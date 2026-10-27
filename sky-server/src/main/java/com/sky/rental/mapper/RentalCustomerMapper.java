package com.sky.rental.mapper;
import com.sky.rental.model.RentalCustomer;
import org.apache.ibatis.annotations.*;
@Mapper
public interface RentalCustomerMapper {
    @Select("select * from rental_customer where username=#{username}") RentalCustomer find(String username);
    @Insert("insert into rental_customer(username,password_hash,display_name,created_at) values(#{username},#{passwordHash},#{displayName},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insert(RentalCustomer customer);
}
