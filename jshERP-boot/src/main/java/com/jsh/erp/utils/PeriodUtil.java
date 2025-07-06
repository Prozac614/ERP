package com.jsh.erp.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PeriodUtil {
    
    /**
     * 获取当前期间的开始和结束时间
     * 期间定义：2月1日-7月31日为一期，8月1日-次年1月31日为一期
     */
    public static String[] getCurrentPeriod() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        
        if (month >= 2 && month <= 7) {
            // 2-7月期间
            return new String[]{
                year + "-02-01 00:00:00",
                year + "-07-31 23:59:59"
            };
        } else {
            // 8-1月期间（跨年）
            if (month >= 8) {
                // 8-12月，期间是当年8月1日到次年1月31日
                return new String[]{
                    year + "-08-01 00:00:00",
                    (year + 1) + "-01-31 23:59:59"
                };
            } else {
                // 1月，期间是上年8月1日到当年1月31日
                return new String[]{
                    (year - 1) + "-08-01 00:00:00",
                    year + "-01-31 23:59:59"
                };
            }
        }
    }
    
    /**
     * 获取上一期间的开始和结束时间
     */
    public static String[] getPreviousPeriod() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        
        if (month >= 2 && month <= 7) {
            // 当前是2-7月期间，上期是上年8月1日到当年1月31日
            return new String[]{
                (year - 1) + "-08-01 00:00:00",
                year + "-01-31 23:59:59"
            };
        } else {
            // 当前是8-1月期间，上期是2-7月期间
            if (month >= 8) {
                // 当前8-12月，上期是当年2-7月
                return new String[]{
                    year + "-02-01 00:00:00",
                    year + "-07-31 23:59:59"
                };
            } else {
                // 当前1月，上期是上年2-7月
                return new String[]{
                    (year - 1) + "-02-01 00:00:00",
                    (year - 1) + "-07-31 23:59:59"
                };
            }
        }
    }
    
    /**
     * 根据指定日期获取其所在期间
     */
    public static String[] getPeriodByDate(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        
        if (month >= 2 && month <= 7) {
            // 2-7月期间
            return new String[]{
                year + "-02-01 00:00:00",
                year + "-07-31 23:59:59"
            };
        } else {
            // 8-1月期间（跨年）
            if (month >= 8) {
                // 8-12月，期间是当年8月1日到次年1月31日
                return new String[]{
                    year + "-08-01 00:00:00",
                    (year + 1) + "-01-31 23:59:59"
                };
            } else {
                // 1月，期间是上年8月1日到当年1月31日
                return new String[]{
                    (year - 1) + "-08-01 00:00:00",
                    year + "-01-31 23:59:59"
                };
            }
        }
    }
} 