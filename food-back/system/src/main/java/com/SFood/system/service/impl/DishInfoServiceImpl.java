package com.SFood.system.service.impl;

import com.SFood.system.dto.DishInfoDTO;
import com.SFood.system.dto.DishWithCategoryDTO;
import com.SFood.system.dto.RatingStatistics;
import com.SFood.system.entity.DishCategory;
import com.SFood.system.entity.DishInfo;
import com.SFood.system.mapper.DishCategoryMapper;
import com.SFood.system.mapper.DishInfoMapper;
import com.SFood.system.service.DishInfoService;
import com.SFood.system.service.DishRatingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

/**
 * 菜品信息服务实现类
 */
@Service
@CacheConfig(cacheNames = "dishScores")
public class DishInfoServiceImpl implements DishInfoService {

    @Autowired
    private DishInfoMapper dishInfoMapper;

    @Autowired
    private DishCategoryMapper dishCategoryMapper;
    
    @Autowired
    private DishRatingService dishRatingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String DISH_SCORE_KEY_PREFIX = "dish:score:";
    // 缓存过期时间
    private static final long CACHE_EXPIRY_TIME = 5 * 60; // 5分钟

    private QueryWrapper<DishInfo> listedDishQuery() {
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        return queryWrapper;
    }

    private QueryWrapper<DishInfo> allDishQuery() {
        return new QueryWrapper<>();
    }

    @Override
    public DishInfo addDish(DishInfoDTO dishInfoDTO) {
        // 验证菜品名称是否已存在
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dish_name", dishInfoDTO.getDishName());
        DishInfo existingDish = dishInfoMapper.selectOne(queryWrapper);
        if (existingDish != null) {
            throw new RuntimeException("菜品名称已存在");
        }

        // 创建新菜品
        DishInfo dishInfo = new DishInfo();
        BeanUtils.copyProperties(dishInfoDTO, dishInfo);
        dishInfo.setHeat(0); // 默认热度为0
        dishInfo.setCreateTime(LocalDateTime.now());

        // 保存菜品
        int result = dishInfoMapper.insert(dishInfo);
        if (result > 0) {
            return dishInfo;
        }
        throw new RuntimeException("添加菜品失败");
    }

    @Override
    public DishInfo updateDish(DishInfoDTO dishInfoDTO) {
        if (dishInfoDTO.getDishId() == null) {
            throw new RuntimeException("菜品ID不能为空");
        }

        // 检查菜品是否存在
        DishInfo existingDish = dishInfoMapper.selectById(dishInfoDTO.getDishId());
        if (existingDish == null) {
            throw new RuntimeException("菜品不存在");
        }

        // 验证菜品名称是否重复（排除当前菜品）
        if (StringUtils.hasText(dishInfoDTO.getDishName()) && 
            !existingDish.getDishName().equals(dishInfoDTO.getDishName())) {
            QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("dish_name", dishInfoDTO.getDishName());
            queryWrapper.ne("dish_id", dishInfoDTO.getDishId());
            DishInfo duplicateDish = dishInfoMapper.selectOne(queryWrapper);
            if (duplicateDish != null) {
                throw new RuntimeException("菜品名称已存在");
            }
        }

        // 更新菜品信息
        BeanUtils.copyProperties(dishInfoDTO, existingDish);
        int result = dishInfoMapper.updateById(existingDish);
        if (result > 0) {
            return existingDish;
        }
        throw new RuntimeException("更新菜品失败");
    }

    @Override
    public boolean deleteDish(Long dishId) {
        if (dishId == null) {
            throw new RuntimeException("菜品ID不能为空");
        }

        // 检查菜品是否存在
        DishInfo existingDish = dishInfoMapper.selectById(dishId);
        if (existingDish == null) {
            throw new RuntimeException("菜品不存在");
        }

        int result = dishInfoMapper.deleteById(dishId);
        return result > 0;
    }

    @Override
    public DishInfo getDishById(Long dishId) {
        if (dishId == null) {
            throw new RuntimeException("菜品ID不能为空");
        }
        return dishInfoMapper.selectById(dishId);
    }

    @Override
    public List<DishInfo> getAllDishes() {
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.orderByDesc("create_time");
        return dishInfoMapper.selectList(queryWrapper);
    }

    @Override
    public Page<DishInfo> getDishesByPage(int page, int size) {
        Page<DishInfo> pageInfo = new Page<>(page, size);
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.orderByDesc("create_time");
        return dishInfoMapper.selectPage(pageInfo, queryWrapper);
    }

