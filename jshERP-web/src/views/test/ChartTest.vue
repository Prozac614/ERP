<template>
  <div class="chart-test-page">
    <a-card title="图表功能测试" :bordered="false">
      <!-- 测试参数设置 -->
      <div class="test-params">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="商品ID">
              <a-input v-model="testParams.materialId" placeholder="请输入商品ID" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="开始日期">
              <a-date-picker
                v-model="testParams.beginDate"
                format="YYYY-MM-DD"
                placeholder="选择开始日期"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="结束日期">
              <a-date-picker
                v-model="testParams.endDate"
                format="YYYY-MM-DD"
                placeholder="选择结束日期"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item>
              <a-button type="primary" @click="testChartData" :loading="loading">
                测试数据获取
              </a-button>
              <a-button @click="showChart" :disabled="!hasTestData" style="margin-left: 8px">
                显示图表
              </a-button>
            </a-form-item>
          </a-col>
        </a-row>
      </div>

      <!-- 测试结果显示 -->
      <div class="test-results" v-if="testResult">
        <a-divider>测试结果</a-divider>
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="商品ID">{{ testResult.materialId }}</a-descriptions-item>
          <a-descriptions-item label="数据条数">{{ testResult.dataCount }}</a-descriptions-item>
          <a-descriptions-item label="开始日期">{{ testResult.beginTime }}</a-descriptions-item>
          <a-descriptions-item label="结束日期">{{ testResult.endTime }}</a-descriptions-item>
        </a-descriptions>

        <!-- 出库数据表格 -->
        <a-table
          :columns="outboundColumns"
          :dataSource="testResult.dailyOutData"
          :pagination="{ pageSize: 10 }"
          size="small"
          style="margin-top: 16px"
        />
      </div>

      <!-- 图表弹窗 -->
      <StockChartModal
        :visible="chartModal.visible"
        :materialInfo="chartModal.materialInfo"
        :dateRange="chartModal.dateRange"
        @cancel="handleChartModalCancel"
      />
    </a-card>
  </div>
</template>

<script>
import moment from 'moment'
import { getAction } from '@/api/manage'
import StockChartModal from '@/components/charts/StockChartModal'

export default {
  name: 'ChartTest',
  components: {
    StockChartModal
  },
  data() {
    return {
      loading: false,
      testParams: {
        materialId: '1',
        beginDate: moment().subtract(1, 'month'),
        endDate: moment()
      },
      testResult: null,
      chartModal: {
        visible: false,
        materialInfo: {},
        dateRange: []
      },
      outboundColumns: [
        { title: '商品编码', dataIndex: 'barCode', width: 120 },
        { title: '商品名称', dataIndex: 'materialName', width: 200 },
        { title: '出库日期', dataIndex: 'outDate', width: 120 },
        { title: '出库数量', dataIndex: 'outQuantity', width: 120 }
      ]
    }
  },
  computed: {
    hasTestData() {
      return this.testResult && this.testResult.dailyOutData && this.testResult.dailyOutData.length > 0
    }
  },
  methods: {
    // 测试数据获取
    async testChartData() {
      this.loading = true
      try {
        const params = {
          materialId: this.testParams.materialId,
          beginTime: this.testParams.beginDate.format('YYYY-MM-DD'),
          endTime: this.testParams.endDate.format('YYYY-MM-DD')
        }
        
        console.log('测试参数:', params)
        
        const response = await getAction('/depotItem/testChartData', params)
        
        console.log('测试响应:', response)
        
        if (response.code === 200) {
          this.testResult = response.data
          this.$message.success(`数据获取成功，共 ${response.data.dataCount} 条记录`)
        } else {
          this.$message.error(response.data || '数据获取失败')
        }
      } catch (error) {
        console.error('测试失败:', error)
        this.$message.error('测试接口调用失败')
      } finally {
        this.loading = false
      }
    },

    // 显示图表
    showChart() {
      if (!this.hasTestData) {
        this.$message.warning('请先获取测试数据')
        return
      }

      // 构造图表所需的商品信息
      const firstOutData = this.testResult.dailyOutData && this.testResult.dailyOutData.length > 0
        ? this.testResult.dailyOutData[0]
        : {}

      const materialInfo = {
        materialId: parseInt(this.testParams.materialId),
        barCode: firstOutData.barCode || 'TEST001',
        materialName: firstOutData.materialName || '测试商品',
        currentPeriodStock: 100,
        previousPeriodStock: 150,
        currentPeriodOut: 50,
        previousPeriodOut: 30
      }

      this.chartModal.materialInfo = materialInfo
      this.chartModal.dateRange = [this.testParams.beginDate, this.testParams.endDate]
      this.chartModal.visible = true
    },

    // 关闭图表弹窗
    handleChartModalCancel() {
      this.chartModal.visible = false
    }
  }
}
</script>

<style scoped>
.chart-test-page {
  padding: 24px;
}

.test-params {
  background: #fafafa;
  padding: 16px;
  border-radius: 6px;
  margin-bottom: 16px;
}

.test-results {
  margin-top: 16px;
}
</style>
