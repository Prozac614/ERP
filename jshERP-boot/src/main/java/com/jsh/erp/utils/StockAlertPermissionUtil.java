package com.jsh.erp.utils;

import com.jsh.erp.datasource.entities.Function;
import com.jsh.erp.datasource.entities.FunctionExample;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.entities.UserBusiness;
import com.jsh.erp.service.FunctionService;
import com.jsh.erp.service.UserBusinessService;
import com.jsh.erp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 库存预警权限检查工具类
 * 用于检查用户是否有库存预警相关功能的权限
 * 
 * @author jshERP
 */
@Component
public class StockAlertPermissionUtil {

    private static final Logger logger = LoggerFactory.getLogger(StockAlertPermissionUtil.class);

    // 库存预警功能编号
    private static final String STOCK_ALERT_FUNCTION_NUMBER = "010104";

    @Autowired
    private UserService userService;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private FunctionService functionService;

    private static StockAlertPermissionUtil instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * 检查当前用户是否有库存预警权限
     * 
     * @param request HTTP请求对象，用于获取当前用户信息
     * @return true-有权限，false-无权限
     */
    public static boolean hasStockAlertPermission(HttpServletRequest request) {
        try {
            // 获取当前用户ID
            Long userId = instance.userService.getUserId(request);
            if (userId == null || userId == 0L) {
                logger.warn("检查库存预警权限失败：无法获取当前用户ID");
                return false;
            }

            logger.debug("检查用户{}的库存预警权限", userId);

            // 获取当前用户信息
            User currentUser = instance.userService.getUser(userId);
            if (currentUser == null) {
                logger.warn("检查库存预警权限失败：用户{}不存在", userId);
                return false;
            }

            // 超级管理员(tenant_id=63)始终有权限
            if (currentUser.getTenantId() != null && currentUser.getTenantId() == 63L) {
                logger.debug("用户{}是超级管理员，直接授予库存预警权限", userId);
                return true;
            }

            // 检查用户的角色功能权限
            return hasStockAlertPermissionForUser(userId);

        } catch (Exception e) {
            logger.error("检查库存预警权限时发生异常", e);
            return false;
        }
    }

    /**
     * 检查指定用户是否有库存预警权限
     * 
     * @param userId 用户ID
     * @return true-有权限，false-无权限
     */
    private static boolean hasStockAlertPermissionForUser(Long userId) {
        try {
            // 获取用户的角色配置
            List<UserBusiness> userRoleList = instance.userBusinessService.getBasicData(userId.toString(), "UserRole");
            if (userRoleList == null || userRoleList.isEmpty()) {
                logger.debug("用户{}没有角色配置", userId);
                return false;
            }

            // 解析角色ID列表
            String roleValue = userRoleList.get(0).getValue();
            if (roleValue == null || roleValue.isEmpty()) {
                logger.debug("用户{}的角色值为空", userId);
                return false;
            }

            logger.debug("用户{}的角色值：{}", userId, roleValue);

            // 从角色值中提取角色ID（格式如：[1][2][3]）
            String[] roleArray = roleValue.replace("][", ",").replace("[", "").replace("]", "").split(",");

            // 检查每个角色是否有库存预警功能权限
            for (String roleIdStr : roleArray) {
                if (roleIdStr.trim().isEmpty())
                    continue;
                try {
                    Long roleId = Long.parseLong(roleIdStr.trim());
                    logger.debug("检查角色{}是否有库存预警权限", roleId);
                    if (hasStockAlertPermissionForRole(roleId)) {
                        logger.debug("角色{}有库存预警权限", roleId);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("解析角色ID失败：{}", roleIdStr);
                }
            }

            logger.debug("用户{}的所有角色都没有库存预警权限", userId);
            return false;

        } catch (Exception e) {
            logger.error("检查用户{}的库存预警权限时发生异常", userId, e);
            return false;
        }
    }

    /**
     * 检查指定角色是否有库存预警权限
     * 
     * @param roleId 角色ID
     * @return true-有权限，false-无权限
     */
    private static boolean hasStockAlertPermissionForRole(Long roleId) {
        try {
            // 获取角色的功能权限配置
            Long roleFunctionId = instance.userBusinessService.checkIsValueExist("RoleFunctions", roleId.toString());
            if (roleFunctionId == null) {
                logger.debug("角色{}没有功能权限配置", roleId);
                return false;
            }

            // 根据ID获取完整的UserBusiness对象
            UserBusiness roleFunctions = instance.userBusinessService.getUserBusiness(roleFunctionId);
            if (roleFunctions == null) {
                logger.debug("角色{}的功能权限对象不存在", roleId);
                return false;
            }

            // 检查功能权限字符串中是否包含库存预警功能
            String functionValues = roleFunctions.getValue();
            if (functionValues == null || functionValues.isEmpty()) {
                logger.debug("角色{}的功能权限值为空", roleId);
                return false;
            }

            logger.debug("角色{}的功能权限值：{}", roleId, functionValues);

            // 获取库存预警功能的ID
            Long stockAlertFunctionId = getFunctionIdByNumber(STOCK_ALERT_FUNCTION_NUMBER);
            if (stockAlertFunctionId == null) {
                logger.warn("未找到库存预警功能记录，功能编号：{}", STOCK_ALERT_FUNCTION_NUMBER);
                return false;
            }

            logger.debug("库存预警功能ID：{}", stockAlertFunctionId);

            // 检查功能ID是否在权限列表中
            String functionIdStr = "[" + stockAlertFunctionId + "]";
            boolean hasPermission = functionValues.contains(functionIdStr);

            logger.debug("角色{}检查功能权限{}：{}", roleId, functionIdStr, hasPermission ? "有权限" : "无权限");

            return hasPermission;

        } catch (Exception e) {
            logger.error("检查角色{}的库存预警权限时发生异常", roleId, e);
            return false;
        }
    }

    /**
     * 根据功能编号获取功能ID
     * 
     * @param functionNumber 功能编号
     * @return 功能ID，如果未找到则返回null
     */
    private static Long getFunctionIdByNumber(String functionNumber) {
        try {
            logger.debug("查询功能编号{}对应的功能ID", functionNumber);

            // 通过FunctionService查询数据库
            List<Function> functions = instance.functionService.getFunction();

            if (functions != null && !functions.isEmpty()) {
                for (Function function : functions) {
                    if (functionNumber.equals(function.getNumber()) && !"1".equals(function.getDeleteFlag())) {
                        logger.debug("找到功能记录：编号={}, ID={}, 名称={}", function.getNumber(), function.getId(),
                                function.getName());
                        return function.getId();
                    }
                }
            }

            logger.warn("未找到功能编号{}对应的功能记录", functionNumber);
            return null;

        } catch (Exception e) {
            logger.error("根据功能编号{}查询功能ID时发生异常", functionNumber, e);
            return null;
        }
    }
}