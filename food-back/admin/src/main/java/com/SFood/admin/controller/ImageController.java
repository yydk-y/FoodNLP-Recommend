package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.service.DishInfoService;
import com.SFood.system.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片控制器
 */
@RestController
@RequestMapping("/image")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private DishInfoService dishInfoService;

    /**
     * 上传菜品图片
     * @param file 图片文件
     * @param dishId 菜品ID
     * @return 上传结果
     */
    @PostMapping("/dish/upload")
    public ResultDTO uploadDishImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("dishId") Long dishId) {

        try {
            // 验证菜品是否存在
            var dish = dishInfoService.getDishById(dishId);
            if (dish == null) {
                return ResultDTO.error("菜品不存在");
            }

            // 上传图片
            String imageUrl = imageService.uploadDishImage(file, dishId);

            return ResultDTO.success("图片上传成功", imageUrl);

        } catch (IOException e) {
            return ResultDTO.error("图片上传失败: " + e.getMessage());
        } catch (Exception e) {
            return ResultDTO.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除菜品图片
     * @param imageUrl 图片URL
     * @return 删除结果
     */
    @DeleteMapping("/dish/delete")
    public ResultDTO deleteDishImage(@RequestParam("imageUrl") String imageUrl) {
        try {
            boolean result = imageService.deleteDishImage(imageUrl);
            if (result) {
                return ResultDTO.success("图片删除成功");
            } else {
                return ResultDTO.error("图片删除失败");
            }
        } catch (IOException e) {
            return ResultDTO.error("图片删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取图片存储信息
     * @return 存储信息
     */
    @GetMapping("/info")
    public ResultDTO getImageInfo() {
        try {
            String storagePath = imageService.getImageStoragePath();
            String baseUrl = imageService.getImageBaseUrl();

            var info = new java.util.HashMap<String, String>();
            info.put("storagePath", storagePath);
            info.put("baseUrl", baseUrl);

            return ResultDTO.success(info);
        } catch (Exception e) {
            return ResultDTO.error("获取图片信息失败: " + e.getMessage());
        }
    }

    /**
     * 根据菜品ID获取图片URL
     * @param dishId 菜品ID
     * @return 图片URL
     */
    @GetMapping("/dish/url")
    public ResultDTO getDishImageUrl(@RequestParam("dishId") Long dishId) {
        try {
            if (dishId == null) {
                return ResultDTO.error("dishId不能为空");
            }

            var dish = dishInfoService.getDishById(dishId);
            if (dish == null) {
                return ResultDTO.error("菜品不存在");
            }

            var result = new java.util.HashMap<String, Object>();
            result.put("dishId", dishId);
            result.put("imageUrl", dish.getImageUrl() == null ? "" : dish.getImageUrl().trim());
            return ResultDTO.success(result);
        } catch (Exception e) {
            return ResultDTO.error("获取菜品图片失败: " + e.getMessage());
        }
    }

}