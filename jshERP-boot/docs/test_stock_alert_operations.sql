-- ========================================
-- 库存告急操作功能测试脚本
-- 创建时间: 2025-07-19
-- 用途: 测试忽略风险和关注风险操作是否正确更新数据库
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 查看当前的库存告急状态
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    last_six_months_sales,
    stock_alert_updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NOT NULL
ORDER BY id
LIMIT 10;

-- 2. 选择一个测试商品ID（请根据上面的查询结果修改）
SET @test_material_id = (
    SELECT id FROM jsh_material 
    WHERE IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    AND stock_alert_status = 'STOCK_ALERT'
    LIMIT 1
);

SELECT CONCAT('测试商品ID: ', @test_material_id) as message;

-- 3. 模拟忽略风险操作
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id;

-- 验证忽略风险操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at,
    '忽略风险操作后' as operation
FROM jsh_material 
WHERE id = @test_material_id;

-- 4. 模拟关注风险操作（清空ignored_at字段）
UPDATE jsh_material 
SET stock_alert_status = 'STOCK_ALERT',
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id;

-- 验证关注风险操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at,
    '关注风险操作后' as operation
FROM jsh_material 
WHERE id = @test_material_id;

-- 5. 测试updateStockAlertStatusAndClearIgnored方法的SQL
-- 先设置为忽略状态
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW()
WHERE id = @test_material_id;

-- 然后使用新的SQL方法清空
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    last_six_months_sales = 50.00,
    stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

-- 验证结果
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    last_six_months_sales,
    stock_alert_updated_at,
    'updateStockAlertStatusAndClearIgnored测试后' as operation
FROM jsh_material 
WHERE id = @test_material_id;

-- 6. 检查字段是否支持NULL值更新
SELECT 
    COLUMN_NAME,
    IS_NULLABLE,
    DATA_TYPE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'jsh_material' 
AND COLUMN_NAME = 'stock_alert_ignored_at';

-- 7. 测试批量操作
-- 创建几个测试记录
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW()
WHERE id IN (
    SELECT id FROM (
        SELECT id FROM jsh_material 
        WHERE IFNULL(delete_flag, '0') != '1' 
        AND tenant_id = @test_tenant_id
        LIMIT 3
    ) AS temp
);

-- 批量清空ignored_at字段
UPDATE jsh_material 
SET stock_alert_ignored_at = NULL,
    stock_alert_updated_at = NOW()
WHERE stock_alert_status = 'RISK_IGNORED'
AND tenant_id = @test_tenant_id
AND IFNULL(delete_flag, '0') != '1';

-- 查看批量操作结果
SELECT 
    id,
    name,
    stock_alert_status,
    stock_alert_ignored_at,
    stock_alert_updated_at
FROM jsh_material 
WHERE stock_alert_status = 'RISK_IGNORED'
AND tenant_id = @test_tenant_id
AND IFNULL(delete_flag, '0') != '1'
LIMIT 5;

SELECT '库存告急操作测试完成' as message;
