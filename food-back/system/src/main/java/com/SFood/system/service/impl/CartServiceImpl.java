package com.SFood.system.service.impl;

import com.SFood.system.entity.OrderMain;
import com.SFood.system.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.SFood.system.entity.CartInfo;
import com.SFood.system.mapper.CartInfoMapper;
import com.SFood.system.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车服务实现类
 */
@Service
public class CartServiceImpl implements CartService {
    
    @Autowired
    private CartInfoMapper cartInfoMapper;
    
    @Autowired
    private OrderService orderService;
    
    @Override
    public CartInfo addToCart(CartInfo cartInfo) {
        // 检查是否已存在相同商品
        LambdaQueryWrapper<CartInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartInfo::getUserId, cartInfo.getUserId())
               .eq(CartInfo::getDishId, cartInfo.getDishId());
        
        CartInfo existingCart = cartInfoMapper.selectOne(wrapper);
        
        if (existingCart != null) {
            // 已存在，更新数量
            existingCart.setQuantity(existingCart.getQuantity() + cartInfo.getQuantity());
            existingCart.setRemark(cartInfo.getRemark());
            existingCart.calculateSubtotal();
            cartInfoMapper.updateById(existingCart);
            return existingCart;
        } else {
            // 不存在，新增
            cartInfo.calculateSubtotal();
            cartInfoMapper.insert(cartInfo);
            return cartInfo;
        }
    }
    
    @Override
    public CartInfo updateQuantity(Long cartId, Integer quantity, String remark) {
        CartInfo cartInfo = cartInfoMapper.selectById(cartId);
        if (cartInfo != null) {
            cartInfo.setQuantity(quantity);
            if (remark != null) {
                cartInfo.setRemark(remark);
            }
            cartInfo.calculateSubtotal();
            cartInfoMapper.updateById(cartInfo);
        }
        return cartInfo;
    }
    
    @Override
    public void removeFromCart(Long cartId) {
        cartInfoMapper.deleteById(cartId);
    }
    
    @Override
    public void clearCart(Long userId) {
        LambdaQueryWrapper<CartInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartInfo::getUserId, userId);
        cartInfoMapper.delete(wrapper);
    }
    
    @Override
    public List<CartInfo> getCartList(Long userId) {
        LambdaQueryWrapper<CartInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartInfo::getUserId, userId);
        return cartInfoMapper.selectList(wrapper);
    }
    
    @Override
    public Integer getCartItemCount(Long userId) {
        LambdaQueryWrapper<CartInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartInfo::getUserId, userId);
        List<CartInfo> cartList = cartInfoMapper.selectList(wrapper);
        return cartList.stream().mapToInt(CartInfo::getQuantity).sum();
    }
    
    @Override
    public void batchRemoveFromCart(List<Long> cartIds) {
        if (cartIds != null && !cartIds.isEmpty()) {
            cartInfoMapper.deleteBatchIds(cartIds);
        }
    }
    
    @Override
    public OrderMain batchCheckout(Long userId, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) {
            throw new RuntimeException("购物车商品不能为空");
        }
        
        // 调用订单服务进行结算
        return orderService.checkoutFromCart(userId, cartIds);
    }
}