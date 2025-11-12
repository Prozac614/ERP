# 库存预警报表逻辑分析

## 概述

库存预警报表是ERP系统中的一个重要功能，用于监控商品库存是否超出安全库存范围，帮助用户及时调整库存策略。

## 前端界面逻辑

### 1. 页面组件
**文件位置**: `jshERP-web/src/views/report/StockWarningReport.vue`

**主要功能**:
- 提供查询条件筛选（仓库、商品信息、商品类别）
- 显示预警商品列表
- 支持数据导出

**关键配置**:
```javascript
// API接口配置
url: {
  list: "/depotItem/findStockWarningCount"
}

// 显示列配置
defColumns: [
  {title: '序号', dataIndex: 'rowIndex'},
  {title: '仓库', dataIndex: 'depotName'},
  {title: '唛头', dataIndex: 'barCode'},
  {title: '名称', dataIndex: 'mname'},
  {title: '规格', dataIndex: 'mstandard'},
  {title: '型号', dataIndex: 'mmodel'},
  {title: '单位', dataIndex: 'materialUnit'},
  {title: '库存', dataIndex: 'currentNumber'},
  {title: '最低安全库存', dataIndex: 'lowSafeStock'},
  {title: '最高安全库存', dataIndex: 'highSafeStock'},
  {title: '建议入库量', dataIndex: 'lowCritical'},
  {title: '建议出库量', dataIndex: 'highCritical'}
]
```

### 2. 查询参数处理
```javascript
getQueryParams() {
  let param = Object.assign({}, this.queryParam, this.isorter);
  param.field = this.getQueryField();
  param.currentPage = this.ipagination.current;
  param.pageSize = this.ipagination.pageSize-1;  // 注意：这里减1
  return param;
}
```

## 后端逻辑分析

### 1. 控制器层
**文件位置**: `jshERP-boot/src/main/java/com/jsh/erp/controller/DepotItemController.java`

**接口**: `GET /depotItem/findStockWarningCount`

**参数**:
- `currentPage`: 当前页码
- `pageSize`: 每页大小
- `materialParam`: 商品搜索参数
- `depotId`: 仓库ID（可选）
- `categoryId`: 商品类别ID（可选）
- `mpList`: 扩展属性列表

**处理逻辑**:
```java
// 1. 处理仓库权限
List<Long> depotList = new ArrayList<>();
if (depotId != null) {
    depotList.add(depotId);
} else {
    // 获取当前用户有权限的仓库
    JSONArray depotArr = depotService.findDepotByCurrentUser();
    for (Object obj : depotArr) {
        JSONObject object = JSONObject.parseObject(obj.toString());
        depotList.add(object.getLong("id"));
    }
}

// 2. 处理商品类别
List<Long> categoryList = new ArrayList<>();
if (categoryId != null) {
    categoryList = materialService.getListByParentId(categoryId);
}

// 3. 调用服务层查询数据
List<DepotItemStockWarningCount> list = depotItemService.findStockWarningCount(
    (currentPage - 1) * pageSize, pageSize, materialParam, depotList, categoryList);

// 4. 计算建议入库量和出库量
for (DepotItemStockWarningCount disw : list) {
    // 低库存预警：建议入库量 = 最低安全库存 - 当前库存
    if (null != disw.getLowSafeStock() 
            && disw.getCurrentNumber().compareTo(disw.getLowSafeStock()) < 0) {
        disw.setLowCritical(disw.getLowSafeStock().subtract(disw.getCurrentNumber()));
    }
    
    // 高库存预警：建议出库量 = 当前库存 - 最高安全库存
    if (null != disw.getHighSafeStock() 
            && disw.getCurrentNumber().compareTo(disw.getHighSafeStock()) > 0) {
        disw.setHighCritical(disw.getCurrentNumber().subtract(disw.getHighSafeStock()));
    }
}
```

### 2. 服务层
**文件位置**: `jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java`

**方法**:
- `findStockWarningCount()`: 查询预警数据列表
- `findStockWarningCountTotal()`: 查询预警数据总数

```java
public List<DepotItemStockWarningCount> findStockWarningCount(
    Integer offset, Integer rows, String materialParam, 
    List<Long> depotList, List<Long> categoryList) {
    return depotItemMapperEx.findStockWarningCount(offset, rows, materialParam, depotList, categoryList);
}

public int findStockWarningCountTotal(
    String materialParam, List<Long> depotList, List<Long> categoryList) {
    return depotItemMapperEx.findStockWarningCountTotal(materialParam, depotList, categoryList);
}
```

### 3. 数据访问层
**文件位置**: `jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml`

## 核心SQL查询逻辑

### 主查询SQL
```sql
SELECT 
    m.id MId, 
    m.name MName, 
    me.bar_code, 
    m.mfrs MMfrs, 
    m.model MModel, 
    m.standard MStandard,
    m.color MColor, 
    m.brand,
    m.other_field1 MOtherField1,
    m.other_field2 MOtherField2,
    m.other_field3 MOtherField3,
    d.name depotName,
    m.unit MaterialUnit, 
    u.basic_unit unit_name,
    mcs.current_number,
    mis.low_safe_stock, 
    mis.high_safe_stock
FROM jsh_material m
LEFT JOIN jsh_material_extend me ON me.material_id=m.id AND IFNULL(me.delete_Flag,'0') !='1'
LEFT JOIN jsh_material_initial_stock mis ON mis.material_id=m.id AND IFNULL(mis.delete_Flag,'0') !='1'
LEFT JOIN jsh_material_current_stock mcs ON mcs.material_id=m.id AND IFNULL(mcs.delete_Flag,'0') !='1'
LEFT JOIN jsh_unit u ON m.unit_id=u.id AND IFNULL(u.delete_Flag,'0') !='1'
LEFT JOIN jsh_depot d ON d.id=mis.depot_id AND IFNULL(d.delete_flag,'0') !='1'
WHERE 1=1
    AND me.default_flag=1
    AND IFNULL(m.delete_flag,'0') !='1'
    AND mis.depot_id=mcs.depot_id
    AND ((IFNULL(mis.low_safe_stock,0)!=0 AND mcs.current_number < IFNULL(mis.low_safe_stock,0))
         OR (IFNULL(mis.high_safe_stock,0)!=0 AND mcs.current_number > IFNULL(mis.high_safe_stock,0)))
```

