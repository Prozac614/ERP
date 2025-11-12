package com.jsh.erp.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务期间工具类
 * 业务期间定义：
 * - 本期：2月1日 - 7月31日
 * - 上期：8月1日 - 次年1月31日
 */
public class PeriodUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取当前期间的时间范围
     * 
     * @return [开始日期, 结束日期] 格式：yyyy-MM-dd
     */
    public static String[] getCurrentPeriod() {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        if (currentMonth >= 2 && currentMonth <= 7) {
            // 当前在2-7月，本期就是2-7月
            return new String[] {
                    String.format("%d-02-01", currentYear),
                    String.format("%d-07-31", currentYear)
            };
        } else if (currentMonth >= 8) {
            // 当前在8-12月，本期是8月到明年1月
            return new String[] {
                    String.format("%d-08-01", currentYear),
                    String.format("%d-01-31", currentYear + 1)
            };
        } else {
            // 当前在1月，本期是去年8月到今年1月
            return new String[] {
                    String.format("%d-08-01", currentYear - 1),
                    String.format("%d-01-31", currentYear)
            };
        }
    }

    /**
     * 获取上期的时间范围
     * 
     * @return [开始日期, 结束日期] 格式：yyyy-MM-dd
     */
    public static String[] getPreviousPeriod() {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        if (currentMonth >= 2 && currentMonth <= 7) {
            // 当前在2-7月，上期是去年8月到今年1月
            return new String[] {
                    String.format("%d-08-01", currentYear - 1),
                    String.format("%d-01-31", currentYear)
            };
        } else if (currentMonth >= 8) {
            // 当前在8-12月，上期是今年2-7月
            return new String[] {
                    String.format("%d-02-01", currentYear),
                    String.format("%d-07-31", currentYear)
            };
        } else {
            // 当前在1月，上期是去年2-7月
            return new String[] {
                    String.format("%d-02-01", currentYear - 1),
                    String.format("%d-07-31", currentYear - 1)
            };
        }
    }

    /**
     * 获取本期到当前时间的范围（用于计算本期数据）
     * 
     * @return [开始日期, 当前日期] 格式：yyyy-MM-dd
     */
    public static String[] getCurrentPeriodToNow() {
        String[] currentPeriod = getCurrentPeriod();
        LocalDate now = LocalDate.now();

        return new String[] {
                currentPeriod[0],
                now.format(DATE_FORMATTER)
        };
    }
}