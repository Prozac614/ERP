package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.DepotHead;
import com.jsh.erp.datasource.entities.DepotHeadExample;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
import com.jsh.erp.datasource.vo.BillMaterialSummary;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DepotHeadMapper {
        long countByExample(DepotHeadExample example);

        int deleteByExample(DepotHeadExample example);

        int deleteByPrimaryKey(Long id);

        int insert(DepotHead record);

        int insertSelective(DepotHead record);

        List<DepotHead> selectByExample(DepotHeadExample example);

        DepotHead selectByPrimaryKey(Long id);

        int updateByExampleSelective(@Param("record") DepotHead record, @Param("example") DepotHeadExample example);

        int updateByExample(@Param("record") DepotHead record, @Param("example") DepotHeadExample example);

        int updateByPrimaryKeySelective(DepotHead record);

        int updateByPrimaryKey(DepotHead record);

        /**
         * 获取今日其他用户的单据汇总信息
         * 
         * @param tenantId      租户ID
         * @param currentUserId 当前用户ID
         * @return 今日其他用户的单据汇总列表
         */
        List<TodayUserBillSummary> getTodayUserBillSummary(@Param("tenantId") Long tenantId,
                        @Param("currentUserId") Long currentUserId);

        /**
         * 获取指定日期其他用户的单据汇总信息
         * 
         * @param validationDate 校验日期
         * @param tenantId       租户ID
         * @param currentUserId  当前用户ID
         * @return 指定日期其他用户的单据汇总列表
         */
        List<TodayUserBillSummary> getUserBillSummaryByDate(@Param("validationDate") String validationDate,
                        @Param("tenantId") Long tenantId, @Param("currentUserId") Long currentUserId,
                        @Param("type") String type, @Param("subType") String subType,
                        @Param("shopNames") List<String> shopNames);

        /**
         * 获取今日指定用户的单据列表
         * 
         * @param tenantId 租户ID
         * @param userId   用户ID
         * @return 今日指定用户的单据列表
         */
        List<DepotHead> getTodayBillsByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

        /**
         * 获取指定日期和用户的商品唛头出库汇总
         * 
         * @param validationDate 校验日期
         * @param tenantId       租户ID
         * @param userIds        用户ID列表
         * @return 商品唛头出库汇总列表
         */
        List<BillMaterialSummary> getBillMaterialSummaryByDateAndUsers(
                        @Param("validationDate") String validationDate,
                        @Param("tenantId") Long tenantId,
                        @Param("userIds") List<Long> userIds,
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("shopNames") List<String> shopNames);

        /**
         * 获取指定日期、用户及商品的明细条目
         */
        List<com.jsh.erp.datasource.vo.ValidationBillDetail> getBillDetailsByDateUsersAndMaterial(
                        @Param("validationDate") String validationDate,
                        @Param("tenantId") Long tenantId,
                        @Param("userIds") List<Long> userIds,
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("shopNames") List<String> shopNames);

        /**
         * 获取指定日期和用户的销售出库单据列表
         * 
         * @param validationDate 校验日期
         * @param tenantId       租户ID
         * @param userIds        用户ID列表
         * @return 销售出库单据列表
         */
        List<DepotHead> getBillsByDateAndUsers(
                        @Param("validationDate") String validationDate,
                        @Param("tenantId") Long tenantId,
                        @Param("userIds") List<Long> userIds,
                        @Param("type") String type,
                        @Param("subType") String subType,
                        @Param("shopNames") List<String> shopNames);
}