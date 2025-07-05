package com.jsh.erp.datasource.vo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 单据商品唛头汇总
 * 用于交叉校验功能中汇总各用户的商品出库数据
 */
public class BillMaterialSummary {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 商品唛头
     */
    private String materialBarCode;

    /**
     * 商品名称
     */
    private String materialName;

    /**
     * 出库总数量
     */
    private BigDecimal totalOutNumber;

    /**
     * 创建时间
     */
    private Date createTime;

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

    public String getMaterialBarCode() {
        return materialBarCode;
    }

    public void setMaterialBarCode(String materialBarCode) {
        this.materialBarCode = materialBarCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public BigDecimal getTotalOutNumber() {
        return totalOutNumber;
    }

    public void setTotalOutNumber(BigDecimal totalOutNumber) {
        this.totalOutNumber = totalOutNumber;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
} 