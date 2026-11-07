package com.dsh.tvhud.pet;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 电视场次预算：默认 15 分钟一场、一天 3 场，到点由宠物拦截。
 *
 * 纯逻辑，不 import Android；持久化在 {@link PetStore}。所有时间由调用方传入。
 * 口径（见 docs/pet-design.md §4）：
 * - 只累计「视频」时间，中途暂停/关电视，本场剩余时间保留；
 * - 到点开闸门（gateOpen），休息记 1 场，答题通过 +5 分钟（每天最多 N 次）；
 * - 场次用完不再出题；家长时间不计数；
 * - 周末默认多 1 场（可关）；允许时段外只拦「新开的一场」，不追溯已在看的这一场；
 * - 家长可以给「今天」发一张免限卡（freePassDay）：当天不拦时段、场次也放宽，
 *   跨天自动失效（比对的是当天 0 点的时间戳，不用定时器）。
 */
public final class Budget {
    public static final int DEFAULT_SESSION_MINUTES = 15;
    public static final int DEFAULT_MAX_SESSIONS = 3;
    public static final int DEFAULT_EXTENSIONS = 2;
    public static final int EXTENSION_MINUTES = 5;
    public static final long REST_MS = 10L * 60000L;

    /** 闸门原因：到点 / 不在允许时段。 */
    public static final int GATE_NORMAL = 0;
    public static final int GATE_WINDOW = 1;

    public long dayStart;
    /** 本场已累计的视频毫秒。 */
    public long sessionMs;
    /** 今天已经用掉的场次（休息或答题失败都算 1 场）。 */
    public int sessionsUsed;
    public int extensionsToday;
    /** 本场通过答题追加的毫秒。 */
    public long extensionMs;
    /** 到点闸门是否打开（等待孩子选择/答题）。 */
    public boolean gateOpen;
    /** 闸门原因（GATE_NORMAL / GATE_WINDOW）。 */
    public int gateReason = GATE_NORMAL;
    /** 家长时间：不计数、不拦截。 */
    public boolean parentMode;

    /**
     * 家长给今天的「免限卡」：当天 0 点的时间戳（0 = 没有）。
     * 有效时不拦允许时段、场次上限也不拦；跨天自动失效（存的是「哪一天」，不是「多久」）。
     */
    public long freePassDay;

    // 家长配置（PetSettingsActivity 写，PetStore 读）
    public int sessionMinutes = DEFAULT_SESSION_MINUTES;
    public int configuredMaxSessions = DEFAULT_MAX_SESSIONS;
    /** 家长临时加的场次，当天有效。 */
    public int extraSessions;
    public int extensionsPerDay = DEFAULT_EXTENSIONS;
    public boolean quizEnabled = true;
    public boolean weekendBonus = true;
    public boolean windowEnabled;
    /** 允许时段的分钟数（0..1439）。 */
    public int windowStartMin = 17 * 60;
    public int windowEndMin = 20 * 60;

    /** 今天的实际场次上限（配置 + 周末 + 家长临时加场）。 */
    public int maxSessions = DEFAULT_MAX_SESSIONS;

    /** 到点前提醒：0 无、1 剩 3 分钟、2 剩 1 分钟（由服务记录是否已提醒）。 */
    public int lastWarnLevel;
    public long restUntil;

    public void rollDay(long todayStart) {
        if (dayStart == todayStart) {
            return;
        }
        dayStart = todayStart;
        sessionMs = 0L;
        sessionsUsed = 0;
        extensionsToday = 0;
        extensionMs = 0L;
        gateOpen = false;
        gateReason = GATE_NORMAL;
        lastWarnLevel = 0;
        restUntil = 0L;
        extraSessions = 0;
        // 免限卡只管今天：跨天就作废（昨天发的卡不能带过来）
        freePassDay = 0L;
        maxSessions = effectiveMaxSessions(configuredMaxSessions, weekendBonus, todayStart);
    }

    public long limitMs() {
        return sessionMinutes * 60000L + extensionMs;
    }

    public long remainingMs() {
        return Math.max(0L, limitMs() - sessionMs);
    }

