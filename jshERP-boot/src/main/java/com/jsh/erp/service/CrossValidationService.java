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
import java.util.Set;
import java.util.HashSet;
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
            logger.info("checkTodayUsers方法执行异常，校验日期: {}, 异常信息: {}", validationDate, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.info("checkTodayUsers方法执行异常，校验日期: {}, 异常信息: {}", validationDate, e.getMessage(), e);
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

            // 构建包含当前用户和选中用户的完整用户列表
            List<Long> allUserIds = new ArrayList<>(request.getSelectedUserIds());
            if (!allUserIds.contains(currentUser.getId())) {
                allUserIds.add(currentUser.getId());
            }
            
            logger.info("准备查询商品唛头汇总数据，日期: {}, 租户ID: {}, 当前用户ID: {}, 选中用户列表: {}, 完整用户列表: {}", 
                    request.getValidationDate(), tenantId, currentUser.getId(), request.getSelectedUserIds(), allUserIds);
            
            List<BillMaterialSummary> materialSummaries = depotHeadMapper.getBillMaterialSummaryByDateAndUsers(
                    request.getValidationDate(), tenantId, allUserIds);

            logger.info("查询到商品唛头汇总数据条数: {}", materialSummaries == null ? 0 : materialSummaries.size());
            
            if (materialSummaries != null && !materialSummaries.isEmpty()) {
                for (BillMaterialSummary summary : materialSummaries) {
                    logger.info("商品数据详情: 用户ID={}, 用户名={}, 条形码={}, 商品名={}, 数量={}", 
                            summary.getUserId(), summary.getUserName(), summary.getMaterialBarCode(), 
                            summary.getMaterialName(), summary.getTotalOutNumber());
                }
            }

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
            logger.info("开始校验数量一致性，输入数据条数: {}", materialSummaries == null ? 0 : materialSummaries.size());
            
            if (materialSummaries == null || materialSummaries.isEmpty()) {
                logger.info("没有数据需要校验，返回空的差异列表");
                return differences;
            }

            // 步骤1: 收集所有参与校验的用户ID
            Set<Long> allUserIds = new HashSet<>();
            for (BillMaterialSummary summary : materialSummaries) {
                allUserIds.add(summary.getUserId());
            }
            logger.info("收集到所有参与校验的用户ID: {}", allUserIds);

            // 步骤2: 收集所有参与校验用户当日未审核单据涉及的商品唛头（所有用户的商品并集）
            Set<String> allBarCodes = new HashSet<>();
            for (BillMaterialSummary summary : materialSummaries) {
                allBarCodes.add(summary.getMaterialBarCode());
            }
            logger.info("收集到所有商品唛头: {}", allBarCodes);

            // 步骤3: 构建用户ID到用户名的映射
            Map<Long, String> userIdToNameMap = new HashMap<>();
            for (BillMaterialSummary summary : materialSummaries) {
                userIdToNameMap.put(summary.getUserId(), summary.getUserName());
            }

            // 步骤4: 构建商品唛头到商品名称的映射
            Map<String, String> barCodeMaterialNameMap = new HashMap<>();
            for (BillMaterialSummary summary : materialSummaries) {
                barCodeMaterialNameMap.put(summary.getMaterialBarCode(), summary.getMaterialName());
            }

            // 步骤5: 构建完整的用户-商品-数量映射表，为缺失的用户-商品组合设置数量为0
            Map<String, Map<Long, BigDecimal>> barCodeUserQuantityMap = new HashMap<>();
            
            // 首先初始化所有商品的所有用户数量为0
            for (String barCode : allBarCodes) {
                Map<Long, BigDecimal> userQuantityMap = new HashMap<>();
                for (Long userId : allUserIds) {
                    userQuantityMap.put(userId, BigDecimal.ZERO);
                }
                barCodeUserQuantityMap.put(barCode, userQuantityMap);
            }

            // 然后填入实际的数量数据
            for (BillMaterialSummary summary : materialSummaries) {
                String barCode = summary.getMaterialBarCode();
                Long userId = summary.getUserId();
                BigDecimal quantity = summary.getTotalOutNumber();
                
                barCodeUserQuantityMap.get(barCode).put(userId, quantity);
            }

            logger.info("构建完整的用户-商品-数量映射表完成，商品数量: {}, 用户数量: {}", allBarCodes.size(), allUserIds.size());

            // 步骤6: 严格校验每个商品在所有用户间的数量一致性
            for (String barCode : allBarCodes) {
                Map<Long, BigDecimal> userQuantityMap = barCodeUserQuantityMap.get(barCode);
                
                logger.info("开始校验商品 {} 的数量一致性", barCode);
                
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
                    
                    logger.info("发现数量不一致的商品: {}, 商品名称: {}", barCode, materialName);
                    
                    // 构建差异描述
                    StringBuilder description = new StringBuilder();
                    description.append("商品唛头: ").append(barCode)
                            .append(", 名称: ").append(materialName)
                            .append(", 各用户出库数量不一致: ");

                    StringBuilder usersInfo = new StringBuilder();
                    
                    for (Map.Entry<Long, BigDecimal> userEntry : userQuantityMap.entrySet()) {
                        Long userId = userEntry.getKey();
                        String userName = userIdToNameMap.get(userId);
                        BigDecimal quantity = userEntry.getValue();
                        
                        description.append(userName).append("(ID:").append(userId).append(")")
                                .append(": ").append(quantity).append("; ");
                        
                        if (usersInfo.length() > 0) {
                            usersInfo.append(", ");
                        }
                        usersInfo.append(userName);
                        
                        logger.info("用户 {} (ID: {}) 的商品 {} 数量: {}", userName, userId, barCode, quantity);
                    }

                    ValidationDifference difference = new ValidationDifference();
                    difference.setMaterialBarCode(barCode);
                    difference.setMaterialName(materialName);
                    difference.setDiffType("QUANTITY_INCONSISTENT");
                    difference.setDiffTypeName("数量不一致");
                    difference.setDescription(description.toString());
                    difference.setUsers(usersInfo.toString());
                    difference.setAffectedBills(userQuantityMap.size());
                    differences.add(difference);
                }
            }

            logger.info("校验完成，共发现 {} 个差异", differences.size());

        } catch (Exception e) {
            logger.error("校验商品唛头数量一致性失败，异常信息: {}", e.getMessage(), e);
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