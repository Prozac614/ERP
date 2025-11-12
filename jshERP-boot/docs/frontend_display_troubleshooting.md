# 前端显示问题完整排查指南

## 问题现象
数据库中商品状态为 `RISK_IGNORED`（忽略风险），但前端仍显示为"库存告急"。

## 排查步骤

### 第一步：验证数据库状态
```sql
-- 执行此查询确认数据库中的实际状态
SELECT 
    id, name, stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = 63  -- 请根据实际租户ID修改
AND stock_alert_status = 'RISK_IGNORED'
LIMIT 5;
```

### 第二步：重启后端服务
**重要：必须重启后端服务以应用代码修改**
```bash
# 停止服务
# 启动服务
```

### 第三步：检查后端日志
重启后端服务后，刷新前端页面，然后查看日志：
```bash
# 查看库存状态相关日志
tail -f application.log | grep -E "(库存状态调试|后端状态统计|发现忽略风险商品)"
```

你应该看到类似这样的日志：
```
后端库存状态调试信息
商品: ID=123, 名称=测试商品, 状态=RISK_IGNORED, 忽略时间=2025-07-19 12:00:00
后端状态统计: {RISK_IGNORED=2, NO_RISK=5, STOCK_ALERT=3}
```

### 第四步：检查前端控制台
1. 打开浏览器开发者工具（F12）
2. 切换到 **Console** 标签
3. 刷新页面
4. 查看控制台输出

你应该看到类似这样的输出：
```
=== 库存状态调试信息 ===
商品1: ID=123, 名称=测试商品, 状态=RISK_IGNORED
状态统计: {RISK_IGNORED: 2, NO_RISK: 5, STOCK_ALERT: 3}
```

### 第五步：检查网络请求
1. 在开发者工具中切换到 **Network** 标签
2. 刷新页面
3. 找到 `getMaterialStockWithDailyOutOptimized` 请求
4. 点击查看 **Response** 数据
5. 检查响应中的 `stockAlertStatus` 字段值

### 第六步：检查前端渲染
如果前端控制台显示状态正确，但页面显示不对，可能是渲染问题：

1. 在浏览器中右键点击显示错误的状态标签
2. 选择"检查元素"
3. 查看HTML结构和CSS样式

## 可能的问题和解决方案

### 问题1：后端日志显示状态为NULL或错误
**原因**：数据库查询或映射问题
**解决方案**：
```sql
-- 执行调试脚本
mysql -u username -p database_name < docs/debug_frontend_display_issue.sql
```

### 问题2：后端日志正确，但前端控制台显示错误
**原因**：网络传输或JSON解析问题
**解决方案**：
1. 检查网络请求的Response数据
2. 检查是否有JSON解析错误
3. 清除浏览器缓存

### 问题3：前端控制台正确，但页面显示错误
**原因**：前端渲染逻辑问题
**解决方案**：
1. 检查Vue模板中的条件判断
2. 检查是否有CSS样式覆盖
3. 强制刷新页面（Ctrl+Shift+R）

### 问题4：缓存问题
**解决方案**：
1. 清除浏览器缓存
2. 使用无痕模式打开页面
3. 重启后端服务
4. 如果使用了Redis缓存，清除相关缓存

## 验证修复效果

### 1. 数据库验证
```sql
-- 确保有测试数据
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW()
WHERE id = (SELECT id FROM (SELECT id FROM jsh_material WHERE IFNULL(delete_flag, '0') != '1' LIMIT 1) t);

-- 验证设置成功
SELECT id, name, stock_alert_status FROM jsh_material WHERE stock_alert_status = 'RISK_IGNORED' LIMIT 1;
```

### 2. 前端验证
1. 刷新页面
2. 找到设置为忽略风险的商品
3. 检查是否显示橙色的"忽略风险"标签
4. 检查操作列是否显示"关注风险"按钮

### 3. 功能验证
1. 点击"关注风险"按钮
2. 检查是否成功切换状态
3. 点击"忽略风险"按钮
4. 检查是否成功切换回忽略状态

## 常见错误信息

### 后端错误
- `NullPointerException` - 可能是数据映射问题
- `数据库连接错误` - 检查数据库连接
- `字段不存在` - 检查数据库字段是否正确创建

### 前端错误
- `Cannot read property 'stockAlertStatus' of undefined` - 数据结构问题
- `Network Error` - 网络连接问题
- `JSON parse error` - 响应数据格式问题

## 联系支持
如果以上步骤都无法解决问题，请提供：
1. 数据库查询结果截图
2. 后端日志输出
3. 前端控制台输出
4. 网络请求的Response数据
5. 页面显示的截图

## 临时解决方案
如果问题仍然存在，可以临时使用以下方案：
1. 直接在前端硬编码测试数据验证渲染逻辑
2. 使用浏览器的本地存储覆盖数据
3. 临时禁用状态计算逻辑，直接返回数据库状态
