-- =========================================
-- 检查真实库存数据和期间汇总问题
-- 找出为什么 period summary 数据错误
-- =========================================

SELECT '=== 检查商品4513的真实库存数据 ===' as step;

-- 1. 检查当前实际库存
SELECT 
    ds.material_id,
    ds.material_name,
    ds.current_stock,
    ds.low_safe_stock,
    ds.high_safe_stock,
    '当前实际库存' as data_type
FROM jsh_depot_stock ds 
WHERE ds.material_id = 4513 
    AND ds.delete_flag = '0'
ORDER BY ds.depot_id;

-- 2. 检查最近的库存变动
SELECT 
    '=== 最近7天的库存变动明细 ===' as step;

SELECT 
    di.id,
    dh.number,
    dh.type,
    dh.sub_type,
    di.basic_number,
    di.depot_id,
    dh.oper_time,
    dh.status,
    CASE 
        WHEN dh.type = '入库' AND di.basic_number > 0 THEN CONCAT('+', di.basic_number)
        WHEN dh.type = '出库' AND di.basic_number > 0 THEN CONCAT('-', di.basic_number)
        ELSE CONCAT('?', di.basic_number)
    END as stock_change
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
WHERE di.material_id = 4513
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.status = '1'  -- 只看已审核的
    AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY dh.oper_time DESC;

-- 3. 计算期间汇总应该的正确值
SELECT 
    '=== 计算期间汇总的正确值 ===' as step;

SELECT 
    4513 as material_id,
    '吊胶' as material_name,
    -- 计算当前期间的出库总量
    SUM(CASE 
        WHEN dh.type = '出库' AND dh.oper_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') 
        THEN di.basic_number 
        ELSE 0 
    END) as correct_current_period_out,
    
    -- 计算当前期间的入库总量  
    SUM(CASE 
        WHEN dh.type = '入库' AND dh.oper_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') 
        THEN di.basic_number 
        ELSE 0 
    END) as correct_current_period_in,
    
    -- 计算上期间的出库总量
    SUM(CASE 
        WHEN dh.type = '出库' 
        AND dh.oper_time >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 1 MONTH)
        AND dh.oper_time < DATE_FORMAT(CURDATE(), '%Y-%m-01')
        THEN di.basic_number 
        ELSE 0 
    END) as correct_previous_period_out,
    
    -- 计算上期间的入库总量
    SUM(CASE 
        WHEN dh.type = '入库' 
        AND dh.oper_time >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 1 MONTH)
        AND dh.oper_time < DATE_FORMAT(CURDATE(), '%Y-%m-01')
        THEN di.basic_number 
        ELSE 0 
    END) as correct_previous_period_in,
    
    COUNT(CASE WHEN dh.type = '出库' AND dh.oper_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN 1 END) as current_out_bills,
    COUNT(CASE WHEN dh.type = '入库' AND dh.oper_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN 1 END) as current_in_bills
    
FROM jsh_depot_item di
LEFT JOIN jsh_depot_head dh ON dh.id = di.header_id
WHERE di.material_id = 4513
    AND dh.delete_flag = '0'
    AND di.delete_flag = '0'
    AND dh.status = '1'
    AND dh.oper_time >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 2 MONTH);

-- 4. 对比当前错误的期间汇总数据
SELECT 
    '=== 当前错误的期间汇总数据 ===' as step;

SELECT 
    material_id,
    material_name,
    current_period_stock,
    previous_period_stock,
    current_period_out,
    previous_period_out,
    current_period_in,
    previous_period_in,
    last_calculation_time,
    TIMESTAMPDIFF(DAY, last_calculation_time, NOW()) as days_since_update,
    '❌ 这些数据可能过期或错误' as status
FROM jsh_material_period_summary 
WHERE material_id = 4513 
    AND delete_flag = '0';

-- 5. 检查是否有期间汇总更新的触发逻辑
SELECT 
    '=== 检查期间汇总更新逻辑 ===' as step;

SELECT 
    '期间汇总表可能需要手动更新或修复更新逻辑' as diagnosis,
    'current_period_out 应该不是 0，因为 daily_summary 显示有出库' as issue1,
    '数据最后更新时间是 2025-07-19，已经过期' as issue2,
    '需要检查 refreshMaterialPeriodSummary 方法是否正常工作' as issue3;

-- 6. 建议的修复方案
SELECT 
    '=== 建议的修复方案 ===' as step;

SELECT 
    '1. 立即修复期间汇总数据计算逻辑' as fix1,
    '2. 确保 refreshMaterialPeriodSummary 方法在出入库时被调用' as fix2,
    '3. 手动重新计算商品4513的期间汇总数据' as fix3,
    '4. 验证修复后的数据准确性' as fix4; 