-- 安全的计算逻辑修复脚本
-- 修复本期结存、上期结存的计算错误

-- 1. 备份现有数据（可选）
-- CREATE TABLE jsh_material_period_summary_backup AS SELECT * FROM jsh_material_period_summary WHERE delete_flag = '0';

-- 2. 测试期间判断逻辑
SELECT 
    '=== 当前期间判断 ===' as info;

SET @current_month = MONTH(NOW());
SET @current_year = YEAR(NOW());

SELECT 
    NOW() as current_datetime,
    @current_month as current_month,
    @current_year as current_year,
    CASE 
        WHEN @current_month >= 2 AND @current_month <= 7 THEN '第一期(2-7月)'
        WHEN @current_month >= 8 THEN '第二期(8-12月)'
        WHEN @current_month = 1 THEN '第二期(1月)'
        ELSE '未知期间'
    END as current_period;

-- 3. 计算正确的期间范围
SET @current_period_start = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN CONCAT(@current_year, '-02-01 00:00:00')
    WHEN @current_month >= 8 THEN CONCAT(@current_year, '-08-01 00:00:00')
    ELSE CONCAT(@current_year - 1, '-08-01 00:00:00')
END;

SET @current_period_end = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN CONCAT(@current_year, '-07-31 23:59:59')
    WHEN @current_month >= 8 THEN CONCAT(@current_year + 1, '-01-31 23:59:59')
    ELSE CONCAT(@current_year, '-01-31 23:59:59')
END;

SET @previous_period_start = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN CONCAT(@current_year - 1, '-08-01 00:00:00')
    WHEN @current_month >= 8 THEN CONCAT(@current_year, '-02-01 00:00:00')
    ELSE CONCAT(@current_year - 1, '-02-01 00:00:00')
END;

SET @previous_period_end = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN CONCAT(@current_year, '-01-31 23:59:59')
    WHEN @current_month >= 8 THEN CONCAT(@current_year, '-07-31 23:59:59')
    ELSE CONCAT(@current_year - 1, '-07-31 23:59:59')
END;

SELECT 
    '=== 期间范围 ===' as info,
    @current_period_start as current_period_start,
    @current_period_end as current_period_end,
    @previous_period_start as previous_period_start,
    @previous_period_end as previous_period_end;

-- 4. 创建临时表存储正确计算的数据
DROP TEMPORARY TABLE IF EXISTS temp_correct_calculation;

CREATE TEMPORARY TABLE temp_correct_calculation AS
SELECT 
    m.id as material_id,
    me.bar_code,
    m.name as material_name,
    m.model as material_model,
    m.unit as material_unit,
    
    -- 本期结存 = 当前库存（这里暂时使用当前库存，实际应该是期初+入库-出库）
    ROUND(COALESCE(cs.current_stock, 0)) as current_period_stock_new,
    
    -- 上期结存 = 当前库存 + 本期出库 - 本期入库（简化的反推公式）
    ROUND(GREATEST(0, 
        COALESCE(cs.current_stock, 0) + 
        COALESCE(co.current_out, 0) - 
        COALESCE(ci.current_in, 0)
    )) as previous_period_stock_new,
    
    -- 本期出库
    ROUND(COALESCE(co.current_out, 0)) as current_period_out_new,
    
    -- 上期出库
    ROUND(COALESCE(po.previous_out, 0)) as previous_period_out_new,
    
    -- 本期入库
    ROUND(COALESCE(ci.current_in, 0)) as current_period_in_new,
    
    -- 上期入库
    ROUND(COALESCE(pi.previous_in, 0)) as previous_period_in_new,
    
    m.tenant_id
    
FROM jsh_material m
LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
    AND me.default_flag = '1'
