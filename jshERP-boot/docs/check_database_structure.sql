-- ===========================
-- 检查数据库结构脚本
-- ===========================

-- 1. 查看所有表名
SHOW TABLES;

-- 2. 查看包含 material 的表
SHOW TABLES LIKE '%material%';

-- 3. 查看包含 stock 或 depot 的表  
SHOW TABLES LIKE '%stock%';
SHOW TABLES LIKE '%depot%';

-- 4. 查看关键表的结构
DESCRIBE jsh_material;
DESCRIBE jsh_material_extend; 
DESCRIBE jsh_depot_head;
DESCRIBE jsh_depot_item;

-- 5. 查看是否有库存相关的表
SHOW TABLES LIKE '%inventory%';
SHOW TABLES LIKE '%current%';

-- 6. 查看 depot_item 表的一些样例数据
SELECT * FROM jsh_depot_item LIMIT 5;

-- 7. 查看 depot_head 表的一些样例数据
SELECT * FROM jsh_depot_head LIMIT 5; 