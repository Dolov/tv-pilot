package com.dsh.tvhud.report;

import com.dsh.tvhud.usage.UsageDay;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 报告粒度与周期边界。周 = 周一…周日；月 = 自然月；年 = 自然年；全部本地时区。
 * 只做「日界」运算（毫秒时间戳是当天 0 点），中国无夏令时，天与天之间按 24h 平移。
 */
public enum ReportPeriod {
    WEEK, MONTH, YEAR;

    private static final long DAY_MS = UsageDay.DAY_MS;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy.MM.dd", Locale.US);

    public static long startOf(ReportPeriod p, long day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        switch (p) {
            case WEEK: {
                int dow = c.get(Calendar.DAY_OF_WEEK);   // Sunday=1
                int offset = (dow + 5) % 7;              // Monday=0
                c.add(Calendar.DAY_OF_YEAR, -offset);
                break;
            }
            case MONTH:
                c.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case YEAR:
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.MONTH, Calendar.JANUARY);
                break;
        }
        return c.getTimeInMillis();
    }

    public static long endOf(ReportPeriod p, long start) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        switch (p) {
            case WEEK:
                c.add(Calendar.DAY_OF_YEAR, 6);
                break;
            case MONTH:
                c.add(Calendar.MONTH, 1);
                c.add(Calendar.DAY_OF_YEAR, -1);
                break;
            case YEAR:
                c.set(Calendar.MONTH, Calendar.DECEMBER);
                c.set(Calendar.DAY_OF_MONTH, 31);
                break;
        }
        return c.getTimeInMillis();
    }

    public static long shift(ReportPeriod p, long start, int n) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        switch (p) {
            case WEEK:
                c.add(Calendar.DAY_OF_YEAR, n * 7);
                break;
            case MONTH:
                c.add(Calendar.MONTH, n);
                break;
            case YEAR:
                c.add(Calendar.YEAR, n);
                break;
        }
        return c.getTimeInMillis();
    }

    /** ISO-8601 周序号：周一为首日，含 1 月 4 日的那周为第 1 周。 */
    public static int weekOfYear(long day) {
        // 本周周四所属的那一年就是 ISO 归属年，周四的年内第几天决定周序号。
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOf(WEEK, day));
        c.add(Calendar.DAY_OF_YEAR, 3);
        return (c.get(Calendar.DAY_OF_YEAR) - 1) / 7 + 1;
    }

    /** ISO 周所属年份（跨年周按周四所在年算，和 weekOfYear 配套）。 */
    public static int weekYear(long day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOf(WEEK, day));
        c.add(Calendar.DAY_OF_YEAR, 3);
        return c.get(Calendar.YEAR);
    }

    /**
     * 具体周期名：第 45 周 / 11 月 / 2025 年。
     * 和当前周期不在同一年时补上归属年份（如 2024 年 11 月），避免「11 月」看不出是哪一年。
     *
     * @param todayStart 今天 0 点，只用来确定「当前周期」的年份基准
     */
    public static String label(ReportPeriod p, long start, long todayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        Calendar cur = Calendar.getInstance();
        cur.setTimeInMillis(startOf(p, todayStart));
        switch (p) {
            case WEEK: {
                int week = weekOfYear(start);
                int wy = weekYear(start);
                return wy == weekYear(cur.getTimeInMillis()) ? "第 " + week + " 周"
                        : wy + " 第 " + week + " 周";
            }
            case MONTH: {
                int m = c.get(Calendar.MONTH) + 1;
                int y = c.get(Calendar.YEAR);
                return y == cur.get(Calendar.YEAR) ? m + " 月" : y + " 年 " + m + " 月";
            }
            default:
                return c.get(Calendar.YEAR) + " 年";
        }
    }

    /** 区间文案，如 2026.11.02 – 11.06（只到日，年只出现一次）。 */
    public static String range(long start, long viewEnd) {
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(viewEnd);
        return DATE_FMT.format(new Date(start)) + " – "
                + pad(e.get(Calendar.MONTH) + 1) + "." + pad(e.get(Calendar.DAY_OF_MONTH));
    }

    public static long earliestDay(long today) {
        return today - (UsageDay.KEEP_DAYS - 1L) * DAY_MS;
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
