-- ========================================
-- 库存告急功能检查和修复脚本
-- 创建时间: 2025-07-19
-- 用途: 检查字段是否存在，只执行必要的修复操作
-- ========================================

-- 设置测试租户ID（请根据实际情况修改）
SET @test_tenant_id = 63;

-- 1. 检查字段是否存在
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

-- 2. 检查索引是否存在
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_NAME = 'jsh_material' 
AND INDEX_NAME IN ('idx_stock_alert_status', 'idx_stock_alert_updated');

-- 3. 如果字段存在但没有索引，创建索引（安全操作）
-- 创建索引（如果不存在的话，MySQL会忽略已存在的索引）
CREATE INDEX IF NOT EXISTS `idx_stock_alert_status` ON `jsh_material` (`stock_alert_status`);
CREATE INDEX IF NOT EXISTS `idx_stock_alert_updated` ON `jsh_material` (`stock_alert_updated_at`);

-- 4. 检查当前数据状态
SELECT 
    IFNULL(stock_alert_status, 'NULL') as status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status;

-- 5. 为NULL状态的商品设置默认值
UPDATE jsh_material 
SET stock_alert_status = 'NO_RISK',
    stock_alert_updated_at = NOW()
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
AND stock_alert_status IS NULL;

SELECT ROW_COUNT() as updated_null_status_count, '设置默认状态' as operation;

-- 6. 创建或更新存储过程
DROP PROCEDURE IF EXISTS `CalculateStockAlertStatus`;

DELIMITER $$
CREATE PROCEDURE `CalculateStockAlertStatus`(
    IN p_material_id BIGINT,
    IN p_tenant_id BIGINT
)
BEGIN
    DECLARE v_current_stock DECIMAL(24,6) DEFAULT 0;
    DECLARE v_six_months_sales DECIMAL(24,6) DEFAULT 0;
    DECLARE v_current_status VARCHAR(20) DEFAULT 'NO_RISK';
    
    -- 获取当前库存总量
    SELECT IFNULL(SUM(current_number), 0) 
    INTO v_current_stock
    FROM jsh_material_current_stock 
    WHERE material_id = p_material_id 
    AND IFNULL(delete_flag, '0') != '1'
    AND (p_tenant_id IS NULL OR tenant_id = p_tenant_id);
    
    -- 计算过去6个月的销量
    SELECT IFNULL(SUM(di.basic_number), 0)
    INTO v_six_months_sales
    FROM jsh_depot_item di
    LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
    WHERE di.material_id = p_material_id
    AND dh.type = '出库'
    AND dh.status = '1'
    AND IFNULL(di.delete_flag, '0') != '1'
    AND IFNULL(dh.delete_flag, '0') != '1'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
    AND (p_tenant_id IS NULL OR dh.tenant_id = p_tenant_id);
    
    -- 获取当前状态，如果是忽略风险则保持不变
    SELECT stock_alert_status INTO v_current_status
    FROM jsh_material WHERE id = p_material_id;
    
    -- 只有在非忽略风险状态时才重新计算
    IF v_current_status != 'RISK_IGNORED' THEN
        IF v_current_stock >= v_six_months_sales THEN
            SET v_current_status = 'NO_RISK';
        ELSE
            SET v_current_status = 'STOCK_ALERT';
        END IF;
    END IF;
    
    -- 更新商品的库存告急状态
    UPDATE jsh_material 
    SET stock_alert_status = v_current_status,
        last_six_months_sales = v_six_months_sales,
        stock_alert_updated_at = NOW()
    WHERE id = p_material_id;
    
END$$
DELIMITER ;

-- 7. 测试单个商品的状态计算
SET @test_material_id = (
    SELECT id FROM jsh_material 
    WHERE IFNULL(delete_flag, '0') != '1' 
    AND tenant_id = @test_tenant_id
    ORDER BY id
    LIMIT 1
);

SELECT CONCAT('测试商品ID: ', IFNULL(@test_material_id, 'NULL')) as message;

-- 调用存储过程测试
CALL CalculateStockAlertStatus(@test_material_id, @test_tenant_id);

-- 8. 查看测试结果
SELECT 
    id,
    name,
    stock_alert_status,
    last_six_months_sales,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE id = @test_material_id;

-- 9. 手动测试忽略风险操作
UPDATE jsh_material 
SET stock_alert_status = 'RISK_IGNORED',
    stock_alert_ignored_at = NOW(),
    stock_alert_updated_at = NOW()
WHERE id = @test_material_id
AND IFNULL(delete_flag, '0') != '1';

SELECT ROW_COUNT() as ignore_risk_result, '忽略风险测试' as operation;

-- 查看忽略风险结果
SELECT 
    id,
    name,
    stock_alert_status,
    DATE_FORMAT(stock_alert_ignored_at, '%Y-%m-%d %H:%i:%s') as ignored_at,
    DATE_FORMAT(stock_alert_updated_at, '%Y-%m-%d %H:%i:%s') as updated_at
FROM jsh_material 
WHERE id = @test_material_id;

-- 10. 最终状态统计
SELECT 
    IFNULL(stock_alert_status, 'NULL') as status,
    COUNT(*) as count
FROM jsh_material 
WHERE IFNULL(delete_flag, '0') != '1' 
AND tenant_id = @test_tenant_id
GROUP BY stock_alert_status
ORDER BY stock_alert_status;

SELECT '库存告急功能检查和修复完成' as message;
