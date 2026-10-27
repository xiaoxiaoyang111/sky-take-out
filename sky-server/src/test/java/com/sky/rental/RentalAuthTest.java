package com.sky.rental;

import com.sky.entity.Employee;
import com.sky.exception.BaseException;
import com.sky.mapper.EmployeeMapper;
import com.sky.rental.auth.RentalAuthInterceptor;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCustomerMapper;
import com.sky.rental.model.RentalCustomer;
import com.sky.rental.model.RentalRequests;
import com.sky.rental.service.RentalAuthService;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.HandlerMapping;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalAuthTest {
    private final String secret="test-rental-signing-key-not-for-production";
    private RentalCustomerMapper customers;private EmployeeMapper employees;private RentalAuthService service;
    @BeforeEach void init() { customers=mock(RentalCustomerMapper.class);employees=mock(EmployeeMapper.class);service=new RentalAuthService(customers,employees,secret); }
    @AfterEach void clean() { RentalContext.clear(); }
    private RentalRequests.Login request() { RentalRequests.Login r=new RentalRequests.Login();r.setUsername("rental_test");r.setPassword("example-password");r.setName("测试用户");return r; }
    @Test void registerHashesPasswordAndCannotChooseAdmin() {
        doAnswer(call->{((RentalCustomer)call.getArgument(0)).setId(9L);return null;}).when(customers).insert(any());
        Map<String,Object> result=service.register(request());ArgumentCaptor<RentalCustomer> capture=ArgumentCaptor.forClass(RentalCustomer.class);verify(customers).insert(capture.capture());
        assertTrue(new BCryptPasswordEncoder().matches(request().getPassword(),capture.getValue().getPasswordHash()));
        assertEquals("USER",result.get("role"));assertEquals("USER",JwtUtil.parseJWT(secret,(String)result.get("token")).get("role"));
        assertFalse(result.toString().contains("passwordHash"));
    }
    @Test void rejectDuplicateRegistration() { doThrow(new DuplicateKeyException("duplicate")).when(customers).insert(any());assertThrows(BaseException.class,()->service.register(request())); }
    @Test void enforceBcryptByteLengthNotCharacterLength() { RentalRequests.Login r=request();r.setPassword(String.join("",java.util.Collections.nCopies(25,"密")));assertThrows(BaseException.class,()->service.register(r));verifyNoInteractions(customers); }
    @Test void unknownCustomerRejected() { assertThrows(BaseException.class,()->service.login(request(),false)); }
    @Test void adminUsesEmployeeAndRejectsDisabled() {
        Employee employee=new Employee();employee.setId(1L);employee.setName("员工");employee.setUsername("rental_test");employee.setStatus(0);employee.setPassword(DigestUtils.md5DigestAsHex(request().getPassword().getBytes(StandardCharsets.UTF_8)));
        when(employees.getByUsername("rental_test")).thenReturn(employee);assertThrows(BaseException.class,()->service.login(request(),true));employee.setStatus(1);assertEquals("ADMIN",service.login(request(),true).get("role"));
    }
    private MockHttpServletRequest http(String method,String path,String role,String audience) {
        MockHttpServletRequest r=new MockHttpServletRequest(method,path);
        if(role!=null) { Map<String,Object> claims=new LinkedHashMap<>();claims.put("id",1);claims.put("role",role);claims.put("aud",audience);claims.put("name","测试");claims.put("username","test");r.addHeader("Authorization","Bearer "+JwtUtil.createJWT(secret,100000,claims)); } return r;
    }
    @Test void publicGetDoesNotRequireLoginButWriteDoes() throws Exception {
        RentalAuthInterceptor i=new RentalAuthInterceptor(secret,employees);assertTrue(i.preHandle(http("GET","/rental/equipment/1/quote",null,null),new MockHttpServletResponse(),null));
        MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(i.preHandle(http("PUT","/rental/equipment/1",null,null),out,null));assertEquals(401,out.getStatus());
    }
    @Test void userTokenCannotAccessAdministration() throws Exception { MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(http("GET","/rental/admin/assets","USER","rental-v1"),out,null));assertEquals(403,out.getStatus()); }
    @Test void assistantStatusIsPublicButHistoryIsPrivate() throws Exception { RentalAuthInterceptor i=new RentalAuthInterceptor(secret,employees);assertTrue(i.preHandle(http("GET","/rental/assistant/status",null,null),new MockHttpServletResponse(),null));MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(i.preHandle(http("GET","/rental/assistant/history",null,null),out,null));assertEquals(401,out.getStatus()); }
    @Test void staffCannotReadCustomerAssistantHistory() throws Exception { MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(http("GET","/rental/assistant/history","ADMIN","rental-v1"),out,null));assertEquals(403,out.getStatus()); }
    @Test void adminTokenCannotPlaceCustomerOrders() throws Exception { MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(http("POST","/rental/orders","ADMIN","rental-v1"),out,null));assertEquals(403,out.getStatus()); }
    @Test void legacyTokenAudienceRejected() throws Exception { MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(http("GET","/rental/me","USER","legacy"),out,null));assertEquals(401,out.getStatus()); }
    @ParameterizedTest
    @ValueSource(strings={"/rental/%61dmin/categories", "/rental/admin;version=1/categories"})
    void normalizedAdminMappingCannotBypassRoleCheck(String rawPath) throws Exception {
        MockHttpServletRequest request=http("GET",rawPath,"USER","rental-v1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/rental/admin/categories");
        MockHttpServletResponse response=new MockHttpServletResponse();
        assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(request,response,null));
        assertEquals(403,response.getStatus());
        assertNull(RentalContext.getUserId());
    }
    @Test void normalizedPublicMappingRemainsPublic() throws Exception {
        MockHttpServletRequest request=http("GET","/rental/equip%6dent/1",null,null);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/rental/equipment/{id}");
        assertTrue(new RentalAuthInterceptor(secret,employees).preHandle(request,new MockHttpServletResponse(),null));
    }
    @Test void disabledEmployeeCannotReuseExistingToken() throws Exception {
        Employee employee=new Employee();employee.setStatus(0);when(employees.getById(1L)).thenReturn(employee);
        MockHttpServletResponse out=new MockHttpServletResponse();assertFalse(new RentalAuthInterceptor(secret,employees).preHandle(http("GET","/rental/admin/equipment","ADMIN","rental-v1"),out,null));assertEquals(401,out.getStatus());
    }
    @Test void requestIdentityClearsAfterCompletion() throws Exception {
        RentalAuthInterceptor i=new RentalAuthInterceptor(secret,employees);MockHttpServletRequest r=http("GET","/rental/me","USER","rental-v1");assertTrue(i.preHandle(r,new MockHttpServletResponse(),null));assertEquals(1L,RentalContext.getUserId());i.afterCompletion(r,new MockHttpServletResponse(),null,null);assertNull(RentalContext.getUserId());
    }
}
