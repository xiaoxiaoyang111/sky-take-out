package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private DishMapper dishMapper;

    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        if (setmealPageQueryDTO == null || setmealPageQueryDTO.getPage() <= 0 ||
                setmealPageQueryDTO.getPageSize() <= 0) {
            throw new BaseException("页码和每页记录数必须为正整数");
        }
        if (setmealPageQueryDTO.getCategoryId() != null && setmealPageQueryDTO.getCategoryId() <= 0) {
            throw new BaseException("套餐分类不正确");
        }
        validateStatus(setmealPageQueryDTO.getStatus());
        try {
            PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
            Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
            return new PageResult(page.getTotal(), page.getResult());
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        List<SetmealDish> dishes = validateAndBuildDishes(setmealDTO, false);
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setId(null);
        // 新增套餐统一从停售开始，通过起售接口校验后再上架。
        setmeal.setStatus(StatusConstant.DISABLE);
        try {
            setmealMapper.insert(setmeal);
        } catch (DuplicateKeyException ex) {
            throw new BaseException("套餐名称已存在");
        }
        insertDishes(setmeal.getId(), dishes);
    }

    @Override
    public SetmealVO getById(Long id) {
        SetmealVO setmeal = requireSetmeal(id);
        setmeal.setSetmealDishes(setmealDishMapper.getBySetmealId(id));
        return setmeal;
    }

    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        SetmealVO current = requireSetmealForUpdate(setmealDTO == null ? null : setmealDTO.getId());
        List<SetmealDish> dishes = validateAndBuildDishes(setmealDTO,
                StatusConstant.ENABLE.equals(current.getStatus()));
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 修改信息不改变售卖状态，避免绕过起售校验。
        setmeal.setStatus(null);
        try {
            setmealMapper.update(setmeal);
        } catch (DuplicateKeyException ex) {
            throw new BaseException("套餐名称已存在");
        }
        setmealDishMapper.deleteBySetmealId(setmeal.getId());
        insertDishes(setmeal.getId(), dishes);
    }

    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        if (status == null) {
            throw new BaseException("套餐状态不正确");
        }
        validateStatus(status);
        requireSetmealForUpdate(id);
        if (StatusConstant.ENABLE.equals(status)) {
            List<SetmealDish> dishes = setmealDishMapper.getBySetmealId(id);
            if (dishes.isEmpty()) {
                throw new SetmealEnableFailedException("套餐未包含菜品，无法起售");
            }
            for (SetmealDish item : dishes) {
                DishVO dish = dishMapper.getById(item.getDishId());
                if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }
        setmealMapper.updateStatus(Setmeal.builder().id(id).status(status).build());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.contains(null) || ids.stream().anyMatch(id -> id <= 0)) {
            throw new BaseException("请选择要删除的套餐");
        }
        // 按固定顺序锁定套餐，避免并发批量删除因锁顺序相反而互相等待。
        List<Long> uniqueIds = new ArrayList<>(new TreeSet<>(ids));
        // 全部校验通过后才删除，避免一批中只删除部分套餐。
        for (Long id : uniqueIds) {
            if (StatusConstant.ENABLE.equals(requireSetmealForUpdate(id).getStatus())) {
                throw new BaseException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        for (Long id : uniqueIds) {
            setmealDishMapper.deleteBySetmealId(id);
            setmealMapper.deleteById(id);
        }
    }

    private SetmealVO requireSetmeal(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException("套餐不存在");
        }
        SetmealVO setmeal = setmealMapper.getById(id);
        if (setmeal == null) {
            throw new BaseException("套餐不存在");
        }
        return setmeal;
    }

    private SetmealVO requireSetmealForUpdate(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException("套餐不存在");
        }
        // 写入口在事务中持有行锁，状态校验与后续修改使用同一份最新数据。
        SetmealVO setmeal = setmealMapper.getByIdForUpdate(id);
        if (setmeal == null) {
            throw new BaseException("套餐不存在");
        }
        return setmeal;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BaseException("套餐状态不正确");
        }
    }

    private List<SetmealDish> validateAndBuildDishes(SetmealDTO dto, boolean requireEnabled) {
        if (dto == null || dto.getCategoryId() == null || dto.getCategoryId() <= 0 ||
                dto.getName() == null || dto.getName().trim().isEmpty() ||
                dto.getImage() == null || dto.getImage().trim().isEmpty() ||
                dto.getPrice() == null || dto.getPrice().signum() <= 0) {
            throw new BaseException("套餐名称、分类、价格和图片不能为空，价格必须大于零");
        }
        if (dto.getName().length() > 32 || dto.getImage().length() > 255 ||
                (dto.getDescription() != null && dto.getDescription().length() > 255)) {
            throw new BaseException("套餐名称最多32个字符，图片地址和描述最多255个字符");
        }
        if (dto.getPrice().compareTo(new BigDecimal("99999999.99")) > 0 ||
                dto.getPrice().stripTrailingZeros().scale() > 2) {
            throw new BaseException("套餐价格超出范围或小数位超过两位");
        }
        validateStatus(dto.getStatus());
        Category category = categoryMapper.getById(dto.getCategoryId());
        if (category == null || !Integer.valueOf(2).equals(category.getType()) ||
                !StatusConstant.ENABLE.equals(category.getStatus())) {
            throw new BaseException("请选择有效的套餐分类");
        }
        if (dto.getSetmealDishes() == null || dto.getSetmealDishes().isEmpty()) {
            throw new BaseException("套餐必须包含菜品");
        }
        Set<Long> dishIds = new HashSet<>();
        List<SetmealDish> dishes = new ArrayList<>();
        for (SetmealDish item : dto.getSetmealDishes()) {
            if (item == null || item.getDishId() == null || item.getDishId() <= 0 ||
                    item.getCopies() == null || item.getCopies() <= 0) {
                throw new BaseException("请选择有效菜品，菜品份数必须为正整数");
            }
            if (!dishIds.add(item.getDishId())) {
                throw new BaseException("套餐中不能重复添加同一菜品");
            }
            DishVO dish = dishMapper.getById(item.getDishId());
            if (dish == null) {
                throw new BaseException(MessageConstant.DISH_NOT_FOUND);
            }
            if (requireEnabled && !StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            }
            // 冗余名称、价格以数据库中的菜品为准，关联主键由数据库生成。
            dishes.add(SetmealDish.builder().dishId(dish.getId()).name(dish.getName())
                    .price(dish.getPrice()).copies(item.getCopies()).build());
        }
        return dishes;
    }

    private void insertDishes(Long setmealId, List<SetmealDish> dishes) {
        for (SetmealDish dish : dishes) {
            dish.setSetmealId(setmealId);
        }
        setmealDishMapper.insertBatch(dishes);
    }
}
