-- =========================================
-- jsh_material_period_summary 更新脚本
-- 用于更新商品期间汇总表的SQL脚本
-- =========================================
-- 创建时间: 2025-01-27
-- 目的: 更新jsh_material_period_summary表，计算本期/上期的库存和出入库汇总
-- 业务期间: 第一期(2月1日-7月31日), 第二期(8月1日-次年1月31日)
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
-- - 确保相关表存在: jsh_material_period_summary, jsh_material_current_stock, jsh_depot_head, jsh_depot_item

-- =========================================
-- Phase 1: 配置信息和期间计算
-- =========================================

SELECT '=== Phase 1: 期间计算和配置信息 ===' as phase;

-- 动态计算期间范围
SET @current_month = MONTH(CURDATE());
SET @current_year = YEAR(CURDATE());

-- 根据当前月份确定本期和上期范围
SET @current_period_start = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN 
        -- 当前在第一期(2-7月)，本期就是2-7月
        CONCAT(@current_year, '-02-01 00:00:00')
    WHEN @current_month >= 8 THEN 
        -- 当前在第二期(8-12月)，本期是8月到次年1月
        CONCAT(@current_year, '-08-01 00:00:00')
    ELSE 
        -- 当前在1月，本期是上年8月到当年1月
        CONCAT(@current_year - 1, '-08-01 00:00:00')
END;

SET @current_period_end = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN 
        -- 第一期结束于7月31日
        CONCAT(@current_year, '-07-31 23:59:59')
    WHEN @current_month >= 8 THEN 
        -- 第二期结束于次年1月31日
        CONCAT(@current_year + 1, '-01-31 23:59:59')
    ELSE 
        -- 1月，第二期结束于当年1月31日
        CONCAT(@current_year, '-01-31 23:59:59')
END;

SET @previous_period_start = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN 
        -- 当前第一期，上期是上年的第二期
        CONCAT(@current_year - 1, '-08-01 00:00:00')
    WHEN @current_month >= 8 THEN 
        -- 当前第二期，上期是当年的第一期
        CONCAT(@current_year, '-02-01 00:00:00')
    ELSE 
        -- 当前1月(第二期)，上期是上年的第一期
        CONCAT(@current_year - 1, '-02-01 00:00:00')
END;

SET @previous_period_end = CASE 
    WHEN @current_month >= 2 AND @current_month <= 7 THEN 
        -- 当前第一期，上期结束于当年1月31日
        CONCAT(@current_year, '-01-31 23:59:59')
    WHEN @current_month >= 8 THEN 
        -- 当前第二期，上期结束于当年7月31日
        CONCAT(@current_year, '-07-31 23:59:59')
    ELSE 
        -- 当前1月，上期结束于上年7月31日
        CONCAT(@current_year - 1, '-07-31 23:59:59')
END;

-- 显示计算的期间信息
SELECT 
    '=== 期间计算结果 ===' as info,
    CURDATE() as today_date,
    @current_month as current_month,
    CASE 
        WHEN @current_month >= 2 AND @current_month <= 7 THEN '第一期(2-7月)'
        WHEN @current_month >= 8 THEN '第二期(8-12月)'
        ELSE '第二期(1月)'
    END as current_period_name;

SELECT 
    '本期时间范围' as period_type,
    @current_period_start as start_time,
    @current_period_end as end_time
UNION ALL
SELECT 
    '上期时间范围' as period_type,
    @previous_period_start as start_time,
    @previous_period_end as end_time;

-- =========================================
-- Phase 2: 数据清理
-- =========================================

SELECT '=== Phase 2: 清理现有汇总数据 ===' as phase;

-- 清理现有的期间汇总数据（保留删除标记为1的记录）
DELETE FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') = '0';

-- 显示清理结果
SELECT 
    '数据清理完成' as status,
    ROW_COUNT() as deleted_records,
    '已清理所有有效的期间汇总记录' as note;

-- =========================================
-- Phase 3: 数据源准备
-- =========================================

SELECT '=== Phase 3: 准备基础数据源 ===' as phase;

