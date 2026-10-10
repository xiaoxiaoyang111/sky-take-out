package com.sky.service;

import com.sky.dto.SetmealPageQueryDTO;
import com.sky.dto.SetmealDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    /**
     * 分页查询套餐
     * @param setmealPageQueryDTO
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    void save(SetmealDTO setmealDTO);

    SetmealVO getById(Long id);

    void update(SetmealDTO setmealDTO);

    void startOrStop(Integer status, Long id);

    void deleteBatch(List<Long> ids);
}
