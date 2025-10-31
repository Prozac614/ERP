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
      
      <div class="diff-table-wrapper">
        <table class="diff-table">
          <thead>
            <tr>
              <th v-for="column in matrixColumns" :key="column.dataIndex" :style="getHeaderStyle(column)">
                {{ column.title }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in matrixData" :key="row.materialKey">
              <td class="shop-cell" :style="getBodyCellStyle(0)">{{ row.shopName || '未指定商店' }}</td>
              <td class="material-cell" :style="getBodyCellStyle(1)">
                <div class="material-info">{{ row.materialBarCode }}</div>
              </td>
              <td
                v-for="(user, index) in allUsers"
                :key="`${row.materialKey}_${user}`"
                :style="getBodyCellStyle(index + 2)"
              >
                <div
                  class="detail-container"
                  :style="getDifferenceStyle(row, user, index)"
                  v-html="row[`user_${index}`]"
                ></div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
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
      modalWidth: 900
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
      this.modalWidth = 900
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
        const normalizedQuantities = {}
        Object.keys(userQuantities).forEach(user => {
          const trimmed = String(user).trim()
          if (trimmed) {
            normalizedQuantities[trimmed] = userQuantities[user]
            allUsersSet.add(trimmed)
          }
        })
        userQuantities = normalizedQuantities
        const userBillDetails = this.normalizeUserDetails(diff.userBillDetails)

        if (Object.keys(userBillDetails).length > 0 && Object.keys(userQuantities).length === 0) {
          const quantitiesFromDetails = {}
          Object.keys(userBillDetails).forEach(user => {
            const total = (userBillDetails[user] || []).reduce((sum, detail) => {
              const numeric = detail && detail.quantity !== undefined && detail.quantity !== null
                ? parseFloat(detail.quantity)
                : 0
              return sum + (isNaN(numeric) ? 0 : numeric)
            }, 0)
            const trimmed = String(user).trim()
            if (trimmed) {
              quantitiesFromDetails[trimmed] = total
              allUsersSet.add(trimmed)
            }
          })
          userQuantities = quantitiesFromDetails
        }
        
        Object.keys(userBillDetails).forEach(user => {
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
          userQuantities: userQuantities,
          userBillDetails: userBillDetails
        })
      })
      this.allUsers = Array.from(allUsersSet).sort()
      this.matrixColumns = [
        {
          title: '销售店铺',
          dataIndex: 'shopName',
          width: 120,
          align: 'center'
        },
        {
          title: '商品信息',
          dataIndex: 'materialInfo',
          width: 220,
          align: 'center'
        }
      ]
      this.allUsers.forEach((user, index) => {
        const userColumnKey = `user_${index}`
        this.matrixColumns.push({
          title: user,
          dataIndex: userColumnKey,
          width: 240,
          align: 'left'
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
          __userQuantities: item.userQuantities,
          __userBillDetails: item.userBillDetails
        }

        const quantityCounts = new Map()
        const normalizedDetailsByUser = new Map()

        this.allUsers.forEach(user => {
          const rawDetails = (item.userBillDetails && item.userBillDetails[user]) || []
          const normalizedList = rawDetails.map(detail => {
            const source = detail && typeof detail === 'object' ? detail : {}
            const billNumber = source.billNumber ? String(source.billNumber) : '-'
            let displayQuantity = '-'
            let numericQuantity = null
            if (source.quantity !== undefined && source.quantity !== null) {
              const parsedQuantity = parseFloat(source.quantity)
              if (!isNaN(parsedQuantity)) {
                numericQuantity = Math.floor(parsedQuantity)
                displayQuantity = String(Math.floor(parsedQuantity))
              } else {
                displayQuantity = String(source.quantity)
              }
            }

            if (numericQuantity !== null) {
              if (!quantityCounts.has(numericQuantity)) {
                quantityCounts.set(numericQuantity, new Map())
              }
              const perUserMap = quantityCounts.get(numericQuantity)
              perUserMap.set(user, (perUserMap.get(user) || 0) + 1)
            }

            return {
              billNumber,
              displayQuantity,
              numericQuantity,
              matched: false
            }
          })

          normalizedDetailsByUser.set(user, normalizedList)
        })

        const allocationByUser = new Map()
        this.allUsers.forEach(user => {
          allocationByUser.set(user, new Map())
        })

        quantityCounts.forEach((perUserMap, quantityValue) => {
          const counts = Array.from(perUserMap.entries()).filter(([, count]) => count > 0)
          if (counts.length < 2) {
            counts.forEach(([user]) => {
              allocationByUser.get(user).set(quantityValue, 0)
            })
            return
          }

          const total = counts.reduce((sum, [, count]) => sum + count, 0)
          counts.forEach(([user, count]) => {
            const totalOthers = total - count
            const unmatched = Math.max(0, count - totalOthers)
            const matched = count - unmatched
            allocationByUser.get(user).set(quantityValue, matched)
          })
        })

        this.allUsers.forEach((user, index) => {
          const userColumnKey = `user_${index}`
          const normalizedList = normalizedDetailsByUser.get(user) || []
          if (normalizedList.length > 0) {
            const allocationMap = allocationByUser.get(user) || new Map()
            const processedList = normalizedList.map(detail => {
              if (detail.numericQuantity !== null) {
                const remaining = allocationMap.get(detail.numericQuantity) || 0
                if (remaining > 0) {
                  allocationMap.set(detail.numericQuantity, remaining - 1)
                  return Object.assign({}, detail, { matched: true })
                }
              }
              return detail
            })
            const htmlContent = processedList.map(detail => {
              const classes = ['detail-line']
              if (detail.matched) {
                classes.push('detail-matched')
              } else {
                classes.push('detail-unmatched')
              }
              const billNumber = detail.billNumber || '-'
              const quantity = detail.displayQuantity !== undefined ? String(detail.displayQuantity) : '-'
              return `
                <div class="${classes.join(' ')}">
                  <span class="detail-label">单号:</span>
                  <span class="detail-value">${billNumber}</span>
                  <span class="detail-label"> 数量:</span>
                  <span class="detail-quantity ${detail.matched ? 'detail-quantity-match' : 'detail-quantity-unmatch'}">${quantity}</span>
                </div>
              `
            }).join('')
            rowData[userColumnKey] = htmlContent || '<div class="detail-line detail-placeholder">-</div>'
          } else {
            const userQuantities = item.userQuantities || {}
            const quantity = userQuantities[user]
            if (quantity !== undefined && quantity !== null) {
              const parsedQuantity = parseFloat(quantity)
              const numericQuantity = isNaN(parsedQuantity) ? null : Math.floor(parsedQuantity)
              const displayQuantity = isNaN(parsedQuantity) ? String(quantity) : String(Math.floor(parsedQuantity))
              rowData[userColumnKey] = `
                <div class="detail-line">
                  <span class="detail-label">单号:</span>
                  <span class="detail-value">-</span>
                  <span class="detail-label"> 数量:</span>
                  <span class="detail-quantity detail-quantity-unmatch">${displayQuantity}</span>
                </div>
              `
            } else {
              rowData[userColumnKey] = '<div class="detail-line detail-placeholder">-</div>'
            }
          }
        })

        return rowData
      })
      
      // 根据列数量计算合适的模态框宽度
      this.calculateModalWidth()
    },
    
    calculateModalWidth() {
      // 基础宽度：店铺列(120px) + 商品信息列(220px) + 边距和滚动条(140px)
      const baseWidth = 120 + 220 + 140
      // 用户列宽度：每个用户列240px
      const userColumnsWidth = this.allUsers.length * 240
      // 计算总宽度
      const totalWidth = baseWidth + userColumnsWidth
      
      // 设置最小宽度900px，最大宽度为屏幕宽度的90%
      const minWidth = 900
      const maxWidth = Math.floor(window.innerWidth * 0.9)
      
      this.modalWidth = Math.max(minWidth, Math.min(totalWidth, maxWidth))
    },

    getHeaderStyle(column) {
      const style = {}
      if (column.width) {
        style.width = `${column.width}px`
        style.minWidth = `${column.width}px`
      }
      style.textAlign = column.align || 'center'
      return style
    },

    getBodyCellStyle(columnIndex) {
      const column = this.matrixColumns[columnIndex]
      if (!column) {
        return {}
      }
      const style = {}
      if (column.width) {
        style.width = `${column.width}px`
        style.minWidth = `${column.width}px`
      }
      style.textAlign = column.align || (columnIndex <= 1 ? 'center' : 'left')
      return style
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

    normalizeUserDetails(rawDetails) {
      if (!rawDetails) {
        return {}
      }
      const toArray = (value) => {
        if (!value) return []
        if (Array.isArray(value)) return value
        if (typeof value === 'string') {
          try {
            const parsed = JSON.parse(value)
            return Array.isArray(parsed) ? parsed : (parsed ? [parsed] : [])
          } catch (e) {
            return []
          }
        }
        return [value]
      }
      if (Array.isArray(rawDetails)) {
        return rawDetails.reduce((acc, entry) => {
          if (entry && entry.key !== undefined) {
            const key = String(entry.key).trim()
            acc[key] = toArray(entry.value)
          }
          return acc
        }, {})
      }
      return Object.keys(rawDetails).reduce((acc, key) => {
        const trimmedKey = String(key).trim()
        acc[trimmedKey] = toArray(rawDetails[key])
        return acc
      }, {})
    },
    
    getDifferenceStyle(record, user, index) {
      const userQuantities = record.__userQuantities || {}
      const allQuantities = Object.values(userQuantities)
      const currentQuantity = userQuantities[user]
      
      if (currentQuantity === undefined || allQuantities.length <= 1) {
        const priceOnlyInconsistent = this.checkPriceInconsistency(record, user)
        return priceOnlyInconsistent
          ? {
              backgroundColor: '#ffebee',
              color: '#c62828',
              fontWeight: 'bold',
              borderRadius: '4px',
              border: '1px solid #ffcdd2'
            }
          : {}
      }

      // 检查数量是否不一致
      const quantityInconsistent = allQuantities.some(q => 
        Math.floor(parseFloat(q || 0)) !== Math.floor(parseFloat(currentQuantity || 0))
      )
      
      // 检查单价是否不一致
      const priceInconsistent = this.checkPriceInconsistency(record, user)

      if (quantityInconsistent || priceInconsistent) {
        return {
          backgroundColor: '#ffebee',
          color: '#c62828',
          fontWeight: 'bold',
          borderRadius: '4px',
          border: '1px solid #ffcdd2'
        }
      }
      
      return {}
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
.diff-table-wrapper {
  width: 100%;
  overflow-x: auto;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  background: #fff;
}

.diff-table thead th {
  background-color: #fafafa;
  font-weight: 600;
  text-align: center;
  padding: 10px 8px;
  border-bottom: 1px solid #e9e9e9;
  border-right: 1px solid #f0f0f0;
  font-size: 13px;
}

.diff-table tbody td {
  padding: 6px 8px;
  border-bottom: 1px solid #f5f5f5;
  border-right: 1px solid #f5f5f5;
  vertical-align: top;
  background: #fff;
}

.diff-table tbody tr:nth-child(even) td {
  background: #fcfcfc;
}

.diff-table tbody tr:last-child td {
  border-bottom: none;
}

.shop-cell {
  font-weight: 500;
  color: #1890ff;
  background-color: #f0f7ff;
  text-align: center;
}

.material-cell {
  text-align: center;
}

.material-info {
  font-weight: 600;
  color: #333;
}

.detail-container {
  min-height: 40px;
  padding: 6px 8px;
  max-height: 200px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.5;
  text-align: left;
}

.detail-line {
  display: block;
  margin-bottom: 4px;
  color: #333;
}

.detail-line:last-child {
  margin-bottom: 0;
}

.detail-label {
  color: #666;
}

.detail-value {
  margin-right: 6px;
}

.detail-quantity {
  font-weight: 600;
}

.detail-quantity-match {
  color: #2e7d32;
}

.detail-quantity-unmatch {
  color: #c62828;
}

.detail-placeholder {
  color: #999;
}

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