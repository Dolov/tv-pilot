package com.dsh.tvhud.pet;

/**
 * 电视场次预算：15 分钟一场、一天 3 场，到点由宠物拦截。
 *
 * 纯逻辑，不 import Android；持久化在 {@link PetStore}。所有时间由调用方传入。
 * 口径（见 docs/pet-design.md §4）：
 * - 只累计「视频」时间，中途暂停/关电视，本场剩余时间保留；
 * - 到点开闸门（gateOpen），休息记 1 场，答题通过 +5 分钟（每天最多 2 次）；
 * - 3 场用完不再出题；家长时间不计数。
 */
public final class Budget {
    public static final int DEFAULT_SESSION_MINUTES = 15;
    public static final int DEFAULT_MAX_SESSIONS = 3;
    public static final int DEFAULT_EXTENSIONS = 2;
    public static final int EXTENSION_MINUTES = 5;
    public static final long REST_MS = 10L * 60000L;

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
    /** 家长时间：不计数、不拦截。 */
    public boolean parentMode;

    public int sessionMinutes = DEFAULT_SESSION_MINUTES;
    public int maxSessions = DEFAULT_MAX_SESSIONS;
    public int extensionsPerDay = DEFAULT_EXTENSIONS;
    public boolean quizEnabled = true;

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
        lastWarnLevel = 0;
        restUntil = 0L;
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
        if (gateOpen || parentMode || sessionsUsed >= maxSessions) {
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
        if (deltaMs <= 0L || parentMode || gateOpen || sessionsUsed >= maxSessions) {
            return;
        }
        sessionMs += deltaMs;
        if (sessionMs >= limitMs()) {
            sessionMs = limitMs();
            gateOpen = true;
        }
    }

    public boolean canQuiz() {
        return quizEnabled && !parentMode
                && extensionsToday < extensionsPerDay
                && sessionsUsed < maxSessions;
    }

    /** 休息：本场结束，计 1 场。返回 false 表示已经用完。 */
    public boolean onRest(long now) {
        if (sessionsUsed >= maxSessions) {
            gateOpen = false;
            return false;
        }
        sessionsUsed++;
        sessionMs = 0L;
        extensionMs = 0L;
        gateOpen = false;
        lastWarnLevel = 0;
        restUntil = now + REST_MS;
        return true;
    }

    /** 答题通过：本场 +5 分钟，闸门关闭，继续看。 */
    public boolean onQuizPass() {
        if (!canQuiz()) {
            gateOpen = false;
            return false;
        }
        extensionsToday++;
        extensionMs += EXTENSION_MINUTES * 60000L;
        gateOpen = false;
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
        lastWarnLevel = 0;
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
}
