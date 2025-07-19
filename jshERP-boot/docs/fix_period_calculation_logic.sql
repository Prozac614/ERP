-- 修复期间库存计算逻辑的SQL脚本
-- 问题：本期结存、上期结存、出库数据计算逻辑错误

-- 1. 创建修复后的存储过程
DELIMITER $$

DROP PROCEDURE IF EXISTS refresh_material_period_summary_correct$$

CREATE PROCEDURE refresh_material_period_summary_correct(IN tenant_id BIGINT)
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

    -- 动态计算期间范围
    SET @current_month = MONTH(NOW());
    SET @current_year = YEAR(NOW());
    
    -- 判断当前期间
    IF @current_month >= 2 AND @current_month <= 7 THEN
        -- 当前在第一期（2-7月）
        SET @current_period_start = CONCAT(@current_year, '-02-01 00:00:00');
        SET @current_period_end = CONCAT(@current_year, '-07-31 23:59:59');
        SET @previous_period_start = CONCAT(@current_year - 1, '-08-01 00:00:00');
        SET @previous_period_end = CONCAT(@current_year, '-01-31 23:59:59');
    ELSE
        -- 当前在第二期（8-1月）
        IF @current_month >= 8 THEN
            -- 8-12月
            SET @current_period_start = CONCAT(@current_year, '-08-01 00:00:00');
            SET @current_period_end = CONCAT(@current_year + 1, '-01-31 23:59:59');
            SET @previous_period_start = CONCAT(@current_year, '-02-01 00:00:00');
            SET @previous_period_end = CONCAT(@current_year, '-07-31 23:59:59');
        ELSE
            -- 1月
            SET @current_period_start = CONCAT(@current_year - 1, '-08-01 00:00:00');
            SET @current_period_end = CONCAT(@current_year, '-01-31 23:59:59');
            SET @previous_period_start = CONCAT(@current_year - 1, '-02-01 00:00:00');
            SET @previous_period_end = CONCAT(@current_year - 1, '-07-31 23:59:59');
        END IF;
    END IF;

    -- 插入正确计算的数据
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
        
        -- 本期结存 = 期初库存 + 本期入库 - 本期出库
        ROUND(GREATEST(0, 
            COALESCE(init_stock.initial_stock, 0) + 
            COALESCE(ci.current_in, 0) - 
            COALESCE(co.current_out, 0)
        )) as current_period_stock,
        
        -- 上期结存 = 上期期初库存 + 上期入库 - 上期出库
        ROUND(GREATEST(0,
            COALESCE(prev_init_stock.previous_initial_stock, 0) + 
            COALESCE(pi.previous_in, 0) - 
            COALESCE(po.previous_out, 0)
        )) as previous_period_stock,
        
        -- 本期出库
        ROUND(COALESCE(co.current_out, 0)) as current_period_out,
        
        -- 上期出库
        ROUND(COALESCE(po.previous_out, 0)) as previous_period_out,
        
        -- 本期入库
        ROUND(COALESCE(ci.current_in, 0)) as current_period_in,
        
        -- 上期入库
        ROUND(COALESCE(pi.previous_in, 0)) as previous_period_in,
        
        m.tenant_id,
        '0' as delete_flag
        
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
        AND me.default_flag = '1' 
        AND (tenant_id IS NULL OR me.tenant_id = tenant_id)
    
    -- 获取期初库存（本期开始时的库存）
    LEFT JOIN (
        SELECT 
            material_id, 
            tenant_id,
            SUM(IFNULL(current_number, 0)) as initial_stock
        FROM jsh_material_current_stock 
        WHERE delete_flag = '0'
        GROUP BY material_id, tenant_id
    ) init_stock ON m.id = init_stock.material_id 
        AND (tenant_id IS NULL OR m.tenant_id = init_stock.tenant_id)
    
    -- 获取上期期初库存（估算：当前库存 + 本期出库 - 本期入库 - 上期入库 + 上期出库）
    LEFT JOIN (
        SELECT 
            m2.id as material_id,
            m2.tenant_id,
            -- 这里使用当前库存反推，实际项目中应该有历史库存表
            GREATEST(0, 
                COALESCE(cs.current_stock, 0) + 
                COALESCE(co2.current_out, 0) - 
                COALESCE(ci2.current_in, 0)
            ) as previous_initial_stock
        FROM jsh_material m2
        LEFT JOIN (
            SELECT material_id, tenant_id, SUM(IFNULL(current_number, 0)) as current_stock
            FROM jsh_material_current_stock 
            WHERE delete_flag = '0'
            GROUP BY material_id, tenant_id
        ) cs ON m2.id = cs.material_id AND (tenant_id IS NULL OR m2.tenant_id = cs.tenant_id)
        LEFT JOIN (
            SELECT 
                di.material_id, 
                dh.tenant_id,
                SUM(IFNULL(di.basic_number, 0)) as current_out
            FROM jsh_depot_head dh
            INNER JOIN jsh_depot_item di ON dh.id = di.header_id
            WHERE dh.type = '出库' AND dh.status = '1' 
            AND dh.delete_flag = '0' AND di.delete_flag = '0'
            AND dh.oper_time >= @current_period_start
            AND dh.oper_time <= @current_period_end
            GROUP BY di.material_id, dh.tenant_id
        ) co2 ON m2.id = co2.material_id AND (tenant_id IS NULL OR m2.tenant_id = co2.tenant_id)
        LEFT JOIN (
            SELECT 
                di.material_id, 
                dh.tenant_id,
                SUM(IFNULL(di.basic_number, 0)) as current_in
            FROM jsh_depot_head dh
            INNER JOIN jsh_depot_item di ON dh.id = di.header_id
            WHERE dh.type = '入库' AND dh.status = '1' 
            AND dh.delete_flag = '0' AND di.delete_flag = '0'
            AND dh.oper_time >= @current_period_start
            AND dh.oper_time <= @current_period_end
            GROUP BY di.material_id, dh.tenant_id
        ) ci2 ON m2.id = ci2.material_id AND (tenant_id IS NULL OR m2.tenant_id = ci2.tenant_id)
    ) prev_init_stock ON m.id = prev_init_stock.material_id 
        AND (tenant_id IS NULL OR m.tenant_id = prev_init_stock.tenant_id)
    
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
        AND dh.oper_time >= @current_period_start
        AND dh.oper_time <= @current_period_end
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
        AND dh.oper_time >= @previous_period_start
        AND dh.oper_time <= @previous_period_end
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
        AND dh.oper_time >= @current_period_start
        AND dh.oper_time <= @current_period_end
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
        AND dh.oper_time >= @previous_period_start
        AND dh.oper_time <= @previous_period_end
        GROUP BY di.material_id, dh.tenant_id
    ) pi ON m.id = pi.material_id AND (tenant_id IS NULL OR m.tenant_id = pi.tenant_id)
    
    WHERE m.delete_flag = '0'
    AND (tenant_id IS NULL OR m.tenant_id = tenant_id);

    COMMIT;
