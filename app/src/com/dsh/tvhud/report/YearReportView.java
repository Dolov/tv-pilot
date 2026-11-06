package com.dsh.tvhud.report;

import com.dsh.tvhud.usage.Categories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Calendar;

/** 年报：12 个月走势 + 月份×时段热力 + 季度构成 + 年度 Top。 */
public final class YearReportView extends ReportCanvasView {
    private final Paint chartFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chartRect = new RectF();
    private static final float[] MONTH_STEPS = {120f, 240f, 480f};

    /**
     * 年报的派生数据在 setReport 时算一次：月汇总/月×时热力要遍历 365 天，
     * 放到 onDraw 会让每次按键重绘都重算 ~6-8 遍。
     */
    private Kpi[] cachedKpis;
    private long[] cachedMonthTotals;
    private long[][] cachedMonthCats;
    private long[][] cachedHeat;

    public YearReportView(Context context) {
        super(context);
        chartStroke.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onReportChanged() {
        if (r == null) {
            cachedKpis = null;
            cachedMonthTotals = null;
            cachedMonthCats = null;
            cachedHeat = null;
            return;
        }
        cachedMonthTotals = buildMonthTotals();
        cachedMonthCats = buildMonthCats();
        cachedHeat = buildHeat();
        cachedKpis = buildKpis();
    }

    @Override
    protected Kpi[] kpis() {
        return cachedKpis;
    }

    private Kpi[] buildKpis() {
        Kpi[] ks = new Kpi[4];
        Kpi total = new Kpi();
        total.label = "全年合计";
        total.en = "TOTAL";
        total.v1 = String.valueOf(ReportMath.hours(r.totalMs));
        total.u1 = "h";
        total.v2 = pad(ReportMath.minutes(r.totalMs));
        total.u2 = "m";
        total.foot = r.hasPrev ? "" : "无同比 · 历史仅 365 天";
        ks[0] = total;

        Kpi avg = kpiAvg();
        avg.spark = monthlyTotals();   // 年报的迷你线看 12 个月走势
        ks[1] = avg;

        long[] mt = monthlyTotals();
        int peakMonth = 0;
        for (int i = 1; i < 12; i++) {
            if (mt[i] > mt[peakMonth]) {
                peakMonth = i;
            }
        }
        int elapsedMonths = monthlyCount();
        Kpi peak = new Kpi();
        peak.label = "最高月";
        peak.en = "PEAK MONTH";
        if (mt[peakMonth] <= 0L) {
            peak.v1 = "—";
            peak.foot = "暂无记录";
        } else {
            peak.v1 = String.valueOf(ReportMath.hours(mt[peakMonth]));
            peak.u1 = "h";
            peak.v2 = pad(ReportMath.minutes(mt[peakMonth]));
            peak.u2 = "m";
            peak.foot = (peakMonth + 1) + " 月 · 月均 "
                    + ReportMath.hhmm(elapsedMonths <= 0 ? 0L : r.totalMs / elapsedMonths);
        }
        ks[2] = peak;
        ks[3] = kpiCoverage("数据覆盖", "COVERAGE");
        return ks;
    }

    @Override
    protected void drawBody(Canvas c) {
        // 全年走势
        card(c, PAD, ROW1_TOP, CONTENT_W, ROW1_H);
        text(c, "全年走势", PAD + 10f, ROW1_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "12 MONTHS", PAD + 66f, ROW1_TOP + 14f, 5.5f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.2f);
        drawCategoryLegend(c, PAD + 500f, ROW1_TOP + 14.5f, 300f);
        text(c, "月均 " + ReportMath.hhmm(monthAvg()), PAD + CONTENT_W - 10f,
                ROW1_TOP + 14.5f, 6.5f, C_DIM, cjk, Paint.Align.RIGHT, 0.2f);
        drawMonths(c, PAD + 10f, ROW1_TOP + 26f, CONTENT_W - 20f, ROW1_H - 34f);

        // 月份 × 时段
        float heatW = 354f;
        float quarterW = 222f;
        float topW = CONTENT_W - heatW - quarterW - 12f;
        float heatX = PAD;
        float quarterX = heatX + heatW + 6f;
        float topX = quarterX + quarterW + 6f;

        card(c, heatX, ROW2_TOP, heatW, ROW2_H);
        text(c, "月份 × 时段", heatX + 10f, ROW2_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "SEASON × HOUR", heatX + 76f, ROW2_TOP + 14f, 5.2f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.1f);
        drawHeatScale(c, heatX + heatW - 74f, ROW2_TOP + 10f, 64f);
        drawMonthHourHeat(c, heatX + 10f, ROW2_TOP + 24f, heatW - 20f, ROW2_H - 30f);

        card(c, quarterX, ROW2_TOP, quarterW, ROW2_H);
        text(c, "季度构成", quarterX + 10f, ROW2_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "QUARTERS", quarterX + 66f, ROW2_TOP + 14f, 5.2f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.1f);
        text(c, "按分类", quarterX + quarterW - 10f, ROW2_TOP + 14.5f, 6.2f, C_DIM, cjk,
                Paint.Align.RIGHT, 0.2f);
        drawQuarters(c, quarterX + 10f, ROW2_TOP + 24f, quarterW - 20f, ROW2_H - 30f);

        card(c, topX, ROW2_TOP, topW, ROW2_H);
        topList(c, topX, ROW2_TOP, topW, ROW2_H);
    }

    private void drawMonths(Canvas c, float x, float y, float w, float h) {
        long[] mt = monthlyTotals();
        long[][] mc = monthlyCats();
        long max = 60L;
        for (int i = 0; i < 12; i++) {
            max = Math.max(max, mt[i]);
        }
        ChartAxis axis = ChartAxis.of(max, MONTH_STEPS, 2.5f);
        float step = axis.step;
        float yMax = axis.yMax;
        float labelW = 22f;
        float plotX = x + labelW;
        float plotW = w - labelW;
        float plotTop = y + 14f;
        float plotH = h - 26f;
        float bottom = plotTop + plotH;
        float slot = plotW / 12f;
        float bw = Math.min(30f, slot * 0.46f);

        // 季度分隔和 Q 标签
        chartStroke.setShader(null);
        chartStroke.setColor(0x2269aacc);
        chartStroke.setStrokeWidth(0.5f);
        chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{2f, 3f}, 0f));
        for (int q = 1; q < 4; q++) {
            float qx = plotX + slot * 3f * q;
            c.drawLine(qx, plotTop - 8f, qx, bottom, chartStroke);
        }
        chartStroke.setPathEffect(null);
        for (int q = 0; q < 4; q++) {
            float qx = plotX + slot * (3f * q + 1.5f);
            text(c, "Q" + (q + 1), qx, plotTop - 3f, 5.8f, 0xaa38bdf8, din,
                    Paint.Align.CENTER, 1.2f);
        }
        // 网格
        chartStroke.setColor(0x1469aacc);
        chartStroke.setStrokeWidth(0.5f);
        chartText.setTypeface(din);
        chartText.setTextSize(5.8f);
        chartText.setTextAlign(Paint.Align.RIGHT);
        for (float v = 0; v <= yMax; v += step) {
            float gy = bottom - v / yMax * plotH;
            chartStroke.setColor(v == 0 ? 0x3869aacc : 0x1269aacc);
            c.drawLine(plotX, gy, x + w, gy, chartStroke);
            chartText.setColor(C_DIM);
            c.drawText(axisLabel(v), x + labelW - 4f, gy + 2f, chartText);
        }
        // 柱
        for (int i = 0; i < 12; i++) {
            float bx = plotX + slot * i + (slot - bw) / 2f;
            // 堆叠几何统一走 BarStack（已单元测试）
            BarStack.stack(bottom, plotH, yMax, mc[i], stackTops, stackBots);
            for (int ci = 0; ci < 5; ci++) {
                if (mc[i][ci] <= 0) {
                    continue;
                }
                chartFill.setShader(new LinearGradient(0, stackTops[ci], 0, stackBots[ci],
                        Categories.color(ci), (Categories.color(ci) & 0x00ffffff) | 0x55000000,
                        Shader.TileMode.CLAMP));
                chartRect.set(bx, stackTops[ci] + 0.5f, bx + bw, stackBots[ci] - 0.5f);
                c.drawRoundRect(chartRect, 1.4f, 1.4f, chartFill);
                chartFill.setShader(null);
            }
            text(c, (i + 1) + " 月", bx + bw / 2f, bottom + 8f, 6f, C_SUB, cjk,
                    Paint.Align.CENTER, 0.2f);
            if (mt[i] > 0 && mt[i] == max && max > 60L) {
                text(c, ReportMath.hhmm(mt[i]), bx + bw / 2f,
                        bottom - plotH * (mt[i] / 60000f) / yMax - 4f, 6.4f, C_TEXT,
                        din, Paint.Align.CENTER, 0.2f);
            }
        }
        // 月均线
        long avg = monthAvg();
        if (avg > 0) {
            float ay = bottom - (avg / 60000f) / yMax * plotH;
            chartStroke.setColor(0xaa38bdf8);
            chartStroke.setStrokeWidth(0.8f);
            chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{1.5f, 3f}, 0f));
            c.drawLine(plotX, ay, x + w, ay, chartStroke);
            chartStroke.setPathEffect(null);
        }
    }

    private long[][] buildHeat() {
        long[][] heat = new long[12][24];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < r.days.size(); i++) {
            DayAgg d = r.days.get(i);
            cal.setTimeInMillis(d.dayStart);
            int m = cal.get(Calendar.MONTH);
            for (int hh = 0; hh < 24; hh++) {
                heat[m][hh] += d.hourMs[hh];
            }
        }
        return heat;
    }

    private void drawMonthHourHeat(Canvas c, float x, float y, float w, float h) {
        String[] labels = new String[12];
        for (int i = 0; i < 12; i++) {
            labels[i] = (i + 1) + "月";
        }
        heatmap(c, x, y, w, h, cachedHeat, labels, Math.min(8.6f, (h - 8f) / 12f), 1.2f);
    }

    private void drawQuarters(Canvas c, float x, float y, float w, float h) {
        long[][] qCats = new long[4][5];
        long[] qTotal = new long[4];
        long[] mt = monthlyTotals();
        long[][] mc = monthlyCats();
        for (int q = 0; q < 4; q++) {
            for (int m = q * 3; m < q * 3 + 3; m++) {
                qTotal[q] += mt[m];
                for (int ci = 0; ci < 5; ci++) {
                    qCats[q][ci] += mc[m][ci];
                }
            }
        }
        long max = 1L;
        for (int q = 0; q < 4; q++) {
            max = Math.max(max, qTotal[q]);
        }
        float rowH = Math.min(20f, h / 4f);
        float barH = 8f;
        float labelW = 20f;
        float trackX = x + labelW;
        float trackW = w - labelW - 38f;
        for (int q = 0; q < 4; q++) {
            float ry = y + rowH * q;
            text(c, "Q" + (q + 1), x, ry + rowH * 0.66f, 6.5f, C_SUB, din,
                    Paint.Align.LEFT, 0.4f);
            fill.setShader(null);
            fill.setColor(0x0d69aacc);
            chartRect.set(trackX, ry + (rowH - barH) / 2f,
                    trackX + trackW, ry + (rowH + barH) / 2f);
            c.drawRoundRect(chartRect, 1.2f, 1.2f, fill);
            float bx = trackX;
            for (int ci = 0; ci < 5; ci++) {
                if (qCats[q][ci] <= 0) {
                    continue;
                }
                float sw = trackW * qCats[q][ci] / max;
                fill.setColor(Categories.color(ci));
                chartRect.set(bx, ry + (rowH - barH) / 2f, bx + sw,
                        ry + (rowH + barH) / 2f);
                c.drawRoundRect(chartRect, 1.2f, 1.2f, fill);
                bx += sw;
            }
            text(c, qTotal[q] <= 0 ? "—" : ReportMath.hhmm(qTotal[q]), x + w,
                    ry + rowH * 0.66f, 6.5f, C_TEXT, din, Paint.Align.RIGHT, 0.2f);
        }
    }

    private long monthAvg() {
        int n = monthlyCount();
        return n <= 0 ? 0L : r.totalMs / n;
    }

    private int monthlyCount() {
        // 只统计「有用量」的月份，避免把没数据的月份算进月均
        long[] mt = monthlyTotals();
        int n = 0;
        for (int i = 0; i < 12; i++) {
            if (mt[i] > 0) {
                n++;
            }
        }
        return n;
    }

    private long[] buildMonthTotals() {
        long[] mt = new long[12];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < r.days.size(); i++) {
            cal.setTimeInMillis(r.days.get(i).dayStart);
            mt[cal.get(Calendar.MONTH)] += r.days.get(i).totalMs;
        }
        return mt;
    }

    private long[][] buildMonthCats() {
        long[][] mc = new long[12][5];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < r.days.size(); i++) {
            cal.setTimeInMillis(r.days.get(i).dayStart);
            int m = cal.get(Calendar.MONTH);
            for (int ci = 0; ci < 5; ci++) {
                mc[m][ci] += r.days.get(i).catMs[ci];
            }
        }
        return mc;
    }

    private long[] monthlyTotals() {
        return cachedMonthTotals;
    }

    private long[][] monthlyCats() {
        return cachedMonthCats;
    }
}
