-- ========================================
-- 测试最终修复后的查询
-- 创建时间: 2025-07-19
-- 用途: 验证修复bar_code字段问题后的查询是否正确
-- ========================================

-- 设置测试租户ID
SET @test_tenant_id = 133;

SELECT '=== 最终查询修复测试开始 ===' as message;

-- 1. 检查jsh_material表结构（确认没有bar_code字段）
DESCRIBE jsh_material;

-- 2. 测试修复后的查询（模拟getMaterialPeriodStockOptimized）
SELECT 
    '修复后查询测试' as step,
    m.id as materialId,
    mps.bar_code as barCode,  -- 从period_summary表获取
    m.name as materialName,
    IFNULL(mps.current_period_stock, 0) as currentPeriodStock,
    IFNULL(mps.previous_period_stock, 0) as previousPeriodStock,
    IFNULL(mps.current_period_out, 0) as currentPeriodOut,
    IFNULL(mps.previous_period_out, 0) as previousPeriodOut,
    m.stock_alert_status as stockAlertStatus,
    m.last_six_months_sales as lastSixMonthsSales,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id
ORDER BY m.id
LIMIT 5;

-- 3. 测试搜索功能（使用bar_code搜索）
SELECT 
    '搜索功能测试' as step,
    COUNT(*) as search_count
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id
AND (mps.bar_code LIKE '%W%' OR m.name LIKE '%胶%');

-- 4. 测试计数查询
SELECT 
    '计数查询测试' as step,
    COUNT(1) as total_count
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id;

-- 5. 创建一个忽略风险的测试商品
SET @test_material_id = (
    SELECT m.id 
    FROM jsh_material m
    WHERE IFNULL(m.delete_flag,'0') != '1'
    AND m.tenant_id = @test_tenant_id
    LIMIT 1
);

UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id;

-- 6. 验证忽略风险状态能正确查询
SELECT 
    '忽略风险状态验证' as step,
    m.id as materialId,
    mps.bar_code as barCode,
    m.name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE m.id = @test_material_id
AND IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id;

SELECT '=== 最终查询修复测试完成 ===' as message;
SELECT '如果以上查询都能正常执行且返回正确结果，说明修复成功' as note;
