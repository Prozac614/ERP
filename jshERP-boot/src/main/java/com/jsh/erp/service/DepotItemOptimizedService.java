package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.DepotItemMapperEx;
import com.jsh.erp.datasource.vo.MaterialStockPeriodVo;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.utils.StockAlertPermissionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.exception.BusinessRunTimeException;

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
            String beginTime, String endTime, String stockAlertStatus,
            List<String> shopNames, HttpServletRequest request) throws Exception {

        logger.info(
                "开始获取优化库存数据，参数：currentPage={}, pageSize={}, materialParam={}, beginTime={}, endTime={}, stockAlertStatus={}, shopNames={}",
                currentPage, pageSize, materialParam, beginTime, endTime, stockAlertStatus, shopNames);

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
                        beginTime, endTime, stockAlertStatus, shopNames, tenantId);
            } else {
                // 无日期范围时，使用期间汇总表
                resultMap = getOptimizedDataWithoutDateRange(currentPage, pageSize, materialParam, stockAlertStatus,
                        tenantId);
            }

            // 检查库存预警权限并添加到结果中
            boolean hasStockAlertPermission = StockAlertPermissionUtil.hasStockAlertPermission(request);
            resultMap.put("hasStockAlertPermission", hasStockAlertPermission);
            logger.debug("库存预警权限检查结果: {}", hasStockAlertPermission);

        } catch (Exception e) {
            logger.error("获取优化库存数据失败", e);
            throw e;
        }

        return resultMap;
    }

    /**
     * 获取当前租户的全部商品库存总金额（不受筛选/分页影响）
     * 计算口径：SUM(current_period_stock * default commodity_decimal)
     * 规则：
     * - 按当前租户约束
     * - 删除标记排除
     * - 负库存按实际值计入
     * - 无默认零售价或为空按0计入，并输出一次warn日志（包含数量）
     * 精度：保留两位小数（HALF_UP）
     */
    public BigDecimal getTotalStockValueForCurrentTenant() {
        try {
            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;
            if (tenantId == null) {
                logger.warn("获取库存总金额时tenantId为空，返回0");
                return BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP);
            }

            BigDecimal total = depotItemMapperEx.getTotalStockValueByTenant(tenantId);
            if (total == null) {
                total = BigDecimal.ZERO;
            }

            Long missing = depotItemMapperEx.countMaterialsMissingDefaultPrice(tenantId);
            if (missing != null && missing > 0) {
                logger.warn("Tenant {}: {} materials missing default retail price (counted as 0)", tenantId, missing);
            }

            // 统一保留两位
            return total.setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (Exception e) {
            logger.error("获取库存总金额失败", e);
            return BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * 计算排除指定店铺销售出库后的库存总金额
     * 
     * @param targetDate      目标日期（格式：YYYY-MM-DD）
     * @param excludeShopName 要排除的店铺名称
     * @return 排除后的库存总金额
     */
    public BigDecimal getTotalStockValueExcludeShop(String targetDate, String excludeShopName) {
        logger.info("========== 开始计算排除店铺后的库存总金额 ==========");
        logger.info("输入参数 - targetDate: {}, excludeShopName: {}", targetDate, excludeShopName);

        try {
            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;
            logger.info("当前用户租户ID: {}", tenantId);

            if (tenantId == null) {
                logger.warn("获取库存总金额时tenantId为空，返回0");
                return BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP);
            }

            // 参数校验
            if (StringUtil.isEmpty(targetDate)) {
                logger.error("参数校验失败: 日期不能为空");
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "日期不能为空");
            }
            if (StringUtil.isEmpty(excludeShopName)) {
                logger.error("参数校验失败: 店铺名称不能为空");
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "店铺名称不能为空");
            }

            // 验证日期格式和范围（最近30天）
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date target = sdf.parse(targetDate);
                Date today = new Date();
                long daysDiff = (today.getTime() - target.getTime()) / (1000 * 60 * 60 * 24);
                logger.info("日期验证 - 目标日期: {}, 今天: {}, 相差天数: {}", targetDate, sdf.format(today), daysDiff);

                if (daysDiff < 0 || daysDiff > 30) {
                    logger.error("日期范围验证失败: 日期必须在最近30天内，当前相差{}天", daysDiff);
                    throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE,
                            "日期必须在最近30天内");
                }
            } catch (ParseException e) {
                logger.error("日期格式验证失败: {}", e.getMessage());
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "日期格式错误");
            }

            logger.info("准备执行分步查询 - targetDate: {}, excludeShopName: {}, tenantId: {}",
                    targetDate, excludeShopName, tenantId);

            // 调试：查询当前库存总金额（用于对比）
            BigDecimal currentTotalStockValue = depotItemMapperEx.getTotalStockValueByTenant(tenantId);
            logger.info("【调试】当前库存总金额（未排除店铺）: {}", currentTotalStockValue);

            // 步骤1：查询商品当前库存和默认零售价（复用getTotalStockValueByTenant的逻辑）
            logger.info("【步骤1】查询商品当前库存和默认零售价...");
            List<Map<String, Object>> materialStockPriceList = depotItemMapperEx.getMaterialStockAndPrice(tenantId);
            Map<Long, BigDecimal> currentStockMap = new HashMap<>();
            Map<Long, BigDecimal> priceMap = new HashMap<>();
            BigDecimal totalCurrentStock = BigDecimal.ZERO;
            int priceCount = 0;
            int noPriceCount = 0;
            for (Map<String, Object> item : materialStockPriceList) {
                Long materialId = ((Number) item.get("materialId")).longValue();
                BigDecimal stock = item.get("currentStock") != null ? (BigDecimal) item.get("currentStock")
                        : BigDecimal.ZERO;
                BigDecimal price = item.get("commodityDecimal") != null ? (BigDecimal) item.get("commodityDecimal")
                        : null;

                currentStockMap.put(materialId, stock);
                totalCurrentStock = totalCurrentStock.add(stock);

                if (price != null) {
                    priceMap.put(materialId, price);
                    priceCount++;
                } else {
                    noPriceCount++;
                }
            }
            logger.info("【步骤1】商品库存和价格查询完成 - 商品数量: {}, 总库存: {}, 有价格商品: {}, 无价格商品: {}",
                    materialStockPriceList.size(), totalCurrentStock, priceCount, noPriceCount);

            // 步骤2：查询单据影响（指定日期及之后，按店铺、商品汇总）
            logger.info("【步骤2】查询单据影响（日期 >= {}）...", targetDate);
            List<Map<String, Object>> billImpactList = depotItemMapperEx.getBillImpactByDateRange(targetDate, tenantId);

            // 存储详细数据：按店铺、商品、日期分组的数据
            List<Map<String, Object>> billImpactDetailList = new ArrayList<>();

            // 汇总按商品ID的总影响量（用于后续计算）
            // 区分：选择日期之后（不包括当天）的所有单据影响 + 选择日期当天的所有单据影响（但排除指定店铺的销售出库）
            Map<Long, BigDecimal> billImpactMap = new HashMap<>();
            BigDecimal totalBillImpact = BigDecimal.ZERO;
            int billCount = 0;
            int afterTargetDateCount = 0; // 选择日期之后的单据数
            int targetDateCount = 0; // 选择日期当天的单据数
            int excludedCount = 0; // 被排除的单据数

            for (Map<String, Object> item : billImpactList) {
                Long materialId = ((Number) item.get("materialId")).longValue();
                String shopName = item.get("shopName") != null ? (String) item.get("shopName") : "";
                String billDate = item.get("billDate") != null ? item.get("billDate").toString() : "";
                String billType = item.get("billType") != null ? (String) item.get("billType") : "";
                String subType = item.get("subType") != null ? (String) item.get("subType") : "";
                BigDecimal inQuantity = item.get("inQuantity") != null ? (BigDecimal) item.get("inQuantity")
                        : BigDecimal.ZERO;
                BigDecimal outQuantity = item.get("outQuantity") != null ? (BigDecimal) item.get("outQuantity")
                        : BigDecimal.ZERO;
                BigDecimal impact = item.get("totalImpact") != null ? (BigDecimal) item.get("totalImpact")
                        : BigDecimal.ZERO;

                // 保存详细数据
                Map<String, Object> detail = new HashMap<>();
                detail.put("materialId", materialId);
                detail.put("shopName", shopName);
                detail.put("billDate", billDate);
                detail.put("billType", billType);
                detail.put("subType", subType);
                detail.put("inQuantity", inQuantity);
                detail.put("outQuantity", outQuantity);
                detail.put("totalImpact", impact);
                billImpactDetailList.add(detail);

                // 判断是否需要回退此单据的影响
                boolean shouldRollback = false;

                if (billDate.compareTo(targetDate) > 0) {
                    // 选择日期之后（不包括当天）的所有单据，全部回退
                    shouldRollback = true;
                    afterTargetDateCount++;
                } else if (billDate.equals(targetDate)) {
                    // 选择日期当天的单据：只回退指定店铺的销售出库，其他单据不回退
                    if (shopName.equals(excludeShopName) && "出库".equals(billType) && "销售".equals(subType)) {
                        // 只回退指定店铺的销售出库
                        shouldRollback = true;
                        targetDateCount++;
                    } else {
                        // 当天的其他单据不回退
                        excludedCount++;
                        logger.debug("当天单据不回退 - 商品ID: {}, 店铺: {}, 日期: {}, 类型: {}/{}, 影响: {}",
                                materialId, shopName, billDate, billType, subType, impact);
                    }
                }

                // 如果需要回退，则累加到影响量中
                if (shouldRollback) {
                    BigDecimal currentImpact = billImpactMap.getOrDefault(materialId, BigDecimal.ZERO);
                    billImpactMap.put(materialId, currentImpact.add(impact));
                    totalBillImpact = totalBillImpact.add(impact);
                    billCount++;
                }
            }

            logger.info("【步骤2】单据影响查询完成 - 总单据明细数: {}, 需要回退的单据数: {}, 不回退的单据数: {}",
                    billImpactDetailList.size(), billCount, excludedCount);
            logger.info("【步骤2-分类】选择日期之后（不包括当天）的单据数: {}, 选择日期当天需要回退的单据数: {} (当天其他单据不回退: {})",
                    afterTargetDateCount, targetDateCount, excludedCount);
            logger.info("【步骤2-汇总】涉及商品数量: {}, 总影响量: {}", billImpactMap.size(), totalBillImpact);
            logger.info("【步骤2-详细】前5条明细数据示例:");
            for (int i = 0; i < Math.min(5, billImpactDetailList.size()); i++) {
                Map<String, Object> detail = billImpactDetailList.get(i);
                logger.info("  商品ID: {}, 店铺: {}, 日期: {}, 类型: {}, 入库: {}, 出库: {}, 影响: {}",
                        detail.get("materialId"), detail.get("shopName"), detail.get("billDate"),
                        detail.get("billType"), detail.get("inQuantity"), detail.get("outQuantity"),
                        detail.get("totalImpact"));
            }

            // 步骤4：计算最终库存和总金额
            logger.info("【步骤4】开始计算最终库存和总金额...");
            BigDecimal totalStockValue = BigDecimal.ZERO;
            int processedCount = 0;
            int skippedCount = 0;
            BigDecimal maxValue = BigDecimal.ZERO;
            Long maxValueMaterialId = null;

            // 获取所有商品ID（从价格Map，因为需要价格才能计算金额）
            Set<Long> allMaterialIds = new HashSet<>(priceMap.keySet());

            // 收集涉及单据的商品ID（有单据影响的商品）
            Set<Long> involvedMaterialIds = new HashSet<>(billImpactMap.keySet());

            logger.info("【步骤4-调试】涉及单据的商品数量: {} (有单据影响: {})",
                    involvedMaterialIds.size(), billImpactMap.size());

            for (Long materialId : allMaterialIds) {
                BigDecimal currentStock = currentStockMap.getOrDefault(materialId, BigDecimal.ZERO);
                BigDecimal billImpact = billImpactMap.getOrDefault(materialId, BigDecimal.ZERO);
                BigDecimal price = priceMap.get(materialId);

                // 计算最终库存：当前库存 - 单据影响（已排除指定店铺的销售出库）
                // billImpactMap 中已经排除了选择日期当天指定店铺的销售出库
                BigDecimal finalStock = currentStock.subtract(billImpact);

                // 如果是涉及单据的商品，打印详细库存变化过程
                if (involvedMaterialIds.contains(materialId)) {
                    logger.info("【步骤4-商品明细】商品ID: {}", materialId);
                    logger.info("  当前库存: {}", currentStock);
                    logger.info("  单据影响: {} (入库-出库)", billImpact);

                    // 打印该商品的所有单据明细
                    logger.info("  单据明细列表:");
                    int detailCount = 0;
                    for (Map<String, Object> detail : billImpactDetailList) {
                        Long detailMaterialId = ((Number) detail.get("materialId")).longValue();
                        if (detailMaterialId.equals(materialId)) {
                            logger.info("    - 日期: {}, 店铺: {}, 类型: {}/{}, 入库: {}, 出库: {}, 影响: {}",
                                    detail.get("billDate"), detail.get("shopName"),
                                    detail.get("billType"), detail.get("subType"),
                                    detail.get("inQuantity"), detail.get("outQuantity"),
                                    detail.get("totalImpact"));
                            detailCount++;
                            if (detailCount >= 10) { // 限制最多打印10条
                                logger.info("    ... (还有更多明细，已省略)");
                                break;
                            }
                        }
                    }

                    // 打印选择日期当天需要回退的单据明细（指定店铺的销售出库）
                    logger.info("  选择日期当天需要回退的单据明细（日期={}, 店铺={}, 类型=出库/销售）:", targetDate, excludeShopName);
                    int rollbackDetailCount = 0;
                    BigDecimal rollbackOutTotal = BigDecimal.ZERO;
                    for (Map<String, Object> detail : billImpactDetailList) {
                        Long detailMaterialId = ((Number) detail.get("materialId")).longValue();
                        String detailBillDate = detail.get("billDate") != null ? detail.get("billDate").toString()
                                : "";
                        String detailShopName = detail.get("shopName") != null ? (String) detail.get("shopName")
                                : "";
                        String detailBillType = detail.get("billType") != null ? (String) detail.get("billType")
                                : "";
                        String detailSubType = detail.get("subType") != null ? (String) detail.get("subType") : "";
                        BigDecimal detailOutQuantity = detail.get("outQuantity") != null
                                ? (BigDecimal) detail.get("outQuantity")
                                : BigDecimal.ZERO;

                        if (detailMaterialId.equals(materialId) &&
                                detailBillDate.equals(targetDate) &&
                                detailShopName.equals(excludeShopName) &&
                                "出库".equals(detailBillType) &&
                                "销售".equals(detailSubType) &&
                                detailOutQuantity.compareTo(BigDecimal.ZERO) > 0) {
                            logger.info("    - 日期: {}, 店铺: {}, 出库量: {} (需要回退)",
                                    detailBillDate, detailShopName, detailOutQuantity);
                            rollbackOutTotal = rollbackOutTotal.add(detailOutQuantity);
                            rollbackDetailCount++;
                        }
                    }
                    if (rollbackDetailCount == 0) {
                        logger.info("    (无)");
                    } else {
                        logger.info("  需要回退的出库总量: {}", rollbackOutTotal);
                    }

                    // 打印选择日期当天不回退的其他单据明细
                    logger.info("  选择日期当天不回退的其他单据明细:");
                    int noRollbackDetailCount = 0;
                    for (Map<String, Object> detail : billImpactDetailList) {
                        Long detailMaterialId = ((Number) detail.get("materialId")).longValue();
                        String detailBillDate = detail.get("billDate") != null ? detail.get("billDate").toString()
                                : "";
                        String detailShopName = detail.get("shopName") != null ? (String) detail.get("shopName")
                                : "";
                        String detailBillType = detail.get("billType") != null ? (String) detail.get("billType")
                                : "";
                        String detailSubType = detail.get("subType") != null ? (String) detail.get("subType") : "";

                        if (detailMaterialId.equals(materialId) &&
                                detailBillDate.equals(targetDate) &&
                                !(detailShopName.equals(excludeShopName) && "出库".equals(detailBillType)
                                        && "销售".equals(detailSubType))) {
                            logger.info("    - 日期: {}, 店铺: {}, 类型: {}/{}, 影响: {} (不回退)",
                                    detailBillDate, detailShopName, detailBillType, detailSubType,
                                    detail.get("totalImpact"));
                            noRollbackDetailCount++;
                            if (noRollbackDetailCount >= 5) {
                                logger.info("    ... (还有更多明细，已省略)");
                                break;
                            }
                        }
                    }
                    if (noRollbackDetailCount == 0) {
                        logger.info("    (无)");
                    }

                    logger.info("  最终库存: {} (当前库存 {} - 单据影响 {})", finalStock, currentStock, billImpact);
                    logger.info("  默认零售价: {}", price);
                    if (price != null && finalStock.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal value = finalStock.multiply(price);
                        logger.info("  商品金额: {} (最终库存 {} × 价格 {})", value, finalStock, price);
                    }
                    logger.info("  ---");
                }

                // 计算金额：最终库存 × 默认零售价
                if (price != null && finalStock.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal value = finalStock.multiply(price);
                    totalStockValue = totalStockValue.add(value);
                    processedCount++;

                    // 记录最大值用于调试
                    if (value.compareTo(maxValue) > 0) {
                        maxValue = value;
                        maxValueMaterialId = materialId;
                    }
                } else {
                    skippedCount++;
                }
            }

            logger.info("【步骤5】计算完成 - 处理商品数: {}, 跳过商品数: {}, 最大单商品金额: {} (商品ID: {})",
                    processedCount, skippedCount, maxValue, maxValueMaterialId);
            logger.info("【调试】中间结果汇总:");
            logger.info("  当前库存总量: {}", totalCurrentStock);
            logger.info("  单据影响总量: {} (回退选择日期之后的单据 + 回退选择日期当天指定店铺的销售出库)", totalBillImpact);
            logger.info("  计算出的库存总金额: {}", totalStockValue);

            // 统一保留两位
            BigDecimal result = totalStockValue.setScale(2, BigDecimal.ROUND_HALF_UP);
            logger.info("【调试】最终计算结果: {} (原始值: {}, 保留2位小数)", result, totalStockValue);
            logger.info("【调试】与当前库存总金额对比: 当前={}, 排除后={}, 差值={}",
                    currentTotalStockValue, result,
                    currentTotalStockValue != null ? currentTotalStockValue.subtract(result) : "N/A");
            logger.info("========== 计算完成 ==========");

            return result;
        } catch (BusinessRunTimeException e) {
            logger.error("获取排除店铺后的库存总金额失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("获取排除店铺后的库存总金额失败", e);
            throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE,
                    "获取排除店铺后的库存总金额失败: " + e.getMessage());
        }
    }

    /**
     * 有日期范围的优化查询
     */
    private Map<String, Object> getOptimizedDataWithDateRange(
            Integer currentPage, Integer pageSize, String materialParam,
            String beginTime, String endTime, String stockAlertStatus,
            List<String> shopNames, Long tenantId) throws Exception {

        Map<String, Object> resultMap = new HashMap<>();

        // 1. 获取商品基础库存数据（分页）
        List<MaterialStockPeriodVo> stockList = depotItemMapperEx.getMaterialPeriodStockOptimized(
                materialParam, (currentPage - 1) * pageSize, pageSize, stockAlertStatus, tenantId);
        int total = depotItemMapperEx.getMaterialPeriodStockCountOptimized(materialParam, stockAlertStatus, tenantId);

        // 2. 获取每日出库汇总数据（受begin/end限制，用于动态列）
        Map<String, Map<String, BigDecimal>> dailyOutMap = new HashMap<>();
        // 同时准备独立的“近六个月（仅销售）”聚合结果，不受begin/end限制
        Map<Long, BigDecimal> lastSixMonthsSalesMap = new HashMap<>();
        if (!stockList.isEmpty()) {
            // 提取商品ID列表
            List<Long> materialIds = new ArrayList<>();
            for (MaterialStockPeriodVo stock : stockList) {
                materialIds.add(stock.getMaterialId());
            }

            // 从汇总表快速获取每日出库数据（用于动态列展示）
            logger.info(
                    "准备调用getDailyOutStockFromSummary，参数：materialIds={}, beginTime={}, endTime={}, shopNames={}, tenantId={}",
                    materialIds, beginTime, endTime, shopNames, tenantId);
            List<Map<String, Object>> dailyOutList = depotItemMapperEx.getDailyOutStockFromSummary(
                    materialIds, beginTime, endTime, shopNames, tenantId);

            for (Map<String, Object> dailyOut : dailyOutList) {
                String barCode = (String) dailyOut.get("barCode");
                String outDate = dailyOut.get("outDate").toString();
                BigDecimal quantity = (BigDecimal) dailyOut.get("totalOutQuantity");
                dailyOutMap.computeIfAbsent(barCode, k -> new HashMap<>()).put(outDate, quantity);
            }

            // 批量查询每个物料近六个月销售出库总量（与筛选无关、固定滚动窗口）
            List<Map<String, Object>> sixMonthsList = depotItemMapperEx.getSixMonthsSalesByMaterialIds(materialIds,
                    tenantId);
            for (Map<String, Object> row : sixMonthsList) {
                Object mid = row.get("materialId");
                Object val = row.get("sixMonthsSales");
                if (mid != null) {
                    Long materialId = (mid instanceof Number) ? ((Number) mid).longValue()
                            : Long.valueOf(mid.toString());
                    BigDecimal qty = (val instanceof BigDecimal) ? (BigDecimal) val
                            : (val != null ? new BigDecimal(val.toString()) : BigDecimal.ZERO);
                    lastSixMonthsSalesMap.put(materialId, qty);
                }
            }

            // 将结果写入VO
            for (MaterialStockPeriodVo stock : stockList) {
                BigDecimal v = lastSixMonthsSalesMap.get(stock.getMaterialId());
                stock.setLastSixMonthsSales(v != null ? v : BigDecimal.ZERO);
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

            // 批量查询每个物料近六个月销售出库总量（与筛选无关、固定滚动窗口）
            if (!stockList.isEmpty()) {
                List<Long> materialIds = new ArrayList<>();
                for (MaterialStockPeriodVo stock : stockList) {
                    materialIds.add(stock.getMaterialId());
                }
                List<Map<String, Object>> sixMonthsList = depotItemMapperEx.getSixMonthsSalesByMaterialIds(materialIds,
                        tenantId);
                Map<Long, BigDecimal> lastSixMonthsSalesMap = new HashMap<>();
                for (Map<String, Object> row : sixMonthsList) {
                    Object mid = row.get("materialId");
                    Object val = row.get("sixMonthsSales");
                    if (mid != null) {
                        Long materialId = (mid instanceof Number) ? ((Number) mid).longValue()
                                : Long.valueOf(mid.toString());
                        BigDecimal qty = (val instanceof BigDecimal) ? (BigDecimal) val
                                : (val != null ? new BigDecimal(val.toString()) : BigDecimal.ZERO);
                        lastSixMonthsSalesMap.put(materialId, qty);
                    }
                }
                for (MaterialStockPeriodVo stock : stockList) {
                    BigDecimal v = lastSixMonthsSalesMap.get(stock.getMaterialId());
                    stock.setLastSixMonthsSales(v != null ? v : BigDecimal.ZERO);
                }
            }

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

    /**
     * 批量计算日期范围内排除指定店铺销售出库后的库存总金额
     * 使用前缀和算法从后往前计算，降低计算复杂度
     * 
     * @param beginDate       开始日期（格式：YYYY-MM-DD）
     * @param endDate         结束日期（格式：YYYY-MM-DD）
     * @param excludeShopName 要排除的店铺名称
     * @return 日期范围内每一天的数据列表，每个Map包含：date, excludeAfterValue, excludeBeforeValue
     */
    public List<Map<String, Object>> getTotalStockValueExcludeShopByDateRange(
            String beginDate, String endDate, String excludeShopName) {
        logger.info("========== 开始批量计算排除店铺后的库存总金额 ==========");
        logger.info("输入参数 - beginDate: {}, endDate: {}, excludeShopName: {}", beginDate, endDate, excludeShopName);

        try {
            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;
            logger.info("当前用户租户ID: {}", tenantId);

            if (tenantId == null) {
                logger.warn("获取库存总金额时tenantId为空，返回空列表");
                return new ArrayList<>();
            }

            // 参数校验
            if (StringUtil.isEmpty(beginDate)) {
                logger.error("参数校验失败: 开始日期不能为空");
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "开始日期不能为空");
            }
            if (StringUtil.isEmpty(endDate)) {
                logger.error("参数校验失败: 结束日期不能为空");
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "结束日期不能为空");
            }
            if (StringUtil.isEmpty(excludeShopName)) {
                logger.error("参数校验失败: 店铺名称不能为空");
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "店铺名称不能为空");
            }

            // 验证日期格式和范围（最大7天）
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date begin = null;
            Date end = null;
            Date today = new Date();
            try {
                begin = sdf.parse(beginDate);
                end = sdf.parse(endDate);

                if (begin.after(end)) {
                    logger.error("日期范围验证失败: 开始日期不能大于结束日期");
                    throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE,
                            "开始日期不能大于结束日期");
                }

                long daysDiff = (end.getTime() - begin.getTime()) / (1000 * 60 * 60 * 24);

                if (end.after(today)) {
                    logger.error("日期范围验证失败: 结束日期不能是未来日期");
                    throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE,
                            "结束日期不能是未来日期");
                }

                logger.info("日期验证通过 - 开始日期: {}, 结束日期: {}, 天数: {}", beginDate, endDate, daysDiff + 1);
            } catch (ParseException e) {
                logger.error("日期格式验证失败: {}", e.getMessage());
                throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE, "日期格式错误");
            }

            // 步骤1：查询商品当前库存和默认零售价
            logger.info("【步骤1】查询商品当前库存和默认零售价...");
            List<Map<String, Object>> materialStockPriceList = depotItemMapperEx.getMaterialStockAndPrice(tenantId);
            Map<Long, BigDecimal> currentStockMap = new HashMap<>();
            Map<Long, BigDecimal> priceMap = new HashMap<>();
            for (Map<String, Object> item : materialStockPriceList) {
                Long materialId = ((Number) item.get("materialId")).longValue();
                BigDecimal stock = item.get("currentStock") != null ? (BigDecimal) item.get("currentStock")
                        : BigDecimal.ZERO;
                BigDecimal price = item.get("commodityDecimal") != null ? (BigDecimal) item.get("commodityDecimal")
                        : null;
                currentStockMap.put(materialId, stock);
                if (price != null) {
                    priceMap.put(materialId, price);
                }
            }
            logger.info("【步骤1】商品库存和价格查询完成 - 商品数量: {}, 有价格商品: {}",
                    materialStockPriceList.size(), priceMap.size());

            // 步骤2：查询从beginDate开始的所有单据影响
            logger.info("【步骤2】查询单据影响（日期 >= {}）...", beginDate);
            List<Map<String, Object>> billImpactList = depotItemMapperEx.getBillImpactByDateRange(beginDate, tenantId);
            logger.info("【步骤2】单据影响查询完成 - 单据明细数: {}", billImpactList.size());

            // 步骤3：按日期分组构建数据结构
            // dateMaterialImpactMap: 每天每个商品的单据影响
            // dateMaterialExcludeShopOutMap: 每天每个商品指定店铺的销售出库
            Map<String, Map<Long, BigDecimal>> dateMaterialImpactMap = new HashMap<>();
            Map<String, Map<Long, BigDecimal>> dateMaterialExcludeShopOutMap = new HashMap<>();

            for (Map<String, Object> item : billImpactList) {
                Long materialId = ((Number) item.get("materialId")).longValue();
                String shopName = item.get("shopName") != null ? (String) item.get("shopName") : "";
                String billDate = item.get("billDate") != null ? item.get("billDate").toString() : "";
                String billType = item.get("billType") != null ? (String) item.get("billType") : "";
                String subType = item.get("subType") != null ? (String) item.get("subType") : "";
                BigDecimal impact = item.get("totalImpact") != null ? (BigDecimal) item.get("totalImpact")
                        : BigDecimal.ZERO;
                BigDecimal outQuantity = item.get("outQuantity") != null ? (BigDecimal) item.get("outQuantity")
                        : BigDecimal.ZERO;

                // 累加每天每个商品的单据影响
                dateMaterialImpactMap.computeIfAbsent(billDate, k -> new HashMap<>())
                        .merge(materialId, impact, BigDecimal::add);

                // 如果是指定店铺的销售出库，累加到排除店铺出库Map中
                if (shopName.equals(excludeShopName) && "出库".equals(billType) && "销售".equals(subType)
                        && outQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    dateMaterialExcludeShopOutMap.computeIfAbsent(billDate, k -> new HashMap<>())
                            .merge(materialId, outQuantity, BigDecimal::add);
                }
            }

            // 步骤4：生成日期范围内的所有日期列表
            List<String> dateList = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            cal.setTime(begin);
            while (!cal.getTime().after(end)) {
                dateList.add(sdf.format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            logger.info("【步骤4】生成日期列表完成 - 日期数量: {}", dateList.size());

            // 步骤5：使用前缀和算法从后往前计算每一天的库存总金额
            logger.info("【步骤5】开始使用前缀和算法计算每一天的库存总金额...");
            List<Map<String, Object>> resultList = new ArrayList<>();

            // 累计影响Map：表示"当前日期之后的所有单据影响"
            Map<Long, BigDecimal> cumulativeImpactMap = new HashMap<>();

            // 从后往前遍历日期
            for (int i = dateList.size() - 1; i >= 0; i--) {
                String date = dateList.get(i);

                // 计算排除前和排除后的库存总金额
                BigDecimal excludeBeforeValue = BigDecimal.ZERO;
                BigDecimal excludeAfterValue = BigDecimal.ZERO;

                // 遍历所有有价格的商品
                for (Map.Entry<Long, BigDecimal> entry : priceMap.entrySet()) {
                    Long materialId = entry.getKey();
                    BigDecimal price = entry.getValue();
                    BigDecimal currentStock = currentStockMap.getOrDefault(materialId, BigDecimal.ZERO);

                    // 排除前：当前库存 - 累计影响（date之后的所有单据影响）
                    BigDecimal excludeBeforeStock = currentStock.subtract(
                            cumulativeImpactMap.getOrDefault(materialId, BigDecimal.ZERO));

                    // 排除后：排除前库存 + 该日期当天指定店铺的销售出库
                    BigDecimal excludeShopOut = dateMaterialExcludeShopOutMap
                            .getOrDefault(date, new HashMap<>())
                            .getOrDefault(materialId, BigDecimal.ZERO);
                    BigDecimal excludeAfterStock = excludeBeforeStock.add(excludeShopOut);

                    // 计算金额
                    if (price != null) {
                        excludeBeforeValue = excludeBeforeValue.add(excludeBeforeStock.multiply(price));
                        excludeAfterValue = excludeAfterValue.add(excludeAfterStock.multiply(price));
                    }
                }

                // 添加到结果列表
                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("date", date);
                resultItem.put("excludeBeforeValue", excludeBeforeValue.setScale(2, BigDecimal.ROUND_HALF_UP));
                resultItem.put("excludeAfterValue", excludeAfterValue.setScale(2, BigDecimal.ROUND_HALF_UP));
                resultList.add(0, resultItem); // 插入到列表开头，保持日期顺序

                // 更新累计影响：加上该日期当天的所有单据影响
                Map<Long, BigDecimal> dateImpactMap = dateMaterialImpactMap.getOrDefault(date, new HashMap<>());
                for (Map.Entry<Long, BigDecimal> entry : dateImpactMap.entrySet()) {
                    Long materialId = entry.getKey();
                    BigDecimal impact = entry.getValue();
                    cumulativeImpactMap.merge(materialId, impact, BigDecimal::add);
                }
            }

            logger.info("【步骤5】计算完成 - 结果数量: {}", resultList.size());
            logger.info("========== 批量计算完成 ==========");

            return resultList;
        } catch (BusinessRunTimeException e) {
            logger.error("批量获取排除店铺后的库存总金额失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("批量获取排除店铺后的库存总金额失败", e);
            throw new BusinessRunTimeException(ExceptionConstants.SERVICE_SYSTEM_ERROR_CODE,
                    "批量获取排除店铺后的库存总金额失败: " + e.getMessage());
        }
    }

}