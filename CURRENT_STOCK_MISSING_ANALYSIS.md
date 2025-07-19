# jsh_material_current_stock 表缺少匹配记录的原因分析

## 问题现象

`jsh_material_current_stock` 表中没有一条匹配正确的记录，导致库存预警报表无法显示数据。

## 根本原因分析

### 1. 当前库存表的更新机制

`jsh_material_current_stock` 表不是自动维护的，需要通过以下方式触发更新：

#### 触发更新的场景：
1. **单据操作时**：入库、出库、调拨等操作会调用 `updateCurrentStock()`
2. **商品信息修改时**：修改商品基础信息时会调用 `updateCurrentStockFun()`
3. **手动批量更新**：调用 `batchSetMaterialCurrentStock()` 方法
4. **系统初始化**：需要手动调用初始化方法

#### 关键代码逻辑：
```java
// DepotItemService.updateCurrentStockFun()
public void updateCurrentStockFun(Long mId, Long dId) throws Exception {
    if(mId!=null && dId!=null) {
        // 1. 查询是否已存在记录
        MaterialCurrentStockExample example = new MaterialCurrentStockExample();
        example.createCriteria().andMaterialIdEqualTo(mId).andDepotIdEqualTo(dId)
                .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<MaterialCurrentStock> list = materialCurrentStockMapper.selectByExample(example);
        
        // 2. 创建或更新记录
        MaterialCurrentStock materialCurrentStock = new MaterialCurrentStock();
        materialCurrentStock.setMaterialId(mId);
        materialCurrentStock.setDepotId(dId);
        // 3. 关键：通过getStockByParam计算当前库存
        materialCurrentStock.setCurrentNumber(getStockByParam(dId,mId,null,null));
        
        if(list!=null && list.size()>0) {
            // 更新现有记录
            Long mcsId = list.get(0).getId();
            materialCurrentStock.setId(mcsId);
            materialCurrentStockMapper.updateByPrimaryKeySelective(materialCurrentStock);
        } else {
            // 插入新记录
            materialCurrentStockMapper.insertSelective(materialCurrentStock);
        }
    }
}
```

### 2. 库存计算逻辑

当前库存通过 `getStockByParam()` 方法计算：

```java
public BigDecimal getStockByParamWithDepotList(List<Long> depotList, Long mId, String beginTime, String endTime) {
    // 1. 获取初始库存
    BigDecimal initStock = materialService.getInitStockByMidAndDepotList(depotList, mId);
    
    // 2. 获取盘点调整数量
    BigDecimal stockCheckSum = depotItemMapperEx.getStockCheckSumByDepotList(depotList, mId, forceFlag, beginTime, endTime);
    
    // 3. 获取出入库统计
    DepotItemVo4Stock stockObj = depotItemMapperEx.getStockByParamWithDepotList(depotList, mId, forceFlag, inOutManageFlag, beginTime, endTime);
    
    // 4. 计算最终库存 = 初始库存 + 入库总量 - 出库总量 + 盘点调整
    return initStock.add(inSum).subtract(outSum).add(stockCheckSum);
}
```

### 3. 为什么会缺少记录

#### 3.1 系统初始化问题
- **新安装的系统**：`jsh_material_current_stock` 表为空
- **数据迁移**：从旧版本升级时，当前库存表可能没有正确初始化
- **数据清理**：误删除了当前库存数据

#### 3.2 业务流程问题
- **没有业务单据**：如果系统中没有入库、出库等业务单据，就不会触发当前库存的计算
- **单据未审核**：未审核的单据不会影响库存计算
- **权限问题**：某些操作可能因为权限问题没有正确执行

#### 3.3 数据一致性问题
- **商品ID不匹配**：商品表和当前库存表的ID不一致
- **仓库ID不匹配**：仓库表和当前库存表的ID不一致
- **租户隔离问题**：多租户环境下数据隔离导致的问题

## 诊断方法

