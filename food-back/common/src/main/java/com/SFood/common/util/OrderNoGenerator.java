package com.SFood.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号生成器
 * 生成随机不重复的订单号
 */
public class OrderNoGenerator {
    
    // 时间格式化器
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    // 原子计数器，确保同一毫秒内的唯一性
    private static final AtomicInteger counter = new AtomicInteger(0);
    
    // 随机数生成器
    private static final Random random = new Random();
    
    /**
     * 生成订单号
     * 格式：年月日时分秒 + 6位随机数 + 3位计数器
     * 例如：20240101120000123456001
     */
    public static String generateOrderNo() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String timePart = now.format(formatter);
        
        // 2. 生成6位随机数
        String randomPart = String.format("%06d", random.nextInt(1000000));
        
        // 3. 获取计数器值（循环计数，避免溢出）
        int count = counter.getAndIncrement() % 1000;
        String countPart = String.format("%03d", count);
        
        // 4. 拼接订单号
        return timePart + randomPart + countPart;
    }
    
    /**
     * 生成带前缀的订单号
     * @param prefix 订单号前缀
     */
    public static String generateOrderNo(String prefix) {
        return prefix + generateOrderNo();
    }
    
    /**
     * 生成指定长度的订单号
     * @param length 订单号长度
     */
    public static String generateOrderNo(int length) {
        if (length < 10) {
            throw new IllegalArgumentException("订单号长度至少为10位");
        }
        
        String baseOrderNo = generateOrderNo();
        if (baseOrderNo.length() >= length) {
            return baseOrderNo.substring(0, length);
        }
        
        // 不足长度时补随机数
        StringBuilder sb = new StringBuilder(baseOrderNo);
        while (sb.length() < length) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}