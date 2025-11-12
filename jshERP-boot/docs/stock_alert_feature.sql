-- ========================================
-- 库存告急功能数据库更新脚本
-- 创建时间: 2025-07-19
-- 功能描述: 为商品表添加库存告急相关字段
-- ========================================

-- 为jsh_material表添加库存告急相关字段
ALTER TABLE `jsh_material` 
ADD COLUMN `stock_alert_status` varchar(20) DEFAULT 'NO_RISK' COMMENT '库存告急状态：NO_RISK-无风险，STOCK_ALERT-库存告急，RISK_IGNORED-忽略风险' AFTER `attribute`,
ADD COLUMN `stock_alert_ignored_at` datetime DEFAULT NULL COMMENT '忽略风险的时间戳' AFTER `stock_alert_status`,
ADD COLUMN `last_six_months_sales` decimal(24,6) DEFAULT 0 COMMENT '过去六个月总销量（缓存字段）' AFTER `stock_alert_ignored_at`,
ADD COLUMN `stock_alert_updated_at` datetime DEFAULT NULL COMMENT '库存告急状态最后更新时间' AFTER `last_six_months_sales`;

-- 创建索引以提高查询性能
CREATE INDEX `idx_stock_alert_status` ON `jsh_material` (`stock_alert_status`);
CREATE INDEX `idx_stock_alert_updated` ON `jsh_material` (`stock_alert_updated_at`);

-- 初始化现有商品的库存告急状态为无风险
UPDATE `jsh_material` 
SET `stock_alert_status` = 'NO_RISK', 
    `stock_alert_updated_at` = NOW() 
WHERE `delete_flag` = '0';

-- ========================================
-- 创建库存告急状态计算的存储过程
-- ========================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `CalculateStockAlertStatus`$$

CREATE PROCEDURE `CalculateStockAlertStatus`(
    IN p_material_id BIGINT,
    IN p_tenant_id BIGINT
)
BEGIN
    DECLARE v_current_stock DECIMAL(24,6) DEFAULT 0;
    DECLARE v_six_months_sales DECIMAL(24,6) DEFAULT 0;
    DECLARE v_current_status VARCHAR(20) DEFAULT 'NO_RISK';
    DECLARE v_ignored_at DATETIME DEFAULT NULL;
    
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
    
    -- 获取当前状态和忽略时间
    SELECT stock_alert_status, stock_alert_ignored_at
    INTO v_current_status, v_ignored_at
    FROM jsh_material
    WHERE id = p_material_id;
    
    -- 计算新的告急状态
    IF v_current_status = 'RISK_IGNORED' THEN
        -- 如果当前是忽略风险状态，保持不变
        SET v_current_status = 'RISK_IGNORED';
    ELSEIF v_current_stock >= v_six_months_sales THEN
        -- 库存充足，无风险
        SET v_current_status = 'NO_RISK';
    ELSE
        -- 库存不足，告急
        SET v_current_status = 'STOCK_ALERT';
    END IF;
    
    -- 更新商品的库存告急状态
    UPDATE jsh_material 
    SET stock_alert_status = v_current_status,
        last_six_months_sales = v_six_months_sales,
        stock_alert_updated_at = NOW()
    WHERE id = p_material_id;
    
END$$

DELIMITER ;

-- ========================================
-- 创建批量更新库存告急状态的存储过程
-- ========================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `BatchUpdateStockAlertStatus`$$

CREATE PROCEDURE `BatchUpdateStockAlertStatus`(
    IN p_tenant_id BIGINT
)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_material_id BIGINT;
    
    -- 声明游标
    DECLARE material_cursor CURSOR FOR 
        SELECT id FROM jsh_material 
        WHERE IFNULL(delete_flag, '0') != '1'
        AND (p_tenant_id IS NULL OR tenant_id = p_tenant_id);
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    -- 打开游标
    OPEN material_cursor;
    
    -- 循环处理每个商品
    read_loop: LOOP
        FETCH material_cursor INTO v_material_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        -- 调用单个商品的状态计算过程
        CALL CalculateStockAlertStatus(v_material_id, p_tenant_id);
    END LOOP;
    
    -- 关闭游标
    CLOSE material_cursor;
    
END$$

DELIMITER ;

-- ========================================
-- 执行说明
-- ========================================
-- 1. 执行此脚本后，所有商品将具有库存告急相关字段
-- 2. 可以调用 CALL BatchUpdateStockAlertStatus(63); 来批量更新所有商品的库存告急状态
-- 3. 可以调用 CALL CalculateStockAlertStatus(商品ID, 租户ID); 来更新单个商品的状态
-- ========================================
