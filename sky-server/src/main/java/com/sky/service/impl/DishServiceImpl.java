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

import java.beans.beancontext.BeanContext;
import java.math.MathContext;
import java.util.ArrayList;
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
        dishMapper.deleteByIds(ids);
        // 删除菜品关联的口味数据
        dishFlavorMapper.deleteByIds(ids);
    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Override
    public DishVO getById(Long id) {
        DishVO dishVO = new DishVO();
        // 查询菜品信息
        Dish dish = dishMapper.getById(id);
        // 查询口味信息
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        // 拷贝属性
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 根据categoryId查询菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> getByCategoryId(Long categoryId) {
        // 查询菜品信息
        List<Dish> dishList = dishMapper.getByCategoryId(categoryId);
        return dishList;
    }

    /**
     * 菜品起售、停售
     * @param status
     */
    @Override
    public void changeStatus(Integer status, Long id) {
        // 调用Mapper层
        dishMapper.changeStatus(status, id);
    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    @Override
    @Transactional
    public void update(DishDTO dishDTO) {
        // 创建一个dish对象
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        // 修改dish表
        dishMapper.update(dish);
        // 删除原有的口味数据
        dishFlavorMapper.deleteById(dishDTO.getId());
        // 重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0){
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            // 插入n条口味
            dishFlavorMapper.insertBath(flavors);
        }
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        // 获取到对应分类的菜品列表
        List<Dish> dishes = dishMapper.getByCategoryId(dish.getCategoryId());

        // 要返回的数据
        List<DishVO> dishVOList = new ArrayList<>();

        dishes.forEach(d -> {
            // 如果未启售，则不添加进列表中
            if (d.getStatus() == StatusConstant.DISABLE)
                return;
            DishVO dishVO = new DishVO();
            // 拷贝数据到dishVO
            BeanUtils.copyProperties(d, dishVO);

            // 查询口味信息
            List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(d.getId());
            dishVO.setFlavors(dishFlavors);

            dishVOList.add(dishVO);
        });

        return dishVOList;
    }
}
