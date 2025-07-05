package com.jsh.erp.service;

import com.jsh.erp.datasource.vo.CrossValidationCheckResult;
import com.jsh.erp.datasource.vo.CrossValidationRequest;
import com.jsh.erp.datasource.vo.CrossValidationResult;
import com.jsh.erp.datasource.vo.TodayUserBillSummary;
import com.jsh.erp.datasource.vo.ValidationDifference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

/**
 * 交叉校验服务
 * 用于处理销售出库单据的用户间交叉校验
 */
@Service
public class CrossValidationService {
    private Logger logger = LoggerFactory.getLogger(CrossValidationService.class);

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

        // TODO: 实现检查指定日期其他用户单据的逻辑
        // 暂时返回空结果，后续在阶段二完善
        List<TodayUserBillSummary> otherUsers = new ArrayList<>();
        result.setOtherUsers(otherUsers);
        result.setHasOtherUsers(otherUsers.size() > 0);

        logger.info("checkTodayUsers方法执行完成，校验日期: {}, 返回结果: hasOtherUsers={}, otherUsers.size()={}",
                validationDate, result.isHasOtherUsers(), otherUsers.size());

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

        // TODO: 实现交叉校验逻辑
        // 暂时返回校验失败结果，后续在阶段二完善
        List<ValidationDifference> differences = new ArrayList<>();
        differences.add(new ValidationDifference("TEMP", "临时", "校验功能正在开发中"));

        result.setConsistent(false);
        result.setDifferences(differences);
        result.setTotalBills(0);

        logger.info("performCrossValidation方法执行完成，校验日期: {}, 返回结果: consistent={}, differences.size()={}",
                request.getValidationDate(), result.isConsistent(), differences.size());

        return result;
    }
}