# 库存告急功能使用指南

## 功能概述

库存告急功能是一个智能库存预警系统，能够自动监控商品库存状态，并根据历史销售数据预测库存风险。

## 功能特性

### 1. 库存告急状态
系统支持三种库存状态：
- **无风险** (NO_RISK)：当前库存充足，大于等于过去6个月的总销量
- **库存告急** (STOCK_ALERT)：当前库存不足，小于过去6个月的总销量
- **忽略风险** (RISK_IGNORED)：用户手动忽略的库存风险状态

### 2. 智能计算逻辑
- 系统自动计算过去6个月的商品销量
- 比较当前库存与历史销量，判断库存风险
- 支持手动忽略和重新关注风险

### 3. 用户操作
- **忽略风险**：对于库存告急的商品，用户可以选择忽略风险
- **关注风险**：对于已忽略风险的商品，用户可以重新关注风险

## 安装部署

### 1. 数据库更新
执行以下SQL脚本来添加必要的数据库字段和存储过程：

```bash
# 执行数据库更新脚本
mysql -u username -p database_name < docs/stock_alert_feature.sql
```

### 2. 后端部署
确保以下文件已更新：
- `Material.java` - 实体类添加新字段
- `MaterialStockPeriodVo.java` - VO类添加新字段
- `MaterialMapper.xml` - Mapper文件添加新字段映射
- `DepotItemMapperEx.xml` - 添加六个月销量查询
- `DepotItemOptimizedService.java` - 添加库存告急计算逻辑
- `MaterialService.java` - 添加状态更新方法
- `DepotItemController.java` - 添加风险操作API

### 3. 前端部署
确保以下文件已更新：
- `IndexChart.vue` - 添加库存状态列和操作按钮

## 使用方法

### 1. 查看库存状态
在首页库存统计表格中，新增了"库存状态"列，显示每个商品的库存告急状态：
- 绿色标签：无风险
- 红色标签：库存告急
- 橙色标签：忽略风险

### 2. 操作库存风险
在操作列中，根据商品的库存状态会显示相应的操作按钮：
- 库存告急状态：显示"忽略风险"按钮
- 忽略风险状态：显示"关注风险"按钮

### 3. 批量更新状态
可以使用存储过程批量更新所有商品的库存告急状态：

```sql
-- 更新指定租户的所有商品状态
CALL BatchUpdateStockAlertStatus(63);

-- 更新单个商品状态
CALL CalculateStockAlertStatus(商品ID, 租户ID);
```

## API接口

### 1. 忽略库存风险
```
POST /depotItem/ignoreStockRisk
参数: materialId (商品ID)
```

### 2. 关注库存风险
```
POST /depotItem/focusStockRisk
参数: materialId (商品ID)
```

### 3. 获取库存数据（已扩展）
```
GET /depotItem/getMaterialStockWithDailyOutOptimized
返回数据中包含库存告急状态信息
```

## 数据库字段说明

### jsh_material表新增字段：
- `stock_alert_status`: 库存告急状态 (varchar(20))
- `stock_alert_ignored_at`: 忽略风险时间 (datetime)
- `last_six_months_sales`: 过去六个月销量缓存 (decimal(24,6))
- `stock_alert_updated_at`: 状态最后更新时间 (datetime)

## 性能优化

### 1. 索引优化
系统自动创建了以下索引：
- `idx_stock_alert_status`: 库存告急状态索引
- `idx_stock_alert_updated`: 状态更新时间索引

### 2. 缓存机制
- 六个月销量数据会缓存在 `last_six_months_sales` 字段中
- 避免重复计算，提高查询性能

### 3. 异步更新
- 库存状态计算采用异步方式，不影响主查询性能
- 支持批量更新，提高处理效率

## 测试验证

### 1. 功能测试
执行测试脚本验证功能：
```bash
mysql -u username -p database_name < docs/test_stock_alert_feature.sql
```

### 2. 性能测试
- 监控查询响应时间
- 检查索引使用情况
- 验证缓存效果

## 故障排除

### 1. 常见问题
- **状态不更新**：检查存储过程是否正确执行
- **性能问题**：检查索引是否创建成功
- **数据不准确**：验证销量计算逻辑

### 2. 日志查看
查看应用日志中的相关信息：
```
grep "库存告急" application.log
grep "StockAlert" application.log
```

## 版本信息
- 版本：1.0.0
- 创建日期：2025-07-19
- 兼容性：jshERP 3.x+

## 联系支持
如有问题，请联系技术支持团队。
