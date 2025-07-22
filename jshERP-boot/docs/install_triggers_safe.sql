-- =========================================
-- 安全版本：无需SUPER权限的触发器安装脚本
-- 解决错误1419：You do not have the SUPER privilege
-- 创建时间：2025-01-24
-- =========================================

SELECT '开始安装触发器（安全版本，无需SUPER权限）...' as message;

-- ----------------------------
-- 方案说明
-- ----------------------------
SELECT '此版本将逻辑直接嵌入触发器，避免存储过程权限问题' as solution_info;

-- ----------------------------
-- 1. 检查现有触发器
-- ----------------------------
SELECT '=== 检查现有触发器 ===' as step;

SHOW TRIGGERS LIKE '%depot_item%';

-- ----------------------------
-- 2. 删除现有触发器（如果存在）
-- ----------------------------
SELECT '=== 清理现有触发器 ===' as step;

DROP TRIGGER IF EXISTS `trg_depot_item_after_insert`;
DROP TRIGGER IF EXISTS `trg_depot_item_after_update`;

-- ----------------------------
-- 3. 创建安全版本的插入触发器（逻辑内联）
-- ----------------------------
SELECT '=== 创建插入触发器（内联版本） ===' as step;

DELIMITER $$
CREATE TRIGGER `trg_depot_item_after_insert`
AFTER INSERT ON `jsh_depot_item`
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(50);
    DECLARE v_status VARCHAR(1);
    DECLARE v_oper_time DATETIME;
    DECLARE v_tenant_id BIGINT;
    
    -- 内联变量：用于汇总计算
    DECLARE v_bar_code VARCHAR(50);
    DECLARE v_material_name VARCHAR(100);
    DECLARE v_total_quantity DECIMAL(24,6) DEFAULT 0;
    DECLARE v_depot_count INT DEFAULT 0;
    DECLARE v_bill_count INT DEFAULT 0;
    DECLARE v_target_date DATE;
    
    -- 获取单据信息
    SELECT dh.type, dh.status, dh.oper_time, dh.tenant_id
    INTO v_type, v_status, v_oper_time, v_tenant_id
    FROM jsh_depot_head dh 
    WHERE dh.id = NEW.header_id;
    
    -- 只有已审核的出库单才处理
    IF v_type = '出库' AND v_status = '1' AND v_oper_time IS NOT NULL THEN
        
        SET v_target_date = DATE(v_oper_time);
        
        -- 获取商品基础信息
        SELECT me.bar_code, m.name 
        INTO v_bar_code, v_material_name
        FROM jsh_material m
        LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
            AND IFNULL(me.delete_flag,'0') != '1' 
            AND me.default_flag = 1
        WHERE m.id = NEW.material_id 
            AND IFNULL(m.delete_flag,'0') != '1';
        
        -- 计算当日出库汇总数据
        SELECT 
            IFNULL(SUM(di.basic_number), 0),
            COUNT(DISTINCT di.depot_id),
            COUNT(DISTINCT dh.id)
        INTO v_total_quantity, v_depot_count, v_bill_count
        FROM jsh_depot_item di
        LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
        WHERE di.material_id = NEW.material_id
            AND dh.type = '出库'
            AND IFNULL(di.delete_flag,'0') != '1'
            AND IFNULL(dh.delete_flag,'0') != '1'
            AND dh.status = '1'
            AND DATE(dh.oper_time) = v_target_date
            AND IFNULL(dh.tenant_id, 0) = IFNULL(v_tenant_id, 0);
        
        -- 插入或更新汇总数据
        INSERT INTO jsh_daily_out_summary (
            material_id, bar_code, material_name, out_date, 
            total_out_quantity, depot_count, bill_count, tenant_id
        ) VALUES (
            NEW.material_id, v_bar_code, v_material_name, v_target_date,
            v_total_quantity, v_depot_count, v_bill_count, v_tenant_id
        ) ON DUPLICATE KEY UPDATE
            bar_code = v_bar_code,
            material_name = v_material_name,
            total_out_quantity = v_total_quantity,
            depot_count = v_depot_count,
            bill_count = v_bill_count,
            last_update_time = CURRENT_TIMESTAMP;
            
    END IF;
END$$
DELIMITER ;

-- ----------------------------
-- 4. 创建安全版本的更新触发器（逻辑内联）
-- ----------------------------
SELECT '=== 创建更新触发器（内联版本） ===' as step;