END$$

DELIMITER ;

-- 2. 测试期间判断逻辑
SELECT 
    NOW() as current_time,
    MONTH(NOW()) as current_month,
    CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN '第一期(2-7月)'
        WHEN MONTH(NOW()) >= 8 THEN '第二期(8-12月)'
        WHEN MONTH(NOW()) = 1 THEN '第二期(1月)'
        ELSE '未知期间'
    END as current_period,
    CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN 
            CONCAT('本期: ', YEAR(NOW()), '-02-01 到 ', YEAR(NOW()), '-07-31')
        WHEN MONTH(NOW()) >= 8 THEN 
            CONCAT('本期: ', YEAR(NOW()), '-08-01 到 ', YEAR(NOW()) + 1, '-01-31')
        WHEN MONTH(NOW()) = 1 THEN 
            CONCAT('本期: ', YEAR(NOW()) - 1, '-08-01 到 ', YEAR(NOW()), '-01-31')
    END as current_period_range;

-- 3. 执行修复
CALL refresh_material_period_summary_correct(NULL);

-- 4. 验证修复结果
SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock as 本期结存,
    previous_period_stock as 上期结存,
    current_period_out as 本期出库,
    previous_period_out as 上期出库,
    current_period_in as 本期入库,
    previous_period_in as 上期入库,
    -- 验证库存平衡：上期结存 + 本期入库 - 本期出库 应该等于本期结存
    (previous_period_stock + current_period_in - current_period_out) as 计算的本期结存,
    (current_period_stock - (previous_period_stock + current_period_in - current_period_out)) as 差异
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
ORDER BY material_id
LIMIT 10;
