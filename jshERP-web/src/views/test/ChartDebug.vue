<template>
  <div class="chart-debug-page">
    <a-card title="图表加载调试" :bordered="false">
      <div class="debug-controls">
        <a-button type="primary" @click="openChart">打开图表</a-button>
        <a-button @click="closeChart" style="margin-left: 8px">关闭图表</a-button>
        <a-button @click="clearLogs" style="margin-left: 8px">清空日志</a-button>
      </div>
      
      <div class="debug-info" style="margin-top: 16px;">
        <h4>当前状态:</h4>
        <p>图表可见: {{ chartModal.visible }}</p>
        <p>商品ID: {{ chartModal.materialInfo.materialId }}</p>
        <p>日期范围: {{ dateRangeStr }}</p>
      </div>
      
      <div class="debug-logs" style="margin-top: 16px;">
        <h4>调试日志:</h4>
        <div class="log-container" style="height: 300px; overflow-y: auto; border: 1px solid #d9d9d9; padding: 8px;">
          <div v-for="(log, index) in logs" :key="index" class="log-item">
            <span class="log-time">{{ log.time }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>

      <!-- 图表弹窗 -->
      <StockChartModal
        :visible="chartModal.visible"
        :materialInfo="chartModal.materialInfo"
        :dateRange="chartModal.dateRange"
        @cancel="closeChart"
      />
    </a-card>
  </div>
</template>

<script>
import moment from 'moment'
import StockChartModal from '@/components/charts/StockChartModal'

export default {
  name: 'ChartDebug',
  components: {
    StockChartModal
  },
  data() {
    return {
      chartModal: {
        visible: false,
        materialInfo: {},
        dateRange: []
      },
      logs: []
    }
  },
  computed: {
    dateRangeStr() {
      if (this.chartModal.dateRange && this.chartModal.dateRange.length === 2) {
        return `${this.chartModal.dateRange[0].format('YYYY-MM-DD')} 到 ${this.chartModal.dateRange[1].format('YYYY-MM-DD')}`
      }
      return '未设置'
    }
  },
  mounted() {
    // 劫持console.log来捕获调试信息
    const originalLog = console.log
    console.log = (...args) => {
      originalLog.apply(console, args)
      this.addLog(args.join(' '))
    }
  },
  methods: {
    addLog(message) {
      this.logs.push({
        time: moment().format('HH:mm:ss.SSS'),
        message: message
      })
      // 自动滚动到底部
      this.$nextTick(() => {
        const container = this.$el.querySelector('.log-container')
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    },

    openChart() {
      this.addLog('=== 开始打开图表 ===')
      
      // 模拟真实的商品数据
      const materialInfo = {
        materialId: 1,
        barCode: 'TEST001',
        materialName: '测试商品A',
        currentPeriodStock: 100,
        previousPeriodStock: 150,
        currentPeriodOut: 50,
        previousPeriodOut: 30
      }
      
      const dateRange = [moment().subtract(1, 'month'), moment()]
      
      this.addLog('设置商品信息: ' + JSON.stringify(materialInfo))
      this.addLog('设置日期范围: ' + dateRange.map(d => d.format('YYYY-MM-DD')).join(' 到 '))
      
      // 先设置数据
      this.chartModal.materialInfo = materialInfo
      this.chartModal.dateRange = dateRange
      
      // 延迟显示弹窗
      this.$nextTick(() => {
        setTimeout(() => {
          this.addLog('显示图表弹窗')
          this.chartModal.visible = true
        }, 100)
      })
    },

    closeChart() {
      this.addLog('关闭图表弹窗')
      this.chartModal.visible = false
      this.chartModal.materialInfo = {}
      this.chartModal.dateRange = []
    },

    clearLogs() {
      this.logs = []
    }
  }
}
</script>

<style scoped>
.chart-debug-page {
  padding: 24px;
}

.debug-controls {
  margin-bottom: 16px;
}

.log-item {
  margin-bottom: 4px;
  font-family: monospace;
  font-size: 12px;
}

.log-time {
  color: #666;
  margin-right: 8px;
}

.log-message {
  color: #333;
}
</style>
