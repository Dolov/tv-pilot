package com.dsh.tvhud;

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
 * 只画一张小脸 + 一句话；真正的 3D/2.5D 房间在 PetActivity。
 */
public final class PetBubble extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float density;
    private String message = "";
    private int mood;

    public PetBubble(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(sp(19));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    public void setMessage(String msg, int mood) {
        this.message = msg == null ? "" : msg;
        this.mood = mood;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) dp(470), (int) dp(132));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        // 气泡底
        fill.setColor(0xee141b2a);
        rect.set(dp(4), dp(4), w - dp(4), h - dp(4));
        c.drawRoundRect(rect, dp(24), dp(24), fill);
        fill.setColor(0x33ffffff);
        c.drawRoundRect(rect, dp(24), dp(24), fill);
        fill.setColor(0xee141b2a);
        rect.set(dp(6), dp(6), w - dp(6), h - dp(6));
        c.drawRoundRect(rect, dp(22), dp(22), fill);

        // 小脸
        float cx = dp(62);
        float cy = h * 0.5f;
        float r = dp(34);
        fill.setColor(0xffffcf6e);
        c.drawCircle(cx, cy, r, fill);
        fill.setColor(0xff5a3b1f);
        c.drawCircle(cx - dp(11), cy - dp(6), dp(5), fill);
        c.drawCircle(cx + dp(11), cy - dp(6), dp(5), fill);
        // 嘴：开心/一般/蔫
        Paint p = fill;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setColor(0xff7a3b2e);
        path.reset();
        if (mood >= 60) {
            path.moveTo(cx - dp(12), cy + dp(6));
            path.quadTo(cx, cy + dp(22), cx + dp(12), cy + dp(6));
            c.drawPath(path, p);
        } else if (mood >= 30) {
            c.drawLine(cx - dp(10), cy + dp(12), cx + dp(10), cy + dp(12), p);
        } else {
            path.moveTo(cx - dp(12), cy + dp(18));
            path.quadTo(cx, cy + dp(2), cx + dp(12), cy + dp(18));
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);

        // 文字
        text.setColor(0xfff2f6fa);
        float tx = dp(112);
        float ty = h * 0.5f - (text.descent() + text.ascent()) * 0.5f;
        c.drawText(message, tx, ty, text);
    }
}
