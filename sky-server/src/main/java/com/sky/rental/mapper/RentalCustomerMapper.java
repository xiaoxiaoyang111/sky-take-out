package com.sky.rental.mapper;
import com.sky.rental.model.RentalCustomer;
import org.apache.ibatis.annotations.*;
@Mapper
public interface RentalCustomerMapper {
    @Select("select c.*,a.avatar_url from rental_customer c left join rental_customer_avatar a on a.customer_id=c.id where c.username=#{username}") RentalCustomer find(String username);
    @Select("select c.*,a.avatar_url from rental_customer c left join rental_customer_avatar a on a.customer_id=c.id where c.id=#{id}") RentalCustomer findById(Long id);
    @Insert("insert into rental_customer_avatar(customer_id,avatar_url) values(#{id},#{url}) on duplicate key update avatar_url=values(avatar_url)") void saveAvatar(@Param("id") Long id,@Param("url") String url);
    @Update("update rental_customer set display_name=#{name} where id=#{id}") int updateName(@Param("id") Long id,@Param("name") String name);
    @Insert("insert into rental_customer(username,password_hash,display_name,created_at) values(#{username},#{passwordHash},#{displayName},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insert(RentalCustomer customer);
}
