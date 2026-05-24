package com.SFood.system.service.impl;

import com.SFood.system.service.ImageService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 图片服务实现类
 */
@Service
public class ImageServiceImpl implements ImageService {
    
    @Value("${app.image.storage-path:./uploads/images}")
    private String storagePath;
    
    @Value("${app.image.base-url:/images}")
    private String baseUrl;
    
    // 允许的图片类型
    private static final String[] ALLOWED_IMAGE_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    };
    
    // 最大文件大小（5MB）
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    // 初始化时解析存储路径为绝对路径
    @PostConstruct
    public void init() {
        // 解析相对路径为绝对路径
        File storageDir = new File(storagePath);
        if (!storageDir.isAbsolute()) {
            storagePath = storageDir.getAbsolutePath();
        }
        // 确保存储目录存在
        try {
            createStorageDirectory();
        } catch (IOException e) {
            System.err.println("初始化存储目录失败: " + e.getMessage());
        }
    }
    
    @Override
    public String uploadDishImage(MultipartFile file, Long dishId) throws IOException {
        // 验证文件
        validateImageFile(file);
        
        // 创建存储目录
        createStorageDirectory();
        
        // 生成文件名
        String fileName = generateUniqueFileName(file.getOriginalFilename());
        
        // 创建菜品专属目录
        String dishDirectory = "dish_" + dishId;
        Path dishPath = Paths.get(storagePath, dishDirectory);
        Files.createDirectories(dishPath);
        
        // 保存文件
        Path filePath = dishPath.resolve(fileName);
        file.transferTo(filePath.toFile());
        
        // 返回访问URL
        return baseUrl + "/" + dishDirectory + "/" + fileName;
    }
    
    @Override
    public boolean deleteDishImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }
        
        // 从URL中提取文件路径
        String relativePath = imageUrl.replaceFirst(baseUrl + "/", "");
        Path filePath = Paths.get(storagePath, relativePath);
        
        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            return false;
        }
        
        // 删除文件
        return Files.deleteIfExists(filePath);
    }
    
    @Override
    public String getImageStoragePath() {
        return storagePath;
    }
    
    @Override
    public String getImageBaseUrl() {
        return baseUrl;
    }
    
    /**
     * 验证图片文件
     */
    private void validateImageFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("上传的文件为空");
        }
        
        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("文件大小不能超过5MB");
        }
        
        // 检查文件类型
        String contentType = file.getContentType();
        boolean isValidType = false;
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equals(contentType)) {
                isValidType = true;
                break;
            }
        }
        
        if (!isValidType) {
            throw new IOException("不支持的文件类型，仅支持JPEG、PNG、GIF、WebP格式");
        }
    }
    
    /**
     * 创建存储目录
     */
    private void createStorageDirectory() throws IOException {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
    
    /**
     * 生成唯一文件名
     */
    private String generateUniqueFileName(String originalFileName) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        // 获取文件扩展名
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        
        return timestamp + "_" + uuid + extension;
    }
}