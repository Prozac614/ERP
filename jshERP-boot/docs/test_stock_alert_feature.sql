-- ========================================
-- 库存告急功能测试脚本
-- 创建时间: 2025-07-19
-- 用途: 测试库存告急功能的完整流程
-- ========================================

-- 1. 检查数据库字段是否正确添加
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'jsh_material' 
AND COLUMN_NAME IN ('stock_alert_status', 'stock_alert_ignored_at', 'last_six_months_sales', 'stock_alert_updated_at')
ORDER BY ORDINAL_POSITION;

-- 2. 检查索引是否创建成功
SHOW INDEX FROM jsh_material WHERE Key_name IN ('idx_stock_alert_status', 'idx_stock_alert_updated');

-- 3. 检查存储过程是否创建成功
SHOW PROCEDURE STATUS WHERE Name IN ('CalculateStockAlertStatus', 'BatchUpdateStockAlertStatus');

-- 4. 测试单个商品的库存告急状态计算
-- 选择一个测试商品ID（请根据实际数据修改）
SET @test_material_id = 568;
SET @test_tenant_id = 63;

-- 查看测试商品的基本信息
SELECT 
    m.id,
    m.name,
    me.bar_code,
    m.stock_alert_status,
    m.last_six_months_sales,
    m.stock_alert_updated_at
FROM jsh_material m
LEFT JOIN jsh_material_extend me ON m.id = me.material_id AND me.default_flag = '1'
WHERE m.id = @test_material_id;

-- 查看测试商品的当前库存
SELECT 
    material_id,
    depot_id,
    current_number,
    current_unit_price
FROM jsh_material_current_stock 
WHERE material_id = @test_material_id 
AND IFNULL(delete_flag, '0') != '1';

-- 查看测试商品过去6个月的销量
SELECT 
    di.material_id,
    DATE(dh.oper_time) as sale_date,
    SUM(di.basic_number) as daily_sales
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
WHERE di.material_id = @test_material_id
AND dh.type = '出库'
AND dh.status = '1'
AND IFNULL(di.delete_flag, '0') != '1'
AND IFNULL(dh.delete_flag, '0') != '1'
AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
AND dh.tenant_id = @test_tenant_id
GROUP BY di.material_id, DATE(dh.oper_time)
ORDER BY sale_date DESC
LIMIT 10;

-- 5. 测试存储过程
CALL CalculateStockAlertStatus(@test_material_id, @test_tenant_id);

-- 查看计算结果
SELECT 
    m.id,
    m.name,
    m.stock_alert_status,
    m.last_six_months_sales,
    m.stock_alert_updated_at,
    -- 当前总库存
    IFNULL((
        SELECT SUM(current_number) 
        FROM jsh_material_current_stock 
        WHERE material_id = m.id 
        AND IFNULL(delete_flag, '0') != '1'
    ), 0) as current_total_stock
FROM jsh_material m
WHERE m.id = @test_material_id;

-- 6. 测试批量更新（小范围测试）
-- 只更新前5个商品，避免影响大量数据
CREATE TEMPORARY TABLE temp_test_materials AS
SELECT id FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
LIMIT 5;

-- 为测试商品计算库存告急状态
UPDATE jsh_material m
INNER JOIN temp_test_materials t ON m.id = t.id
SET m.stock_alert_status = CASE 
    WHEN m.stock_alert_status = 'RISK_IGNORED' THEN 'RISK_IGNORED'
    WHEN IFNULL((
        SELECT SUM(current_number) 
        FROM jsh_material_current_stock mcs 
        WHERE mcs.material_id = m.id 
        AND IFNULL(mcs.delete_flag, '0') != '1'
    ), 0) >= IFNULL((
        SELECT SUM(di.basic_number)
        FROM jsh_depot_item di
        LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
        WHERE di.material_id = m.id
        AND dh.type = '出库'
        AND dh.status = '1'
        AND IFNULL(di.delete_flag, '0') != '1'
        AND IFNULL(dh.delete_flag, '0') != '1'
        AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
        AND dh.tenant_id = @test_tenant_id
    ), 0) THEN 'NO_RISK'
    ELSE 'STOCK_ALERT'
END,
m.last_six_months_sales = IFNULL((
    SELECT SUM(di.basic_number)
    FROM jsh_depot_item di
    LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
    WHERE di.material_id = m.id
    AND dh.type = '出库'
    AND dh.status = '1'
    AND IFNULL(di.delete_flag, '0') != '1'
    AND IFNULL(dh.delete_flag, '0') != '1'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
    AND dh.tenant_id = @test_tenant_id
), 0),
m.stock_alert_updated_at = NOW();

-- 7. 查看测试结果统计
SELECT 
    stock_alert_status,
    COUNT(*) as count,
    AVG(last_six_months_sales) as avg_six_months_sales
FROM jsh_material m
INNER JOIN temp_test_materials t ON m.id = t.id
GROUP BY stock_alert_status;

-- 8. 清理测试数据
DROP TEMPORARY TABLE temp_test_materials;

-- 9. 验证API相关的查询性能
-- 模拟前端查询
SELECT 
    mps.material_id as materialId,
    mps.bar_code as barCode,
    mps.material_name as materialName,
    mps.current_period_stock as currentPeriodStock,
    mps.previous_period_stock as previousPeriodStock,
    mps.current_period_out as currentPeriodOut,
    mps.previous_period_out as previousPeriodOut,
    m.stock_alert_status as stockAlertStatus,
    m.last_six_months_sales as lastSixMonthsSales,
    m.stock_alert_ignored_at as stockAlertIgnoredAt
FROM jsh_material_period_summary mps
LEFT JOIN jsh_material m ON mps.material_id = m.id
WHERE IFNULL(mps.delete_flag,'0') != '1'
AND IFNULL(m.delete_flag,'0') != '1'
AND mps.tenant_id = @test_tenant_id
ORDER BY mps.material_id
LIMIT 10;

-- 10. 测试六个月销量查询性能
SELECT 
    material_id,
    IFNULL(SUM(di.basic_number), 0) as six_months_sales
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
WHERE di.material_id IN (SELECT id FROM jsh_material WHERE tenant_id = @test_tenant_id LIMIT 5)
AND dh.type = '出库'
AND dh.status = '1'
AND IFNULL(di.delete_flag, '0') != '1'
AND IFNULL(dh.delete_flag, '0') != '1'
AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
AND dh.tenant_id = @test_tenant_id
GROUP BY material_id;

-- ========================================
-- 测试完成提示
-- ========================================
SELECT 
    '库存告急功能测试完成' as message,
    NOW() as test_time,
    '请检查以上查询结果，确认功能正常' as note;
