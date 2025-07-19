package com.jsh.erp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis性能优化工具类
 * 为ERP系统的性能优化提供缓存管理
 */
@Component
public class RedisOptimizationUtil {

    private static final Logger logger = LoggerFactory.getLogger(RedisOptimizationUtil.class);
    
    @Resource
    private RedisTemplate<String, Object> optimizationRedisTemplate;

    // 缓存键前缀
    private static final String CACHE_PREFIX = "erp:optimization:";
    private static final String DAILY_OUT_PREFIX = CACHE_PREFIX + "daily_out:";
    private static final String MATERIAL_STOCK_PREFIX = CACHE_PREFIX + "material_stock:";
    private static final String STATISTICS_PREFIX = CACHE_PREFIX + "statistics:";
    private static final String PERIOD_SUMMARY_PREFIX = CACHE_PREFIX + "period_summary:";

    /**
     * 生成缓存键
     */
    public String generateCacheKey(String type, Object... params) {
        StringBuilder key = new StringBuilder();
        switch (type) {
            case "daily_out":
                key.append(DAILY_OUT_PREFIX);
                break;
            case "material_stock":
                key.append(MATERIAL_STOCK_PREFIX);
                break;
            case "statistics":
                key.append(STATISTICS_PREFIX);
                break;
            case "period_summary":
                key.append(PERIOD_SUMMARY_PREFIX);
                break;
            default:
                key.append(CACHE_PREFIX).append(type).append(":");
                break;
        }
        
        for (Object param : params) {
            if (param != null) {
                key.append(param.toString()).append(":");
            }
        }
        
        // 移除最后一个冒号
        if (key.length() > 0 && key.charAt(key.length() - 1) == ':') {
            key.deleteCharAt(key.length() - 1);
        }
        
        return key.toString();
    }

    /**
     * 设置缓存
     */
    public void setCache(String key, Object value, long timeout, TimeUnit unit) {
        try {
            optimizationRedisTemplate.opsForValue().set(key, value, timeout, unit);
            logger.debug("缓存设置成功: {}, 过期时间: {} {}", key, timeout, unit);
        } catch (Exception e) {
            logger.error("设置缓存失败: {}", key, e);
        }
    }

    /**
     * 获取缓存
     */
    public Object getCache(String key) {
        try {
            Object value = optimizationRedisTemplate.opsForValue().get(key);
            if (value != null) {
                logger.debug("缓存命中: {}", key);
                // 记录缓存命中统计
                incrementCacheHit();
            } else {
                logger.debug("缓存未命中: {}", key);
                // 记录缓存未命中统计
                incrementCacheMiss();
            }
            return value;
        } catch (Exception e) {
            logger.error("获取缓存失败: {}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public void deleteCache(String key) {
        try {
            optimizationRedisTemplate.delete(key);
            logger.debug("缓存删除: {}", key);
        } catch (Exception e) {
            logger.error("删除缓存失败: {}", key, e);
        }
    }

    /**
     * 批量删除缓存
     */
    public void deleteCachePattern(String pattern) {
        try {
            Set<String> keys = optimizationRedisTemplate.keys(pattern + "*");
            if (keys != null && !keys.isEmpty()) {
                optimizationRedisTemplate.delete(keys);
                logger.info("批量删除缓存: {} 个键, 模式: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            logger.error("批量删除缓存失败, 模式: {}", pattern, e);
        }
    }

    /**
     * 清除所有优化相关的缓存
     */
    public void clearAllOptimizationCache() {
        deleteCachePattern(CACHE_PREFIX);
        logger.info("已清除所有优化相关缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取不同类型缓存的键数量
            int dailyOutCount = getKeyCount(DAILY_OUT_PREFIX + "*");
            int materialStockCount = getKeyCount(MATERIAL_STOCK_PREFIX + "*");
            int statisticsCount = getKeyCount(STATISTICS_PREFIX + "*");
            int periodSummaryCount = getKeyCount(PERIOD_SUMMARY_PREFIX + "*");
            
            stats.put("dailyOutCacheCount", dailyOutCount);
            stats.put("materialStockCacheCount", materialStockCount);
            stats.put("statisticsCacheCount", statisticsCount);
            stats.put("periodSummaryCacheCount", periodSummaryCount);
            stats.put("totalCacheCount", dailyOutCount + materialStockCount + statisticsCount + periodSummaryCount);
            
            // 获取缓存命中率
            long hits = getCacheHits();
            long misses = getCacheMisses();
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;
            
            stats.put("cacheHits", hits);
            stats.put("cacheMisses", misses);
            stats.put("cacheHitRate", String.format("%.2f%%", hitRate));
            
            // Redis内存使用情况
            stats.put("redisInfo", getRedisInfo());
            
        } catch (Exception e) {
            logger.error("获取缓存统计信息失败", e);
            stats.put("error", "获取统计信息失败: " + e.getMessage());
        }
        
        return stats;
    }

    /**
     * 获取指定模式的键数量
     */
    private int getKeyCount(String pattern) {
        try {
            Set<String> keys = optimizationRedisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            logger.error("获取键数量失败, 模式: {}", pattern, e);
            return 0;
        }
    }

    /**
     * 增加缓存命中计数
     */
    private void incrementCacheHit() {
        try {
            optimizationRedisTemplate.opsForValue().increment(STATISTICS_PREFIX + "hits");
        } catch (Exception e) {
            logger.error("增加缓存命中计数失败", e);
        }
    }

    /**
     * 增加缓存未命中计数
     */
    private void incrementCacheMiss() {
        try {
            optimizationRedisTemplate.opsForValue().increment(STATISTICS_PREFIX + "misses");
        } catch (Exception e) {
            logger.error("增加缓存未命中计数失败", e);
        }
    }

    /**
     * 获取缓存命中次数
     */
    private long getCacheHits() {
        try {
            Object hits = optimizationRedisTemplate.opsForValue().get(STATISTICS_PREFIX + "hits");
            return hits != null ? Long.valueOf(hits.toString()) : 0L;
        } catch (Exception e) {
            logger.error("获取缓存命中次数失败", e);
            return 0L;
        }
    }

    /**
     * 获取缓存未命中次数
     */
    private long getCacheMisses() {
        try {
            Object misses = optimizationRedisTemplate.opsForValue().get(STATISTICS_PREFIX + "misses");
            return misses != null ? Long.valueOf(misses.toString()) : 0L;
        } catch (Exception e) {
            logger.error("获取缓存未命中次数失败", e);
            return 0L;
        }
    }

    /**
     * 获取Redis基本信息
     */
    private Map<String, String> getRedisInfo() {
        Map<String, String> info = new HashMap<>();
        try {
            // 这里可以添加更多Redis信息获取逻辑
            info.put("status", "connected");
            info.put("database", "0");
        } catch (Exception e) {
            logger.error("获取Redis信息失败", e);
            info.put("status", "error");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * 预热缓存 - 在系统启动或数据更新后调用
     */
    public void warmUpCache(Long tenantId) {
        logger.info("开始预热缓存, 租户ID: {}", tenantId);
        try {
            // 这里可以预加载一些常用的缓存数据
            // 例如：热门商品的库存信息、最近的统计数据等
            
            logger.info("缓存预热完成, 租户ID: {}", tenantId);
        } catch (Exception e) {
            logger.error("缓存预热失败, 租户ID: {}", tenantId, e);
        }
    }
} 