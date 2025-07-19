-- ===========================
-- 库存计算快速修复脚本
-- 立即修复首页表格中的库存计算问题
-- ===========================

-- 1. 首先执行修复后的存储过程创建
-- 注意：如果存储过程已存在，会先删除再创建

DELIMITER $$

DROP PROCEDURE IF EXISTS refresh_material_period_summary_correct$$

CREATE PROCEDURE refresh_material_period_summary_correct(IN tenant_id BIGINT)
BEGIN
    DECLARE current_year INT;
    DECLARE current_month INT;
    DECLARE current_period_start DATE;
    DECLARE current_period_end DATE;
    DECLARE previous_period_start DATE;
    DECLARE previous_period_end DATE;
    
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    
    -- 设置当前年月
    SET current_year = YEAR(CURDATE());
    SET current_month = MONTH(CURDATE());
    
    -- 根据当前月份确定期间范围（本期取决于当前日期）
    IF current_month >= 2 AND current_month <= 7 THEN
        -- 当前在2-7月，本期就是2-7月，上期是上一个8-1月
        SET current_period_start = STR_TO_DATE(CONCAT(current_year, '-02-01'), '%Y-%m-%d');
        SET current_period_end = STR_TO_DATE(CONCAT(current_year, '-07-31'), '%Y-%m-%d');
        SET previous_period_start = STR_TO_DATE(CONCAT(current_year - 1, '-08-01'), '%Y-%m-%d');
        SET previous_period_end = STR_TO_DATE(CONCAT(current_year, '-01-31'), '%Y-%m-%d');
    ELSE
        -- 当前在8-1月，本期就是8-1月，上期是上一个2-7月
        IF current_month >= 8 THEN
            -- 8-12月
            SET current_period_start = STR_TO_DATE(CONCAT(current_year, '-08-01'), '%Y-%m-%d');
            SET current_period_end = STR_TO_DATE(CONCAT(current_year + 1, '-01-31'), '%Y-%m-%d');
            SET previous_period_start = STR_TO_DATE(CONCAT(current_year, '-02-01'), '%Y-%m-%d');
            SET previous_period_end = STR_TO_DATE(CONCAT(current_year, '-07-31'), '%Y-%m-%d');
        ELSE
            -- 1月
            SET current_period_start = STR_TO_DATE(CONCAT(current_year - 1, '-08-01'), '%Y-%m-%d');
            SET current_period_end = STR_TO_DATE(CONCAT(current_year, '-01-31'), '%Y-%m-%d');
            SET previous_period_start = STR_TO_DATE(CONCAT(current_year - 1, '-02-01'), '%Y-%m-%d');
            SET previous_period_end = STR_TO_DATE(CONCAT(current_year - 1, '-07-31'), '%Y-%m-%d');
        END IF;
    END IF;

    -- 清空现有数据
    IF tenant_id IS NULL THEN
        DELETE FROM jsh_material_period_summary WHERE delete_flag = '0';
    ELSE
        DELETE FROM jsh_material_period_summary WHERE tenant_id = tenant_id AND delete_flag = '0';
    END IF;

    -- 插入正确计算的数据
    INSERT INTO jsh_material_period_summary (
        material_id, bar_code, material_name, material_model, material_unit,
        current_period_stock, previous_period_stock,
        current_period_out, previous_period_out,
        current_period_in, previous_period_in,
        tenant_id, delete_flag, last_calculation_time
    )
    SELECT 
        m.id as material_id,
        me.bar_code,
        m.name as material_name,
        m.model as material_model,
        m.unit as material_unit,
        
        -- 本期结存：使用当前库存（确保是整数）
        COALESCE(cs.current_stock, 0) as current_period_stock,

        -- 上期结存：根据库存平衡公式反推
        GREATEST(0, COALESCE(cs.current_stock, 0) - COALESCE(ci.current_in, 0) + COALESCE(co.current_out, 0)) as previous_period_stock,

        -- 本期出库
        COALESCE(co.current_out, 0) as current_period_out,

        -- 上期出库
        COALESCE(po.previous_out, 0) as previous_period_out,

        -- 本期入库
        COALESCE(ci.current_in, 0) as current_period_in,

        -- 上期入库
        COALESCE(pi.previous_in, 0) as previous_period_in,
        
        m.tenant_id,
        '0' as delete_flag,
        NOW() as last_calculation_time
        
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
        AND me.default_flag = '1' 
        AND (tenant_id IS NULL OR me.tenant_id = tenant_id)
    
    -- 当前库存
    LEFT JOIN (
        SELECT 
            material_id, 
            tenant_id,
            SUM(IFNULL(current_number, 0)) as current_stock
        FROM jsh_material_current_stock 
        WHERE delete_flag = '0'
        GROUP BY material_id, tenant_id
    ) cs ON m.id = cs.material_id 
        AND (tenant_id IS NULL OR m.tenant_id = cs.tenant_id)
    
    -- 本期出库
    LEFT JOIN (
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as current_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= current_period_start
        AND DATE(dh.oper_time) <= current_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) co ON m.id = co.material_id AND (tenant_id IS NULL OR m.tenant_id = co.tenant_id)
    
    -- 上期出库
    LEFT JOIN (
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as previous_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= previous_period_start
        AND DATE(dh.oper_time) <= previous_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) po ON m.id = po.material_id AND (tenant_id IS NULL OR m.tenant_id = po.tenant_id)
    
    -- 本期入库
    LEFT JOIN (
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as current_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= current_period_start
        AND DATE(dh.oper_time) <= current_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) ci ON m.id = ci.material_id AND (tenant_id IS NULL OR m.tenant_id = ci.tenant_id)
    
    -- 上期入库
    LEFT JOIN (
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as previous_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= previous_period_start
        AND DATE(dh.oper_time) <= previous_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) pi ON m.id = pi.material_id AND (tenant_id IS NULL OR m.tenant_id = pi.tenant_id)
    
    WHERE m.delete_flag = '0'
    AND (tenant_id IS NULL OR m.tenant_id = tenant_id);

    -- 验证数据完整性：检查是否有非整数数据
    SET @decimal_count = (
        SELECT COUNT(*)
        FROM jsh_material_period_summary
        WHERE delete_flag = '0'
        AND (
            current_period_stock != ROUND(current_period_stock)
            OR previous_period_stock != ROUND(previous_period_stock)
            OR current_period_out != ROUND(current_period_out)
            OR previous_period_out != ROUND(previous_period_out)
            OR current_period_in != ROUND(current_period_in)
            OR previous_period_in != ROUND(previous_period_in)
        )
    );

    -- 如果发现非整数数据，回滚并报错
    IF @decimal_count > 0 THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = CONCAT('Found ', @decimal_count, ' non-integer stock data records');
    END IF;

    COMMIT;

    SELECT CONCAT('库存计算修复完成，处理时间：', NOW(), '，处理商品数：', ROW_COUNT()) as result;
END$$

DELIMITER ;

-- 2. 立即执行修复
CALL refresh_material_period_summary_correct(NULL);

-- 3. 快速验证结果
SELECT 
    '修复完成' as 状态,
    COUNT(*) as 商品总数,
    COUNT(CASE WHEN current_period_stock > 0 THEN 1 END) as 有库存商品,
    MAX(last_calculation_time) as 计算时间
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 4. 显示前10个商品的修复结果
SELECT 
    bar_code as 商品编码,
    material_name as 商品名称,
    current_period_stock as 本期结存,
    previous_period_stock as 上期结存,
    current_period_out as 本期出库,
    previous_period_out as 上期出库
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
ORDER BY current_period_stock DESC
LIMIT 10;

SELECT '库存计算修复完成！请刷新前端页面查看效果。' as 提示;
