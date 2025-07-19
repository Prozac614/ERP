# 库存告急功能问题修复总结

## 修复的问题

### 1. 操作栏按钮换行问题 ✅
**问题描述**: 操作栏中的"忽略风险"和"关注风险"按钮与"查看图表"按钮在同一行显示

**解决方案**: 
- 将按钮分别放在不同的div中
- 为风险操作按钮添加上边距(margin-top: 4px)
- 实现按钮垂直排列

**修改文件**: `jshERP-web/src/views/dashboard/IndexChart.vue`

### 2. 库存状态标签渲染问题 ✅
**问题描述**: 第一页的库存状态标签没有正确渲染，显示为空或默认状态

**根本原因**: 
- 后端计算逻辑有问题，只在状态为空时才计算
- 数据库中的状态字段可能为NULL

**解决方案**:
- 修改`calculateAndUpdateStockAlertStatus`方法逻辑
- 优先使用数据库中已有的状态
- 只在状态为空时才重新计算
- 创建测试数据初始化脚本

**修改文件**: 
- `jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemOptimizedService.java`
- `jshERP-boot/docs/init_stock_alert_test_data.sql`

### 3. 点击忽略告急操作错误 ✅
**问题描述**: 点击"忽略风险"按钮时报操作错误

**根本原因**:
- 后端API使用`@RequestParam`接收参数，但前端发送的是JSON数据
- 参数接收方式不匹配

**解决方案**:
- 将后端API参数接收方式改为`@RequestBody JSONObject`
- 从JSON对象中提取materialId参数
- 添加参数验证逻辑

**修改文件**: `jshERP-boot/src/main/java/com/jsh/erp/controller/DepotItemController.java`

## 技术细节

### 前端修改
```vue
<!-- 操作栏按钮换行 -->
<span slot="action" slot-scope="text, record">
  <div>
    <a @click="viewChart(record)">查看图表</a>
  </div>
  <div v-if="record.stockAlertStatus === 'STOCK_ALERT'" style="margin-top: 4px;">
    <a @click="ignoreStockRisk(record)" style="color: #fa8c16;">忽略风险</a>
  </div>
  <div v-if="record.stockAlertStatus === 'RISK_IGNORED'" style="margin-top: 4px;">
    <a @click="focusStockRisk(record)" style="color: #1890ff;">关注风险</a>
  </div>
</span>
```

### 后端API修改
```java
// 修改前
@PostMapping(value = "/ignoreStockRisk")
public BaseResponseInfo ignoreStockRisk(@RequestParam("materialId") Long materialId, HttpServletRequest request)

// 修改后
@PostMapping(value = "/ignoreStockRisk")
public BaseResponseInfo ignoreStockRisk(@RequestBody JSONObject obj, HttpServletRequest request) {
    Long materialId = obj.getLong("materialId");
    // ... 处理逻辑
}
```

### 状态计算逻辑优化
```java
// 优先使用数据库中已有的状态
String currentStatus = stock.getStockAlertStatus();
if (currentStatus == null || currentStatus.trim().isEmpty()) {
    // 只在状态为空时才重新计算
    // ... 计算逻辑
}
```

## 测试验证

### 1. 数据库初始化
```sql
-- 执行测试数据初始化
mysql -u username -p database_name < docs/init_stock_alert_test_data.sql
```

### 2. 功能测试步骤
1. 重启后端服务
2. 刷新前端页面
3. 检查库存状态列是否正确显示标签
4. 测试"忽略风险"按钮功能
5. 测试"关注风险"按钮功能
6. 验证按钮是否正确换行显示

### 3. 预期结果
- ✅ 库存状态列显示彩色标签（绿色/红色/橙色）
- ✅ 操作按钮垂直排列，不在同一行
- ✅ 点击"忽略风险"成功，状态变为"忽略风险"
- ✅ 点击"关注风险"成功，重新计算状态

## 后续优化建议

### 1. 性能优化
- 考虑使用缓存减少数据库查询
- 批量更新状态而不是逐个更新

### 2. 用户体验
- 添加加载状态指示器
- 优化错误提示信息
- 考虑添加状态变更历史记录

### 3. 数据完整性
- 定期批量更新所有商品的库存状态
- 添加数据一致性检查

## 文件清单

### 修改的文件
1. `jshERP-web/src/views/dashboard/IndexChart.vue` - 前端界面修改
2. `jshERP-boot/src/main/java/com/jsh/erp/controller/DepotItemController.java` - API接口修改
3. `jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemOptimizedService.java` - 业务逻辑修改

### 新增的文件
1. `jshERP-boot/docs/init_stock_alert_test_data.sql` - 测试数据初始化脚本
2. `jshERP-boot/docs/stock_alert_fixes_summary.md` - 本修复总结文档

## 版本信息
- 修复版本: 1.0.1
- 修复日期: 2025-07-19
- 修复内容: 操作按钮布局、状态渲染、API错误修复
