package com.dsh.tvhud.pet;

/**
 * 家长设置目录页的「规则摘要」纯逻辑：输入当前值，输出给家长看的一行事实。
 *
 * 为什么单独一个类：二级菜单的价值是「不进去也知道现在什么状态」，
 * 五组规则要被压成五句结论。这些判定和文案需要离线单测覆盖（边界：免限/家长时间/
 * 正在休息/场次用完/不在时段），页面只负责排版，不在这里之外再写一遍判断。
 *
 * 不 import Android，纯 Java；时间跨度判断复用 {@link Budget#inWindow}。
 */
public final class SettingsFacts {

    /** 今天的可用状态，判断优先级从高到低。 */
    public static final int TODAY_FREE_PASS = 0;
    public static final int TODAY_PARENT_TIME = 1;
    public static final int TODAY_RESTING = 2;
    public static final int TODAY_USED_UP = 3;
    public static final int TODAY_OUTSIDE = 4;
    public static final int TODAY_OK = 5;

    private SettingsFacts() {
    }

    /**
     * 今天的状态。优先级：免限 &gt; 家长时间 &gt; 正在休息（闸门已开）&gt; 场次用完 &gt;
     * 不在允许时段 &gt; 可以看。
     *
     * 免限最优先：今天不拦时段也不拦场次，其它状态都没有意义；
     * 家长时间同理（不计数、不拦截）；闸门已开表示「本场已经到点」，比场次用完更具体。
     */
    public static int todayState(boolean freePass, boolean parentMode, boolean gateOpen,
                                 int used, int max, boolean windowEnabled, boolean inWindow) {
        if (freePass) {
            return TODAY_FREE_PASS;
        }
        if (parentMode) {
            return TODAY_PARENT_TIME;
        }
        if (gateOpen) {
            return TODAY_RESTING;
        }
        if (used >= max) {
            return TODAY_USED_UP;
        }
        if (windowEnabled && !inWindow) {
            return TODAY_OUTSIDE;
        }
        return TODAY_OK;
    }

    /** 目录页「今天」右侧的摘要：一句话 + 场次进度。 */
    public static String todaySummary(int state, int used, int max) {
        int u = Math.max(0, used);
        int m = Math.max(u, max);
        String progress = u + "/" + m + " 场";
        switch (state) {
            case TODAY_FREE_PASS:
                return "今天免限 · 明天恢复";
            case TODAY_PARENT_TIME:
                return "家长时间 · 不计数";
            case TODAY_RESTING:
                return "已用 " + progress + " · 正在休息";
            case TODAY_USED_UP:
                return "已用 " + progress + " · 今天用完了";
            case TODAY_OUTSIDE:
                return "已用 " + progress + " · 不在允许时段";
            default:
                return "已用 " + progress + " · 现在可以看";
        }
    }

    /** 分组页顶部状态短语（不带场次进度，进度在标题左侧）。 */
    public static String todayStatus(int state) {
        switch (state) {
            case TODAY_FREE_PASS:
                return "今天免限";
            case TODAY_PARENT_TIME:
                return "家长时间";
            case TODAY_RESTING:
                return "正在休息";
            case TODAY_USED_UP:
                return "今天的场次用完了";
            case TODAY_OUTSIDE:
                return "不在允许时段";
            default:
                return "现在可以看";
        }
    }

