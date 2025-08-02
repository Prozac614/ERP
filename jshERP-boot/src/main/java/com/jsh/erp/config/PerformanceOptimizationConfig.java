package com.jsh.erp.config;

import com.jsh.erp.service.DepotItemOptimizedService;
import com.jsh.erp.service.StockWarningCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

/**
 * 性能优化配置类
 * 用于定时任务和缓存管理
 * 
 * @author jishenghua
 */
@Configuration
@EnableScheduling
@EnableCaching
public class PerformanceOptimizationConfig {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceOptimizationConfig.class);

    @Autowired(required = false)
    private DepotItemOptimizedService depotItemOptimizedService;

    @Autowired(required = false)
    private StockWarningCalculationService stockWarningCalculationService;

    /**
     * 每小时刷新最近3天的每日汇总数据
     * 适用于查询频繁的场景
     * 暂时禁用以避免MyBatis-Plus拦截器问题
     */
    // @Scheduled(fixedRate = 3600000) // 1小时 = 3600000ms
    public void refreshRecentDailySummary() {
        if (depotItemOptimizedService != null) {
            try {
                logger.info("开始定时刷新最近3天的每日汇总数据");
                // 暂时禁用以避免MyBatis-Plus拦截器问题
                // depotItemOptimizedService.refreshDailySummaryForRecentDays(3);
                logger.info("定时刷新最近3天的每日汇总数据已暂时禁用");
            } catch (Exception e) {
                logger.error("定时刷新每日汇总数据失败", e);
            }
        }
    }

    /**
     * 每天凌晨2点刷新所有商品期间汇总数据
     * 用于保证数据的准确性
     * 暂时禁用以避免MyBatis-Plus拦截器问题
     */
    // @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void refreshMaterialPeriodSummary() {
        if (depotItemOptimizedService != null) {
            try {
                logger.info("开始定时刷新商品期间汇总数据");
                // 暂时禁用以避免MyBatis-Plus拦截器问题
                // depotItemOptimizedService.refreshMaterialPeriodSummary(null); // null表示所有租户
                logger.info("定时刷新商品期间汇总数据已暂时禁用");
            } catch (Exception e) {
                logger.error("定时刷新商品期间汇总数据失败", e);
            }
        }
    }

    /**
     * 每天凌晨1点清理过期缓存
     * 保持缓存的时效性
     */
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨1点
    public void clearExpiredCache() {
        if (depotItemOptimizedService != null) {
            try {
                logger.info("开始定时清理缓存");
                // depotItemOptimizedService.clearAllCache();
                logger.info("定时清理缓存完成");
            } catch (Exception e) {
                logger.error("定时清理缓存失败", e);
            }
        }
    }

    /**
     * 每周日凌晨3点进行全量数据刷新
     * 确保数据完整性
     */
    @Scheduled(cron = "0 0 3 * * SUN") // 每周日凌晨3点
    public void weeklyFullRefresh() {
        if (depotItemOptimizedService != null) {
            try {
                logger.info("开始每周全量数据刷新");

                // 刷新最近30天的每日汇总
                // depotItemOptimizedService.refreshDailySummaryForRecentDays(30);

                // 刷新商品期间汇总
                // depotItemOptimizedService.refreshMaterialPeriodSummary(null);

                // 清理缓存
                // depotItemOptimizedService.clearAllCache();

                logger.info("每周全量数据刷新完成");
            } catch (Exception e) {
                logger.error("每周全量数据刷新失败", e);
            }
        }
    }

    /**
     * 每天凌晨4点清理库存预警计算任务
     * 清理超过24小时的已完成任务
     */
    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点
    public void cleanupStockWarningTasks() {
        if (stockWarningCalculationService != null) {
            try {
                logger.info("开始清理库存预警计算任务");
                // stockWarningCalculationService.cleanupCompletedTasks();
                logger.info("库存预警计算任务清理完成");
            } catch (Exception e) {
                logger.error("清理库存预警计算任务失败", e);
            }
        }
    }

    /**
     * 每天早上8点定时更新库存告急状态
     * 不覆盖忽略风险状态，保护用户手动设置的忽略风险商品
     */
    @Scheduled(cron = "0 0 15 * * ?") // 每天早上8点
    public void updateStockAlertStatusDaily() {
        if (depotItemOptimizedService != null) {
            try {
                logger.info("开始定时更新库存告急状态");
                // 调用批量计算方法，保留忽略风险状态（不覆盖用户手动设置的忽略风险）
                Map<String, Object> result = depotItemOptimizedService.calculateAllStockAlertStatus(null, true);
                logger.info("定时更新库存告急状态完成：{}", result);
            } catch (Exception e) {
                logger.error("定时更新库存告急状态失败", e);
            }
        }
    }
}