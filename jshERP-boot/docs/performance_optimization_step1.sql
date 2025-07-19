-- ===========================
-- ERP性能优化SQL脚本 - 第一步：创建表和基础索引
-- 执行前请备份数据库！
-- ===========================

-- 1. 每日出库汇总表
DROP TABLE IF EXISTS `jsh_daily_out_summary`;
CREATE TABLE `jsh_daily_out_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint(20) NOT NULL COMMENT '商品ID',
  `bar_code` varchar(50) DEFAULT NULL COMMENT '商品唛头',
  `material_name` varchar(100) DEFAULT NULL COMMENT '商品名称',
  `out_date` date NOT NULL COMMENT '出库日期',
  `total_out_quantity` decimal(24,6) DEFAULT '0.000000' COMMENT '总出库数量',
  `depot_count` int(10) DEFAULT '0' COMMENT '涉及仓库数量',
  `bill_count` int(10) DEFAULT '0' COMMENT '单据数量',
  `last_update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_date_tenant` (`material_id`, `out_date`, `tenant_id`),
  KEY `idx_barcode_date` (`bar_code`, `out_date`),
  KEY `idx_date_range` (`out_date`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_material_tenant` (`material_id`, `tenant_id`),
  KEY `idx_update_time` (`last_update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='每日出库汇总表-性能优化';

-- 2. 商品期间汇总表
DROP TABLE IF EXISTS `jsh_material_period_summary`;
CREATE TABLE `jsh_material_period_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint(20) NOT NULL COMMENT '商品ID',
  `bar_code` varchar(50) DEFAULT NULL COMMENT '商品唛头',
  `material_name` varchar(100) DEFAULT NULL COMMENT '商品名称',
  `material_model` varchar(50) DEFAULT NULL COMMENT '规格',
  `material_unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `current_period_stock` decimal(24,6) DEFAULT '0.000000' COMMENT '本期结存',
  `previous_period_stock` decimal(24,6) DEFAULT '0.000000' COMMENT '上期结存',
  `current_period_out` decimal(24,6) DEFAULT '0.000000' COMMENT '本期出库',
  `previous_period_out` decimal(24,6) DEFAULT '0.000000' COMMENT '上期出库',
  `current_period_in` decimal(24,6) DEFAULT '0.000000' COMMENT '本期入库',
  `previous_period_in` decimal(24,6) DEFAULT '0.000000' COMMENT '上期入库',
  `last_calculation_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后计算时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_tenant` (`material_id`, `tenant_id`),
  KEY `idx_barcode` (`bar_code`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_calculation_time` (`last_calculation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='商品期间汇总表-性能优化';

-- 验证表创建
SELECT 'Step 1 completed: Tables created successfully' as status; 