    @Override
    public List<DishInfo> getDishesByCategory(Long categoryId) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.eq("category_id", categoryId);
        queryWrapper.orderByDesc("create_time");
        return dishInfoMapper.selectList(queryWrapper);
    }

    @Override
    public Page<DishInfo> getDishesByCategoryPage(Long categoryId, int page, int size) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        
        Page<DishInfo> pageInfo = new Page<>(page, size);
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.eq("category_id", categoryId);
        queryWrapper.orderByDesc("create_time");
        
        return dishInfoMapper.selectPage(pageInfo, queryWrapper);
    }

    @Override
    public List<DishInfo> getDishesByStatus(Integer status) {
        if (status == null) {
            throw new RuntimeException("状态不能为空");
        }
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", status);
        queryWrapper.orderByDesc("create_time");
        return dishInfoMapper.selectList(queryWrapper);
    }

    @Override
    public DishInfo getDishByName(String dishName) {
        if (!StringUtils.hasText(dishName)) {
            throw new RuntimeException("菜品名称不能为空");
        }
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dish_name", dishName);
        return dishInfoMapper.selectOne(queryWrapper);
    }

    @Override
    public List<DishInfo> searchDishesByName(String dishName) {
        if (!StringUtils.hasText(dishName)) {
            throw new RuntimeException("菜品名称不能为空");
        }
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.like("dish_name", dishName);
        queryWrapper.orderByDesc("create_time");
        return dishInfoMapper.selectList(queryWrapper);
    }

    @Override
    public Page<DishInfo> searchDishesByNamePage(String dishName, int page, int size) {
        if (!StringUtils.hasText(dishName)) {
            throw new RuntimeException("菜品名称不能为空");
        }
        
        Page<DishInfo> pageInfo = new Page<>(page, size);
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.like("dish_name", dishName);
        queryWrapper.orderByDesc("create_time");
        
        return dishInfoMapper.selectPage(pageInfo, queryWrapper);
    }

    @Override
    public DishWithCategoryDTO getDishWithCategoryById(Long dishId) {
        if (dishId == null) {
            throw new RuntimeException("菜品ID不能为空");
        }
        
        // 查询菜品信息
        DishInfo dishInfo = dishInfoMapper.selectById(dishId);
        if (dishInfo == null) {
            throw new RuntimeException("菜品不存在");
        }
        
        // 查询分类信息
        DishCategory dishCategory = dishCategoryMapper.selectById(dishInfo.getCategoryId());
        
        return new DishWithCategoryDTO(dishInfo, dishCategory);
    }

    @Override
    public List<DishWithCategoryDTO> getAllDishesWithCategory() {
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        List<DishInfo> dishes = dishInfoMapper.selectList(queryWrapper);
        
        return dishes.stream()
                .map(dish -> {
                    DishCategory dishCategory = dishCategoryMapper.selectById(dish.getCategoryId());
                    return new DishWithCategoryDTO(dish, dishCategory);
                })
                .toList();
    }

    @Override
    public List<DishWithCategoryDTO> getDishesWithCategoryByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.eq("category_id", categoryId);
        queryWrapper.orderByDesc("create_time");
        List<DishInfo> dishes = dishInfoMapper.selectList(queryWrapper);
        
        // 查询分类信息
        DishCategory dishCategory = dishCategoryMapper.selectById(categoryId);
        
        return dishes.stream()
                .map(dish -> new DishWithCategoryDTO(dish, dishCategory))
                .toList();
    }

    @Override
    public List<DishInfo> getAllDishesByRating() {
        // 获取所有菜品
        QueryWrapper<DishInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        List<DishInfo> dishes = dishInfoMapper.selectList(queryWrapper);
        
        // 按评分排序
        return getDishInfos(dishes);
    }

    // ... existing code ...
    // 批量计算菜品的加权评分
    private Map<Long, Double> batchCalculateWeightedScores(List<DishInfo> dishes) {
        Map<Long, Double> scoreMap = new HashMap<>();
        List<Long> needToCalculateIds = new ArrayList<>();

        // 收集需要计算评分的菜品ID
        for (DishInfo dish : dishes) {
            Long dishId = dish.getDishId();
            // 从Redis获取评分
            Double score = getScoreFromRedis(dishId);
            if (score != null) {
                scoreMap.put(dishId, score);
            } else {
                needToCalculateIds.add(dishId);
            }
        }

        // 批量获取需要计算评分的菜品的统计信息
        if (!needToCalculateIds.isEmpty()) {
            Map<Long, RatingStatistics> statsMap = new HashMap<>();
            for (Long dishId : needToCalculateIds) {
                RatingStatistics stats = dishRatingService.getRatingStatistics(dishId);
                statsMap.put(dishId, stats);
            }

            // 计算每个菜品的加权评分并更新Redis缓存
            for (Long dishId : needToCalculateIds) {
                RatingStatistics stats = statsMap.get(dishId);
                double score;

                if (stats == null || stats.getTotalRatings() == 0) {
                    // 没有评论，使用默认评分
                    score = 0.0;
                } else {
                    double averageRating = stats.getAverageScore();
                    int ratingCount = stats.getTotalRatings();

                    // 计算加权分数
                    double ratingWeight = 0.4;
                    double countWeight = 0.6;
                    double normalizedCount = Math.log1p(ratingCount);
                    double weightedScore = (averageRating / 5.0) * ratingWeight + (normalizedCount / Math.log1p(100)) * countWeight;
                    score = weightedScore;
                }

                // 更新Redis缓存
                setScoreToRedis(dishId, score);
                scoreMap.put(dishId, score);
            }
        }

        return scoreMap;
    }

    // 从Redis获取评分
    private Double getScoreFromRedis(Long dishId) {
        String key = DISH_SCORE_KEY_PREFIX + dishId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.valueOf(value.toString()) : null;
    }

    // 将评分存入Redis
    private void setScoreToRedis(Long dishId, double score) {
        String key = DISH_SCORE_KEY_PREFIX + dishId;
        redisTemplate.opsForValue().set(key, score, CACHE_EXPIRY_TIME, TimeUnit.SECONDS);
    }

    private List<DishInfo> getDishInfos(List<DishInfo> dishes) {
        // 批量计算加权评分
        Map<Long, Double> scoreMap = batchCalculateWeightedScores(dishes);
        
        // 按加权分数降序排序
        return dishes.stream()
                .sorted((d1, d2) -> {
                    double score1 = scoreMap.getOrDefault(d1.getDishId(), 0.0);
                    double score2 = scoreMap.getOrDefault(d2.getDishId(), 0.0);
                    return Double.compare(score2, score1);
                })
                .toList();
    }