    /** 目录页「时间与场次」的摘要：一屏看清每场多久、几场、间隔、周末加场与续时。 */
    public static String timeSummary(int sessionMinutes, int maxSessions, int weekendExtra,
                                     int restMinutes, boolean quizEnabled,
                                     int extensionMinutes, int extensionsPerDay) {
        StringBuilder sb = new StringBuilder();
        sb.append(Budget.formatMinutes(Budget.clamp(sessionMinutes,
                Budget.MIN_SESSION_MINUTES, Budget.MAX_SESSION_MINUTES))).append("/场");
        sb.append(" · 每天 ").append(Budget.clamp(maxSessions,
                Budget.MIN_SESSIONS, Budget.MAX_SESSIONS)).append(" 场");
        sb.append(" · 间隔 ").append(shortMinutes(Budget.clamp(restMinutes,
                Budget.MIN_REST_MINUTES, Budget.MAX_REST_MINUTES)));
        int extra = Budget.clamp(weekendExtra,
                Budget.MIN_WEEKEND_EXTRA, Budget.MAX_WEEKEND_EXTRA);
        if (extra > 0) {
            sb.append(" · 周末 +").append(extra).append(" 场");
        }
        if (quizEnabled) {
            sb.append(" · 续时 ").append(shortMinutes(Budget.clamp(extensionMinutes,
                    Budget.MIN_EXTENSION_MINUTES, Budget.MAX_EXTENSION_MINUTES))).append(" ×")
                    .append(Budget.clamp(extensionsPerDay,
                            Budget.MIN_EXTENSIONS, Budget.MAX_EXTENSIONS));
        }
        return sb.toString();
    }

    /** 摘要里的短写法：少于 1 小时用「N 分」，整小时用「N 小时」。 */
    private static String shortMinutes(int minutes) {
        int m = Math.max(0, minutes);
        if (m < 60) {
            return m + " 分";
        }
        if (m % 60 == 0) {
            return (m / 60) + " 小时";
        }
        return (m / 60) + " 小时 " + (m % 60) + " 分";
    }

    // ---------- 遥控器步进（←→ 改值）：范围、步长与回绕都在这里，页面只负责调用 ----------

    public static int stepSessionMinutes(int cur, int dir) {
        return step(cur, dir, Budget.MIN_SESSION_MINUTES, Budget.MAX_SESSION_MINUTES, 5);
    }

    public static int stepMaxSessions(int cur, int dir) {
        return step(cur, dir, Budget.MIN_SESSIONS, Budget.MAX_SESSIONS, 1);
    }

    public static int stepWeekendExtra(int cur, int dir) {
        return step(cur, dir, Budget.MIN_WEEKEND_EXTRA, Budget.MAX_WEEKEND_EXTRA, 1);
    }

    public static int stepExtensionMinutes(int cur, int dir) {
        return step(cur, dir, Budget.MIN_EXTENSION_MINUTES, Budget.MAX_EXTENSION_MINUTES, 5);
    }

    public static int stepExtensionsPerDay(int cur, int dir) {
        return step(cur, dir, Budget.MIN_EXTENSIONS, Budget.MAX_EXTENSIONS, 1);
    }

    /** 间隔从 5 分钟到 12 小时：近处 5 分钟一档，越远步长越大，避免按几十次。 */
    public static int stepRestMinutes(int cur, int dir) {
        int step = cur < 60 ? 5 : (cur < 180 ? 15 : 30);
        return step(cur, dir, Budget.MIN_REST_MINUTES, Budget.MAX_REST_MINUTES, step);
    }

    /** 在 [min,max] 内按 step 走一格；到边界回绕到另一端。 */
    static int step(int cur, int dir, int min, int max, int step) {
        int v = Budget.clamp(cur, min, max);
        int next = v + (dir < 0 ? -step : step);
        if (next < min) {
            return max;
        }
        if (next > max) {
            return min;
        }
        return next;
    }

    /** 目录页「允许时段」的摘要：关 = 全天可看；开 = 起止时间。 */
    public static String windowSummary(boolean enabled, int startMin, int endMin) {
        if (!enabled) {
            return "关闭 · 全天可看";
        }
        return Budget.formatMinute(startMin) + "–" + Budget.formatMinute(endMin);
    }

    /** 目录页「宠物与成长」的摘要。 */
    public static String petSummary(boolean degradeEnabled, boolean gentleMode) {
        return "退化 " + onOff(degradeEnabled) + " · 温和 " + onOff(gentleMode);
    }

    private static String onOff(boolean on) {
        return on ? "开" : "关";
    }
}
