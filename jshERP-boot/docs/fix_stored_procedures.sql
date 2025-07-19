-- ===========================
-- 修复存储过程脚本
-- ===========================

DELIMITER $$

-- 删除旧的存储过程
DROP PROCEDURE IF EXISTS `update_material_period_summary`$$

-- 重新创建简化版的商品期间汇总存储过程
CREATE PROCEDURE `update_material_period_summary`(
    IN target_tenant_id BIGINT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    
    -- 清空现有数据
    DELETE FROM jsh_material_period_summary 
    WHERE target_tenant_id IS NULL OR tenant_id = target_tenant_id;
    
    -- 插入简化的汇总数据（不包含库存，只包含出入库数据）
    INSERT INTO jsh_material_period_summary (
        material_id, bar_code, material_name, material_model, material_unit,
        current_period_out, previous_period_out,
        current_period_in, previous_period_in,
        tenant_id
    )
    SELECT 
        m.id as material_id,
        me.bar_code,
        m.name as material_name,
        m.model as material_model,
        m.unit as material_unit,
        COALESCE(co.current_out, 0) as current_period_out,
        COALESCE(po.previous_out, 0) as previous_period_out,
        COALESCE(ci.current_in, 0) as current_period_in,
        COALESCE(pi.previous_in, 0) as previous_period_in,
        m.tenant_id
    FROM jsh_material m
    LEFT JOIN jsh_material_extend me ON m.id = me.material_id 
        AND me.default_flag = '1' 
        AND me.tenant_id = m.tenant_id
    LEFT JOIN (
        -- 本期出库（最近30天）
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(CASE WHEN di.oper_number IS NULL THEN 0 ELSE di.oper_number END) as current_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY di.material_id, dh.tenant_id
    ) co ON m.id = co.material_id AND m.tenant_id = co.tenant_id
    LEFT JOIN (
        -- 上期出库（30-60天前）
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(CASE WHEN di.oper_number IS NULL THEN 0 ELSE di.oper_number END) as previous_out
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '出库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 60 DAY)
        AND dh.oper_time < DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY di.material_id, dh.tenant_id
    ) po ON m.id = po.material_id AND m.tenant_id = po.tenant_id
    LEFT JOIN (
        -- 本期入库（最近30天）
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(CASE WHEN di.oper_number IS NULL THEN 0 ELSE di.oper_number END) as current_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY di.material_id, dh.tenant_id
    ) ci ON m.id = ci.material_id AND m.tenant_id = ci.tenant_id
    LEFT JOIN (
        -- 上期入库（30-60天前）
        SELECT 
            di.material_id, 
            dh.tenant_id,
            SUM(CASE WHEN di.oper_number IS NULL THEN 0 ELSE di.oper_number END) as previous_in
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.type = '入库' AND dh.status = '1' 
        AND dh.delete_flag = '0' AND di.delete_flag = '0'
        AND dh.oper_time >= DATE_SUB(CURDATE(), INTERVAL 60 DAY)
        AND dh.oper_time < DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY di.material_id, dh.tenant_id
    ) pi ON m.id = pi.material_id AND m.tenant_id = pi.tenant_id
    WHERE m.delete_flag = '0'
    AND (target_tenant_id IS NULL OR m.tenant_id = target_tenant_id);
    
    COMMIT;
    
    SELECT 'Material period summary updated successfully (without stock data)' as result;
END$$

DELIMITER ;

-- 验证修复
SELECT 'Stored procedures fixed successfully' as status; 