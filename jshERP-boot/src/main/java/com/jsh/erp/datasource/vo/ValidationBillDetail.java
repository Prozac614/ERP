package com.jsh.erp.datasource.vo;

import java.math.BigDecimal;

/**
 * 描述单据明细，用于交叉校验差异展示。
 */
public class ValidationBillDetail {

    /**
     * 单据编号
     */
    private String billNumber;

    /**
     * 商品唛头
     */
    private String materialBarCode;

    /**
     * 商品名称
     */
    private String materialName;

    /**
     * 店铺名称（可能为空）
     */
    private String shopName;

    /**
     * 明细数量
     */
    private BigDecimal quantity;

    /**
     * 明细单价
     */
    private BigDecimal unitPrice;

    /**
     * 单据日期字符串
     */
    private String billDate;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
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

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getBillDate() {
        return billDate;
    }

    public void setBillDate(String billDate) {
        this.billDate = billDate;
    }

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
}

