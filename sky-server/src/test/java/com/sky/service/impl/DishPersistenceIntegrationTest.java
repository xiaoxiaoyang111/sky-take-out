package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.DishDTO;
import com.sky.entity.DishFlavor;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SKY_DB_INTEGRATION", matches = "true")
class DishPersistenceIntegrationTest {

    @Autowired
    private DishService dishService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void dishAndFlavorsAreSavedTogetherAndFailedFlavorRollsBackDish() {
        String goodName = "CodexDish_" + UUID.randomUUID().toString().substring(0, 8);
        String badName = "CodexRollback_" + UUID.randomUUID().toString().substring(0, 8);
        BaseContext.setCurrentId(1L);
        try {
            DishDTO good = dish(goodName, "[\"Mild\",\"Hot\"]");
            dishService.save(good);

            Integer dishCount = jdbcTemplate.queryForObject(
                    "select count(*) from dish where name = ?", Integer.class, goodName);
            Integer flavorCount = jdbcTemplate.queryForObject(
                    "select count(*) from dish_flavor where dish_id = (select id from dish where name = ?)",
                    Integer.class, goodName);
            Long createUser = jdbcTemplate.queryForObject(
                    "select create_user from dish where name = ?", Long.class, goodName);
            assertEquals(1, dishCount);
            assertEquals(1, flavorCount);
            assertEquals(1L, createUser);

            Long dishId = jdbcTemplate.queryForObject(
                    "select id from dish where name = ?", Long.class, goodName);
            DishVO loaded = dishService.getById(dishId);
            assertEquals(goodName, loaded.getName());
            assertEquals("酒水饮料", loaded.getCategoryName());
            assertEquals(1, loaded.getFlavors().size());
            assertEquals(dishId, loaded.getFlavors().get(0).getDishId());
            assertEquals("[\"Mild\",\"Hot\"]", loaded.getFlavors().get(0).getValue());

            DishDTO bad = dish(badName, String.join("", Collections.nCopies(300, "x")));
            assertThrows(RuntimeException.class, () -> dishService.save(bad));
            Integer rolledBackCount = jdbcTemplate.queryForObject(
                    "select count(*) from dish where name = ?", Integer.class, badName);
            assertEquals(0, rolledBackCount);
        } finally {
            jdbcTemplate.update("delete from dish_flavor where dish_id in (select id from dish where name in (?, ?))",
                    goodName, badName);
            jdbcTemplate.update("delete from dish where name in (?, ?)", goodName, badName);
        }
    }

    @Test
    void getByIdReturnsEmptyFlavorListAndRejectsMissingId() {
        String name = "CodexNoFlavor_" + UUID.randomUUID().toString().substring(0, 8);
        BaseContext.setCurrentId(1L);
        try {
            DishDTO dto = dish(name, "unused");
            dto.setFlavors(Collections.emptyList());
            dishService.save(dto);
            Long id = jdbcTemplate.queryForObject("select id from dish where name = ?", Long.class, name);

            DishVO loaded = dishService.getById(id);
            assertEquals(name, loaded.getName());
            assertTrue(loaded.getFlavors().isEmpty());
            assertThrows(RuntimeException.class, () -> dishService.getById(Long.MAX_VALUE));
        } finally {
            jdbcTemplate.update("delete from dish where name = ?", name);
        }
    }

    @Test
    void updateReplacesAndClearsFlavorsAndRollsBackOnFailedInsert() {
        String originalName = "CodexUpdate_" + UUID.randomUUID().toString().substring(0, 8);
        String updatedName = originalName + "_new";
        BaseContext.setCurrentId(1L);
        Long id = null;
        try {
            dishService.save(dish(originalName, "[\"Mild\"]"));
            id = jdbcTemplate.queryForObject("select id from dish where name = ?", Long.class, originalName);

            DishDTO update = dish(updatedName, "[\"Hot\"]");
            update.setId(id);
            update.getFlavors().get(0).setId(999999L);
            update.getFlavors().get(0).setDishId(999999L);
            dishService.update(update);

            DishVO loaded = dishService.getById(id);
            assertEquals(updatedName, loaded.getName());
            assertEquals(1, loaded.getFlavors().size());
            assertEquals(id, loaded.getFlavors().get(0).getDishId());
            assertEquals("[\"Hot\"]", loaded.getFlavors().get(0).getValue());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "select update_user from dish where id = ?", Long.class, id));

            DishDTO failing = dish(originalName, String.join("", Collections.nCopies(300, "x")));
            failing.setId(id);
            assertThrows(RuntimeException.class, () -> dishService.update(failing));
            DishVO afterFailure = dishService.getById(id);
            assertEquals(updatedName, afterFailure.getName());
            assertEquals("[\"Hot\"]", afterFailure.getFlavors().get(0).getValue());

            update.setFlavors(Collections.emptyList());
            dishService.update(update);
            assertTrue(dishService.getById(id).getFlavors().isEmpty());
        } finally {
            if (id != null) {
                jdbcTemplate.update("delete from dish_flavor where dish_id = ?", id);
                jdbcTemplate.update("delete from dish where id = ?", id);
            }
        }
    }

    private DishDTO dish(String name, String flavorValue) {
        DishDTO dto = new DishDTO();
        dto.setName(name);
        dto.setCategoryId(11L);
        dto.setPrice(new BigDecimal("12.50"));
        dto.setImage("http://localhost:8080/images/test.png");
        dto.setFlavors(Collections.singletonList(
                DishFlavor.builder().name("Spice").value(flavorValue).build()));
        return dto;
    }
}
