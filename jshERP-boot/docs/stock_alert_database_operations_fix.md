# 库存告急数据库操作修复方案

## 问题描述
用户反馈忽略风险和关注风险的按钮操作后，数据库中的状态没有正确更新。

## 问题分析

### 1. 原始问题
- 使用通用的`updateByPrimaryKeySelective`方法更新数据
- 对于NULL值的处理不够精确
- 缺乏专门的业务逻辑方法
- 没有充分的日志记录和错误处理

### 2. 根本原因
- MyBatis的`updateByPrimaryKeySelective`方法在处理NULL值时有限制
- `stock_alert_ignored_at`字段需要能够被设置为NULL，但原有逻辑无法正确处理
- 缺乏针对库存告急状态的专门更新方法

## 解决方案

### 1. 创建专门的业务方法

#### MaterialService中新增方法：
```java
// 忽略库存风险
public void ignoreStockRisk(Long materialId)

// 重新关注库存风险  
public void focusStockRisk(Long materialId)
```

#### 特点：
- 专门处理库存告急状态的业务逻辑
- 包含完整的错误处理和日志记录
- 抛出明确的异常信息

### 2. 创建专门的SQL更新方法

#### MaterialMapperEx中新增方法：
```java
int updateStockAlertStatusAndClearIgnored(
    @Param("materialId") Long materialId,
    @Param("alertStatus") String alertStatus,
    @Param("sixMonthsSales") BigDecimal sixMonthsSales
);
```

#### 对应的SQL实现：
```sql
UPDATE jsh_material 
SET stock_alert_status = #{alertStatus},
    last_six_months_sales = #{sixMonthsSales},
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE id = #{materialId}
AND IFNULL(delete_flag, '0') != '1'
```

### 3. 优化Controller层调用

#### 修改前：
```java
// 复杂的多步骤操作
materialService.updateStockAlertStatus(materialId, "RISK_IGNORED", null);
Material material = new Material();
material.setId(materialId);
material.setStockAlertIgnoredAt(new Date());
materialService.updateMaterialByEntity(material);
```

#### 修改后：
```java
// 简单的单一方法调用
materialService.ignoreStockRisk(materialId);
```

## 技术细节

### 1. 忽略风险操作
- 设置`stock_alert_status = 'RISK_IGNORED'`
- 设置`stock_alert_ignored_at = NOW()`
- 更新`stock_alert_updated_at = NOW()`

### 2. 关注风险操作
- 重新计算库存告急状态（NO_RISK 或 STOCK_ALERT）
- 设置`stock_alert_ignored_at = NULL`
- 更新六个月销量数据
- 更新`stock_alert_updated_at = NOW()`

### 3. 状态计算逻辑
```java
String alertStatus;
if (currentStock.compareTo(sixMonthsSales) >= 0) {
    alertStatus = "NO_RISK";  // 库存充足
} else {
    alertStatus = "STOCK_ALERT";  // 库存不足
}
```

## 修改的文件

### 后端文件
1. `MaterialService.java` - 添加专门的业务方法
2. `MaterialMapperEx.java` - 添加新的接口方法
3. `MaterialMapperEx.xml` - 添加SQL实现
4. `DepotItemController.java` - 简化API调用逻辑

### 测试文件
1. `test_stock_alert_operations.sql` - 数据库操作测试脚本
2. `quick_fix_stock_alert_status.sql` - 快速修复状态脚本

## 验证步骤

### 1. 数据库验证
```sql
-- 执行测试脚本
mysql -u username -p database_name < docs/test_stock_alert_operations.sql
```

### 2. 功能验证
1. 重启后端服务
2. 在前端点击"忽略风险"按钮
3. 检查数据库中`stock_alert_status`是否变为`RISK_IGNORED`
4. 检查`stock_alert_ignored_at`是否有时间戳
5. 点击"关注风险"按钮
6. 检查`stock_alert_ignored_at`是否变为NULL
7. 检查状态是否重新计算

### 3. 日志验证
查看应用日志中的相关信息：
```bash
grep "库存风险" application.log
grep "ignoreStockRisk\|focusStockRisk" application.log
```

## 预期结果

### 忽略风险操作后：
- `stock_alert_status` = 'RISK_IGNORED'
- `stock_alert_ignored_at` = 当前时间戳
- `stock_alert_updated_at` = 当前时间戳

### 关注风险操作后：
- `stock_alert_status` = 'NO_RISK' 或 'STOCK_ALERT'（根据实际库存计算）
- `stock_alert_ignored_at` = NULL
- `last_six_months_sales` = 计算得出的六个月销量
- `stock_alert_updated_at` = 当前时间戳

## 错误处理

### 1. 异常抛出
- 所有方法都会抛出明确的RuntimeException
- 包含详细的错误信息

### 2. 日志记录
- 成功操作记录INFO级别日志
- 失败操作记录ERROR级别日志
- 包含商品ID和操作详情

### 3. 前端反馈
- 成功操作显示成功消息
- 失败操作显示具体错误信息

## 性能考虑

### 1. 单次更新
- 使用单个SQL语句完成所有字段更新
- 避免多次数据库交互

### 2. 事务处理
- 所有更新操作都在事务中执行
- 确保数据一致性

### 3. 索引优化
- 利用已有的主键索引进行更新
- 更新操作性能良好

## 版本信息
- 修复版本: 1.0.2
- 修复日期: 2025-07-19
- 修复内容: 库存告急数据库操作优化
