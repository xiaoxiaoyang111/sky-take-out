package com.sky.controller.admin;

import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import org.springframework.http.MediaType;
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
                .setControllerAdvice(new GlobalExceptionHandler())
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

    @Test
    void saveBindsDishAndFlavors() throws Exception {
        mockMvc.perform(post("/admin/dish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试菜品\",\"categoryId\":11,\"price\":12.5," +
                                "\"image\":\"http://localhost/image.png\",\"flavors\":[{\"name\":\"辣度\",\"value\":\"[\\\"微辣\\\"]\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        org.mockito.ArgumentCaptor<com.sky.dto.DishDTO> captor =
                org.mockito.ArgumentCaptor.forClass(com.sky.dto.DishDTO.class);
        verify(dishService).save(captor.capture());
        assertEquals("测试菜品", captor.getValue().getName());
        assertEquals(1, captor.getValue().getFlavors().size());
        assertEquals("辣度", captor.getValue().getFlavors().get(0).getName());
    }

    @Test
    void getByIdReturnsDishAndFlavors() throws Exception {
        DishVO dish = DishVO.builder().id(68L).name("鸡蛋汤")
                .categoryName("汤类")
                .flavors(Collections.singletonList(DishFlavor.builder()
                        .id(1L).dishId(68L).name("温度").value("[\"热\"]").build()))
                .build();
        when(dishService.getById(68L)).thenReturn(dish);

        mockMvc.perform(get("/admin/dish/68"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(68))
                .andExpect(jsonPath("$.data.categoryName").value("汤类"))
                .andExpect(jsonPath("$.data.flavors[0].dishId").value(68));
        verify(dishService).getById(68L);
    }

    @Test
    void getByIdReturnsBusinessErrorWhenDishDoesNotExist() throws Exception {
        when(dishService.getById(999999L)).thenThrow(new BaseException("菜品不存在"));

        mockMvc.perform(get("/admin/dish/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("菜品不存在"));
    }

    @Test
    void updateBindsDishIdAndReplacementFlavors() throws Exception {
        mockMvc.perform(put("/admin/dish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":67,\"name\":\"鮰鱼2斤\",\"categoryId\":16," +
                                "\"price\":72,\"image\":\"http://localhost/image.png\"," +
                                "\"flavors\":[{\"id\":9,\"dishId\":999,\"name\":\"辣度\"," +
                                "\"value\":\"[\\\"微辣\\\"]\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<com.sky.dto.DishDTO> captor =
                ArgumentCaptor.forClass(com.sky.dto.DishDTO.class);
        verify(dishService).update(captor.capture());
        assertEquals(67L, captor.getValue().getId());
        assertEquals(1, captor.getValue().getFlavors().size());
    }

    @Test
    void deleteBindsCommaSeparatedIds() throws Exception {
        mockMvc.perform(delete("/admin/dish").param("ids", "12,13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        verify(dishService).deleteBatch(java.util.Arrays.asList(12L, 13L));
    }

    @Test
    void listBindsCategoryId() throws Exception {
        when(dishService.list(11L, null)).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/admin/dish/list").param("categoryId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(dishService).list(11L, null);
    }

    @Test
    void listAcceptsNameWithoutCategoryId() throws Exception {
        when(dishService.list(null, "鸡")).thenReturn(Collections.singletonList(
                DishVO.builder().id(68L).name("鸡蛋汤").status(1).build()));

        mockMvc.perform(get("/admin/dish/list").param("name", "鸡"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data[0].name").value("鸡蛋汤"));
        verify(dishService).list(null, "鸡");
    }

    @Test
    void listBindsCategoryAndNameTogether() throws Exception {
        when(dishService.list(11L, "鸡")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/dish/list").param("categoryId", "11").param("name", "鸡"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").isArray());
        verify(dishService).list(11L, "鸡");
    }

    @Test
    void statusBindsStatusAndDishId() throws Exception {
        mockMvc.perform(post("/admin/dish/status/0").param("id", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        verify(dishService).startOrStop(0, 12L);
    }
}
