package com.sky.controller.admin;

import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShopControllerTest {
    @Mock private ShopService service;
    @InjectMocks private ShopController adminController;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        com.sky.controller.user.ShopController userController = new com.sky.controller.user.ShopController();
        ReflectionTestUtils.setField(userController, "shopService", service);
        mvc = MockMvcBuilders.standaloneSetup(adminController, userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new Validator() {
                    public boolean supports(Class<?> type) { return false; }
                    public void validate(Object target, Errors errors) { }
                }).build();
    }

    @Test
    void bothClientsReadSameStatus() throws Exception {
        when(service.getStatus()).thenReturn(1);
        for (String path : new String[]{"/admin/shop/status", "/user/shop/status"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data").value(1));
        }
    }

    @Test
    void setterBindsPathVariableAndReturnsSuccess() throws Exception {
        mvc.perform(put("/admin/shop/0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        verify(service).setStatus(0);
    }

    @Test
    void invalidStatusReturnsUnifiedBusinessError() throws Exception {
        doThrow(new BaseException("营业状态只能为0或1")).when(service).setStatus(2);
        mvc.perform(put("/admin/shop/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void nonNumericStatusIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(put("/admin/shop/invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void usersCannotSetStatus() throws Exception {
        mvc.perform(put("/user/shop/1")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void redisFailureReturnsErrorForBothClients() throws Exception {
        when(service.getStatus()).thenThrow(new BaseException("营业状态服务暂不可用，请稍后重试"));
        for (String path : new String[]{"/admin/shop/status", "/user/shop/status"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.msg").value("营业状态服务暂不可用，请稍后重试"));
        }
    }
}
