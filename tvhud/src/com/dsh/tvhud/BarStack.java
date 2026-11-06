package com.dsh.tvhud;

/**
 * 堆叠柱的几何计算：把「各类别时长」换算成每层在屏幕上的 [top, bottom) 像素。
 *
 * 周报「7 天节奏」、月报「每日趋势」、年报「全年走势」共用这一份换算，避免三处各写一遍
 * 后修好一处、漏掉两处（v1.13 前就出现过「偏移多除 60000、柱子只画最高一类」的 bug）。
 * 纯计算、不 import Android，可以直接单元测试（tests/java）。
 */
public final class BarStack {
    private BarStack() {
    }

    /**
     * @param bottom 柱底 y（像素）
     * @param plotH  绘图区高度（像素）
     * @param yMax   纵轴最大值（分钟），必须 &gt; 0
     * @param catMs  各类别时长（毫秒），按类别下标从下往上堆叠
     * @param tops   输出：每层顶边 y（长度必须 &gt;= catMs.length）
     * @param bots   输出：每层底边 y（长度必须 &gt;= catMs.length）
     * @return 整个柱子（各类别之和）的像素高度；数据为 0 时返回 0
     */
    public static float stack(float bottom, float plotH, float yMax, long[] catMs,
                             float[] tops, float[] bots) {
        int n = catMs.length;
        if (n > tops.length || n > bots.length) {
            throw new IllegalArgumentException("tops/bots 长度不足");
        }
        if (yMax <= 0f) {
            throw new IllegalArgumentException("yMax 必须 > 0");
        }
        float accMin = 0f;
        for (int i = 0; i < n; i++) {
            long ms = Math.max(0L, catMs[i]);
            if (ms <= 0L) {
                // 空类别不占高度，但也要把输出填好，方便调用方直接遍历
                tops[i] = bottom;
                bots[i] = bottom;
                continue;
            }
            float segH = plotH * (ms / 60000f) / yMax;
            float b = bottom - plotH * accMin / yMax;
            tops[i] = b - segH;
            bots[i] = b;
            accMin += ms / 60000f;
        }
        return plotH * accMin / yMax;
    }

    /** 各类别总时长（毫秒）。 */
    public static long total(long[] catMs) {
        long sum = 0L;
        for (int i = 0; i < catMs.length; i++) {
            sum += Math.max(0L, catMs[i]);
        }
        return sum;
    }
}
