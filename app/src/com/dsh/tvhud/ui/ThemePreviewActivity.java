package com.dsh.tvhud.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.tvhud.hud.HudService;
import com.dsh.tvhud.report.ReportPeriod;
import com.dsh.tvhud.usage.HistoryStore;
import com.dsh.tvhud.usage.UsageDay;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 主题配置：左边一张首页样张（菜单 + 本周周报条），右边 4 × 2 块主题漆片。
 *
 * 左边不是截图，是拿当前 look 的真实 token 现场画的：
 * - 首页四行菜单（换 look 就连焦点色脊、发丝线一起换）；
 * - 本周周报的一小块（总时长 / 七天节奏 / 分类构成），数据取本周真实归档 + 今天实时快照。
 * 右边每块漆片写名字 + 一句短描述，主色 / 辅色缩成两块小色卡、圆角跟材质走。
 *
 * 页面只保留左上角一行「主题配置」，没有键位说明和页脚：← → ↑ ↓ / OK / 返回 就是全部操作。
 */
public final class ThemePreviewActivity extends Activity {

    private static final float PAD_X_DP = 40f;
    private static final float PAD_TOP_DP = 16f;
    private static final float PAD_BOTTOM_DP = 18f;

    /** 左侧样张里的首页菜单（样板而已，不读真实数据）。 */
    private static final String[] MENU_IDX = {"01", "02", "03", "04"};
    private static final String[] MENU_NAMES = {"使用记录", "使用报告", "我的宠物", "家长设置"};
    private static final String[] MENU_DESCS = {
            "今日 · 历史 · 左右键切换日期",
            "周报 · 月报 · 年报，趋势与构成",
            "商店 · 换装 · 装饰 · 进化",
            "时间 · 时段 · 外观 · 周报与维护"};
    private static final String[] MENU_TAGS = {"›", "›", "›", "PIN"};
    /** 右栏漆片是 4 列 × 2 排。 */
    private static final int COLS = 4;

    /** 周一下起的七天标签，与报告页同一口径。 */
    private static final String[] DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private final Looks.Look[] looks = Looks.ALL;
    private int index;
    private Palette palette;

    private LinearLayout root;
    private TextView title;
    private PreviewView preview;
    private WeekData weekData;
    private LookCard[] cards;

    private Typeface cjk;
    private Typeface din;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.ensureFonts(this);
        cjk = Ui.cjk();
        din = Ui.din();

        // 进来时选中已保存的 look；没有存过时 ThemeStore 会顺手从旧 scheme/accent 迁移
        index = Looks.indexOf(ThemeStore.look(this));
        palette = looks[index].palette();
        weekData = loadWeek();

