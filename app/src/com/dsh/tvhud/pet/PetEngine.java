package com.dsh.tvhud.pet;

/**
 * 电子宠物的纯逻辑：衰减、照顾、答题奖励、每日结算、进化/分支/变异/退化判定。
 *
 * 不 import Android，所有时间都由调用方传入墙钟毫秒，方便离线单测
 * （tests/java/src/com/dsh/tvhud/pet/PetCoreTest.java）。
 */
public final class PetEngine {
    private static final long HOUR_MS = 3600000L;
    private static final long DAY_MS = 86400000L;
    private static final long MUTATION_CHECK_MS = 7L * DAY_MS;

    // 照顾动作（不是 careMask 的 bit，调用方用这个枚举）
    public static final int ACTION_EAT = 1;
    public static final int ACTION_DRINK = 2;
    public static final int ACTION_PET = 3;
    public static final int ACTION_BATH = 4;
    public static final int ACTION_BRUSH = 5;
    public static final int ACTION_CANDY = 6;

    /** 一次操作后的变化，UI 用它决定播什么动画/提示。 */
    public static final class Result {
        public boolean leveled;
        public boolean evolved;
        public int oldLevel;
        public int newLevel;
        public int oldStage;
        public int newStage;
        public boolean firstCare;
        public boolean allCareDone;
        public boolean quizPassed;
        public boolean compliant;
        public boolean degraded;
        public boolean recovered;
        public boolean mutated;
        public boolean branchLocked;
        public int mutationA;
        public int mutationB;
        public int mutationPalette;
        public int branch;
        public int growth;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (mutated) {
                sb.append("变异啦！").append(PetState.mutationPartName(mutationA))
                        .append(" + ").append(PetState.mutationPartName(mutationB));
            } else if (evolved) {
                sb.append("进化成 ").append(PetState.stageName(newStage)).append("！");
            } else if (branchLocked) {
                sb.append("分叉成").append(branchName(branch)).append("啦！");
            } else if (leveled) {
                sb.append("升到 Lv ").append(newLevel).append("！");
            }
            if (recovered) {
                appendSpace(sb).append("精神回来啦");
            }
            if (degraded) {
                appendSpace(sb).append("有点累了，明天休息好就回来");
            }
            if (allCareDone) {
                appendSpace(sb).append("今天的照顾全做完啦");
            }
            return sb.toString();
        }
    }

    private PetEngine() {
    }

    public static PetState newPet(long now, long dayStart) {
        PetState s = new PetState();
        s.dayStart = dayStart;
        s.lastSettleMs = now;
        s.lastSeenMs = now;
        return s;
    }

    public static String stageName(int stage) {
        return PetState.stageName(stage);
    }

    public static String branchName(int branch) {
        switch (branch) {
            case PetState.BRANCH_WISDOM:
                return "智慧线";
            case PetState.BRANCH_SWIFT:
                return "疾风线";
            case PetState.BRANCH_GOURMET:
                return "美食线";
            default:
                return "";
        }
    }

    /** 按经过的小时衰减需求值；最多按 24 小时算，长时间不开电视不会「饿死」。 */
    public static void settle(PetState s, long now) {
        if (s.lastSettleMs <= 0L) {
            s.lastSettleMs = now;
            finishTeething(s, now);
            return;
        }
        long elapsed = now - s.lastSettleMs;
        if (elapsed < HOUR_MS) {
            finishTeething(s, now);
            return;
        }
        int hours = (int) Math.min(24L, elapsed / HOUR_MS);
        s.hunger = clamp(s.hunger - 3 * hours);
        s.thirst = clamp(s.thirst - 5 * hours);
        s.teeth = clamp(s.teeth - (hours / 2));
        int moodDrop = (s.hunger < 30 || s.thirst < 30) ? 2 * hours : hours / 2;
        s.mood = clamp(s.mood - moodDrop);
        s.energy = clamp(s.energy + 5 * hours);
        s.lastSettleMs = now;
        finishTeething(s, now);
    }

    private static void finishTeething(PetState s, long now) {
        if (s.teethingUntil > 0L && now >= s.teethingUntil) {
            s.teethingUntil = 0L;
            s.teeth = clamp(s.teeth + 10);
            // 长出新牙，心情也变好
            s.mood = clamp(s.mood + 5);
            s.teethingDone = true;
        }
    }

    /** 照顾一次：第一次做才算「今日照顾」，重复做只影响状态、不重复给经验。 */
    public static Result care(PetState s, int action, long now) {
        Result r = new Result();
        settle(s, now);
        beginExp(s, r);
        int bit = careBit(action);
        if (bit != 0 && (s.careMask & bit) == 0) {
            s.careMask |= bit;
            s.careTodayCount++;
            s.totalCare++;
            s.exp += 1;
            r.firstCare = true;
            if (bit == PetState.CARE_EAT) {
                s.totalEat++;
            }
            if (s.careMask == PetState.CARE_ALL) {
                s.stars += 1;
                s.starsEarned += 1;
                s.exp += 3;
                r.allCareDone = true;
            }
        }
        switch (action) {
            case ACTION_EAT:
                s.hunger = clamp(s.hunger + 30);
                s.weight = clamp(s.weight + (r.firstCare ? 4 : 1));
                s.mood = clamp(s.mood + 2);
                break;
            case ACTION_DRINK:
                s.thirst = clamp(s.thirst + 30);
                s.mood = clamp(s.mood + 1);
                break;
            case ACTION_PET:
                s.mood = clamp(s.mood + 10);
                s.energy = clamp(s.energy + 2);
                break;
            case ACTION_BATH:
                s.mood = clamp(s.mood + 5);
                s.energy = clamp(s.energy + 5);
                break;
            case ACTION_BRUSH:
                s.teeth = clamp(s.teeth + 30);
                s.mood = clamp(s.mood + 2);
                break;
            case ACTION_CANDY:
                s.mood = clamp(s.mood + 8);
                s.teeth = clamp(s.teeth - 15);
                s.weight = clamp(s.weight + 2);
                break;
            default:
                break;
        }
        s.careMissDays = 0;
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    /** 喂商店买的食物（狗粮/小零食/甜食）。星星已在 Shop.feed 扣掉。 */
    public static Result feed(PetState s, String food, long now) {
        Result r = new Result();
        settle(s, now);
        beginExp(s, r);
        if ((s.careMask & PetState.CARE_EAT) == 0) {
            s.careMask |= PetState.CARE_EAT;
            s.careTodayCount++;
            s.totalCare++;
            s.totalEat++;
            s.exp += 1;
            r.firstCare = true;
            if (s.careMask == PetState.CARE_ALL) {
                s.stars += 1;
                s.starsEarned += 1;
                s.exp += 3;
                r.allCareDone = true;
            }
        }
        if ("kibble".equals(food)) {
            s.hunger = clamp(s.hunger + 35);
            s.weight = clamp(s.weight + 4);
            s.mood = clamp(s.mood + 4);
        } else if ("snack".equals(food)) {
            s.hunger = clamp(s.hunger + 10);
            s.mood = clamp(s.mood + 12);
            s.weight = clamp(s.weight + 2);
        } else if ("candy".equals(food)) {
            s.mood = clamp(s.mood + 15);
            s.teeth = clamp(s.teeth - 15);
            s.weight = clamp(s.weight + 2);
        }
        s.careMissDays = 0;
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    public static Result onSessionRest(PetState s, boolean auto, long now) {
        Result r = new Result();
        settle(s, now);
        beginExp(s, r);
        int stars = auto ? 2 : 3;
        s.stars += stars;
        s.starsEarned += stars;
        s.totalRest++;
        s.exp += auto ? 2 : 4;
        s.mood = clamp(s.mood + (auto ? 1 : 3));
        s.careMissDays = 0;
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    /**
     * 允许时段外被拦下后「安静休息」：不给星星/成长（避免鼓励在非允许时段开电视），
     * 只让宠物心情好一点、清掉躲壳状态。不惩罚、不扣任何数值。
     */
    public static Result onQuietRest(PetState s, long now) {
        Result r = new Result();
        settle(s, now);
        s.mood = clamp(s.mood + 1);
        s.careMissDays = 0;
        s.lastSeenMs = now;
        return r;
    }

    public static Result onQuiz(PetState s, int correct, int total, long now) {
        Result r = new Result();
        settle(s, now);
        beginExp(s, r);
        s.quizToday++;
        s.quizCorrect += correct;
        s.totalQuizCorrect += correct;
        boolean pass = correct * 2 >= total; // 3 题对 2 过
        r.quizPassed = pass;
        s.exp += 1;
        if (correct == total) {
            s.exp += 2;
            s.stars += 1;
            s.starsEarned += 1;
        }
        s.mood = clamp(s.mood + (pass ? 5 : -2));
        s.careMissDays = 0;
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    /**
     * 跨天结算上一日：达标 +8，连续 3 天额外 +5、7 天额外 +10。
     * overtime 表示当天有没有「到点赖着不休息」——有就不算达标、连续天数清零。
     */
    public static Result settleDay(PetState s, int sessionsUsed, int maxSessions,
                                   boolean overtime, long todayStart) {
        return settleDay(s, sessionsUsed, maxSessions, overtime, todayStart, true);
    }

    public static Result settleDay(PetState s, int sessionsUsed, int maxSessions,
                                   boolean overtime, long todayStart, boolean degradeEnabled) {
        Result r = new Result();
        beginExp(s, r);
        boolean compliant = !overtime && sessionsUsed <= maxSessions;
        r.compliant = compliant;
        int growth = compliant ? 8 : 2;
        if (compliant) {
            s.streakDays++;
            if (s.streakDays > 0 && s.streakDays % 7 == 0) {
                growth += 10;
            } else if (s.streakDays > 0 && s.streakDays % 3 == 0) {
                growth += 5;
            }
            s.badDays = 0;
        } else {
            s.streakDays = 0;
            s.badDays++;
        }
        r.growth = growth;
        s.exp += growth;

        // 退化：连续 3 天不达标 → 外观退回上一阶段 2 天，EXP -20 但不掉阶
        if (!compliant && degradeEnabled && s.badDays >= 3 && s.stage() > PetState.STAGE_EGG) {
            s.badDays = 0;
            s.degradeStage = Math.max(PetState.STAGE_EGG, s.stage() - 1);
            s.degradeUntil = todayStart + PetState.DEGRADE_MS;
            int floor = PetState.STAGE_EXP[s.stage()];
            s.exp = Math.max(floor, s.exp - 20);
            r.degraded = true;
        }
        // 次日达标即恢复外观（不等 2 天自然结束）
        if (compliant && s.degradeUntil > 0L) {
            s.degradeUntil = 0L;
            r.recovered = true;
        }

        // 连续没进宠物房间：躲壳（一次互动即恢复）
        if (s.careTodayCount <= 0) {
            s.careMissDays++;
        } else {
            s.careMissDays = 0;
        }

        maybeLockBranch(s, r);
        maybeMutate(s, todayStart, false, r);
        finishExp(s, r);
        s.rollDay(todayStart);
        return r;
    }

    /**
     * 换牙事件：Lv 10 起触发一次，掉一颗牙，两天后长回来。
     * 对 6 岁孩子这是「我也在换牙」的共鸣点，不是惩罚。
     */
    public static boolean maybeStartTeething(PetState s, long now) {
        if (s.teethingDone || s.teethingUntil > 0L) {
            return false;
        }
        if (s.level() < 10) {
            return false;
        }
        s.teethingUntil = now + 2L * DAY_MS;
        s.teeth = clamp(s.teeth - 10);
        return true;
    }

    /** 由当前 EXP 重新确认阶段；返回是否有变化。 */
    public static boolean refreshStage(PetState s) {
        int before = s.stage();
        int after = PetState.stageOfExp(s.exp);
        return before != after;
    }

    // ---------- 分支 ----------

    /**
     * Lv24 时按累计偏好锁定分支：答题多→智慧，按时休息多→疾风，吃饭多→美食。
     * 只改外观，没有强弱；30 星可换线（{@link #switchBranch}）。
     */
    public static int decideBranch(PetState s) {
        int wisdom = s.totalQuizCorrect;
        int swift = s.totalRest / 3;
        int gourmet = s.totalEat;
        if (wisdom > swift && wisdom >= gourmet) {
            return PetState.BRANCH_WISDOM;
        }
        if (gourmet > swift && gourmet > wisdom) {
            return PetState.BRANCH_GOURMET;
        }
        return PetState.BRANCH_SWIFT;
    }

    private static void maybeLockBranch(PetState s, Result r) {
        if (s.branch != PetState.BRANCH_NONE || s.stage() < PetState.STAGE_ADULT) {
            return;
        }
        s.branch = decideBranch(s);
        s.celebrateEvolution = true;
        r.branchLocked = true;
        r.branch = s.branch;
    }

    /** 花 30 星换一条分支的外观；不降级、不影响数值。 */
    public static Result switchBranch(PetState s, int target, long now) {
        Result r = new Result();
        if (s.stage() < PetState.STAGE_ADULT || s.branch == PetState.BRANCH_NONE
                || target == s.branch || target < PetState.BRANCH_WISDOM
                || target > PetState.BRANCH_GOURMET) {
            return r;
        }
        if (s.stars < Shop.BRANCH_SWITCH_COST) {
            return r;
        }
        s.stars -= Shop.BRANCH_SWITCH_COST;
        s.branch = target;
        s.celebrateEvolution = true;
        r.branchLocked = true;
        r.branch = target;
        r.leveled = true;
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    // ---------- 变异 ----------

    /**
     * 完全体之后每 7 天检定一次：基础 15%，连续达标最多 +15%（封顶 30%）。
     * force = true 时（调试/图鉴保底）必中。变异只加外观，不替换已购物品。
     */
    public static boolean checkMutation(PetState s, long now) {
        return checkMutation(s, now, false);
    }

    public static boolean checkMutation(PetState s, long now, boolean force) {
        return maybeMutate(s, now, force, null);
    }

    private static boolean maybeMutate(PetState s, long now, boolean force, Result r) {
        if (s.stage() < PetState.STAGE_FINAL || s.mutationCount >= 12) {
            return false;
        }
        if (!force) {
            if (s.lastMutationCheck > 0L && now - s.lastMutationCheck < MUTATION_CHECK_MS) {
                return false;
            }
            if (s.lastMutationCheck <= 0L) {
                // 第一次检定从「刚长成完全体」开始算，7 天后才有机会
                s.lastMutationCheck = now;
                return false;
            }
        }
        int chance = 15 + 5 * Math.min(3, Math.max(0, s.streakDays));
        if (!force && nextRoll(s, now) % 100 >= chance) {
            s.lastMutationCheck = now;
            return false;
        }
        int a = 1 + (int) (nextRoll(s, now + 11L) % 6);
        int b = 1 + (int) (nextRoll(s, now + 29L) % 6);
        if (b == a) {
            b = b % 6 + 1;
        }
        s.mutationPartA = a;
        s.mutationPartB = b;
        s.mutationPalette = (int) (nextRoll(s, now + 43L) % 6);
        s.mutationCount++;
        s.mutationSeenAdd(a, b);
        s.lastMutationCheck = now;
        s.celebrateEvolution = true;
        if (r != null) {
            r.mutated = true;
            r.mutationA = a;
            r.mutationB = b;
            r.mutationPalette = s.mutationPalette;
        }
        return true;
    }

    /** 确定性伪随机：不依赖 java.util.Random，方便离线测试复现。 */
    private static long nextRoll(PetState s, long salt) {
        long x = (s.exp * 1103515245L + s.mutationCount * 12345L + salt
                + s.totalCare * 7919L + s.totalRest * 104729L) & 0x7fffffffL;
        x ^= (x >>> 13);
        x = (x * 1103515245L + 12345L) & 0x7fffffffL;
        return x;
    }

    /** 调试入口：直接给一个变异。 */
    public static boolean forceMutation(PetState s, long now) {
        return checkMutation(s, now, true);
    }

    // ---------- 内部 ----------

    /** 在改 exp 之前记录旧值；改完后调 {@link #finishExp}。 */
    private static void beginExp(PetState s, Result r) {
        r.oldLevel = s.level();
        r.oldStage = s.stage();
    }

    private static void finishExp(PetState s, Result r) {
        if (s.exp < 0) {
            s.exp = 0;
        }
        r.newLevel = s.level();
        r.newStage = s.stage();
        r.leveled = r.leveled || r.newLevel != r.oldLevel;
        r.evolved = r.newStage != r.oldStage;
        if (r.evolved) {
            s.celebrateEvolution = true;
        }
        maybeLockBranch(s, r);
    }

    private static int careBit(int action) {
        switch (action) {
            case ACTION_EAT:
                return PetState.CARE_EAT;
            case ACTION_DRINK:
                return PetState.CARE_DRINK;
            case ACTION_PET:
                return PetState.CARE_PET;
            case ACTION_BATH:
                return PetState.CARE_BATH;
            case ACTION_BRUSH:
                return PetState.CARE_BRUSH;
            default:
                return 0;
        }
    }

    private static StringBuilder appendSpace(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append("  ");
        }
        return sb;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
