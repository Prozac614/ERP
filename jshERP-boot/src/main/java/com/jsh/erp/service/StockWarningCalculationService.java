package com.jsh.erp.service;

import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.mappers.*;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 库存预警计算服务
 * 用于自动计算商品的最低安全库存阈值
 * 
 * @author jishenghua
 */
@Service
public class StockWarningCalculationService {
    
    private Logger logger = LoggerFactory.getLogger(StockWarningCalculationService.class);
    
    @Resource
    private MaterialMapper materialMapper;
    
    @Resource
    private MaterialInitialStockMapper materialInitialStockMapper;
    
    @Resource
    private DepotMapper depotMapper;
    
    @Resource
    private DepotItemService depotItemService;
    
    @Resource
    private MaterialService materialService;
    
    @Resource
    private UserService userService;

    @Resource
    private MaterialExtendService materialExtendService;

    @Resource(name = "stockWarningTaskExecutor")
    private Executor stockWarningTaskExecutor;

    // 任务状态管理
    private static final Map<String, CalculationTask> taskMap = new ConcurrentHashMap<>();
    
    /**
     * 计算任务状态类
     */
    public static class CalculationTask {
        private String taskId;
        private String status; // RUNNING, COMPLETED, FAILED
        private int totalCount;
        private AtomicInteger processedCount = new AtomicInteger(0);
        private AtomicInteger successCount = new AtomicInteger(0);
        private AtomicInteger failedCount = new AtomicInteger(0);
        private Date startTime;
        private Date endTime;
        private String errorMessage;
        private Long userId;
        
        public CalculationTask(String taskId, int totalCount, Long userId) {
            this.taskId = taskId;
            this.totalCount = totalCount;
            this.userId = userId;
            this.status = "RUNNING";
            this.startTime = new Date();
        }
        
