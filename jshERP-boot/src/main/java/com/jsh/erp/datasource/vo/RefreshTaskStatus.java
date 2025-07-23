package com.jsh.erp.datasource.vo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 汇总表刷新任务状态
 */
public class RefreshTaskStatus {

    private String taskId;

    private String status; // RUNNING, COMPLETED, FAILED

    private Integer totalCount;

    private Integer processedCount;

    private Integer successCount;

    private Integer failedCount;

    private Double progress;

    private String currentMaterial;

    private String startTime;

    private String endTime;

    private String errorMessage;

    public RefreshTaskStatus() {
        this.processedCount = 0;
        this.successCount = 0;
        this.failedCount = 0;
        this.progress = 0.0;
        this.status = "RUNNING";
        this.startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public RefreshTaskStatus(String taskId) {
        this();
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            this.endTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(Integer processedCount) {
        this.processedCount = processedCount;
        updateProgress();
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Double getProgress() {
        return progress;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public String getCurrentMaterial() {
        return currentMaterial;
    }

    public void setCurrentMaterial(String currentMaterial) {
        this.currentMaterial = currentMaterial;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 更新进度百分比
     */
    private void updateProgress() {
        if (totalCount != null && totalCount > 0 && processedCount != null) {
            this.progress = (double) processedCount / totalCount * 100;
        }
    }

    /**
     * 增加处理计数
     */
    public void incrementProcessed(boolean success) {
        this.processedCount = (this.processedCount == null ? 0 : this.processedCount) + 1;
        if (success) {
            this.successCount = (this.successCount == null ? 0 : this.successCount) + 1;
        } else {
            this.failedCount = (this.failedCount == null ? 0 : this.failedCount) + 1;
        }
        updateProgress();
    }
}