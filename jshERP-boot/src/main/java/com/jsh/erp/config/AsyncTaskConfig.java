package com.jsh.erp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 用于处理需要保持请求上下文的异步任务
 * 
 * @author jishenghua
 */
@Configuration
@EnableAsync
public class AsyncTaskConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncTaskConfig.class);
    
    /**
     * 创建异步任务执行器
     * 支持传递请求上下文到异步线程
     */
    @Bean("stockWarningTaskExecutor")
    public Executor stockWarningTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                // 获取当前请求上下文
                RequestAttributes requestAttributes = null;
                try {
                    requestAttributes = RequestContextHolder.currentRequestAttributes();
                } catch (IllegalStateException e) {
                    // 如果没有请求上下文，直接执行任务
                    logger.debug("没有找到请求上下文，直接执行异步任务");
                }
                
                final RequestAttributes finalRequestAttributes = requestAttributes;
                
                // 包装任务，在异步线程中设置请求上下文
                Runnable wrappedTask = () -> {
                    if (finalRequestAttributes != null) {
                        try {
                            RequestContextHolder.setRequestAttributes(finalRequestAttributes);
                            task.run();
                        } finally {
                            RequestContextHolder.resetRequestAttributes();
                        }
                    } else {
                        task.run();
                    }
                };
                
                super.execute(wrappedTask);
            }
        };
        
        // 配置线程池参数
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("StockWarning-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
    
    /**
     * 通用的异步任务执行器
     */
    @Bean("contextAwareTaskExecutor")
    public Executor contextAwareTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                // 获取当前请求上下文
                RequestAttributes requestAttributes = null;
                try {
                    requestAttributes = RequestContextHolder.currentRequestAttributes();
                } catch (IllegalStateException e) {
                    // 如果没有请求上下文，记录警告但继续执行
                    logger.warn("异步任务执行时没有找到请求上下文，可能会影响租户隔离");
                }
                
                final RequestAttributes finalRequestAttributes = requestAttributes;
                
                // 包装任务
                Runnable wrappedTask = () -> {
                    if (finalRequestAttributes != null) {
                        try {
                            RequestContextHolder.setRequestAttributes(finalRequestAttributes);
                            logger.debug("异步任务开始执行，已设置请求上下文");
                            task.run();
                        } catch (Exception e) {
                            logger.error("异步任务执行失败", e);
                            throw e;
                        } finally {
                            RequestContextHolder.resetRequestAttributes();
                            logger.debug("异步任务执行完成，已清理请求上下文");
                        }
                    } else {
                        logger.warn("异步任务在没有请求上下文的情况下执行");
                        task.run();
                    }
                };
                
                super.execute(wrappedTask);
            }
        };
        
        // 配置线程池参数
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ContextAware-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
