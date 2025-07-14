<template>
  <a-modal
    title="校验差异详情"
    :width="1000"
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
      matrixColumns: []
    }
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
    },
    
    processMatrixData() {
      console.log('原始差异数据:', this.differences)
      
      if (!this.differences || this.differences.length === 0) {
        return
      }
      
      // 收集所有用户和商品信息
      const allUsers = new Set()
      const materialsData = new Map()
      
      this.differences.forEach((diff, index) => {
        console.log(`处理差异 ${index}:`, diff)
        
        const materialKey = diff.materialBarCode || diff.materialName || `material_${index}`
        
        // 从description提取用户数量信息 - 使用更简单的解析方法
        const userQuantities = this.extractUserQuantities(diff.description)
        console.log(`商品 ${materialKey} 的用户数量:`, userQuantities)
        
        // 收集用户信息
        Object.keys(userQuantities).forEach(user => {
          if (user && user.trim()) {
            allUsers.add(user.trim())
          }
        })
        
        // 存储商品信息
        materialsData.set(materialKey, {
          materialKey: materialKey,
          materialName: diff.materialName || '未知商品',
          materialBarCode: diff.materialBarCode || '',
          userQuantities: userQuantities
        })
      })
      
      console.log('所有用户:', Array.from(allUsers))
      console.log('商品数据:', Array.from(materialsData.values()))
      
      // 构建表格列
      this.matrixColumns = [
        {
          title: '商品信息',
          dataIndex: 'materialInfo',
          width: 200,
          fixed: 'left',
          scopedSlots: { customRender: 'materialInfo' }
        }
      ]
      
      // 为每个用户添加列
      const sortedUsers = Array.from(allUsers).sort()
      console.log('排序后的用户列表:', sortedUsers)
      
      sortedUsers.forEach((user, index) => {
        console.log(`添加用户列 ${index}:`, user)
        
        // 使用安全的dataIndex，避免特殊字符问题
        const safeDataIndex = `user_${index}`
        
        this.matrixColumns.push({
          title: user,
          dataIndex: safeDataIndex,
          width: 100,
          align: 'center',
          customRender: (text, record) => {
            console.log(`渲染列 ${user}:`, {
              text: text,
              userQuantities: record.userQuantities,
              userValue: record.userQuantities[user]
            })
            
            const quantity = record.userQuantities[user]
            if (quantity !== undefined && quantity !== null) {
              const intQuantity = parseInt(quantity)
              const style = this.getDifferenceStyle(record, user)
              
              // 检查是否需要高亮
              if (style && Object.keys(style).length > 0) {
                return `<span style="background-color: ${style.backgroundColor}; color: ${style.color}; font-weight: ${style.fontWeight}; padding: ${style.padding}; border-radius: ${style.borderRadius};">${intQuantity}</span>`
              }
              return intQuantity.toString()
            }
            return '-'
          }
        })
      })
             
       // 同时修改表格数据，为每行添加对应的用户数据
       this.matrixData = Array.from(materialsData.values()).map(item => {
         const newItem = { ...item }
         sortedUsers.forEach((user, index) => {
           const safeDataIndex = `user_${index}`
           newItem[safeDataIndex] = item.userQuantities[user]
         })
         return newItem
       })
       
       console.log('最终表格列:', this.matrixColumns)
       console.log('最终表格数据:', this.matrixData)
    },
    
    extractUserQuantities(description) {
      const userQuantities = {}
      
      if (!description) return userQuantities
      
      console.log('解析描述:', description)
      
      // 查找"各用户出库数量不一致:"后的内容
      const marker = '各用户出库数量不一致:'
      const startIndex = description.indexOf(marker)
      if (startIndex === -1) {
        console.log('未找到标记:', marker)
        return userQuantities
      }
      
      const userPart = description.substring(startIndex + marker.length).trim()
      console.log('用户部分:', userPart)
      
      // 分割每个用户的信息 "用户名(ID:xxx): 数量; "
      const userEntries = userPart.split(';')
      
      userEntries.forEach(entry => {
        const trimmedEntry = entry.trim()
        if (!trimmedEntry) return
        
        console.log('处理条目:', trimmedEntry)
        
        // 匹配格式: "用户名(ID:xxx): 数量"
        const match = trimmedEntry.match(/^(.+?)\(ID:\d+\):\s*(.+)$/)
        if (match) {
          const userName = match[1].trim()
          const quantity = match[2].trim()
          console.log(`提取: 用户=${userName}, 数量=${quantity}`)
          userQuantities[userName] = quantity
        }
      })
      
      console.log('提取结果:', userQuantities)
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
        parseInt(q) !== parseInt(currentQuantity)
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