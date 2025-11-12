-- ========================================
-- 数据库查询测试脚本
-- 创建时间: 2025-07-19
-- 用途: 测试后端SQL查询是否正确返回库存告急状态
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

SELECT '=== 数据库查询测试开始 ===' as message;

-- 1. 首先确保有测试数据
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY id
LIMIT 2;

SELECT ROW_COUNT() as updated_rows, '设置测试数据' as operation;

-- 2. 直接查询jsh_material表
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    'jsh_material表直接查询' as source
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status = 'RISK_IGNORED'
ORDER BY id
LIMIT 5;

-- 3. 模拟后端的getMaterialPeriodStockOptimized查询
SELECT 
    mps.material_id as materialId,
    mps.bar_code as barCode,
    mps.material_name as materialName,
    mps.current_period_stock as currentPeriodStock,
    mps.previous_period_stock as previousPeriodStock,
    mps.current_period_out as currentPeriodOut,
    mps.previous_period_out as previousPeriodOut,
    -- 库存告急状态计算
    m.stock_alert_status as stockAlertStatus,
    m.last_six_months_sales as lastSixMonthsSales,
    m.stock_alert_ignored_at as stockAlertIgnoredAt,
    '后端查询模拟' as source
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
ORDER BY mps.material_id
LIMIT 10;

-- 4. 检查是否有数据不匹配的情况
SELECT 
    m.id,
    m.name,
    m.stock_alert_status as material_status,
    mps.material_id as summary_material_id,
    CASE 
        WHEN mps.material_id IS NULL THEN '❌ 缺少period_summary记录'
        WHEN m.stock_alert_status != 'RISK_IGNORED' THEN '❌ 状态不是RISK_IGNORED'
        ELSE '✅ 数据正常'
    END as status_check,
    '数据一致性检查' as source
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id 
    AND IFNULL(mps.delete_flag,'0') != '1'
WHERE IFNULL(m.delete_flag, '0') != '1' 
AND m.tenant_id = @test_tenant_id
AND m.stock_alert_status = 'RISK_IGNORED'
ORDER BY m.id
LIMIT 5;

-- 5. 检查jsh_material_period_summary表的数据完整性
SELECT 
    COUNT(*) as total_materials,
    '总商品数' as description
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

SELECT 
    COUNT(*) as total_summaries,
    '期间汇总记录数' as description
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id;

-- 6. 检查是否有商品没有对应的期间汇总记录
SELECT 
    m.id,
    m.name,
    '缺少期间汇总记录的商品' as issue
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id 
    AND IFNULL(mps.delete_flag,'0') != '1'
WHERE IFNULL(m.delete_flag, '0') != '1' 
AND m.tenant_id = @test_tenant_id
AND mps.material_id IS NULL
LIMIT 5;

-- 7. 测试特定商品的查询结果
SET @test_material_id = (
    SELECT id FROM jsh_material 
    WHERE stock_alert_status = 'RISK_IGNORED'
    AND IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    LIMIT 1
);

SELECT CONCAT('测试商品ID: ', IFNULL(@test_material_id, 'NULL')) as message;

-- 单独查询这个商品
SELECT 
    mps.material_id as materialId,
    mps.material_name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    '单个商品查询测试' as source
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE mps.material_id = @test_material_id
AND IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1';

-- 8. 检查字段类型和长度
DESCRIBE jsh_material;

-- 9. 检查是否有触发器或其他逻辑影响查询
SHOW TRIGGERS LIKE 'jsh_material%';

SELECT '=== 数据库查询测试完成 ===' as message;
SELECT '如果后端查询模拟的结果显示stockAlertStatus不是RISK_IGNORED，说明问题出现在数据库层面' as note;
