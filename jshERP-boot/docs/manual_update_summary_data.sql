-- =========================================
-- 手动补充历史汇总数据脚本
-- 解决因缺少触发器导致的汇总表数据过时问题
-- 创建时间：2025-01-24
-- =========================================

SELECT '开始手动补充历史汇总数据...' as message;

-- ----------------------------
-- 1. 检查当前汇总表状态
-- ----------------------------
SELECT '=== 检查汇总表当前状态 ===' as step;

SELECT 
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date,
    COUNT(DISTINCT out_date) as date_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- ----------------------------
-- 2. 检查需要补充的时间范围
-- ----------------------------
SELECT '=== 检查需要补充的时间范围 ===' as step;

-- 查看最近30天有哪些已审核的出库单据
SELECT 
    DATE(oper_time) as oper_date,
    COUNT(*) as bill_count,
    COUNT(DISTINCT di.material_id) as material_count
FROM jsh_depot_head dh
LEFT JOIN jsh_depot_item di ON di.header_id = dh.id
WHERE dh.type = '出库'
    AND dh.status = '1'
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY DATE(dh.oper_time)
ORDER BY oper_date DESC;

-- ----------------------------
-- 3. 手动补充最近30天的汇总数据
-- ----------------------------
SELECT '=== 开始补充最近30天的汇总数据 ===' as step;

INSERT INTO jsh_daily_out_summary (
    material_id, bar_code, material_name, out_date, 
    total_out_quantity, depot_count, bill_count, tenant_id
)
SELECT 
    di.material_id,
    me.bar_code,
    m.name as material_name,
    DATE(dh.oper_time) as out_date,
    SUM(IFNULL(di.basic_number, 0)) as total_out_quantity,
    COUNT(DISTINCT di.depot_id) as depot_count,
    COUNT(DISTINCT dh.id) as bill_count,
    dh.tenant_id
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
LEFT JOIN jsh_material m ON m.id = di.material_id
LEFT JOIN jsh_material_extend me ON me.material_id = m.id 
    AND IFNULL(me.delete_flag,'0') != '1' 
    AND me.default_flag = 1
WHERE dh.type = '出库'
    AND IFNULL(di.delete_flag,'0') != '1'
    AND IFNULL(dh.delete_flag,'0') != '1'
    AND IFNULL(m.delete_flag,'0') != '1'
    AND dh.status = '1'  -- 只处理已审核的单据
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY di.material_id, DATE(dh.oper_time), dh.tenant_id
ON DUPLICATE KEY UPDATE
    bar_code = VALUES(bar_code),
    material_name = VALUES(material_name),
    total_out_quantity = VALUES(total_out_quantity),
    depot_count = VALUES(depot_count),
    bill_count = VALUES(bill_count),
    last_update_time = CURRENT_TIMESTAMP;

-- ----------------------------
-- 4. 显示补充结果
-- ----------------------------
SELECT '=== 补充完成，显示结果 ===' as step;

-- 检查汇总表更新后的状态
SELECT 
    COUNT(*) as total_records,
    MIN(out_date) as earliest_date,
    MAX(out_date) as latest_date,
    COUNT(DISTINCT out_date) as date_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

-- 显示最近10天的汇总数据
SELECT 
    out_date,
    COUNT(DISTINCT material_id) as material_count,
    SUM(total_out_quantity) as daily_total_out,
    SUM(bill_count) as daily_bill_count
FROM jsh_daily_out_summary
WHERE delete_flag = '0'
    AND out_date >= DATE_SUB(CURDATE(), INTERVAL 10 DAY)
GROUP BY out_date
ORDER BY out_date DESC;

-- ----------------------------
-- 5. 验证特定商品的数据
-- ----------------------------
SELECT '=== 验证特定商品的汇总数据 ===' as step;

-- 选择一个有数据的商品进行验证
SELECT 
    ds.material_id,
    ds.material_name,
    ds.out_date,
    ds.total_out_quantity as summary_quantity,
    -- 直接计算的数量（用于验证）
    IFNULL((
        SELECT SUM(di.basic_number)
        FROM jsh_depot_item di
        LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
        WHERE di.material_id = ds.material_id
            AND dh.type = '出库'
            AND dh.status = '1'
            AND DATE(dh.oper_time) = ds.out_date
            AND IFNULL(di.delete_flag,'0') != '1'
            AND IFNULL(dh.delete_flag,'0') != '1'
    ), 0) as calculated_quantity,
    ds.last_update_time
FROM jsh_daily_out_summary ds
WHERE ds.delete_flag = '0'
    AND ds.out_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    AND ds.total_out_quantity > 0
ORDER BY ds.out_date DESC, ds.material_id
LIMIT 5;

-- ----------------------------
-- 6. 最终状态报告
-- ----------------------------
SELECT '=== 最终状态报告 ===' as step;

SELECT 
    CASE 
        WHEN MAX(out_date) >= CURDATE() - INTERVAL 1 DAY 
        THEN '✅ 汇总数据已更新到最新'
        WHEN MAX(out_date) >= CURDATE() - INTERVAL 7 DAY 
        THEN '⚠️  汇总数据较新但可能有缺失'
        ELSE '❌ 汇总数据仍然过时'
    END as data_status,
    MAX(out_date) as latest_date,
    COUNT(DISTINCT out_date) as date_count,
    COUNT(*) as total_records
FROM jsh_daily_out_summary
WHERE delete_flag = '0';

SELECT '历史数据补充完成！现在应用层代码将自动维护最新数据。' as completion_message; 