-- 清理可能存在的临时表
DROP TEMPORARY TABLE IF EXISTS temp_active_materials;
DROP TEMPORARY TABLE IF EXISTS temp_current_stock;
DROP TEMPORARY TABLE IF EXISTS temp_current_in_out;
DROP TEMPORARY TABLE IF EXISTS temp_previous_in_out;

-- 创建活跃商品临时表（有库存或有交易记录的商品）
CREATE TEMPORARY TABLE temp_active_materials AS
SELECT DISTINCT m.id as material_id, m.tenant_id
FROM jsh_material m
WHERE IFNULL(m.delete_flag, '0') = '0'
AND (
    -- 有当前库存的商品
    EXISTS (
        SELECT 1 FROM jsh_material_current_stock mcs 
        WHERE mcs.material_id = m.id 
        AND IFNULL(mcs.delete_flag, '0') = '0'
        AND IFNULL(mcs.current_number, 0) > 0
    )
    OR
    -- 或在本期/上期有交易记录的商品
    EXISTS (
        SELECT 1 FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE di.material_id = m.id
        AND dh.type IN ('出库', '入库')
        AND dh.status = '1'
        AND IFNULL(dh.delete_flag, '0') = '0'
        AND IFNULL(di.delete_flag, '0') = '0'
        AND (
            (dh.oper_time >= @current_period_start AND dh.oper_time <= @current_period_end)
            OR
            (dh.oper_time >= @previous_period_start AND dh.oper_time <= @previous_period_end)
        )
    )
);

-- 创建当前库存汇总临时表
CREATE TEMPORARY TABLE temp_current_stock AS
SELECT 
    mcs.material_id,
    mcs.tenant_id,
    SUM(IFNULL(mcs.current_number, 0)) as current_stock
FROM jsh_material_current_stock mcs
INNER JOIN temp_active_materials tam ON tam.material_id = mcs.material_id 
    AND (tam.tenant_id = mcs.tenant_id OR (tam.tenant_id IS NULL AND mcs.tenant_id IS NULL))
WHERE IFNULL(mcs.delete_flag, '0') = '0'
GROUP BY mcs.material_id, mcs.tenant_id;

-- 创建本期出入库汇总临时表
CREATE TEMPORARY TABLE temp_current_in_out AS
SELECT 
    di.material_id,
    dh.tenant_id,
    SUM(CASE WHEN dh.type = '出库' THEN IFNULL(di.basic_number, 0) ELSE 0 END) as current_out,
    SUM(CASE WHEN dh.type = '入库' THEN IFNULL(di.basic_number, 0) ELSE 0 END) as current_in
FROM jsh_depot_head dh
INNER JOIN jsh_depot_item di ON dh.id = di.header_id
INNER JOIN temp_active_materials tam ON tam.material_id = di.material_id
    AND (tam.tenant_id = dh.tenant_id OR (tam.tenant_id IS NULL AND dh.tenant_id IS NULL))
WHERE dh.type IN ('出库', '入库')
AND dh.status = '1'
AND IFNULL(dh.delete_flag, '0') = '0'
AND IFNULL(di.delete_flag, '0') = '0'
AND dh.oper_time >= @current_period_start
AND dh.oper_time <= @current_period_end
GROUP BY di.material_id, dh.tenant_id;

-- 创建上期出入库汇总临时表
CREATE TEMPORARY TABLE temp_previous_in_out AS
SELECT 
    di.material_id,
    dh.tenant_id,
    SUM(CASE WHEN dh.type = '出库' THEN IFNULL(di.basic_number, 0) ELSE 0 END) as previous_out,
    SUM(CASE WHEN dh.type = '入库' THEN IFNULL(di.basic_number, 0) ELSE 0 END) as previous_in
FROM jsh_depot_head dh
INNER JOIN jsh_depot_item di ON dh.id = di.header_id
INNER JOIN temp_active_materials tam ON tam.material_id = di.material_id
    AND (tam.tenant_id = dh.tenant_id OR (tam.tenant_id IS NULL AND dh.tenant_id IS NULL))
WHERE dh.type IN ('出库', '入库')
AND dh.status = '1'
AND IFNULL(dh.delete_flag, '0') = '0'
AND IFNULL(di.delete_flag, '0') = '0'
AND dh.oper_time >= @previous_period_start
AND dh.oper_time <= @previous_period_end
GROUP BY di.material_id, dh.tenant_id;

