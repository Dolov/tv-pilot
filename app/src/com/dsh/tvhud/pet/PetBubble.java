package com.dsh.tvhud.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/**
 * 观看中的宠物气泡：剩 3 分钟 / 1 分钟时浮在右上角，不挡画面、不可聚焦。
 *
 * 不是「系统通知」，是宠物探个头提醒：小脸 + 沙漏（1 分钟时打哈欠/zzz）
 * + 一句口语化文案。宽度固定 470dp，高度 150dp，位置由 HudService 的
 * TYPE_SYSTEM_ALERT 窗口决定（不可聚焦、不可触摸）。
 *
 * {@link #setMessage(String, int)} 接口保持不变（HudService 在用）；
 * {@link #setProgress(float)} 可选画一条「宠物走向门口」的小进度条。
 */
public final class PetBubble extends View {
    private static final int PANEL = 0xf0141b2a;
    private static final int PANEL_EDGE = 0x44ffe27a;
    private static final int FACE = 0xffffcf6e;
    private static final int FACE_LIGHT = 0xffffe6a8;
    private static final int FACE_DARK = 0xff5a3b1f;
    private static final int MOUTH = 0xff7a3b2e;
    private static final int CHEEK = 0x66ff9f8f;
    private static final int TEXT = 0xfff2f6fa;
    private static final int SAND = 0xffffd54f;
    private static final int GLASS = 0x88bfe9ff;
    private static final int TRACK = 0x33ffffff;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF bar = new RectF();
    private final Path path = new Path();

    private String message = "";
    private String shownMessage = "";
    private int mood = 70;
    /** 1 = 剩 3 分钟，2 = 剩 1 分钟，0 = 未知（只画普通提醒）。 */
    private int level;
    /** < 0 表示不画进度条；0..1 表示宠物走向门口的进度。 */
    private float progress = -1f;

    public PetBubble(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(sp(20));
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setTextSize(sp(15));
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    /** 保持原接口：HudService 传「还有 3 分钟…」「还有 1 分钟…」。 */
    public void setMessage(String msg, int mood) {
        this.message = msg == null ? "" : msg;
        this.mood = mood;
        this.level = inferLevel(this.message);
        this.shownMessage = fit(this.message);
        invalidate();
    }

    /**
     * 可选：宠物走向门口的小进度条（0..1）。传负数清掉。
     * 不调用时气泡保持原样，不破坏现有调用。
     */
    public void setProgress(float p) {
        this.progress = p < 0f ? -1f : Math.min(1f, p);
        invalidate();
    }

    private int inferLevel(String msg) {
        if (msg == null) {
            return 0;
        }
        if (msg.contains("1 分钟") || msg.contains("1分钟")) {
            return 2;
        }
        if (msg.contains("3 分钟") || msg.contains("3分钟")) {
            return 1;
        }
        return 0;
    }

    /** 文案按气泡宽度裁一次，避免 onDraw 里量文字/拼字符串。 */
    private String fit(String s) {
        if (s.isEmpty()) {
            return s;
        }
        float max = dp(470) - dp(168) - dp(26);
        if (text.measureText(s) <= max) {
            return s;
        }
        int end = s.length();
        while (end > 1 && text.measureText(s, 0, end) + text.measureText("…") > max) {
            end--;
        }
        return s.substring(0, end) + "…";
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) dp(470), (int) dp(150));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();

        // 气泡底：圆角 + 一圈暖黄柔光边，别做成系统通知那种冷灰块
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(PANEL);
        rect.set(dp(4), dp(4), w - dp(4), h - dp(4));
        c.drawRoundRect(rect, dp(26), dp(26), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(PANEL_EDGE);
        c.drawRoundRect(rect, dp(26), dp(26), stroke);

        drawFace(c, dp(66), h * 0.44f, dp(33));
        drawHourglass(c, dp(126), h * 0.44f);

        text.setColor(TEXT);
        float tx = dp(166);
        float ty = h * 0.44f - (text.descent() + text.ascent()) * 0.5f;
        if (shownMessage.length() > 0) {
            c.drawText(shownMessage, tx, ty, text);
        }

        if (progress >= 0f) {
            drawProgress(c, w, h);
        }
    }

    /** 小脸：圆脸 + 两根天线 + 腮红，1 分钟时打哈欠。 */
    private void drawFace(Canvas c, float cx, float cy, float r) {
        // 天线
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(3));
        stroke.setColor(FACE_DARK);
        c.drawLine(cx - dp(14), cy - r + dp(2), cx - dp(24), cy - r - dp(14), stroke);
        c.drawLine(cx + dp(14), cy - r + dp(2), cx + dp(24), cy - r - dp(14), stroke);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(FACE_DARK);
        c.drawCircle(cx - dp(24), cy - r - dp(14), dp(4), fill);
        c.drawCircle(cx + dp(24), cy - r - dp(14), dp(4), fill);

