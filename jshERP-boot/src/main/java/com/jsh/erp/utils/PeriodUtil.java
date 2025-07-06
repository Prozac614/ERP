package com.jsh.erp.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PeriodUtil {
    
    /**
     * 获取当前期间的开始和结束时间
     */
    public static String[] getCurrentPeriod() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        
        if (month <= 6) {
            // 上半年期
            return new String[]{
                year + "-01-01 00:00:00",
                year + "-06-30 23:59:59"
            };
        } else {
            // 下半年期
            return new String[]{
                year + "-07-01 00:00:00",
                year + "-12-31 23:59:59"
            };
        }
    }
    
    /**
     * 获取上一期间的开始和结束时间
     */
    public static String[] getPreviousPeriod() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        
        if (month <= 6) {
            // 当前是上半年，上期是去年下半年
            return new String[]{
                (year - 1) + "-07-01 00:00:00",
                (year - 1) + "-12-31 23:59:59"
            };
        } else {
            // 当前是下半年，上期是今年上半年
            return new String[]{
                year + "-01-01 00:00:00",
                year + "-06-30 23:59:59"
            };
        }
    }
    
    /**
     * 根据指定日期获取其所在期间
     */
    public static String[] getPeriodByDate(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        
        if (month <= 6) {
            return new String[]{
                year + "-01-01 00:00:00",
                year + "-06-30 23:59:59"
            };
        } else {
            return new String[]{
                year + "-07-01 00:00:00",
                year + "-12-31 23:59:59"
            };
        }
    }
} 