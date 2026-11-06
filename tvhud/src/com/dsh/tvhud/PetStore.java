package com.dsh.tvhud;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 宠物存档：SharedPreferences("pet") 逐字段读写 + 跨天结算。
 *
 * 为什么不塞 JSON：这是纯 Java 小 App，逐字段读写更直观，也避免和归档 JSON 的 schema
 * 混淆。纯逻辑仍然在 {@link PetEngine} / {@link Budget}，这里只做 Android IO。
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
    private static final String K_STREAK = "streak";
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
    private static final String K_GATE_OPEN = "gate_open";
    private static final String K_PARENT_MODE = "parent_mode";

    private static final String K_CFG_SESSION_MIN = "cfg_session_minutes";
    private static final String K_CFG_MAX_SESSIONS = "cfg_max_sessions";
    private static final String K_CFG_QUIZ = "cfg_quiz_enabled";
    private static final String K_CFG_PIN = "cfg_pin";

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
            rolled = true;
        } else if (budget.dayStart != today) {
            PetEngine.settleDay(state, budget.sessionsUsed, budget.maxSessions,
                    budget.gateOpen, today);
            state.rollDay(today);
            budget.rollDay(today);
            rolled = true;
        } else if (state.dayStart == 0L) {
            state.dayStart = today;
            rolled = true;
        } else if (state.dayStart != today) {
            PetEngine.settleDay(state, 0, budget.maxSessions, false, today);
            state.rollDay(today);
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
        s.streakDays = sp.getInt(K_STREAK, 0);
        s.lastSettleMs = sp.getLong(K_LAST_SETTLE, now);
        s.lastSeenMs = sp.getLong(K_LAST_SEEN, now);
        s.teethingUntil = sp.getLong(K_TEETHING_UNTIL, 0L);
        s.teethingDone = sp.getBoolean(K_TEETHING_DONE, false);
        s.hat = sp.getString(K_HAT, "");
        s.clothes = sp.getString(K_CLOTHES, "");
        s.wings = sp.getString(K_WINGS, "");
        return s;
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
        e.putInt(K_STREAK, s.streakDays);
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
        b.gateOpen = sp.getBoolean(K_GATE_OPEN, false);
        b.parentMode = sp.getBoolean(K_PARENT_MODE, false);
        b.sessionMinutes = sp.getInt(K_CFG_SESSION_MIN, Budget.DEFAULT_SESSION_MINUTES);
        b.maxSessions = sp.getInt(K_CFG_MAX_SESSIONS, Budget.DEFAULT_MAX_SESSIONS);
        b.quizEnabled = sp.getBoolean(K_CFG_QUIZ, true);
        return b;
    }

    public static void saveBudget(Context ctx, Budget b) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, 0).edit();
        e.putLong(K_BUDGET_DAY, b.dayStart);
        e.putLong(K_SESSION_MS, b.sessionMs);
        e.putInt(K_SESSIONS_USED, b.sessionsUsed);
        e.putInt(K_EXT_TODAY, b.extensionsToday);
        e.putLong(K_EXT_MS, b.extensionMs);
        e.putBoolean(K_GATE_OPEN, b.gateOpen);
        e.putBoolean(K_PARENT_MODE, b.parentMode);
        e.apply();
        sVersion++;
    }

    public static void save(Context ctx, PetState s, Budget b) {
        saveState(ctx, s);
        saveBudget(ctx, b);
    }

    // ---------- 设置 ----------

    public static int sessionMinutes(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0)
                .getInt(K_CFG_SESSION_MIN, Budget.DEFAULT_SESSION_MINUTES);
    }

    public static void setSessionMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_SESSION_MIN, Math.max(5, Math.min(20, minutes))).apply();
        sVersion++;
    }

    public static int maxSessions(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0)
                .getInt(K_CFG_MAX_SESSIONS, Budget.DEFAULT_MAX_SESSIONS);
    }

    public static void setMaxSessions(Context ctx, int n) {
        ctx.getSharedPreferences(PREFS, 0).edit()
                .putInt(K_CFG_MAX_SESSIONS, Math.max(1, Math.min(6, n))).apply();
        sVersion++;
    }

    public static boolean quizEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_CFG_QUIZ, true);
    }

    public static void setQuizEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_CFG_QUIZ, on).apply();
        sVersion++;
    }

    public static boolean parentMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(K_PARENT_MODE, false);
    }

    public static void setParentMode(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_PARENT_MODE, on).apply();
        sVersion++;
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
        b.sessionMinutes = sessionMinutes(ctx);
        b.maxSessions = maxSessions(ctx);
        b.quizEnabled = quizEnabled(ctx);
        save(ctx, s, b);
        return s;
    }
}
