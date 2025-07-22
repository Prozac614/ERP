-- =========================================
-- 测试应用层汇总更新解决方案
-- 验证出入库操作后汇总表是否自动更新
-- 创建时间：2025-01-24
-- =========================================

SELECT '开始测试应用层汇总更新解决方案...' as message;

-- ----------------------------
-- 1. 记录测试前的汇总表状态
-- ----------------------------
SELECT '=== 测试前状态检查 ===' as step;

-- 记录当前汇总表的最新数据
SELECT 
    MAX(out_date) as max_date_before,
    COUNT(*) as total_records_before,
    MAX(last_update_time) as last_update_before
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- 查看最近的出库单据
SELECT 
    id,
    number,
    type,
    status,
    oper_time,
    CASE 
        WHEN status = '1' THEN '已审核 ✅'
        WHEN status = '0' THEN '未审核 ❌'
        ELSE status
    END as status_desc
FROM jsh_depot_head 
WHERE type = '出库'
    AND delete_flag = '0'
ORDER BY oper_time DESC
LIMIT 5;

-- ----------------------------
-- 2. 测试说明
-- ----------------------------
SELECT '=== 测试步骤说明 ===' as step;

SELECT 
    '现在请执行以下操作之一来测试自动更新功能：' as instruction,
    '1. 创建新的出库单据并审核' as option1,
    '2. 修改现有未审核出库单据并审核' as option2,
    '3. 重新编译部署后端代码' as option3,
    '4. 然后运行下面的验证查询' as next_step;

-- ----------------------------
-- 3. 验证应用层解决方案是否生效的查询
-- ----------------------------
SELECT '=== 验证查询（请在测试操作后运行） ===' as step;

-- 这些查询用于验证汇总表是否实时更新

-- 查询1：检查汇总表是否有新数据
SELECT 
    'test_query_1' as query_name,
    '检查汇总表最新状态' as description;

SELECT 
    MAX(out_date) as max_date_after,
    COUNT(*) as total_records_after,
    MAX(last_update_time) as last_update_after
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- 查询2：检查今天是否有汇总数据
SELECT 
    'test_query_2' as query_name,
    '检查今天的汇总数据' as description;

SELECT 
    COUNT(*) as today_records,
    SUM(total_out_quantity) as today_total_out,
    COUNT(DISTINCT material_id) as today_material_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0'
    AND out_date = CURDATE();

-- 查询3：检查最近更新的汇总记录
SELECT 
    'test_query_3' as query_name,
    '检查最近更新的汇总记录' as description;

SELECT 
    material_id,
    material_name,
    out_date,
    total_out_quantity,
    last_update_time,
    TIMESTAMPDIFF(MINUTE, last_update_time, NOW()) as minutes_ago
FROM jsh_daily_out_summary
WHERE delete_flag = '0'
    AND last_update_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY last_update_time DESC
LIMIT 10;

-- 查询4：验证特定出库单据的汇总更新
SELECT 
    'test_query_4' as query_name,
    '验证特定出库单据的汇总更新' as description;

-- 选择最近的已审核出库单据，检查其汇总数据
SELECT 
    dh.id as header_id,
    dh.number as bill_number,
    dh.oper_time,
    di.material_id,
    SUM(di.basic_number) as actual_out_quantity,
    ds.total_out_quantity as summary_quantity,
    ds.last_update_time,
    CASE 
        WHEN ds.total_out_quantity IS NOT NULL 
        THEN '✅ 汇总数据存在'
        ELSE '❌ 汇总数据缺失'
    END as summary_status
FROM jsh_depot_head dh
LEFT JOIN jsh_depot_item di ON di.header_id = dh.id
LEFT JOIN jsh_daily_out_summary ds ON ds.material_id = di.material_id 
    AND ds.out_date = DATE(dh.oper_time)
    AND ds.delete_flag = '0'
WHERE dh.type = '出库'
    AND dh.status = '1'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY dh.id, dh.number, dh.oper_time, di.material_id, ds.total_out_quantity, ds.last_update_time
ORDER BY dh.oper_time DESC
LIMIT 5;

-- ----------------------------
-- 4. 应用层日志检查建议
-- ----------------------------
SELECT '=== 应用层日志检查建议 ===' as step;

SELECT 
    '请检查应用服务器日志中的以下关键信息：' as log_check_instruction,
    'grep "开始更新汇总数据" /path/to/app.log' as log_command1,
    'grep "汇总数据更新完成" /path/to/app.log' as log_command2,
    'grep "updateSummaryDataAfterOperation" /path/to/app.log' as log_command3;

-- ----------------------------
-- 5. 缓存验证
-- ----------------------------
SELECT '=== 缓存验证 ===' as step;

SELECT 
    '验证缓存是否被正确清除：' as cache_check,
    '1. 检查前端首页表格是否显示最新数据' as frontend_check,
    '2. 检查API响应是否包含最新统计' as api_check,
    '3. 必要时手动清除缓存：POST /depotItem/clearCache' as manual_clear;

-- ----------------------------
-- 6. 性能影响评估
-- ----------------------------
SELECT '=== 性能影响评估 ===' as step;

-- 检查出库单据的频率
SELECT 
    DATE(oper_time) as oper_date,
    COUNT(*) as daily_bills,
    COUNT(DISTINCT di.material_id) as daily_materials,
    CASE 
        WHEN COUNT(*) > 100 THEN '⚠️  高频操作日'
        WHEN COUNT(*) > 50 THEN '🔶 中频操作日'
        ELSE '✅ 正常操作日'
    END as frequency_level
FROM jsh_depot_head dh
LEFT JOIN jsh_depot_item di ON di.header_id = dh.id
WHERE dh.type = '出库'
    AND dh.status = '1'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY DATE(dh.oper_time)
ORDER BY oper_date DESC;

-- ----------------------------
-- 7. 最终测试结果评估
-- ----------------------------
SELECT '=== 最终测试结果评估 ===' as step;

SELECT 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM jsh_daily_out_summary 
            WHERE delete_flag = '0' 
            AND last_update_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
        ) THEN '✅ 应用层解决方案工作正常'
        
        WHEN EXISTS (
            SELECT 1 FROM jsh_daily_out_summary 
            WHERE delete_flag = '0' 
            AND out_date >= CURDATE() - INTERVAL 1 DAY
        ) THEN '⚠️  数据较新，需进一步测试'
        
        ELSE '❌ 解决方案可能未生效，请检查代码部署'
    END as solution_status,
    
    NOW() as test_time;

SELECT '测试脚本执行完成。请根据查询结果判断解决方案是否生效。' as completion_message; 