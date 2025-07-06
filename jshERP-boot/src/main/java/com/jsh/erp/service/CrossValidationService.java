package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.DepotHeadMapper;
import com.jsh.erp.datasource.vo.BillMaterialSummary;
import com.jsh.erp.datasource.vo.CrossValidationCheckResult;
import com.jsh.erp.datasource.vo.CrossValidationRequest;
import com.jsh.erp.datasource.vo.CrossValidationResult;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
import com.jsh.erp.datasource.vo.ValidationDifference;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.exception.JshException;
import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * 交叉校验服务
 * 用于处理销售出库单据的用户间交叉校验
 */
@Service
public class CrossValidationService {
    private Logger logger = LoggerFactory.getLogger(CrossValidationService.class);

    @Resource
    private DepotHeadMapper depotHeadMapper;

    @Resource
    private UserService userService;

    /**
     * 检查指定日期其他用户的单据情况
     * 
     * @param validationDate 校验日期
     * @return 检查结果
     */
    public CrossValidationCheckResult checkTodayUsers(String validationDate) {
        logger.info("开始执行checkTodayUsers方法，校验日期: {}", validationDate);

        CrossValidationCheckResult result = new CrossValidationCheckResult();
        result.setCurrentUserIds(new ArrayList<>());

        try {
            // 参数校验
            if (StringUtil.isEmpty(validationDate)) {
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_DATE_FORMAT_ERROR_CODE,
                        ExceptionConstants.CROSS_VALIDATION_DATE_FORMAT_ERROR_MSG);
            }
            
            // 验证日期格式
            if (!isValidDateFormat(validationDate)) {
                logger.debug("日期格式错误: {}", validationDate);
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_DATE_FORMAT_ERROR_CODE,
                        "日期格式错误，请使用YYYY-MM-DD格式");
            }

            // 获取当前用户和租户信息
            User currentUser = userService.getCurrentUser();
            Long currentUserId = currentUser.getId();
            Long tenantId = currentUser.getTenantId();
            
            logger.info("当前用户信息: userId={}, tenantId={}", currentUserId, tenantId);
            
