package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.SetmealDish;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(SetmealPersistenceIntegrationTest.SqlFailureConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SKY_DB_INTEGRATION", matches = "true")
class SetmealPersistenceIntegrationTest {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RelationInsertFailureInterceptor insertFailure;

    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> dishIds = new ArrayList<>();
    private String prefix;
    private Long setmealCategoryId;
    private Long dishCategoryId;
    private Long firstDishId;
    private Long secondDishId;

    @BeforeEach
    void createIndependentFixtures() {
        prefix = "SmIT_" + UUID.randomUUID().toString().substring(0, 8);
        BaseContext.setCurrentId(1L);
        setmealCategoryId = createCategory(prefix + "_combo", 2);
        dishCategoryId = createCategory(prefix + "_dishes", 1);
        firstDishId = createDish(prefix + "_dish1", "12.50");
        secondDishId = createDish(prefix + "_dish2", "18.00");
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        insertFailure.failNext.set(false);
        BaseContext.removeCurrentId();
        for (Long categoryId : categoryIds) {
            jdbcTemplate.update("delete from setmeal_dish where setmeal_id in " +
                    "(select id from setmeal where category_id = ?)", categoryId);
            jdbcTemplate.update("delete from setmeal where category_id = ?", categoryId);
        }
        for (Long dishId : dishIds) {
            jdbcTemplate.update("delete from dish where id = ?", dishId);
        }
        for (Long categoryId : categoryIds) {
            jdbcTemplate.update("delete from category where id = ?", categoryId);
        }
    }

    @Test
    void saveUsesGeneratedIdsDatabaseDishValuesAndAuditFields() {
        SetmealDTO dto = setmeal(prefix + "_save", firstDishId);
        dto.setId(Long.MAX_VALUE);
        dto.setStatus(1);
        SetmealDish suppliedDish = dto.getSetmealDishes().get(0);
        suppliedDish.setId(Long.MAX_VALUE);
        suppliedDish.setSetmealId(Long.MAX_VALUE);
        suppliedDish.setName("Untrusted name");
        suppliedDish.setPrice(new BigDecimal("0.01"));

        Long id = saveAndGetId(dto);
        SetmealVO loaded = setmealService.getById(id);
        assertNotEquals(Long.MAX_VALUE, id);
        assertEquals(0, loaded.getStatus());
        assertEquals(dto.getName(), loaded.getName());
        assertEquals(prefix + "_combo", loaded.getCategoryName());
        assertEquals(1, loaded.getSetmealDishes().size());
        SetmealDish storedDish = loaded.getSetmealDishes().get(0);
        assertNotNull(storedDish.getId());
        assertNotEquals(Long.MAX_VALUE, storedDish.getId());
        assertEquals(id, storedDish.getSetmealId());
        assertEquals(firstDishId, storedDish.getDishId());
        assertEquals(prefix + "_dish1", storedDish.getName());
        assertEquals(new BigDecimal("12.50"), storedDish.getPrice());
        assertEquals(2, storedDish.getCopies());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select create_user from setmeal where id = ?", Long.class, id));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select update_user from setmeal where id = ?", Long.class, id));
        assertNotNull(jdbcTemplate.queryForObject(
                "select create_time from setmeal where id = ?", java.sql.Timestamp.class, id));
        assertNotNull(loaded.getUpdateTime());
        assertThrows(RuntimeException.class, () -> setmealService.getById(Long.MAX_VALUE));
    }

    @Test
    void pageFiltersNameCategoryAndStatusAndKeepsTotalAcrossPages() {
        Long olderId = saveAndGetId(setmeal(prefix + "_hit1", firstDishId));
        Long newerId = saveAndGetId(setmeal(prefix + "_hit2", firstDishId));
        Long enabledId = saveAndGetId(setmeal(prefix + "_hit3", firstDishId));
        setmealService.startOrStop(1, enabledId);
        saveAndGetId(setmeal(prefix + "_other", firstDishId));
        SetmealDTO otherCategory = setmeal(prefix + "_hit4", firstDishId);
        otherCategory.setCategoryId(createCategory(prefix + "_combo2", 2));
        saveAndGetId(otherCategory);
        // 同一创建时间时仍应按主键倒序，保证翻页顺序稳定。
        jdbcTemplate.update("update setmeal set create_time = '2024-01-01 12:00:00' where id in (?, ?)",
                olderId, newerId);

        SetmealPageQueryDTO query = new SetmealPageQueryDTO();
        query.setPage(1);
        query.setPageSize(1);
        query.setName(prefix + "_hit");
        query.setCategoryId(setmealCategoryId);
        query.setStatus(0);
        PageResult firstPage = setmealService.pageQuery(query);
        assertEquals(2L, firstPage.getTotal());
        assertEquals(1, firstPage.getRecords().size());
        assertEquals(newerId, ((SetmealVO) firstPage.getRecords().get(0)).getId());
        assertEquals(prefix + "_combo", ((SetmealVO) firstPage.getRecords().get(0)).getCategoryName());

        query.setPage(2);
        PageResult secondPage = setmealService.pageQuery(query);
        assertEquals(2L, secondPage.getTotal());
        assertEquals(olderId, ((SetmealVO) secondPage.getRecords().get(0)).getId());

        query.setPage(3);
        PageResult beyondLastPage = setmealService.pageQuery(query);
        assertEquals(2L, beyondLastPage.getTotal());
        assertTrue(beyondLastPage.getRecords().isEmpty());

        query.setPage(1);
        query.setStatus(1);
        PageResult enabled = setmealService.pageQuery(query);
        assertEquals(1L, enabled.getTotal());
        assertEquals(enabledId, ((SetmealVO) enabled.getRecords().get(0)).getId());

        query.setName(prefix + "_missing");
        PageResult missing = setmealService.pageQuery(query);
        assertEquals(0L, missing.getTotal());
        assertTrue(missing.getRecords().isEmpty());
    }

    @Test
    void updateReplacesRelationsPreservesPrimaryKeyAndIgnoresSubmittedStatus() {
        Long id = saveAndGetId(setmeal(prefix + "_before", firstDishId));
        Long previousRelationId = setmealService.getById(id).getSetmealDishes().get(0).getId();
        SetmealDTO update = setmeal(prefix + "_after", secondDishId);
        update.setId(id);
        update.setStatus(1);
        update.getSetmealDishes().get(0).setId(previousRelationId);
        update.getSetmealDishes().get(0).setSetmealId(Long.MAX_VALUE);
        BaseContext.setCurrentId(2L);

        setmealService.update(update);

        SetmealVO loaded = setmealService.getById(id);
        assertEquals(id, loaded.getId());
        assertEquals(update.getName(), loaded.getName());
        assertEquals(0, loaded.getStatus());
        assertEquals(1, loaded.getSetmealDishes().size());
        SetmealDish relation = loaded.getSetmealDishes().get(0);
        assertEquals(id, relation.getSetmealId());
        assertEquals(secondDishId, relation.getDishId());
        assertNotEquals(previousRelationId, relation.getId());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from setmeal_dish where id = ?", Integer.class, previousRelationId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select create_user from setmeal where id = ?", Long.class, id));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "select update_user from setmeal where id = ?", Long.class, id));
    }

    @Test
    void actualRelationInsertSqlFailureRollsBackNewSetmeal() {
        SetmealDTO dto = setmeal(prefix + "_badSave", firstDishId);
        insertFailure.failNext.set(true);

        assertThrows(DataAccessException.class, () -> setmealService.save(dto));

        assertFalse(insertFailure.failNext.get(), "故障必须发生在真实关联 INSERT 阶段");
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from setmeal where name = ?", Integer.class, dto.getName()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from setmeal_dish where dish_id = ?", Integer.class, firstDishId));
    }

    @Test
    void actualRelationInsertSqlFailureRestoresPreviousSetmealAndRelations() {
        Long id = saveAndGetId(setmeal(prefix + "_rollback", firstDishId));
        SetmealVO before = setmealService.getById(id);
        SetmealDTO update = setmeal(prefix + "_failed", secondDishId);
        update.setId(id);
        update.setPrice(new BigDecimal("66.00"));
        BaseContext.setCurrentId(2L);
        insertFailure.failNext.set(true);

        assertThrows(DataAccessException.class, () -> setmealService.update(update));

        assertFalse(insertFailure.failNext.get());
        SetmealVO after = setmealService.getById(id);
        assertEquals(before, after);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select update_user from setmeal where id = ?", Long.class, id));
    }

    @Test
    void statusAndBatchDeletionDoNotPartiallyDeleteMixedBatch() {
        Long stoppedId = saveAndGetId(setmeal(prefix + "_stop", firstDishId));
        Long enabledId = saveAndGetId(setmeal(prefix + "_sale", secondDishId));
        BaseContext.setCurrentId(2L);
        setmealService.startOrStop(1, enabledId);
        assertEquals(1, setmealService.getById(enabledId).getStatus());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "select update_user from setmeal where id = ?", Long.class, enabledId));

        assertThrows(RuntimeException.class,
                () -> setmealService.deleteBatch(Collections.singletonList(enabledId)));
        assertThrows(RuntimeException.class,
                () -> setmealService.deleteBatch(Arrays.asList(stoppedId, enabledId)));
        assertThrows(RuntimeException.class,
                () -> setmealService.deleteBatch(Arrays.asList(stoppedId, Long.MAX_VALUE)));
        assertEquals(1, setmealService.getById(stoppedId).getSetmealDishes().size());
        assertEquals(1, setmealService.getById(enabledId).getSetmealDishes().size());

        setmealService.startOrStop(0, enabledId);
        setmealService.deleteBatch(Arrays.asList(stoppedId, enabledId, stoppedId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from setmeal where id in (?, ?)", Integer.class, stoppedId, enabledId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from setmeal_dish where setmeal_id in (?, ?)",
                Integer.class, stoppedId, enabledId));
    }

    @Test
    void enablingRejectsStoppedMissingAndEmptyDishAssociations() {
        Long id = saveAndGetId(setmeal(prefix + "_blocked", firstDishId));
        jdbcTemplate.update("update dish set status = 0 where id = ?", firstDishId);
        assertThrows(RuntimeException.class, () -> setmealService.startOrStop(1, id));
        assertEquals(0, setmealService.getById(id).getStatus());

        jdbcTemplate.update("delete from dish where id = ?", firstDishId);
        assertThrows(RuntimeException.class, () -> setmealService.startOrStop(1, id));
        assertEquals(0, setmealService.getById(id).getStatus());

        jdbcTemplate.update("delete from setmeal_dish where setmeal_id = ?", id);
        assertThrows(RuntimeException.class, () -> setmealService.startOrStop(1, id));
        assertEquals(0, setmealService.getById(id).getStatus());
    }

    private Long createCategory(String name, int type) {
        jdbcTemplate.update("insert into category (name, type, sort, status, create_time, update_time, " +
                        "create_user, update_user) values (?, ?, 1, 1, now(), now(), 1, 1)", name, type);
        Long id = jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
        categoryIds.add(id);
        return id;
    }

    private Long createDish(String name, String price) {
        jdbcTemplate.update("insert into dish (name, category_id, price, image, description, status, " +
                        "create_time, update_time, create_user, update_user) " +
                        "values (?, ?, ?, 'http://localhost/test.png', 'integration fixture', 1, now(), now(), 1, 1)",
                name, dishCategoryId, new BigDecimal(price));
        Long id = jdbcTemplate.queryForObject("select id from dish where name = ?", Long.class, name);
        dishIds.add(id);
        return id;
    }

    private SetmealDTO setmeal(String name, Long dishId) {
        SetmealDTO dto = new SetmealDTO();
        dto.setName(name);
        dto.setCategoryId(setmealCategoryId);
        dto.setPrice(new BigDecimal("28.50"));
        dto.setImage("http://localhost/setmeal-test.png");
        dto.setDescription("integration fixture");
        dto.setSetmealDishes(Collections.singletonList(
                SetmealDish.builder().dishId(dishId).copies(2).build()));
        return dto;
    }

    private Long saveAndGetId(SetmealDTO dto) {
        setmealService.save(dto);
        return jdbcTemplate.queryForObject("select id from setmeal where name = ?", Long.class, dto.getName());
    }

    @TestConfiguration
    static class SqlFailureConfiguration {
        @Bean
        RelationInsertFailureInterceptor relationInsertFailureInterceptor() {
            return new RelationInsertFailureInterceptor();
        }
    }

    /** 测试不包外层事务；故障发生后由真实 Service 的事务恢复数据库。 */
    @Intercepts(@Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}))
    static class RelationInsertFailureInterceptor implements Interceptor {
        private final AtomicBoolean failNext = new AtomicBoolean();

        @Override
        @SuppressWarnings("unchecked")
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if ("com.sky.mapper.SetmealDishMapper.insertBatch".equals(statement.getId()) &&
                    failNext.compareAndSet(true, false)) {
                Map<String, Object> parameters = (Map<String, Object>) invocation.getArgs()[1];
                List<SetmealDish> dishes = (List<SetmealDish>) parameters.get("setmealDishes");
                // 保留真实 MyBatis INSERT，使 MySQL 的字段长度限制触发 SQL 异常。
                dishes.get(0).setName(String.join("", Collections.nCopies(300, "x")));
            }
            return invocation.proceed();
        }
    }
}
