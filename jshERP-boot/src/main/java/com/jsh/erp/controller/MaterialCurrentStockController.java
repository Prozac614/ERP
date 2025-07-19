package com.jsh.erp.controller;

import com.jsh.erp.service.MaterialService;
import com.jsh.erp.service.DepotService;
import com.jsh.erp.service.DepotItemService;
import com.jsh.erp.datasource.entities.Material;
import com.jsh.erp.datasource.entities.Depot;
import com.jsh.erp.datasource.entities.MaterialCurrentStock;
import com.jsh.erp.datasource.entities.MaterialCurrentStockExample;
import com.jsh.erp.datasource.mappers.MaterialCurrentStockMapper;
import com.jsh.erp.utils.BaseResponseInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品当前库存控制器
 * 用于初始化和管理当前库存数据
 * 
 * @author jishenghua
 */
@RestController
@RequestMapping(value = "/materialCurrentStock")
@Api(tags = {"商品当前库存管理"})
public class MaterialCurrentStockController {
    
    private Logger logger = LoggerFactory.getLogger(MaterialCurrentStockController.class);
    
    @Resource
    private MaterialService materialService;
    
    @Resource
    private DepotService depotService;
    
    @Resource
    private DepotItemService depotItemService;
    
    @Resource
    private MaterialCurrentStockMapper materialCurrentStockMapper;
    
    /**
     * 初始化所有商品的当前库存数据
     * 
     * @param request
     * @return
     */
    @PostMapping(value = "/initializeCurrentStock")
    @ApiOperation(value = "初始化所有商品的当前库存数据")
    public BaseResponseInfo initializeCurrentStock(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            logger.info("开始初始化所有商品的当前库存数据");
            
            // 获取所有商品
            List<Material> materials = materialService.getAllList();
            // 获取所有仓库
            List<Depot> depots = depotService.getAllList();
            
            if (materials.isEmpty()) {
                res.code = 400;
                res.data = "没有找到商品数据，请先添加商品";
                return res;
            }
            
            if (depots.isEmpty()) {
                res.code = 400;
                res.data = "没有找到仓库数据，请先添加仓库";
                return res;
            }
            
            int totalCount = materials.size() * depots.size();
            int successCount = 0;
            int failedCount = 0;
            int updatedCount = 0;
            int createdCount = 0;
            
            logger.info("需要初始化的库存记录数：{} (商品数：{} × 仓库数：{})", 
                    totalCount, materials.size(), depots.size());
            
            for (Material material : materials) {
                for (Depot depot : depots) {
                    try {
                        // 检查是否已存在记录
                        boolean exists = checkCurrentStockExists(material.getId(), depot.getId());
                        
                        // 更新或创建当前库存记录
                        depotItemService.updateCurrentStockFun(material.getId(), depot.getId());
                        
                        if (exists) {
                            updatedCount++;
                        } else {
                            createdCount++;
                        }
                        successCount++;
                        
                        if (successCount % 100 == 0) {
                            logger.info("初始化进度：{}/{} (成功:{}, 失败:{}, 新建:{}, 更新:{})", 
                                    successCount + failedCount, totalCount, 
                                    successCount, failedCount, createdCount, updatedCount);
                        }
                    } catch (Exception e) {
                        logger.error("初始化商品{}({})在仓库{}({})的当前库存失败", 
                                material.getId(), material.getName(), depot.getId(), depot.getName(), e);
                        failedCount++;
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount);
            result.put("successCount", successCount);
            result.put("failedCount", failedCount);
            result.put("createdCount", createdCount);
            result.put("updatedCount", updatedCount);
            result.put("materialCount", materials.size());
            result.put("depotCount", depots.size());
            result.put("message", "当前库存数据初始化完成");
            
            res.code = 200;
            res.data = result;
            
            logger.info("当前库存数据初始化完成，总数：{}，成功：{}，失败：{}，新建：{}，更新：{}", 
                    totalCount, successCount, failedCount, createdCount, updatedCount);
            
        } catch (Exception e) {
            logger.error("初始化当前库存数据失败", e);
            res.code = 500;
            res.data = "初始化失败: " + e.getMessage();
        }
        return res;
    }
    
    /**
     * 检查当前库存记录是否存在
     */
    private boolean checkCurrentStockExists(Long materialId, Long depotId) {
        try {
            MaterialCurrentStockExample example = new MaterialCurrentStockExample();
            example.createCriteria()
                    .andMaterialIdEqualTo(materialId)
                    .andDepotIdEqualTo(depotId)
                    .andDeleteFlagNotEqualTo("1");
            
            List<MaterialCurrentStock> list = materialCurrentStockMapper.selectByExample(example);
            return list != null && !list.isEmpty();
        } catch (Exception e) {
            logger.error("检查当前库存记录失败", e);
            return false;
        }
    }
    
    /**
     * 批量更新指定商品的当前库存
     * 
     * @param materialIds 商品ID列表，用逗号分隔
     * @param request
     * @return
     */
    @PostMapping(value = "/batchUpdateCurrentStock")
    @ApiOperation(value = "批量更新指定商品的当前库存")
    public BaseResponseInfo batchUpdateCurrentStock(@RequestParam("materialIds") String materialIds,
                                                   HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            logger.info("开始批量更新商品当前库存，商品IDs：{}", materialIds);
            
            int result = materialService.batchSetMaterialCurrentStock(materialIds);
            
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("result", result);
            resultMap.put("message", "批量更新当前库存完成");
            
            res.code = 200;
            res.data = resultMap;
            
            logger.info("批量更新商品当前库存完成，结果：{}", result);
            
        } catch (Exception e) {
            logger.error("批量更新商品当前库存失败", e);
            res.code = 500;
            res.data = "批量更新失败: " + e.getMessage();
        }
        return res;
    }
    
