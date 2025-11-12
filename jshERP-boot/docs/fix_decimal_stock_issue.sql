-- 修复库存小数点问题的SQL脚本
-- 问题：上期结存显示小数，应该显示整数

-- 1. 首先检查当前数据中的小数问题
SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    previous_period_stock,
    ROUND(previous_period_stock) as previous_period_stock_rounded
FROM jsh_material_period_summary 
WHERE previous_period_stock != ROUND(previous_period_stock)
LIMIT 10;

-- 2. 修复现有数据：将所有小数库存四舍五入为整数
UPDATE jsh_material_period_summary 
SET 
    current_period_stock = ROUND(current_period_stock),
    previous_period_stock = ROUND(previous_period_stock),
    current_period_out = ROUND(current_period_out),
    previous_period_out = ROUND(previous_period_out),
    current_period_in = ROUND(current_period_in),
    previous_period_in = ROUND(previous_period_in)
WHERE 
    current_period_stock != ROUND(current_period_stock)
    OR previous_period_stock != ROUND(previous_period_stock)
    OR current_period_out != ROUND(current_period_out)
    OR previous_period_out != ROUND(previous_period_out)
    OR current_period_in != ROUND(current_period_in)
    OR previous_period_in != ROUND(previous_period_in);

-- 3. 创建修复后的存储过程
DELIMITER $$

DROP PROCEDURE IF EXISTS refresh_material_period_summary_fixed$$

