package com.dsh.tvhud.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 2.5D 宠物渲染器：骨骼变换 + 分层部件，全部 Canvas 矢量绘制。
 *
 * 分层：房间背景/装饰 → 尾巴/翅膀/变异背部件 → 腿 → 身体 → 花纹 → 衣服 → 手臂 → 头/耳朵 →
 * 角/额纹 → 眼睛 → 分支眼镜 → 腮红 → 嘴/牙齿 → 小虎牙 → 厨师帽带 → 帽子 → 手持物/庆祝特效。
 *
 * 体型、阶段、分支、变异、退化、躲壳全部是「参数 + 槽位」：运行时合成，不预渲染。
 * 动画接口保持 P0 的 play()/setState()/setSleeping()，新增 ANIM_MUTATE/ANIM_BRANCH/ANIM_DEGRADE。
 *
 * 性能：30fps 自绘；onDraw 里不 new 任何对象、不拼字符串、不 split；Paint/Path/RectF 全部复用，
 * 星星/星爆/叶子等坐标在 init() 里预计算，渐变 shader 在 onSizeChanged() 里建一次。
 */
public final class PetView extends View {
    // ---------- 动画（保持 P0 的 0..7 编号不变） ----------
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_EAT = 1;
    public static final int ANIM_DRINK = 2;
    public static final int ANIM_PET = 3;
    public static final int ANIM_BATH = 4;
    public static final int ANIM_BRUSH = 5;
    public static final int ANIM_HAPPY = 6;
    public static final int ANIM_EVOLVE = 7;
    /** 变异庆祝：2.6s 彩色光环 + 星爆。 */
    public static final int ANIM_MUTATE = 8;
    /** 分支庆祝：2.6s 光柱 + 配色过渡。 */
    public static final int ANIM_BRANCH = 9;
    /** 退化表现：1.8s 变淡再恢复（不恐怖）。 */
    public static final int ANIM_DEGRADE = 10;

    // ---------- 背景：夜空 + 暖光房间 ----------
    private static final int COL_BG_TOP = 0xff22305a;
    private static final int COL_BG_BOTTOM = 0xff0b1120;
    private static final int COL_FLOOR = 0xff33406b;
    private static final int COL_FLOOR_EDGE = 0xff44568f;
    private static final int COL_RUG = 0xff3b3154;
    private static final int COL_STAR = 0x66ffffff;
    private static final int COL_SHADOW = 0x33000000;

    // ---------- 基础外观：暖黄 + 珊瑚 ----------
    private static final int COL_BODY = 0xffffcf6e;
    private static final int COL_BODY_LIGHT = 0xffffe6a8;
    private static final int COL_OUTLINE = 0xff5a3b1f;
    private static final int COL_EAR = 0xffff9f6e;
    private static final int COL_ACCENT = 0xffffb74d;
    private static final int COL_EYE = 0xff24303a;
    private static final int COL_EYE_WHITE = 0xfff7f3e8;
    private static final int COL_MOUTH = 0xff7a3b2e;
    private static final int COL_TEETH = 0xfff7f3e8;
    private static final int COL_DIRTY = 0xffd8c06a;
    private static final int COL_CHEEK = 0x55ff9f8f;
    private static final int COL_SHIRT = 0xff5fb6c9;
    private static final int COL_SHIRT_2 = 0xffe88b7d;
    private static final int COL_DENIM = 0xff4a6fa5;
    private static final int COL_DENIM_DARK = 0xff35527a;
    private static final int COL_HAT = 0xffe06c75;
    private static final int COL_HAT_2 = 0xff7c6bd6;
    private static final int COL_GOLD = 0xffffd54f;
    private static final int COL_GOLD_DARK = 0xffe0982f;
    private static final int COL_WING = 0xffe8f1ff;
    private static final int COL_WING_EDGE = 0xff9fb6d8;
    private static final int COL_BLANKET = 0xff5fb6c9;
    private static final int COL_BLANKET_2 = 0xff7ec8e3;
    private static final int COL_CREAM = 0xfffff3d8;
    private static final int COL_HEART = 0xffff7f9a;
    private static final int COL_BUBBLE = 0x99bfe9ff;

    // ---------- 分支进化配色 ----------
    private static final int COL_WIS_BODY = 0xffcdd6ff;   // 智慧线：浅蓝紫
    private static final int COL_WIS_LIGHT = 0xffeef2ff;
    private static final int COL_WIS_EAR = 0xffa9b8ff;
    private static final int COL_WIS_ACCENT = 0xff7c6bd6;
    private static final int COL_WIS_OUTLINE = 0xff3a3f78;

    private static final int COL_SWI_BODY = 0xff8ee8e0;   // 疾风线：青色
    private static final int COL_SWI_LIGHT = 0xffd9fbf7;
    private static final int COL_SWI_EAR = 0xff5fd6c9;
    private static final int COL_SWI_ACCENT = 0xff2fbfc9;
    private static final int COL_SWI_OUTLINE = 0xff1f5f6b;

    private static final int COL_GOU_BODY = 0xffffb3a0;   // 美食线：珊瑚粉
    private static final int COL_GOU_LIGHT = 0xffffe0d6;
    private static final int COL_GOU_EAR = 0xffff8f7a;
    private static final int COL_GOU_ACCENT = 0xffe06c75;
    private static final int COL_GOU_OUTLINE = 0xff7a3b2e;

    /** 变异配色：薄荷 / 淡紫 / 珊瑚 / 暖金 / 天蓝 / 粉红。 */
    private static final int[] MUT_BODY = {
            0xff9ff0d8, 0xffcbb8ff, 0xffffad95, 0xffffd977, 0xff9fdcff, 0xffffb8d4};
    private static final int[] MUT_LIGHT = {
            0xffe6fff6, 0xfff0e9ff, 0xffffe3d9, 0xfffff0c2, 0xffe3f6ff, 0xffffe6f1};
    private static final int[] MUT_EAR = {
            0xff63d8b4, 0xffa58cff, 0xffff8a70, 0xffffb74d, 0xff6fc6f2, 0xffff8fb8};
    private static final int[] MUT_ACCENT = {
            0xff2fbf9a, 0xff7c6bd6, 0xffe06c75, 0xffe0982f, 0xff3aa6d8, 0xffe0568f};

    /** 庆祝特效的彩虹色（含暖黄/珊瑚/湖蓝/淡紫，6 岁审美）。 */
    private static final int[] RAINBOW = {
            0xffffe27a, 0xffff9f8f, 0xff7ec8e3, 0xffa99bff,
            0xff5fd6c9, 0xffffb8d4, 0xffffd54f, 0xffff8fb8};

    // 预计算坐标（全部是相对 w/h 的比例，onDraw 里只做乘法，不分配）
    private static final float[] LEAF_DX = {-0.045f, -0.030f, -0.012f, 0.010f, 0.030f, 0.050f};
    private static final float[] LEAF_DY = {-0.010f, -0.030f, -0.045f, -0.040f, -0.028f, -0.006f};
    private static final float[] LEAF_ANGLE = {-50f, -25f, 0f, 20f, 45f, 65f};
    private static final float[] CLOTH_STAR_X = {-58f, 34f, -22f, 58f, 4f};
    private static final float[] CLOTH_STAR_Y = {-12f, 12f, 52f, 54f, 30f};
    private static final float[] BRANCH_SPARK_X = {
            -150f, -110f, -70f, -30f, 0f, 40f, 80f, 120f, 155f, -20f};
    private static final int SPARK_N = 12;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final Random starRandom = new Random(7L);
    private final float[] starX = new float[48];
    private final float[] starY = new float[48];
    private final float[] starR = new float[48];
    /** 五角星的单位顶点（10 个点，交替外/内半径），预计算避免每帧三角函数。 */
    private final float[] starUnitX = new float[10];
    private final float[] starUnitY = new float[10];
    /** 变异星爆：固定角度 + 半径抖动。 */
    private final float[] sparkAngle = new float[SPARK_N];
    private final float[] sparkRadius = new float[SPARK_N];
    /** 退化/躲壳的整体系数（饱和度 + 透明度），只在 init 里建一次。 */
    private final ColorFilter[] fadeFilters = new ColorFilter[6];
    private ColorFilter degradeFilter;
    private ColorFilter shellFilter;

    private Shader bgShader;
    private Shader glowShader;

