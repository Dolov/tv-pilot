package com.dsh.tvhud;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 报告用纯计算：分段 → 日汇总、汇总 → KPI/洞察、百分比、格式化。
 * 不 import Android，方便单独跑逻辑测试。
 */
public final class ReportMath {
    private static final long DAY_MS = UsageDay.DAY_MS;
    static final String[] WEEK_CN = {"一", "二", "三", "四", "五", "六", "日"};
    private static final SimpleDateFormat MD = new SimpleDateFormat("MM/dd", Locale.US);
    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.US);

    private ReportMath() {
    }

    public static DayAgg aggregate(long dayStart, List<UsageDay.Seg> segs, boolean archived,
                                   int scheme) {
        DayAgg d = new DayAgg(dayStart);
        d.scheme = scheme;
        d.recorded = archived;
        if (segs != null) {
            for (int i = 0; i < segs.size(); i++) {
                UsageDay.Seg s = segs.get(i);
                if (scheme == UsageDay.SCHEME) {
                    d.add(s.start, s.end, s.cat);
                } else {
                    // 旧分类口径：只计总时长，不把颜色画错
                    long from = Math.max(s.start, dayStart);
                    long to = Math.min(s.end, dayStart + DAY_MS);
                    if (to > from) {
                        d.totalMs += to - from;
                        d.recorded = true;
                    }
                }
            }
        }
        d.finish();
        return d;
    }

    public static void accumulate(Report r, DayAgg d) {
        for (int i = 0; i < 5; i++) {
            r.catMs[i] += d.catMs[i];
        }
        for (int i = 0; i < 24; i++) {
            r.hourMs[i] += d.hourMs[i];
        }
        r.totalMs += d.totalMs;
        if (d.recorded || d.totalMs > 0) {
            r.recordedDays++;
        }
        if (d.scheme != UsageDay.SCHEME && d.totalMs > 0) {
            r.staleSchemeDays++;
        }
        if (r.maxDay == null || d.totalMs > r.maxDay.totalMs) {
            r.maxDay = d;
        }
    }

    /** 最大余数法：保证加起来正好 100。 */
    public static int[] percentages(long[] parts) {
        long total = 0L;
        for (int i = 0; i < parts.length; i++) {
            total += Math.max(0L, parts[i]);
        }
        int[] out = new int[parts.length];
        if (total <= 0) {
            return out;
        }
        double[] raw = new double[parts.length];
        int used = 0;
        for (int i = 0; i < parts.length; i++) {
            raw[i] = Math.max(0L, parts[i]) * 100.0 / total;
            out[i] = (int) Math.floor(raw[i]);
            used += out[i];
        }
        int rest = 100 - used;
        while (rest > 0) {
            int best = -1;
            double bestFrac = -1;
            for (int i = 0; i < parts.length; i++) {
                if (out[i] < raw[i]) {
                    double frac = raw[i] - out[i];
                    if (frac > bestFrac) {
                        bestFrac = frac;
                        best = i;
                    }
                }
            }
            if (best < 0) {
                break;
            }
            out[best]++;
            rest--;
        }
        return out;
    }

    public static int hours(long ms) {
        return (int) (Math.max(0L, ms) / 3600000L);
    }

    public static int minutes(long ms) {
        return (int) ((Math.max(0L, ms) / 60000L) % 60L);
    }

    /** H:MM（时长，不补小时前导 0）。 */
    public static String hhmm(long ms) {
        return hours(ms) + ":" + pad(minutes(ms));
    }

    /** HH:mm 时刻。 */
    public static String clock(long wallMs) {
        return HM.format(new Date(wallMs));
    }

    public static String dateMd(long dayStart) {
        return MD.format(new Date(dayStart));
    }

    public static String weekdayCn(int mondayIndex) {
        return "周" + WEEK_CN[((mondayIndex % 7) + 7) % 7];
    }

    /** 0=周一 … 6=周日。 */
    public static int mondayIndex(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** 2 小时峰值的起始整点。 */
    public static int peakWindowStart(long[] hourMs) {
        int best = 0;
        long bestV = -1;
        for (int h = 0; h < 23; h++) {
            long v = hourMs[h] + hourMs[h + 1];
            if (v > bestV) {
                bestV = v;
                best = h;
            }
        }
        return best;
    }

    public static String peakPhrase(long[] hourMs) {
        int h = peakWindowStart(hourMs);
        return h + "–" + (h + 2) + " 点";
    }

    /**
     * 报告内部一致性自检：返回第一条不满足的说明，全部通过返回 null。
     * 单元测试直接断言；真机 E2E 会 grep logcat 里的 REPORT_INVARIANT 告警。
     */
    public static String checkInvariants(Report r) {
        if (r == null) {
            return "report == null";
        }
        if (r.current && r.viewEndDay != r.todayStart) {
            return "current 报告 viewEndDay != todayStart";
        }
        if (r.viewEndDay < r.startDay) {
            return "viewEndDay < startDay";
        }
        if (r.coverageStartDay < r.startDay) {
            return "coverageStartDay < startDay";
        }
        if (r.coverageStartDay > r.viewEndDay) {
            return "coverageStartDay > viewEndDay";
        }
        if (r.isPartialCoverage() && r.elapsedDays !=
                (int) ((r.viewEndDay - r.coverageStartDay) / UsageDay.DAY_MS) + 1) {
            return "elapsedDays 与覆盖区间不一致";
        }
        long total = 0L;
        long staleTotal = 0L;
        for (int k = 0; k < r.days.size(); k++) {
            DayAgg d = r.days.get(k);
            if (d.totalMs < 0L) {
                return "day totalMs < 0";
            }
            long cat = 0L;
            for (int i = 0; i < 5; i++) {
                if (d.catMs[i] < 0L) {
                    return "day catMs < 0";
                }
                cat += d.catMs[i];
            }
            long hour = 0L;
            for (int i = 0; i < 24; i++) {
                if (d.hourMs[i] < 0L) {
                    return "day hourMs < 0";
                }
                hour += d.hourMs[i];
            }
            if (d.scheme == UsageDay.SCHEME) {
                if (cat != d.totalMs) {
                    return "day catMs 之和 != totalMs";
                }
                if (hour != d.totalMs) {
                    return "day hourMs 之和 != totalMs";
                }
            } else {
                // 旧分类口径的天只计总时长，不允许进分类/小时桶
                staleTotal += d.totalMs;
                if (cat != 0L || hour != 0L) {
                    return "旧口径天仍有分类/小时桶";
                }
            }
            total += d.totalMs;
        }
        if (total != r.totalMs) {
            return "r.totalMs != 每天之和";
        }
        long catSum = 0L;
        for (int i = 0; i < 5; i++) {
            catSum += r.catMs[i];
        }
        if (catSum != r.totalMs - staleTotal) {
            return "r.catMs 之和 != totalMs - 旧口径天";
        }
        long hourSum = 0L;
        for (int i = 0; i < 24; i++) {
            hourSum += r.hourMs[i];
        }
        if (hourSum != r.totalMs - staleTotal) {
            return "r.hourMs 之和 != totalMs - 旧口径天";
        }
        if (r.maxDay != null && r.maxDay.totalMs > r.totalMs) {
            return "maxDay 大于 totalMs";
        }
        return null;
    }

    /** 周一=0…周日=6 的每日总时长合计；只统计有记录（totalMs &gt; 0）的天。 */
    public static long[] weekdaySumMs(List<DayAgg> days) {
        long[] sum = new long[7];
        if (days == null) {
            return sum;
        }
        for (int i = 0; i < days.size(); i++) {
            DayAgg d = days.get(i);
            if (d == null || d.totalMs <= 0) {
                continue;
            }
            sum[mondayIndex(d.dayStart)] += d.totalMs;
        }
        return sum;
    }

    /** 与 {@link #weekdaySumMs} 配套：每个周几各有多少个「有记录日」。 */
    public static int[] weekdayCount(List<DayAgg> days) {
        int[] count = new int[7];
        if (days == null) {
            return count;
        }
        for (int i = 0; i < days.size(); i++) {
            DayAgg d = days.get(i);
            if (d == null || d.totalMs <= 0) {
                continue;
            }
            count[mondayIndex(d.dayStart)]++;
        }
        return count;
    }

    /** 各周几的日均时长（毫秒）；没有记录的那天不参与分母。 */
    public static long[] weekdayAverageMs(List<DayAgg> days) {
        long[] sum = weekdaySumMs(days);
        int[] count = weekdayCount(days);
        long[] avg = new long[7];
        for (int i = 0; i < 7; i++) {
            avg[i] = count[i] == 0 ? 0L : sum[i] / count[i];
        }
        return avg;
    }

    /**
     * 实际有保留数据的第一天：周期首日与 365 天窗口起点的较大者，且不晚于统计末日。
     * 最早那一期被窗口截断时（如 2025 年报只剩 11/07 起），日均/覆盖率必须从这里算。
     */
    public static long coverageStartDay(long periodStart, long viewEnd, long earliestDay) {
        long start = Math.max(periodStart, earliestDay);
        return Math.min(start, viewEnd);
    }

    /** 一句话洞察；空数据返回空串。 */
    public static String insight(Report r) {
        if (r.totalMs <= 0) {
            return "";
        }
        if (r.period == ReportPeriod.WEEK) {
            DayAgg hi = null;
            DayAgg lo = null;
            for (int i = 0; i < r.days.size(); i++) {
                DayAgg d = r.days.get(i);
                if (d.totalMs <= 0) {
                    continue;
                }
                if (hi == null || d.totalMs > hi.totalMs) {
                    hi = d;
                }
                // 今天还没过完，不参与「最短」比较，否则总是今天最短
                if (r.current && d.dayStart == r.todayStart) {
                    continue;
                }
                if (lo == null || d.totalMs < lo.totalMs) {
                    lo = d;
                }
            }
            if (hi == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(weekdayCn(mondayIndex(hi.dayStart))).append("最长（")
                    .append(hhmm(hi.totalMs)).append("）");
            if (lo != null && lo != hi) {
                sb.append("，").append(weekdayCn(mondayIndex(lo.dayStart))).append("最短（")
                        .append(hhmm(lo.totalMs)).append("）");
            }
            sb.append("；").append(peakPhrase(r.hourMs)).append("最集中。");
            return sb.toString();
        }
        if (r.period == ReportPeriod.MONTH) {
            // 与月报「周内平均」图共用同一份口径：只统计有记录的日子
            long[] weekdaySum = weekdaySumMs(r.days);
            int[] weekdayDays = weekdayCount(r.days);
            long we = weekdaySum[5] + weekdaySum[6];
            long wd = weekdaySum[0] + weekdaySum[1] + weekdaySum[2]
                    + weekdaySum[3] + weekdaySum[4];
            int weN = weekdayDays[5] + weekdayDays[6];
            int wdN = weekdayDays[0] + weekdayDays[1] + weekdayDays[2]
                    + weekdayDays[3] + weekdayDays[4];
            if (weN == 0 || wdN == 0) {
                return "本月有记录 " + r.recordedDays + " 天，日均 " + hhmm(r.avgMs())
                        + "；" + peakPhrase(r.hourMs) + "最集中。";
            }
            long weAvg = weN == 0 ? 0L : we / weN;
            long wdAvg = wdN == 0 ? 0L : wd / wdN;
            long diff = (weAvg - wdAvg) / 60000L;
            if (diff >= 0) {
                return "周末日均比工作日多 " + diff + " 分钟；" + peakPhrase(r.hourMs) + "最集中。";
            }
            return "周末日均比工作日少 " + (-diff) + " 分钟；" + peakPhrase(r.hourMs) + "最集中。";
        }
        // YEAR：按月聚合，比较「日均」而不是月总时长（2 月天然少几天）
        long[] monthTotal = new long[12];
        int[] monthDays = new int[12];
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < r.days.size(); i++) {
            DayAgg d = r.days.get(i);
            if (r.coverageStartDay > 0L && d.dayStart < r.coverageStartDay) {
                continue;   // 保留窗口外的天没有数据，也不该稀释月日均
            }
            c.setTimeInMillis(d.dayStart);
            int m = c.get(Calendar.MONTH);
            monthTotal[m] += d.totalMs;
            monthDays[m]++;
        }
        int hiM = -1, loM = -1;
        long hiAvg = -1L, loAvg = Long.MAX_VALUE;
        int activeMonths = 0;
        for (int m = 0; m < 12; m++) {
            // 没有记录用量的月份不参与同比：否则「1 月最低 0:00」只是没数据
            if (monthDays[m] <= 0 || monthTotal[m] <= 0) {
                continue;
            }
            activeMonths++;
            long avg = monthTotal[m] / monthDays[m];
            if (avg > hiAvg) {
                hiAvg = avg;
                hiM = m;
            }
            if (avg < loAvg) {
                loAvg = avg;
                loM = m;
            }
        }
        if (hiM < 0) {
            return "";
        }
        if (activeMonths == 1) {
            return (hiM + 1) + " 月有记录 " + r.recordedDays + " 天，日均 " + hhmm(hiAvg)
                    + "；" + peakPhrase(r.hourMs) + "最集中。";
        }
        return (hiM + 1) + " 月日均最高（" + hhmm(hiAvg) + "），" + (loM + 1)
                + " 月最低（" + hhmm(loAvg) + "）；" + peakPhrase(r.hourMs) + "最集中。";
    }
}
