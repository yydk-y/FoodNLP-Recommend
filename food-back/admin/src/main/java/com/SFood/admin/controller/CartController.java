package com.SFood.admin.controller;

import com.SFood.system.entity.CartInfo;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.service.CartService;
import com.SFood.common.dto.ResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 */
@RestController
@RequestMapping("/cart")
public class CartController {
    
    @Autowired
    private CartService cartService;
    
    /**
     * 添加商品到购物车
     */
    @PostMapping("/add")
    public ResultDTO<CartInfo> addToCart(@RequestBody CartInfo cartInfo) {
        CartInfo result = cartService.addToCart(cartInfo);
        return ResultDTO.success(result);
    }
    
    /**
     * 更新购物车商品数量
     */
    @PutMapping("/update")
    public ResultDTO<CartInfo> updateQuantity(@RequestParam Long cartId, 
                                             @RequestParam Integer quantity,
                                             @RequestParam(required = false) String remark) {
        CartInfo result = cartService.updateQuantity(cartId, quantity, remark);
        return ResultDTO.success(result);
    }
    
    /**
     * 删除购物车商品
     */
    @DeleteMapping("/remove/{cartId}")
    public ResultDTO removeFromCart(@PathVariable Long cartId) {
        cartService.removeFromCart(cartId);
        return ResultDTO.success("删除成功");
    }
    
    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    public ResultDTO clearCart(@RequestParam Long userId) {
        cartService.clearCart(userId);
        return ResultDTO.success(null);
    }
    
    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    public ResultDTO<List<CartInfo>> getCartList(@RequestParam Long userId) {
        List<CartInfo> cartList = cartService.getCartList(userId);
        return ResultDTO.success(cartList);
    }
    
    /**
     * 获取购物车商品数量
     */
    @GetMapping("/count")
    public ResultDTO<Integer> getCartItemCount(@RequestParam Long userId) {
        Integer count = cartService.getCartItemCount(userId);
        return ResultDTO.success(count);
    }
    
    /**
     * 批量删除购物车商品
     */
    @DeleteMapping("/batch-remove")
    public ResultDTO batchRemoveFromCart(@RequestBody List<Long> cartIds) {
        cartService.batchRemoveFromCart(cartIds);
        return ResultDTO.success("批量删除成功");
    }
    
    /**
     * 批量结算购物车商品并生成订单
     */
    @PostMapping("/batch-checkout")
    public ResultDTO<OrderMain> batchCheckout(@RequestParam Long userId, @RequestBody List<Long> cartIds) {
        OrderMain order = cartService.batchCheckout(userId, cartIds);
        return ResultDTO.success(order);
    }
}