package com.dsh.tvhud.ui;

/**
 * 带子（Day Tape）的几何：纯 Java，不依赖 Android。
 *
 * 全 App 只有一条带子，四个尺度（首页眉标 4dp / 使用记录 10dp / 报告周期条 / 宠物生长条）。
 * 这里只放「一天 24 小时 → 像素」的换算与刻度规则，绘制留在 {@link TapeView} 与
 * report / pet 各自的画布里——几何可被离线单测覆盖，视觉改动才不怕算错。
 */
public final class Tape {

    /** 一天的毫秒数：带子永远表示完整 24 小时。 */
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** 整点刻度数量：0..24 共 25 根。 */
    public static final int TICKS = 25;

    /** 每 3 小时一根带数字的长刻度。 */
    public static final int LABEL_STEP = 3;

    private Tape() {
    }

    /**
     * 某一时刻在带子上的位置，0..1。
     *
     * 规则：没有 dayStart（0 / 负数）时返回 0；早于当天或晚于 24 点都被夹到边界，
     * 保证游标永远落在轨道里。
     */
    public static float fraction(long ms, long dayStart) {
        if (dayStart <= 0L || ms <= dayStart) {
            return 0f;
        }
        long delta = ms - dayStart;
        if (delta >= DAY_MS) {
            return 1f;
        }
        return (float) delta / (float) DAY_MS;
    }

    /** 某一时刻对应的 x 坐标（逻辑像素）。 */
    public static float xAt(long ms, long dayStart, float left, float width) {
        return left + fraction(ms, dayStart) * width;
    }

    /** 第 hour 个小时刻度的 x 坐标（hour 0..24）。 */
    public static float hourX(int hour, float left, float width) {
        int h = hour < 0 ? 0 : (hour > 24 ? 24 : hour);
        return left + h / 24f * width;
    }

    /** 整点刻度是否带数字（每 3 小时）。 */
    public static boolean isMajorHour(int hour) {
        return hour % LABEL_STEP == 0;
    }

    /** 「现在」游标是否该画：没有当天起点，或还没到当天，就不画。 */
    public static boolean hasNow(long nowMs, long dayStart) {
        return dayStart > 0L && nowMs > dayStart;
    }

    /** 一段使用时间在带子上的起点；早于当天就夹到 0。 */
    public static float segStart(long segStartMs, long dayStart, float left, float width) {
        return xAt(segStartMs, dayStart, left, width);
    }

    /** 一段使用时间在带子上的终点；跨天时夹到右边界。 */
    public static float segEnd(long segEndMs, long dayStart, float left, float width) {
        return xAt(segEndMs, dayStart, left, width);
    }
}
