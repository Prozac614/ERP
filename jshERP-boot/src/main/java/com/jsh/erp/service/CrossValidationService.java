package com.jsh.erp.service;

import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.entities.DepotHead;
import com.jsh.erp.datasource.entities.DepotHeadExample;
import com.jsh.erp.datasource.mappers.DepotHeadMapper;
import com.jsh.erp.datasource.vo.CrossValidationCheckResult;
import com.jsh.erp.datasource.vo.CrossValidationRequest;
import com.jsh.erp.datasource.vo.CrossValidationResult;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
import com.jsh.erp.datasource.vo.ValidationDifference;
import com.jsh.erp.datasource.vo.ValidationBillDetail;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
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

            logger.info("准备查询商品明细数据，日期: {}, 租户ID: {}, 当前用户ID: {}, 选中用户列表: {}, 完整用户列表: {}",
                    request.getValidationDate(), tenantId, currentUser.getId(), request.getSelectedUserIds(),
                    allUserIds);

            List<ValidationBillDetail> billDetails = depotHeadMapper.getBillDetailsByDateUsersAndMaterial(
                    request.getValidationDate(), tenantId, allUserIds, request.getType(), request.getSubType(),
                    request.getShopNames());

            logger.info("查询到商品明细数据条数: {}", billDetails == null ? 0 : billDetails.size());

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
            if (billDetails == null || billDetails.isEmpty()) {
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

            Map<Long, String> userIdToName = new HashMap<>();
            userIdToName.put(currentUser.getId(), currentUser.getUsername());

            if (billDetails != null) {
                for (ValidationBillDetail detail : billDetails) {
                    if (detail != null && detail.getUserId() != null && !StringUtil.isEmpty(detail.getUserName())) {
                        userIdToName.put(detail.getUserId(), detail.getUserName());
                    }
                }
            }

            for (Long userId : allUserIds) {
                userIdToName.computeIfAbsent(userId, id -> {
                    try {
                        User user = userService.getUser(id);
                        return user != null ? user.getUsername() : "用户" + id;
                    } catch (Exception ex) {
                        logger.warn("根据用户ID获取用户名失败: {}", id, ex);
                        return "用户" + id;
                    }
                });
            }

            List<ValidationDifference> differences = buildDifferencesFromDetails(billDetails, allUserIds,
                    userIdToName, request.getType(), request.getSubType());

            Set<String> uniqueBarCodes = billDetails.stream()
                    .map(ValidationBillDetail::getMaterialBarCode)
                    .filter(code -> !StringUtil.isEmpty(code))
                    .collect(Collectors.toSet());
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

    private List<ValidationDifference> buildDifferencesFromDetails(List<ValidationBillDetail> billDetails,
            List<Long> allUserIds, Map<Long, String> userIdToName, String type, String subType) {
        List<ValidationDifference> differences = new ArrayList<>();

        try {
            logger.info("开始基于明细数据构建差异，输入数据条数: {}", billDetails == null ? 0 : billDetails.size());

            // 判断是否为采购入库（只有采购入库才需要校验单价）
            boolean isPurchaseIn = "入库".equals(type) && BusinessConstants.SUB_TYPE_PURCHASE.equals(subType);

            if (billDetails == null || billDetails.isEmpty()) {
                return differences;
            }

            Map<String, Map<Long, List<ValidationBillDetail>>> detailMap = new LinkedHashMap<>();
            Map<String, String> materialNameMap = new HashMap<>();
            Map<String, String> shopNameMap = new HashMap<>();

            for (ValidationBillDetail detail : billDetails) {
                if (detail == null || StringUtil.isEmpty(detail.getMaterialBarCode()) || detail.getUserId() == null) {
                    continue;
                }
                String shopName = detail.getShopName() == null ? "" : detail.getShopName();
                String key = detail.getMaterialBarCode() + "||" + shopName;
                materialNameMap.putIfAbsent(key, detail.getMaterialName());
                shopNameMap.putIfAbsent(key, shopName);
                detailMap.computeIfAbsent(key, k -> new LinkedHashMap<>())
                        .computeIfAbsent(detail.getUserId(), k -> new ArrayList<>())
                        .add(detail);
            }

            for (Map.Entry<String, Map<Long, List<ValidationBillDetail>>> entry : detailMap.entrySet()) {
                String key = entry.getKey();
                Map<Long, List<ValidationBillDetail>> userDetailMap = entry.getValue();

                Map<Long, BigDecimal> userQuantityPerUser = new LinkedHashMap<>();
                Map<Long, LinkedHashSet<BigDecimal>> userPricePerUser = new LinkedHashMap<>();
                Map<String, BigDecimal> userQuantityByName = new LinkedHashMap<>();
                Map<String, List<ValidationBillDetail>> detailByUserName = new LinkedHashMap<>();

                BigDecimal baselineQuantity = null;
                boolean quantityConsistent = true;
                BigDecimal baselinePrice = null;
                // 非采购入库单据，不校验单价，默认单价一致；采购入库会在循环中根据实际情况设置
                boolean priceConsistent = true;

                for (Long userId : allUserIds) {
                    List<ValidationBillDetail> detailsForUser = userDetailMap.getOrDefault(userId, Collections.emptyList());
                    BigDecimal totalQuantity = detailsForUser.stream()
                            .map(ValidationBillDetail::getQuantity)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    LinkedHashSet<BigDecimal> priceSet = detailsForUser.stream()
                            .map(ValidationBillDetail::getUnitPrice)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    if (baselineQuantity == null) {
                        baselineQuantity = totalQuantity;
                    } else if (baselineQuantity.compareTo(totalQuantity) != 0) {
                        quantityConsistent = false;
                    }

                    // 只有采购入库才校验单价
                    if (isPurchaseIn) {
                        if (priceSet.size() > 1) {
                            priceConsistent = false;
                        }
                        if (!priceSet.isEmpty()) {
                            BigDecimal candidate = priceSet.iterator().next();
                            if (baselinePrice == null) {
                                baselinePrice = candidate;
                            } else if (baselinePrice.compareTo(candidate) != 0) {
                                priceConsistent = false;
                            }
                        }
                    }

                    userQuantityPerUser.put(userId, totalQuantity);
                    userPricePerUser.put(userId, priceSet);

                    String userName = userIdToName.getOrDefault(userId, "用户" + userId);
                    userQuantityByName.put(userName, totalQuantity);
                    detailByUserName.put(userName, new ArrayList<>(detailsForUser));
                }

                // 采购入库需要数量和单价都一致，其他单据类型只需要数量一致
                if (quantityConsistent && (isPurchaseIn ? priceConsistent : true)) {
                    continue;
                }

                String[] parts = key.split("\\|\\|", -1);
                String materialBarCode = parts.length > 0 ? parts[0] : "";
                String shopRaw = shopNameMap.getOrDefault(key, "");
                String materialName = materialNameMap.get(key);
                String shopDisplayName = StringUtil.isEmpty(shopRaw) ? "未指定商店" : shopRaw;

                ValidationDifference difference = new ValidationDifference();
                difference.setMaterialBarCode(materialBarCode);
                difference.setMaterialName(materialName != null ? materialName : "未知商品");
                difference.setShopNameRaw(shopRaw);
                difference.setShopName(shopDisplayName);

                StringBuilder description = new StringBuilder();
                description.append("商品唛头: ").append(materialBarCode)
                        .append(", 名称: ").append(difference.getMaterialName())
                        .append(", 商店: ").append(shopDisplayName);

                if (!quantityConsistent && isPurchaseIn && !priceConsistent) {
                    difference.setDiffType("QUANTITY_PRICE_INCONSISTENT");
                    difference.setDiffTypeName("数量单价不一致");
                    description.append(", 各用户数量和单价均不一致: ");
                } else if (!quantityConsistent) {
                    difference.setDiffType("QUANTITY_INCONSISTENT");
                    difference.setDiffTypeName("数量不一致");
                    description.append(", 各用户数量不一致: ");
                } else if (isPurchaseIn && !priceConsistent) {
                    // 只有采购入库才会报告单价不一致
                    difference.setDiffType("PRICE_INCONSISTENT");
                    difference.setDiffTypeName("单价不一致");
                    description.append(", 各用户单价不一致: ");
                } else {
                    // 非采购入库单据，如果数量一致但单价不一致，不应该到达这里
                    // 因为非采购入库时 priceConsistent 始终为 true
                    continue;
                }

                StringBuilder usersInfo = new StringBuilder();
                for (Long userId : allUserIds) {
                    String userName = userIdToName.getOrDefault(userId, "用户" + userId);
                    BigDecimal totalQuantity = userQuantityPerUser.getOrDefault(userId, BigDecimal.ZERO);
                    LinkedHashSet<BigDecimal> priceSet = userPricePerUser.getOrDefault(userId, new LinkedHashSet<>());
                    BigDecimal displayPrice = priceSet.isEmpty() ? null : priceSet.iterator().next();

                    description.append(userName).append("(ID:").append(userId).append(")")
                            .append(": 数量=").append(totalQuantity);
                    // 只有采购入库才显示单价信息
                    if (isPurchaseIn) {
                        description.append(", 单价=").append(displayPrice == null ? "无" : displayPrice);
                    }
                    description.append("; ");

                    if (usersInfo.length() > 0) {
                        usersInfo.append(", ");
                    }
                    usersInfo.append(userName);
                }

                difference.setDescription(description.toString());
                difference.setUsers(usersInfo.toString());
                difference.setAffectedBills(detailByUserName.values().stream().mapToInt(List::size).sum());
                difference.setUserQuantities(userQuantityByName);
                difference.setUserBillDetails(detailByUserName);

                differences.add(difference);
            }

            logger.info("基于明细的数据差异构建完成，共发现 {} 个差异", differences.size());
        } catch (Exception e) {
            logger.error("处理交叉校验明细差异时发生异常: {}", e.getMessage(), e);
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