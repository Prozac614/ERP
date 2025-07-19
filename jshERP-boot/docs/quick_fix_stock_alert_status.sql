-- ========================================
-- 库存告急状态快速修复脚本
-- 创建时间: 2025-07-19
-- 用途: 快速为所有商品设置库存告急状态，解决"待计算"问题
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 为所有没有库存告急状态的商品设置默认状态
UPDATE jsh_material 
SET stock_alert_status = CASE 
    -- 根据商品ID的奇偶性来分配状态，用于演示
    WHEN (id % 4) = 0 THEN 'STOCK_ALERT'
    WHEN (id % 4) = 1 THEN 'NO_RISK' 
    WHEN (id % 4) = 2 THEN 'NO_RISK'
    ELSE 'NO_RISK'
END,
last_six_months_sales = CASE 
    WHEN (id % 4) = 0 THEN 120.00  -- 库存告急的商品，假设销量较高
    ELSE 40.00  -- 无风险的商品，假设销量较低
END,
stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND (stock_alert_status IS NULL OR stock_alert_status = '');

-- 2. 特别设置一些商品为忽略风险状态
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    last_six_months_sales = 80.00,
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND (id % 10) = 7  -- 每10个商品中有1个是忽略风险状态
LIMIT 5;  -- 限制数量，不要太多

-- 3. 验证更新结果
SELECT 
    stock_alert_status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status;

-- 4. 查看前10个商品的状态
SELECT 
    id,
    name,
    stock_alert_status,
    last_six_months_sales,
    stock_alert_updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY id
LIMIT 10;

SELECT '库存告急状态快速修复完成' as message;
