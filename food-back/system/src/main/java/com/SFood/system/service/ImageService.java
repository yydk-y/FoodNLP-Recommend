package com.SFood.system.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片服务接口
 */
public interface ImageService {
    
    /**
     * 上传菜品图片
     * @param file 图片文件
     * @param dishId 菜品ID
     * @return 图片访问URL
     */
    String uploadDishImage(MultipartFile file, Long dishId) throws IOException;
    
    /**
     * 删除菜品图片
     * @param imageUrl 图片URL
     * @return 是否删除成功
     */
    boolean deleteDishImage(String imageUrl) throws IOException;
    
    /**
     * 获取图片存储目录
     * @return 图片存储目录路径
     */
    String getImageStoragePath();
    
    /**
     * 获取图片访问基础URL
     * @return 图片访问基础URL
     */
    String getImageBaseUrl();
}