package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.DepotHead;
import com.jsh.erp.datasource.entities.DepotHeadExample;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
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
     * 获取今日指定用户的单据列表
     * 
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 今日指定用户的单据列表
     */
    List<DepotHead> getTodayBillsByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}