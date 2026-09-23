package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishPageQueryDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;

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
}
