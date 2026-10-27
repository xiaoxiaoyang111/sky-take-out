package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.model.*;
import com.sky.rental.service.RentalBookingService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(RentalBookingPersistenceIntegrationTest.FailureConfig.class)
@EnabledIfEnvironmentVariable(named="SKY_DB_INTEGRATION",matches="true")
class RentalBookingPersistenceIntegrationTest {
    @Autowired RentalBookingService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventFailureInterceptor failure;
    String prefix;
    Long categoryId,equipmentId,customerId,otherCustomerId;
    final LocalDate today=LocalDate.now();
    @BeforeEach void setup() {
        prefix="RB"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        jdbc.update("insert into rental_customer(username,password_hash,display_name) values(?,?,?)",prefix,"test-only-hash",prefix);
        customerId=jdbc.queryForObject("select id from rental_customer where username=?",Long.class,prefix);
        jdbc.update("insert into rental_customer(username,password_hash,display_name) values(?,?,?)",prefix+"b","test-only-hash",prefix+"b");
        otherCustomerId=jdbc.queryForObject("select id from rental_customer where username=?",Long.class,prefix+"b");
        jdbc.update("insert into rental_category(name,sort,status) values(?,0,1)",prefix);
        categoryId=jdbc.queryForObject("select id from rental_category where name=?",Long.class,prefix);
        jdbc.update("insert into rental_equipment(category_id,name,daily_rate,deposit,status) values(?,?,12.50,100,1)",categoryId,prefix);
        equipmentId=jdbc.queryForObject("select id from rental_equipment where category_id=?",Long.class,categoryId);
        jdbc.update("insert into rental_asset(equipment_id,asset_code,status) values(?,?,'READY')",equipmentId,prefix+"-A");
        user(customerId);
    }
    @AfterEach void cleanup() {
        failure.failNext.set(false);RentalContext.clear();
        if(equipmentId!=null) {
            jdbc.update("delete from rental_order_event where order_id in (select id from rental_order where equipment_id=?)",equipmentId);
            jdbc.update("delete from rental_order where equipment_id=?",equipmentId);
            jdbc.update("delete from rental_asset where equipment_id=?",equipmentId);
            jdbc.update("delete from rental_equipment where id=?",equipmentId);
        }
        if(categoryId!=null) jdbc.update("delete from rental_category where id=?",categoryId);
        if(customerId!=null) jdbc.update("delete from rental_customer where id=?",customerId);
        if(otherCustomerId!=null) jdbc.update("delete from rental_customer where id=?",otherCustomerId);
    }
    void user(Long id) { RentalContext.set(id,"USER","Fixture",prefix); }
    void admin() { RentalContext.set(1L,"ADMIN","Fixture admin","admin"); }
    RentalBookingRequest request(String key,LocalDate start,LocalDate end) { RentalBookingRequest r=new RentalBookingRequest();r.setEquipmentId(equipmentId);r.setRequestId(key);r.setStartDate(start);r.setEndDate(end);return r; }
    @Test void snapshotsIdempotencyAndOwnershipAreEnforced() {
        RentalBookingRequest r=request("one",today,today.plusDays(2));RentalOrder o=service.create(r);
        assertEquals("25.00",o.getRentalAmount().toPlainString());assertEquals("100.00",o.getDepositAmount().toPlainString());assertEquals(prefix,o.getEquipmentName());assertEquals(1,o.getEvents().size());
        assertEquals(o.getId(),service.create(r).getId());
        assertEquals(1,jdbc.queryForObject("select count(*) from rental_order where equipment_id=?",Integer.class,equipmentId));
        r.setEndDate(today.plusDays(3));assertThrows(BaseException.class,()->service.create(r));
        user(otherCustomerId);assertThrows(BaseException.class,()->service.detail(o.getId(),false));assertThrows(BaseException.class,()->service.transition(o.getId(),"cancel",null));assertEquals(0,service.list(false,1,12,null,null).getTotal());
    }
    @Test void overlapIsRejectedAdjacentDatesWorkAndCancelReleasesOccupancy() {
        RentalOrder first=service.create(request("first",today,today.plusDays(2)));
        assertThrows(BaseException.class,()->service.create(request("overlap",today.plusDays(1),today.plusDays(3))));
        assertEquals("PENDING",service.create(request("adjacent",today.plusDays(2),today.plusDays(3))).getStatus());
        service.transition(first.getId(),"cancel","changed plan");
        assertEquals("PENDING",service.create(request("replacement",today,today.plusDays(2))).getStatus());
    }
    @Test void adminLifecycleAndRepeatedOperationsHaveNoDuplicateEffects() {
        RentalOrder order=service.create(request("lifecycle",today,today.plusDays(2)));admin();
        assertEquals("CONFIRMED",service.transition(order.getId(),"confirm",null).getStatus());
        assertThrows(BaseException.class,()->service.transition(order.getId(),"confirm",null));
        assertEquals("RENTED",service.transition(order.getId(),"pickup","collected").getStatus());
        RentalOrder complete=service.transition(order.getId(),"return","inspected: good");
        assertEquals("COMPLETED",complete.getStatus());assertEquals("inspected: good",complete.getReturnNote());assertNotNull(complete.getReturnedAt());assertEquals(4,complete.getEvents().size());
        assertThrows(BaseException.class,()->service.transition(order.getId(),"return",null));
        assertEquals(4,service.detail(order.getId(),true).getEvents().size());
        assertTrue(service.dashboard().getRentalRevenue().compareTo(complete.getRentalAmount())>=0);
    }
    @Test void eventFailureRollsBackTheInsertedOrderAndInventory() {
        failure.failNext.set(true);
        assertThrows(RuntimeException.class,()->service.create(request("rollback",today,today.plusDays(2))));
        assertEquals(0,jdbc.queryForObject("select count(*) from rental_order where equipment_id=?",Integer.class,equipmentId));
        assertEquals(1,service.availability(equipmentId,today,today.plusDays(2)).getAvailableCount());
        assertNotNull(service.create(request("retry",today,today.plusDays(2))).getId());
    }
    @Test void concurrentRequestsCannotDoubleBookSingleAsset() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++) {final int n=i;results.add(pool.submit(()->{user(n==0?customerId:otherCustomerId);ready.countDown();go.await();try{service.create(request("concurrent-"+n,today,today.plusDays(2)));return true;}catch(BaseException expected){assertTrue(expected.getMessage().contains("暂无可租"));return false;}finally{RentalContext.clear();}}));}
            assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();int success=0;for(Future<Boolean> result:results) if(result.get(15,TimeUnit.SECONDS)) success++;
            assertEquals(1,success);assertEquals(1,jdbc.queryForObject("select count(*) from rental_order where equipment_id=?",Integer.class,equipmentId));
        } finally {go.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentIdenticalRetriesReturnOneOrderAndOneEvent() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            Callable<Long> call=()->{user(customerId);ready.countDown();go.await();try{return service.create(request("same-key",today,today.plusDays(2))).getId();}finally{RentalContext.clear();}};
            Future<Long> a=pool.submit(call),b=pool.submit(call);assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from rental_order where equipment_id=?",Integer.class,equipmentId));
            assertEquals(1,jdbc.queryForObject("select count(*) from rental_order_event where order_id in (select id from rental_order where equipment_id=?)",Integer.class,equipmentId));
        } finally {go.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void maintenanceAndInactiveEquipmentHaveNoBookableInventory() {
        jdbc.update("update rental_asset set status='MAINTENANCE' where equipment_id=?",equipmentId);
        assertEquals(0,service.availability(equipmentId,today,today.plusDays(1)).getAvailableCount());
        assertThrows(BaseException.class,()->service.create(request("repair",today,today.plusDays(1))));
        jdbc.update("update rental_equipment set status=0 where id=?",equipmentId);
        assertThrows(BaseException.class,()->service.quote(equipmentId,today,today.plusDays(1)));
    }
    @Test void unreturnedAssetPastReturnDateIsNotAdvertisedAsAvailable() {
        RentalOrder order=service.create(request("held",today,today.plusDays(1)));
        admin();service.transition(order.getId(),"confirm",null);service.transition(order.getId(),"pickup","collected");
        // Simulate the calendar advancing while the physical asset has not been returned.
        jdbc.update("update rental_order set start_date=?,end_date=? where id=?",today.minusDays(1),today,order.getId());
        assertEquals(0,service.availability(equipmentId,today.plusDays(2),today.plusDays(3)).getAvailableCount());
        user(otherCustomerId);
        assertThrows(BaseException.class,()->service.create(request("replacement",today.plusDays(2),today.plusDays(3))));
        admin();service.transition(order.getId(),"return","asset inspected and returned");
        assertEquals(1,service.availability(equipmentId,today.plusDays(2),today.plusDays(3)).getAvailableCount());
    }
    @Test void prebookedNextCustomerCannotCollectAnAssetThatIsStillOut() {
        RentalOrder first=service.create(request("first-holder",today,today.plusDays(1)));
        user(otherCustomerId);
        RentalOrder next=service.create(request("next-holder",today.plusDays(1),today.plusDays(2)));
        admin();service.transition(first.getId(),"confirm",null);service.transition(next.getId(),"confirm",null);
        service.transition(first.getId(),"pickup","first collection");
        jdbc.update("update rental_order set start_date=?,end_date=? where id=?",today.minusDays(1),today,first.getId());
        jdbc.update("update rental_order set start_date=?,end_date=? where id=?",today,today.plusDays(1),next.getId());
        assertThrows(BaseException.class,()->service.transition(next.getId(),"pickup","second collection"));
        assertEquals("CONFIRMED",service.detail(next.getId(),true).getStatus());
        service.transition(first.getId(),"return","first asset checked back in");
        assertEquals("RENTED",service.transition(next.getId(),"pickup","second collection").getStatus());
    }
    @TestConfiguration static class FailureConfig { @Bean EventFailureInterceptor rentalEventFailureInterceptor(){return new EventFailureInterceptor();} }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    static class EventFailureInterceptor implements Interceptor {
        final AtomicBoolean failNext=new AtomicBoolean();
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement=(MappedStatement)invocation.getArgs()[0];
            if(statement.getId().equals("com.sky.rental.mapper.RentalBookingMapper.insertEvent") && failNext.compareAndSet(true,false)) throw new DataIntegrityViolationException("Deliberate event write failure for rollback verification");
            return invocation.proceed();
        }
    }
}
