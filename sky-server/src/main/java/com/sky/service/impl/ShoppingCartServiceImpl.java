package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.SetmealVO;
import lombok.Builder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Builder
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 新建一个购物车对象
        ShoppingCart shoppingCart = new ShoppingCart();
        // 拷贝信息到该对象
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        // 判断商品是否存在
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        // 若已存在则商品数量+1
        if (shoppingCartList != null && shoppingCartList.size() > 0){
            ShoppingCart cart = shoppingCartList.get(0);
            cart.setNumber(cart.getNumber()+ 1); // 更新购物车信息
            shoppingCartMapper.updateNumberById(cart);
        }else {
            // 若不存在则新建对象
            // 判断添加到购物车的是菜品还是套餐
            Long dishId = shoppingCartDTO.getDishId();
            Long setmealId = shoppingCartDTO.getSetmealId();
            if (dishId != null){
                // 本次添加到购物车的是菜品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName()); // 设置购物车中菜品名称
                shoppingCart.setImage(dish.getImage()); // 设置购物车中菜品图片
                shoppingCart.setAmount(dish.getPrice()); // 设置购物车中菜品价格
            }else {
                // 本次添加到购物车的是套餐
                SetmealVO setmealVO = setmealMapper.getById(setmealId);
                shoppingCart.setName(setmealVO.getName()); // 设置购物车中菜品名称
                shoppingCart.setImage(setmealVO.getImage()); // 设置购物车中菜品图片
                shoppingCart.setAmount(setmealVO.getPrice()); // 设置购物车中菜品价格
            }
            shoppingCart.setNumber(1); // 设置购物车中菜品数量
            shoppingCart.setCreateTime(LocalDateTime.now()); // 设置购物车中创建时间
            // 往购物车中插入数据
            shoppingCartMapper.insert(shoppingCart);
        }
    }
}
