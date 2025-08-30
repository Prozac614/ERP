package com.jsh.erp.datasource.vo;

import java.util.List;

/**
 * 交叉校验请求
 * 用于接收前端提交的校验请求参数
 */
public class CrossValidationRequest {

    /**
     * 当前用户选中的单据ID列表
     */
    private List<String> currentUserIds;

    /**
     * 选中进行对比的其他用户ID列表
     */
    private List<Long> selectedUserIds;

    /**
     * 校验日期
     */
    private String validationDate;

    /**
     * 单据类型
     */
    private String type;

    /**
     * 单据子类型
     */
    private String subType;

    public List<String> getCurrentUserIds() {
        return currentUserIds;
    }

    public void setCurrentUserIds(List<String> currentUserIds) {
        this.currentUserIds = currentUserIds;
    }

    public List<Long> getSelectedUserIds() {
        return selectedUserIds;
    }

    public void setSelectedUserIds(List<Long> selectedUserIds) {
        this.selectedUserIds = selectedUserIds;
    }

    public String getValidationDate() {
        return validationDate;
    }

    public void setValidationDate(String validationDate) {
        this.validationDate = validationDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }
}