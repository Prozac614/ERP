<template>
  <a-modal
    :title="modalTitle"
    :visible="visible"
    :width="1400"
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
          class="chart-wrapper"
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
        <div class="legend-item" v-if="chartType === 'history'">
          <span class="legend-color" style="background-color: #52c41a;"></span>
          <span>出库量</span>
        </div>
        <div class="legend-item" v-if="chartType === 'flow'">
          <span class="legend-color" style="background-color: #faad14;"></span>
          <span>累计出库</span>
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
    chartType: {
      type: String,
      default: 'history', // 'history' 或 'flow'
      validator: value => ['history', 'flow'].includes(value)
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
      return this.chartType === 'history' ? '库存历史图表' : '出库流水图表'
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
          // 延迟一下确保所有props都已经传递完成
          setTimeout(() => {
            this.loadChartData()
          }, 100)
        })
      } else {
        this.destroyChart()
      }
    },
    chartType() {
      if (this.visible) {
        this.loadChartData()
      }
    },
    // 监听dateRange变化，确保数据及时更新
    dateRange: {
      handler(newVal, oldVal) {
        console.log('dateRange变化:', oldVal, '->', newVal)
        if (this.canLoadData()) {
          // 避免重复加载，只有当值真正改变时才重新加载
          if (!oldVal || oldVal.length !== 2 ||
              newVal[0] !== oldVal[0] || newVal[1] !== oldVal[1]) {
            setTimeout(() => {
              this.loadChartData()
            }, 50)
          }
        }
      },
      deep: true,
      immediate: true
    },
    // 监听materialInfo变化
    materialInfo: {
      handler(newVal, oldVal) {
        console.log('materialInfo变化:', oldVal, '->', newVal)
        if (this.canLoadData()) {
          // 避免重复加载，只有当materialId真正改变时才重新加载
          if (!oldVal || oldVal.materialId !== newVal.materialId) {
            setTimeout(() => {
              this.loadChartData()
            }, 50)
          }
        }
      },
      deep: true,
      immediate: true
    }
  },
  mounted() {
    // 如果组件挂载时已经是可见状态，立即加载数据
    if (this.visible) {
      this.$nextTick(() => {
        this.initChart()
        setTimeout(() => {
          this.loadChartData()
        }, 100)
      })
    }
  },
  beforeDestroy() {
    this.destroyChart()
  },
  methods: {
    // 验证是否可以加载数据
    canLoadData() {
      const hasValidMaterial = this.materialInfo && this.materialInfo.materialId
      const hasValidDateRange = this.dateRange && this.dateRange.length === 2
      const isVisible = this.visible

      console.log('数据加载条件检查:', {
        hasValidMaterial,
        hasValidDateRange,
        isVisible,
        materialInfo: this.materialInfo,
        dateRange: this.dateRange
      })

      return hasValidMaterial && hasValidDateRange && isVisible
    },

    // 初始化图表
    initChart() {
      if (!this.$refs.chartContainer) return
      
      this.destroyChart()
      
      // 确保容器有正确的尺寸
      const container = this.$refs.chartContainer
      if (container.offsetWidth === 0) {
        // 如果容器宽度为0，延迟初始化，避免无限递归
        setTimeout(() => {
          if (this.$refs.chartContainer && this.$refs.chartContainer.offsetWidth > 0) {
            this.initChart()
          }
        }, 100)
        return
      }
      
      try {
        this.chartInstance = echarts.init(container)
        
        // 监听窗口大小变化
        window.addEventListener('resize', this.handleResize)
      } catch (error) {
        console.error('图表初始化失败:', error)
        this.error = '图表初始化失败，请刷新页面重试'
      }
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
      console.log('开始加载图表数据...')

      // 使用统一的验证方法
      if (!this.canLoadData()) {
        console.log('数据加载条件不满足，跳过加载')
        this.loading = false
        return
      }

      this.loading = true
      this.error = null

      try {

        // 确保日期对象有format方法
        let beginDate, endDate
        try {
          beginDate = this.dateRange[0].format ? this.dateRange[0].format('YYYY-MM-DD') : this.dateRange[0]
          endDate = this.dateRange[1].format ? this.dateRange[1].format('YYYY-MM-DD') : this.dateRange[1]
        } catch (dateError) {
          console.error('日期格式化失败:', dateError)
          this.error = '日期格式错误'
          return
        }

        const params = {
          materialId: this.materialInfo.materialId, // 使用正确的字段名
          barCode: this.materialInfo.barCode,
          beginDate: beginDate,
          endDate: endDate,
          chartType: this.chartType,
          // 传递库存信息用于更准确的图表计算
          currentPeriodStock: this.materialInfo.currentPeriodStock,
          previousPeriodStock: this.materialInfo.previousPeriodStock,
          currentPeriodOut: this.materialInfo.currentPeriodOut,
          previousPeriodOut: this.materialInfo.previousPeriodOut
        }

        console.log('图表数据请求参数:', params)

        // 添加超时保护
        const timeoutPromise = new Promise((_, reject) => {
          setTimeout(() => reject(new Error('请求超时')), 30000) // 30秒超时
        })

        // 使用对应的API函数
        const apiPromise = this.chartType === 'history'
          ? getStockHistory(params)
          : getOutboundFlow(params)

        const response = await Promise.race([apiPromise, timeoutPromise])

        console.log('图表数据响应:', response)

        if (response.code === 200) {
          this.chartData = response.data
          this.renderChart()
        } else {
          this.error = response.message || '数据加载失败'
        }
      } catch (error) {
        console.error('加载图表数据失败:', error)
        if (error.message === '请求超时') {
          this.error = '数据加载超时，请检查网络连接或减少日期范围'
        } else {
          this.error = '网络请求失败，请检查网络连接'
        }
      } finally {
        this.loading = false
      }
    },

    // 渲染图表
    renderChart() {
      if (!this.chartInstance || !this.hasData) return

      const option = this.getChartOption()
      this.chartInstance.setOption(option)
      
      // 强制重新计算尺寸
      this.$nextTick(() => {
        if (this.chartInstance) {
          this.chartInstance.resize()
        }
      })
    },

    // 获取图表配置
    getChartOption() {
      if (this.chartType === 'history') {
        return this.getHistoryChartOption()
      } else {
        return this.getFlowChartOption()
      }
    },

    // 库存历史图表配置（双纵坐标）
    getHistoryChartOption() {
      return {
        title: {
          text: `${this.materialInfo.materialName || '商品'} - 库存与出库趋势`,
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            crossStyle: {
              color: '#999'
            }
          },
          formatter: (params) => {
            let result = `<div style="margin-bottom: 8px; font-weight: bold;">${params[0].axisValue}</div>`
            params.forEach(param => {
              const unit = param.seriesName === '库存量' ? '件' : '件'
              const value = param.value !== null && param.value !== undefined ? Math.floor(param.value) : 0
              result += `<div style="color: ${param.color}; margin: 4px 0;">
                <span style="display: inline-block; width: 10px; height: 10px; background: ${param.color}; border-radius: 50%; margin-right: 8px;"></span>
                ${param.seriesName}: <strong>${value} ${unit}</strong>
              </div>`
            })
            return result
          }
        },
        legend: {
          data: ['库存量', '出库量'],
          top: 35,
          textStyle: {
            fontSize: 12
          }
        },
        grid: {
          left: '60px',
          right: '60px',
          bottom: '80px',
          top: '90px',
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
            formatter: (value) => moment(value).format('MM-DD'),
            fontSize: 11
          },
          axisLine: {
            lineStyle: {
              color: '#666'
            }
          }
        },
        yAxis: [
          {
            type: 'value',
            name: '库存量(件)',
            nameLocation: 'middle',
            nameGap: 40,
            position: 'left',
            axisLabel: {
              formatter: (value) => Math.floor(value),
              color: '#1890ff'
            },
            axisLine: {
              lineStyle: {
                color: '#1890ff'
              }
            },
            splitLine: {
              show: true,
              lineStyle: {
                color: '#f0f0f0',
                type: 'dashed'
              }
            }
          },
          {
            type: 'value',
            name: '出库量(件)',
            nameLocation: 'middle',
            nameGap: 40,
            position: 'right',
            axisLabel: {
              formatter: (value) => Math.floor(value),
              color: '#52c41a'
            },
            axisLine: {
              lineStyle: {
                color: '#52c41a'
              }
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
              width: 3,
              color: '#1890ff'
            },
            symbol: 'circle',
            symbolSize: 6,
            smooth: true,
            emphasis: {
              focus: 'series'
            },
            markLine: {
              silent: true,
              lineStyle: {
                color: '#ff4d4f',
                type: 'dashed'
              },
              data: [
                {
                  name: '低库存警戒线',
                  yAxis: 20
                }
              ]
            }
          },
          {
            name: '出库量',
            type: 'bar',
            yAxisIndex: 1,
            data: (this.chartData.dailyOutData && this.chartData.dailyOutData.map(val => Math.floor(val || 0))) || [],
            itemStyle: {
              color: '#52c41a',
              opacity: 0.8
            },
            barWidth: '60%',
            emphasis: {
              focus: 'series',
              itemStyle: {
                opacity: 1
              }
            }
          }
        ],
        dataZoom: [
          {
            type: 'slider',
            start: 0,
            end: 100,
            height: 25,
            bottom: 15,
            textStyle: {
              fontSize: 11
            },
            handleStyle: {
              color: '#1890ff'
            },
            fillerColor: 'rgba(24, 144, 255, 0.2)'
          },
          {
            type: 'inside',
            start: 0,
            end: 100
          }
        ],
        toolbox: {
          feature: {
            dataZoom: {
              yAxisIndex: 'none'
            },
            restore: {},
            saveAsImage: {
              name: `${this.materialInfo.barCode || 'material'}_stock_chart`
            }
          },
          right: 20,
          top: 20
        }
      }
    },

    // 出库流水图表配置
    getFlowChartOption() {
      return {
        title: {
          text: '出库流水趋势',
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
                ${param.seriesName}: ${Math.floor(param.value)} 件
              </div>`
            })
            return result
          }
        },
        legend: {
          data: ['日出库量', '累计出库'],
          top: 30
        },
        grid: {
          left: '50px',
          right: '50px',
          bottom: '50px',
          top: '80px',
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
        yAxis: {
          type: 'value',
          name: '数量',
          axisLabel: {
            formatter: (value) => Math.floor(value)
          }
        },
        series: [
          {
            name: '日出库量',
            type: 'line',
            data: (this.chartData.outboundData && this.chartData.outboundData.map(val => Math.floor(val || 0))) || [],
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
            name: '累计出库',
            type: 'line',
            data: (this.chartData.cumulativeData && this.chartData.cumulativeData.map(val => Math.floor(val || 0))) || [],
            itemStyle: {
              color: '#faad14'
            },
            lineStyle: {
              width: 2,
              type: 'dashed'
            },
            symbol: 'diamond',
            symbolSize: 4,
            smooth: true
          }
        ],
        dataZoom: [
          {
            type: 'slider',
            start: 0,
            end: 100,
            height: 20,
            bottom: 10
          }
        ]
      }
    },

    // 刷新图表
    refreshChart() {
      this.error = null
      this.chartData = null
      this.loadChartData()
    },

    // 导出图表
    exportChart() {
      if (this.chartInstance && this.hasData) {
        try {
          const url = this.chartInstance.getDataURL({
            pixelRatio: 2,
            backgroundColor: '#fff'
          })
          const link = document.createElement('a')
          link.href = url
          link.download = `${this.materialInfo.barCode || 'material'}_stock_chart_${moment().format('YYYYMMDD_HHmmss')}.png`
          link.click()
          this.$message.success('图表导出成功')
        } catch (error) {
          console.error('导出图表失败:', error)
          this.$message.error('图表导出失败')
        }
      } else {
        this.$message.warning('图表未加载完成或无数据，无法导出')
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
  min-height: 450px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  width: 100%;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.chart-wrapper {
  width: 100% !important;
  height: 450px !important;
  min-width: 100%;
  border-radius: 8px;
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