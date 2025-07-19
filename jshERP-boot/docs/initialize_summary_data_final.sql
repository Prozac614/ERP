-- ===========================
-- 最终版本的初始化汇总表数据脚本
-- ===========================

-- 1. 初始化最近6个月的每日出库汇总数据
CALL update_daily_out_summary(
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 
    CURDATE(), 
    NULL
);

-- 2. 初始化商品期间汇总数据（使用正确的表结构）
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

-- 5. 查看商品期间汇总数据样例
SELECT 
    '=== 商品期间汇总样例数据 ===' as info;

SELECT 
    material_name,
    bar_code,
    current_period_stock,
    current_period_out,
    current_period_in
FROM jsh_material_period_summary 
WHERE current_period_out > 0 OR current_period_in > 0
ORDER BY current_period_out DESC
LIMIT 10;

-- 6. 检查原始数据情况（如果汇总表没有数据）
SELECT 
    '=== 原始单据数据检查 ===' as info;

SELECT 
    '最近出库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '出库' 
AND status = '1' 
AND delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY);

SELECT 
    '最近入库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '入库' 
AND status = '1' 
AND delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY);

SELECT 
    '商品总数' as description,
    COUNT(*) as count
FROM jsh_material 
WHERE delete_flag = '0';

SELECT 'Complete data initialization finished' as status; 