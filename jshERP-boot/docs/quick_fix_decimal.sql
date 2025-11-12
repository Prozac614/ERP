-- 快速修复库存小数问题
-- 只修复小数，不改变计算逻辑

-- 1. 检查当前小数问题
SELECT 
    '=== 小数问题检查 ===' as info;

SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as decimal_current_stock,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as decimal_previous_stock,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as decimal_current_out,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as decimal_previous_out,
    COUNT(CASE WHEN current_period_in != ROUND(current_period_in) THEN 1 END) as decimal_current_in,
    COUNT(CASE WHEN previous_period_in != ROUND(previous_period_in) THEN 1 END) as decimal_previous_in
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
    ROUND(current_period_stock) as current_stock_fixed,
    previous_period_stock,
    ROUND(previous_period_stock) as previous_stock_fixed,
    current_period_out,
    ROUND(current_period_out) as current_out_fixed
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

-- 3. 执行修复：将所有小数四舍五入为整数
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
    COUNT(CASE WHEN current_period_in != ROUND(current_period_in) THEN 1 END) as remaining_decimal_current_in,
    COUNT(CASE WHEN previous_period_in != ROUND(previous_period_in) THEN 1 END) as remaining_decimal_previous_in,
    CASE 
        WHEN COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) = 0
        AND COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) = 0
        AND COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) = 0
        AND COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) = 0
        AND COUNT(CASE WHEN current_period_in != ROUND(current_period_in) THEN 1 END) = 0
        AND COUNT(CASE WHEN previous_period_in != ROUND(previous_period_in) THEN 1 END) = 0
        THEN '✅ 修复成功，无小数问题'
        ELSE '❌ 仍有小数问题'
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

-- 提示：修复完成后，建议清除应用缓存
SELECT 
    '提示：请在应用中点击"刷新数据"按钮清除缓存，或调用清除缓存接口' as reminder;
