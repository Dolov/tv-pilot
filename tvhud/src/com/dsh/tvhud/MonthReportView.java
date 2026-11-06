package com.dsh.tvhud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Locale;

/** 月报：日历热力 + 每日趋势 + 周内平均 + 环形构成 + Top。 */
public final class MonthReportView extends ReportCanvasView {
    private final Paint chartFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chartText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chartRect = new RectF();
    private static final float[] DAY_STEPS = {30f, 60f, 120f};

    /** KPI 只在 setReport 时算一次。 */
    private Kpi[] cachedKpis;

    public MonthReportView(Context context) {
        super(context);
        chartStroke.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onReportChanged() {
        cachedKpis = r == null ? null : buildKpis();
    }

    @Override
    protected Kpi[] kpis() {
        return cachedKpis;
    }

    private Kpi[] buildKpis() {
        Kpi[] ks = new Kpi[4];
        Kpi total = new Kpi();
        total.label = "本月合计";
        total.en = "TOTAL";
        total.v1 = String.valueOf(ReportMath.hours(r.totalMs));
        total.u1 = "h";
        total.v2 = pad(ReportMath.minutes(r.totalMs));
        total.u2 = "m";
        total.spark = totals(r.days);
        if (r.prevComparable() && r.prevAvgMs() > 0) {
            long cur = r.avgMs();
            long prev = r.prevAvgMs();
            total.delta = String.format(Locale.US, "%.1f%%",
                    Math.abs(cur - prev) * 100.0 / prev);
            total.up = cur >= prev;
        }
        ks[0] = total;

        ks[1] = kpiAvg();
        ks[2] = kpiPeak();
        ks[3] = kpiCoverage("有记录天数", "COVERAGE");
        return ks;
    }

    @Override
    protected void drawBody(Canvas c) {
        float calW = 507f;
        float donutX = PAD + calW + 6f;
        float donutW = CONTENT_W - calW - 6f;

        // 日历
        card(c, PAD, ROW1_TOP, calW, ROW1_H);
        text(c, "日历热力", PAD + 10f, ROW1_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "CALENDAR", PAD + 66f, ROW1_TOP + 14f, 5.5f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.2f);
        text(c, "颜色越亮，当天看得越久", PAD + calW - 10f, ROW1_TOP + 14.5f, 6.5f, C_DIM,
                cjk, Paint.Align.RIGHT, 0.2f);
        drawCalendar(c, PAD + 10f, ROW1_TOP + 22f, calW - 20f, ROW1_H - 30f);

        // 环形
        card(c, donutX, ROW1_TOP, donutW, ROW1_H);
        text(c, "内容构成", donutX + 10f, ROW1_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "MIX", donutX + 66f, ROW1_TOP + 14f, 5.5f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.2f);
        text(c, "按观看时长", donutX + donutW - 10f, ROW1_TOP + 14.5f, 6.5f, C_DIM, cjk,
                Paint.Align.RIGHT, 0.2f);
        drawDonut(c, donutX, ROW1_TOP, donutW, ROW1_H);

        // 每日趋势 / 周内平均 / Top
        float dailyW = 385f;
        float weekW = 187f;
        float topW = CONTENT_W - dailyW - weekW - 12f;
        float dailyX = PAD;
        float weekX = PAD + dailyW + 6f;
        float topX = weekX + weekW + 6f;

        card(c, dailyX, ROW2_TOP, dailyW, ROW2_H);
        text(c, "每日趋势", dailyX + 10f, ROW2_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "DAILY TREND", dailyX + 66f, ROW2_TOP + 14f, 5.5f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.2f);
        text(c, "虚线 = 日均 " + ReportMath.hhmm(r.avgMs()), dailyX + dailyW - 10f,
                ROW2_TOP + 14.5f, 6.2f, C_DIM, cjk, Paint.Align.RIGHT, 0.2f);
        drawDaily(c, dailyX + 8f, ROW2_TOP + 24f, dailyW - 16f, ROW2_H - 32f);

        card(c, weekX, ROW2_TOP, weekW, ROW2_H);
        text(c, "周内平均", weekX + 10f, ROW2_TOP + 15f, 9.5f, 0xffd5e5f5, cjk,
                Paint.Align.LEFT, 0.6f);
        text(c, "BY WEEKDAY", weekX + 10f, ROW2_TOP + 24f, 5.2f, 0xaa38bdf8, din,
                Paint.Align.LEFT, 1.1f);
        drawWeekdayAvg(c, weekX + 8f, ROW2_TOP + 30f, weekW - 16f, ROW2_H - 38f);

        card(c, topX, ROW2_TOP, topW, ROW2_H);
        topList(c, topX, ROW2_TOP, topW, ROW2_H);
    }

    private void drawCalendar(Canvas c, float x, float y, float w, float h) {
        int firstIndex = ReportMath.mondayIndex(r.startDay);
        int totalDays = daysInMonth(r.startDay);
        int rows = (int) Math.ceil((firstIndex + totalDays) / 7.0);
        float gap = 2.4f;
        float weekdayH = 8f;
        float cellW = (w - gap * 6f) / 7f;
        float cellH = (h - weekdayH - 4f - gap * (rows - 1)) / rows;
        String[] wd = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            text(c, wd[i], x + (cellW + gap) * i + cellW / 2f, y + 5f, 5.8f, C_DIM,
                    cjk, Paint.Align.CENTER, 0.6f);
        }
        long max = 1L;
        for (int i = 0; i < r.days.size(); i++) {
            max = Math.max(max, r.days.get(i).totalMs);
        }
        int maxIdx = -1;
        for (int i = 0; i < r.days.size(); i++) {
            if (r.days.get(i).totalMs == max && max > 1) {
                maxIdx = i;
                break;
            }
        }
        float gridY = y + weekdayH + 2f;
        for (int i = 0; i < totalDays; i++) {
            int pos = firstIndex + i;
            int row = pos / 7;
            int col = pos % 7;
            DayAgg d = i < r.days.size() ? r.days.get(i) : null;
            float cx = x + col * (cellW + gap);
            float cy = gridY + row * (cellH + gap);
            double v = d == null ? 0 : (double) d.totalMs / max;
            int alpha = d == null ? 5
                    : d.totalMs <= 0 ? 8
                    : (int) Math.min(150, 14 + 136 * Math.pow(v, 0.55));
            fill.setShader(null);
            fill.setColor(android.graphics.Color.argb(alpha, 56, 189, 248));
            chartRect.set(cx, cy, cx + cellW, cy + cellH);
            c.drawRoundRect(chartRect, 2.2f, 2.2f, fill);
            if (i == maxIdx && d != null && d.totalMs > 0) {
                chartStroke.setShader(null);
                chartStroke.setColor(0xcc38bdf8);
                chartStroke.setStrokeWidth(0.7f);
                c.drawRoundRect(chartRect, 2.2f, 2.2f, chartStroke);
            }
            text(c, String.valueOf(i + 1), cx + 4f, cy + 8.2f, 5.8f,
                    d != null && d.totalMs > 0 ? 0xffcfe0f0 : 0xff3a4654, din,
                    Paint.Align.LEFT, 0f);
            if (d != null && d.totalMs > 0 && cellW > 34f) {
                text(c, ReportMath.hhmm(d.totalMs), cx + cellW - 4f, cy + cellH - 3.2f, 5.2f,
                        0xffdbe8f4, din, Paint.Align.RIGHT, 0f);
            }
        }
        text(c, "周一起 · 空格 = 未到 / 无记录", x, y + h, 5.2f, C_DIM, cjk,
                Paint.Align.LEFT, 0f);
        if (maxIdx >= 0) {
            text(c, "峰值 " + ReportMath.hhmm(max) + " · "
                            + ReportMath.dateMd(r.days.get(maxIdx).dayStart),
                    x + w, y + h, 5.2f, C_DIM, cjk, Paint.Align.RIGHT, 0f);
        }
    }

