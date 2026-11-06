package com.dsh.tvhud.pet;

import com.dsh.tvhud.usage.UsageDay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 宠物核心逻辑的纯 Java 单测：PetState / PetEngine / Budget / QuizBank。
 * 不依赖 Android、不需要电视；stubs 见 tests/java/stubs。
 */
public final class PetCoreTest {
    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    public static void main(String[] args) {
        runCase("等级与阶段：EXP 30/100/240/450 对应 Lv 3/10/24/45", new Body() {
            @Override
            public void run() {
                PetState s = new PetState();
                eq("初始蛋", 1, s.level());
                eq("初始阶段", PetState.STAGE_EGG, s.stage());
                s.exp = 30;
                eq("Lv3", 3, s.level());
                eq("幼年", PetState.STAGE_BABY, s.stage());
                s.exp = 100;
                eq("Lv10", 10, s.level());
                eq("少年", PetState.STAGE_TEEN, s.stage());
                s.exp = 240;
                eq("Lv24", 24, s.level());
                eq("成年", PetState.STAGE_ADULT, s.stage());
                s.exp = 450;
                eq("Lv45", 45, s.level());
                eq("完全体", PetState.STAGE_FINAL, s.stage());
            }
        });

        runCase("照顾：首次才给经验，重复只改状态；五项做完加星", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                PetEngine.Result r = PetEngine.care(s, PetEngine.ACTION_EAT, now());
                check("首次吃饭给经验", r.firstCare);
                eq("经验 +1", 1, s.exp);
                eq("体重 +4", 54, s.weight);
                int expAfter = s.exp;
                PetEngine.care(s, PetEngine.ACTION_EAT, now());
                eq("重复吃饭不再加经验", expAfter, s.exp);
                eq("重复吃饭只 +1 体重", 55, s.weight);
                eq("照顾位只记一次", 1, s.careTodayCount);

                PetEngine.care(s, PetEngine.ACTION_DRINK, now());
                PetEngine.care(s, PetEngine.ACTION_PET, now());
                PetEngine.care(s, PetEngine.ACTION_BATH, now());
                PetEngine.Result last = PetEngine.care(s, PetEngine.ACTION_BRUSH, now());
                check("五项做完", last.allCareDone);
                eq("星星 +1", 1, s.stars);
                eq("五项经验 = 5 + 3 = 8", 8, s.exp);
                check("牙齿变亮", s.teeth > 80);
            }
        });

        runCase("衰减：按小时扣需求值，24 小时封顶，不归零", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                s.lastSettleMs = now() - 2L * 3600000L;
                int hunger = s.hunger, thirst = s.thirst, mood = s.mood, energy = s.energy;
                PetEngine.settle(s, now());
                eq("2 小时饥饿 -6", hunger - 6, s.hunger);
                eq("2 小时口渴 -10", thirst - 10, s.thirst);
                eq("2 小时心情 -1", mood - 1, s.mood);
                eq("2 小时精力 +10", energy + 10, s.energy);
                s.lastSettleMs = now() - 48L * 3600000L;
                PetEngine.settle(s, now());
                check("长时间不归零", s.hunger >= 0 && s.thirst >= 0);
                eq("最多按 24 小时算", 0, s.hunger);
            }
        });

        runCase("答题：2/3 算过，全对额外星星", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                PetEngine.Result r = PetEngine.onQuiz(s, 2, 3, now());
                check("2/3 通过", r.quizPassed);
                eq("参与 +1 经验", 1, s.exp);
                eq("没全对没星", 0, s.stars);
                PetEngine.Result r2 = PetEngine.onQuiz(s, 3, 3, now());
                check("3/3 通过", r2.quizPassed);
                eq("全对 +3 经验", 4, s.exp);
                eq("全对 +1 星", 1, s.stars);
                eq("答题计数", 2, s.quizToday);
            }
        });

        runCase("每日结算：达标 +8，超时只 +2 且清零连续天数", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                PetEngine.Result ok = PetEngine.settleDay(s, 3, 3, false, dayStart());
                check("达标", ok.compliant);
                eq("达标成长", 8, ok.growth);
                eq("连续 1 天", 1, s.streakDays);
                PetEngine.Result bad = PetEngine.settleDay(s, 2, 3, true, dayStart());
                check("超时不算达标", !bad.compliant);
                eq("超时成长", 2, bad.growth);
                eq("连续清零", 0, s.streakDays);
            }
        });

        runCase("连续奖励：3 天 +5，7 天 +10", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                int growth3 = 0;
                for (int i = 0; i < 3; i++) {
                    growth3 = PetEngine.settleDay(s, 3, 3, false, dayStart()).growth;
                }
                eq("第 3 天奖励", 13, growth3); // 8 + 5
                int growth7 = 0;
                for (int i = 4; i <= 7; i++) {
                    growth7 = PetEngine.settleDay(s, 3, 3, false, dayStart()).growth;
                }
                eq("第 7 天奖励", 18, growth7); // 8 + 10
            }
        });

        runCase("场次预算：15 分钟到点、+5 续时、每天 2 次、3 场硬顶", new Body() {
            @Override
            public void run() {
                Budget b = new Budget();
                b.dayStart = dayStart();
                b.sessionMinutes = 15;
                b.onVideo(14L * 60000L);
                eq("14 分钟未到点", false, b.gateOpen);
                eq("剩 1 分钟提醒", 2, b.warnLevel());
                b.onVideo(60000L);
                eq("15 分钟到点", true, b.gateOpen);
                check("可以答题", b.canQuiz());
                check("答题通过", b.onQuizPass());
                eq("续时 5 分钟", 300000L, b.extensionMs);
                eq("闸门关闭", false, b.gateOpen);
                b.onVideo(300000L);
                eq("再 5 分钟又到点", true, b.gateOpen);
                check("第二次答题", b.onQuizPass());
                eq("用掉 2 次", 2, b.extensionsToday);
                check("不能第三次", !b.canQuiz());
                b.onRest(now());
                eq("休息记 1 场", 1, b.sessionsUsed);
                eq("本场清零", 0L, b.sessionMs);
                eq("续时清零", 0L, b.extensionMs);

                b.onVideo(15L * 60000L);
                b.onRest(now());
                b.onVideo(15L * 60000L);
                b.onRest(now());
                eq("3 场用完", true, b.isFinished());
                eq("剩余场次 0", 0, b.sessionsLeft());
                b.onVideo(15L * 60000L);
                eq("用完不再到点", false, b.gateOpen);
            }
        });

        runCase("场次预算：答题失败算 1 场；家长时间不计数", new Body() {
            @Override
            public void run() {
                Budget b = new Budget();
                b.dayStart = dayStart();
                b.onVideo(15L * 60000L);
                b.onQuizFail();
                eq("失败记 1 场", 1, b.sessionsUsed);
                eq("本场清零", 0L, b.sessionMs);
                b.parentMode = true;
                b.onVideo(20L * 60000L);
                eq("家长时间不计数", 0L, b.sessionMs);
                eq("家长时间不开闸", false, b.gateOpen);
                b.parentMode = false;
                b.onVideo(15L * 60000L);
                eq("恢复后正常到点", true, b.gateOpen);
            }
        });

        runCase("题库：解析、抽题、判分、非法题跳过", new Body() {
            @Override
            public void run() {
                String json = "{\"v\":1,\"items\":["
                        + "[\"m1\",\"math\",1,\"8 + 5 = ?\",\"8 加 5 等于几\",[\"11\",\"12\",\"13\",\"14\"],1],"
                        + "[\"m2\",\"math\",1,\"3 + 4 = ?\",\"3 加 4 等于几\",[\"5\",\"6\",\"7\",\"8\"],2],"
                        + "[\"bad\",\"math\",1,\"坏题\",\"坏题\",[\"1\"],0]"
                        + "]}";
                QuizBank bank = QuizBank.parse(json);
                eq("有效题 2 道", 2, bank.size());
                List<QuizBank.Question> pick = bank.pick(3);
                eq("不足时给全部", 2, pick.size());
                QuizBank.Question q = bank.all().get(0);
                check("正确答案判定", q.isCorrect(1));
                check("错误答案判定", !q.isCorrect(0));
                HashSet<String> recent = new HashSet<String>();
                recent.add("m1");
                List<QuizBank.Question> prefer = bank.pick(2, recent);
                eq("避开最近题", 2, prefer.size());
                check("优先非最近题", "m2".equals(prefer.get(0).id));
            }
        });

        runCase("换牙：Lv10 触发一次，两天后长回来", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                s.exp = 100;
                check("Lv10 可换牙", PetEngine.maybeStartTeething(s, now()));
                check("不会重复触发", !PetEngine.maybeStartTeething(s, now()));
                int teeth = s.teeth;
                // 把「上次结算」推到换牙结束前 1 秒，避免 24 小时衰减干扰牙齿断言
                s.lastSettleMs = now() + 2L * 86400000L - 1000L;
                PetEngine.settle(s, now() + 2L * 86400000L);
                check("换牙结束", s.teethingDone);
                eq("新牙回来", Math.min(100, teeth + 10), s.teeth);
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

    // ---------- 迷你断言 ----------

    private static long now() {
        return 1700000000000L;
    }

    private static long dayStart() {
        return UsageDay.dayStartOf(now());
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

    private static void eq(String what, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failedCases++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void eq(String what, long expected, long actual) {
        checks++;
        if (expected != actual) {
            failedCases++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void eq(String what, int expected, int actual) {
        checks++;
        if (expected != actual) {
            failedCases++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void eq(String what, boolean expected, boolean actual) {
        checks++;
        if (expected != actual) {
            failedCases++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
