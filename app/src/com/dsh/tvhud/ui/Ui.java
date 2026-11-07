package com.dsh.tvhud.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * 安卓侧的小工具：dp / 字体 / 文本 / 圆角 / 分割线。
 *
 * 目的只有一个：把首页、记录页、报告页、宠物页各自复制的一套 helper 收成一份，
 * 换主题时只改颜色参数，不改布局代码。
 *
 * 字体加载一次静态缓存；失败自动回退系统字体，不抛异常。
 */
public final class Ui {

    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";

    private static Typeface sCjk;
    private static Typeface sDin;
    private static Typeface sNum;
    private static boolean sLoaded;

    private Ui() {
    }

    public static void ensureFonts(Context ctx) {
        if (sLoaded) {
            return;
        }
        sLoaded = true;
        try {
            sCjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
        try {
            sDin = Typeface.createFromAsset(ctx.getAssets(), "DINPro_Medium.otf");
        } catch (Throwable ignored) {
        }
        try {
            sNum = Typeface.createFromAsset(ctx.getAssets(), "MIUI_EX_Light.ttf");
        } catch (Throwable ignored) {
        }
        if (sCjk == null) {
            sCjk = Typeface.DEFAULT;
        }
        if (sDin == null) {
            sDin = Typeface.DEFAULT;
        }
        if (sNum == null) {
            sNum = Typeface.DEFAULT;
        }
    }

    public static Typeface cjk() {
        return sCjk == null ? Typeface.DEFAULT : sCjk;
    }

    public static Typeface din() {
        return sDin == null ? Typeface.DEFAULT : sDin;
    }

    public static Typeface num() {
        return sNum == null ? Typeface.DEFAULT : sNum;
    }

    public static int dp(Context ctx, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    public static float ts(Context ctx, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    /** 单行文本，统一字体/字距/省略。 */
    public static TextView label(Context ctx, CharSequence text, float sizeDp, int color,
                                 Typeface face, float tracking) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextSize(ts(ctx, sizeDp));
        v.setTextColor(color);
        v.setTypeface(face);
        v.setLetterSpacing(tracking);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    /** 1px 发丝线。 */
    public static android.view.View hairline(Context ctx, int color) {
        android.view.View v = new android.view.View(ctx);
        v.setBackgroundColor(color);
        return v;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radiusPx);
        bg.setColor(color);
        return bg;
    }

    public static GradientDrawable roundStroke(int color, float radiusPx, int strokeColor,
                                               int strokePx) {
        GradientDrawable bg = round(color, radiusPx);
        bg.setStroke(Math.max(1, strokePx), strokeColor);
        return bg;
    }

    public static GradientDrawable pill(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(999f);
        bg.setColor(color);
        return bg;
    }

    public static TextView centered(Context ctx, TextView v) {
        v.setGravity(Gravity.CENTER);
        return v;
    }
}