    private void drawDonut(Canvas c, float x, float y, float w, float h) {
        float cx = x + 64f;
        float cy = y + h / 2f;
        donut(c, cx, cy, 40f, 9f);
        // 中心
        text(c, ReportMath.hhmm(r.totalMs), cx, cy - 1f, 13f, C_TEXT, din,
                Paint.Align.CENTER, 0.2f);
        text(c, "本期合计", cx, cy + 10f, 5.5f, C_DIM, cjk, Paint.Align.CENTER, 0.4f);
        // 图例
        long[] cats = r.catMs;
        int[] pct = ReportMath.percentages(cats);
        float lx = x + 118f;
        float ly = y + 40f;
        for (int i = 0; i < 5; i++) {
            float ry = ly + i * 17f;
            fill.setShader(null);
            fill.setColor(Categories.color(i));
            c.drawCircle(lx + 2f, ry - 2f, 1.5f, fill);
            String name = Categories.name(i);
            if (cats[i] <= 0) {
                text(c, name, lx + 7f, ry, 6.2f, C_DIM, cjk, Paint.Align.LEFT, 0f);
                continue;
            }
            text(c, name, lx + 7f, ry, 6.2f, C_SUB, cjk, Paint.Align.LEFT, 0f);
            text(c, ReportMath.hhmm(cats[i]), x + w - 28f, ry, 6.2f, C_TEXT, din,
                    Paint.Align.RIGHT, 0f);
            text(c, pct[i] + "%", x + w - 10f, ry, 5.8f, C_DIM, din,
                    Paint.Align.RIGHT, 0f);
        }
    }

