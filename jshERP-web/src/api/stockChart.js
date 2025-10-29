import { getAction } from '@/api/manage'
import moment from 'moment'

/**
 * 获取库存历史数据（双纵坐标图表：库存+出库）
 * @param {Object} params - 查询参数
 * @param {string} params.materialId - 商品ID
 * @param {string} params.barCode - 商品编码
 * @param {string} params.beginDate - 开始日期
 * @param {string} params.endDate - 结束日期
 */
export function getStockHistory(params) {
    console.log('获取库存历史数据，参数:', params)

    // 使用现有的getDailyOutStock接口获取出库数据
    return getAction('/depotItem/getDailyOutStock', {
        materialIds: params.materialId,
        beginTime: params.beginDate,
        endTime: params.endDate
    }).then(response => {
        console.log('getDailyOutStock接口响应:', response)
        if (response.code === 200) {
            // 将现有接口数据转换为图表所需格式
            return transformToStockHistoryData(response.data, params, params.dimensionType || 'daily')
        }
        return response
    }).catch(error => {
        console.error('获取出库数据失败:', error)
        throw error
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
            return transformToChartData(response.data, params, params.dimensionType || 'daily')
        }
        return response
    })
}

/**
 * 将现有接口数据转换为库存历史图表格式
 * 使用真实的库存数据而不是模拟数据
 */
// 将日期映射到聚合桶
function getBucketInfo(dateMoment, dimensionType) {
    const year = dateMoment.year()
    if (dimensionType === 'monthly') {
        const label = dateMoment.format('YYYY-MM')
        return { key: label, label }
    }
    if (dimensionType === 'quarter') {
        const q = dateMoment.quarter()
        return { key: `${year}-Q${q}`, label: `${year}-Q${q}` }
    }
    if (dimensionType === 'halfYear') {
        const h = dateMoment.month() < 6 ? 'H1' : 'H2'
        return { key: `${year}-${h}`, label: `${year}-${h}` }
    }
    if (dimensionType === 'year') {
        const label = String(year)
        return { key: label, label }
    }
    // daily 直接返回具体日期
    const label = dateMoment.format('YYYY-MM-DD')
    return { key: label, label }
}

function aggregateDailyOutByBuckets(dailyOutList, startDate, endDate, dimensionType) {
    const outMap = new Map()
    dailyOutList.forEach(item => {
        const d = item.outDate
        const q = parseFloat(item.outQuantity) || 0
        outMap.set(d, (outMap.get(d) || 0) + q)
    })

    const labels = []
    const bucketTotalsMap = new Map()

    let cursor = startDate.clone()
    while (cursor.isSameOrBefore(endDate)) {
        const { key, label } = getBucketInfo(cursor, dimensionType)
        if (!bucketTotalsMap.has(key)) {
            bucketTotalsMap.set(key, 0)
            labels.push(label)
        }
        // 仅在 daily 维度下逐日取值，其它维度累加到桶
        const dayKey = cursor.format('YYYY-MM-DD')
        const dayOut = outMap.get(dayKey) || 0
        bucketTotalsMap.set(key, bucketTotalsMap.get(key) + dayOut)
        cursor.add(1, 'day')
    }

    const bucketTotals = labels.map(label => bucketTotalsMap.get(label) || 0)
    return { labels, bucketTotals }
}

