package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.vo.DepotItemStockWarningCount;
import com.jsh.erp.datasource.vo.DepotItemVo4Stock;
import com.jsh.erp.datasource.vo.DepotItemVoBatchNumberList;
import com.jsh.erp.datasource.vo.InOutPriceVo;
import com.jsh.erp.datasource.vo.MaterialStockPeriodVo;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @Author: cjl
 * @Date: 2019/1/24 16:59
 */
public interface DepotItemMapperEx {
        List<DepotItem> selectByConditionDepotItem(
                        @Param("name") String name,
                        @Param("type") Integer type,
                        @Param("remark") String remark,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows);

        Long countsByDepotItem(
                        @Param("name") String name,
                        @Param("type") Integer type,
                        @Param("remark") String remark);

        List<DepotItemVo4DetailByTypeAndMId> findDetailByDepotIdsAndMaterialIdList(
                        @Param("depotIdArray") Long[] depotIdArray,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("inOutManageFlag") Boolean inOutManageFlag,
                        @Param("sku") String sku,
                        @Param("batchNumber") String batchNumber,
                        @Param("number") String number,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("mId") Long mId,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows);

        Long findDetailByDepotIdsAndMaterialIdCount(
                        @Param("depotIdArray") Long[] depotIdArray,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("inOutManageFlag") Boolean inOutManageFlag,
                        @Param("sku") String sku,
                        @Param("batchNumber") String batchNumber,
                        @Param("number") String number,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("mId") Long mId);

        List<DepotItemVo4WithInfoEx> getDetailList(
                        @Param("headerId") Long headerId);

        List<DepotItemVo4WithInfoEx> getBillDetailListByIds(
                        @Param("idList") List<Long> idList);

        List<DepotItemVo4WithInfoEx> getInOutStock(
                        @Param("materialParam") String materialParam,
                        @Param("categoryIdList") List<Long> categoryIdList,
                        @Param("endTime") String endTime,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows);

        int getInOutStockCount(
                        @Param("materialParam") String materialParam,
                        @Param("categoryIdList") List<Long> categoryIdList,
                        @Param("endTime") String endTime);

        List<DepotItemVo4WithInfoEx> getListWithBuyOrSale(
                        @Param("materialParam") String materialParam,
                        @Param("billType") String billType,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("organId") Long organId,
                        @Param("organArray") String[] organArray,
                        @Param("categoryList") List<Long> categoryList,
                        @Param("depotList") List<Long> depotList,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows);

        int getListWithBuyOrSaleCount(
                        @Param("materialParam") String materialParam,
                        @Param("billType") String billType,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("organId") Long organId,
                        @Param("organArray") String[] organArray,
                        @Param("categoryList") List<Long> categoryList,
                        @Param("depotList") List<Long> depotList,
                        @Param("forceFlag") Boolean forceFlag);

        BigDecimal buyOrSaleNumber(
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("meId") Long meId,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("organId") Long organId,
                        @Param("organArray") String[] organArray,
                        @Param("depotList") List<Long> depotList,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("sumType") String sumType);

        BigDecimal buyOrSalePrice(
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("meId") Long meId,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("organId") Long organId,
                        @Param("organArray") String[] organArray,
                        @Param("depotList") List<Long> depotList,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("sumType") String sumType);

        BigDecimal buyOrSalePriceTotal(
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("materialParam") String materialParam,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("organId") Long organId,
                        @Param("organArray") String[] organArray,
                        @Param("categoryList") List<Long> categoryList,
                        @Param("depotList") List<Long> depotList,
                        @Param("forceFlag") Boolean forceFlag);

        List<InOutPriceVo> inOrOutPriceList(
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("creatorArray") String[] creatorArray,
                        @Param("forceFlag") Boolean forceFlag);

        BigDecimal getSkuStockCheckSumByDepotList(
                        @Param("depotList") List<Long> depotList,
                        @Param("meId") Long meId,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime);