CREATE PROCEDURE refresh_material_period_summary_fixed(IN tenant_id BIGINT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 清空现有数据
    IF tenant_id IS NULL THEN
        DELETE FROM jsh_material_period_summary WHERE delete_flag = '0';
    ELSE
        DELETE FROM jsh_material_period_summary WHERE tenant_id = tenant_id AND delete_flag = '0';
    END IF;

    -- 定义期间范围（根据当前月份判断）
    SET @current_period_start = CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN CONCAT(YEAR(NOW()), '-02-01')
        ELSE CONCAT(YEAR(NOW()) - 1, '-08-01')
    END;
    
    SET @current_period_end = CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN CONCAT(YEAR(NOW()), '-07-31')
        ELSE CONCAT(YEAR(NOW()), '-01-31')
    END;
    
    SET @previous_period_start = CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN CONCAT(YEAR(NOW()) - 1, '-08-01')
        ELSE CONCAT(YEAR(NOW()) - 1, '-02-01')
    END;
    
    SET @previous_period_end = CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN CONCAT(YEAR(NOW()) - 1, '-01-31')
        ELSE CONCAT(YEAR(NOW()) - 1, '-07-31')
    END;

    -- 插入修复后的数据（所有数值都使用ROUND函数确保为整数）
    INSERT INTO jsh_material_period_summary (
        material_id, bar_code, material_name, material_model, material_unit,
        current_period_stock, previous_period_stock,
        current_period_out, previous_period_out,
        current_period_in, previous_period_in,
        tenant_id, delete_flag
    )
    SELECT 
        m.id as material_id,
        me.bar_code,
        m.name as material_name,
        m.model as material_model,
        m.unit as material_unit,
        -- 本期库存（当前库存）- 使用ROUND确保整数
        ROUND(COALESCE(cs.current_stock, 0)) as current_period_stock,
        -- 上期库存 - 使用业务逻辑计算，而不是简单的90%
        ROUND(COALESCE(ps.previous_stock, 0)) as previous_period_stock,
        -- 本期出库 - 使用ROUND确保整数
        ROUND(COALESCE(co.current_out, 0)) as current_period_out,
        -- 上期出库 - 使用ROUND确保整数
        ROUND(COALESCE(po.previous_out, 0)) as previous_period_out,
        -- 本期入库 - 使用ROUND确保整数
        ROUND(COALESCE(ci.current_in, 0)) as current_period_in,
        -- 上期入库 - 使用ROUND确保整数
        ROUND(COALESCE(pi.previous_in, 0)) as previous_period_in,
        m.tenant_id,
        '0' as delete_flag
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
        AND me.default_flag = '1' 
        AND (tenant_id IS NULL OR me.tenant_id = tenant_id)
    LEFT JOIN (
        -- 本期库存（当前库存）
        SELECT material_id, tenant_id, SUM(IFNULL(current_number, 0)) as current_stock
        FROM jsh_material_current_stock 
        WHERE delete_flag = '0'
        GROUP BY material_id, tenant_id
    ) cs ON m.id = cs.material_id AND (tenant_id IS NULL OR m.tenant_id = tenant_id)
    LEFT JOIN (
        -- 上期库存 - 使用更合理的计算方法：当前库存 + 本期出库 - 本期入库
        SELECT 
            m2.id as material_id, 
            m2.tenant_id,
            GREATEST(0, 
                IFNULL(cs2.current_stock, 0) + 
                IFNULL(co2.current_out, 0) - 
                IFNULL(ci2.current_in, 0)
            ) as previous_stock
        FROM jsh_material m2
        LEFT JOIN (
            SELECT material_id, tenant_id, SUM(IFNULL(current_number, 0)) as current_stock
            FROM jsh_material_current_stock 
            WHERE delete_flag = '0'
            GROUP BY material_id, tenant_id
        ) cs2 ON m2.id = cs2.material_id
        LEFT JOIN (
            SELECT 
                di.material_id, 
                dh.tenant_id,
                SUM(IFNULL(di.basic_number, 0)) as current_out
            FROM jsh_depot_head dh
            INNER JOIN jsh_depot_item di ON dh.id = di.header_id
            WHERE dh.type = '出库' AND dh.status = '1' 
            AND dh.delete_flag = '0' AND di.delete_flag = '0'
            AND DATE(dh.oper_time) >= @current_period_start
            AND DATE(dh.oper_time) <= @current_period_end
            GROUP BY di.material_id, dh.tenant_id
        ) co2 ON m2.id = co2.material_id AND m2.tenant_id = co2.tenant_id
        LEFT JOIN (
            SELECT 
                di.material_id, 
                dh.tenant_id,
                SUM(IFNULL(di.basic_number, 0)) as current_in
            FROM jsh_depot_head dh
            INNER JOIN jsh_depot_item di ON dh.id = di.header_id
            WHERE dh.type = '入库' AND dh.status = '1' 
            AND dh.delete_flag = '0' AND di.delete_flag = '0'
            AND DATE(dh.oper_time) >= @current_period_start
            AND DATE(dh.oper_time) <= @current_period_end
            GROUP BY di.material_id, dh.tenant_id
        ) ci2 ON m2.id = ci2.material_id AND m2.tenant_id = ci2.tenant_id
    ) ps ON m.id = ps.material_id AND (tenant_id IS NULL OR m.tenant_id = ps.tenant_id)
    LEFT JOIN (
        -- 本期出库
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as current_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= @current_period_start
        AND DATE(dh.oper_time) <= @current_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) co ON m.id = co.material_id AND (tenant_id IS NULL OR m.tenant_id = co.tenant_id)
    LEFT JOIN (
        -- 上期出库
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as previous_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= @previous_period_start
        AND DATE(dh.oper_time) <= @previous_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) po ON m.id = po.material_id AND (tenant_id IS NULL OR m.tenant_id = po.tenant_id)
    LEFT JOIN (
        -- 本期入库
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as current_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= @current_period_start
        AND DATE(dh.oper_time) <= @current_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) ci ON m.id = ci.material_id AND (tenant_id IS NULL OR m.tenant_id = ci.tenant_id)
    LEFT JOIN (
        -- 上期入库
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(IFNULL(di.basic_number, 0)) as previous_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND DATE(dh.oper_time) >= @previous_period_start
        AND DATE(dh.oper_time) <= @previous_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) pi ON m.id = pi.material_id AND (tenant_id IS NULL OR m.tenant_id = pi.tenant_id)
    WHERE m.delete_flag = '0'
    AND (tenant_id IS NULL OR m.tenant_id = tenant_id);

    COMMIT;
END$$

DELIMITER ;

-- 4. 执行修复后的存储过程
CALL refresh_material_period_summary_fixed(NULL);

-- 5. 验证修复结果
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as decimal_records,
    MIN(previous_period_stock) as min_previous_stock,
    MAX(previous_period_stock) as max_previous_stock,
    AVG(previous_period_stock) as avg_previous_stock
FROM jsh_material_period_summary;

-- 6. 显示修复后的示例数据
SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    previous_period_stock,
    current_period_out,
    previous_period_out
FROM jsh_material_period_summary 
ORDER BY material_id
LIMIT 10;
