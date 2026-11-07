package com.dsh.tvhud.report;

import com.dsh.tvhud.usage.UsageDay;

/**
 * 单日汇总。报告只需要「每天多少、分类构成、小时桶、起止、主类别」，
 * 不再把原始分段带到 UI 层——365 天一人 5+24 个数，内存可忽略。
 */
public final class DayAgg {
    public final long dayStart;
    public long totalMs;
    public final long[] catMs = new long[5];
    public final long[] hourMs = new long[24];
    public long firstMs;
    public long lastMs;
    public int mainCat = -1;
    public int scheme = UsageDay.SCHEME;
    /** 当天有没有存档；没有存档不一定代表没看（见报告口径）。 */
    public boolean recorded;

    public DayAgg(long dayStart) {
        this.dayStart = dayStart;
    }

    /** 把一段使用时间并入当天；跨小时按小时切分。 */
    public void add(long start, long end, int cat) {
        long from = Math.max(start, dayStart);
        long to = Math.min(end, dayStart + UsageDay.DAY_MS);
        if (to <= from) {
            return;
        }
        long dur = to - from;
        totalMs += dur;
        if (cat >= 0 && cat < catMs.length) {
            catMs[cat] += dur;
        }
        if (firstMs == 0L || from < firstMs) {
            firstMs = from;
        }
        if (to > lastMs) {
            lastMs = to;
        }
        long cursor = from;
        while (cursor < to) {
            long hour = (cursor - dayStart) / 3600000L;
            if (hour < 0) {
                hour = 0;
            }
            if (hour > 23) {
                hour = 23;
            }
            long hourEnd = dayStart + (hour + 1) * 3600000L;
            long segEnd = Math.min(to, hourEnd);
            hourMs[(int) hour] += segEnd - cursor;
            cursor = segEnd;
        }
        recorded = true;
    }

    /** 分类汇总完成后调用：找出时长最大的类别。 */
    public void finish() {
        long best = -1;
        for (int i = 0; i < catMs.length; i++) {
            if (catMs[i] > best) {
                best = catMs[i];
                mainCat = i;
            }
        }
        if (best <= 0) {
            mainCat = -1;
        }
    }

    public boolean hasUsage() {
        return totalMs > 0;
    }
}