LEFT JOIN (
    -- 当前库存
    SELECT material_id, tenant_id, SUM(IFNULL(current_number, 0)) as current_stock
    FROM jsh_material_current_stock 
    WHERE delete_flag = '0'
    GROUP BY material_id, tenant_id
) cs ON m.id = cs.material_id AND (m.tenant_id = cs.tenant_id OR (m.tenant_id IS NULL AND cs.tenant_id IS NULL))
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
    AND dh.oper_time >= @current_period_start
    AND dh.oper_time <= @current_period_end
    GROUP BY di.material_id, dh.tenant_id
) co ON m.id = co.material_id AND (m.tenant_id = co.tenant_id OR (m.tenant_id IS NULL AND co.tenant_id IS NULL))
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
    AND dh.oper_time >= @previous_period_start
    AND dh.oper_time <= @previous_period_end
    GROUP BY di.material_id, dh.tenant_id
) po ON m.id = po.material_id AND (m.tenant_id = po.tenant_id OR (m.tenant_id IS NULL AND po.tenant_id IS NULL))
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
    AND dh.oper_time >= @current_period_start
    AND dh.oper_time <= @current_period_end
    GROUP BY di.material_id, dh.tenant_id
) ci ON m.id = ci.material_id AND (m.tenant_id = ci.tenant_id OR (m.tenant_id IS NULL AND ci.tenant_id IS NULL))
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
    AND dh.oper_time >= @previous_period_start
    AND dh.oper_time <= @previous_period_end
    GROUP BY di.material_id, dh.tenant_id
) pi ON m.id = pi.material_id AND (m.tenant_id = pi.tenant_id OR (m.tenant_id IS NULL AND pi.tenant_id IS NULL))

WHERE m.delete_flag = '0';

-- 5. 比较新旧数据的差异
SELECT 
    '=== 计算结果对比 ===' as info;

SELECT 
    COUNT(*) as total_materials,
    COUNT(CASE WHEN old.current_period_stock != new.current_period_stock_new THEN 1 END) as current_stock_changes,
    COUNT(CASE WHEN old.previous_period_stock != new.previous_period_stock_new THEN 1 END) as previous_stock_changes,
    COUNT(CASE WHEN old.current_period_out != new.current_period_out_new THEN 1 END) as current_out_changes,
    COUNT(CASE WHEN old.previous_period_out != new.previous_period_out_new THEN 1 END) as previous_out_changes
FROM jsh_material_period_summary old
INNER JOIN temp_correct_calculation new ON old.material_id = new.material_id
WHERE old.delete_flag = '0';

-- 6. 显示差异示例
SELECT 
    '=== 差异示例（前10条）===' as info;

SELECT 
    old.material_id,
    old.bar_code,
    old.material_name,
    old.current_period_stock as old_current_stock,
    new.current_period_stock_new as new_current_stock,
    old.previous_period_stock as old_previous_stock,
    new.previous_period_stock_new as new_previous_stock,
    (new.current_period_stock_new - old.current_period_stock) as current_stock_diff,
    (new.previous_period_stock_new - old.previous_period_stock) as previous_stock_diff
FROM jsh_material_period_summary old
INNER JOIN temp_correct_calculation new ON old.material_id = new.material_id
WHERE old.delete_flag = '0'
AND (
    old.current_period_stock != new.current_period_stock_new 
    OR old.previous_period_stock != new.previous_period_stock_new
)
ORDER BY ABS(new.current_period_stock_new - old.current_period_stock) DESC
LIMIT 10;

-- 7. 执行更新（请确认上面的对比结果合理后再执行）
SELECT 
    '=== 准备更新数据 ===' as info;

-- 注释掉更新语句，需要手动确认后再执行
/*
UPDATE jsh_material_period_summary old
INNER JOIN temp_correct_calculation new ON old.material_id = new.material_id
SET 
    old.current_period_stock = new.current_period_stock_new,
    old.previous_period_stock = new.previous_period_stock_new,
    old.current_period_out = new.current_period_out_new,
    old.previous_period_out = new.previous_period_out_new,
    old.current_period_in = new.current_period_in_new,
    old.previous_period_in = new.previous_period_in_new,
    old.last_calculation_time = NOW()
WHERE old.delete_flag = '0';
*/

SELECT 
    '=== 计算逻辑分析完成 ===' as info;
SELECT 
    '请检查上面的对比结果，如果合理，请手动执行UPDATE语句' as next_step;
