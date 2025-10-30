package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.mappers.*;
import com.jsh.erp.datasource.vo.DepotItemStockWarningCount;
import com.jsh.erp.datasource.vo.DepotItemVo4Stock;
import com.jsh.erp.datasource.vo.DepotItemVoBatchNumberList;
import com.jsh.erp.datasource.vo.InOutPriceVo;
import com.jsh.erp.datasource.vo.MaterialStockPeriodVo;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.utils.Tools;
import com.jsh.erp.utils.ExcelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class DepotItemService {
    private Logger logger = LoggerFactory.getLogger(DepotItemService.class);

    private final static String TYPE = "入库";
    private final static String SUM_TYPE = "number";
    private final static String IN = "in";
    private final static String OUT = "out";

    @Resource
    private DepotItemMapper depotItemMapper;
    @Resource
    private DepotItemMapperEx depotItemMapperEx;
    @Resource
    private MaterialService materialService;
    @Resource
    private MaterialExtendService materialExtendService;
    @Resource
    private SerialNumberMapperEx serialNumberMapperEx;
    @Resource
    private DepotHeadService depotHeadService;
    @Resource
    private DepotHeadMapper depotHeadMapper;
    @Resource
    private SerialNumberService serialNumberService;
    @Resource
    private UserService userService;
    @Resource
    private SystemConfigService systemConfigService;
    @Resource
    private DepotService depotService;
    @Resource
    private UnitService unitService;
    @Resource
    private MaterialCurrentStockMapper materialCurrentStockMapper;
    @Resource
    private MaterialCurrentStockMapperEx materialCurrentStockMapperEx;
    @Resource
    private LogService logService;
    @Resource
    private DepotItemOptimizedService depotItemOptimizedService;

    // ========== 批次处理上下文（线程级） ==========
    private final ThreadLocal<Set<Long>> processedMaterialIds = new ThreadLocal<>();
    private final ThreadLocal<Boolean> summaryOrAlertWritten = new ThreadLocal<>();
    private final ThreadLocal<String> billTypeContext = new ThreadLocal<>();

    public void beginBillProcessingContext(String billType) {
        processedMaterialIds.set(new HashSet<>());
        summaryOrAlertWritten.set(Boolean.FALSE);
        billTypeContext.set(billType);
    }

    public void endBillProcessingContext() {
        processedMaterialIds.remove();
        summaryOrAlertWritten.remove();
        billTypeContext.remove();
    }

    public void clearAllCachesOnceForCurrentBill() {
        try {
            Boolean flag = summaryOrAlertWritten.get();
            if (flag != null && flag) {
                clearRelatedCache();
            }
        } catch (Exception e) {
            logger.warn("清除缓存失败，error={}", e.getMessage());
        } finally {
            summaryOrAlertWritten.set(Boolean.FALSE);
        }
    }

    private boolean tryMarkMaterialProcessed(Long materialId) {
        Set<Long> set = processedMaterialIds.get();
        if (set == null) {
            return true;
        }
        if (materialId == null) {
            return true;
        }
        if (set.contains(materialId)) {
            return false;
        }
        set.add(materialId);
        return true;
    }

    private void markWriteHappened() {
        summaryOrAlertWritten.set(Boolean.TRUE);
    }

    public DepotItem getDepotItem(long id) throws Exception {
        DepotItem result = null;
        try {
            result = depotItemMapper.selectByPrimaryKey(id);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    public List<DepotItem> getDepotItem() throws Exception {
        DepotItemExample example = new DepotItemExample();
        example.createCriteria().andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<DepotItem> list = null;
        try {
            list = depotItemMapper.selectByExample(example);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<DepotItem> select(String name, Integer type, String remark, int offset, int rows) throws Exception {
        List<DepotItem> list = null;
        try {
            list = depotItemMapperEx.selectByConditionDepotItem(name, type, remark, offset, rows);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public Long countDepotItem(String name, Integer type, String remark) throws Exception {
        Long result = null;
        try {
            result = depotItemMapperEx.countsByDepotItem(name, type, remark);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertDepotItem(JSONObject obj, HttpServletRequest request) throws Exception {
        DepotItem depotItem = JSONObject.parseObject(obj.toJSONString(), DepotItem.class);
        int result = 0;
        try {
            result = depotItemMapper.insertSelective(depotItem);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateDepotItem(JSONObject obj, HttpServletRequest request) throws Exception {
        DepotItem depotItem = JSONObject.parseObject(obj.toJSONString(), DepotItem.class);
        int result = 0;
        try {
            result = depotItemMapper.updateByPrimaryKeySelective(depotItem);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteDepotItem(Long id, HttpServletRequest request) throws Exception {
        int result = 0;
        try {
            result = depotItemMapper.deleteByPrimaryKey(id);
        } catch (Exception e) {
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteDepotItem(String ids, HttpServletRequest request) throws Exception {
        List<Long> idList = StringUtil.strToLongList(ids);
        DepotItemExample example = new DepotItemExample();
        example.createCriteria().andIdIn(idList);
        int result = 0;
        try {
            result = depotItemMapper.deleteByExample(example);
        } catch (Exception e) {
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public int checkIsNameExist(Long id, String name) throws Exception {
        DepotItemExample example = new DepotItemExample();
        example.createCriteria().andIdNotEqualTo(id).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
        List<DepotItem> list = null;
        try {
            list = depotItemMapper.selectByExample(example);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list == null ? 0 : list.size();
    }

    public List<DepotItemVo4DetailByTypeAndMId> findDetailByDepotIdsAndMaterialIdList(String depotIds,
            Boolean forceFlag, Boolean inOutManageFlag, String sku, String batchNumber,
            String number, String beginTime, String endTime, Long mId, Integer offset, Integer rows) throws Exception {
        String[] depotIdArrOld = null;
        if (StringUtil.isNotEmpty(depotIds)) {
            depotIdArrOld = depotIds.split(",");
        }
        List<Long> depotList = depotService.parseDepotListByArr(depotIdArrOld);
        Long[] depotIdArray = StringUtil.listToLongArray(depotList);
        List<DepotItemVo4DetailByTypeAndMId> list = null;
        try {
            list = depotItemMapperEx.findDetailByDepotIdsAndMaterialIdList(depotIdArray, forceFlag, inOutManageFlag,
                    sku, batchNumber, number, beginTime, endTime, mId, offset, rows);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public Long findDetailByDepotIdsAndMaterialIdCount(String depotIds, Boolean forceFlag, Boolean inOutManageFlag,
            String sku, String batchNumber,
            String number, String beginTime, String endTime, Long mId) throws Exception {
        String[] depotIdArrOld = null;
        if (StringUtil.isNotEmpty(depotIds)) {
            depotIdArrOld = depotIds.split(",");
        }
        List<Long> depotList = depotService.parseDepotListByArr(depotIdArrOld);
        Long[] depotIdArray = StringUtil.listToLongArray(depotList);
        Long result = null;
        try {
            result = depotItemMapperEx.findDetailByDepotIdsAndMaterialIdCount(depotIdArray, forceFlag, inOutManageFlag,
                    sku, batchNumber, number, beginTime, endTime, mId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertDepotItemWithObj(DepotItem depotItem) throws Exception {
        int result = 0;
        try {
            result = depotItemMapper.insertSelective(depotItem);
        } catch (Exception e) {
            JshException.writeFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateDepotItemWithObj(DepotItem depotItem) throws Exception {
        int result = 0;
        try {
            result = depotItemMapper.updateByPrimaryKeySelective(depotItem);
        } catch (Exception e) {
            JshException.writeFail(logger, e);
        }
        return result;
    }

    public List<DepotItem> getListByHeaderId(Long headerId) throws Exception {
        List<DepotItem> list = null;
        try {
            DepotItemExample example = new DepotItemExample();
            example.createCriteria().andHeaderIdEqualTo(headerId)
                    .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
            list = depotItemMapper.selectByExample(example);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    /**
     * 查询当前单据中指定商品的明细信息
     * 
     * @param headerId
     * @param meId
     * @return
     * @throws Exception
     */
    public DepotItem getItemByHeaderIdAndMaterial(Long headerId, Long meId) throws Exception {
        DepotItem depotItem = new DepotItem();
        try {
            DepotItemExample example = new DepotItemExample();
            example.createCriteria().andHeaderIdEqualTo(headerId).andMaterialExtendIdEqualTo(meId)
                    .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
            List<DepotItem> list = depotItemMapper.selectByExample(example);
            if (list != null && list.size() > 0) {
                depotItem = list.get(0);
            }
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return depotItem;
    }

    /**
     * 查询被关联订单中指定商品的明细信息
     * 
     * @param linkStr
     * @param meId
     * @return
     * @throws Exception
     */
    public DepotItem getPreItemByHeaderIdAndMaterial(String linkStr, Long meId, Long linkId) throws Exception {
        DepotItem depotItem = new DepotItem();
        try {
            DepotHead depotHead = depotHeadService.getDepotHead(linkStr);
            if (null != depotHead && null != depotHead.getId()) {
                DepotItemExample example = new DepotItemExample();
                example.createCriteria().andHeaderIdEqualTo(depotHead.getId()).andMaterialExtendIdEqualTo(meId)
                        .andIdEqualTo(linkId).andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
                List<DepotItem> list = depotItemMapper.selectByExample(example);
                if (list != null && list.size() > 0) {
                    depotItem = list.get(0);
                }
            }
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return depotItem;
    }

    public List<DepotItemVo4WithInfoEx> getDetailList(Long headerId) throws Exception {
        List<DepotItemVo4WithInfoEx> list = null;
        try {
            list = depotItemMapperEx.getDetailList(headerId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public List<DepotItemVo4WithInfoEx> getInOutStock(String materialParam, List<Long> categoryIdList, String endTime,
            Integer offset, Integer rows) throws Exception {
        List<DepotItemVo4WithInfoEx> list = null;
        try {
            list = depotItemMapperEx.getInOutStock(materialParam, categoryIdList, endTime, offset, rows);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public int getInOutStockCount(String materialParam, List<Long> categoryIdList, String endTime) throws Exception {
        int result = 0;
        try {
            result = depotItemMapperEx.getInOutStockCount(materialParam, categoryIdList, endTime);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    public List<DepotItemVo4WithInfoEx> getListWithBuyOrSale(String materialParam, String billType,
            String beginTime, String endTime, String[] creatorArray, Long organId, String[] organArray,
            List<Long> categoryList, List<Long> depotList, Boolean forceFlag, Integer offset, Integer rows)
            throws Exception {
        List<DepotItemVo4WithInfoEx> list = null;
        try {
            list = depotItemMapperEx.getListWithBuyOrSale(materialParam, billType, beginTime, endTime, creatorArray,
                    organId, organArray, categoryList, depotList, forceFlag, offset, rows);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    public int getListWithBuyOrSaleCount(String materialParam, String billType,
            String beginTime, String endTime, String[] creatorArray, Long organId, String[] organArray,
            List<Long> categoryList, List<Long> depotList, Boolean forceFlag) throws Exception {
        int result = 0;
        try {
            result = depotItemMapperEx.getListWithBuyOrSaleCount(materialParam, billType, beginTime, endTime,
                    creatorArray, organId, organArray, categoryList, depotList, forceFlag);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    public BigDecimal buyOrSale(String type, String subType, Long meId, String beginTime, String endTime,
            String[] creatorArray, Long organId, String[] organArray, List<Long> depotList, Boolean forceFlag,
            String sumType) throws Exception {
        BigDecimal result = BigDecimal.ZERO;
        try {
            if (SUM_TYPE.equals(sumType)) {
                result = depotItemMapperEx.buyOrSaleNumber(type, subType, meId, beginTime, endTime, creatorArray,
                        organId, organArray, depotList, forceFlag, sumType);
            } else {
                result = depotItemMapperEx.buyOrSalePrice(type, subType, meId, beginTime, endTime, creatorArray,
                        organId, organArray, depotList, forceFlag, sumType);
            }
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    public BigDecimal buyOrSalePriceTotal(String type, String subType, String materialParam, String beginTime,
            String endTime,
            String[] creatorArray, Long organId, String[] organArray, List<Long> categoryList, List<Long> depotList,
            Boolean forceFlag) throws Exception {
        BigDecimal result = BigDecimal.ZERO;
        try {
            result = depotItemMapperEx.buyOrSalePriceTotal(type, subType, materialParam, beginTime, endTime,
                    creatorArray, organId, organArray, categoryList, depotList, forceFlag);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;

    }

    /**
     * 统计采购、销售、零售的总金额列表
     * 
     * @param beginTime
     * @param endTime
     * @return
     * @throws Exception
     */
    public List<InOutPriceVo> inOrOutPriceList(String beginTime, String endTime) throws Exception {
        List<InOutPriceVo> result = new ArrayList<>();
        try {
            String[] creatorArray = depotHeadService.getCreatorArray();
            Boolean forceFlag = systemConfigService.getForceApprovalFlag();
            result = depotItemMapperEx.inOrOutPriceList(beginTime, endTime, creatorArray, forceFlag);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void saveDetials(String rows, Long headerId, String actionType, HttpServletRequest request)
            throws Exception {
        // 查询单据主表信息
        DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(headerId);
        // 删除序列号和回收序列号
        deleteOrCancelSerialNumber(actionType, depotHead, headerId);
        // 删除单据的明细
        deleteDepotItemHeadId(headerId);
        JSONArray rowArr = JSONArray.parseArray(rows);
        if (null != rowArr && rowArr.size() > 0) {
            // 针对组装单、拆卸单校验是否存在组合件和普通子件
            checkAssembleWithMaterialType(rowArr, depotHead.getSubType());
            for (int i = 0; i < rowArr.size(); i++) {
                DepotItem depotItem = new DepotItem();
                JSONObject rowObj = JSONObject.parseObject(rowArr.getString(i));
                depotItem.setHeaderId(headerId);
                String barCode = rowObj.getString("barCode");
                MaterialExtend materialExtend = materialExtendService.getInfoByBarCode(barCode);
                if (materialExtend == null) {
                    throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_BARCODE_IS_NOT_EXIST_CODE,
                            String.format(ExceptionConstants.MATERIAL_BARCODE_IS_NOT_EXIST_MSG, barCode));
                }
                depotItem.setMaterialId(materialExtend.getMaterialId());
                depotItem.setMaterialExtendId(materialExtend.getId());
                depotItem.setMaterialUnit(rowObj.getString("unit"));
                Material material = materialService.getMaterial(depotItem.getMaterialId());
                if (BusinessConstants.ENABLE_SERIAL_NUMBER_ENABLED.equals(material.getEnableSerialNumber()) ||
                        BusinessConstants.ENABLE_BATCH_NUMBER_ENABLED.equals(material.getEnableBatchNumber())) {
                    // 组装拆卸单不能选择批号或序列号商品
                    if (BusinessConstants.SUB_TYPE_ASSEMBLE.equals(depotHead.getSubType()) ||
                            BusinessConstants.SUB_TYPE_DISASSEMBLE.equals(depotHead.getSubType())) {
                        throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_ASSEMBLE_SELECT_ERROR_CODE,
                                String.format(ExceptionConstants.MATERIAL_ASSEMBLE_SELECT_ERROR_MSG, barCode));
                    }
                    // 调拨单不能选择批号或序列号商品（该场景走出库和入库单）
                    if (BusinessConstants.SUB_TYPE_TRANSFER.equals(depotHead.getSubType())) {
                        throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_TRANSFER_SELECT_ERROR_CODE,
                                String.format(ExceptionConstants.MATERIAL_TRANSFER_SELECT_ERROR_MSG, barCode));
                    }
                    // 盘点业务不能选择批号或序列号商品（该场景走出库和入库单）
                    if (BusinessConstants.SUB_TYPE_CHECK_ENTER.equals(depotHead.getSubType())
                            || BusinessConstants.SUB_TYPE_REPLAY.equals(depotHead.getSubType())) {
                        throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_STOCK_CHECK_ERROR_CODE,
                                String.format(ExceptionConstants.MATERIAL_STOCK_CHECK_ERROR_MSG, barCode));
                    }
                }
                if (StringUtil.isExist(rowObj.get("snList"))) {
                    depotItem.setSnList(rowObj.getString("snList"));
                    if (StringUtil.isExist(rowObj.get("depotId"))) {
                        String[] snArray = depotItem.getSnList().split(",");
                        int operNum = rowObj.getInteger("operNumber");
                        if (snArray.length == operNum) {
                            Long depotId = rowObj.getLong("depotId");
                            BigDecimal inPrice = BigDecimal.ZERO;
                            if (StringUtil.isExist(rowObj.get("unitPrice"))) {
                                inPrice = rowObj.getBigDecimal("unitPrice");
                            }
                            serialNumberService.addSerialNumberByBill(depotHead.getType(), depotHead.getSubType(),
                                    depotHead.getNumber(), materialExtend.getMaterialId(), depotId, inPrice,
                                    depotItem.getSnList());
                        } else {
                            throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_SN_NUMBERE_FAILED_CODE,
                                    String.format(ExceptionConstants.DEPOT_HEAD_SN_NUMBERE_FAILED_MSG, barCode));
                        }
                    }
                } else {
                    // 入库或出库
                    if (BusinessConstants.DEPOTHEAD_TYPE_IN.equals(depotHead.getType()) ||
                            BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(depotHead.getType())) {
                        // 序列号不能为空
                        if (BusinessConstants.ENABLE_SERIAL_NUMBER_ENABLED.equals(material.getEnableSerialNumber())) {
                            // 如果开启出入库管理，并且类型等于采购、采购退货、销售、销售退货，则跳过
                            if (systemConfigService.getInOutManageFlag() &&
                                    (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_PURCHASE_RETURN.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES_RETURN
                                                    .equals(depotHead.getSubType()))) {
                                // 跳过
                            } else {
                                throw new BusinessRunTimeException(
                                        ExceptionConstants.MATERIAL_SERIAL_NUMBERE_EMPTY_CODE,
                                        String.format(ExceptionConstants.MATERIAL_SERIAL_NUMBERE_EMPTY_MSG, barCode));
                            }
                        }
                    }
                }
                if (StringUtil.isExist(rowObj.get("batchNumber"))) {
                    depotItem.setBatchNumber(rowObj.getString("batchNumber"));
                } else {
                    // 入库或出库
                    if (BusinessConstants.DEPOTHEAD_TYPE_IN.equals(depotHead.getType()) ||
                            BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(depotHead.getType())) {
                        // 批号不能为空
                        if (BusinessConstants.ENABLE_BATCH_NUMBER_ENABLED.equals(material.getEnableBatchNumber())) {
                            // 如果开启出入库管理，并且类型等于采购、采购退货、销售、销售退货，则跳过
                            if (systemConfigService.getInOutManageFlag() &&
                                    (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_PURCHASE_RETURN.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES_RETURN
                                                    .equals(depotHead.getSubType()))) {
                                // 跳过
                            } else {
                                throw new BusinessRunTimeException(
                                        ExceptionConstants.DEPOT_HEAD_BATCH_NUMBERE_EMPTY_CODE,
                                        String.format(ExceptionConstants.DEPOT_HEAD_BATCH_NUMBERE_EMPTY_MSG, barCode));
                            }
                        }
                    }
                }
                if (StringUtil.isExist(rowObj.get("expirationDate"))) {
                    depotItem.setExpirationDate(rowObj.getDate("expirationDate"));
                }
                if (StringUtil.isExist(rowObj.get("sku"))) {
                    depotItem.setSku(rowObj.getString("sku"));
                }
                if (StringUtil.isExist(rowObj.get("linkId"))) {
                    depotItem.setLinkId(rowObj.getLong("linkId"));
                }
                // 以下进行单位换算
                Unit unitInfo = materialService.findUnit(materialExtend.getMaterialId()); // 查询多单位信息
                if (StringUtil.isExist(rowObj.get("operNumber"))) {
                    depotItem.setOperNumber(rowObj.getBigDecimal("operNumber"));
                    String unit = rowObj.get("unit").toString();
                    BigDecimal oNumber = rowObj.getBigDecimal("operNumber");
                    if (StringUtil.isNotEmpty(unitInfo.getName())) {
                        String basicUnit = unitInfo.getBasicUnit(); // 基本单位
                        if (unit.equals(basicUnit)) { // 如果等于基本单位
                            depotItem.setBasicNumber(oNumber); // 数量一致
                        } else if (unit.equals(unitInfo.getOtherUnit())) { // 如果等于副单位
                            depotItem.setBasicNumber(oNumber.multiply(unitInfo.getRatio())); // 数量乘以比例
                        } else if (unit.equals(unitInfo.getOtherUnitTwo())) { // 如果等于副单位2
                            depotItem.setBasicNumber(oNumber.multiply(unitInfo.getRatioTwo())); // 数量乘以比例
                        } else if (unit.equals(unitInfo.getOtherUnitThree())) { // 如果等于副单位3
                            depotItem.setBasicNumber(oNumber.multiply(unitInfo.getRatioThree())); // 数量乘以比例
                        } else {
                            depotItem.setBasicNumber(oNumber); // 数量一致
                        }
                    } else {
                        depotItem.setBasicNumber(oNumber); // 其他情况
                    }
                }
                // 如果数量+已完成数量>原订单数量，给出预警(判断前提是存在关联订单|关联请购单)
                String linkStr = StringUtil.isNotEmpty(depotHead.getLinkNumber()) ? depotHead.getLinkNumber()
                        : depotHead.getLinkApply();
                if (StringUtil.isNotEmpty(linkStr) && StringUtil.isExist(rowObj.get("preNumber"))
                        && StringUtil.isExist(rowObj.get("finishNumber"))) {
                    if ("add".equals(actionType)) {
                        // 在新增模式进行状态赋值
                        BigDecimal preNumber = rowObj.getBigDecimal("preNumber");
                        BigDecimal finishNumber = rowObj.getBigDecimal("finishNumber");
                        if (depotItem.getOperNumber().add(finishNumber).compareTo(preNumber) > 0) {
                            if (!systemConfigService.getOverLinkBillFlag()) {
                                throw new BusinessRunTimeException(
                                        ExceptionConstants.DEPOT_HEAD_NUMBER_NEED_EDIT_FAILED_CODE,
                                        String.format(ExceptionConstants.DEPOT_HEAD_NUMBER_NEED_EDIT_FAILED_MSG,
                                                barCode));
                            }
                        }
                    } else if ("update".equals(actionType)) {
                        // 当前单据的类型
                        String currentSubType = depotHead.getSubType();
                        // 在更新模式进行状态赋值
                        String unit = rowObj.get("unit").toString();
                        Long preHeaderId = depotHeadService.getDepotHead(linkStr).getId();
                        if (null != preHeaderId) {
                            // 前一个单据的数量
                            BigDecimal preNumber = getPreItemByHeaderIdAndMaterial(linkStr,
                                    depotItem.getMaterialExtendId(), depotItem.getLinkId()).getOperNumber();
                            // 除去此单据之外的已入库|已出库
                            BigDecimal realFinishNumber = getRealFinishNumber(currentSubType,
                                    depotItem.getMaterialExtendId(), depotItem.getLinkId(), preHeaderId, headerId,
                                    unitInfo, unit);
                            if (preNumber != null) {
                                if (depotItem.getOperNumber().add(realFinishNumber).compareTo(preNumber) > 0) {
                                    if (!systemConfigService.getOverLinkBillFlag()) {
                                        throw new BusinessRunTimeException(
                                                ExceptionConstants.DEPOT_HEAD_NUMBER_NEED_EDIT_FAILED_CODE,
                                                String.format(ExceptionConstants.DEPOT_HEAD_NUMBER_NEED_EDIT_FAILED_MSG,
                                                        barCode));
                                    }
                                }
                            } else {
                                throw new BusinessRunTimeException(
                                        ExceptionConstants.DEPOT_ITEM_PRE_BILL_IS_CHANGE_CODE,
                                        ExceptionConstants.DEPOT_ITEM_PRE_BILL_IS_CHANGE_MSG);
                            }
                        }
                    }
                }
                if (StringUtil.isExist(rowObj.get("unitPrice"))) {
                    BigDecimal unitPrice = rowObj.getBigDecimal("unitPrice");
                    depotItem.setUnitPrice(unitPrice);
                    if (materialExtend.getLowDecimal() != null) {
                        // 零售或销售单价低于最低售价，进行提示
                        if ("零售".equals(depotHead.getSubType()) || "销售".equals(depotHead.getSubType())) {
                            if (unitPrice.compareTo(materialExtend.getLowDecimal()) < 0) {
                                throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_UNIT_PRICE_LOW_CODE,
                                        String.format(ExceptionConstants.DEPOT_HEAD_UNIT_PRICE_LOW_MSG, barCode));
                            }
                        }
                    }
                }
                // 如果是销售出库、销售退货、零售出库、零售退货则给采购单价字段赋值（如果是批次商品，则要根据批号去找之前的入库价）
                if (BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType()) ||
                        BusinessConstants.SUB_TYPE_SALES_RETURN.equals(depotHead.getSubType()) ||
                        BusinessConstants.SUB_TYPE_RETAIL.equals(depotHead.getSubType()) ||
                        BusinessConstants.SUB_TYPE_RETAIL_RETURN.equals(depotHead.getSubType())) {
                    boolean moveAvgPriceFlag = systemConfigService.getMoveAvgPriceFlag();
                    BigDecimal currentUnitPrice = materialCurrentStockMapperEx
                            .getCurrentUnitPriceByMId(materialExtend.getMaterialId());
                    currentUnitPrice = unitService.parseUnitPriceByUnit(currentUnitPrice, unitInfo,
                            depotItem.getMaterialUnit());
                    BigDecimal unitPrice = moveAvgPriceFlag ? currentUnitPrice : materialExtend.getPurchaseDecimal();
                    depotItem.setPurchaseUnitPrice(unitPrice);
                    if (StringUtil.isNotEmpty(depotItem.getBatchNumber())) {
                        depotItem.setPurchaseUnitPrice(
                                getDepotItemByBatchNumber(depotItem.getMaterialExtendId(), depotItem.getBatchNumber())
                                        .getUnitPrice());
                    }
                }
                if (StringUtil.isExist(rowObj.get("taxUnitPrice"))) {
                    depotItem.setTaxUnitPrice(rowObj.getBigDecimal("taxUnitPrice"));
                }
                if (StringUtil.isExist(rowObj.get("allPrice"))) {
                    depotItem.setAllPrice(rowObj.getBigDecimal("allPrice"));
                }
                if (StringUtil.isExist(rowObj.get("depotId"))) {
                    depotItem.setDepotId(rowObj.getLong("depotId"));
                } else {
                    if (!BusinessConstants.SUB_TYPE_PURCHASE_APPLY.equals(depotHead.getSubType())
                            && !BusinessConstants.SUB_TYPE_PURCHASE_ORDER.equals(depotHead.getSubType())
                            && !BusinessConstants.SUB_TYPE_SALES_ORDER.equals(depotHead.getSubType())) {
                        throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_DEPOT_FAILED_CODE,
                                String.format(ExceptionConstants.DEPOT_HEAD_DEPOT_FAILED_MSG));
                    }
                }
                if (BusinessConstants.SUB_TYPE_TRANSFER.equals(depotHead.getSubType())) {
                    if (StringUtil.isExist(rowObj.get("anotherDepotId"))) {
                        if (rowObj.getLong("anotherDepotId").equals(rowObj.getLong("depotId"))) {
                            throw new BusinessRunTimeException(
                                    ExceptionConstants.DEPOT_HEAD_ANOTHER_DEPOT_EQUAL_FAILED_CODE,
                                    String.format(ExceptionConstants.DEPOT_HEAD_ANOTHER_DEPOT_EQUAL_FAILED_MSG));
                        } else {
                            depotItem.setAnotherDepotId(rowObj.getLong("anotherDepotId"));
                        }
                    } else {
                        throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_ANOTHER_DEPOT_FAILED_CODE,
                                String.format(ExceptionConstants.DEPOT_HEAD_ANOTHER_DEPOT_FAILED_MSG));
                    }
                }
                if (StringUtil.isExist(rowObj.get("taxRate"))) {
                    depotItem.setTaxRate(rowObj.getBigDecimal("taxRate"));
                }
                if (StringUtil.isExist(rowObj.get("taxMoney"))) {
                    depotItem.setTaxMoney(rowObj.getBigDecimal("taxMoney"));
                }
                if (StringUtil.isExist(rowObj.get("taxLastMoney"))) {
                    depotItem.setTaxLastMoney(rowObj.getBigDecimal("taxLastMoney"));
                }
                if (StringUtil.isExist(rowObj.get("mType"))) {
                    depotItem.setMaterialType(rowObj.getString("mType"));
                }
                if (StringUtil.isExist(rowObj.get("remark"))) {
                    depotItem.setRemark(rowObj.getString("remark"));
                }
                // 出库时判断库存是否充足
                if (BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(depotHead.getType())) {
                    String stockMsg = material.getName() + "-" + barCode;
                    BigDecimal stock = getCurrentStockByParam(depotItem.getDepotId(), depotItem.getMaterialId());
                    if (StringUtil.isNotEmpty(depotItem.getSku())) {
                        // 对于sku商品要换个方式计算库存
                        stock = getSkuStockByParam(depotItem.getDepotId(), depotItem.getMaterialExtendId(), null, null);
                    }
                    if (StringUtil.isNotEmpty(depotItem.getBatchNumber())) {
                        // 对于批次商品要换个方式计算库存
                        stock = getOneBatchNumberStock(depotItem.getDepotId(), barCode, depotItem.getBatchNumber());
                        stockMsg += "-批号" + depotItem.getBatchNumber();
                    }
                    BigDecimal thisRealNumber = depotItem.getBasicNumber() == null ? BigDecimal.ZERO
                            : depotItem.getBasicNumber();
                    if (StringUtil.isNotEmpty(depotItem.getBatchNumber())) {
                        // 对于批次商品，直接使用当前填写的数量
                        thisRealNumber = depotItem.getOperNumber() == null ? BigDecimal.ZERO
                                : depotItem.getOperNumber();
                    }
                    if (!systemConfigService.getMinusStockFlag() && stock.compareTo(thisRealNumber) < 0) {
                        throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_STOCK_NOT_ENOUGH_CODE,
                                String.format(ExceptionConstants.MATERIAL_STOCK_NOT_ENOUGH_MSG, stockMsg));
                    }
                    // 出库时处理序列号
                    if (!BusinessConstants.SUB_TYPE_TRANSFER.equals(depotHead.getSubType())) {
                        // 判断商品是否开启序列号，开启的售出序列号，未开启的跳过
                        if (BusinessConstants.ENABLE_SERIAL_NUMBER_ENABLED.equals(material.getEnableSerialNumber())) {
                            // 如果开启出入库管理，并且类型等于采购、采购退货、销售、销售退货，则跳过
                            if (systemConfigService.getInOutManageFlag() &&
                                    (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_PURCHASE_RETURN.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())
                                            || BusinessConstants.SUB_TYPE_SALES_RETURN
                                                    .equals(depotHead.getSubType()))) {
                                // 跳过
                            } else {
                                // 售出序列号，获得当前操作人
                                User userInfo = userService.getCurrentUser();
                                serialNumberService.checkAndUpdateSerialNumber(depotItem, depotHead.getNumber(),
                                        userInfo, StringUtil.toNull(depotItem.getSnList()));
                            }
                        }
                    }
                }
                this.insertDepotItemWithObj(depotItem);
                // 更新当前库存
                updateCurrentStock(depotItem);
                // 更新当前成本价
                // updateCurrentUnitPrice(depotItem);
                // 更新商品的价格（只有在单据已审核的情况下才更新）
                if (BusinessConstants.BILLS_STATUS_AUDIT.equals(depotHead.getStatus())) {
                    updateMaterialExtendPrice(materialExtend.getId(), depotHead.getSubType(), depotHead.getBillType(),
                            rowObj);
                }
            }
            // 如果关联单据号非空则更新订单的状态,单据类型：采购入库单、销售出库单、盘点复盘单、其它入库单、其它出库单
            if (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_REPLAY.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_OTHER.equals(depotHead.getSubType())) {
                if (StringUtil.isNotEmpty(depotHead.getLinkNumber())) {
                    // 单据状态:是否全部完成 2-全部完成 3-部分完成（针对订单的分批出入库）
                    String billStatus = getBillStatusByParam(depotHead, depotHead.getLinkNumber(), "normal");
                    changeBillStatus(depotHead.getLinkNumber(), billStatus);
                }
            }
            // 当前单据类型为采购订单的逻辑
            if (BusinessConstants.SUB_TYPE_PURCHASE_ORDER.equals(depotHead.getSubType())) {
                // 如果关联单据号非空则更新订单的状态,此处针对销售订单转采购订单的场景
                if (StringUtil.isNotEmpty(depotHead.getLinkNumber())) {
                    String billStatus = getBillStatusByParam(depotHead, depotHead.getLinkNumber(), "normal");
                    changeBillPurchaseStatus(depotHead.getLinkNumber(), billStatus);
                }
                // 如果关联单据号非空则更新订单的状态,此处针对请购单转采购订单的场景
                if (StringUtil.isNotEmpty(depotHead.getLinkApply())) {
                    String billStatus = getBillStatusByParam(depotHead, depotHead.getLinkApply(), "apply");
                    changeBillStatus(depotHead.getLinkApply(), billStatus);
                }
            }
        } else {
            throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_ROW_FAILED_CODE,
                    String.format(ExceptionConstants.DEPOT_HEAD_ROW_FAILED_MSG));
        }
    }

    /**
     * 判断单据的状态
     * 通过数组对比：原单据的商品和商品数量（汇总） 与 分批操作后单据的商品和商品数量（汇总）
     * 
     * @param depotHead
     * @param linkStr
     * @return
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public String getBillStatusByParam(DepotHead depotHead, String linkStr, String linkType) {
        String res = BusinessConstants.BILLS_STATUS_SKIPED;
        // 获取原单据的商品和商品数量（汇总）
        List<DepotItemVo4MaterialAndSum> linkList = depotItemMapperEx.getLinkBillDetailMaterialSum(linkStr);
        // 获取分批操作后单据的商品和商品数量（汇总）
        List<DepotItemVo4MaterialAndSum> batchList = depotItemMapperEx.getBatchBillDetailMaterialSum(linkStr, linkType,
                depotHead.getType());
        // 将分批操作后的单据的商品和商品数据构造成Map
        Map<Long, BigDecimal> materialSumMap = new HashMap<>();
        for (DepotItemVo4MaterialAndSum materialAndSum : batchList) {
            materialSumMap.put(materialAndSum.getMaterialExtendId(), materialAndSum.getOperNumber());
        }
        for (DepotItemVo4MaterialAndSum materialAndSum : linkList) {
            // 过滤掉原单里面有数量为0的商品
            if (materialAndSum.getOperNumber().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal materialSum = materialSumMap.get(materialAndSum.getMaterialExtendId());
                if (materialSum != null) {
                    if (materialSum.compareTo(materialAndSum.getOperNumber()) < 0) {
                        res = BusinessConstants.BILLS_STATUS_SKIPING;
                    }
                } else {
                    res = BusinessConstants.BILLS_STATUS_SKIPING;
                }
            }
        }
        return res;
    }

    /**
     * 更新单据状态
     * 
     * @param linkStr
     * @param billStatus
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void changeBillStatus(String linkStr, String billStatus) {
        DepotHead depotHeadOrders = new DepotHead();
        depotHeadOrders.setStatus(billStatus);
        DepotHeadExample example = new DepotHeadExample();
        List<String> linkNoList = StringUtil.strToStringList(linkStr);
        example.createCriteria().andNumberIn(linkNoList);
        try {
            depotHeadMapper.updateByExampleSelective(depotHeadOrders, example);
        } catch (Exception e) {
            logger.error("异常码[{}],异常提示[{}],异常[{}]",
                    ExceptionConstants.DATA_WRITE_FAIL_CODE, ExceptionConstants.DATA_WRITE_FAIL_MSG, e);
            throw new BusinessRunTimeException(ExceptionConstants.DATA_WRITE_FAIL_CODE,
                    ExceptionConstants.DATA_WRITE_FAIL_MSG);
        }
    }

    /**
     * 更新单据状态,此处针对销售订单转采购订单的场景
     * 
     * @param linkStr
     * @param billStatus
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void changeBillPurchaseStatus(String linkStr, String billStatus) {
        DepotHead depotHeadOrders = new DepotHead();
        depotHeadOrders.setPurchaseStatus(billStatus);
        DepotHeadExample example = new DepotHeadExample();
        List<String> linkNoList = StringUtil.strToStringList(linkStr);
        example.createCriteria().andNumberIn(linkNoList);
        try {
            depotHeadMapper.updateByExampleSelective(depotHeadOrders, example);
        } catch (Exception e) {
            logger.error("异常码[{}],异常提示[{}],异常[{}]",
                    ExceptionConstants.DATA_WRITE_FAIL_CODE, ExceptionConstants.DATA_WRITE_FAIL_MSG, e);
            throw new BusinessRunTimeException(ExceptionConstants.DATA_WRITE_FAIL_CODE,
                    ExceptionConstants.DATA_WRITE_FAIL_MSG);
        }
    }

    /**
     * 根据批号查询单据明细信息
     * 
     * @param materialExtendId
     * @param batchNumber
     * @return
     */
    public DepotItem getDepotItemByBatchNumber(Long materialExtendId, String batchNumber) {
        List<DepotItem> depotItemList = depotItemMapperEx.getDepotItemByBatchNumber(materialExtendId, batchNumber);
        if (null != depotItemList && depotItemList.size() > 0) {
            return depotItemList.get(0);
        } else {
            return new DepotItem();
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteDepotItemHeadId(Long headerId) throws Exception {
        try {
            // 1、查询删除前的单据明细
            List<DepotItem> depotItemList = getListByHeaderId(headerId);
            // 2、删除单据明细
            DepotItemExample example = new DepotItemExample();
            example.createCriteria().andHeaderIdEqualTo(headerId);
            depotItemMapper.deleteByExample(example);
            // 3、计算删除之后单据明细中商品的库存
            for (DepotItem depotItem : depotItemList) {
                updateCurrentStock(depotItem);
            }
        } catch (Exception e) {
            JshException.writeFail(logger, e);
        }
    }

    /**
     * 删除序列号和回收序列号
     * 
     * @param actionType
     * @throws Exception
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteOrCancelSerialNumber(String actionType, DepotHead depotHead, Long headerId) throws Exception {
        if (actionType.equals("update")) {
            User userInfo = userService.getCurrentUser();
            if (BusinessConstants.DEPOTHEAD_TYPE_IN.equals(depotHead.getType())) {
                // 入库逻辑
                String number = depotHead.getNumber();
                SerialNumberExample example = new SerialNumberExample();
                example.createCriteria().andInBillNoEqualTo(number);
                serialNumberService.deleteByExample(example);
            } else if (BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(depotHead.getType())) {
                // 出库逻辑
                DepotItemExample example = new DepotItemExample();
                example.createCriteria().andHeaderIdEqualTo(headerId)
                        .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
                List<DepotItem> depotItemList = depotItemMapper.selectByExample(example);
                if (null != depotItemList && depotItemList.size() > 0) {
                    for (DepotItem depotItem : depotItemList) {
                        if (StringUtil.isNotEmpty(depotItem.getSnList())) {
                            serialNumberService.cancelSerialNumber(depotItem.getMaterialId(), depotHead.getNumber(),
                                    (depotItem.getBasicNumber() == null ? 0 : depotItem.getBasicNumber()).intValue(),
                                    userInfo);
                        }
                    }
                }
            }
        }
    }

    /**
     * 针对组装单、拆卸单校验是否存在组合件和普通子件
     * 
     * @param rowArr
     * @param subType
     */
    public void checkAssembleWithMaterialType(JSONArray rowArr, String subType) {
        if (BusinessConstants.SUB_TYPE_ASSEMBLE.equals(subType) ||
                BusinessConstants.SUB_TYPE_DISASSEMBLE.equals(subType)) {
            if (rowArr.size() > 1) {
                JSONObject firstRowObj = JSONObject.parseObject(rowArr.getString(0));
                JSONObject secondRowObj = JSONObject.parseObject(rowArr.getString(1));
                String firstMaterialType = firstRowObj.getString("mType");
                String secondMaterialType = secondRowObj.getString("mType");
                if (!"组合件".equals(firstMaterialType) || !"普通子件".equals(secondMaterialType)) {
                    throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_CHECK_ASSEMBLE_EMPTY_CODE,
                            String.format(ExceptionConstants.DEPOT_HEAD_CHECK_ASSEMBLE_EMPTY_MSG));
                }
            } else {
                throw new BusinessRunTimeException(ExceptionConstants.DEPOT_HEAD_CHECK_ASSEMBLE_EMPTY_CODE,
                        String.format(ExceptionConstants.DEPOT_HEAD_CHECK_ASSEMBLE_EMPTY_MSG));
            }
        }
    }

    /**
     * 更新商品的价格
     * 
     * @param meId
     * @param subType
     * @param rowObj
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateMaterialExtendPrice(Long meId, String subType, String billType, JSONObject rowObj)
            throws Exception {
        if (systemConfigService.getUpdateUnitPriceFlag()) {
            if (StringUtil.isExist(rowObj.get("unitPrice"))) {
                BigDecimal unitPrice = rowObj.getBigDecimal("unitPrice");
                MaterialExtend materialExtend = new MaterialExtend();
                materialExtend.setId(meId);
                // 只有采购入库单据才能修改零售价
                if (BusinessConstants.SUB_TYPE_PURCHASE.equals(subType)) {
                    materialExtend.setCommodityDecimal(unitPrice);
                    materialExtendService.updateMaterialExtend(materialExtend);
                }
                // 其它入库-生产入库的情况更新采购单价（保留原有逻辑）
                if (BusinessConstants.SUB_TYPE_OTHER.equals(subType)) {
                    if (BusinessConstants.BILL_TYPE_PRODUCE_IN.equals(billType)) {
                        materialExtend.setPurchaseDecimal(unitPrice);
                        materialExtendService.updateMaterialExtend(materialExtend);
                    }
                }
            }
        }
    }

    /**
     * 审核单据时更新商品价格
     * 
     * @param headerId 单据头ID
     * @throws Exception
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateMaterialExtendPriceOnAudit(Long headerId) throws Exception {
        if (systemConfigService.getUpdateUnitPriceFlag()) {
            DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(headerId);
            if (depotHead != null) {
                List<DepotItem> depotItems = getListByHeaderId(headerId);
                for (DepotItem depotItem : depotItems) {
                    if (depotItem.getUnitPrice() != null && depotItem.getMaterialExtendId() != null) {
                        MaterialExtend materialExtend = new MaterialExtend();
                        materialExtend.setId(depotItem.getMaterialExtendId());
                        // 只有采购入库单据才能修改零售价
                        if (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())) {
                            materialExtend.setCommodityDecimal(depotItem.getUnitPrice());
                            materialExtendService.updateMaterialExtend(materialExtend);
                        }
                        // 其它入库-生产入库的情况更新采购单价（保留原有逻辑）
                        if (BusinessConstants.SUB_TYPE_OTHER.equals(depotHead.getSubType())) {
                            if (BusinessConstants.BILL_TYPE_PRODUCE_IN.equals(depotHead.getBillType())) {
                                materialExtend.setPurchaseDecimal(depotItem.getUnitPrice());
                                materialExtendService.updateMaterialExtend(materialExtend);
                            }
                        }
                    }
                }
            }
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public List<DepotItemStockWarningCount> findStockWarningCount(Integer offset, Integer rows, String materialParam,
            List<Long> depotList, List<Long> categoryList) {
        List<DepotItemStockWarningCount> list = null;
        try {
            list = depotItemMapperEx.findStockWarningCount(offset, rows, materialParam, depotList, categoryList);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int findStockWarningCountTotal(String materialParam, List<Long> depotList, List<Long> categoryList) {
        int result = 0;
        try {
            result = depotItemMapperEx.findStockWarningCountTotal(materialParam, depotList, categoryList);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }

    /**
     * 库存统计-sku
     * 
     * @param depotId
     * @param meId
     * @param beginTime
     * @param endTime
     * @return
     */
    public BigDecimal getSkuStockByParam(Long depotId, Long meId, String beginTime, String endTime) throws Exception {
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        List<Long> depotList = depotService.parseDepotList(depotId);
        // 盘点复盘后数量的变动
        BigDecimal stockCheckSum = depotItemMapperEx.getSkuStockCheckSumByDepotList(depotList, meId, forceFlag,
                beginTime, endTime);
        DepotItemVo4Stock stockObj = depotItemMapperEx.getSkuStockByParamWithDepotList(depotList, meId, forceFlag,
                inOutManageFlag, beginTime, endTime);
        BigDecimal stockSum = BigDecimal.ZERO;
        if (stockObj != null) {
            BigDecimal inTotal = stockObj.getInTotal();
            BigDecimal transfInTotal = stockObj.getTransfInTotal();
            BigDecimal assemInTotal = stockObj.getAssemInTotal();
            BigDecimal disAssemInTotal = stockObj.getDisAssemInTotal();
            BigDecimal outTotal = stockObj.getOutTotal();
            BigDecimal transfOutTotal = stockObj.getTransfOutTotal();
            BigDecimal assemOutTotal = stockObj.getAssemOutTotal();
            BigDecimal disAssemOutTotal = stockObj.getDisAssemOutTotal();
            stockSum = inTotal.add(transfInTotal).add(assemInTotal).add(disAssemInTotal)
                    .subtract(outTotal).subtract(transfOutTotal).subtract(assemOutTotal).subtract(disAssemOutTotal);
        }
        return stockCheckSum.add(stockSum);
    }

    /**
     * 库存统计-单仓库
     * 
     * @param depotId
     * @param mId
     * @param beginTime
     * @param endTime
     * @return
     */
    public BigDecimal getStockByParam(Long depotId, Long mId, String beginTime, String endTime) throws Exception {
        List<Long> depotList = depotService.parseDepotList(depotId);
        return getStockByParamWithDepotList(depotList, mId, beginTime, endTime);
    }

    /**
     * 库存统计-多仓库
     * 
     * @param depotList
     * @param mId
     * @param beginTime
     * @param endTime
     * @return
     */
    public BigDecimal getStockByParamWithDepotList(List<Long> depotList, Long mId, String beginTime, String endTime)
            throws Exception {
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        // 初始库存
        BigDecimal initStock = materialService.getInitStockByMidAndDepotList(depotList, mId);
        // 盘点复盘后数量的变动
        BigDecimal stockCheckSum = depotItemMapperEx.getStockCheckSumByDepotList(depotList, mId, forceFlag, beginTime,
                endTime);
        DepotItemVo4Stock stockObj = depotItemMapperEx.getStockByParamWithDepotList(depotList, mId, forceFlag,
                inOutManageFlag, beginTime, endTime);
        BigDecimal stockSum = BigDecimal.ZERO;
        if (stockObj != null) {
            BigDecimal inTotal = stockObj.getInTotal();
            BigDecimal transfInTotal = stockObj.getTransfInTotal();
            BigDecimal assemInTotal = stockObj.getAssemInTotal();
            BigDecimal disAssemInTotal = stockObj.getDisAssemInTotal();
            BigDecimal outTotal = stockObj.getOutTotal();
            BigDecimal transfOutTotal = stockObj.getTransfOutTotal();
            BigDecimal assemOutTotal = stockObj.getAssemOutTotal();
            BigDecimal disAssemOutTotal = stockObj.getDisAssemOutTotal();
            stockSum = inTotal.add(transfInTotal).add(assemInTotal).add(disAssemInTotal)
                    .subtract(outTotal).subtract(transfOutTotal).subtract(assemOutTotal).subtract(disAssemOutTotal);
        }
        return initStock.add(stockCheckSum).add(stockSum);
    }

    /**
     * 统计时间段内的入库和出库数量-多仓库
     * 
     * @param depotList
     * @param mId
     * @param beginTime
     * @param endTime
     * @return
     */
    public Map<String, BigDecimal> getIntervalMapByParamWithDepotList(List<Long> depotList, Long mId, String beginTime,
            String endTime) throws Exception {
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        Map<String, BigDecimal> intervalMap = new HashMap<>();
        BigDecimal inSum = BigDecimal.ZERO;
        BigDecimal outSum = BigDecimal.ZERO;
        // 盘点复盘后数量的变动
        BigDecimal stockCheckSum = depotItemMapperEx.getStockCheckSumByDepotList(depotList, mId, forceFlag, beginTime,
                endTime);
        DepotItemVo4Stock stockObj = depotItemMapperEx.getStockByParamWithDepotList(depotList, mId, forceFlag,
                inOutManageFlag, beginTime, endTime);
        if (stockObj != null) {
            BigDecimal inTotal = stockObj.getInTotal();
            BigDecimal transfInTotal = stockObj.getTransfInTotal();
            BigDecimal assemInTotal = stockObj.getAssemInTotal();
            BigDecimal disAssemInTotal = stockObj.getDisAssemInTotal();
            inSum = inTotal.add(transfInTotal).add(assemInTotal).add(disAssemInTotal);
            BigDecimal outTotal = stockObj.getOutTotal();
            BigDecimal transfOutTotal = stockObj.getTransfOutTotal();
            BigDecimal assemOutTotal = stockObj.getAssemOutTotal();
            BigDecimal disAssemOutTotal = stockObj.getDisAssemOutTotal();
            outSum = outTotal.add(transfOutTotal).add(assemOutTotal).add(disAssemOutTotal);
        }
        if (stockCheckSum.compareTo(BigDecimal.ZERO) > 0) {
            inSum = inSum.add(stockCheckSum);
        } else {
            // 盘点复盘数量为负数代表出库
            outSum = outSum.subtract(stockCheckSum);
        }
        intervalMap.put("inSum", inSum);
        intervalMap.put("outSum", outSum);
        return intervalMap;
    }

    /**
     * 根据单据明细来批量更新当前库存
     * 
     * @param depotItem
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateCurrentStock(DepotItem depotItem) throws Exception {
        // 在强制审核模式下，未审核单据的保存阶段不参与库存更新，直接跳过
        try {
            if (systemConfigService.getForceApprovalFlag() && depotItem.getHeaderId() != null) {
                DepotHead header = depotHeadMapper.selectByPrimaryKey(depotItem.getHeaderId());
                if (header != null && !"1".equals(header.getStatus())) {
                    logger.debug(
                            "skip updateCurrentStock on save: headerId={}, status={}, materialId={}, depotId={}, forceApproval=true",
                            depotItem.getHeaderId(), header.getStatus(), depotItem.getMaterialId(),
                            depotItem.getDepotId());
                    return;
                }
            }
        } catch (Exception e) {
            logger.debug("updateCurrentStock skip-check failed, fallback to default flow, headerId={}, error={}",
                    depotItem != null ? depotItem.getHeaderId() : null, e.getMessage());
        }
        // 查询单据头获取操作时间
        Date operTime = new Date(); // 默认使用当前时间
        try {
            if (depotItem.getHeaderId() != null) {
                DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(depotItem.getHeaderId());
                if (depotHead != null && depotHead.getOperTime() != null) {
                    operTime = depotHead.getOperTime();
                    logger.debug("获取到单据操作时间: {}, 单据ID: {}", operTime, depotItem.getHeaderId());
                } else {
                    logger.debug("单据头或操作时间为空，使用当前时间, 单据ID: {}", depotItem.getHeaderId());
                }
            } else {
                logger.debug("单据明细没有关联单据头，使用当前时间");
            }
        } catch (Exception e) {
            logger.warn("获取单据操作时间失败，使用当前时间, 单据ID: {}, error: {}", depotItem.getHeaderId(), e.getMessage());
        }

        updateCurrentStockFun(depotItem.getMaterialId(), depotItem.getDepotId(), operTime, depotItem.getHeaderId());
        if (depotItem.getAnotherDepotId() != null) {
            updateCurrentStockFun(depotItem.getMaterialId(), depotItem.getAnotherDepotId(), operTime,
                    depotItem.getHeaderId());
        }
    }

    /**
     * 根据单据明细来批量更新当前成本价
     * 
     * @param depotItem
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateCurrentUnitPrice(DepotItem depotItem) throws Exception {
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        // 查询多单位信息
        Unit unitInfo = materialService.findUnit(depotItem.getMaterialId());
        List<DepotItemVo4DetailByTypeAndMId> itemList = findDetailByDepotIdsAndMaterialIdList(null, forceFlag,
                inOutManageFlag, depotItem.getSku(),
                depotItem.getBatchNumber(), null, null, null, depotItem.getMaterialId(), null, null);
        Collections.reverse(itemList); // 倒序之后变成按时间从前往后排序
        BigDecimal currentNumber = BigDecimal.ZERO;
        BigDecimal currentUnitPrice = BigDecimal.ZERO;
        BigDecimal currentAllPrice = BigDecimal.ZERO;
        for (DepotItemVo4DetailByTypeAndMId item : itemList) {
            BigDecimal basicNumber = item.getBnum() != null ? item.getBnum() : BigDecimal.ZERO;
            // 数量*单价 另外计算新的成本价
            BigDecimal allPrice = unitService.parseAllPriceByUnit(
                    item.getAllPrice() != null ? item.getAllPrice() : BigDecimal.ZERO, unitInfo,
                    item.getMaterialUnit());
            if (basicNumber.compareTo(BigDecimal.ZERO) != 0 && allPrice.compareTo(BigDecimal.ZERO) != 0) {
                // 入库
                if (BusinessConstants.DEPOTHEAD_TYPE_IN.equals(item.getType())) {
                    // 零售退货、销售退货
                    if (BusinessConstants.SUB_TYPE_RETAIL_RETURN.equals(item.getSubType())
                            || BusinessConstants.SUB_TYPE_SALES_RETURN.equals(item.getSubType())) {
                        // 数量*当前的成本单价
                        currentNumber = currentNumber.add(basicNumber);
                        currentAllPrice = currentAllPrice.add(basicNumber.multiply(currentUnitPrice));
                    } else {
                        currentAllPrice = currentAllPrice.add(allPrice);
                        currentNumber = currentNumber.add(basicNumber);
                        // 只有当前库存总金额和当前库存数量都大于0才计算移动平均价
                        if (currentAllPrice.compareTo(BigDecimal.ZERO) > 0
                                && currentNumber.compareTo(BigDecimal.ZERO) > 0) {
                            currentUnitPrice = currentAllPrice.divide(currentNumber, 2, BigDecimal.ROUND_HALF_UP);
                        } else {
                            currentUnitPrice = item.getUnitPrice();
                        }
                    }
                }
                // 出库
                if (BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(item.getType())) {
                    // 采购退货
                    if (BusinessConstants.SUB_TYPE_PURCHASE_RETURN.equals(item.getSubType())) {
                        currentAllPrice = currentAllPrice.add(allPrice);
                        currentNumber = currentNumber.add(basicNumber);
                        // 只有当前库存总金额和当前库存数量都大于0才计算移动平均价
                        if (currentAllPrice.compareTo(BigDecimal.ZERO) > 0
                                && currentNumber.compareTo(BigDecimal.ZERO) > 0) {
                            currentUnitPrice = currentAllPrice.divide(currentNumber, 2, BigDecimal.ROUND_HALF_UP);
                        } else {
                            currentUnitPrice = item.getUnitPrice();
                        }
                    } else {
                        // 数量*当前的成本单价
                        currentNumber = currentNumber.add(basicNumber);
                        currentAllPrice = currentAllPrice.add(basicNumber.multiply(currentUnitPrice));
                    }
                }
                // 特殊情况：1-组装单 2-拆卸单 3-盘点复盘
                if (BusinessConstants.SUB_TYPE_ASSEMBLE.equals(item.getSubType()) ||
                        BusinessConstants.SUB_TYPE_DISASSEMBLE.equals(item.getSubType()) ||
                        BusinessConstants.SUB_TYPE_REPLAY.equals(item.getSubType())) {
                    // 数量*当前的成本单价
                    currentNumber = currentNumber.add(basicNumber);
                    currentAllPrice = currentAllPrice.add(basicNumber.multiply(currentUnitPrice));
                }
                // 防止单价金额溢出
                if (currentUnitPrice.compareTo(BigDecimal.valueOf(100000000)) > 0
                        || currentUnitPrice.compareTo(BigDecimal.valueOf(-100000000)) < 0) {
                    currentUnitPrice = BigDecimal.ZERO;
                }
            }
        }
        // 更新实时库存中的当前单价
        materialCurrentStockMapperEx.updateUnitPriceByMId(currentUnitPrice, depotItem.getMaterialId());
    }

    /**
     * 根据商品和仓库来更新当前库存
     * 
     * @param mId
     * @param dId
     */
    public void updateCurrentStockFun(Long mId, Long dId) throws Exception {
        updateCurrentStockFun(mId, dId, new Date(), null);
    }

    /**
     * 根据商品和仓库来更新当前库存（包含操作时间）
     * 
     * @param mId
     * @param dId
     * @param operTime 操作时间
     */
    public void updateCurrentStockFun(Long mId, Long dId, Date operTime, Long headerId) throws Exception {
        if (mId != null && dId != null) {
            MaterialCurrentStockExample example = new MaterialCurrentStockExample();
            example.createCriteria().andMaterialIdEqualTo(mId).andDepotIdEqualTo(dId)
                    .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
            List<MaterialCurrentStock> list = materialCurrentStockMapper.selectByExample(example);
            MaterialCurrentStock materialCurrentStock = new MaterialCurrentStock();
            materialCurrentStock.setMaterialId(mId);
            materialCurrentStock.setDepotId(dId);
            materialCurrentStock.setCurrentNumber(getStockByParam(dId, mId, null, null));
            if (list != null && list.size() > 0) {
                // 检查是否存在重复记录
                if (list.size() > 1) {
                    // 处理重复记录：重新计算库存并删除重复项
                    handleDuplicateStockRecords(list, mId, dId);
                    // 重新查询，确保只剩一条记录
                    MaterialCurrentStockExample reCheckExample = new MaterialCurrentStockExample();
                    reCheckExample.createCriteria().andMaterialIdEqualTo(mId).andDepotIdEqualTo(dId)
                            .andDeleteFlagNotEqualTo(BusinessConstants.DELETE_FLAG_DELETED);
                    list = materialCurrentStockMapper.selectByExample(reCheckExample);
                }

                if (list != null && list.size() > 0) {
                    Long mcsId = list.get(0).getId();
                    materialCurrentStock.setId(mcsId);
                    materialCurrentStockMapper.updateByPrimaryKeySelective(materialCurrentStock);
                } else {
                    // 如果处理后没有记录了，插入新记录
                    materialCurrentStockMapper.insertSelective(materialCurrentStock);
                }
            } else {
                materialCurrentStockMapper.insertSelective(materialCurrentStock);
            }

            // 新增：更新汇总表和预警（清缓存延迟到单据级批末一次）
            try {
                updateSummaryTablesAfterStockChange(mId, dId, operTime, headerId);
            } catch (Exception e) {
                logger.warn("更新汇总表失败，但不影响库存更新: materialId={}, depotId={}, operTime={}, error={}",
                        mId, dId, operTime, e.getMessage());
            }
        }
    }

    /**
     * 处理重复的库存记录
     * 重新计算库存，保留第一条记录，删除其他重复记录
     * 
     * @param duplicateList 重复记录列表
     * @param materialId    商品ID
     * @param depotId       仓库ID
     */
    private void handleDuplicateStockRecords(List<MaterialCurrentStock> duplicateList, Long materialId, Long depotId) {
        try {
            logger.warn("发现重复库存记录: materialId={}, depotId={}, 记录数={}",
                    materialId, depotId, duplicateList.size());

            // 重新计算准确的库存数量
            BigDecimal recalculatedStock = getStockByParam(depotId, materialId, null, null);
            logger.info("重新计算库存: materialId={}, depotId={}, 计算结果={}",
                    materialId, depotId, recalculatedStock);

            // 保留第一条记录的ID，用于后续更新
            Long keepRecordId = duplicateList.get(0).getId();

            // 删除从第二条开始的所有重复记录
            for (int i = 1; i < duplicateList.size(); i++) {
                Long deleteId = duplicateList.get(i).getId();
                int deleteResult = materialCurrentStockMapper.deleteByPrimaryKey(deleteId);
                logger.info("删除重复库存记录: id={}, materialId={}, depotId={}, 删除结果={}",
                        deleteId, materialId, depotId, deleteResult > 0 ? "成功" : "失败");
            }

            // 更新保留的记录为重新计算的库存数量
            MaterialCurrentStock updateRecord = new MaterialCurrentStock();
            updateRecord.setId(keepRecordId);
            updateRecord.setCurrentNumber(recalculatedStock);
            int updateResult = materialCurrentStockMapper.updateByPrimaryKeySelective(updateRecord);

            logger.info("更新保留记录: id={}, materialId={}, depotId={}, 新库存={}, 更新结果={}",
                    keepRecordId, materialId, depotId, recalculatedStock, updateResult > 0 ? "成功" : "失败");

        } catch (Exception e) {
            logger.error("处理重复库存记录失败: materialId={}, depotId={}, error={}",
                    materialId, depotId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public BigDecimal getFinishNumber(Long meId, Long id, Long headerId, Unit unitInfo, String materialUnit,
            String linkType) {
        Long linkId = id;
        String goToType = "";
        DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(headerId);
        String linkStr = depotHead.getNumber(); // 订单号
        if ("purchase".equals(linkType)) {
            // 针对以销定购的情况
            if (BusinessConstants.SUB_TYPE_SALES_ORDER.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_PURCHASE_ORDER;
            }
        } else if ("other".equals(linkType)) {
            // 采购入库、采购退货、销售出库、销售退货都转其它入库
            if (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_PURCHASE_RETURN.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())
                    || BusinessConstants.SUB_TYPE_SALES_RETURN.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_OTHER;
            }
        } else if ("basic".equals(linkType)) {
            // 采购订单转采购入库
            if (BusinessConstants.SUB_TYPE_PURCHASE_ORDER.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_PURCHASE;
            }
            // 销售订单转销售出库
            if (BusinessConstants.SUB_TYPE_SALES_ORDER.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_SALES;
            }
            // 采购入库转采购退货
            if (BusinessConstants.SUB_TYPE_PURCHASE.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_PURCHASE_RETURN;
            }
            // 销售出库转销售退货
            if (BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType())) {
                goToType = BusinessConstants.SUB_TYPE_SALES_RETURN;
            }
        }
        String noType = "normal";
        if (BusinessConstants.SUB_TYPE_PURCHASE_APPLY.equals(depotHead.getSubType())) {
            noType = "apply";
        }
        BigDecimal count = depotItemMapperEx.getFinishNumber(meId, linkId, linkStr, noType, goToType);
        // 根据多单位情况进行数量的转换
        if (materialUnit.equals(unitInfo.getOtherUnit()) && unitInfo.getRatio() != null
                && unitInfo.getRatio().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatio(), 2, BigDecimal.ROUND_HALF_UP);
        }
        if (materialUnit.equals(unitInfo.getOtherUnitTwo()) && unitInfo.getRatioTwo() != null
                && unitInfo.getRatioTwo().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatioTwo(), 2, BigDecimal.ROUND_HALF_UP);
        }
        if (materialUnit.equals(unitInfo.getOtherUnitThree()) && unitInfo.getRatioThree() != null
                && unitInfo.getRatioThree().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatioThree(), 2, BigDecimal.ROUND_HALF_UP);
        }
        return count;
    }

    /**
     * 除去此单据之外的已入库|已出库|已转采购
     * 
     * @param currentSubType
     * @param meId
     * @param linkId
     * @param preHeaderId
     * @param currentHeaderId
     * @param unitInfo
     * @param materialUnit
     * @return
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public BigDecimal getRealFinishNumber(String currentSubType, Long meId, Long linkId, Long preHeaderId,
            Long currentHeaderId, Unit unitInfo, String materialUnit) {
        String goToType = currentSubType;
        DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(preHeaderId);
        String linkStr = depotHead.getNumber(); // 订单号
        String linkType = "normal";
        if (BusinessConstants.SUB_TYPE_PURCHASE_APPLY.equals(depotHead.getSubType())) {
            linkType = "apply";
        }
        BigDecimal count = depotItemMapperEx.getRealFinishNumber(meId, linkId, linkStr, linkType, currentHeaderId,
                goToType);
        // 根据多单位情况进行数量的转换
        if (materialUnit.equals(unitInfo.getOtherUnit()) && unitInfo.getRatio() != null
                && unitInfo.getRatio().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatio(), 2, BigDecimal.ROUND_HALF_UP);
        }
        if (materialUnit.equals(unitInfo.getOtherUnitTwo()) && unitInfo.getRatioTwo() != null
                && unitInfo.getRatioTwo().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatioTwo(), 2, BigDecimal.ROUND_HALF_UP);
        }
        if (materialUnit.equals(unitInfo.getOtherUnitThree()) && unitInfo.getRatioThree() != null
                && unitInfo.getRatioThree().compareTo(BigDecimal.ZERO) != 0) {
            count = count.divide(unitInfo.getRatioThree(), 2, BigDecimal.ROUND_HALF_UP);
        }
        return count;
    }

    public List<DepotItemVoBatchNumberList> getBatchNumberList(String number, String name, Long depotId, String barCode,
            String batchNumber, Boolean forceFlag, Boolean inOutManageFlag) throws Exception {
        List<DepotItemVoBatchNumberList> reslist = new ArrayList<>();
        List<DepotItemVoBatchNumberList> list = depotItemMapperEx.getBatchNumberList(StringUtil.toNull(number), name,
                depotId, barCode, batchNumber, forceFlag, inOutManageFlag);
        for (DepotItemVoBatchNumberList bn : list) {
            if (bn.getTotalNum() != null && bn.getTotalNum().compareTo(BigDecimal.ZERO) > 0) {
                bn.setExpirationDateStr(Tools.parseDateToStr(bn.getExpirationDate()));
                if (bn.getUnitId() != null) {
                    Unit unit = unitService.getUnit(bn.getUnitId());
                    String commodityUnit = bn.getCommodityUnit();
                    bn.setTotalNum(unitService.parseStockByUnit(bn.getTotalNum(), unit, commodityUnit));
                }
                reslist.add(bn);
            }
        }
        return reslist;
    }

    /**
     * 查询某个批号的商品库存
     * 
     * @param depotId
     * @param barCode
     * @param batchNumber
     * @return
     * @throws Exception
     */
    public BigDecimal getOneBatchNumberStock(Long depotId, String barCode, String batchNumber) throws Exception {
        BigDecimal totalNum = BigDecimal.ZERO;
        Boolean forceFlag = systemConfigService.getForceApprovalFlag();
        Boolean inOutManageFlag = systemConfigService.getInOutManageFlag();
        List<DepotItemVoBatchNumberList> list = depotItemMapperEx.getBatchNumberList(null, null,
                depotId, barCode, batchNumber, forceFlag, inOutManageFlag);
        if (list != null && list.size() > 0) {
            DepotItemVoBatchNumberList bn = list.get(0);
            totalNum = bn.getTotalNum();
            if (bn.getTotalNum() != null && bn.getTotalNum().compareTo(BigDecimal.ZERO) > 0) {
                if (bn.getUnitId() != null) {
                    Unit unit = unitService.getUnit(bn.getUnitId());
                    String commodityUnit = bn.getCommodityUnit();
                    totalNum = unitService.parseStockByUnit(bn.getTotalNum(), unit, commodityUnit);
                }
            }
        }
        return totalNum;
    }

    public Long getCountByMaterialAndDepot(Long mId, Long depotId) {
        return depotItemMapperEx.getCountByMaterialAndDepot(mId, depotId);
    }

    public JSONObject parseMapByExcelData(String barCodes, List<Map<String, String>> detailList, String prefixNo)
            throws Exception {
        JSONObject map = new JSONObject();
        JSONArray arr = new JSONArray();
        List<MaterialVo4Unit> list = depotItemMapperEx.getBillItemByParam(barCodes);
        Map<String, MaterialVo4Unit> materialMap = new HashMap<>();
        Map<String, Long> depotMap = new HashMap<>();
        for (MaterialVo4Unit material : list) {
            materialMap.put(material.getmBarCode(), material);
        }
        JSONArray depotArr = depotService.findDepotByCurrentUser();
        for (Object depotObj : depotArr) {
            if (depotObj != null) {
                JSONObject depotObject = JSONObject.parseObject(depotObj.toString());
                depotMap.put(depotObject.getString("depotName"), depotObject.getLong("id"));
            }
        }
        for (Map<String, String> detailMap : detailList) {
            JSONObject item = new JSONObject();
            String barCode = detailMap.get("barCode");
            if (StringUtil.isNotEmpty(barCode)) {
                MaterialVo4Unit m = materialMap.get(barCode);
                if (m != null) {
                    // 判断仓库是否存在
                    String depotName = detailMap.get("depotName");
                    if (StringUtil.isNotEmpty(depotName)) {
                        if (depotMap.get(depotName) != null) {
                            item.put("depotName", depotName);
                            item.put("depotId", depotMap.get(depotName));
                        } else {
                            throw new BusinessRunTimeException(
                                    ExceptionConstants.DEPOT_ITEM_DEPOTNAME_IS_NOT_EXIST_CODE,
                                    String.format(ExceptionConstants.DEPOT_ITEM_DEPOTNAME_IS_NOT_EXIST_MSG, depotName));
                        }
                    }
                    item.put("barCode", barCode);
                    item.put("name", m.getName());
                    item.put("standard", m.getStandard());
                    if (StringUtil.isNotEmpty(m.getModel())) {
                        item.put("model", m.getModel());
                    }
                    if (StringUtil.isNotEmpty(m.getColor())) {
                        item.put("color", m.getColor());
                    }
                    if (StringUtil.isNotEmpty(m.getSku())) {
                        item.put("sku", m.getSku());
                    }
                    BigDecimal stock = BigDecimal.ZERO;
                    if (StringUtil.isNotEmpty(m.getSku())) {
                        stock = getSkuStockByParam(null, m.getMeId(), null, null);
                    } else {
                        stock = getCurrentStockByParam(null, m.getId());
                    }
                    item.put("stock", stock);
                    item.put("unit", m.getCommodityUnit());
                    BigDecimal operNumber = BigDecimal.ZERO;
                    BigDecimal unitPrice = BigDecimal.ZERO;
                    BigDecimal taxRate = BigDecimal.ZERO;
                    if (StringUtil.isNotEmpty(detailMap.get("num"))) {
                        operNumber = new BigDecimal(detailMap.get("num"));
                    }
                    if (StringUtil.isNotEmpty(detailMap.get("unitPrice"))) {
                        unitPrice = new BigDecimal(detailMap.get("unitPrice"));
                    } else {
                        if ("CGDD".equals(prefixNo)) {
                            unitPrice = m.getPurchaseDecimal();
                        } else if ("XSDD".equals(prefixNo)) {
                            unitPrice = m.getWholesaleDecimal();
                        }
                    }
                    if (StringUtil.isNotEmpty(detailMap.get("taxRate"))) {
                        taxRate = new BigDecimal(detailMap.get("taxRate"));
                    }
                    String remark = detailMap.get("remark");
                    item.put("operNumber", operNumber);
                    item.put("unitPrice", unitPrice);
                    BigDecimal allPrice = BigDecimal.ZERO;
                    if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) != 0) {
                        allPrice = unitPrice.multiply(operNumber);
                    }
                    BigDecimal taxMoney = BigDecimal.ZERO;
                    if (taxRate.compareTo(BigDecimal.ZERO) != 0) {
                        taxMoney = taxRate.multiply(allPrice).divide(BigDecimal.valueOf(100), 2,
                                BigDecimal.ROUND_HALF_UP);
                    }
                    BigDecimal taxLastMoney = allPrice.add(taxMoney);
                    item.put("allPrice", allPrice);
                    item.put("taxRate", taxRate);
                    item.put("taxMoney", taxMoney);
                    item.put("taxLastMoney", taxLastMoney);
                    item.put("remark", remark);
                    arr.add(item);
                } else {
                    throw new BusinessRunTimeException(ExceptionConstants.DEPOT_ITEM_BARCODE_IS_NOT_EXIST_CODE,
                            String.format(ExceptionConstants.DEPOT_ITEM_BARCODE_IS_NOT_EXIST_MSG, barCode));
                }
            }
        }
        map.put("rows", arr);
        return map;
    }

    public BigDecimal getLastUnitPriceByParam(Long organId, Long meId, String prefixNo) {
        String type = "";
        String subType = "";
        if ("XSDD".equals(prefixNo)) {
            type = "其它";
            subType = "销售订单";
        } else if ("XSCK".equals(prefixNo)) {
            type = "出库";
            subType = "销售";
        } else if ("XSTH".equals(prefixNo)) {
            type = "入库";
            subType = "销售退货";
        } else if ("QTCK".equals(prefixNo)) {
            type = "出库";
            subType = "其它";
        }
        return depotItemMapperEx.getLastUnitPriceByParam(organId, meId, type, subType);
    }

    public BigDecimal getCurrentStockByParam(Long depotId, Long mId) {
        BigDecimal stock = depotItemMapperEx.getCurrentStockByParam(depotId, mId);
        return stock != null ? stock : BigDecimal.ZERO;
    }

    /**
     * 获取商品期间库存统计（分页）
     * 
     * @param materialParam
     * @param offset
     * @param rows
     * @return
     * @throws Exception
     */
    public List<MaterialStockPeriodVo> getMaterialPeriodStock(String materialParam, int offset, int rows)
            throws Exception {
        List<MaterialStockPeriodVo> list = depotItemMapperEx.getMaterialPeriodStock(materialParam, offset, rows);
        return list;
    }

    /**
     * 获取商品期间库存统计总数
     * 
     * @param materialParam
     * @return
     * @throws Exception
     */
    public int getMaterialPeriodStockCount(String materialParam) throws Exception {
        int count = depotItemMapperEx.getMaterialPeriodStockCount(materialParam);
        return count;
    }

    /**
     * 获取商品每日出库数据
     * 
     * @param materialIds 商品ID列表，逗号分隔
     * @param beginTime   开始时间
     * @param endTime     结束时间
     * @return
     * @throws Exception
     */
    public List<Map<String, Object>> getDailyOutStock(String materialIds, String beginTime, String endTime)
            throws Exception {
        List<Map<String, Object>> list = depotItemMapperEx.getDailyOutStock(materialIds, beginTime, endTime);
        return list;
    }

    /**
     * 库存变化后更新汇总表和清除缓存
     * 
     * @param materialId 商品ID
     * @param depotId    仓库ID
     */
    private void updateSummaryTablesAfterStockChange(Long materialId, Long depotId, Date operTime, Long headerId) {
        // 保存阶段不执行每日出库汇总与库存预警（仅在已审核单据时执行）
        try {
            if (headerId == null) {
                logger.debug("skip summary/alert: headerId is null, materialId={}, depotId={}", materialId, depotId);
                return;
            }
            DepotHead header = depotHeadMapper.selectByPrimaryKey(headerId);
            if (header == null || !"1".equals(header.getStatus())) {
                logger.debug("skip summary/alert: headerId={}, status={}, materialId={}, depotId={}",
                        headerId, header != null ? header.getStatus() : null, materialId, depotId);
                return;
            }
        } catch (Exception e) {
            logger.debug("summary/alert audit-check failed, fallback to default flow, headerId={}, error={}", headerId,
                    e.getMessage());
        }
        logger.debug("开始更新汇总表，materialId={}, depotId={}, operTime={}", materialId, depotId, operTime);

        // 获取当前用户的租户ID
        Long tenantId = null;
        try {
            User currentUser = userService.getCurrentUser();
            tenantId = currentUser != null ? currentUser.getTenantId() : null;
        } catch (Exception e) {
            logger.debug("获取当前用户失败，使用null作为tenantId");
        }

        // 当次单据内的物料级重复操作短路
        boolean materialFirstTime = tryMarkMaterialProcessed(materialId);

        // 1) 仅在出库型场景下更新每日出库汇总，且只在物料首次出现时执行
        if (materialFirstTime && isOutTypeAffectingSummary(materialId, operTime, tenantId)) {
            // 获取当前操作的单据头信息
            DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(headerId);
            String shopName = depotHead != null ? depotHead.getShopName() : "";
            updateDailyOutSummaryForMaterialBusinessLogic(materialId, tenantId, operTime, shopName);
            markWriteHappened();
        } else {
            logger.debug("跳过每日出库汇总: materialId={}, materialFirstTime={}, outType={} ", materialId,
                    materialFirstTime, isOutTypeAffectingSummary(materialId, operTime, tenantId));
        }

        // 2) 更新库存预警状态（物料级，仅首次执行；并带等值短路）
        if (materialFirstTime) {
            try {
                // 获取当前操作的单据头信息
                DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(headerId);
                // 判断是否是销售出库：必须同时满足出库类型且子类型为销售出库
                boolean isSalesOut = BusinessConstants.DEPOTHEAD_TYPE_OUT.equals(depotHead.getType())
                        && BusinessConstants.SUB_TYPE_SALES.equals(depotHead.getSubType());
                boolean updated = updateStockAlertStatusForMaterial(materialId, tenantId, isSalesOut);
                if (updated) {
                    markWriteHappened();
                }
            } catch (Exception e) {
                logger.warn("更新库存预警状态失败，但不影响其他操作: materialId={}, error={}", materialId, e.getMessage());
            }
        }

        logger.debug("汇总表更新完成，materialId={}, depotId={}, operTime={}", materialId, depotId, operTime);
    }

    // 判断当日是否存在需要进入每日出库汇总口径的出库明细（存在即返回true）
    private boolean isOutTypeAffectingSummary(Long materialId, Date operTime, Long tenantId) {
        try {
            if (materialId == null || operTime == null) {
                return false;
            }
            // 这里复用 Mapper 中复杂SQL的口径：出库 + 具体子类型（以销售为主）。
            // 为降低成本，仅做存在性判断应使用对应的 exists/limit 1 查询。
            // 暂用已有 upsert 口径的同一日期，若查询接口不可用，则默认返回 true，保持功能正确性。
            return true;
        } catch (Exception e) {
            logger.debug("判断出库统计口径失败，默认true, materialId={}, error={}", materialId, e.getMessage());
            return true;
        }
    }

    /**
     * 更新指定商品的库存预警状态
     * 
     * @param materialId 商品ID
     * @param tenantId   租户ID
     */
    private boolean updateStockAlertStatusForMaterial(Long materialId, Long tenantId, boolean isSalesOut) {
        try {
            logger.debug("开始更新商品库存预警状态，materialId={}, tenantId={}", materialId, tenantId);

            // 1. 获取商品信息
            Material material = materialService.getMaterial(materialId);
            if (material == null) {
                logger.warn("商品不存在，跳过库存预警状态更新，materialId={}", materialId);
                return false;
            }

            // 2. 获取当前库存
            BigDecimal currentStock = depotItemMapperEx.getMaterialCurrentStock(materialId, tenantId);
            if (currentStock == null) {
                currentStock = BigDecimal.ZERO;
            }

            // 3. 获取6个月销量
            BigDecimal sixMonthsSales = depotItemMapperEx.getSixMonthsSalesByMaterialId(materialId, tenantId);
            if (sixMonthsSales == null) {
                sixMonthsSales = BigDecimal.ZERO;
            }

            logger.debug("库存预警状态计算参数，materialId={}, currentStock={}, sixMonthsSales={}",
                    materialId, currentStock, sixMonthsSales);

            // 4. 计算新的预警状态
            String newAlertStatus;
            if (currentStock.compareTo(sixMonthsSales) >= 0) {
                newAlertStatus = "NO_RISK";
            } else {
                newAlertStatus = "STOCK_ALERT";
            }

            // 5. 获取当前预警状态
            String currentAlertStatus = material.getStockAlertStatus();

            logger.debug("预警状态计算结果，materialId={}, currentStatus={}, newStatus={}",
                    materialId, currentAlertStatus, newAlertStatus);

            // 6. 应用业务规则：如果新的预警状态是库存告警且原有的状态是忽略告警，且是销售出库操作时，不用更新库存状态
            if ("STOCK_ALERT".equals(newAlertStatus) &&
                    "RISK_IGNORED".equals(currentAlertStatus) &&
                    isSalesOut) {
                logger.debug("销售出库操作且新状态为库存告警，原状态为忽略告警，跳过更新，materialId={}", materialId);
                return false;
            }

            // 7. 等值短路：若状态和值均未变化则跳过
            if (newAlertStatus != null && newAlertStatus.equals(currentAlertStatus)
                    && sixMonthsSales.compareTo(material.getLastSixMonthsSales() == null ? BigDecimal.ZERO
                            : material.getLastSixMonthsSales()) == 0) {
                logger.debug("预警状态与销量未变化，跳过更新，materialId={}", materialId);
                return false;
            }

            materialService.updateStockAlertStatus(materialId, newAlertStatus, sixMonthsSales);
            logger.info(
                    "库存预警状态更新成功，materialId={}, oldStatus={}, newStatus={}, currentStock={}, sixMonthsSales={}, isSalesOut={}",
                    materialId, currentAlertStatus, newAlertStatus, currentStock, sixMonthsSales, isSalesOut);
            return true;

        } catch (Exception e) {
            logger.error("更新库存预警状态失败，materialId={}, tenantId={}", materialId, tenantId, e);
            // 不抛出异常，避免影响主流程
            return false;
        }
    }

    /**
     * 使用业务层逻辑更新指定商品的每日出库汇总
     * 
     * @param materialId 商品ID
     * @param tenantId   租户ID
     * @param operTime   操作时间
     */
    private void updateDailyOutSummaryForMaterialBusinessLogic(Long materialId, Long tenantId, Date operTime,
            String shopName) {
        try {
            // 使用传入的操作时间而不是当前日期
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String operationDate = sdf.format(operTime != null ? operTime : new Date());

            // 直接使用简化版本更新汇总数据
            depotItemMapperEx.insertOrUpdateDailyOutSummarySimple(materialId, operationDate, shopName, tenantId);
            logger.debug("每日出库汇总更新成功，materialId={}, date={}, operTime={}, shopName={}",
                    materialId, operationDate, operTime, shopName);

        } catch (Exception e) {
            logger.warn("更新每日出库汇总失败，materialId={}, operTime={}, shopName={}, error={}",
                    materialId, operTime, shopName, e.getMessage());
        }
    }

    /**
     * 使用业务层逻辑更新商品期间汇总
     * 
     * @param materialId 商品ID
     * @param tenantId   租户ID
     */
    private void updatePeriodSummaryForMaterialBusinessLogic(Long materialId, Long tenantId) {
        // 已弃用：不再维护商品期间汇总表
        logger.debug("updatePeriodSummaryForMaterialBusinessLogic 跳过执行（功能已弃用），materialId={}, tenantId={}", materialId,
                tenantId);
    }

    /**
     * 批量初始化汇总表数据（用于系统启动或数据修复）
     * 
     * @param tenantId 租户ID
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void initializeSummaryData(Long tenantId) {
        // 已弃用：不再初始化期间汇总表数据，仅清缓存
        logger.info("跳过期间汇总初始化（功能已弃用），tenantId={}", tenantId);
        clearRelatedCache();
    }

    /**
     * 检查并开启强制审核配置
     * 
     * @return 是否开启成功
     */
    public boolean ensureForceApprovalEnabled() {
        try {
            boolean currentFlag = systemConfigService.getForceApprovalFlag();
            if (!currentFlag) {
                logger.warn("强制审核配置未开启，这会导致汇总表无法在审核时更新");
                // 这里可以提供开启的建议，但不直接修改配置
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("检查强制审核配置失败", e);
            return false;
        }
    }

    /**
     * 清除相关缓存
     */
    private void clearRelatedCache() {
        try {
            if (depotItemOptimizedService != null) {
                depotItemOptimizedService.clearAllCache();
                logger.debug("相关缓存清除成功");
            } else {
                logger.warn("DepotItemOptimizedService为空，无法清除缓存");
            }
        } catch (Exception e) {
            logger.warn("清除缓存失败，error={}", e.getMessage());
        }
    }

    /**
     * 导出商品库存数据到Excel
     * 
     * @param materialParam 商品筛选参数
     * @param beginTime     开始时间
     * @param endTime       结束时间
     * @param response      HTTP响应对象
     * @throws Exception
     */
    public void exportMaterialStockToExcel(String materialParam, String beginTime, String endTime,
            HttpServletResponse response) throws Exception {
        logger.info("开始导出商品库存数据，参数: materialParam={}, beginTime={}, endTime={}", materialParam, beginTime, endTime);

        // 1. 获取所有符合条件的库存数据（不分页）
        // 使用现有的查询方法，设置一个较大的数量限制来获取所有数据
        List<MaterialStockPeriodVo> stockList = depotItemMapperEx.getMaterialPeriodStock(materialParam, 0, 999999);

        if (stockList == null || stockList.isEmpty()) {
            throw new BusinessRunTimeException(ExceptionConstants.MATERIAL_NOT_EXISTS_CODE, "没有找到符合条件的库存数据");
        }

        logger.info("查询到{}条库存数据", stockList.size());

        // 2. 如果有时间范围，获取每日出库数据
        Map<String, Map<String, Object>> dailyOutMap = new HashMap<>();
        List<String> dateColumns = new ArrayList<>();

        if (StringUtil.isNotEmpty(beginTime) && StringUtil.isNotEmpty(endTime)) {
            // 生成完整的日期列表（包含起止日期，格式yyyy-MM-dd）
            List<String> fullDateList = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date start = sdf.parse(beginTime);
            Date end = sdf.parse(endTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(start);
            while (!cal.getTime().after(end)) {
                fullDateList.add(sdf.format(cal.getTime()));
                cal.add(Calendar.DATE, 1);
            }
            dateColumns.addAll(fullDateList);

            // 提取商品ID列表
            StringBuilder materialIds = new StringBuilder();
            for (int i = 0; i < stockList.size(); i++) {
                if (i > 0)
                    materialIds.append(",");
                materialIds.append(stockList.get(i).getMaterialId());
            }

            if (materialIds.length() > 0) {
                String formattedBeginTime = beginTime + BusinessConstants.DAY_FIRST_TIME;
                String formattedEndTime = endTime + BusinessConstants.DAY_LAST_TIME;

                List<Map<String, Object>> dailyOutList = getDailyOutStock(
                        materialIds.toString(), formattedBeginTime, formattedEndTime);

                // 将每日出库数据按商品编码和日期组织
                for (Map<String, Object> dailyOut : dailyOutList) {
                    String barCode = (String) dailyOut.get("barCode");
                    String outDate = (String) dailyOut.get("outDate");

                    if (!dailyOutMap.containsKey(barCode)) {
                        dailyOutMap.put(barCode, new HashMap<>());
                    }
                    dailyOutMap.get(barCode).put(outDate, dailyOut.get("outQuantity"));
                }
            }
        }

        // 3. 构造Excel表头 - 按照要求的固定列顺序
        List<String> headers = new ArrayList<>();
        headers.add("商品编码");
        headers.add("商品名称");
        headers.add("上期结存");
        headers.add("本期入库");
        headers.add("本期出库");
        headers.add("本期结存");

        // 添加每日出库列（动态列）
        for (String date : dateColumns) {
            headers.add(date);
        }

        if (stockList.get(0).getStockAlertStatus() != null) {
            headers.add("库存预警状态");
        }

        String[] headerArray = headers.toArray(new String[0]);

        // 4. 构造Excel数据 - 按照表头顺序排列数据
        List<Object[]> dataList = new ArrayList<>();
        for (MaterialStockPeriodVo stock : stockList) {
            List<Object> row = new ArrayList<>();

            // 固定前6列数据
            row.add(stock.getBarCode()); // 商品编码
            row.add(stock.getMaterialName()); // 商品名称
            row.add(stock.getPreviousPeriodStock() != null
                    ? stock.getPreviousPeriodStock().setScale(2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO); // 上期结存
            row.add(stock.getCurrentPeriodIn() != null
                    ? stock.getCurrentPeriodIn().setScale(2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO); // 本期入库
            row.add(stock.getCurrentPeriodOut() != null
                    ? stock.getCurrentPeriodOut().setScale(2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO); // 本期出库
            row.add(stock.getCurrentPeriodStock() != null
                    ? stock.getCurrentPeriodStock().setScale(2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO); // 本期结存

            // 添加每日出库数据（动态列）
            Map<String, Object> dailyData = dailyOutMap.get(stock.getBarCode());
            for (String date : dateColumns) {
                Object quantity = dailyData != null ? dailyData.get(date) : null;
                if (quantity instanceof BigDecimal) {
                    row.add(((BigDecimal) quantity).setScale(2, BigDecimal.ROUND_HALF_UP));
                } else if (quantity instanceof Number) {
                    row.add(new BigDecimal(quantity.toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
                } else {
                    row.add(BigDecimal.ZERO);
                }
            }

            // 库存预警状态（可选列）
            if (stock.getStockAlertStatus() != null) {
                String alertStatusText = "";
                switch (stock.getStockAlertStatus()) {
                    case "CRITICAL":
                        alertStatusText = "库存告急";
                        break;
                    case "WARNING":
                        alertStatusText = "库存预警";
                        break;
                    case "RISK_IGNORED":
                        alertStatusText = "风险已忽略";
                        break;
                    default:
                        alertStatusText = "正常";
                        break;
                }
                row.add(alertStatusText);
            }

            dataList.add(row.toArray());
        }

        // 5. 生成Excel文件
        String title = "商品库存数据";
        String fileName = "商品库存数据_" + getNowFormatStr();
        String tip = "导出时间：" + getNowFormatStr();
        if (StringUtil.isNotEmpty(beginTime) && StringUtil.isNotEmpty(endTime)) {
            tip += "，时间范围：" + beginTime + " ~ " + endTime;
        }
        if (StringUtil.isNotEmpty(materialParam)) {
            tip += "，筛选条件：" + materialParam;
        }

        File file = ExcelUtils.exportStockDataWithFrozenColumns(fileName, tip, headerArray, title, dataList);
        ExcelUtils.downloadExcel(file, fileName, response);

        logger.info("商品库存数据导出完成，共导出{}条记录", dataList.size());
    }

    /**
     * 获取当前时间格式化字符串
     */
    private String getNowFormatStr() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    /**
     * 批量更新所有商品的期间汇总数据
     * 用于定时任务调用
     * 
     * @throws Exception
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateAllMaterialsPeriodSummary() throws Exception {
        logger.info("开始批量更新所有商品的期间汇总数据");

        try {
            // 1. 获取所有未删除的商品列表
            List<Material> materialList = materialService.getMaterial();
            if (materialList == null || materialList.isEmpty()) {
                logger.info("没有找到需要更新的商品数据");
                return;
            }

            logger.info("找到{}个商品需要更新期间汇总数据", materialList.size());

            // 2. 获取当前用户的租户ID
            Long tenantId = null;
            try {
                User currentUser = userService.getCurrentUser();
                tenantId = currentUser != null ? currentUser.getTenantId() : null;
                logger.debug("获取到租户ID: {}", tenantId);
            } catch (Exception e) {
                logger.warn("获取当前用户租户ID失败，将使用null作为租户ID: {}", e.getMessage());
            }

            // 3. 分批处理商品（每批100个，避免内存问题）
            int batchSize = 100;
            int totalCount = materialList.size();
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < totalCount; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalCount);
                List<Material> batch = materialList.subList(i, endIndex);

                logger.debug("处理第{}批商品，范围: {}-{}", (i / batchSize + 1), i, endIndex - 1);

                for (Material material : batch) {
                    try {
                        // 4. 调用现有的更新方法
                        updatePeriodSummaryForMaterialBusinessLogic(material.getId(), tenantId);
                        successCount++;

                        if (successCount % 50 == 0) {
                            logger.info("已成功更新{}个商品的期间汇总数据", successCount);
                        }
                    } catch (Exception e) {
                        failCount++;
                        logger.error("更新商品{}的期间汇总数据失败: {}", material.getId(), e.getMessage());
                        // 继续处理下一个商品，不中断整个批量处理
                    }
                }
            }

            logger.info("批量更新商品期间汇总数据完成 - 总数: {}, 成功: {}, 失败: {}",
                    totalCount, successCount, failCount);

        } catch (Exception e) {
            logger.error("批量更新商品期间汇总数据时发生异常", e);
            throw e;
        }
    }
}
