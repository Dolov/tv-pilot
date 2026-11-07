package com.dsh.tvhud.ui;

/**
 * 对比度工具：WCAG 相对亮度比。
 *
 * 纯 Java，不依赖 Android；主题断言与预览工具共用。
 * 主题只能通过这里推导「表面上的文字色」，不允许手写反白字。
 */
public final class Contrast {

    private Contrast() {
    }

    /** 相对亮度 0..1。 */
    public static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** 两色的对比度，1..21。 */
    public static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /**
     * 在两种候选文字色里取对比度更高的一个。
     * 所有「实底表面」都走这里，不手写 onColor。
     */
    public static int on(int background, int candidateA, int candidateB) {
        return ratio(background, candidateA) >= ratio(background, candidateB)
                ? candidateA : candidateB;
    }

    /** 按 alpha（0..1）把颜色压到目标表面上。 */
    public static int alpha(int color, float alpha) {
        int a = Math.round(alpha * 255f);
        if (a < 0) {
            a = 0;
        }
        if (a > 255) {
            a = 255;
        }
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
