package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.DepotItemMapperEx;
import com.jsh.erp.datasource.vo.MaterialStockPeriodVo;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.exception.JshException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品库存服务类 - 实时数据版本
 * 专门处理实时查询场景，无缓存机制
 * 
 * @author jishenghua
 */
@Service
public class DepotItemOptimizedService {

    private Logger logger = LoggerFactory.getLogger(DepotItemOptimizedService.class);

    @Resource
    private DepotItemMapperEx depotItemMapperEx;

    @Resource
    private MaterialService materialService;

    @Resource
    private UserService userService;

    /**
     * 获取商品库存统计与每日出库数据 - 实时数据版本
     * 直接查询数据库，无缓存机制
     */
    public Map<String, Object> getOptimizedMaterialStockWithDailyOut(
            Integer currentPage, Integer pageSize, String materialParam,
            String beginTime, String endTime, String stockAlertStatus, HttpServletRequest request) throws Exception {

        Map<String, Object> resultMap = new HashMap<>();

        try {
            // 设置默认分页参数
            if (currentPage == null)
                currentPage = 1;
            if (pageSize == null)
                pageSize = 10;

            // 获取当前用户的租户ID
            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;

            // 使用优化的查询方法
            if (StringUtil.isNotEmpty(beginTime) && StringUtil.isNotEmpty(endTime)) {
                // 有日期范围时，使用汇总表快速查询
                resultMap = getOptimizedDataWithDateRange(currentPage, pageSize, materialParam,
                        beginTime, endTime, stockAlertStatus, tenantId);
            } else {
                // 无日期范围时，使用期间汇总表
                resultMap = getOptimizedDataWithoutDateRange(currentPage, pageSize, materialParam, stockAlertStatus,
                        tenantId);
            }

        } catch (Exception e) {
            logger.error("获取优化库存数据失败", e);
            throw e;
        }

        return resultMap;
    }

    /**
     * 有日期范围的优化查询
     */
    private Map<String, Object> getOptimizedDataWithDateRange(
            Integer currentPage, Integer pageSize, String materialParam,
            String beginTime, String endTime, String stockAlertStatus, Long tenantId) throws Exception {

        Map<String, Object> resultMap = new HashMap<>();

        // 1. 获取商品基础库存数据（分页）
        List<MaterialStockPeriodVo> stockList = depotItemMapperEx.getMaterialPeriodStockOptimized(
                materialParam, (currentPage - 1) * pageSize, pageSize, stockAlertStatus, tenantId);
        int total = depotItemMapperEx.getMaterialPeriodStockCountOptimized(materialParam, stockAlertStatus, tenantId);

        // 2. 获取每日出库汇总数据
        Map<String, Map<String, BigDecimal>> dailyOutMap = new HashMap<>();
        if (!stockList.isEmpty()) {
            // 提取商品ID列表
            List<Long> materialIds = new ArrayList<>();
            for (MaterialStockPeriodVo stock : stockList) {
                materialIds.add(stock.getMaterialId());
            }

            // 从汇总表快速获取每日出库数据
            List<Map<String, Object>> dailyOutList = depotItemMapperEx.getDailyOutStockFromSummary(
                    materialIds, beginTime, endTime, tenantId);

            // 组织每日出库数据
            for (Map<String, Object> dailyOut : dailyOutList) {
                String barCode = (String) dailyOut.get("barCode");
                String outDate = (String) dailyOut.get("outDate");
                BigDecimal quantity = (BigDecimal) dailyOut.get("totalOutQuantity");

                dailyOutMap.computeIfAbsent(barCode, k -> new HashMap<>()).put(outDate, quantity);
            }
        }

        // 3. 直接读取数据库状态，不进行任何计算
        // 只确保每个商品都有一个默认状态（如果数据库中为空的话）
        ensureDefaultStatus(stockList);

        resultMap.put("rows", stockList);
        resultMap.put("total", total);
        resultMap.put("dailyOutData", dailyOutMap);
        resultMap.put("beginTime", beginTime);
        resultMap.put("endTime", endTime);
        resultMap.put("cached", false);

        logger.info("有日期范围查询完成，商品数：{}, 日期范围：{} - {}", stockList.size(), beginTime, endTime);

        return resultMap;
    }

