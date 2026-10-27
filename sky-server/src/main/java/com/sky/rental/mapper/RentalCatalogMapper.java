package com.sky.rental.mapper;
import com.sky.rental.model.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface RentalCatalogMapper {
    @Select("select * from rental_category where (#{admin}=true or status=1) order by sort,id") List<RentalCategory> categories(boolean admin);
    @Select("select * from rental_category where id=#{id}") RentalCategory category(Long id);
    @Insert("insert into rental_category(name,sort,status) values(#{name},#{sort},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") void insertCategory(RentalCategory c);
    @Update("update rental_category set name=#{name},sort=#{sort},status=#{status} where id=#{id}") void updateCategory(RentalCategory c);
    List<RentalEquipment> equipmentList(@Param("admin") boolean admin,@Param("categoryId") Long categoryId,@Param("keyword") String keyword);
    RentalEquipment getEquipment(Long id);
    @Select("select * from rental_equipment where id=#{id} for update") RentalEquipment lockEquipment(Long id);
    @Insert("insert into rental_equipment(category_id,name,brand,description,image,daily_rate,deposit,status,created_at,updated_at,create_user,update_user) values(#{categoryId},#{name},#{brand},#{description},#{image},#{dailyRate},#{deposit},#{status},#{createdAt},#{updatedAt},#{createUser},#{updateUser})") @Options(useGeneratedKeys=true,keyProperty="id") void insertEquipment(RentalEquipment e);
    @Update("update rental_equipment set category_id=#{categoryId},name=#{name},brand=#{brand},description=#{description},image=#{image},daily_rate=#{dailyRate},deposit=#{deposit},status=#{status},updated_at=#{updatedAt},update_user=#{updateUser} where id=#{id}") void updateEquipment(RentalEquipment e);
    @Select("select * from rental_asset where equipment_id=#{id} order by id") List<RentalAsset> assets(Long id);
    @Select("select * from rental_asset where id=#{id}") RentalAsset asset(Long id);
    @Select("select * from rental_asset where id=#{id} for update") RentalAsset lockAsset(Long id);
    @Select("select id from rental_order where asset_id=#{id} and status in ('PENDING','CONFIRMED','RENTED') for update") List<Long> occupyingOrders(Long id);
    @Insert("insert into rental_asset(equipment_id,asset_code,status,created_at) values(#{equipmentId},#{assetCode},#{status},#{createdAt})") @Options(useGeneratedKeys=true,keyProperty="id") void insertAsset(RentalAsset a);
    @Update("update rental_asset set status=#{status} where id=#{id}") void updateAsset(RentalAsset a);
}
