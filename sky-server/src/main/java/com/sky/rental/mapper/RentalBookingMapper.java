package com.sky.rental.mapper;

import com.sky.rental.model.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RentalBookingMapper {
    @Select("select e.*,c.status category_status from rental_equipment e join rental_category c on c.id=e.category_id where e.id=#{id}")
    BookingEquipmentRow equipment(Long id);
    // Only equipment rows are locked; category updates do not change bookability of existing equipment implicitly.
    @Select("select * from rental_equipment where id=#{id} for update")
    BookingEquipmentRow lockEquipment(Long id);
    @Select("select status from rental_category where id=(select category_id from rental_equipment where id=#{id}) lock in share mode")
    Integer categoryStatus(Long id);
    @Select("select * from rental_asset where equipment_id=#{id} and status='READY' order by id")
    List<BookingAssetRow> readyAssets(Long id);
    @Select("select * from rental_asset where equipment_id=#{id} and status='READY' order by id for update")
    List<BookingAssetRow> lockReadyAssets(Long id);
    @Select("select distinct asset_id from rental_order where equipment_id=#{equipmentId} and ((status in ('PENDING','CONFIRMED','RENTED') and start_date < #{endDate} and end_date > #{startDate}) or (status='RENTED' and end_date <= #{today}))")
    List<Long> occupied(@Param("equipmentId") Long equipmentId,@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate,@Param("today") LocalDate today);
    @Select("select asset_id from rental_order where equipment_id=#{equipmentId} and ((status in ('PENDING','CONFIRMED','RENTED') and start_date < #{endDate} and end_date > #{startDate}) or (status='RENTED' and end_date <= #{today})) for update")
    List<Long> lockOccupied(@Param("equipmentId") Long equipmentId,@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate,@Param("today") LocalDate today);
    @Select("select id from rental_order where asset_id=#{assetId} and id != #{orderId} and status='RENTED' for update")
    List<Long> lockOtherRentedOrders(@Param("assetId") Long assetId,@Param("orderId") Long orderId);
    @Select("select * from rental_order where customer_id=#{customerId} and request_id=#{requestId}")
    RentalOrder findRequest(@Param("customerId") Long customerId,@Param("requestId") String requestId);
    @Select("select * from rental_order where id=#{id}")
    RentalOrder findOrder(Long id);
    @Select("select * from rental_order where id=#{id} for update")
    RentalOrder lockOrder(Long id);
    void insertOrder(RentalOrder order);
    @Update("update rental_order set status=#{status},return_note=#{returnNote},updated_at=#{updatedAt},confirmed_at=#{confirmedAt},picked_up_at=#{pickedUpAt},returned_at=#{returnedAt} where id=#{id}")
    int updateOrder(RentalOrder order);
    @Insert("insert into rental_order_event(order_id,actor_role,actor_id,action,note,created_at) values(#{orderId},#{actorRole},#{actorId},#{action},#{note},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    void insertEvent(RentalOrderEvent event);
    @Select("select * from rental_order_event where order_id=#{id} order by id")
    List<RentalOrderEvent> events(Long id);
    List<RentalOrder> page(@Param("customerId") Long customerId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);
    long count(@Param("customerId") Long customerId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("select (select count(*) from rental_equipment) equipment_count,(select count(*) from rental_asset) asset_count,coalesce(sum(status='PENDING'),0) pending_count,coalesce(sum(status='RENTED'),0) rented_count,coalesce(sum(status='COMPLETED'),0) completed_count,coalesce(sum(case when status='COMPLETED' then rental_amount else 0 end),0) rental_revenue from rental_order")
    RentalDashboard dashboard();
}