    private void drawDaily(Canvas c, float x, float y, float w, float h) {
        if (r.days.isEmpty()) {
            return;
        }
        int totalDays = daysInMonth(r.startDay);
        long max = 60L;
        for (int i = 0; i < r.days.size(); i++) {
            max = Math.max(max, r.days.get(i).totalMs);
        }
        ChartAxis axis = ChartAxis.of(max, DAY_STEPS, 4f);
        float step = axis.step;
        float yMax = axis.yMax;
        float labelW = 18f;
        float plotX = x + labelW;
        float plotW = w - labelW;
        float plotTop = y + 2f;
        float plotH = h - 14f;
        float bottom = plotTop + plotH;
        float slot = plotW / totalDays;
        for (int i = 0; i < totalDays; i++) {
            long dayStart = r.startDay + i * UsageDay.DAY_MS;
            if (ReportMath.mondayIndex(dayStart) >= 5) {
                fill.setShader(null);
                fill.setColor(0x0d38bdf8);
                c.drawRect(plotX + slot * i, plotTop, plotX + slot * (i + 1), bottom, fill);
            }
        }
        chartStroke.setShader(null);
        chartStroke.setStrokeWidth(0.5f);
        chartText.setTypeface(din);
        chartText.setTextSize(5.6f);
        chartText.setTextAlign(Paint.Align.RIGHT);
        for (float v = 0; v <= yMax; v += step) {
            float gy = bottom - v / yMax * plotH;
            chartStroke.setColor(v == 0 ? 0x3869aacc : 0x1269aacc);
            c.drawLine(plotX, gy, x + w, gy, chartStroke);
            chartText.setColor(C_DIM);
            c.drawText(axisLabel(v), x + labelW - 4f, gy + 2f, chartText);
        }
        float bw = Math.min(8f, slot * 0.62f);
        for (int i = 0; i < totalDays; i++) {
            DayAgg d = i < r.days.size() ? r.days.get(i) : null;
            float bx = plotX + slot * i + (slot - bw) / 2f;
            if (d != null && d.totalMs > 0) {
                // 堆叠几何统一走 BarStack（已单元测试）
                BarStack.stack(bottom, plotH, yMax, d.catMs, stackTops, stackBots);
                for (int ci = 0; ci < 5; ci++) {
                    if (d.catMs[ci] <= 0) {
                        continue;
                    }
                    chartFill.setShader(new LinearGradient(0, stackTops[ci], 0, stackBots[ci],
                            Categories.color(ci),
                            (Categories.color(ci) & 0x00ffffff) | 0x55000000,
                            Shader.TileMode.CLAMP));
                    chartRect.set(bx, stackTops[ci] + 0.4f, bx + bw, stackBots[ci] - 0.4f);
                    c.drawRoundRect(chartRect, 1f, 1f, chartFill);
                    chartFill.setShader(null);
                }
            } else if (d == null) {
                chartStroke.setColor(0x1a69aacc);
                chartStroke.setStrokeWidth(0.5f);
                c.drawLine(bx, bottom, bx + bw, bottom, chartStroke);
            }
            if (i % 5 == 0 || i == totalDays - 1) {
                text(c, String.valueOf(i + 1), plotX + slot * i + slot / 2f, bottom + 7f,
                        5.6f, d == null ? 0xff3a4654 : C_DIM, din, Paint.Align.CENTER, 0f);
            }
        }
        if (r.totalMs > 0) {
            float ay = bottom - (r.avgMs() / 60000f) / yMax * plotH;
            chartStroke.setColor(0xaa38bdf8);
            chartStroke.setStrokeWidth(0.8f);
            chartStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{1.5f, 3f}, 0f));
            c.drawLine(plotX, ay, x + w, ay, chartStroke);
            chartStroke.setPathEffect(null);
        }
    }

    private void drawWeekdayAvg(Canvas c, float x, float y, float w, float h) {
        // 与 ReportMath.insight 的周末/工作日对比共用同一口径：没记录的那天不进分母
        long[] vals = ReportMath.weekdayAverageMs(r.days);
        long max = 60L;
        for (int i = 0; i < 7; i++) {
            max = Math.max(max, vals[i]);
        }
        float bottom = y + h - 8f;
        float plotH = h - 18f;
        float slot = w / 7f;
        float bw = Math.min(13f, slot * 0.52f);
        for (int i = 0; i < 7; i++) {
            float cx = x + slot * i + slot / 2f;
            float bh = plotH * vals[i] / max;
            if (vals[i] > 0) {
                fill.setShader(new LinearGradient(0, bottom - bh, 0, bottom,
                        0xff9be6ff, 0xaa2f9fd8, Shader.TileMode.CLAMP));
                chartRect.set(cx - bw / 2f, bottom - bh, cx + bw / 2f, bottom);
                c.drawRoundRect(chartRect, 1.5f, 1.5f, fill);
                fill.setShader(null);
                text(c, ReportMath.hhmm(vals[i]), cx, bottom - bh - 3f, 5.4f, 0xffcfe0f0,
                        din, Paint.Align.CENTER, 0f);
            } else {
                chartRect.set(cx - bw / 2f, bottom - 1.5f, cx + bw / 2f, bottom);
                fill.setShader(null);
                fill.setColor(0x2269aacc);
                c.drawRoundRect(chartRect, 1f, 1f, fill);
            }
            text(c, ReportMath.WEEK_CN[i], cx, bottom + 8f, 5.8f, C_SUB, cjk,
                    Paint.Align.CENTER, 0.4f);
        }
    }

    private static int daysInMonth(long dayStart) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        return c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
    }

}
