<template>
  <a-modal
    title="校验差异详情"
    :visible="visible"
    :footer="null"
    :width="modalWidth"
    @cancel="handleCancel"
  >
    <div>
      <a-alert
        message="校验失败"
        :description="getAlertDescription()"
        type="error"
        show-icon
        style="margin-bottom: 16px"
      />
      
      <!-- 单据数量不一致时显示的提示信息 -->
      <div v-if="hasBillCountInconsistency()" class="bill-count-message">
        <a-alert
          :message="getBillCountMessage()"
          type="warning"
          show-icon
          style="margin-bottom: 16px"
        />
      </div>
      
      <a-table
        :columns="matrixColumns"
        :data-source="matrixData"
        :pagination="false"
        row-key="materialKey"
        size="small"
        bordered
        style="width: 100%; margin: 0 auto;"
      >
        <template slot="materialInfo" slot-scope="text, record">
          <div>
            <div style="font-weight: bold;">{{ record.materialName }}</div>
            <div style="font-size: 12px; color: #666;">{{ record.materialBarCode }}</div>
          </div>
        </template>
        
        <!-- 动态渲染用户列 -->
        <template v-for="(user, index) in allUsers">
          <template :slot="`user_${index}`" slot-scope="text, record">
            <div :key="`user_${index}`" :style="getDifferenceStyle(record, user, index)">
              {{ formatUserData(record, user, index) }}
            </div>
          </template>
        </template>
      </a-table>
      
      <div style="text-align: center; margin-top: 16px;">
        <a-button type="primary" @click="handleCancel">
          确定
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
export default {
  name: 'ValidationDifferencesModal',
  data() {
    return {
      visible: false,
      differences: [],
      matrixData: [],
      matrixColumns: [],
      allUsers: [],
      modalWidth: 600
    }
  },
  created() {
  },
  mounted() {
  },
  methods: {
    show(differences) {
      this.visible = true
      this.differences = differences || []
      this.processMatrixData()
    },
    
    handleCancel() {
      this.visible = false
      this.differences = []
      this.matrixData = []
      this.matrixColumns = []
      this.allUsers = []
      this.modalWidth = 600
    },

    getAlertDescription() {
      if (this.hasBillCountInconsistency()) {
        return '发现用户间单据数量不一致，请确保所有用户都录入了相应的单据。'
      }
      return '发现以下单据内容不一致，请核实后重新录入单据。'
    },

    hasBillCountInconsistency() {
      return this.differences.some(diff => diff.diffType === 'BILL_COUNT_INCONSISTENT')
    },

    getBillCountMessage() {
      if (!this.differences || this.differences.length === 0) return ''
      
      const diff = this.differences.find(d => d.diffType === 'BILL_COUNT_INCONSISTENT')
      return diff ? diff.description : ''
    },
    
    processMatrixData() {
      if (!this.differences || this.differences.length === 0) {
        return
      }
      // 收集所有用户和商品信息
      const allUsersSet = new Set()
      const materialsData = new Map()
      this.differences.forEach((diff, index) => {
        const materialKey = diff.materialBarCode || diff.materialName || `material_${index}`
        // 优先使用结构化的用户数量数据，如果没有则fallback到解析description
        let userQuantities = diff.userQuantities || {}
        if (Object.keys(userQuantities).length === 0) {
          userQuantities = this.extractUserQuantities(diff.description)
        }
        Object.keys(userQuantities).forEach(user => {
          if (user && user.trim()) {
            allUsersSet.add(user.trim())
          }
        })
        // 使用商品和商店的组合作为唯一键
        const shopKey = diff.shopName || ''
        const combinedKey = `${materialKey}_${shopKey}`
        materialsData.set(combinedKey, {
          materialKey: combinedKey,
          materialName: diff.materialName || '未知商品',
          materialBarCode: diff.materialBarCode || '',
          shopName: diff.shopName || '',
          userQuantities: userQuantities
        })
      })
      this.allUsers = Array.from(allUsersSet).sort()
      this.matrixColumns = [
        {
          title: '销售店铺',
          dataIndex: 'shopName',
          width: 120,
          align: 'center',
          customRender: (text) => text || '未指定商店'
        },
        {
          title: '商品信息',
          dataIndex: 'materialInfo',
          width: 160,
          scopedSlots: { customRender: 'materialInfo' }
        }
      ]
      this.allUsers.forEach((user, index) => {
        const userColumnKey = `user_${index}`
        this.matrixColumns.push({
          title: user,
          dataIndex: userColumnKey,
          width: 150,
          align: 'center',
          scopedSlots: { customRender: userColumnKey }
        })
      })
      // 将Map转换为数组并排序
      this.matrixData = Array.from(materialsData.values())
        .sort((a, b) => {
          // 首先按店铺排序
          const shopA = a.shopName || '';
          const shopB = b.shopName || '';
          if (shopA !== shopB) {
            // 空店铺排在最后
            if (!shopA) return 1;
            if (!shopB) return -1;
            return shopA.localeCompare(shopB);
          }

          // 然后按唛头排序
          const barCodeA = a.materialBarCode || '';
          const barCodeB = b.materialBarCode || '';
          
          // 提取唛头的字母和数字部分
          const [, letterA = '', numberA = ''] = barCodeA.match(/^([WB])?(\d+)/) || [];
          const [, letterB = '', numberB = ''] = barCodeB.match(/^([WB])?(\d+)/) || [];
          
          // W在前，B在后
          if (letterA !== letterB) {
            if (letterA === 'W') return -1;
            if (letterB === 'W') return 1;
            if (letterA === 'B') return -1;
            if (letterB === 'B') return 1;
          }
          
          // 按数字部分排序
          const numA = parseInt(numberA) || 0;
          const numB = parseInt(numberB) || 0;
          return numA - numB;
        })
        .map(item => {
        const rowData = {
          materialKey: item.materialKey,
          materialName: item.materialName,
          materialBarCode: item.materialBarCode,
          shopName: item.shopName,
          userQuantities: item.userQuantities
        }
        this.allUsers.forEach((user, index) => {
          const userColumnKey = `user_${index}`
          const rawValue = item.userQuantities[user]
          let processedValue = ''
          if (rawValue !== undefined && rawValue !== null) {
            if (typeof rawValue === 'object' && rawValue.toString) {
              processedValue = rawValue.toString()
            } else {
              processedValue = String(rawValue)
            }
          }
          rowData[userColumnKey] = processedValue
        })
        return rowData
      })
      
      // 根据列数量计算合适的模态框宽度
      this.calculateModalWidth()
    },
    
    calculateModalWidth() {
      // 基础宽度：店铺列(120px) + 商品信息列(160px) + 边距和滚动条(60px)
      const baseWidth = 120 + 160 + 60
      // 用户列宽度：每个用户列150px
      const userColumnsWidth = this.allUsers.length * 150
      // 计算总宽度
      const totalWidth = baseWidth + userColumnsWidth
      
      // 设置最小宽度500px，最大宽度为屏幕宽度的90%
      const minWidth = 500
      const maxWidth = Math.floor(window.innerWidth * 0.9)
      
      this.modalWidth = Math.max(minWidth, Math.min(totalWidth, maxWidth))
    },
    
    extractUserQuantities(description) {
      const userQuantities = {}
      
      if (!description) return userQuantities
      
      // 查找差异描述的内容，支持多种格式
      const markers = ['各用户数量和单价均不一致: ', '各用户数量不一致: ', '各用户单价不一致: ']
      let startIndex = -1
      let foundMarker = ''
      
      for (const marker of markers) {
        const index = description.indexOf(marker)
        if (index !== -1) {
          startIndex = index
          foundMarker = marker
          break
        }
      }
      
      if (startIndex === -1) {
        return userQuantities
      }
      
      const userPart = description.substring(startIndex + foundMarker.length).trim()
      
      // 分割每个用户的信息 "用户名(ID:xxx): 数量=xxx, 单价=xxx; "
      const userEntries = userPart.split(';')
      
      userEntries.forEach(entry => {
        const trimmedEntry = entry.trim()
        if (!trimmedEntry) return
        
        // 匹配格式: "用户名(ID:xxx): 数量=xxx, 单价=xxx" 或 "用户名(ID:xxx): 数量"
        const match = trimmedEntry.match(/^(.+?)\(ID:\d+\):\s*(.+)$/)
        if (match) {
          const userName = match[1].trim()
          const dataStr = match[2].trim()
          
          // 尝试解析新格式: "数量=xxx, 单价=xxx"
          const quantityMatch = dataStr.match(/数量=([^,]+)/)
          if (quantityMatch) {
            userQuantities[userName] = quantityMatch[1].trim()
          } else {
            // 兼容旧格式，直接使用整个字符串作为数量
            userQuantities[userName] = dataStr
          }
        }
      })
      
      return userQuantities
    },
    
    formatUserData(record, user, index) {
      const userColumnKey = `user_${index}`
      const rawValue = record[userColumnKey]
      
      if (!rawValue || rawValue === '-') {
        return '-'
      }
      
      // 检查是否有单价信息（从原始差异数据中解析）
      const userQuantities = record.userQuantities || {}
      const quantity = userQuantities[user]
      
      if (quantity !== undefined && quantity !== null) {
        // 尝试从description中解析单价信息
        const priceInfo = this.extractUserPriceInfo(record, user)
        if (priceInfo) {
          return `数量: ${Math.floor(parseFloat(quantity))}\n单价: ${priceInfo}`
        } else {
          return Math.floor(parseFloat(quantity)).toString()
        }
      }
      
      return rawValue || '-'
    },
    
    extractUserPriceInfo(record, user) {
      // 从相关差异记录中找到这个商品和用户的单价信息
      if (!this.differences) return null
      
      const relevantDiff = this.differences.find(diff => 
        diff.materialBarCode === record.materialBarCode || 
        diff.materialName === record.materialName
      )
      
      if (!relevantDiff || !relevantDiff.description) return null
      
      // 解析description中的单价信息: "用户名(ID:xxx): 数量=xxx, 单价=xxx; "
      const userPattern = new RegExp(`${user}\\(ID:\\d+\\):\\s*数量=[^,]+,\\s*单价=([^;]+)`, 'g')
      const match = userPattern.exec(relevantDiff.description)
      
      if (match && match[1]) {
        const price = match[1].trim()
        if (price === '无') {
          return '无'
        }
        // 尝试格式化价格为合理的小数位数
        const numPrice = parseFloat(price)
        if (!isNaN(numPrice)) {
          return numPrice.toFixed(2)
        }
        return price
      }
      
      return null
    },

    getDifferenceStyle(record, user, index) {
      const userQuantities = record.userQuantities || {}
      const allQuantities = Object.values(userQuantities)
      const currentQuantity = userQuantities[user]
      
      if (currentQuantity === undefined || allQuantities.length <= 1) {
        return {}
      }
      
      // 检查数量是否不一致
      const quantityInconsistent = allQuantities.some(q => 
        Math.floor(parseFloat(q || 0)) !== Math.floor(parseFloat(currentQuantity || 0))
      )
      
      // 检查单价是否不一致
      const priceInfo = this.extractUserPriceInfo(record, user)
      const priceInconsistent = this.checkPriceInconsistency(record, user)
      
      if (quantityInconsistent || priceInconsistent) {
        return {
          backgroundColor: '#ffebee',
          color: '#c62828',
          fontWeight: 'bold',
          padding: '6px 8px',
          borderRadius: '4px',
          border: '1px solid #ffcdd2',
          whiteSpace: 'pre-line'
        }
      }
      
      return {
        padding: '6px 8px',
        whiteSpace: 'pre-line'
      }
    },
    
    checkPriceInconsistency(record, user) {
      if (!this.differences) return false
      
      const relevantDiff = this.differences.find(diff => 
        diff.materialBarCode === record.materialBarCode || 
        diff.materialName === record.materialName
      )
      
      if (!relevantDiff) return false
      
      // 检查差异类型是否包含单价不一致
      return relevantDiff.diffType === 'PRICE_INCONSISTENT' || 
             relevantDiff.diffType === 'QUANTITY_PRICE_INCONSISTENT'
    }
  }
}
</script>

<style scoped>
.ant-table-tbody > tr > td {
  padding: 4px 8px;
  vertical-align: top;
}

.ant-table-thead > tr > th {
  background-color: #fafafa;
  font-weight: 600;
  text-align: center;
  padding: 8px 4px;
}

.ant-table-tbody > tr > td > div {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  line-height: 1.4;
}

/* 店铺列样式 */
.ant-table-tbody > tr > td:first-child {
  font-weight: 500;
  color: #1890ff;
  background-color: #f0f7ff;
}

/* 保证表格内容正确对齐 */
.ant-table-small .ant-table-tbody > tr > td {
  padding: 4px;
}

/* 突出显示差异的样式增强 */
.difference-highlight {
  background: linear-gradient(135deg, #ffebee 0%, #fce4ec 100%);
  box-shadow: 0 1px 3px rgba(198, 40, 40, 0.2);
  transition: all 0.2s ease;
}

.difference-highlight:hover {
  box-shadow: 0 2px 6px rgba(198, 40, 40, 0.3);
  transform: translateY(-1px);
}

.bill-count-message {
  margin-bottom: 16px;
}
</style> 