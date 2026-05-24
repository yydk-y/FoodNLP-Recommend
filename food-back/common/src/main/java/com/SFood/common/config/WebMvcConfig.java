package com.SFood.common.config;

import com.SFood.common.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Value("${app.image.storage-path:./uploads/images}")
    private String imageStoragePath;

    @Value("${app.image.base-url:/images}")
    private String imageBaseUrl;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**",
                    "/",
                    "/favicon.ico",
                    "/images/**",
                    "/static/**"
                );
    }

    /**
     * 配置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 解析相对路径为绝对路径
        File storageDir = new File(imageStoragePath);
        String absolutePath = storageDir.getAbsolutePath();
        
        // 映射上传的图片文件
        registry.addResourceHandler(imageBaseUrl + "/**")
                .addResourceLocations("file:" + absolutePath + "/")
                .setCachePeriod(3600); // 缓存1小时
        
        // 添加其他静态资源映射
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}