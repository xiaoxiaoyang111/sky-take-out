package com.sky.service;

import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.DishDTO;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import java.util.List;

public interface DishService {
    void save(DishDTO dishDTO);

    DishVO getById(Long id);

    void update(DishDTO dishDTO);

    void deleteBatch(List<Long> ids);

    List<DishVO> listByCategoryId(Long categoryId);

    List<DishVO> list(Long categoryId, String name);

    void startOrStop(Integer status, Long id);

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO 查询条件
     * @return 分页结果
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);
}
