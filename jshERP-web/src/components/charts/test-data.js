// 测试数据和工具函数
import moment from 'moment'

/**
 * 生成测试用的出库数据
 * @param {string} beginDate - 开始日期
 * @param {string} endDate - 结束日期
 * @param {string} barCode - 商品编码
 * @returns {Array} 模拟的出库数据
 */
export function generateTestOutboundData(beginDate, endDate, barCode = 'TEST001') {
  const data = []
  const start = moment(beginDate)
  const end = moment(endDate)
  
  let current = start.clone()
  while (current.isSameOrBefore(end)) {
    // 随机生成出库量（0-20件，70%概率有出库）
    const hasOutbound = Math.random() > 0.3
    const quantity = hasOutbound ? Math.floor(Math.random() * 20) + 1 : 0
    
    if (quantity > 0) {
      data.push({
        barCode: barCode,
        materialName: '测试商品',
        outDate: current.format('YYYY-MM-DD'),
        outQuantity: quantity.toString()
      })
    }
    
    current.add(1, 'day')
  }
  
  return data
}

/**
 * 生成测试用的商品信息
 * @returns {Object} 模拟的商品信息
 */
export function generateTestMaterialInfo() {
  return {
    materialId: 1001,
    barCode: 'TEST001',
    materialName: '测试商品A',
    currentPeriodStock: 150,
    previousPeriodStock: 200,
    currentPeriodOut: 80,
    previousPeriodOut: 60
  }
}

/**
 * 测试图表数据转换函数
 * @param {Function} transformFunction - 要测试的转换函数
 */
export function testDataTransformation(transformFunction) {
  const testParams = {
    materialId: 1001,
    barCode: 'TEST001',
    beginDate: '2024-01-01',
    endDate: '2024-01-31',
    currentPeriodStock: 150
  }
  
  const testOutboundData = generateTestOutboundData(
    testParams.beginDate, 
    testParams.endDate, 
    testParams.barCode
  )
  
  console.log('测试参数:', testParams)
  console.log('测试出库数据:', testOutboundData)
  
  const result = transformFunction(testOutboundData, testParams)
  
  console.log('转换结果:', result)
  
  // 验证结果
  const validation = {
    hasDates: result.data && result.data.dates && result.data.dates.length > 0,
    hasStockData: result.data && result.data.stockData && result.data.stockData.length > 0,
    hasOutboundData: result.data && result.data.dailyOutData && result.data.dailyOutData.length > 0,
    dataLengthMatch: result.data && 
      result.data.dates.length === result.data.stockData.length &&
      result.data.dates.length === result.data.dailyOutData.length
  }
  
  console.log('验证结果:', validation)
  
  return {
    success: Object.values(validation).every(v => v === true),
    validation,
    result
  }
}

// 导出默认对象
export default {
  generateTestOutboundData,
  generateTestMaterialInfo,
  testDataTransformation
}
