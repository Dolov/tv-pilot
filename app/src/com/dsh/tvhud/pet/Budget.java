package com.dsh.tvhud.pet;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 电视场次预算：默认 15 分钟一场、一天 3 场，到点由宠物拦截。
 *
 * 纯逻辑，不 import Android；持久化在 {@link PetStore}。所有时间由调用方传入。
 * 口径（见 docs/pet-design.md §4）：
 * - 只累计「视频」时间，中途暂停/关电视，本场剩余时间保留；
 * - 到点开闸门（gateOpen），休息记 1 场；答题通过按配置加时（次数按配置）；
 * - 两场之间有可配置的休息间隔（默认 30 分钟），间隔内继续看视频会被拦；
 * - 场次用完不再出题；家长时间不计数；
 * - 周末可按配置多开 N 场；允许时段外只拦「新开的一场」，不追溯已在看的这一场；
 * - 家长可以给「今天」发一张免限卡（freePassDay）：当天不拦时段、场次也放宽，
 *   跨天自动失效（比对的是当天 0 点的时间戳，不用定时器）。
 */
public final class Budget {
    // ---- 每场时长：5 分钟 .. 3 小时，默认 15 分钟 ----
    public static final int MIN_SESSION_MINUTES = 5;
    public static final int MAX_SESSION_MINUTES = 180;
    public static final int DEFAULT_SESSION_MINUTES = 15;
    // ---- 每天场次：1 .. 24，默认 3 ----
    public static final int MIN_SESSIONS = 1;
    public static final int MAX_SESSIONS = 24;
    public static final int DEFAULT_MAX_SESSIONS = 3;
    // ---- 周末加场：0（关） .. 24，默认 5 ----
    public static final int MIN_WEEKEND_EXTRA = 0;
    public static final int MAX_WEEKEND_EXTRA = 24;
    public static final int DEFAULT_WEEKEND_EXTRA = 5;
    // ---- 答题续时：每次 5 分钟 .. 3 小时，默认 5 分钟；每天 1 .. 24 次，默认 2 次 ----
    public static final int MIN_EXTENSION_MINUTES = 5;
    public static final int MAX_EXTENSION_MINUTES = 180;
    public static final int DEFAULT_EXTENSION_MINUTES = 5;
    public static final int MIN_EXTENSIONS = 1;
    public static final int MAX_EXTENSIONS = 24;
    public static final int DEFAULT_EXTENSIONS = 2;
    // ---- 两场之间的休息间隔：5 分钟 .. 12 小时，默认 30 分钟 ----
    public static final int MIN_REST_MINUTES = 5;
    public static final int MAX_REST_MINUTES = 12 * 60;
    public static final int DEFAULT_REST_MINUTES = 30;
    /** 家长「临时加场」当天能加的上限（与每天场次上限一致，撤回不另设下限 = 0）。 */
    public static final int MAX_EXTRA_SESSIONS = 24;

    /** 闸门原因：到点 / 不在允许时段 / 两场之间的休息间隔。 */
    public static final int GATE_NORMAL = 0;
    public static final int GATE_WINDOW = 1;
    public static final int GATE_INTERVAL = 2;

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
    /** 每次答题续时的分钟数。 */
    public int extensionMinutes = DEFAULT_EXTENSION_MINUTES;
    public boolean quizEnabled = true;
    /** 周末多开的场次数（0 = 周末不加场）。 */
    public int weekendExtra = DEFAULT_WEEKEND_EXTRA;
    /** 两场之间的休息间隔（分钟）。 */
    public int restIntervalMinutes = DEFAULT_REST_MINUTES;
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
        maxSessions = effectiveMaxSessions(configuredMaxSessions, weekendExtra, todayStart);
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
        if (deltaMs <= 0L || parentMode) {
            return;
        }
        if (gateOpen) {
            // 间隔闸门是唯一会「自己到点」的闸门：休息结束就收掉，别把孩子卡在旧状态里
            if (gateReason == GATE_INTERVAL && !inRest(now)) {
                gateOpen = false;
                gateReason = GATE_NORMAL;
            } else {
                return;
            }
        }
        // 免限卡：今天不拦（时段不拦、场次也不拦），只累计时间让宠物知道孩子在看
        if (hasFreePass(now)) {
            sessionMs += deltaMs;
            return;
        }
        if (sessionsUsed >= maxSessions) {
            return;
        }
        // 两场之间：上一场已经结束，休息间隔没到就不累计，交给拦截页显示倒计时
        if (inRest(now)) {
            gateReason = GATE_INTERVAL;
            gateOpen = true;
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
                && gateReason != GATE_INTERVAL
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
        restUntil = now + restIntervalMinutes * 60000L;
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
        extensionMs += extensionMinutes * 60000L;
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
        restUntil = System.currentTimeMillis() + restIntervalMinutes * 60000L;
    }

    /** 家长临时加一场（当天有效）；已到 {@link #MAX_EXTRA_SESSIONS} 时返回 false。 */
    public boolean addSession() {
        if (extraSessions >= MAX_EXTRA_SESSIONS) {
            return false;
        }
        extraSessions++;
        maxSessions++;
        return true;
    }

    /**
     * 家长撤回一场今天临时加的场次（设置页 ← 键）。
     *
     * 三条边界：已经撤到 0 就不再退；撤完不能让上限低于「今天已经用掉的场次」
     * （用掉的场不能凭空退回来）；只动家长加的临时场，不改基础配置。
     */
    public boolean removeSession() {
        if (extraSessions <= 0) {
            return false;
        }
        if (maxSessions - 1 < sessionsUsed) {
            return false;
        }
        extraSessions--;
        maxSessions--;
        return true;
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

    /** 今天有效的场次上限：周末多开 N 场（0 = 不加），再加家长临时加场。 */
    public static int effectiveMaxSessions(int configured, int weekendExtra, long now) {
        int n = clamp(configured, MIN_SESSIONS, MAX_SESSIONS);
        if (isWeekend(now)) {
            n += clamp(weekendExtra, MIN_WEEKEND_EXTRA, MAX_WEEKEND_EXTRA);
        }
        return n;
    }

    /** 配置值夹到合法区间；读旧存档/调试广播时用。 */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** 分钟数的中文短写法：15 分钟 / 1 小时 / 1 小时 30 分 / 12 小时。 */
    public static String formatMinutes(int minutes) {
        int m = Math.max(0, minutes);
        int h = m / 60;
        int rest = m % 60;
        if (h == 0) {
            return rest + " 分钟";
        }
        if (rest == 0) {
            return h + " 小时";
        }
        return h + " 小时 " + rest + " 分";
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
