-- =========================================
-- 测试缓存行为 - 验证数据更新但缓存未清除的情况
-- 创建时间：2025-01-24
-- =========================================

SELECT '测试缓存行为和数据一致性...' as message;

-- ----------------------------
-- 1. 立即修复汇总数据（模拟应用层更新）
-- ----------------------------
SELECT '=== 立即修复汇总数据 ===' as step;

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
    NULL as tenant_id
FROM jsh_material_extend me 
WHERE me.material_id = 4513 
    AND me.default_flag = 1 
    AND IFNULL(me.delete_flag,'0') != '1'
LIMIT 1
ON DUPLICATE KEY UPDATE
    total_out_quantity = 1.000000,
    bill_count = 1,
    last_update_time = CURRENT_TIMESTAMP;

-- ----------------------------
-- 2. 验证数据库中的数据
-- ----------------------------
SELECT '=== 验证数据库数据 ===' as step;

SELECT 
    material_id,
    material_name,
    out_date,
    total_out_quantity,
    last_update_time,
    '数据库中的真实数据' as data_source
FROM jsh_daily_out_summary 
WHERE material_id = 4513 
    AND out_date = '2025-07-23'
    AND delete_flag = '0';

-- ----------------------------
-- 3. 检查缓存相关提示
-- ----------------------------
SELECT '=== 缓存测试说明 ===' as step;

SELECT 
    '现在请测试前端页面的显示情况：' as instruction,
    '情况1：如果立即显示新数据 → 缓存已清除或不存在' as case1,
    '情况2：如果仍显示旧数据 → 缓存仍在生效' as case2,
    '情况3：等待15分钟后显示新数据 → 缓存自然过期' as case3,
    '这样可以验证正常用户的体验时间' as conclusion;

-- ----------------------------
-- 4. 缓存过期时间参考
-- ----------------------------
SELECT '=== 缓存过期时间参考 ===' as step;

SELECT 
    '后端Redis缓存' as cache_type,
    '15分钟' as expire_time,
    '最长等待时间' as note
UNION ALL
SELECT 
    '前端内存缓存' as cache_type,
    '5分钟' as expire_time,
    '一般等待时间' as note
UNION ALL
SELECT 
    '前端分组缓存' as cache_type,
    '30秒' as expire_time,
    '最短等待时间' as note;

-- ----------------------------
-- 5. 测试结果判断
-- ----------------------------
SELECT '=== 测试结果判断标准 ===' as step;

SELECT 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM jsh_daily_out_summary 
            WHERE material_id = 4513 
            AND out_date = '2025-07-23'
            AND delete_flag = '0'
        ) THEN '✅ 数据库数据已修复'
        ELSE '❌ 数据库数据修复失败'
    END as database_status,
    
    '现在请访问前端首页检查显示结果' as next_step,
    
    CASE 
        WHEN TIME(NOW()) BETWEEN '02:00:00' AND '06:00:00' 
        THEN '当前是凌晨时段，缓存可能被定时任务清理'
        ELSE '正常时段，缓存行为正常'
    END as cache_timing_note;

SELECT '修复完成！请在前端页面验证显示效果。如果显示旧数据，等待15分钟后再检查。' as completion; 