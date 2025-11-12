-- 检查表结构和数据状态

-- 1. 检查 jsh_material_period_summary 表结构
SELECT 
    '=== jsh_material_period_summary 表结构 ===' as info;

DESCRIBE jsh_material_period_summary;

-- 2. 检查表中的数据量
SELECT 
    '=== 数据量统计 ===' as info;

SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN delete_flag = '0' THEN 1 END) as active_records,
    COUNT(CASE WHEN delete_flag = '1' THEN 1 END) as deleted_records
FROM jsh_material_period_summary;

-- 3. 检查小数问题
SELECT 
    '=== 小数问题检查 ===' as info;

SELECT 
    COUNT(*) as total_active_records,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as decimal_current_stock,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as decimal_previous_stock,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as decimal_current_out,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as decimal_previous_out,
    COUNT(CASE WHEN current_period_in != ROUND(current_period_in) THEN 1 END) as decimal_current_in,
    COUNT(CASE WHEN previous_period_in != ROUND(previous_period_in) THEN 1 END) as decimal_previous_in
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 4. 显示有小数问题的数据样例
SELECT 
    '=== 小数问题数据样例 ===' as info;

SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    previous_period_stock,
    current_period_out,
    previous_period_out,
    current_period_in,
    previous_period_in
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
LIMIT 5;

-- 5. 检查数据范围
SELECT 
    '=== 数据范围检查 ===' as info;

SELECT 
    MIN(current_period_stock) as min_current_stock,
    MAX(current_period_stock) as max_current_stock,
    AVG(current_period_stock) as avg_current_stock,
    MIN(previous_period_stock) as min_previous_stock,
    MAX(previous_period_stock) as max_previous_stock,
    AVG(previous_period_stock) as avg_previous_stock,
    MIN(current_period_out) as min_current_out,
    MAX(current_period_out) as max_current_out,
    AVG(current_period_out) as avg_current_out
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 6. 检查是否存在相关的存储过程
SELECT 
    '=== 存储过程检查 ===' as info;

SHOW PROCEDURE STATUS WHERE Name LIKE '%material%period%';

-- 7. 检查最近更新时间
SELECT 
    '=== 最近更新时间 ===' as info;

SELECT 
    MIN(last_calculation_time) as earliest_calculation,
    MAX(last_calculation_time) as latest_calculation,
    COUNT(DISTINCT DATE(last_calculation_time)) as calculation_days
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

SELECT 
    '=== 表结构检查完成 ===' as info;
