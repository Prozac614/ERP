-- =========================================
-- 专门解决汇总表更新问题的触发器安装脚本
-- 目标：修复 jsh_daily_out_summary 表不更新的问题
-- 创建时间：2025-01-24
-- =========================================

SELECT '开始安装触发器和存储过程...' as message;

-- ----------------------------
-- 1. 检查并确认存储过程存在
-- ----------------------------
SELECT '=== 检查存储过程是否存在 ===' as step;

-- 检查存储过程
SHOW PROCEDURE STATUS WHERE Name = 'update_daily_out_summary';

-- ----------------------------
-- 2. 创建/更新存储过程（确保最新版本）
-- ----------------------------
SELECT '=== 创建/更新存储过程 ===' as step;

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
-- 3. 安装触发器
-- ----------------------------
SELECT '=== 安装触发器 ===' as step;

-- 删除现有触发器（如果存在）
DROP TRIGGER IF EXISTS `trg_depot_item_after_insert`;
DROP TRIGGER IF EXISTS `trg_depot_item_after_update`;

-- 创建插入触发器
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

-- 创建更新触发器
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
-- 4. 验证安装结果
-- ----------------------------
SELECT '=== 验证安装结果 ===' as step;

-- 验证触发器是否创建成功
SHOW TRIGGERS LIKE '%depot_item%';

-- 验证存储过程是否存在
SHOW PROCEDURE STATUS WHERE Name = 'update_daily_out_summary';

-- ----------------------------
-- 5. 补充历史数据（最近30天）
-- ----------------------------
SELECT '=== 开始补充历史汇总数据 ===' as step;

-- 这将补充从6月27日到现在缺失的汇总数据
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
    AND dh.status = '1'  -- 只处理已审核的单据
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
-- 6. 验证历史数据补充结果
-- ----------------------------
SELECT '=== 验证历史数据补充结果 ===' as step;

-- 检查汇总表最新数据
SELECT 
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date,
    COUNT(DISTINCT out_date) as date_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- 检查最近7天的汇总数据
SELECT 
    out_date,
    COUNT(DISTINCT material_id) as material_count,
    SUM(total_out_quantity) as daily_total_out,
    SUM(bill_count) as daily_bill_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0'
    AND out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY out_date
ORDER BY out_date DESC;

-- ----------------------------
-- 7. 测试触发器工作
-- ----------------------------
SELECT '=== 准备测试触发器 ===' as step;

-- 提示用户如何测试
SELECT 
    '触发器安装完成！' as message,
    '测试方法：' as test_method,
    '1. 创建新的出库单据并审核' as step1,
    '2. 或者更新现有出库单据' as step2,
    '3. 检查 jsh_daily_out_summary 表是否实时更新' as step3,
    '4. 前端首页表格应该显示最新数据' as step4;

-- ----------------------------
-- 8. 最终状态总结
-- ----------------------------
SELECT '=== 安装完成总结 ===' as step;

SELECT 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS 
            WHERE TRIGGER_NAME = 'trg_depot_item_after_insert'
        ) AND EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS 
            WHERE TRIGGER_NAME = 'trg_depot_item_after_update'  
        ) AND EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.ROUTINES 
            WHERE ROUTINE_NAME = 'update_daily_out_summary'
        )
        THEN '✅ 触发器和存储过程安装成功！汇总表将实时更新'
        ELSE '❌ 安装可能有问题，请检查错误信息'
    END as final_status;

SELECT '脚本执行完成。现在出入库操作将自动更新首页汇总数据。' as completion_message; 