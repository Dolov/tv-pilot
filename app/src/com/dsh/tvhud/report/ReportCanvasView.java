package com.dsh.tvhud.report;

import com.dsh.tvhud.usage.Categories;
import com.dsh.tvhud.usage.UsageDay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;
import java.util.Locale;

/**
 * 使用报告「数据大屏」的画布基类：背景、顶部导航、KPI 卡、卡片、周期条、页脚、
 * 以及三页共用的小图表（迷你线、环形、热力、Top 榜）。
 *
 * 坐标系：960×540dp，onDraw 里按 density 缩放，所有数值都按设计稿 dp 写。
 * 纯 Canvas 自绘，不依赖第三方图表库。
 */
public abstract class ReportCanvasView extends View {
    protected static final float SCREEN_W = 960f;
    protected static final float SCREEN_H = 540f;
    protected static final float PAD = 48f;
    protected static final float CONTENT_W = SCREEN_W - PAD * 2f;

    protected static final float HEADER_TOP = 16f;
    protected static final float INSIGHT_TOP = 37f;
    protected static final float KPI_TOP = 55f;
    protected static final float KPI_H = 62f;
    protected static final float ROW1_TOP = 124f;
    protected static final float ROW1_H = 172f;
    protected static final float ROW2_TOP = 302f;
    protected static final float ROW2_H = 152f;
    protected static final float RIBBON_TOP = 460f;
    protected static final float RIBBON_H = 52f;
    /** 页脚再往下压一点，尽量贴住屏幕底边（540 逻辑像素）。 */
    protected static final float FOOTER_TOP = 525f;

    protected static final int C_BG = 0xff06090f;
    protected static final int C_TEXT = 0xffe8f0f8;
    protected static final int C_SUB = 0xff93a4b8;
    protected static final int C_DIM = 0xff5c6b7d;
    protected static final int C_CYAN = 0xff38bdf8;
    protected static final int C_CYAN2 = 0xff22d3ee;
    protected static final int C_LINE = 0x2469aacc;
    protected static final int C_LINE_SOFT = 0x1469aacc;
    protected static final int C_PANEL_TOP = 0xff131e2b;
    protected static final int C_PANEL_BOT = 0xff090f17;
    protected static final int C_TRACK = 0x1469aacc;

    protected final float density;
    protected Typeface din = Typeface.DEFAULT;
    protected Typeface cjk = Typeface.DEFAULT;
    protected Report r;

    /** 字体加载一次就够：三页来回切粒度时不重复 createFromAsset/File。 */
    private static boolean sTypefacesLoaded;
    private static Typeface sDin = Typeface.DEFAULT;
    private static Typeface sCjk = Typeface.DEFAULT;

    /** 背景径向光晕的 Shader 只跟设计坐标有关，构造时建一次，不要每帧 new。 */
    private final RadialGradient topGlow;
    private final RadialGradient bottomGlow;

    /** 后台聚合失败时的提示；非 null 时画错误态，OK 键重试。 */
    private String loadError;

    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    /** 堆叠柱临时数组（5 个类别），三张图共用的绘制临时量。 */
    protected final float[] stackTops = new float[5];
    protected final float[] stackBots = new float[5];

