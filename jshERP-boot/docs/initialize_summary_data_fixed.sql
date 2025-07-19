-- ===========================
-- 修复后的初始化汇总表数据脚本
-- ===========================

-- 1. 先执行修复存储过程脚本
-- (这会重新创建没有问题的存储过程)

-- 2. 只初始化每日出库汇总数据
CALL update_daily_out_summary(
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 
    CURDATE(), 
    NULL
);

-- 3. 验证每日出库汇总数据是否正确填充
SELECT 
    '每日出库汇总表记录数' as table_name,
    COUNT(*) as record_count,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date
FROM jsh_daily_out_summary;

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

-- 5. 如果上面有数据，再尝试初始化商品期间汇总数据
-- 如果没有数据，说明可能没有历史出库记录，我们需要检查原始数据

SELECT 'Daily summary data initialization completed' as status; 