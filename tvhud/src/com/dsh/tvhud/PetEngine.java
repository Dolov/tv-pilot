package com.dsh.tvhud;

/**
 * 电子宠物的纯逻辑：衰减、照顾、答题奖励、每日结算、进化判定。
 *
 * 不 import Android，所有时间都由调用方传入墙钟毫秒，方便离线单测
 * （tests/java/src/com/dsh/tvhud/PetCoreTest.java）。
 */
public final class PetEngine {
    private static final long HOUR_MS = 3600000L;
    private static final long DAY_MS = 86400000L;

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
        public int growth;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (evolved) {
                sb.append("进化成 ").append(stageName(newStage)).append("！");
            } else if (leveled) {
                sb.append("升到 Lv ").append(newLevel).append("！");
            }
            if (allCareDone) {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                sb.append("今天的照顾全做完啦");
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
        switch (stage) {
            case PetState.STAGE_BABY:
                return "奶团兽";
            case PetState.STAGE_TEEN:
                return "闪电兽";
            case PetState.STAGE_ADULT:
                return "电视兽";
            case PetState.STAGE_FINAL:
                return "星翼兽";
            default:
                return "蛋";
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
            if (s.careMask == PetState.CARE_ALL) {
                s.stars += 1;
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
        finishExp(s, r);
        s.lastSeenMs = now;
        return r;
    }

    public static Result onSessionRest(PetState s, boolean auto, long now) {
        Result r = new Result();
        settle(s, now);
        beginExp(s, r);
        s.stars += auto ? 2 : 3;
        s.exp += auto ? 2 : 4;
        s.mood = clamp(s.mood + (auto ? 1 : 3));
        finishExp(s, r);
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
        }
        s.mood = clamp(s.mood + (pass ? 5 : -2));
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
        } else {
            s.streakDays = 0;
        }
        r.growth = growth;
        s.exp += growth;
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
        int after = stageOf(s.exp);
        return before != after;
    }

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
        r.leveled = r.newLevel != r.oldLevel;
        r.evolved = r.newStage != r.oldStage;
    }

    private static int stageOf(int exp) {
        int stage = PetState.STAGE_EGG;
        for (int i = 1; i < PetState.STAGE_EXP.length; i++) {
            if (exp >= PetState.STAGE_EXP[i]) {
                stage = i;
            }
        }
        return stage;
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

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
