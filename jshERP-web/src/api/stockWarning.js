import { getAction, postAction } from '@/api/manage'

/**
 * 库存预警相关API
 */

// 开始库存预警计算
export const startStockWarningCalculation = () => {
  return postAction('/stockWarning/startCalculation', {})
}

// 获取计算任务状态
export const getTaskStatus = (taskId) => {
  return getAction('/stockWarning/getTaskStatus', { taskId })
}

// 清理已完成的任务
export const cleanupTasks = () => {
  return postAction('/stockWarning/cleanupTasks', {})
}
