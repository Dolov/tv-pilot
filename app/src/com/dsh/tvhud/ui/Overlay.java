package com.dsh.tvhud.ui;

/**
 * 覆盖层 token：屏保角标叠在任意电视画面上，不能跟随主题。
 *
 * 只保证可读性：白字 + 黑投影 + 固定的轨道/游标透明度；
 * 唯一跟随设置的是「夜间调暗」。
 */
public final class Overlay {

    public static final int TEXT = 0xFFF4EEE3;
    public static final int TEXT_DIM = 0xFFC6BFB2;
    public static final int SHADOW = 0xCC000000;
    public static final int TRACK = 0x1AFFFFFF;
    public static final int NOW = 0x66FFFFFF;
    public static final int WARN = 0xFFFF7B72;

    private Overlay() {
    }

    /** 夜间调暗：只压覆盖层，不改主题。 */
    public static int dimAlpha(boolean dim) {
        return dim ? 184 : 255;
    }
}
