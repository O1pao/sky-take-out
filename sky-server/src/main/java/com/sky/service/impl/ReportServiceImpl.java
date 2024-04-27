package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import io.swagger.models.auth.In;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

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
}
