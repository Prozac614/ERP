import Vue from 'vue'

/**
 * 将一个请求分组 - 实时数据版本
 * 所有请求直接访问后端，无缓存机制
 *
 * @param getPromise 传入一个可以获取到Promise对象的方法
 * @param groupId 分组ID（已禁用缓存功能）
 * @param expire 过期时间（已禁用缓存功能）
 */
export function httpGroupRequest(getPromise, groupId, expire = 1000 * 30) {
  // 直接执行请求，不使用任何缓存机制
  console.log("--------直接请求实时数据--------groupId = " + groupId)
  return getPromise()
}


