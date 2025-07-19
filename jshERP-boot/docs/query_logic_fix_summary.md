# 库存告急查询逻辑修复总结

## 📋 **问题分析**

### **根本问题**
`getMaterialStockWithDailyOutOptimized` 方法无法获取到库存告急状态，原因是：

1. **错误的查询逻辑**：原来以 `jsh_material_period_summary` 为主表查询
2. **字段缺失**：`jsh_material_period_summary` 表根本没有库存告急相关字段
3. **表的作用混淆**：期间汇总表只是性能优化表，不包含业务状态

### **表结构分析**

#### **jsh_material_period_summary（期间汇总表）**
```sql
-- 这个表只包含库存统计数据，没有业务状态
- material_id: 商品ID
- current_period_stock: 本期结存
- previous_period_stock: 上期结存  
- current_period_out: 本期出库
- previous_period_out: 上期出库
- tenant_id: 租户ID
```

#### **jsh_material（商品主表）**
```sql
-- 这个表包含库存告急状态字段
- id: 商品ID
- name: 商品名称
- stock_alert_status: 库存告急状态 ⭐
- stock_alert_ignored_at: 忽略风险时间 ⭐
- last_six_months_sales: 六个月销量 ⭐
- tenant_id: 租户ID
```

## 🔧 **修复方案**

### **核心思路**
将查询逻辑从"以期间汇总表为主"改为"以商品主表为主"：

```sql
-- 修复前（错误）
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id

-- 修复后（正确）  
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
```

### **具体修改**

#### **1. 主查询修改**
```xml
<!-- 修改前 -->
<select id="getMaterialPeriodStockOptimized">
    select
        mps.material_id as materialId,
        mps.material_name as materialName,
        m.stock_alert_status as stockAlertStatus  -- 可能为NULL
    from jsh_material_period_summary mps
    left join jsh_material m on mps.material_id = m.id
</select>

<!-- 修改后 -->
<select id="getMaterialPeriodStockOptimized">
    select
        m.id as materialId,
        m.name as materialName,
        m.stock_alert_status as stockAlertStatus,  -- 直接获取
        IFNULL(mps.current_period_stock, 0) as currentPeriodStock
    from jsh_material m
    left join jsh_material_period_summary mps on m.id = mps.material_id
</select>
```

#### **2. 计数查询修改**
```xml
<!-- 修改前 -->
<select id="getMaterialPeriodStockCountOptimized">
    select count(1) from jsh_material_period_summary mps
    where ifnull(mps.delete_flag,'0') != '1'
</select>

<!-- 修改后 -->
<select id="getMaterialPeriodStockCountOptimized">
    select count(1) from jsh_material m
    where ifnull(m.delete_flag,'0') != '1'
</select>
```

#### **3. 条件查询修改**
```xml
<!-- 修改前 -->
<if test="tenantId != null">
    and mps.tenant_id = #{tenantId}
</if>
<if test="materialParam != null and materialParam != ''">
    and (mps.bar_code like concat('%', #{materialParam}, '%')
        or mps.material_name like concat('%', #{materialParam}, '%'))
</if>

<!-- 修改后 -->
<if test="tenantId != null">
    and m.tenant_id = #{tenantId}
</if>
<if test="materialParam != null and materialParam != ''">
    and (m.bar_code like concat('%', #{materialParam}, '%')
        or m.name like concat('%', #{materialParam}, '%'))
</if>
```

## 📊 **修复效果**

### **修复前的问题**
- ❌ 库存告急状态经常为NULL
- ❌ 忽略风险状态无法正确显示
- ❌ 只能查询到有期间汇总数据的商品

### **修复后的效果**
- ✅ 库存告急状态正确显示
- ✅ 忽略风险状态正确显示  
- ✅ 所有商品都能被查询到
- ✅ 期间统计数据正常显示（如果有的话）

## 🔍 **数据流程**

### **修复后的查询逻辑**
```
1. 以 jsh_material 为主表查询所有商品
2. LEFT JOIN jsh_material_period_summary 获取期间统计数据
3. 直接返回 jsh_material 中的库存告急状态
4. 如果没有期间统计数据，统计字段显示为0
```

### **数据完整性保证**
```sql
-- 所有商品都会被查询到
SELECT COUNT(*) FROM jsh_material WHERE tenant_id = 133;  -- 主表商品数

-- 期间统计数据可选
SELECT COUNT(*) FROM jsh_material_period_summary WHERE tenant_id = 133;  -- 559条

-- 查询结果 = 主表商品数（库存状态完整）+ 期间统计数据（可选）
```

## 🧪 **测试验证**

### **测试步骤**
1. **执行测试脚本**：
   ```bash
   mysql -u username -p database_name < docs/test_fixed_query.sql
   ```

2. **重启后端服务**（应用SQL修改）

3. **前端验证**：
   - 刷新库存管理页面
   - 检查库存告急状态是否正确显示
   - 测试忽略风险/关注风险功能

### **预期结果**
- ✅ 所有商品的库存告急状态都能正确显示
- ✅ 忽略风险状态不再丢失
- ✅ 期间统计数据正常显示
- ✅ 搜索和分页功能正常

## 📝 **注意事项**

### **性能考虑**
- 主表查询可能比期间汇总表查询稍慢
- 但数据完整性更重要
- 可以通过索引优化性能

### **数据一致性**
- 确保 `jsh_material` 表的库存告急字段有正确的数据
- 定期执行库存预警校验更新状态

### **向后兼容**
- 修改不影响其他功能
- 期间统计数据仍然可用
- 只是改变了查询的主次关系

## 🎯 **总结**

这次修复解决了库存告急功能的根本问题：
1. **正确的数据源**：从商品主表获取业务状态
2. **完整的数据**：所有商品都能被查询到
3. **准确的状态**：库存告急状态不再丢失

修复后，库存告急功能将能够正常工作，用户可以正确看到和操作商品的库存预警状态。
