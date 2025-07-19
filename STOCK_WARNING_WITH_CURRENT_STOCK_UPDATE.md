# 库存预警计算功能增强 - 同时更新当前库存表

## 功能改进概述

为了解决库存预警报表无数据的问题，我们对库存预警计算功能进行了增强，现在点击"库存预警检查"按钮时会同时执行以下操作：

1. **更新当前库存表** - 为所有商品和仓库组合更新 `jsh_material_current_stock` 表
2. **计算安全库存** - 基于过去6个月的销售数据计算最低安全库存阈值

## 技术实现

### 修改的文件

#### 1. StockWarningCalculationService.java
- 添加了 `DepotItemService` 依赖
- 修改了 `executeCalculation()` 方法，在计算安全库存前先更新当前库存

```java
// 新增依赖
@Resource
private DepotItemService depotItemService;

// 修改后的执行逻辑
for (Material material : materials) {
    try {
        // 1. 先更新该商品在所有仓库的当前库存
        for (Depot depot : depots) {
            try {
                depotItemService.updateCurrentStockFun(material.getId(), depot.getId());
            } catch (Exception e) {
                logger.warn("更新商品{}在仓库{}的当前库存失败: {}", 
                        material.getId(), depot.getId(), e.getMessage());
            }
        }
        
        // 2. 计算该商品的最低安全库存阈值
        calculateMaterialSafeStock(material, depots);
        task.incrementSuccess();
    } catch (Exception e) {
        logger.error("处理商品{}失败", material.getId(), e);
        task.incrementFailed();
    }
}
```

#### 2. StockWarningController.java
- 更新了返回消息，告知用户会同时更新当前库存表
- 添加了操作说明

```java
Map<String, Object> result = new HashMap<>();
result.put("taskId", taskId);
result.put("message", "库存预警计算任务已启动，同时会更新当前库存表");
result.put("note", "此操作会同时更新当前库存数据和计算安全库存阈值");
```

### 处理流程

#### 原有流程
```
点击库存预警检查 → 计算安全库存 → 更新安全库存阈值
```

#### 增强后流程
```
点击库存预警检查 → 更新当前库存表 → 计算安全库存 → 更新安全库存阈值
```

### 具体执行步骤

1. **获取所有商品和仓库**
   - 查询所有有效商品 (`jsh_material`)
   - 查询所有有效仓库 (`jsh_depot`)

2. **更新当前库存表**
   - 对每个商品×仓库组合调用 `updateCurrentStockFun()`
   - 计算实际库存：初始库存 + 入库 - 出库 + 盘点调整
   - 更新或插入 `jsh_material_current_stock` 记录

3. **计算安全库存**
   - 基于过去6个月销售数据计算平均日销量
   - 设置最低安全库存 = 平均日销量 × 180天
   - 更新 `jsh_material_initial_stock` 表

## 用户体验改进

### 前端界面更新

1. **按钮文本更新**
   - 原：`开始计算`
   - 新：`开始计算（含库存更新）`

2. **提示信息增强**
   - 添加了功能说明：会同时更新当前库存表
   - 在测试页面添加了重要提示

3. **进度监控优化**
   - 日志记录频率从每100个商品调整为每50个商品
   - 增加了当前库存更新的日志信息

### 测试页面更新

#### test_stock_warning_frontend.html
- 添加了新功能说明
- 更新了按钮样式和文本

#### test_current_stock_initialization.html
- 添加了重要提示框
- 说明用户可以直接使用库存预警检查功能

## 解决的问题

### 1. 库存预警报表无数据
**原因**：`jsh_material_current_stock` 表缺少记录
**解决**：自动更新当前库存表，确保数据完整性

### 2. 数据不一致
**原因**：当前库存数据可能过时或不准确
**解决**：每次计算前重新计算所有商品的当前库存

### 3. 操作复杂性
**原因**：用户需要分别执行库存初始化和安全库存计算
**解决**：一键完成所有必要操作

## 性能考虑

### 执行时间
- **增加的操作**：当前库存更新
- **预估影响**：执行时间可能增加30-50%
- **优化措施**：异步执行，不阻塞用户界面

### 资源消耗
- **数据库操作**：增加了大量的UPDATE/INSERT操作
- **内存使用**：基本无变化
- **CPU使用**：略有增加

### 监控和日志
- 详细的进度日志
- 错误处理和警告信息
- 任务状态实时更新

## 使用方法

### 简单使用（推荐）
1. 登录系统首页
2. 点击"库存预警检查"按钮
3. 等待计算完成
4. 查看库存预警报表

### 高级诊断
1. 使用 `test_current_stock_initialization.html` 检查当前库存状态
2. 使用 `test_stock_warning_frontend.html` 监控计算进度
3. 查看详细的执行日志

## 验证方法

### 1. 功能验证
```bash
# 启动计算
curl -X POST "http://localhost:9999/jshERP-boot/stockWarning/startCalculation"

# 检查当前库存表
SELECT COUNT(*) FROM jsh_material_current_stock WHERE delete_flag != '1';

# 检查库存预警报表
curl "http://localhost:9999/jshERP-boot/depotItem/findStockWarningCount?currentPage=1&pageSize=10"
```

### 2. 数据完整性验证
```sql
-- 检查当前库存记录完整性
SELECT 
    (SELECT COUNT(*) FROM jsh_material WHERE delete_flag != '1') * 
    (SELECT COUNT(*) FROM jsh_depot WHERE delete_flag != '1') as expected_records,
    (SELECT COUNT(*) FROM jsh_material_current_stock WHERE delete_flag != '1') as actual_records;
```

## 注意事项

### 1. 执行时间
- 大量商品和仓库的系统可能需要较长时间
- 建议在业务低峰期执行

### 2. 数据备份
- 建议在首次使用前备份相关表
- 特别是 `jsh_material_current_stock` 和 `jsh_material_initial_stock`

### 3. 权限要求
- 需要对相关表的读写权限
- 需要执行存储过程的权限

## 总结

这个增强功能通过在库存预警计算过程中同时更新当前库存表，彻底解决了库存预警报表无数据的问题。用户现在只需要点击一个按钮就能完成所有必要的数据更新和计算操作，大大简化了使用流程，提高了系统的可用性和数据的准确性。

### 主要优势
- ✅ **一键解决**：无需分步操作
- ✅ **数据准确**：确保当前库存数据最新
- ✅ **用户友好**：简化操作流程
- ✅ **问题预防**：避免数据不一致导致的问题

### 适用场景
- 新系统初始化
- 数据迁移后的修复
- 定期数据维护
- 库存预警功能异常时的修复
