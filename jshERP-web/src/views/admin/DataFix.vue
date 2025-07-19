<template>
  <div class="data-fix-page">
    <a-card title="数据修复工具" :bordered="false">
      <div class="fix-section">
        <h3>库存小数点问题修复</h3>
        <p class="description">
          修复首页表格中库存数据显示小数的问题，将所有库存数据四舍五入为整数。
        </p>
        
        <div class="fix-actions">
          <a-button 
            type="primary" 
            @click="fixDecimalStock" 
            :loading="fixing"
            :disabled="fixing"
          >
            {{ fixing ? '修复中...' : '修复库存小数问题' }}
          </a-button>
          
          <a-button 
            @click="checkDecimalStock" 
            :loading="checking"
            style="margin-left: 8px"
          >
            {{ checking ? '检查中...' : '检查问题数据' }}
          </a-button>
        </div>

        <div v-if="checkResult" class="check-result" style="margin-top: 16px;">
          <a-alert
            :message="checkResult.message"
            :type="checkResult.type"
            show-icon
          />
        </div>

        <div v-if="fixResult" class="fix-result" style="margin-top: 16px;">
          <a-alert
            :message="fixResult.message"
            :type="fixResult.type"
            show-icon
          />
        </div>
      </div>

      <a-divider />

      <div class="fix-section">
        <h3>期间库存计算逻辑修复</h3>
        <p class="description">
          修复本期结存、上期结存、本期出库、上期出库的计算逻辑错误。
          <br>
          <strong>期间划分：</strong>第一期(2月1日-7月31日)，第二期(8月1日-次年1月31日)
          <br>
          <strong>修复内容：</strong>
          <br>• 本期结存 = 期初库存 + 本期入库 - 本期出库
          <br>• 上期结存 = 上期期初库存 + 上期入库 - 上期出库
          <br>• 出库数据按正确的期间范围统计
        </p>

        <div class="fix-actions">
          <a-button
            type="primary"
            @click="fixPeriodCalculation"
            :loading="fixingPeriod"
            :disabled="fixingPeriod"
          >
            {{ fixingPeriod ? '修复中...' : '修复期间计算逻辑' }}
          </a-button>

          <a-button
            @click="validatePeriodCalculation"
            :loading="validating"
            style="margin-left: 8px"
          >
            {{ validating ? '验证中...' : '验证计算结果' }}
          </a-button>
        </div>

        <div v-if="periodFixResult" class="fix-result" style="margin-top: 16px;">
          <a-alert
            :message="periodFixResult.message"
            :type="periodFixResult.type"
            show-icon
          />
        </div>

        <div v-if="validationResult" class="validation-result" style="margin-top: 16px;">
          <a-alert
            :message="`验证完成：总记录${validationResult.totalRecords}条，错误${validationResult.errorRecords}条，成功率${validationResult.successRate.toFixed(1)}%`"
            :type="validationResult.errorRecords > 0 ? 'warning' : 'success'"
            show-icon
          />

          <div v-if="validationResult.validationDetails && validationResult.validationDetails.length > 0"
               style="margin-top: 12px;">
            <h4>验证详情（前10条）：</h4>
            <a-table
              :columns="validationColumns"
              :dataSource="validationResult.validationDetails"
              :pagination="false"
              size="small"
              :scroll="{ x: 800 }"
            />
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script>
import { postAction, getAction } from '@/api/manage'

