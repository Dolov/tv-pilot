package com.dsh.tvhud.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 商店/衣柜/照顾按钮用的小图标：纯 Canvas 矢量，不读图片资源。
 *
 * 用法：`new PetIconView(ctx); setItem(Shop.TYPE_HAT, "hat_cap")`；
 * 照顾按钮传 {@link #TYPE_CARE} + eat/drink/pet/bath/brush。
 * 只在 setItem 时重绘一次，不跑动画，几十个卡片同屏也没有性能压力。
 */
public final class PetIconView extends View {
    public static final String TYPE_CARE = "care";

    private static final int C_YELLOW = 0xffffd54f;
    private static final int C_ORANGE = 0xffffb74d;
    private static final int C_PINK = 0xffff9f8f;
    private static final int C_MINT = 0xff5fd6c9;
    private static final int C_BLUE = 0xff7ec8e3;
    private static final int C_LILAC = 0xffa99bff;
    private static final int C_RED = 0xffff8a80;
    private static final int C_WHITE = 0xfff7f3e8;
    private static final int C_DARK = 0xff5a3b1f;
    private static final int C_GREEN = 0xff7ee0a3;
    private static final int C_BROWN = 0xffb07a4a;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private String type = "";
    private String id = "";

    public PetIconView(Context context) {
        super(context);
        init();
    }

    public PetIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(false);
        setClickable(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setItem(String type, String id) {
        this.type = type == null ? "" : type;
        this.id = id == null ? "" : id;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float unit = Math.min(w, h) / 100f;
        c.save();
        c.translate(w * 0.5f, h * 0.5f);
        c.scale(unit, unit);

        if ("food_kibble".equals(id)) {
            drawBowl(c, C_BROWN, C_ORANGE);
        } else if ("food_snack".equals(id)) {
            drawCookie(c);
        } else if ("food_candy".equals(id)) {
            drawCandy(c);
        } else if ("hat_cap".equals(id)) {
            drawCap(c);
        } else if ("hat_beanie".equals(id)) {
            drawBeanie(c);
        } else if ("hat_crown".equals(id)) {
            drawCrown(c);
        } else if ("hat_chef".equals(id)) {
            drawChefHat(c);
        } else if ("clothes_shirt".equals(id)) {
            drawShirt(c);
        } else if ("clothes_cape".equals(id)) {
            drawCape(c);
        } else if ("clothes_overalls".equals(id)) {
            drawOveralls(c);
        } else if ("clothes_star".equals(id)) {
            drawStarVest(c);
        } else if ("wings_small".equals(id)) {
            drawWings(c, 0.8f, C_WHITE);
        } else if ("wings_big".equals(id)) {
            drawWings(c, 1.15f, C_WHITE);
        } else if ("wings_butterfly".equals(id)) {
            drawWings(c, 1.1f, C_PINK);
        } else if ("decor_plant".equals(id)) {
            drawPlant(c);
        } else if ("decor_lamp".equals(id)) {
            drawLamp(c);
        } else if ("decor_poster".equals(id)) {
            drawPoster(c);
        } else if ("decor_rug".equals(id)) {
            drawRug(c);
        } else if ("decor_fish".equals(id)) {
            drawFishTank(c);
        } else if ("decor_balloon".equals(id)) {
            drawBalloon(c);
        } else if ("eat".equals(id)) {
            drawBowl(c, C_BROWN, C_ORANGE);
        } else if ("drink".equals(id)) {
            drawCup(c);
        } else if ("pet".equals(id)) {
            drawHeart(c, C_PINK, 0f, 0f, 36f);
        } else if ("bath".equals(id)) {
            drawBubbles(c);
        } else if ("brush".equals(id)) {
            drawBrush(c);
        } else if ("star".equals(id)) {
            drawHeart(c, C_YELLOW, 0f, 0f, 0f);
        } else {
            drawStar(c, 0f, 0f, 38f, C_YELLOW);
        }
        c.restore();
    }

    // ---------- 食物 ----------

    private void drawBowl(Canvas c, int bowl, int food) {
        fill.setColor(food);
        rect.set(-34f, -26f, 34f, -10f);
        c.drawOval(rect, fill);
        fill.setColor(bowl);
        path.reset();
        path.moveTo(-40f, -18f);
        path.lineTo(40f, -18f);
        path.lineTo(28f, 26f);
        path.quadTo(0f, 40f, -28f, 26f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x55ffffff);
        rect.set(-26f, 2f, 26f, 10f);
        c.drawRoundRect(rect, 6f, 6f, fill);
    }

    private void drawCookie(Canvas c) {
        fill.setColor(0xffd9a05b);
        c.drawCircle(0f, 0f, 34f, fill);
        fill.setColor(0xff7a4a24);
        c.drawCircle(-12f, -8f, 5f, fill);
        c.drawCircle(10f, -14f, 4f, fill);
        c.drawCircle(8f, 12f, 5f, fill);
        c.drawCircle(-14f, 14f, 3.5f, fill);
    }

    private void drawCandy(Canvas c) {
        fill.setColor(0xffff8ab5);
        c.drawCircle(-22f, 0f, 16f, fill);
        c.drawCircle(22f, 0f, 16f, fill);
        fill.setColor(0xffffd1e1);
        rect.set(-16f, -16f, 16f, 16f);
        c.drawRoundRect(rect, 12f, 12f, fill);
        stroke.setColor(0xffff5c8a);
        stroke.setStrokeWidth(4f);
        c.drawLine(-6f, -10f, 6f, 10f, stroke);
        c.drawLine(6f, -10f, -6f, 10f, stroke);
    }

    // ---------- 帽子 ----------

    private void drawCap(Canvas c) {
        fill.setColor(C_PINK);
        path.reset();
        path.moveTo(-38f, 8f);
        path.quadTo(-34f, -36f, 0f, -36f);
        path.quadTo(32f, -36f, 36f, 8f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(C_RED);
        rect.set(2f, 4f, 48f, 16f);
        c.drawRoundRect(rect, 8f, 8f, fill);
        fill.setColor(C_YELLOW);
        c.drawCircle(0f, -34f, 7f, fill);
    }

    private void drawBeanie(Canvas c) {
        fill.setColor(C_LILAC);
        rect.set(-38f, -26f, 38f, 20f);
        c.drawRoundRect(rect, 26f, 26f, fill);
        fill.setColor(0xff8f7ff0);
        rect.set(-42f, 10f, 42f, 28f);
        c.drawRoundRect(rect, 10f, 10f, fill);
        fill.setColor(C_YELLOW);
        c.drawCircle(0f, -34f, 12f, fill);
    }

    private void drawCrown(Canvas c) {
        fill.setColor(C_YELLOW);
        path.reset();
        path.moveTo(-40f, 22f);
        path.lineTo(-40f, -14f);
        path.lineTo(-18f, 2f);
        path.lineTo(0f, -26f);
        path.lineTo(18f, 2f);
        path.lineTo(40f, -14f);
        path.lineTo(40f, 22f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xffff9f43);
        c.drawCircle(-22f, 10f, 4f, fill);
        c.drawCircle(0f, 10f, 4f, fill);
        c.drawCircle(22f, 10f, 4f, fill);
        fill.setColor(C_PINK);
        c.drawCircle(0f, -30f, 6f, fill);
    }

    private void drawChefHat(Canvas c) {
        fill.setColor(C_WHITE);
        c.drawCircle(-18f, -12f, 20f, fill);
        c.drawCircle(18f, -12f, 20f, fill);
        c.drawCircle(0f, -22f, 22f, fill);
        rect.set(-28f, -12f, 28f, 22f);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setColor(C_YELLOW);
        rect.set(-28f, 10f, 28f, 22f);
        c.drawRoundRect(rect, 5f, 5f, fill);
    }

    // ---------- 衣服 ----------

    private void drawShirt(Canvas c) {
        fill.setColor(C_BLUE);
        path.reset();
        path.moveTo(-20f, -28f);
        path.lineTo(-44f, -14f);
        path.lineTo(-34f, 6f);
        path.lineTo(-26f, 0f);
        path.lineTo(-26f, 34f);
        path.lineTo(26f, 34f);
        path.lineTo(26f, 0f);
        path.lineTo(34f, 6f);
        path.lineTo(44f, -14f);
        path.lineTo(20f, -28f);
        path.quadTo(0f, -14f, -20f, -28f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x66ffffff);
        c.drawCircle(0f, 8f, 5f, fill);
    }

    private void drawCape(Canvas c) {
        fill.setColor(C_PINK);
        path.reset();
        path.moveTo(-20f, -28f);
        path.lineTo(20f, -28f);
        path.quadTo(44f, 10f, 38f, 36f);
        path.lineTo(-38f, 36f);
        path.quadTo(-44f, 10f, -20f, -28f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x44ffffff);
        rect.set(-24f, -12f, 24f, 0f);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    private void drawOveralls(Canvas c) {
        fill.setColor(0xff5b8dd6);
        path.reset();
        path.moveTo(-30f, -28f);
        path.lineTo(-14f, -28f);
        path.lineTo(-14f, -6f);
        path.lineTo(14f, -6f);
        path.lineTo(14f, -28f);
        path.lineTo(30f, -28f);
        path.lineTo(38f, 36f);
        path.lineTo(-38f, 36f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xff3f6fae);
        rect.set(-30f, 4f, -8f, 22f);
        c.drawRoundRect(rect, 4f, 4f, fill);
        rect.set(8f, 4f, 30f, 22f);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    private void drawStarVest(Canvas c) {
        fill.setColor(0xff3b4a8f);
        path.reset();
        path.moveTo(-20f, -28f);
        path.lineTo(-42f, -12f);
        path.lineTo(-26f, 34f);
        path.lineTo(26f, 34f);
        path.lineTo(42f, -12f);
        path.lineTo(20f, -28f);
        path.quadTo(0f, -14f, -20f, -28f);
        path.close();
        c.drawPath(path, fill);
        drawStar(c, -14f, 6f, 8f, C_YELLOW);
        drawStar(c, 16f, 18f, 6f, C_WHITE);
        drawStar(c, 4f, -4f, 5f, C_MINT);
    }

    // ---------- 翅膀 ----------

    private void drawWings(Canvas c, float scale, int color) {
        c.save();
        c.scale(scale, scale);
        drawWing(c, -1, color);
        drawWing(c, 1, color);
        c.restore();
    }

    private void drawWing(Canvas c, int dir, int color) {
        c.save();
        c.scale(dir, 1f);
        path.reset();
        path.moveTo(0f, -4f);
        path.cubicTo(26f, -44f, 58f, -26f, 54f, 2f);
        path.cubicTo(50f, 24f, 22f, 30f, 4f, 20f);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
        stroke.setColor(0x66a0b4d0);
        stroke.setStrokeWidth(3f);
        c.drawPath(path, stroke);
        if ("wings_butterfly".equals(id)) {
            fill.setColor(0x66ff8ab5);
            c.drawCircle(26f, -14f, 9f, fill);
            fill.setColor(0x66ffe27a);
            c.drawCircle(38f, 8f, 8f, fill);
        }
        c.restore();
    }

    // ---------- 装饰 ----------

    private void drawPlant(Canvas c) {
        fill.setColor(C_BROWN);
        rect.set(-24f, 16f, 24f, 40f);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setColor(C_GREEN);
        path.reset();
        path.moveTo(0f, 16f);
        path.quadTo(-34f, 4f, -20f, -18f);
        path.quadTo(-4f, -8f, 0f, 10f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(0f, 16f);
        path.quadTo(34f, 4f, 20f, -18f);
        path.quadTo(4f, -8f, 0f, 10f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(0f, 10f);
        path.quadTo(-8f, -34f, 12f, -34f);
        path.quadTo(10f, -12f, 4f, 6f);
        path.close();
        c.drawPath(path, fill);
    }

    private void drawLamp(Canvas c) {
        fill.setColor(C_YELLOW);
        c.drawCircle(0f, -14f, 22f, fill);
        fill.setColor(0x66ffe27a);
        c.drawCircle(0f, -14f, 32f, fill);
        stroke.setColor(0xffb08a3a);
        stroke.setStrokeWidth(4f);
        c.drawLine(0f, 8f, 0f, 38f, stroke);
        fill.setColor(0xffb08a3a);
        rect.set(-18f, 36f, 18f, 44f);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    private void drawPoster(Canvas c) {
        fill.setColor(0xff2f3b63);
        rect.set(-36f, -32f, 36f, 32f);
        c.drawRoundRect(rect, 4f, 4f, fill);
        fill.setColor(C_YELLOW);
        rect.set(-28f, -24f, 28f, 24f);
        c.drawRoundRect(rect, 3f, 3f, fill);
        fill.setColor(C_ORANGE);
        c.drawCircle(-2f, -2f, 12f, fill);
        fill.setColor(C_DARK);
        c.drawCircle(-6f, -5f, 2f, fill);
        c.drawCircle(2f, -5f, 2f, fill);
        fill.setColor(C_PINK);
        c.drawRect(-8f, 2f, 4f, 6f, fill);
    }

    private void drawRug(Canvas c) {
        fill.setColor(0xff7c6bd6);
        rect.set(-42f, -22f, 42f, 22f);
        c.drawOval(rect, fill);
        fill.setColor(0xffffe27a);
        rect.set(-30f, -13f, 30f, 13f);
        c.drawOval(rect, fill);
        fill.setColor(0xffff9f8f);
        rect.set(-18f, -7f, 18f, 7f);
        c.drawOval(rect, fill);
    }

    private void drawFishTank(Canvas c) {
        fill.setColor(0x3366ccff);
        rect.set(-32f, -30f, 32f, 30f);
        c.drawRoundRect(rect, 8f, 8f, fill);
        stroke.setColor(0x88ffffff);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, 8f, 8f, stroke);
        fill.setColor(0x44a9e8ff);
        rect.set(-30f, -14f, 30f, 28f);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setColor(C_ORANGE);
        c.drawCircle(-8f, 2f, 6f, fill);
        fill.setColor(0xffff6b3d);
        path.reset();
        path.moveTo(-2f, 2f);
        path.lineTo(8f, -4f);
        path.lineTo(8f, 8f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(C_MINT);
        c.drawCircle(14f, 14f, 6f, fill);
        fill.setColor(C_GREEN);
        c.drawRect(-24f, 22f, -14f, 36f, fill);
    }

    private void drawBalloon(Canvas c) {
        fill.setColor(C_PINK);
        rect.set(-22f, -38f, 22f, 10f);
        c.drawOval(rect, fill);
        fill.setColor(0x66ffffff);
        c.drawCircle(-8f, -24f, 5f, fill);
        stroke.setColor(0xaaffffff);
        stroke.setStrokeWidth(2.5f);
        c.drawLine(0f, 10f, -4f, 40f, stroke);
    }

    // ---------- 照顾动作 ----------

    private void drawCup(Canvas c) {
        fill.setColor(C_BLUE);
        path.reset();
        path.moveTo(-24f, -26f);
        path.lineTo(24f, -26f);
        path.lineTo(18f, 34f);
        path.lineTo(-18f, 34f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x66ffffff);
        rect.set(-16f, -20f, 16f, -12f);
        c.drawRoundRect(rect, 4f, 4f, fill);
        stroke.setColor(C_BLUE);
        stroke.setStrokeWidth(6f);
        c.drawCircle(30f, 4f, 10f, stroke);
    }

    private void drawBubbles(Canvas c) {
        fill.setColor(0x99bfe9ff);
        c.drawCircle(-18f, -16f, 15f, fill);
        c.drawCircle(16f, -8f, 12f, fill);
        c.drawCircle(-2f, 14f, 18f, fill);
        fill.setColor(0x66ffffff);
        c.drawCircle(-22f, -20f, 5f, fill);
        c.drawCircle(-6f, 10f, 6f, fill);
        c.drawCircle(14f, -11f, 4f, fill);
    }

    private void drawBrush(Canvas c) {
        fill.setColor(C_WHITE);
        rect.set(-30f, -8f, 18f, 8f);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setColor(C_MINT);
        rect.set(14f, -12f, 34f, 12f);
        c.drawRoundRect(rect, 5f, 5f, fill);
        fill.setColor(0xff9ad6ff);
        for (int i = 0; i < 3; i++) {
            c.drawCircle(20f + i * 6f, -16f, 3f, fill);
        }
    }

    // ---------- 通用 ----------

    private void drawHeart(Canvas c, int color, float x, float y, float r) {
        if (r <= 0f) {
            drawStar(c, x, y, 34f, C_YELLOW);
            return;
        }
        fill.setColor(color);
        path.reset();
        path.moveTo(x, y + r * 0.75f);
        path.cubicTo(x - r * 1.5f, y - r * 0.35f, x - r * 0.5f, y - r * 1.4f, x, y - r * 0.45f);
        path.cubicTo(x + r * 0.5f, y - r * 1.4f, x + r * 1.5f, y - r * 0.35f, x, y + r * 0.75f);
        c.drawPath(path, fill);
    }

    private void drawStar(Canvas c, float x, float y, float r, int color) {
        fill.setColor(color);
        path.reset();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? r : r * 0.45f;
            float px = x + (float) Math.cos(angle) * rad;
            float py = y + (float) Math.sin(angle) * rad;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        c.drawPath(path, fill);
    }
}