        // 脸
        fill.setColor(FACE);
        c.drawCircle(cx, cy, r, fill);
        fill.setColor(FACE_LIGHT);
        c.drawCircle(cx - dp(8), cy - dp(9), r * 0.42f, fill);

        // 腮红
        fill.setColor(CHEEK);
        c.drawCircle(cx - r * 0.62f, cy + dp(10), dp(6), fill);
        c.drawCircle(cx + r * 0.62f, cy + dp(10), dp(6), fill);

        // 眼睛
        fill.setColor(FACE_DARK);
        c.drawCircle(cx - dp(12), cy - dp(4), dp(5), fill);
        c.drawCircle(cx + dp(12), cy - dp(4), dp(5), fill);

        // 嘴：开心/一般/1 分钟时打哈欠
        if (level == 2) {
            fill.setColor(MOUTH);
            rect.set(cx - dp(8), cy + dp(6), cx + dp(8), cy + dp(22));
            c.drawOval(rect, fill);
        } else if (mood >= 60) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
            stroke.setColor(MOUTH);
            path.reset();
            path.moveTo(cx - dp(12), cy + dp(6));
            path.quadTo(cx, cy + dp(20), cx + dp(12), cy + dp(6));
            c.drawPath(path, stroke);
        } else if (mood >= 30) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
            stroke.setColor(MOUTH);
            c.drawLine(cx - dp(10), cy + dp(12), cx + dp(10), cy + dp(12), stroke);
        } else {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
            stroke.setColor(MOUTH);
            path.reset();
            path.moveTo(cx - dp(12), cy + dp(18));
            path.quadTo(cx, cy + dp(2), cx + dp(12), cy + dp(18));
            c.drawPath(path, stroke);
        }

        if (level == 2) {
            // 困了：两个小 z
            hint.setColor(TEXT);
            c.drawText("z", cx + r + dp(2), cy - r + dp(6), hint);
            c.drawText("z", cx + r + dp(12), cy - r - dp(8), hint);
        }
    }

    /** 沙漏：3 分钟时上面还有很多沙，1 分钟时快漏完了。 */
    private void drawHourglass(Canvas c, float cx, float cy) {
        float halfW = dp(12);
        float halfH = dp(17);
        float top = cy - halfH;
        float bottom = cy + halfH;
        float left = cx - halfW;
        float right = cx + halfW;

        // 玻璃：上下两个三角
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(GLASS);
        path.reset();
        path.moveTo(left, top);
        path.lineTo(right, top);
        path.lineTo(cx, cy);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(left, bottom);
        path.lineTo(right, bottom);
        path.lineTo(cx, cy);
        path.close();
        c.drawPath(path, fill);

        // 沙：剩 3 分钟 → 上面 60%，剩 1 分钟 → 上面 15%
        float topSand = level == 2 ? 0.12f : (level == 1 ? 0.58f : 0.35f);
        float bottomSand = level == 2 ? 0.72f : (level == 1 ? 0.3f : 0.5f);
        fill.setColor(SAND);
        float inset = halfW * (1f - topSand);
        path.reset();
        path.moveTo(left + inset, top + dp(1));
        path.lineTo(right - inset, top + dp(1));
        path.lineTo(cx, top + halfH * topSand);
        path.close();
        c.drawPath(path, fill);
        float bInset = halfW * (1f - bottomSand);
        path.reset();
        path.moveTo(cx, bottom - halfH * bottomSand);
        path.lineTo(right - bInset, bottom - dp(1));
        path.lineTo(left + bInset, bottom - dp(1));
        path.close();
        c.drawPath(path, fill);

        // 玻璃描边
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(GLASS);
        path.reset();
        path.moveTo(left, top);
        path.lineTo(right, top);
        path.lineTo(cx, cy);
        path.close();
        c.drawPath(path, stroke);
        path.reset();
        path.moveTo(left, bottom);
        path.lineTo(right, bottom);
        path.lineTo(cx, cy);
        path.close();
        c.drawPath(path, stroke);
        c.drawLine(left - dp(2), top, right + dp(2), top, stroke);
        c.drawLine(left - dp(2), bottom, right + dp(2), bottom, stroke);
    }

    /** 可选的小进度条：宠物从屏幕边走到门口。 */
    private void drawProgress(Canvas c, float w, float h) {
        float left = dp(40);
        float right = w - dp(30);
        float cy = h - dp(20);
        float half = dp(4);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(TRACK);
        bar.set(left, cy - half, right, cy + half);
        c.drawRoundRect(bar, half, half, fill);
        fill.setColor(FACE);
        bar.set(left, cy - half, left + (right - left) * progress, cy + half);
        c.drawRoundRect(bar, half, half, fill);
        fill.setColor(FACE_LIGHT);
        c.drawCircle(bar.right, cy, dp(6), fill);
        fill.setColor(FACE_DARK);
        c.drawCircle(bar.right, cy, dp(3), fill);
    }
}
