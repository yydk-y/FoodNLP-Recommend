package com.SFood.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全上下文工具类
 * 用于获取当前登录用户信息
 */
public class SecurityContextUtil {

    /**
     * 获取当前请求的用户ID
     * @return 用户ID，如果未登录返回null
     */
    public static Long getCurrentUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object userId = request.getAttribute("userId");
            if (userId != null) {
                return (Long) userId;
            }
        }
        return null;
    }

    /**
     * 获取当前请求的用户手机号
     * @return 手机号，如果未登录返回null
     */
    public static String getCurrentUserPhone() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object phone = request.getAttribute("phone");
            if (phone != null) {
                return (String) phone;
            }
        }
        return null;
    }

    /**
     * 检查当前用户是否已登录
     * @return 是否已登录
     */
    public static boolean isLoggedIn() {
        return getCurrentUserId() != null;
    }

    /**
     * 获取当前HTTP请求
     * @return HttpServletRequest对象
     */
    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest();
        }
        return null;
    }
}