-- ========================================
-- 前端显示问题调试脚本
-- 创建时间: 2025-07-19
-- 用途: 调试为什么数据库中是忽略风险，但前端显示库存告警
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

SELECT '=== 前端显示问题调试开始 ===' as message;

-- 1. 检查jsh_material表中的实际状态
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    delete_flag,
    tenant_id,
    'jsh_material表状态' as source
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status = 'RISK_IGNORED'
ORDER BY id
LIMIT 5;

-- 2. 检查jsh_material_period_summary表是否存在对应记录
SELECT 
    mps.material_id,
    mps.material_name,
    mps.current_period_stock,
    mps.delete_flag as mps_delete_flag,
    mps.tenant_id as mps_tenant_id,
    'period_summary表状态' as source
FROM jsh_material_period_summary mps
WHERE mps.material_id IN (
    SELECT id FROM jsh_material 
    WHERE stock_alert_status = 'RISK_IGNORED'
    AND IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    LIMIT 5
)
AND IFNULL(mps.delete_flag,'0') != '1'
ORDER BY mps.material_id;

-- 3. 模拟后端查询（getMaterialPeriodStockOptimized）
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
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    '后端查询结果' as source
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
AND m.stock_alert_status = 'RISK_IGNORED'
ORDER BY mps.material_id
LIMIT 5;

-- 4. 检查是否有数据不一致的情况
SELECT 
    m.id,
    m.name,
    m.stock_alert_status as material_status,
    CASE 
        WHEN mps.material_id IS NULL THEN '缺少period_summary记录'
        ELSE '有period_summary记录'
    END as period_summary_status,
    m.tenant_id as material_tenant,
    mps.tenant_id as summary_tenant,
    '数据一致性检查' as source
FROM jsh_material m
LEFT JOIN jsh_material_period_summary mps ON m.id = mps.material_id 
    AND IFNULL(mps.delete_flag,'0') != '1'
WHERE IFNULL(m.delete_flag, '0') != '1' 
AND m.tenant_id = @test_tenant_id
AND m.stock_alert_status = 'RISK_IGNORED'
ORDER BY m.id
LIMIT 5;

-- 5. 检查是否有重复的period_summary记录
SELECT 
    material_id,
    COUNT(*) as record_count,
    GROUP_CONCAT(id) as summary_ids,
    '重复记录检查' as source
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag,'0') != '1'
AND tenant_id = @test_tenant_id
AND material_id IN (
    SELECT id FROM jsh_material 
    WHERE stock_alert_status = 'RISK_IGNORED'
    AND IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    LIMIT 5
)
GROUP BY material_id
HAVING COUNT(*) > 1;

-- 6. 检查字段映射是否正确
DESCRIBE jsh_material_period_summary;

-- 7. 检查最近的更新记录
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    TIMESTAMPDIFF(MINUTE, stock_alert_updated_at, NOW()) as minutes_ago,
    '最近更新记录' as source
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_updated_at IS NOT NULL
ORDER BY stock_alert_updated_at DESC
LIMIT 10;

-- 8. 统计各种状态的数量
SELECT 
    IFNULL(stock_alert_status, 'NULL') as status,
    COUNT(*) as count,
    '状态统计' as source
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status
ORDER BY stock_alert_status;

-- 9. 检查是否有缓存或其他干扰因素
-- 查看是否有其他表或视图可能影响结果
SHOW TABLES LIKE '%material%';

-- 10. 建议的解决方案测试
-- 手动设置一个商品为忽略风险状态，然后验证查询结果
SET @debug_material_id = (
    SELECT id FROM jsh_material 
    WHERE IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    ORDER BY id
    LIMIT 1
);

-- 设置为忽略风险
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @debug_material_id;

-- 验证设置结果
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    '手动设置后验证' as source
FROM jsh_material 
WHERE id = @debug_material_id;

-- 验证后端查询是否能正确返回
SELECT 
    mps.material_id as materialId,
    mps.material_name as materialName,
    m.stock_alert_status as stockAlertStatus,
    DATE_FORMAT(m.stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as stockAlertIgnoredAt,
    '后端查询验证' as source
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE mps.material_id = @debug_material_id
AND IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1';

SELECT '=== 前端显示问题调试完成 ===' as message;
SELECT '请检查以上查询结果，特别关注stockAlertStatus字段的值' as note;
