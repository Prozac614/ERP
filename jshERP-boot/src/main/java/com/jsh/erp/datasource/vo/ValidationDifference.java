package com.jsh.erp.datasource.vo;

/**
 * 校验差异信息
 * 用于描述单据对比中发现的差异
 */
public class ValidationDifference {

    /**
     * 差异类型代码
     */
    private String diffType;

    /**
     * 差异类型名称
     */
    private String diffTypeName;

    /**
     * 差异描述
     */
    private String description;

    /**
     * 涉及的用户
     */
    private String users;

    /**
     * 影响的单据数量
     */
    private Integer affectedBills;

    public ValidationDifference() {
    }

    public ValidationDifference(String diffType, String diffTypeName, String description) {
        this.diffType = diffType;
        this.diffTypeName = diffTypeName;
        this.description = description;
    }

    public String getDiffType() {
        return diffType;
    }

    public void setDiffType(String diffType) {
        this.diffType = diffType;
    }

    public String getDiffTypeName() {
        return diffTypeName;
    }

    public void setDiffTypeName(String diffTypeName) {
        this.diffTypeName = diffTypeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUsers() {
        return users;
    }

    public void setUsers(String users) {
        this.users = users;
    }

    public Integer getAffectedBills() {
        return affectedBills;
    }

    public void setAffectedBills(Integer affectedBills) {
        this.affectedBills = affectedBills;
    }
}