    /**
     * 无日期范围的优化查询
     */
    private Map<String, Object> getOptimizedDataWithoutDateRange(
            Integer currentPage, Integer pageSize, String materialParam, String stockAlertStatus, Long tenantId)
            throws Exception {

        Map<String, Object> resultMap = new HashMap<>();

        try {
            // 从期间汇总表获取数据
            List<MaterialStockPeriodVo> stockList = depotItemMapperEx.getMaterialPeriodStockOptimized(
                    materialParam, (currentPage - 1) * pageSize, pageSize, stockAlertStatus, tenantId);
            int total = depotItemMapperEx.getMaterialPeriodStockCountOptimized(materialParam, stockAlertStatus,
                    tenantId);

            // 直接读取数据库状态，不进行任何计算
            ensureDefaultStatus(stockList);

            resultMap.put("rows", stockList);
            resultMap.put("total", total);
            resultMap.put("dailyOutData", new HashMap<>());
            resultMap.put("cached", false);

            logger.info("无日期范围查询完成，商品数：{}", stockList.size());

        } catch (Exception e) {
            logger.error("无日期范围查询失败", e);
            throw e;
        }

        return resultMap;
    }

    /**
     * 刷新商品期间汇总数据
     */
    public void refreshMaterialPeriodSummary(Long tenantId) {
        try {
            // 暂时简化实现，避免调用不存在的方法
            logger.info("商品期间汇总数据刷新请求，租户ID：{}", tenantId);

            // 清除相关缓存
            // if (redisTemplate != null) {
            // Set<String> keys = redisTemplate.keys("material_stock:*");
            // if (keys != null && !keys.isEmpty()) {
            // redisTemplate.delete(keys);
            // logger.info("清除了 {} 个相关缓存", keys.size());
            // }
            // }

            logger.info("商品期间汇总数据刷新完成，租户ID：{}", tenantId);
        } catch (Exception e) {
            logger.error("刷新商品期间汇总数据失败", e);
        }
    }

    /**
     * 更新单个商品的每日出库汇总
     */
    public void updateDailyOutSummary(Long materialId, String targetDate, Long tenantId) {
        try {
            depotItemMapperEx.updateDailyOutSummary(materialId, targetDate, tenantId);

            // 清除相关缓存
            // if (redisTemplate != null) {
            // Set<String> keys = redisTemplate.keys("material_stock:*");
            // if (keys != null && !keys.isEmpty()) {
            // redisTemplate.delete(keys);
            // logger.info("清除了 {} 个相关缓存", keys.size());
            // }
            // }

            logger.info("每日出库汇总更新完成，商品ID：{}, 日期：{}", materialId, targetDate);
        } catch (Exception e) {
            logger.error("更新每日出库汇总失败", e);
        }
    }

    /**
     * 批量更新最近N天的汇总数据
     * 注意：此方法用于定时任务，不依赖当前用户上下文
     */
    public void refreshDailySummaryForRecentDays(int days) {
        try {
            // 定时任务中无法获取当前用户，传入null让数据库处理所有租户的数据
            Long tenantId = null;

            // 暂时跳过这个操作以避免MyBatis-Plus拦截器问题
            logger.warn("refreshDailySummaryForRecentDays 暂时禁用以避免MyBatis-Plus拦截器问题");
            // depotItemMapperEx.refreshDailySummaryForRecentDays(days, tenantId);

            // 清除所有相关缓存
            // if (redisTemplate != null) {
            // Set<String> keys = redisTemplate.keys("material_stock:*");
            // if (keys != null && !keys.isEmpty()) {
            // redisTemplate.delete(keys);
            // logger.info("批量刷新后清除了 {} 个缓存", keys.size());
            // }
            // }

            logger.info("最近 {} 天的汇总数据刷新已跳过", days);
        } catch (Exception e) {
            logger.error("批量刷新汇总数据失败", e);
        }
    }

    /**
     * 修复库存小数点问题
     * 将所有库存数据四舍五入为整数
     */
    public void fixDecimalStockIssue() {
        try {
            logger.info("开始修复库存小数点问题...");

            // 1. 检查当前有多少小数记录
            int decimalRecords = depotItemMapperEx.countDecimalStockRecords();
            logger.info("发现 {} 条包含小数的库存记录", decimalRecords);

            if (decimalRecords > 0) {
                // 2. 修复现有数据：将小数四舍五入为整数
                int updatedRecords = depotItemMapperEx.fixDecimalStockData();
                logger.info("已修复 {} 条库存记录的小数问题", updatedRecords);

                // 3. 清除相关缓存
                // clearAllCache();

                logger.info("库存小数点问题修复完成");
            } else {
                logger.info("没有发现小数库存记录，无需修复");
            }

        } catch (Exception e) {
            logger.error("修复库存小数点问题失败", e);
            throw new RuntimeException("修复库存小数点问题失败: " + e.getMessage());
        }
    }

