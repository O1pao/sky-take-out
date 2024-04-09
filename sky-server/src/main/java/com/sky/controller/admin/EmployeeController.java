package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @ApiOperation(value = "员工登录")
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation("员工退出")
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 新增员工
     * @return
     */
    @ApiOperation("新增员工")
    @PostMapping
    public Result addEmp(@RequestBody EmployeeDTO employeeDTO){
        employeeService.addEmp(employeeDTO);
        log.info("新增员工{}", employeeDTO);
        return Result.success();
    }

    /**
     * 员工分页查询
     * @return
     */
    @ApiOperation("员工分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询：参数为：{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 禁用/启用员工
     * @param status
     * @return
     */
    @ApiOperation("禁用/启用员工")
    @PostMapping("status/{status}")
    public Result changeStatus(@PathVariable Integer status, Long id){
        log.info("启用/禁用员工账号{}{}", status, id);
        employeeService.changeStatus(id, status);
        return Result.success();
    }

    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    @ApiOperation("根据id查询员工")
    @GetMapping("/{id}")
    public Result<Employee> getById(@PathVariable Long id){
        log.info("查询id为{}的员工", id);
        Employee employee = employeeService.getById(id);
        employee.setPassword("****");
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     * @param employeeDTO 员工更新后的信息
     * @param id 员工id
     * @return
     */
    @ApiOperation("编辑员工信息")
    @PutMapping
    public Result update(@RequestBody EmployeeDTO employeeDTO, Long id){
        log.info("编辑id为{}的员工信息", id);
        employeeService.update(employeeDTO);
        return Result.success();
    }
}
