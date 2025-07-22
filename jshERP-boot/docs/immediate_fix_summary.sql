-- =========================================
-- 立即修复汇总数据 - 针对刚才的出库单据
-- 创建时间：2025-01-24
-- =========================================

SELECT '立即修复刚才出库单据的汇总数据...' as message;

-- ----------------------------
-- 1. 手动为刚才的出库单据创建汇总数据
-- ----------------------------
SELECT '=== 手动创建汇总数据 ===' as step;

-- 为商品ID 4513 (吊胶) 在 2025-07-23 创建汇总记录
INSERT INTO jsh_daily_out_summary (
    material_id, bar_code, material_name, out_date, 
    total_out_quantity, depot_count, bill_count, tenant_id
)
SELECT 
    4513 as material_id,
    me.bar_code,
    '吊胶' as material_name,
    '2025-07-23' as out_date,
    1.000000 as total_out_quantity,
    1 as depot_count,
    1 as bill_count,
    140 as tenant_id  -- 假设租户ID，请根据实际情况调整
FROM jsh_material_extend me 
WHERE me.material_id = 4513 
    AND me.default_flag = 1 
    AND IFNULL(me.delete_flag,'0') != '1'
LIMIT 1
ON DUPLICATE KEY UPDATE
    total_out_quantity = total_out_quantity + 1.000000,
    bill_count = bill_count + 1,
    last_update_time = CURRENT_TIMESTAMP;

-- ----------------------------
-- 2. 验证修复结果
-- ----------------------------
SELECT '=== 验证修复结果 ===' as step;

SELECT 
    material_id,
    material_name,
    out_date,
    total_out_quantity,
    last_update_time
FROM jsh_daily_out_summary 
WHERE material_id = 4513 
    AND out_date = '2025-07-23'
    AND delete_flag = '0';

-- ----------------------------
-- 3. 清除缓存（手动执行API）
-- ----------------------------
SELECT '=== 缓存清除提醒 ===' as step;

SELECT 
    '手动修复完成后，请执行以下API清除缓存：' as instruction,
    'curl -X POST http://your-domain/depotItem/clearCache' as clear_cache_api,
    '或者在浏览器中访问该API端点' as alternative;

SELECT '立即修复完成！请刷新前端页面查看结果。' as completion; 