        BigDecimal getStockCheckSumByDepotList(
                        @Param("depotList") List<Long> depotList,
                        @Param("mId") Long mId,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime);

        DepotItemVo4Stock getSkuStockByParamWithDepotList(
                        @Param("depotList") List<Long> depotList,
                        @Param("meId") Long meId,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("inOutManageFlag") Boolean inOutManageFlag,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime);

        DepotItemVo4Stock getStockByParamWithDepotList(
                        @Param("depotList") List<Long> depotList,
                        @Param("mId") Long mId,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("inOutManageFlag") Boolean inOutManageFlag,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime);

        /**
         * 通过单据主表id查询所有单据子表数据
         * 
         * @param depotheadId
         * @param enableSerialNumber
         * @return
         */
        List<DepotItem> findDepotItemListBydepotheadId(@Param("depotheadId") Long depotheadId,
                        @Param("enableSerialNumber") String enableSerialNumber);

        /**
         * 根据单据主表id删除单据子表数据
         */
        int batchDeleteDepotItemByDepotHeadIds(@Param("depotheadIds") Long[] depotHeadIds);

        int batchDeleteDepotItemByIds(@Param("updateTime") Date updateTime, @Param("updater") Long updater,
                        @Param("ids") String ids[]);

        List<DepotItem> getDepotItemListListByDepotIds(@Param("depotIds") String[] depotIds);

        List<DepotItem> getDepotItemListListByMaterialIds(@Param("materialIds") String[] materialIds);

        List<DepotItemStockWarningCount> findStockWarningCount(
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows,
                        @Param("materialParam") String materialParam,
                        @Param("depotList") List<Long> depotList,
                        @Param("categoryList") List<Long> categoryList);

        int findStockWarningCountTotal(
                        @Param("materialParam") String materialParam,
                        @Param("depotList") List<Long> depotList,
                        @Param("categoryList") List<Long> categoryList);

        BigDecimal getFinishNumber(
                        @Param("meId") Long meId,
                        @Param("linkId") Long linkId,
                        @Param("linkStr") String linkStr,
                        @Param("noType") String noType,
                        @Param("goToType") String goToType);

        BigDecimal getRealFinishNumber(
                        @Param("meId") Long meId,
                        @Param("linkId") Long linkId,
                        @Param("linkStr") String linkStr,
                        @Param("linkType") String linkType,
                        @Param("currentHeaderId") Long currentHeaderId,
                        @Param("goToType") String goToType);

        List<DepotItemVoBatchNumberList> getBatchNumberList(
                        @Param("number") String number,
                        @Param("name") String name,
                        @Param("depotId") Long depotId,
                        @Param("barCode") String barCode,
                        @Param("batchNumber") String batchNumber,
                        @Param("forceFlag") Boolean forceFlag,
                        @Param("inOutManageFlag") Boolean inOutManageFlag);

        Long getCountByMaterialAndDepot(
                        @Param("mId") Long mId,
                        @Param("depotId") Long depotId);

        List<DepotItemVo4MaterialAndSum> getLinkBillDetailMaterialSum(
                        @Param("linkStr") String linkStr);

        List<DepotItemVo4MaterialAndSum> getBatchBillDetailMaterialSum(
                        @Param("linkStr") String linkStr,
                        @Param("linkType") String linkType,
                        @Param("type") String type);

        Long getCountByMaterialAndBatchNumber(
                        @Param("meId") Long meId,
                        @Param("batchNumber") String batchNumber);

        List<DepotItem> getDepotItemByBatchNumber(
                        @Param("materialExtendId") Long materialExtendId,
                        @Param("batchNumber") String batchNumber);

        List<MaterialVo4Unit> getBillItemByParam(
                        @Param("barCodes") String barCodes);

        BigDecimal getCurrentStockByParam(
                        @Param("depotId") Long depotId,
                        @Param("mId") Long mId);

        BigDecimal getLastUnitPriceByParam(
                        @Param("organId") Long organId,
                        @Param("meId") Long meId,
                        @Param("type") String type,
                        @Param("subType") String subType);

