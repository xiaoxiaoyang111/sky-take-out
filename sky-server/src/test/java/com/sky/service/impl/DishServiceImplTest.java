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
import com.sky.mapper.SetmealMapper;
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
import java.util.Arrays;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private SetmealMapper setmealMapper;

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

    @Test
    void getByIdReturnsDishWithFlavors() {
        DishVO dish = DishVO.builder().id(68L).name("鸡蛋汤").categoryName("汤类").build();
        DishFlavor flavor = DishFlavor.builder().id(9L).dishId(68L).name("温度").value("[\"热\"]").build();
        when(dishMapper.getById(68L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(68L)).thenReturn(Collections.singletonList(flavor));

        DishVO result = dishService.getById(68L);

        assertSame(dish, result);
        assertEquals("汤类", result.getCategoryName());
        assertEquals(Collections.singletonList(flavor), result.getFlavors());
    }

    @Test
    void getByIdReturnsEmptyFlavorsWhenDishHasNone() {
        DishVO dish = DishVO.builder().id(69L).name("平菇豆腐汤").build();
        when(dishMapper.getById(69L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(69L)).thenReturn(Collections.emptyList());

        DishVO result = dishService.getById(69L);

        assertEquals(0, result.getFlavors().size());
    }

    @Test
    void getByIdRejectsMissingDishWithoutLoadingFlavors() {
        BaseException error = assertThrows(BaseException.class, () -> dishService.getById(999999L));

        assertEquals("菜品不存在", error.getMessage());
        verify(dishMapper).getById(999999L);
        verifyNoInteractions(dishFlavorMapper);
    }

    @Test
    void getByIdRejectsInvalidIdBeforeQuery() {
        assertThrows(BaseException.class, () -> dishService.getById(0L));
        verifyNoInteractions(dishMapper, dishFlavorMapper);
    }

    @Test
    void updateReplacesFlavorsUsingTrustedDishId() {
        DishDTO dto = validDish();
        dto.setId(67L);
        DishFlavor flavor = DishFlavor.builder().id(55L).dishId(999L)
                .name("辣度").value("[\"微辣\"]").build();
        dto.setFlavors(Collections.singletonList(flavor));
        when(dishMapper.getById(67L)).thenReturn(DishVO.builder().id(67L).build());
        validCategory();

        dishService.update(dto);

        ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).update(captor.capture());
        assertEquals(67L, captor.getValue().getId());
        assertEquals(null, captor.getValue().getStatus());
        verify(dishFlavorMapper).deleteByDishId(67L);
        assertEquals(null, flavor.getId());
        assertEquals(67L, flavor.getDishId());
        verify(dishFlavorMapper).insertBatch(dto.getFlavors());
    }

    @Test
    void updateWithNoFlavorsClearsOldFlavors() {
        DishDTO dto = validDish();
        dto.setId(67L);
        when(dishMapper.getById(67L)).thenReturn(DishVO.builder().id(67L).build());
        validCategory();

        dishService.update(dto);

        verify(dishFlavorMapper).deleteByDishId(67L);
        verify(dishFlavorMapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void updateRejectsMissingDishBeforeWrite() {
        DishDTO dto = validDish();
        dto.setId(999999L);

        assertThrows(BaseException.class, () -> dishService.update(dto));

        verify(dishMapper).getById(999999L);
        verify(dishMapper, never()).update(org.mockito.ArgumentMatchers.any(Dish.class));
        verifyNoInteractions(dishFlavorMapper);
    }

    @Test
    void updateRejectsInvalidFlavorBeforeWrite() {
        DishDTO dto = validDish();
        dto.setId(67L);
        dto.setFlavors(Collections.singletonList(DishFlavor.builder().name("").value("[]").build()));
        when(dishMapper.getById(67L)).thenReturn(DishVO.builder().id(67L).build());
        validCategory();

        assertThrows(BaseException.class, () -> dishService.update(dto));

        verify(dishMapper, never()).update(org.mockito.ArgumentMatchers.any(Dish.class));
        verifyNoInteractions(dishFlavorMapper);
    }

    @Test
    void deleteBatchRemovesStoppedDishesAndFlavorsOnce() {
        when(dishMapper.getById(12L)).thenReturn(DishVO.builder().id(12L).status(0).build());
        when(dishMapper.getById(13L)).thenReturn(DishVO.builder().id(13L).status(0).build());

        dishService.deleteBatch(Arrays.asList(12L, 12L, 13L));

        verify(dishFlavorMapper).deleteByDishId(12L);
        verify(dishFlavorMapper).deleteByDishId(13L);
        verify(dishMapper).deleteById(12L);
        verify(dishMapper).deleteById(13L);
    }

    @Test
    void deleteBatchRejectsOnSaleOrSetmealDishBeforeAnyDelete() {
        when(dishMapper.getById(12L)).thenReturn(DishVO.builder().id(12L).status(0).build());
        when(dishMapper.getById(13L)).thenReturn(DishVO.builder().id(13L).status(1).build());
        assertEquals("起售中的菜品不能删除", assertThrows(BaseException.class,
                () -> dishService.deleteBatch(Arrays.asList(12L, 13L))).getMessage());
        verify(dishMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
        verifyNoInteractions(dishFlavorMapper);

        when(dishMapper.getById(14L)).thenReturn(DishVO.builder().id(14L).status(0).build());
        when(setmealMapper.countByDishId(14L)).thenReturn(1);
        assertEquals("当前菜品关联了套餐,不能删除", assertThrows(BaseException.class,
                () -> dishService.deleteBatch(Collections.singletonList(14L))).getMessage());
        verify(dishMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteBatchRejectsMissingOrEmptyIds() {
        assertThrows(BaseException.class, () -> dishService.deleteBatch(Collections.emptyList()));
        assertThrows(BaseException.class, () -> dishService.deleteBatch(Collections.singletonList(0L)));
        assertThrows(BaseException.class, () -> dishService.deleteBatch(Collections.singletonList(999L)));
        verify(dishMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void statusChangesOnlyStatusAndAuditFields() {
        when(dishMapper.getById(12L)).thenReturn(DishVO.builder().id(12L).status(1).build());
        dishService.startOrStop(0, 12L);
        ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).updateStatus(captor.capture());
        assertEquals(12L, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getName());
    }

    @Test
    void statusAndCategoryListRejectInvalidInput() {
        assertThrows(BaseException.class, () -> dishService.startOrStop(2, 12L));
        assertThrows(BaseException.class, () -> dishService.startOrStop(1, 999L));
        assertThrows(BaseException.class, () -> dishService.listByCategoryId(0L));
        verify(dishMapper, never()).updateStatus(org.mockito.ArgumentMatchers.any(Dish.class));
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