        buildUi();
        bindViews();
    }

    // ---------- 键位 ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 左右：同一排内循环，行首再往左 = 本排最后一个
                if (index % COLS == 0) {
                    index = rowStart() + COLS - 1;
                } else {
                    index = index - 1;
                }
                bindViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 行尾再往右 = 本排第一个
                if (index % COLS == COLS - 1) {
                    index = rowStart();
                } else {
                    index = index + 1;
                }
                bindViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                // 上下：换排循环，列不变
                index = (index + looks.length - COLS) % looks.length;
                bindViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                index = (index + COLS) % looks.length;
                bindViews();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                save();
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private int rowStart() {
        return (index / COLS) * COLS;
    }

    private void save() {
        Looks.Look look = looks[index];
        ThemeStore.setLook(this, look);
        Toast.makeText(this, "已保存 · " + look.name, Toast.LENGTH_SHORT).show();
    }

    // ---------- 小工具 ----------

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private GradientDrawable round(int color, float radiusDp) {
        return Ui.round(color, dp(radiusDp));
    }

    private GradientDrawable roundStroke(int color, float radiusDp, int stroke, float strokeDp) {
        return Ui.roundStroke(color, dp(radiusDp), stroke, Math.max(1, dp(strokeDp)));
    }

    /** 本页文字按 dp 设字号（Ui.label 走 px→sp 会放大一倍，小字会超框）。 */
    private TextView text(String s, float sizeDp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        v.setTextColor(color);
        v.setTypeface(cjk, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setSingleLine(true);
        return v;
    }

    /** 多行小字：描述类文字用，允许折两行。 */
    private TextView wrapText(String s, float sizeDp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        v.setTextColor(color);
        v.setTypeface(cjk, Typeface.NORMAL);
        v.setSingleLine(false);
        v.setMaxLines(3);
        v.setLineSpacing(dp(2), 1.0f);
        v.setLetterSpacing(0.01f);
        return v;
    }

    // ---------- 建界面 ----------

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.appInk);
        root.setPadding(dp(PAD_X_DP), dp(PAD_TOP_DP), dp(PAD_X_DP), dp(PAD_BOTTOM_DP));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        // 左上角只留四个字，字号压到 14dp：它是定位，不是标题
        title = text("主题配置", 15f, palette.appText, true);
        title.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(8);
        root.addView(title, titleLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 左：首页样张（菜单 + 本周周报条），竖排居中
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(dp(360),
                LinearLayout.LayoutParams.MATCH_PARENT);
        leftLp.rightMargin = dp(22);
        body.addView(left, leftLp);
        preview = new PreviewView();
        left.addView(preview, new LinearLayout.LayoutParams(
                dp(360), LinearLayout.LayoutParams.MATCH_PARENT));

        buildGrid(body);

        setContentView(root);
        root.requestFocus();
    }

    /** 右：4 × 2 漆片，每块用它自己的底色 / 文字 / 圆角画。 */
    private void buildGrid(LinearLayout body) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        body.addView(grid, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bottomLp.topMargin = dp(10);
        grid.addView(bottom, bottomLp);

        cards = new LookCard[looks.length];
        for (int i = 0; i < looks.length; i++) {
            LookCard card = new LookCard(looks[i]);
            cards[i] = card;
            LinearLayout row = i < looks.length / 2 ? top : bottom;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i % 4 > 0) {
                lp.leftMargin = dp(10);
            }
            row.addView(card, lp);
        }
    }

    // ---------- 跟当前 look 换色 ----------

    private void bindViews() {
        palette = looks[index].palette();
        root.setBackgroundColor(palette.appInk);
        title.setTextColor(palette.appText);
        preview.setPalette(palette);
        for (int i = 0; i < cards.length; i++) {
            cards[i].setActive(i == index);
        }
    }

    // ---------- 左侧首页样张 ----------

    /** 一张和右栏等高的首页缩略：品牌行 + 本周周报条 + 四行菜单（第一行焦点）。 */
    private final class PreviewView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;

        private Palette palette;

        PreviewView() {
            super(ThemePreviewActivity.this);
            density = getResources().getDisplayMetrics().density;
            stroke.setStyle(Paint.Style.STROKE);
            // 这块 Canvas 文字很密，个别电视 GPU 在硬件加速下会留字模残影，走软件绘制更稳
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setPalette(Palette p) {
            palette = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (palette == null || weekData == null) {
                return;
            }
            // 先把整块 view 刷一遍底色：软件层复用时不留上一帧的字模残影
            c.drawColor(palette.appInk);
            c.save();
            c.scale(density, density);
            float w = getWidth() / density;
            float h = getHeight() / density;

            // 一块「电视屏」：圆角 = 当前材质
            float radius = palette.panelRadius;
            rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
            fill.setColor(palette.appInk);
            c.drawRoundRect(rect, radius, radius, fill);
            stroke.setStrokeWidth(1f);
            stroke.setColor(palette.appRule);
            c.drawRoundRect(rect, radius, radius, stroke);

            float pad = 18f;
            float x0 = pad;
            float x1 = w - pad;

            // 品牌行
            label(c, "tv-pilot", x0, 28f, 10f, palette.appText2, din, 0.16f, Paint.Align.LEFT);
            label(c, dateLine(), x1, 27f, 9f, palette.appText3, cjk, 0.02f, Paint.Align.RIGHT);
            fill.setColor(palette.appRule);
            c.drawRect(x0, 36f, x1, 37f, fill);

            // 周报条：上下随页面高度伸缩，菜单始终贴底
            float weekTop = 46f;
            float weekBottom = Math.max(weekTop + 130f, h * 0.58f);
            drawWeekBlock(c, x0, x1, weekTop, weekBottom);
            fill.setColor(palette.appRule);
            c.drawRect(x0, weekBottom + 20f, x1, weekBottom + 21f, fill);

            drawMenu(c, x0, x1, weekBottom + 28f, h - 18f);
            c.restore();
        }

        /** 本周周报的一小块：总时长 / 日均 / 七天节奏 / 构成条。 */
        private void drawWeekBlock(Canvas c, float x0, float x1, float top, float bottom) {
            label(c, "本周 · " + weekData.range, x0, top + 12f, 9f, palette.appText3, cjk,
                    0.02f, Paint.Align.LEFT);
            if (weekData.totalMs <= 0) {
                label(c, "本周还没有记录", x0, top + 48f, 14f, palette.appText2, cjk,
                        0.02f, Paint.Align.LEFT);
                return;
            }
            label(c, formatTotal(weekData.totalMs), x0, top + 46f, 22f, palette.appText, din,
                    0.02f, Paint.Align.LEFT);
            long avg = weekData.elapsedDays <= 0 ? 0L
                    : weekData.totalMs / weekData.elapsedDays;
            label(c, "日均 " + formatTotal(avg) + " · 记录 " + weekData.recordedDays + " 天",
                    x1, top + 46f, 8.5f, palette.appText3, cjk, 0.01f, Paint.Align.RIGHT);

            // 七天节奏：柱高 = 当天 / 本周峰值；今天实心主色
            float barsTop = top + 62f;
            float barsBottom = bottom - 26f;
            float barH = Math.max(20f, barsBottom - barsTop);
            float barW = 20f;
            float gap = (x1 - x0 - barW * DAY_LABELS.length) / (DAY_LABELS.length - 1);
            long max = 0L;
            for (int i = 0; i < weekData.dayMs.length; i++) {
                if (weekData.dayMs[i] > max) {
                    max = weekData.dayMs[i];
                }
            }
            fill.setStyle(Paint.Style.FILL);
            for (int i = 0; i < DAY_LABELS.length; i++) {
                float bx = x0 + i * (barW + gap);
                fill.setColor(palette.reportTrack);
                c.drawRoundRect(bx, barsTop, bx + barW, barsBottom, 2.5f, 2.5f, fill);
                long ms = weekData.dayMs[i];
                boolean today = i == weekData.elapsedDays - 1;
                if (ms > 0 && max > 0) {
                    float bh = Math.max(3f, barH * (ms / (float) max));
                    fill.setColor(today ? palette.lamp : Contrast.alpha(palette.lamp, 0.72f));
                    c.drawRoundRect(bx, barsBottom - bh, bx + barW, barsBottom, 2.5f, 2.5f, fill);
                }
                label(c, DAY_LABELS[i], bx + barW / 2f, bottom - 10f, 8f,
                        today ? palette.appText : palette.appText3, cjk, 0.02f,
                        Paint.Align.CENTER);
            }

            // 分类构成：一条 8dp 的细带，颜色就是各主题的视频 / 音乐 / …
            rect.set(x0, bottom - 2f, x1, bottom + 6f);
            fill.setColor(palette.reportTrack);
            c.drawRoundRect(rect, 4f, 4f, fill);
            float cx = x0;
            float cw = x1 - x0;
            for (int i = 0; i < weekData.catMs.length; i++) {
                if (weekData.catMs[i] <= 0) {
                    continue;
                }
                float seg = cw * (weekData.catMs[i] / (float) weekData.totalMs);
                fill.setColor(palette.dataColor(i));
                c.drawRect(cx, bottom - 2f, cx + seg, bottom + 6f, fill);
                cx += seg;
            }
        }

        /** 首页四行菜单，第一行是焦点（底 + 色脊 + 亮字）；行高跟着剩余空间分。 */
        private void drawMenu(Canvas c, float x0, float x1, float top, float bottom) {
            float rowH = (bottom - top) / MENU_NAMES.length;
            for (int i = 0; i < MENU_NAMES.length; i++) {
                float ry = top + i * rowH;
                float cy = ry + rowH / 2f;
                boolean focused = i == 0;
                if (i > 0) {
                    fill.setColor(palette.appRule);
                    c.drawRect(x0, ry, x1, ry + 1f, fill);
                }
                if (focused) {
                    fill.setColor(palette.appRowHover);
                    c.drawRoundRect(x0, ry + 1f, x1, ry + rowH - 1f, 3f, 3f, fill);
                    fill.setColor(palette.appSpine);
                    c.drawRoundRect(x0 + 8f, cy - 6f, x0 + 10.5f, cy + 6f, 1f, 1f, fill);
                }
                label(c, MENU_IDX[i], x0 + 18f, cy + 3f, 8f,
                        focused ? palette.appText : palette.appText3, din, 0.05f,
                        Paint.Align.LEFT);
                label(c, MENU_NAMES[i], x0 + 40f, cy + 4f, 12f,
                        focused ? palette.appText : palette.appText2, cjk, 0.02f,
                        Paint.Align.LEFT);
                label(c, MENU_DESCS[i], x0 + 150f, cy + 3f, 8f,
                        focused ? palette.appText2 : palette.appText3, cjk, 0.01f,
                        Paint.Align.LEFT);
                label(c, MENU_TAGS[i], x1 - 6f, cy + 3f, 8f,
                        focused ? palette.appText : palette.appText3, cjk, 0.05f,
                        Paint.Align.RIGHT);
            }
        }

        private String dateLine() {
            Calendar cal = Calendar.getInstance();
            return String.format(Locale.CHINA, "%04d-%02d-%02d %s",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH), weekday(cal));
        }

        private String weekday(Calendar cal) {
            String[] names = {"日", "一", "二", "三", "四", "五", "六"};
            return "周" + names[cal.get(Calendar.DAY_OF_WEEK) - 1];
        }

        private void label(Canvas c, String s, float x, float baseline, float size, int color,
                           Typeface face, float trackingEm, Paint.Align align) {
            text.setTypeface(face);
            text.setTextSize(size);
            text.setColor(color);
            text.setTextAlign(align);
            text.setLetterSpacing(trackingEm);
            text.setShadowLayer(0f, 0f, 0f, 0);
            c.drawText(s, x, baseline, text);
        }
    }

    // ---------- 本周数据 ----------

    /** 当前周（周一起）的逐日时长与分类构成；读归档 + 今天的实时快照。 */
    private static final class WeekData {
        long weekStart;
        final long[] dayMs = new long[7];
        final long[] catMs = new long[5];
        long totalMs;
        int elapsedDays;
        int recordedDays;
        String range = "";
    }

    private WeekData loadWeek() {
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        long weekStart = ReportPeriod.startOf(ReportPeriod.WEEK, today);
        WeekData w = new WeekData();
        w.weekStart = weekStart;
        w.elapsedDays = (int) ((today - weekStart) / UsageDay.DAY_MS) + 1;
        w.range = dateRange(weekStart, weekStart + 6L * UsageDay.DAY_MS);
        for (int i = 0; i < 7; i++) {
            long day = weekStart + i * UsageDay.DAY_MS;
            if (day > today) {
                break;
            }
            List<UsageDay.Seg> segs = null;
            if (day == today) {
                UsageDay live = HudService.peekUsageSnapshot();
                if (live != null && live.dayStart == today) {
                    segs = live.segments();
                }
            }
            if (segs == null) {
                UsageDay loaded = UsageDay.load(this, day);
                if (loaded != null && loaded.dayStart == day && !loaded.segments().isEmpty()) {
                    segs = loaded.segments();
                }
            }
            if (segs == null) {
                HistoryStore.DayData archived = HistoryStore.read(this, day);
                if (archived != null) {
                    segs = archived.segs;
                }
            }
            if (segs == null) {
                continue;
            }
            for (int k = 0; k < segs.size(); k++) {
                UsageDay.Seg seg = segs.get(k);
                long from = Math.max(seg.start, day);
                long to = Math.min(seg.end, day + UsageDay.DAY_MS);
                if (to <= from) {
                    continue;
                }
                long dur = to - from;
                w.dayMs[i] += dur;
                if (seg.cat >= 0 && seg.cat < w.catMs.length) {
                    w.catMs[seg.cat] += dur;
                }
            }
            w.totalMs += w.dayMs[i];
            if (w.dayMs[i] > 0) {
                w.recordedDays++;
            }
        }
        return w;
    }

    /** 周报同款时长写法：3h 18m / 28m / 0m。 */
    private static String formatTotal(long ms) {
        long minutes = Math.max(0L, ms) / 60000L;
        long h = minutes / 60L;
        long m = minutes % 60L;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private static String dateRange(long from, long to) {
        return date(from) + " – " + date(to);
    }

    private static String date(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.CHINA, "%02d/%02d",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    // ---------- 主题漆片 ----------

    /** 4 × 2 里的一块：左上角名字 → 两块小色卡（圆角跟材质走）→ 可换行的描述。 */
    private final class LookCard extends LinearLayout {
        private final Looks.Look look;
        private final Palette own;
        private final TextView name;
        private final TextView desc;
        private final View selectedBar;

        LookCard(Looks.Look look) {
            super(ThemePreviewActivity.this);
            this.look = look;
            own = look.palette();
            setOrientation(LinearLayout.VERTICAL);
            setPadding(dp(14), dp(12), dp(14), dp(10));

            name = text(look.name, 14f, own.appText, true);
            name.setLetterSpacing(0.04f);
            addView(name);

            // 两块小色卡：28dp，圆角直接用材质的圆角（封顶 14dp）
            LinearLayout swatches = new LinearLayout(ThemePreviewActivity.this);
            swatches.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            swLp.topMargin = dp(9);
            addView(swatches, swLp);
            float r = Math.min(14f, Math.max(0f, own.panelRadius));
            View lampSwatch = new View(ThemePreviewActivity.this);
            lampSwatch.setBackground(round(own.lamp, r));
            swatches.addView(lampSwatch, new LinearLayout.LayoutParams(dp(28), dp(28)));
            View secondarySwatch = new View(ThemePreviewActivity.this);
            secondarySwatch.setBackground(round(own.secondary, r));
            LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            secLp.leftMargin = dp(7);
            swatches.addView(secondarySwatch, secLp);

            desc = wrapText(look.desc, 11f, own.appText3);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = dp(9);
            addView(desc, descLp);

            addView(new View(ThemePreviewActivity.this),
                    new LinearLayout.LayoutParams(0, 0, 1f));

            selectedBar = new View(ThemePreviewActivity.this);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(3));
            barLp.topMargin = dp(6);
            addView(selectedBar, barLp);
            selectedBar.setVisibility(View.INVISIBLE);
        }

        void setActive(boolean value) {
            float r = Math.max(0f, own.panelRadius);
            setBackground(value
                    ? roundStroke(own.appInk, r, own.lamp, 2f)
                    : roundStroke(own.appInk, r, own.appRule, 1f));
            name.setTextColor(value ? own.appText : own.appText2);
            desc.setTextColor(own.appText3);
            selectedBar.setBackground(round(own.lamp, 1.5f));
            selectedBar.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
