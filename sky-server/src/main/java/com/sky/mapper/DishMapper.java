package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.annotation.AutoFill;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);

    DishVO getById(Long id);

    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    @AutoFill(OperationType.UPDATE)
    void updateStatus(Dish dish);

    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);

    List<DishVO> listByCategoryId(Long categoryId);

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO 查询条件
     * @return 菜品分页数据
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

}
