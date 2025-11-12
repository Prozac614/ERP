-- ========================================
-- 库存告急状态检查脚本
-- 创建时间: 2025-07-19
-- 用途: 快速检查库存告急状态，用于操作前后对比
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 显示当前时间
SELECT NOW() as current_time, '=== 库存告急状态检查开始 ===' as message;

-- 1. 统计各种状态的商品数量
SELECT 
    IFNULL(stock_alert_status, 'NULL') as status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status
ORDER BY stock_alert_status;

-- 2. 显示前10个商品的详细状态
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
ORDER BY id
LIMIT 10;

-- 3. 显示所有忽略风险的商品
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status = 'RISK_IGNORED'
ORDER BY stock_alert_ignored_at DESC;

-- 4. 显示所有库存告急的商品
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status = 'STOCK_ALERT'
ORDER BY id
LIMIT 5;

-- 5. 显示最近更新的商品状态
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_updated_at IS NOT NULL
ORDER BY stock_alert_updated_at DESC
LIMIT 5;

SELECT '=== 库存告急状态检查完成 ===' as message;
