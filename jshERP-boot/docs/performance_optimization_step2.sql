-- ===========================
-- ERP性能优化SQL脚本 - 第二步：优化现有表索引
-- ===========================

-- 检查并添加单据主表索引（如果不存在）
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_head' AND index_name = 'idx_type_status_time') > 0,
  'SELECT "Index idx_type_status_time already exists"',
  'ALTER TABLE `jsh_depot_head` ADD INDEX `idx_type_status_time` (`type`, `status`, `oper_time`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_head' AND index_name = 'idx_oper_time_type') > 0,
  'SELECT "Index idx_oper_time_type already exists"',
  'ALTER TABLE `jsh_depot_head` ADD INDEX `idx_oper_time_type` (`oper_time`, `type`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_head' AND index_name = 'idx_tenant_time') > 0,
  'SELECT "Index idx_tenant_time already exists"',
  'ALTER TABLE `jsh_depot_head` ADD INDEX `idx_tenant_time` (`tenant_id`, `oper_time`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加单据子表索引（如果不存在）
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_item' AND index_name = 'idx_material_header_time') > 0,
  'SELECT "Index idx_material_header_time already exists"',
  'ALTER TABLE `jsh_depot_item` ADD INDEX `idx_material_header_time` (`material_id`, `header_id`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_item' AND index_name = 'idx_depot_material') > 0,
  'SELECT "Index idx_depot_material already exists"',
  'ALTER TABLE `jsh_depot_item` ADD INDEX `idx_depot_material` (`depot_id`, `material_id`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
   WHERE table_schema = DATABASE() AND table_name = 'jsh_depot_item' AND index_name = 'idx_tenant_material') > 0,
  'SELECT "Index idx_tenant_material already exists"',
  'ALTER TABLE `jsh_depot_item` ADD INDEX `idx_tenant_material` (`tenant_id`, `material_id`)'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证索引创建
SELECT 'Step 2 completed: Indexes optimized successfully' as status; 