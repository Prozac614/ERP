-- ========================================
-- 测试修复后的查询逻辑
-- 创建时间: 2025-07-19
-- 用途: 验证修改后的getMaterialPeriodStockOptimized查询是否正确
-- ========================================

-- 设置测试租户ID（根据您的数据，应该是133）
SET @test_tenant_id = 133;

SELECT '=== 修复后查询测试开始 ===' as message;

-- 1. 检查jsh_material表中有多少商品
SELECT 
    '1. jsh_material表商品统计' as step,
    COUNT(*) as total_materials,
    SUM(CASE WHEN stock_alert_status = 'NO_RISK' THEN 1 ELSE 0 END) as no_risk_count,
    SUM(CASE WHEN stock_alert_status = 'STOCK_ALERT' THEN 1 ELSE 0 END) as alert_count,
    SUM(CASE WHEN stock_alert_status = 'RISK_IGNORED' THEN 1 ELSE 0 END) as ignored_count,
    SUM(CASE WHEN stock_alert_status IS NULL THEN 1 ELSE 0 END) as null_count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

-- 2. 检查period_summary表中有多少记录
SELECT 
    '2. period_summary表记录统计' as step,
    COUNT(*) as total_summaries
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

-- 3. 测试修复后的查询逻辑（模拟getMaterialPeriodStockOptimized）
SELECT 
    '3. 修复后查询测试' as step,
    m.id as materialId,
    m.bar_code as barCode,
    m.name as materialName,
    -- 从期间汇总表获取库存统计数据（如果有的话）
    IFNULL(mps.current_period_stock, 0) as currentPeriodStock,
    IFNULL(mps.previous_period_stock, 0) as previousPeriodStock,
    IFNULL(mps.current_period_out, 0) as currentPeriodOut,
    IFNULL(mps.previous_period_out, 0) as previousPeriodOut,
    -- 直接从商品表获取库存告急状态
    m.stock_alert_status as stockAlertStatus,
    m.last_six_months_sales as lastSixMonthsSales,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    CASE 
        WHEN mps.material_id IS NULL THEN '⚠️ 无期间汇总数据'
        ELSE '✅ 有期间汇总数据'
    END as summary_status
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id
ORDER BY m.id
LIMIT 10;

-- 4. 专门测试忽略风险状态的商品
-- 先创建一个测试商品
SET @test_material_id = (
    SELECT m.id 
    FROM jsh_material m
    WHERE IFNULL(m.delete_flag,'0') != '1'
    AND m.tenant_id = @test_tenant_id
    LIMIT 1
);

-- 设置为忽略风险
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id;

-- 测试查询是否能正确返回忽略风险状态
SELECT 
    '4. 忽略风险状态测试' as step,
    m.id as materialId,
    m.name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    IFNULL(mps.current_period_stock, 0) as currentPeriodStock
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE m.id = @test_material_id
AND IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id;

-- 5. 测试搜索功能
SELECT 
    '5. 搜索功能测试' as step,
    COUNT(*) as search_result_count
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id
AND (m.bar_code LIKE '%W%' OR m.name LIKE '%胶%');

-- 6. 测试分页功能
SELECT 
    '6. 分页功能测试' as step,
    m.id as materialId,
    m.name as materialName,
    m.stock_alert_status as stockAlertStatus
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id
ORDER BY m.id
LIMIT 0, 5;

-- 7. 统计最终结果
SELECT 
    '7. 最终统计' as step,
    COUNT(*) as total_query_results,
    SUM(CASE WHEN m.stock_alert_status = 'RISK_IGNORED' THEN 1 ELSE 0 END) as ignored_in_results,
    SUM(CASE WHEN mps.material_id IS NOT NULL THEN 1 ELSE 0 END) as with_summary_data
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id
    AND IFNULL(mps.delete_flag,'0') != '1'
    AND mps.tenant_id = @test_tenant_id
WHERE IFNULL(m.delete_flag,'0') != '1'
AND m.tenant_id = @test_tenant_id;

SELECT '=== 修复后查询测试完成 ===' as message;
SELECT '如果看到忽略风险状态能正确返回，说明修复成功' as note;
