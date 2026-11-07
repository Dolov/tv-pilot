package com.dsh.tvhud.pet;

import com.dsh.tvhud.usage.UsageDay;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 宠物存档：SharedPreferences("pet") 逐字段读写 + 跨天结算。
 *
 * 为什么不塞 JSON：这是纯 Java 小 App，逐字段读写更直观，也避免和归档 JSON 的 schema
 * 混淆。纯逻辑仍然在 {@link PetEngine} / {@link Budget} / {@link Shop}，这里只做 Android IO。
 */
public final class PetStore {
    public static final String PREFS = "pet";
    public static final String DEFAULT_PIN = "2580";

    private static final String K_SCHEMA = "state_schema";
    private static final String K_DAY = "state_day";
    private static final String K_EXP = "exp";
    private static final String K_STARS = "stars";
    private static final String K_HUNGER = "hunger";
    private static final String K_THIRST = "thirst";
    private static final String K_MOOD = "mood";
    private static final String K_ENERGY = "energy";
    private static final String K_WEIGHT = "weight";
    private static final String K_TEETH = "teeth";
    private static final String K_CARE_MASK = "care_mask";
    private static final String K_CARE_COUNT = "care_count";
    private static final String K_QUIZ_TODAY = "quiz_today";
    private static final String K_QUIZ_CORRECT = "quiz_correct";
    private static final String K_TOTAL_CARE = "total_care";
    private static final String K_TOTAL_QUIZ = "total_quiz";
    private static final String K_TOTAL_REST = "total_rest";
    private static final String K_TOTAL_EAT = "total_eat";
    private static final String K_STARS_EARNED = "stars_earned";
    private static final String K_STREAK = "streak";
    private static final String K_BAD_DAYS = "bad_days";
    private static final String K_CARE_MISS = "care_miss";
    private static final String K_DEGRADE_UNTIL = "degrade_until";
    private static final String K_DEGRADE_STAGE = "degrade_stage";
    private static final String K_BRANCH = "branch";
    private static final String K_MUT_A = "mutation_a";
    private static final String K_MUT_B = "mutation_b";
    private static final String K_MUT_PAL = "mutation_palette";
    private static final String K_MUT_COUNT = "mutation_count";
    private static final String K_MUT_SEEN = "mutation_seen";
    private static final String K_LAST_MUT = "last_mutation";
    private static final String K_CELEBRATE = "celebrate_evolution";
    private static final String K_OWNED = "owned";
    private static final String K_DECOR = "decor";
    private static final String K_LAST_SETTLE = "last_settle";
    private static final String K_LAST_SEEN = "last_seen";
    private static final String K_TEETHING_UNTIL = "teething_until";
    private static final String K_TEETHING_DONE = "teething_done";
    private static final String K_HAT = "hat";
    private static final String K_CLOTHES = "clothes";
    private static final String K_WINGS = "wings";

    private static final String K_BUDGET_DAY = "budget_day";
    private static final String K_SESSION_MS = "session_ms";
    private static final String K_SESSIONS_USED = "sessions_used";
    private static final String K_EXT_TODAY = "extensions_today";
    private static final String K_EXT_MS = "extension_ms";
    private static final String K_EXTRA_SESSIONS = "extra_sessions";
    private static final String K_GATE_OPEN = "gate_open";
    private static final String K_GATE_REASON = "gate_reason";
    /** 两场之间的休息截止时间（wall-clock 毫秒）。 */
    private static final String K_REST_UNTIL = "rest_until";
    private static final String K_PARENT_MODE = "parent_mode";
    /** 家长发的「今天免限卡」：存的是发卡那天的 0 点时间戳，跨天自动失效。 */
    private static final String K_FREE_PASS_DAY = "free_pass_day";

    private static final String K_CFG_SESSION_MIN = "cfg_session_minutes";
    private static final String K_CFG_MAX_SESSIONS = "cfg_max_sessions";
    private static final String K_CFG_QUIZ = "cfg_quiz_enabled";
    private static final String K_CFG_EXTENSIONS = "cfg_extensions";
    private static final String K_CFG_EXT_MINUTES = "cfg_extension_minutes";
    private static final String K_CFG_WEEKEND_EXTRA = "cfg_weekend_extra";
    private static final String K_CFG_REST_MINUTES = "cfg_rest_minutes";
    private static final String K_CFG_DEGRADE = "cfg_degrade";
    private static final String K_CFG_GENTLE = "cfg_gentle";
    private static final String K_CFG_WINDOW_ON = "cfg_window_on";
    private static final String K_CFG_WINDOW_START = "cfg_window_start";
    private static final String K_CFG_WINDOW_END = "cfg_window_end";
    private static final String K_CFG_PIN = "cfg_pin";
    private static final String K_JOURNAL = "journal";

