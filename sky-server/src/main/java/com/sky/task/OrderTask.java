package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ? ")
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        // 设置time = 当前时间 - 15分钟
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        if (!ordersList.isEmpty()){
            for (Orders orders : ordersList) {
                // 设置订单状态为已取消
                orders.setStatus(Orders.CANCELLED);
                // 设置取消原因
                orders.setCancelReason("订单超时未支付，自动取消");
                // 设置取消时间
                orders.setCancelTime(LocalDateTime.now());
                // 更新订单状态
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ? *")
    public void processDeliveryOrder(){
        log.info("处理一直处于派送中的订单：{}", LocalDateTime.now());
        // 设置time = 当前时间 - 1小时
        LocalDateTime time = LocalDateTime.now().plusHours(-1);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if (!ordersList.isEmpty()){
            for (Orders orders : ordersList) {
                // 设置订单状态为已完成
                orders.setStatus(Orders.COMPLETED);
                // 更新订单状态
                orderMapper.update(orders);
            }
        }
    }
}
