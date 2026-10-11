package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetmealServiceImplTest {

    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private DishMapper dishMapper;
    @InjectMocks private SetmealServiceImpl setmealService;

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void pageQueryReturnsTotalAndRecordsAndClearsPagingContext() {
        SetmealPageQueryDTO query = validQuery();
        query.setCategoryId(3000000000L);
        query.setName("双人");
        query.setStatus(0);
        SetmealVO setmeal = SetmealVO.builder().id(18L).categoryName("双人套餐").build();
        Page<SetmealVO> page = new Page<>(2, 5);
        page.setTotal(6);
        page.add(setmeal);
        when(setmealMapper.pageQuery(query)).thenAnswer(invocation -> {
            assertEquals(2, PageHelper.getLocalPage().getPageNum());
            assertEquals(5, PageHelper.getLocalPage().getPageSize());
            return page;
        });

        PageResult result = setmealService.pageQuery(query);

        assertEquals(6, result.getTotal());
        assertEquals(Collections.singletonList(setmeal), result.getRecords());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void pageQueryClearsPagingContextIfMapperFails() {
        SetmealPageQueryDTO query = validQuery();
        RuntimeException failure = new RuntimeException("query failed");
        when(setmealMapper.pageQuery(query)).thenThrow(failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> setmealService.pageQuery(query)));
        assertNull(PageHelper.getLocalPage());
    }

    @ParameterizedTest
    @CsvSource({"0,10,1,1", "1,0,1,1", "-1,10,1,1", "1,10,2,1", "1,10,1,0"})
    void pageQueryRejectsInvalidParameters(int page, int size, int status, long categoryId) {
        SetmealPageQueryDTO query = validQuery();
        query.setPage(page);
        query.setPageSize(size);
        query.setStatus(status);
        query.setCategoryId(categoryId);

        assertThrows(BaseException.class, () -> setmealService.pageQuery(query));
        verifyNoInteractions(setmealMapper);
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveUsesGeneratedIdDefaultsStoppedAndLoadsTrustedDishDetails() {
        SetmealDTO dto = validSetmeal();
        dto.setId(999L);
        dto.setStatus(1);
        SetmealDish submitted = dto.getSetmealDishes().get(0);
        submitted.setId(888L);
        submitted.setSetmealId(777L);
        submitted.setName("客户端伪造名称");
        submitted.setPrice(BigDecimal.ZERO);
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(1));
        doAnswer(invocation -> {
            Setmeal setmeal = invocation.getArgument(0);
            assertNull(setmeal.getId());
            assertEquals(0, setmeal.getStatus());
            setmeal.setId(101L);
            return null;
        }).when(setmealMapper).insert(any(Setmeal.class));

        setmealService.save(dto);

        ArgumentCaptor<List<SetmealDish>> captor = ArgumentCaptor.forClass(List.class);
        verify(setmealDishMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        SetmealDish saved = captor.getValue().get(0);
        assertNull(saved.getId());
        assertEquals(101L, saved.getSetmealId());
        assertEquals(20L, saved.getDishId());
        assertEquals("鸡蛋汤", saved.getName());
        assertEquals(new BigDecimal("12.50"), saved.getPrice());
        assertEquals(2, saved.getCopies());
    }

    @ParameterizedTest
    @MethodSource("invalidSetmeals")
    void saveRejectsInvalidBodyBeforeAnyWrite(SetmealDTO dto) {
        // 不同校验顺序都可以接受，只约束无效数据不得写入。
        lenient().when(categoryMapper.getById(11L)).thenReturn(validCategoryEntity());
        lenient().when(dishMapper.getById(20L)).thenReturn(actualDish(1));

        assertThrows(BaseException.class, () -> setmealService.save(dto));

        verify(setmealMapper, never()).insert(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    static Stream<SetmealDTO> invalidSetmeals() {
        List<Consumer<SetmealDTO>> invalidators = Arrays.asList(
                dto -> dto.setName(" "),
                dto -> dto.setPrice(null),
                dto -> dto.setPrice(BigDecimal.ZERO),
                dto -> dto.setPrice(new BigDecimal("-0.01")),
                dto -> dto.setPrice(new BigDecimal("100000000.00")),
                dto -> dto.setPrice(new BigDecimal("1.001")),
                dto -> dto.setImage(" "),
                dto -> dto.setName(String.join("", Collections.nCopies(33, "名"))),
                dto -> dto.setImage(String.join("", Collections.nCopies(256, "i"))),
                dto -> dto.setDescription(String.join("", Collections.nCopies(256, "描"))),
                dto -> dto.setCategoryId(null),
                dto -> dto.setStatus(2),
                dto -> dto.setSetmealDishes(Collections.emptyList()),
                dto -> dto.setSetmealDishes(null),
                dto -> dto.setSetmealDishes(Collections.singletonList(null)),
                dto -> dto.getSetmealDishes().get(0).setCopies(0),
                dto -> dto.getSetmealDishes().get(0).setCopies(null),
                dto -> dto.getSetmealDishes().get(0).setDishId(null),
                dto -> dto.setSetmealDishes(Arrays.asList(
                        SetmealDish.builder().dishId(20L).copies(1).build(),
                        SetmealDish.builder().dishId(20L).copies(2).build())));
        return Stream.concat(Stream.of((SetmealDTO) null), invalidators.stream().map(invalidator -> {
            SetmealDTO dto = validSetmeal();
            invalidator.accept(dto);
            return dto;
        }));
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,0"})
    void saveRejectsWrongTypeOrDisabledCategory(int type, int status) {
        when(categoryMapper.getById(11L)).thenReturn(Category.builder().type(type).status(status).build());

        assertThrows(BaseException.class, () -> setmealService.save(validSetmeal()));

        verify(setmealMapper, never()).insert(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void saveRejectsMissingCategory() {
        assertThrows(BaseException.class, () -> setmealService.save(validSetmeal()));
        verify(setmealMapper, never()).insert(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void saveRejectsMissingDish() {
        validCategory();
        assertThrows(BaseException.class, () -> setmealService.save(validSetmeal()));
        verify(setmealMapper, never()).insert(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    @ParameterizedTest
    @CsvSource({"0.01", "99999999.99", "20.000"})
    void saveAcceptsPriceAndTextAtDatabaseBoundaries(String price) {
        SetmealDTO dto = validSetmeal();
        dto.setPrice(new BigDecimal(price));
        dto.setName(String.join("", Collections.nCopies(32, "名")));
        dto.setImage(String.join("", Collections.nCopies(255, "i")));
        dto.setDescription(String.join("", Collections.nCopies(255, "描")));
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(1));

        setmealService.save(dto);

        verify(setmealMapper).insert(any(Setmeal.class));
        verify(setmealDishMapper).insertBatch(anyList());
    }

    @Test
    void saveDuplicateNameReturnsBusinessErrorWithoutInsertingAssociations() {
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(1));
        doThrow(new DuplicateKeyException("duplicate name")).when(setmealMapper).insert(any(Setmeal.class));

        BaseException failure = assertThrows(BaseException.class, () -> setmealService.save(validSetmeal()));

        assertEquals("套餐名称已存在", failure.getMessage());
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void getByIdReturnsCategoryAndAssociatedDishes() {
        SetmealVO setmeal = SetmealVO.builder().id(101L).categoryName("套餐分类").build();
        List<SetmealDish> dishes = Collections.singletonList(
                SetmealDish.builder().id(7L).setmealId(101L).dishId(20L).copies(2).build());
        when(setmealMapper.getById(101L)).thenReturn(setmeal);
        when(setmealDishMapper.getBySetmealId(101L)).thenReturn(dishes);

        SetmealVO result = setmealService.getById(101L);

        assertEquals("套餐分类", result.getCategoryName());
        assertEquals(dishes, result.getSetmealDishes());
    }

    @Test
    void getByIdRejectsMissingOrInvalidIdWithoutLoadingAssociations() {
        assertThrows(BaseException.class, () -> setmealService.getById(null));
        assertThrows(BaseException.class, () -> setmealService.getById(0L));
        verifyNoInteractions(setmealMapper);
        assertThrows(BaseException.class, () -> setmealService.getById(999L));
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReplacesAssociationsAndDoesNotChangeExistingStatus() {
        SetmealDTO dto = validSetmeal();
        dto.setId(101L);
        dto.setStatus(1);
        dto.getSetmealDishes().get(0).setId(999L);
        dto.getSetmealDishes().get(0).setSetmealId(888L);
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(0));

        setmealService.update(dto);

        ArgumentCaptor<Setmeal> setmealCaptor = ArgumentCaptor.forClass(Setmeal.class);
        ArgumentCaptor<List<SetmealDish>> dishCaptor = ArgumentCaptor.forClass(List.class);
        InOrder writes = inOrder(setmealMapper, setmealDishMapper);
        writes.verify(setmealMapper).getByIdForUpdate(101L);
        writes.verify(setmealMapper).update(setmealCaptor.capture());
        writes.verify(setmealDishMapper).deleteBySetmealId(101L);
        writes.verify(setmealDishMapper).insertBatch(dishCaptor.capture());
        assertEquals(101L, setmealCaptor.getValue().getId());
        assertNull(setmealCaptor.getValue().getStatus());
        SetmealDish saved = dishCaptor.getValue().get(0);
        assertNull(saved.getId());
        assertEquals(101L, saved.getSetmealId());
        assertEquals("鸡蛋汤", saved.getName());
        assertEquals(new BigDecimal("12.50"), saved.getPrice());
    }

    @Test
    void updateOnSaleSetmealRejectsStoppedDishBeforeAnyWrite() {
        SetmealDTO dto = validSetmeal();
        dto.setId(101L);
        dto.setStatus(0);
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(1).build());
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(0));

        assertThrows(BaseException.class, () -> setmealService.update(dto));

        verify(setmealMapper, never()).update(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void updateRejectsMissingSetmealBeforeAnyWrite() {
        SetmealDTO dto = validSetmeal();
        dto.setId(999L);

        assertThrows(BaseException.class, () -> setmealService.update(dto));

        verify(setmealMapper, never()).update(any(Setmeal.class));
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void updateDuplicateNameDoesNotDeleteExistingAssociations() {
        SetmealDTO dto = validSetmeal();
        dto.setId(101L);
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        validCategory();
        when(dishMapper.getById(20L)).thenReturn(actualDish(1));
        doThrow(new DuplicateKeyException("duplicate name")).when(setmealMapper).update(any(Setmeal.class));

        BaseException failure = assertThrows(BaseException.class, () -> setmealService.update(dto));

        assertEquals("套餐名称已存在", failure.getMessage());
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void startingSetmealChecksActualDishStatusAndOnlyUpdatesStatus() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealDishMapper.getBySetmealId(101L)).thenReturn(validSetmeal().getSetmealDishes());
        when(dishMapper.getById(20L)).thenReturn(actualDish(1));

        setmealService.startOrStop(1, 101L);

        ArgumentCaptor<Setmeal> captor = ArgumentCaptor.forClass(Setmeal.class);
        InOrder order = inOrder(setmealMapper, setmealDishMapper, dishMapper);
        order.verify(setmealMapper).getByIdForUpdate(101L);
        order.verify(setmealDishMapper).getBySetmealId(101L);
        order.verify(dishMapper).getById(20L);
        order.verify(setmealMapper).updateStatus(captor.capture());
        assertEquals(101L, captor.getValue().getId());
        assertEquals(1, captor.getValue().getStatus());
        assertNull(captor.getValue().getName());
        assertNull(captor.getValue().getPrice());
    }

    @Test
    void stoppingSetmealDoesNotRequireAssociatedDishesToBeAvailable() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(1).build());

        setmealService.startOrStop(0, 101L);

        ArgumentCaptor<Setmeal> captor = ArgumentCaptor.forClass(Setmeal.class);
        verify(setmealMapper).updateStatus(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verifyNoInteractions(dishMapper);
    }

    @Test
    void startingSetmealRejectsNoAssociatedDishes() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealDishMapper.getBySetmealId(101L)).thenReturn(Collections.emptyList());

        assertThrows(BaseException.class, () -> setmealService.startOrStop(1, 101L));

        verify(setmealMapper, never()).updateStatus(any(Setmeal.class));
    }

    @Test
    void startingSetmealRejectsDeletedDish() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealDishMapper.getBySetmealId(101L)).thenReturn(validSetmeal().getSetmealDishes());

        assertThrows(BaseException.class, () -> setmealService.startOrStop(1, 101L));

        verify(setmealMapper, never()).updateStatus(any(Setmeal.class));
    }

    @Test
    void startingSetmealRejectsStoppedDish() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealDishMapper.getBySetmealId(101L)).thenReturn(validSetmeal().getSetmealDishes());
        when(dishMapper.getById(20L)).thenReturn(actualDish(0));

        assertThrows(BaseException.class, () -> setmealService.startOrStop(1, 101L));

        verify(setmealMapper, never()).updateStatus(any(Setmeal.class));
    }

    @Test
    void statusChangeRejectsInvalidStatusAndMissingSetmeal() {
        assertThrows(BaseException.class, () -> setmealService.startOrStop(2, 101L));
        assertThrows(BaseException.class, () -> setmealService.startOrStop(null, 101L));
        assertThrows(BaseException.class, () -> setmealService.startOrStop(1, 0L));
        verifyNoInteractions(setmealMapper);
        assertThrows(BaseException.class, () -> setmealService.startOrStop(1, 999L));
        verify(setmealMapper, never()).updateStatus(any(Setmeal.class));
    }

    @Test
    void deleteBatchLocksIdsInAscendingOrderAndDeletesEachAssociationBeforeItsSetmealOnce() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealMapper.getByIdForUpdate(102L)).thenReturn(SetmealVO.builder().id(102L).status(0).build());

        setmealService.deleteBatch(Arrays.asList(102L, 101L, 102L));

        InOrder order = inOrder(setmealMapper, setmealDishMapper);
        order.verify(setmealMapper).getByIdForUpdate(101L);
        order.verify(setmealMapper).getByIdForUpdate(102L);
        order.verify(setmealDishMapper).deleteBySetmealId(101L);
        order.verify(setmealMapper).deleteById(101L);
        order.verify(setmealDishMapper).deleteBySetmealId(102L);
        order.verify(setmealMapper).deleteById(102L);
        verify(setmealMapper).deleteById(101L);
        verify(setmealDishMapper).deleteBySetmealId(101L);
    }

    @Test
    void deleteBatchRejectsOnSaleItemWithoutDeletingEarlierStoppedItem() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());
        when(setmealMapper.getByIdForUpdate(102L)).thenReturn(SetmealVO.builder().id(102L).status(1).build());

        assertThrows(BaseException.class, () -> setmealService.deleteBatch(Arrays.asList(101L, 102L)));

        verify(setmealMapper, never()).deleteById(anyLong());
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void deleteBatchRejectsMissingItemWithoutDeletingEarlierStoppedItem() {
        when(setmealMapper.getByIdForUpdate(101L)).thenReturn(SetmealVO.builder().id(101L).status(0).build());

        assertThrows(BaseException.class, () -> setmealService.deleteBatch(Arrays.asList(101L, 999L)));

        verify(setmealMapper, never()).deleteById(anyLong());
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void deleteBatchRejectsEmptyOrInvalidIds() {
        assertThrows(BaseException.class, () -> setmealService.deleteBatch(null));
        assertThrows(BaseException.class, () -> setmealService.deleteBatch(Collections.emptyList()));
        assertThrows(BaseException.class, () -> setmealService.deleteBatch(Collections.singletonList(null)));
        assertThrows(BaseException.class, () -> setmealService.deleteBatch(Collections.singletonList(0L)));
        verify(setmealMapper, never()).deleteById(anyLong());
        verifyNoInteractions(setmealDishMapper);
    }

    private static SetmealDTO validSetmeal() {
        SetmealDTO dto = new SetmealDTO();
        dto.setName("测试双人套餐");
        dto.setCategoryId(11L);
        dto.setPrice(new BigDecimal("20.00"));
        dto.setImage("http://localhost/setmeal.png");
        dto.setSetmealDishes(Collections.singletonList(SetmealDish.builder().dishId(20L).copies(2).build()));
        return dto;
    }

    private SetmealPageQueryDTO validQuery() {
        SetmealPageQueryDTO query = new SetmealPageQueryDTO();
        query.setPage(2);
        query.setPageSize(5);
        return query;
    }

    private Category validCategoryEntity() {
        return Category.builder().id(11L).type(2).status(1).build();
    }

    private void validCategory() {
        when(categoryMapper.getById(11L)).thenReturn(validCategoryEntity());
    }

    private DishVO actualDish(int status) {
        return DishVO.builder().id(20L).name("鸡蛋汤").price(new BigDecimal("12.50")).status(status).build();
    }
}