DELIMITER $$
CREATE TRIGGER `trg_depot_item_after_update`
AFTER UPDATE ON `jsh_depot_item`
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(50);
    DECLARE v_status VARCHAR(1);
    DECLARE v_oper_time DATETIME;
    DECLARE v_tenant_id BIGINT;
    
    -- 内联变量：用于汇总计算
    DECLARE v_bar_code VARCHAR(50);
    DECLARE v_material_name VARCHAR(100);
    DECLARE v_total_quantity DECIMAL(24,6) DEFAULT 0;
    DECLARE v_depot_count INT DEFAULT 0;
    DECLARE v_bill_count INT DEFAULT 0;
    DECLARE v_target_date DATE;
    
    -- 获取单据信息
    SELECT dh.type, dh.status, dh.oper_time, dh.tenant_id
    INTO v_type, v_status, v_oper_time, v_tenant_id
    FROM jsh_depot_head dh 
    WHERE dh.id = NEW.header_id;
    
    -- 只有已审核的出库单才处理
    IF v_type = '出库' AND v_status = '1' AND v_oper_time IS NOT NULL THEN
        
        SET v_target_date = DATE(v_oper_time);
        
        -- 处理新商品ID的汇总更新
        -- 获取商品基础信息
        SELECT me.bar_code, m.name 
        INTO v_bar_code, v_material_name
        FROM jsh_material m
        LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
            AND IFNULL(me.delete_flag,'0') != '1' 
            AND me.default_flag = 1
        WHERE m.id = NEW.material_id 
            AND IFNULL(m.delete_flag,'0') != '1';
        
        -- 计算当日出库汇总数据（新商品）
        SELECT 
            IFNULL(SUM(di.basic_number), 0),
            COUNT(DISTINCT di.depot_id),
            COUNT(DISTINCT dh.id)
        INTO v_total_quantity, v_depot_count, v_bill_count
        FROM jsh_depot_item di
        LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
        WHERE di.material_id = NEW.material_id
            AND dh.type = '出库'
            AND IFNULL(di.delete_flag,'0') != '1'
            AND IFNULL(dh.delete_flag,'0') != '1'
            AND dh.status = '1'
            AND DATE(dh.oper_time) = v_target_date
            AND IFNULL(dh.tenant_id, 0) = IFNULL(v_tenant_id, 0);
        
        -- 更新新商品的汇总数据
        INSERT INTO jsh_daily_out_summary (
            material_id, bar_code, material_name, out_date, 
            total_out_quantity, depot_count, bill_count, tenant_id
        ) VALUES (
            NEW.material_id, v_bar_code, v_material_name, v_target_date,
            v_total_quantity, v_depot_count, v_bill_count, v_tenant_id
        ) ON DUPLICATE KEY UPDATE
            bar_code = v_bar_code,
            material_name = v_material_name,
            total_out_quantity = v_total_quantity,
            depot_count = v_depot_count,
            bill_count = v_bill_count,
            last_update_time = CURRENT_TIMESTAMP;
        
        -- 如果商品ID发生变化，更新旧商品的汇总
        IF OLD.material_id != NEW.material_id THEN
            
            -- 获取旧商品基础信息
            SELECT me.bar_code, m.name 
            INTO v_bar_code, v_material_name
            FROM jsh_material m
            LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
                AND IFNULL(me.delete_flag,'0') != '1' 
                AND me.default_flag = 1
            WHERE m.id = OLD.material_id 
                AND IFNULL(m.delete_flag,'0') != '1';
            
            -- 重新计算旧商品的汇总数据
            SELECT 
                IFNULL(SUM(di.basic_number), 0),
                COUNT(DISTINCT di.depot_id),
                COUNT(DISTINCT dh.id)
            INTO v_total_quantity, v_depot_count, v_bill_count
            FROM jsh_depot_item di
            LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
            WHERE di.material_id = OLD.material_id
                AND dh.type = '出库'
                AND IFNULL(di.delete_flag,'0') != '1'
                AND IFNULL(dh.delete_flag,'0') != '1'
                AND dh.status = '1'
                AND DATE(dh.oper_time) = v_target_date
                AND IFNULL(dh.tenant_id, 0) = IFNULL(v_tenant_id, 0);
            
            -- 更新旧商品的汇总数据
            INSERT INTO jsh_daily_out_summary (
                material_id, bar_code, material_name, out_date, 
                total_out_quantity, depot_count, bill_count, tenant_id
            ) VALUES (
                OLD.material_id, v_bar_code, v_material_name, v_target_date,
                v_total_quantity, v_depot_count, v_bill_count, v_tenant_id
            ) ON DUPLICATE KEY UPDATE
                bar_code = v_bar_code,
                material_name = v_material_name,
                total_out_quantity = v_total_quantity,
                depot_count = v_depot_count,
                bill_count = v_bill_count,
                last_update_time = CURRENT_TIMESTAMP;
                
        END IF;
        
    END IF;
END$$
DELIMITER ;

-- ----------------------------
-- 5. 验证触发器安装
-- ----------------------------
SELECT '=== 验证触发器安装结果 ===' as step;

SHOW TRIGGERS LIKE '%depot_item%';

-- ----------------------------
-- 6. 手动补充历史数据（无需存储过程）
-- ----------------------------
SELECT '=== 开始补充历史汇总数据 ===' as step;

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
-- 7. 验证数据更新结果
-- ----------------------------
SELECT '=== 验证数据更新结果 ===' as step;

SELECT 
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date,
    COUNT(DISTINCT out_date) as date_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- ----------------------------
-- 8. 最终状态检查
-- ----------------------------
SELECT '=== 最终安装状态 ===' as step;

SELECT 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS 
            WHERE TRIGGER_NAME = 'trg_depot_item_after_insert'
        ) AND EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS 
            WHERE TRIGGER_NAME = 'trg_depot_item_after_update'
        )
        THEN '✅ 触发器安装成功！汇总表将实时更新'
        ELSE '❌ 触发器安装失败，请检查错误信息'
    END as final_status;

SELECT 
    '✅ 安全版本脚本执行完成' as message,
    '✅ 无需SUPER权限' as advantage1,  
    '✅ 避免了存储过程权限问题' as advantage2,
    '✅ 触发器逻辑内联，更安全' as advantage3,
    '✅ 出入库操作将自动更新汇总数据' as result; 