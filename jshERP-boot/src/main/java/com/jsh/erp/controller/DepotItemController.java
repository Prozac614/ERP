package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.vo.DepotItemStockWarningCount;
import com.jsh.erp.datasource.vo.DepotItemVoBatchNumberList;
import com.jsh.erp.datasource.vo.InOutPriceVo;
import com.jsh.erp.datasource.vo.MaterialStockPeriodVo;
import com.jsh.erp.datasource.vo.RefreshTaskStatus;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.service.DepotService;
import com.jsh.erp.service.DepotHeadService;
import com.jsh.erp.service.DepotItemService;
import com.jsh.erp.service.DepotItemOptimizedService;
import com.jsh.erp.service.MaterialService;
import com.jsh.erp.service.RoleService;
import com.jsh.erp.service.SummaryRefreshTaskService;
import com.jsh.erp.service.SystemConfigService;
import com.jsh.erp.service.UnitService;
import com.jsh.erp.service.UserService;
import com.jsh.erp.utils.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jxl.Sheet;
import jxl.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jsh.erp.utils.ResponseJsonUtil.returnJson;

/**
 * @author ji-sheng-hua 管伊佳erp
 */
@RestController
@RequestMapping(value = "/depotItem")
@Api(tags = { "单据明细" })
public class DepotItemController {
    private Logger logger = LoggerFactory.getLogger(DepotItemController.class);

    @Resource
    private DepotHeadService depotHeadService;

    @Resource
    private DepotItemService depotItemService;

    @Resource
    private MaterialService materialService;

    @Resource
    private UnitService unitService;

    @Resource
    private DepotService depotService;

    @Resource
    private RoleService roleService;

    @Resource
    private UserService userService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private DepotItemOptimizedService depotItemOptimizedService;

    @Resource
    private SummaryRefreshTaskService summaryRefreshTaskService;

    @Resource
    private com.jsh.erp.datasource.mappers.DepotItemMapperEx depotItemMapperEx;

    @Value(value = "${file.uploadType}")
    private Long fileUploadType;

    /**
     * 根据仓库和商品查询单据列表
     * 
     * @param mId
     * @param request
     * @return
     */
    @GetMapping(value = "/findDetailByDepotIdsAndMaterialId")
    @ApiOperation(value = "根据仓库和商品查询单据列表")
    public String findDetailByDepotIdsAndMaterialId(
            @RequestParam(value = Constants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(value = Constants.CURRENT_PAGE, required = false) Integer currentPage,
            @RequestParam(value = "depotIds", required = false) String depotIds,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "batchNumber", required = false) String batchNumber,
            @RequestParam(value = "number", required = false) String number,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam("materialId") Long mId,
            HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        if (StringUtil.isNotEmpty(beginTime)) {
            beginTime = beginTime + BusinessConstants.DAY_FIRST_TIME;
        }
        if (StringUtil.isNotEmpty(endTime)) {
            endTime = endTime + BusinessConstants.DAY_LAST_TIME;
        }
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        List<DepotItemVo4DetailByTypeAndMId> list = depotItemService.findDetailByDepotIdsAndMaterialIdList(depotIds,
                forceFlag, inOutManageFlag, sku,
                batchNumber, StringUtil.toNull(number), beginTime, endTime, mId, (currentPage - 1) * pageSize,
                pageSize);
        JSONArray dataArray = new JSONArray();
        if (list != null) {
            for (DepotItemVo4DetailByTypeAndMId d : list) {
                JSONObject item = new JSONObject();
                item.put("number", d.getNumber()); // 编号
                item.put("barCode", d.getBarCode()); // 唛头
                item.put("materialName", d.getMaterialName()); // 名称
                String type = d.getType();
                String subType = d.getSubType();
                if (("其它").equals(type)) {
                    item.put("type", subType); // 进出类型
                } else {
                    item.put("type", subType + type); // 进出类型
                }
                item.put("depotName", d.getDepotName()); // 仓库名称
                item.put("basicNumber", d.getBnum()); // 数量
                item.put("unitPrice", d.getUnitPrice()); // 单价
                item.put("allPrice", d.getAllPrice()); // 金额
                item.put("operTime", Tools.getCenternTime(d.getOtime())); // 时间
                dataArray.add(item);
            }
        }
        if (list == null) {
            objectMap.put("rows", new ArrayList<Object>());
            objectMap.put("total", BusinessConstants.DEFAULT_LIST_NULL_NUMBER);
            return returnJson(objectMap, "查找不到数据", ErpInfo.OK.code);
        }
        objectMap.put("rows", dataArray);
        objectMap.put("total",
                depotItemService.findDetailByDepotIdsAndMaterialIdCount(depotIds, forceFlag, inOutManageFlag, sku,
                        batchNumber, StringUtil.toNull(number), beginTime, endTime, mId));
        return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
    }

