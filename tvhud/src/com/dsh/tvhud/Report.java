package com.dsh.tvhud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 一次报告渲染所需的全部数据。视图只读，不再碰文件/服务。
 */
public final class Report {
    public ReportPeriod period;
    /** 选中周期的第一天 0 点。 */
    public long startDay;
    /**
     * 实际有保留数据的第一天 0 点：通常是 startDay；最早那一期可能被 365 天窗口
     * 截断（如 2025 年报只剩 11/07 起），此时它晚于 startDay。
     * 日均 / 覆盖率按它算，避免把「窗口外不可能有数据的天」当成没看。
     */
    public long coverageStartDay;
    /** 实际统计到的最后一天（本期 = 今天，往期 = 周期最后一天）。 */
    public long viewEndDay;
    public long todayStart;
    public boolean current;

    /** 选中区间内逐日汇总（含无记录日，recorded=false）。 */
    public final ArrayList<DayAgg> days = new ArrayList<>();
    /** 上一同粒度周期逐日（年报告为空）。 */
    public final ArrayList<DayAgg> prevDays = new ArrayList<>();
    public boolean hasPrev;

    public long totalMs;
    public long prevTotalMs;
    public final long[] catMs = new long[5];
    public final long[] hourMs = new long[24];
    public int elapsedDays;
    public int recordedDays;
    public int staleSchemeDays;
    public DayAgg maxDay;

    public List<TopPlay> topPlays = new ArrayList<>();
    /** 每天最后看的那一条片名（日流水用；不受全局 Top5 截断影响）。 */
    public final HashMap<Long, String> dayPlayName = new HashMap<>();
    public List<PeriodStat> ribbon = new ArrayList<>();
    public String insight = "";

    public static final class TopPlay {
        public String name = "";
        public int episodes;
        public long lastMs;
    }

    public static final class PeriodStat {
        public long startDay;
        public long totalMs;
        public String label = "";
        public boolean selected;
        public boolean current;
        public boolean outOfRange;
    }

    public long avgMs() {
        return elapsedDays <= 0 ? 0L : totalMs / elapsedDays;
    }

    public long prevAvgMs() {
        if (prevDays.isEmpty()) {
            return 0L;
        }
        return prevTotalMs / prevDays.size();
    }

    /** 这一期是否被 365 天保留窗口截断（起始日比周期首日晚）。 */
    public boolean isPartialCoverage() {
        return coverageStartDay > startDay;
    }

    /** 上期数据覆盖 >= 80% 才允许环比，避免拿残缺历史算同比。 */
    public boolean prevComparable() {
        if (!hasPrev || period == ReportPeriod.YEAR || prevDays.isEmpty()) {
            return false;
        }
        int recorded = 0;
        for (int i = 0; i < prevDays.size(); i++) {
            if (prevDays.get(i).recorded) {
                recorded++;
            }
        }
        return recorded * 5 >= prevDays.size() * 4;
    }

    public long peakHourMs() {
        long best = 0L;
        for (int i = 0; i < hourMs.length; i++) {
            if (hourMs[i] > best) {
                best = hourMs[i];
            }
        }
        return best;
    }
}
