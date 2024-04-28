package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id获取明细
     * @param ordersId
     * @return
     */
    @Select("select * from order_detail where order_id = #{ordersId}")
    List<OrderDetail> getByOrdersId(Long ordersId);

    /**
     * 套餐和菜品销量top10统计
     * @param map
     */
    List<GoodsSalesDTO> getSalesTop10ByMap(Map map);
}
