-- ===========================
-- 检查库存表结构脚本
-- ===========================

-- 1. 查看库存表的结构
DESCRIBE jsh_material_current_stock;

-- 2. 查看库存表的一些样例数据
SELECT * FROM jsh_material_current_stock LIMIT 5;

-- 3. 查看所有字段名
SHOW COLUMNS FROM jsh_material_current_stock;

-- 4. 检查是否有相关的库存字段
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'jsh_material_current_stock';

-- 5. 检查库存表是否有数据
SELECT COUNT(*) as total_records FROM jsh_material_current_stock;

-- 6. 查看depot_item表的结构（可能包含库存信息）
DESCRIBE jsh_depot_item;

-- 7. 查看material表的结构
DESCRIBE jsh_material; 