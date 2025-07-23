-- =========================================
-- jsh_daily_out_summary 定时任务脚本
-- 用于定时更新每日出库汇总表（增量处理版本）
-- =========================================
-- 创建时间: 2025-01-27
-- 用途: 定时任务专用版本，只处理最近几天的数据
-- 执行频率: 建议每日执行一次
-- 版本: 1.0 (定时任务优化版)
-- =========================================

-- 定时任务配置参数
SET @days_to_process = 3;  -- 处理最近3天的数据
SET @cleanup_days = 180;   -- 清理6个月前的数据

-- =========================================
-- Phase 1: 数据清理（每次清理6个月前数据）
-- =========================================

-- 清理6个月前的数据
DELETE FROM jsh_daily_out_summary 
WHERE out_date < DATE_SUB(CURDATE(), INTERVAL @cleanup_days DAY);

-- =========================================
-- Phase 2: 增量更新最近几天数据
-- =========================================

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS temp_recent_materials;

-- 创建最近几天有出库活动的材料临时表
CREATE TEMPORARY TABLE temp_recent_materials AS
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
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL @days_to_process DAY);

-- 删除最近几天的旧汇总数据（准备重新计算）
DELETE FROM jsh_daily_out_summary 
WHERE out_date >= DATE_SUB(CURDATE(), INTERVAL @days_to_process DAY);

-- 重新计算最近几天的汇总数据
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
INNER JOIN temp_recent_materials tmp ON tmp.material_id = di.material_id
WHERE dh.type = '出库'
    AND IFNULL(di.delete_flag,'0') != '1'
    AND IFNULL(dh.delete_flag,'0') != '1'
    AND dh.status = '1'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL @days_to_process DAY)
GROUP BY di.material_id, tmp.bar_code, tmp.material_name, DATE(dh.oper_time), dh.tenant_id
HAVING SUM(IFNULL(di.basic_number, 0)) > 0;

-- =========================================
-- Phase 3: 执行结果记录
-- =========================================

-- 简化的执行结果
SELECT 
    '定时任务执行完成' as status,
    NOW() as execution_time,
    @days_to_process as days_processed,
    ROW_COUNT() as records_updated;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS temp_recent_materials;

-- =========================================
-- 定时任务说明
-- =========================================

/*
定时任务配置建议:

1. 执行时间: 建议每日凌晨2-4点执行（业务低峰期）

2. Linux Cron 示例:
   0 2 * * * mysql -u用户名 -p密码 数据库名 < /path/to/update_daily_out_summary_scheduled.sql

3. Windows 任务计划程序:
   - 程序: mysql.exe
   - 参数: -u用户名 -p密码 数据库名 < C:\path\to\update_daily_out_summary_scheduled.sql

4. 性能特点:
   - 只处理最近3天数据，执行速度快
   - 自动清理6个月前旧数据
   - 资源消耗低，适合频繁执行

5. 监控建议:
   - 记录执行日志
   - 监控执行时间
   - 检查返回的 records_updated 数量

6. 故障处理:
   - 如果某天执行失败，下次执行会自动补齐数据
   - 脚本具有幂等性，可以安全重复执行
*/ 