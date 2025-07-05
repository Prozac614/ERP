package com.jsh.erp.datasource.vo;

import java.util.Date;

/**
 * 今日用户单据汇总
 * 用于校验功能中显示今日其他用户的单据信息
 */
public class TodayUserBillSummary {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 单据数量
     */
    private Integer billCount;

    /**
     * 第一张单据时间
     */
    private Date firstBillTime;

    /**
     * 最后一张单据时间
     */
    private Date lastBillTime;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Integer getBillCount() {
        return billCount;
    }

    public void setBillCount(Integer billCount) {
        this.billCount = billCount;
    }

    public Date getFirstBillTime() {
        return firstBillTime;
    }

    public void setFirstBillTime(Date firstBillTime) {
        this.firstBillTime = firstBillTime;
    }

    public Date getLastBillTime() {
        return lastBillTime;
    }

    public void setLastBillTime(Date lastBillTime) {
        this.lastBillTime = lastBillTime;
    }
}