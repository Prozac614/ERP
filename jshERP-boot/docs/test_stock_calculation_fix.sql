-- ===========================
-- 库存计算修复测试和验证脚本
-- 用于验证修复后的库存计算逻辑是否正确
-- ===========================

-- 1. 检查当前数据状态
SELECT '=== 修复前数据状态检查 ===' as info;

-- 检查期间汇总表数据量
SELECT 
    COUNT(*) as 总记录数,
    COUNT(CASE WHEN delete_flag = '0' THEN 1 END) as 有效记录数,
    MIN(last_calculation_time) as 最早计算时间,
    MAX(last_calculation_time) as 最新计算时间
FROM jsh_material_period_summary;

-- 检查小数问题
SELECT 
    '小数问题检查' as 检查项目,
    COUNT(CASE WHEN current_period_stock != ROUND(current_period_stock) THEN 1 END) as 本期结存有小数,
    COUNT(CASE WHEN previous_period_stock != ROUND(previous_period_stock) THEN 1 END) as 上期结存有小数,
    COUNT(CASE WHEN current_period_out != ROUND(current_period_out) THEN 1 END) as 本期出库有小数,
    COUNT(CASE WHEN previous_period_out != ROUND(previous_period_out) THEN 1 END) as 上期出库有小数
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 检查库存平衡问题
SELECT 
    '库存平衡检查' as 检查项目,
    COUNT(*) as 总商品数,
    COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) as 平衡商品数,
    ROUND(COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) * 100.0 / COUNT(*), 2) as 平衡率百分比
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 2. 执行修复
SELECT '=== 开始执行修复 ===' as info;

-- 执行修复后的存储过程
CALL refresh_material_period_summary_correct(NULL);

-- 3. 验证修复结果
SELECT '=== 修复后数据验证 ===' as info;

-- 重新检查小数问题
SELECT 
    '修复后小数问题检查' as 检查项目,
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
    END as 修复状态
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 重新检查库存平衡
SELECT 
    '修复后库存平衡检查' as 检查项目,
    COUNT(*) as 总商品数,
    COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) as 平衡商品数,
    ROUND(COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) * 100.0 / COUNT(*), 2) as 平衡率百分比,
    CASE 
        WHEN COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END) = COUNT(*)
        THEN '✓ 所有商品库存平衡'
        ELSE CONCAT('⚠ ', COUNT(*) - COUNT(CASE WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 THEN 1 END), ' 个商品库存不平衡')
    END as 平衡状态
FROM jsh_material_period_summary 
WHERE delete_flag = '0';

-- 4. 显示修复后的样例数据
SELECT '=== 修复后样例数据 ===' as info;

SELECT 
    material_id as 商品ID,
    bar_code as 商品编码,
    material_name as 商品名称,
    current_period_stock as 本期结存,
    previous_period_stock as 上期结存,
    current_period_out as 本期出库,
    previous_period_out as 上期出库,
    current_period_in as 本期入库,
    previous_period_in as 上期入库,
    -- 验证库存平衡公式
    (previous_period_stock + current_period_in - current_period_out) as 计算的本期结存,
    (current_period_stock - (previous_period_stock + current_period_in - current_period_out)) as 差异,
    CASE 
        WHEN ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) <= 1 
        THEN '✓' 
        ELSE '✗' 
    END as 平衡状态,
    last_calculation_time as 计算时间
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
AND (current_period_stock > 0 OR current_period_out > 0 OR previous_period_out > 0)
ORDER BY current_period_stock DESC
LIMIT 10;

-- 5. 检查不平衡的商品（如果有的话）
SELECT '=== 不平衡商品检查 ===' as info;

SELECT 
    material_id as 商品ID,
    bar_code as 商品编码,
    material_name as 商品名称,
    current_period_stock as 本期结存,
    previous_period_stock as 上期结存,
    current_period_in as 本期入库,
    current_period_out as 本期出库,
    (previous_period_stock + current_period_in - current_period_out) as 计算的本期结存,
    (current_period_stock - (previous_period_stock + current_period_in - current_period_out)) as 差异,
    '需要进一步检查' as 备注
FROM jsh_material_period_summary 
WHERE delete_flag = '0'
AND ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) > 1
ORDER BY ABS(current_period_stock - (previous_period_stock + current_period_in - current_period_out)) DESC
LIMIT 5;

-- 6. 期间范围验证
SELECT '=== 期间范围验证 ===' as info;

SELECT
    NOW() as 当前时间,
    MONTH(NOW()) as 当前月份,
    CASE
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN '本期(2-7月)'
        WHEN MONTH(NOW()) >= 8 THEN '上期(8-12月，本期是下一年2-7月)'
        WHEN MONTH(NOW()) = 1 THEN '上期(1月，本期是当年2-7月)'
        ELSE '未知期间'
    END as 期间状态,
    CASE
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN
            CONCAT('本期: ', YEAR(NOW()), '-02-01 到 ', YEAR(NOW()), '-07-31')
        WHEN MONTH(NOW()) >= 8 THEN
            CONCAT('本期: ', YEAR(NOW()) + 1, '-02-01 到 ', YEAR(NOW()) + 1, '-07-31')
        WHEN MONTH(NOW()) = 1 THEN
            CONCAT('本期: ', YEAR(NOW()), '-02-01 到 ', YEAR(NOW()), '-07-31')
    END as 本期范围,
    CASE
        WHEN MONTH(NOW()) >= 2 AND MONTH(NOW()) <= 7 THEN
            CONCAT('上期: ', YEAR(NOW()) - 1, '-08-01 到 ', YEAR(NOW()), '-01-31')
        WHEN MONTH(NOW()) >= 8 THEN
            CONCAT('上期: ', YEAR(NOW()), '-08-01 到 ', YEAR(NOW()) + 1, '-01-31')
        WHEN MONTH(NOW()) = 1 THEN
            CONCAT('上期: ', YEAR(NOW()) - 1, '-08-01 到 ', YEAR(NOW()), '-01-31')
    END as 上期范围;

-- 7. 最终总结
SELECT '=== 修复总结 ===' as info;

SELECT 
    '库存计算修复完成' as 状态,
    COUNT(*) as 处理商品总数,
    COUNT(CASE WHEN current_period_stock > 0 THEN 1 END) as 有库存商品数,
    COUNT(CASE WHEN current_period_out > 0 OR previous_period_out > 0 THEN 1 END) as 有出库记录商品数,
    MAX(last_calculation_time) as 最新计算时间,
    '数据已按照正确的业务逻辑重新计算' as 备注
FROM jsh_material_period_summary 
WHERE delete_flag = '0';