-- 显示数据准备结果
SELECT 
    '数据源准备完成' as status,
    (SELECT COUNT(*) FROM temp_active_materials) as active_materials_count,
    (SELECT COUNT(*) FROM temp_current_stock) as current_stock_records,
    (SELECT COUNT(*) FROM temp_current_in_out) as current_period_transactions,
    (SELECT COUNT(*) FROM temp_previous_in_out) as previous_period_transactions;

-- =========================================
-- Phase 4: 核心计算和数据插入
-- =========================================

SELECT '=== Phase 4: 开始期间汇总计算 ===' as phase;

-- 插入计算结果到期间汇总表
INSERT INTO jsh_material_period_summary (
    material_id, bar_code, material_name, material_model, material_unit,
    current_period_stock, previous_period_stock,
    current_period_out, previous_period_out,
    current_period_in, previous_period_in,
    tenant_id, delete_flag, last_calculation_time
)
SELECT 
    m.id as material_id,
    me.bar_code,
    m.name as material_name,
    m.model as material_model,
    m.unit as material_unit,
    
    -- 本期结存：直接使用当前库存
    COALESCE(tcs.current_stock, 0) as current_period_stock,
    
    -- 上期结存：使用库存平衡公式反推
    -- 公式：上期结存 = 本期结存 - 本期入库 + 本期出库
    GREATEST(0, 
        COALESCE(tcs.current_stock, 0) - 
        COALESCE(tcio.current_in, 0) + 
        COALESCE(tcio.current_out, 0)
    ) as previous_period_stock,
    
    -- 本期出库
    COALESCE(tcio.current_out, 0) as current_period_out,
    
    -- 上期出库
    COALESCE(tpio.previous_out, 0) as previous_period_out,
    
    -- 本期入库
    COALESCE(tcio.current_in, 0) as current_period_in,
    
    -- 上期入库
    COALESCE(tpio.previous_in, 0) as previous_period_in,
    
    m.tenant_id,
    '0' as delete_flag,
    NOW() as last_calculation_time
    
FROM temp_active_materials tam
INNER JOIN jsh_material m ON m.id = tam.material_id
    AND (tam.tenant_id = m.tenant_id OR (tam.tenant_id IS NULL AND m.tenant_id IS NULL))
LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
    AND me.default_flag = '1' 
    AND IFNULL(me.delete_flag, '0') = '0'
LEFT JOIN temp_current_stock tcs ON tcs.material_id = m.id
    AND (tcs.tenant_id = m.tenant_id OR (tcs.tenant_id IS NULL AND m.tenant_id IS NULL))
LEFT JOIN temp_current_in_out tcio ON tcio.material_id = m.id
    AND (tcio.tenant_id = m.tenant_id OR (tcio.tenant_id IS NULL AND m.tenant_id IS NULL))
LEFT JOIN temp_previous_in_out tpio ON tpio.material_id = m.id
    AND (tpio.tenant_id = m.tenant_id OR (tpio.tenant_id IS NULL AND m.tenant_id IS NULL))
WHERE IFNULL(m.delete_flag, '0') = '0'
ON DUPLICATE KEY UPDATE
    bar_code = VALUES(bar_code),
    material_name = VALUES(material_name),
    material_model = VALUES(material_model),
    material_unit = VALUES(material_unit),
    current_period_stock = VALUES(current_period_stock),
    previous_period_stock = VALUES(previous_period_stock),
    current_period_out = VALUES(current_period_out),
    previous_period_out = VALUES(previous_period_out),
    current_period_in = VALUES(current_period_in),
    previous_period_in = VALUES(previous_period_in),
    delete_flag = '0',
    last_calculation_time = NOW();

-- 显示插入结果
SELECT 
    '期间汇总计算完成' as status,
    ROW_COUNT() as records_processed,
    '所有活跃商品的期间汇总已更新' as note;

-- =========================================
-- Phase 5: 数据验证和统计报告
-- =========================================

SELECT '=== Phase 5: 数据验证和统计报告 ===' as phase;

-- 总体统计信息
SELECT 
    '=== 更新统计信息 ===' as info;

