package com.sky.controller.admin;

import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DishControllerTest {

    @Mock
    private DishService dishService;

    @InjectMocks
    private DishController dishController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dishController)
                .setValidator(new Validator() {
                    @Override
                    public boolean supports(Class<?> clazz) {
                        return false;
                    }

                    @Override
                    public void validate(Object target, Errors errors) {
                        // 当前接口没有校验注解，测试中不需要 Bean Validation 提供者。
                    }
                })
                .build();
    }

    @Test
    void pageBindsAllQueryParametersAndReturnsUnifiedResult() throws Exception {
        when(dishService.pageQuery(org.mockito.ArgumentMatchers.any(DishPageQueryDTO.class)))
                .thenReturn(new PageResult(0, Collections.emptyList()));

        mockMvc.perform(get("/admin/dish/page")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("name", "鸡")
                        .param("categoryId", "11")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isArray());

        ArgumentCaptor<DishPageQueryDTO> captor = ArgumentCaptor.forClass(DishPageQueryDTO.class);
        verify(dishService).pageQuery(captor.capture());
        DishPageQueryDTO query = captor.getValue();
        assertEquals(2, query.getPage());
        assertEquals(5, query.getPageSize());
        assertEquals("鸡", query.getName());
        assertEquals(11, query.getCategoryId());
        assertEquals(1, query.getStatus());
    }
}
