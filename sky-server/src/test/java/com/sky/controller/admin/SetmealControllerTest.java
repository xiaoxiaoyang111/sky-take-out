package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SetmealControllerTest {

    @Mock private SetmealService setmealService;
    @InjectMocks private SetmealController setmealController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(setmealController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new Validator() {
                    @Override
                    public boolean supports(Class<?> clazz) {
                        return false;
                    }

                    @Override
                    public void validate(Object target, Errors errors) {
                        // 当前参数校验由 Service 负责，与现有 Controller 测试保持一致。
                    }
                }).build();
    }

    @Test
    void pageBindsAllFiltersIncludingLongCategoryIdAndReturnsUnifiedPage() throws Exception {
        SetmealVO setmeal = SetmealVO.builder().id(101L).categoryName("双人套餐").build();
        when(setmealService.pageQuery(any(SetmealPageQueryDTO.class)))
                .thenReturn(new PageResult(6, Collections.singletonList(setmeal)));

        mockMvc.perform(get("/admin/setmeal/page")
                        .param("page", "2").param("pageSize", "5")
                        .param("name", "双人").param("categoryId", "3000000000").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.records[0].categoryName").value("双人套餐"));

        ArgumentCaptor<SetmealPageQueryDTO> captor = ArgumentCaptor.forClass(SetmealPageQueryDTO.class);
        verify(setmealService).pageQuery(captor.capture());
        assertEquals(2, captor.getValue().getPage());
        assertEquals(5, captor.getValue().getPageSize());
        assertEquals("双人", captor.getValue().getName());
        assertEquals(3000000000L, captor.getValue().getCategoryId());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void pageReturnsEmptyRecordsWhenNoSetmealMatches() throws Exception {
        when(setmealService.pageQuery(any(SetmealPageQueryDTO.class)))
                .thenReturn(new PageResult(0, Collections.emptyList()));

        mockMvc.perform(get("/admin/setmeal/page").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void saveBindsSetmealAndAssociatedDishes() throws Exception {
        mockMvc.perform(post("/admin/setmeal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"双人套餐\",\"categoryId\":11,\"price\":20.50," +
                                "\"image\":\"http://localhost/setmeal.png\",\"setmealDishes\":[{\"dishId\":20,\"copies\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<SetmealDTO> captor = ArgumentCaptor.forClass(SetmealDTO.class);
        verify(setmealService).save(captor.capture());
        assertEquals("双人套餐", captor.getValue().getName());
        assertEquals(11L, captor.getValue().getCategoryId());
        assertEquals(new BigDecimal("20.50"), captor.getValue().getPrice());
        assertEquals(20L, captor.getValue().getSetmealDishes().get(0).getDishId());
        assertEquals(2, captor.getValue().getSetmealDishes().get(0).getCopies());
    }

    @Test
    void getByIdReturnsSetmealWithAssociatedDishes() throws Exception {
        when(setmealService.getById(101L)).thenReturn(SetmealVO.builder().id(101L).categoryName("套餐分类")
                .setmealDishes(Collections.singletonList(SetmealDish.builder()
                        .id(1L).setmealId(101L).dishId(20L).name("鸡蛋汤").copies(2).build())).build());

        mockMvc.perform(get("/admin/setmeal/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.categoryName").value("套餐分类"))
                .andExpect(jsonPath("$.data.setmealDishes[0].dishId").value(20))
                .andExpect(jsonPath("$.data.setmealDishes[0].copies").value(2));
    }

    @Test
    void updateBindsIdAndReplacementDishes() throws Exception {
        mockMvc.perform(put("/admin/setmeal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":101,\"name\":\"新套餐名称\",\"categoryId\":11,\"price\":22," +
                                "\"image\":\"http://localhost/setmeal.png\",\"setmealDishes\":[{\"dishId\":21,\"copies\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<SetmealDTO> captor = ArgumentCaptor.forClass(SetmealDTO.class);
        verify(setmealService).update(captor.capture());
        assertEquals(101L, captor.getValue().getId());
        assertEquals(21L, captor.getValue().getSetmealDishes().get(0).getDishId());
        assertEquals(3, captor.getValue().getSetmealDishes().get(0).getCopies());
    }

    @Test
    void statusBindsPathStatusAndQueryId() throws Exception {
        mockMvc.perform(post("/admin/setmeal/status/1").param("id", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(setmealService).startOrStop(1, 101L);
    }

    @Test
    void deleteBindsCommaSeparatedIds() throws Exception {
        mockMvc.perform(delete("/admin/setmeal").param("ids", "101,102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(setmealService).deleteBatch(Arrays.asList(101L, 102L));
    }

    @Test
    void validationFailureUsesExistingBusinessErrorEnvelope() throws Exception {
        doThrow(new BaseException("套餐必须包含菜品")).when(setmealService).save(any(SetmealDTO.class));

        mockMvc.perform(post("/admin/setmeal").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("套餐必须包含菜品"));
    }

    @Test
    void missingSetmealUsesExistingBusinessErrorEnvelope() throws Exception {
        when(setmealService.getById(999L)).thenThrow(new BaseException("套餐不存在"));

        mockMvc.perform(get("/admin/setmeal/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("套餐不存在"));
    }

    @Test
    void rejectedStartUsesExistingBusinessErrorEnvelope() throws Exception {
        doThrow(new BaseException("套餐内包含停售菜品，无法起售"))
                .when(setmealService).startOrStop(1, 101L);

        mockMvc.perform(post("/admin/setmeal/status/1").param("id", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("套餐内包含停售菜品，无法起售"));
    }
}
