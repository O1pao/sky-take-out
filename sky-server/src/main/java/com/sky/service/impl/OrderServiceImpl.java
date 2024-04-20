package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 处理业务异常（地址簿为空，购物车为空）
        // 地址簿为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null)
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);

        // 购物车为空
        // 获取当前用户id下的购物车列表
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty())
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);

        // 往订单表中插入数据
        // 创建订单对象orders
        Orders orders = new Orders();
        // 拷贝属性到orders中， 包含地址簿id、支付方式、总金额、备注、打包费、餐具数量、餐具数量状态
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        // 设置其他属性
        // 订单编号  生成一个不包含'-'的订单编号
        String number = UUID.randomUUID().toString().replace("-", "");
        // 获取用户对象

        orders.setNumber(number); // 设置订单编号
        orders.setStatus(Orders.PENDING_PAYMENT); // 设置订单状态为待支付
        orders.setUserId(userId); // 设置用户id
        orders.setOrderTime(LocalDateTime.now()); // 设置下单时间为当前时间
        orders.setPayStatus(Orders.UN_PAID); // 设置支付状态为未支付
        orders.setPhone(addressBook.getPhone()); // 设置用户手机号
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail()); // 设置地址
        orders.setUserName(null); // 设置用户名称
        orders.setConsignee(addressBook.getConsignee()); // 设置收货人

        orderMapper.insert(orders);

        // 向订单明细表中插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            // 订单明细
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空购物车数据
        // 删除对应id的购物车数据
        shoppingCartMapper.delete(shoppingCart);

        // 封装VO返回结果
        OrderSubmitVO submitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return submitVO;
    }
}
