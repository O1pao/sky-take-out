package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.SneakyThrows;
import org.apache.poi.ss.formula.functions.Odd;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.jaxb.SpringDataJaxb;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Builder
@Slf4j
public class OrderServiceImpl implements OrderService {
    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        // 查询已接单的数量并传入orderStatisticsVO
        orderStatisticsVO.setConfirmed(orderMapper.getStatistics(Orders.CONFIRMED));
        // 查询派送中的数量并传入orderStatisticsVO
        orderStatisticsVO.setDeliveryInProgress(orderMapper.getStatistics(Orders.DELIVERY_IN_PROGRESS));
        // 查询待接单的数量并传入orderStatisticsVO
        orderStatisticsVO.setToBeConfirmed(orderMapper.getStatistics(Orders.TO_BE_CONFIRMED));
        return orderStatisticsVO;
    }

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
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private Orders order;

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

        orders.setNumber(number); // 设置订单编号
        orders.setStatus(Orders.PENDING_PAYMENT); // 设置订单状态为待支付
        orders.setUserId(userId); // 设置用户id
        orders.setOrderTime(LocalDateTime.now()); // 设置下单时间为当前时间
        orders.setPayStatus(Orders.UN_PAID); // 设置支付状态为未支付
        orders.setPhone(addressBook.getPhone()); // 设置用户手机号
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail()); // 设置地址
        orders.setConsignee(addressBook.getConsignee()); // 设置收货人

        this.order = orders; // 设置全局变量orders
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

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        /*//调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code","ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        Integer OrderPaidStatus = Orders.PAID;//支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单
        LocalDateTime check_out_time = LocalDateTime.now();//更新支付时间
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, this.order.getId());
        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 查询历史订单
     * @return
     */
    @Override
    public PageResult pageQuery4User(Integer page, Integer pageSize, Integer status) {
        PageHelper.startPage(page, pageSize);

        // 将信息传入OrdersPageQueryDTO对象
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setPage(page);
        ordersPageQueryDTO.setPageSize(pageSize);
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        // 进行动态查询操作
        // 查询orders表，封装成Page列表
        Page<Orders> ordersPage = orderMapper.list(ordersPageQueryDTO);

        // 存入订单明细的集合
        List<Orders> ordersList = new ArrayList<>();

        // 查询订单明细表
        if (!ordersPage.isEmpty()){
            for (Orders orders : ordersPage) {
                Long ordersId = orders.getId();

                // 查询出订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrdersId(ordersId);
                // 封装数据并返回
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                ordersList.add(orderVO);
            }
        }

        PageResult pageResult = new PageResult(ordersPage.getTotal(), ordersList);
        return pageResult;
    }

    /**
     * 用户端根据id查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO getOrderDetail(Long id) {
        // 查询订单信息
        OrderVO orderVO = orderMapper.getOrdersById(id);
        // 查询出订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrdersId(id);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户端取消订单
     * @param id
     */
    @SneakyThrows
    @Override
    public void userCancelOrder(Long id) {
        // 查询订单是否存在
        Orders orders = orderMapper.getOrdersById(id);
        if (orders == null)
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        // 获取订单状态
        Integer orderStatus = orders.getStatus();

        // 商家已接单状态下，用户取消订单需电话沟通商家
        // 派送中状态下，用户取消订单需电话沟通商家
        if (orderStatus == Orders.CONFIRMED || orderStatus == Orders.DELIVERY_IN_PROGRESS){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 如果在待接单状态下取消订单，需要给用户退款
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 待支付和待接单状态下，用户可直接取消订单
        // 将订单状态设置为已取消
        orders.setStatus(Orders.CANCELLED);

        // 取消订单后需要将订单状态修改为“已取消”
        orders.setCancelReason("用户取消订单"); // 设置取消原因为“用户取消订单”
        orders.setCancelTime(LocalDateTime.now()); // 设置取消时间为当前时间
        orderMapper.update(orders);
    }

    /**
     * 用户端再来一单
     * @param id 订单id
     */
    @Override
    @Transactional
    public void userRepetition(Long id) {
        // 获取对应订单id中的菜品
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrdersId(id);
        // 创建一个购物车对象
        ShoppingCart shoppingCart = new ShoppingCart();
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            // 拷贝对象信息
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            // 设置购物车对象用户id为当前用户id
            shoppingCart.setUserId(BaseContext.getCurrentId());
            // 设置创建时间为当前时间
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }
        // 往购物车中批量插入数据
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 管理端订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4Admin(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 开始分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        // 分页查询结果
        Page<Orders> ordersPage = orderMapper.list(ordersPageQueryDTO);
        // 封装查询结果
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders orders : ordersPage) {
            // 拷贝属性
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            // 获取该订单下菜品的信息
            orderVO.setOrderDishes(getOrderDishesStr(orders.getId()));
            orderVOList.add(orderVO);
        }

        PageResult pageResult = new PageResult(ordersPage.getTotal(), orderVOList);
        return pageResult;
    }

    /**
     * 根据订单获取菜品字符串 示例：宫保鸡丁*3
     * @param ordersId
     * @return
     */
    @Override
    public String getOrderDishesStr(Long ordersId) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrdersId(ordersId);
        StringBuffer result = new StringBuffer();
        // 获取该订单下菜品的信息
        for (OrderDetail orderDetail : orderDetailList) {
            result.append(orderDetail.getName() + "*" + orderDetail.getNumber() + ";");
        }
        return String.valueOf(result);
    }

    /**
     * 商家接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 获取订单信息
        OrderVO orders = orderMapper.getOrdersById(ordersConfirmDTO.getId());
        // 如果订单不存在抛出异常
        if (orders == null)
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);

        // 更改订单状态为已接单
        orders.setStatus(Orders.CONFIRMED);
        orderMapper.update(orders);
    }

    /**
     * 商家拒单
     * @param ordersRejectionDTO
     */
    @Override
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        // 获取订单信息
        OrderVO orders = orderMapper.getOrdersById(ordersRejectionDTO.getId());
        // 如果订单不存在或者订单不为待接单状态抛出异常
        if (orders == null || orders.getStatus() != Orders.TO_BE_CONFIRMED)
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);

        //支付状态
