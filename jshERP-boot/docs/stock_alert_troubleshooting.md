# 库存告急功能问题排查指南

## 问题现象
点击"忽略风险"按钮后，数据库中的状态没有更新。

## 排查步骤

### 1. 检查后端日志
查看应用日志中的相关信息：
```bash
# 查看忽略风险相关日志
grep -i "ignoreStockRisk\|忽略库存风险" application.log

# 查看数据库更新相关日志
grep -i "updateStockAlertToIgnored" application.log

# 查看错误日志
grep -i "error.*stock.*alert" application.log
```

### 2. 检查数据库状态
执行检查脚本：
```sql
-- 操作前检查
mysql -u username -p database_name < docs/check_stock_alert_status.sql

-- 执行操作（点击忽略风险按钮）

-- 操作后检查
mysql -u username -p database_name < docs/check_stock_alert_status.sql
```

### 3. 手动测试数据库更新
```sql
-- 执行调试脚本
mysql -u username -p database_name < docs/debug_stock_alert_update.sql
```

### 4. 检查API调用
使用浏览器开发者工具检查：
- 网络请求是否成功发送
- 请求参数是否正确
- 响应状态码和内容

### 5. 检查数据库连接和权限
```sql
-- 检查当前用户权限
SHOW GRANTS FOR CURRENT_USER();

-- 检查表结构
DESCRIBE jsh_material;

-- 检查字段是否存在
SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'jsh_material' 
AND COLUMN_NAME LIKE '%stock_alert%';
```

## 可能的原因和解决方案

### 1. 数据库字段不存在
**检查方法**：
```sql
DESCRIBE jsh_material;
```

**解决方案**：
```sql
-- 执行数据库更新脚本
mysql -u username -p database_name < docs/stock_alert_feature.sql
```

### 2. 事务回滚
**检查方法**：查看日志中是否有事务回滚的信息

**解决方案**：检查是否有其他约束或触发器导致更新失败

### 3. 租户ID不匹配
**检查方法**：
```sql
-- 检查商品的租户ID
SELECT id, name, tenant_id FROM jsh_material WHERE id = 商品ID;

-- 检查当前用户的租户ID
-- 需要在应用日志中查看
```

**解决方案**：确保操作的商品属于当前用户的租户

### 4. 商品已被删除
**检查方法**：
```sql
SELECT id, name, delete_flag FROM jsh_material WHERE id = 商品ID;
```

**解决方案**：确保商品的delete_flag不是'1'

### 5. 权限问题
**检查方法**：
```sql
SHOW GRANTS FOR CURRENT_USER();
```

**解决方案**：确保数据库用户有UPDATE权限

### 6. 缓存问题
**解决方案**：
- 重启应用服务
- 清除浏览器缓存
- 刷新页面

## 调试技巧

### 1. 启用详细日志
在application.properties中添加：
```properties
logging.level.com.jsh.erp.service.MaterialService=DEBUG
logging.level.com.jsh.erp.controller.DepotItemController=DEBUG
```

### 2. 使用SQL监控
启用MyBatis的SQL日志：
```properties
logging.level.com.jsh.erp.datasource.mappers=DEBUG
```

### 3. 手动测试SQL
直接在数据库中执行更新SQL：
```sql
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = 商品ID
AND IFNULL(delete_flag, '0') != '1';

SELECT ROW_COUNT(); -- 查看影响的行数
```

## 验证修复

### 1. 功能验证
1. 点击"忽略风险"按钮
2. 检查页面是否显示成功消息
3. 刷新页面，检查状态是否变为"忽略风险"
4. 检查数据库中的记录

### 2. 数据库验证
```sql
-- 检查特定商品的状态
SELECT 
    id, name, stock_alert_status, 
    stock_alert_ignored_at, stock_alert_updated_at
FROM jsh_material 
WHERE id = 商品ID;
```

### 3. 日志验证
检查日志中是否有成功的更新记录：
```
商品XXX已忽略库存风险
忽略商品XXX库存风险，更新结果：1
```

## 联系支持
如果以上步骤都无法解决问题，请提供：
1. 完整的错误日志
2. 数据库检查结果
3. 网络请求的详细信息
4. 数据库版本和应用版本信息
