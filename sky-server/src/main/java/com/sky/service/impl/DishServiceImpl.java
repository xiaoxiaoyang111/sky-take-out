package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.DishDTO;
import com.sky.constant.StatusConstant;
import com.sky.constant.MessageConstant;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Category;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    @Transactional
    public void save(DishDTO dishDTO) {
        validateDish(dishDTO);

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dish.setId(null);
        if (dish.getStatus() == null) {
            dish.setStatus(StatusConstant.ENABLE);
        }
        dishMapper.insert(dish);

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            for (DishFlavor flavor : flavors) {
                flavor.setId(null);
                flavor.setDishId(dish.getId());
            }
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    private void validateDish(DishDTO dishDTO) {
        if (dishDTO == null || dishDTO.getCategoryId() == null || dishDTO.getCategoryId() <= 0 ||
                dishDTO.getName() == null || dishDTO.getName().trim().isEmpty() ||
                dishDTO.getImage() == null || dishDTO.getImage().trim().isEmpty() ||
                dishDTO.getPrice() == null || dishDTO.getPrice().signum() < 0) {
            throw new BaseException("菜品名称、分类、价格和图片不能为空，价格不能为负数");
        }
        if (dishDTO.getStatus() != null && dishDTO.getStatus() != 0 && dishDTO.getStatus() != 1) {
            throw new BaseException("菜品状态不正确");
        }
        Category category = categoryMapper.getById(dishDTO.getCategoryId());
        if (category == null || !Integer.valueOf(1).equals(category.getType()) ||
                !StatusConstant.ENABLE.equals(category.getStatus())) {
            throw new BaseException("请选择有效的菜品分类");
        }
        if (dishDTO.getFlavors() != null) {
            for (DishFlavor flavor : dishDTO.getFlavors()) {
                if (flavor == null || flavor.getName() == null || flavor.getName().trim().isEmpty() ||
                        flavor.getValue() == null || flavor.getValue().trim().isEmpty()) {
                    throw new BaseException("菜品口味名称和值不能为空");
                }
            }
        }

    }

    @Override
    public DishVO getById(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }
        DishVO dish = dishMapper.getById(id);
        if (dish == null) {
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }
        dish.setFlavors(dishFlavorMapper.getByDishId(id));
        return dish;
    }

    @Override
    @Transactional
    public void update(DishDTO dishDTO) {
        if (dishDTO == null || dishDTO.getId() == null || dishDTO.getId() <= 0 ||
                dishMapper.getById(dishDTO.getId()) == null) {
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }
        validateDish(dishDTO);

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        dishFlavorMapper.deleteByDishId(dish.getId());
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            for (DishFlavor flavor : flavors) {
                flavor.setId(null);
                flavor.setDishId(dish.getId());
            }
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.contains(null) || ids.stream().anyMatch(id -> id <= 0)) {
            throw new BaseException("请选择要删除的菜品");
        }
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(ids));
        for (Long id : uniqueIds) {
            DishVO dish = dishMapper.getById(id);
            if (dish == null) {
                throw new BaseException(MessageConstant.DISH_NOT_FOUND);
            }
            if (StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new BaseException(MessageConstant.DISH_ON_SALE);
            }
            if (setmealMapper.countByDishId(id) > 0) {
                throw new BaseException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
            }
        }
        for (Long id : uniqueIds) {
            dishFlavorMapper.deleteByDishId(id);
            dishMapper.deleteById(id);
        }
    }

    @Override
    public List<DishVO> listByCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new BaseException("菜品分类不正确");
        }
        return dishMapper.listByCategoryId(categoryId);
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BaseException("菜品状态不正确");
        }
        if (id == null || id <= 0 || dishMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }
        dishMapper.updateStatus(Dish.builder().id(id).status(status).build());
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        // getPage():第几页,getPageSize():每页最多返回多少条数据
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }
}