        List<MaterialStockPeriodVo> getMaterialPeriodStock(
                        @Param("materialParam") String materialParam,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows,
                        @Param("shopNames") List<String> shopNames);

        int getMaterialPeriodStockCount(
                        @Param("materialParam") String materialParam);

        List<java.util.Map<String, Object>> getDailyOutStock(
                        @Param("materialIds") String materialIds,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("shopNames") List<String> shopNames);

        // ========== 性能优化相关方法 ==========

        List<MaterialStockPeriodVo> getMaterialPeriodStockOptimized(
                        @Param("materialParam") String materialParam,
                        @Param("offset") Integer offset,
                        @Param("rows") Integer rows,
                        @Param("stockAlertStatus") String stockAlertStatus,
                        @Param("tenantId") Long tenantId);

        int getMaterialPeriodStockCountOptimized(
                        @Param("materialParam") String materialParam,
                        @Param("stockAlertStatus") String stockAlertStatus,
                        @Param("tenantId") Long tenantId);

        List<java.util.Map<String, Object>> getDailyOutStockFromSummary(
                        @Param("materialIds") List<Long> materialIds,
                        @Param("beginTime") String beginTime,
                        @Param("endTime") String endTime,
                        @Param("shopNames") List<String> shopNames,
                        @Param("tenantId") Long tenantId);

        /**
         * 获取商品过去6个月的销量
         */
        BigDecimal getSixMonthsSalesByMaterialId(
                        @Param("materialId") Long materialId,
                        @Param("tenantId") Long tenantId);

        /**
         * 批量获取商品过去6个月的销量（仅销售）
         */
        List<Map<String, Object>> getSixMonthsSalesByMaterialIds(
                        @Param("materialIds") List<Long> materialIds,
                        @Param("tenantId") Long tenantId);

        /**
         * 聚合查询：按当前租户统计全量商品库存总金额（current_period_stock * commodity_decimal）
         */
        BigDecimal getTotalStockValueByTenant(@Param("tenantId") Long tenantId);

        /**
         * 计算排除指定店铺销售出库后的库存总金额
         * 
         * @param targetDate      目标日期（格式：YYYY-MM-DD）
         * @param excludeShopName 要排除的店铺名称
         * @param tenantId        租户ID
         * @return 排除后的库存总金额
         */
        BigDecimal getTotalStockValueExcludeShop(
                        @Param("targetDate") String targetDate,
                        @Param("excludeShopName") String excludeShopName,
                        @Param("tenantId") Long tenantId);

        /**
         * 查询商品当前库存和默认零售价（复用getTotalStockValueByTenant的逻辑）
         * 
         * @param tenantId 租户ID
         * @return List<Map<String, Object>> 包含 materialId, currentStock,
         *         commodityDecimal
         */
        List<Map<String, Object>> getMaterialStockAndPrice(@Param("tenantId") Long tenantId);

        /**
         * 查询指定日期及之后的单据影响（按店铺、商品汇总，便于后续计算）
         * 
         * @param targetDate 目标日期
         * @param tenantId   租户ID
         * @return List<Map<String, Object>> 包含 materialId, shopName, billDate,
         *         inQuantity, outQuantity, totalImpact
         */
        List<Map<String, Object>> getBillImpactByDateRange(
                        @Param("beginDate") String beginDate,
                        @Param("endDate") String endDate,
                        @Param("tenantId") Long tenantId);

        List<Map<String, Object>> getBillImpactDetailByDateRange(
                        @Param("beginDate") String beginDate,
                        @Param("endDate") String endDate,
                        @Param("tenantId") Long tenantId);

        /**
         * 统计：按当前租户统计缺失默认零售价（或默认价为null）的商品数量
         */
        Long countMaterialsMissingDefaultPrice(@Param("tenantId") Long tenantId);

        void refreshMaterialPeriodSummary(@Param("tenantId") Long tenantId);

