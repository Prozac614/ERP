-- ========================================
-- 库存告急功能测试数据初始化脚本
-- 创建时间: 2025-07-19
-- 用途: 为测试创建一些示例数据
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 为前几个商品设置不同的库存告急状态用于测试
UPDATE jsh_material 
SET stock_alert_status = 'STOCK_ALERT',
    last_six_months_sales = 100.00,
    stock_alert_updated_at = NOW()
WHERE id IN (
    SELECT id FROM (
        SELECT id FROM jsh_material 
        WHERE IFNULL(delete_flag, '0') != '1' 
        AND tenant_id = @test_tenant_id
        LIMIT 3
    ) AS temp
);

-- 2. 为接下来的几个商品设置无风险状态
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    last_six_months_sales = 50.00,
    stock_alert_updated_at = NOW()
WHERE id IN (
    SELECT id FROM (
        SELECT id FROM jsh_material 
        WHERE IFNULL(delete_flag, '0') != '1' 
        AND tenant_id = @test_tenant_id
        AND stock_alert_status IS NULL
        LIMIT 3
    ) AS temp
);

-- 3. 为一个商品设置忽略风险状态
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    last_six_months_sales = 80.00,
    stock_alert_updated_at = NOW()
WHERE id IN (
    SELECT id FROM (
        SELECT id FROM jsh_material 
        WHERE IFNULL(delete_flag, '0') != '1' 
        AND tenant_id = @test_tenant_id
        AND stock_alert_status IS NULL
        LIMIT 1
    ) AS temp
);

-- 4. 查看设置结果
SELECT 
    id,
    name,
    stock_alert_status,
    last_six_months_sales,
    stock_alert_ignored_at,
    stock_alert_updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NOT NULL
ORDER BY id
LIMIT 10;

-- 5. 验证期间汇总表中的数据
SELECT 
    mps.material_id,
    mps.material_name,
    mps.current_period_stock,
    m.stock_alert_status,
    m.last_six_months_sales
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
AND m.stock_alert_status IS NOT NULL
ORDER BY mps.material_id
LIMIT 10;

SELECT '测试数据初始化完成' as message;
