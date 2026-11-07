package com.dsh.tvhud.ui;

import java.util.HashSet;
import java.util.Set;

/**
 * 带子 + 精选外观的纯 Java 单元测试：不依赖 Android、不依赖电视。
 *
 * 守住的不是「好不好看」，而是 P2 起会反复用到的两条硬约束：
 * 1. 一天 24 小时 → 像素的换算在边界上不许跑出轨道（游标/数据段会画歪）；
 * 2. 4 个 look 必须覆盖 4 套材质、id 稳定、旧 scheme/accent 能无损迁移。
 *
 * 运行：bash tests/java/run.sh；退出码 0=全过，1=有失败。
 */
public final class TapeCoreTest {

    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<String>();
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long DAY_START = 1_700_000_000_000L;

    private TapeCoreTest() {
    }

    public static void main(String[] args) {
        runCase("带子：24 小时 → 0..1，边界夹紧", new Body() {
            @Override
            public void run() {
                eq("没有当天起点", 0f, Tape.fraction(DAY_START + 6 * HOUR, 0L));
                eq("早于当天", 0f, Tape.fraction(DAY_START - HOUR, DAY_START));
                eq("正好当天 0 点", 0f, Tape.fraction(DAY_START, DAY_START));
                eq("6 点", 0.25f, Tape.fraction(DAY_START + 6 * HOUR, DAY_START));
                eq("12 点", 0.5f, Tape.fraction(DAY_START + 12 * HOUR, DAY_START));
                eq("24 点整", 1f, Tape.fraction(DAY_START + 24 * HOUR, DAY_START));
                eq("跨过 24 点", 1f, Tape.fraction(DAY_START + 30 * HOUR, DAY_START));
                float f23 = Tape.fraction(DAY_START + 23 * HOUR, DAY_START);
                check(f23 > 0.95f && f23 < 1f, "23 点应接近右端: " + f23);
            }
        });

        runCase("带子：坐标换算与整点刻度", new Body() {
            @Override
            public void run() {
                eq("左边界", 100f, Tape.hourX(0, 100f, 800f));
                eq("右边界", 900f, Tape.hourX(24, 100f, 800f));
                eq("中点", 500f, Tape.hourX(12, 100f, 800f));
                eq("越界左", 100f, Tape.hourX(-3, 100f, 800f));
                eq("越界右", 900f, Tape.hourX(99, 100f, 800f));
                eq("xAt 当天 0 点", 100f, Tape.xAt(DAY_START, DAY_START, 100f, 800f));
                eq("xAt 6 点", 300f, Tape.xAt(DAY_START + 6 * HOUR, DAY_START, 100f, 800f));
                check(Tape.isMajorHour(0) && Tape.isMajorHour(3) && Tape.isMajorHour(24),
                        "0/3/24 应是长刻度");
                check(!Tape.isMajorHour(1) && !Tape.isMajorHour(23),
                        "1/23 应是短刻度");
                int majors = 0;
                for (int h = 0; h < Tape.TICKS; h++) {
                    if (Tape.isMajorHour(h)) {
                        majors++;
                    }
                }
                eq("长刻度数量", 9, majors);
            }
        });

        runCase("带子：现在游标只在当天且开始后出现", new Body() {
            @Override
            public void run() {
                check(!Tape.hasNow(DAY_START, 0L), "没有当天起点不画游标");
                check(!Tape.hasNow(DAY_START, DAY_START), "0 点整不画游标");
                check(!Tape.hasNow(DAY_START - HOUR, DAY_START), "开始之前不画游标");
                check(Tape.hasNow(DAY_START + HOUR, DAY_START), "当天开始后画游标");
                float f = Tape.fraction(DAY_START + HOUR, DAY_START);
                check(f > 0f && f < 0.1f, "游标应在左端附近: " + f);
            }
        });

        runCase("带子：数据段两端夹到轨道内", new Body() {
            @Override
            public void run() {
                float left = 100f;
                float width = 800f;
                float start = Tape.segStart(DAY_START - 2 * HOUR, DAY_START, left, width);
                float end = Tape.segEnd(DAY_START + 3 * HOUR, DAY_START, left, width);
                eq("早于当天的段压到左端", left, start);
                check(end > start, "段的终点应在起点右侧");
                float endLate = Tape.segEnd(DAY_START + 30 * HOUR, DAY_START, left, width);
                eq("跨天段压到右端", left + width, endLate);
            }
        });

        runCase("精选外观：8 个 look 覆盖 8 套材质，id 唯一", new Body() {
            @Override
            public void run() {
                eq("look 数量", 8, Looks.ALL.length);
                Set<String> ids = new HashSet<String>();
                Set<String> schemes = new HashSet<String>();
                for (int i = 0; i < Looks.ALL.length; i++) {
                    Looks.Look look = Looks.ALL[i];
                    check(look.name != null && look.name.length() > 0, "look 名字不能为空");
                    check(look.desc != null && look.desc.length() > 0, "look 描述不能为空");
                    check(ids.add(look.id), "look id 重复: " + look.id);
                    check(schemes.add(look.scheme.id), "look 材质重复: " + look.scheme.id);
                    check(look.theme.scheme == look.scheme, "theme 材质应一致: " + look.id);
                    check(look.theme.accent == look.accent, "theme 灯色应一致: " + look.id);
                    eq("look.palette().lamp", look.accent.color, look.palette().lamp);
                }
                eq("材质数量", 8, schemes.size());
                check(Looks.defaultLook() == Looks.NIGHT, "默认 look 应为夜班");
                check(Looks.byId("nope") == Looks.NIGHT, "未知 id 回默认");
                check(Looks.byId(null) == Looks.NIGHT, "null 回默认");
                check(Looks.byId("dark") == Looks.DARK, "dark 可反查");
                check(Looks.byId("forest") == Looks.FOREST, "forest 可反查");
            }
        });

        runCase("精选外观：旧 scheme/accent 无损迁移", new Body() {
            @Override
            public void run() {
                check(Looks.fromLegacy("dashboard", "signal") == Looks.NIGHT, "dashboard.signal → 夜班");
                check(Looks.fromLegacy("paperlight", "ember") == Looks.WARM, "paperlight.ember → 暖房");
                check(Looks.fromLegacy("cinema", "ice") == Looks.CINEMA, "cinema.ice → 影院");
                check(Looks.fromLegacy("ledger", "moon") == Looks.LEDGER, "ledger.moon → 账本");
                check(Looks.fromLegacy("paperlight", "signal") == Looks.WARM,
                        "同材质优先 → 暖房");
                check(Looks.fromLegacy("unknown", "unknown") == Looks.NIGHT, "未知材质回默认");
                check(Looks.fromLegacy(null, null) == Looks.NIGHT, "null 回默认");
                check(Looks.fromLegacy("dark", "snow") == Looks.DARK, "dark.snow → 暗色");
                check(Looks.fromLegacy("forest", "mint") == Looks.FOREST, "forest.mint → 森林");
            }
        });

        runCase("带子 token：灯色 = 灯，轨道/刻度由文字色派生", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    Palette p = Palette.of(scheme, Accent.byId(scheme.defaultAccentId));
                    eq(scheme.id + " tapeNow 应为灯", p.lamp, p.tapeNow);
                    eq(scheme.id + " tapeTrack alpha", 0x14, (p.tapeTrack >>> 24) & 0xFF);
                    eq(scheme.id + " tapeRule alpha", 0x1C, (p.tapeRule >>> 24) & 0xFF);
                    eq(scheme.id + " tapeTick alpha", 0x40, (p.tapeTick >>> 24) & 0xFF);
                    eq(scheme.id + " tapeLabel 应为第三级文字", p.appText3, p.tapeLabel);
                    check(Contrast.ratio(p.tapeLabel, p.appInk) >= 3.0,
                            scheme.id + " 带子刻度数字对比度应 >= 3.0");
                }
            }
        });

        System.out.println();
        System.out.println("用例 PASS " + passedCases + "  FAIL " + failedCases
                + "（断言 " + checks + " 条）");
        if (!failures.isEmpty()) {
            for (int i = 0; i < failures.size(); i++) {
                System.out.println("  FAIL " + failures.get(i));
            }
            System.exit(1);
        }
    }

    private interface Body {
        void run();
    }

    private static void runCase(String name, Body body) {
        int before = failures.size();
        try {
            body.run();
        } catch (Throwable t) {
            failures.add(name + " 抛异常: " + t);
        }
        if (failures.size() == before) {
            passedCases++;
            System.out.println("PASS  " + name);
        } else {
            failedCases++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            failures.add(message);
        }
    }

    private static void eq(String what, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures.add(what + " 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void eq(String what, float expected, float actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.0001f) {
            failures.add(what + " 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void eq(String what, long expected, long actual) {
        checks++;
        if (expected != actual) {
            failures.add(what + " 期望 " + expected + "，实际 " + actual);
        }
    }
}