        void updateDailyOutSummary(
                        @Param("materialId") Long materialId,
                        @Param("targetDate") String targetDate,
                        @Param("tenantId") Long tenantId);

        void refreshDailySummaryForRecentDays(
                        @Param("days") Integer days,
                        @Param("tenantId") Long tenantId);

        // ========== 数据修复相关方法 ==========

        /**
         * 统计包含小数的库存记录数量
         */
        int countDecimalStockRecords();

        /**
         * 修复库存小数数据，将其四舍五入为整数
         */
        int fixDecimalStockData();

        /**
         * 使用修复后的逻辑刷新商品期间汇总
         */
        void refreshMaterialPeriodSummaryCorrect(@Param("tenantId") Long tenantId);

        /**
         * 验证期间库存计算结果
         */
        List<Map<String, Object>> validatePeriodStockBalance();

        // ========== 业务层汇总表更新方法（替换存储过程） ==========

        /**
         * 插入或更新每日出库汇总
         */
        void insertOrUpdateDailyOutSummary(
                        @Param("materialId") Long materialId,
                        @Param("targetDate") String targetDate,
                        @Param("shopName") String shopName,
                        @Param("tenantId") Long tenantId);

        /**
         * 简化版每日出库汇总更新（备用方案）
         */
        void insertOrUpdateDailyOutSummarySimple(
                        @Param("materialId") Long materialId,
                        @Param("targetDate") String targetDate,
                        @Param("shopName") String shopName,
                        @Param("tenantId") Long tenantId);

        /**
         * 删除指定条件的每日出库汇总记录
         */
        void deleteDailyOutSummary(
                        @Param("materialId") Long materialId,
                        @Param("targetDate") String targetDate,
                        @Param("tenantId") Long tenantId);

        /**
         * 插入或更新商品期间汇总
         */
        void insertOrUpdateMaterialPeriodSummary(
                        @Param("materialId") Long materialId,
                        @Param("tenantId") Long tenantId);

        /**
         * 删除指定条件的商品期间汇总记录
         */
        void deleteMaterialPeriodSummary(
                        @Param("materialId") Long materialId,
                        @Param("tenantId") Long tenantId);

        /**
         * 批量初始化汇总表数据
         */
        void batchInitializeSummaryData(@Param("tenantId") Long tenantId);

        /**
         * 简化版批量初始化汇总表数据（备用方案）
         */
        void batchInitializeSummaryDataSimple(@Param("tenantId") Long tenantId);

        /**
         * 根据时间范围查询商品出库总量
         */
        BigDecimal getMaterialOutQuantityByPeriod(
                        @Param("materialId") Long materialId,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate,
                        @Param("tenantId") Long tenantId);

        /**
         * 根据时间范围查询商品入库总量
         */
        BigDecimal getMaterialInQuantityByPeriod(
                        @Param("materialId") Long materialId,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate,
                        @Param("tenantId") Long tenantId);

        /**
         * 查询商品当前库存总量
         */
        BigDecimal getMaterialCurrentStock(
                        @Param("materialId") Long materialId,
                        @Param("tenantId") Long tenantId);

        /**
         * 简化版插入或更新商品期间汇总（只插入计算好的数据）
         */
        void insertOrUpdateMaterialPeriodSummarySimple(
                        @Param("materialId") Long materialId,
                        @Param("barCode") String barCode,
                        @Param("materialName") String materialName,
                        @Param("materialModel") String materialModel,
                        @Param("materialUnit") String materialUnit,
                        @Param("currentPeriodStock") BigDecimal currentPeriodStock,
                        @Param("previousPeriodStock") BigDecimal previousPeriodStock,
                        @Param("currentPeriodOut") BigDecimal currentPeriodOut,
                        @Param("previousPeriodOut") BigDecimal previousPeriodOut,
                        @Param("currentPeriodIn") BigDecimal currentPeriodIn,
                        @Param("previousPeriodIn") BigDecimal previousPeriodIn);
}
