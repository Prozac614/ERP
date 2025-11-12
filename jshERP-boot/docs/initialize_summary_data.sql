-- ===========================
-- 初始化汇总表数据脚本
-- ===========================

-- 1. 初始化最近6个月的每日出库汇总数据
CALL update_daily_out_summary(
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 
    CURDATE(), 
    NULL
);

-- 2. 初始化商品期间汇总数据
CALL update_material_period_summary(NULL);

-- 3. 验证数据是否正确填充
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

-- 4. 查看最近几天的每日出库数据样例
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

SELECT 'Data initialization completed successfully' as status; 