package com.dsh.tvhud.pet;

import java.util.ArrayList;
import java.util.List;

/**
 * 家长设置目录页摘要的纯逻辑单测：状态优先级、进度口径、时段格式。
 * 不依赖 Android、不需要电视。
 */
public final class SettingsFactsTest {
    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    public static void main(String[] args) {
        runCase("今天状态优先级：免限 > 家长时间 > 休息 > 用完 > 时段外 > 可看", new Body() {
            @Override
            public void run() {
                // 全部条件都成立时，免限赢
                eq("免限优先", SettingsFacts.TODAY_FREE_PASS,
                        SettingsFacts.todayState(true, true, true, 5, 5, true, false));
                // 去掉免限，家长时间赢
                eq("家长时间其次", SettingsFacts.TODAY_PARENT_TIME,
                        SettingsFacts.todayState(false, true, true, 5, 5, true, false));
                // 闸门已开（本场到点）排在「场次用完」之前
                eq("闸门优先于用完", SettingsFacts.TODAY_RESTING,
                        SettingsFacts.todayState(false, false, true, 5, 5, false, true));
                eq("用完优先于时段外", SettingsFacts.TODAY_USED_UP,
                        SettingsFacts.todayState(false, false, false, 5, 5, true, false));
                eq("时段外", SettingsFacts.TODAY_OUTSIDE,
                        SettingsFacts.todayState(false, false, false, 2, 5, true, false));
                eq("可看", SettingsFacts.TODAY_OK,
                        SettingsFacts.todayState(false, false, false, 2, 5, true, true));
                // 允许时段关着时，窗内/窗外不影响判定
                eq("时段关闭视为可看", SettingsFacts.TODAY_OK,
                        SettingsFacts.todayState(false, false, false, 2, 5, false, false));
                eq("场次只剩 1 场也算可看", SettingsFacts.TODAY_OK,
                        SettingsFacts.todayState(false, false, false, 4, 5, false, true));
            }
        });

        runCase("今天摘要：每种状态都有进度且不出现 0/0、5/3 这种倒挂", new Body() {
            @Override
            public void run() {
                String ok = SettingsFacts.todaySummary(SettingsFacts.TODAY_OK, 2, 5);
                eq("可看摘要", "已用 2/5 场 · 现在可以看", ok);
                eq("免限摘要不显示进度", "今天免限 · 明天恢复",
                        SettingsFacts.todaySummary(SettingsFacts.TODAY_FREE_PASS, 9, 3));
                eq("用完摘要", "已用 5/5 场 · 今天用完了",
                        SettingsFacts.todaySummary(SettingsFacts.TODAY_USED_UP, 5, 5));
                eq("场次倒挂时上限取已用（不显示 5/3）", "已用 5/5 场 · 今天用完了",
                        SettingsFacts.todaySummary(SettingsFacts.TODAY_USED_UP, 5, 3));
                eq("负场次按 0 处理", "已用 0/1 场 · 现在可以看",
                        SettingsFacts.todaySummary(SettingsFacts.TODAY_OK, -2, 1));
            }
        });

        runCase("今天状态短语：六个状态都有中文，不复用「可以看」", new Body() {
            @Override
            public void run() {
                String[] got = {
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_FREE_PASS),
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_PARENT_TIME),
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_RESTING),
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_USED_UP),
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_OUTSIDE),
                        SettingsFacts.todayStatus(SettingsFacts.TODAY_OK),
                };
                String[] want = {"今天免限", "家长时间", "正在休息", "今天的场次用完了",
                        "不在允许时段", "现在可以看"};
                for (int i = 0; i < want.length; i++) {
                    eq("状态短语 " + i, want[i], got[i]);
                }
            }
        });

        runCase("时间摘要：时长/场次/间隔/周末加场/续时", new Body() {
            @Override
            public void run() {
                eq("默认（开启答题续时）",
                        "15 分钟/场 · 每天 3 场 · 间隔 30 分 · 周末 +5 场 · 续时 5 分 ×2",
                        SettingsFacts.timeSummary(15, 3, 5, 30, true, 5, 2));
                eq("关周末 + 关答题",
                        "30 分钟/场 · 每天 2 场 · 间隔 1 小时",
                        SettingsFacts.timeSummary(30, 2, 0, 60, false, 5, 2));
                eq("小时/分钟混合",
                        "1 小时 30 分/场 · 每天 24 场 · 间隔 1 小时 30 分 · 周末 +1 场 · 续时 30 分 ×3",
                        SettingsFacts.timeSummary(90, 24, 1, 90, true, 30, 3));
                eq("越界值收敛",
                        "5 分钟/场 · 每天 1 场 · 间隔 5 分 · 周末 +24 场 · 续时 5 分 ×1",
                        SettingsFacts.timeSummary(0, 0, 99, 0, true, 0, 0));
            }
        });

        runCase("遥控器步进：范围 / 步长 / 边界回绕", new Body() {
            @Override
            public void run() {
                eq("每场时长 +", 20, SettingsFacts.stepSessionMinutes(15, 1));
                eq("每场时长 -", 10, SettingsFacts.stepSessionMinutes(15, -1));
                eq("每场时长上限回绕", 5, SettingsFacts.stepSessionMinutes(180, 1));
                eq("每场时长下限回绕", 180, SettingsFacts.stepSessionMinutes(5, -1));
                eq("场次 +", 4, SettingsFacts.stepMaxSessions(3, 1));
                eq("场次上限回绕", 1, SettingsFacts.stepMaxSessions(24, 1));
                eq("场次下限回绕", 24, SettingsFacts.stepMaxSessions(1, -1));
                eq("周末加场 +", 6, SettingsFacts.stepWeekendExtra(5, 1));
                eq("周末加场 0 向上", 1, SettingsFacts.stepWeekendExtra(0, 1));
                eq("周末加场 0 向下回绕", 24, SettingsFacts.stepWeekendExtra(0, -1));
                eq("续时次数 +", 3, SettingsFacts.stepExtensionsPerDay(2, 1));
                eq("续时次数上限回绕", 1, SettingsFacts.stepExtensionsPerDay(24, 1));
                eq("续时分钟 +", 10, SettingsFacts.stepExtensionMinutes(5, 1));
                eq("续时分钟上限回绕", 5, SettingsFacts.stepExtensionMinutes(180, 1));
                eq("间隔 30 +", 35, SettingsFacts.stepRestMinutes(30, 1));
                eq("间隔 60 起 15 分钟档", 75, SettingsFacts.stepRestMinutes(60, 1));
                eq("间隔 180 起 30 分钟档", 210, SettingsFacts.stepRestMinutes(180, 1));
                eq("间隔上限回绕", 5, SettingsFacts.stepRestMinutes(720, 1));
                eq("间隔下限回绕", 720, SettingsFacts.stepRestMinutes(5, -1));
            }
        });

        runCase("时段摘要：关 = 全天；开 = 起止；跨零点照原样显示", new Body() {
            @Override
            public void run() {
                eq("关闭", "关闭 · 全天可看", SettingsFacts.windowSummary(false, 17 * 60, 20 * 60));
                eq("普通时段", "17:00–20:00", SettingsFacts.windowSummary(true, 17 * 60, 20 * 60));
                eq("跨零点", "20:30–07:00",
                        SettingsFacts.windowSummary(true, 20 * 60 + 30, 7 * 60));
                eq("开始=结束显示成同一时刻（全天）", "17:00–17:00",
                        SettingsFacts.windowSummary(true, 17 * 60, 17 * 60));
                eq("分钟回绕", "00:00–23:30",
                        SettingsFacts.windowSummary(true, 1440, 1410));
            }
        });

        runCase("宠物摘要：两个开关", new Body() {
            @Override
            public void run() {
                eq("默认", "退化 开 · 温和 关", SettingsFacts.petSummary(true, false));
                eq("都关", "退化 关 · 温和 关", SettingsFacts.petSummary(false, false));
                eq("都开", "退化 开 · 温和 开", SettingsFacts.petSummary(true, true));
            }
        });

        System.out.printf("%nSettingsFactsTest: %d cases, %d checks, %d failed%n",
                passedCases + failedCases, checks, failedCases);
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        if (failedCases > 0) {
            System.exit(1);
        }
    }

    private static void runCase(String name, Body body) {
        int before = failedCases;
        try {
            body.run();
        } catch (Throwable t) {
            failedCases++;
            failures.add(name + " → 抛异常 " + t);
        }
        if (failedCases == before) {
            passedCases++;
            System.out.println("PASS  " + name);
        } else {
            System.out.println("FAIL  " + name);
        }
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failedCases++;
            failures.add(what);
            System.out.println("  FAIL " + what);
        }
    }

    private static void eq(String what, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (!ok) {
            failedCases++;
            failures.add(what + "：want=" + want + " got=" + got);
            System.out.println("  FAIL " + what + "：want=" + want + " got=" + got);
        }
        checks++;
    }
}