    /** 本场进度 0..1，给 UI 画环形/沙漏。 */
    public float progress() {
        long limit = limitMs();
        if (limit <= 0L) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, sessionMs / (float) limit));
    }

    public int warnLevel() {
        if (gateOpen || parentMode) {
            return 0;
        }
        // 有免限卡时不提醒「快到点了」：今天不拦，提醒了也没意义
        if (hasFreePass(System.currentTimeMillis()) || sessionsUsed >= maxSessions) {
            return 0;
        }
        long rem = remainingMs();
        if (rem <= 60000L) {
            return 2;
        }
        if (rem <= 180000L) {
            return 1;
        }
        return 0;
    }

    /** 累计一段视频时间；只在「正在看视频」且 tick 连续时调用。 */
    public void onVideo(long deltaMs) {
        onVideo(deltaMs, System.currentTimeMillis());
    }

    public void onVideo(long deltaMs, long now) {
        if (deltaMs <= 0L || parentMode || gateOpen) {
            return;
        }
        // 免限卡：今天不拦（时段不拦、场次也不拦），只累计时间让宠物知道孩子在看
        if (hasFreePass(now)) {
            sessionMs += deltaMs;
            return;
        }
        if (sessionsUsed >= maxSessions) {
            return;
        }
        // 允许时段：只拦「新开的一场」（本场还没累计时间时），不把正在看的动画片掐断
        if (windowEnabled && sessionMs <= 0L && !inWindow(now, windowStartMin, windowEndMin)) {
            gateReason = GATE_WINDOW;
            gateOpen = true;
            return;
        }
        gateReason = GATE_NORMAL;
        sessionMs += deltaMs;
        if (sessionMs >= limitMs()) {
            sessionMs = limitMs();
            gateOpen = true;
        }
    }

    /**
     * 今天的免限卡是否有效。
     *
     * 只认「今天」：freePassDay 是发卡那天的 0 点时间戳，跨天后自然对不上，
     * 不需要定时器去关，也就不会出现「忘了关、孩子多看一天」的情况。
     */
    public boolean hasFreePass(long now) {
        return freePassDay > 0L && freePassDay == dayStartOf(now);
    }

    /** 发一张今天的免限卡（当天有效，跨天自动失效）。 */
    public void grantFreePass(long now) {
        freePassDay = dayStartOf(now);
    }

    /** 收回免限卡。 */
    public void revokeFreePass() {
        freePassDay = 0L;
    }

    public boolean canQuiz() {
        return quizEnabled && !parentMode && gateReason != GATE_WINDOW
                && extensionsToday < extensionsPerDay
                && sessionsUsed < maxSessions;
    }

    /** 休息：本场结束，计 1 场。返回 false 表示已经用完。 */
    public boolean onRest(long now) {
        if (gateReason == GATE_WINDOW) {
            // 时段外被拦下来的，不算孩子看掉一场；清掉闸门即可
            gateOpen = false;
            gateReason = GATE_NORMAL;
            sessionMs = 0L;
            extensionMs = 0L;
            return true;
        }
        if (sessionsUsed >= maxSessions) {
            gateOpen = false;
            return false;
        }
        sessionsUsed++;
        sessionMs = 0L;
        extensionMs = 0L;
        gateOpen = false;
        gateReason = GATE_NORMAL;
        lastWarnLevel = 0;
        restUntil = now + REST_MS;
        return true;
    }

    /** 答题通过：本场 +5 分钟，闸门关闭，继续看。 */
    public boolean onQuizPass() {
        if (!canQuiz()) {
            gateOpen = false;
            gateReason = GATE_NORMAL;
            return false;
        }
        extensionsToday++;
        extensionMs += EXTENSION_MINUTES * 60000L;
        gateOpen = false;
        gateReason = GATE_NORMAL;
        lastWarnLevel = 0;
        return true;
    }

    /** 答题没过：本场结束，计 1 场。 */
    public void onQuizFail() {
        if (sessionsUsed < maxSessions) {
            sessionsUsed++;
        }
        sessionMs = 0L;
        extensionMs = 0L;
        gateOpen = false;
        gateReason = GATE_NORMAL;
        lastWarnLevel = 0;
    }

    /** 家长临时加一场（当天有效）。 */
    public void addSession() {
        extraSessions++;
        maxSessions++;
    }

    public int sessionsLeft() {
        return Math.max(0, maxSessions - sessionsUsed);
    }

    public boolean isFinished() {
        return sessionsUsed >= maxSessions;
    }

    /** 本场是不是今天最后一场（到点后不再出题）。 */
    public boolean isLastSession() {
        return sessionsUsed + 1 >= maxSessions;
    }

    public boolean inRest(long now) {
        return restUntil > now;
    }

    // ---------- 纯时间函数（可离线单测） ----------

    /**
     * 某一天 0 点的 wall-clock 毫秒（本地时区）。
     *
     * 为什么不在这里用 UsageDay.dayStartOf：那个类 import 了 Android 的 Context，
     * 而 Budget 要能被离线单测直接编出来（tests/java/run.sh 只编纯逻辑 + 桩）。
     * 这里用同样的算法（Calendar 归零时分秒），两边口径一致。
     */
    public static long dayStartOf(long wallMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 今天有效的场次上限：周末默认 +1，可被家长关掉；再加临时加场。 */
    public static int effectiveMaxSessions(int configured, boolean weekendBonus, long now) {
        int n = Math.max(1, configured);
        if (weekendBonus && isWeekend(now)) {
            n++;
        }
        return n;
    }

    public static boolean isWeekend(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
    }

    /** 允许时段（支持跨零点，如 20:00–07:00）。start == end 视为全天。 */
    public static boolean inWindow(long now, int startMin, int endMin) {
        if (startMin == endMin) {
            return true;
        }
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.setTimeInMillis(now);
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (startMin < endMin) {
            return minute >= startMin && minute < endMin;
        }
        return minute >= startMin || minute < endMin;
    }

    public static String formatMinute(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(java.util.Locale.CHINA, "%02d:%02d", m / 60, m % 60);
    }
}
