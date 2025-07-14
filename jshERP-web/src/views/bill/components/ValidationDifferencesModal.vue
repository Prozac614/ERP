<template>
  <a-modal
    title="校验差异详情"
    :visible="visible"
    :footer="null"
    @cancel="handleCancel"
  >
    <div>
      <a-alert
        message="校验失败"
        description="发现以下单据内容不一致，请核实后重新录入单据。"
        type="error"
        show-icon
        style="margin-bottom: 16px"
      />
      
      <a-table
        :columns="matrixColumns"
        :data-source="matrixData"
        :pagination="false"
        row-key="materialKey"
        size="small"
        bordered
        style="width: auto; min-width: 400px; max-width: 90vw; margin: 0 auto;"
      >
        <template slot="materialInfo" slot-scope="text, record">
          <div>
            <div style="font-weight: bold;">{{ record.materialName }}</div>
            <div style="font-size: 12px; color: #666;">{{ record.materialBarCode }}</div>
          </div>
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
      allUsers: []
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
        materialsData.set(materialKey, {
          materialKey: materialKey,
          materialName: diff.materialName || '未知商品',
          materialBarCode: diff.materialBarCode || '',
          userQuantities: userQuantities
        })
      })
      this.allUsers = Array.from(allUsersSet).sort()
      this.matrixColumns = [
        {
          title: '商品信息',
          dataIndex: 'materialInfo',
          width: 200,
          scopedSlots: { customRender: 'materialInfo' }
        }
      ]
      this.allUsers.forEach((user, index) => {
        const userColumnKey = `user_${index}`
        this.matrixColumns.push({
          title: user,
          dataIndex: userColumnKey,
          width: 100,
          align: 'center',
          customRender: ((columnKey) => {
            return (text, record) => {
              if (text !== undefined && text !== null && text !== '') {
                try {
                  const numValue = parseFloat(String(text))
                  if (!isNaN(numValue)) {
                    return Math.floor(numValue).toString()
                  }
                } catch (e) {}
                return String(text)
              }
              return '-'
            }
          })(userColumnKey)
        })
      })
      this.matrixData = Array.from(materialsData.values()).map(item => {
        const rowData = {
          materialKey: item.materialKey,
          materialName: item.materialName,
          materialBarCode: item.materialBarCode,
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
    },
    
    extractUserQuantities(description) {
      const userQuantities = {}
      
      if (!description) return userQuantities
      
      // 查找"各用户出库数量不一致:"后的内容
      const marker = '各用户出库数量不一致:'
      const startIndex = description.indexOf(marker)
      if (startIndex === -1) {
        return userQuantities
      }
      
      const userPart = description.substring(startIndex + marker.length).trim()
      
      // 分割每个用户的信息 "用户名(ID:xxx): 数量; "
      const userEntries = userPart.split(';')
      
      userEntries.forEach(entry => {
        const trimmedEntry = entry.trim()
        if (!trimmedEntry) return
        
        // 匹配格式: "用户名(ID:xxx): 数量"
        const match = trimmedEntry.match(/^(.+?)\(ID:\d+\):\s*(.+)$/)
        if (match) {
          const userName = match[1].trim()
          const quantity = match[2].trim()
          userQuantities[userName] = quantity
        }
      })
      
      return userQuantities
    },
    
    getDifferenceStyle(record, user) {
      const quantities = Object.values(record.userQuantities)
      const currentQuantity = record.userQuantities[user]
      
      if (currentQuantity === undefined || quantities.length <= 1) {
        return {}
      }
      
      // 检查是否有不一致的数量
      const hasInconsistency = quantities.some(q => 
        Math.floor(parseFloat(q)) !== Math.floor(parseFloat(currentQuantity))
      )
      
      if (hasInconsistency) {
        return {
          backgroundColor: '#ffebee',
          color: '#c62828',
          fontWeight: 'bold',
          padding: '4px 8px',
          borderRadius: '4px'
        }
      }
      
      return {}
    }
  }
}
</script>

<style scoped>
.ant-table-tbody > tr > td {
  padding: 8px 16px;
}

.ant-table-thead > tr > th {
  background-color: #fafafa;
  font-weight: 600;
  text-align: center;
}
</style> 