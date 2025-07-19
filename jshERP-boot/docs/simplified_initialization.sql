-- ===========================
-- 简化版初始化脚本（跳过库存问题）
-- ===========================

-- 1. 只初始化每日出库汇总数据
CALL update_daily_out_summary(
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 
    CURDATE(), 
    NULL
);

-- 2. 显示当前业务周期信息
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

-- 3. 验证每日出库汇总数据
SELECT 
    '=== 每日出库汇总表统计 ===' as info;

SELECT 
    '每日出库汇总表记录数' as table_name,
    COUNT(*) as record_count,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date
FROM jsh_daily_out_summary;

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

-- 5. 原始数据检查
SELECT 
    '=== 原始单据数据检查 ===' as info;

-- 检查最近1年的出库数据
SELECT 
    '最近1年出库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '出库' 
AND status = '1' 
AND delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR);

-- 检查本年度出库数据
SELECT 
    '本年度出库单据数量' as description,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE type = '出库' 
AND status = '1' 
AND delete_flag = '0'
AND YEAR(oper_time) = YEAR(CURDATE());

SELECT 
    '商品总数' as description,
    COUNT(*) as count
FROM jsh_material 
WHERE delete_flag = '0';

-- 6. 检查depot_item表中的数据
SELECT 
    '出库明细记录数' as description,
    COUNT(*) as count
FROM jsh_depot_item di
INNER JOIN jsh_depot_head dh ON di.header_id = dh.id
WHERE dh.type = '出库' 
AND dh.delete_flag = '0' 
AND di.delete_flag = '0';

SELECT 'Simplified data initialization completed successfully' as status; 