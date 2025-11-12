-- ===========================
-- 修复期间库存计算逻辑的SQL脚本（完全修正版）
-- 问题：本期结存、上期结存、出库数据计算逻辑错误
-- 
-- 业务规则：
-- 1. 期间定义：本期（2月1日-7月31日），上期（8月1日-次年1月31日）
-- 2. 库存平衡公式：本期结存 = 上期结存 + 本期入库 - 本期出库
-- 3. 所有库存数据四舍五入为整数
-- ===========================

-- 1. 删除旧的存储过程
DELIMITER $$

DROP PROCEDURE IF EXISTS refresh_material_period_summary_correct$$
DROP PROCEDURE IF EXISTS update_material_period_summary$$

-- 2. 创建修复后的存储过程
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
        -- 公式：上期结存 = 本期结存 - 本期入库 + 本期出库
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

    -- 返回执行结果
    SELECT CONCAT(
        'Material period summary updated successfully. ',
        'Current period: ', current_period_start, ' to ', current_period_end, ', ',
        'Previous period: ', previous_period_start, ' to ', previous_period_end
    ) as result;
END$$

DELIMITER ;

-- 3. 测试期间判断逻辑
SELECT
    '=== 期间判断测试 ===' as info,
    NOW() as current_datetime,
    MONTH(NOW()) as current_month,
    CASE
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN '本期(2-7月)'
        WHEN MONTH(NOW()) >= 8 THEN '本期(8-12月)'
        WHEN MONTH(NOW()) = 1 THEN '本期(1月，属于8-1月期间)'
        ELSE '未知期间'
    END as period_status,
    CASE
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN
            CONCAT('本期: ', YEAR(NOW()), '-02-01 到 ', YEAR(NOW()), '-07-31')
        WHEN MONTH(NOW()) >= 8 THEN
            CONCAT('本期: ', YEAR(NOW()), '-08-01 到 ', YEAR(NOW()) + 1, '-01-31')
        WHEN MONTH(NOW()) = 1 THEN
            CONCAT('本期: ', YEAR(NOW()) - 1, '-08-01 到 ', YEAR(NOW()), '-01-31')
    END as current_period_range;

-- 4. 执行修复
CALL refresh_material_period_summary_correct(NULL);

-- 5. 验证修复结果
SELECT
    '=== 修复结果验证 ===' as info;

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
    (current_period_stock - (previous_period_stock + current_period_in - current_period_out)) as 差异,
    CASE
        WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1
        THEN '✓ 平衡'
        ELSE '✗ 不平衡'
    END as 平衡状态
FROM jsh_material_period_summary
WHERE delete_flag = '0'
ORDER BY ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) DESC
LIMIT 20;

-- 6. 统计修复效果
SELECT
    '=== 修复效果统计 ===' as info;

SELECT
    COUNT(*) as 总商品数,
    COUNT(CASE WHEN current_period_stock > 0 THEN 1 END) as 有库存商品数,
    COUNT(CASE WHEN current_period_out > 0 THEN 1 END) as 本期有出库商品数,
    COUNT(CASE WHEN previous_period_out > 0 THEN 1 END) as 上期有出库商品数,
    COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) as 库存平衡商品数,
    ROUND(COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) * 100.0 / COUNT(*), 2) as 平衡率百分比
FROM jsh_material_period_summary
WHERE delete_flag = '0';

-- 7. 检查小数问题是否解决
SELECT
    '=== 小数问题检查 ===' as info;

SELECT
    COUNT(*) as 总记录数,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as 本期结存有小数,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as 上期结存有小数,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as 本期出库有小数,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as 上期出库有小数,
    CASE
        WHEN COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock)
                         OR previous_period_stock != ROUND(previous_period_stock)
                         OR current_period_out != ROUND(current_period_out)
                         OR previous_period_out != ROUND(previous_period_out) THEN 1 END) = 0
        THEN '✓ 小数问题已解决'
        ELSE '✗ 仍有小数问题'
    END as 小数问题状态
FROM jsh_material_period_summary
WHERE delete_flag = '0';
