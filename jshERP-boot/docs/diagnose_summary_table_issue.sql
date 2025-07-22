-- ===========================
-- 完整诊断脚本：为什么 jsh_daily_out_summary 表没有更新
-- ===========================

SELECT '=== 1. 检查数据库触发器是否存在 ===' as step;

-- 检查触发器是否存在
SHOW TRIGGERS LIKE '%depot_item%';

SELECT '=== 2. 检查存储过程是否存在 ===' as step;

-- 检查存储过程是否存在
SHOW PROCEDURE STATUS WHERE Name = 'update_daily_out_summary';

SELECT '=== 3. 检查最近的出入库单据状态 ===' as step;

-- 检查最近30天的出入库单据状态分布
SELECT 
    type,
    status,
    CASE 
        WHEN status = '0' THEN '未审核'
        WHEN status = '1' THEN '已审核'
        WHEN status = '2' THEN '完成采购|销售'
        WHEN status = '3' THEN '部分采购|销售'
        WHEN status = '9' THEN '审核中'
        ELSE '其他'
    END as status_desc,
    COUNT(*) as count
FROM jsh_depot_head 
WHERE delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY type, status
ORDER BY type, status;

SELECT '=== 4. 检查是否有已审核的出库单据 ===' as step;

-- 检查最近是否有已审核的出库单据
SELECT 
    COUNT(*) as approved_outbound_bills,
    MAX(oper_time) as latest_approved_time
FROM jsh_depot_head 
WHERE type = '出库'
AND status = '1'  -- 已审核
AND delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY);

SELECT '=== 5. 检查 jsh_daily_out_summary 表数据 ===' as step;

-- 检查汇总表是否有数据
SELECT 
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

SELECT '=== 6. 查看最近的出库单据样例 ===' as step;

-- 查看最近的出库单据（包括状态）
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
        ELSE status
    END as status_desc,
    oper_time,
    creator
FROM jsh_depot_head 
WHERE type = '出库'
AND delete_flag = '0'
AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 10 DAY)
ORDER BY oper_time DESC
LIMIT 10;

SELECT '=== 7. 诊断结论 ===' as step;

-- 基于检查结果给出诊断结论
SELECT 
    CASE 
        WHEN NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_NAME LIKE '%depot_item%') 
        THEN '❌ 缺少数据库触发器'
        
        WHEN NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_NAME = 'update_daily_out_summary') 
        THEN '❌ 缺少存储过程'
        
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_depot_head 
            WHERE type = '出库' AND status = '1' AND delete_flag = '0'
            AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        ) 
        THEN '❌ 最近30天没有已审核的出库单据'
        
        ELSE '✅ 基础设施正常，需要进一步检查'
    END as diagnosis;

SELECT '=== 解决方案建议 ===' as step;

SELECT 
    CASE 
        WHEN NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_NAME LIKE '%depot_item%') 
        THEN '建议：执行 performance_optimization.sql 脚本安装触发器'
        
        WHEN NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_NAME = 'update_daily_out_summary') 
        THEN '建议：执行存储过程安装脚本'
        
        WHEN NOT EXISTS (
            SELECT 1 FROM jsh_depot_head 
            WHERE type = '出库' AND status = '1' AND delete_flag = '0'
            AND oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        ) 
        THEN '建议：需要审核出入库单据，或者手动初始化汇总表数据'
        
        ELSE '建议：检查触发器是否正常工作'
    END as solution; 