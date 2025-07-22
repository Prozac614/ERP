-- =========================================
-- 诊断应用层代码为什么没有执行
-- 确保正向解决问题
-- 创建时间：2025-01-24
-- =========================================

SELECT '诊断应用层代码执行情况...' as message;

-- ----------------------------
-- 1. 检查最近是否有出库单据被处理
-- ----------------------------
SELECT '=== 1. 检查最近出库单据处理情况 ===' as step;

-- 检查最近2小时的出库单据
SELECT 
    id,
    number,
    type,
    status,
    CASE 
        WHEN status = '1' THEN '已审核 ✅'
        WHEN status = '0' THEN '未审核 ❌'
        ELSE status
    END as status_desc,
    oper_time,
    TIMESTAMPDIFF(MINUTE, oper_time, NOW()) as minutes_ago,
    '这些单据应该触发汇总表更新' as note
FROM jsh_depot_head 
WHERE type = '出库'
    AND delete_flag = '0'
    AND oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
ORDER BY oper_time DESC;

-- ----------------------------
-- 2. 检查这些单据的明细信息
-- ----------------------------
SELECT '=== 2. 检查出库单据明细 ===' as step;

SELECT 
    di.id,
    di.header_id,
    dh.number as bill_number,
    di.material_id,
    m.name as material_name,
    di.basic_number,
    dh.oper_time,
    dh.status,
    '应该更新这些商品的汇总数据' as note
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
LEFT JOIN jsh_material m ON m.id = di.material_id
WHERE dh.type = '出库'
    AND dh.status = '1'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
ORDER BY dh.oper_time DESC;

-- ----------------------------
-- 3. 检查应用日志建议
-- ----------------------------
SELECT '=== 3. 应用日志检查重点 ===' as step;

SELECT 
    '请检查应用日志中的关键信息：' as instruction,
    '1. 查看是否有updateSummaryDataAfterOperation方法调用' as check1,
    '2. 查看是否有"开始更新汇总数据"日志' as check2,
    '3. 查看是否有错误或异常信息' as check3,
    '4. 确认DepotItemService.saveDetials方法是否被调用' as check4;

-- ----------------------------
-- 4. 代码执行验证建议
-- ----------------------------
SELECT '=== 4. 代码执行验证 ===' as step;

SELECT 
    '验证代码是否正确执行的方法：' as verification,
    '1. 在updateSummaryDataAfterOperation方法开头添加日志' as method1,
    '2. 检查DepotItemOptimizedService是否正确注入' as method2,
    '3. 确认XML中的updateDailyOutSummary方法是否工作' as method3,
    '4. 测试clearAllCache方法是否执行' as method4;

-- ----------------------------
-- 5. 问题可能原因分析
-- ----------------------------
SELECT '=== 5. 可能的问题原因 ===' as step;

SELECT 
    CASE 
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_depot_head 
            WHERE type = '出库' AND status = '1' 
            AND oper_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
            AND delete_flag = '0'
        ) THEN '❌ 最近没有已审核的出库单据，无法触发代码执行'
        
        ELSE '✅ 有已审核的出库单据，应该能触发代码执行'
    END as trigger_condition,
    
    '可能的问题：' as possible_issues,
    '1. 代码未正确部署' as issue1,
    '2. DepotItemOptimizedService注入失败' as issue2,
    '3. updateDailyOutSummary方法执行失败' as issue3,
    '4. 事务回滚导致更新失败' as issue4;

SELECT '诊断完成。请根据检查结果确定问题所在。' as completion; 