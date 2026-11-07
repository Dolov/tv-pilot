package com.dsh.tvhud.pet;

import com.dsh.tvhud.hud.HudService;
import com.dsh.tvhud.usage.Categories;
import com.dsh.tvhud.usage.HistoryStore;
import com.dsh.tvhud.usage.UsageDay;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 宠物周报（家长看）：最近 7 天的观看时长 + 宠物行为汇总 + 每天一行。
 *
 * 口径：
 * - 观看时长只累加 {@link Categories#VIDEO} 类的分段秒数，不读片名、不记录内容；
 * - 场次/按时/超时/答题来自 {@link PetJournal} 的日级摘要（服务在跨天时写一条）；
 * - 成长值/阶段/分支/变异/星星来自宠物存档；
 * - 只在本机统计，不上传、不评价孩子，文案保持中性。
 *
 * 风格：安静的深色底 + 大字号，和 report/ReportsActivity 一致；返回键回设置页。
 */
public final class PetReportActivity extends Activity {
    private static final String TAG = "TVPILOT";
    /** 汇总窗口天数：今天 + 前 6 天。 */
    private static final int DAYS = 7;

    private static final String[] WEEK_NAMES = {"日", "一", "二", "三", "四", "五", "六"};

    private LinearLayout content;
    private TextView backRow;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PetUi.BG);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padH = PetUi.dp(this, 72);
        content.setPadding(padH, PetUi.dp(this, 32), padH, PetUi.dp(this, 40));
        scroll.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        showLoading();
        loadAsync();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    // ---------- 加载 ----------

    private void showLoading() {
        content.removeAllViews();
        content.addView(title());
        content.addView(para("正在汇总…", 20f, PetUi.TEXT_DIM));
    }

    private void loadAsync() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Week week = null;
                try {
                    week = compute(app);
                } catch (Throwable t) {
                    Log.w(TAG, "pet report load failed", t);
                }
                final Week result = week;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || isFinishing()) {
                            return;
                        }
                        if (result == null) {
                            showError();
                        } else {
                            render(result);
                        }
                    }
                });
            }
        }, "pet-report").start();
    }

    private Week compute(Context ctx) {
        Week w = new Week();
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        w.today = today;
        for (int i = 0; i < DAYS; i++) {
            // 下标 0 = 最早的一天，最后一个是今天
            w.days[i] = today - (DAYS - 1L - i) * UsageDay.DAY_MS;
        }

        // 观看时长：今天可能还没落归档，优先读服务内存快照，其次 SharedPreferences，最后当天归档
        for (long day : w.days) {
            if (day == today) {
                w.videoMs += todayVideoMs(ctx, today);
            } else {
                w.videoMs += archivedVideoMs(HistoryStore.read(ctx, day));
            }
        }

        // 宠物行为：日级摘要，只有场次/答题/是否超时，没有任何内容信息
        String csv = PetStore.journal(ctx);
        PetJournal.Summary sum = PetJournal.summarize(csv, w.days[0]);
        w.sessions = sum.sessions;
        w.quizCorrect = sum.quizCorrect;
        w.quizTotal = sum.quizTotal;
        w.quizRate = sum.quizRate();
        w.compliantDays = sum.compliantDays;
        w.overtimeDays = sum.overtimeDays;

        Map<Long, PetJournal.Day> byDay = new HashMap<Long, PetJournal.Day>();
        List<PetJournal.Day> journal = PetJournal.parse(csv);
        for (int i = 0; i < journal.size(); i++) {
            PetJournal.Day d = journal.get(i);
            if (d != null) {
                byDay.put(d.dayStart, d);
            }
        }
        for (int i = 0; i < DAYS; i++) {
            w.journal[i] = byDay.get(w.days[i]);
        }

        PetState state = PetStore.loadState(ctx);
        w.exp = state.exp;
        w.level = state.level();
        w.stars = state.stars;
        w.mutationCount = state.mutationCount;
        w.stage = state.stageName();
        w.branch = state.branchName();
        return w;
    }

    private static long todayVideoMs(Context ctx, long today) {
        UsageDay live = HudService.peekUsageSnapshot();
        if (live != null && live.dayStart == today) {
            return sumVideoMs(live.segments());
        }
        UsageDay loaded = UsageDay.load(ctx, today);
        if (loaded != null && !loaded.segments().isEmpty()) {
            return sumVideoMs(loaded.segments());
        }
        return archivedVideoMs(HistoryStore.read(ctx, today));
    }

    private static long archivedVideoMs(HistoryStore.DayData data) {
        if (data == null) {
            return 0L;
        }
        // 旧颜色编码的分段 cat 含义可能不同，宁可不计也不画错
        if (data.scheme != UsageDay.SCHEME) {
            return 0L;
        }
        return sumVideoMs(data.segs);
    }

    private static long sumVideoMs(List<UsageDay.Seg> segs) {
        long ms = 0L;
        if (segs == null) {
            return ms;
        }
        for (int i = 0; i < segs.size(); i++) {
            UsageDay.Seg s = segs.get(i);
            if (s != null && s.cat == Categories.VIDEO && s.end > s.start) {
                ms += s.end - s.start;
            }
        }
        return ms;
    }

    // ---------- 渲染 ----------

    private void showError() {
        content.removeAllViews();
        content.addView(title());
        content.addView(para("周报暂时读不出来，按返回键退出后重试。", 20f, PetUi.TEXT_DIM));
        addBackButton();
    }

    private void render(Week w) {
        content.removeAllViews();

        content.addView(title());
        content.addView(para("最近 7 天 · " + dayLabel(w.days[0]) + " 至 " + dayLabel(w.today)
                + "（只在这台电视上统计）", 15f, PetUi.TEXT_DIM));

        addSection("汇总");
        String sentence;
        if (w.sessions > 0) {
            sentence = "这周自己按了 " + w.sessions + " 次休息。";
        } else {
            sentence = "这周还没有场次记录。";
        }
        TextView sentenceView = para(sentence, 21f, PetUi.TEXT);
        sentenceView.setPadding(PetUi.dp(this, 4), PetUi.dp(this, 4),
                PetUi.dp(this, 4), PetUi.dp(this, 10));
        content.addView(sentenceView);

        addDataRow("观看时长", duration(w.videoMs));
        addDataRow("场次（主动休息）", w.sessions + " 次");
        addDataRow("按时休息天数", w.compliantDays + " 天");
        addDataRow("超时天数", w.overtimeDays + " 天");
        addDataRow("答题正确率", w.quizRate < 0 ? "—" : w.quizRate + "%");
        addDataRow("成长值", w.exp + "（Lv " + w.level + "）");
        addDataRow("阶段", w.stage == null || w.stage.isEmpty() ? "—" : w.stage);
        addDataRow("分支", w.branch == null || w.branch.isEmpty() ? "—" : w.branch + "线");
        addDataRow("变异次数", w.mutationCount + " 次");
        addDataRow("星星", w.stars + " 颗");

        addSection("每天");
        for (int i = 0; i < DAYS; i++) {
            PetJournal.Day day = w.journal[i];
            String value;
            if (day == null) {
                value = "—";
            } else {
                value = day.sessions + " 场 · 答题 " + quizText(day.quizCorrect, day.quizTotal)
                        + " · " + (day.overtime ? "超时" : "按时");
            }
            addDataRow(dayLabel(w.days[i]), value);
        }

        TextView note = para("只统计时长和行为，不记录看了什么，也不上传到任何地方。",
                14f, PetUi.TEXT_FAINT);
        note.setPadding(0, PetUi.dp(this, 20), 0, PetUi.dp(this, 10));
        content.addView(note);

        addBackButton();
    }

    private TextView title() {
        TextView v = PetUi.text(this, "宠物周报", 34f, PetUi.TEXT, true);
        v.setGravity(Gravity.START);
        v.setPadding(0, 0, 0, PetUi.dp(this, 6));
        return v;
    }

    private TextView para(String text, float size, int color) {
        TextView v = PetUi.text(this, text, size, color, false);
        v.setSingleLine(false);
        v.setGravity(Gravity.START);
        return v;
    }

    private void addSection(String text) {
        TextView v = PetUi.text(this, text, 16f, PetUi.TEXT_DIM, true);
        v.setGravity(Gravity.START);
        v.setPadding(0, PetUi.dp(this, 22), 0, PetUi.dp(this, 6));
        content.addView(v);
    }

    private void addDataRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(PetUi.dp(this, 22), PetUi.dp(this, 12),
                PetUi.dp(this, 22), PetUi.dp(this, 12));
        row.setBackground(PetUi.round(this, PetUi.CARD, 12f));

        TextView name = PetUi.text(this, label, 21f, PetUi.TEXT_DIM, false);
        name.setGravity(Gravity.START);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = PetUi.text(this, value, 22f, PetUi.TEXT, true);
        val.setGravity(Gravity.END);
        row.addView(name);
        row.addView(val);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = PetUi.dp(this, 8);
        content.addView(row, lp);
    }

    private void addBackButton() {
        backRow = PetUi.cardButton(this, "返回设置（按返回键也可以）", 20f);
        backRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this, 64));
        lp.topMargin = PetUi.dp(this, 24);
        content.addView(backRow, lp);
        backRow.requestFocus();
    }

    // ---------- 文案 ----------

    private static String quizText(int correct, int total) {
        if (total <= 0) {
            return "—";
        }
        return correct + "/" + total;
    }

    private static String duration(long ms) {
        if (ms <= 0L) {
            return "0 分钟";
        }
        long minutes = Math.round(ms / 60000.0);
        if (minutes <= 0L) {
            return "不到 1 分钟";
        }
        if (minutes < 60L) {
            return minutes + " 分钟";
        }
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private static String dayLabel(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        return String.format(Locale.CHINA, "%02d-%02d 周%s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                WEEK_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1]);
    }

    /** 一次汇总结果；只在后台线程算，整块交给 UI 线程渲染，避免两边共享可变状态。 */
    private static final class Week {
        long today;
        final long[] days = new long[DAYS];
        final PetJournal.Day[] journal = new PetJournal.Day[DAYS];
        long videoMs;
        int sessions;
        int quizCorrect;
        int quizTotal;
        int quizRate = -1;
        int compliantDays;
        int overtimeDays;
        int exp;
        int level;
        int stars;
        int mutationCount;
        String stage = "";
        String branch = "";
    }
}