    /** 同进程内 Activity 改存档后通知 Service 重新加载，避免两套内存状态打架。 */
    private static volatile long sVersion = 1L;

    private PetStore() {
    }

    public static long version() {
        return sVersion;
    }

    // ---------- 当天边界 ----------

    public static long todayStart() {
        return UsageDay.dayStartOf(System.currentTimeMillis());
    }

    /**
     * 跨天时先结算昨天、再滚到今天。服务、宠物房间、拦截页进入时都调一次。
     * 返回 true 表示发生了跨天结算。
     */
    public static boolean ensureDay(Context ctx, PetState state, Budget budget) {
        long now = System.currentTimeMillis();
        long today = UsageDay.dayStartOf(now);
        boolean rolled = false;
        if (budget.dayStart == 0L) {
            budget.dayStart = today;
            applyConfig(ctx, budget, today);
            rolled = true;
        } else if (budget.dayStart != today) {
            int used = budget.sessionsUsed;
            int cap = budget.maxSessions;
            boolean overtime = budget.gateOpen && budget.gateReason == Budget.GATE_NORMAL;
            long endedDay = budget.dayStart;
            int quizCorrect = state.quizCorrect;
            int quizTotal = state.quizToday;
            int expBefore = state.exp;
            PetEngine.Result r = PetEngine.settleDay(state, used, cap, overtime, today,
                    degradeEnabled(ctx));
            PetJournal.Day day = new PetJournal.Day(endedDay);
            day.sessions = used;
            day.extensions = budget.extensionsToday;
            day.quizCorrect = quizCorrect;
            day.quizTotal = quizTotal;
            day.overtime = overtime;
            day.expGain = state.exp - expBefore;
            appendJournal(ctx, day);
            state.rollDay(today);
            budget.rollDay(today);
            applyConfig(ctx, budget, today);
            rolled = true;
            if (r.degraded) {
                state.celebrateEvolution = true;
            }
        } else if (state.dayStart == 0L) {
            state.dayStart = today;
            rolled = true;
        } else if (state.dayStart != today) {
            PetEngine.settleDay(state, 0, budget.maxSessions, false, today, degradeEnabled(ctx));
            state.rollDay(today);
            rolled = true;
        }
        // 免限卡只管今天：跨天后旧卡必须从存档里删掉（它的 key 是独立的，
        // 不随 saveBudget 走，所以这里直接改 prefs）。
        if (freePassDay(ctx) != 0L && freePassDay(ctx) != today) {
            revokeFreePass(ctx);
            budget.freePassDay = 0L;
            rolled = true;
        }
        if (rolled) {
            PetEngine.settle(state, now);
            save(ctx, state, budget);
        }
        return rolled;
    }

    // ---------- 状态 ----------

