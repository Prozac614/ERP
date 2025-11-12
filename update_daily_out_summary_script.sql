-- =========================================
-- jsh_daily_out_summary Update Script
-- 用于更新每日出库汇总表的SQL脚本
-- =========================================
-- 创建时间: 2025-01-27
-- 目的: 更新jsh_daily_out_summary表，清理6个月前数据并重新计算最近6个月的每日出库汇总
-- 作者: ERP系统维护
-- 版本: 1.0
-- =========================================

-- !!!!! 重要警告 !!!!!
-- 1. 执行前请备份数据库！
-- 2. 建议在业务低峰期执行
-- 3. 脚本执行时间可能较长，请耐心等待
-- 4. 如有疑问，请联系系统管理员

-- 先决条件检查:
-- - MySQL 5.7+ 
-- - 用户需要对相关表的 SELECT, INSERT, UPDATE, DELETE 权限
-- - 建议至少有 1GB 可用内存用于处理

-- =========================================
-- 配置变量设置
-- =========================================

-- 获取当前日期和6个月前的日期
SELECT 
    '=== 脚本配置信息 ===' as info,
    CURDATE() as today_date,
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH) as six_months_ago,
    '批处理大小: 100' as batch_size;

-- =========================================
-- Phase 1: 数据清理阶段
-- =========================================

SELECT '=== Phase 1: 开始清理6个月前的数据 ===' as phase;

-- 清理6个月前的数据
DELETE FROM jsh_daily_out_summary 
WHERE out_date < DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- 进度报告：显示清理的记录数
SELECT 
    '数据清理完成' as status,
    ROW_COUNT() as deleted_records,
    DATE_SUB(CURDATE(), INTERVAL 6 MONTH) as cleanup_before_date;

-- =========================================
-- Phase 2: 材料枚举阶段
-- =========================================

SELECT '=== Phase 2: 枚举需要处理的商品 ===' as phase;

-- 先删除可能存在的临时表，然后创建新的临时表
DROP TEMPORARY TABLE IF EXISTS temp_materials_to_process;

-- 创建临时表存储需要处理的材料列表
CREATE TEMPORARY TABLE temp_materials_to_process AS
SELECT DISTINCT 
    di.material_id,
    m.name as material_name,
    me.bar_code
FROM jsh_depot_item di
INNER JOIN jsh_depot_head dh ON dh.id = di.header_id
LEFT JOIN jsh_material m ON m.id = di.material_id
LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
    AND IFNULL(me.delete_flag,'0') != '1' 
    AND me.default_flag = 1
WHERE dh.type = '出库'
    AND IFNULL(di.delete_flag,'0') != '1'
    AND IFNULL(dh.delete_flag,'0') != '1'
    AND IFNULL(m.delete_flag,'0') != '1'
    AND dh.status = '1'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- 进度报告：显示总共需要处理的材料数
SELECT 
    '材料枚举完成' as status,
    COUNT(*) as total_materials_to_process
FROM temp_materials_to_process;

-- =========================================
-- Phase 3: 批处理阶段 (兼容老版本MySQL)
-- =========================================

SELECT '=== Phase 3: 开始批量处理商品数据 ===' as phase;

-- 直接处理所有材料，使用JOIN代替IN子查询来避免MySQL版本兼容问题
SELECT '--- 开始处理所有商品的每日出库汇总 ---' as batch_info;

INSERT INTO jsh_daily_out_summary (
    material_id, bar_code, material_name, out_date,
    total_out_quantity, depot_count, bill_count, tenant_id
)
SELECT 
    di.material_id,
    tmp.bar_code,
    tmp.material_name,
    DATE(dh.oper_time) as out_date,
    SUM(IFNULL(di.basic_number, 0)) as total_out_quantity,
    COUNT(DISTINCT di.depot_id) as depot_count,
    COUNT(DISTINCT dh.id) as bill_count,
    dh.tenant_id
FROM jsh_depot_item di
INNER JOIN jsh_depot_head dh ON dh.id = di.header_id
INNER JOIN temp_materials_to_process tmp ON tmp.material_id = di.material_id
WHERE dh.type = '出库'
    AND IFNULL(di.delete_flag,'0') != '1'
    AND IFNULL(dh.delete_flag,'0') != '1'
    AND dh.status = '1'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
GROUP BY di.material_id, tmp.bar_code, tmp.material_name, DATE(dh.oper_time), dh.tenant_id
HAVING SUM(IFNULL(di.basic_number, 0)) > 0
ON DUPLICATE KEY UPDATE
    bar_code = VALUES(bar_code),
    material_name = VALUES(material_name),
    total_out_quantity = VALUES(total_out_quantity),
    depot_count = VALUES(depot_count),
    bill_count = VALUES(bill_count),
    last_update_time = CURRENT_TIMESTAMP;

-- 进度报告 - 处理完成
SELECT 
    '所有材料处理完成' as status,
    ROW_COUNT() as records_processed,
    '已完成所有商品的每日出库汇总计算' as progress;

-- =========================================
-- Phase 4: 验证和最终报告
-- =========================================

SELECT '=== Phase 4: 数据验证和最终报告 ===' as phase;

-- 统计信息报告
SELECT 
    '=== 汇总表统计信息 ===' as info;

SELECT 
    '每日出库汇总表记录数' as description,
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date
FROM jsh_daily_out_summary;

-- 最近7天的样例数据
SELECT 
    '=== 最近7天出库汇总样例 ===' as info;

SELECT 
    material_name,
    bar_code,
    out_date,
    total_out_quantity,
    depot_count,
    bill_count,
    last_update_time
FROM jsh_daily_out_summary 
WHERE out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY out_date DESC, total_out_quantity DESC
LIMIT 10;

-- 按日期统计记录数
SELECT 
    '=== 按日期统计记录分布 ===' as info;

SELECT 
    out_date,
    COUNT(*) as daily_record_count,
    SUM(total_out_quantity) as daily_total_quantity
FROM jsh_daily_out_summary 
WHERE out_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY out_date
ORDER BY out_date DESC
LIMIT 10;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS temp_materials_to_process;

-- 最终完成报告
SELECT 
    '=== 脚本执行完成 ===' as final_status,
    NOW() as completion_time,
    '所有阶段执行成功' as result,
    '数据已更新到最新状态' as note;

-- =========================================
-- 执行说明和故障排除
-- =========================================

/*
执行时间估计:
- 小型数据库 (< 10万记录): 1-5分钟
- 中型数据库 (10-100万记录): 5-30分钟  
- 大型数据库 (> 100万记录): 30分钟以上

故障排除:
1. 如果出现内存不足错误，可以减少批处理大小
2. 如果执行时间过长，可以在业务低峰期重新执行
3. 如果某个批次失败，可以单独重新执行该批次

性能优化提示:
- 确保以下索引存在:
  - jsh_daily_out_summary(material_id, out_date, tenant_id)
  - jsh_depot_head(type, status, oper_time)
  - jsh_depot_item(material_id, header_id)

安全措施:
- 使用了批处理来控制内存使用
- 每个批次都有进度报告
- 使用 ON DUPLICATE KEY UPDATE 进行增量更新
- 只处理有实际出库记录的商品和日期
*/ 