package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import io.swagger.models.auth.In;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 分类管理分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        // 开始分页查询
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());
        // 查询分类信息并分页
        List<Category> categoryList = categoryMapper.pageQuery(categoryPageQueryDTO);
        // 将其转换为Page对象
        Page<Category> categoryPage = (Page<Category>) categoryList;
        PageResult pageResult = new PageResult(categoryPage.getTotal(), categoryPage.getResult());
        return pageResult;
    }

    /**
     * 修改分类信息
     * @param categoryDTO
     */
    @Override
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        // 从categoryDTO拷贝属性到category上
        BeanUtils.copyProperties(categoryDTO, category);
        // 修改最后更新时间和更新人
        category.setUpdateTime(LocalDateTime.now());
        category.setUpdateUser(BaseContext.getCurrentId());
        // 执行更新操作
        categoryMapper.update(category);
    }

    /**
     * 根据类型查询分类
     * @return
     */
    @Override
    public List<Category> getListByType(Integer type) {
        List<Category> categories = categoryMapper.selectByType(type);
        return categories;
    }

    /**
     * 新增分类
     * @param categoryDTO
     */
    @Override
    public void add(CategoryDTO categoryDTO) {
        Category category = new Category();
        // 拷贝categoryDTO的属性到category上
        BeanUtils.copyProperties(categoryDTO, category);
        // 设置更新时间，创建时间，创建人id， 修改人id
        category.setStatus(StatusConstant.DISABLE);
        category.setUpdateTime(LocalDateTime.now());
        category.setCreateTime(LocalDateTime.now());
        category.setCreateUser(BaseContext.getCurrentId());
        category.setUpdateUser(BaseContext.getCurrentId());
        categoryMapper.add(category);
    }

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     */
    @Override
    public void changeStatus(Integer status, Long id) {
        categoryMapper.changeStatus(status, id);
    }

    /**
     * 根据id删除分类
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        // 查询是否关联了菜品，关联了菜品就抛出异常
        Integer count = dishMapper.countByCategoryId(id);
        if (count > 0)
            // 当前分类有菜品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);

        // 查询该分类是否关联了套餐，关联了套餐抛出异常
        count = setmealMapper.countByCategoryId(id);
        if (count > 0)
            // 当前分类有套餐，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);

        // 执行删除分类
        categoryMapper.deleteById(id);
    }
}
