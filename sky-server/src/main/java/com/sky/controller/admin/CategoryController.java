package com.sky.controller.admin;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 分类管理
 */
@RestController
@RequestMapping("/admin/category")
@Slf4j
@Api(tags = "分类相关接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 分类管理分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    @ApiOperation("分类管理分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(CategoryPageQueryDTO categoryPageQueryDTO){
        log.info("分类管理分页查询:参数为:{}", categoryPageQueryDTO);
        // 将分页查询结果封装成PageResult对象
        PageResult pageResult = categoryService.pageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据类型查询分类
     * @return
     */
    @ApiOperation("根据类型查询分类")
    @GetMapping("/list")
    public Result<List<Category>> getByType(Integer type){
        log.info("查询类型为{}的分类", type);
        // 获取category集合
        List<Category> categories = categoryService.getListByType(type);
        return Result.success(categories);
    }

    /**
     * 修改分类信息
     * @param categoryDTO
     * @return
     */
    @ApiOperation("修改分类")
    @PutMapping
    public Result update(@RequestBody CategoryDTO categoryDTO){
        log.info("修改分类：{}", categoryDTO);
        // 进行分类修改操作
        categoryService.update(categoryDTO);
        return Result.success();
    }

    /**
     * 新增分类
     * @param categoryDTO
     * @return
     */
    @ApiOperation("新增分类")
    @PostMapping
    public Result add(@RequestBody CategoryDTO categoryDTO){
        log.info("新增分类：{}", categoryDTO);
        // 进行新增分类操作
        categoryService.add(categoryDTO);
        return Result.success();
    }

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     * @return
     */
    @ApiOperation("启用、禁用分类")
    @PostMapping("/status/{status}")
    public Result changeStatus(@PathVariable Integer status, Long id){
        log.info("修改id为{}的状态为{}", id, status);
        // 进行修改操作
        categoryService.changeStatus(status, id);

        return Result.success();
    }

    /**
     * 根据id删除分类
     * @param id
     * @return
     */
    @ApiOperation("根据id删除分类")
    @DeleteMapping
    public Result deleteById(Long id){
        log.info("删除id为{}的分类", id);

        // 执行删除
        categoryService.deleteById(id);

        return Result.success();
    }
}
