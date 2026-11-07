package com.dsh.tvhud.pet;

import java.util.ArrayList;
import java.util.List;

/**
 * 家长设置 PIN 免输入窗口的纯逻辑单测：窗口长度、边界、锁定优先、未输过。
 * 不依赖 Android、不需要电视。
 */
public final class PinGraceTest {
    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    private static final long T = 1700000000000L;
    private static final long FIVE_MIN = 5L * 60L * 1000L;

    public static void main(String[] args) {
        runCase("窗口长度：输对后 5 分钟 = 300000ms，按绝对时间戳存", new Body() {
            @Override
            public void run() {
                eq("窗口 300000ms", 300000L, FIVE_MIN);
                eq("窗口常量", FIVE_MIN, PinGrace.WINDOW_MS);
                eq("截止 = 输对时刻 + 5 分钟", T + FIVE_MIN, PinGrace.okUntil(T));
            }
        });

        runCase("窗口判定：截止前 1ms 仍免输，到点即失效（边界）", new Body() {
            @Override
            public void run() {
                long until = PinGrace.okUntil(T);
                check("刚输完免输", PinGrace.active(until, T));
                check("4:59.999 免输", PinGrace.active(until, until - 1L));
                check("5:00.000 失效", !PinGrace.active(until, until));
                check("5:00.001 失效", !PinGrace.active(until, until + 1L));
            }
        });

        runCase("进入页判定：未输过要输；窗口内免输；锁定优先", new Body() {
            @Override
            public void run() {
                long until = PinGrace.okUntil(T);
                check("没输过要输", PinGrace.needPin(false, 0L, T));
                check("4 分钟时免输", !PinGrace.needPin(false, until, T + 4L * 60L * 1000L));
                check("5 分钟后要输", PinGrace.needPin(false, until, until));
                check("锁定中即使窗口也没过也要拦", PinGrace.needPin(true, until, T));
                check("锁定中且窗口过期也要拦", PinGrace.needPin(true, 0L, T));
            }
        });

        runCase("时钟回拨/重启：只看绝对截止时间，不因 now 回退而失效", new Body() {
            @Override
            public void run() {
                long until = PinGrace.okUntil(T);
                check("now 回退 1 秒仍在窗口内", PinGrace.active(until, T - 1000L));
                check("now 回退 10 分钟仍未过期（截止时刻还没到）",
                        PinGrace.active(until, T - 10L * 60L * 1000L));
                check("now 远未来必过期", !PinGrace.active(until, until + 86400000L));
            }
        });

        System.out.println("用例 PASS " + passedCases + "  FAIL " + failedCases
                + "（断言 " + checks + " 条）");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("  FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void runCase(String name, Body body) {
        int before = failedCases;
        try {
            body.run();
        } catch (Throwable t) {
            failedCases++;
            failures.add(name + "：抛异常 " + t);
        }
        if (failedCases == before) {
            passedCases++;
            System.out.println("  ok  " + name);
        } else {
            System.out.println("  FAIL " + name);
        }
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failedCases++;
            failures.add(what);
        }
    }

    private static void eq(String what, long expected, long actual) {
        checks++;
        if (expected != actual) {
            failedCases++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
