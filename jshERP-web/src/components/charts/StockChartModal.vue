<template>
  <a-modal
    :title="modalTitle"
    :visible="visible"
    :width="1200"
    :footer="null"
    @cancel="handleCancel"
    :maskClosable="false"
  >
    <div class="stock-chart-modal">
      <!-- 图表头部信息 -->
      <div class="chart-header">
        <div class="material-info">
          <h3>{{ materialInfo.materialName }}</h3>
          <p>商品编码: {{ materialInfo.barCode }}</p>
          <p>统计时间: {{ dateRangeText }}</p>
        </div>
        <div class="chart-actions">
          <a-button @click="refreshChart" :loading="loading" icon="reload">刷新数据</a-button>
          <a-button @click="exportChart" icon="download">导出图表</a-button>
        </div>
      </div>

      <!-- 图表容器 -->
      <div class="chart-container">
        <div 
          ref="chartContainer" 
          :style="{ width: '100%', height: '600px' }"
          v-show="!loading && !error && hasData"
        ></div>
        
        <!-- 加载状态 -->
        <div v-if="loading" class="chart-loading">
          <a-spin size="large">
            <div style="text-align: center; padding: 100px 0;">
              <p style="margin-top: 20px; color: #666;">正在加载图表数据...</p>
            </div>
          </a-spin>
        </div>

        <!-- 错误状态 -->
        <div v-if="error && !loading" class="chart-error">
          <div style="text-align: center; padding: 60px 0;">
            <a-icon type="exclamation-circle" style="font-size: 48px; color: #ff7875; margin-bottom: 16px;" />
            <h4>数据加载失败</h4>
            <p style="color: #666; margin-bottom: 20px;">{{ error }}</p>
            <a-button type="primary" @click="refreshChart">重新加载</a-button>
          </div>
        </div>

        <!-- 无数据状态 -->
        <div v-if="!hasData && !loading && !error" class="chart-empty">
          <div style="text-align: center; padding: 60px 0;">
            <a-icon type="inbox" style="font-size: 48px; color: #d9d9d9; margin-bottom: 16px;" />
            <h4>暂无数据</h4>
            <p style="color: #666;">该商品在选定时间范围内暂无相关数据</p>
          </div>
        </div>
      </div>

      <!-- 图表说明 -->
      <div class="chart-legend" v-if="hasData && !loading">
        <div class="legend-item">
          <span class="legend-color" style="background-color: #1890ff;"></span>
          <span>库存量</span>
        </div>
        <div class="legend-item">
          <span class="legend-color" style="background-color: #52c41a;"></span>
          <span>出库量</span>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script>
import * as echarts from 'echarts'
import { getAction } from '@/api/manage'
import { getStockHistory, getOutboundFlow } from '@/api/stockChart'
import moment from 'moment'