### 1. 检查表数据状态

```sql
-- 检查当前库存表记录数
SELECT COUNT(*) as total_records FROM jsh_material_current_stock WHERE delete_flag != '1';

-- 检查商品数量
SELECT COUNT(*) as total_materials FROM jsh_material WHERE delete_flag != '1';

-- 检查仓库数量
SELECT COUNT(*) as total_depots FROM jsh_depot WHERE delete_flag != '1';

-- 检查是否有匹配的记录
SELECT 
    m.id as material_id,
    m.name as material_name,
    d.id as depot_id,
    d.name as depot_name,
    mcs.current_number
FROM jsh_material m
CROSS JOIN jsh_depot d
LEFT JOIN jsh_material_current_stock mcs 
    ON m.id = mcs.material_id 
    AND d.id = mcs.depot_id 
    AND mcs.delete_flag != '1'
WHERE m.delete_flag != '1' 
  AND d.delete_flag != '1'
  AND mcs.id IS NULL
LIMIT 10;
```

### 2. 检查业务数据

```sql
-- 检查是否有业务单据
SELECT COUNT(*) as total_depot_items FROM jsh_depot_item WHERE delete_flag != '1';

-- 检查是否有初始库存设置
SELECT COUNT(*) as total_initial_stock FROM jsh_material_initial_stock WHERE delete_flag != '1';

-- 检查单据审核状态
SELECT 
    dh.status,
    COUNT(*) as count
FROM jsh_depot_head dh
WHERE dh.delete_flag != '1'
GROUP BY dh.status;
```

## 解决方案

### 方案1：全量初始化（推荐）

#### 步骤1：创建初始化接口
```java
@PostMapping(value = "/initializeAllCurrentStock")
public BaseResponseInfo initializeAllCurrentStock(HttpServletRequest request) {
    BaseResponseInfo res = new BaseResponseInfo();
    try {
        // 获取所有商品
        List<Material> materials = materialService.getAllList();
        // 获取所有仓库
        List<Depot> depots = depotService.getAllList();
        
        int totalCount = 0;
        int successCount = 0;
        
        for (Material material : materials) {
            for (Depot depot : depots) {
                try {
                    depotItemService.updateCurrentStockFun(material.getId(), depot.getId());
                    successCount++;
                } catch (Exception e) {
                    logger.error("初始化商品{}在仓库{}的当前库存失败", 
                            material.getId(), depot.getId(), e);
                }
                totalCount++;
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failedCount", totalCount - successCount);
        
        res.code = 200;
        res.data = result;
        
    } catch (Exception e) {
        res.code = 500;
        res.data = "初始化失败: " + e.getMessage();
    }
    return res;
}
```

#### 步骤2：执行初始化
```bash
curl -X POST "http://localhost:9999/jshERP-boot/materialCurrentStock/initializeCurrentStock"
```

### 方案2：SQL直接初始化

```sql
-- 为所有商品和仓库组合创建当前库存记录
INSERT INTO jsh_material_current_stock (material_id, depot_id, current_number, delete_flag)
SELECT 
    m.id as material_id,
    d.id as depot_id,
    0 as current_number,  -- 初始设为0，后续通过业务逻辑计算
    '0' as delete_flag
FROM jsh_material m
CROSS JOIN jsh_depot d
LEFT JOIN jsh_material_current_stock mcs 
    ON m.id = mcs.material_id 
    AND d.id = mcs.depot_id 
    AND mcs.delete_flag != '1'
WHERE m.delete_flag != '1' 
  AND d.delete_flag != '1'
  AND mcs.id IS NULL;
```

### 方案3：基于业务数据计算

