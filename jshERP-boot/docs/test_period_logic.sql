-- 测试期间计算逻辑的简化脚本

-- 1. 测试期间判断逻辑
SELECT 
    '=== 期间判断测试 ===' as info;

SELECT 
    NOW() as current_datetime,
    MONTH(NOW()) as current_month,
    YEAR(NOW()) as current_year,
    CASE 
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN '第一期(2-7月)'
        WHEN MONTH(NOW()) >= 8 THEN '第二期(8-12月)'
        WHEN MONTH(NOW()) = 1 THEN '第二期(1月)'
        ELSE '未知期间'
    END as current_period;

-- 2. 计算期间范围
SELECT 
    '=== 期间范围计算 ===' as info;

SET @current_month = MONTH(NOW());
SET @current_year = YEAR(NOW());

SELECT 
    @current_month as current_month,
    @current_year as current_year;

-- 判断当前期间并设置范围
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
    @current_period_start as current_period_start,
    @current_period_end as current_period_end,
    @previous_period_start as previous_period_start,
    @previous_period_end as previous_period_end;

-- 3. 检查现有数据的计算逻辑问题
SELECT 
    '=== 现有数据问题检查 ===' as info;

SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as decimal_current_stock,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as decimal_previous_stock,
    AVG(current_period_stock) as avg_current_stock,
    AVG(previous_period_stock) as avg_previous_stock,
    -- 检查库存平衡关系
    COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) > 0.01 THEN 1 END) as balance_error_count
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 4. 显示库存平衡问题的示例
SELECT 
    '=== 库存平衡问题示例 ===' as info;

SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    previous_period_stock,
    current_period_in,
    current_period_out,
    (previous_period_stock + current_period_in - current_period_out) as calculated_current_stock,
    (current_period_stock - (previous_period_stock + current_period_in - current_period_out)) as difference
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
AND ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) > 0.01
ORDER BY ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) DESC
LIMIT 10;

-- 5. 检查出库数据的期间分布
SELECT 
    '=== 出库数据期间分布检查 ===' as info;

SELECT 
    DATE_FORMAT(dh.oper_time, '%Y-%m') as month_year,
    COUNT(*) as bill_count,
    SUM(di.basic_number) as total_out_quantity,
    CASE 
        WHEN DATE(dh.oper_time) >= @current_period_start AND DATE(dh.oper_time) <= @current_period_end THEN '本期'
        WHEN DATE(dh.oper_time) >= @previous_period_start AND DATE(dh.oper_time) <= @previous_period_end THEN '上期'
        ELSE '其他期间'
    END as period_type
FROM jsh_depot_head dh
INNER JOIN jsh_depot_item di ON dh.id = di.header_id
WHERE dh.type = '出库' AND dh.status = '1' 
AND dh.delete_flag = '0' AND di.delete_flag = '0'
AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 18 MONTH)
GROUP BY DATE_FORMAT(dh.oper_time, '%Y-%m'), period_type
ORDER BY month_year DESC
LIMIT 20;

SELECT 
    '=== 期间逻辑测试完成 ===' as info;