    public static PetState loadState(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
        long now = System.currentTimeMillis();
        PetState s = new PetState();
        if (!sp.contains(K_SCHEMA)) {
            s = PetEngine.newPet(now, UsageDay.dayStartOf(now));
            return s;
        }
        s.schema = sp.getInt(K_SCHEMA, PetState.SCHEMA);
        s.dayStart = sp.getLong(K_DAY, UsageDay.dayStartOf(now));
        s.exp = sp.getInt(K_EXP, 0);
        s.stars = sp.getInt(K_STARS, 0);
        s.hunger = sp.getInt(K_HUNGER, 70);
        s.thirst = sp.getInt(K_THIRST, 70);
        s.mood = sp.getInt(K_MOOD, 70);
        s.energy = sp.getInt(K_ENERGY, 80);
        s.weight = sp.getInt(K_WEIGHT, 50);
        s.teeth = sp.getInt(K_TEETH, 80);
        s.careMask = sp.getInt(K_CARE_MASK, 0);
        s.careTodayCount = sp.getInt(K_CARE_COUNT, 0);
        s.quizToday = sp.getInt(K_QUIZ_TODAY, 0);
        s.quizCorrect = sp.getInt(K_QUIZ_CORRECT, 0);
        s.totalCare = sp.getInt(K_TOTAL_CARE, 0);
        s.totalQuizCorrect = sp.getInt(K_TOTAL_QUIZ, 0);
        s.totalRest = sp.getInt(K_TOTAL_REST, 0);
        s.totalEat = sp.getInt(K_TOTAL_EAT, 0);
        s.starsEarned = sp.getInt(K_STARS_EARNED, 0);
        s.streakDays = sp.getInt(K_STREAK, 0);
        s.badDays = sp.getInt(K_BAD_DAYS, 0);
        s.careMissDays = sp.getInt(K_CARE_MISS, 0);
        s.degradeUntil = sp.getLong(K_DEGRADE_UNTIL, 0L);
        s.degradeStage = sp.getInt(K_DEGRADE_STAGE, PetState.STAGE_EGG);
        s.branch = sp.getInt(K_BRANCH, PetState.BRANCH_NONE);
        s.mutationPartA = sp.getInt(K_MUT_A, 0);
        s.mutationPartB = sp.getInt(K_MUT_B, 0);
        s.mutationPalette = sp.getInt(K_MUT_PAL, 0);
        s.mutationCount = sp.getInt(K_MUT_COUNT, 0);
        s.mutationsSeen = sp.getString(K_MUT_SEEN, "");
        s.lastMutationCheck = sp.getLong(K_LAST_MUT, 0L);
        s.celebrateEvolution = sp.getBoolean(K_CELEBRATE, false);
        s.owned = sp.getString(K_OWNED, "");
        s.decor = sp.getString(K_DECOR, "");
        s.lastSettleMs = sp.getLong(K_LAST_SETTLE, now);
        s.lastSeenMs = sp.getLong(K_LAST_SEEN, now);
        s.teethingUntil = sp.getLong(K_TEETHING_UNTIL, 0L);
        s.teethingDone = sp.getBoolean(K_TEETHING_DONE, false);
        s.hat = sp.getString(K_HAT, "");
        s.clothes = sp.getString(K_CLOTHES, "");
        s.wings = sp.getString(K_WINGS, "");
        migrate(s);
        return s;
    }

    /** v1→v2：把 P0 已经穿在身上的装备补进衣柜，避免升级后衣服「消失」。 */
    private static void migrate(PetState s) {
        if (s.schema >= PetState.SCHEMA) {
            return;
        }
        seedOwned(s, s.hat, "hat_");
        seedOwned(s, s.clothes, "clothes_");
        seedOwned(s, s.wings, "wings_");
        if (s.starsEarned < s.stars) {
            s.starsEarned = s.stars;
        }
        s.schema = PetState.SCHEMA;
    }

    private static void seedOwned(PetState s, String slot, String prefix) {
        if (slot == null || slot.isEmpty()) {
            return;
        }
        s.addOwned(prefix + slot);
    }

