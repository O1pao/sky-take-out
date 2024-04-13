package com.sky.mapper;

import com.sky.dto.DishDTO;
import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    /**
     * 批量插入口味
     * @param flavors
     */
    void insertBath(List<DishFlavor> flavors);

    /**
     * 根据菜品id删除口味
     * @param dishIds
     */
    void deleteByIds(List<Long> dishIds);

    @Delete("delete from dish_flavor where dish_id = #{id}")
    void deleteById(Long id);
    /**
     * 根据菜品id获取口味
     * @param dishId
     * @return
     */
    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);


}
