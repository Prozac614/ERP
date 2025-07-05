package com.jsh.erp.datasource.vo;

import java.util.List;

/**
 * 交叉校验检查结果
 * 用于返回今日其他用户单据检查的结果
 */
public class CrossValidationCheckResult {

    /**
     * 是否有其他用户的单据
     */
    private boolean hasOtherUsers;

    /**
     * 其他用户的单据汇总列表
     */
    private List<TodayUserBillSummary> otherUsers;

    /**
     * 当前用户选中的单据ID列表
     */
    private List<String> currentUserIds;

    /**
     * 单据总数
     */
    private int totalBills;

    public boolean isHasOtherUsers() {
        return hasOtherUsers;
    }

    public void setHasOtherUsers(boolean hasOtherUsers) {
        this.hasOtherUsers = hasOtherUsers;
    }

    public List<TodayUserBillSummary> getOtherUsers() {
        return otherUsers;
    }

    public void setOtherUsers(List<TodayUserBillSummary> otherUsers) {
        this.otherUsers = otherUsers;
    }

    public List<String> getCurrentUserIds() {
        return currentUserIds;
    }

    public void setCurrentUserIds(List<String> currentUserIds) {
        this.currentUserIds = currentUserIds;
    }

    public int getTotalBills() {
        return totalBills;
    }

    public void setTotalBills(int totalBills) {
        this.totalBills = totalBills;
    }
}