    /**
     * 检查当前库存数据状态
     * 
     * @param request
     * @return
     */
    @GetMapping(value = "/checkCurrentStockStatus")
    @ApiOperation(value = "检查当前库存数据状态")
    public BaseResponseInfo checkCurrentStockStatus(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 获取商品总数
            List<Material> materials = materialService.getAllList();
            // 获取仓库总数
            List<Depot> depots = depotService.getAllList();
            
            // 获取当前库存记录总数
            MaterialCurrentStockExample example = new MaterialCurrentStockExample();
            example.createCriteria().andDeleteFlagNotEqualTo("1");
            long currentStockCount = materialCurrentStockMapper.countByExample(example);
            
            int expectedRecords = materials.size() * depots.size();
            
            Map<String, Object> result = new HashMap<>();
            result.put("materialCount", materials.size());
            result.put("depotCount", depots.size());
            result.put("expectedRecords", expectedRecords);
            result.put("actualRecords", currentStockCount);
            result.put("missingRecords", Math.max(0, expectedRecords - currentStockCount));
            result.put("completeness", expectedRecords > 0 ? (double)currentStockCount / expectedRecords * 100 : 0);
            result.put("message", "当前库存状态检查完成");
            
            res.code = 200;
            res.data = result;
            
            logger.info("当前库存状态检查完成，商品数：{}，仓库数：{}，预期记录数：{}，实际记录数：{}，完整度：{}%", 
                    materials.size(), depots.size(), expectedRecords, currentStockCount, 
                    expectedRecords > 0 ? (double)currentStockCount / expectedRecords * 100 : 0);
            
        } catch (Exception e) {
            logger.error("检查当前库存状态失败", e);
            res.code = 500;
            res.data = "检查失败: " + e.getMessage();
        }
        return res;
    }
    
    /**
     * 清理无效的当前库存记录
     * 
     * @param request
     * @return
     */
    @PostMapping(value = "/cleanupInvalidRecords")
    @ApiOperation(value = "清理无效的当前库存记录")
    public BaseResponseInfo cleanupInvalidRecords(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            logger.info("开始清理无效的当前库存记录");
            
            // 这里可以添加清理逻辑
            // 例如：删除商品ID或仓库ID不存在的记录
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "清理功能待实现");
            result.put("suggestion", "建议使用SQL脚本手动清理无效记录");
            
            res.code = 200;
            res.data = result;
            
        } catch (Exception e) {
            logger.error("清理无效当前库存记录失败", e);
            res.code = 500;
            res.data = "清理失败: " + e.getMessage();
        }
        return res;
    }
}
