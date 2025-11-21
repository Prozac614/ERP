package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialCurrentStock;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MaterialCurrentStockMapperEx {

    int batchInsert(List<MaterialCurrentStock> list);

    List<MaterialCurrentStock> getCurrentStockMapByIdList(
            @Param("materialIdList") List<Long> materialIdList);

    void updateUnitPriceByMId(
            @Param("currentUnitPrice") BigDecimal currentUnitPrice,
            @Param("materialId") Long materialId);

    BigDecimal getCurrentUnitPriceByMId(@Param("materialId") Long materialId);

    void batchDeleteByDepots(@Param("ids") String ids[]);

    List<MaterialCurrentStock> getStockByMaterialAndDepotList(
            @Param("stockParams") List<Map<String, Long>> stockParams);
}