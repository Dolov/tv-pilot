package com.dsh.tvhud;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.view.View;

import java.util.List;

/**
 * 右上角角标（自绘）：
 *   开机 [N] 天 [HH:MM]
 *   遥控器 [NN%]
 *   今日使用长条（0:00 → 24:00，色块长度 = 使用时长，无文字）
 * 全部右对齐，共用右边界。
 */
public class HudView extends View {
    private static final int COLOR_LABEL = 0xff9aa4ae;
    private static final int COLOR_DAYS = 0xfffafbfc;
    private static final int COLOR_TIME = 0xffc6cfd8;
    private static final int COLOR_WARN = 0xffff7b72;
    private static final int COLOR_TRACK = 0x1affffff;
    private static final int COLOR_NOW = 0x66ffffff;

    private static final float BAR_W_DP = 220f;
    private static final float BAR_H_DP = 8f;
    private static final float BAR_GAP_DP = 14f;
    private static final float BAR_RADIUS_DP = 4f;
    private static final float BAR_TICK_DP = 3f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density = getResources().getDisplayMetrics().density;

    private Typeface cjk;
    private Typeface num;

    private String days = "0";
    private String time = "00:00";
    private int battPct = -1;

    private List<UsageDay.Seg> segs;
    private long dayStart = 0L;
    private long nowMs = 0L;

    private final float gapLine = 8f * density;
    private final RectF barRect = new RectF();

    // 长条在 2x 离屏位图里画好（圆角底槽 + 整高色块）再整体缩放到屏幕：
    // - 色块和底槽来自同一张图，上下边缘完全对齐（不会高度不一致、不会和底色之间留缝）
    // - 2x 降采样后颜色交界平滑，不会出现硬缝或压暗线
    private Bitmap barBmp;
    private Canvas barBmpCanvas;
    private final Paint barBmpPaint = new Paint();
    private final Paint barShapePaint = new Paint();
    private final Paint barSegPaint = new Paint();
    private final Path barClipPath = new Path();

    public HudView(Context context, Typeface cjk, Typeface num) {
        super(context);
        this.cjk = cjk;
        this.num = num;
    }

    public void setData(String days, String time, int battPct) {
        this.days = days;
        this.time = time;
        this.battPct = battPct;
        requestLayout();
        invalidate();
    }

    public void setData(String days, String time, int battPct,
                        List<UsageDay.Seg> segs, long dayStart, long nowMs) {
        this.days = days;
        this.time = time;
        this.battPct = battPct;
        this.segs = segs;
        this.dayStart = dayStart;
        this.nowMs = nowMs;
        requestLayout();
        invalidate();
    }

    private static class Seg {
        final String text;
        final float sp;
        final Typeface tf;
        final int color;
        final float letterSpacing;

        Seg(String text, float sp, Typeface tf, int color, float letterSpacing) {
            this.text = text;
            this.sp = sp;
            this.tf = tf;
            this.color = color;
            this.letterSpacing = letterSpacing;
        }
    }

    private void setup(Seg s, float sizePx) {
        p.reset();
        p.setAntiAlias(true);
        p.setTypeface(s.tf);
        p.setTextSize(sizePx);
        p.setColor(s.color);
        p.setLetterSpacing(s.letterSpacing);
        p.setShadowLayer(6.0f, 0.0f, 2.0f, 0xcc000000);
    }

    private float sizePx(Seg s) {
        return s.sp * density;
    }

    private float measureLine(Seg[] segs, float[] gapsBefore) {
        float w = 0f;
        for (int i = 0; i < segs.length; i++) {
            w += gapsBefore[i];
            setup(segs[i], sizePx(segs[i]));
            w += p.measureText(segs[i].text);
        }
        return w;
    }

    private float[] lineMetrics(Seg[] segs) {
        float ascent = 0f;
        float descent = 0f;
        for (Seg s : segs) {
            setup(s, sizePx(s));
            Paint.FontMetrics fm = p.getFontMetrics();
            ascent = Math.max(ascent, -fm.ascent);
            descent = Math.max(descent, fm.descent);
        }
        return new float[]{ascent, descent};
    }

    private Seg[] line1() {
        return new Seg[]{
                new Seg("开机", 16f, cjk, COLOR_LABEL, 0.1f),
                new Seg(days, 34f, num, COLOR_DAYS, 0.03f),
                new Seg("天", 16f, cjk, COLOR_LABEL, 0.1f),
                new Seg(time, 22f, num, COLOR_TIME, 0.06f)
        };
    }

    private Seg[] line2() {
        int color = (battPct >= 0 && battPct <= 25) ? COLOR_WARN : COLOR_TIME;
        String value = battPct < 0 ? "--" : (battPct + "%");
        return new Seg[]{
                new Seg("遥控器", 14f, cjk, COLOR_LABEL, 0.1f),
                new Seg(value, 20f, num, color, 0.06f)
        };
    }