```sql
-- 基于实际业务数据计算并插入当前库存
INSERT INTO jsh_material_current_stock (material_id, depot_id, current_number, delete_flag)
SELECT 
    material_depot.material_id,
    material_depot.depot_id,
    COALESCE(
        -- 初始库存
        (SELECT SUM(IFNULL(mis.number, 0)) 
         FROM jsh_material_initial_stock mis 
         WHERE mis.material_id = material_depot.material_id 
           AND mis.depot_id = material_depot.depot_id 
           AND mis.delete_flag != '1')
        +
        -- 入库数量
        (SELECT SUM(IFNULL(di.basic_number, 0)) 
         FROM jsh_depot_item di
         INNER JOIN jsh_depot_head dh ON di.header_id = dh.id
         WHERE di.material_id = material_depot.material_id 
           AND (di.depot_id = material_depot.depot_id OR di.another_depot_id = material_depot.depot_id)
           AND dh.type = '入库'
           AND dh.status = '2'  -- 已审核
           AND di.delete_flag != '1' 
           AND dh.delete_flag != '1')
        -
        -- 出库数量
        (SELECT SUM(IFNULL(di.basic_number, 0)) 
         FROM jsh_depot_item di
         INNER JOIN jsh_depot_head dh ON di.header_id = dh.id
         WHERE di.material_id = material_depot.material_id 
           AND di.depot_id = material_depot.depot_id
           AND dh.type = '出库'
           AND dh.status = '2'  -- 已审核
           AND di.delete_flag != '1' 
           AND dh.delete_flag != '1')
        , 0
    ) as current_number,
    '0' as delete_flag
FROM (
    SELECT DISTINCT m.id as material_id, d.id as depot_id
    FROM jsh_material m
    CROSS JOIN jsh_depot d
    WHERE m.delete_flag != '1' AND d.delete_flag != '1'
) material_depot
LEFT JOIN jsh_material_current_stock mcs 
    ON material_depot.material_id = mcs.material_id 
    AND material_depot.depot_id = mcs.depot_id 
    AND mcs.delete_flag != '1'
WHERE mcs.id IS NULL;
```

## 验证方法

### 1. 检查初始化结果
```sql
-- 检查记录数是否正确
SELECT 
    (SELECT COUNT(*) FROM jsh_material WHERE delete_flag != '1') * 
    (SELECT COUNT(*) FROM jsh_depot WHERE delete_flag != '1') as expected_records,
    (SELECT COUNT(*) FROM jsh_material_current_stock WHERE delete_flag != '1') as actual_records;

-- 检查是否有匹配问题
SELECT COUNT(*) as mismatch_count
FROM jsh_material_current_stock mcs
LEFT JOIN jsh_material m ON mcs.material_id = m.id AND m.delete_flag != '1'
LEFT JOIN jsh_depot d ON mcs.depot_id = d.id AND d.delete_flag != '1'
WHERE mcs.delete_flag != '1' 
  AND (m.id IS NULL OR d.id IS NULL);
```

### 2. 测试库存预警功能
```bash
# 重新计算安全库存
curl -X POST "http://localhost:9999/jshERP-boot/stockWarning/startCalculation"

# 检查预警报表
curl "http://localhost:9999/jshERP-boot/depotItem/findStockWarningCount?currentPage=1&pageSize=10"
```

## 预防措施

### 1. 定期数据检查
- 定期检查当前库存表的数据完整性
- 监控库存计算的准确性

### 2. 业务流程优化
- 确保所有库存相关操作都正确触发当前库存更新
- 添加数据一致性检查

### 3. 系统初始化流程
- 在系统部署时自动初始化当前库存表
- 提供数据修复工具

## 总结

`jsh_material_current_stock` 表缺少匹配记录的主要原因是：
1. **系统初始化不完整**：新系统或升级后没有正确初始化当前库存数据
2. **业务流程缺失**：没有足够的业务单据触发库存计算
3. **数据一致性问题**：商品ID或仓库ID不匹配

**推荐解决方案**：使用全量初始化方法，为所有商品和仓库组合创建当前库存记录，然后重新计算安全库存，最后验证库存预警功能是否正常工作。
