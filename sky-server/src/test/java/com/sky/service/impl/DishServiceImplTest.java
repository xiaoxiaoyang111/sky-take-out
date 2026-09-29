package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Category;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;

    @Mock
    private DishFlavorMapper dishFlavorMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private DishServiceImpl dishService;

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void pageQueryReturnsMapperTotalAndRecords() {
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setPage(2);
        query.setPageSize(5);
        query.setName("鸡");
        query.setCategoryId(11);
        query.setStatus(1);

        DishVO dish = DishVO.builder()
                .id(1L)
                .name("宫保鸡丁")
                .categoryId(11L)
                .categoryName("川菜")
                .price(new BigDecimal("22.00"))
                .status(1)
                .build();
        Page<DishVO> mapperPage = new Page<>(2, 5);
        mapperPage.setTotal(6);
        mapperPage.add(dish);
        when(dishMapper.pageQuery(query)).thenReturn(mapperPage);

        PageResult result = dishService.pageQuery(query);

        assertEquals(6, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertSame(dish, result.getRecords().get(0));
        verify(dishMapper).pageQuery(query);
    }

    @Test
    void pageQueryReturnsEmptyPageWhenNoDishMatches() {
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setPage(1);
        query.setPageSize(10);
        query.setName("不存在的菜品");

        Page<DishVO> mapperPage = new Page<>(1, 10);
        mapperPage.setTotal(0);
        when(dishMapper.pageQuery(query)).thenReturn(mapperPage);

        PageResult result = dishService.pageQuery(query);

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getRecords().size());
        verify(dishMapper).pageQuery(query);
    }

    @Test
    void saveUsesGeneratedDishIdForFlavorsAndDefaultsStatus() {
        DishDTO dto = validDish();
        validCategory();
        DishFlavor flavor = DishFlavor.builder().name("辣度").value("[\"微辣\"]").build();
        dto.setFlavors(Collections.singletonList(flavor));
        doAnswer(invocation -> {
            Dish dish = invocation.getArgument(0);
            dish.setId(88L);
            return null;
        }).when(dishMapper).insert(org.mockito.ArgumentMatchers.any(Dish.class));

        dishService.save(dto);

        ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).insert(dishCaptor.capture());
        assertEquals(1, dishCaptor.getValue().getStatus());
        assertEquals(88L, flavor.getDishId());
        verify(dishFlavorMapper).insertBatch(dto.getFlavors());
    }

    @Test
    void saveWithoutFlavorsDoesNotCallFlavorMapper() {
        validCategory();
        dishService.save(validDish());
        verify(dishMapper).insert(org.mockito.ArgumentMatchers.any(Dish.class));
        verify(dishFlavorMapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void invalidDishIsRejectedBeforeDatabaseWrite() {
        DishDTO dto = validDish();
        dto.setPrice(new BigDecimal("-1"));
        assertThrows(BaseException.class, () -> dishService.save(dto));
        verify(dishMapper, never()).insert(org.mockito.ArgumentMatchers.any(Dish.class));
    }

    @Test
    void invalidCategoryIsRejectedBeforeDatabaseWrite() {
        DishDTO dto = validDish();
        assertThrows(BaseException.class, () -> dishService.save(dto));
        verify(dishMapper, never()).insert(org.mockito.ArgumentMatchers.any(Dish.class));
    }

    private void validCategory() {
        Category category = new Category();
        category.setType(1);
        category.setStatus(1);
        when(categoryMapper.getById(11L)).thenReturn(category);
    }

    private DishDTO validDish() {
        DishDTO dto = new DishDTO();
        dto.setName("测试菜品");
        dto.setCategoryId(11L);
        dto.setPrice(new BigDecimal("12.50"));
        dto.setImage("http://localhost/image.png");
        return dto;
    }
}
