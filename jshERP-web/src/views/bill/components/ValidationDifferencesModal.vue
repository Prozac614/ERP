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
        row-key="materialName"
        size="small"
        bordered
      >
        <template slot="materialName" slot-scope="text, record">
          <div>
            <div style="font-weight: bold;">{{ text }}</div>
            <div style="font-size: 12px; color: #666;">{{ record.materialBarCode }}</div>
          </div>
        </template>
        <template v-for="user in allUsers" :slot="user" slot-scope="text, record">
          <span :key="user" :style="getDifferenceStyle(record, user)">
            {{ record.userQuantities[user] !== undefined ? Math.floor(record.userQuantities[user]) : '-' }}
          </span>
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
      // 收集所有用户和商品信息
      const userSet = new Set()
      const materialMap = new Map()
      
      this.differences.forEach(diff => {
        const materialKey = diff.materialName || diff.materialBarCode
        
        // 从users字段获取用户列表
        const userList = diff.users ? diff.users.split(',').map(u => u.trim()).filter(u => u.length > 0) : []
        
        // 解析description中的用户和数量信息
        const userQuantities = this.parseUserQuantities(diff.description)
        
        // 收集用户信息
        userList.forEach(user => {
          if (user && user.length > 0) {
            userSet.add(user)
          }
        })
        
        // 构建商品信息
        materialMap.set(materialKey, {
          materialName: diff.materialName || materialKey,
          materialBarCode: diff.materialBarCode || '',
          userQuantities: userQuantities
        })
      })
      
      // 转换为数组并排序，过滤掉空值
      this.allUsers = Array.from(userSet).filter(user => user && user.length > 0).sort()
      this.matrixData = Array.from(materialMap.values()).sort((a, b) => 
        a.materialName.localeCompare(b.materialName)
      )
      
      // 构建动态列
      this.matrixColumns = [
        {
          title: '商品信息',
          dataIndex: 'materialName',
          width: 200,
          fixed: 'left',
          scopedSlots: { customRender: 'materialName' }
        },
        ...this.allUsers.map(user => ({
          title: user,
          dataIndex: user,
          width: 100,
          align: 'center',
          scopedSlots: { customRender: user }
        }))
      ]
    },
    
    parseUserQuantities(description) {
      const userQuantities = {}
      
      // 先找到"各用户出库数量不一致:"之后的部分
      const startIndex = description.indexOf('各用户出库数量不一致:')
      if (startIndex === -1) return userQuantities
      
      const userPart = description.substring(startIndex + '各用户出库数量不一致:'.length)
      
      // 解析类似 " 用户A(ID:1): 10; 用户B(ID:2): 5;" 的格式
      const regex = /\s*([^(]+)\(ID:[^)]+\):\s*([^;]+);/g
      let match
      
      while ((match = regex.exec(userPart)) !== null) {
        const userName = match[1].trim()
        const quantity = match[2].trim()
        // 确保数量显示为整数，不带小数点
        const intQuantity = parseInt(parseFloat(quantity))
        userQuantities[userName] = intQuantity
      }
      
      return userQuantities
    },
    
    getDifferenceStyle(record, user) {
      const quantities = Object.values(record.userQuantities)
      const currentQuantity = record.userQuantities[user]
      
      if (!currentQuantity || quantities.length <= 1) {
        return {}
      }
      
      // 如果数量不一致，用颜色标记
      const hasInconsistency = quantities.some(q => q !== currentQuantity)
      
      if (hasInconsistency) {
        return {
          'background-color': '#ffebee',
          'color': '#c62828',
          'font-weight': 'bold',
          'padding': '4px 8px',
          'border-radius': '4px'
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