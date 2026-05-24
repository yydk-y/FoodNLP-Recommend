package com.SFood.system.service.impl;

import com.SFood.system.dto.DishCategoryDTO;
import com.SFood.system.entity.DishCategory;
import com.SFood.system.mapper.DishCategoryMapper;
import com.SFood.system.service.DishCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 菜品分类服务实现类
 */
@Service
public class DishCategoryServiceImpl implements DishCategoryService {

    @Autowired
    private DishCategoryMapper dishCategoryMapper;

    @Override
    public DishCategory addCategory(DishCategoryDTO dishCategoryDTO) {
        // 验证分类名称是否已存在
        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_name", dishCategoryDTO.getCategoryName());
        DishCategory existingCategory = dishCategoryMapper.selectOne(queryWrapper);
        if (existingCategory != null) {
            throw new RuntimeException("分类名称已存在");
        }

        // 创建新分类
        DishCategory dishCategory = new DishCategory();
        BeanUtils.copyProperties(dishCategoryDTO, dishCategory);

        // 保存分类
        int result = dishCategoryMapper.insert(dishCategory);
        if (result > 0) {
            return dishCategory;
        }
        throw new RuntimeException("添加分类失败");
    }

    @Override
    public DishCategory updateCategory(DishCategoryDTO dishCategoryDTO) {
        if (dishCategoryDTO.getCategoryId() == null) {
            throw new RuntimeException("分类ID不能为空");
        }

        // 检查分类是否存在
        DishCategory existingCategory = dishCategoryMapper.selectById(dishCategoryDTO.getCategoryId());
        if (existingCategory == null) {
            throw new RuntimeException("分类不存在");
        }

        // 验证分类名称是否重复（排除当前分类）
        if (StringUtils.hasText(dishCategoryDTO.getCategoryName()) && 
            !existingCategory.getCategoryName().equals(dishCategoryDTO.getCategoryName())) {
            QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("category_name", dishCategoryDTO.getCategoryName());
            queryWrapper.ne("category_id", dishCategoryDTO.getCategoryId());
            DishCategory duplicateCategory = dishCategoryMapper.selectOne(queryWrapper);
            if (duplicateCategory != null) {
                throw new RuntimeException("分类名称已存在");
            }
        }

        // 更新分类信息
        BeanUtils.copyProperties(dishCategoryDTO, existingCategory);
        int result = dishCategoryMapper.updateById(existingCategory);
        if (result > 0) {
            return existingCategory;
        }
        throw new RuntimeException("更新分类失败");
    }

    @Override
    public boolean deleteCategory(Long categoryId) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }

        // 检查分类是否存在
        DishCategory existingCategory = dishCategoryMapper.selectById(categoryId);
        if (existingCategory == null) {
            throw new RuntimeException("分类不存在");
        }

        int result = dishCategoryMapper.deleteById(categoryId);
        return result > 0;
    }

    @Override
    public DishCategory getCategoryById(Long categoryId) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        return dishCategoryMapper.selectById(categoryId);
    }

    @Override
    public List<DishCategory> getAllCategories() {
        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        return dishCategoryMapper.selectList(queryWrapper);
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<DishCategory> getAllCategoriesPage(int page, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DishCategory> pageInfo = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        return dishCategoryMapper.selectPage(pageInfo, queryWrapper);
    }

    @Override
    public DishCategory getCategoryByName(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            throw new RuntimeException("分类名称不能为空");
        }
        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_name", categoryName);
        return dishCategoryMapper.selectOne(queryWrapper);
    }
}