    public static void saveState(Context ctx, PetState s) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, 0).edit();
        e.putInt(K_SCHEMA, PetState.SCHEMA);
        e.putLong(K_DAY, s.dayStart);
        e.putInt(K_EXP, s.exp);
        e.putInt(K_STARS, s.stars);
        e.putInt(K_HUNGER, s.hunger);
        e.putInt(K_THIRST, s.thirst);
        e.putInt(K_MOOD, s.mood);
        e.putInt(K_ENERGY, s.energy);
        e.putInt(K_WEIGHT, s.weight);
        e.putInt(K_TEETH, s.teeth);
        e.putInt(K_CARE_MASK, s.careMask);
        e.putInt(K_CARE_COUNT, s.careTodayCount);
        e.putInt(K_QUIZ_TODAY, s.quizToday);
        e.putInt(K_QUIZ_CORRECT, s.quizCorrect);
        e.putInt(K_TOTAL_CARE, s.totalCare);
        e.putInt(K_TOTAL_QUIZ, s.totalQuizCorrect);
        e.putInt(K_TOTAL_REST, s.totalRest);
        e.putInt(K_TOTAL_EAT, s.totalEat);
        e.putInt(K_STARS_EARNED, s.starsEarned);
        e.putInt(K_STREAK, s.streakDays);
        e.putInt(K_BAD_DAYS, s.badDays);
        e.putInt(K_CARE_MISS, s.careMissDays);
        e.putLong(K_DEGRADE_UNTIL, s.degradeUntil);
        e.putInt(K_DEGRADE_STAGE, s.degradeStage);
        e.putInt(K_BRANCH, s.branch);
        e.putInt(K_MUT_A, s.mutationPartA);
        e.putInt(K_MUT_B, s.mutationPartB);
        e.putInt(K_MUT_PAL, s.mutationPalette);
        e.putInt(K_MUT_COUNT, s.mutationCount);
        e.putString(K_MUT_SEEN, s.mutationsSeen == null ? "" : s.mutationsSeen);
        e.putLong(K_LAST_MUT, s.lastMutationCheck);
        e.putBoolean(K_CELEBRATE, s.celebrateEvolution);
        e.putString(K_OWNED, s.owned == null ? "" : s.owned);
        e.putString(K_DECOR, s.decor == null ? "" : s.decor);
        e.putLong(K_LAST_SETTLE, s.lastSettleMs);
        e.putLong(K_LAST_SEEN, s.lastSeenMs);
        e.putLong(K_TEETHING_UNTIL, s.teethingUntil);
        e.putBoolean(K_TEETHING_DONE, s.teethingDone);
        e.putString(K_HAT, s.hat == null ? "" : s.hat);
        e.putString(K_CLOTHES, s.clothes == null ? "" : s.clothes);
        e.putString(K_WINGS, s.wings == null ? "" : s.wings);
        e.apply();
        sVersion++;
    }

    // ---------- 预算 ----------

    public static Budget loadBudget(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
        Budget b = new Budget();
        b.dayStart = sp.getLong(K_BUDGET_DAY, 0L);
        b.sessionMs = sp.getLong(K_SESSION_MS, 0L);
        b.sessionsUsed = sp.getInt(K_SESSIONS_USED, 0);
        b.extensionsToday = sp.getInt(K_EXT_TODAY, 0);
        b.extensionMs = sp.getLong(K_EXT_MS, 0L);
        b.extraSessions = Budget.clamp(sp.getInt(K_EXTRA_SESSIONS, 0), 0,
                Budget.MAX_EXTRA_SESSIONS);
        b.gateOpen = sp.getBoolean(K_GATE_OPEN, false);
        b.gateReason = sp.getInt(K_GATE_REASON, Budget.GATE_NORMAL);
        b.restUntil = sp.getLong(K_REST_UNTIL, 0L);
        b.parentMode = sp.getBoolean(K_PARENT_MODE, false);
        // 免限卡是「家长设置」而不是「当天预算」：它有自己的 key，
        // saveBudget 不会写它，也就不会被旧的内存状态覆盖掉。
        b.freePassDay = sp.getLong(K_FREE_PASS_DAY, 0L);
        readConfig(ctx, b);
        long refDay = b.dayStart > 0L ? b.dayStart : System.currentTimeMillis();
        b.maxSessions = Budget.effectiveMaxSessions(b.configuredMaxSessions, b.weekendExtra, refDay)
                + b.extraSessions;
        return b;
    }

    public static void saveBudget(Context ctx, Budget b) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, 0).edit();
        e.putLong(K_BUDGET_DAY, b.dayStart);
        e.putLong(K_SESSION_MS, b.sessionMs);
        e.putInt(K_SESSIONS_USED, b.sessionsUsed);
        e.putInt(K_EXT_TODAY, b.extensionsToday);
        e.putLong(K_EXT_MS, b.extensionMs);
        e.putInt(K_EXTRA_SESSIONS, b.extraSessions);
        e.putBoolean(K_GATE_OPEN, b.gateOpen);
        e.putInt(K_GATE_REASON, b.gateReason);
        e.putLong(K_REST_UNTIL, b.restUntil);
        e.putBoolean(K_PARENT_MODE, b.parentMode);
        e.apply();
        sVersion++;
    }

    public static void save(Context ctx, PetState s, Budget b) {
        saveState(ctx, s);
        saveBudget(ctx, b);
    }

    /** 把家长配置灌进内存里的 Budget；跨天/改配置后调一次。 */
    public static void applyConfig(Context ctx, Budget b, long refDay) {
        readConfig(ctx, b);
        b.maxSessions = Budget.effectiveMaxSessions(b.configuredMaxSessions, b.weekendExtra, refDay)
                + b.extraSessions;
        if (b.maxSessions < b.sessionsUsed) {
            b.maxSessions = b.sessionsUsed;
        }
    }

    private static void readConfig(Context ctx, Budget b) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
        b.sessionMinutes = Budget.clamp(sp.getInt(K_CFG_SESSION_MIN, Budget.DEFAULT_SESSION_MINUTES),
                Budget.MIN_SESSION_MINUTES, Budget.MAX_SESSION_MINUTES);
        b.configuredMaxSessions = Budget.clamp(sp.getInt(K_CFG_MAX_SESSIONS,
                Budget.DEFAULT_MAX_SESSIONS), Budget.MIN_SESSIONS, Budget.MAX_SESSIONS);
        b.quizEnabled = sp.getBoolean(K_CFG_QUIZ, true);
        b.extensionsPerDay = Budget.clamp(sp.getInt(K_CFG_EXTENSIONS, Budget.DEFAULT_EXTENSIONS),
                Budget.MIN_EXTENSIONS, Budget.MAX_EXTENSIONS);
        b.extensionMinutes = Budget.clamp(sp.getInt(K_CFG_EXT_MINUTES,
                Budget.DEFAULT_EXTENSION_MINUTES), Budget.MIN_EXTENSION_MINUTES,
                Budget.MAX_EXTENSION_MINUTES);
        b.weekendExtra = Budget.clamp(sp.getInt(K_CFG_WEEKEND_EXTRA,
                Budget.DEFAULT_WEEKEND_EXTRA), Budget.MIN_WEEKEND_EXTRA, Budget.MAX_WEEKEND_EXTRA);
        b.restIntervalMinutes = Budget.clamp(sp.getInt(K_CFG_REST_MINUTES,
                Budget.DEFAULT_REST_MINUTES), Budget.MIN_REST_MINUTES, Budget.MAX_REST_MINUTES);
        b.windowEnabled = sp.getBoolean(K_CFG_WINDOW_ON, false);
        b.windowStartMin = sp.getInt(K_CFG_WINDOW_START, 17 * 60);
        b.windowEndMin = sp.getInt(K_CFG_WINDOW_END, 20 * 60);
    }

    // ---------- 设置 ----------

    public static int sessionMinutes(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0)
                .getInt(K_CFG_SESSION_MIN, Budget.DEFAULT_SESSION_MINUTES);
    }

    public static void setSessionMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_SESSION_MIN, Budget.clamp(minutes,
                        Budget.MIN_SESSION_MINUTES, Budget.MAX_SESSION_MINUTES)).apply();
        sVersion++;
    }

    public static int maxSessions(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0)
                .getInt(K_CFG_MAX_SESSIONS, Budget.DEFAULT_MAX_SESSIONS);
    }

    public static void setMaxSessions(Context ctx, int n) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_MAX_SESSIONS, Budget.clamp(n,
                        Budget.MIN_SESSIONS, Budget.MAX_SESSIONS)).apply();
        sVersion++;
    }

    public static boolean quizEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_CFG_QUIZ, true);
    }

    public static void setQuizEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_CFG_QUIZ, on).apply();
        sVersion++;
    }

    public static int extensionsPerDay(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0)
                .getInt(K_CFG_EXTENSIONS, Budget.DEFAULT_EXTENSIONS);
    }

    public static void setExtensionsPerDay(Context ctx, int n) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_EXTENSIONS, Budget.clamp(n,
                        Budget.MIN_EXTENSIONS, Budget.MAX_EXTENSIONS)).apply();
        sVersion++;
    }

    /** 每次答题续时的分钟数。 */
    public static int extensionMinutes(Context ctx) {
        return Budget.clamp(ctx.getSharedPreferences(PREFS, 0)
                        .getInt(K_CFG_EXT_MINUTES, Budget.DEFAULT_EXTENSION_MINUTES),
                Budget.MIN_EXTENSION_MINUTES, Budget.MAX_EXTENSION_MINUTES);
    }

    public static void setExtensionMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_EXT_MINUTES, Budget.clamp(minutes,
                        Budget.MIN_EXTENSION_MINUTES, Budget.MAX_EXTENSION_MINUTES)).apply();
        sVersion++;
    }

    /** 周末多开几场（0 = 不加场）。 */
    public static int weekendExtra(Context ctx) {
        return Budget.clamp(ctx.getSharedPreferences(PREFS, 0)
                        .getInt(K_CFG_WEEKEND_EXTRA, Budget.DEFAULT_WEEKEND_EXTRA),
                Budget.MIN_WEEKEND_EXTRA, Budget.MAX_WEEKEND_EXTRA);
    }

    public static void setWeekendExtra(Context ctx, int n) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_WEEKEND_EXTRA, Budget.clamp(n,
                        Budget.MIN_WEEKEND_EXTRA, Budget.MAX_WEEKEND_EXTRA)).apply();
        sVersion++;
    }

    /** 两场之间的休息间隔（分钟）。 */
    public static int restIntervalMinutes(Context ctx) {
        return Budget.clamp(ctx.getSharedPreferences(PREFS, 0)
                        .getInt(K_CFG_REST_MINUTES, Budget.DEFAULT_REST_MINUTES),
                Budget.MIN_REST_MINUTES, Budget.MAX_REST_MINUTES);
    }

    public static void setRestIntervalMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_REST_MINUTES, Budget.clamp(minutes,
                        Budget.MIN_REST_MINUTES, Budget.MAX_REST_MINUTES)).apply();
        sVersion++;
    }

    /** 退化机制总开关：敏感孩子可关；关掉后连续不达标只影响当天成长值。 */
    public static boolean degradeEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_CFG_DEGRADE, true);
    }

    public static void setDegradeEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_CFG_DEGRADE, on).apply();
        sVersion++;
    }

    /** 温和模式：不重复拉回拦截页（只提醒一次），建议和关退化一起用。 */
    public static boolean gentleMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_CFG_GENTLE, false);
    }

    public static void setGentleMode(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_CFG_GENTLE, on).apply();
        sVersion++;
    }

    public static boolean windowEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_CFG_WINDOW_ON, false);
    }

    public static void setWindowEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_CFG_WINDOW_ON, on).apply();
        sVersion++;
    }

    public static int windowStart(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getInt(K_CFG_WINDOW_START, 17 * 60);
    }

    public static int windowEnd(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getInt(K_CFG_WINDOW_END, 20 * 60);
    }

    public static void setWindow(Context ctx, int startMin, int endMin) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_WINDOW_START, clampMinute(startMin))
                .putInt(K_CFG_WINDOW_END, clampMinute(endMin))
                .apply();
        sVersion++;
    }

    private static int clampMinute(int m) {
        int v = m % 1440;
        return v < 0 ? v + 1440 : v;
    }

    public static boolean parentMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_PARENT_MODE, false);
    }

    public static void setParentMode(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_PARENT_MODE, on).apply();
        sVersion++;
    }

    // ---------- 今天免限卡 ----------

    /**
     * 家长给今天发一张免限卡：今天不拦允许时段、场次上限也不拦，跨天自动失效。
     *
     * 存的是「今天 0 点」的时间戳而不是「还有多久失效」：这样电视断电、重启、
     * 跨天后都自然恢复限制，不需要任何定时器去关。
     */
    public static void grantFreePass(Context ctx) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putLong(K_FREE_PASS_DAY, Budget.dayStartOf(System.currentTimeMillis()))
                .apply();
        sVersion++;
    }

    /** 收回今天的免限卡。 */
    public static void revokeFreePass(Context ctx) {
        ctx.getSharedPreferences(PREFS, 0).edit().putLong(K_FREE_PASS_DAY, 0L).apply();
        sVersion++;
    }

    /** 今天有没有免限卡（已经按当天 0 点比对过，跨天的旧卡返回 false）。 */
    public static boolean hasFreePass(Context ctx) {
        long day = ctx.getSharedPreferences(PREFS, 0).getLong(K_FREE_PASS_DAY, 0L);
        return day > 0L && day == Budget.dayStartOf(System.currentTimeMillis());
    }

    /**
     * 存档里记的免限卡是哪一天（0 = 没有）。
     * 不做「是不是今天」的判断：调用方要自己比对，因为跨天时它得负责清掉旧卡。
     */
    public static long freePassDay(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getLong(K_FREE_PASS_DAY, 0L);
    }

    public static String pin(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getString(K_CFG_PIN, DEFAULT_PIN);
    }

    public static void setPin(Context ctx, String pin) {
        if (pin == null || pin.length() != 4) {
            return;
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString(K_CFG_PIN, pin).apply();
        sVersion++;
    }

    // ---------- 宠物流水（周报） ----------

    public static String journal(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getString(K_JOURNAL, "");
    }

    public static void appendJournal(Context ctx, PetJournal.Day day) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putString(K_JOURNAL, PetJournal.append(journal(ctx), day)).apply();
        sVersion++;
    }

    // ---------- 题库 ----------

    public static QuizBank loadQuiz(Context ctx) {
        return QuizBank.parse(readAsset(ctx, "quiz.json"));
    }

    private static String readAsset(Context ctx, String name) {
        InputStream in = null;
        BufferedReader reader = null;
        try {
            in = ctx.getAssets().open(name);
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- 重置 ----------

    public static PetState reset(Context ctx) {
        PetState s = PetEngine.newPet(System.currentTimeMillis(), todayStart());
        Budget b = new Budget();
        b.dayStart = s.dayStart;
        applyConfig(ctx, b, b.dayStart);
        save(ctx, s, b);
        return s;
    }
}
