package com.jsh.erp.service;

import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.datasource.entities.MaterialExtend;
import com.jsh.erp.datasource.entities.MaterialPriceHistory;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.MaterialExtendMapper;
import com.jsh.erp.datasource.mappers.MaterialPriceHistoryMapper;
import com.jsh.erp.datasource.mappers.MaterialPriceHistoryMapperEx;
import com.jsh.erp.exception.JshException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class MaterialPriceHistoryService {
    private Logger logger = LoggerFactory.getLogger(MaterialPriceHistoryService.class);

    @Resource
    private MaterialPriceHistoryMapper materialPriceHistoryMapper;

    @Resource
    private MaterialPriceHistoryMapperEx materialPriceHistoryMapperEx;

    @Resource
    private MaterialExtendMapper materialExtendMapper;

    @Resource
    private UserService userService;

    /**
     * 记录价格变更历史
     * 
     * @param materialExtendId 商品扩展ID
     * @param oldPrice         旧价格
     * @param newPrice         新价格
     * @param changeSource     变更来源
     * @param sourceBillId     源单据ID（可选）
     * @param sourceBillNumber 源单据编号（可选）
     * @param changeReason     变更原因（可选）
     * @param effectiveDate    生效日期（可选，如果为null则使用当前日期）
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void recordPriceChange(Long materialExtendId, BigDecimal oldPrice, BigDecimal newPrice,
            String changeSource, Long sourceBillId, String sourceBillNumber,
            String changeReason, Date effectiveDate) {
        try {
            // 获取商品扩展信息
            MaterialExtend materialExtend = materialExtendMapper.selectByPrimaryKey(materialExtendId);
            if (materialExtend == null) {
                logger.warn("商品扩展ID不存在: {}", materialExtendId);
                return;
            }

            // 获取当前用户
            User user = userService.getCurrentUser();
            String createSerial = user != null ? user.getLoginName() : "system";

            // 构建价格历史记录
            MaterialPriceHistory history = new MaterialPriceHistory();
            history.setMaterialId(materialExtend.getMaterialId());
            history.setMaterialExtendId(materialExtendId);
            history.setBarCode(materialExtend.getBarCode());
            history.setRetailPrice(newPrice);
            // 生效日期：如果提供了则使用提供的日期，否则使用当前日期
            Date finalEffectiveDate = effectiveDate != null ? effectiveDate : new Date();
            history.setEffectiveDate(finalEffectiveDate);
            history.setChangeReason(changeReason);
            history.setChangeSource(changeSource);
            history.setSourceBillId(sourceBillId);
            history.setSourceBillNumber(sourceBillNumber);
            history.setCreateTime(new Date());
            history.setCreateSerial(createSerial);
            // 注意：tenantId由MyBatis-Plus多租户插件自动注入，不需要手动设置
            history.setDeleteFlag(BusinessConstants.DELETE_FLAG_NORMAL);

            // 插入记录
            materialPriceHistoryMapper.insertSelective(history);

            logger.info("✅ 记录价格变更历史成功 - 商品扩展ID: {}, 条码: {}, 旧价格: {}, 新价格: {}, 变更来源: {}, 生效日期: {}, 源单据: {}",
                    materialExtendId, materialExtend.getBarCode(), oldPrice, newPrice, changeSource, finalEffectiveDate,
                    sourceBillNumber);
        } catch (Exception e) {
            logger.error("记录价格变更历史失败 - 商品扩展ID: {}, 错误: {}", materialExtendId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 根据商品扩展ID查询价格历史
     * 
     * @param materialExtendId 商品扩展ID
     * @return 价格历史列表
     */
    public List<MaterialPriceHistory> getByMaterialExtendId(Long materialExtendId) {
        List<MaterialPriceHistory> list = null;
        try {
            list = materialPriceHistoryMapperEx.getByMaterialExtendId(materialExtendId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    /**
     * 根据商品ID查询价格历史
     * 
     * @param materialId 商品ID
     * @return 价格历史列表
     */
    public List<MaterialPriceHistory> getByMaterialId(Long materialId) {
        List<MaterialPriceHistory> list = null;
        try {
            list = materialPriceHistoryMapperEx.getByMaterialId(materialId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    /**
     * 根据时间范围查询价格变更记录
     * 
     * @param beginDate 开始日期
     * @param endDate   结束日期
     * @return 价格历史列表
     */
    public List<MaterialPriceHistory> getByDateRange(Date beginDate, Date endDate) {
        List<MaterialPriceHistory> list = null;
        try {
            list = materialPriceHistoryMapperEx.getByDateRange(beginDate, endDate);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return list;
    }

    /**
     * 查询最近的价格记录
     * 
     * @param materialExtendId 商品扩展ID
     * @return 最近的价格记录
     */
    public MaterialPriceHistory getLatestByMaterialExtendId(Long materialExtendId) {
        MaterialPriceHistory result = null;
        try {
            result = materialPriceHistoryMapperEx.getLatestByMaterialExtendId(materialExtendId);
        } catch (Exception e) {
            JshException.readFail(logger, e);
        }
        return result;
    }
}
