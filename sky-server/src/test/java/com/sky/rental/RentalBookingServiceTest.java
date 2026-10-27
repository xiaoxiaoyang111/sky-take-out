package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalBookingMapper;
import com.sky.rental.model.*;
import com.sky.rental.service.RentalBookingService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RentalBookingServiceTest {
    RentalBookingMapper mapper;
    RentalBookingService service;
    LocalDate today=LocalDate.now();
    @BeforeEach void setup() { mapper=mock(RentalBookingMapper.class);service=new RentalBookingService(mapper);RentalContext.set(11L,"USER","Test","test"); }
    @AfterEach void clear() { RentalContext.clear(); }
    BookingEquipmentRow equipment() { BookingEquipmentRow e=new BookingEquipmentRow();e.setId(5L);e.setName("Camera");e.setStatus(1);e.setCategoryStatus(1);e.setDailyRate(new BigDecimal("12.50"));e.setDeposit(new BigDecimal("100.00"));return e; }
    RentalBookingRequest request() { RentalBookingRequest r=new RentalBookingRequest();r.setEquipmentId(5L);r.setStartDate(today);r.setEndDate(today.plusDays(2));r.setRequestId("test-key");return r; }
    RentalOrder order(String status) { RentalOrder o=new RentalOrder();o.setId(7L);o.setCustomerId(11L);o.setEquipmentId(5L);o.setStatus(status);o.setStartDate(today);o.setEndDate(today.plusDays(2));return o; }
    @Test void quoteComputesBackendMoneyAndExcludesOccupiedAssets() {
        when(mapper.equipment(5L)).thenReturn(equipment());BookingAssetRow a=new BookingAssetRow();a.setId(1L);BookingAssetRow b=new BookingAssetRow();b.setId(2L);
        when(mapper.readyAssets(5L)).thenReturn(Arrays.asList(a,b));when(mapper.occupied(5L,today,today.plusDays(2),today)).thenReturn(Collections.singletonList(1L));
        RentalQuote q=service.quote(5L,today,today.plusDays(2));assertEquals(1,q.getAvailableCount());assertEquals(new BigDecimal("25.00"),q.getRentalAmount());assertEquals(new BigDecimal("125.00"),q.getTotalAmount());
    }
    @Test void rejectsDateBoundariesWithoutQueryingDatabase() {
        for(LocalDate[] dates:Arrays.asList(new LocalDate[]{today.minusDays(1),today},new LocalDate[]{today,today},new LocalDate[]{today,today.plusDays(31)},new LocalDate[]{today.plusDays(181),today.plusDays(182)},new LocalDate[]{null,today}))
            assertThrows(BaseException.class,()->service.quote(5L,dates[0],dates[1]));
        verifyNoInteractions(mapper);
    }
    @Test void acceptsThirtyDaysAndFutureBoundary() {
        when(mapper.equipment(5L)).thenReturn(equipment());when(mapper.readyAssets(5L)).thenReturn(Collections.emptyList());when(mapper.occupied(anyLong(),any(),any(),any())).thenReturn(Collections.emptyList());
        assertEquals(30,service.quote(5L,today.plusDays(180),today.plusDays(210)).getRentalDays());
    }
    @Test void disabledEquipmentCannotBeQuoted() { BookingEquipmentRow e=equipment();e.setStatus(0);when(mapper.equipment(5L)).thenReturn(e);assertThrows(BaseException.class,()->service.quote(5L,today,today.plusDays(1))); }
    @Test void anotherUsersOrderCannotBeReadOrChanged() {RentalOrder o=order("PENDING");o.setCustomerId(99L);when(mapper.findOrder(7L)).thenReturn(o);assertThrows(BaseException.class,()->service.detail(7L,false));assertThrows(BaseException.class,()->service.transition(7L,"cancel",null));verify(mapper,never()).lockEquipment(anyLong());}
    @Test void adminCannotCreateUserBooking() {RentalContext.set(1L,"ADMIN","Admin","admin");assertThrows(BaseException.class,()->service.create(request()));verifyNoInteractions(mapper);}
    @Test void invalidPaginationAndStatusFailBeforeQuery() {assertThrows(BaseException.class,()->service.list(false,0,12,null,null));assertThrows(BaseException.class,()->service.list(false,1,101,null,null));assertThrows(BaseException.class,()->service.list(false,1,12,"FAKE",null));verifyNoInteractions(mapper);}
    @Test void idempotentRetryReturnsExistingOrderEvenIfEquipmentDisabled() {
        BookingEquipmentRow e=equipment();e.setStatus(0);when(mapper.lockEquipment(5L)).thenReturn(e);RentalOrder o=order("CANCELLED");o.setRemark("");when(mapper.findRequest(11L,"test-key")).thenReturn(o);
        assertSame(o,service.create(request()));verify(mapper,never()).insertOrder(any());verify(mapper,never()).insertEvent(any());
    }
    @Test void requestKeyCannotBeReusedForDifferentDates() {when(mapper.lockEquipment(5L)).thenReturn(equipment());RentalOrder o=order("PENDING");o.setRemark("");o.setEndDate(today.plusDays(3));when(mapper.findRequest(11L,"test-key")).thenReturn(o);assertThrows(BaseException.class,()->service.create(request()));}
    @Test void completedOrderCannotBeCancelled() {RentalOrder o=order("COMPLETED");when(mapper.findOrder(7L)).thenReturn(o);when(mapper.lockEquipment(5L)).thenReturn(equipment());when(mapper.lockOrder(7L)).thenReturn(o);assertThrows(BaseException.class,()->service.transition(7L,"cancel",null));verify(mapper,never()).updateOrder(any());}
    @Test void pickupBeforeStartDateIsRejected() {RentalContext.set(1L,"ADMIN","Admin","admin");RentalOrder o=order("CONFIRMED");o.setStartDate(today.plusDays(1));when(mapper.findOrder(7L)).thenReturn(o);when(mapper.lockEquipment(5L)).thenReturn(equipment());when(mapper.lockOrder(7L)).thenReturn(o);assertThrows(BaseException.class,()->service.transition(7L,"pickup",null));verify(mapper,never()).updateOrder(any());}
    @Test void longRequestAndNoteRejected() { RentalBookingRequest r=request();r.setRequestId(String.join("",Collections.nCopies(65,"x")));assertThrows(BaseException.class,()->service.create(r));assertThrows(BaseException.class,()->service.transition(1L,"cancel",String.join("",Collections.nCopies(501,"x"))));verifyNoInteractions(mapper);}
    @Test void inspectionAndRejectionNeedAnExplanation() {
        RentalContext.set(1L,"ADMIN","Admin","admin");
        assertEquals("请填写验收说明",assertThrows(BaseException.class,()->service.transition(7L,"return"," ")).getMessage());
        assertEquals("请填写拒绝原因",assertThrows(BaseException.class,()->service.transition(7L,"reject",null)).getMessage());
        verifyNoInteractions(mapper);
    }
}
