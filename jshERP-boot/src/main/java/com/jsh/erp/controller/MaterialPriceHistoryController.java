package com.jsh.erp.controller;

import com.jsh.erp.datasource.entities.MaterialPriceHistory;
import com.jsh.erp.service.MaterialPriceHistoryService;
import com.jsh.erp.utils.BaseResponseInfo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品价格历史管理
 * 提供价格历史查询接口
 */
@RestController
@RequestMapping(value = "/materialPriceHistory")
public class MaterialPriceHistoryController {

    @Resource
    private MaterialPriceHistoryService materialPriceHistoryService;

    /**
     * 根据商品扩展ID查询价格历史
     * 
     * @param materialExtendId 商品扩展ID
     * @return 价格历史列表
     */
    @GetMapping(value = "/getByMaterialExtendId")
    public BaseResponseInfo getByMaterialExtendId(@RequestParam("materialExtendId") Long materialExtendId) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<MaterialPriceHistory> list = materialPriceHistoryService.getByMaterialExtendId(materialExtendId);
            res.code = 200;
            res.data = list;
        } catch (Exception e) {
            e.printStackTrace();
            res.code = 500;
            res.data = "获取价格历史失败";
        }
        return res;
    }

    /**
     * 根据商品ID查询价格历史
     * 
     * @param materialId 商品ID
     * @return 价格历史列表
     */
    @GetMapping(value = "/getByMaterialId")
    public BaseResponseInfo getByMaterialId(@RequestParam("materialId") Long materialId) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<MaterialPriceHistory> list = materialPriceHistoryService.getByMaterialId(materialId);
            res.code = 200;
            res.data = list;
        } catch (Exception e) {
            e.printStackTrace();
            res.code = 500;
            res.data = "获取价格历史失败";
        }
        return res;
    }

    /**
     * 根据时间范围查询价格变更记录
     * 
     * @param beginDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @return 价格历史列表
     */
    @GetMapping(value = "/getByDateRange")
    public BaseResponseInfo getByDateRange(
            @RequestParam(value = "beginDate", required = false) String beginDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date begin = null;
            Date end = null;
            
            if (beginDate != null && !beginDate.isEmpty()) {
                begin = sdf.parse(beginDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = sdf.parse(endDate);
            }
            
            List<MaterialPriceHistory> list = materialPriceHistoryService.getByDateRange(begin, end);
            res.code = 200;
            res.data = list;
        } catch (Exception e) {
            e.printStackTrace();
            res.code = 500;
            res.data = "获取价格历史失败";
        }
        return res;
    }

    /**
     * 查询最近的价格记录
     * 
     * @param materialExtendId 商品扩展ID
     * @return 最近的价格记录
     */
    @GetMapping(value = "/getLatest")
    public BaseResponseInfo getLatest(@RequestParam("materialExtendId") Long materialExtendId) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            MaterialPriceHistory history = materialPriceHistoryService.getLatestByMaterialExtendId(materialExtendId);
            res.code = 200;
            res.data = history;
        } catch (Exception e) {
            e.printStackTrace();
            res.code = 500;
            res.data = "获取最新价格记录失败";
        }
        return res;
    }
}