    public ReportCanvasView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        loadTypefaces(context);
        din = sDin;
        cjk = sCjk;
        topGlow = new RadialGradient(SCREEN_W * 0.82f, -80f, 460f,
                new int[]{0x2438bdf8, 0x0038bdf8}, null, Shader.TileMode.CLAMP);
        bottomGlow = new RadialGradient(-60f, SCREEN_H + 60f, 420f,
                new int[]{0x1cf43098, 0x00f43098}, null, Shader.TileMode.CLAMP);
        setBackgroundColor(C_BG);
        stroke.setStyle(Paint.Style.STROKE);
    }

    /** 字体是静态共享的，只加载一次；失败就回退系统字体（不抛异常）。 */
    private static void loadTypefaces(Context context) {
        if (sTypefacesLoaded) {
            return;
        }
        sTypefacesLoaded = true;
        try {
            sDin = Typeface.createFromAsset(context.getAssets(), "DINPro_Medium.otf");
        } catch (Throwable ignored) {
        }
        try {
            sCjk = Typeface.createFromFile("/system/fonts/MILanPro_Normal.ttf");
        } catch (Throwable ignored) {
        }
        if (sDin == null) {
            sDin = Typeface.DEFAULT;
        }
        if (sCjk == null) {
            sCjk = Typeface.DEFAULT;
        }
    }

    /** 数据到达/清空时调用；子类可重写 {@link #onReportChanged()} 预计算派生数据。 */
    public void setReport(Report report) {
        this.r = report;
        if (report != null) {
            loadError = null;
            setContentDescription("使用报告 " + ReportPeriod.label(report.period, report.startDay,
                    report.todayStart) + "，合计 " + ReportMath.hhmm(report.totalMs)
                    + "，覆盖 " + report.recordedDays + "/" + report.elapsedDays + " 天");
        }
        onReportChanged();
        invalidate();
    }

    /** 每次 setReport 之后调一次，用来算 KPI / 月度汇总 / 热力矩阵，不要放到 onDraw。 */
    protected void onReportChanged() {
    }

    /** 后台聚合失败：清掉旧画面并画错误态，避免「看起来正常但其实没加载」。 */
    public void setLoadError(String message) {
        loadError = message == null || message.length() == 0 ? "报告加载失败" : message;
        r = null;
        onReportChanged();
        invalidate();
    }

    public boolean hasLoadError() {
        return loadError != null;
    }

    public void clearLoadError() {
        if (loadError != null) {
            loadError = null;
            invalidate();
        }
    }

    public Report report() {
        return r;
    }

    /** 每页的 4 张 KPI。 */
    protected abstract Kpi[] kpis();

    /** 每页自己的图表区。 */
    protected abstract void drawBody(Canvas c);

    // ================= 结构 =================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) {
            return;
        }
        // 960×540dp 设计稿等比缩放到实际尺寸并居中：非 16:9 / 非 2.0 密度的电视不会被裁掉
        float scale = Math.min(viewW / (SCREEN_W * density),
                viewH / (SCREEN_H * density)) * density;
        float offsetX = (viewW - SCREEN_W * scale) / 2f;
        float offsetY = (viewH - SCREEN_H * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas);
        if (r == null) {
            drawLoading(canvas);
            canvas.restore();
            return;
        }
        drawHeader(canvas);
        drawInsight(canvas);
        drawKpis(canvas);
        drawBody(canvas);
        drawRibbon(canvas);
        drawFooter(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c) {
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(C_BG);
        c.drawRect(0, 0, SCREEN_W, SCREEN_H, fill);
        fill.setShader(topGlow);
        c.drawRect(0, 0, SCREEN_W, SCREEN_H, fill);
        fill.setShader(bottomGlow);
        c.drawRect(0, 0, SCREEN_W, SCREEN_H, fill);
        fill.setShader(null);
        // 工程网格
        stroke.setShader(null);
        stroke.setColor(0x0d69aacc);
        stroke.setStrokeWidth(0.5f);
        for (float x = 0; x <= SCREEN_W; x += 32f) {
            c.drawLine(x, 0, x, SCREEN_H, stroke);
        }
        for (float y = 0; y <= SCREEN_H; y += 32f) {
            c.drawLine(0, y, SCREEN_W, y, stroke);
        }
    }

    private void drawLoading(Canvas c) {
        if (loadError != null) {
            text(c, loadError, SCREEN_W / 2f, SCREEN_H / 2f - 8f, 11f, 0xffff9bb8, cjk,
                    Paint.Align.CENTER, 0f);
            text(c, "按 OK 重试，返回上一页", SCREEN_W / 2f, SCREEN_H / 2f + 14f, 8.5f,
                    C_DIM, cjk, Paint.Align.CENTER, 0.2f);
            return;
        }
        text(c, "正在汇总使用数据…", SCREEN_W / 2f, SCREEN_H / 2f, 11f, C_SUB, cjk,
                Paint.Align.CENTER, 0f);
    }

    private void drawHeader(Canvas c) {
        float x = PAD;
        // tv-pilot
        text(c, "tv-pilot", x, HEADER_TOP + 10f, 8f, C_CYAN, din, Paint.Align.LEFT, 2.2f);
        x += measure("tv-pilot", 8f, din, 2.2f) + 10f;
        stroke.setColor(0x9969aacc);
        stroke.setStrokeWidth(0.7f);
        c.drawLine(x, HEADER_TOP + 1f, x, HEADER_TOP + 15f, stroke);
        x += 10f;
        // 标题
        numberText(c, "使用报告", x, HEADER_TOP + 13f, 15f, Paint.Align.LEFT);
        x += measure("使用报告", 15f, cjk, 0f) + 14f;

        // 周期范围 + 状态
        String range = ReportPeriod.range(r.coverageStartDay, r.viewEndDay);
        String status = r.current ? "进行中" : "已归档";
        float rangeW = measure(range, 8.5f, din, 0.4f) + measure(status, 7f, cjk, 0.6f) + 34f;
        pill(c, x, HEADER_TOP + 1f, rangeW, 14f, 0x1469aacc, C_LINE, 7f);
        text(c, range, x + 9f, HEADER_TOP + 11f, 8.5f, 0xffcfe0f0, din, Paint.Align.LEFT, 0.4f);
        text(c, status, x + rangeW - measure(status, 7f, cjk, 0.6f) - 9f,
                HEADER_TOP + 10.6f, 7f, r.current ? 0xff7ee2c8 : 0xff8aa0b8, cjk,
                Paint.Align.LEFT, 0.6f);

        // 右侧：粒度 tabs / 步进器 / 回到本期
        float right = PAD + CONTENT_W;
        float backW = 0f;
        if (!r.current) {
            backW = measure("回到本期", 7.5f, cjk, 0.5f) + 14f;
            float bx = right - backW;
            pill(c, bx, HEADER_TOP + 1f, backW, 14f, 0x1138bdf8, 0x5538bdf8, 7f);
            text(c, "回到本期", bx + 7f, HEADER_TOP + 10.6f, 7.5f, C_CYAN, cjk,
                    Paint.Align.LEFT, 0.5f);
            right -= backW + 8f;
        }
        // 步进器：直接显示具体周期（第 45 周 / 11 月 / 2025 年），宽度随文字自适应
        String stepLabel = periodLabel();
        float stepW = Math.max(74f, measure(stepLabel, 7.5f, cjk, 0.5f) + 32f);
        float sx = right - stepW;
        pill(c, sx, HEADER_TOP + 1f, stepW, 14f, 0x0d69aacc, C_LINE_SOFT, 7f);
        chevron(c, sx + 9f, HEADER_TOP + 8f, -1f);
        chevron(c, sx + stepW - 9f, HEADER_TOP + 8f, 1f);
        text(c, stepLabel, sx + stepW / 2f, HEADER_TOP + 10.6f, 7.5f, 0xffd5e5f5, cjk,
                Paint.Align.CENTER, 0.5f);
        right = sx - 8f;

        // tabs
        String[] tabs = {"周报", "月报", "年报"};
        ReportPeriod[] values = {ReportPeriod.WEEK, ReportPeriod.MONTH, ReportPeriod.YEAR};
        float tabW = 42f;
        for (int i = tabs.length - 1; i >= 0; i--) {
            float tx = right - tabW;
            boolean on = values[i] == r.period;
            if (on) {
                pill(c, tx, HEADER_TOP, tabW, 16f, 0x2a38bdf8, 0x8838bdf8, 8f);
            }
            text(c, tabs[i], tx + tabW / 2f, HEADER_TOP + 11f, 8f,
                    on ? 0xffe8f6ff : C_DIM, cjk, Paint.Align.CENTER, 0.8f);
            right = tx - 4f;
        }
    }

    /** 当前查看的具体周期名（步进器/周期条共用），如「第 45 周」「11 月」「2025 年」。 */
    private String periodLabel() {
        return ReportPeriod.label(r.period, r.startDay, r.todayStart);
    }

    private void drawInsight(Canvas c) {
        float x = PAD;
        float tagW = 34f;
        pill(c, x, INSIGHT_TOP, tagW, 13f, 0x1238bdf8, 0x5538bdf8, 6.5f);
        text(c, "洞察", x + 7f, INSIGHT_TOP + 9.6f, 7f, C_CYAN, cjk, Paint.Align.LEFT, 1f);
        x += tagW + 8f;
        String s = r.insight == null ? "" : r.insight;
        if (s.isEmpty()) {
            s = "本期没有记录到使用时间；屏保、熄屏不计入。";
        }
        if (r.staleSchemeDays > 0) {
            s = s + "（" + r.staleSchemeDays + " 天为旧分类口径）";
        }
        text(c, s, x, INSIGHT_TOP + 10f, 9.5f, C_SUB, cjk, Paint.Align.LEFT, 0.2f);
    }

    // ================= KPI =================

    protected static final class Kpi {
        String label = "";
        String en = "";
        String v1 = "";
        String u1 = "";
        String v2 = "";
        String u2 = "";
        String foot = "";
        String delta = "";
        boolean up;
        long[] spark;
        float progress = -1f;
        String progressLabel = "";
    }

    protected void drawKpis(Canvas c) {
        Kpi[] ks = kpis();
        if (ks == null) {
            return;
        }
        float gap = 6f;
        float w = (CONTENT_W - gap * 3f) / 4f;
        for (int i = 0; i < ks.length && i < 4; i++) {
            drawKpiCard(c, PAD + i * (w + gap), KPI_TOP, w, KPI_H, ks[i]);
        }
    }

    /** 日均使用 KPI：三页相同（标签、副注、迷你线一致）。 */
    protected Kpi kpiAvg() {
        Kpi k = new Kpi();
        k.label = "日均使用";
        k.en = "AVG / DAY";
        k.v1 = String.valueOf(ReportMath.hours(r.avgMs()));
        k.u1 = "h";
        k.v2 = pad(ReportMath.minutes(r.avgMs()));
        k.u2 = "m";
        k.foot = "已过 " + r.elapsedDays + " 天 · 有记录 " + r.recordedDays + " 天"
                + (r.isPartialCoverage() ? " · 仅窗口内" : "");
        k.spark = ribbonTotals();
        return k;
    }

    /** 单日峰值 KPI：三页相同；空数据降级成「—」，不显示假的 0h00m。 */
    protected Kpi kpiPeak() {
        Kpi k = new Kpi();
        k.label = "单日峰值";
        k.en = "PEAK DAY";
        long peakMs = r.maxDay == null ? 0L : r.maxDay.totalMs;
        if (peakMs <= 0L) {
            k.v1 = "—";
            k.foot = "暂无记录";
            return k;
        }
        long peakDay = r.maxDay == null ? r.startDay : r.maxDay.dayStart;
        k.v1 = String.valueOf(ReportMath.hours(peakMs));
        k.u1 = "h";
        k.v2 = pad(ReportMath.minutes(peakMs));
        k.u2 = "m";
        k.foot = ReportMath.weekdayCn(ReportMath.mondayIndex(peakDay)) + " "
                + ReportMath.dateMd(peakDay);
        return k;
    }

    /** 覆盖率 KPI（月报 / 年报共用，标签由调用方给）。 */
    protected Kpi kpiCoverage(String label, String en) {
        Kpi k = new Kpi();
        k.label = label;
        k.en = en;
        k.v1 = String.valueOf(r.recordedDays);
        k.u1 = "/";
        k.v2 = String.valueOf(r.elapsedDays);
        k.u2 = "天";
        k.progress = r.elapsedDays == 0 ? 0f
                : Math.min(1f, (float) r.recordedDays / r.elapsedDays);
        k.progressLabel = Math.round(k.progress * 100f) + "%";
        return k;
    }

    /**
     * 分类图例（周报 / 年报共用）。
     * @return 下一个可用的 x（调用方可以接着画「对比上一期」）
     */
    protected float drawCategoryLegend(Canvas c, float x, float y, float maxW) {
        float cx = x;
        for (int i = 0; i < 5; i++) {
            String name = Categories.name(i);
            float itemW = measure(name, 6.2f, cjk, 0f) + 9f;
            if (cx + itemW > x + maxW) {
                return cx;
            }
            fill.setShader(null);
            fill.setColor(Categories.color(i));
            rect.set(cx, y - 3.2f, cx + 3.2f, y);
            c.drawRoundRect(rect, 0.8f, 0.8f, fill);
            text(c, name, cx + 6.5f, y, 6.2f, C_SUB, cjk, Paint.Align.LEFT, 0f);
            cx += itemW + 10f;
        }
        return cx;
    }

    /** 热力色阶（周报 / 年报共用）。 */
    protected void drawHeatScale(Canvas c, float x, float y, float w) {
        text(c, "少", x, y + 4f, 5.5f, C_DIM, cjk, Paint.Align.LEFT, 0f);
        fill.setShader(new LinearGradient(x + 10f, 0, x + w - 14f, 0,
                0x1438bdf8, 0xff38bdf8, Shader.TileMode.CLAMP));
        rect.set(x + 10f, y - 1f, x + w - 14f, y + 3f);
        c.drawRoundRect(rect, 1f, 1f, fill);
        fill.setShader(null);
        text(c, "多", x + w - 10f, y + 4f, 5.5f, C_DIM, cjk, Paint.Align.LEFT, 0f);
    }

    private void drawKpiCard(Canvas c, float x, float y, float w, float h, Kpi k) {
        card(c, x, y, w, h);
        c.save();
        c.clipRect(x, y, x + w, y + h);
        text(c, k.label, x + 10f, y + 13f, 8.5f, C_SUB, cjk, Paint.Align.LEFT, 0.4f);
        text(c, k.en, x + w - 10f, y + 12.5f, 5.5f, C_DIM, din, Paint.Align.RIGHT, 1.2f);
        // 主值
        float bx = x + 10f;
        float baseline = y + 39f;
        gradientNumber(c, k.v1, bx, baseline, 23f);
        bx += measure(k.v1, 23f, din, 0.2f);
        if (k.u1.length() > 0) {
            text(c, k.u1, bx + 1.5f, baseline - 8f, 10f, C_SUB, din, Paint.Align.LEFT, 0f);
            bx += measure(k.u1, 10f, din, 0f) + 1.5f;
        }
        if (k.v2.length() > 0) {
            gradientNumber(c, k.v2, bx, baseline, 23f);
            bx += measure(k.v2, 23f, din, 0.2f);
        }
        if (k.u2.length() > 0) {
            text(c, k.u2, bx + 1.5f, baseline - 8f, 10f, C_SUB,
                    "天".equals(k.u2) ? cjk : din, Paint.Align.LEFT, 0f);
        }
        // 底部：delta / 备注 / 迷你线 / 进度
        float fy = y + 53f;
        float noteX = x + 10f;
        if (k.delta != null && k.delta.length() > 0) {
            float dw = measure(k.delta, 7f, din, 0.3f) + 12f;
            pill(c, noteX, fy - 8f, dw, 11f, k.up ? 0x1838bdf8 : 0x18f43098,
                    k.up ? 0x5538bdf8 : 0x55f43098, 5.5f);
            text(c, (k.up ? "▲ " : "▼ ") + k.delta, noteX + 5f, fy - 0.4f, 7f,
                    k.up ? C_CYAN : 0xffff9bb8, din, Paint.Align.LEFT, 0.3f);
            noteX += dw + 6f;
        }
        if (k.foot != null && k.foot.length() > 0) {
            text(c, k.foot, noteX, fy, 7.5f, C_DIM, cjk, Paint.Align.LEFT, 0.2f);
        }
        if (k.spark != null && k.spark.length > 1) {
            sparkline(c, x + w - 62f, y + 27f, 50f, 14f, k.spark, C_CYAN);
        }
        if (k.progress >= 0f) {
            float py = y + h - 8f;
            fill.setShader(null);
            fill.setColor(C_TRACK);
            rect.set(x + 10f, py, x + w - 10f, py + 2.5f);
            c.drawRoundRect(rect, 1.2f, 1.2f, fill);
            fill.setShader(new LinearGradient(x + 10f, 0, x + w - 10f, 0,
                    0x8838bdf8, C_CYAN, Shader.TileMode.CLAMP));
            rect.set(x + 10f, py, x + 10f + (w - 20f) * k.progress, py + 2.5f);
            c.drawRoundRect(rect, 1.2f, 1.2f, fill);
            fill.setShader(null);
            if (k.progressLabel.length() > 0) {
                text(c, k.progressLabel, x + w - 10f, py - 3f, 6.5f, C_DIM, din,
                        Paint.Align.RIGHT, 0.2f);
            }
        }
        c.restore();
    }

    // ================= 周期条 / 页脚 =================

    private void drawRibbon(Canvas c) {
        List<Report.PeriodStat> list = r.ribbon;
        if (list == null || list.isEmpty()) {
            return;
        }
        float x = PAD;
        float y = RIBBON_TOP;
        card(c, x, y, CONTENT_W, RIBBON_H);
        text(c, "PERIOD", x + 10f, y + 10.5f, 6f, 0xaa38bdf8, din, Paint.Align.LEFT, 1.6f);
        int count = list.size();
        String name = r.period == ReportPeriod.WEEK ? "近 " + count + " 周"
                : r.period == ReportPeriod.MONTH ? "近 " + count + " 个月"
                : "近 " + count + " 年";
        text(c, name + " · 左右切换", x + 52f, y + 10.5f, 7.5f, C_SUB, cjk,
                Paint.Align.LEFT, 0.4f);
        // 右上角不再写「当前周期 · 柱高 = 总时长」：周期条本身已经能看出选中项

        float areaX = x + 16f;
        float areaW = CONTENT_W - 32f;
        float slotW = areaW / count;
        float max = 1f;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, list.get(i).totalMs);
        }
        float barMaxH = 17f;
        // 柱子的基线；日期/周期文字统一画在基线下面，不再压进选中的柱子里
        float baseY = y + 38f;
        stroke.setShader(null);
        stroke.setColor(0x2469aacc);
        stroke.setStrokeWidth(0.5f);
        c.drawLine(areaX, baseY, areaX + areaW, baseY, stroke);
        for (int i = 0; i < count; i++) {
            Report.PeriodStat st = list.get(i);
            float sx = areaX + slotW * i;
            float cx = sx + slotW / 2f;
            float bw = Math.min(24f, slotW * 0.46f);
            float bh = st.totalMs <= 0 ? 2f : Math.max(2f, st.totalMs / max * barMaxH);
            boolean sel = st.selected;
            fill.setShader(null);
            if (st.outOfRange) {
                fill.setColor(0x1469aacc);
            } else if (sel) {
                fill.setShader(new LinearGradient(cx, baseY - bh, cx, baseY,
                        0xff7dd8ff, 0x4038bdf8, Shader.TileMode.CLAMP));
            } else {
                fill.setColor(0x88607380);
            }
            rect.set(cx - bw / 2f, baseY - bh, cx + bw / 2f, baseY);
            c.drawRoundRect(rect, 1.5f, 1.5f, fill);
            fill.setShader(null);
            if (sel) {
                stroke.setColor(0x6638bdf8);
                stroke.setStrokeWidth(0.8f);
                rect.set(cx - bw / 2f - 3f, baseY - bh - 3f, cx + bw / 2f + 3f, baseY);
                c.drawRoundRect(rect, 2f, 2f, stroke);
            }
            // 文字基线在基线下方 9 逻辑像素：选中柱再高也不会盖住日期，又不至于离柱子太远
            text(c, st.label, cx, y + 47f, 6.5f,
                    sel ? 0xffe8f6ff : (st.outOfRange ? 0xff3a4654 : C_DIM), din,
                    Paint.Align.CENTER, 0.2f);
            if (st.current) {
                fill.setColor(C_CYAN);
                c.drawCircle(cx + measure(st.label, 6.5f, din, 0.2f) / 2f + 3f, y + 45f,
                        1.4f, fill);
            }
        }
    }

    private void drawFooter(Canvas c) {
        text(c, "← → 切换周期    ↑ ↓ 周 / 月 / 年    OK 回到本期    返回 退出",
                PAD, FOOTER_TOP + 8f, 7.5f, C_DIM, cjk, Paint.Align.LEFT, 0.2f);
        String cover = "覆盖 " + r.recordedDays + "/" + r.elapsedDays + " 天";
        String right = "更新于 " + ReportMath.clock(System.currentTimeMillis())
                + " · " + cover + " · 数据只在本机";
        text(c, right, PAD + CONTENT_W, FOOTER_TOP + 8f, 7.5f, C_DIM, cjk,
                Paint.Align.RIGHT, 0.2f);
    }

    // ================= 共用图表 =================

    protected void card(Canvas c, float x, float y, float w, float h) {
        fill.setShader(new LinearGradient(x, y, x, y + h, C_PANEL_TOP, C_PANEL_BOT,
                Shader.TileMode.CLAMP));
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 6f, 6f, fill);
        fill.setShader(null);
        stroke.setShader(null);
        stroke.setColor(C_LINE);
        stroke.setStrokeWidth(0.5f);
        c.drawRoundRect(rect, 6f, 6f, stroke);
        // 顶部高光
        stroke.setColor(0xaa38bdf8);
        stroke.setStrokeWidth(1.2f);
        c.drawLine(x + 10f, y, x + 48f, y, stroke);
        // 右下切角
        stroke.setColor(0x3369aacc);
        stroke.setStrokeWidth(0.6f);
        c.drawLine(x + w - 9f, y + h - 2f, x + w - 2f, y + h - 2f, stroke);
        c.drawLine(x + w - 2f, y + h - 9f, x + w - 2f, y + h - 2f, stroke);
    }

    protected void pill(Canvas c, float x, float y, float w, float h, int fillColor,
                        int borderColor, float radius) {
        fill.setShader(null);
        fill.setColor(fillColor);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, radius, radius, fill);
        stroke.setShader(null);
        stroke.setColor(borderColor);
        stroke.setStrokeWidth(0.6f);
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    protected void topList(Canvas c, float x, float y, float w, float h) {
        text(c, "看了什么", x + 10f, y + 15f, 9f, 0xffd5e5f5, cjk, Paint.Align.LEFT, 0.5f);
        text(c, "TOP CONTENT", x + 68f, y + 14f, 5.5f, 0xaa38bdf8, din, Paint.Align.LEFT, 1.2f);
        text(c, "按集数", x + w - 10f, y + 14.5f, 7f, C_DIM, cjk, Paint.Align.RIGHT, 0.2f);
        List<Report.TopPlay> tops = r.topPlays;
        float top = y + 24f;
        float rowH = (h - 28f) / 5f;
        if (tops == null || tops.isEmpty()) {
            text(c, "本期没有可用的播放记录", x + 10f, top + 16f, 7.5f, C_DIM, cjk,
                    Paint.Align.LEFT, 0.2f);
            text(c, "播放日志会轮转，只看得到抓到的部分", x + 10f, top + 28f, 6.5f, 0x44697a8c,
                    cjk, Paint.Align.LEFT, 0.2f);
            return;
        }
        int maxEpisodes = 1;
        for (int i = 0; i < tops.size(); i++) {
            maxEpisodes = Math.max(maxEpisodes, tops.get(i).episodes);
        }
        for (int i = 0; i < tops.size() && i < 5; i++) {
            Report.TopPlay t = tops.get(i);
            float ry = top + rowH * i;
            text(c, i < 9 ? "0" + (i + 1) : String.valueOf(i + 1), x + 10f, ry + rowH * 0.62f,
                    8.5f, 0x8838bdf8, din, Paint.Align.LEFT, 0.3f);
            text(c, fit(t.name, w - 120f, 8.5f, cjk), x + 28f, ry + rowH * 0.62f,
                    8.5f, 0xffdbe8f4, cjk, Paint.Align.LEFT, 0.1f);
            String ep = t.episodes + " 集";
            float epW = measure(ep, 6.5f, din, 0.2f) + 8f;
            float epX = x + w - epW - 58f;
            pill(c, epX, ry + rowH * 0.30f, epW, 9f, 0x1238bdf8, 0x4438bdf8, 2.5f);
            text(c, ep, epX + 4f, ry + rowH * 0.30f + 6.6f, 6.5f, C_CYAN, din,
                    Paint.Align.LEFT, 0.2f);
            text(c, "最近 " + lastLabel(t.lastMs), x + w - 10f, ry + rowH * 0.62f,
                    6.5f, C_DIM, din, Paint.Align.RIGHT, 0.2f);
            // 比例条
            fill.setShader(null);
            fill.setColor(0x1069aacc);
            rect.set(x + 28f, ry + rowH - 3.5f, x + w - 10f, ry + rowH - 1f);
            c.drawRoundRect(rect, 1f, 1f, fill);
            fill.setShader(new LinearGradient(x + 28f, 0, x + w - 10f, 0,
                    0x66f43098, 0xfff43098, Shader.TileMode.CLAMP));
            rect.set(x + 28f, ry + rowH - 3.5f,
                    x + 28f + (w - 38f) * t.episodes / maxEpisodes, ry + rowH - 1f);
            c.drawRoundRect(rect, 1f, 1f, fill);
            fill.setShader(null);
        }
    }

    private String lastLabel(long ts) {
        if (r.period == ReportPeriod.WEEK) {
            return ReportMath.clock(ts);
        }
        return ReportMath.dateMd(UsageDay.dayStartOf(ts));
    }

    /** 7×24 / 12×24 热力：行数可变。 */
    protected void heatmap(Canvas c, float x, float y, float w, float h, long[][] matrix,
                           String[] rowLabels, float cellHs, float gap) {
        int rows = matrix.length;
        int cols = 24;
        long max = 1L;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                max = Math.max(max, matrix[i][j]);
            }
        }
        float labelW = 20f;
        float gridX = x + labelW;
        float gridW = w - labelW - 4f;
        float cw = (gridW - gap * (cols - 1)) / cols;
        float cellH = cellHs;
        for (int i = 0; i < rows; i++) {
            float cy = y + i * (cellH + gap);
            text(c, rowLabels[i], x, cy + cellH - 1.5f, 5.8f, C_DIM, cjk,
                    Paint.Align.LEFT, 0.2f);
            for (int j = 0; j < cols; j++) {
                double v = (double) matrix[i][j] / max;
                int alpha = v <= 0 ? 8 : (int) Math.min(242, 26 + 216 * Math.pow(v, 0.55));
                fill.setShader(null);
                fill.setColor(Color.argb(alpha, 56, 189, 248));
                rect.set(gridX + j * (cw + gap), cy, gridX + j * (cw + gap) + cw, cy + cellH);
                c.drawRoundRect(rect, 1f, 1f, fill);
            }
        }
        // 小时轴
        for (int j = 0; j < 5; j++) {
            float hx = gridX + (gridW) * j / 4f;
            String label = j == 4 ? "24" : String.valueOf(j * 6);
            text(c, label, hx, y + rows * (cellH + gap) + 6f, 5.5f, C_DIM, din,
                    j == 0 ? Paint.Align.LEFT : j == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER,
                    0f);
        }
    }

    protected void donut(Canvas c, float cx, float cy, float radius, float thickness) {
        long[] cats = r.catMs;
        long total = 0L;
        for (long v : cats) {
            total += v;
        }
        stroke.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(thickness);
        stroke.setColor(0x1469aacc);
        c.drawCircle(cx, cy, radius, stroke);
        if (total <= 0) {
            return;
        }
        float start = -90f;
        for (int i = 0; i < 5; i++) {
            if (cats[i] <= 0) {
                continue;
            }
            float sweep = cats[i] * 360f / total;
            float gap = Math.min(2.5f, sweep * 0.1f);
            stroke.setColor(Categories.color(i));
            c.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                    start + gap / 2f, Math.max(0.4f, sweep - gap), false, stroke);
            start += sweep;
        }
        stroke.setStyle(Paint.Style.STROKE);
    }

    protected void sparkline(Canvas c, float x, float y, float w, float h, long[] vals,
                             int color) {
        if (vals == null || vals.length < 2) {
            return;
        }
        long max = 1L;
        for (long v : vals) {
            max = Math.max(max, v);
        }
        path.reset();
        float step = w / (vals.length - 1);
        for (int i = 0; i < vals.length; i++) {
            float px = x + step * i;
            float py = y + h - (float) vals[i] / max * h;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        stroke.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(0.9f);
        stroke.setColor(color);
        c.drawPath(path, stroke);
        fill.setShader(null);
        fill.setColor(color);
        c.drawCircle(x + w, y + h - (float) vals[vals.length - 1] / max * h, 1.2f, fill);
    }

    // ================= 文字 / 图形工具 =================

    protected void text(Canvas c, String s, float x, float y, float size, int color,
                        Typeface tf, Paint.Align align, float tracking) {
        if (s == null || s.length() == 0) {
            return;
        }
        textPaint.setShader(null);
        textPaint.setTypeface(tf);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPaint.setTextAlign(align);
        textPaint.setLetterSpacing(tracking * 0.15f);
        textPaint.setStyle(Paint.Style.FILL);
        c.drawText(s, x, y, textPaint);
        textPaint.setLetterSpacing(0f);
    }

    private void numberText(Canvas c, String s, float x, float baseline, float size,
                            Paint.Align align) {
        textPaint.setShader(new LinearGradient(x, baseline - size, x, baseline,
                0xffffffff, 0xff9ec9e8, Shader.TileMode.CLAMP));
        textPaint.setTypeface(cjk);
        textPaint.setTextSize(size);
        textPaint.setColor(C_TEXT);
        textPaint.setTextAlign(align);
        textPaint.setLetterSpacing(0f);
        c.drawText(s, x, baseline, textPaint);
        textPaint.setShader(null);
    }

    private void gradientNumber(Canvas c, String s, float x, float baseline, float size) {
        if (s == null || s.length() == 0) {
            return;
        }
        textPaint.setShader(new LinearGradient(x, baseline - size, x, baseline,
                0xffffffff, 0xff86cdf5, Shader.TileMode.CLAMP));
        textPaint.setTypeface(din);
        textPaint.setTextSize(size);
        textPaint.setColor(C_TEXT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setLetterSpacing(0.1f);
        c.drawText(s, x, baseline, textPaint);
        textPaint.setShader(null);
        textPaint.setLetterSpacing(0f);
    }

    protected float measure(String s, float size, Typeface tf, float tracking) {
        if (s == null || s.length() == 0) {
            return 0f;
        }
        textPaint.setShader(null);
        textPaint.setTypeface(tf);
        textPaint.setTextSize(size);
        textPaint.setLetterSpacing(tracking * 0.15f);
        float w = textPaint.measureText(s);
        textPaint.setLetterSpacing(0f);
        return w;
    }

    protected String fit(String s, float maxW, float size, Typeface tf) {
        if (s == null) {
            return "";
        }
        if (measure(s, size, tf, 0f) <= maxW) {
            return s;
        }
        String ell = "…";
        for (int i = s.length() - 1; i > 0; i--) {
            String t = s.substring(0, i) + ell;
            if (measure(t, size, tf, 0f) <= maxW) {
                return t;
            }
        }
        return ell;
    }

    protected void chevron(Canvas c, float x, float y, float dir) {
        stroke.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(0.9f);
        stroke.setColor(C_CYAN);
        path.reset();
        path.moveTo(x - 2f * dir, y - 3f);
        path.lineTo(x + 2f * dir, y);
        path.lineTo(x - 2f * dir, y + 3f);
        c.drawPath(path, stroke);
    }

    protected static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    protected long[] totals(List<DayAgg> days) {
        long[] out = new long[days.size()];
        for (int i = 0; i < days.size(); i++) {
            out[i] = days.get(i).totalMs;
        }
        return out;
    }

    protected long[] ribbonTotals() {
        long[] out = new long[r.ribbon.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = r.ribbon.get(i).totalMs;
        }
        return out;
    }

    protected static String axisLabel(float minutes) {
        if (minutes <= 0f) {
            return "0";
        }
        if (minutes < 60f) {
            return ((int) minutes) + "m";
        }
        float h = minutes / 60f;
        if (h == (int) h) {
            return ((int) h) + "h";
        }
        return String.format(Locale.US, "%.1fh", h);
    }

    protected void hairline(Canvas c, float x, float y, float w) {
        stroke.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(0.5f);
        stroke.setColor(C_LINE_SOFT);
        c.drawLine(x, y, x + w, y, stroke);
    }
}