            // 检查租户ID是否为空
            if (tenantId == null) {
                logger.warn("当前用户租户ID为空，无法进行交叉校验");
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_QUERY_FAILED_CODE,
                        "当前用户租户信息异常，无法进行交叉校验");
            }

            // 查询指定日期其他用户的单据汇总情况
            List<TodayUserBillSummary> otherUsers = getTodayUserBillSummaryByDate(validationDate, tenantId, currentUserId);

            // 获取单据总数
            int totalBills = countBillsByDateAndUsers(validationDate, tenantId, otherUsers);

            result.setOtherUsers(otherUsers);
            result.setHasOtherUsers(otherUsers.size() > 0);
            result.setTotalBills(totalBills);

            logger.info("checkTodayUsers方法执行完成，校验日期: {}, 返回结果: hasOtherUsers={}, otherUsers.size()={}, totalBills={}",
                    validationDate, result.isHasOtherUsers(), otherUsers.size(), totalBills);

        } catch (BusinessRunTimeException e) {
            // 如果是业务异常，直接重新抛出，保留原始错误信息
            logger.debug("checkTodayUsers方法执行异常，校验日期: {}, 异常信息: {}", validationDate, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.debug("checkTodayUsers方法执行异常，校验日期: {}, 异常信息: {}", validationDate, e.getMessage(), e);
            throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_QUERY_FAILED_CODE,
                    ExceptionConstants.CROSS_VALIDATION_QUERY_FAILED_MSG + ": " + e.getMessage());
        }

        return result;
    }

    /**
     * 执行交叉校验
     * 
     * @param request 校验请求
     * @return 校验结果
     */
    public CrossValidationResult performCrossValidation(CrossValidationRequest request) {
        logger.info("开始执行performCrossValidation方法，参数: {}", request);
        logger.info("校验日期: {}", request.getValidationDate());

        CrossValidationResult result = new CrossValidationResult();

        try {
            // 参数校验
            if (request == null || StringUtil.isEmpty(request.getValidationDate()) || 
                request.getSelectedUserIds() == null || request.getSelectedUserIds().isEmpty()) {
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_USER_PARAM_ERROR_CODE,
                        ExceptionConstants.CROSS_VALIDATION_USER_PARAM_ERROR_MSG);
            }
            
            // 验证日期格式
            if (!isValidDateFormat(request.getValidationDate())) {
                logger.debug("日期格式错误: {}", request.getValidationDate());
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_DATE_FORMAT_ERROR_CODE,
                        "日期格式错误，请使用YYYY-MM-DD格式");
            }

            // 获取当前用户和租户信息
            User currentUser = userService.getCurrentUser();
            Long tenantId = currentUser.getTenantId();
            
            logger.info("执行交叉校验 - 当前用户信息: userId={}, tenantId={}", currentUser.getId(), tenantId);
            
            // 检查租户ID是否为空
            if (tenantId == null) {
                logger.warn("当前用户租户ID为空，无法进行交叉校验");
                throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_EXECUTE_FAILED_CODE,
                        "当前用户租户信息异常，无法进行交叉校验");
            }

            // 获取指定日期和用户的商品唛头汇总数据
            List<BillMaterialSummary> materialSummaries = depotHeadMapper.getBillMaterialSummaryByDateAndUsers(
                    request.getValidationDate(), tenantId, request.getSelectedUserIds());

            if (materialSummaries == null || materialSummaries.isEmpty()) {
                // 无数据情况
                result.setConsistent(true);
                result.setDifferences(new ArrayList<>());
                result.setTotalBills(0);
                logger.info("指定日期无相关单据数据，校验日期: {}", request.getValidationDate());
                return result;
            }

            // 执行数量一致性校验
            List<ValidationDifference> differences = validateQuantityConsistency(materialSummaries);

            // 统计单据总数
            int totalBills = countBillsByDateAndUsers(request.getValidationDate(), tenantId, 
                    materialSummaries.stream().map(summary -> {
                        TodayUserBillSummary userSummary = new TodayUserBillSummary();
                        userSummary.setUserId(summary.getUserId());
                        userSummary.setBillCount(1); // 简化统计
                        return userSummary;
                    }).collect(Collectors.toList()));

            result.setConsistent(differences.isEmpty());
            result.setDifferences(differences);
            result.setTotalBills(totalBills);

            logger.info("performCrossValidation方法执行完成，校验日期: {}, 返回结果: consistent={}, differences.size()={}, totalBills={}",
                    request.getValidationDate(), result.isConsistent(), differences.size(), totalBills);

        } catch (BusinessRunTimeException e) {
            // 如果是业务异常，直接重新抛出，保留原始错误信息
            logger.debug("performCrossValidation方法执行异常，校验日期: {}, 异常信息: {}", request.getValidationDate(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.debug("performCrossValidation方法执行异常，校验日期: {}, 异常信息: {}", request.getValidationDate(), e.getMessage(), e);
            throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_EXECUTE_FAILED_CODE,
                    ExceptionConstants.CROSS_VALIDATION_EXECUTE_FAILED_MSG + ": " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取指定日期用户的单据汇总信息
     * 
     * @param validationDate 校验日期
     * @param tenantId       租户ID
     * @param currentUserId  当前用户ID
     * @return 用户单据汇总列表
     */
    private List<TodayUserBillSummary> getTodayUserBillSummaryByDate(String validationDate, Long tenantId, Long currentUserId) {
        try {
            logger.info("查询指定日期用户单据汇总，参数: validationDate={}, tenantId={}, currentUserId={}", 
                    validationDate, tenantId, currentUserId);
            
            // 先进行简单的测试：检查当前用户是否有效
            if (currentUserId == null) {
                throw new RuntimeException("当前用户ID为空");
            }
            
            // 调用指定日期的用户单据汇总查询方法
            List<TodayUserBillSummary> result;
            try {
                logger.info("准备调用 depotHeadMapper.getUserBillSummaryByDate 方法");
                result = depotHeadMapper.getUserBillSummaryByDate(validationDate, tenantId, currentUserId);
                logger.info("depotHeadMapper.getUserBillSummaryByDate 方法调用成功");
            } catch (Exception e) {
                logger.debug("调用 depotHeadMapper.getUserBillSummaryByDate 失败: {}", e.getMessage(), e);
                throw new RuntimeException("数据库查询失败: " + e.getMessage(), e);
            }
            
            logger.info("查询指定日期用户单据汇总完成，返回结果数量: {}", result == null ? 0 : result.size());
            if (result != null && !result.isEmpty()) {
                for (TodayUserBillSummary summary : result) {
                    logger.info("用户单据汇总详情: userId={}, userName={}, billCount={}", 
                            summary.getUserId(), summary.getUserName(), summary.getBillCount());
                }
            }
            
            return result;
        } catch (Exception e) {
                        logger.debug("获取指定日期用户单据汇总失败，日期: {}, 租户ID: {}, 当前用户ID: {}, 异常信息: {}",
                    validationDate, tenantId, currentUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 统计指定日期和用户的单据总数
     * 
     * @param validationDate 校验日期
     * @param tenantId       租户ID
     * @param otherUsers     其他用户列表
     * @return 单据总数
     */
    private int countBillsByDateAndUsers(String validationDate, Long tenantId, List<TodayUserBillSummary> otherUsers) {
        if (otherUsers == null || otherUsers.isEmpty()) {
            return 0;
        }

        try {
            return otherUsers.stream()
                    .mapToInt(TodayUserBillSummary::getBillCount)
                    .sum();
        } catch (Exception e) {
            logger.debug("统计指定日期单据总数失败，日期: {}, 租户ID: {}", validationDate, tenantId);
            throw e;
        }
    }

    /**
     * 校验商品唛头的数量一致性
     * 
     * @param materialSummaries 商品唛头汇总数据
     * @return 校验差异列表
     */
    private List<ValidationDifference> validateQuantityConsistency(List<BillMaterialSummary> materialSummaries) {
        List<ValidationDifference> differences = new ArrayList<>();

        try {
            // 按商品唛头分组统计各用户的出库数量
            Map<String, Map<Long, BigDecimal>> barCodeUserQuantityMap = new HashMap<>();
            Map<String, String> barCodeMaterialNameMap = new HashMap<>();

            // 数据聚合
            for (BillMaterialSummary summary : materialSummaries) {
                String barCode = summary.getMaterialBarCode();
                Long userId = summary.getUserId();
                BigDecimal quantity = summary.getTotalOutNumber();

                // 存储商品名称映射
                barCodeMaterialNameMap.put(barCode, summary.getMaterialName());

                // 按唛头分组统计
                barCodeUserQuantityMap.computeIfAbsent(barCode, k -> new HashMap<>())
                        .put(userId, quantity);
            }

            // 检查每个唛头的数量一致性
            for (Map.Entry<String, Map<Long, BigDecimal>> entry : barCodeUserQuantityMap.entrySet()) {
                String barCode = entry.getKey();
                Map<Long, BigDecimal> userQuantityMap = entry.getValue();

                // 如果只有一个用户，跳过检查
                if (userQuantityMap.size() <= 1) {
                    continue;
                }

                // 检查所有用户的数量是否一致
                BigDecimal firstQuantity = null;
                boolean isConsistent = true;

                for (BigDecimal quantity : userQuantityMap.values()) {
                    if (firstQuantity == null) {
                        firstQuantity = quantity;
                    } else if (firstQuantity.compareTo(quantity) != 0) {
                        isConsistent = false;
                        break;
                    }
                }

                // 如果不一致，记录差异
                if (!isConsistent) {
                    String materialName = barCodeMaterialNameMap.get(barCode);
                    StringBuilder description = new StringBuilder();
                    description.append("商品唛头: ").append(barCode)
                            .append(", 名称: ").append(materialName)
                            .append(", 各用户出库数量不一致: ");

                    for (Map.Entry<Long, BigDecimal> userEntry : userQuantityMap.entrySet()) {
                        description.append("用户ID ").append(userEntry.getKey())
                                .append(": ").append(userEntry.getValue()).append("; ");
                    }

                    ValidationDifference difference = new ValidationDifference();
                    difference.setMaterialBarCode(barCode);
                    difference.setMaterialName(materialName);
                    difference.setDescription(description.toString());
                    differences.add(difference);
                }
            }

        } catch (Exception e) {
            logger.debug("校验商品唛头数量一致性失败，异常信息: {}", e.getMessage());
            throw e;
        }

        return differences;
    }

    /**
     * 验证日期格式是否正确
     * 
     * @param dateString 日期字符串
     * @return 是否为有效的日期格式
     */
    private boolean isValidDateFormat(String dateString) {
        if (StringUtil.isEmpty(dateString)) {
            return false;
        }
        
        // 验证日期格式：YYYY-MM-DD
        Pattern datePattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
        if (!datePattern.matcher(dateString).matches()) {
            return false;
        }
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            sdf.parse(dateString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}