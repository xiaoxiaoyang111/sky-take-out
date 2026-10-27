package com.sky.rental.service;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalBookingMapper;
import com.sky.rental.model.*;
import com.sky.result.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** MySQL is the source of truth for date occupancy. All inventory writers lock equipment first. */
@Service
public class RentalBookingService {
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList("PENDING","CONFIRMED","RENTED","COMPLETED","CANCELLED","REJECTED"));
    private final RentalBookingMapper mapper;
    public RentalBookingService(RentalBookingMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public RentalQuote quote(Long equipmentId, LocalDate start, LocalDate end) {
        int days = validateDates(start,end);
        BookingEquipmentRow equipment = bookable(mapper.equipment(positive(equipmentId)));
        Set<Long> occupied = new HashSet<>(mapper.occupied(equipmentId,start,end,LocalDate.now()));
        int available = (int) mapper.readyAssets(equipmentId).stream().filter(a -> !occupied.contains(a.getId())).count();
        RentalQuote q = new RentalQuote();
        q.setEquipmentId(equipmentId); q.setStartDate(start); q.setEndDate(end); q.setRentalDays(days);
        q.setDailyRate(equipment.getDailyRate()); q.setRentalAmount(equipment.getDailyRate().multiply(java.math.BigDecimal.valueOf(days)));
        q.setDepositAmount(equipment.getDeposit()); q.setTotalAmount(q.getRentalAmount().add(q.getDepositAmount())); q.setAvailableCount(available);
        return q;
    }

    @Transactional(readOnly = true)
    public RentalAvailability availability(Long equipmentId, LocalDate start, LocalDate end) {
        RentalQuote q=quote(equipmentId,start,end);
        RentalAvailability a=new RentalAvailability(); a.setEquipmentId(equipmentId);a.setStartDate(start);a.setEndDate(end);a.setAvailableCount(q.getAvailableCount());return a;
    }

    @Transactional
    public RentalOrder create(RentalBookingRequest request) {
        requireRole("USER");
        if (request==null) throw error("预约参数不能为空");
        positive(request.getEquipmentId());
        String requestId=requiredText(request.getRequestId(),64,"请求编号");
        if (!requestId.equals(request.getRequestId())) throw error("请求编号不能包含首尾空格");
        String remark=optionalText(request.getRemark(),500,"备注");
        if(remark==null) remark="";
        BookingEquipmentRow equipment=mapper.lockEquipment(request.getEquipmentId());
        if(equipment==null) throw error("器材不存在");
        Long customerId=RentalContext.getUserId();
        RentalOrder existing=mapper.findRequest(customerId,requestId);
        if(existing!=null) {
            if(!Objects.equals(existing.getEquipmentId(),request.getEquipmentId()) || !Objects.equals(existing.getStartDate(),request.getStartDate()) || !Objects.equals(existing.getEndDate(),request.getEndDate()) || !Objects.equals(existing.getRemark(),remark))
                throw error("同一请求编号不能用于不同预约参数");
            return withEvents(existing);
        }
        int days=validateDates(request.getStartDate(),request.getEndDate());
        equipment.setCategoryStatus(mapper.categoryStatus(equipment.getId()));
        bookable(equipment);
        // These must be current reads after the equipment lock, not repeatable-read snapshots.
        Set<Long> occupied=new HashSet<>(mapper.lockOccupied(equipment.getId(),request.getStartDate(),request.getEndDate(),LocalDate.now()));
        BookingAssetRow asset=mapper.lockReadyAssets(equipment.getId()).stream().filter(a -> !occupied.contains(a.getId())).findFirst().orElseThrow(() -> error("所选租期暂无可租器材"));
        RentalOrder order=new RentalOrder();
        order.setOrderNo("GY"+UUID.randomUUID().toString().replace("-",""));
        order.setCustomerId(customerId);order.setEquipmentId(equipment.getId());order.setAssetId(asset.getId());order.setEquipmentName(equipment.getName());order.setAssetCode(asset.getAssetCode());
        order.setStartDate(request.getStartDate());order.setEndDate(request.getEndDate());order.setRentalDays(days);order.setDailyRate(equipment.getDailyRate());
        order.setRentalAmount(equipment.getDailyRate().multiply(java.math.BigDecimal.valueOf(days)));order.setDepositAmount(equipment.getDeposit());
        order.setStatus("PENDING");order.setRequestId(requestId);order.setRemark(remark);order.setCreatedAt(LocalDateTime.now());order.setUpdatedAt(order.getCreatedAt());
        try { mapper.insertOrder(order); } catch(DuplicateKeyException ex) { throw error("请求编号已使用，请刷新订单后重试"); }
        event(order,"CREATE",remark);
        return withEvents(order);
    }

    public PageResult list(boolean admin,int page,int pageSize,String status,String keyword) {
        requireRole(admin?"ADMIN":"USER");
        if(page<1 || pageSize<1 || pageSize>100) throw error("页码须为正整数，每页记录数须为1至100");
        status=optionalText(status,16,"订单状态");
        if(status!=null && !STATUSES.contains(status)) throw error("订单状态无效");
        keyword=optionalText(keyword,100,"搜索条件");
        Long customerId=admin?null:RentalContext.getUserId();
        return new PageResult(mapper.count(customerId,status,keyword),mapper.page(customerId,status,keyword,((long)page-1)*pageSize,pageSize));
    }

    public RentalOrder detail(Long id,boolean admin) {
        requireRole(admin?"ADMIN":"USER");
        RentalOrder order=mapper.findOrder(positive(id));
        checkOwner(order,admin);
        return withEvents(order);
    }

    @Transactional
    public RentalOrder transition(Long id,String action,String note) {
        boolean admin=!"cancel".equals(action);
        requireRole(admin?"ADMIN":"USER");
        note=optionalText(note,500,"操作说明");
        if("return".equals(action) && note==null) throw error("请填写验收说明");
        if("reject".equals(action) && note==null) throw error("请填写拒绝原因");
        RentalOrder initial=mapper.findOrder(positive(id));
        checkOwner(initial,admin);
        // Locate, then lock equipment before order. Initial lookup is never trusted for mutable state.
        if(mapper.lockEquipment(initial.getEquipmentId())==null) throw error("器材不存在");
        RentalOrder order=mapper.lockOrder(id);
        checkOwner(order,admin);
        LocalDateTime now=LocalDateTime.now();
        switch(action) {
            case "cancel":
                if(!Arrays.asList("PENDING","CONFIRMED").contains(order.getStatus())) throw error("仅待确认或待领取订单可取消");
                order.setStatus("CANCELLED");break;
            case "confirm":
                expect(order,"PENDING");order.setStatus("CONFIRMED");order.setConfirmedAt(now);break;
            case "reject":
                expect(order,"PENDING");order.setStatus("REJECTED");break;
            case "pickup":
                expect(order,"CONFIRMED");
                LocalDate today=LocalDate.now();
                if(today.isBefore(order.getStartDate()) || !today.isBefore(order.getEndDate())) throw error("仅租期开始日至归还日前可领取");
                if(!mapper.lockOtherRentedOrders(order.getAssetId(),order.getId()).isEmpty()) throw error("该实物尚未归还，暂不能领取");
                order.setStatus("RENTED");order.setPickedUpAt(now);break;
            case "return":
                expect(order,"RENTED");order.setStatus("COMPLETED");order.setReturnedAt(now);order.setReturnNote(note);break;
            default:throw error("订单操作无效");
        }
        order.setUpdatedAt(now);mapper.updateOrder(order);event(order,action.toUpperCase(Locale.ROOT),note);
        return withEvents(order);
    }

    public RentalDashboard dashboard() { requireRole("ADMIN");return mapper.dashboard(); }

    static int validateDates(LocalDate start,LocalDate end) {
        LocalDate today=LocalDate.now();
        if(start==null || end==null) throw error("请选择领取和归还日期");
        long days=ChronoUnit.DAYS.between(start,end);
        if(start.isBefore(today) || start.isAfter(today.plusDays(180)) || days<1 || days>30) throw error("开始日期须在今天至未来180天内，租期须为1至30天");
        return (int)days;
    }
    private BookingEquipmentRow bookable(BookingEquipmentRow row) {
        if(row==null) throw error("器材不存在");
        if(!Integer.valueOf(1).equals(row.getStatus()) || !Integer.valueOf(1).equals(row.getCategoryStatus())) throw error("器材或分类已停用");
        return row;
    }
    private void checkOwner(RentalOrder order,boolean admin) {
        if(order==null || (!admin && !Objects.equals(order.getCustomerId(),RentalContext.getUserId()))) throw error("订单不存在或无权访问");
    }
    private RentalOrder withEvents(RentalOrder order) { order.setEvents(mapper.events(order.getId()));return order; }
    private void event(RentalOrder order,String action,String note) {
        RentalOrderEvent e=new RentalOrderEvent();e.setOrderId(order.getId());e.setActorRole(RentalContext.getRole());e.setActorId(RentalContext.getUserId());e.setAction(action);e.setNote(note==null?"":note);e.setCreatedAt(LocalDateTime.now());mapper.insertEvent(e);
    }
    private static void expect(RentalOrder order,String expected) { if(!expected.equals(order.getStatus())) throw error("当前订单状态不允许此操作"); }
    private static Long positive(Long id) { if(id==null || id<=0) throw error("编号须为正整数");return id; }
    private static String requiredText(String text,int max,String label) { String value=optionalText(text,max,label);if(value==null) throw error(label+"不能为空");return value; }
    private static String optionalText(String text,int max,String label) { if(text==null || text.trim().isEmpty()) return null;String value=text.trim();if(value.length()>max) throw error(label+"长度不能超过"+max);return value; }
    private static void requireRole(String role) { if(RentalContext.getUserId()==null || !role.equals(RentalContext.getRole())) throw error("无权执行此操作"); }
    private static BaseException error(String message) { return new BaseException(message); }
}