### 关键条件分析

#### 1. 表关联条件
- `me.material_id=m.id`: 商品与商品扩展表关联
- `mis.material_id=m.id`: 商品与初始库存表关联
- `mcs.material_id=m.id`: 商品与当前库存表关联
- `mis.depot_id=mcs.depot_id`: **关键条件**，确保初始库存和当前库存是同一仓库

#### 2. 预警触发条件
```sql
((IFNULL(mis.low_safe_stock,0)!=0 AND mcs.current_number < IFNULL(mis.low_safe_stock,0))
 OR (IFNULL(mis.high_safe_stock,0)!=0 AND mcs.current_number > IFNULL(mis.high_safe_stock,0)))
```

**低库存预警**:
- `mis.low_safe_stock != 0`: 必须设置了最低安全库存
- `mcs.current_number < mis.low_safe_stock`: 当前库存小于最低安全库存

**高库存预警**:
- `mis.high_safe_stock != 0`: 必须设置了最高安全库存
- `mcs.current_number > mis.high_safe_stock`: 当前库存大于最高安全库存

#### 3. 其他过滤条件
- `me.default_flag=1`: 只查询默认的商品扩展记录
- `IFNULL(m.delete_flag,'0') !='1'`: 排除已删除的商品
- 支持按仓库、商品类别、商品信息进行筛选

## 数据依赖关系

### 必需的数据表和字段

1. **jsh_material** (商品表)
   - `id`: 商品ID
   - `name`: 商品名称
   - `delete_flag`: 删除标记

2. **jsh_material_extend** (商品扩展表)
   - `material_id`: 关联商品ID
   - `bar_code`: 商品唛头
   - `default_flag`: 默认标记（必须为1）
   - `delete_flag`: 删除标记

3. **jsh_material_initial_stock** (初始库存表) - **关键表**
   - `material_id`: 关联商品ID
   - `depot_id`: 关联仓库ID
   - `low_safe_stock`: 最低安全库存 ⭐
   - `high_safe_stock`: 最高安全库存 ⭐
   - `delete_flag`: 删除标记

4. **jsh_material_current_stock** (当前库存表) - **关键表**
   - `material_id`: 关联商品ID
   - `depot_id`: 关联仓库ID
   - `current_number`: 当前库存数量 ⭐
   - `delete_flag`: 删除标记

5. **jsh_depot** (仓库表)
   - `id`: 仓库ID
   - `name`: 仓库名称
   - `delete_flag`: 删除标记

## 常见问题分析

### 1. 为什么库存预警报表没有数据？

**可能原因**:
1. **缺少安全库存设置**: `jsh_material_initial_stock` 表中没有设置 `low_safe_stock` 或 `high_safe_stock`
2. **缺少当前库存数据**: `jsh_material_current_stock` 表中没有对应的库存记录
3. **仓库ID不匹配**: `mis.depot_id != mcs.depot_id`，导致关联失败
4. **商品ID不匹配**: 表中的 `material_id` 与实际商品ID不对应
5. **库存在安全范围内**: 所有商品的当前库存都在安全库存范围内

### 2. 数据完整性检查

**检查SQL**:
```sql
-- 检查是否有安全库存设置
SELECT COUNT(*) FROM jsh_material_initial_stock 
WHERE delete_flag != '1' 
  AND (low_safe_stock IS NOT NULL AND low_safe_stock != 0 
       OR high_safe_stock IS NOT NULL AND high_safe_stock != 0);

-- 检查是否有当前库存数据
SELECT COUNT(*) FROM jsh_material_current_stock 
WHERE delete_flag != '1';

-- 检查仓库ID匹配情况
SELECT 
    COUNT(*) as total_initial_stock,
    COUNT(mcs.id) as matched_current_stock
FROM jsh_material_initial_stock mis
LEFT JOIN jsh_material_current_stock mcs 
    ON mis.material_id = mcs.material_id 
    AND mis.depot_id = mcs.depot_id 
    AND mcs.delete_flag != '1'
WHERE mis.delete_flag != '1';
```

## 解决方案

### 1. 数据初始化
1. 初始化当前库存数据：调用 `/materialCurrentStock/initializeCurrentStock`
2. 计算安全库存：调用 `/stockWarning/startCalculation`

### 2. 数据修复
1. 清理无效数据：删除商品ID或仓库ID不存在的记录
2. 修复ID匹配问题：确保 `material_id` 和 `depot_id` 正确对应

### 3. 功能验证
1. 检查预警条件是否满足
2. 验证数据关联关系是否正确
3. 测试查询结果是否符合预期

## 总结

库存预警报表的核心逻辑是通过比较当前库存与安全库存来识别需要预警的商品。关键在于确保以下数据的完整性和正确性：

1. **安全库存设置** (`jsh_material_initial_stock`)
2. **当前库存数据** (`jsh_material_current_stock`)
3. **仓库和商品ID的正确匹配**
4. **预警条件的正确判断**

只有当这些条件都满足时，库存预警报表才能正常显示预警数据。
