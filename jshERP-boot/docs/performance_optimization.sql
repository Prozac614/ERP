-- =========================================
-- ERP性能优化SQL脚本
-- 适用于查询多于修改的场景
-- 创建时间：2024年
-- =========================================

-- ----------------------------
-- 1. 每日出库汇总表
-- ----------------------------
DROP TABLE IF EXISTS `jsh_daily_out_summary`;
CREATE TABLE `jsh_daily_out_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint(20) NOT NULL COMMENT '商品ID',
  `bar_code` varchar(50) DEFAULT NULL COMMENT '商品唛头',
  `material_name` varchar(100) DEFAULT NULL COMMENT '商品名称',
  `out_date` date NOT NULL COMMENT '出库日期',
  `total_out_quantity` decimal(24,6) DEFAULT '0.000000' COMMENT '总出库数量',
  `depot_count` int(10) DEFAULT '0' COMMENT '涉及仓库数量',
  `bill_count` int(10) DEFAULT '0' COMMENT '单据数量',
  `last_update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_date_tenant` (`material_id`, `out_date`, `tenant_id`),
  KEY `idx_barcode_date` (`bar_code`, `out_date`),
  KEY `idx_date_range` (`out_date`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_material_tenant` (`material_id`, `tenant_id`),
  KEY `idx_update_time` (`last_update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='每日出库汇总表-性能优化';

-- ----------------------------
-- 2. 商品期间汇总表
-- ----------------------------
DROP TABLE IF EXISTS `jsh_material_period_summary`;
CREATE TABLE `jsh_material_period_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint(20) NOT NULL COMMENT '商品ID',
  `bar_code` varchar(50) DEFAULT NULL COMMENT '商品唛头',
  `material_name` varchar(100) DEFAULT NULL COMMENT '商品名称',
  `material_model` varchar(50) DEFAULT NULL COMMENT '规格',
  `material_unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `current_period_stock` decimal(24,6) DEFAULT '0.000000' COMMENT '本期结存',
  `previous_period_stock` decimal(24,6) DEFAULT '0.000000' COMMENT '上期结存',
  `current_period_out` decimal(24,6) DEFAULT '0.000000' COMMENT '本期出库',
  `previous_period_out` decimal(24,6) DEFAULT '0.000000' COMMENT '上期出库',
  `current_period_in` decimal(24,6) DEFAULT '0.000000' COMMENT '本期入库',
  `previous_period_in` decimal(24,6) DEFAULT '0.000000' COMMENT '上期入库',
  `last_calculation_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后计算时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_tenant` (`material_id`, `tenant_id`),
  KEY `idx_barcode` (`bar_code`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_calculation_time` (`last_calculation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='商品期间汇总表-性能优化';

-- ----------------------------
-- 3. 优化现有表索引
-- ----------------------------

-- 优化单据主表索引
ALTER TABLE `jsh_depot_head` ADD INDEX `idx_type_status_time` (`type`, `status`, `oper_time`);
ALTER TABLE `jsh_depot_head` ADD INDEX `idx_oper_time_type` (`oper_time`, `type`);
ALTER TABLE `jsh_depot_head` ADD INDEX `idx_tenant_time` (`tenant_id`, `oper_time`);

-- 优化单据子表索引
ALTER TABLE `jsh_depot_item` ADD INDEX `idx_material_header_time` (`material_id`, `header_id`);
ALTER TABLE `jsh_depot_item` ADD INDEX `idx_depot_material` (`depot_id`, `material_id`);
ALTER TABLE `jsh_depot_item` ADD INDEX `idx_tenant_material` (`tenant_id`, `material_id`);

-- 优化商品扩展表索引
ALTER TABLE `jsh_material_extend` ADD INDEX `idx_material_default_tenant` (`material_id`, `default_flag`, `tenant_id`);
ALTER TABLE `jsh_material_extend` ADD INDEX `idx_barcode_tenant` (`bar_code`, `tenant_id`);

-- 优化商品表索引
ALTER TABLE `jsh_material` ADD INDEX `idx_name_tenant` (`name`, `tenant_id`);
ALTER TABLE `jsh_material` ADD INDEX `idx_category_tenant` (`category_id`, `tenant_id`);

-- ----------------------------
-- 4. 存储过程：更新每日出库汇总
-- ----------------------------
DROP PROCEDURE IF EXISTS `update_daily_out_summary`;
DELIMITER $$
CREATE PROCEDURE `update_daily_out_summary`(
    IN p_material_id BIGINT,
    IN p_target_date DATE,
    IN p_tenant_id BIGINT
)
BEGIN
    DECLARE v_bar_code VARCHAR(50);
    DECLARE v_material_name VARCHAR(100);
    DECLARE v_total_quantity DECIMAL(24,6) DEFAULT 0;
    DECLARE v_depot_count INT DEFAULT 0;
    DECLARE v_bill_count INT DEFAULT 0;
    
    -- 获取商品基础信息
    SELECT me.bar_code, m.name 
    INTO v_bar_code, v_material_name
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
        AND IFNULL(me.delete_flag,'0') != '1' 
        AND me.default_flag = 1
    WHERE m.id = p_material_id 
        AND IFNULL(m.delete_flag,'0') != '1';
    
    -- 计算当日出库汇总数据
    SELECT 
        IFNULL(SUM(di.basic_number), 0),
        COUNT(DISTINCT di.depot_id),
        COUNT(DISTINCT dh.id)
    INTO v_total_quantity, v_depot_count, v_bill_count
    FROM jsh_depot_item di
    LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
    WHERE di.material_id = p_material_id
        AND dh.type = '出库'
        AND IFNULL(di.delete_flag,'0') != '1'
        AND IFNULL(dh.delete_flag,'0') != '1'
        AND dh.status = '1'
        AND DATE(dh.oper_time) = p_target_date
        AND IFNULL(dh.tenant_id, 0) = IFNULL(p_tenant_id, 0);
    
    -- 插入或更新汇总数据
    INSERT INTO jsh_daily_out_summary (
        material_id, bar_code, material_name, out_date, 
        total_out_quantity, depot_count, bill_count, tenant_id
    ) VALUES (
        p_material_id, v_bar_code, v_material_name, p_target_date,
        v_total_quantity, v_depot_count, v_bill_count, p_tenant_id
    ) ON DUPLICATE KEY UPDATE
        bar_code = v_bar_code,
        material_name = v_material_name,
        total_out_quantity = v_total_quantity,
        depot_count = v_depot_count,
        bill_count = v_bill_count,
        last_update_time = CURRENT_TIMESTAMP;
        
END$$
DELIMITER ;

-- ----------------------------
-- 5. 存储过程：批量更新期间汇总
-- ----------------------------
DROP PROCEDURE IF EXISTS `refresh_material_period_summary`;
DELIMITER $$
CREATE PROCEDURE `refresh_material_period_summary`(
    IN p_tenant_id BIGINT
)
BEGIN
    -- 刷新商品期间汇总表
    INSERT INTO jsh_material_period_summary (
        material_id, bar_code, material_name, material_model, material_unit,
        current_period_stock, previous_period_stock, 
        current_period_out, previous_period_out,
        current_period_in, previous_period_in, tenant_id
    )
    SELECT 
        m.id as material_id,
        me.bar_code,
        m.name as material_name,
        m.model as material_model,
        m.unit as material_unit,
        IFNULL(mcs.current_number, 0) as current_period_stock,
        IFNULL(mis.number, 0) as previous_period_stock,
        0 as current_period_out,  -- 需要通过其他方式计算
        0 as previous_period_out, -- 需要通过其他方式计算
        0 as current_period_in,   -- 需要通过其他方式计算
        0 as previous_period_in,  -- 需要通过其他方式计算
        m.tenant_id
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
        AND IFNULL(me.delete_flag,'0') != '1' 
        AND me.default_flag = 1
    LEFT JOIN jsh_material_current_stock mcs ON mcs.material_id = m.id
        AND IFNULL(mcs.delete_flag,'0') != '1'
    LEFT JOIN jsh_material_initial_stock mis ON mis.material_id = m.id
        AND IFNULL(mis.delete_flag,'0') != '1'
    WHERE IFNULL(m.delete_flag,'0') != '1'
        AND IFNULL(m.tenant_id, 0) = IFNULL(p_tenant_id, 0)
    ON DUPLICATE KEY UPDATE
        bar_code = VALUES(bar_code),
        material_name = VALUES(material_name),
        material_model = VALUES(material_model),
        material_unit = VALUES(material_unit),
        current_period_stock = VALUES(current_period_stock),
        previous_period_stock = VALUES(previous_period_stock),
        last_calculation_time = CURRENT_TIMESTAMP;
        
END$$
DELIMITER ;

-- ----------------------------
-- 6. 触发器：自动更新每日汇总
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_depot_item_after_insert`;
DELIMITER $$
CREATE TRIGGER `trg_depot_item_after_insert`
AFTER INSERT ON `jsh_depot_item`
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(50);
    DECLARE v_status VARCHAR(1);
    DECLARE v_oper_time DATETIME;
    DECLARE v_tenant_id BIGINT;
    
    -- 获取单据信息
    SELECT dh.type, dh.status, dh.oper_time, dh.tenant_id
    INTO v_type, v_status, v_oper_time, v_tenant_id
    FROM jsh_depot_head dh 
    WHERE dh.id = NEW.header_id;
    
    -- 如果是已审核的出库单，更新汇总
    IF v_type = '出库' AND v_status = '1' AND v_oper_time IS NOT NULL THEN
        CALL update_daily_out_summary(NEW.material_id, DATE(v_oper_time), v_tenant_id);
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_depot_item_after_update`;
DELIMITER $$
CREATE TRIGGER `trg_depot_item_after_update`
AFTER UPDATE ON `jsh_depot_item`
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(50);
    DECLARE v_status VARCHAR(1);
    DECLARE v_oper_time DATETIME;
    DECLARE v_tenant_id BIGINT;
    
    -- 获取单据信息
    SELECT dh.type, dh.status, dh.oper_time, dh.tenant_id
    INTO v_type, v_status, v_oper_time, v_tenant_id
    FROM jsh_depot_head dh 
    WHERE dh.id = NEW.header_id;
    
    -- 如果是已审核的出库单，更新汇总
    IF v_type = '出库' AND v_status = '1' AND v_oper_time IS NOT NULL THEN
        CALL update_daily_out_summary(NEW.material_id, DATE(v_oper_time), v_tenant_id);
        -- 如果商品ID发生变化，也需要更新旧商品的汇总
        IF OLD.material_id != NEW.material_id THEN
            CALL update_daily_out_summary(OLD.material_id, DATE(v_oper_time), v_tenant_id);
        END IF;
    END IF;
END$$
DELIMITER ;

-- ----------------------------
-- 7. 初始化历史数据（可选）
-- ----------------------------
-- 注意：这个脚本会处理大量数据，建议在低峰期执行

-- 初始化最近30天的每日出库汇总数据
INSERT INTO jsh_daily_out_summary (
    material_id, bar_code, material_name, out_date, 
    total_out_quantity, depot_count, bill_count, tenant_id
)
SELECT 
    di.material_id,
    me.bar_code,
    m.name as material_name,
    DATE(dh.oper_time) as out_date,
    SUM(IFNULL(di.basic_number, 0)) as total_out_quantity,
    COUNT(DISTINCT di.depot_id) as depot_count,
    COUNT(DISTINCT dh.id) as bill_count,
    dh.tenant_id
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
LEFT JOIN jsh_material m ON m.id = di.material_id
LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
    AND IFNULL(me.delete_flag,'0') != '1' 
    AND me.default_flag = 1
WHERE dh.type = '出库'
    AND IFNULL(di.delete_flag,'0') != '1'
    AND IFNULL(dh.delete_flag,'0') != '1'
    AND IFNULL(m.delete_flag,'0') != '1'
    AND dh.status = '1'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY di.material_id, DATE(dh.oper_time), dh.tenant_id
ON DUPLICATE KEY UPDATE
    bar_code = VALUES(bar_code),
    material_name = VALUES(material_name),
    total_out_quantity = VALUES(total_out_quantity),
    depot_count = VALUES(depot_count),
    bill_count = VALUES(bill_count),
    last_update_time = CURRENT_TIMESTAMP;

-- ----------------------------
-- 8. 定期维护脚本
-- ----------------------------

-- 清理6个月前的每日汇总数据（可配置为定时任务）
-- DELETE FROM jsh_daily_out_summary 
-- WHERE out_date < DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- ----------------------------
-- 执行完成提示
-- ----------------------------
SELECT '性能优化SQL脚本执行完成！' as message,
       '请注意：' as note1,
       '1. 初始化数据脚本可能需要较长时间' as note2,
       '2. 建议在业务低峰期执行' as note3,
       '3. 执行前请备份重要数据' as note4; 