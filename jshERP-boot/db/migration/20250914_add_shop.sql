-- Add shop_name column to jsh_depot_head (guard for MySQL versions without IF NOT EXISTS)
DELIMITER $$
DROP PROCEDURE IF EXISTS add_shop_name_column $$
CREATE PROCEDURE add_shop_name_column()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jsh_depot_head'
      AND COLUMN_NAME = 'shop_name'
  ) THEN
    ALTER TABLE jsh_depot_head ADD COLUMN shop_name VARCHAR(32) NULL AFTER sales_man;
  END IF;
END $$
CALL add_shop_name_column() $$
DROP PROCEDURE IF EXISTS add_shop_name_column $$
DELIMITER ;

-- Add shop_name column to jsh_daily_out_summary (guard for MySQL versions without IF NOT EXISTS)
DELIMITER $$
DROP PROCEDURE IF EXISTS add_daily_out_summary_shop_name_column $$
CREATE PROCEDURE add_daily_out_summary_shop_name_column()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jsh_daily_out_summary'
      AND COLUMN_NAME = 'shop_name'
  ) THEN
    ALTER TABLE jsh_daily_out_summary ADD COLUMN shop_name VARCHAR(32) NULL AFTER material_name;
  END IF;
END $$
CALL add_daily_out_summary_shop_name_column() $$
DROP PROCEDURE IF EXISTS add_daily_out_summary_shop_name_column $$
DELIMITER ;

-- Create jsh_shop table (if not exists)
CREATE TABLE IF NOT EXISTS jsh_shop (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL,
  tenant_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  delete_flag CHAR(1) NOT NULL DEFAULT '0',
  create_time TIMESTAMP NULL,
  update_time TIMESTAMP NULL,
  UNIQUE KEY uniq_tenant_name (tenant_id, name),
  KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Initialize default shops (店1) for each tenant
INSERT INTO jsh_shop (name, tenant_id, enabled, delete_flag, create_time, update_time)
SELECT '店1', t.tenant_id, 1, '0', NOW(), NOW()
FROM (
  SELECT DISTINCT u.tenant_id
  FROM jsh_user u
  WHERE u.tenant_id IS NOT NULL
) AS t
WHERE NOT EXISTS (
  SELECT 1 FROM jsh_shop s
  WHERE s.tenant_id = t.tenant_id AND s.name = '店1'
);

-- Initialize default shops (店2) for each tenant
INSERT INTO jsh_shop (name, tenant_id, enabled, delete_flag, create_time, update_time)
SELECT '店2', t.tenant_id, 1, '0', NOW(), NOW()
FROM (
  SELECT DISTINCT u.tenant_id
  FROM jsh_user u
  WHERE u.tenant_id IS NOT NULL
) AS t
WHERE NOT EXISTS (
  SELECT 1 FROM jsh_shop s
  WHERE s.tenant_id = t.tenant_id AND s.name = '店2'
);

