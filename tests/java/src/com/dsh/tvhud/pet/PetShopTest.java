package com.dsh.tvhud.pet;

import com.dsh.tvhud.usage.UsageDay;

import java.util.ArrayList;
import java.util.List;

/**
 * P1/P2 纯逻辑单测：商店/衣柜/喂食、分支进化、退化、变异、宠物流水、周末场次、允许时段。
 * 和 PetCoreTest 一样不依赖 Android、不需要电视。
 */
public final class PetShopTest {
    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    private static final long DAY = 86400000L;

    public static void main(String[] args) {
        runCase("商店：等级门槛、星星不足、重复购买、原价退", new Body() {
            @Override
            public void run() {
                PetState s = new PetState();
                s.stars = 100;
                eq("Lv1 买帽子等级不够", Shop.ERR_LEVEL, Shop.buy(s, "hat_cap"));
                s.exp = 30; // Lv3
                eq("Lv3 买帽子成功", Shop.OK, Shop.buy(s, "hat_cap"));
                eq("扣 10 星", 90, s.stars);
                check("衣柜里有帽子", s.owns("hat_cap"));
                eq("不能重复买", Shop.ERR_OWNED, Shop.buy(s, "hat_cap"));
                s.stars = 5;
                eq("星星不够", Shop.ERR_NOT_ENOUGH, Shop.buy(s, "hat_beanie"));

                s.exp = 100;
                s.stars = 100;
                eq("买王冠", Shop.OK, Shop.buy(s, "hat_crown"));
                Shop.equip(s, "hat_crown");
                eq("穿上王冠", "crown", s.hat);
                check("退掉王冠成功", Shop.sell(s, "hat_crown"));
                eq("原价退回 25 星", 100, s.stars);
                check("退掉后衣柜没有", !s.owns("hat_crown"));
                eq("退掉后自动脱下", "", s.hat);
            }
        });

        runCase("衣柜：装备/切换/脱下；装饰最多摆 3 个，买后自动摆放", new Body() {
            @Override
            public void run() {
                PetState s = new PetState();
                s.exp = 100;
                s.stars = 500;
                eq("买 T 恤", Shop.OK, Shop.buy(s, "clothes_shirt"));
                eq("买斗篷", Shop.OK, Shop.buy(s, "clothes_cape"));
                check("穿上 T 恤", Shop.equip(s, "clothes_shirt"));
                eq("衣服槽", "shirt", s.clothes);
                check("换成斗篷", Shop.equip(s, "clothes_cape"));
                eq("衣服槽切换", "cape", s.clothes);
                check("脱下衣服", Shop.unequip(s, Shop.TYPE_CLOTHES));
                eq("衣服槽清空", "", s.clothes);

                eq("买盆栽", Shop.OK, Shop.buy(s, "decor_plant"));
                eq("买星星灯", Shop.OK, Shop.buy(s, "decor_lamp"));
                eq("买海报", Shop.OK, Shop.buy(s, "decor_poster"));
                eq("摆满 3 个", 3, s.decorList().length);
                eq("买鱼缸", Shop.OK, Shop.buy(s, "decor_fish"));
                eq("还是 3 个", 3, s.decorList().length);
                check("最早的盆栽被换下（物品还在衣柜）", !s.decorHas("plant"));
                check("盆栽仍在已购列表", s.owns("decor_plant"));
                check("鱼缸已摆上", s.decorHas("fish"));
                check("收起鱼缸", Shop.equip(s, "decor_fish"));
                check("鱼缸收起", !s.decorHas("fish"));
            }
        });

        runCase("喂食：扣星、加状态、首次吃饭算照顾，甜食扣牙齿", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                s.stars = 50;
                s.hunger = 40;
                s.weight = 50;
                eq("喂狗粮", Shop.feed(s, "food_kibble", now()), Shop.OK);
                eq("扣 5 星", 45, s.stars);
                check("饱食增加", s.hunger > 40);
                check("体重增加", s.weight > 50);
                eq("首次吃饭给经验", 1, s.exp);
                eq("喂食算照顾", 1, s.careTodayCount);
                int teeth = s.teeth;
                eq("喂甜食", Shop.feed(s, "food_candy", now()), Shop.OK);
                eq("甜食扣牙齿", Math.max(0, teeth - 15), s.teeth);
                s.stars = 0;
                eq("没星星不能喂", Shop.ERR_NOT_ENOUGH, Shop.feed(s, "food_snack", now()));
                eq("不存在的东西", Shop.ERR_UNKNOWN, Shop.buy(s, "hat_unknown"));
            }
        });

        runCase("分支：答题多→智慧，休息多→疾风，吃饭多→美食", new Body() {
            @Override
            public void run() {
                PetState wise = new PetState();
                wise.totalQuizCorrect = 20;
                wise.totalRest = 6;
                wise.totalEat = 2;
                eq("智慧线", PetState.BRANCH_WISDOM, PetEngine.decideBranch(wise));

                PetState swift = new PetState();
                swift.totalRest = 90;
                eq("疾风线", PetState.BRANCH_SWIFT, PetEngine.decideBranch(swift));

                PetState foodie = new PetState();
                foodie.totalEat = 30;
                foodie.totalRest = 6;
                eq("美食线", PetState.BRANCH_GOURMET, PetEngine.decideBranch(foodie));

                PetState s = new PetState();
                s.exp = 240; // Lv24 成年
                s.totalQuizCorrect = 12;
                PetEngine.settleDay(s, 3, 3, false, dayStart());
                eq("Lv24 锁定智慧线", PetState.BRANCH_WISDOM, s.branch);
                check("分支庆祝标记", s.celebrateEvolution);

                s.stars = 30;
                PetEngine.Result r = PetEngine.switchBranch(s, PetState.BRANCH_SWIFT, now());
                check("30 星换线成功", r.branchLocked);
                eq("换到疾风线", PetState.BRANCH_SWIFT, s.branch);
                eq("扣 30 星", 0, s.stars);
                check("星不够不能换", !PetEngine.switchBranch(s, PetState.BRANCH_GOURMET, now())
                        .branchLocked);
            }
        });

        runCase("退化：连续 3 天不达标外观回退，EXP 保底不掉阶，次日达标恢复", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                s.exp = 260; // 成年
                int expBefore = s.exp;
                for (int i = 0; i < 3; i++) {
                    PetEngine.settleDay(s, 0, 3, true, dayStart() + i * DAY);
                }
                check("触发退化", s.degradeUntil > 0L);
                eq("外观退回上一阶段", PetState.STAGE_TEEN, s.displayStage(dayStart()));
                check("EXP 有扣减", s.exp < expBefore);
                check("不掉阶（EXP 不低于成年门槛）", s.exp >= PetState.STAGE_EXP[PetState.STAGE_ADULT]);
                eq("阶段本身没变", PetState.STAGE_ADULT, s.stage());

                PetEngine.Result r = PetEngine.settleDay(s, 3, 3, false, dayStart() + 3 * DAY);
                check("达标当天恢复", r.recovered);
                eq("退化结束", 0L, s.degradeUntil);
                eq("外观恢复", PetState.STAGE_ADULT, s.displayStage(dayStart() + 3 * DAY));

                PetState off = PetEngine.newPet(now(), dayStart());
                off.exp = 260;
                for (int i = 0; i < 3; i++) {
                    PetEngine.settleDay(off, 0, 3, true, dayStart() + i * DAY, false);
                }
                eq("关掉退化机制不触发", 0L, off.degradeUntil);
            }
        });

        runCase("变异：Lv45 前不触发，首次只记时间，7 天后 force 必中且部位不同", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                s.exp = 240;
                check("未成年不触发", !PetEngine.checkMutation(s, now()));

                s.exp = 450;
                check("第一次只记录检定时间", !PetEngine.checkMutation(s, now()));
                check("7 天内不再检定", !PetEngine.checkMutation(s, now() + 6 * DAY));
                check("force 必中", PetEngine.forceMutation(s, now() + 7 * DAY));
                eq("变异次数 1", 1, s.mutationCount);
                check("两个部位不同", s.mutationPartA != s.mutationPartB);
                check("部位在 1..6", s.mutationPartA >= 1 && s.mutationPartA <= 6
                        && s.mutationPartB >= 1 && s.mutationPartB <= 6);
                eq("图鉴记 1 条", 1, s.mutationSeenCount());
                check("变异庆祝标记", s.celebrateEvolution);
            }
        });

        runCase("宠物流水：同日覆盖、保留 14 天、周报汇总正确率与超时", new Body() {
            @Override
            public void run() {
                String csv = "";
                for (int i = 0; i < 20; i++) {
                    PetJournal.Day d = new PetJournal.Day(dayStart() + i * DAY);
                    d.sessions = 3;
                    d.quizCorrect = i % 3;
                    d.quizTotal = 3;
                    d.overtime = i == 5;
                    d.expGain = 8;
                    csv = PetJournal.append(csv, d);
                }
                eq("最多保留 14 天", 14, PetJournal.parse(csv).size());

                String one = PetJournal.append("", new PetJournal.Day(dayStart()));
                PetJournal.Day d1 = PetJournal.find(one, dayStart());
                d1.sessions = 1;
                one = PetJournal.append(one, d1);
                d1 = PetJournal.find(one, dayStart());
                d1.sessions = 3;
                one = PetJournal.append(one, d1);
                eq("同日覆盖", 1, PetJournal.parse(one).size());
                eq("覆盖后的值", 3, PetJournal.find(one, dayStart()).sessions);

                String bad = "1700000000000|1|0|0|0|0|8;坏行;1700086400000|2|0|1|3|1|2";
                eq("坏行跳过", 2, PetJournal.parse(bad).size());

                PetJournal.Summary sum = PetJournal.summarize(one, dayStart());
                eq("汇总天数", 1, sum.days);
                eq("汇总场次", 3, sum.sessions);
                eq("汇总超时", 0, sum.overtimeDays);
                eq("没答题时正确率 -1", -1, PetJournal.summarize(one, dayStart()).quizRate());
            }
        });

        runCase("场次：周末可加 N 场（默认 5）、可关；家长临时加场", new Body() {
            @Override
            public void run() {
                long wed = dayStart();
                long sat = dayStart() + 3 * DAY; // 2023-11-18 周六
                eq("工作日不加场", 3, Budget.effectiveMaxSessions(3, 5, wed));
                eq("周六默认 +5", 8, Budget.effectiveMaxSessions(3, 5, sat));
                eq("周末关掉加场", 3, Budget.effectiveMaxSessions(3, 0, sat));
                eq("周末最多 +24", 27, Budget.effectiveMaxSessions(3, 24, sat));
                eq("场次上限 24", 24, Budget.effectiveMaxSessions(99, 0, wed));
                eq("周末加场夹到 24", 48, Budget.effectiveMaxSessions(24, 99, sat));

                Budget b = new Budget();
                b.dayStart = sat;
                b.configuredMaxSessions = 3;
                b.weekendExtra = 5;
                b.maxSessions = Budget.effectiveMaxSessions(3, 5, sat);
                eq("周末上限 8", 8, b.maxSessions);
                b.addSession();
                eq("临时加一场后 9", 9, b.maxSessions);
                eq("临时场次记录", 1, b.extraSessions);

                // 临时加场可加可退：退的是「还没用掉」的那部分，配置本身不动
                Budget add = new Budget();
                add.dayStart = wed;
                add.maxSessions = 3;
                eq("初始没有临时场", 0, add.extraSessions);
                check("加一场", add.addSession());
                eq("加后上限 4", 4, add.maxSessions);
                check("撤回一场", add.removeSession());
                eq("撤回后上限 3", 3, add.maxSessions);
                eq("撤回后临时场 0", 0, add.extraSessions);
                check("0 场时不能再撤", !add.removeSession());

                // 已经用掉的场次不能退：上限 = 已用时撤不动
                Budget used = new Budget();
                used.dayStart = wed;
                used.extraSessions = 1;
                used.maxSessions = 4;
                used.sessionsUsed = 3;
                check("还剩 1 场余量时可以退", used.removeSession());
                eq("退后上限 = 已用 3", 3, used.maxSessions);
                check("用满后退不动", !used.removeSession());

                Budget full = new Budget();
                full.extraSessions = 1;
                full.maxSessions = 4;
                full.sessionsUsed = 4;
                check("已用满时撤不回", !full.removeSession());

                // 当天临时加场有上限（24）
                Budget cap = new Budget();
                for (int i = 0; i < Budget.MAX_EXTRA_SESSIONS; i++) {
                    check("加第 " + (i + 1) + " 场", cap.addSession());
                }
                eq("加满 24 场上限", 3 + Budget.MAX_EXTRA_SESSIONS, cap.maxSessions);
                check("第 25 场被拒", !cap.addSession());

                Budget roll = new Budget();
                roll.dayStart = wed;
                roll.extraSessions = 2;
                roll.maxSessions = 5;
                roll.rollDay(wed + DAY);
                eq("跨天清掉临时场", 0, roll.extraSessions);
            }
        });

        runCase("允许时段：跨零点判断、时段外只拦新开场且不计场次", new Body() {
            @Override
            public void run() {
                long day = dayStart();
                long at1830 = day + 18L * 3600000L + 30L * 60000L;
                long at2100 = day + 21L * 3600000L;
                check("18:30 在时段内", Budget.inWindow(at1830, 17 * 60, 20 * 60));
                check("21:00 在时段外", !Budget.inWindow(at2100, 17 * 60, 20 * 60));
                check("跨零点 23:00", Budget.inWindow(day + 23L * 3600000L, 20 * 60, 7 * 60));
                check("跨零点 06:00", Budget.inWindow(day + 6L * 3600000L, 20 * 60, 7 * 60));
                check("跨零点 12:00 不在", !Budget.inWindow(day + 12L * 3600000L, 20 * 60, 7 * 60));
                eq("格式化 17:05", "17:05", Budget.formatMinute(17 * 60 + 5));

                Budget b = new Budget();
                b.dayStart = day;
                b.maxSessions = 3;
                b.windowEnabled = true;
                b.windowStartMin = 17 * 60;
                b.windowEndMin = 20 * 60;
                b.onVideo(60000L, at2100);
                check("时段外开闸", b.gateOpen);
                eq("原因是时段", Budget.GATE_WINDOW, b.gateReason);
                eq("不计本场时间", 0L, b.sessionMs);
                check("时段外不能答题", !b.canQuiz());
                b.onRest(at2100);
                eq("时段外休息不计场次", 0, b.sessionsUsed);
                check("闸门关闭", !b.gateOpen);

                b.onVideo(60000L, at1830);
                eq("时段内正常累计", 60000L, b.sessionMs);
                check("时段内不开闸", !b.gateOpen);
            }
        });

        runCase("躲壳：连续两天没照顾躲进壳里，一次互动恢复", new Body() {
            @Override
            public void run() {
                PetState s = PetEngine.newPet(now(), dayStart());
                PetEngine.settleDay(s, 0, 3, false, dayStart() + DAY);
                eq("第一天没照顾", 1, s.careMissDays);
                check("还没躲壳", !s.isShell());
                PetEngine.settleDay(s, 0, 3, false, dayStart() + 2 * DAY);
                check("两天没照顾躲壳", s.isShell());
                PetEngine.care(s, PetEngine.ACTION_PET, now());
                check("一次互动恢复", !s.isShell());
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
}