    /**
     * 根据商品唛头和仓库id查询库存数量
     * 
     * @param depotId
     * @param barCode
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/findStockByDepotAndBarCode")
    @ApiOperation(value = "根据商品唛头和仓库id查询库存数量")
    public BaseResponseInfo findStockByDepotAndBarCode(
            @RequestParam(value = "depotId", required = false) Long depotId,
            @RequestParam("barCode") String barCode,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            BigDecimal stock = BigDecimal.ZERO;
            List<MaterialVo4Unit> list = materialService.getMaterialByBarCode(barCode);
            if (list != null && list.size() > 0) {
                MaterialVo4Unit materialVo4Unit = list.get(0);
                if (StringUtil.isNotEmpty(materialVo4Unit.getSku())) {
                    stock = depotItemService.getSkuStockByParam(depotId, materialVo4Unit.getMeId(), null, null);
                } else {
                    stock = depotItemService.getCurrentStockByParam(depotId, materialVo4Unit.getId());
                    if (materialVo4Unit.getUnitId() != null) {
                        Unit unit = unitService.getUnit(materialVo4Unit.getUnitId());
                        String commodityUnit = materialVo4Unit.getCommodityUnit();
                        stock = unitService.parseStockByUnit(stock, unit, commodityUnit);
                    }
                }
            }
            map.put("stock", stock);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 单据明细列表
     * 
     * @param headerId
     * @param mpList
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getDetailList")
    @ApiOperation(value = "单据明细列表")
    public BaseResponseInfo getDetailList(@RequestParam("headerId") Long headerId,
            @RequestParam(value = "mpList", required = false) String mpList,
            @RequestParam(value = "linkType", required = false) String linkType,
            @RequestParam(value = "isReadOnly", required = false) String isReadOnly,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Long userId = userService.getUserId(request);
            String priceLimit = userService.getRoleTypeByUserId(userId).getPriceLimit();
            List<DepotItemVo4WithInfoEx> dataList = new ArrayList<>();
            String billCategory = depotHeadService
                    .getBillCategory(depotHeadService.getDepotHead(headerId).getSubType());
            if (headerId != 0) {
                dataList = depotItemService.getDetailList(headerId);
            }
            JSONObject outer = new JSONObject();
            outer.put("total", dataList.size());
            // 存放数据json数组
            JSONArray dataArray = new JSONArray();
            if (null != dataList) {
                BigDecimal totalOperNumber = BigDecimal.ZERO;
                BigDecimal totalAllPrice = BigDecimal.ZERO;
                BigDecimal totalTaxMoney = BigDecimal.ZERO;
                BigDecimal totalTaxLastMoney = BigDecimal.ZERO;
                BigDecimal totalWeight = BigDecimal.ZERO;
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    JSONObject item = new JSONObject();
                    item.put("id", diEx.getId());
                    item.put("materialExtendId", diEx.getMaterialExtendId() == null ? "" : diEx.getMaterialExtendId());
                    item.put("barCode", diEx.getBarCode());
                    item.put("name", diEx.getMName());
                    item.put("standard", diEx.getMStandard());
                    item.put("model", diEx.getMModel());
                    item.put("color", diEx.getMColor());
                    item.put("brand", diEx.getBrand());
                    item.put("mfrs", diEx.getMMfrs());
                    item.put("otherField1", diEx.getMOtherField1());
                    item.put("otherField2", diEx.getMOtherField2());
                    item.put("otherField3", diEx.getMOtherField3());
                    BigDecimal stock;
                    Unit unitInfo = materialService.findUnit(diEx.getMaterialId()); // 查询多单位信息
                    String materialUnit = diEx.getMaterialUnit();
                    if (StringUtil.isNotEmpty(diEx.getSku())) {
                        stock = depotItemService.getSkuStockByParam(diEx.getDepotId(), diEx.getMaterialExtendId(), null,
                                null);
                    } else {
                        stock = depotItemService.getCurrentStockByParam(diEx.getDepotId(), diEx.getMaterialId());
                        if (StringUtil.isNotEmpty(unitInfo.getName())) {
                            stock = unitService.parseStockByUnit(stock, unitInfo, materialUnit);
                        }
                    }
                    item.put("stock", stock);
                    item.put("unit", diEx.getMaterialUnit());
                    item.put("snList", diEx.getSnList());
                    item.put("batchNumber", diEx.getBatchNumber());
                    item.put("expirationDate", Tools.parseDateToStr(diEx.getExpirationDate()));
                    item.put("sku", diEx.getSku());
                    item.put("enableSerialNumber", diEx.getEnableSerialNumber());
                    item.put("enableBatchNumber", diEx.getEnableBatchNumber());
                    item.put("operNumber", diEx.getOperNumber());
                    item.put("basicNumber", diEx.getBasicNumber());
                    item.put("preNumber", diEx.getOperNumber()); // 原数量
                    item.put("finishNumber", depotItemService.getFinishNumber(diEx.getMaterialExtendId(), diEx.getId(),
                            diEx.getHeaderId(), unitInfo, materialUnit, linkType)); // 已入库|已出库
                    item.put("purchaseDecimal", roleService.parseBillPriceByLimit(diEx.getPurchaseDecimal(),
                            billCategory, priceLimit, request)); // 采购价
                    if ("basic".equals(linkType) || "1".equals(isReadOnly)) {
                        // 正常情况显示金额，而以销定购的情况不能显示金额
                        item.put("unitPrice", roleService.parseBillPriceByLimit(diEx.getUnitPrice(), billCategory,
                                priceLimit, request));
                        item.put("taxUnitPrice", roleService.parseBillPriceByLimit(diEx.getTaxUnitPrice(), billCategory,
                                priceLimit, request));
                        item.put("allPrice", roleService.parseBillPriceByLimit(diEx.getAllPrice(), billCategory,
                                priceLimit, request));
                        item.put("taxRate", roleService.parseBillPriceByLimit(diEx.getTaxRate(), billCategory,
                                priceLimit, request));
                        item.put("taxMoney", roleService.parseBillPriceByLimit(diEx.getTaxMoney(), billCategory,
                                priceLimit, request));
                        item.put("taxLastMoney", roleService.parseBillPriceByLimit(diEx.getTaxLastMoney(), billCategory,
                                priceLimit, request));
                    }
                    BigDecimal allWeight = diEx.getBasicNumber() == null || diEx.getWeight() == null ? BigDecimal.ZERO
                            : diEx.getBasicNumber().multiply(diEx.getWeight());
                    item.put("weight", allWeight);
                    item.put("position", diEx.getPosition());
                    item.put("remark", diEx.getRemark());
                    item.put("imgName", diEx.getImgName());
                    if (fileUploadType == 2) {
                        item.put("imgSmall", "small");
                        item.put("imgLarge", "large");
                    } else {
                        item.put("imgSmall", "");
                        item.put("imgLarge", "");
                    }
                    item.put("linkId", diEx.getLinkId());
                    item.put("depotId", diEx.getDepotId() == null ? "" : diEx.getDepotId());
                    item.put("depotName", diEx.getDepotId() == null ? "" : diEx.getDepotName());
                    item.put("anotherDepotId", diEx.getAnotherDepotId() == null ? "" : diEx.getAnotherDepotId());
                    item.put("anotherDepotName", diEx.getAnotherDepotId() == null ? "" : diEx.getAnotherDepotName());
                    item.put("mType", diEx.getMaterialType());
                    item.put("op", 1);
                    dataArray.add(item);
                    // 合计数据汇总
                    totalOperNumber = totalOperNumber
                            .add(diEx.getOperNumber() == null ? BigDecimal.ZERO : diEx.getOperNumber());
                    totalAllPrice = totalAllPrice
                            .add(diEx.getAllPrice() == null ? BigDecimal.ZERO : diEx.getAllPrice());
                    totalTaxMoney = totalTaxMoney
                            .add(diEx.getTaxMoney() == null ? BigDecimal.ZERO : diEx.getTaxMoney());
                    totalTaxLastMoney = totalTaxLastMoney
                            .add(diEx.getTaxLastMoney() == null ? BigDecimal.ZERO : diEx.getTaxLastMoney());
                    totalWeight = totalWeight.add(allWeight);
                }
                if (StringUtil.isNotEmpty(isReadOnly) && "1".equals(isReadOnly)) {
                    JSONObject footItem = new JSONObject();
                    footItem.put("operNumber", totalOperNumber);
                    footItem.put("allPrice",
                            roleService.parseBillPriceByLimit(totalAllPrice, billCategory, priceLimit, request));
                    footItem.put("taxMoney",
                            roleService.parseBillPriceByLimit(totalTaxMoney, billCategory, priceLimit, request));
                    footItem.put("taxLastMoney",
                            roleService.parseBillPriceByLimit(totalTaxLastMoney, billCategory, priceLimit, request));
                    footItem.put("weight", totalWeight);
                    dataArray.add(footItem);
                }
            }
            outer.put("rows", dataArray);
            res.code = 200;
            res.data = outer;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 进销存统计查询
     * 
     * @param currentPage
     * @param pageSize
     * @param depotIds
     * @param beginTime
     * @param endTime
     * @param materialParam
     * @param mpList
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getInOutStock")
    @ApiOperation(value = "进销存统计查询")
    public BaseResponseInfo getInOutStock(@RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "depotIds", required = false) String depotIds,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam("beginTime") String beginTime,
            @RequestParam("endTime") String endTime,
            @RequestParam("materialParam") String materialParam,
            @RequestParam(value = "mpList", required = false) String mpList,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<>();
        try {
            Boolean moveAvgPriceFlag = systemConfigService.getMoveAvgPriceFlag();
            List<Long> categoryIdList = new ArrayList<>();
            if (categoryId != null) {
                categoryIdList = materialService.getListByParentId(categoryId);
            }
            beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
            endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
            List<Long> depotList = parseListByDepotIds(depotIds);
            List<DepotItemVo4WithInfoEx> dataList = depotItemService.getInOutStock(StringUtil.toNull(materialParam),
                    categoryIdList, endTime, (currentPage - 1) * pageSize, pageSize);
            int total = depotItemService.getInOutStockCount(StringUtil.toNull(materialParam), categoryIdList, endTime);
            map.put("total", total);
            // 存放数据json数组
            JSONArray dataArray = new JSONArray();
            if (null != dataList) {
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    JSONObject item = new JSONObject();
                    Long mId = diEx.getMId();
                    item.put("barCode", diEx.getBarCode());
                    item.put("materialName", diEx.getMName());
                    item.put("materialModel", diEx.getMModel());
                    item.put("materialStandard", diEx.getMStandard());
                    item.put("materialColor", diEx.getMColor());
                    item.put("materialMfrs", diEx.getMMfrs());
                    item.put("materialBrand", diEx.getBrand());
                    // 扩展信息
                    item.put("otherField1", diEx.getMOtherField1());
                    item.put("otherField2", diEx.getMOtherField2());
                    item.put("otherField3", diEx.getMOtherField3());
                    item.put("unitId", diEx.getUnitId());
                    item.put("unitName",
                            null != diEx.getUnitId() ? diEx.getMaterialUnit() + "[多单位]" : diEx.getMaterialUnit());
                    BigDecimal prevSum = depotItemService.getStockByParamWithDepotList(depotList, mId, null, beginTime);
                    Map<String, BigDecimal> intervalMap = depotItemService.getIntervalMapByParamWithDepotList(depotList,
                            mId, beginTime, endTime);
                    BigDecimal inSum = intervalMap.get("inSum");
                    BigDecimal outSum = intervalMap.get("outSum");
                    BigDecimal thisSum = prevSum.add(inSum).subtract(outSum);
                    item.put("prevSum", prevSum);
                    item.put("inSum", inSum);
                    item.put("outSum", outSum);
                    item.put("thisSum", thisSum);
                    // 将小单位的库存换算为大单位的库存
                    item.put("bigUnitStock", materialService.getBigUnitStock(thisSum, diEx.getUnitId()));
                    if (moveAvgPriceFlag) {
                        item.put("unitPrice", diEx.getCurrentUnitPrice());
                    } else {
                        item.put("unitPrice", diEx.getPurchaseDecimal());
                    }
                    if (moveAvgPriceFlag) {
                        item.put("thisAllPrice", thisSum.multiply(diEx.getCurrentUnitPrice()));
                    } else {
                        item.put("thisAllPrice", thisSum.multiply(diEx.getPurchaseDecimal()));
                    }
                    dataArray.add(item);
                }
            }
            map.put("rows", dataArray);
            res.code = 200;
            res.data = map;
        } catch (BusinessRunTimeException e) {
            res.code = e.getCode();
            res.data = e.getData().get("message");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 进销存统计总计金额
     * 
     * @param depotIds
     * @param endTime
     * @param materialParam
     * @param request
     * @return
     */
    @GetMapping(value = "/getInOutStockCountMoney")
    @ApiOperation(value = "进销存统计总计金额")
    public BaseResponseInfo getInOutStockCountMoney(@RequestParam(value = "depotIds", required = false) String depotIds,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam("endTime") String endTime,
            @RequestParam("materialParam") String materialParam,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<>();
        try {
            Boolean moveAvgPriceFlag = systemConfigService.getMoveAvgPriceFlag();
            List<Long> categoryIdList = new ArrayList<>();
            if (categoryId != null) {
                categoryIdList = materialService.getListByParentId(categoryId);
            }
            endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
            List<Long> depotList = parseListByDepotIds(depotIds);
            List<DepotItemVo4WithInfoEx> dataList = depotItemService.getInOutStock(StringUtil.toNull(materialParam),
                    categoryIdList, endTime, null, null);
            BigDecimal thisAllStock = BigDecimal.ZERO;
            BigDecimal thisAllPrice = BigDecimal.ZERO;
            if (null != dataList) {
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    Long mId = diEx.getMId();
                    BigDecimal thisSum = depotItemService.getStockByParamWithDepotList(depotList, mId, null, endTime);
                    thisAllStock = thisAllStock.add(thisSum);
                    BigDecimal unitPrice = null;
                    if (moveAvgPriceFlag) {
                        unitPrice = diEx.getCurrentUnitPrice();
                    } else {
                        unitPrice = diEx.getPurchaseDecimal();
                    }
                    if (unitPrice == null) {
                        unitPrice = BigDecimal.ZERO;
                    }
                    thisAllPrice = thisAllPrice.add(thisSum.multiply(unitPrice));
                }
            }
            map.put("totalStock", thisAllStock);
            map.put("totalCount", thisAllPrice);
            res.code = 200;
            res.data = map;
        } catch (BusinessRunTimeException e) {
            res.code = e.getCode();
            res.data = e.getData().get("message");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    private List<Long> parseListByDepotIds(@RequestParam("depotIds") String depotIds) throws Exception {
        List<Long> depotList = new ArrayList<>();
        if (StringUtil.isNotEmpty(depotIds)) {
            depotList = StringUtil.strToLongList(depotIds);
        } else {
            // 未选择仓库时默认为当前用户有权限的仓库
            JSONArray depotArr = depotService.findDepotByCurrentUser();
            for (Object obj : depotArr) {
                JSONObject object = JSONObject.parseObject(obj.toString());
                depotList.add(object.getLong("id"));
            }
            // 如果有权限的仓库数量太多则提示要选择仓库
            if (depotList.size() > 20) {
                throw new BusinessRunTimeException(ExceptionConstants.REPORT_TWO_MANY_DEPOT_FAILED_CODE,
                        ExceptionConstants.REPORT_TWO_MANY_DEPOT_FAILED_MSG);
            }
        }
        return depotList;
    }

    /**
     * 采购统计
     * 
     * @param currentPage
     * @param pageSize
     * @param beginTime
     * @param endTime
     * @param materialParam
     * @param mpList
     * @param request
     * @return
     */
    @GetMapping(value = "/buyIn")
    @ApiOperation(value = "采购统计")
    public BaseResponseInfo buyIn(@RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam("beginTime") String beginTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "organId", required = false) Long organId,
            @RequestParam(value = "depotId", required = false) Long depotId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "organizationId", required = false) Long organizationId,
            @RequestParam("materialParam") String materialParam,
            @RequestParam(value = "mpList", required = false) String mpList,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
        endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
        try {
            String[] creatorArray = depotHeadService.getCreatorArray();
            if (creatorArray == null && organizationId != null) {
                creatorArray = depotHeadService.getCreatorArrayByOrg(organizationId);
            }
            String[] organArray = null;
            List<Long> categoryList = new ArrayList<>();
            if (categoryId != null) {
                categoryList = materialService.getListByParentId(categoryId);
            }
            List<Long> depotList = depotService.parseDepotList(depotId);
            Boolean forceFlag = systemConfigService.getForceApprovalFlag();
            List<DepotItemVo4WithInfoEx> dataList = depotItemService.getListWithBuyOrSale(
                    StringUtil.toNull(materialParam),
                    "buy", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag,
                    (currentPage - 1) * pageSize, pageSize);
            int total = depotItemService.getListWithBuyOrSaleCount(StringUtil.toNull(materialParam),
                    "buy", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            map.put("total", total);
            // 存放数据json数组
            JSONArray dataArray = new JSONArray();
            if (null != dataList) {
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    JSONObject item = new JSONObject();
                    BigDecimal InSum = depotItemService.buyOrSale("入库", "采购", diEx.getMaterialExtendId(), beginTime,
                            endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal OutSum = depotItemService.buyOrSale("出库", "采购退货", diEx.getMaterialExtendId(), beginTime,
                            endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal InSumPrice = depotItemService.buyOrSale("入库", "采购", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal OutSumPrice = depotItemService.buyOrSale("出库", "采购退货", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal InOutSumPrice = InSumPrice.subtract(OutSumPrice);
                    item.put("barCode", diEx.getBarCode());
                    item.put("materialName", diEx.getMName());
                    item.put("materialModel", diEx.getMModel());
                    item.put("materialStandard", diEx.getMStandard());
                    // 扩展信息
                    item.put("otherField1", diEx.getMOtherField1());
                    item.put("otherField2", diEx.getMOtherField2());
                    item.put("otherField3", diEx.getMOtherField3());
                    item.put("materialColor", diEx.getMColor());
                    item.put("materialBrand", diEx.getBrand());
                    item.put("materialMfrs", diEx.getMMfrs());
                    item.put("materialUnit", diEx.getMaterialUnit());
                    item.put("unitName", diEx.getUnitName());
                    item.put("inSum", InSum);
                    item.put("outSum", OutSum);
                    item.put("inSumPrice", InSumPrice);
                    item.put("outSumPrice", OutSumPrice);
                    item.put("inOutSumPrice", InOutSumPrice);// 实际采购金额
                    dataArray.add(item);
                }
            }
            BigDecimal inSumPriceTotal = depotItemService.buyOrSalePriceTotal("入库", "采购",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal outSumPriceTotal = depotItemService.buyOrSalePriceTotal("出库", "采购退货",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal realityPriceTotal = inSumPriceTotal.subtract(outSumPriceTotal);
            map.put("rows", dataArray);
            map.put("realityPriceTotal", realityPriceTotal);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 零售统计
     * 
     * @param currentPage
     * @param pageSize
     * @param beginTime
     * @param endTime
     * @param materialParam
     * @param mpList
     * @param request
     * @return
     */
    @GetMapping(value = "/retailOut")
    @ApiOperation(value = "零售统计")
    public BaseResponseInfo retailOut(@RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam("beginTime") String beginTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "organId", required = false) Long organId,
            @RequestParam(value = "depotId", required = false) Long depotId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "organizationId", required = false) Long organizationId,
            @RequestParam("materialParam") String materialParam,
            @RequestParam(value = "mpList", required = false) String mpList,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
        endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
        try {
            String[] creatorArray = depotHeadService.getCreatorArray();
            if (creatorArray == null && organizationId != null) {
                creatorArray = depotHeadService.getCreatorArrayByOrg(organizationId);
            }
            String[] organArray = null;
            List<Long> categoryList = new ArrayList<>();
            if (categoryId != null) {
                categoryList = materialService.getListByParentId(categoryId);
            }
            List<Long> depotList = depotService.parseDepotList(depotId);
            Boolean forceFlag = systemConfigService.getForceApprovalFlag();
            List<DepotItemVo4WithInfoEx> dataList = depotItemService.getListWithBuyOrSale(
                    StringUtil.toNull(materialParam),
                    "retail", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag,
                    (currentPage - 1) * pageSize, pageSize);
            int total = depotItemService.getListWithBuyOrSaleCount(StringUtil.toNull(materialParam),
                    "retail", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList,
                    forceFlag);
            map.put("total", total);
            // 存放数据json数组
            JSONArray dataArray = new JSONArray();
            if (null != dataList) {
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    JSONObject item = new JSONObject();
                    BigDecimal OutSumRetail = depotItemService.buyOrSale("出库", "零售", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal InSumRetail = depotItemService.buyOrSale("入库", "零售退货", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal OutSumRetailPrice = depotItemService.buyOrSale("出库", "零售", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal InSumRetailPrice = depotItemService.buyOrSale("入库", "零售退货", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal OutInSumPrice = OutSumRetailPrice.subtract(InSumRetailPrice);
                    item.put("barCode", diEx.getBarCode());
                    item.put("materialName", diEx.getMName());
                    item.put("materialModel", diEx.getMModel());
                    item.put("materialStandard", diEx.getMStandard());
                    // 扩展信息
                    item.put("otherField1", diEx.getMOtherField1());
                    item.put("otherField2", diEx.getMOtherField2());
                    item.put("otherField3", diEx.getMOtherField3());
                    item.put("materialColor", diEx.getMColor());
                    item.put("materialBrand", diEx.getBrand());
                    item.put("materialMfrs", diEx.getMMfrs());
                    item.put("materialUnit", diEx.getMaterialUnit());
                    item.put("unitName", diEx.getUnitName());
                    item.put("outSum", OutSumRetail);
                    item.put("inSum", InSumRetail);
                    item.put("outSumPrice", OutSumRetailPrice);
                    item.put("inSumPrice", InSumRetailPrice);
                    item.put("outInSumPrice", OutInSumPrice);// 实际销售金额
                    dataArray.add(item);
                }
            }
            BigDecimal outSumPriceTotal = depotItemService.buyOrSalePriceTotal("出库", "零售",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal inSumPriceTotal = depotItemService.buyOrSalePriceTotal("入库", "零售退货",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal realityPriceTotal = outSumPriceTotal.subtract(inSumPriceTotal);
            map.put("rows", dataArray);
            map.put("realityPriceTotal", realityPriceTotal);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 销售统计
     * 
     * @param currentPage
     * @param pageSize
     * @param beginTime
     * @param endTime
     * @param materialParam
     * @param mpList
     * @param request
     * @return
     */
    @GetMapping(value = "/saleOut")
    @ApiOperation(value = "销售统计")
    public BaseResponseInfo saleOut(@RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam("beginTime") String beginTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "organId", required = false) Long organId,
            @RequestParam(value = "depotId", required = false) Long depotId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "organizationId", required = false) Long organizationId,
            @RequestParam("materialParam") String materialParam,
            @RequestParam(value = "mpList", required = false) String mpList,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
        endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
        try {
            String[] creatorArray = depotHeadService.getCreatorArray();
            if (creatorArray == null && organizationId != null) {
                creatorArray = depotHeadService.getCreatorArrayByOrg(organizationId);
            }
            String[] organArray = depotHeadService.getOrganArray("销售", "");
            List<Long> categoryList = new ArrayList<>();
            if (categoryId != null) {
                categoryList = materialService.getListByParentId(categoryId);
            }
            List<Long> depotList = depotService.parseDepotList(depotId);
            Boolean forceFlag = systemConfigService.getForceApprovalFlag();
            List<DepotItemVo4WithInfoEx> dataList = depotItemService.getListWithBuyOrSale(
                    StringUtil.toNull(materialParam),
                    "sale", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag,
                    (currentPage - 1) * pageSize, pageSize);
            int total = depotItemService.getListWithBuyOrSaleCount(StringUtil.toNull(materialParam),
                    "sale", beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            map.put("total", total);
            // 存放数据json数组
            JSONArray dataArray = new JSONArray();
            if (null != dataList) {
                for (DepotItemVo4WithInfoEx diEx : dataList) {
                    JSONObject item = new JSONObject();
                    BigDecimal OutSum = depotItemService.buyOrSale("出库", "销售", diEx.getMaterialExtendId(), beginTime,
                            endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal InSum = depotItemService.buyOrSale("入库", "销售退货", diEx.getMaterialExtendId(), beginTime,
                            endTime, creatorArray, organId, organArray, depotList, forceFlag, "number");
                    BigDecimal OutSumPrice = depotItemService.buyOrSale("出库", "销售", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal InSumPrice = depotItemService.buyOrSale("入库", "销售退货", diEx.getMaterialExtendId(),
                            beginTime, endTime, creatorArray, organId, organArray, depotList, forceFlag, "price");
                    BigDecimal OutInSumPrice = OutSumPrice.subtract(InSumPrice);
                    item.put("barCode", diEx.getBarCode());
                    item.put("materialName", diEx.getMName());
                    item.put("materialModel", diEx.getMModel());
                    item.put("materialStandard", diEx.getMStandard());
                    // 扩展信息
                    item.put("otherField1", diEx.getMOtherField1());
                    item.put("otherField2", diEx.getMOtherField2());
                    item.put("otherField3", diEx.getMOtherField3());
                    item.put("materialColor", diEx.getMColor());
                    item.put("materialBrand", diEx.getBrand());
                    item.put("materialMfrs", diEx.getMMfrs());
                    item.put("materialUnit", diEx.getMaterialUnit());
                    item.put("unitName", diEx.getUnitName());
                    item.put("outSum", OutSum);
                    item.put("inSum", InSum);
                    item.put("outSumPrice", OutSumPrice);
                    item.put("inSumPrice", InSumPrice);
                    item.put("outInSumPrice", OutInSumPrice);// 实际销售金额
                    dataArray.add(item);
                }
            }
            BigDecimal outSumPriceTotal = depotItemService.buyOrSalePriceTotal("出库", "销售",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal inSumPriceTotal = depotItemService.buyOrSalePriceTotal("入库", "销售退货",
                    StringUtil.toNull(materialParam),
                    beginTime, endTime, creatorArray, organId, organArray, categoryList, depotList, forceFlag);
            BigDecimal realityPriceTotal = outSumPriceTotal.subtract(inSumPriceTotal);
            map.put("rows", dataArray);
            map.put("realityPriceTotal", realityPriceTotal);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 获取单位
     * 
     * @param materialUnit
     * @param uName
     * @return
     */
    public String getUName(String materialUnit, String uName) {
        String unitName = null;
        if (StringUtil.isNotEmpty(materialUnit)) {
            unitName = materialUnit;
        } else if (StringUtil.isNotEmpty(uName)) {
            unitName = uName;
        }
        return unitName;
    }

    /**
     * 库存预警报表
     * 
     * @param currentPage
     * @param pageSize
     * @return
     */
    @GetMapping(value = "/findStockWarningCount")
    @ApiOperation(value = "库存预警报表")
    public BaseResponseInfo findStockWarningCount(@RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam("materialParam") String materialParam,
            @RequestParam(value = "depotId", required = false) Long depotId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "mpList", required = false) String mpList) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            List<Long> depotList = new ArrayList<>();
            if (depotId != null) {
                depotList.add(depotId);
            } else {
                // 未选择仓库时默认为当前用户有权限的仓库
                JSONArray depotArr = depotService.findDepotByCurrentUser();
                for (Object obj : depotArr) {
                    JSONObject object = JSONObject.parseObject(obj.toString());
                    depotList.add(object.getLong("id"));
                }
            }
            List<Long> categoryList = new ArrayList<>();
            if (categoryId != null) {
                categoryList = materialService.getListByParentId(categoryId);
            }
            // 解析多属性参数，但此处不需要使用
            // String[] mpArr = mpList.split(",");
            List<DepotItemStockWarningCount> list = depotItemService.findStockWarningCount((currentPage - 1) * pageSize,
                    pageSize, materialParam, depotList, categoryList);
            // 存放数据json数组
            if (null != list) {
                for (DepotItemStockWarningCount disw : list) {
                    DepotItemVo4WithInfoEx diEx = new DepotItemVo4WithInfoEx();
                    diEx.setMOtherField1(disw.getMOtherField1());
                    diEx.setMOtherField2(disw.getMOtherField2());
                    diEx.setMOtherField3(disw.getMOtherField3());
                    disw.setMaterialUnit(getUName(disw.getMaterialUnit(), disw.getUnitName()));
                    if (null != disw.getLowSafeStock()
                            && disw.getCurrentNumber().compareTo(disw.getLowSafeStock()) < 0) {
                        disw.setLowCritical(disw.getLowSafeStock().subtract(disw.getCurrentNumber()));
                    }
                    if (null != disw.getHighSafeStock()
                            && disw.getCurrentNumber().compareTo(disw.getHighSafeStock()) > 0) {
                        disw.setHighCritical(disw.getCurrentNumber().subtract(disw.getHighSafeStock()));
                    }
                }
            }
            int total = depotItemService.findStockWarningCountTotal(materialParam, depotList, categoryList);
            map.put("total", total);
            map.put("rows", list);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 统计采购、销售、零售的总金额
     * 
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/buyOrSalePrice")
    @ApiOperation(value = "统计采购、销售、零售的总金额")
    public BaseResponseInfo buyOrSalePrice(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Map<String, Object> map = new HashMap<>();
            String loginName = userService.getCurrentUser().getLoginName();
            if (!"admin".equals(loginName)) {
                Long userId = userService.getUserId(request);
                List<String> monthList = Tools.getLastMonths(6);
                String beginTime = Tools.firstDayOfMonth(monthList.get(0)) + BusinessConstants.DAY_FIRST_TIME;
                String endTime = Tools.getNow() + BusinessConstants.DAY_LAST_TIME;
                List<InOutPriceVo> inOrOutPriceList = depotItemService.inOrOutPriceList(beginTime, endTime);
                String priceLimit = userService.getRoleTypeByUserId(userId).getPriceLimit();
                JSONArray buyPriceList = new JSONArray();
                for (String month : monthList) {
                    JSONObject obj = new JSONObject();
                    BigDecimal outPrice = BigDecimal.ZERO;
                    BigDecimal inPrice = BigDecimal.ZERO;
                    for (InOutPriceVo item : inOrOutPriceList) {
                        String billOperMonth = Tools.dateToStr(item.getOperTime(), "yyyy-MM");
                        if (month.equals(billOperMonth)) {
                            if ("入库".equals(item.getType()) && "采购".equals(item.getSubType())) {
                                outPrice = outPrice.add(item.getDiscountLastMoney());
                            }
                            if ("出库".equals(item.getType()) && "采购退货".equals(item.getSubType())) {
                                inPrice = inPrice.add(item.getDiscountLastMoney());
                            }
                        }
                    }
                    obj.put("x", month);
                    obj.put("y", roleService.parseHomePriceByLimit(outPrice.subtract(inPrice), "buy", priceLimit, "***",
                            request));
                    buyPriceList.add(obj);
                }
                map.put("buyPriceList", buyPriceList);
                JSONArray salePriceList = new JSONArray();
                for (String month : monthList) {
                    JSONObject obj = new JSONObject();
                    BigDecimal outPrice = BigDecimal.ZERO;
                    BigDecimal inPrice = BigDecimal.ZERO;
                    for (InOutPriceVo item : inOrOutPriceList) {
                        String billOperMonth = Tools.dateToStr(item.getOperTime(), "yyyy-MM");
                        if (month.equals(billOperMonth)) {
                            if ("出库".equals(item.getType()) && "销售".equals(item.getSubType())) {
                                outPrice = outPrice.add(item.getDiscountLastMoney());
                            }
                            if ("入库".equals(item.getType()) && "销售退货".equals(item.getSubType())) {
                                inPrice = inPrice.add(item.getDiscountLastMoney());
                            }
                        }
                    }
                    obj.put("x", month);
                    obj.put("y", roleService.parseHomePriceByLimit(outPrice.subtract(inPrice), "sale", priceLimit,
                            "***", request));
                    salePriceList.add(obj);
                }
                map.put("salePriceList", salePriceList);
                JSONArray retailPriceList = new JSONArray();
                for (String month : monthList) {
                    JSONObject obj = new JSONObject();
                    BigDecimal outPrice = BigDecimal.ZERO;
                    BigDecimal inPrice = BigDecimal.ZERO;
                    for (InOutPriceVo item : inOrOutPriceList) {
                        String billOperMonth = Tools.dateToStr(item.getOperTime(), "yyyy-MM");
                        if (month.equals(billOperMonth)) {
                            if ("出库".equals(item.getType()) && "零售".equals(item.getSubType())) {
                                outPrice = outPrice.add(item.getTotalPrice().abs());
                            }
                            if ("入库".equals(item.getType()) && "零售退货".equals(item.getSubType())) {
                                inPrice = inPrice.add(item.getTotalPrice().abs());
                            }
                        }
                    }
                    obj.put("x", month);
                    obj.put("y", roleService.parseHomePriceByLimit(outPrice.subtract(inPrice), "retail", priceLimit,
                            "***", request));
                    retailPriceList.add(obj);
                }
                map.put("retailPriceList", retailPriceList);
            }
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "统计失败";
        }
        return res;
    }

    /**
     * 获取商品期间库存统计
     * 
     * @param materialParam
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getMaterialPeriodStock")
    @ApiOperation(value = "获取商品期间库存统计")
    public BaseResponseInfo getMaterialPeriodStock(
            @RequestParam(value = "currentPage", required = false) Integer currentPage,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "materialParam", required = false) String materialParam,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> objectMap = new HashMap<>();
        try {
            // 设置默认分页参数
            if (currentPage == null) {
                currentPage = 1;
            }
            if (pageSize == null) {
                pageSize = 10;
            }

            List<MaterialStockPeriodVo> list = depotItemService.getMaterialPeriodStock(materialParam,
                    (currentPage - 1) * pageSize, pageSize);
            int total = depotItemService.getMaterialPeriodStockCount(materialParam);

            objectMap.put("rows", list);
            objectMap.put("total", total);
            res.code = 200;
            res.data = objectMap;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 获取商品每日出库数据
     * 
     * @param materialIds
     * @param beginTime
     * @param endTime
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getDailyOutStock")
    @ApiOperation(value = "获取商品每日出库数据")
    public BaseResponseInfo getDailyOutStock(
            @RequestParam(value = "materialIds", required = false) String materialIds,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            if (StringUtil.isNotEmpty(beginTime)) {
                beginTime = beginTime + BusinessConstants.DAY_FIRST_TIME;
            }
            if (StringUtil.isNotEmpty(endTime)) {
                endTime = endTime + BusinessConstants.DAY_LAST_TIME;
            }

            List<Map<String, Object>> list = depotItemService.getDailyOutStock(materialIds, beginTime, endTime);
            res.code = 200;
            res.data = list;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 获取商品库存统计与每日出库数据合并结果（性能优化版本）
     * 
     * @param currentPage
     * @param pageSize
     * @param materialParam
     * @param beginTime
     * @param endTime
     * @param dimensionType
     * @param calculateAll
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getMaterialStockWithDailyOutOptimized")
    @ApiOperation(value = "获取商品库存统计与每日出库数据合并结果（性能优化版本）")
    public BaseResponseInfo getMaterialStockWithDailyOutOptimized(
            @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "materialParam", required = false) String materialParam,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "stockAlertStatus", required = false) String stockAlertStatus,
            @RequestParam(value = "dimensionType", defaultValue = "daily") String dimensionType,
            @RequestParam(value = "calculateAll", defaultValue = "false") Boolean calculateAll,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 根据维度类型处理时间参数
            if ("quarterly".equals(dimensionType) || "period".equals(dimensionType)) {
                // 季度和期间维度：获取最近几年的完整数据
                if (calculateAll) {
                    LocalDate now = LocalDate.now();
                    beginTime = now.minusYears(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    endTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
            }

            if (StringUtil.isNotEmpty(beginTime)) {
                beginTime = beginTime + BusinessConstants.DAY_FIRST_TIME;
            }
            if (StringUtil.isNotEmpty(endTime)) {
                endTime = endTime + BusinessConstants.DAY_LAST_TIME;
            }

            Map<String, Object> result = depotItemOptimizedService.getOptimizedMaterialStockWithDailyOut(
                    currentPage, pageSize, materialParam, beginTime, endTime, stockAlertStatus, request);

            // 添加维度类型到结果中
            result.put("dimensionType", dimensionType);

            res.code = 200;
            res.data = result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 获取当前租户全部商品库存总金额（全量，独立轻量接口）
     */
    @GetMapping(value = "/getTotalStockValue")
    @ApiOperation(value = "获取当前租户全部商品库存总金额")
    public BaseResponseInfo getTotalStockValue(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            BigDecimal value = depotItemOptimizedService.getTotalStockValueForCurrentTenant();
            Map<String, Object> map = new HashMap<>();
            map.put("totalStockValue", value);
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error("获取库存总金额失败", e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 测试图表数据接口
     * 用于验证图表功能是否正常工作
     * 
     * @param materialId
     * @param beginTime
     * @param endTime
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/testChartData")
    @ApiOperation(value = "测试图表数据接口")
    public BaseResponseInfo testChartData(
            @RequestParam(value = "materialId", required = false) String materialId,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 设置默认值用于测试
            if (StringUtil.isEmpty(materialId)) {
                materialId = "1"; // 默认商品ID
            }
            if (StringUtil.isEmpty(beginTime)) {
                beginTime = "2024-01-01";
            }
            if (StringUtil.isEmpty(endTime)) {
                endTime = "2024-01-31";
            }

            // 调用现有的getDailyOutStock接口
            List<Map<String, Object>> dailyOutList = depotItemService.getDailyOutStock(
                    materialId,
                    beginTime + BusinessConstants.DAY_FIRST_TIME,
                    endTime + BusinessConstants.DAY_LAST_TIME);

            Map<String, Object> result = new HashMap<>();
            result.put("materialId", materialId);
            result.put("beginTime", beginTime);
            result.put("endTime", endTime);
            result.put("dailyOutData", dailyOutList);
            result.put("dataCount", dailyOutList.size());

            res.code = 200;
            res.data = result;

            logger.info("测试图表数据接口调用成功，商品ID: {}, 数据条数: {}", materialId, dailyOutList.size());

        } catch (Exception e) {
            logger.error("测试图表数据接口失败", e);
            res.code = 500;
            res.data = "测试接口调用失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 获取批次商品列表信息
     * 
     * @param request
     * @return
     */
    @GetMapping(value = "/getBatchNumberList")
    @ApiOperation(value = "获取批次商品列表信息")
    public BaseResponseInfo getBatchNumberList(@RequestParam("name") String name,
            @RequestParam("depotItemId") Long depotItemId,
            @RequestParam("depotId") Long depotId,
            @RequestParam("barCode") String barCode,
            @RequestParam(value = "batchNumber", required = false) String batchNumber,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<>();
        try {
            String number = "";
            if (depotItemId != null) {
                DepotItem depotItem = depotItemService.getDepotItem(depotItemId);
                number = depotHeadService.getDepotHead(depotItem.getHeaderId()).getNumber();
            }
            Boolean forceFlag = systemConfigService.getForceApprovalFlag();
            Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
            List<DepotItemVoBatchNumberList> list = depotItemService.getBatchNumberList(number, name, depotId, barCode,
                    batchNumber, forceFlag, inOutManageFlag);
            map.put("rows", list);
            map.put("total", list.size());
            res.code = 200;
            res.data = map;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * Excel导入明细
     * 
     * @param file
     * @param request
     * @param response
     * @return
     */
    @PostMapping(value = "/importItemExcel")
    public BaseResponseInfo importItemExcel(MultipartFile file,
            @RequestParam(required = false, value = "prefixNo") String prefixNo,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> data = new HashMap<>();
        String message = "";
        try {
            String barCodes = "";
            // 文件合法性校验
            Sheet src = null;
            try {
                Workbook workbook = Workbook.getWorkbook(file.getInputStream());
                src = workbook.getSheet(0);
            } catch (Exception e) {
                message = "导入文件不合法，请检查";
                data.put("message", message);
                res.code = 400;
                res.data = data;
            }
            if (src.getRows() > 1000) {
                message = "导入失败，明细不能超出1000条";
                res.code = 500;
                data.put("message", message);
                res.data = data;
            } else {
                List<Map<String, String>> detailList = new ArrayList<>();
                for (int i = 2; i < src.getRows(); i++) {
                    String depotName = "", barCode = "", num = "", unitPrice = "", taxRate = "", remark = "";
                    if ("QGD".equals(prefixNo)) {
                        barCode = ExcelUtils.getContent(src, i, 0);
                        num = ExcelUtils.getContent(src, i, 2);
                        remark = ExcelUtils.getContent(src, i, 3);
                    }
                    if ("CGDD".equals(prefixNo) || "XSDD".equals(prefixNo)) {
                        barCode = ExcelUtils.getContent(src, i, 0);
                        num = ExcelUtils.getContent(src, i, 2);
                        unitPrice = ExcelUtils.getContent(src, i, 3);
                        taxRate = ExcelUtils.getContent(src, i, 4);
                        remark = ExcelUtils.getContent(src, i, 5);
                    }
                    if ("CGRK".equals(prefixNo) || "XSCK".equals(prefixNo)) {
                        depotName = ExcelUtils.getContent(src, i, 0);
                        barCode = ExcelUtils.getContent(src, i, 1);
                        num = ExcelUtils.getContent(src, i, 3);
                        unitPrice = ExcelUtils.getContent(src, i, 4);
                        taxRate = ExcelUtils.getContent(src, i, 5);
                        remark = ExcelUtils.getContent(src, i, 6);
                    }
                    if ("QTRK".equals(prefixNo) || "QTCK".equals(prefixNo)) {
                        depotName = ExcelUtils.getContent(src, i, 0);
                        barCode = ExcelUtils.getContent(src, i, 1);
                        num = ExcelUtils.getContent(src, i, 3);
                        unitPrice = ExcelUtils.getContent(src, i, 4);
                        remark = ExcelUtils.getContent(src, i, 5);
                    }
                    Map<String, String> materialMap = new HashMap<>();
                    materialMap.put("depotName", depotName);
                    materialMap.put("barCode", barCode);
                    materialMap.put("num", num);
                    materialMap.put("unitPrice", unitPrice);
                    materialMap.put("taxRate", taxRate);
                    materialMap.put("remark", remark);
                    detailList.add(materialMap);
                    barCodes += "'" + barCode + "',";
                }
                if (StringUtil.isNotEmpty(barCodes)) {
                    barCodes = barCodes.substring(0, barCodes.length() - 1);
                }
                JSONObject map = depotItemService.parseMapByExcelData(barCodes, detailList, prefixNo);
                if (map != null) {
                    res.code = 200;
                } else {
                    res.code = 500;
                }
                res.data = map;
            }
        } catch (BusinessRunTimeException e) {
            res.code = 500;
            data.put("message", e.getData().get("message"));
            res.data = data;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            message = "导入失败，请检查表格内容";
            res.code = 500;
            data.put("message", message);
            res.data = data;
        }
        return res;
    }

    /**
     * 修复库存小数点问题
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/fixDecimalStock")
    @ApiOperation(value = "修复库存小数点问题")
    public BaseResponseInfo fixDecimalStock(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            depotItemOptimizedService.fixDecimalStockIssue();

            res.code = 200;
            res.data = "库存小数点问题修复完成";

            logger.info("库存小数点问题修复成功");

        } catch (Exception e) {
            logger.error("修复库存小数点问题失败", e);
            res.code = 500;
            res.data = "修复失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 修复期间库存计算逻辑
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/fixPeriodCalculation")
    @ApiOperation(value = "修复期间库存计算逻辑")
    public BaseResponseInfo fixPeriodCalculation(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            depotItemOptimizedService.fixPeriodCalculationLogic();

            res.code = 200;
            res.data = "期间库存计算逻辑修复完成";

            logger.info("期间库存计算逻辑修复成功");

        } catch (Exception e) {
            logger.error("修复期间库存计算逻辑失败", e);
            res.code = 500;
            res.data = "修复失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 验证期间库存计算结果
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/validatePeriodBalance")
    @ApiOperation(value = "验证期间库存计算结果")
    public BaseResponseInfo validatePeriodBalance(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<Map<String, Object>> validationResults = depotItemMapperEx.validatePeriodStockBalance();

            res.code = 200;
            res.data = validationResults;

            logger.info("期间库存验证完成，发现{}个可能的异常记录", validationResults.size());

        } catch (Exception e) {
            logger.error("验证期间库存计算结果失败", e);
            res.code = 500;
            res.data = "验证失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 刷新商品期间汇总数据（异步，适合定时任务调用）
     * 注意：这个方法运行时间较长，建议在业务量少的时候执行
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/refreshPeriodSummary")
    @ApiOperation(value = "刷新商品期间汇总数据")
    public BaseResponseInfo refreshPeriodSummary(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            depotItemOptimizedService.refreshMaterialPeriodSummary(null);

            res.code = 200;
            res.data = "期间汇总数据刷新完成";

            logger.info("期间汇总数据刷新成功");

        } catch (Exception e) {
            logger.error("刷新期间汇总数据失败", e);
            res.code = 500;
            res.data = "刷新失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 刷新最近N天的每日出库汇总数据
     * 
     * @param days    天数，默认7天
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/refreshDailySummary")
    @ApiOperation(value = "刷新最近N天的每日出库汇总数据")
    public BaseResponseInfo refreshDailySummary(
            @RequestParam(value = "days", defaultValue = "7") Integer days,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            depotItemOptimizedService.refreshDailySummaryForRecentDays(days);

            res.code = 200;
            res.data = "最近" + days + "天的每日出库汇总数据刷新完成";

            logger.info("最近{}天的每日出库汇总数据刷新成功", days);

        } catch (Exception e) {
            logger.error("刷新每日出库汇总数据失败", e);
            res.code = 500;
            res.data = "刷新失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 刷新所有商品期间汇总数据
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/refreshAllMaterialsPeriodSummary")
    @ApiOperation(value = "刷新所有商品期间汇总数据")
    public BaseResponseInfo refreshAllMaterialsPeriodSummary(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            depotItemService.updateAllMaterialsPeriodSummary();

            res.code = 200;
            res.data = "所有商品期间汇总数据刷新完成";

            logger.info("所有商品期间汇总数据刷新成功");

        } catch (Exception e) {
            logger.error("刷新所有商品期间汇总数据失败", e);
            res.code = 500;
            res.data = "刷新失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 清除库存相关缓存
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/clearStockCache")
    public BaseResponseInfo clearStockCache(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 调用清除缓存的方法
            depotItemOptimizedService.clearAllCache();

            res.code = 200;
            res.data = "缓存清除成功";
            logger.info("库存数据缓存已清除");

        } catch (Exception e) {
            logger.error("清除缓存失败", e);
            res.code = 500;
            res.data = "清除缓存失败：" + e.getMessage();
        }
        return res;
    }

    // ========== 业务层汇总表管理接口 ==========

    /**
     * 初始化汇总表数据（解决汇总表为空的问题）
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/initializeSummaryData")
    @ApiOperation(value = "初始化汇总表数据")
    public BaseResponseInfo initializeSummaryData(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 获取当前用户的租户ID
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser != null ? currentUser.getTenantId() : null;

            // 调用业务层初始化方法
            depotItemService.initializeSummaryData(tenantId);

            res.code = 200;
            res.data = "汇总表数据初始化完成！这将为首页表格提供数据支持。";

            logger.info("汇总表数据初始化成功，tenantId={}", tenantId);

        } catch (Exception e) {
            logger.error("初始化汇总表数据失败", e);
            res.code = 500;
            res.data = "初始化失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 检查强制审核配置状态
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/checkForceApprovalConfig")
    @ApiOperation(value = "检查强制审核配置状态")
    public BaseResponseInfo checkForceApprovalConfig(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> result = new HashMap<>();

        try {
            depotItemService.ensureForceApprovalEnabled();
            boolean currentFlag = systemConfigService.getForceApprovalFlag();

            result.put("forceApprovalEnabled", currentFlag);
            result.put("statusMessage", currentFlag ? "强制审核已开启，汇总表将在审核时实时更新" : "强制审核未开启，汇总表不会在审核时更新");
            result.put("recommendation", currentFlag ? "配置正常" : "建议在系统配置中开启强制审核，确保数据实时性");

            res.code = 200;
            res.data = result;

            logger.info("强制审核配置检查完成，当前状态: {}", currentFlag);

        } catch (Exception e) {
            logger.error("检查强制审核配置失败", e);
            res.code = 500;
            res.data = "检查失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 获取汇总表状态信息
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getSummaryTableStatus")
    @ApiOperation(value = "获取汇总表状态信息")
    public BaseResponseInfo getSummaryTableStatus(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取当前用户的租户ID
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser != null ? currentUser.getTenantId() : null;

            // 统计商品期间汇总表数据量
            int periodSummaryCount = depotItemService.getMaterialPeriodStockCount(null);

            // 检查强制审核配置
            boolean forceApprovalEnabled = systemConfigService.getForceApprovalFlag();

            result.put("tenantId", tenantId);
            result.put("periodSummaryCount", periodSummaryCount);
            result.put("forceApprovalEnabled", forceApprovalEnabled);
            result.put("summaryTableEmpty", periodSummaryCount == 0);
            result.put("statusMessage",
                    periodSummaryCount == 0 ? "汇总表为空，建议执行初始化操作" : "汇总表包含 " + periodSummaryCount + " 条记录");

            res.code = 200;
            res.data = result;

            logger.info("汇总表状态检查完成: periodSummaryCount={}, forceApprovalEnabled={}",
                    periodSummaryCount, forceApprovalEnabled);

        } catch (Exception e) {
            logger.error("获取汇总表状态失败", e);
            res.code = 500;
            res.data = "获取状态失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 测试汇总表更新功能（用于验证修复）
     * 
     * @param materialId 商品ID
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/testSummaryUpdate")
    @ApiOperation(value = "测试汇总表更新功能")
    public BaseResponseInfo testSummaryUpdate(
            @RequestParam(value = "materialId", required = false) Long materialId,
            @RequestParam(value = "useSimple", defaultValue = "false") Boolean useSimple,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取当前用户的租户ID
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser != null ? currentUser.getTenantId() : null;

            if (materialId == null) {
                // 批量初始化测试
                if (useSimple) {
                    // 直接使用简化版本
                    depotItemMapperEx.batchInitializeSummaryDataSimple(tenantId);
                    result.put("operation", "批量初始化汇总表（简化版本）");
                    result.put("sqlType", "简化SQL成功");
                } else {
                    // 使用带回退的方法
                    depotItemService.initializeSummaryData(tenantId);
                    result.put("operation", "批量初始化汇总表（自动回退）");
                }
                result.put("tenantId", tenantId);
            } else {
                // 单个商品测试
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                String currentDate = sdf.format(new java.util.Date());

                // 先删除该商品的汇总记录
                depotItemMapperEx.deleteDailyOutSummary(materialId, currentDate, tenantId);

                // 尝试更新
                if (useSimple) {
                    // 直接使用简化版本
                    // depotItemMapperEx.insertOrUpdateDailyOutSummarySimple(materialId,
                    // currentDate, tenantId);
                    result.put("sqlType", "简化SQL成功");
                } else {
                    // 直接使用简化版本，因为复杂版本需要额外的shopName参数
                    // depotItemMapperEx.insertOrUpdateDailyOutSummarySimple(materialId,
                    // currentDate, tenantId);
                    result.put("sqlType", "简化SQL成功");
                }

                result.put("operation", "单个商品汇总更新");
                result.put("materialId", materialId);
                result.put("date", currentDate);
                result.put("tenantId", tenantId);
            }

            res.code = 200;
            res.data = result;

            logger.info("汇总表更新测试成功: {}", result);

        } catch (Exception e) {
            logger.error("汇总表更新测试失败", e);
            res.code = 500;
            res.data = "测试失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 启动全量汇总表刷新任务
     * 
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/startFullSummaryRefresh")
    @ApiOperation(value = "启动全量汇总表刷新任务")
    public BaseResponseInfo startFullSummaryRefresh(HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();

        try {
            // 获取当前用户的租户ID
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser != null ? currentUser.getTenantId() : null;

            // 启动刷新任务
            String taskId = summaryRefreshTaskService.startRefreshTask(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("message", "汇总表刷新任务已启动");

            res.code = 200;
            res.data = result;

        } catch (Exception e) {
            logger.error("启动汇总表刷新任务失败", e);
            res.code = 500;
            res.data = e.getMessage();
        }

        return res;
    }

    /**
     * 查询汇总表刷新任务进度
     * 
     * @param taskId  任务ID
     * @param request
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/getSummaryRefreshProgress")
    @ApiOperation(value = "查询汇总表刷新任务进度")
    public BaseResponseInfo getSummaryRefreshProgress(
            @RequestParam("taskId") String taskId,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();

        try {
            RefreshTaskStatus taskStatus = summaryRefreshTaskService.getTaskStatus(taskId);

            res.code = 200;
            res.data = taskStatus;

        } catch (Exception e) {
            logger.error("查询汇总表刷新任务进度失败", e);
            res.code = 500;
            res.data = e.getMessage();
        }

        return res;
    }

    /**
     * 测试SQL修复效果
     * 
     * @param materialId 商品ID
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/testSqlFix")
    @ApiOperation(value = "测试SQL修复效果")
    public BaseResponseInfo testSqlFix(
            @RequestParam(value = "materialId", defaultValue = "4513") Long materialId,
            HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取当前用户的租户ID
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser != null ? currentUser.getTenantId() : null;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String currentDate = sdf.format(new java.util.Date());

            // 测试1：删除每日出库汇总
            try {
                depotItemMapperEx.deleteDailyOutSummary(materialId, currentDate, tenantId);
                result.put("step1_delete", "成功");
            } catch (Exception e) {
                result.put("step1_delete", "失败: " + e.getMessage());
            }

            // 测试2：使用简化版每日出库汇总SQL
            try {
                // depotItemMapperEx.insertOrUpdateDailyOutSummarySimple(materialId,
                // currentDate, tenantId);
                result.put("step2_dailySummary", "成功 - 简化SQL有效");
            } catch (Exception e) {
                result.put("step2_dailySummary", "失败: " + e.getMessage());
            }

            // 测试3：删除商品期间汇总
            try {
                depotItemMapperEx.deleteMaterialPeriodSummary(materialId, tenantId);
                result.put("step3_deletePeriod", "成功");
            } catch (Exception e) {
                result.put("step3_deletePeriod", "失败: " + e.getMessage());
            }

            // 测试4：使用新的商品期间汇总SQL
            try {
                depotItemMapperEx.insertOrUpdateMaterialPeriodSummary(materialId, tenantId);
                result.put("step4_periodSummary", "成功 - 新SQL有效");
            } catch (Exception e) {
                result.put("step4_periodSummary", "失败: " + e.getMessage());
            }

            result.put("materialId", materialId);
            result.put("tenantId", tenantId);
            result.put("testDate", currentDate);
            result.put("conclusion", "测试完成");

            res.code = 200;
            res.data = result;

            logger.info("SQL修复测试完成: {}", result);

        } catch (Exception e) {
            logger.error("SQL修复测试失败", e);
            res.code = 500;
            res.data = "测试失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 批量计算库存预警状态
     *
     * @param obj
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/calculateAllStockAlertStatus")
    @ApiOperation(value = "批量计算库存预警状态")
    public BaseResponseInfo calculateAllStockAlertStatus(@RequestBody JSONObject obj, HttpServletRequest request)
            throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 检查库存预警权限
            if (!StockAlertPermissionUtil.hasStockAlertPermission(request)) {
                res.code = 403;
                res.data = "没有库存预警功能权限";
                return res;
            }

            User user = userService.getCurrentUser();
            Long tenantId = user != null ? user.getTenantId() : null;

            // 从请求体中读取是否保留忽略风险状态参数，默认为false（覆盖所有状态）
            Boolean preserveIgnoredStatus = obj.getBoolean("preserveIgnoredStatus");
            if (preserveIgnoredStatus == null) {
                preserveIgnoredStatus = false;
            }

            Map<String, Object> result = depotItemOptimizedService.calculateAllStockAlertStatus(tenantId,
                    preserveIgnoredStatus);

            res.code = 200;
            res.data = result;

            logger.info("批量计算库存预警状态完成: {}", result);

        } catch (Exception e) {
            logger.error("批量计算库存预警状态失败", e);
            res.code = 500;
            res.data = "计算失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 忽略库存风险
     *
     * @param obj
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/ignoreStockRisk")
    @ApiOperation(value = "忽略库存风险")
    public BaseResponseInfo ignoreStockRisk(@RequestBody JSONObject obj, HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 检查库存预警权限
            if (!StockAlertPermissionUtil.hasStockAlertPermission(request)) {
                res.code = 403;
                res.data = "没有库存预警功能权限";
                return res;
            }

            Long materialId = obj.getLong("materialId");

            materialService.ignoreStockRisk(materialId);

            res.code = 200;
            res.data = "已忽略库存风险";

            logger.info("忽略商品{}库存风险完成", materialId);

        } catch (Exception e) {
            logger.error("忽略库存风险失败", e);
            res.code = 500;
            res.data = "操作失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 重新关注库存风险
     *
     * @param obj
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/focusStockRisk")
    @ApiOperation(value = "重新关注库存风险")
    public BaseResponseInfo focusStockRisk(@RequestBody JSONObject obj, HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            // 检查库存预警权限
            if (!StockAlertPermissionUtil.hasStockAlertPermission(request)) {
                res.code = 403;
                res.data = "没有库存预警功能权限";
                return res;
            }

            Long materialId = obj.getLong("materialId");

            materialService.focusStockRisk(materialId);

            res.code = 200;
            res.data = "已重新关注库存风险";

            logger.info("重新关注商品{}库存风险完成", materialId);

        } catch (Exception e) {
            logger.error("重新关注库存风险失败", e);
            res.code = 500;
            res.data = "操作失败: " + e.getMessage();
        }
        return res;
    }

    /**
     * 导出商品库存数据
     * 
     * @param materialParam 商品筛选参数
     * @param beginTime     开始时间
     * @param endTime       结束时间
     * @param request       请求对象
     * @param response      响应对象
     */
    @GetMapping(value = "/exportMaterialStock")
    @ApiOperation(value = "导出商品库存数据")
    public void exportMaterialStock(
            @RequestParam(value = "materialParam", required = false) String materialParam,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            depotItemService.exportMaterialStockToExcel(
                    StringUtil.toNull(materialParam),
                    StringUtil.toNull(beginTime),
                    StringUtil.toNull(endTime),
                    response);
        } catch (Exception e) {
            logger.error("导出商品库存数据失败", e);
            try {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"导出失败: " + e.getMessage() + "\"}");
            } catch (Exception ex) {
                logger.error("返回错误信息失败", ex);
            }
        }
    }
}
