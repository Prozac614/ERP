-- ========================================
-- 验证修复效果脚本
-- 创建时间: 2025-07-19
-- 用途: 验证前端显示问题是否已修复
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 确保有忽略风险的测试数据
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY id
LIMIT 2;

-- 2. 确保有库存告急的测试数据
UPDATE jsh_material 
SET stock_alert_status = 'STOCK_ALERT',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NULL
ORDER BY id
LIMIT 2;

-- 3. 确保有无风险的测试数据
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NULL
ORDER BY id
LIMIT 2;

-- 4. 验证测试数据设置结果
SELECT 
    stock_alert_status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NOT NULL
GROUP BY stock_alert_status
ORDER BY stock_alert_status;

-- 5. 模拟前端查询，验证返回结果
SELECT 
    mps.material_id as materialId,
    mps.material_name as materialName,
    mps.current_period_stock as currentPeriodStock,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    CASE 
        WHEN m.stock_alert_status = 'RISK_IGNORED' THEN '✓ 应显示忽略风险'
        WHEN m.stock_alert_status = 'STOCK_ALERT' THEN '✓ 应显示库存告急'
        WHEN m.stock_alert_status = 'NO_RISK' THEN '✓ 应显示无风险'
        ELSE '✗ 状态异常'
    END as expected_display
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
AND m.stock_alert_status IS NOT NULL
ORDER BY m.stock_alert_status, mps.material_id
LIMIT 10;

-- 6. 检查特定的忽略风险商品
SELECT 
    '=== 忽略风险商品检查 ===' as message;

SELECT 
    mps.material_id as materialId,
    mps.material_name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    '这些商品应该在前端显示为忽略风险' as note
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
AND m.stock_alert_status = 'RISK_IGNORED'
ORDER BY mps.material_id
LIMIT 5;

SELECT '验证完成，请重启后端服务并刷新前端页面测试' as message;
