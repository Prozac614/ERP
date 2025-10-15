package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.entities.DepotHead;
import com.jsh.erp.datasource.entities.DepotHeadExample;
import com.jsh.erp.datasource.mappers.DepotHeadMapper;
import com.jsh.erp.datasource.vo.BillMaterialSummary;
import com.jsh.erp.datasource.vo.CrossValidationCheckResult;
import com.jsh.erp.datasource.vo.CrossValidationRequest;
import com.jsh.erp.datasource.vo.CrossValidationResult;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
import com.jsh.erp.datasource.vo.ValidationDifference;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.exception.BusinessRunTimeException;

import com.jsh.erp.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

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
import java.util.regex.Pattern;
import java.util.Collections;

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
    private DepotHeadService depotHeadService;

    @Resource
    private UserService userService;

    /**
     * 检查指定日期其他用户的单据情况
     * 
     * @param validationDate 校验日期
     * @return 检查结果
     */
    public CrossValidationCheckResult checkTodayUsers(String validationDate, String type, String subType,
            List<String> shopNames) {
        logger.info("开始执行checkTodayUsers方法，校验日期: {}, 单据类型: {}, 子类型: {}", validationDate, type, subType);

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
            List<TodayUserBillSummary> otherUsers = getTodayUserBillSummaryByDate(validationDate, tenantId,
                    currentUserId, type, subType, shopNames);

            // 获取单据总数
            int totalBills = countBillsByDateAndUsers(validationDate, tenantId, otherUsers, type, subType);

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
                    request.getValidationDate(), tenantId, currentUser.getId(), request.getSelectedUserIds(),
                    allUserIds);

            List<BillMaterialSummary> materialSummaries = depotHeadMapper.getBillMaterialSummaryByDateAndUsers(
                    request.getValidationDate(), tenantId, allUserIds, request.getType(), request.getSubType(),
                    request.getShopNames());

            logger.info("查询到商品唛头汇总数据条数: {}", materialSummaries == null ? 0 : materialSummaries.size());

            if (materialSummaries != null && !materialSummaries.isEmpty()) {
                for (BillMaterialSummary summary : materialSummaries) {
                    logger.info("商品数据详情: 用户ID={}, 用户名={}, 条形码={}, 商品名={}, 数量={}",
                            summary.getUserId(), summary.getUserName(), summary.getMaterialBarCode(),
                            summary.getMaterialName(), summary.getTotalOutNumber());
                }
            }

            // 先检查每个用户是否都有单据
            Map<Long, Integer> userBillCounts = new HashMap<>();
            for (Long userId : allUserIds) {
                List<DepotHead> userBills = depotHeadMapper.getBillsByDateAndUsers(
                        request.getValidationDate(), tenantId,
                        Collections.singletonList(userId),
                        request.getType(), request.getSubType(),
                        request.getShopNames());
                userBillCounts.put(userId, userBills == null ? 0 : userBills.size());
            }

            // 检查是否只有一个用户有单据
            long usersWithBills = userBillCounts.values().stream().filter(count -> count > 0).count();
            if (usersWithBills <= 1) {
                result.setConsistent(false);
                List<ValidationDifference> differences = new ArrayList<>();
                ValidationDifference difference = new ValidationDifference();
                difference.setDiffType("BILL_COUNT_INCONSISTENT");
                difference.setDiffTypeName("单据数量不一致");

                // 找出有单据的用户
                Map.Entry<Long, Integer> userWithBills = userBillCounts.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .findFirst()
                        .orElse(null);

                String description;
                if (userWithBills != null) {
                    User user = userService.getUser(userWithBills.getKey());
                    String userName = user != null ? user.getUsername() : "未知用户";
                    description = String.format("只有用户 %s 录入了单据（%d份），无法进行交叉校验",
                            userName, userWithBills.getValue());
                } else {
                    description = "没有用户录入任何单据，无法进行交叉校验";
                }

                difference.setDescription(description);
                differences.add(difference);
                result.setDifferences(differences);
                result.setTotalBills(0);
                logger.info("单据数量不足以进行交叉校验，校验日期: {}", request.getValidationDate());
                return result;
            }

            // 如果所有用户都有单据，但没有商品明细数据，说明单据可能是空的
            if (materialSummaries == null || materialSummaries.isEmpty()) {
                result.setConsistent(false);
                List<ValidationDifference> differences = new ArrayList<>();
                ValidationDifference difference = new ValidationDifference();
                difference.setDiffType("NO_MATERIAL_DETAILS");
                difference.setDiffTypeName("无商品明细");
                difference.setDescription("所有用户的单据中都没有商品明细数据");
                differences.add(difference);
                result.setDifferences(differences);
                result.setTotalBills(0);
                logger.info("所有单据都没有商品明细数据，校验日期: {}", request.getValidationDate());
                return result;
            }

            // 执行数量一致性校验
            List<ValidationDifference> differences = validateQuantityConsistency(materialSummaries);

            // 统计校验的商品种类数量
            Set<String> uniqueBarCodes = new HashSet<>();
            for (BillMaterialSummary summary : materialSummaries) {
                uniqueBarCodes.add(summary.getMaterialBarCode());
            }
            int totalMaterials = uniqueBarCodes.size();

            result.setConsistent(differences.isEmpty());
            result.setDifferences(differences);
            result.setTotalBills(totalMaterials);

            // 如果校验通过，自动更新单据状态
            if (differences.isEmpty()) {
                updateBillStatusAfterValidation(request.getValidationDate(), tenantId, allUserIds, currentUser.getId(),
                        request.getType(), request.getSubType(), request.getShopNames());
            }

            logger.info(
                    "performCrossValidation方法执行完成，校验日期: {}, 返回结果: consistent={}, differences.size()={}, totalMaterials={}",
                    request.getValidationDate(), result.isConsistent(), differences.size(), totalMaterials);

        } catch (BusinessRunTimeException e) {
            // 如果是业务异常，直接重新抛出，保留原始错误信息
            logger.debug("performCrossValidation方法执行异常，校验日期: {}, 异常信息: {}", request.getValidationDate(),
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.debug("performCrossValidation方法执行异常，校验日期: {}, 异常信息: {}", request.getValidationDate(), e.getMessage(),
                    e);
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
    private List<TodayUserBillSummary> getTodayUserBillSummaryByDate(String validationDate, Long tenantId,
            Long currentUserId, String type, String subType, List<String> shopNames) {
        try {
            logger.info("查询指定日期用户单据汇总，参数: validationDate={}, tenantId={}, currentUserId={}, type={}, subType={}",
                    validationDate, tenantId, currentUserId, type, subType);

            // 先进行简单的测试：检查当前用户是否有效
            if (currentUserId == null) {
                throw new RuntimeException("当前用户ID为空");
            }

            // 调用指定日期的用户单据汇总查询方法
            List<TodayUserBillSummary> result;
            try {
                result = depotHeadMapper.getUserBillSummaryByDate(validationDate, tenantId, currentUserId, type,
                        subType, shopNames);
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
    private int countBillsByDateAndUsers(String validationDate, Long tenantId, List<TodayUserBillSummary> otherUsers,
            String type, String subType) {
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
     * 校验商品唛头的数量和单价一致性
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

            // 步骤5: 构建完整的用户-商品-商店-数量和单价映射表
            // 结构: Map<商品条码, Map<String, Map<Long, List<商品记录>>>>
            // 第一层key是商品条码，第二层key是商店名称（空字符串表示未指定商店），第三层key是用户ID
            Map<String, Map<String, Map<Long, List<BillMaterialSummary>>>> barCodeShopUserRecordsMap = new HashMap<>();

            // 按商品条码、商店和用户ID组织数据
            for (BillMaterialSummary summary : materialSummaries) {
                String barCode = summary.getMaterialBarCode();
                String shopName = summary.getShopName() == null ? "" : summary.getShopName();
                Long userId = summary.getUserId();

                barCodeShopUserRecordsMap.computeIfAbsent(barCode, k -> new HashMap<>())
                        .computeIfAbsent(shopName, k -> new HashMap<>())
                        .computeIfAbsent(userId, k -> new ArrayList<>())
                        .add(summary);
            }

            // 计算每个商店每个用户每个商品的总数量和统一单价
            Map<String, Map<String, Map<Long, BigDecimal>>> barCodeShopUserQuantityMap = new HashMap<>();
            Map<String, Map<String, Map<Long, BigDecimal>>> barCodeShopUserPriceMap = new HashMap<>();

            for (String barCode : allBarCodes) {
                Map<String, Map<Long, BigDecimal>> shopUserQuantityMap = new HashMap<>();
                Map<String, Map<Long, BigDecimal>> shopUserPriceMap = new HashMap<>();

                // 获取该商品的所有商店
                Set<String> shopNames = barCodeShopUserRecordsMap.getOrDefault(barCode, new HashMap<>()).keySet();

                for (String shopName : shopNames) {
                    Map<Long, BigDecimal> userQuantityMap = new HashMap<>();
                    Map<Long, BigDecimal> userPriceMap = new HashMap<>();

                    for (Long userId : allUserIds) {
                        List<BillMaterialSummary> userRecords = barCodeShopUserRecordsMap
                                .getOrDefault(barCode, new HashMap<>())
                                .getOrDefault(shopName, new HashMap<>())
                                .getOrDefault(userId, new ArrayList<>());

                        if (userRecords.isEmpty()) {
                            // 用户在该商店没有该商品的记录
                            userQuantityMap.put(userId, BigDecimal.ZERO);
                            userPriceMap.put(userId, null);
                        } else {
                            // 计算该用户在该商店该商品的总数量
                            BigDecimal totalQuantity = userRecords.stream()
                                    .map(BillMaterialSummary::getTotalOutNumber)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            userQuantityMap.put(userId, totalQuantity);

                            // 检查该用户在该商店该商品的单价是否一致
                            Set<BigDecimal> prices = userRecords.stream()
                                    .map(BillMaterialSummary::getUnitPrice)
                                    .collect(java.util.stream.Collectors.toSet());

                            if (prices.size() > 1) {
                                // 同一用户同一商店同一商品有多个不同单价，记录为差异
                                logger.warn("用户 {} 在商店 {} 的商品 {} 有多个不同单价: {}", userId, shopName, barCode, prices);
                            }

                            // 使用第一个记录的单价作为该用户该商品的单价
                            userPriceMap.put(userId, userRecords.get(0).getUnitPrice());
                        }
                    }

                    shopUserQuantityMap.put(shopName, userQuantityMap);
                    shopUserPriceMap.put(shopName, userPriceMap);
                }

                barCodeShopUserQuantityMap.put(barCode, shopUserQuantityMap);
                barCodeShopUserPriceMap.put(barCode, shopUserPriceMap);
            }

            logger.info("构建完整的用户-商品-数量映射表完成，商品数量: {}, 用户数量: {}", allBarCodes.size(), allUserIds.size());

            // 步骤6: 严格校验每个商品在每个商店的所有用户间的数量和单价一致性
            for (String barCode : allBarCodes) {
                Map<String, Map<Long, BigDecimal>> shopUserQuantityMap = barCodeShopUserQuantityMap.get(barCode);
                Map<String, Map<Long, BigDecimal>> shopUserPriceMap = barCodeShopUserPriceMap.get(barCode);

                logger.info("开始校验商品 {} 的数量和单价一致性", barCode);

                for (String shopName : shopUserQuantityMap.keySet()) {
                    Map<Long, BigDecimal> userQuantityMap = shopUserQuantityMap.get(shopName);
                    Map<Long, BigDecimal> userPriceMap = shopUserPriceMap.get(shopName);

                    // 检查所有用户在该商店的数量是否一致
                    BigDecimal firstQuantity = null;
                    boolean quantityConsistent = true;

                    for (BigDecimal quantity : userQuantityMap.values()) {
                        if (firstQuantity == null) {
                            firstQuantity = quantity;
                        } else if (firstQuantity.compareTo(quantity) != 0) {
                            quantityConsistent = false;
                            break;
                        }
                    }

                    // 检查所有用户在该商店的单价是否一致（排除没有该商品的用户）
                    BigDecimal firstPrice = null;
                    boolean priceConsistent = true;

                    for (BigDecimal price : userPriceMap.values()) {
                        if (price != null) { // 只比较有该商品的用户的单价
                            if (firstPrice == null) {
                                firstPrice = price;
                            } else if (firstPrice.compareTo(price) != 0) {
                                priceConsistent = false;
                                break;
                            }
                        }
                    }

                    boolean isConsistent = quantityConsistent && priceConsistent;

                    // 如果不一致，记录差异
                    if (!isConsistent) {
                        String materialName = barCodeMaterialNameMap.get(barCode);
                        String shopDisplayName = shopName.isEmpty() ? "未指定商店" : shopName;

                        logger.info("发现商店 {} 的商品数量或单价不一致: {}, 商品名称: {}, 数量一致: {}, 单价一致: {}",
                                shopDisplayName, barCode, materialName, quantityConsistent, priceConsistent);

                        // 构建差异描述
                        StringBuilder description = new StringBuilder();
                        description.append("商品唛头: ").append(barCode)
                                .append(", 名称: ").append(materialName)
                                .append(", 商店: ").append(shopDisplayName);

                        if (!quantityConsistent && !priceConsistent) {
                            description.append(", 各用户数量和单价均不一致: ");
                        } else if (!quantityConsistent) {
                            description.append(", 各用户数量不一致: ");
                        } else {
                            description.append(", 各用户单价不一致: ");
                        }

                        StringBuilder usersInfo = new StringBuilder();

                        // 构建结构化的用户数量映射（key为用户名）
                        Map<String, BigDecimal> userQuantitiesMap = new HashMap<>();

                        for (Map.Entry<Long, BigDecimal> userEntry : userQuantityMap.entrySet()) {
                            Long userId = userEntry.getKey();
                            String userName = userIdToNameMap.get(userId);
                            BigDecimal quantity = userEntry.getValue();
                            BigDecimal price = userPriceMap.get(userId);

                            // 设置结构化数据
                            userQuantitiesMap.put(userName, quantity);

                            description.append(userName).append("(ID:").append(userId).append(")")
                                    .append(": 数量=").append(quantity)
                                    .append(", 单价=").append(price == null ? "无" : price)
                                    .append("; ");

                            if (usersInfo.length() > 0) {
                                usersInfo.append(", ");
                            }
                            usersInfo.append(userName);

                            logger.info("用户 {} (ID: {}) 在商店 {} 的商品 {} 数量: {}, 单价: {}",
                                    userName, userId, shopDisplayName, barCode, quantity, price);
                        }

                        ValidationDifference difference = new ValidationDifference();
                        difference.setMaterialBarCode(barCode);
                        difference.setMaterialName(materialName);
                        difference.setShopName(shopDisplayName);

                        if (!quantityConsistent && !priceConsistent) {
                            difference.setDiffType("QUANTITY_PRICE_INCONSISTENT");
                            difference.setDiffTypeName("数量单价不一致");
                        } else if (!quantityConsistent) {
                            difference.setDiffType("QUANTITY_INCONSISTENT");
                            difference.setDiffTypeName("数量不一致");
                        } else {
                            difference.setDiffType("PRICE_INCONSISTENT");
                            difference.setDiffTypeName("单价不一致");
                        }

                        difference.setDescription(description.toString());
                        difference.setUsers(usersInfo.toString());
                        difference.setAffectedBills(userQuantityMap.size());
                        // 设置结构化的用户数量数据
                        difference.setUserQuantities(userQuantitiesMap);
                        differences.add(difference);
                    }
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
     * 校验通过后更新单据状态
     * 当前用户的单据设置为已审核，其他用户的单据设置为完成出库（表示数据已录入库存）
     * 
     * @param validationDate 校验日期
     * @param tenantId       租户ID
     * @param allUserIds     所有参与校验的用户ID
     * @param currentUserId  当前用户ID
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    private void updateBillStatusAfterValidation(String validationDate, Long tenantId, List<Long> allUserIds,
            Long currentUserId, String type, String subType, List<String> shopNames) {
        try {
            logger.info("开始更新校验通过后的单据状态，日期: {}, 租户ID: {}, 用户列表: {}, 当前用户: {}, 类型: {}, 子类型: {}",
                    validationDate, tenantId, allUserIds, currentUserId, type, subType);

            // 查询所有参与校验的用户的单据
            List<DepotHead> bills = depotHeadMapper.getBillsByDateAndUsers(validationDate, tenantId, allUserIds, type,
                    subType, shopNames);

            if (bills == null || bills.isEmpty()) {
                logger.info("未找到需要更新状态的单据");
                return;
            }

            logger.info("查询到需要更新状态的单据数量: {}", bills.size());

            // 分别处理当前用户和其他用户的单据
            List<Long> currentUserBillIds = new ArrayList<>();
            List<Long> otherUserBillIds = new ArrayList<>();

            for (DepotHead bill : bills) {
                if (currentUserId.equals(bill.getCreator())) {
                    currentUserBillIds.add(bill.getId());
                } else {
                    otherUserBillIds.add(bill.getId());
                }
            }

            logger.info("当前用户单据数量: {}, 其他用户单据数量: {}", currentUserBillIds.size(), otherUserBillIds.size());

            // 更新当前用户的单据为已审核状态（使用batchSetStatus确保库存更新）
            if (!currentUserBillIds.isEmpty()) {
                String currentUserIds = currentUserBillIds.stream().map(String::valueOf)
                        .collect(Collectors.joining(","));
                depotHeadService.batchSetStatus(BusinessConstants.BILLS_STATUS_AUDIT, currentUserIds);
                logger.info("已将当前用户的 {} 张单据设置为已审核状态并更新库存", currentUserBillIds.size());
            }

            // 更新其他用户的单据为完成出库状态（直接更新，不需要库存更新）
            if (!otherUserBillIds.isEmpty()) {
                updateBillStatusByIds(otherUserBillIds, BusinessConstants.BILLS_STATUS_SKIPED); // "2"完成出库状态
                logger.info("已将其他用户的 {} 张单据设置为完成出库状态", otherUserBillIds.size());
            }

            logger.info("校验通过后单据状态更新完成");

        } catch (Exception e) {
            logger.error("更新校验通过后的单据状态失败，异常信息: {}", e.getMessage(), e);
            throw new BusinessRunTimeException(ExceptionConstants.CROSS_VALIDATION_EXECUTE_FAILED_CODE,
                    "更新单据状态失败: " + e.getMessage());
        }
    }

    /**
     * 根据单据ID列表批量更新单据状态
     * 
     * @param billIds 单据ID列表
     * @param status  目标状态
     */
    private void updateBillStatusByIds(List<Long> billIds, String status) {
        if (billIds == null || billIds.isEmpty()) {
            return;
        }

        try {
            // 构建更新对象
            DepotHead updateBill = new DepotHead();
            updateBill.setStatus(status);

            // 构建更新条件
            DepotHeadExample example = new DepotHeadExample();
            example.createCriteria().andIdIn(billIds);

            // 执行批量更新
            int updateCount = depotHeadMapper.updateByExampleSelective(updateBill, example);
            logger.info("批量更新单据状态完成，更新状态: {}, 影响行数: {}", status, updateCount);

        } catch (Exception e) {
            logger.error("批量更新单据状态失败，单据ID列表: {}, 目标状态: {}, 异常信息: {}",
                    billIds, status, e.getMessage(), e);
            throw e;
        }
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