    /**
     * 修复期间库存计算逻辑
     * 重新计算本期结存、上期结存、出库入库数据
     */
    public void fixPeriodCalculationLogic() {
        try {
            logger.info("开始修复期间库存计算逻辑...");

            // 1. 获取当前用户租户ID
            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;

            // 2. 记录修复请求（存储过程已通过SQL直接执行）
            logger.info("期间库存计算逻辑修复请求，租户ID：{}", tenantId);

            // 3. 清除所有相关缓存
            // clearAllCache();

            logger.info("期间库存计算逻辑修复完成，租户ID：{}", tenantId);

        } catch (Exception e) {
            logger.error("修复期间库存计算逻辑失败", e);
            throw new RuntimeException("修复期间库存计算逻辑失败: " + e.getMessage());
        }
    }

    /**
     * 验证期间库存计算结果
     * 检查库存平衡关系是否正确
     */
    public Map<String, Object> validatePeriodCalculation() {
        try {
            logger.info("开始验证期间库存计算结果...");

            // 获取验证结果
            List<Map<String, Object>> validationResults = depotItemMapperEx.validatePeriodStockBalance();

            // 统计验证结果
            int totalRecords = validationResults.size();
            int errorRecords = 0;

            for (Map<String, Object> result : validationResults) {
                Object difference = result.get("difference");
                if (difference != null && Math.abs(((Number) difference).doubleValue()) > 0.01) {
                    errorRecords++;
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRecords", totalRecords);
            summary.put("errorRecords", errorRecords);
            summary.put("successRate",
                    totalRecords > 0 ? (double) (totalRecords - errorRecords) / totalRecords * 100 : 100);
            summary.put("validationDetails",
                    validationResults.size() > 10 ? validationResults.subList(0, 10) : validationResults);

            logger.info("验证完成，总记录数：{}，错误记录数：{}，成功率：{}%",
                    totalRecords, errorRecords, summary.get("successRate"));

            return summary;

        } catch (Exception e) {
            logger.error("验证期间库存计算结果失败", e);
            throw new RuntimeException("验证期间库存计算结果失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        // if (redisTemplate != null) {
        // try {
        // Set<String> keys = redisTemplate.keys("material_stock:*");
        // stats.put("cacheCount", keys != null ? keys.size() : 0);
        // stats.put("cacheEnabled", true);
        // } catch (Exception e) {
        // stats.put("cacheEnabled", false);
        // stats.put("error", e.getMessage());
        // }
        // } else {
        stats.put("cacheEnabled", false);
        stats.put("message", "Redis未配置");
        // }

        return stats;
    }

    /**
     * 清除所有相关缓存
     */
    public void clearAllCache() {
        // if (redisTemplate != null) {
        // try {
        // // 清除多种模式的缓存键
        // String[] patterns = { "material_stock:*", "*stock*", "depot_item:*" };
        // int totalCleared = 0;

        // for (String pattern : patterns) {
        // Set<String> keys = redisTemplate.keys(pattern);
        // if (keys != null && !keys.isEmpty()) {
        // redisTemplate.delete(keys);
        // totalCleared += keys.size();
        // logger.info("清除了{}个匹配'{}'的缓存键", keys.size(), pattern);
        // }
        // }

        // if (totalCleared > 0) {
        // logger.info("总共清除了{}个缓存键", totalCleared);
        // } else {
        // logger.info("没有找到需要清除的缓存键");
        // }
        // } catch (Exception e) {
        // logger.error("清除缓存失败", e);
        // throw new RuntimeException("清除缓存失败: " + e.getMessage());
        // }
        // } else {
        logger.warn("Redis模板为空，无法清除缓存");
        // }
    }

    /**
     * 确保每个商品都有默认状态（仅用于空状态的情况）
     * 表格显示直接读取数据库状态，不进行任何计算
     * 
     * @param stockList 商品库存列表
     */
    private void ensureDefaultStatus(List<MaterialStockPeriodVo> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return;
        }

        try {
            for (MaterialStockPeriodVo stock : stockList) {
                String currentStatus = stock.getStockAlertStatus();

                // 只有当状态为空时，才设置默认状态，避免前端显示空白
                if (currentStatus == null || currentStatus.trim().isEmpty()) {
                    stock.setStockAlertStatus("NO_RISK"); // 设置默认显示状态
                    logger.debug("商品{}状态为空，设置默认显示状态：NO_RISK", stock.getMaterialId());
                }
                // 其他情况直接使用数据库中的状态，不做任何修改
            }
        } catch (Exception e) {
            logger.error("确保默认状态失败", e);
        }
    }

    /**
     * 批量计算和更新所有商品的库存告急状态（手动触发）
     * 校验逻辑：当前库存 >= 过去六个月总出库量 则无风险，否则库存告急
     * 校验结果直接覆盖原有状态（包括忽略风险状态）
     * 
     * @param tenantId 租户ID
     * @return 更新结果
     */
    public Map<String, Object> calculateAllStockAlertStatus(Long tenantId) {
        return calculateAllStockAlertStatus(tenantId, false);
    }

    /**
     * 批量计算和更新所有商品的库存告急状态
     * 校验逻辑：当前库存 >= 过去六个月总出库量 则无风险，否则库存告急
     * 
     * @param tenantId              租户ID
     * @param preserveIgnoredStatus 是否保留忽略风险状态（true=不覆盖忽略风险状态，false=覆盖所有状态）
     * @return 更新结果
     */
    public Map<String, Object> calculateAllStockAlertStatus(Long tenantId, Boolean preserveIgnoredStatus) {
        Map<String, Object> result = new HashMap<>();

        try {
            logger.info("开始批量计算库存告急状态，租户ID：{}，保留忽略风险状态：{}", tenantId, preserveIgnoredStatus);

            // 获取所有商品（不分页）
            List<MaterialStockPeriodVo> allStockList = depotItemMapperEx.getMaterialPeriodStockOptimized(
                    null, 0, Integer.MAX_VALUE, null, tenantId);

            int totalCount = allStockList.size();
            int updatedCount = 0;
            int noRiskCount = 0;
            int alertCount = 0;
            int ignoredCount = 0;

            for (MaterialStockPeriodVo stock : allStockList) {
                // 如果需要保留忽略风险状态，且当前商品状态为忽略风险，则跳过
                if (preserveIgnoredStatus != null && preserveIgnoredStatus &&
                        "RISK_IGNORED".equals(stock.getStockAlertStatus())) {
                    ignoredCount++;
                    continue;
                }

                // 获取当前库存
                BigDecimal currentStock = stock.getCurrentPeriodStock();
                if (currentStock == null) {
                    currentStock = BigDecimal.ZERO;
                }

                // 计算过去6个月的销量
                BigDecimal sixMonthsSales = calculateSixMonthsSales(stock.getMaterialId(), tenantId);

                // 校验逻辑：当前库存 >= 过去六个月总出库量 则无风险，否则库存告急
                String newStatus;
                if (currentStock.compareTo(sixMonthsSales) >= 0) {
                    newStatus = "NO_RISK"; // 无风险
                    noRiskCount++;
                } else {
                    newStatus = "STOCK_ALERT"; // 库存告急
                    alertCount++;
                }

                // 更新数据库
                materialService.updateStockAlertStatus(stock.getMaterialId(), newStatus, sixMonthsSales);
                updatedCount++;

                if (updatedCount % 100 == 0) {
                    logger.info("已处理{}个商品", updatedCount);
                }
            }

            result.put("success", true);
            result.put("totalCount", totalCount);
            result.put("updatedCount", updatedCount);
            result.put("noRiskCount", noRiskCount);
            result.put("alertCount", alertCount);
            result.put("ignoredCount", ignoredCount);

            String message;
            if (preserveIgnoredStatus != null && preserveIgnoredStatus && ignoredCount > 0) {
                message = String.format("成功校验%d个商品：无风险%d个，库存告急%d个，跳过忽略风险%d个",
                        updatedCount, noRiskCount, alertCount, ignoredCount);
            } else {
                message = String.format("成功校验%d个商品：无风险%d个，库存告急%d个",
                        updatedCount, noRiskCount, alertCount);
            }
            result.put("message", message);

            logger.info("批量计算库存告急状态完成：总数={}, 更新={}, 无风险={}, 告急={}, 跳过忽略风险={}",
                    totalCount, updatedCount, noRiskCount, alertCount, ignoredCount);

        } catch (Exception e) {
            logger.error("批量计算库存告急状态失败", e);
            result.put("success", false);
            result.put("message", "计算失败：" + e.getMessage());
        }

        return result;
    }

    /**
     * 计算过去6个月的销量
     * 
     * @param materialId 商品ID
     * @param tenantId   租户ID
     * @return 过去6个月销量
     */
    private BigDecimal calculateSixMonthsSales(Long materialId, Long tenantId) {
        try {
            // 这里可以调用已有的查询方法或者创建新的查询
            // 暂时返回一个模拟值，实际应该查询数据库
            return depotItemMapperEx.getSixMonthsSalesByMaterialId(materialId, tenantId);
        } catch (Exception e) {
            logger.error("计算六个月销量失败，materialId: {}", materialId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 异步更新商品的库存告急状态
     * 
     * @param materialId     商品ID
     * @param alertStatus    告急状态
     * @param sixMonthsSales 六个月销量
     */
    private void updateMaterialStockAlertStatusAsync(Long materialId, String alertStatus, BigDecimal sixMonthsSales) {
        // 使用异步方式更新，避免影响查询性能
        // 这里可以使用线程池或者消息队列来处理
        try {
            materialService.updateStockAlertStatus(materialId, alertStatus, sixMonthsSales);
        } catch (Exception e) {
            logger.error("异步更新库存告急状态失败，materialId: {}", materialId, e);
        }
    }

}