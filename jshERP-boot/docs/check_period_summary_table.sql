-- ========================================
-- 检查期间汇总表结构和数据来源
-- 创建时间: 2025-07-19
-- 用途: 了解jsh_material_period_summary表的结构和数据来源
-- ========================================

-- 1. 检查表结构
DESCRIBE jsh_material_period_summary;

-- 2. 检查表中的数据样例
SELECT * FROM jsh_material_period_summary LIMIT 5;

-- 3. 检查数据来源和更新机制
SHOW CREATE TABLE jsh_material_period_summary;

-- 4. 检查是否有相关的存储过程或触发器
SHOW TRIGGERS LIKE '%material_period_summary%';

-- 5. 检查是否有定时任务或其他机制更新这个表
SELECT 
    COUNT(*) as total_records,
    MIN(id) as min_id,
    MAX(id) as max_id
FROM jsh_material_period_summary;

-- 6. 检查租户数据分布
SELECT 
    tenant_id,
    COUNT(*) as record_count
FROM jsh_material_period_summary 
GROUP BY tenant_id
ORDER BY tenant_id;
