package com.dsh.tvhud.report;

import com.dsh.tvhud.hud.HudService;
import com.dsh.tvhud.usage.HistoryStore;
import com.dsh.tvhud.usage.PlayEntry;
import com.dsh.tvhud.usage.UsageDay;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告数据装配：一次扫描窗口内所有日，派生选中周期 / 上期 / 周期条 / Top 片单。
 * 只在后台线程调用；带一个进程内 LRU，翻回看过的周期不重复读盘。
 */
final class ReportEngine {
    private static final String TAG = "TVPILOT";
    private static final int RIBBON_WEEKS = 8;
    private static final int RIBBON_MONTHS = 7;
    private static final int RIBBON_YEARS = 3;

    private static final Map<String, Report> CACHE =
            new LinkedHashMap<String, Report>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Report> eldest) {
                    return size() > 6;
                }
            };

    private ReportEngine() {
    }

    static Report load(Context ctx, ReportPeriod period, long anchorStart) {
        long begin = System.currentTimeMillis();
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        long currentStart = ReportPeriod.startOf(period, today);
        long earliest = ReportPeriod.earliestDay(today);

        long start = ReportPeriod.startOf(period, anchorStart);
        if (start > currentStart) {
            start = currentStart;
        }
        if (ReportPeriod.endOf(period, start) < earliest) {
            start = ReportPeriod.startOf(period, earliest);
        }
        if (start < earliest) {
            start = ReportPeriod.startOf(period, earliest);
        }

        String key = period.name() + "@" + start + "@" + today
                + "@" + (System.currentTimeMillis() / 60000L);
        synchronized (CACHE) {
            Report hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
        }

        long viewEnd = Math.min(ReportPeriod.endOf(period, start), today);
        // 最早那一期可能被 365 天窗口截断：日均/覆盖率只算窗口内的天，
        // 否则「2025 年报」会把窗口外的 310 天当成没看电视，日均被稀释。
        long coverageStart = ReportMath.coverageStartDay(start, viewEnd, earliest);
        int elapsed = (int) ((viewEnd - coverageStart) / UsageDay.DAY_MS) + 1;

        long prevStart = ReportPeriod.shift(period, start, -1);
        long prevEnd = ReportPeriod.endOf(period, prevStart);
        boolean hasPrev = period != ReportPeriod.YEAR && prevEnd >= earliest;

        int ribbonCount = period == ReportPeriod.WEEK ? RIBBON_WEEKS
                : period == ReportPeriod.MONTH ? RIBBON_MONTHS : RIBBON_YEARS;
        // 周期条的窗口跟着「当前查看」走：看很久以前的周期时，选中项也在条上
        long ribbonEnd = start == currentStart ? currentStart : start;
        long ribbonStart = ReportPeriod.shift(period, ribbonEnd, -(ribbonCount - 1));

        long windowStart = start;
        if (hasPrev && prevStart < windowStart) {
            windowStart = prevStart;
        }
        if (ribbonStart < windowStart) {
            windowStart = ribbonStart;
        }
        if (windowStart < earliest) {
            windowStart = earliest;
        }

        Map<Long, DayAgg> dayMap = new HashMap<>();
        Map<Long, List<PlayEntry>> selectedPlays = new HashMap<>();

        // 从窗口起点扫到今天：selected / prev / ribbon 共用这一遍
        for (long d = windowStart; d <= today; d += UsageDay.DAY_MS) {
            DayAgg agg;
            List<PlayEntry> plays = null;
            if (d == today) {
                // 快照：后台线程不能直接遍历服务里那个每秒还在 append 的活对象
                UsageDay live = HudService.peekUsageSnapshot();
                boolean liveOk = live != null && live.dayStart == today;
                List<UsageDay.Seg> segs;
                HistoryStore.DayData archived = null;
                if (liveOk) {
                    segs = live.segments();
                } else {
                    UsageDay loaded = UsageDay.load(ctx, today);
                    segs = loaded != null && loaded.dayStart == today ? loaded.segments() : null;
                    if (segs == null) {
                        archived = HistoryStore.read(ctx, today);
                        if (archived != null) {
                            segs = archived.segs;
                        }
                    }
                }
                plays = HudService.peekPlays();
                if (plays == null || plays.isEmpty()) {
                    if (archived == null) {
                        archived = HistoryStore.read(ctx, today);
                    }
                    if (archived != null) {
                        plays = archived.plays;
                    }
                }
                agg = ReportMath.aggregate(today, segs, liveOk || segs != null, UsageDay.SCHEME);
            } else {
                HistoryStore.DayData data = HistoryStore.read(ctx, d);
                if (data != null) {
                    agg = ReportMath.aggregate(d, data.segs, true, data.scheme);
                    plays = data.plays;
                } else {
                    agg = new DayAgg(d);
                }
            }
            dayMap.put(d, agg);
            if (plays != null && !plays.isEmpty() && d >= start && d <= viewEnd) {
                selectedPlays.put(d, plays);
            }
        }

        Report r = new Report();
        r.period = period;
        r.startDay = start;
        r.coverageStartDay = coverageStart;
        r.viewEndDay = viewEnd;
        r.todayStart = today;
        r.current = start == currentStart;
        r.elapsedDays = elapsed;

        for (long d = start; d <= viewEnd; d += UsageDay.DAY_MS) {
            DayAgg dg = dayMap.get(d);
            if (dg == null) {
                dg = new DayAgg(d);
            }
            r.days.add(dg);
            ReportMath.accumulate(r, dg);
        }

        if (hasPrev) {
            for (long d = prevStart; d <= prevEnd; d += UsageDay.DAY_MS) {
                DayAgg dg = dayMap.get(d);
                if (dg == null) {
                    dg = new DayAgg(d);
                }
                r.prevDays.add(dg);
                r.prevTotalMs += dg.totalMs;
            }
        }
        r.hasPrev = hasPrev && !r.prevDays.isEmpty();

        // 周期导航条
        for (int i = 0; i < ribbonCount; i++) {
            long ps = ReportPeriod.shift(period, ribbonEnd, -(ribbonCount - 1) + i);
            long pe = Math.min(ReportPeriod.endOf(period, ps), today);
            Report.PeriodStat st = new Report.PeriodStat();
            st.startDay = ps;
            st.selected = ps == start;
            st.current = ps == currentStart;
            st.outOfRange = pe < earliest;
            long total = 0L;
            if (!st.outOfRange) {
                for (long d = ps; d <= pe; d += UsageDay.DAY_MS) {
                    DayAgg dg = dayMap.get(d);
                    if (dg != null) {
                        total += dg.totalMs;
                    }
                }
            }
            st.totalMs = total;
            st.label = period == ReportPeriod.WEEK ? ReportMath.dateMd(ps)
                    : period == ReportPeriod.MONTH ? (month(ps) + 1) + "月"
                    : String.valueOf(year(ps));
            r.ribbon.add(st);
        }

        r.topPlays = topPlays(selectedPlays);
        // 日流水每行只写一个片名：取当天最后看的那一集；不能复用全局 Top5（会被截断）
        for (Map.Entry<Long, List<PlayEntry>> e : selectedPlays.entrySet()) {
            PlayEntry last = null;
            for (int i = 0; i < e.getValue().size(); i++) {
                PlayEntry p = e.getValue().get(i);
                if (p == null || p.name == null || p.name.isEmpty()) {
                    continue;
                }
                if (last == null || p.ts >= last.ts) {
                    last = p;
                }
            }
            if (last != null) {
                r.dayPlayName.put(e.getKey(), last.name);
            }
        }
        r.insight = ReportMath.insight(r);
        String violated = ReportMath.checkInvariants(r);
        if (violated != null) {
            // 真机 E2E 会 grep 这行；出现说明聚合逻辑被改坏了
            Log.w(TAG, "REPORT_INVARIANT " + period + " " + ReportMath.dateMd(start)
                    + " " + violated);
        }

        synchronized (CACHE) {
            CACHE.put(key, r);
        }
        Log.i(TAG, "report load " + period + " " + ReportMath.dateMd(start) + " -> "
                + ReportMath.dateMd(viewEnd) + " days=" + (dayMap.size())
                + " in " + (System.currentTimeMillis() - begin) + "ms");
        return r;
    }

    private static List<Report.TopPlay> topPlays(Map<Long, List<PlayEntry>> byDay) {
        Map<String, Report.TopPlay> byName = new HashMap<>();
        for (Map.Entry<Long, List<PlayEntry>> e : byDay.entrySet()) {
            for (int i = 0; i < e.getValue().size(); i++) {
                PlayEntry p = e.getValue().get(i);
                if (p == null || p.name == null || p.name.isEmpty()) {
                    continue;
                }
                Report.TopPlay t = byName.get(p.name);
                if (t == null) {
                    t = new Report.TopPlay();
                    t.name = p.name;
                    byName.put(p.name, t);
                }
                t.episodes++;
                if (p.ts > t.lastMs) {
                    t.lastMs = p.ts;
                }
            }
        }
        List<Report.TopPlay> list = new ArrayList<>(byName.values());
        Collections.sort(list, new Comparator<Report.TopPlay>() {
            @Override
            public int compare(Report.TopPlay a, Report.TopPlay b) {
                if (a.episodes != b.episodes) {
                    return b.episodes - a.episodes;
                }
                return Long.compare(b.lastMs, a.lastMs);
            }
        });
        return list.size() > 5 ? new ArrayList<>(list.subList(0, 5)) : list;
    }

    private static int month(long day) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(day);
        return c.get(java.util.Calendar.MONTH);
    }

    private static int year(long day) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(day);
        return c.get(java.util.Calendar.YEAR);
    }
}
