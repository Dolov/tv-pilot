package com.dsh.tvhud.report;

import com.dsh.tvhud.usage.ArchiveBackfill;
import com.dsh.tvhud.usage.ArchivePolicy;
import com.dsh.tvhud.usage.Categories;
import com.dsh.tvhud.usage.HistoryStore;
import com.dsh.tvhud.usage.PlayEntry;
import com.dsh.tvhud.usage.UsageDay;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * 报告核心逻辑的纯 Java 单元测试：不依赖 Android、不依赖真电视（Android 相关 import
 * 由 tests/java/stubs 下的最小桩提供）。
 *
 * 运行：bash tests/java/run.sh（make test 会调用）；退出码 0=全过，1=有失败。
 * 约定：用例名写「被保护的行为」，不写实现细节；断言失败信息要能直接定位。
 */
public final class ReportCoreTest {
    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    public static void main(String[] args) {
        // 报告按本地时区做日界运算；固定成电视所在时区，避免 CI/本机时区不同导致结果漂移
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        runCase("周期边界：周=周一到周日，月/年=自然周期", new Body() {
            @Override
            public void run() {
                long wed = day(2026, 11, 4);
                eq("周三所在周的起点是周一", day(2026, 11, 2), ReportPeriod.startOf(ReportPeriod.WEEK, wed));
                eq("周终点是周日", day(2026, 11, 8),
                        ReportPeriod.endOf(ReportPeriod.WEEK, ReportPeriod.startOf(ReportPeriod.WEEK, wed)));
                eq("周平移 -1 等于上一周", day(2026, 10, 26),
                        ReportPeriod.shift(ReportPeriod.WEEK, ReportPeriod.startOf(ReportPeriod.WEEK, wed), -1));
                eq("月起点是 1 号", day(2026, 11, 1), ReportPeriod.startOf(ReportPeriod.MONTH, wed));
                eq("11 月终点是 30 号", day(2026, 11, 30),
                        ReportPeriod.endOf(ReportPeriod.MONTH, day(2026, 11, 1)));
                eq("年起点是 1/1", day(2026, 1, 1), ReportPeriod.startOf(ReportPeriod.YEAR, wed));
                eq("年终点是 12/31", day(2026, 12, 31),
                        ReportPeriod.endOf(ReportPeriod.YEAR, day(2026, 1, 1)));
                eq("年平移 -1 等于去年", day(2025, 1, 1),
                        ReportPeriod.shift(ReportPeriod.YEAR, day(2026, 1, 1), -1));
                eq("区间文案含起止日期", "2026.11.02 – 11.06",
                        ReportPeriod.range(day(2026, 11, 2), day(2026, 11, 6)));
                eq("365 天窗口起点", day(2026, 11, 6) - 364L * UsageDay.DAY_MS,
                        ReportPeriod.earliestDay(day(2026, 11, 6)));
            }
        });

        runCase("ISO 周序号：跨年周按归属年份算（含 1/4 的周为第 1 周）", new Body() {
            @Override
            public void run() {
                eq("2026-11-05 是第 45 周", 45, ReportPeriod.weekOfYear(day(2026, 11, 5)));
                eq("2026-11-05 归属 2026", 2026, ReportPeriod.weekYear(day(2026, 11, 5)));
                eq("2026-01-01 是第 1 周", 1, ReportPeriod.weekOfYear(day(2026, 1, 1)));
                eq("2025-12-29 属 2026 年第 1 周", 1, ReportPeriod.weekOfYear(day(2025, 12, 29)));
                eq("2025-12-29 归属 2026", 2026, ReportPeriod.weekYear(day(2025, 12, 29)));
                eq("2025-12-22 是第 52 周", 52, ReportPeriod.weekOfYear(day(2025, 12, 22)));
            }
        });

        runCase("周期标签：具体名称，跨年补年份", new Body() {
            @Override
            public void run() {
                long today = day(2026, 11, 6);
                eq("本周", "第 45 周", ReportPeriod.label(ReportPeriod.WEEK, day(2026, 11, 2), today));
                eq("去年同一周序号补年份", "2025 第 45 周",
                        ReportPeriod.label(ReportPeriod.WEEK, day(2025, 11, 3), today));
                eq("本月", "11 月", ReportPeriod.label(ReportPeriod.MONTH, day(2026, 11, 1), today));
                eq("去年同月补年份", "2025 年 11 月",
                        ReportPeriod.label(ReportPeriod.MONTH, day(2025, 11, 1), today));
                eq("年份", "2025 年", ReportPeriod.label(ReportPeriod.YEAR, day(2025, 1, 1), today));
            }
        });

        runCase("日汇总：分段求和、跨小时切分、起止与主类别", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                DayAgg a = new DayAgg(d);
                a.add(at(d, 20, 50), at(d, 21, 10), Categories.VIDEO);
                eq("总时长", 20L * 60000L, a.totalMs);
                eq("20 点桶", 10L * 60000L, a.hourMs[20]);
                eq("21 点桶", 10L * 60000L, a.hourMs[21]);
                eq("视频类别", 20L * 60000L, a.catMs[Categories.VIDEO]);
                eq("首个时刻", at(d, 20, 50), a.firstMs);
                eq("末个时刻", at(d, 21, 10), a.lastMs);
                ok("有记录标记", a.recorded);
                a.finish();
                eq("主类别", Categories.VIDEO, a.mainCat);

                DayAgg mixed = new DayAgg(d);
                mixed.add(at(d, 10, 0), at(d, 10, 30), Categories.VIDEO);
                mixed.add(at(d, 10, 30), at(d, 10, 40), Categories.SYSTEM);
                mixed.finish();
                eq("主类别取最大", Categories.VIDEO, mixed.mainCat);
                eq("一小时的桶相加=总时长", mixed.totalMs, mixed.hourMs[10]);

                DayAgg clamped = new DayAgg(d);
                clamped.add(d - 10L * 60000L, at(d, 0, 5), Categories.SYSTEM);
                eq("跨零点分段被裁到当天", 5L * 60000L, clamped.totalMs);

                DayAgg empty = new DayAgg(d);
                empty.finish();
                eq("空天没有主类别", -1, empty.mainCat);
                ok("空天不算有记录", !empty.recorded);
                ok("空天 hasUsage=false", !empty.hasUsage());
            }
        });

        runCase("聚合：分类口径一致 vs 旧口径（只计总时长）", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                List<UsageDay.Seg> segs = segs(
                        seg(at(d, 20, 0), at(d, 20, 30), Categories.VIDEO),
                        seg(at(d, 20, 30), at(d, 20, 40), Categories.SYSTEM));
                DayAgg ok = ReportMath.aggregate(d, segs, true, UsageDay.SCHEME);
                eq("总时长", 40L * 60000L, ok.totalMs);
                eq("视频", 30L * 60000L, ok.catMs[Categories.VIDEO]);
                eq("系统", 10L * 60000L, ok.catMs[Categories.SYSTEM]);
                eq("小时桶之和=总时长", ok.totalMs, sum(ok.hourMs));
                ok("分类之和=总时长", sum(ok.catMs) == ok.totalMs);

                DayAgg stale = ReportMath.aggregate(d, segs, true, UsageDay.SCHEME - 1);
                eq("旧口径仍计总时长", 40L * 60000L, stale.totalMs);
                eq("旧口径不填分类", 0L, sum(stale.catMs));
                eq("旧口径不填小时桶", 0L, sum(stale.hourMs));
                ok("旧口径天数仍算已记录", stale.recorded);

                DayAgg none = ReportMath.aggregate(d, null, false, UsageDay.SCHEME);
                eq("空数据总时长 0", 0L, none.totalMs);
                ok("空数据未记录", !none.recorded);
            }
        });

        runCase("周期累计：总时长/记录天数/旧口径天数/峰值日", new Body() {
            @Override
            public void run() {
                long d1 = day(2026, 11, 5);
                long d2 = day(2026, 11, 6);
                Report r = new Report();
                r.period = ReportPeriod.WEEK;
                r.startDay = day(2026, 11, 2);
                r.todayStart = d2;
                r.viewEndDay = d2;
                ReportMath.accumulate(r, agg(d1, 20, 0, 30));
                ReportMath.accumulate(r, agg(d2, 16, 0, 20));
                ReportMath.accumulate(r, ReportMath.aggregate(day(2026, 11, 4),
                        segs(seg(at(day(2026, 11, 4), 9, 0), at(day(2026, 11, 4), 9, 5), Categories.SYSTEM)),
                        true, UsageDay.SCHEME - 1));
                eq("总时长", 55L * 60000L, r.totalMs);
                eq("有记录天数", 3, r.recordedDays);
                eq("旧口径天数", 1, r.staleSchemeDays);
                eq("峰值日=11/05", d1, r.maxDay.dayStart);
                eq("分类之和 = 总时长 - 旧口径", 50L * 60000L, sum(r.catMs));
                eq("小时桶之和 = 总时长 - 旧口径", 50L * 60000L, sum(r.hourMs));
                r.elapsedDays = 5;
                eq("日均", 11L * 60000L, r.avgMs());
                eq("日均文案", "0:11", ReportMath.hhmm(r.avgMs()));
                eq("峰值小时", 30L * 60000L, r.peakHourMs());
            }
        });

        runCase("百分比：最大余数法，合计必须正好 100", new Body() {
            @Override
            public void run() {
                long[] parts = {55L, 21L, 0L, 0L, 0L};
                int[] pct = ReportMath.percentages(parts);
                eq("视频 72%", 72, pct[0]);
                eq("音乐 28%", 28, pct[1]);
                eq("合计 100", 100, pct[0] + pct[1] + pct[2] + pct[3] + pct[4]);
                int[] zeros = ReportMath.percentages(new long[]{0L, 0L, 0L, 0L, 0L});
                eq("全零不进百分比", 0, zeros[0] + zeros[1] + zeros[2] + zeros[3] + zeros[4]);
            }
        });

        runCase("格式化与星期换算", new Body() {
            @Override
            public void run() {
                eq("0:38", "0:38", ReportMath.hhmm(2324400L));
                eq("1:01", "1:01", ReportMath.hhmm(3660000L));
                eq("小时数", 1, ReportMath.hours(3660000L));
                eq("分钟数", 1, ReportMath.minutes(3660000L));
                long d = day(2026, 11, 5);
                eq("时刻", "20:30", ReportMath.clock(at(d, 20, 30)));
                eq("日期 MM/dd", "11/05", ReportMath.dateMd(d));
                eq("2026-11-05 是周四", 3, ReportMath.mondayIndex(d));
                eq("周四文案", "周四", ReportMath.weekdayCn(3));
                eq("周日文案", "周日", ReportMath.weekdayCn(6));
            }
        });

        runCase("峰值时段：取连续两小时之和最大的起点", new Body() {
            @Override
            public void run() {
                long[] h = new long[24];
                h[19] = 10L * 60000L;
                h[20] = 30L * 60000L;
                eq("峰值起点", 19, ReportMath.peakWindowStart(h));
                eq("峰值文案", "19–21 点", ReportMath.peakPhrase(h));
                eq("全零回落到 0 点", 0, ReportMath.peakWindowStart(new long[24]));
            }
        });

        runCase("洞察：周报最长/最短，今天不参与「最短」", new Body() {
            @Override
            public void run() {
                long thu = day(2026, 11, 5);
                long fri = day(2026, 11, 6);
                Report r = report(ReportPeriod.WEEK, day(2026, 11, 2), thu, false,
                        agg(thu, 16, 0, 38.74), agg(fri, 16, 0, 38));
                eq("周报洞察", "周四最长（0:38），周五最短（0:38）；15–17 点最集中。",
                        ReportMath.insight(r));

                Report current = report(ReportPeriod.WEEK, day(2026, 11, 2), fri, true,
                        agg(thu, 16, 0, 38), agg(fri, 16, 0, 5));
                String s = ReportMath.insight(current);
                ok("今天不参与最短：" + s, s.contains("周四最长") && !s.contains("最短"));

                Report empty = report(ReportPeriod.WEEK, day(2026, 11, 2), thu, false,
                        new DayAgg(thu));
                eq("没数据洞察为空", "", ReportMath.insight(empty));
            }
        });

        runCase("洞察：月报周末 vs 工作日，缺一类时降级", new Body() {
            @Override
            public void run() {
                long thu = day(2026, 11, 5);
                long fri = day(2026, 11, 6);
                long sat = day(2026, 11, 7);
                Report r = report(ReportPeriod.MONTH, day(2026, 11, 1), fri, false,
                        agg(thu, 16, 0, 30), agg(fri, 16, 0, 20), agg(sat, 16, 0, 60));
                contains("周末多 35 分钟", ReportMath.insight(r), "周末日均比工作日多 35 分钟");
                contains("峰值时段", ReportMath.insight(r), "15–17 点最集中");

                Report onlyWeekdays = report(ReportPeriod.MONTH, day(2026, 11, 1), fri, false,
                        agg(thu, 16, 0, 30), agg(fri, 16, 0, 20));
                contains("缺周末时降级", ReportMath.insight(onlyWeekdays), "本月有记录 2 天，日均 0:25");
            }
        });

        runCase("洞察：年报按月均比较，空月份不参与", new Body() {
            @Override
            public void run() {
                Report single = report(ReportPeriod.YEAR, day(2026, 1, 1), day(2026, 11, 6), false,
                        agg(day(2026, 10, 5), 16, 0, 60), agg(day(2026, 10, 6), 16, 0, 25));
                contains("只有一个月记录", ReportMath.insight(single), "10 月有记录 2 天，日均 0:42");

                Report two = report(ReportPeriod.YEAR, day(2026, 1, 1), day(2026, 11, 6), false,
                        agg(day(2026, 10, 5), 16, 0, 60), agg(day(2026, 11, 5), 16, 0, 25));
                contains("按月均比较", ReportMath.insight(two), "10 月日均最高（1:00），11 月最低（0:25）");
            }
        });

        runCase("播放日志解析：只认当天、有片名的播放事件", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 6);
                String line = "2026:11:06:16:40:45.123 [I] ots: {\"play_status\":1,"
                        + "\"media_id\":\"123\",\"video_name\":\"示例节目\",\"ci\":36,"
                        + "\"total_ci\":40,\"foo\":\"bar\",\"total_duration\":123456}";
                PlayEntry e = PlayEntry.parse(line, d, d + UsageDay.DAY_MS);
                ok("应解析成功", e != null);
                eq("时刻", at(d, 16, 40) + 45123L, e.ts);
                eq("片名", "示例节目", e.name);
                eq("集数", 36, e.ci);
                eq("总集数", 40, e.totalCi);
                eq("单集时长", 123456L, e.durMs);
                eq("去重键", "示例节目|36", e.key());

                isNull("非播放行返回 null",
                        PlayEntry.parse("2026:11:06:16:40:45.123 [I] miio_monitor: work time = 1", d,
                                d + UsageDay.DAY_MS));
                isNull("不在当天返回 null", PlayEntry.parse(line, day(2026, 11, 7),
                        day(2026, 11, 8)));
                isNull("空片名返回 null", PlayEntry.parse(
                        "2026:11:06:16:40:45.123 {\"play_status\":1,\"media_id\":\"1\","
                                + "\"video_name\":\"\",\"ci\":1,\"total_ci\":1,"
                                + "\"total_duration\":1}", d, d + UsageDay.DAY_MS));
            }
        });

        runCase("环比：上一期覆盖率不足 80% 时不算", new Body() {
            @Override
            public void run() {
                Report r = new Report();
                r.period = ReportPeriod.WEEK;
                r.hasPrev = true;
                for (int i = 0; i < 7; i++) {
                    DayAgg d = new DayAgg(day(2026, 10, 26) + i * UsageDay.DAY_MS);
                    if (i < 6) {
                        d.recorded = true;
                        d.add(d.dayStart + 60000L, d.dayStart + 120000L, Categories.VIDEO);
                        d.finish();
                    }
                    r.prevDays.add(d);
                }
                ok("6/7 天有记录 → 可比", r.prevComparable());
                r.prevDays.get(5).recorded = false;
                ok("5/7 天有记录 → 不可比", !r.prevComparable());
                r.period = ReportPeriod.YEAR;
                ok("年报没有上一年可比", !r.prevComparable());
            }
        });

        runCase("柱状图几何：堆叠柱高度=总时长（v1.13 堆叠 bug 回归）", new Body() {
            @Override
            public void run() {
                float bottom = 200f;
                float plotH = 120f;
                float yMax = 60f;
                // 2026-11-05 的真实构成：视频 21 分钟 + 桌面/系统 17 分钟 = 38 分钟
                long[] cats = {21L * 60000L, 0L, 0L, 17L * 60000L, 0L};
                float[] tops = new float[5];
                float[] bots = new float[5];
                float h = BarStack.stack(bottom, plotH, yMax, cats, tops, bots);
                near("柱高=38 分钟对应像素", plotH * 38f / yMax, h);
                near("视频层底边=柱底", bottom, bots[0]);
                near("视频层顶边", bottom - plotH * 21f / yMax, tops[0]);
                near("系统层底边接视频层", tops[0], bots[3]);
                near("柱顶=柱底-总高", bottom - h, tops[3]);
                ok("第二层必须在第一层之上（旧 bug 会重叠）", tops[3] < tops[0] - 1f);
                near("系统层高度", plotH * 17f / yMax, bots[3] - tops[3]);

                // 总时长相同的两天，柱子高度必须一样（与类别构成无关）
                long[] otherCats = {4L * 60000L, 0L, 0L, 34L * 60000L, 0L};
                float h2 = BarStack.stack(bottom, plotH, yMax, otherCats, tops, bots);
                near("同总时长 → 同柱高", h, h2);

                long[] empty = {0L, 0L, 0L, 0L, 0L};
                float h0 = BarStack.stack(bottom, plotH, yMax, empty, tops, bots);
                ok("空数据柱高为 0", h0 == 0f);
                near("空数据层边=柱底", bottom, tops[2]);
                eq("总时长合计", 38L * 60000L, BarStack.total(cats));
                eq("总时长合计（空）", 0L, BarStack.total(empty));

                boolean threw = false;
                try {
                    BarStack.stack(bottom, plotH, 0f, cats, tops, bots);
                } catch (IllegalArgumentException expected) {
                    threw = true;
                }
                ok("yMax<=0 抛异常", threw);
                threw = false;
                try {
                    BarStack.stack(bottom, plotH, yMax, cats, new float[1], new float[1]);
                } catch (IllegalArgumentException expected) {
                    threw = true;
                }
                ok("输出数组太短抛异常", threw);
            }
        });


        runCase("脏标记：只有实质变更才推进版本号", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 6);
                UsageDay u = new UsageDay(d);
                long rev0 = u.revision();
                u.append(at(d, 20, 0), at(d, 20, 10), Categories.VIDEO);
                ok("新增分段推进版本", u.revision() > rev0);
                long rev1 = u.revision();
                u.append(at(d, 20, 10), at(d, 20, 12), Categories.VIDEO);
                ok("同类相邻延长也推进版本", u.revision() > rev1);
                long rev2 = u.revision();
                u.append(at(d, 20, 12), at(d, 20, 12), Categories.VIDEO);
                eq("零长度分段不推进版本", rev2, u.revision());
                u.append(at(d, 20, 30), at(d, 20, 20), Categories.VIDEO);
                eq("倒挂分段不推进版本", rev2, u.revision());
                long rev3 = u.revision();
                u.reset(day(2026, 11, 7));
                ok("跨天重置推进版本", u.revision() > rev3);
            }
        });

        runCase("归档节流：强制写 / 没变不写 / 间隔内不写", new Body() {
            @Override
            public void run() {
                long now = 1000000000L;
                long interval = ArchivePolicy.DEFAULT_INTERVAL_MS;
                ok("间隔就是 5 分钟", interval == 300000L);
                ok("强制写无视一切", ArchivePolicy.shouldWrite(true, false, now, now, interval));
                ok("强制写即使刚写完也写", ArchivePolicy.shouldWrite(true, true, now, now, interval));
                ok("数据没变且非强制 → 不写",
                        !ArchivePolicy.shouldWrite(false, false, now, now, interval));
                ok("从没写过 → 写", ArchivePolicy.shouldWrite(false, true, now, 0L, interval));
                ok("刚写完 1 秒内有变化 → 不写",
                        !ArchivePolicy.shouldWrite(false, true, now, now - 1000L, interval));
                ok("距上次写 4 分 59 秒 → 不写",
                        !ArchivePolicy.shouldWrite(false, true, now, now - interval + 1000L, interval));
                ok("距上次写正好 5 分钟 → 写",
                        ArchivePolicy.shouldWrite(false, true, now, now - interval, interval));
                ok("距上次写超过 5 分钟 → 写",
                        ArchivePolicy.shouldWrite(false, true, now, now - interval - 1000L, interval));
            }
        });

        runCase("归档完整性：分段必须非负、在当天内、不重叠", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                HistoryStore.DayData ok1 = new HistoryStore.DayData();
                ok1.dayStart = d;
                ok1.segs.add(seg(at(d, 20, 0), at(d, 20, 30), Categories.VIDEO));
                ok1.segs.add(seg(at(d, 21, 0), at(d, 21, 30), Categories.SYSTEM));
                eq("正常归档没问题", null, HistoryStore.verify(ok1));

                HistoryStore.DayData neg = new HistoryStore.DayData();
                neg.dayStart = d;
                neg.segs.add(seg(at(d, 20, 30), at(d, 20, 0), Categories.VIDEO));
                contains("负时长要报", HistoryStore.verify(neg), "时长非正");

                HistoryStore.DayData out = new HistoryStore.DayData();
                out.dayStart = d;
                out.segs.add(seg(at(d, 23, 59), d + UsageDay.DAY_MS + 60000L, Categories.VIDEO));
                contains("越出当天要报", HistoryStore.verify(out), "越出当天");

                HistoryStore.DayData overlap = new HistoryStore.DayData();
                overlap.dayStart = d;
                overlap.segs.add(seg(at(d, 20, 0), at(d, 20, 30), Categories.VIDEO));
                overlap.segs.add(seg(at(d, 20, 20), at(d, 21, 0), Categories.SYSTEM));
                contains("重叠要报", HistoryStore.verify(overlap), "重叠");

                HistoryStore.DayData badCat = new HistoryStore.DayData();
                badCat.dayStart = d;
                badCat.segs.add(seg(at(d, 20, 0), at(d, 20, 30), 9));
                contains("类别越界要报", HistoryStore.verify(badCat), "类别越界");

                HistoryStore.DayData empty = new HistoryStore.DayData();
                empty.dayStart = d;
                eq("空归档没问题", null, HistoryStore.verify(empty));
            }
        });


        runCase("补写合并：分段取更全的，播放记录取并集", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                List<UsageDay.Seg> archived = segs(seg(at(d, 16, 0), at(d, 16, 30), Categories.VIDEO));
                List<UsageDay.Seg> pending = segs(
                        seg(at(d, 16, 0), at(d, 16, 40), Categories.VIDEO));
                near("待补写更全 → 用待补写的", 40L * 60000L,
                        ArchiveBackfill.totalMs(ArchiveBackfill.mergeSegs(archived, pending)));
                List<UsageDay.Seg> archivedLonger = segs(
                        seg(at(d, 16, 0), at(d, 17, 0), Categories.VIDEO));
                near("归档更全 → 用归档的", 60L * 60000L,
                        ArchiveBackfill.totalMs(ArchiveBackfill.mergeSegs(archivedLonger, pending)));
                near("空归档 → 用待补写的", 40L * 60000L,
                        ArchiveBackfill.totalMs(ArchiveBackfill.mergeSegs(null, pending)));

                PlayEntry a = play("示例节目", 36, at(d, 16, 40));
                PlayEntry b = play("示例节目", 37, at(d, 16, 42));
                PlayEntry dup = play("示例节目", 36, at(d, 16, 40));
                List<PlayEntry> merged = ArchiveBackfill.mergePlays(
                        new ArrayList<PlayEntry>(java.util.Arrays.asList(a)),
                        new ArrayList<PlayEntry>(java.util.Arrays.asList(dup, b)));
                eq("同片名同集号只留一条 + 新集数补进来 = 2", 2, merged.size());
                eq("保留归档原有顺序", "示例节目|36", merged.get(0).key());
                eq("新记录追加在后面", "示例节目|37", merged.get(1).key());
                eq("空归档只用待补写", 2,
                        ArchiveBackfill.mergePlays(null,
                                new ArrayList<PlayEntry>(java.util.Arrays.asList(a, b))).size());
            }
        });

        runCase("跨天定稿：归档文件写出去能原样读回来", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                android.content.Context ctx = new android.content.Context();
                List<UsageDay.Seg> segs = segs(
                        seg(at(d, 16, 24), at(d, 16, 34), Categories.VIDEO),
                        seg(at(d, 18, 58), at(d, 19, 6), Categories.SYSTEM));
                ok("有数据时写归档返回 true", HistoryStore.write(ctx, d, segs, null));
                ok("空数据不写归档（返回 false，不占节流窗口）",
                        !HistoryStore.write(ctx, day(2026, 11, 6), null, null));
                HistoryStore.DayData back = HistoryStore.read(ctx, d);
                ok("归档文件能读回来", back != null);
                eq("分段数一致", 2, back.segs.size());
                near("总时长一致", ArchiveBackfill.totalMs(segs),
                        ArchiveBackfill.totalMs(back.segs));
                eq("第一段起止不变", at(d, 16, 24), back.segs.get(0).start);
                eq("第一段止不变", at(d, 16, 34), back.segs.get(0).end);
                eq("类别不变", Categories.VIDEO, back.segs.get(0).cat);
                eq("读回的数据完整性没问题", null, HistoryStore.verify(back));
                eq("归档文件名就是日期", "2026-11-05.json",
                        HistoryStore.fileFor(ctx, d).getName());
            }
        });

        runCase("不变量自检：分类/小时桶必须与总时长一致", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 5);
                Report r = report(ReportPeriod.WEEK, d, d, false, agg(d, 20, 0, 38));
                eq("正常报告无告警", null, ReportMath.checkInvariants(r));
                r.catMs[0] += 1000L;
                contains("分类被改坏要报", ReportMath.checkInvariants(r), "catMs");
                r.catMs[0] -= 1000L;

                Report stale = report(ReportPeriod.WEEK, d, d, false,
                        ReportMath.aggregate(d, segs(seg(at(d, 9, 0), at(d, 9, 5), Categories.SYSTEM)),
                                true, UsageDay.SCHEME - 1));
                eq("旧口径报告本身合法", null, ReportMath.checkInvariants(stale));
                stale.days.get(0).catMs[0] = 5000L;
                contains("旧口径天混入分类要报", ReportMath.checkInvariants(stale), "旧口径");

                Report badCurrent = report(ReportPeriod.WEEK, d, d, true, agg(d, 20, 0, 30));
                badCurrent.viewEndDay = d - UsageDay.DAY_MS;
                contains("本期截止日不是今天要报", ReportMath.checkInvariants(badCurrent), "todayStart");
                eq("null 报告有明确提示", "report == null", ReportMath.checkInvariants(null));
            }
        });

        runCase("纵轴刻度：周/月/年三张图共用 ChartAxis，档位与旧实现一致", new Body() {
            @Override
            public void run() {
                ChartAxis week = ChartAxis.of(120L * 60000L,
                        new float[]{30f, 60f, 120f}, 4f);
                ok("2 小时 → 每格 30 分钟", week.step == 30f);
                ok("2 小时 → 轴高 120 分钟", week.yMax == 120f);
                ChartAxis month = ChartAxis.of(200L * 60000L,
                        new float[]{30f, 60f, 120f}, 4f);
                ok("3h20m → 每格 60 分钟", month.step == 60f);
                ok("3h20m → 轴高 240 分钟", month.yMax == 240f);
                ChartAxis year = ChartAxis.of(500L * 60000L,
                        new float[]{120f, 240f, 480f}, 2.5f);
                ok("8h20m（月总）→ 每格 240 分钟", year.step == 240f);
                ok("8h20m → 轴高 720 分钟", year.yMax == 720f);
                ChartAxis zero = ChartAxis.of(0L, new float[]{30f, 60f, 120f}, 4f);
                ok("0 数据也给出合法轴（无除零）", zero.step > 0f && zero.yMax > 0f);
            }
        });

        runCase("覆盖区间：365 天窗口外的天不计入日均/覆盖率", new Body() {
            @Override
            public void run() {
                long start = day(2025, 11, 1);
                long firstKept = day(2025, 11, 7);
                Report partial = report(ReportPeriod.MONTH, start, firstKept, false,
                        agg(firstKept, 20, 0, 30));
                partial.coverageStartDay = firstKept;
                partial.elapsedDays = 1;
                eq("窗口起点晚于周期首日时取窗口起点", firstKept,
                        ReportMath.coverageStartDay(start, day(2025, 11, 30), firstKept));
                eq("整期都在窗口内时就是周期首日", start,
                        ReportMath.coverageStartDay(start, day(2025, 11, 30), day(2025, 1, 1)));
                eq("最坏情况不超过统计末日", viewEndOf(start),
                        ReportMath.coverageStartDay(start, viewEndOf(start),
                                day(2026, 12, 31)));
                eq("部分覆盖报告本身合法", null, ReportMath.checkInvariants(partial));
                ok("能识别被窗口截断", partial.isPartialCoverage());
                partial.elapsedDays = 30;
                contains("elapsedDays 与覆盖区间不一致要报",
                        ReportMath.checkInvariants(partial), "覆盖区间");
                partial.coverageStartDay = start - UsageDay.DAY_MS;
                contains("覆盖起点早于周期首日要报",
                        ReportMath.checkInvariants(partial), "coverageStartDay");
            }
        });

        runCase("周内平均：只统计有记录的日子（图与洞察同口径）", new Body() {
            @Override
            public void run() {
                long mon = day(2026, 11, 2);   // 周一
                long tue = day(2026, 11, 3);   // 周二
                Report r = report(ReportPeriod.MONTH, day(2026, 11, 1),
                        day(2026, 11, 30), false,
                        agg(mon, 20, 0, 60),
                        agg(tue, 20, 0, 30),
                        new DayAgg(day(2026, 11, 9)),    // 下周一没记录
                        new DayAgg(day(2026, 11, 10)));  // 下周二没记录
                long[] avg = ReportMath.weekdayAverageMs(r.days);
                eq("周一 60 分钟", 60L * 60000L, avg[0]);
                eq("周二 30 分钟", 30L * 60000L, avg[1]);
                eq("无记录的周三为 0", 0L, avg[2]);
                eq("周一有记录日数 = 1（无记录日不进分母）",
                        1, ReportMath.weekdayCount(r.days)[0]);
                eq("周一合计", 60L * 60000L, ReportMath.weekdaySumMs(r.days)[0]);
            }
        });

        runCase("旧归档没有 v 字段时按 scheme 3 兼容（bump SCHEME 也不误判）", new Body() {
            @Override
            public void run() {
                android.content.Context ctx = new android.content.Context();
                long d = day(2026, 1, 3);
                java.io.File f = HistoryStore.fileFor(ctx, d);
                java.io.File dir = f.getParentFile();
                if (dir != null) {
                    dir.mkdirs();
                }
                try {
                    String json = "{\"day\":" + d
                            + ",\"segs\":[[" + (d + 1000L) + "," + (d + 2000L) + ",0]],"
                            + "\"plays\":[]}";
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    try {
                        out.write(json.getBytes("UTF-8"));
                    } finally {
                        out.close();
                    }
                } catch (Throwable t) {
                    throw new AssertionError("准备旧归档失败: " + t);
                }
                HistoryStore.DayData back = HistoryStore.read(ctx, d);
                ok("旧归档能读出来", back != null);
                eq("缺省 scheme = 3", 3, back.scheme);
                eq("旧归档读取不报损坏", null, HistoryStore.verify(back));
                ok("旧归档读出的 scheme 与当前 SCHEME 一致（当前装载不会画错色）",
                        back.scheme == UsageDay.SCHEME);
                ok("非当前口径的报告仍只计总时长",
                        ReportMath.aggregate(d, back.segs, true, UsageDay.SCHEME + 1)
                                .catMs[Categories.VIDEO] == 0L);
                f.delete();
            }
        });

        runCase("活对象快照：报告后台聚合拿到的是独立副本", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 6);
                UsageDay u = new UsageDay(d);
                u.append(at(d, 10, 0), at(d, 10, 30), Categories.VIDEO);
                UsageDay snap = u.snapshot();
                u.append(at(d, 11, 0), at(d, 11, 30), Categories.MUSIC);
                eq("快照不随后续 append 变化", 1, snap.segments().size());
                eq("快照时长只含拷贝时刻", 30L * 60000L,
                        snap.segments().get(0).end - snap.segments().get(0).start);
                eq("原对象继续增长", 2, u.segments().size());
            }
        });

        runCase("归档自检也管播放记录", new Body() {
            @Override
            public void run() {
                long d = day(2026, 11, 6);
                HistoryStore.DayData bad = new HistoryStore.DayData();
                bad.dayStart = d;
                bad.plays.add(play("越界", 1, d - 1000L));
                contains("播放记录越出当天要报", HistoryStore.verify(bad), "播放记录");
                HistoryStore.DayData okPlay = new HistoryStore.DayData();
                okPlay.dayStart = d;
                okPlay.plays.add(play("正常", 1, at(d, 19, 0)));
                eq("当天的播放记录没问题", null, HistoryStore.verify(okPlay));
            }
        });

        System.out.println();
        System.out.println("== 汇总 ==");
        System.out.println("用例 PASS " + passedCases + "  FAIL " + failedCases
                + "（断言 " + checks + " 条）");
        if (failedCases > 0) {
            System.out.println("失败用例：");
            for (int i = 0; i < failures.size(); i++) {
                System.out.println("  - " + failures.get(i));
            }
        }
        if (failedCases > 0) {
            System.exit(1);
        }


    }

    // ---------- 断言与用例框架 ----------

    private static void runCase(String name, Body body) {
        int before = checks;
        try {
            body.run();
            if (checks == before) {
                throw new AssertionError("用例没有执行任何断言");
            }
            passedCases++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failedCases++;
            String detail = t.getMessage() == null ? t.toString() : t.getMessage();
            failures.add(name + "：" + detail);
            System.out.println("FAIL  " + name + "：" + detail);
        }
    }

    private static void isNull(String name, Object value) {
        checks++;
        if (value != null) {
            throw new AssertionError(name + "（实际非 null：" + value + "）");
        }
    }

    private static void ok(String name, boolean cond) {
        checks++;
        if (!cond) {
            throw new AssertionError(name);
        }
    }

    private static void eq(String name, long expected, long actual) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(name + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    private static void eq(String name, String expected, String actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + "（期望 «" + expected + "»，实际 «" + actual + "»）");
        }
    }

    private static void contains(String name, String text, String part) {
        checks++;
        if (text == null || !text.contains(part)) {
            throw new AssertionError(name + "（«" + part + "» 不在 «" + text + "» 里）");
        }
    }

    private static void near(String name, double expected, double actual) {
        checks++;
        if (Math.abs(expected - actual) > 1e-3) {
            throw new AssertionError(name + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    // ---------- 构造辅助 ----------

    private static long day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long at(long dayStart, int hour, int minute) {
        return dayStart + hour * 3600000L + minute * 60000L;
    }

    private static long sum(long[] values) {
        long s = 0L;
        for (int i = 0; i < values.length; i++) {
            s += values[i];
        }
        return s;
    }

    private static PlayEntry play(String name, int ci, long ts) {
        PlayEntry e = new PlayEntry();
        e.name = name;
        e.ci = ci;
        e.totalCi = 40;
        e.ts = ts;
        e.status = 1004;
        return e;
    }

    private static UsageDay.Seg seg(long start, long end, int cat) {
        return new UsageDay.Seg(start, end, cat);
    }

    private static List<UsageDay.Seg> segs(UsageDay.Seg... items) {
        List<UsageDay.Seg> list = new ArrayList<UsageDay.Seg>();
        for (int i = 0; i < items.length; i++) {
            list.add(items[i]);
        }
        return list;
    }

    /** 造一个「从 hh:mm 起连续 minutes 分钟」的当日汇总。 */
    private static DayAgg agg(long dayStart, int hour, int minute, double minutes) {
        DayAgg d = new DayAgg(dayStart);
        long from = at(dayStart, hour, minute);
        d.add(from, from + (long) (minutes * 60000.0), Categories.VIDEO);
        d.finish();
        return d;
    }

    private static long viewEndOf(long start) {
        return start + 29L * UsageDay.DAY_MS;
    }

    private static Report report(ReportPeriod period, long start, long today, boolean current,
                                 DayAgg... days) {
        Report r = new Report();
        r.period = period;
        r.startDay = start;
        r.coverageStartDay = start;
        r.todayStart = today;
        r.current = current;
        r.elapsedDays = days.length;
        for (int i = 0; i < days.length; i++) {
            r.days.add(days[i]);
            ReportMath.accumulate(r, days[i]);
            r.viewEndDay = Math.max(r.viewEndDay, days[i].dayStart);
        }
        if (r.viewEndDay == 0L) {
            r.viewEndDay = start;
        }
        return r;
    }
}
