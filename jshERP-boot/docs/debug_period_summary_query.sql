-- ========================================
-- 调试期间汇总表查询问题
-- 创建时间: 2025-07-19
-- 用途: 检查为什么getMaterialPeriodStockOptimized查询不到库存告急状态
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

SELECT '=== 期间汇总表查询调试开始 ===' as message;

-- 1. 检查jsh_material表中的库存告急状态数据
SELECT 
    '1. jsh_material表状态检查' as step,
    COUNT(*) as total_materials,
    SUM(CASE WHEN stock_alert_status = 'NO_RISK' THEN 1 ELSE 0 END) as no_risk_count,
    SUM(CASE WHEN stock_alert_status = 'STOCK_ALERT' THEN 1 ELSE 0 END) as alert_count,
    SUM(CASE WHEN stock_alert_status = 'RISK_IGNORED' THEN 1 ELSE 0 END) as ignored_count,
    SUM(CASE WHEN stock_alert_status IS NULL THEN 1 ELSE 0 END) as null_count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

-- 2. 检查jsh_material_period_summary表的数据
SELECT 
    '2. period_summary表数据检查' as step,
    COUNT(*) as total_summaries
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

-- 3. 检查两个表的关联情况
SELECT 
    '3. 表关联检查' as step,
    COUNT(DISTINCT mps.material_id) as summary_materials,
    COUNT(DISTINCT m.id) as material_records,
    COUNT(DISTINCT CASE WHEN m.id IS NOT NULL THEN mps.material_id END) as joined_materials
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id AND IFNULL(m.delete_flag, '0') != '1'
WHERE IFNULL(mps.delete_flag, '0') != '1' 
AND mps.tenant_id = @test_tenant_id;

-- 4. 查看具体的关联结果（前10条）
SELECT 
    '4. 具体关联结果' as step,
    mps.material_id,
    mps.material_name,
    m.id as material_table_id,
    m.name as material_table_name,
    m.stock_alert_status,
    CASE 
        WHEN m.id IS NULL THEN '❌ 未关联到material表'
        WHEN m.stock_alert_status IS NULL THEN '⚠️ 状态为NULL'
        ELSE '✅ 正常'
    END as status_check
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id AND IFNULL(m.delete_flag, '0') != '1'
WHERE IFNULL(mps.delete_flag, '0') != '1' 
AND mps.tenant_id = @test_tenant_id
ORDER BY mps.material_id
LIMIT 10;

-- 5. 检查是否有商品在material表中但不在period_summary表中
SELECT 
    '5. 缺失的period_summary记录' as step,
    COUNT(*) as missing_count
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id 
    AND IFNULL(mps.delete_flag, '0') != '1'
WHERE IFNULL(m.delete_flag, '0') != '1' 
AND m.tenant_id = @test_tenant_id
AND mps.material_id IS NULL;

-- 6. 显示缺失的商品（前5个）
SELECT 
    '6. 缺失的商品详情' as step,
    m.id,
    m.name,
    m.stock_alert_status,
    '缺少period_summary记录' as issue
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id 
    AND IFNULL(mps.delete_flag, '0') != '1'
WHERE IFNULL(m.delete_flag, '0') != '1' 
AND m.tenant_id = @test_tenant_id
AND mps.material_id IS NULL
LIMIT 5;

-- 7. 完整模拟getMaterialPeriodStockOptimized查询
SELECT 
    '7. 完整查询模拟' as step,
    mps.material_id as materialId,
    mps.bar_code as barCode,
    mps.material_name as materialName,
    mps.current_period_stock as currentPeriodStock,
    mps.previous_period_stock as previousPeriodStock,
    mps.current_period_out as currentPeriodOut,
    mps.previous_period_out as previousPeriodOut,
    m.stock_alert_status as stockAlertStatus,
    m.last_six_months_sales as lastSixMonthsSales,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    CASE 
        WHEN m.stock_alert_status IS NULL THEN '❌ 状态为NULL'
        WHEN m.stock_alert_status = '' THEN '❌ 状态为空字符串'
        ELSE CONCAT('✅ 状态: ', m.stock_alert_status)
    END as status_check
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
ORDER BY mps.material_id
LIMIT 10;

-- 8. 检查是否有忽略风险的商品能被正确查询到
SELECT 
    '8. 忽略风险商品查询测试' as step,
    mps.material_id as materialId,
    mps.material_name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
AND m.stock_alert_status = 'RISK_IGNORED'
ORDER BY mps.material_id
LIMIT 5;

-- 9. 如果没有忽略风险的商品，手动创建一个测试
-- 先找一个商品设置为忽略风险
SET @test_material_id = (
    SELECT mps.material_id 
    FROM jsh_material_period_summary mps
    LEFT JOIN jsh_material m ON mps.material_id = m.id
    WHERE IFNULL(mps.delete_flag,'0') != '1'
    AND IFNULL(m.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
    LIMIT 1
);

-- 设置为忽略风险
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id;

-- 验证设置结果
SELECT 
    '9. 测试商品设置验证' as step,
    @test_material_id as test_material_id,
    m.stock_alert_status,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at
FROM jsh_material m
WHERE m.id = @test_material_id;

-- 验证查询是否能返回正确结果
SELECT 
    '10. 最终查询验证' as step,
    mps.material_id as materialId,
    mps.material_name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE mps.material_id = @test_material_id
AND IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1';

SELECT '=== 期间汇总表查询调试完成 ===' as message;
SELECT '请检查以上结果，特别关注status_check列的信息' as note;
