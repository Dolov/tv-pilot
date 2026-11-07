package com.dsh.tvhud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.dsh.tvhud.usage.UsageDay;

import java.util.List;

/**
 * 带子（Day Tape）的 Android 绘制：一条 24 小时轨道 + 数据段 + 现在游标。
 *
 * 全 App 同构，只有两个尺度：
 * - {@link #COMPACT}：首页眉标下面那条 4dp 的细带，不画刻度，只交代「今天走到哪」；
 * - {@link #STANDARD}：使用记录页的 10dp 带，带整点刻度与 0/3/…/24 的数字。
 *
 * 颜色全部来自 {@link Palette} 的 tape 角色，几何全部来自 {@link Tape}：
 * 换 look 时这里一行不用改，页面也不再各自发明「轨道/刻度/游标」的灰。
 */
public class TapeView extends View {

    /** 首页眉标：4dp，无刻度。 */
    public static final int COMPACT = 0;
    /** 使用记录：10dp + 每小时刻度 + 每 3 小时数字。 */
    public static final int STANDARD = 1;

    private static final float COMPACT_BAR_DP = 4f;
    private static final float STANDARD_BAR_DP = 10f;
    private static final float TICK_GAP_DP = 4f;
    private static final float LABEL_H_DP = 15f;
    private static final float TICK_MINOR_DP = 2f;
    private static final float LABEL_SIZE_DP = 9f;

    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF bar = new RectF();

    private Palette palette;
    private Typeface num;
    private int scale = STANDARD;
    private List<UsageDay.Seg> segs;
    private long dayStart;
    private long nowMs;

    public TapeView(Context context, Palette palette, Typeface num) {
        super(context);
        this.palette = palette;
        this.num = num == null ? Typeface.DEFAULT : num;
        density = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(this.num);
    }

    public void setPalette(Palette palette) {
        this.palette = palette;
        invalidate();
    }

    public void setScale(int scale) {
        this.scale = scale == COMPACT ? COMPACT : STANDARD;
        requestLayout();
        invalidate();
    }

    public void setData(List<UsageDay.Seg> segs, long dayStart, long nowMs) {
        this.segs = segs;
        this.dayStart = dayStart;
        this.nowMs = nowMs;
        invalidate();
    }

    private float barH() {
        return (scale == COMPACT ? COMPACT_BAR_DP : STANDARD_BAR_DP) * density;
    }

    private float radius() {
        float r = palette == null ? 0f : palette.panelRadius * density;
        return Math.min(r, barH() / 2f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(getPaddingLeft() + getPaddingRight() + (int) (400f * density),
                widthMeasureSpec);
        float h = barH();
        if (scale == STANDARD) {
            h += (TICK_GAP_DP + LABEL_H_DP) * density;
        } else {
            // 游标要伸出轨道一点，给上下各留 2dp，避免被裁掉
            h += 4f * density;
        }
        int height = resolveSize((int) Math.ceil(getPaddingTop() + h + getPaddingBottom()),
                heightMeasureSpec);
        setMeasuredDimension(w, height);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (palette == null) {
            return;
        }
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + (scale == COMPACT ? 2f * density : 0f);
        float w = right - left;
        if (w <= 0f) {
            return;
        }
        float h = barH();
        float radius = radius();

        // 轨道
        bar.set(left, top, right, top + h);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(palette.tapeTrack);
        c.drawRoundRect(bar, radius, radius, fill);

        // 数据段：裁到圆角里，最短 2px 也要看得见
        if (segs != null && !segs.isEmpty() && dayStart > 0L) {
            clip.reset();
            clip.addRoundRect(bar, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            fill.setAntiAlias(false);
            for (int i = 0; i < segs.size(); i++) {
                UsageDay.Seg s = segs.get(i);
                float x0 = Tape.segStart(s.start, dayStart, left, w);
                float x1 = Tape.segEnd(s.end, dayStart, left, w);
                if (x1 - x0 < 2f) {
                    x1 = x0 + 2f;
                }
                fill.setColor(palette.dataColor(s.cat));
                c.drawRect(x0, top, x1, top + h, fill);
            }
            c.restore();
            fill.setAntiAlias(true);
        }

        if (scale == STANDARD) {
            drawTicks(c, left, right, top, w, h);
        }

        // 「现在」游标：全 App 唯一的发光体。
        // COMPACT（首页 4dp）不外伸，避免被自己的 View 高度裁掉；STANDARD 才带圆点与外伸。
        if (Tape.hasNow(nowMs, dayStart)) {
            float nx = Tape.xAt(nowMs, dayStart, left, w);
            stroke.setColor(palette.tapeNow);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            if (scale == COMPACT) {
                stroke.setStrokeWidth(2f * density);
                c.drawLine(nx, top, nx, top + h, stroke);
            } else {
                // 游标外伸量不超过上内边距，避免在无 padding 的页面上被裁掉
                float over = Math.min(5f * density, top);
                stroke.setStrokeWidth(1.5f * density);
                c.drawLine(nx, top - over, nx, top + h + over, stroke);
                if (over >= 2f * density) {
                    fill.setColor(palette.tapeNow);
                    c.drawCircle(nx, top - over, 2.0f * density, fill);
                }
            }
        }
    }

    private void drawTicks(Canvas c, float left, float right, float top, float w, float h) {
        float tickTop = top + h + TICK_GAP_DP * density;
        float labelY = tickTop + LABEL_H_DP * density - 2f * density;
        text.setTypeface(num);
        text.setTextSize(LABEL_SIZE_DP * density);
        text.setColor(palette.tapeLabel);
        for (int hour = 0; hour < Tape.TICKS; hour++) {
            float x = Tape.hourX(hour, left, w);
            boolean major = Tape.isMajorHour(hour);
            stroke.setColor(major ? palette.tapeRule : palette.tapeTick);
            stroke.setStrokeWidth(major ? 1.5f * density : 1f * density);
            stroke.setStrokeCap(major ? Paint.Cap.BUTT : Paint.Cap.ROUND);
            float len = (major ? TICK_MINOR_DP * 2f : TICK_MINOR_DP) * density;
            c.drawLine(x, tickTop, x, tickTop + len, stroke);
            if (!major) {
                continue;
            }
            stroke.setColor(palette.tapeRule);
            stroke.setStrokeWidth(1f * density);
            c.drawLine(x, top, x, top + h, stroke);
            if (hour == 0) {
                text.setTextAlign(Paint.Align.LEFT);
                c.drawText("0", left, labelY, text);
            } else if (hour == 24) {
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText("24", right, labelY, text);
            } else {
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(hour), x, labelY, text);
            }
        }
    }
}
