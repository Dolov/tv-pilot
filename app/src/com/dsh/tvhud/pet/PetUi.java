package com.dsh.tvhud.pet;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * 宠物房间/商店/设置共用的样式小工具：颜色、圆角卡片、焦点态、字体、dp。
 *
 * 为什么单独一个类：P0 每个 Activity 各写一套 GradientDrawable，焦点态和配色容易漂；
 * 6 岁孩子看的界面要「大按钮 + 高对比 + 暖色焦点」，统一到这里改一处全生效。
 * 纯 UI，不参与离线单测。
 */
public final class PetUi {
    // 与 PetView 的夜空背景同一套色系：暖黄 + 湖蓝 + 珊瑚粉
    public static final int BG = 0xff0b0f1a;
    public static final int PANEL = 0xf0141b2a;
    public static final int PANEL_SOLID = 0xff141b2a;
    public static final int CARD = 0x22ffffff;
    public static final int CARD_HOVER = 0x33ffffff;
    public static final int FOCUS = 0xffffe27a;
    public static final int FOCUS_SOFT = 0x33ffe27a;
    public static final int TEXT = 0xfff6f8fb;
    public static final int TEXT_DIM = 0xff9aa7b2;
    public static final int TEXT_FAINT = 0xff697788;
    public static final int TEXT_DARK = 0xff0e1114;

    public static final int YELLOW = 0xffffe27a;
    public static final int ORANGE = 0xffffb74d;
    public static final int PINK = 0xffff9f8f;
    public static final int MINT = 0xff5fd6c9;
    public static final int BLUE = 0xff7ec8e3;
    public static final int LILAC = 0xffa99bff;
    public static final int GREEN = 0xff7ee0a3;
    public static final int RED = 0xffff8a80;
    public static final int STAR = 0xffffd54f;

    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    private static Typeface cjk;

    private PetUi() {
    }

    public static Typeface cjk(Context ctx) {
        if (cjk == null) {
            Typeface t = null;
            try {
                t = Typeface.createFromFile(CJK_FONT);
            } catch (Throwable ignored) {
            }
            cjk = t == null ? Typeface.DEFAULT : t;
        }
        return cjk;
    }

    public static int dp(Context ctx, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    public static int sp(Context ctx, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                ctx.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(Context ctx, int color, float radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, radiusDp));
        bg.setColor(color);
        return bg;
    }

    public static GradientDrawable roundStroke(Context ctx, int color, int strokeColor,
                                               float radiusDp, float strokeDp) {
        GradientDrawable bg = round(ctx, color, radiusDp);
        bg.setStroke(Math.max(1, dp(ctx, strokeDp)), strokeColor);
        return bg;
    }

    public static GradientDrawable pill(Context ctx, int color) {
        return round(ctx, color, 999f);
    }

    public static GradientDrawable circle(Context ctx, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    /** 基础文字：统一字体、不换行、居中/左对齐由调用方改。 */
    public static TextView text(Context ctx, CharSequence s, float sizeDp, int color, boolean bold) {
        TextView v = new TextView(ctx);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        v.setTextColor(color);
        v.setTypeface(cjk(ctx), bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setSingleLine(true);
        return v;
    }

    /**
     * 大卡片按钮：遥控器焦点从「深色半透明」变「暖黄底 + 深色字」，孩子一眼知道选到哪。
     * 尺寸按电视 3 米观看距离给足（≥ 120dp 宽、≥ 72dp 高由调用方布局保证）。
     */
    public static TextView cardButton(final Context ctx, final String label, float sizeDp) {
        final TextView v = text(ctx, label, sizeDp, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        v.setBackground(round(ctx, CARD, 18f));
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focus) {
                TextView tv = (TextView) view;
                if (focus) {
                    tv.setBackground(roundStroke(ctx, FOCUS, 0xffffffff, 18f, 3f));
                    tv.setTextColor(TEXT_DARK);
                } else {
                    tv.setBackground(round(ctx, CARD, 18f));
                    tv.setTextColor(TEXT);
                }
            }
        });
        return v;
    }

    /** 小圆角标签：等级、星星、状态用。 */
    public static TextView chip(Context ctx, String label, int textColor) {
        TextView v = text(ctx, label, 13f, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        v.setBackground(round(ctx, 0x26ffffff, 12f));
        return v;
    }

    /** 分区标题。 */
    public static TextView section(Context ctx, String label) {
        TextView v = text(ctx, label, 14f, TEXT_DIM, true);
        v.setLetterSpacing(0.08f);
        v.setPadding(0, dp(ctx, 6), 0, dp(ctx, 8));
        return v;
    }
}
