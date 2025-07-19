-- ===========================
-- 正确的库存计算修复脚本
-- 直接从数据库正向查询各个时期的实际数据
-- ===========================

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

    -- 插入正确的数据：直接从数据库查询各个时期的实际数据
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
        
        -- 本期结存：当前的库存（从库存表直接查询）
        COALESCE(cs.current_stock, 0) as current_period_stock,
        
        -- 上期结存：通过库存变动计算上期结束时的库存
        -- 方法：当前库存 - 本期净变动 = 上期结存
        GREATEST(0, 
            COALESCE(cs.current_stock, 0) - 
            COALESCE(ci.current_in, 0) + 
            COALESCE(co.current_out, 0)
        ) as previous_period_stock,
        
        -- 本期出库：本期时间范围内的出库总量（直接查询）
        COALESCE(co.current_out, 0) as current_period_out,
        
        -- 上期出库：上期时间范围内的出库总量（直接查询）
        COALESCE(po.previous_out, 0) as previous_period_out,
        
        -- 本期入库：本期时间范围内的入库总量（直接查询）
        COALESCE(ci.current_in, 0) as current_period_in,
        
        -- 上期入库：上期时间范围内的入库总量（直接查询）
        COALESCE(pi.previous_in, 0) as previous_period_in,
        
        m.tenant_id,
        '0' as delete_flag,
        NOW() as last_calculation_time
        
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
        AND me.default_flag = '1' 
        AND (tenant_id IS NULL OR me.tenant_id = tenant_id)
    
    -- 本期结存：从当前库存表查询
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
    
    -- 本期出库：查询本期时间范围内的出库记录
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
    
    -- 上期出库：查询上期时间范围内的出库记录
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
    
    -- 本期入库：查询本期时间范围内的入库记录
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
    
    -- 上期入库：查询上期时间范围内的入库记录
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

    COMMIT;
    
    SELECT 'Stock calculation completed successfully' as result;
END$$

DELIMITER ;

-- 立即执行修复
CALL refresh_material_period_summary_correct(NULL);

-- 验证结果
SELECT 
    'Fix completed' as status,
    COUNT(*) as total_materials,
    COUNT(CASE WHEN current_period_stock > 0 THEN 1 END) as materials_with_stock,
    MAX(last_calculation_time) as calculation_time
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 显示前10个商品的修复结果
SELECT 
    bar_code,
    material_name,
    current_period_stock as current_stock,
    previous_period_stock as previous_stock,
    current_period_out as current_out,
    previous_period_out as previous_out,
    current_period_in as current_in,
    previous_period_in as previous_in
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
ORDER BY current_period_stock DESC
LIMIT 10;
