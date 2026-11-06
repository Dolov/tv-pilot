package com.dsh.tvhud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 2.5D 宠物渲染器：骨骼变换 + 分层部件，全部 Canvas 矢量绘制。
 *
 * 分层：背景 → 尾巴/翅膀 → 腿 → 身体 → 衣服 → 手臂 → 头/耳朵 → 眼睛/嘴/牙齿 → 帽子 → 特效。
 * 体型 0..100 只改身体宽度/高度，不换图；衣服/帽子/翅膀只是槽位，所以组合不用预渲染。
 * 30fps 自绘，View 不聚焦、不参与 TV 遥控器焦点。
 */
public final class PetView extends View {
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_EAT = 1;
    public static final int ANIM_DRINK = 2;
    public static final int ANIM_PET = 3;
    public static final int ANIM_BATH = 4;
    public static final int ANIM_BRUSH = 5;
    public static final int ANIM_HAPPY = 6;
    public static final int ANIM_EVOLVE = 7;

    private static final int COL_BG_TOP = 0xff1b2542;
    private static final int COL_BG_BOTTOM = 0xff090d18;
    private static final int COL_FLOOR = 0xff2a3352;
    private static final int COL_RUG = 0xff3b3154;
    private static final int COL_STAR = 0x66ffffff;

    private static final int COL_BODY = 0xffffcf6e;
    private static final int COL_BODY_LIGHT = 0xffffe6a8;
    private static final int COL_OUTLINE = 0xff5a3b1f;
    private static final int COL_EAR = 0xffff9f6e;
    private static final int COL_EYE = 0xff24303a;
    private static final int COL_EYE_WHITE = 0xfff7f3e8;
    private static final int COL_MOUTH = 0xff7a3b2e;
    private static final int COL_TEETH = 0xfff7f3e8;
    private static final int COL_DIRTY = 0xffd8c06a;
    private static final int COL_SHIRT = 0xff5fb6c9;
    private static final int COL_SHIRT_2 = 0xffe88b7d;
    private static final int COL_HAT = 0xffe06c75;
    private static final int COL_HAT_2 = 0xff7c6bd6;
    private static final int COL_WING = 0xffe8f1ff;
    private static final int COL_WING_EDGE = 0xff9fb6d8;
    private static final int COL_HEART = 0xffff7f9a;
    private static final int COL_BUBBLE = 0x99bfe9ff;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Random starRandom = new Random(7L);
    private final float[] starX = new float[48];
    private final float[] starY = new float[48];
    private final float[] starR = new float[48];
    private Shader bgShader;
    private int bgShaderW;
    private int bgShaderH;