export default {
  name: 'StockChartModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    materialInfo: {
      type: Object,
      default: () => ({})
    },
    dateRange: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      loading: false,
      error: null,
      chartData: null,
      chartInstance: null
    }
  },
  computed: {
    modalTitle() {
      return '库存图表'
    },
    dateRangeText() {
      if (this.dateRange.length === 2) {
        return `${this.dateRange[0].format('YYYY-MM-DD')} 至 ${this.dateRange[1].format('YYYY-MM-DD')}`
      }
      return '未设置时间范围'
    },
    hasData() {
      return this.chartData && this.chartData.dates && this.chartData.dates.length > 0
    }
  },
      watch: {
      visible(newVal) {
        if (newVal) {
          this.$nextTick(() => {
            this.initChart()
            this.loadChartData()
          })
        } else {
          this.destroyChart()
        }
      }
    },
  beforeDestroy() {
    this.destroyChart()
  },
  methods: {
    // 初始化图表
    initChart() {
      if (!this.$refs.chartContainer) return
      
      this.destroyChart()
      this.chartInstance = echarts.init(this.$refs.chartContainer)
      
      // 监听窗口大小变化
      window.addEventListener('resize', this.handleResize)
    },

    // 销毁图表
    destroyChart() {
      if (this.chartInstance) {
        this.chartInstance.dispose()
        this.chartInstance = null
      }
      window.removeEventListener('resize', this.handleResize)
    },

    // 处理窗口大小变化
    handleResize() {
      if (this.chartInstance) {
        this.chartInstance.resize()
      }
    },

    // 加载图表数据
    async loadChartData() {
      this.loading = true
      this.error = null

      try {
        const params = {
          materialId: this.materialInfo.id,
          barCode: this.materialInfo.barCode,
          beginDate: this.dateRange[0].format('YYYY-MM-DD'),
          endDate: this.dateRange[1].format('YYYY-MM-DD')
        }

        // 使用库存历史API函数
        const response = await getStockHistory(params)
        
        if (response.code === 200) {
          this.chartData = response.data
          this.renderChart()
        } else {
          this.error = response.message || '数据加载失败'
        }
      } catch (error) {
        console.error('加载图表数据失败:', error)
        this.error = '网络请求失败，请检查网络连接'
      } finally {
        this.loading = false
      }
    },

    // 渲染图表
    renderChart() {
      if (!this.chartInstance || !this.hasData) return

      const option = this.getChartOption()
      this.chartInstance.setOption(option)
    },

    // 获取图表配置
    getChartOption() {
      return this.getHistoryChartOption()
    },

    // 库存历史图表配置
    getHistoryChartOption() {
      return {
        title: {
          text: '库存变化趋势',
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross'
          },
          formatter: (params) => {
            let result = `<div style="margin-bottom: 5px;">${params[0].axisValue}</div>`
            params.forEach(param => {
              result += `<div style="color: ${param.color};">
                ${param.seriesName}: ${Math.floor(param.value)} 
                ${param.seriesName === '库存量' ? '件' : '件'}
              </div>`
            })
            return result
          }
        },
        legend: {
          data: ['库存量', '出库量'],
          top: 40
        },
        grid: {
          left: '60px',
          right: '60px',
          bottom: '80px',
          top: '100px',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: this.chartData.dates || [],
          axisTick: {
            alignWithLabel: true
          },
          axisLabel: {
            rotate: 45,
            formatter: (value) => moment(value).format('MM-DD')
          }
        },
        yAxis: [
          {
            type: 'value',
            name: '库存量',
            position: 'left',
            axisLabel: {
              formatter: (value) => Math.floor(value)
            },
            splitLine: {
              show: true
            }
          },
          {
            type: 'value',
            name: '出库量',
            position: 'right',
            axisLabel: {
              formatter: (value) => Math.floor(value)
            },
            splitLine: {
              show: false
            }
          }
        ],
        series: [
          {
            name: '库存量',
            type: 'line',
            yAxisIndex: 0,
            data: (this.chartData.stockData && this.chartData.stockData.map(val => Math.floor(val || 0))) || [],
            itemStyle: {
              color: '#1890ff'
            },
            lineStyle: {
              width: 2
            },
            symbol: 'circle',
            symbolSize: 4,
            smooth: true
          },
          {
            name: '出库量',
            type: 'line',
            yAxisIndex: 1,
            data: (this.chartData.dailyOutData && this.chartData.dailyOutData.map(val => Math.floor(val || 0))) || [],
            itemStyle: {
              color: '#52c41a'
            },
            lineStyle: {
              width: 2
            },
            symbol: 'circle',
            symbolSize: 4,
            smooth: true
          }
        ],
        dataZoom: [
          {
            type: 'slider',
            start: 0,
            end: 100,
            height: 30,
            bottom: 20
          }
        ]
      }
    },

    // 刷新图表
    refreshChart() {
      this.loadChartData()
    },

    // 导出图表
    exportChart() {
      if (this.chartInstance) {
        const url = this.chartInstance.getDataURL({
          pixelRatio: 2,
          backgroundColor: '#fff'
        })
        const link = document.createElement('a')
        link.href = url
        link.download = `${this.materialInfo.materialName}_${this.modalTitle}_${moment().format('YYYYMMDD')}.png`
        link.click()
      }
    },

    // 关闭弹窗
    handleCancel() {
      this.$emit('cancel')
    }
  }
}
</script>

<style scoped>
.stock-chart-modal {
  padding: 0;
  max-height: 80vh;
  overflow: hidden;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.material-info h3 {
  margin: 0 0 8px 0;
  color: #262626;
  font-size: 18px;
  font-weight: 600;
}

.material-info p {
  margin: 4px 0;
  color: #666;
  font-size: 14px;
}

.chart-actions {
  display: flex;
  gap: 8px;
}

.chart-container {
  position: relative;
  min-height: 600px;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.chart-loading,
.chart-error,
.chart-empty {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #666;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}
</style> 