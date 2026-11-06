package com.dsh.tvhud.usage;

import com.dsh.tvhud.hud.HudService;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 详情页：今天的「人在看」时间轴 + 分段明细（起止时间、时长、类别）。
 *
 * 设计语言：一张安静的使用台账。
 * - 中性色打底（近黑蓝灰），颜色只出现在数据上：时间轴色块 / 分类圆点 / 行首色脊；
 * - 时间码用 DIN Pro（表格式排布），合计数字用屏保同款细体，中文用系统兰亭；
 * - 尺寸克制：正文 15dp、标签 10–12dp，靠字距和留白分区，而不是靠大字号。
 * 屏保角标不受影响。
 */
public class MainActivity extends Activity {
    /** 从首页菜单进来时指定打开哪一页：今日 / 使用记录。 */
    public static final String EXTRA_OPEN = "open";
    public static final String OPEN_TODAY = "today";
    public static final String OPEN_HISTORY = "history";

    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    /** 详情页刷新间隔：一分钟一次就够（秒级刷新会让列表不断重建、焦点被打断）。 */
    private static final long REFRESH_MS = 60000L;

    // 中性色（只有数据用彩色）
    private static final int COL_BG = 0xff0e1114;
    private static final int COL_PRIMARY = 0xffe9eef3;
    private static final int COL_SECOND = 0xff9aa7b2;
    private static final int COL_TERTIARY = 0xff5f6b76;
    private static final int COL_HAIRLINE = 0x288a97a4;
    private static final int COL_FOCUS = 0x18ffffff;

    private Typeface cjk = Typeface.DEFAULT;
    private Typeface num = Typeface.DEFAULT;   // 细体数字（与屏保同款）
    private Typeface din = Typeface.DEFAULT;   // 时间码 / 小标签

    private TextView metaLine;
    private TextView totalValue;
    private TextView totalUnit;
    private LinearLayout catRow;
    private LinearLayout listContainer;
    private TimelineView timeline;
    private TextView eyebrow;
    private ScrollView scrollView;
    private long shownDay = 0L;
    private String openMode = OPEN_TODAY;

