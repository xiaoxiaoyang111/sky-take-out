package com.sky.rental.mapper;

import com.sky.rental.model.RentalMemberModels.*;
import com.sky.rental.model.RentalOrder;
import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface RentalMemberMapper {
    @Select("select count(*) from rental_favorite where customer_id=#{customerId}") long favoriteCount(Long customerId);
    @Select("select count(*) from rental_order where customer_id=#{customerId}") long orderCount(Long customerId);
    @Select("select count(*) from rental_review where customer_id=#{customerId}") long reviewCount(Long customerId);
    @Select("select count(*) from rental_favorite where customer_id=#{customerId} and equipment_id=#{equipmentId}") long favoriteExists(@Param("customerId") Long customerId,@Param("equipmentId") Long equipmentId);
    @Insert("insert into rental_favorite(customer_id,equipment_id) values(#{customerId},#{equipmentId}) on duplicate key update equipment_id=values(equipment_id)") void addFavorite(@Param("customerId") Long customerId,@Param("equipmentId") Long equipmentId);
    @Delete("delete from rental_favorite where customer_id=#{customerId} and equipment_id=#{equipmentId}") void removeFavorite(@Param("customerId") Long customerId,@Param("equipmentId") Long equipmentId);
    List<FavoriteView> favorites(@Param("customerId") Long customerId,@Param("offset") long offset,@Param("limit") int limit);
    @Select("select id,customer_id,status,equipment_name from rental_order where id=#{id}") RentalOrder reviewOrder(Long id);
    @Insert("insert into rental_review(customer_id,order_id,rating,content) values(#{customerId},#{orderId},#{rating},#{content})") void addReview(@Param("customerId") Long customerId,@Param("orderId") Long orderId,@Param("rating") int rating,@Param("content") String content);
    List<ReviewView> reviews(@Param("customerId") Long customerId,@Param("offset") long offset,@Param("limit") int limit);
}