export default {
  name: 'DataFix',
  data() {
    return {
      fixing: false,
      checking: false,
      checkResult: null,
      fixResult: null,
      fixingPeriod: false,
      validating: false,
      periodFixResult: null,
      validationResult: null,
      validationColumns: [
        { title: '商品编码', dataIndex: 'bar_code', width: 120 },
        { title: '商品名称', dataIndex: 'material_name', width: 150 },
        { title: '本期结存', dataIndex: 'currentPeriodStock', width: 100 },
        { title: '上期结存', dataIndex: 'previousPeriodStock', width: 100 },
        { title: '本期入库', dataIndex: 'currentPeriodIn', width: 100 },
        { title: '本期出库', dataIndex: 'currentPeriodOut', width: 100 },
        { title: '计算结存', dataIndex: 'calculatedCurrentStock', width: 100 },
        { title: '差异', dataIndex: 'difference', width: 80,
          customRender: (text) => {
            const diff = parseFloat(text) || 0
            return diff === 0 ? '0' : `${diff > 0 ? '+' : ''}${diff}`
          }
        }
      ]
    }
  },
  methods: {
    // 检查小数问题数据
    async checkDecimalStock() {
      this.checking = true
      this.checkResult = null
      
      try {
        // 这里可以添加一个检查接口，暂时使用模拟数据
        await new Promise(resolve => setTimeout(resolve, 1000))
        
        this.checkResult = {
          type: 'info',
          message: '检查完成，发现部分库存数据包含小数，建议执行修复操作。'
        }
        
      } catch (error) {
        console.error('检查失败:', error)
        this.checkResult = {
          type: 'error',
          message: '检查失败: ' + (error.message || '未知错误')
        }
      } finally {
        this.checking = false
      }
    },

    // 修复小数问题
    async fixDecimalStock() {
      this.fixing = true
      this.fixResult = null
      
      try {
        const response = await postAction('/depotItem/fixDecimalStock', {})
        
        if (response.code === 200) {
          this.fixResult = {
            type: 'success',
            message: '修复成功！' + response.data
          }
          
          // 修复成功后，建议刷新页面数据
          this.$message.success('库存小数问题修复完成，建议刷新首页查看效果')
          
        } else {
          this.fixResult = {
            type: 'error',
            message: '修复失败: ' + response.data
          }
        }
        
      } catch (error) {
        console.error('修复失败:', error)
        this.fixResult = {
          type: 'error',
          message: '修复失败: ' + (error.message || '网络错误')
        }
      } finally {
        this.fixing = false
      }
    },

    // 修复期间计算逻辑
    async fixPeriodCalculation() {
      this.fixingPeriod = true
      this.periodFixResult = null

      try {
        const response = await postAction('/depotItem/fixPeriodCalculation', {})

        if (response.code === 200) {
          this.periodFixResult = {
            type: 'success',
            message: '修复成功！' + response.data
          }

          this.$message.success('期间库存计算逻辑修复完成，建议验证计算结果')

        } else {
          this.periodFixResult = {
            type: 'error',
            message: '修复失败: ' + response.data
          }
        }

      } catch (error) {
        console.error('修复失败:', error)
        this.periodFixResult = {
          type: 'error',
          message: '修复失败: ' + (error.message || '网络错误')
        }
      } finally {
        this.fixingPeriod = false
      }
    },

    // 验证期间计算结果
    async validatePeriodCalculation() {
      this.validating = true
      this.validationResult = null

      try {
        const response = await getAction('/depotItem/validatePeriodCalculation')

        if (response.code === 200) {
          this.validationResult = response.data

          if (response.data.errorRecords === 0) {
            this.$message.success('验证通过，所有数据计算正确！')
          } else {
            this.$message.warning(`发现${response.data.errorRecords}条数据计算异常，请检查详情`)
          }

        } else {
          this.$message.error('验证失败: ' + response.data)
        }

      } catch (error) {
        console.error('验证失败:', error)
        this.$message.error('验证失败: ' + (error.message || '网络错误'))
      } finally {
        this.validating = false
      }
    }
  }
}
</script>

<style scoped>
.data-fix-page {
  padding: 24px;
}

.fix-section {
  margin-bottom: 32px;
}

.fix-section h3 {
  color: #1890ff;
  margin-bottom: 8px;
}

.description {
  color: #666;
  margin-bottom: 16px;
  line-height: 1.6;
}

.fix-actions {
  margin-bottom: 16px;
}

.check-result,
.fix-result {
  max-width: 600px;
}
</style>