    // 颜色/外观缓存：每帧由 computeLook() 算一次，绘制时只读
    private int lookBody = COL_BODY;
    private int lookLight = COL_BODY_LIGHT;
    private int lookEar = COL_EAR;
    private int lookAccent = COL_ACCENT;
    private int lookOutline = COL_OUTLINE;
    private int stage = PetState.STAGE_EGG;
    private boolean branchOn;
    private boolean wisdom;
    private boolean swift;
    private boolean gourmet;
    private boolean mutation;
    private boolean finalStage;
    private boolean shell;
    private boolean degraded;
    private int mutA;
    private int mutB;
    private int mutPal;

    // 房间装饰缓存：decor 字符串换引用时重新解析，onDraw 里不 split
    private String decorRef;
    private boolean decorRug;
    private boolean decorPlant;
    private boolean decorLamp;
    private boolean decorPoster;
    private boolean decorFish;
    private boolean decorBalloon;

    private PetState state;
    private boolean sleeping;
    private int action = ANIM_IDLE;
    private long actionStart;
    private long actionDuration = 1200L;
    private final float[] hearts = {-110f, -300f, -70f, -340f, -40f, -310f};
    private final float[] bubbles = {-120f, 120f, -80f, 160f, -150f, 180f, 60f, 150f, 110f, 190f};

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
        // 五角星顶点：外半径 1，内半径 0.45
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2d + i * Math.PI / 5d;
            float r = (i % 2 == 0) ? 1f : 0.45f;
            starUnitX[i] = (float) Math.cos(ang) * r;
            starUnitY[i] = (float) Math.sin(ang) * r;
        }
        // 星爆角度/半径
        for (int i = 0; i < SPARK_N; i++) {
            sparkAngle[i] = (float) (i * (Math.PI * 2d / SPARK_N) + starRandom.nextFloat() * 0.4f);
            sparkRadius[i] = 0.55f + starRandom.nextFloat() * 0.75f;
        }

        // 退化：降饱和 + 略暗，表现「有点累」，不生病、不发灰发绿
        degradeFilter = makeFilter(0.55f, 0.94f);
        // 躲壳：整体变淡
        shellFilter = makeFilter(0.90f, 0.72f);
        // 退化动画的淡出档位（0 = 不淡）
        for (int i = 0; i < fadeFilters.length; i++) {
            fadeFilters[i] = i == 0 ? null : makeFilter(1f, 1f - i * 0.13f);
        }
    }

    private static ColorFilter makeFilter(float saturation, float alpha) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(saturation);
        if (alpha < 1f) {
            ColorMatrix a = new ColorMatrix();
            a.setScale(1f, 1f, 1f, alpha);
            m.postConcat(a);
        }
        return new ColorMatrixColorFilter(m);
    }

    public void setState(PetState state) {
        this.state = state;
        if (state == null) {
            clearDecor();
        } else {
            refreshDecor(state);
        }
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
        this.actionDuration = durationOf(anim);
        invalidate();
    }

    public int currentAction() {
        return action;
    }

    private static long durationOf(int anim) {
        switch (anim) {
            case ANIM_EVOLVE:
            case ANIM_MUTATE:
            case ANIM_BRANCH:
                return 2600L;
            case ANIM_DEGRADE:
                return 1800L;
            default:
                return 1400L;
        }
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

    /** 渐变只在尺寸变化时建一次，保证 onDraw 稳态零分配。 */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        bgShader = new LinearGradient(0, 0, 0, h, COL_BG_TOP, COL_BG_BOTTOM,
                Shader.TileMode.CLAMP);
        float gr = Math.max(w, h) * 0.36f;
        glowShader = new RadialGradient(w * 0.5f, h * 0.56f, gr,
                0x40ffd98a, 0x00ffd98a, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        clearFilters();
        PetState s = state;
        if (s == null) {
            clearDecor();
        } else if (s.decor != decorRef) {
            refreshDecor(s);
        }

        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        float progress = updateAction();

        // 退化/躲壳只改显示阶段，不改真实进度
        int displayStage = s == null ? PetState.STAGE_EGG : s.displayStage(now);
        stage = displayStage;
        shell = s != null && displayStage != PetState.STAGE_EGG && s.isShell();
        degraded = s != null && s.isDegraded(now);
        finalStage = displayStage >= PetState.STAGE_FINAL;
        if (s != null) {
            computeLook(s, progress);
        }

        float unit = Math.min(w, h * 2.4f) / 2100f;
        switch (displayStage) {
            case PetState.STAGE_BABY:
                unit *= 0.85f;
                break;
            case PetState.STAGE_TEEN:
                unit *= 0.95f;
                break;
            case PetState.STAGE_ADULT:
                unit *= 1.0f;
                break;
            case PetState.STAGE_FINAL:
                unit *= 1.08f;
                break;
            default:
                unit *= 0.95f;
                break;
        }

        drawBackground(canvas, w, h, unit, t);
        if (s == null) {
            return;
        }

        // 胖瘦只改比例，不换图；美食线再圆一点
        float fat = (s.weight - 50) / 50f;
        float bodyW = 1f + fat * 0.38f;
        float bodyH = 1f - fat * 0.14f;
        if (gourmet) {
            bodyW *= 1.10f;
            bodyH *= 1.04f;
        }

        float bob = (float) Math.sin(t * 2.1f) * 6f;
        if (action == ANIM_HAPPY) {
            bob -= (float) Math.sin(progress * Math.PI) * 46f;
        } else if (action == ANIM_BATH) {
            bob += (float) Math.sin(progress * Math.PI * 6f) * 3f;
        } else if (action == ANIM_MUTATE) {
            bob += (float) Math.sin(progress * Math.PI * 2f) * 10f;
        } else if (action == ANIM_BRANCH) {
            bob -= (float) Math.sin(progress * Math.PI) * 26f;
        } else if (action == ANIM_DEGRADE) {
            bob *= 0.4f;
        }
        if (sleeping || shell) {
            bob = (float) Math.sin(t * 1.1f) * 2.5f;
        }

        float cx = w * 0.5f;
        float cy = h * 0.62f;

        applyFilter(pickFilter(progress));
        canvas.save();
        canvas.translate(cx, cy + bob * unit);
        canvas.scale(unit, unit);

        drawEffectsBehind(canvas, progress);
        if (displayStage == PetState.STAGE_EGG) {
            drawEgg(canvas, s, t);
        } else if (shell) {
            drawShell(canvas, s, t);
        } else {
            drawPet(canvas, s, t, progress, bodyW, bodyH);
        }
        drawEffectsFront(canvas, progress);
        canvas.restore();

        drawForeground(canvas, w, h, s);
    }

    private float updateAction() {
        if (action == ANIM_IDLE) {
            return 0f;
        }
        long elapsed = SystemClock.elapsedRealtime() - actionStart;
        float progress = Math.min(1f, elapsed / (float) actionDuration);
        if (progress >= 1f) {
            action = ANIM_IDLE;
            return 0f;
        }
        return progress;
    }

    // ---------- 颜色 / 外观合成 ----------

    /**
     * 每帧算一次当前外观：分支换色 → 变异换色 → 分支动画过渡。
     * 纯整数运算，不分配对象。
     */
    private void computeLook(PetState s, float progress) {
        int body = COL_BODY;
        int light = COL_BODY_LIGHT;
        int ear = COL_EAR;
        int accent = COL_ACCENT;
        int outline = COL_OUTLINE;

        branchOn = s.branch != PetState.BRANCH_NONE && s.stage() >= PetState.STAGE_ADULT;
        wisdom = branchOn && s.branch == PetState.BRANCH_WISDOM;
        swift = branchOn && s.branch == PetState.BRANCH_SWIFT;
        gourmet = branchOn && s.branch == PetState.BRANCH_GOURMET;

        if (wisdom) {
            body = COL_WIS_BODY;
            light = COL_WIS_LIGHT;
            ear = COL_WIS_EAR;
            accent = COL_WIS_ACCENT;
            outline = COL_WIS_OUTLINE;
        } else if (swift) {
            body = COL_SWI_BODY;
            light = COL_SWI_LIGHT;
            ear = COL_SWI_EAR;
            accent = COL_SWI_ACCENT;
            outline = COL_SWI_OUTLINE;
        } else if (gourmet) {
            body = COL_GOU_BODY;
            light = COL_GOU_LIGHT;
            ear = COL_GOU_EAR;
            accent = COL_GOU_ACCENT;
            outline = COL_GOU_OUTLINE;
        }

        mutation = s.mutationCount > 0;
        mutA = s.mutationPartA;
        mutB = s.mutationPartB;
        mutPal = s.mutationPalette;
        if (mutPal < 0 || mutPal >= MUT_BODY.length) {
            mutPal = 0;
        }
        if (mutation) {
            body = MUT_BODY[mutPal];
            light = MUT_LIGHT[mutPal];
            ear = MUT_EAR[mutPal];
            accent = MUT_ACCENT[mutPal];
            outline = mix(body, 0xff2a1a12, 0.72f);
        }

        // 分支动画：从基础暖黄平滑过渡到分支配色
        if (action == ANIM_BRANCH) {
            float f = progress / 0.55f;
            if (f > 1f) {
                f = 1f;
            }
            f = easeOut(f);
            body = mix(COL_BODY, body, f);
            light = mix(COL_BODY_LIGHT, light, f);
            ear = mix(COL_EAR, ear, f);
            accent = mix(COL_ACCENT, accent, f);
            outline = mix(COL_OUTLINE, outline, f);
        }

        lookBody = body;
        lookLight = light;
        lookEar = ear;
        lookAccent = accent;
        lookOutline = outline;
    }

    private boolean mutHas(int part) {
        return mutation && (mutA == part || mutB == part);
    }

    private static int mix(int a, int b, float f) {
        if (f <= 0f) {
            return a;
        }
        if (f >= 1f) {
            return b;
        }
        int aa = (a >>> 24) & 0xff;
        int ar = (a >> 16) & 0xff;
        int ag = (a >> 8) & 0xff;
        int ab = a & 0xff;
        int ba = (b >>> 24) & 0xff;
        int br = (b >> 16) & 0xff;
        int bg = (b >> 8) & 0xff;
        int bb = b & 0xff;
        int ra = (int) (aa + (ba - aa) * f);
        int rr = (int) (ar + (br - ar) * f);
        int rg = (int) (ag + (bg - ag) * f);
        int rb = (int) (ab + (bb - ab) * f);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static int alpha(int color, int a) {
        if (a < 0) {
            a = 0;
        } else if (a > 255) {
            a = 255;
        }
        return (a << 24) | (color & 0x00ffffff);
    }

    private static float easeOut(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    // ---------- 滤镜（退化 / 躲壳 / 退化动画） ----------

    private void clearFilters() {
        fill.setColorFilter(null);
        stroke.setColorFilter(null);
        text.setColorFilter(null);
    }

    private void applyFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        stroke.setColorFilter(cf);
        text.setColorFilter(cf);
    }

    private ColorFilter pickFilter(float progress) {
        if (action == ANIM_DEGRADE) {
            float f = (float) Math.sin(progress * Math.PI);
            if (f < 0f) {
                f = 0f;
            } else if (f > 1f) {
                f = 1f;
            }
            int idx = (int) (f * (fadeFilters.length - 0.01f));
            return fadeFilters[idx];
        }
        if (degraded) {
            return degradeFilter;
        }
        if (shell) {
            return shellFilter;
        }
        return null;
    }

    // ---------- 房间背景 + 装饰 ----------

    private void clearDecor() {
        decorRef = null;
        decorRug = false;
        decorPlant = false;
        decorLamp = false;
        decorPoster = false;
        decorFish = false;
        decorBalloon = false;
    }

    private void refreshDecor(PetState s) {
        decorRef = s.decor;
        decorRug = hasCsv(s.decor, "rug");
        decorPlant = hasCsv(s.decor, "plant");
        decorLamp = hasCsv(s.decor, "lamp");
        decorPoster = hasCsv(s.decor, "poster");
        decorFish = hasCsv(s.decor, "fish");
        decorBalloon = hasCsv(s.decor, "balloon");
    }

    /** 零分配的 CSV 成员判断（不 substring、不 split），可在 onDraw 里安全使用。 */
    private static boolean hasCsv(String csv, String slot) {
        if (csv == null || csv.isEmpty() || slot == null || slot.isEmpty()) {
            return false;
        }
        int n = csv.length();
        int m = slot.length();
        int from = 0;
        while (from <= n) {
            int at = csv.indexOf(',', from);
            int end = at < 0 ? n : at;
            if (end - from == m && csv.regionMatches(from, slot, 0, m)) {
                return true;
            }
            if (at < 0) {
                return false;
            }
            from = at + 1;
        }
        return false;
    }

    private void drawBackground(Canvas c, int w, int h, float unit, float t) {
        if (bgShader != null) {
            fill.setShader(bgShader);
        } else {
            fill.setShader(null);
            fill.setColor(COL_BG_TOP);
        }
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        // 星空
        fill.setColor(COL_STAR);
        for (int i = 0; i < starX.length; i++) {
            c.drawCircle(starX[i] * w, starY[i] * h, starR[i], fill);
        }

        // 窗 + 月亮 + 云
        fill.setColor(0x223f6b9a);
        rect.set(w * 0.72f, h * 0.08f, w * 0.92f, h * 0.38f);
        c.drawRoundRect(rect, 18f, 18f, fill);
        stroke.setColor(0x557995b5);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, 18f, 18f, stroke);
        fill.setColor(0x66ffe9a8);
        c.drawCircle(w * 0.84f, h * 0.16f, w * 0.018f, fill);
        fill.setColor(0x226b86a8);
        rect.set(w * 0.74f, h * 0.28f, w * 0.86f, h * 0.33f);
        c.drawRoundRect(rect, h * 0.025f, h * 0.025f, fill);
        rect.set(w * 0.78f, h * 0.34f, w * 0.92f, h * 0.39f);
        c.drawRoundRect(rect, h * 0.025f, h * 0.025f, fill);

        // 墙面装饰：海报、壁灯
        if (decorPoster) {
            drawPoster(c, w, h);
        }
        if (decorLamp) {
            drawLamp(c, w, h);
        }

        // 地面
        fill.setColor(COL_FLOOR);
        c.drawRect(0, h * 0.74f, w, h, fill);
        fill.setColor(COL_FLOOR_EDGE);
        c.drawRect(0, h * 0.74f, w, h * 0.748f, fill);

        // 地毯（装饰地毯叠在默认地毯上）
        drawRug(c, w, h);

        // 宠物身后的暖光（让暖黄宠物在夜空里更亮）
        if (glowShader != null) {
            float gr = Math.max(w, h) * 0.36f;
            fill.setShader(glowShader);
            c.drawCircle(w * 0.5f, h * 0.56f, gr, fill);
            fill.setShader(null);
        }

        // 地面影子
        float shadowY = h * 0.62f + (stage == PetState.STAGE_EGG ? 95f : 180f) * unit;
        float shadowW = (stage == PetState.STAGE_EGG ? 95f : 175f) * unit;
        fill.setColor(COL_SHADOW);
        rect.set(w * 0.5f - shadowW, shadowY - 18f * unit, w * 0.5f + shadowW, shadowY + 18f * unit);
        c.drawOval(rect, fill);

        // 地面装饰：盆栽、鱼缸
        if (decorPlant) {
            drawPlant(c, w, h);
        }
        if (decorFish) {
            drawFish(c, w, h, t);
        }
        // 天花板附近：气球
        if (decorBalloon) {
            drawBalloon(c, w, h, t);
        }
    }

    private void drawPoster(Canvas c, int w, int h) {
        float x0 = w * 0.05f;
        float y0 = h * 0.30f;
        float x1 = w * 0.185f;
        float y1 = h * 0.48f;
        fill.setColor(COL_SHIRT);
        rect.set(x0 - w * 0.006f, y0 - w * 0.006f, x1 + w * 0.006f, y1 + w * 0.006f);
        c.drawRoundRect(rect, 18f, 18f, fill);
        fill.setColor(COL_CREAM);
        rect.set(x0, y0, x1, y1);
        c.drawRoundRect(rect, 12f, 12f, fill);

        // 海报里的小电视兽
        float mx0 = x0 + (x1 - x0) * 0.24f;
        float mx1 = x1 - (x1 - x0) * 0.24f;
        float my0 = y0 + (y1 - y0) * 0.40f;
        float my1 = y1 - (y1 - y0) * 0.18f;
        fill.setColor(COL_EAR);
        rect.set(mx0, my0, mx1, my1);
        c.drawRoundRect(rect, 14f, 14f, fill);
        // 天线
        stroke.setColor(COL_MOUTH);
        stroke.setStrokeWidth(4f);
        c.drawLine((mx0 + mx1) * 0.5f, my0, (mx0 + mx1) * 0.5f - w * 0.018f, my0 - h * 0.03f, stroke);
        fill.setColor(COL_GOLD);
        c.drawCircle((mx0 + mx1) * 0.5f - w * 0.018f, my0 - h * 0.03f, w * 0.009f, fill);
        // 眼睛
        fill.setColor(COL_EYE);
        c.drawCircle((mx0 + mx1) * 0.5f - (mx1 - mx0) * 0.22f,
                (my0 + my1) * 0.48f, w * 0.0085f, fill);
        c.drawCircle((mx0 + mx1) * 0.5f + (mx1 - mx0) * 0.22f,
                (my0 + my1) * 0.48f, w * 0.0085f, fill);
        // 笑嘴
        stroke.setColor(COL_EYE);
        stroke.setStrokeWidth(3f);
        rect.set((mx0 + mx1) * 0.5f - w * 0.014f, (my0 + my1) * 0.56f,
                (mx0 + mx1) * 0.5f + w * 0.014f, (my0 + my1) * 0.78f);
        c.drawArc(rect, 20f, 140f, false, stroke);
    }

    private void drawLamp(Canvas c, int w, int h) {
        float lx = w * 0.63f;
        // 吊线
        stroke.setColor(0xff8fa4c4);
        stroke.setStrokeWidth(3f);
        c.drawLine(lx, 0f, lx, h * 0.075f, stroke);
        // 灯罩
        path.reset();
        path.moveTo(lx - w * 0.055f, h * 0.075f);
        path.lineTo(lx + w * 0.055f, h * 0.075f);
        path.lineTo(lx + w * 0.030f, h * 0.135f);
        path.lineTo(lx - w * 0.030f, h * 0.135f);
        path.close();
        fill.setColor(COL_ACCENT);
        c.drawPath(path, fill);
        stroke.setColor(COL_GOLD_DARK);
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
        // 灯泡
        fill.setColor(0xfffff0b3);
        c.drawCircle(lx, h * 0.146f, w * 0.014f, fill);
        // 光晕
        fill.setColor(0x22ffd98a);
        c.drawCircle(lx, h * 0.15f, w * 0.10f, fill);
        fill.setColor(0x18ffd98a);
        c.drawCircle(lx, h * 0.15f, w * 0.16f, fill);
    }

    private void drawRug(Canvas c, int w, int h) {
        float cx = w * 0.5f;
        float cy = h * 0.855f;
        float rx = w * 0.23f;
        float ry = h * 0.075f;
        // 默认地毯（深色，让地面不空）
        fill.setColor(COL_RUG);
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(rect, fill);
        if (!decorRug) {
            return;
        }
        fill.setColor(0xffe88b7d);
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(rect, fill);
        fill.setColor(COL_CREAM);
        rect.set(cx - rx * 0.76f, cy - ry * 0.72f, cx + rx * 0.76f, cy + ry * 0.72f);
        c.drawOval(rect, fill);
        fill.setColor(0xffff9f8f);
        rect.set(cx - rx * 0.46f, cy - ry * 0.42f, cx + rx * 0.46f, cy + ry * 0.42f);
        c.drawOval(rect, fill);
        // 小星星
        fill.setColor(COL_GOLD);
        drawStar(c, cx - rx * 0.9f, cy - ry * 1.02f, w * 0.008f);
        drawStar(c, cx + rx * 0.9f, cy - ry * 1.02f, w * 0.008f);
    }

    private void drawPlant(Canvas c, int w, int h) {
        float px = w * 0.10f;
        float base = h * 0.79f;
        // 叶子
        fill.setColor(0xff7ee0a3);
        for (int i = 0; i < LEAF_DX.length; i++) {
            float lx = px + LEAF_DX[i] * w;
            float ly = base - h * 0.135f + LEAF_DY[i] * h;
            c.save();
            c.rotate(LEAF_ANGLE[i], lx, ly);
            rect.set(lx - w * 0.022f, ly - h * 0.032f, lx + w * 0.022f, ly + h * 0.032f);
            c.drawOval(rect, fill);
            c.restore();
        }
        // 茎
        stroke.setColor(0xff4fae74);
        stroke.setStrokeWidth(5f);
        c.drawLine(px, base - h * 0.085f, px, base - h * 0.165f, stroke);
        // 花盆
        path.reset();
        path.moveTo(px - w * 0.045f, base - h * 0.085f);
        path.lineTo(px + w * 0.045f, base - h * 0.085f);
        path.lineTo(px + w * 0.032f, base);
        path.lineTo(px - w * 0.032f, base);
        path.close();
        fill.setColor(COL_SHIRT_2);
        c.drawPath(path, fill);
        stroke.setColor(0xffa8524a);
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
    }

    private void drawFish(Canvas c, int w, int h, float t) {
        float tx = w * 0.88f;
        float top = h * 0.645f;
        float bot = h * 0.79f;
        // 水
        fill.setColor(0x668fd9f0);
        rect.set(tx - w * 0.05f, top, tx + w * 0.05f, bot);
        c.drawRoundRect(rect, 14f, 14f, fill);
        fill.setColor(0x559fe6ff);
        rect.set(tx - w * 0.05f, top, tx + w * 0.05f, top + h * 0.03f);
        c.drawRoundRect(rect, 14f, 14f, fill);
        // 玻璃
        stroke.setColor(0xaaf7f3e8);
        stroke.setStrokeWidth(4f);
        rect.set(tx - w * 0.05f, top, tx + w * 0.05f, bot);
        c.drawRoundRect(rect, 14f, 14f, stroke);
        // 两条小鱼
        float f1x = tx + (float) Math.cos(t * 1.4f) * w * 0.018f;
        float f1y = (top + bot) * 0.5f + (float) Math.sin(t * 1.8f) * h * 0.018f;
        drawMiniFish(c, f1x, f1y, w * 0.012f, COL_EAR, 1f);
        float f2x = tx + (float) Math.cos(t * 1.1f + 2.6f) * w * 0.018f;
        float f2y = (top + bot) * 0.5f + (float) Math.sin(t * 1.4f + 1.2f) * h * 0.020f;
        drawMiniFish(c, f2x, f2y, w * 0.009f, COL_GOLD, -1f);
        // 泡泡
        fill.setColor(0x88e3f6ff);
        for (int i = 0; i < 3; i++) {
            float by = bot - ((t * 0.04f + i * 0.33f) % 1f) * (bot - top);
            c.drawCircle(tx + (i - 1) * w * 0.02f, by, w * 0.006f, fill);
        }
        // 底座
        fill.setColor(0xff8fa4c4);
        rect.set(tx - w * 0.055f, bot, tx + w * 0.055f, bot + h * 0.014f);
        c.drawRoundRect(rect, 6f, 6f, fill);
    }

    private void drawMiniFish(Canvas c, float x, float y, float r, int color, float dir) {
        fill.setColor(color);
        rect.set(x - r * 1.6f, y - r, x + r * 1.6f, y + r);
        c.drawOval(rect, fill);
        path.reset();
        path.moveTo(x + dir * r * 1.5f, y);
        path.lineTo(x + dir * r * 2.5f, y - r * 1.15f);
        path.lineTo(x + dir * r * 2.5f, y + r * 1.15f);
        path.close();
        c.drawPath(path, fill);
        if (dir > 0f) {
            fill.setColor(COL_EYE);
            c.drawCircle(x + r * 0.75f, y - r * 0.3f, r * 0.28f, fill);
        } else {
            fill.setColor(COL_EYE);
            c.drawCircle(x - r * 0.75f, y - r * 0.3f, r * 0.28f, fill);
        }
    }

    private void drawBalloon(Canvas c, int w, int h, float t) {
        float bx = w * 0.27f + (float) Math.sin(t * 0.7f) * w * 0.005f;
        float by = h * 0.12f + (float) Math.sin(t * 0.9f + 1f) * h * 0.008f;
        float bw = w * 0.032f;
        float bh = h * 0.055f;
        // 线
        stroke.setColor(0x88e8f1ff);
        stroke.setStrokeWidth(3f);
        path.reset();
        path.moveTo(bx, by + bh);
        path.quadTo(bx + w * 0.025f, by + bh + h * 0.09f, bx - w * 0.004f, by + bh + h * 0.16f);
        c.drawPath(path, stroke);
        // 气球
        fill.setColor(COL_EAR);
        rect.set(bx - bw, by - bh, bx + bw, by + bh);
        c.drawOval(rect, fill);
        fill.setColor(0x66ffffff);
        rect.set(bx - bw * 0.55f, by - bh * 0.6f, bx - bw * 0.05f, by - bh * 0.05f);
        c.drawOval(rect, fill);
        // 结
        fill.setColor(COL_HAT);
        path.reset();
        path.moveTo(bx - w * 0.008f, by + bh);
        path.lineTo(bx + w * 0.008f, by + bh);
        path.lineTo(bx, by + bh + h * 0.012f);
        path.close();
        c.drawPath(path, fill);
        // 第二个小气球（湖蓝）
        float bx2 = bx + w * 0.055f;
        float by2 = by + h * 0.05f;
        fill.setColor(COL_SHIRT);
        rect.set(bx2 - bw * 0.62f, by2 - bh * 0.62f, bx2 + bw * 0.62f, by2 + bh * 0.62f);
        c.drawOval(rect, fill);
        fill.setColor(0x55ffffff);
        rect.set(bx2 - bw * 0.35f, by2 - bh * 0.38f, bx2 - bw * 0.05f, by2 - bh * 0.05f);
        c.drawOval(rect, fill);
    }

    // ---------- 宠物主体 ----------

    private void drawPet(Canvas c, PetState s, float t, float progress,
                         float bodyW, float bodyH) {
        drawTail(c, s, t);
        drawWings(c, s, t);
        drawMutationBack(c, s, t);
        drawFinalHalo(c, t);
        drawLegs(c, s, t);
        drawBody(c, s, bodyW, bodyH, t);
        drawMutationBody(c, s, t);
        drawClothes(c, s, bodyW, bodyH);
        drawArms(c, s, t, progress);
        drawHead(c, s, t, progress);
        drawEars(c, s, t);
        drawMutationHead(c, s, t);
        drawEyes(c, s, t);
        drawBranchGlasses(c);
        drawCheeks(c);
        drawMouth(c, s, t, progress);
        drawMutationMouth(c, s);
        drawBranchBand(c);
        drawHat(c, s);
        drawActionProp(c, s, progress);
    }

    // ---------- 蛋 ----------

    private void drawEgg(Canvas c, PetState s, float t) {
        float wobble = (float) Math.sin(t * 2.4f) * 3f;
        path.reset();
        rect.set(-72f + wobble, -160f, 72f + wobble, 80f);
        path.addOval(rect, Path.Direction.CW);
        fill.setColor(lookLight);
        c.drawPath(path, fill);
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(7f);
        c.drawPath(path, stroke);
        // 蛋壳斑纹（暖黄 + 湖蓝，圆圆的）
        fill.setColor(alpha(lookAccent, 0x99));
        c.drawCircle(-34f + wobble, -70f, 10f, fill);
        c.drawCircle(38f + wobble, -18f, 8f, fill);
        c.drawCircle(-20f + wobble, 32f, 7f, fill);
        // 裂纹
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(5f);
        c.drawLine(-20f + wobble, -60f, 6f + wobble, -20f, stroke);
        c.drawLine(6f + wobble, -20f, -10f + wobble, 10f, stroke);
        // 眼睛
        fill.setColor(COL_EYE);
        c.drawCircle(-24f + wobble, -92f, 8f, fill);
        c.drawCircle(18f + wobble, -92f, 8f, fill);
    }

    // ---------- 尾巴 ----------

    private void drawTail(Canvas c, PetState s, float t) {
        if (stage < PetState.STAGE_TEEN) {
            return;
        }
        float wag = (float) Math.sin(t * 3.2f) * 12f;
        float tipX;
        float tipY;
        if (swift) {
            // 疾风线：闪电尾
            tipX = 210f;
            tipY = -24f + wag;
            path.reset();
            path.moveTo(92f, 40f);
            path.lineTo(132f, 28f + wag);
            path.lineTo(150f, 62f + wag);
            path.lineTo(184f, 18f + wag);
            path.lineTo(166f, -4f + wag);
            path.lineTo(210f, -24f + wag);
            stroke.setColor(lookOutline);
            stroke.setStrokeWidth(22f);
            c.drawPath(path, stroke);
            stroke.setColor(lookBody);
            stroke.setStrokeWidth(13f);
            c.drawPath(path, stroke);
            fill.setColor(COL_GOLD);
            c.drawCircle(tipX, tipY, 8f, fill);
        } else {
            tipX = 205f;
            tipY = 20f + wag;
            path.reset();
            path.moveTo(96f, 40f);
            path.cubicTo(150f, 40f, 190f, -10f + wag, 205f, 20f + wag);
            stroke.setColor(lookOutline);
            stroke.setStrokeWidth(22f);
            c.drawPath(path, stroke);
            stroke.setColor(lookBody);
            stroke.setStrokeWidth(14f);
            c.drawPath(path, stroke);
        }
        // 变异：星尾
        if (mutHas(4)) {
            fill.setColor(lookAccent);
            drawStar(c, tipX, tipY, 18f);
            fill.setColor(alpha(lookLight, 0xdd));
            drawStar(c, tipX - 26f, tipY - 14f + wag * 0.3f, 7f);
            fill.setColor(alpha(COL_GOLD, 0xcc));
            drawStar(c, tipX + 14f, tipY + 20f + wag * 0.2f, 5f);
        }
    }

    // ---------- 翅膀（装备 + 变异） ----------

    private void drawWings(Canvas c, PetState s, float t) {
        String wg = s.wings;
        if (wg == null || wg.isEmpty()) {
            return;
        }
        float flap = (float) Math.sin(t * 7f) * (10f + (action == ANIM_IDLE ? 0f : 14f));
        if ("butterfly".equals(wg)) {
            drawButterflyWing(c, -1, flap);
            drawButterflyWing(c, 1, flap);
        } else if ("small".equals(wg)) {
            drawWing(c, -1, 0.92f, flap);
            drawWing(c, 1, 0.92f, flap);
        } else if ("big".equals(wg)) {
            drawWing(c, -1, 1.4f, flap);
            drawWing(c, 1, 1.4f, flap);
        }
        // 未知槽位安全忽略
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
        fill.setColor(wisdom ? 0xfff4f0ff : COL_WING);
        c.drawPath(path, fill);
        stroke.setColor(wisdom ? 0xffa99bff : COL_WING_EDGE);
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
        if (wisdom) {
            // 智慧线：翅膀像书页
            stroke.setStrokeWidth(3f);
            c.drawLine(84f, -34f, 152f, -46f, stroke);
            c.drawLine(86f, -18f, 158f, -22f, stroke);
            c.drawLine(92f, -2f, 162f, 2f, stroke);
        }
        c.restore();
    }

    private void drawButterflyWing(Canvas c, int dir, float flap) {
        c.save();
        c.scale(dir, 1f);
        c.rotate(flap * 0.7f, 70f, -40f);
        path.reset();
        path.moveTo(58f, -34f);
        path.cubicTo(84f, -132f, 178f, -122f, 172f, -46f);
        path.cubicTo(168f, -4f, 120f, 16f, 76f, 6f);
        path.close();
        fill.setColor(0xffffb8d4);
        c.drawPath(path, fill);
        stroke.setColor(0xffe0568f);
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
        path.reset();
        path.moveTo(70f, 0f);
        path.cubicTo(98f, 42f, 152f, 66f, 142f, 12f);
        path.cubicTo(132f, -22f, 100f, -22f, 78f, 2f);
        path.close();
        fill.setColor(0xffa99bff);
        c.drawPath(path, fill);
        stroke.setColor(0xff7c6bd6);
        c.drawPath(path, stroke);
        fill.setColor(0xffffffff);
        c.drawCircle(118f, -72f, 11f, fill);
        c.drawCircle(142f, -42f, 7f, fill);
        fill.setColor(0x88ffffff);
        c.drawCircle(104f, 22f, 6f, fill);
        c.restore();
    }

    private void drawMutationBack(Canvas c, PetState s, float t) {
        if (!mutation) {
            return;
        }
        if (mutHas(6)) {
            drawShoulderWing(c, -1, t);
            drawShoulderWing(c, 1, t);
        }
        if (mutHas(2)) {
            // 星光环：围着头的椭圆轨道
            stroke.setColor(alpha(lookAccent, 0x44));
            stroke.setStrokeWidth(4f);
            rect.set(-158f, -268f, 158f, -112f);
            c.drawOval(rect, stroke);
            for (int i = 0; i < 8; i++) {
                float ang = t * 0.9f + i * 0.7853982f;
                float sx = (float) Math.cos(ang) * 156f;
                float sy = -190f + (float) Math.sin(ang) * 78f;
                fill.setColor(i % 2 == 0 ? alpha(COL_GOLD, 0xdd) : alpha(lookAccent, 0xdd));
                drawStar(c, sx, sy, i % 2 == 0 ? 12f : 8f);
            }
        }
    }

    private void drawShoulderWing(Canvas c, int dir, float t) {
        float flap = (float) Math.sin(t * 5f) * 6f;
        c.save();
        c.scale(dir, 1f);
        c.rotate(flap, 110f, -10f);
        path.reset();
        path.moveTo(104f, -26f);
        path.cubicTo(138f, -78f, 176f, -66f, 170f, -22f);
        path.cubicTo(166f, 2f, 136f, 12f, 110f, 8f);
        path.close();
        fill.setColor(alpha(lookAccent, 0xee));
        c.drawPath(path, fill);
        stroke.setColor(alpha(lookOutline, 0xcc));
        stroke.setStrokeWidth(4f);
        c.drawPath(path, stroke);
        c.restore();
    }

    // ---------- 完全体光环 ----------

    private void drawFinalHalo(Canvas c, float t) {
        if (!finalStage) {
            return;
        }
        stroke.setColor(0x66ffe27a);
        stroke.setStrokeWidth(6f);
        rect.set(-150f, -320f, 150f, -60f);
        c.drawOval(rect, stroke);
        for (int i = 0; i < 6; i++) {
            float ang = t * 0.7f + i * 1.0471976f;
            float sx = (float) Math.cos(ang) * 150f;
            float sy = -190f + (float) Math.sin(ang) * 130f;
            fill.setColor(alpha(COL_GOLD, 0xcc));
            drawStar(c, sx, sy, 9f);
        }
    }

    // ---------- 腿 / 身体 / 衣服 / 手臂 ----------

    private void drawLegs(Canvas c, PetState s, float t) {
        float swing = (float) Math.sin(t * 2.1f) * 4f;
        drawLimb(c, -50f, 100f, -58f + swing, 178f, 20f);
        drawLimb(c, 50f, 100f, 58f - swing, 178f, 20f);
        fill.setColor(lookOutline);
        c.drawCircle(-58f + swing, 180f, 18f, fill);
        c.drawCircle(58f - swing, 180f, 18f, fill);
    }

    private void drawBody(Canvas c, PetState s, float bodyW, float bodyH, float t) {
        float squash = 1f + (float) Math.sin(t * 2.1f) * 0.018f;
        rect.set(-112f * bodyW, -64f * bodyH * squash, 112f * bodyW, 120f * bodyH * squash);
        fill.setColor(lookBody);
        c.drawRoundRect(rect, 56f, 56f, fill);
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(7f);
        c.drawRoundRect(rect, 56f, 56f, stroke);
        // 肚皮
        fill.setColor(lookLight);
        rect.set(-70f * bodyW, -20f, 70f * bodyW, 104f * bodyH);
        c.drawRoundRect(rect, 40f, 40f, fill);
        // 侧边高光，让身体更亮更圆
        fill.setColor(0x33ffffff);
        rect.set(-98f * bodyW, -50f, -54f * bodyW, 30f);
        c.drawRoundRect(rect, 22f, 22f, fill);
    }

    private void drawMutationBody(Canvas c, PetState s, float t) {
        if (!mutHas(3)) {
            return;
        }
        // 花纹：身体侧面斑点 + 肚皮星星
        fill.setColor(alpha(lookAccent, 0x55));
        c.drawCircle(-82f, 16f, 13f, fill);
        c.drawCircle(-66f, 58f, 9f, fill);
        c.drawCircle(80f, 32f, 11f, fill);
        fill.setColor(alpha(lookAccent, 0x99));
        drawStar(c, 0f, 52f, 13f);
    }

    private void drawClothes(Canvas c, PetState s, float bodyW, float bodyH) {
        String cl = s.clothes;
        if (cl == null || cl.isEmpty()) {
            return;
        }
        if ("shirt".equals(cl)) {
            drawShirt(c, bodyW, bodyH);
        } else if ("cape".equals(cl)) {
            drawCape(c, bodyW, bodyH);
        } else if ("overalls".equals(cl)) {
            drawOveralls(c, bodyW, bodyH);
        } else if ("star".equals(cl)) {
            drawStarVest(c, bodyW, bodyH);
        }
        // 未知槽位安全忽略
    }

    private void drawShirt(Canvas c, float bodyW, float bodyH) {
        fill.setColor(COL_SHIRT);
        rect.set(-104f * bodyW, -58f, 104f * bodyW, 78f * bodyH);
        c.drawRoundRect(rect, 34f, 34f, fill);
        stroke.setColor(0xff3f8fa8);
        stroke.setStrokeWidth(5f);
        c.drawRoundRect(rect, 34f, 34f, stroke);
        // 领口
        fill.setColor(COL_CREAM);
        path.reset();
        path.moveTo(-34f, -60f);
        path.lineTo(0f, -32f);
        path.lineTo(34f, -60f);
        path.close();
        c.drawPath(path, fill);
        // 纽扣
        fill.setColor(0xccffffff);
        c.drawCircle(0f, 14f, 6f, fill);
        c.drawCircle(0f, 46f, 6f, fill);
    }

    private void drawCape(Canvas c, float bodyW, float bodyH) {
        path.reset();
        path.moveTo(-100f * bodyW, -58f);
        path.lineTo(-126f * bodyW, 96f * bodyH);
        path.lineTo(-70f * bodyW, 112f * bodyH);
        path.lineTo(-24f * bodyW, 96f * bodyH);
        path.lineTo(24f * bodyW, 112f * bodyH);
        path.lineTo(70f * bodyW, 96f * bodyH);
        path.lineTo(126f * bodyW, 96f * bodyH);
        path.lineTo(100f * bodyW, -58f);
        path.close();
        fill.setColor(COL_SHIRT_2);
        c.drawPath(path, fill);
        stroke.setColor(0xffa8524a);
        stroke.setStrokeWidth(5f);
        c.drawPath(path, stroke);
        // 领口 + 扣子
        fill.setColor(COL_CREAM);
        path.reset();
        path.moveTo(-36f, -60f);
        path.lineTo(0f, -30f);
        path.lineTo(36f, -60f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(COL_GOLD);
        c.drawCircle(0f, -30f, 8f, fill);
    }

    private void drawOveralls(Canvas c, float bodyW, float bodyH) {
        // 背带
        stroke.setColor(COL_DENIM);
        stroke.setStrokeWidth(15f);
        c.drawLine(-62f * bodyW, -50f, -50f * bodyW, -2f, stroke);
        c.drawLine(62f * bodyW, -50f, 50f * bodyW, -2f, stroke);
        // 裤身
        fill.setColor(COL_DENIM);
        rect.set(-84f * bodyW, -10f, 84f * bodyW, 104f * bodyH);
        c.drawRoundRect(rect, 28f, 28f, fill);
        stroke.setColor(COL_DENIM_DARK);
        stroke.setStrokeWidth(5f);
        c.drawRoundRect(rect, 28f, 28f, stroke);
        // 口袋
        fill.setColor(0x33ffffff);
        rect.set(-34f * bodyW, 30f, 34f * bodyW, 68f);
        c.drawRoundRect(rect, 10f, 10f, fill);
        stroke.setColor(COL_DENIM_DARK);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, 10f, 10f, stroke);
        // 扣子
        fill.setColor(COL_GOLD);
        c.drawCircle(-50f * bodyW, -2f, 8f, fill);
        c.drawCircle(50f * bodyW, -2f, 8f, fill);
    }

    private void drawStarVest(Canvas c, float bodyW, float bodyH) {
        fill.setColor(0xff2a3352);
        rect.set(-100f * bodyW, -58f, 100f * bodyW, 82f * bodyH);
        c.drawRoundRect(rect, 30f, 30f, fill);
        stroke.setColor(0xff1b2542);
        stroke.setStrokeWidth(5f);
        c.drawRoundRect(rect, 30f, 30f, stroke);
        // V 领
        fill.setColor(lookLight);
        path.reset();
        path.moveTo(-36f, -60f);
        path.lineTo(0f, -18f);
        path.lineTo(36f, -60f);
        path.close();
        c.drawPath(path, fill);
        // 星空
        fill.setColor(COL_GOLD);
        for (int i = 0; i < CLOTH_STAR_X.length; i++) {
            drawStar(c, CLOTH_STAR_X[i] * bodyW, CLOTH_STAR_Y[i], 8f);
        }
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
        } else if (action == ANIM_MUTATE || action == ANIM_BRANCH) {
            leftAngle = 38f * (float) Math.sin(progress * Math.PI);
            rightAngle = -38f * (float) Math.sin(progress * Math.PI);
        }
        drawArm(c, -108f, -30f, -1, swing + leftAngle);
        drawArm(c, 108f, -30f, 1, swing + rightAngle);
    }

    private void drawArm(Canvas c, float x, float y, int dir, float angle) {
        c.save();
        c.rotate(angle, x, y);
        drawLimb(c, x, y, x + dir * 30f, y + 72f, 16f);
        fill.setColor(lookBody);
        c.drawCircle(x + dir * 30f, y + 72f, 15f, fill);
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(4f);
        c.drawCircle(x + dir * 30f, y + 72f, 15f, stroke);
        c.restore();
    }

    // ---------- 头 / 耳朵 / 变异头部部件 ----------

    private void drawHead(Canvas c, PetState s, float t, float progress) {
        float nod = (float) Math.sin(t * 1.3f) * 2f;
        if (action == ANIM_EAT || action == ANIM_DRINK || action == ANIM_BRUSH) {
            nod += (float) Math.sin(progress * Math.PI) * 6f;
        }
        c.save();
        c.translate(0f, nod);
        rect.set(-104f, -268f, 104f, -64f);
        fill.setColor(lookBody);
        c.drawRoundRect(rect, 58f, 58f, fill);
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(7f);
        c.drawRoundRect(rect, 58f, 58f, stroke);
        // 额头高光
        fill.setColor(0x22ffffff);
        rect.set(-88f, -252f, -18f, -214f);
        c.drawRoundRect(rect, 30f, 30f, fill);
        c.restore();
    }

    private void drawEars(Canvas c, PetState s, float t) {
        if (stage < PetState.STAGE_TEEN) {
            return;
        }
        float twitch = (float) Math.sin(t * 1.7f) * 4f;
        fill.setColor(lookEar);
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
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(4f);
        c.drawLine(-84f, -268f, -104f, -318f, stroke);
        c.drawLine(84f, -268f, 104f, -318f, stroke);
        if (wisdom) {
            // 智慧线：天线变成小星星
            fill.setColor(COL_GOLD);
            drawStar(c, -104f, -322f, 13f);
            fill.setColor(COL_GOLD);
            drawStar(c, 104f, -322f, 13f);
        } else if (finalStage) {
            fill.setColor(COL_GOLD);
            drawStar(c, -104f, -322f, 11f);
            drawStar(c, 104f, -322f, 11f);
        } else {
            fill.setColor(0xffffe27a);
            c.drawCircle(-104f, -322f, 8f, fill);
            c.drawCircle(104f, -322f, 8f, fill);
        }
    }

    private void drawMutationHead(Canvas c, PetState s, float t) {
        if (!mutation) {
            return;
        }
        if (mutHas(1)) {
            // 小角：圆润的小尖角，不吓人
            fill.setColor(lookLight);
            stroke.setColor(lookOutline);
            stroke.setStrokeWidth(5f);
            path.reset();
            path.moveTo(-70f, -262f);
            path.quadTo(-74f, -306f, -58f, -318f);
            path.quadTo(-44f, -300f, -40f, -262f);
            path.close();
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
            path.reset();
            path.moveTo(70f, -262f);
            path.quadTo(74f, -306f, 58f, -318f);
            path.quadTo(44f, -300f, 40f, -262f);
            path.close();
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
        }
        if (mutHas(3)) {
            // 额头的小星纹
            fill.setColor(alpha(lookAccent, 0xcc));
            drawStar(c, 0f, -246f, 11f);
        }
    }

    // ---------- 眼睛 / 嘴 / 牙齿 ----------

    private void drawEyes(Canvas c, PetState s, float t) {
        boolean blink = !sleeping && stage != PetState.STAGE_EGG && (t % 4.2f) < 0.12f;
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
        if (degraded) {
            // 有点累：上眼皮压下来一点，不是生病
            fill.setColor(lookBody);
            c.drawCircle(-40f, -206f, 28f, fill);
            c.drawCircle(40f, -206f, 28f, fill);
            fill.setColor(COL_EYE);
            c.drawCircle(-40f + look, -176f, 6f, fill);
            c.drawCircle(40f + look, -176f, 6f, fill);
        }
    }

    private void drawBranchGlasses(Canvas c) {
        if (!wisdom) {
            return;
        }
        // 圆眼镜：镜片 + 金色镜框 + 桥
        fill.setColor(0x33ffffff);
        rect.set(-72f, -214f, -8f, -150f);
        c.drawOval(rect, fill);
        rect.set(8f, -214f, 72f, -150f);
        c.drawOval(rect, fill);
        stroke.setColor(COL_GOLD_DARK);
        stroke.setStrokeWidth(6f);
        rect.set(-72f, -214f, -8f, -150f);
        c.drawOval(rect, stroke);
        rect.set(8f, -214f, 72f, -150f);
        c.drawOval(rect, stroke);
        c.drawLine(-8f, -186f, 8f, -186f, stroke);
        c.drawLine(-72f, -184f, -106f, -198f, stroke);
        c.drawLine(72f, -184f, 106f, -198f, stroke);
        // 镜片反光
        fill.setColor(0x55ffffff);
        rect.set(-66f, -208f, -32f, -176f);
        c.drawOval(rect, fill);
        rect.set(14f, -208f, 48f, -176f);
        c.drawOval(rect, fill);
    }

    private void drawCheeks(Canvas c) {
        if (sleeping) {
            return;
        }
        fill.setColor(alpha(COL_CHEEK, 0x55));
        c.drawCircle(-72f, -140f, 12f, fill);
        c.drawCircle(72f, -140f, 12f, fill);
    }

    private void drawMouth(Canvas c, PetState s, float t, float progress) {
        boolean open = action == ANIM_EAT || action == ANIM_DRINK
                || action == ANIM_BRUSH || action == ANIM_EVOLVE
                || action == ANIM_MUTATE || action == ANIM_BRANCH;
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

    private void drawMutationMouth(Canvas c, PetState s) {
        if (!mutHas(5)) {
            return;
        }
        // 两颗小虎牙，加在嘴角，不挡表情
        fill.setColor(COL_TEETH);
        path.reset();
        path.moveTo(-25f, -124f);
        path.lineTo(-15f, -124f);
        path.lineTo(-20f, -108f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(15f, -124f);
        path.lineTo(25f, -124f);
        path.lineTo(20f, -108f);
        path.close();
        c.drawPath(path, fill);
    }

    // ---------- 分支配件 / 帽子 ----------

    private void drawBranchBand(Canvas c) {
        if (!gourmet) {
            return;
        }
        // 美食线：厨师帽带（白底红条 + 小结），即使没买帽子也有辨识度
        fill.setColor(COL_CREAM);
        rect.set(-96f, -274f, 96f, -244f);
        c.drawRoundRect(rect, 14f, 14f, fill);
        fill.setColor(COL_GOU_ACCENT);
        rect.set(-96f, -264f, 96f, -254f);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setColor(COL_CREAM);
        c.drawCircle(96f, -258f, 13f, fill);
        fill.setColor(COL_GOU_ACCENT);
        c.drawCircle(96f, -258f, 6f, fill);
    }

    private void drawHat(Canvas c, PetState s) {
        String hat = s.hat;
        if (hat == null || hat.isEmpty()) {
            return;
        }
        if ("cap".equals(hat)) {
            drawCap(c);
        } else if ("beanie".equals(hat)) {
            drawBeanie(c);
        } else if ("crown".equals(hat)) {
            drawCrown(c);
        } else if ("chef".equals(hat)) {
            drawChefHat(c);
        }
        // 未知槽位安全忽略
    }

    private void drawCap(Canvas c) {
        fill.setColor(COL_HAT);
        rect.set(-104f, -306f, 104f, -272f);
        c.drawRoundRect(rect, 14f, 14f, fill);
        fill.setColor(0xffc8565f);
        rect.set(-126f, -280f, 92f, -260f);
        c.drawRoundRect(rect, 8f, 8f, fill);
        fill.setColor(0x33ffffff);
        rect.set(-72f, -302f, -10f, -288f);
        c.drawRoundRect(rect, 7f, 7f, fill);
        fill.setColor(COL_GOLD);
        c.drawCircle(0f, -308f, 7f, fill);
    }

    private void drawBeanie(Canvas c) {
        fill.setColor(COL_HAT_2);
        rect.set(-96f, -322f, 96f, -258f);
        c.drawRoundRect(rect, 44f, 44f, fill);
        fill.setColor(0x22ffffff);
        rect.set(-70f, -312f, 10f, -290f);
        c.drawRoundRect(rect, 10f, 10f, fill);
        fill.setColor(COL_GOLD);
        c.drawCircle(0f, -330f, 20f, fill);
    }

    private void drawCrown(Canvas c) {
        path.reset();
        path.moveTo(-88f, -268f);
        path.lineTo(-92f, -330f);
        path.lineTo(-48f, -300f);
        path.lineTo(0f, -342f);
        path.lineTo(48f, -300f);
        path.lineTo(92f, -330f);
        path.lineTo(88f, -268f);
        path.close();
        fill.setColor(COL_GOLD);
        c.drawPath(path, fill);
        stroke.setColor(COL_GOLD_DARK);
        stroke.setStrokeWidth(5f);
        c.drawPath(path, stroke);
        // 宝石
        fill.setColor(COL_HAT);
        c.drawCircle(0f, -308f, 9f, fill);
        fill.setColor(COL_SHIRT);
        c.drawCircle(-52f, -302f, 6f, fill);
        c.drawCircle(52f, -302f, 6f, fill);
        // 底边
        fill.setColor(COL_GOLD_DARK);
        rect.set(-92f, -276f, 92f, -264f);
        c.drawRoundRect(rect, 6f, 6f, fill);
    }

    private void drawChefHat(Canvas c) {
        // 蓬松帽顶（三团云）
        fill.setColor(0xfffffdf5);
        c.drawCircle(-46f, -322f, 34f, fill);
        c.drawCircle(0f, -338f, 42f, fill);
        c.drawCircle(46f, -322f, 34f, fill);
        // 帽箍
        fill.setColor(COL_CREAM);
        rect.set(-90f, -288f, 90f, -258f);
        c.drawRoundRect(rect, 12f, 12f, fill);
        stroke.setColor(0xffd9cfb8);
        stroke.setStrokeWidth(4f);
        c.drawRoundRect(rect, 12f, 12f, stroke);
        // 红条
        fill.setColor(COL_HAT);
        rect.set(-90f, -278f, 90f, -268f);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    // ---------- 躲壳 / 被子 ----------

    private void drawShell(Canvas c, PetState s, float t) {
        // 枕头（只露眼睛的「暖和」版本，不黑暗不吓人）
        fill.setColor(0xffffe9c4);
        rect.set(-168f, -236f, 168f, -140f);
        c.drawRoundRect(rect, 42f, 42f, fill);
        stroke.setColor(0xffe0c9a0);
        stroke.setStrokeWidth(4f);
        c.drawRoundRect(rect, 42f, 42f, stroke);
        // 头 + 耳朵 + 眼睛
        drawHead(c, s, t, 0f);
        drawEars(c, s, t);
        drawEyes(c, s, t);
        // 被子
        float puff = (float) Math.sin(t * 1.6f) * 2f;
        fill.setColor(COL_BLANKET);
        rect.set(-212f, -118f + puff, 212f, 212f);
        c.drawRoundRect(rect, 64f, 64f, fill);
        stroke.setColor(0xff3f8fa8);
        stroke.setStrokeWidth(6f);
        c.drawRoundRect(rect, 64f, 64f, stroke);
        // 被子顶边波浪
        fill.setColor(COL_BLANKET_2);
        c.drawCircle(-122f, -118f + puff, 32f, fill);
        c.drawCircle(0f, -120f + puff, 34f, fill);
        c.drawCircle(122f, -118f + puff, 32f, fill);
        // 折边
        fill.setColor(COL_CREAM);
        rect.set(-212f, -118f + puff, 212f, -94f + puff);
        c.drawRoundRect(rect, 22f, 22f, fill);
        // 小星星
        fill.setColor(alpha(COL_GOLD, 0xcc));
        drawStar(c, -130f, 40f, 12f);
        drawStar(c, 130f, 46f, 10f);
        drawStar(c, 0f, 120f, 9f);
    }

    // ---------- 手持物 / 动作特效 ----------

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

    private void drawEffectsBehind(Canvas c, float progress) {
        if (action == ANIM_BRANCH) {
            drawBranchBeam(c, progress);
        } else if (action == ANIM_MUTATE) {
            drawMutateHalo(c, progress);
        }
    }

    private void drawEffectsFront(Canvas c, float progress) {
        if (action == ANIM_EVOLVE) {
            drawEvolve(c, progress);
        } else if (action == ANIM_MUTATE) {
            drawMutateSparks(c, progress);
        } else if (action == ANIM_BRANCH) {
            drawBranchSparks(c, progress);
        }
    }

    private void drawBranchBeam(Canvas c, float progress) {
        float a = (float) Math.sin(progress * Math.PI);
        if (a <= 0f) {
            return;
        }
        int al = (int) (a * 0x30);
        fill.setColor(alpha(0xffbfe9ff, al));
        c.drawRect(-170f, -520f, 170f, 210f, fill);
        fill.setColor(alpha(0xffe8f4ff, al + 0x18));
        c.drawRect(-90f, -520f, 90f, 210f, fill);
        fill.setColor(alpha(0xffffffff, al + 0x20));
        c.drawRect(-34f, -520f, 34f, 210f, fill);
        // 地面光斑
        fill.setColor(alpha(0xffffe27a, (int) (a * 0x40)));
        rect.set(-190f, 190f, 190f, 230f);
        c.drawOval(rect, fill);
    }

    private void drawBranchSparks(Canvas c, float progress) {
        for (int i = 0; i < BRANCH_SPARK_X.length; i++) {
            float p = (progress * 2.2f + i * 0.137f) % 1f;
            float y = 200f - p * 620f;
            int al = (int) ((1f - p) * 200);
            fill.setColor(alpha(i % 2 == 0 ? lookAccent : 0xffbfe9ff, al));
            drawStar(c, BRANCH_SPARK_X[i] + (float) Math.sin(p * 6f + i) * 10f, y,
                    6f + (i % 3) * 3f);
        }
    }

    private void drawMutateHalo(Canvas c, float progress) {
        float p = easeOut(progress);
        for (int i = 0; i < 3; i++) {
            float rp = p - i * 0.16f;
            if (rp <= 0f) {
                continue;
            }
            float rad = 80f + rp * 300f;
            int al = (int) ((1f - rp) * 0x90);
            stroke.setColor(alpha(RAINBOW[i], al));
            stroke.setStrokeWidth(10f - i * 2f);
            c.drawCircle(0f, -90f, rad, stroke);
        }
        fill.setColor(alpha(0xffffe9a8, (int) (p * 0x30)));
        c.drawCircle(0f, -90f, 90f + p * 130f, fill);
    }

    private void drawMutateSparks(Canvas c, float progress) {
        for (int i = 0; i < SPARK_N; i++) {
            float rad = (60f + sparkRadius[i] * 190f) * (0.35f + progress * 0.9f);
            float ang = sparkAngle[i] + progress * 2.4f;
            float x = (float) Math.cos(ang) * rad;
            float y = -90f + (float) Math.sin(ang) * rad * 0.75f;
            int al = (int) ((1f - progress * 0.7f) * 220);
            fill.setColor(alpha(RAINBOW[i % RAINBOW.length], al));
            drawStar(c, x, y, 7f + (i % 3) * 4f);
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

    // ---------- 基础绘制工具 ----------

    private void drawStar(Canvas c, float x, float y, float r) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            float px = x + starUnitX[i] * r;
            float py = y + starUnitY[i] * r;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        c.drawPath(path, fill);
    }

    private void drawHeart(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y + r * 0.7f);
        path.cubicTo(x - r * 1.4f, y - r * 0.3f, x - r * 0.5f, y - r * 1.3f, x, y - r * 0.4f);
        path.cubicTo(x + r * 0.5f, y - r * 1.3f, x + r * 1.4f, y - r * 0.3f, x, y + r * 0.7f);
        c.drawPath(path, fill);
    }

    private void drawLimb(Canvas c, float x1, float y1, float x2, float y2, float width) {
        stroke.setColor(lookOutline);
        stroke.setStrokeWidth(width + 6f);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(lookBody);
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