    private float barWidth() {
        return BAR_W_DP * density;
    }

    private float barHeight() {
        return BAR_H_DP * density;
    }

    private float barGap() {
        return BAR_GAP_DP * density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Seg[] l1 = line1();
        Seg[] l2 = line2();
        float w1 = measureLine(l1, new float[]{0f, 10f * density, 5f * density, 18f * density});
        float w2 = measureLine(l2, new float[]{0f, 10f * density});
        float[] m1 = lineMetrics(l1);
        float[] m2 = lineMetrics(l2);
        float h1 = m1[0] + m1[1];
        float h2 = m2[0] + m2[1];
        int w = (int) Math.ceil(Math.max(Math.max(w1, w2), barWidth()))
                + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil(h1 + gapLine + h2 + barGap() + barHeight())
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    private void drawLine(Canvas c, Seg[] segs, float[] gaps, float right, float baselineY) {
        float total = measureLine(segs, gaps);
        float x = right - total;
        for (int i = 0; i < segs.length; i++) {
            x += gaps[i];
            setup(segs[i], sizePx(segs[i]));
            c.drawText(segs[i].text, x, baselineY, p);
            x += p.measureText(segs[i].text);
        }
    }

    private void drawUsageBar(Canvas c, float right, float top) {
        float w = barWidth();
        float h = barHeight();
        float radius = BAR_RADIUS_DP * density;

        int bw = Math.max(4, Math.round(w) * 2);
        int bh = Math.max(4, Math.round(h) * 2);
        if (barBmp == null || barBmp.getWidth() != bw || barBmp.getHeight() != bh) {
            barBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            barBmpCanvas = new Canvas(barBmp);
            barBmpPaint.setFilterBitmap(true);
            barBmpPaint.setAntiAlias(true);
        }
        barBmpCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        // 底槽（圆角）
        barShapePaint.reset();
        barShapePaint.setAntiAlias(true);
        barShapePaint.setStyle(Paint.Style.FILL);
        barShapePaint.setColor(COLOR_TRACK);
        float r2 = radius * 2f;
        barBmpCanvas.drawRoundRect(0, 0, bw, bh, r2, r2, barShapePaint);

        // 色块：整高矩形，裁到圆角范围内
        if (segs != null && !segs.isEmpty() && dayStart > 0 && nowMs > dayStart) {
            barClipPath.reset();
            barClipPath.addRoundRect(0, 0, bw, bh, r2, r2, Path.Direction.CW);
            barBmpCanvas.save();
            barBmpCanvas.clipPath(barClipPath);
            barSegPaint.reset();
            barSegPaint.setStyle(Paint.Style.FILL);
            barSegPaint.setAntiAlias(false);
            float span = UsageDay.DAY_MS;
            float minW = 2f; // 位图是 2x
            for (int i = 0; i < segs.size(); i++) {
                UsageDay.Seg s = segs.get(i);
                float x0 = (s.start - dayStart) / span * bw;
                float x1 = (s.end - dayStart) / span * bw;
                if (x1 - x0 < minW) {
                    x1 = x0 + minW;
                }
                barSegPaint.setColor(Categories.color(s.cat));
                barBmpCanvas.drawRect(x0, 0, x1, bh, barSegPaint);
            }
            barBmpCanvas.restore();
        }

        barRect.set(right - w, top, right, top + h);
        c.drawBitmap(barBmp, null, barRect, barBmpPaint);

        // 「现在」位置：一条细线，半透明
        if (dayStart > 0 && nowMs > dayStart) {
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            p.setColor(COLOR_NOW);
            float nx = right - w
                    + Math.min(w, Math.max(0f, (nowMs - dayStart) / (float) UsageDay.DAY_MS * w));
            float tick = BAR_TICK_DP * density;
            c.drawRect(nx - 0.5f * density, top - tick, nx + 0.5f * density, top + h + tick, p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Seg[] l1 = line1();
        Seg[] l2 = line2();
        float[] gaps1 = {0f, 10f * density, 5f * density, 18f * density};
        float[] gaps2 = {0f, 10f * density};

        float[] m1 = lineMetrics(l1);
        float[] m2 = lineMetrics(l2);
        float h1 = m1[0] + m1[1];
        float h2 = m2[0] + m2[1];

        float right = getWidth() - getPaddingRight();
        float baseline1 = getPaddingTop() + m1[0];
        float baseline2 = getPaddingTop() + h1 + gapLine + m2[0];

        drawLine(c, l1, gaps1, right, baseline1);
        drawLine(c, l2, gaps2, right, baseline2);

        float barTop = getPaddingTop() + h1 + gapLine + h2 + barGap();
        drawUsageBar(c, right, barTop);
    }
}