    private PetState state;
    private boolean sleeping;
    private int action = ANIM_IDLE;
    private long actionStart;
    private long actionDuration = 1200L;
    private float[] hearts;
    private float[] bubbles;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postDelayed(this, 33L);
        }
    };

    public PetView(Context context) {
        super(context);
        init();
    }

    public PetView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(false);
        setClickable(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = starRandom.nextFloat();
            starY[i] = starRandom.nextFloat() * 0.62f;
            starR[i] = 0.6f + starRandom.nextFloat() * 1.6f;
        }
        hearts = new float[]{-110f, -300f, -70f, -340f, -40f, -310f};
        bubbles = new float[]{-120f, 120f, -80f, 160f, -150f, 180f, 60f, 150f, 110f, 190f};
    }

    public void setState(PetState state) {
        this.state = state;
        invalidate();
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
        invalidate();
    }

    /** 播一段动作；动画结束后自动回 idle。 */
    public void play(int anim) {
        this.action = anim;
        this.actionStart = SystemClock.elapsedRealtime();
        this.actionDuration = anim == ANIM_EVOLVE ? 2600L : 1400L;
        invalidate();
    }

    public int currentAction() {
        return action;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(frame);
        post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        drawBackground(canvas, w, h);

        PetState s = state;
        if (s == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        float progress = 0f;
        if (action != ANIM_IDLE) {
            long elapsed = SystemClock.elapsedRealtime() - actionStart;
            progress = Math.min(1f, elapsed / (float) actionDuration);
            if (progress >= 1f) {
                action = ANIM_IDLE;
            }
        }

        // 1080p 下成年体连天线约 400px 高，给底部按钮和顶部状态留空间
        float unit = Math.min(w, h * 1.45f) / 2100f;
        float stageScale;
        switch (s.stage()) {
            case PetState.STAGE_BABY:
                stageScale = 0.72f;
                break;
            case PetState.STAGE_TEEN:
                stageScale = 0.86f;
                break;
            case PetState.STAGE_ADULT:
                stageScale = 1.0f;
                break;
            case PetState.STAGE_FINAL:
                stageScale = 1.08f;
                break;
            default:
                stageScale = 0.58f;
                break;
        }
        unit *= stageScale;

        float cx = w * 0.5f;
        float cy = h * 0.60f;
        // 胖瘦只改比例，不换图
        float fat = (s.weight - 50) / 50f; // -1..1
        float bodyW = 1f + fat * 0.38f;
        float bodyH = 1f - fat * 0.14f;
        float bob = (float) Math.sin(t * 2.1f) * 6f;
        if (action == ANIM_HAPPY) {
            bob -= (float) Math.sin(progress * Math.PI) * 46f;
        }
        if (action == ANIM_BATH) {
            bob += (float) Math.sin(progress * Math.PI * 6f) * 3f;
        }
        if (sleeping) {
            bob = (float) Math.sin(t * 1.1f) * 2.5f;
        }

        canvas.save();
        canvas.translate(cx, cy + bob * unit);
        canvas.scale(unit, unit);

        drawTail(canvas, s, t);
        drawWings(canvas, s, t);
        if (s.stage() == PetState.STAGE_EGG) {
            drawEgg(canvas, s, t);
            canvas.restore();
            drawForeground(canvas, w, h, s);
            return;
        }
        drawLegs(canvas, s, t);
        drawBody(canvas, s, bodyW, bodyH, t);
        drawClothes(canvas, s, bodyW, bodyH);
        drawArms(canvas, s, t, progress);
        drawHead(canvas, s, t, progress);
        drawEars(canvas, s, t);
        drawEyes(canvas, s, t);
        drawMouth(canvas, s, t, progress);
        drawHat(canvas, s);
        drawActionProp(canvas, s, progress);
        if (action == ANIM_EVOLVE) {
            drawEvolve(canvas, progress);
        }
        canvas.restore();

        drawForeground(canvas, w, h, s);
    }

    // ---------- 背景 ----------

    private void drawBackground(Canvas c, int w, int h) {
        if (bgShader == null || bgShaderW != w || bgShaderH != h) {
            bgShader = new LinearGradient(0, 0, 0, h,
                    COL_BG_TOP, COL_BG_BOTTOM, Shader.TileMode.CLAMP);
            bgShaderW = w;
            bgShaderH = h;
        }
        fill.setShader(bgShader);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        fill.setColor(COL_STAR);
        for (int i = 0; i < starX.length; i++) {
            c.drawCircle(starX[i] * w, starY[i] * h, starR[i], fill);
        }
        // 地面 + 地毯
        fill.setColor(COL_FLOOR);
        c.drawRect(0, h * 0.74f, w, h, fill);
        fill.setColor(COL_RUG);
        rect.set(w * 0.18f, h * 0.76f, w * 0.82f, h * 0.9f);
        c.drawOval(rect, fill);
        // 窗
        fill.setColor(0x223f6b9a);
        rect.set(w * 0.72f, h * 0.08f, w * 0.92f, h * 0.38f);
        c.drawRoundRect(rect, 18f, 18f, fill);
        stroke.setColor(0x557995b5);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, 18f, 18f, stroke);
        // 窗外：月亮 + 云
        fill.setColor(0x66ffe9a8);
        c.drawCircle(w * 0.84f, h * 0.16f, w * 0.018f, fill);
        fill.setColor(0x226b86a8);
        rect.set(w * 0.74f, h * 0.28f, w * 0.86f, h * 0.33f);
        c.drawRoundRect(rect, h * 0.025f, h * 0.025f, fill);
        rect.set(w * 0.78f, h * 0.34f, w * 0.92f, h * 0.39f);
        c.drawRoundRect(rect, h * 0.025f, h * 0.025f, fill);
    }

    // ---------- 部件 ----------

    private void drawEgg(Canvas c, PetState s, float t) {
        float wobble = (float) Math.sin(t * 2.4f) * 3f;
        path.reset();
        rect.set(-72f + wobble, -160f, 72f + wobble, 80f);
        path.addOval(rect, Path.Direction.CW);
        fill.setColor(COL_BODY_LIGHT);
        c.drawPath(path, fill);
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(7f);
        c.drawPath(path, stroke);
        // 裂纹
        stroke.setStrokeWidth(5f);
        c.drawLine(-20f + wobble, -60f, 6f + wobble, -20f, stroke);
        c.drawLine(6f + wobble, -20f, -10f + wobble, 10f, stroke);
        // 眼睛
        fill.setColor(COL_EYE);
        c.drawCircle(-24f + wobble, -70f, 8f, fill);
        c.drawCircle(18f + wobble, -70f, 8f, fill);
    }

    private void drawTail(Canvas c, PetState s, float t) {
        if (s.stage() < PetState.STAGE_TEEN) {
            return;
        }
        float wag = (float) Math.sin(t * 3.2f) * 12f;
        path.reset();
        path.moveTo(96f, 40f);
        path.cubicTo(150f, 40f, 190f, -10f + wag, 205f, 20f + wag);
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(22f);
        c.drawPath(path, stroke);
        stroke.setColor(COL_BODY);
        stroke.setStrokeWidth(14f);
        c.drawPath(path, stroke);
    }

    private void drawWings(Canvas c, PetState s, float t) {
        if (!"small".equals(s.wings) && !"big".equals(s.wings)) {
            return;
        }
        float size = "big".equals(s.wings) ? 1.35f : 1f;
        float flap = (float) Math.sin(t * 7f) * (10f + (action == ANIM_IDLE ? 0f : 14f));
        drawWing(c, -1, size, flap);
        drawWing(c, 1, size, flap);
    }

    private void drawWing(Canvas c, int dir, float size, float flap) {
        c.save();
        c.scale(dir * size, size);
        c.rotate(flap, 70f, -40f);
        path.reset();
        path.moveTo(60f, -40f);
        path.cubicTo(120f, -110f, 175f, -80f, 170f, -20f);
        path.cubicTo(160f, 10f, 120f, 20f, 80f, 10f);
        path.close();
        fill.setColor(COL_WING);
        c.drawPath(path, fill);
        stroke.setColor(COL_WING_EDGE);
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
        c.restore();
    }

    private void drawLegs(Canvas c, PetState s, float t) {
        float swing = (float) Math.sin(t * 2.1f) * 4f;
        drawLimb(c, -50f, 100f, -58f + swing, 178f, 20f);
        drawLimb(c, 50f, 100f, 58f - swing, 178f, 20f);
        fill.setColor(COL_OUTLINE);
        c.drawCircle(-58f + swing, 180f, 18f, fill);
        c.drawCircle(58f - swing, 180f, 18f, fill);
    }

    private void drawBody(Canvas c, PetState s, float bodyW, float bodyH, float t) {
        float squash = 1f + (float) Math.sin(t * 2.1f) * 0.018f;
        rect.set(-112f * bodyW, -64f * bodyH * squash, 112f * bodyW, 120f * bodyH * squash);
        fill.setColor(COL_BODY);
        c.drawRoundRect(rect, 56f, 56f, fill);
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(7f);
        c.drawRoundRect(rect, 56f, 56f, stroke);
        // 肚皮
        fill.setColor(COL_BODY_LIGHT);
        rect.set(-70f * bodyW, -20f, 70f * bodyW, 100f * bodyH);
        c.drawRoundRect(rect, 40f, 40f, fill);
    }

    private void drawClothes(Canvas c, PetState s, float bodyW, float bodyH) {
        if (s.clothes == null || s.clothes.isEmpty()) {
            return;
        }
        int color = "cape".equals(s.clothes) ? COL_SHIRT_2 : COL_SHIRT;
        fill.setColor(color);
        rect.set(-104f * bodyW, -58f, 104f * bodyW, 74f * bodyH);
        c.drawRoundRect(rect, 34f, 34f, fill);
        // 领口
        fill.setColor(COL_BODY_LIGHT);
        path.reset();
        path.moveTo(-34f, -60f);
        path.lineTo(0f, -34f);
        path.lineTo(34f, -60f);
        path.close();
        c.drawPath(path, fill);
        // 纽扣
        fill.setColor(0x88ffffff);
        c.drawCircle(0f, 10f, 6f, fill);
        c.drawCircle(0f, 40f, 6f, fill);
    }

    private void drawArms(Canvas c, PetState s, float t, float progress) {
        float swing = (float) Math.sin(t * 2.1f) * 8f;
        float leftAngle = 0f;
        float rightAngle = 0f;
        if (action == ANIM_EAT || action == ANIM_DRINK || action == ANIM_BRUSH) {
            rightAngle = -70f * (float) Math.sin(Math.min(1f, progress * 2f) * Math.PI);
        } else if (action == ANIM_PET) {
            leftAngle = 30f;
        } else if (action == ANIM_HAPPY) {
            leftAngle = 55f;
            rightAngle = -55f;
        }
        drawArm(c, -108f, -30f, -1, swing + leftAngle);
        drawArm(c, 108f, -30f, 1, swing + rightAngle);
    }

    private void drawArm(Canvas c, float x, float y, int dir, float angle) {
        c.save();
        c.rotate(angle, x, y);
        drawLimb(c, x, y, x + dir * 30f, y + 72f, 16f);
        fill.setColor(COL_BODY);
        c.drawCircle(x + dir * 30f, y + 72f, 15f, fill);
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(4f);
        c.drawCircle(x + dir * 30f, y + 72f, 15f, stroke);
        c.restore();
    }

    private void drawHead(Canvas c, PetState s, float t, float progress) {
        float nod = (float) Math.sin(t * 1.3f) * 2f;
        if (action == ANIM_EAT || action == ANIM_DRINK || action == ANIM_BRUSH) {
            nod += (float) Math.sin(progress * Math.PI) * 6f;
        }
        c.save();
        c.translate(0f, nod);
        rect.set(-104f, -268f, 104f, -64f);
        fill.setColor(COL_BODY);
        c.drawRoundRect(rect, 58f, 58f, fill);
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(7f);
        c.drawRoundRect(rect, 58f, 58f, stroke);
        c.restore();
    }

    private void drawEars(Canvas c, PetState s, float t) {
        if (s.stage() < PetState.STAGE_TEEN) {
            return;
        }
        float twitch = (float) Math.sin(t * 1.7f) * 4f;
        fill.setColor(COL_EAR);
        path.reset();
        path.moveTo(-78f, -262f);
        path.lineTo(-120f + twitch, -330f);
        path.lineTo(-46f, -286f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(78f, -262f);
        path.lineTo(120f - twitch, -330f);
        path.lineTo(46f, -286f);
        path.close();
        c.drawPath(path, fill);
        // 天线
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(4f);
        c.drawLine(-84f, -268f, -104f, -318f, stroke);
        c.drawLine(84f, -268f, 104f, -318f, stroke);
        fill.setColor(0xffffe27a);
        c.drawCircle(-104f, -322f, 8f, fill);
        c.drawCircle(104f, -322f, 8f, fill);
    }

    private void drawEyes(Canvas c, PetState s, float t) {
        boolean blink = !sleeping && s.stage() != PetState.STAGE_EGG
                && (t % 4.2f) < 0.12f;
        if (sleeping) {
            stroke.setColor(COL_EYE);
            stroke.setStrokeWidth(6f);
            c.drawArc(-62f, -196f, -18f, -160f, 0f, 180f, false, stroke);
            c.drawArc(18f, -196f, 62f, -160f, 0f, 180f, false, stroke);
            return;
        }
        if (blink) {
            stroke.setColor(COL_EYE);
            stroke.setStrokeWidth(6f);
            c.drawLine(-58f, -180f, -20f, -180f, stroke);
            c.drawLine(20f, -180f, 58f, -180f, stroke);
            return;
        }
        fill.setColor(COL_EYE_WHITE);
        c.drawCircle(-40f, -184f, 26f, fill);
        c.drawCircle(40f, -184f, 26f, fill);
        fill.setColor(COL_EYE);
        float look = (float) Math.sin(t * 0.8f) * 3f;
        c.drawCircle(-40f + look, -182f, 15f, fill);
        c.drawCircle(40f + look, -182f, 15f, fill);
        fill.setColor(0xffffffff);
        c.drawCircle(-35f + look, -188f, 5f, fill);
        c.drawCircle(45f + look, -188f, 5f, fill);
    }

    private void drawMouth(Canvas c, PetState s, float t, float progress) {
        boolean open = action == ANIM_EAT || action == ANIM_DRINK
                || action == ANIM_BRUSH || action == ANIM_EVOLVE;
        boolean sad = s.mood < 30;
        float mouthY = -120f;
        if (open) {
            fill.setColor(COL_MOUTH);
            rect.set(-28f, mouthY - 6f, 28f, mouthY + 24f);
            c.drawOval(rect, fill);
        } else {
            stroke.setColor(COL_MOUTH);
            stroke.setStrokeWidth(6f);
            path.reset();
            if (sad) {
                path.moveTo(-26f, mouthY + 4f);
                path.quadTo(0f, mouthY - 16f, 26f, mouthY + 4f);
            } else {
                path.moveTo(-26f, mouthY - 4f);
                path.quadTo(0f, mouthY + 16f, 26f, mouthY - 4f);
            }
            c.drawPath(path, stroke);
        }
        drawTeeth(c, s, mouthY, open);
    }

    private void drawTeeth(Canvas c, PetState s, float mouthY, boolean open) {
        if (s.teeth < 60) {
            fill.setColor(COL_DIRTY);
        } else {
            fill.setColor(COL_TEETH);
        }
        boolean teething = s.teethingUntil > System.currentTimeMillis();
        if (teething) {
            // 缺一颗门牙：画三颗，中间空一格
            c.drawRect(-22f, mouthY - 2f, -10f, mouthY + 10f, fill);
            c.drawRect(10f, mouthY - 2f, 22f, mouthY + 10f, fill);
        } else if (open || s.mood >= 60) {
            c.drawRect(-20f, mouthY - 2f, -8f, mouthY + 10f, fill);
            c.drawRect(-2f, mouthY - 2f, 6f, mouthY + 10f, fill);
            c.drawRect(14f, mouthY - 2f, 24f, mouthY + 10f, fill);
        }
    }

    private void drawHat(Canvas c, PetState s) {
        if (s.hat == null || s.hat.isEmpty()) {
            return;
        }
        int color = "beanie".equals(s.hat) ? COL_HAT_2 : COL_HAT;
        fill.setColor(color);
        if ("beanie".equals(s.hat)) {
            rect.set(-96f, -320f, 96f, -258f);
            c.drawRoundRect(rect, 44f, 44f, fill);
            fill.setColor(0xffffe27a);
            c.drawCircle(0f, -328f, 20f, fill);
        } else {
            rect.set(-104f, -306f, 104f, -272f);
            c.drawRoundRect(rect, 14f, 14f, fill);
            rect.set(-130f, -278f, 84f, -262f);
            c.drawRoundRect(rect, 8f, 8f, fill);
        }
    }

    private void drawActionProp(Canvas c, PetState s, float progress) {
        if (action == ANIM_EAT) {
            fill.setColor(0xffffb74d);
            rect.set(70f, -20f, 150f, 34f);
            c.drawRoundRect(rect, 16f, 16f, fill);
        } else if (action == ANIM_DRINK) {
            fill.setColor(0xff7ec8e3);
            rect.set(76f, -32f, 140f, 30f);
            c.drawRoundRect(rect, 12f, 12f, fill);
        } else if (action == ANIM_BRUSH) {
            fill.setColor(0xfff7f3e8);
            rect.set(60f, -30f, 150f, -10f);
            c.drawRoundRect(rect, 6f, 6f, fill);
            fill.setColor(0xff5fb6c9);
            rect.set(96f, -34f, 150f, -6f);
            c.drawRoundRect(rect, 4f, 4f, fill);
        } else if (action == ANIM_BATH) {
            fill.setColor(COL_BUBBLE);
            for (int i = 0; i < bubbles.length; i += 2) {
                float y = bubbles[i + 1] - progress * 160f;
                c.drawCircle(bubbles[i], y, 14f + (i % 3) * 4f, fill);
            }
        } else if (action == ANIM_PET) {
            fill.setColor(COL_HEART);
            for (int i = 0; i < hearts.length; i += 2) {
                float y = hearts[i + 1] - progress * 80f;
                drawHeart(c, hearts[i], y, 16f);
            }
        }
    }

    private void drawEvolve(Canvas c, float progress) {
        float r = 80f + progress * 260f;
        stroke.setColor(0x88ffe27a);
        stroke.setStrokeWidth(8f);
        c.drawCircle(0f, -60f, r, stroke);
        fill.setColor(0x33ffe27a);
        c.drawCircle(0f, -60f, r * 0.8f, fill);
    }

    private void drawHeart(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y + r * 0.7f);
        path.cubicTo(x - r * 1.4f, y - r * 0.3f, x - r * 0.5f, y - r * 1.3f, x, y - r * 0.4f);
        path.cubicTo(x + r * 0.5f, y - r * 1.3f, x + r * 1.4f, y - r * 0.3f, x, y + r * 0.7f);
        c.drawPath(path, fill);
    }

    private void drawLimb(Canvas c, float x1, float y1, float x2, float y2, float width) {
        stroke.setColor(COL_OUTLINE);
        stroke.setStrokeWidth(width + 6f);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(COL_BODY);
        stroke.setStrokeWidth(width);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void drawForeground(Canvas c, int w, int h, PetState s) {
        if (sleeping) {
            text.setColor(0xaa9fb6d8);
            text.setTextSize(h * 0.045f);
            c.drawText("z", w * 0.62f, h * 0.28f, text);
            c.drawText("Z", w * 0.66f, h * 0.23f, text);
            c.drawText("Z", w * 0.71f, h * 0.18f, text);
        }
    }
}
