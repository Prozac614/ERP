package com.jsh.erp.config;

import com.baomidou.mybatisplus.core.parser.ISqlParser;
import com.baomidou.mybatisplus.core.parser.ISqlParserFilter;
import com.baomidou.mybatisplus.core.parser.SqlParserHelper;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.extension.plugins.PerformanceInterceptor;
import com.baomidou.mybatisplus.extension.plugins.tenant.TenantHandler;
import com.baomidou.mybatisplus.extension.plugins.tenant.TenantSqlParser;
import com.jsh.erp.utils.Tools;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Service
public class TenantConfig {

    @Bean
    public PaginationInterceptor paginationInterceptor(HttpServletRequest request) {
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        List<ISqlParser> sqlParserList = new ArrayList<>();
        TenantSqlParser tenantSqlParser = new TenantSqlParser();
        tenantSqlParser.setTenantHandler(new TenantHandler() {
            @Override
            public Expression getTenantId() {
                try {
                    String token = request.getHeader("X-Access-Token");
                    Long tenantId = Tools.getTenantIdByToken(token);
                    if (tenantId!=0L) {
                        return new LongValue(tenantId);
                    } else {
                        //超管
                        return null;
                    }
                } catch (IllegalStateException e) {
                    // 在异步线程中无法获取请求上下文时，返回null（超管权限）
                    // 这样可以避免异步任务失败，但需要确保异步任务的安全性
                    return null;
                }
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean doTableFilter(String tableName) {
                //获取开启状态
                Boolean res = true;
                try {
                    String token = request.getHeader("X-Access-Token");
                    Long tenantId = Tools.getTenantIdByToken(token);
                    if (tenantId!=0L) {
                        // 这里可以判断是否过滤表
                        if ("jsh_sequence".equals(tableName) || "jsh_function".equals(tableName)
                                || "jsh_platform_config".equals(tableName) || "jsh_tenant".equals(tableName)) {
                            res = true;
                        } else {
                            res = false;
                        }
                    }
                } catch (IllegalStateException e) {
                    // 在异步线程中无法获取请求上下文时，不过滤表（超管权限）
                    res = true;
                }
                return res;
            }
        });

        sqlParserList.add(tenantSqlParser);
        paginationInterceptor.setSqlParserList(sqlParserList);
        paginationInterceptor.setSqlParserFilter(new ISqlParserFilter() {
            @Override
            public boolean doFilter(MetaObject metaObject) {
                MappedStatement ms = SqlParserHelper.getMappedStatement(metaObject);
                // 过滤自定义查询此时无租户信息约束出现
                if ("com.jsh.erp.datasource.mappers.UserMapperEx.getUserByWeixinOpenId".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.UserMapperEx.updateUserWithWeixinOpenId".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.UserMapperEx.getUserListByUserNameOrLoginName".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.UserMapperEx.disableUserByLimit".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.RoleMapperEx.getRoleWithoutTenant".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.LogMapperEx.insertLogWithUserId".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.UserBusinessMapperEx.getBasicDataByKeyIdAndType".equals(ms.getId())) {
                    return true;
                } else if ("com.jsh.erp.datasource.mappers.DepotItemMapperEx.refreshDailySummaryForRecentDays".equals(ms.getId())) {
                    return true; // 排除复杂的INSERT...SELECT...ON DUPLICATE KEY UPDATE语句
                } else if ("com.jsh.erp.datasource.mappers.DepotItemMapperEx.updateDailyOutSummary".equals(ms.getId())) {
                    return true; // 排除存储过程调用
                } else if ("com.jsh.erp.datasource.mappers.DepotItemMapperEx.refreshMaterialPeriodSummary".equals(ms.getId())) {
                    return true; // 排除存储过程调用
                } else if ("com.jsh.erp.datasource.mappers.MaterialMapperEx.getDirectDailyOutStock".equals(ms.getId())) {
                    return true; // 排除直接查询每日出库数据的方法
                } else if ("com.jsh.erp.datasource.mappers.MaterialMapperEx.getTotalOutQuantity".equals(ms.getId())) {
                    return true; // 排除简化查询总出库量的方法
                }
                return false;
            }
        });
        return paginationInterceptor;
    }

    /**
     * 相当于顶部的：
     * {@code @MapperScan("com.jsh.erp.datasource.mappers*")}
     * 这里可以扩展，比如使用配置文件来配置扫描Mapper的路径
     */
    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer scannerConfigurer = new MapperScannerConfigurer();
        scannerConfigurer.setBasePackage("com.jsh.erp.datasource.mappers*");
        return scannerConfigurer;
    }

    /**
     * 性能分析拦截器，不建议生产使用
     */
//    @Bean
//    public PerformanceInterceptor performanceInterceptor(){
//        return new PerformanceInterceptor();
//    }


}
