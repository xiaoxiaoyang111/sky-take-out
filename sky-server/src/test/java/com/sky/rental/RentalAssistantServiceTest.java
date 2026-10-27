package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.model.*;
import com.sky.rental.model.RentalAssistantModels.*;
import com.sky.rental.service.*;
import com.sky.result.PageResult;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalAssistantServiceTest {
    RentalAssistantStore store;RentalModelClient model;RentalBookingService booking;RentalCatalogService catalog;RentalAssistantService service;
    @BeforeEach void init() { store=mock(RentalAssistantStore.class);model=mock(RentalModelClient.class);booking=mock(RentalBookingService.class);catalog=mock(RentalCatalogService.class);service=new RentalAssistantService(store,model,booking,catalog);RentalContext.set(9L,"USER","fixture","fixture");when(store.acquire(9L)).thenReturn("lease");when(store.history(anyLong(),anyString())).thenReturn(Collections.emptyList()); }
    @AfterEach void clean() { RentalContext.clear(); }
    Request request(String action) { Request r=new Request();r.setSessionId("test-session-123456");r.setMessage("帮我选一款相机");r.setAction(action);return r; }
    @Test void rulesComeFromKnowledgeWithoutCallingModel() { Turn t=service.ask(request("RULES"));assertEquals("RULES",t.getMode());assertTrue(t.getAnswer().contains("归还日不计费"));verifyNoInteractions(model,booking,catalog);verify(store).append(eq(9L),eq("test-session-123456"),same(t));verify(store).release(9L,"lease"); }
    @Test void recentOrdersUseCurrentIdentityAndNeverSendToModel() { RentalOrder o=new RentalOrder();o.setEquipmentName("Camera");o.setStatus("CONFIRMED");when(booking.list(false,1,5,null,null)).thenReturn(new PageResult(1,Collections.singletonList(o)));Turn t=service.ask(request("ORDERS"));assertTrue(t.getAnswer().contains("待领取"));verifyNoInteractions(model,catalog);verify(booking).list(false,1,5,null,null); }
    @Test void quoteUsesExistingPricingService() { Request r=request("QUOTE");r.setEquipmentId(1L);r.setStartDate(LocalDate.now());r.setEndDate(LocalDate.now().plusDays(2));RentalQuote q=new RentalQuote();q.setEquipmentId(1L);q.setAvailableCount(2);q.setRentalDays(2);q.setRentalAmount(new BigDecimal("25.00"));q.setDepositAmount(new BigDecimal("100.00"));q.setTotalAmount(new BigDecimal("125.00"));when(booking.quote(1L,r.getStartDate(),r.getEndDate())).thenReturn(q);assertTrue(service.ask(r).getAnswer().contains("125.00"));verifyNoInteractions(model); }
    @Test void malformedSessionAndUnknownActionsRejectedBeforeRedis() { Request r=request("RULES");r.setSessionId("../another-user");assertThrows(BaseException.class,()->service.ask(r));r.setSessionId("valid-session-12345");r.setAction("CANCEL");assertThrows(BaseException.class,()->service.ask(r));verifyNoInteractions(store,model,booking,catalog); }
    @Test void adminCannotUseCustomerHistoryOrQueries() { RentalContext.set(1L,"ADMIN","staff","staff");assertThrows(BaseException.class,()->service.ask(request("RULES")));assertThrows(BaseException.class,()->service.history("test-session-123456"));verifyNoInteractions(store); }
    @Test void blankOrLongQuestionRejectedBeforeRedis() { Request r=request("ASK");r.setMessage(" ");assertThrows(BaseException.class,()->service.ask(r));r.setMessage(String.join("",Collections.nCopies(601,"a")));assertThrows(BaseException.class,()->service.ask(r));verifyNoInteractions(store); }
    @Test void failedToolReleasesLeaseAndDoesNotAppendSuccess() { Request r=request("QUOTE");when(booking.quote(any(),any(),any())).thenThrow(new BaseException("invalid date"));assertThrows(BaseException.class,()->service.ask(r));verify(store).release(9L,"lease");verify(store,never()).append(any(),any(),any()); }
    @Test void modelRequiresExplicitConsent() { when(model.configured()).thenReturn(true);assertThrows(BaseException.class,()->service.ask(request("ASK")));verify(model,never()).answer(any(),any(),any()); }
    @Test void privateToolHistoryNeverIncludedInModelPrompt() { Turn privateTurn=new Turn();privateTurn.setMode("LIVE");privateTurn.setAnswer("PRIVATE_ORDER");when(store.history(anyLong(),anyString())).thenReturn(Collections.singletonList(privateTurn));when(model.configured()).thenReturn(true);when(model.answer(any(),any(),any())).thenReturn("建议确认相机卡口");Request r=request("ASK");r.setAllowModel(true);assertEquals("MODEL",service.ask(r).getMode());verify(model).answer(argThat(s->!s.contains("PRIVATE_ORDER")),eq(Collections.emptyList()),eq(r.getMessage())); }
    @Test void rateLimitFailureDoesNotCallBusinessOrModel() { when(store.acquire(9L)).thenThrow(new BaseException("limit"));assertThrows(BaseException.class,()->service.ask(request("ORDERS")));verifyNoInteractions(model,booking,catalog); }
}
