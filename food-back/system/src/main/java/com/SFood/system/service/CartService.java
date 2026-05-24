package com.SFood.system.service;

import com.SFood.system.entity.CartInfo;
import com.SFood.system.entity.OrderMain;

import java.util.List;

/**
 * 购物车服务接口
 */
public interface CartService {
    
    /**
     * 添加商品到购物车
     * @param cartInfo 购物车信息
     * @return 购物车信息
     */
    CartInfo addToCart(CartInfo cartInfo);
    
    /**
     * 更新购物车商品数量
     * @param cartId 购物车ID
     * @param quantity 新数量
     * @param remark 备注
     * @return 更新后的购物车信息
     */
    CartInfo updateQuantity(Long cartId, Integer quantity, String remark);
    
    /**
     * 删除购物车商品
     * @param cartId 购物车ID
     */
    void removeFromCart(Long cartId);
    
    /**
     * 清空用户购物车
     * @param userId 用户ID
     */
    void clearCart(Long userId);
    
    /**
     * 获取用户购物车列表
     * @param userId 用户ID
     * @return 购物车列表
     */
    List<CartInfo> getCartList(Long userId);
    
    /**
     * 获取购物车商品数量
     * @param userId 用户ID
     * @return 商品数量
     */
    Integer getCartItemCount(Long userId);
    
    /**
     * 批量删除购物车商品
     * @param cartIds 购物车ID列表
     */
    void batchRemoveFromCart(List<Long> cartIds);
    
    /**
     * 批量结算购物车商品并生成订单
     * @param userId 用户ID
     * @param cartIds 购物车ID列表
     * @return 生成的订单信息
     */
    OrderMain batchCheckout(Long userId, List<Long> cartIds);
}