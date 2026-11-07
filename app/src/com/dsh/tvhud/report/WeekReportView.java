package com.dsh.tvhud.report;

import com.dsh.tvhud.ui.Contrast;
import com.dsh.tvhud.ui.Palette;
import com.dsh.tvhud.usage.Categories;
import com.dsh.tvhud.usage.UsageDay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.List;
import java.util.Locale;

/** 周报：7 天节奏大柱 + 日流水 + 构成条 + 7×24 作息热力 + Top 片单。 */
public final class WeekReportView extends ReportCanvasView {
    private final Paint chartFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chartRect = new RectF();
    private static final float[] WEEK_STEPS = {30f, 60f, 120f};

    /** 一次 setReport 算一次：KPI 与 7×24 热力不随每帧重算。 */
    private Kpi[] cachedKpis;
    private long[][] cachedHeat;

    public WeekReportView(Context context, Palette palette) {
        super(context, palette);
        chartStroke.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onReportChanged() {
        if (r == null) {
            cachedKpis = null;
            cachedHeat = null;
            return;
        }
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
        total.label = "本期合计";
        total.en = "TOTAL";
        total.v1 = String.valueOf(ReportMath.hours(r.totalMs));
        total.u1 = "h";
        total.v2 = pad(ReportMath.minutes(r.totalMs));
        total.u2 = "m";
        total.spark = totals(r.days);
        if (r.prevComparable() && r.prevAvgMs() > 0) {
            long cur = r.avgMs();
            long prev = r.prevAvgMs();
            double pct = Math.abs(cur - prev) * 100.0 / prev;
            total.delta = String.format(Locale.US, "%.1f%%", pct);
            total.up = cur >= prev;
        }
        ks[0] = total;

        ks[1] = kpiAvg();
        ks[2] = kpiPeak();

        long we = 0L;
        int weN = 0;
        for (int i = 0; i < r.days.size(); i++) {
            DayAgg d = r.days.get(i);
            if (ReportMath.mondayIndex(d.dayStart) >= 5) {
                we += d.totalMs;
                weN++;
            }
        }
        long weekendAvg = weN == 0 ? 0L : we / weN;
        long wd = 0L;
        int wdN = 0;
        for (int i = 0; i < r.days.size(); i++) {
            DayAgg d = r.days.get(i);
            if (ReportMath.mondayIndex(d.dayStart) < 5) {
                wd += d.totalMs;
                wdN++;
            }
        }
        Kpi weekend = new Kpi();
        weekend.label = "周末日均";
        weekend.en = "WEEKEND AVG";
        weekend.v1 = String.valueOf(ReportMath.hours(weekendAvg));
        weekend.u1 = "h";
        weekend.v2 = pad(ReportMath.minutes(weekendAvg));
        weekend.u2 = "m";
        weekend.foot = "工作日 " + ReportMath.hhmm(wdN == 0 ? 0L : wd / wdN);
        ks[3] = weekend;
        return ks;
    }

    @Override
    protected void drawBody(Canvas c) {
        float leftW = 534f;
        float rightW = CONTENT_W - leftW - 6f;
        float rightX = PAD + leftW + 6f;

        // ---- 7 天节奏 + 构成条 ----
        card(c, PAD, ROW1_TOP, leftW, ROW1_H);
        text(c, "7 天节奏", PAD + 10f, ROW1_TOP + 15f, 9.5f, C_TEXT, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "DAILY RHYTHM", PAD + 68f, ROW1_TOP + 14f, 5.5f, Contrast.alpha(C_CYAN, 0.67f), din,
                Paint.Align.LEFT, 1.2f);
        drawLegend(c, PAD + 210f, ROW1_TOP + 14.5f, leftW - 218f);
        drawComposition(c, PAD + 10f, ROW1_TOP + 23f, leftW - 20f);
        drawWeekBars(c, PAD + 8f, ROW1_TOP + 56f, leftW - 16f, ROW1_H - 64f);

        // ---- 日流水 ----
        card(c, rightX, ROW1_TOP, rightW, ROW1_H);
        text(c, "日流水", rightX + 10f, ROW1_TOP + 15f, 9.5f, C_TEXT, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "DAY BY DAY", rightX + 50f, ROW1_TOP + 14f, 5.5f, Contrast.alpha(C_CYAN, 0.67f), din,
                Paint.Align.LEFT, 1.2f);
        text(c, "每天从几点看到几点", rightX + rightW - 10f, ROW1_TOP + 14.5f, 6.5f, C_DIM,
                cjk, Paint.Align.RIGHT, 0.2f);
        drawLedger(c, rightX + 10f, ROW1_TOP + 24f, rightW - 20f, ROW1_H - 30f);

        // ---- 时段热力 + Top ----
        float heatW = 492f;
        float topW = CONTENT_W - heatW - 6f;
        card(c, PAD, ROW2_TOP, heatW, ROW2_H);
        text(c, "时段分布", PAD + 10f, ROW2_TOP + 15f, 9.5f, C_TEXT, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "7×24 HEATMAP", PAD + 70f, ROW2_TOP + 14f, 5.5f, Contrast.alpha(C_CYAN, 0.67f), din,
                Paint.Align.LEFT, 1.2f);
        drawHeatScale(c, PAD + heatW - 74f, ROW2_TOP + 10f, 64f);
        drawWeekHeat(c, PAD + 10f, ROW2_TOP + 24f, heatW - 20f, ROW2_H - 30f);

        card(c, PAD + heatW + 6f, ROW2_TOP, topW, ROW2_H);
        topList(c, PAD + heatW + 6f, ROW2_TOP, topW, ROW2_H);
    }

    private void drawLegend(Canvas c, float x, float y, float maxW) {
        float cx = drawCategoryLegend(c, x, y, maxW);
        // 上期覆盖不足 80% / 超出保留窗口时不画对比，避免「对比 第 N 周」但没有数据
        if (!r.prevComparable()) {
            return;
        }
        // 上一周示意：直接标出是哪一周，不写「上周」
        chartStroke.setShader(null);
        chartStroke.setColor(Contrast.alpha(C_SUB, 0.60f));
        chartStroke.setStrokeWidth(0.8f);
        chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{2f, 2f}, 0f));
        c.drawLine(cx, y - 1.6f, cx + 12f, y - 1.6f, chartStroke);
        chartStroke.setPathEffect(null);
        String prevWeek = "对比 "
                + ReportPeriod.label(ReportPeriod.WEEK,
                        ReportPeriod.shift(ReportPeriod.WEEK, r.startDay, -1), r.todayStart);
        if (cx + 16f + measure(prevWeek, LEGEND, cjk, 0f) <= x + maxW) {
            text(c, prevWeek, cx + 16f, y, LEGEND, C_DIM, cjk, Paint.Align.LEFT, 0f);
        }
    }

    private void drawComposition(Canvas c, float x, float y, float w) {
        long[] cats = r.catMs;
        int[] pct = ReportMath.percentages(cats);
        float bx = x;
        for (int i = 0; i < 5; i++) {
            if (cats[i] <= 0) {
                continue;
            }
            float sw = w * cats[i] / Math.max(1L, r.totalMs);
            fill.setShader(null);
            fill.setColor(palette.dataColor(i));
            chartRect.set(bx, y, bx + sw, y + 4.5f);
            c.drawRoundRect(chartRect, 1.3f, 1.3f, fill);
            bx += sw + 1.2f;
        }
        // chips
        float cx = x;
        for (int i = 0; i < 5; i++) {
            if (cats[i] <= 0) {
                continue;
            }
            String s = Categories.name(i) + " " + ReportMath.hhmm(cats[i]) + " " + pct[i] + "%";
            float cw = measure(s, LEGEND, cjk, 0f) + 14f;
            if (cx + cw > x + w) {
                return;
            }
            fill.setColor(palette.dataColor(i));
            c.drawCircle(cx + 2f, y + 13f, 1.8f, fill);
            text(c, s, cx + 6.5f, y + 15.5f, LEGEND, C_SUB, cjk, Paint.Align.LEFT, 0f);
            cx += cw;
        }
    }

    private void drawWeekBars(Canvas c, float x, float y, float w, float h) {
        List<DayAgg> days = r.days;
        if (days.isEmpty()) {
            return;
        }
        long max = 60L;
        for (int i = 0; i < days.size(); i++) {
            max = Math.max(max, days.get(i).totalMs);
        }
        if (r.prevDays != null) {
            for (int i = 0; i < r.prevDays.size(); i++) {
                max = Math.max(max, r.prevDays.get(i).totalMs);
            }
        }
        ChartAxis axis = ChartAxis.of(max, WEEK_STEPS, 4f);
        float step = axis.step;
        float yMax = axis.yMax;
        float labelW = 20f;
        float plotX = x + labelW;
        float plotW = w - labelW;
        float plotTop = y + 4f;
        float plotH = h - 16f;
        float bottom = plotTop + plotH;

        // 网格 + 值
        chartStroke.setShader(null);
        chartStroke.setStrokeWidth(0.5f);
        chartText.setTypeface(din);
        chartText.setTextSize(5.8f);
        chartText.setTextAlign(Paint.Align.RIGHT);
        for (float v = 0; v <= yMax; v += step) {
            float gy = bottom - v / yMax * plotH;
            chartStroke.setColor(v == 0 ? Contrast.alpha(C_CYAN, 0.22f) : C_TRACK);
            c.drawLine(plotX, gy, x + w, gy, chartStroke);
            chartText.setColor(C_DIM);
            c.drawText(axisLabel(v), x + labelW - 4f, gy + 2f, chartText);
        }

        float slot = plotW / 7f;
        float bw = Math.min(30f, slot * 0.52f);
        // 上周刻度（只有可比时画，和 KPI/图例保持一致）
        chartStroke.setColor(Contrast.alpha(C_SUB, 0.53f));
        chartStroke.setStrokeWidth(0.7f);
        chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{2.5f, 2f}, 0f));
        for (int i = 0; i < 7 && i < r.prevDays.size() && r.prevComparable(); i++) {
            long pm = r.prevDays.get(i).totalMs;
            if (pm <= 0) {
                continue;
            }
            float cx = plotX + slot * i + slot / 2f;
            float py = bottom - (pm / 60000f) / yMax * plotH;
            c.drawLine(cx - bw / 2f - 2f, py, cx + bw / 2f + 2f, py, chartStroke);
        }
        chartStroke.setPathEffect(null);

        for (int i = 0; i < 7; i++) {
            DayAgg d = i < days.size() ? days.get(i) : null;
            float cx = plotX + slot * i + slot / 2f;
            float bx = cx - bw / 2f;
            long dayStart = d != null ? d.dayStart : r.startDay + i * UsageDay.DAY_MS;
            if (d == null) {
                text(c, "未到", cx, bottom - 3f, 5.8f, C_DIM2, cjk,
                        Paint.Align.CENTER, 0.4f);
            } else if (d.totalMs <= 0) {
                text(c, "无记录", cx, bottom - 3f, 5.6f, C_DIM, cjk,
                        Paint.Align.CENTER, 0.4f);
            } else {
                // 堆叠几何统一走 BarStack（已单元测试），别在视图里再手写一遍
                float barH = BarStack.stack(bottom, plotH, yMax, d.catMs, stackTops, stackBots);
                for (int ci = 0; ci < 5; ci++) {
                    if (d.catMs[ci] <= 0) {
                        continue;
                    }
                    chartFill.setShader(new LinearGradient(0, stackTops[ci], 0, stackBots[ci],
                            palette.dataColor(ci), Contrast.alpha(palette.dataColor(ci), 0.33f),
                            Shader.TileMode.CLAMP));
                    chartRect.set(bx, stackTops[ci] + 0.6f, bx + bw, stackBots[ci] - 0.6f);
                    c.drawRoundRect(chartRect, 1.2f, 1.2f, chartFill);
                    chartFill.setShader(null);
                }
                text(c, ReportMath.hhmm(d.totalMs), cx, bottom - barH - 5f,
                        6.8f, C_TEXT, din, Paint.Align.CENTER, 0.2f);
            }
            text(c, "周" + ReportMath.WEEK_CN[i],
                    cx, bottom + 8f, 6.5f, d == null ? C_DIM2 : C_SUB, cjk,
                    Paint.Align.CENTER, 0.6f);
            text(c, ReportMath.dateMd(dayStart), cx, bottom + 15f, 5.8f,
                    d == null ? C_DIM2 : C_DIM, din, Paint.Align.CENTER, 0.2f);
        }

        // 日均线
        if (r.totalMs > 0) {
            float ay = bottom - (r.avgMs() / 60000f) / yMax * plotH;
            chartStroke.setColor(Contrast.alpha(C_CYAN, 0.67f));
            chartStroke.setStrokeWidth(0.8f);
            chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{1.5f, 3f}, 0f));
            c.drawLine(plotX, ay, x + w, ay, chartStroke);
            chartStroke.setPathEffect(null);
        }
    }

    private void drawLedger(Canvas c, float x, float y, float w, float h) {
        float rowH = Math.min(19.6f, h / 7f);
        for (int i = 0; i < 7; i++) {
            DayAgg d = i < r.days.size() ? r.days.get(i) : null;
            float ry = y + rowH * i;
            float baseline = ry + rowH * 0.66f;
            long dayStart = d != null ? d.dayStart : r.startDay + i * UsageDay.DAY_MS;
            hairline(c, x, ry + rowH - 1f, w);
            text(c, "周" + ReportMath.WEEK_CN[i], x, baseline, 9.0f,
                    d == null ? C_DIM2 : C_SUB, cjk, Paint.Align.LEFT, 0f);
            text(c, ReportMath.dateMd(dayStart), x + 20f, baseline, 8.4f,
                    d == null ? C_DIM2 : C_DIM, din, Paint.Align.LEFT, 0f);
            if (d == null) {
                text(c, "—", x + 52f, baseline, 8.4f, C_DIM2, din, Paint.Align.LEFT, 0f);
                continue;
            }
            String time = d.totalMs <= 0 ? "—"
                    : ReportMath.clock(d.firstMs) + "–" + ReportMath.clock(d.lastMs);
            text(c, time, x + 52f, baseline, 8.8f, C_TEXT, din,
                    Paint.Align.LEFT, 0f);
            text(c, d.totalMs <= 0 ? "—" : ReportMath.hhmm(d.totalMs), x + 130f,
                    baseline, 9.2f, C_TEXT, din, Paint.Align.RIGHT, 0f);
            if (d.totalMs <= 0) {
                text(c, "无记录", x + 150f, baseline, 8.4f, C_DIM2, cjk,
                        Paint.Align.LEFT, 0f);
                continue;
            }
            int main = d.mainCat < 0 ? 4 : d.mainCat;
            fill.setShader(null);
            fill.setColor(palette.dataColor(main));
            c.drawCircle(x + 145f, ry + rowH * 0.62f, 1.5f, fill);
            text(c, Categories.name(main), x + 150f, baseline, 8.4f, C_SUB,
                    cjk, Paint.Align.LEFT, 0f);
            String name = d.mainCat == Categories.VIDEO ? topNameForDay(d.dayStart) : "";
            text(c, fit(name, w - 222f, 8.4f, cjk), x + 192f, baseline, 8.4f,
                    C_TEXT, cjk, Paint.Align.LEFT, 0f);
        }
    }

    private String topNameForDay(long dayStart) {
        String name = r.dayPlayName.get(dayStart);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        for (int i = 0; i < r.topPlays.size(); i++) {
            Report.TopPlay t = r.topPlays.get(i);
            long d = UsageDay.dayStartOf(t.lastMs);
            if (d == dayStart) {
                return t.name;
            }
        }
        return "";
    }

    private long[][] buildHeat() {
        long[][] heat = new long[7][24];
        for (int i = 0; i < r.days.size(); i++) {
            DayAgg d = r.days.get(i);
            int row = ReportMath.mondayIndex(d.dayStart);
            for (int hh = 0; hh < 24; hh++) {
                heat[row][hh] += d.hourMs[hh];
            }
        }
        return heat;
    }

    private void drawWeekHeat(Canvas c, float x, float y, float w, float h) {
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        heatmap(c, x, y, w, h, cachedHeat, labels, Math.min(11.5f, (h - 10f) / 7f), 2f);
    }

}