        // Getters and setters
        public String getTaskId() { return taskId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalCount() { return totalCount; }
        public int getProcessedCount() { return processedCount.get(); }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailedCount() { return failedCount.get(); }
        public Date getStartTime() { return startTime; }
        public Date getEndTime() { return endTime; }
        public void setEndTime(Date endTime) { this.endTime = endTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Long getUserId() { return userId; }
        
        public void incrementProcessed() { processedCount.incrementAndGet(); }
        public void incrementSuccess() { successCount.incrementAndGet(); }
        public void incrementFailed() { failedCount.incrementAndGet(); }
        
        public double getProgress() {
            return totalCount > 0 ? (double) processedCount.get() / totalCount * 100 : 0;
        }
    }
    
    /**
     * 开始库存预警计算任务
     *
     * @return 任务ID
     */
    public String startStockWarningCalculation() {
        try {
            Long userId = userService.getCurrentUser().getId();
            String taskId = "stock_warning_" + System.currentTimeMillis() + "_" + userId;

            // 在主线程中获取所有有效商品和仓库（避免异步线程中的租户上下文问题）
            List<Material> materials = getAllActiveMaterials();
            List<Depot> depots = getAllActiveDepots();

            if (materials.isEmpty()) {
                throw new RuntimeException("没有找到需要计算的商品");
            }

            if (depots.isEmpty()) {
                throw new RuntimeException("没有找到有效的仓库");
            }

            // 创建计算任务
            CalculationTask task = new CalculationTask(taskId, materials.size(), userId);
            taskMap.put(taskId, task);

            // 异步执行计算，传入预先获取的仓库列表
            new Thread(() -> executeCalculation(task, materials, depots)).start();

            logger.info("库存预警计算任务已启动，任务ID: {}, 商品数量: {}, 仓库数量: {}", taskId, materials.size(), depots.size());

            return taskId;

        } catch (Exception e) {
            logger.error("启动库存预警计算任务失败", e);
            throw new RuntimeException("启动计算任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        CalculationTask task = taskMap.get(taskId);
        if (task == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "NOT_FOUND");
            result.put("message", "任务不存在");
            return result;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("status", task.getStatus());
        result.put("totalCount", task.getTotalCount());
        result.put("processedCount", task.getProcessedCount());
        result.put("successCount", task.getSuccessCount());
        result.put("failedCount", task.getFailedCount());
        result.put("progress", Math.round(task.getProgress() * 100.0) / 100.0);
        result.put("startTime", task.getStartTime());
        result.put("endTime", task.getEndTime());
        result.put("errorMessage", task.getErrorMessage());
        
        return result;
    }
    
    /**
     * 获取所有有效商品
     */
    private List<Material> getAllActiveMaterials() {
        MaterialExample example = new MaterialExample();
        example.createCriteria()
                .andEnabledEqualTo(true)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        return materialMapper.selectByExample(example);
    }
    
    /**
     * 执行计算任务
     */
    private void executeCalculation(CalculationTask task, List<Material> materials, List<Depot> depots) {
        try {
            logger.info("开始执行库存预警计算，任务ID: {}, 商品数量: {}, 仓库数量: {}",
                    task.getTaskId(), materials.size(), depots.size());

            for (Material material : materials) {
                try {
                    // 计算该商品的最低安全库存阈值，传入预先获取的仓库列表
                    calculateMaterialSafeStock(material, depots);
                    task.incrementSuccess();

                } catch (Exception e) {
                    logger.error("计算商品{}的安全库存失败", material.getId(), e);
                    task.incrementFailed();
                }

                task.incrementProcessed();

                // 每处理100个商品记录一次日志
                if (task.getProcessedCount() % 100 == 0) {
                    logger.info("任务进度: {}/{}, 成功: {}, 失败: {}",
                            task.getProcessedCount(), task.getTotalCount(),
                            task.getSuccessCount(), task.getFailedCount());
                }
            }

            task.setStatus("COMPLETED");
            task.setEndTime(new Date());

            logger.info("库存预警计算任务完成，任务ID: {}, 总数: {}, 成功: {}, 失败: {}",
                    task.getTaskId(), task.getTotalCount(), task.getSuccessCount(), task.getFailedCount());

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setEndTime(new Date());
            logger.error("库存预警计算任务执行失败，任务ID: {}", task.getTaskId(), e);
        }
    }
    
    /**
     * 计算单个商品的安全库存并更新到数据库
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    private void calculateMaterialSafeStock(Material material, List<Depot> depots) throws Exception {
        logger.info("开始计算商品{}({})的安全库存", material.getName(), material.getId());

        // 计算平均日销量
        BigDecimal averageDailySales = materialService.calculateAverageDailySales(material.getId());

        // 计算最低安全库存阈值（6个月的销量）
        BigDecimal lowSafeStock = materialService.calculateLowSafeStock(averageDailySales);

        // 如果计算出的安全库存为0，则跳过更新
        if (lowSafeStock.compareTo(BigDecimal.ZERO) <= 0) {
            logger.info("商品{}({})的安全库存为0，跳过更新", material.getName(), material.getId());
            return;
        }

        logger.info("商品{}({})将更新{}个仓库的安全库存，安全库存值：{}",
                material.getName(), material.getId(), depots.size(), lowSafeStock);

        // 为每个仓库更新该商品的最低安全库存
        for (Depot depot : depots) {
            updateMaterialSafeStock(material.getId(), depot.getId(), lowSafeStock);
        }

        logger.info("商品{}({})安全库存更新完成", material.getName(), material.getId());
    }

    /**
     * 计算单个商品的安全库存并更新到数据库（重载方法，保持向后兼容）
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    private void calculateMaterialSafeStock(Material material) throws Exception {
        // 获取所有仓库
        List<Depot> depots = getAllActiveDepots();
        // 调用带仓库参数的方法
        calculateMaterialSafeStock(material, depots);
    }

    /**
     * 获取所有有效仓库
     */
    private List<Depot> getAllActiveDepots() {
        DepotExample example = new DepotExample();
        example.createCriteria()
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        return depotMapper.selectByExample(example);
    }
    
    /**
     * 更新商品在指定仓库的安全库存
     */
    private void updateMaterialSafeStock(Long materialId, Long depotId, BigDecimal lowSafeStock) {
        try {
            logger.debug("更新商品{}在仓库{}的安全库存为{}", materialId, depotId, lowSafeStock);

            // 查找是否已存在记录
            MaterialInitialStockExample example = new MaterialInitialStockExample();
            example.createCriteria()
                    .andMaterialIdEqualTo(materialId)
                    .andDepotIdEqualTo(depotId)
                    .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);

            List<MaterialInitialStock> existingList = materialInitialStockMapper.selectByExample(example);

            if (existingList != null && !existingList.isEmpty()) {
                // 更新现有记录
                MaterialInitialStock existing = existingList.get(0);
                BigDecimal oldLowSafeStock = existing.getLowSafeStock();
                existing.setLowSafeStock(lowSafeStock);
                int updateCount = materialInitialStockMapper.updateByPrimaryKeySelective(existing);
                logger.info("更新商品{}在仓库{}的安全库存：{} -> {}，影响行数：{}",
                        materialId, depotId, oldLowSafeStock, lowSafeStock, updateCount);
            } else {
                // 创建新记录
                MaterialInitialStock newRecord = new MaterialInitialStock();
                newRecord.setMaterialId(materialId);
                newRecord.setDepotId(depotId);
                newRecord.setNumber(BigDecimal.ZERO); // 初始库存设为0
                newRecord.setLowSafeStock(lowSafeStock);
                newRecord.setDeleteFlag(BusinessConstants.DELETE_FLAG_EXISTS);
                int insertCount = materialInitialStockMapper.insertSelective(newRecord);
                logger.info("新增商品{}在仓库{}的安全库存记录：{}，影响行数：{}",
                        materialId, depotId, lowSafeStock, insertCount);
            }

        } catch (Exception e) {
            logger.error("更新商品{}在仓库{}的安全库存失败", materialId, depotId, e);
            throw e;
        }
    }
    
    /**
     * 清理已完成的任务（避免内存泄漏）
     */
    public void cleanupCompletedTasks() {
        Iterator<Map.Entry<String, CalculationTask>> iterator = taskMap.entrySet().iterator();
        Date cutoffTime = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000); // 24小时前

        while (iterator.hasNext()) {
            Map.Entry<String, CalculationTask> entry = iterator.next();
            CalculationTask task = entry.getValue();

            if (("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus()))
                    && task.getEndTime() != null && task.getEndTime().before(cutoffTime)) {
                iterator.remove();
                logger.info("清理已完成的任务: {}", task.getTaskId());
            }
        }
    }

    /**
     * 测试单个商品的安全库存计算
     *
     * @param materialId 商品ID
     * @return 计算结果详情
     */
    public Map<String, Object> testSingleMaterialCalculation(Long materialId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 获取商品信息
        Material material = materialMapper.selectByPrimaryKey(materialId);
        if (material == null) {
            throw new RuntimeException("商品不存在，ID: " + materialId);
        }

        result.put("materialId", materialId);
        result.put("materialName", material.getName());

        // 获取商品编码（从MaterialExtend表中获取默认的barCode）
        String barCode = "";
        try {
            Long meId = materialExtendService.selectIdByMaterialIdAndDefaultFlag(materialId, "1");
            if (meId != null && meId > 0) {
                MaterialExtend me = materialExtendService.getMaterialExtend(meId);
                if (me != null) {
                    barCode = me.getBarCode();
                }
            }
        } catch (Exception e) {
            logger.warn("获取商品{}的编码失败", materialId, e);
        }
        result.put("barCode", barCode);

        // 计算平均日销量
        BigDecimal averageDailySales = materialService.calculateAverageDailySales(materialId);
        result.put("averageDailySales", averageDailySales);

        // 计算最低安全库存
        BigDecimal lowSafeStock = materialService.calculateLowSafeStock(averageDailySales);
        result.put("calculatedLowSafeStock", lowSafeStock);

        // 获取当前安全库存设置
        List<Depot> depots = getAllActiveDepots();
        List<Map<String, Object>> currentSettings = new ArrayList<>();

        for (Depot depot : depots) {
            MaterialInitialStock safeStock = materialService.getSafeStock(materialId, depot.getId());
            Map<String, Object> setting = new HashMap<>();
            setting.put("depotId", depot.getId());
            setting.put("depotName", depot.getName());
            setting.put("currentLowSafeStock", safeStock.getLowSafeStock());
            setting.put("currentHighSafeStock", safeStock.getHighSafeStock());
            currentSettings.add(setting);
        }

        result.put("currentSafeStockSettings", currentSettings);
        result.put("depotCount", depots.size());
        result.put("calculationTime", new Date());

        logger.info("测试商品{}的安全库存计算完成，平均日销量：{}，建议安全库存：{}",
                materialId, averageDailySales, lowSafeStock);

        return result;
    }

    /**
     * 强制计算并更新单个商品的安全库存
     *
     * @param materialId 商品ID
     * @return 计算和更新结果
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Map<String, Object> forceCalculateAndUpdateSingleMaterial(Long materialId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 获取商品信息
        Material material = materialMapper.selectByPrimaryKey(materialId);
        if (material == null) {
            throw new RuntimeException("商品不存在，ID: " + materialId);
        }

        logger.info("=== 开始强制计算并更新商品{}({})的安全库存 ===", material.getName(), materialId);

        result.put("materialId", materialId);
        result.put("materialName", material.getName());
        result.put("startTime", new Date());

        try {
            // 获取所有仓库（在主线程中获取，避免租户上下文问题）
            List<Depot> depots = getAllActiveDepots();

            // 执行计算和更新
            calculateMaterialSafeStock(material, depots);
            List<Map<String, Object>> updatedSettings = new ArrayList<>();

            for (Depot depot : depots) {
                MaterialInitialStockExample example = new MaterialInitialStockExample();
                example.createCriteria()
                        .andMaterialIdEqualTo(materialId)
                        .andDepotIdEqualTo(depot.getId())
                        .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);

                List<MaterialInitialStock> stockList = materialInitialStockMapper.selectByExample(example);

                Map<String, Object> setting = new HashMap<>();
                setting.put("depotId", depot.getId());
                setting.put("depotName", depot.getName());

                if (stockList != null && !stockList.isEmpty()) {
                    MaterialInitialStock stock = stockList.get(0);
                    setting.put("lowSafeStock", stock.getLowSafeStock());
                    setting.put("highSafeStock", stock.getHighSafeStock());
                    setting.put("updated", true);
                } else {
                    setting.put("lowSafeStock", null);
                    setting.put("highSafeStock", null);
                    setting.put("updated", false);
                }

                updatedSettings.add(setting);
            }

            result.put("updatedSettings", updatedSettings);
            result.put("depotCount", depots.size());
            result.put("status", "SUCCESS");
            result.put("endTime", new Date());

            logger.info("=== 商品{}({})的安全库存强制计算并更新完成 ===", material.getName(), materialId);

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            result.put("endTime", new Date());

            logger.error("商品{}({})的安全库存强制计算并更新失败", material.getName(), materialId, e);
            throw e;
        }

        return result;
    }
}
