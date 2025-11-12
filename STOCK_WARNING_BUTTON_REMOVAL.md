# 库存预警按钮移除说明

## 操作概述

根据用户要求，已成功移除系统首页的"库存预警检查"按钮及其相关功能。

## 修改内容

### 文件：`jshERP-web/src/views/dashboard/IndexChart.vue`

#### 1. 移除按钮
**位置**：操作按钮区域
**移除内容**：
```html
<a-button @click="startStockWarningCalculation" type="default" icon="warning">库存预警检查</a-button>
```

#### 2. 移除相关导入
**移除的导入**：
```javascript
import StockWarningProgressModal from '@/components/StockWarningProgressModal'
import { startStockWarningCalculation } from '@/api/stockWarning'
```

#### 3. 移除组件注册
**移除的组件**：
```javascript
StockWarningProgressModal
```

#### 4. 移除模板组件
**移除的模板**：
```html
<!-- 库存预警计算进度弹窗 -->
<StockWarningProgressModal
  :visible="progressModal.visible"
  :taskStatus="progressModal.taskStatus"
  @cancel="handleProgressModalCancel"
/>
```

#### 5. 移除数据定义
**移除的数据**：
```javascript
// 库存预警进度弹窗控制
progressModal: {
  visible: false,
  taskStatus: {}
}
```

#### 6. 移除相关方法
**移除的方法**：
- `startStockWarningCalculation()` - 启动库存预警计算
- `executeStockWarningCalculation()` - 执行库存预警计算
- `startPollingTaskStatus()` - 轮询任务状态
- `handleProgressModalCancel()` - 处理进度弹窗取消

## 影响范围

### 前端界面
- ✅ 首页不再显示"库存预警检查"按钮
- ✅ 移除了库存预警计算的进度弹窗
- ✅ 清理了相关的JavaScript代码和数据

### 后端功能
- ⚠️ 后端的库存预警计算功能仍然保留
- ⚠️ API接口 `/stockWarning/startCalculation` 仍然可用
- ⚠️ 库存预警报表功能不受影响

### 其他功能
- ✅ 导出库存功能正常
- ✅ 刷新数据功能正常
- ✅ 其他首页功能不受影响

## 用户体验变化

### 移除前
用户可以在首页点击"库存预警检查"按钮来启动库存预警计算任务。

### 移除后
- 用户无法从首页直接启动库存预警计算
- 如需查看库存预警，用户需要直接访问"报表管理" → "库存预警报表"
- 库存预警报表的查看功能完全正常

## 替代方案

如果用户仍需要库存预警功能，可以通过以下方式：

### 1. 直接访问报表
- 路径：报表管理 → 库存预警报表
- 功能：查看当前的库存预警情况

### 2. 使用测试工具（开发环境）
- 使用 `test_stock_warning_frontend.html` 测试页面
- 可以手动启动库存预警计算

### 3. API调用（技术人员）
```bash
# 启动库存预警计算
curl -X POST "http://localhost:9999/jshERP-boot/stockWarning/startCalculation"

# 查看库存预警报表
curl "http://localhost:9999/jshERP-boot/depotItem/findStockWarningCount?currentPage=1&pageSize=10"
```

## 恢复方法

如果将来需要恢复库存预警按钮，可以参考以下步骤：

### 1. 恢复按钮
在操作按钮区域添加：
```html
<a-button @click="startStockWarningCalculation" type="default" icon="warning">库存预警检查</a-button>
```

### 2. 恢复导入
```javascript
import StockWarningProgressModal from '@/components/StockWarningProgressModal'
import { startStockWarningCalculation } from '@/api/stockWarning'
```

### 3. 恢复组件和方法
参考之前的实现或查看git历史记录。

## 文件状态

### 已修改的文件
- ✅ `jshERP-web/src/views/dashboard/IndexChart.vue` - 移除库存预警功能

### 保留的文件
- ⚠️ `jshERP-web/src/components/StockWarningProgressModal.vue` - 组件文件保留
- ⚠️ `jshERP-web/src/api/stockWarning.js` - API文件保留
- ⚠️ `jshERP-boot/src/main/java/com/jsh/erp/controller/StockWarningController.java` - 后端控制器保留
- ⚠️ `jshERP-boot/src/main/java/com/jsh/erp/service/StockWarningCalculationService.java` - 后端服务保留

### 测试文件
- ⚠️ `test_stock_warning_frontend.html` - 测试页面保留
- ⚠️ `test_current_stock_initialization.html` - 测试页面保留

## 验证方法

### 1. 前端验证
1. 启动前端项目
2. 访问系统首页
3. 确认操作按钮区域只有"导出库存"和"刷新数据"按钮
4. 确认没有"库存预警检查"按钮

### 2. 功能验证
1. 访问"报表管理" → "库存预警报表"
2. 确认库存预警报表功能正常
3. 确认其他首页功能不受影响

## 总结

库存预警按钮及其相关功能已成功从系统首页移除。这个操作：

- ✅ **简化了用户界面**：减少了首页的按钮数量
- ✅ **保留了核心功能**：库存预警报表功能完全保留
- ✅ **清理了代码**：移除了不需要的前端代码和依赖
- ✅ **保持了灵活性**：后端功能保留，便于将来恢复

用户现在可以通过报表管理模块直接查看库存预警信息，界面更加简洁清晰。
