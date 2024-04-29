package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import io.swagger.models.auth.In;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 当前集合用于存放从begin到end范围内每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            // 查询date日期对应的营业额，营业额是指：状态为已完成的订单金额合计
            // 该日期的开始时间 00:00:00
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            // 该日期的结束时间 23:59:59
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap<>();
            map.put("beginTime", beginTime);
            map.put("endTime", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
//            turnover = turnover == null ? 0.0 : turnover;
            if (turnover == null)
                turnover = 0.0;
            turnoverList.add(turnover);
        }

        // 求出begin到end这些天已完成的营业额
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // 用于存放begin到end之间的日期集合
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 用于存放对应日期新增的用户数
        List<Integer> newUserList = new ArrayList<>();
        // 用于存放截止对应日期之前的总用户数
        List<Integer> totalUserList = new ArrayList<>();
        for (LocalDate localDate : dateList) {
            // 设置日期的开始时间
            Map map = new HashMap<>();
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);
            map.put("endTime", endTime);

            // 查询截止对应日期之前的总用户数
            Integer totalUsers = userMapper.getUserByMap(map);
            totalUsers = totalUsers == null ? 0 : totalUsers;
            map.put("beginTime", beginTime);
            totalUserList.add(totalUsers);

            // 查询对应日期的新增用户数
            Integer newUsers = userMapper.getUserByMap(map);
            newUsers = newUsers == null ? 0 : newUsers;
            newUserList.add(newUsers);

        }

        // 封装返回对象
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        // 用于存放begin到end之间的日期集合
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 存放对应日期对应的订单总数
        List<Integer> orderCountList = new ArrayList<>();
        // 存放对应日期对应的有效订单总数，即已完成的订单数量
        List<Integer> validOrderCountList = new ArrayList<>();
        // 订单状态
        Integer status = Orders.COMPLETED;
        for (LocalDate localDate : dateList) {
            // 对应日期开始时间
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            // 对应日期结束时间
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);

            // 调用getOrderCount方法查询对应日期的订单总数
            Integer totalOrder = getOrderCount(beginTime, endTime, null);
            totalOrder = totalOrder == null ? 0 : totalOrder;
            orderCountList.add(totalOrder);

            // 调用getOrderCount方法查询对应日期的已完成订单总数
            Integer validOrder = getOrderCount(beginTime, endTime, status);
            validOrder = validOrder == null ? 0 : validOrder;
            validOrderCountList.add(validOrder);

        }
        // 计算时间区间内订单总数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        // 计算时间区间内有效订单总数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        // 计算对应日期的订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0)
            orderCompletionRate =  validOrderCount.doubleValue() / totalOrderCount;


        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(Double.valueOf(String.format("%.1f", orderCompletionRate)))
                .build();
    }

    /**
     * 套餐和菜品销量top10统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        // 用于存放begin到end之间的日期集合
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 对应日期开始时间
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        // 对应日期结束时间
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        Map map = new HashMap<>();
        map.put("beginTime", beginTime);
        map.put("endTime", endTime);
        map.put("status", Orders.COMPLETED); // 查询的是订单状态为已完成的菜品

        List<GoodsSalesDTO> salesTop10 = orderDetailMapper.getSalesTop10ByMap(map);

        // 存放对应日期对应的菜品/订单名字
        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        // 存放对应日期对应的菜品/订单订单数量
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        // 封装返回对象
        return SalesTop10ReportVO
                .builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
    }

    /**
     * 根据条件查询订单数量
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status){
        Map map = new HashMap<>();
        map.put("beginTime", beginTime);
        map.put("endTime", endTime);
        map.put("status", status);

        return orderMapper.countByMap(map);
    }

    /**
     * 导出运营数据Excel报表
     */
    @Override
    public void export(HttpServletResponse httpServletResponse) {

        LocalDateTime endTime = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.MIN);
        LocalDateTime beginTime = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MAX);

        // 查询数据库，获取数据（查询最近30天的数据）
        BusinessDataVO businessData = workspaceService.getBusinessData(beginTime, endTime);

        // 通过POI写入Excel文件

        try (
                // 获取模版资源
                InputStream is = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
                // 根据模版创建一个新的Excel文件
                XSSFWorkbook excel = new XSSFWorkbook(is);
                // 通过输出流将Excel文件下载到客户端浏览器
                ServletOutputStream os = httpServletResponse.getOutputStream();
        ) {
            // 往新的Excel中填充数据
            // 获取第一个标签页
            XSSFSheet sheet = excel.getSheetAt(0);
            // 设置第2行第2个单元格的值 -- 时间
            sheet.getRow(1).getCell(1).setCellValue("时间：" + beginTime.toLocalDate() + "至" + endTime.toLocalDate() );
            // 设置第4行第3个单元格的值 -- 营业额
            sheet.getRow(3).getCell(2).setCellValue(businessData.getTurnover());
            // 设置第4行第5个单元格的值 -- 订单完成率
            sheet.getRow(3).getCell(4).setCellValue(businessData.getOrderCompletionRate());
            // 设置第4行第7个单元格的值 -- 新增用户数
            sheet.getRow(3).getCell(6).setCellValue(businessData.getNewUsers());
            // 设置第5行第3个单元格的值 -- 有效订单
            sheet.getRow(4).getCell(2).setCellValue(businessData.getValidOrderCount());
            // 设置第5行第5个单元格的值 -- 平均客单价
            sheet.getRow(4).getCell(4).setCellValue(businessData.getUnitPrice());

            // 填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate localDate = beginTime.plusDays(i).toLocalDate();
                BusinessDataVO businessDataTemp = workspaceService.getBusinessData(
                        LocalDateTime.of(localDate, LocalTime.MIN),
                        LocalDateTime.of(localDate, LocalTime.MAX));
                // 设置第7+i行第2个单元格的值 -- 日期
                sheet.getRow(7 + i).getCell(1).setCellValue(localDate.toString());
                // 设置第7+i行第3个单元格的值 -- 营业额
                sheet.getRow(7 + i).getCell(2).setCellValue(businessDataTemp.getTurnover());
                // 设置第7+i行第4个单元格的值 -- 有效订单
                sheet.getRow(7 + i).getCell(3).setCellValue(businessDataTemp.getValidOrderCount());
                // 设置第7+i行第5个单元格的值 -- 订单完成率
                sheet.getRow(7 + i).getCell(4).setCellValue(businessDataTemp.getOrderCompletionRate());
                // 设置第7+i行第6个单元格的值 -- 平均客单价
                sheet.getRow(7 + i).getCell(5).setCellValue(businessDataTemp.getUnitPrice());
                // 设置第7+i行第7个单元格的值 -- 新增用户数
                sheet.getRow(7 + i).getCell(6).setCellValue(businessDataTemp.getNewUsers());
            }

            // 通过输出流将Excel文件下载到客户端浏览器
            excel.write(os);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