    // 用哨兵值而不是 ""：空的一天 rows 签名本来就是 ""，否则首次进入空日期时列表不会渲染空状态
    private String shownSignature = "\u0001";
    private String shownCats = "\u0001";
    private final Handler handler = new Handler();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd EEE", Locale.CHINA);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent intent = getIntent();
        if (intent != null && OPEN_HISTORY.equals(intent.getStringExtra(EXTRA_OPEN))) {
            openMode = OPEN_HISTORY;
        }
        initFonts();
        buildUi();
        startService(new Intent(this, HudService.class));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                shiftDay(-1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                shiftDay(1);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** 左右键按天翻历史：只能翻到今天（含）之前、最多一年。 */
    private void shiftDay(int delta) {
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        long minDay = today - (HistoryStore.KEEP_DAYS - 1L) * UsageDay.DAY_MS;
        long target = shownDay + delta * UsageDay.DAY_MS;
        if (target > today) {
            target = today;
        }
        if (target < minDay) {
            target = minDay;
        }
        if (target == shownDay) {
            return;
        }
        shownDay = target;
        // 不要清空 shownSignature/shownCats：空的一天签名本来就是 ""，清空后会被当成"没变化"不刷新
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- 字体 / 尺寸 ----------

    private void initFonts() {
        try {
            cjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
        try {
            num = Typeface.createFromAsset(getAssets(), "MIUI_EX_Light.ttf");
        } catch (Throwable ignored) {
        }
        try {
            din = Typeface.createFromAsset(getAssets(), "DINPro_Medium.otf");
        } catch (Throwable ignored) {
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** 文字尺寸按 dp（忽略系统 fontScale），保证版式稳定、字不会过大。 */
    private float ts(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private TextView label(String s, float size, int color, Typeface tf, float tracking) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(ts(size));
        v.setTextColor(color);
        v.setTypeface(tf);
        v.setLetterSpacing(tracking);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    private View hairline() {
        View v = new View(this);
        v.setBackgroundColor(COL_HAIRLINE);
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // ---------- 界面 ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COL_BG);
        root.setPadding(dp(64), dp(36), dp(64), dp(22));

        // 眉标：今日使用 …………………………… 日期 / 开机时长
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        eyebrow = label("今日使用", 10f, COL_SECOND, cjk, 0.18f);
        row1.addView(eyebrow, lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row1.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        metaLine = label("", 10f, COL_TERTIARY, cjk, 0.04f);
        metaLine.setGravity(Gravity.END);
        row1.addView(metaLine);
        root.addView(row1);

        // 主角：今日合计；右侧是分类图例（颜色只做圆点）
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams row2Lp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dp(6);

        totalValue = label("00:00", 26f, COL_PRIMARY, num, 0.02f);
        row2.addView(totalValue);

        totalUnit = label("今日合计", 9f, COL_TERTIARY, cjk, 0.10f);
        LinearLayout.LayoutParams unitLp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        unitLp.leftMargin = dp(14);
        unitLp.bottomMargin = dp(6);
        row2.addView(totalUnit, unitLp);

        row2.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams catLp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        catLp.bottomMargin = dp(6);
        row2.addView(catRow, catLp);

        root.addView(row2, row2Lp);

        // 分隔 + 时间轴
        LinearLayout.LayoutParams hair1 = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        hair1.topMargin = dp(12);
        root.addView(hairline(), hair1);

        timeline = new TimelineView(this, din);
        timeline.setPadding(0, dp(5), 0, 0);
        LinearLayout.LayoutParams tlLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.topMargin = dp(8);
        tlLp.bottomMargin = dp(8);
        root.addView(timeline, tlLp);

        root.addView(hairline(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // 明细表头（列宽与行一致）
        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setPadding(dp(24), 0, dp(16), 0);
        listHeader.addView(label("时间", 9f, COL_TERTIARY, cjk, 0.14f),
                lp(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView titleHead = label("看了什么", 9f, COL_TERTIARY, cjk, 0.14f);
        listHeader.addView(titleHead, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView durHead = label("时长", 9f, COL_TERTIARY, cjk, 0.14f);
        durHead.setGravity(Gravity.END);
        listHeader.addView(durHead, lp(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView catHead = label("类别", 9f, COL_TERTIARY, cjk, 0.14f);
        catHead.setGravity(Gravity.END);
        listHeader.addView(catHead, lp(dp(118), LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams headLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headLp.topMargin = dp(8);
        headLp.bottomMargin = dp(2);
        root.addView(listHeader, headLp);

        // 明细列表（可上下滚动，行可获焦以支持遥控器）
        scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFocusable(false);   // 焦点交给行，滚动量才能按整行对齐
        scrollView.setClipToPadding(false);
        ScrollView scroll = scrollView;
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, dp(8));
        scroll.addView(listContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(4);
        root.addView(scroll, scrollLp);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                snapListHeight(scroll);
            }
        });

        setContentView(root);
    }

    /** 把列表视口高度吸附成「整数行」：滚动后边缘永远落在行分隔线上，不会切半行字。 */
    private void snapListHeight(ScrollView scroll) {
        if (!(scroll.getChildAt(0) instanceof ViewGroup)) {
            return;
        }
        ViewGroup content = (ViewGroup) scroll.getChildAt(0);
        if (content.getChildCount() < 3) {
            return;
        }
        // 行距按实际布局量：row0 顶 → row1 顶（中间夹着 1px 发丝线），避免 dp 取整误差
        int pitch = content.getChildAt(2).getTop() - content.getChildAt(0).getTop();
        int avail = scroll.getHeight();
        if (pitch <= 0 || avail < pitch * 2) {
            return;
        }
        int rows = avail / pitch;
        int target = rows * pitch;
        ViewGroup.LayoutParams params = scroll.getLayoutParams();
        if (params.height == target) {
            return;
        }
        params.height = target;
        if (params instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) params).weight = 0;
        }
        scroll.setLayoutParams(params);
    }

    // ---------- 刷新 ----------

    private void refresh() {
        long now = System.currentTimeMillis();
        long todayStart = UsageDay.dayStartOf(now);
        if (shownDay == 0L) {
            // 从菜单的「使用记录」进来时，落到最近有存档的一天；没有存档就停到今天
            shownDay = OPEN_HISTORY.equals(openMode) ? latestArchivedDay(todayStart) : todayStart;
        }
        if (shownDay > todayStart) {
            shownDay = todayStart;
        }
        boolean isToday = shownDay == todayStart;

        List<UsageDay.Seg> segs = new ArrayList<>();
        List<PlayEntry> plays;
        if (isToday) {
            UsageDay day = HudService.peekUsage();
            if (day == null || day.dayStart != todayStart) {
                day = UsageDay.load(this, todayStart);
            }
            if (day != null && day.dayStart == todayStart) {
                segs.addAll(day.segments());
            }
            plays = HudService.peekPlays();
        } else {
            HistoryStore.DayData archived = HistoryStore.read(this, shownDay);
            if (archived != null) {
                segs.addAll(archived.segs);
                plays = archived.plays;
            } else {
                plays = new ArrayList<>();
            }
        }

        long total = 0L;
        long[] cats = new long[5];
        for (int i = 0; i < segs.size(); i++) {
            UsageDay.Seg s = segs.get(i);
            long d = Math.max(0L, s.end - s.start);
            total += d;
            if (s.cat >= 0 && s.cat < cats.length) {
                cats[s.cat] += d;
            }
        }
        List<Row> rows = buildRows(segs, plays);
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            sig.append(r.start).append('-').append(r.end).append('-').append(r.cat)
                    .append('-').append(r.title).append('-').append(r.ci).append(';');
        }

        String sumLabel;
        if (isToday) {
            long uptime = SystemClock.elapsedRealtime() / 1000L;
            eyebrow.setText("今日使用");
            metaLine.setText(String.format(Locale.CHINA, "%s · 已开机 %d 天 %02d:%02d",
                    dateFmt.format(new Date(now)), uptime / 86400,
                    (uptime % 86400) / 3600, (uptime % 3600) / 60));
            sumLabel = "今日合计";
        } else {
            eyebrow.setText("使用记录");
            metaLine.setText("‹ " + dateFmt.format(new Date(shownDay)) + " › · 左右键切换日期");
            sumLabel = "当日合计";
        }
        totalValue.setText(fmtClock(total));
        totalUnit.setText(rows.isEmpty() ? sumLabel
                : String.format(Locale.CHINA, "%s · %d 段", sumLabel, rows.size()));

        StringBuilder catSig = new StringBuilder();
        for (int i = 0; i < cats.length; i++) {
            catSig.append(cats[i]).append(',');
        }
        if (!catSig.toString().equals(shownCats)) {
            shownCats = catSig.toString();
            rebuildCats(cats);
        }

        if (!sig.toString().equals(shownSignature)) {
            shownSignature = sig.toString();
            rebuildList(rows);
        }

        timeline.setData(segs, shownDay, isToday ? now : 0L);
    }

    /** 最近一个「有当天存档」的日期（不含今天）；都没有就落到昨天。 */
    private long latestArchivedDay(long todayStart) {
        for (int i = 1; i < HistoryStore.KEEP_DAYS; i++) {
            long d = todayStart - i * UsageDay.DAY_MS;
            if (HistoryStore.has(this, d)) {
                return d;
            }
        }
        return todayStart - UsageDay.DAY_MS;
    }

    /** 分类图例：小圆点 + 名称 + 时长；颜色只做圆点。 */
    private void rebuildCats(long[] cats) {
        catRow.removeAllViews();
        for (int i = 0; i < cats.length; i++) {
            if (cats[i] <= 0) {
                continue;
            }
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(this);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(Categories.color(i));
            dot.setBackground(oval);
            LinearLayout.LayoutParams dotLp = lp(dp(5), dp(5));
            dotLp.rightMargin = dp(7);
            chip.addView(dot, dotLp);

            chip.addView(label(Categories.name(i), 9f, COL_SECOND, cjk, 0.02f));
            TextView val = label(fmtClock(cats[i]), 9f, COL_PRIMARY, din, 0.02f);
            LinearLayout.LayoutParams valLp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            valLp.leftMargin = dp(6);
            chip.addView(val, valLp);

            LinearLayout.LayoutParams chipLp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.leftMargin = dp(22);
            catRow.addView(chip, chipLp);
        }
        if (catRow.getChildCount() == 0) {
            boolean today = shownDay == UsageDay.dayStartOf(System.currentTimeMillis());
            catRow.addView(label(today ? "今天还没有记录" : "这一天没有记录", 9f, COL_TERTIARY,
                    cjk, 0.06f));
        }
    }

    private static class Row {
        long start;
        long end;
        long dur;      // 实际前台时长之和（合并跨过空档时 < end-start）
        int cat;
        String title = "";
        int ci;
        int totalCi;
    }

    /**
     * 明细行：普通时段一段一行；视频时段按「每集开始时间」切开，让「看了什么」这一列有片名。
     */
    private List<Row> buildRows(List<UsageDay.Seg> segs, List<PlayEntry> plays) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < segs.size(); i++) {
            UsageDay.Seg s = segs.get(i);
            if (s.cat != Categories.VIDEO) {
                rows.add(plainRow(s.start, s.end, s.cat));
                continue;
            }
            ArrayList<PlayEntry> in = new ArrayList<>();
            for (int k = 0; k < plays.size(); k++) {
                PlayEntry p = plays.get(k);
                if (p.ts >= s.start && p.ts < s.end) {
                    in.add(p);
                }
            }
            if (in.isEmpty()) {
                rows.add(plainRow(s.start, s.end, s.cat));
                continue;
            }
            long cursor = s.start;
            for (int k = 0; k < in.size(); k++) {
                PlayEntry p = in.get(k);
                if (p.ts > cursor) {
                    rows.add(plainRow(cursor, p.ts, s.cat));
                }
                long end = (k + 1 < in.size()) ? in.get(k + 1).ts : s.end;
                Row r = plainRow(p.ts, end, s.cat);
                r.title = p.name;
                r.ci = p.ci;
                r.totalCi = p.totalCi;
                rows.add(r);
                cursor = end;
            }
            if (cursor < s.end) {
                rows.add(plainRow(cursor, s.end, s.cat));
            }
        }
        return mergeSameRows(rows);
    }

    /** 相邻两行的「同类空档」上限：≤5 分钟归成一行（再长就是另一段使用了）。 */
    private static final long MERGE_GAP_MS = 5 * 60 * 1000L;

    /**
     * 把相邻的同类行合并成一行：类别相同、片名/集数相同、中间空档 ≤ {@link #MERGE_GAP_MS}。
     * 只影响展示：时间列是首尾跨度，时长列是各段实际时长之和（空档不计）。
     */
    private static List<Row> mergeSameRows(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Row last = out.isEmpty() ? null : out.get(out.size() - 1);
            boolean same = last != null
                    && last.cat == r.cat
                    && last.title.equals(r.title)
                    && last.ci == r.ci
                    && last.totalCi == r.totalCi
                    && r.start - last.end <= MERGE_GAP_MS;
            if (same) {
                last.end = Math.max(last.end, r.end);
                last.dur += r.dur;
            } else {
                out.add(r);
            }
        }
        return out;
    }

    private static Row plainRow(long start, long end, int cat) {
        Row r = new Row();
        r.start = start;
        r.end = end;
        r.dur = Math.max(0L, end - start);
        r.cat = cat;
        return r;
    }

    private void rebuildList(List<Row> rows) {
        // 刷新会整表重建：记住当前焦点行和滚动位置，重建后恢复，避免一分钟一次「跳回顶部」
        long focusAt = Long.MIN_VALUE;
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            View child = listContainer.getChildAt(i);
            if (child.hasFocus() && child.getTag() instanceof Row) {
                focusAt = ((Row) child.getTag()).start;
                break;
            }
        }
        final int keepScroll = scrollView.getScrollY();
        final long focusStart = focusAt;

        listContainer.removeAllViews();
        if (rows.isEmpty()) {
            boolean today = shownDay == UsageDay.dayStartOf(System.currentTimeMillis());
            TextView empty = label(today ? "今天还没有记录到使用时间" : "这一天没有记录到使用时间",
                    11f, COL_SECOND, cjk, 0.04f);
            empty.setPadding(0, dp(18), 0, 0);
            listContainer.addView(empty);
            TextView sub = label(today ? "屏保、熄屏的时间不计入" : "当天没有检测到「人在看」的时段",
                    10f, COL_TERTIARY, cjk, 0.04f);
            sub.setPadding(0, dp(6), 0, 0);
            listContainer.addView(sub);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                LinearLayout.LayoutParams divLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1));
                divLp.leftMargin = dp(12);
                listContainer.addView(hairline(), divLp);
            }
            listContainer.addView(buildRow(rows.get(i)));
        }
        // 行变了，视口高度重新吸附到整数行（首次从菜单进有内容的日期时尤其需要）
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                snapListHeight(scrollView);
            }
        });
        // 恢复焦点/滚动位置：优先找回同一时刻所在行，找不到就落到最近的一行
        if (focusStart != Long.MIN_VALUE) {
            final View target = findRowAt(focusStart);
            listContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (target != null) {
                        target.requestFocus();
                    }
                    scrollView.scrollTo(0, keepScroll);
                }
            });
        }
    }

    /** 找覆盖 ts 的那一行；没有覆盖的就找起点最近的一行。 */
    private View findRowAt(long ts) {
        View nearest = null;
        long nearestGap = Long.MAX_VALUE;
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            View child = listContainer.getChildAt(i);
            if (!(child.getTag() instanceof Row)) {
                continue;
            }
            Row r = (Row) child.getTag();
            if (ts >= r.start && ts < r.end) {
                return child;
            }
            long gap = Math.min(Math.abs(r.start - ts), Math.abs(r.end - ts));
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest = child;
            }
        }
        return nearest;
    }

    private View buildRow(Row s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(16), 0);
        row.setFocusable(true);
        row.setBackgroundColor(0x00000000);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundColor(hasFocus ? COL_FOCUS : 0x00000000);
            }
        });
        row.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        row.setTag(s);

        // 行首色脊：类别颜色的唯一落点
        View spine = new View(this);
        GradientDrawable spineBg = new GradientDrawable();
        spineBg.setColor(Categories.color(s.cat));
        spineBg.setCornerRadius(dp(1.5f));
        spine.setBackground(spineBg);
        LinearLayout.LayoutParams spineLp = lp(dp(3), dp(14));
        spineLp.rightMargin = dp(10);
        spineLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(spine, spineLp);

        TextView range = label(timeFmt.format(new Date(s.start)) + " – "
                + timeFmt.format(new Date(s.end)), 10f, COL_PRIMARY, din, 0.02f);
        row.addView(range, lp(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = label("", 10f, COL_PRIMARY, cjk, 0.02f);
        if (s.title.isEmpty()) {
            if (s.cat == Categories.VIDEO) {
                title.setText("未记录片名");
                title.setTextColor(COL_TERTIARY);
            } else {
                title.setText("");
            }
        } else {
            SpannableStringBuilder sb = new SpannableStringBuilder(s.title);
            sb.setSpan(new ForegroundColorSpan(COL_PRIMARY), 0, sb.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (s.totalCi > 0) {
                int a = sb.length();
                sb.append(" 第").append(String.valueOf(s.ci)).append("集");
                sb.setSpan(new ForegroundColorSpan(COL_SECOND), a, sb.length(),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            title.setText(sb);
        }
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView dur = label(fmtClock(s.dur), 9f, COL_SECOND, din,
                0.02f);
        dur.setGravity(Gravity.END);
        row.addView(dur, lp(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView cat = label(Categories.name(s.cat), 10f, COL_SECOND, cjk, 0.04f);
        cat.setGravity(Gravity.END);
        row.addView(cat, lp(dp(118), LinearLayout.LayoutParams.WRAP_CONTENT));

        return row;
    }

    /** 纯数字时长：≥1h 用 H:MM:SS，否则 MM:SS。 */
    private static String fmtClock(long ms) {
        long sec = Math.max(0L, ms) / 1000L;
        if (sec >= 3600L) {
            return String.format(Locale.US, "%d:%02d:%02d", sec / 3600L, (sec % 3600L) / 60L,
                    sec % 60L);
        }
        return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L);
    }
}
