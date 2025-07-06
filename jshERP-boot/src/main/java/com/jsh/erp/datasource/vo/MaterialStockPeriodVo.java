package com.jsh.erp.datasource.vo;

import java.math.BigDecimal;

public class MaterialStockPeriodVo {
    private Long materialId;
    private String materialName;
    private String barCode;
    private BigDecimal previousPeriodStock;  // 上期结存
    private BigDecimal currentPeriodStock;   // 本期结存
    private BigDecimal previousPeriodOut;    // 上期出库
    private BigDecimal currentPeriodOut;     // 本期出库
    
    // getter和setter方法
    public Long getMaterialId() { 
        return materialId; 
    }
    
    public void setMaterialId(Long materialId) { 
        this.materialId = materialId; 
    }
    
    public String getMaterialName() { 
        return materialName; 
    }
    
    public void setMaterialName(String materialName) { 
        this.materialName = materialName; 
    }
    
    public String getBarCode() { 
        return barCode; 
    }
    
    public void setBarCode(String barCode) { 
        this.barCode = barCode; 
    }
    
    public BigDecimal getPreviousPeriodStock() { 
        return previousPeriodStock; 
    }
    
    public void setPreviousPeriodStock(BigDecimal previousPeriodStock) { 
        this.previousPeriodStock = previousPeriodStock; 
    }
    
    public BigDecimal getCurrentPeriodStock() { 
        return currentPeriodStock; 
    }
    
    public void setCurrentPeriodStock(BigDecimal currentPeriodStock) { 
        this.currentPeriodStock = currentPeriodStock; 
    }
    
    public BigDecimal getPreviousPeriodOut() { 
        return previousPeriodOut; 
    }
    
    public void setPreviousPeriodOut(BigDecimal previousPeriodOut) { 
        this.previousPeriodOut = previousPeriodOut; 
    }
    
    public BigDecimal getCurrentPeriodOut() { 
        return currentPeriodOut; 
    }
    
    public void setCurrentPeriodOut(BigDecimal currentPeriodOut) { 
        this.currentPeriodOut = currentPeriodOut; 
    }
} 