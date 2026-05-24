package com.SFood.common.interceptor;

import com.SFood.common.util.JwtUtil;
import com.SFood.common.dto.ResultDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT Token拦截器
 * 用于验证请求中的token是否有效
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
    // 获取请求路径
        String requestURI = request.getRequestURI();

        // 放行登录注册相关接口
        if (isAuthEndpoint(requestURI)) {
            return true;
        }

        // 获取token
        String token = getTokenFromRequest(request);
        // 验证token
        if (token == null || token.isEmpty()) {
            sendErrorResponse(response, "缺少token");
            return false;
        }

        if (!jwtUtil.validateToken(token)) {
            sendErrorResponse(response, "token无效或已过期");
            return false;
        }

        // token验证通过，将用户信息存入请求属性中
        Long userId = jwtUtil.getUserIdFromToken(token);
        String phone = jwtUtil.getPhoneFromToken(token);
        request.setAttribute("userId", userId);
        request.setAttribute("phone", phone);

        return true;
    }

    /**
     * 检查是否为认证相关接口（需要放行的接口）
     */
    private boolean isAuthEndpoint(String requestURI) {
        return requestURI.equals("/**") ||
                requestURI.startsWith("/auth/") ||
               requestURI.equals("/auth") ||
               requestURI.startsWith("/swagger") || 
               requestURI.startsWith("/v3/api-docs") ||
               requestURI.startsWith("/webjars/") ||
               requestURI.equals("/") ||
               requestURI.equals("/favicon.ico");
    }

    /**
     * 从请求中获取token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 从Authorization header中获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // 从参数中获取
        return request.getParameter("token");
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        String jsonResponse = String.format(
            "{\"code\":401,\"message\":\"%s\",\"data\":null}", 
            message
        );
        
        response.getWriter().write(jsonResponse);
    }
}