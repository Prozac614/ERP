package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialPriceHistory;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface MaterialPriceHistoryMapperEx {
    
    /**
     * 根据商品扩展ID查询价格历史
     * @param materialExtendId 商品扩展ID
     * @return 价格历史列表（按生效日期倒序）
     */
    List<MaterialPriceHistory> getByMaterialExtendId(@Param("materialExtendId") Long materialExtendId);
    
    /**
     * 根据商品ID查询价格历史
     * @param materialId 商品ID
     * @return 价格历史列表（按生效日期倒序）
     */
    List<MaterialPriceHistory> getByMaterialId(@Param("materialId") Long materialId);
    
    /**
     * 根据时间范围查询价格变更记录
     * @param beginDate 开始日期
     * @param endDate 结束日期
     * @return 价格历史列表
     */
    List<MaterialPriceHistory> getByDateRange(@Param("beginDate") Date beginDate, @Param("endDate") Date endDate);
    
    /**
     * 查询最近的价格记录
     * @param materialExtendId 商品扩展ID
     * @return 最近的价格记录
     */
    MaterialPriceHistory getLatestByMaterialExtendId(@Param("materialExtendId") Long materialExtendId);
}

