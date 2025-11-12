# 首页表格库存计算逻辑修复总结

## 问题分析

通过详细分析代码，发现首页表格中库存数据计算存在以下主要问题：

### 1. 期间定义不一致
- 代码中存在两套不同的期间定义逻辑
- 部分地方期间范围计算错误

### 2. 上期结存计算逻辑错误
- **错误逻辑**：`上期结存 = 当前库存 + 本期出库 - 本期入库`
- **正确逻辑**：`本期结存 = 上期结存 + 本期入库 - 本期出库`
- 应该是：`上期结存 = 本期结存 - 本期入库 + 本期出库`

### 3. 数据来源不一致
- 有些地方使用实时计算（从原始单据表）
- 有些地方使用预聚合表（jsh_material_period_summary）
- 两种方式可能产生不一致的结果

### 4. 小数精度问题
- 库存数据存在小数，但业务上应该是整数

## 修复方案

### 1. 统一期间定义（动态判断）
- **期间划分**：每半年为一期（2-7月 和 8-1月）
- **本期/上期取决于当前日期**：
  - 如果当前在2-7月，则本期是2-7月，上期是8-1月
  - 如果当前在8-1月，则本期是8-1月，上期是2-7月
- 根据当前月份动态判断期间范围

### 2. 修正库存计算公式
- **本期结存**：使用当前实际库存（最准确）
- **上期结存**：根据库存平衡公式反推：`上期结存 = 本期结存 - 本期入库 + 本期出库`
- **出库数据**：按正确的期间范围统计

### 3. 统一数据源
- 优先使用预聚合表提高性能
- 确保预聚合表数据及时更新
- 添加数据一致性验证

### 4. 严格的整数验证
- **库存数据必须是整数**：业务上不可能出现小数
- **如果发现小数**：直接报错，不进行四舍五入
- **数据完整性**：确保所有库存相关数据都是整数

## 修复内容

### 1. 数据库层面修复

#### 创建修复后的存储过程
- 文件：`fix_period_calculation_logic_corrected.sql`
- 存储过程：`refresh_material_period_summary_correct`
- 功能：按正确逻辑重新计算期间汇总数据

#### 修复SQL查询逻辑
- 文件：`DepotItemMapperEx.xml`
- 修复：`getMaterialPeriodStock` 查询中的计算逻辑
- 统一期间范围判断逻辑

### 2. 后端服务层修复

#### 优化服务类
- 文件：`DepotItemOptimizedService.java`
- 添加：自动刷新期间汇总数据的逻辑
- 确保：数据的实时性和准确性

#### 添加存储过程调用
- 文件：`DepotItemMapperEx.java`
- 添加：`refreshMaterialPeriodSummaryCorrect` 方法
- 映射：存储过程调用

### 3. 前端展示层验证
- 文件：`IndexChart.vue`
- 验证：数据展示逻辑正确
- 确认：列定义和渲染逻辑无误

## 测试和验证

### 1. 完整测试脚本
- 文件：`test_stock_calculation_fix.sql`
- 功能：全面验证修复效果
- 包含：修复前后对比、数据一致性检查

### 2. 快速修复脚本
- 文件：`quick_fix_stock_calculation.sql`
- 功能：立即应用修复
- 适用：生产环境快速部署

## 使用说明

### 立即修复（强烈推荐）
```sql
-- 执行最简修复脚本（确保无语法错误）
source jshERP-boot/docs/minimal_fix_stock_calculation.sql;
```

### 备选方案
```sql
-- 简化版本（包含更多验证）
source jshERP-boot/docs/simple_fix_stock_calculation.sql;

-- 完整版本（包含详细测试，修复语法错误后）
source jshERP-boot/docs/fix_period_calculation_logic_corrected.sql;
```

### 完整测试和修复
```sql
-- 1. 先执行完整修复脚本
source jshERP-boot/docs/fix_period_calculation_logic_corrected.sql;

-- 2. 再执行测试验证脚本
source jshERP-boot/docs/test_stock_calculation_fix.sql;
```

### 重启应用服务
修复完成后，建议重启应用服务以清除缓存：
```bash
# 重启Spring Boot应用
systemctl restart jsh-erp
# 或者
./restart.sh
```

## 验证结果

修复完成后，可以通过以下方式验证：

### 1. 数据库验证
```sql
-- 检查库存平衡
SELECT 
    COUNT(*) as 总商品数,
    COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) as 平衡商品数
FROM jsh_material_period_summary 
WHERE delete_flag = '0';
```

### 2. 前端验证
- 访问首页表格
- 检查本期结存、上期结存、本期出库、上期出库数据
- 验证数据的合理性和一致性

## 注意事项

1. **备份数据**：修复前建议备份 `jsh_material_period_summary` 表
2. **测试环境**：建议先在测试环境验证修复效果
3. **缓存清理**：修复后需要清理应用缓存
4. **定期更新**：建议定期执行存储过程更新汇总数据

## 预期效果

修复完成后，首页表格将显示：
- ✅ 准确的本期结存数据
- ✅ 正确计算的上期结存数据  
- ✅ 按正确期间统计的出库数据
- ✅ 严格验证的整数格式库存数据
- ✅ 符合库存平衡公式的数据关系

修复完成！请按照使用说明执行修复脚本，然后刷新前端页面查看效果。