// ... existing code ...


    @Override
    public List<DishInfo> getDishesByCategoryByRating(Long categoryId) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        
        // 获取该分类下的所有菜品
        QueryWrapper<DishInfo> queryWrapper = allDishQuery();
        queryWrapper.eq("category_id", categoryId);
        queryWrapper.orderByDesc("create_time");
        List<DishInfo> dishes = dishInfoMapper.selectList(queryWrapper);
        
        // 按评分排序
        return getDishInfos(dishes);
    }

    @Override
    public Page<DishInfo> getDishesByPageByRating(int page, int size) {
        // 先获取所有菜品ID（不分页，只获取ID）
        QueryWrapper<DishInfo> idQueryWrapper = new QueryWrapper<>();
        idQueryWrapper.select("dish_id");
        idQueryWrapper.eq("status", 1);
        List<DishInfo> idList = dishInfoMapper.selectList(idQueryWrapper);
        
        // 提取所有菜品ID
        List<Long> dishIds = idList.stream()
                .map(DishInfo::getDishId)
                .collect(Collectors.toList());
        
        // 批量获取菜品详情
        List<DishInfo> allDishes = new ArrayList<>();
        if (!dishIds.isEmpty()) {
            allDishes = dishInfoMapper.selectBatchIds(dishIds).stream()
                    .filter(d -> d != null && Integer.valueOf(1).equals(d.getStatus()))
                    .toList();
        }
        
        // 按评分和评论人数排序
        List<DishInfo> sortedDishes = getDishInfos(allDishes);
        
        // 计算分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, sortedDishes.size());
        
        // 截取分页数据
        List<DishInfo> pageDishes = start < sortedDishes.size() ? sortedDishes.subList(start, end) : new ArrayList<>();
        
        // 创建分页对象
        Page<DishInfo> pageInfo = new Page<>(page, size);
        pageInfo.setRecords(pageDishes);
        pageInfo.setTotal(sortedDishes.size());
        pageInfo.setPages((sortedDishes.size() + size - 1) / size);
        
        return pageInfo;
    }

    @Override
    public Page<DishInfo> getDishesByCategoryPageByRating(Long categoryId, int page, int size) {
        if (categoryId == null) {
            throw new RuntimeException("分类ID不能为空");
        }
        
        // 先获取该分类下的所有菜品ID（不分页，只获取ID）
        QueryWrapper<DishInfo> idQueryWrapper = new QueryWrapper<>();
        idQueryWrapper.select("dish_id");
        idQueryWrapper.eq("status", 1);
        idQueryWrapper.eq("category_id", categoryId);
        List<DishInfo> idList = dishInfoMapper.selectList(idQueryWrapper);
        
        // 提取所有菜品ID
        List<Long> dishIds = idList.stream()
                .map(DishInfo::getDishId)
                .collect(Collectors.toList());
        
        // 批量获取菜品详情
        List<DishInfo> allDishes = new ArrayList<>();
        if (!dishIds.isEmpty()) {
            allDishes = dishInfoMapper.selectBatchIds(dishIds).stream()
                    .filter(d -> d != null && Integer.valueOf(1).equals(d.getStatus()))
                    .toList();
        }
        
        // 按评分和评论人数排序
        List<DishInfo> sortedDishes = getDishInfos(allDishes);
        
        // 计算分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, sortedDishes.size());
        
        // 截取分页数据
        List<DishInfo> pageDishes = start < sortedDishes.size() ? sortedDishes.subList(start, end) : new ArrayList<>();
        
        // 创建分页对象
        Page<DishInfo> pageInfo = new Page<>(page, size);
        pageInfo.setRecords(pageDishes);
        pageInfo.setTotal(sortedDishes.size());
        pageInfo.setPages((sortedDishes.size() + size - 1) / size);
        
        return pageInfo;
    }
}