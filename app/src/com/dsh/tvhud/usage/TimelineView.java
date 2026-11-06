package com.dsh.tvhud.usage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/**
 * 详情页的「时间轴尺」：细长条 + 整点刻度 + 现在游标。
 *
 * 设计意图：读起来像一条播出带（tape）——不是块状的进度条。
 * 颜色只出现在数据段上（内容类型），刻度、标签、轨道都保持中性，避免抢戏。
 */
public class TimelineView extends View {
    private static final int COLOR_TRACK = 0x14ffffff;
    private static final int COLOR_GRID = 0x1cffffff;
    private static final int COLOR_TICK = 0x40ffffff;
    private static final int COLOR_LABEL = 0xff6b7885;
    private static final int COLOR_PLAYHEAD = 0xd9e9eef3;

    /** 标签间隔（小时）；每 1 小时一根短刻度。 */
    private static final int LABEL_STEP = 3;

    private final float density = getResources().getDisplayMetrics().density;
    private final float barH = 10f * density;
    private final float labelH = 15f * density;
    private final float radius = 5f * density;
    private final float tickGap = 4f * density;
    private final float tickMinor = 2f * density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF bar = new RectF();

    private Typeface num;
    private List<UsageDay.Seg> segs;
    private long dayStart;
    private long nowMs;

    public TimelineView(Context context, Typeface num) {
        super(context);
        this.num = num;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(num);
    }

    public void setData(List<UsageDay.Seg> segs, long dayStart, long nowMs) {
        this.segs = segs;
        this.dayStart = dayStart;
        this.nowMs = nowMs;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(getPaddingLeft() + getPaddingRight() + (int) (400f * density),
                widthMeasureSpec);
        int h = resolveSize((int) Math.ceil(getPaddingTop() + barH + tickGap + labelH
                + getPaddingBottom()), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        float w = right - left;
        if (w <= 0f) {
            return;
        }

        // 轨道
        bar.set(left, top, right, top + barH);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(COLOR_TRACK);
        c.drawRoundRect(bar, radius, radius, fill);

        // 数据段：裁到圆角内，最短 2px 也要看得见
        if (segs != null && !segs.isEmpty() && dayStart > 0) {
            clip.reset();
            clip.addRoundRect(bar, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            fill.setAntiAlias(false);
            float span = UsageDay.DAY_MS;
            for (int i = 0; i < segs.size(); i++) {
                UsageDay.Seg s = segs.get(i);
                float x0 = left + (s.start - dayStart) / span * w;
                float x1 = left + (s.end - dayStart) / span * w;
                if (x1 - x0 < 2f) {
                    x1 = x0 + 2f;
                }
                fill.setColor(Categories.color(s.cat));
                c.drawRect(x0, top, x1, top + barH, fill);
            }
            c.restore();
            fill.setAntiAlias(true);
        }

        // 每小时的短刻度（在轨道下方），整 3 小时换成带数字的长刻度
        float tickTop = top + barH + tickGap;
        float labelY = tickTop + labelH - 2f * density;
        text.setTypeface(num);
        text.setTextSize(9f * density);
        for (int h = 0; h <= 24; h++) {
            float x = left + h / 24f * w;
            boolean major = (h % LABEL_STEP == 0);
            stroke.setColor(major ? COLOR_GRID : COLOR_TICK);
            stroke.setStrokeWidth(1f);
            stroke.setStrokeCap(major ? Paint.Cap.BUTT : Paint.Cap.ROUND);
            c.drawLine(x, tickTop, x, tickTop + (major ? tickMinor * 2f : tickMinor), stroke);
            if (!major) {
                continue;
            }
            // 轨道内的整点参考线
            stroke.setColor(COLOR_GRID);
            c.drawLine(x, top, x, top + barH, stroke);
            text.setColor(COLOR_LABEL);
            String label = String.valueOf(h);
            if (h == 0) {
                text.setTextAlign(Paint.Align.LEFT);
                c.drawText(label, left, labelY, text);
            } else if (h == 24) {
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(label, right, labelY, text);
            } else {
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(label, x, labelY, text);
            }
        }

        // 「现在」游标
        if (dayStart > 0 && nowMs > dayStart) {
            float f = Math.min(1f, (nowMs - dayStart) / (float) UsageDay.DAY_MS);
            float nx = left + f * w;
            stroke.setColor(COLOR_PLAYHEAD);
            stroke.setStrokeWidth(1.5f * density);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(nx, top - 5f * density, nx, top + barH + 4f * density, stroke);
            fill.setColor(COLOR_PLAYHEAD);
            c.drawCircle(nx, top - 5f * density, 2.0f * density, fill);
        }
    }
}