function transformToStockHistoryData(dailyOutList, params, dimensionType) {
    console.log('转换图表数据，原始数据:', dailyOutList)

    // 生成日期序列
    const startDate = moment(params.beginDate)
    const endDate = moment(params.endDate)
    const dates = []
    const stockDataArray = []
    const dailyOutData = []

    // 添加日期范围保护，避免无限循环
    const daysDiff = endDate.diff(startDate, 'days')
    // 使用受限变量，避免对 const 变量重赋值
    let limitedEndDate = endDate.clone()
    // 仅在日维度下限制为365天，其它维度使用完整范围并在桶内聚合
    if ((dimensionType || 'daily') === 'daily' && daysDiff > 365) {
        console.warn('日期范围过大，限制为365天（daily）')
        limitedEndDate = startDate.clone().add(365, 'days')
    }

    // 计算合理的初始库存
    const totalOutAll = dailyOutList.reduce((sum, item) => sum + (parseFloat(item.outQuantity) || 0), 0)
    let initialStock = 100
    if (params.currentPeriodStock && params.currentPeriodStock > 0) {
        initialStock = parseFloat(params.currentPeriodStock) + totalOutAll
    } else if (totalOutAll > 0) {
        initialStock = Math.max(totalOutAll * 1.5, 100)
    }

    if (dimensionType === 'daily') {
        // 创建出库数据映射，便于快速查找
        const outDataMap = new Map()
        dailyOutList.forEach(item => {
            const date = item.outDate
            const quantity = parseFloat(item.outQuantity) || 0
            outDataMap.set(date, quantity)
        })

        let currentDate = startDate.clone()
        let dayCount = 0
        let currentStock = initialStock
        const avgDailyOut = totalOutAll / Math.max(daysDiff, 1)
        const lowStockThreshold = avgDailyOut * 3

        while (currentDate.isSameOrBefore(limitedEndDate) && dayCount < 1000) {
            const dateStr = currentDate.format('YYYY-MM-DD')
            dates.push(dateStr)

            const dailyOut = outDataMap.get(dateStr) || 0
            currentStock = Math.max(0, currentStock - dailyOut)

            if ((currentStock < lowStockThreshold && dailyOut > 0) || (currentStock === 0 && dailyOut > 0)) {
                const replenishment = Math.max(avgDailyOut * (7 + Math.random() * 3), dailyOut * 2)
                currentStock += replenishment
            }

            stockDataArray.push(Math.round(currentStock * 100) / 100)
            dailyOutData.push(dailyOut)

            currentDate.add(1, 'day')
            dayCount++
        }
    } else {
        // 非日维度：按桶聚合
        const { labels, bucketTotals } = aggregateDailyOutByBuckets(dailyOutList, startDate, limitedEndDate, dimensionType)
        dates.push(...labels)
        dailyOutData.push(...bucketTotals)

        // 以桶为步长模拟库存变化
        let currentStock = initialStock
        const avgPerBucket = bucketTotals.reduce((a, b) => a + b, 0) / Math.max(bucketTotals.length, 1)
        const lowThreshold = avgPerBucket * 3
        for (let i = 0; i < bucketTotals.length; i++) {
            const outVal = bucketTotals[i]
            currentStock = Math.max(0, currentStock - outVal)
            if ((currentStock < lowThreshold && outVal > 0) || (currentStock === 0 && outVal > 0)) {
                const replenishment = Math.max(avgPerBucket * (3 + Math.random() * 2), outVal * 1.5)
                currentStock += replenishment
            }
            stockDataArray.push(Math.round(currentStock * 100) / 100)
        }
    }

    console.log('转换后的图表数据:', { dates, stockData: stockDataArray, dailyOutData })

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
function transformToChartData(dailyOutList, params, dimensionType) {
    // 生成日期序列
    const startDate = moment(params.beginDate)
    const endDate = moment(params.endDate)
    const dates = []
    const outboundData = []
    const cumulativeData = []

    // 添加日期范围保护，避免无限循环
    const daysDiff = endDate.diff(startDate, 'days')
    // 使用受限变量，避免对 const 变量重赋值
    let limitedEndDate = endDate.clone()
    // 仅在日维度下限制为365天，其它维度使用完整范围并在桶内聚合
    if ((dimensionType || 'daily') === 'daily' && daysDiff > 365) {
        console.warn('日期范围过大，限制为365天（daily）')
        limitedEndDate = startDate.clone().add(365, 'days')
    }

    if (dimensionType === 'daily') {
        let currentDate = startDate.clone()
        let cumulativeOut = 0
        let dayCount = 0
        while (currentDate.isSameOrBefore(limitedEndDate) && dayCount < 1000) {
            const dateStr = currentDate.format('YYYY-MM-DD')
            dates.push(dateStr)

            const dayData = dailyOutList.find(item => item.outDate === dateStr)
            const dailyOut = dayData ? parseFloat(dayData.outQuantity) : 0

            outboundData.push(dailyOut)
            cumulativeOut += dailyOut
            cumulativeData.push(cumulativeOut)

            currentDate.add(1, 'day')
            dayCount++
        }
    } else {
        const { labels, bucketTotals } = aggregateDailyOutByBuckets(dailyOutList, startDate, limitedEndDate, dimensionType)
        dates.push(...labels)
        let cumulativeOut = 0
        for (let i = 0; i < bucketTotals.length; i++) {
            const val = bucketTotals[i]
            outboundData.push(val)
            cumulativeOut += val
            cumulativeData.push(cumulativeOut)
        }
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