-- =========================================
-- 诊断应用层解决方案为什么没有生效
-- 创建时间：2025-01-24
-- =========================================

SELECT '开始诊断应用层解决方案...' as message;

-- ----------------------------
-- 1. 检查最新的出库单据状态
-- ----------------------------
SELECT '=== 1. 检查最新的出库单据状态 ===' as step;

SELECT 
    id,
    number,
    type,
    sub_type,
    status,
    CASE 
        WHEN status = '0' THEN '未审核 ❌'
        WHEN status = '1' THEN '已审核 ✅'
        WHEN status = '2' THEN '完成出库 ✅'
        ELSE '其他状态'
    END as status_desc,
    oper_time,
    TIMESTAMPDIFF(MINUTE, oper_time, NOW()) as minutes_ago
FROM jsh_depot_head 
WHERE type = '出库'
    AND delete_flag = '0'
    AND oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
ORDER BY oper_time DESC
LIMIT 5;

-- ----------------------------
-- 2. 检查最新出库单据的明细
-- ----------------------------
SELECT '=== 2. 检查最新出库单据的明细 ===' as step;

SELECT 
    di.id as item_id,
    di.header_id,
    dh.number as bill_number,
    di.material_id,
    m.name as material_name,
    di.basic_number as out_quantity,
    dh.oper_time,
    dh.status as bill_status
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
LEFT JOIN jsh_material m ON m.id = di.material_id
WHERE dh.type = '出库'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
ORDER BY dh.oper_time DESC, di.id DESC
LIMIT 10;

-- ----------------------------
-- 3. 检查汇总表是否有对应的更新
-- ----------------------------
SELECT '=== 3. 检查汇总表是否有对应的更新 ===' as step;

-- 检查今天的汇总数据
SELECT 
    material_id,
    material_name,
    out_date,
    total_out_quantity,
    last_update_time,
    TIMESTAMPDIFF(MINUTE, last_update_time, NOW()) as minutes_ago
FROM jsh_daily_out_summary 
WHERE delete_flag = '0'
    AND out_date = CURDATE()
ORDER BY last_update_time DESC
LIMIT 10;

-- ----------------------------
-- 4. 验证特定商品的汇总数据是否正确
-- ----------------------------
SELECT '=== 4. 验证特定商品的汇总数据是否正确 ===' as step;

-- 对比最新出库单据的商品汇总数据
SELECT 
    di.material_id,
    m.name as material_name,
    DATE(dh.oper_time) as oper_date,
    SUM(di.basic_number) as actual_total_out,
    ds.total_out_quantity as summary_total_out,
    ds.last_update_time,
    CASE 
        WHEN ds.total_out_quantity IS NULL THEN '❌ 汇总数据缺失'
        WHEN ds.total_out_quantity = SUM(di.basic_number) THEN '✅ 数据一致'
        ELSE '⚠️ 数据不一致'
    END as data_status
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id  
LEFT JOIN jsh_material m ON m.id = di.material_id
LEFT JOIN jsh_daily_out_summary ds ON ds.material_id = di.material_id 
    AND ds.out_date = DATE(dh.oper_time)
    AND ds.delete_flag = '0'
WHERE dh.type = '出库'
    AND dh.status = '1'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
GROUP BY di.material_id, m.name, DATE(dh.oper_time), ds.total_out_quantity, ds.last_update_time
ORDER BY dh.oper_time DESC;

-- ----------------------------
-- 5. 检查首页API使用的数据源
-- ----------------------------
SELECT '=== 5. 检查首页API使用的数据源 ===' as step;

-- 模拟首页API查询（简化版本）
SELECT 
    ds.material_id,
    ds.material_name,
    ds.out_date,
    ds.total_out_quantity,
    ds.last_update_time,
    'daily_summary_table' as data_source
FROM jsh_daily_out_summary ds
WHERE ds.delete_flag = '0'
    AND ds.out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    AND ds.total_out_quantity > 0
ORDER BY ds.out_date DESC, ds.total_out_quantity DESC
LIMIT 10;

-- ----------------------------
-- 6. 检查是否存在重复的汇总记录
-- ----------------------------
SELECT '=== 6. 检查是否存在重复的汇总记录 ===' as step;

SELECT 
    material_id,
    out_date,
    COUNT(*) as record_count,
    GROUP_CONCAT(id) as record_ids,
    GROUP_CONCAT(total_out_quantity) as quantities
FROM jsh_daily_out_summary
WHERE delete_flag = '0'
    AND out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY material_id, out_date
HAVING COUNT(*) > 1
ORDER BY out_date DESC;

-- ----------------------------
-- 7. 检查Redis缓存键（如果可能）
-- ----------------------------
SELECT '=== 7. 缓存相关检查建议 ===' as step;

SELECT 
    '请检查以下缓存相关问题：' as cache_check,
    '1. Redis服务是否正常运行' as check1,
    '2. 应用是否能正常连接Redis' as check2,
    '3. 执行缓存清除API：POST /depotItem/clearCache' as check3,
    '4. 检查应用日志中的缓存操作' as check4;

-- ----------------------------
-- 8. 应用层代码执行检查
-- ----------------------------
SELECT '=== 8. 应用层代码执行检查 ===' as step;

SELECT 
    '请检查应用服务器日志中是否有以下关键信息：' as log_check,
    'grep "开始更新汇总数据" app.log' as log_cmd1,
    'grep "汇总数据更新完成" app.log' as log_cmd2,
    'grep "updateSummaryDataAfterOperation" app.log' as log_cmd3,
    'grep "updateDailyOutSummary" app.log' as log_cmd4,
    'grep "clearAllCache" app.log' as log_cmd5;

-- ----------------------------
-- 9. 问题诊断结论
-- ----------------------------
SELECT '=== 9. 问题诊断结论 ===' as step;

SELECT 
    CASE 
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_depot_head 
            WHERE type = '出库' AND status = '1' AND delete_flag = '0'
            AND oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
        ) THEN '❌ 最近2小时内没有已审核的出库单据'
        
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_daily_out_summary 
            WHERE delete_flag = '0' AND out_date = CURDATE()
        ) THEN '❌ 今天没有汇总数据，应用层代码可能未执行'
        
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_daily_out_summary 
            WHERE delete_flag = '0' 
            AND last_update_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
        ) THEN '❌ 最近2小时内汇总表没有更新，代码可能未生效'
        
        ELSE '⚠️ 汇总表有数据，但可能是缓存问题'
    END as diagnosis,
    
    NOW() as check_time;

-- ----------------------------
-- 10. 下一步建议
-- ----------------------------
SELECT '=== 10. 下一步建议 ===' as step;

SELECT 
    '根据诊断结果，建议采取以下行动：' as next_actions,
    '1. 检查应用服务器是否重启并加载了新代码' as action1,
    '2. 查看应用日志确认代码是否执行' as action2,
    '3. 手动执行缓存清除API' as action3,
    '4. 如果代码未执行，可能需要重新部署' as action4,
    '5. 检查updateDailyOutSummary方法是否正常工作' as action5;

SELECT '诊断脚本执行完成。请根据结果判断问题所在。' as completion; 