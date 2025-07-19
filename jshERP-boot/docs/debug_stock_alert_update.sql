-- ========================================
-- 库存告急更新调试脚本
-- 创建时间: 2025-07-19
-- 用途: 调试忽略风险功能的数据库更新问题
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 查看当前所有商品的库存告急状态
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at,
    delete_flag,
    tenant_id
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY id
LIMIT 10;

-- 2. 选择一个测试商品ID
SET @test_material_id = (
    SELECT id FROM jsh_material 
    WHERE IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    ORDER BY id
    LIMIT 1
);

SELECT CONCAT('选择的测试商品ID: ', IFNULL(@test_material_id, 'NULL')) as message;

-- 3. 查看测试商品的详细信息
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at,
    delete_flag,
    tenant_id,
    '更新前状态' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 4. 测试直接SQL更新（模拟updateStockAlertToIgnored方法）
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

-- 查看更新结果
SELECT ROW_COUNT() as affected_rows, '直接SQL更新后' as note;

-- 5. 验证更新结果
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at,
    delete_flag,
    tenant_id,
    '更新后状态' as note
FROM jsh_material 
WHERE id = @test_material_id;

-- 6. 测试条件是否正确
SELECT 
    id,
    name,
    IFNULL(delete_flag, '0') as delete_flag_check,
    CASE WHEN IFNULL(delete_flag, '0') != '1' THEN '符合条件' ELSE '不符合条件' END as condition_check
FROM jsh_material 
WHERE id = @test_material_id;

-- 7. 检查字段是否存在
DESCRIBE jsh_material;

-- 8. 检查字段约束
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'jsh_material' 
AND COLUMN_NAME IN ('stock_alert_status', 'stock_alert_ignored_at', 'stock_alert_updated_at')
ORDER BY ORDINAL_POSITION;

-- 9. 测试批量更新
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NULL;

SELECT ROW_COUNT() as batch_update_affected_rows, '批量初始化更新' as note;

-- 10. 最终状态检查
SELECT 
    stock_alert_status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status;

-- 11. 检查是否有权限问题
SHOW GRANTS FOR CURRENT_USER();

SELECT '调试脚本执行完成' as message;
