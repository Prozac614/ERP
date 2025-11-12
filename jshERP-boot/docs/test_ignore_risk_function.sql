-- ========================================
-- 忽略风险功能专项测试脚本
-- 创建时间: 2025-07-19
-- 用途: 专门测试忽略风险功能是否正常工作
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

SELECT '=== 忽略风险功能测试开始 ===' as message;

-- 1. 选择一个测试商品
SET @test_material_id = (
    SELECT id FROM jsh_material 
    WHERE IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    ORDER BY id
    LIMIT 1
);

SELECT CONCAT('选择的测试商品ID: ', IFNULL(@test_material_id, 'NULL')) as message;

-- 2. 查看测试商品的初始状态
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    delete_flag,
    tenant_id,
    '初始状态' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 3. 先设置为库存告急状态（确保有可操作的状态）
UPDATE jsh_material 
SET stock_alert_status = 'STOCK_ALERT',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

SELECT ROW_COUNT() as setup_result, '设置为库存告急状态' as operation;

-- 4. 查看设置后的状态
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    '设置为告急后' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 5. 执行忽略风险操作（模拟后端API调用）
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

SELECT ROW_COUNT() as ignore_result, '执行忽略风险操作' as operation;

-- 6. 验证忽略风险操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    CASE 
        WHEN stock_alert_status = 'RISK_IGNORED' AND stock_alert_ignored_at IS NOT NULL THEN '✓ 成功'
        ELSE '✗ 失败'
    END as test_result,
    '忽略风险后' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 7. 测试关注风险操作
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

SELECT ROW_COUNT() as focus_result, '执行关注风险操作' as operation;

-- 8. 验证关注风险操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at,
    CASE 
        WHEN stock_alert_ignored_at IS NULL THEN '✓ 成功清空忽略时间'
        ELSE '✗ 未能清空忽略时间'
    END as test_result,
    '关注风险后' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 9. 测试批量操作
-- 创建几个测试记录
UPDATE jsh_material 
SET stock_alert_status = 'STOCK_ALERT',
    stock_alert_ignored_at = NULL
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
LIMIT 3;

-- 批量忽略风险
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE stock_alert_status = 'STOCK_ALERT'
AND IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
LIMIT 2;

SELECT ROW_COUNT() as batch_ignore_result, '批量忽略风险操作' as operation;

-- 10. 查看批量操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at
FROM jsh_material 
WHERE stock_alert_status = 'RISK_IGNORED'
AND IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY stock_alert_ignored_at DESC
LIMIT 5;

-- 11. 最终统计
SELECT 
    stock_alert_status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status
ORDER BY stock_alert_status;

-- 12. 测试总结
SELECT 
    '忽略风险功能测试' as test_name,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM jsh_material 
            WHERE stock_alert_status = 'RISK_IGNORED' 
            AND stock_alert_ignored_at IS NOT NULL
            AND IFNULL(delete_flag, '0') != '1' 
            AND tenant_id = @test_tenant_id
        ) THEN '✓ 通过'
        ELSE '✗ 失败'
    END as test_result;

SELECT '=== 忽略风险功能测试完成 ===' as message;