SELECT 
    '期间汇总表记录数' as description,
    COUNT(*) as total_records,
    COUNT(CASE WHEN current_period_stock > 0 THEN 1 END) as records_with_stock,
    COUNT(CASE WHEN current_period_out > 0 THEN 1 END) as records_with_out,
    COUNT(CASE WHEN current_period_in > 0 THEN 1 END) as records_with_in
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') = '0';

-- 按租户统计
SELECT 
    '=== 按租户统计 ===' as info;

SELECT 
    IFNULL(tenant_id, 'NULL') as tenant_id,
    COUNT(*) as record_count,
    SUM(current_period_stock) as total_current_stock,
    SUM(current_period_out) as total_current_out,
    SUM(current_period_in) as total_current_in
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') = '0'
GROUP BY tenant_id
ORDER BY tenant_id;

-- 数据样例展示
SELECT 
    '=== 数据样例（前10条有活动的记录） ===' as info;

SELECT 
    material_name,
    bar_code,
    current_period_stock,
    previous_period_stock,
    current_period_out,
    previous_period_out,
    current_period_in,
    previous_period_in,
    last_calculation_time
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') = '0'
AND (current_period_stock > 0 OR current_period_out > 0 OR current_period_in > 0)
ORDER BY current_period_stock DESC, current_period_out DESC
LIMIT 10;

-- 数据合理性验证
SELECT 
    '=== 数据合理性检查 ===' as info;

SELECT 
    '负数检查' as check_type,
    COUNT(CASE WHEN current_period_stock < 0 THEN 1 END) as negative_current_stock,
    COUNT(CASE WHEN previous_period_stock < 0 THEN 1 END) as negative_previous_stock,
    COUNT(CASE WHEN current_period_out < 0 OR current_period_in < 0 THEN 1 END) as negative_in_out
FROM jsh_material_period_summary 
WHERE IFNULL(delete_flag, '0') = '0';

-- =========================================
-- Phase 6: 清理和完成
-- =========================================

SELECT '=== Phase 6: 清理临时表和完成 ===' as phase;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS temp_active_materials;
DROP TEMPORARY TABLE IF EXISTS temp_current_stock;
DROP TEMPORARY TABLE IF EXISTS temp_current_in_out;
DROP TEMPORARY TABLE IF EXISTS temp_previous_in_out;

-- 最终完成报告
SELECT 
    '=== 脚本执行完成 ===' as final_status,
    NOW() as completion_time,
    '期间汇总表更新成功' as result,
    CONCAT('本期: ', @current_period_start, ' 至 ', @current_period_end) as current_period_info,
    CONCAT('上期: ', @previous_period_start, ' 至 ', @previous_period_end) as previous_period_info;

-- =========================================
-- 执行说明和故障排除
-- =========================================

/*
脚本功能说明:
1. 自动计算当前业务期间（第一期: 2-7月, 第二期: 8月-次年1月）
2. 更新 jsh_material_period_summary 表的所有字段
3. 本期结存 = 当前库存总和
4. 上期结存 = 本期结存 - 本期入库 + 本期出库
5. 出入库数据按期间时间范围统计已审核单据

执行时间估计:
- 小型数据库 (< 1万商品): 30秒-2分钟
- 中型数据库 (1-10万商品): 2-10分钟  
- 大型数据库 (> 10万商品): 10分钟以上

数据来源:
- 库存数据: jsh_material_current_stock.current_number
- 出入库数据: jsh_depot_head + jsh_depot_item (status='1', basic_number)
- 商品信息: jsh_material + jsh_material_extend (default_flag=1)

故障排除:
1. 如果出现内存不足，可以增加临时表索引或分批处理
2. 如果期间计算错误，检查系统当前日期是否正确
3. 如果数据不一致，确认源表数据完整性

验证方法:
1. 检查 total_records 数量是否合理
2. 验证期间时间范围是否正确
3. 抽查几个商品的库存平衡公式: 上期结存 + 本期入库 - 本期出库 = 本期结存

注意事项:
- 脚本具有幂等性，可以安全重复执行
- 使用了库存平衡公式反推上期结存，适用于无历史库存表的情况
- 支持多租户数据，按 tenant_id 分组处理
- 只处理有库存或有交易记录的活跃商品，提高效率
*/ 