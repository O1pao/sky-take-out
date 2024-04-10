package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import io.swagger.models.auth.In;

import java.util.List;

public interface CategoryService {

    /**
     * 分类管理分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 修改分类信息
     * @param categoryDTO
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 根据类型查询分类
     * @return
     */
    List<Category> getListByType(Integer type);

    /**
     * 新增分类
     * @param categoryDTO
     */
    void add(CategoryDTO categoryDTO);

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     */
    void changeStatus(Integer status, Long id);

    /**
     * 根据id删除分类
     * @param id
     */
    void deleteById(Long id);
}
