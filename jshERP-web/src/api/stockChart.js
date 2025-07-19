import { getAction, postAction } from '@/api/manage'
import moment from 'moment'

/**
 * 获取库存历史数据
 * @param {Object} params - 查询参数
 * @param {string} params.materialId - 商品ID
 * @param {string} params.beginDate - 开始日期
 * @param {string} params.endDate - 结束日期
 */
export function getStockHistory(params) {
    // TODO: 实际项目中调用真实API
    // return getAction('/depotItem/getStockHistory', params)

    // 临时使用模拟数据
    return new Promise((resolve) => {
        setTimeout(() => {
            const mockData = generateMockStockHistory(params)
            resolve({
                code: 200,
                message: 'success',
                data: mockData
            })
        }, 1000) // 模拟网络延迟
    })
}

/**
 * 获取出库流水数据
 * @param {Object} params - 查询参数
 * @param {string} params.materialId - 商品ID
 * @param {string} params.beginDate - 开始日期
 * @param {string} params.endDate - 结束日期
 */
export function getOutboundFlow(params) {
    // TODO: 实际项目中调用真实API
    // return getAction('/depotItem/getOutboundFlow', params)

    // 临时使用模拟数据
    return new Promise((resolve) => {
        setTimeout(() => {
            const mockData = generateMockOutboundFlow(params)
            resolve({
                code: 200,
                message: 'success',
                data: mockData
            })
        }, 1000) // 模拟网络延迟
    })
}

/**
 * 生成模拟库存历史数据
 */
function generateMockStockHistory(params) {
    const { beginDate, endDate } = params
    const startDate = moment(beginDate)
    const endDateMoment = moment(endDate)

    const dates = []
    const stockData = []
    const dailyOutData = []

    let currentDate = startDate.clone()
    let currentStock = Math.floor(Math.random() * 500) + 200 // 初始库存 200-700

    while (currentDate.isSameOrBefore(endDateMoment)) {
        dates.push(currentDate.format('YYYY-MM-DD'))

        // 模拟每日出库量 (0-20)
        const dailyOut = Math.floor(Math.random() * 21)
        dailyOutData.push(dailyOut)

        // 库存变化：减去出库量，偶尔补货
        currentStock -= dailyOut

        // 随机补货（10%概率）
        if (Math.random() < 0.1) {
            const restockAmount = Math.floor(Math.random() * 100) + 50
            currentStock += restockAmount
        }

        // 确保库存不为负数
        currentStock = Math.max(0, currentStock)
        stockData.push(currentStock)

        currentDate.add(1, 'day')
    }

    return {
        dates,
        stockData,
        dailyOutData
    }
}

/**
 * 生成模拟出库流水数据
 */
function generateMockOutboundFlow(params) {
    const { beginDate, endDate } = params
    const startDate = moment(beginDate)
    const endDateMoment = moment(endDate)

    const dates = []
    const outboundData = []
    const cumulativeData = []

    let currentDate = startDate.clone()
    let cumulativeOut = 0

    while (currentDate.isSameOrBefore(endDateMoment)) {
        dates.push(currentDate.format('YYYY-MM-DD'))

        // 模拟每日出库量，工作日较多，周末较少
        const isWeekend = currentDate.day() === 0 || currentDate.day() === 6
        const baseAmount = isWeekend ? 3 : 12
        const dailyOut = Math.floor(Math.random() * baseAmount) + (isWeekend ? 0 : 2)

        outboundData.push(dailyOut)
        cumulativeOut += dailyOut
        cumulativeData.push(cumulativeOut)

        currentDate.add(1, 'day')
    }

    return {
        dates,
        outboundData,
        cumulativeData
    }
}

// 导出默认对象以支持不同的导入方式
export default {
    getStockHistory,
    getOutboundFlow
} 