//        Integer payStatus = orders.getPayStatus();
//        if (payStatus == Orders.PAID) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    orders.getNumber(),
//                    orders.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }

        // 更改订单状态为已取消
        orders.setStatus(Orders.CANCELLED);
        // 添加拒单原因
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        // 更改支付状态为退款
        orders.setPayStatus(Orders.REFUND);
        // 更新订单状态
        orderMapper.update(orders);
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    @Override
    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) {

        // 获取订单信息
        OrderVO orders = orderMapper.getOrdersById(ordersCancelDTO.getId());
        // 如果订单不存在或者订单不为已接单状态抛出异常
        if (orders == null || orders.getStatus() != Orders.CONFIRMED)
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);

        //支付状态
//        Integer payStatus = orders.getPayStatus();
//        if (payStatus == Orders.PAID) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    orders.getNumber(),
//                    orders.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }

        // 更改订单状态为已取消
        orders.setStatus(Orders.CANCELLED);
        // 添加取消原因
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        // 更改支付状态为退款
        orders.setPayStatus(Orders.REFUND);
        // 更改取消订单时间
        orders.setCancelTime(LocalDateTime.now());
        // 更新订单状态
        orderMapper.update(orders);
    }

    /**
     * 商家派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        OrderVO ordersDB = orderMapper.getOrdersById(id);
        Integer status = ordersDB.getStatus();
        // 若订单为空或订单并非已接单状态，抛出订单错误异常
        if (ordersDB == null && status != Orders.CONFIRMED)
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);

        // 修改订单状态为派送中
        ordersDB.setStatus(Orders.DELIVERY_IN_PROGRESS);

        // 更新订单状态
        orderMapper.update(ordersDB);
    }
}
