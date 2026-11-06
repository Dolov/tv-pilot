package com.dsh.tvhud.report;

/**
 * 柱状图纵轴刻度：把窗口内的最大时长（毫秒）换算成「每格多少分钟 + 轴最大值」。
 *
 * 为什么单独抽出来：周报 7 天节奏、月报每日趋势、年报 12 个月走势原本各写了一份
 * 阈值（30/60/120 与 120/240/480），改一处容易漏两处，而且没法离线断言。
 * 这里是纯计算、不 import Android，可直接在 tests/java 里测。
 */
public final class ChartAxis {
    /** 每格代表的分钟数。 */
    public final float step;
    /** 纵轴最大值（分钟），一定是 step 的整数倍。 */
    public final float yMax;

    private ChartAxis(float step, float yMax) {
        this.step = step;
        this.yMax = yMax;
    }

    /**
     * @param maxMs          窗口内最大时长（毫秒）
     * @param stepCandidates 候选步长（分钟，升序），取第一个「maxMin <= step * headroom」的
     * @param headroom       每档步长最多画几格；超过就换下一档
     */
    public static ChartAxis of(long maxMs, float[] stepCandidates, float headroom) {
        if (stepCandidates == null || stepCandidates.length == 0) {
            throw new IllegalArgumentException("stepCandidates 不能为空");
        }
        if (headroom <= 0f) {
            throw new IllegalArgumentException("headroom 必须 > 0");
        }
        float maxMin = Math.max(1f, maxMs / 60000f);
        float step = stepCandidates[stepCandidates.length - 1];
        for (int i = 0; i < stepCandidates.length; i++) {
            if (maxMin <= stepCandidates[i] * headroom) {
                step = stepCandidates[i];
                break;
            }
        }
        float yMax = (float) Math.ceil(maxMin / step) * step;
        return new ChartAxis(step, yMax);
    }
}
