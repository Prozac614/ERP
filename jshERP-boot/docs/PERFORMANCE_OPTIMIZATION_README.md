# ERP系统性能优化部署指南

## 📋 概述

本性能优化方案专门针对**查询多于修改**的使用场景设计，通过数据库预聚合、多级缓存和智能更新机制，显著提升系统性能。

## 🚀 性能提升预期

### 查询性能
- **响应时间**: 从 2-5秒 降低到 100-500ms
- **并发能力**: 提升 5-10倍
- **缓存命中率**: 85-95%

### 资源消耗
- **CPU使用**: 减少 60-80%
- **内存占用**: 减少 30-50%
- **网络传输**: 减少 40-60%

## 📦 部署步骤

### 1. 数据库部署

#### 1.1 执行SQL优化脚本
```bash
# 连接到MySQL数据库
mysql -u username -p database_name

# 执行性能优化脚本
source /path/to/performance_optimization.sql
```

#### 1.2 验证表和索引创建
```sql
-- 检查汇总表是否创建成功
SHOW TABLES LIKE '%summary%';

-- 检查索引是否创建成功
SHOW INDEX FROM jsh_depot_head WHERE Key_name LIKE 'idx_%';
SHOW INDEX FROM jsh_depot_item WHERE Key_name LIKE 'idx_%';
```

#### 1.3 初始化历史数据
```sql
-- 初始化最近30天的汇总数据（可选，根据数据量调整）
-- 该脚本已包含在 performance_optimization.sql 中
-- 如需手动执行，请在业务低峰期进行
```

### 2. 后端部署

#### 2.1 确认依赖
确保以下依赖已添加到 `pom.xml`:
```xml
<!-- Redis缓存支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 缓存注解支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

#### 2.2 Redis配置
在 `application.properties` 中添加Redis配置:
```properties
# Redis配置（可选，不配置则只使用本地缓存）
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0
spring.redis.timeout=3000
spring.redis.jedis.pool.max-active=20
spring.redis.jedis.pool.max-wait=-1
spring.redis.jedis.pool.max-idle=10
spring.redis.jedis.pool.min-idle=5
```

#### 2.3 重新编译和部署
```bash
# 编译项目
mvn clean package -DskipTests

# 重启应用服务
# 方式1：使用启动脚本
./restart.sh

# 方式2：直接启动jar包
java -jar jshERP-boot-*.jar
```

### 3. 前端部署

#### 3.1 构建前端项目
```bash
cd jshERP-web
npm run build
```

#### 3.2 部署到Web服务器
```bash
# 将构建产物复制到Web服务器目录
cp -r dist/* /var/www/html/erp/

# 重启Web服务器（如nginx）
systemctl restart nginx
```

## 🔧 配置说明

### 定时任务配置

系统已自动配置以下定时任务：

| 任务 | 频率 | 说明 |
|------|------|------|
| 刷新最近3天汇总 | 每小时 | 保持热点数据实时性 |
| 刷新期间汇总 | 每天凌晨2点 | 全量数据准确性 |
| 清理过期缓存 | 每天凌晨1点 | 保持缓存时效性 |
| 全量数据刷新 | 每周日凌晨3点 | 数据完整性保证 |

### 缓存策略配置

```java
// 缓存配置参数
- 短期缓存: 15分钟 (实时数据)
- 中期缓存: 2小时 (统计数据) 
- 长期缓存: 24小时 (基础数据)
```

## 📊 监控和维护

### 1. 性能监控

#### 1.1 查看缓存统计
```
GET /depotItem/getCacheStats
```
返回缓存命中率、条目数等统计信息。

#### 1.2 查看查询性能
前端会显示每次查询的性能统计，包括：
- 查询耗时
- 性能评级
- 缓存命中状态
- 数据量统计

### 2. 手动维护操作

#### 2.1 清除缓存
```
POST /depotItem/clearCache
```

#### 2.2 刷新汇总数据
```bash
# 刷新最近7天的每日汇总
POST /depotItem/refreshSummaryData?type=daily&days=7

# 刷新商品期间汇总
POST /depotItem/refreshSummaryData?type=period
```

#### 2.3 数据库维护
```sql
-- 清理6个月前的汇总数据
DELETE FROM jsh_daily_out_summary 
WHERE out_date < DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- 优化表
OPTIMIZE TABLE jsh_daily_out_summary;
OPTIMIZE TABLE jsh_material_period_summary;
```

## 🚨 注意事项

### 1. 部署前准备
- **数据备份**: 执行SQL脚本前务必备份数据库
- **业务停机**: 建议在业务低峰期执行部署
- **测试环境**: 先在测试环境验证所有功能

### 2. 性能监控
- **磁盘空间**: 汇总表会占用额外存储空间
- **内存使用**: Redis缓存会增加内存消耗
- **网络带宽**: 需要监控Redis连接

### 3. 故障处理
- **数据不一致**: 可通过刷新汇总数据解决
- **缓存异常**: 可清除所有缓存，系统会自动重建
- **性能下降**: 检查定时任务是否正常运行

## 🔄 回滚方案

如需回滚到原始版本：

### 1. 数据库回滚
```sql
-- 删除新增的汇总表（可选）
DROP TABLE IF EXISTS jsh_daily_out_summary;
DROP TABLE IF EXISTS jsh_material_period_summary;

-- 删除新增的索引（可选）
ALTER TABLE jsh_depot_head DROP INDEX idx_type_status_time;
-- ... 删除其他新增索引
```

### 2. 代码回滚
```bash
# 恢复原始代码版本
git checkout <previous_commit>

# 重新编译部署
mvn clean package -DskipTests
```

## 📞 技术支持

如遇到问题，请检查：
1. 数据库连接是否正常
2. Redis服务是否启动（如已配置）
3. 定时任务是否按预期执行
4. 应用日志中的错误信息

---

**注意**: 本优化方案适用于查询频繁、修改较少的业务场景。如业务模式不符，请谨慎使用。 