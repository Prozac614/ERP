package com.jsh.erp.controller;

import com.jsh.erp.service.StockWarningCalculationService;
import com.jsh.erp.utils.BaseResponseInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 库存预警控制器
 * 
 * @author jishenghua
 */
@RestController
@RequestMapping(value = "/stockWarning")
@Api(tags = {"库存预警管理"})
public class StockWarningController {
    
    private Logger logger = LoggerFactory.getLogger(StockWarningController.class);
    
    @Resource
    private StockWarningCalculationService stockWarningCalculationService;
    
    /**
     * 开始库存预警检查计算
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/startCalculation")
    @ApiOperation(value = "开始库存预警检查计算")
    public BaseResponseInfo startCalculation(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            String taskId = stockWarningCalculationService.startStockWarningCalculation();

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("message", "库存预警计算任务已启动，同时会更新当前库存表");
            result.put("note", "此操作会同时更新当前库存数据和计算安全库存阈值");

            res.code = 200;
            res.data = result;

            logger.info("库存预警计算任务启动成功，任务ID: {}，将同时更新当前库存表", taskId);

        } catch (Exception e) {
            logger.error("启动库存预警计算任务失败", e);
            res.code = 500;
            res.data = "启动计算任务失败: " + e.getMessage();
        }
        return res;
    }
    
    /**
     * 获取计算任务状态
     * 
     * @param taskId 任务ID
     * @param request
     * @return
     */
    @GetMapping(value = "/getTaskStatus")
    @ApiOperation(value = "获取计算任务状态")
    public BaseResponseInfo getTaskStatus(@RequestParam("taskId") String taskId, 
                                        HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Map<String, Object> taskStatus = stockWarningCalculationService.getTaskStatus(taskId);
            
            res.code = 200;
            res.data = taskStatus;
            
        } catch (Exception e) {
            logger.error("获取任务状态失败，任务ID: {}", taskId, e);
            res.code = 500;
            res.data = "获取任务状态失败: " + e.getMessage();
        }
        return res;
    }
    
    /**
     * 清理已完成的任务
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/cleanupTasks")
    @ApiOperation(value = "清理已完成的任务")
    public BaseResponseInfo cleanupTasks(HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            stockWarningCalculationService.cleanupCompletedTasks();

            res.code = 200;
            res.data = "任务清理完成";

            logger.info("库存预警计算任务清理完成");

        } catch (Exception e) {
            logger.error("清理任务失败", e);
            res.code = 500;
            res.data = "清理任务失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 测试单个商品的安全库存计算
     *
     * @param materialId 商品ID
     * @param request
     * @return
     */
    @GetMapping(value = "/testCalculation")
    @ApiOperation(value = "测试单个商品的安全库存计算")
    public BaseResponseInfo testCalculation(@RequestParam("materialId") Long materialId,
                                          HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Map<String, Object> result = stockWarningCalculationService.testSingleMaterialCalculation(materialId);

            res.code = 200;
            res.data = result;

            logger.info("测试商品{}的安全库存计算完成", materialId);

        } catch (Exception e) {
            logger.error("测试商品{}的安全库存计算失败", materialId, e);
            res.code = 500;
            res.data = "测试计算失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 强制执行单个商品的安全库存计算并更新数据库
     *
     * @param materialId 商品ID
     * @param request
     * @return
     */
    @PostMapping(value = "/forceCalculateAndUpdate")
    @ApiOperation(value = "强制执行单个商品的安全库存计算并更新数据库")
    public BaseResponseInfo forceCalculateAndUpdate(@RequestParam("materialId") Long materialId,
                                                   HttpServletRequest request) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Map<String, Object> result = stockWarningCalculationService.forceCalculateAndUpdateSingleMaterial(materialId);

            res.code = 200;
            res.data = result;

            logger.info("强制计算并更新商品{}的安全库存完成", materialId);

        } catch (Exception e) {
            logger.error("强制计算并更新商品{}的安全库存失败", materialId, e);
            res.code = 500;
            res.data = "强制计算并更新失败: " + e.getMessage();
        }
        return res;
    }
}
