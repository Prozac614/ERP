-- ===========================
-- 基于业务周期的初始化汇总表数据脚本
-- 本期：2月1号到7月31号
-- 上期：8月1号到1月31号
-- ===========================

-- 1. 初始化最近6个月的每日出库汇总数据
CALL update_daily_out_summary(
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 
    CURDATE(), 
    NULL
);

-- 2. 初始化商品期间汇总数据（使用正确的业务周期）
CALL update_material_period_summary(NULL);

-- 3. 显示当前业务周期信息
SELECT 
    '=== 当前业务周期信息 ===' as info;

SELECT 
    CASE 
        WHEN MONTH(CURDATE()) >= 2 AND MONTH(CURDATE()) <= 7 THEN
            CONCAT('本期：', YEAR(CURDATE()), '-02-01 到 ', YEAR(CURDATE()), '-07-31')
        WHEN MONTH(CURDATE()) >= 8 THEN
            CONCAT('本期：', YEAR(CURDATE()) + 1, '-02-01 到 ', YEAR(CURDATE()) + 1, '-07-31')
        ELSE
            CONCAT('本期：', YEAR(CURDATE()), '-02-01 到 ', YEAR(CURDATE()), '-07-31')
    END as current_period;

SELECT 
    CASE 
        WHEN MONTH(CURDATE()) >= 2 AND MONTH(CURDATE()) <= 7 THEN
            CONCAT('上期：', YEAR(CURDATE()) - 1, '-08-01 到 ', YEAR(CURDATE()), '-01-31')
        WHEN MONTH(CURDATE()) >= 8 THEN
            CONCAT('上期：', YEAR(CURDATE()), '-08-01 到 ', YEAR(CURDATE()) + 1, '-01-31')
        ELSE
            CONCAT('上期：', YEAR(CURDATE()) - 1, '-08-01 到 ', YEAR(CURDATE()), '-01-31')
    END as previous_period;

-- 4. 验证数据是否正确填充
SELECT 
    '=== 汇总表数据统计 ===' as info;

SELECT 
    '每日出库汇总表记录数' as table_name,
    COUNT(*) as record_count,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date
FROM jsh_daily_out_summary
UNION ALL
SELECT 
    '商品期间汇总表记录数' as table_name,
    COUNT(*) as record_count,
    NULL as earliest_date,
    NULL as latest_date
FROM jsh_material_period_summary;

-- 5. 查看最近几天的每日出库数据样例
SELECT 
    '=== 每日出库汇总样例数据 ===' as info;

SELECT 
    material_name,
    bar_code,
    out_date,
    total_out_quantity,
    depot_count,
    bill_count
FROM jsh_daily_out_summary 
WHERE out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY out_date DESC, total_out_quantity DESC
LIMIT 10;

-- 6. 查看商品期间汇总数据样例
SELECT 
    '=== 商品期间汇总样例数据 ===' as info;

SELECT 
    material_name,
    bar_code,
    current_period_stock,
    current_period_out,
    previous_period_out,
    current_period_in,
    previous_period_in
FROM jsh_material_period_summary 
WHERE current_period_out > 0 OR previous_period_out > 0 OR current_period_in > 0 OR previous_period_in > 0
ORDER BY current_period_out DESC
LIMIT 10;

-- 7. 按业务周期检查原始数据情况
SELECT 
    '=== 原始单据数据检查（按业务周期） ===' as info;

-- 本期出库数据检查
SELECT 
    '本期出库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '出库' 
AND status = '1' 
AND delete_flag = '0'
AND (
    (MONTH(CURDATE()) >= 2 AND MONTH(CURDATE()) <= 7 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()), '-02-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()), '-07-31'))
    OR
    (MONTH(CURDATE()) >= 8 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()) + 1, '-02-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()) + 1, '-07-31'))
    OR
    (MONTH(CURDATE()) = 1 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()), '-02-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()), '-07-31'))
);

-- 上期出库数据检查
SELECT 
    '上期出库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '出库' 
AND status = '1' 
AND delete_flag = '0'
AND (
    (MONTH(CURDATE()) >= 2 AND MONTH(CURDATE()) <= 7 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()) - 1, '-08-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()), '-01-31'))
    OR
    (MONTH(CURDATE()) >= 8 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()), '-08-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()) + 1, '-01-31'))
    OR
    (MONTH(CURDATE()) = 1 AND 
     DATE(oper_time) >= CONCAT(YEAR(CURDATE()) - 1, '-08-01') AND 
     DATE(oper_time) <= CONCAT(YEAR(CURDATE()), '-01-31'))
);

SELECT 
    '商品总数' as description,
    COUNT(*) as count
FROM jsh_material 
WHERE delete_flag = '0';

SELECT 'Business period data initialization completed' as status; 