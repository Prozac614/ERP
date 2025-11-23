package com.jsh.erp.datasource.vo;

import java.util.Date;

/**
 * 价格变更上下文信息
 * 用于在更新价格时传递变更来源等元数据
 */
public class PriceChangeContext {

    /**
     * 变更来源
     * PURCHASE_AUDIT - 采购入库审核
     * MATERIAL_UPDATE - 商品信息修改
     * MATERIAL_IMPORT - 商品导入
     */
    private String changeSource;

    /**
     * 源单据ID（如果来自单据）
     */
    private Long sourceBillId;

    /**
     * 源单据编号（如果来自单据）
     */
    private String sourceBillNumber;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 生效日期（如果来自单据，则为单据日期；否则为当前日期）
     */
    private Date effectiveDate;

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String changeSource) {
        this.changeSource = changeSource;
    }

    public Long getSourceBillId() {
        return sourceBillId;
    }

    public void setSourceBillId(Long sourceBillId) {
        this.sourceBillId = sourceBillId;
    }

    public String getSourceBillNumber() {
        return sourceBillNumber;
    }

    public void setSourceBillNumber(String sourceBillNumber) {
        this.sourceBillNumber = sourceBillNumber;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(Date effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
