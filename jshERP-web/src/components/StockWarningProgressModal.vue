<template>
  <a-modal
    :title="modalTitle"
    :visible="visible"
    :maskClosable="false"
    :closable="false"
    :width="600"
    @cancel="handleCancel"
  >
    <div class="progress-content">
      <!-- 任务信息 -->
      <div class="task-info">
        <a-row :gutter="16">
          <a-col :span="12">
            <div class="info-item">
              <span class="label">任务状态:</span>
              <a-tag :color="getStatusColor(taskStatus.status)">
                {{ getStatusText(taskStatus.status) }}
              </a-tag>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="info-item">
              <span class="label">总商品数:</span>
              <span class="value">{{ taskStatus.totalCount || 0 }}</span>
            </div>
          </a-col>
        </a-row>
        
        <a-row :gutter="16" style="margin-top: 16px;">
          <a-col :span="8">
            <div class="info-item">
              <span class="label">已处理:</span>
              <span class="value">{{ taskStatus.processedCount || 0 }}</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="info-item">
              <span class="label">成功:</span>
              <span class="value success">{{ taskStatus.successCount || 0 }}</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="info-item">
              <span class="label">失败:</span>
              <span class="value error">{{ taskStatus.failedCount || 0 }}</span>
            </div>
          </a-col>
        </a-row>
      </div>
      
      <!-- 进度条 -->
      <div class="progress-bar" style="margin: 24px 0;">
        <a-progress 
          :percent="Math.round(taskStatus.progress || 0)" 
          :status="getProgressStatus(taskStatus.status)"
          :stroke-width="8"
        />
      </div>
      
      <!-- 时间信息 -->
      <div class="time-info" v-if="taskStatus.startTime">
        <a-row :gutter="16">
          <a-col :span="12">
            <div class="info-item">
              <span class="label">开始时间:</span>
              <span class="value">{{ formatTime(taskStatus.startTime) }}</span>
            </div>
          </a-col>
          <a-col :span="12" v-if="taskStatus.endTime">
            <div class="info-item">
              <span class="label">结束时间:</span>
              <span class="value">{{ formatTime(taskStatus.endTime) }}</span>
            </div>
          </a-col>
        </a-row>
        
        <div class="info-item" v-if="taskStatus.status === 'RUNNING'">
          <span class="label">预计剩余时间:</span>
          <span class="value">{{ getEstimatedTime() }}</span>
        </div>
      </div>
      
      <!-- 错误信息 -->
      <div class="error-info" v-if="taskStatus.status === 'FAILED' && taskStatus.errorMessage">
        <a-alert
          type="error"
          :message="taskStatus.errorMessage"
          show-icon
          style="margin-top: 16px;"
        />
      </div>
      
      <!-- 成功信息 -->
      <div class="success-info" v-if="taskStatus.status === 'COMPLETED'">
        <a-alert
          type="success"
          message="库存预警计算完成！"
          :description="`成功处理 ${taskStatus.successCount} 个商品，失败 ${taskStatus.failedCount} 个商品。`"
          show-icon
          style="margin-top: 16px;"
        />
      </div>
    </div>
    
    <template slot="footer">
      <a-button 
        v-if="taskStatus.status === 'RUNNING'" 
        @click="handleCancel"
      >
        后台运行
      </a-button>
      <a-button 
        v-else
        type="primary" 
        @click="handleCancel"
      >
        确定
      </a-button>
    </template>
  </a-modal>
</template>

<script>
import moment from 'moment'

export default {
  name: 'StockWarningProgressModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    taskStatus: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    modalTitle() {
      const statusMap = {
        'RUNNING': '库存预警计算进度',
        'COMPLETED': '库存预警计算完成',
        'FAILED': '库存预警计算失败',
        'NOT_FOUND': '任务不存在'
      }
      return statusMap[this.taskStatus.status] || '库存预警计算'
    }
  },
  methods: {
    handleCancel() {
      this.$emit('cancel')
    },
    
    getStatusText(status) {
      const statusMap = {
        'RUNNING': '计算中',
        'COMPLETED': '已完成',
        'FAILED': '失败',
        'NOT_FOUND': '任务不存在'
      }
      return statusMap[status] || status
    },
    
    getStatusColor(status) {
      const colorMap = {
        'RUNNING': 'processing',
        'COMPLETED': 'success',
        'FAILED': 'error',
        'NOT_FOUND': 'default'
      }
      return colorMap[status] || 'default'
    },
    
    getProgressStatus(status) {
      if (status === 'FAILED') return 'exception'
      if (status === 'COMPLETED') return 'success'
      return 'active'
    },
    
    formatTime(timeStr) {
      if (!timeStr) return '-'
      return moment(timeStr).format('YYYY-MM-DD HH:mm:ss')
    },
    
    getEstimatedTime() {
      if (!this.taskStatus.startTime || !this.taskStatus.processedCount || !this.taskStatus.totalCount) {
        return '计算中...'
      }
      
      const startTime = moment(this.taskStatus.startTime)
      const now = moment()
      const elapsed = now.diff(startTime, 'seconds')
      const processed = this.taskStatus.processedCount
      const total = this.taskStatus.totalCount
      
      if (processed === 0) return '计算中...'
      
      const avgTimePerItem = elapsed / processed
      const remaining = total - processed
      const estimatedSeconds = remaining * avgTimePerItem
      
      if (estimatedSeconds < 60) {
        return `约 ${Math.round(estimatedSeconds)} 秒`
      } else if (estimatedSeconds < 3600) {
        return `约 ${Math.round(estimatedSeconds / 60)} 分钟`
      } else {
        return `约 ${Math.round(estimatedSeconds / 3600)} 小时`
      }
    }
  }
}
</script>

<style scoped>
.progress-content {
  padding: 16px 0;
}

.task-info, .time-info {
  background: #fafafa;
  padding: 16px;
  border-radius: 6px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.info-item:last-child {
  margin-bottom: 0;
}

.label {
  font-weight: 500;
  color: #666;
  margin-right: 8px;
  min-width: 80px;
}

.value {
  color: #333;
  font-weight: 500;
}

.value.success {
  color: #52c41a;
}

.value.error {
  color: #ff4d4f;
}

.progress-bar {
  text-align: center;
}
</style>
