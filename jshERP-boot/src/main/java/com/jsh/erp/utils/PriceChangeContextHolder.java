package com.jsh.erp.utils;

import com.jsh.erp.datasource.vo.PriceChangeContext;

/**
 * 价格变更上下文持有者
 * 使用ThreadLocal在同一线程中传递价格变更的上下文信息
 */
public class PriceChangeContextHolder {
    
    private static final ThreadLocal<PriceChangeContext> contextHolder = new ThreadLocal<>();
    
    /**
     * 设置当前线程的价格变更上下文
     * @param context 上下文信息
     */
    public static void setContext(PriceChangeContext context) {
        contextHolder.set(context);
    }
    
    /**
     * 获取当前线程的价格变更上下文
     * @return 上下文信息，如果没有设置则返回null
     */
    public static PriceChangeContext getContext() {
        return contextHolder.get();
    }
    
    /**
     * 清除当前线程的价格变更上下文
     * 使用完毕后务必调用此方法，避免内存泄漏
     */
    public static void clearContext() {
        contextHolder.remove();
    }
}

