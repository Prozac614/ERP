-- 简化的库存小数修复脚本
-- 只修复现有数据的小数问题，不重新计算逻辑

-- 1. 检查当前数据中的小数问题
SELECT 
    '=== 检查小数问题 ===' as info;

SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as decimal_current_stock,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as decimal_previous_stock,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as decimal_current_out,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as decimal_previous_out
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 2. 显示小数问题的示例数据
SELECT 
    '=== 小数问题示例 ===' as info;

SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    ROUND(current_period_stock) as current_stock_rounded,
    previous_period_stock,
    ROUND(previous_period_stock) as previous_stock_rounded,
    current_period_out,
    ROUND(current_period_out) as current_out_rounded
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
LIMIT 10;

-- 3. 修复现有数据：将所有小数库存四舍五入为整数
SELECT 
    '=== 开始修复小数问题 ===' as info;

UPDATE jsh_material_period_summary 
SET 
    current_period_stock = ROUND(current_period_stock),
    previous_period_stock = ROUND(previous_period_stock),
    current_period_out = ROUND(current_period_out),
    previous_period_out = ROUND(previous_period_out),
    current_period_in = ROUND(current_period_in),
    previous_period_in = ROUND(previous_period_in),
    last_calculation_time = NOW()
WHERE delete_flag = '0'
AND (
    current_period_stock != ROUND(current_period_stock)
    OR previous_period_stock != ROUND(previous_period_stock)
    OR current_period_out != ROUND(current_period_out)
    OR previous_period_out != ROUND(previous_period_out)
    OR current_period_in != ROUND(current_period_in)
    OR previous_period_in != ROUND(previous_period_in)
);

-- 4. 验证修复结果
SELECT 
    '=== 修复结果验证 ===' as info;

SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as remaining_decimal_current_stock,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as remaining_decimal_previous_stock,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as remaining_decimal_current_out,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as remaining_decimal_previous_out,
    CASE 
        WHEN COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) = 0
        AND COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) = 0
        AND COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) = 0
        AND COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) = 0
        THEN '修复成功'
        ELSE '仍有小数问题'
    END as fix_status
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 5. 显示修复后的示例数据
SELECT 
    '=== 修复后数据示例 ===' as info;

SELECT 
    material_id,
    bar_code,
    material_name,
    current_period_stock,
    previous_period_stock,
    current_period_out,
    previous_period_out,
    current_period_in,
    previous_period_in,
    last_calculation_time
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
ORDER BY material_id
LIMIT 10;

SELECT 
    '=== 小数修复完成 ===' as info;
