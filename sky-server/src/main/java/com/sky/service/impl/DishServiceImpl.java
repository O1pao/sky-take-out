package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.MathContext;
import java.util.List;

@Service
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品以及口味
     * @param dishDTO
     */
    @Override
    @Transactional
    public void addDishWithFlavor(DishDTO dishDTO) {
        // 新建一个dish对象
        Dish dish = new Dish();
        // 将前端转来的dishDTO数据拷贝给dish对象
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);

        // 获取insert语句生成的主键值
        Long dishId = dish.getId();

        // 向口味表插入n条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0){
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            dishFlavorMapper.insertBath(flavors);
        }
    }

    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        // 开始分页查询
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        // 查询菜品信息并分页
        List<DishVO> dishVOList = dishMapper.pageQuery(dishPageQueryDTO);
        // 将查询结果转换为page对象
        Page<DishVO> page = (Page<DishVO>) dishVOList;
        // 封装成PageResult对象返回前端
        PageResult pageResult = new PageResult(page.getTotal(), page.getResult());
        return pageResult;
    }

    /**
     * 批量删除菜品
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 判断当前菜品是否能被删除--是否存在起售中的
        ids.forEach(id ->{
            Dish dish = dishMapper.getById(id);

            // 当前菜品处于起售中
            if (dish.getStatus() == StatusConstant.ENABLE)
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        });
        // 判断是否有被套餐关联的菜品
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        // 若存在被套餐关联的菜品，抛出异常
        if (setmealIds != null && setmealIds.size() > 0)
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        // 删除菜品
        ids.forEach(id -> {
            dishMapper.deleteById(id);
            // 删除菜品关联的口味数据
            dishFlavorMapper.deleteById(id);
        });
    }
}
