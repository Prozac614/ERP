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
    // 复用现有的getDailyOutStock接口获取出库数据
    return getAction('/depotItem/getDailyOutStock', {
        materialIds: params.materialId,
        beginTime: params.beginDate,
        endTime: params.endDate
    }).then(response => {
        if (response.code === 200) {
            // 将现有接口数据转换为图表所需格式
            return transformToStockHistoryData(response.data, params)
        }
        return response
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
    // 复用现有的getDailyOutStock接口
    return getAction('/depotItem/getDailyOutStock', {
        materialIds: params.materialId,
        beginTime: params.beginDate,
        endTime: params.endDate
    }).then(response => {
        if (response.code === 200) {
            // 将现有接口数据转换为图表所需格式
            return transformToChartData(response.data, params)
        }
        return response
    })
}

/**
 * 将现有接口数据转换为库存历史图表格式
 */
function transformToStockHistoryData(dailyOutList, params) {
    // 生成日期序列
    const startDate = moment(params.beginDate)
    const endDate = moment(params.endDate)
    const dates = []
    const stockDataArray = []
    const dailyOutData = []

    let currentDate = startDate.clone()
    // 模拟初始库存（实际项目中可以从其他接口获取）
    let currentStock = 100

    while (currentDate.isSameOrBefore(endDate)) {
        const dateStr = currentDate.format('YYYY-MM-DD')
        dates.push(dateStr)

        // 查找该日期的出库数据
        const dayData = dailyOutList.find(item => item.outDate === dateStr)
        const dailyOut = dayData ? parseFloat(dayData.outQuantity) : 0

        // 库存变化：减去出库量，偶尔补货
        currentStock = Math.max(0, currentStock - dailyOut)

        // 随机补货（10%概率）
        if (Math.random() < 0.1) {
            currentStock += Math.floor(Math.random() * 50) + 20
        }

        stockDataArray.push(currentStock)
        dailyOutData.push(dailyOut)

        currentDate.add(1, 'day')
    }

    return {
        code: 200,
        message: 'success',
        data: {
            dates,
            stockData: stockDataArray,
            dailyOutData
        }
    }
}

/**
 * 将现有接口数据转换为出库流水图表格式
 */
function transformToChartData(dailyOutList, params) {
    // 生成日期序列
    const startDate = moment(params.beginDate)
    const endDate = moment(params.endDate)
    const dates = []
    const outboundData = []
    const cumulativeData = []

    let currentDate = startDate.clone()
    let cumulativeOut = 0

    while (currentDate.isSameOrBefore(endDate)) {
        const dateStr = currentDate.format('YYYY-MM-DD')
        dates.push(dateStr)

        // 查找该日期的出库数据
        const dayData = dailyOutList.find(item => item.outDate === dateStr)
        const dailyOut = dayData ? parseFloat(dayData.outQuantity) : 0

        outboundData.push(dailyOut)
        cumulativeOut += dailyOut
        cumulativeData.push(cumulativeOut)

        currentDate.add(1, 'day')
    }

    return {
        code: 200,
        message: 'success',
        data: {
            dates,
            outboundData,
            cumulativeData
        }
    }
}



// 导出默认对象以支持不同的导入方式
export default {
    getStockHistory,
    getOutboundFlow
} 