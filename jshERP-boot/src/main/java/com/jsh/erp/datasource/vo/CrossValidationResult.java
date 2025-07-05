package com.jsh.erp.datasource.vo;

import java.util.List;

/**
 * 交叉校验结果
 * 用于返回用户间单据对比校验的结果
 */
public class CrossValidationResult {

    /**
     * 是否一致
     */
    private boolean isConsistent;

    /**
     * 校验通过时自动审核的单据总数
     */
    private Integer totalBills;

    /**
     * 差异信息列表（校验失败时使用）
     */
    private List<ValidationDifference> differences;

    public boolean isConsistent() {
        return isConsistent;
    }

    public void setConsistent(boolean consistent) {
        isConsistent = consistent;
    }

    public Integer getTotalBills() {
        return totalBills;
    }

    public void setTotalBills(Integer totalBills) {
        this.totalBills = totalBills;
    }

    public List<ValidationDifference> getDifferences() {
        return differences;
    }

    public void setDifferences(List<ValidationDifference> differences) {
        this.differences = differences;
    }
}