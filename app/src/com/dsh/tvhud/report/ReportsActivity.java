package com.dsh.tvhud.report;

import com.dsh.tvhud.hud.HudService;
import com.dsh.tvhud.usage.UsageDay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用报告：周报 / 月报 / 年报三套布局共用这个 Activity。
 * ←→ 翻周期，↑↓ 切粒度，OK 回到本期，返回退出。
 *
 * 线程模型：聚合在单线程 {@link ExecutorService} 上跑；回调 post 回主线程。
 * token 用来丢弃过期结果；后台任务开始前也会比对 token，避免长按方向键时
 * 把一串「已经没人要的 365 天扫描」全部串行跑完。
 */
public final class ReportsActivity extends Activity {
    private static final String TAG = "TVPILOT";
    private static final String PREF_PERIOD = "report_period";

    private SharedPreferences prefs;
    private FrameLayout root;
    private ReportCanvasView view;
    private ReportPeriod period = ReportPeriod.WEEK;
    private long anchorStart;
    /** 当前锚点是否就是「本期」；不依赖已经加载的 Report，加载中也不会判错。 */
    private boolean atCurrent = true;

    private final Handler handler = new Handler();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** 只在主线程递增；背景线程读取前先看是否已被新请求取代。 */
    private volatile int token = 0;
    /** 上一次请求发起时的「今天」；跨天回前台时用来判断要不要刷新。 */
    private long requestedToday;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("hud", 0);
        String saved = prefs.getString(PREF_PERIOD, ReportPeriod.WEEK.name());
        try {
            period = ReportPeriod.valueOf(saved);
        } catch (Throwable ignored) {
            period = ReportPeriod.WEEK;
        }
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        anchorStart = ReportPeriod.startOf(period, today);
        atCurrent = true;

        root = new FrameLayout(this);
        root.setBackgroundColor(0xff06090f);
        setContentView(root);
        rebuildView();
        startService(new Intent(this, HudService.class));
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 跨天回到前台时，报告里的「今天」已经过期
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        if (requestedToday != 0L && requestedToday != today) {
            if (atCurrent) {
                anchorStart = ReportPeriod.startOf(period, today);
            }
            load();
        }
    }

    @Override
    protected void onDestroy() {
        token++;
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // 遥控器长按会以 ~50ms 间隔重复派发 ACTION_DOWN；只认第一下，
            // 否则会排队几十次全量扫描，画面一直停在加载态。
            // 只吞我们自己处理的键，其它键的重复事件照常交给系统。
            if (event.getRepeatCount() > 0) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        return true;
                    default:
                        break;
                }
            }
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shiftPeriod(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shiftPeriod(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    switchPeriod(coarser());
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    switchPeriod(finer());
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (view != null && view.hasLoadError()) {
                        load();
                    } else {
                        backToCurrent();
                    }
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private ReportPeriod coarser() {
        switch (period) {
            case WEEK:
                return ReportPeriod.MONTH;
            case MONTH:
                return ReportPeriod.YEAR;
            default:
                return ReportPeriod.YEAR;
        }
    }

    private ReportPeriod finer() {
        switch (period) {
            case YEAR:
                return ReportPeriod.MONTH;
            case MONTH:
                return ReportPeriod.WEEK;
            default:
                return ReportPeriod.WEEK;
        }
    }

    private void shiftPeriod(int delta) {
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        long current = ReportPeriod.startOf(period, today);
        long earliest = ReportPeriod.earliestDay(today);
        long target = ReportPeriod.shift(period, anchorStart, delta);
        if (target > current) {
            target = current;
        }
        if (ReportPeriod.endOf(period, target) < earliest) {
            return;
        }
        if (target == anchorStart) {
            return;
        }
        anchorStart = target;
        atCurrent = target == current;
        load();
    }

    private void switchPeriod(ReportPeriod next) {
        if (next == period) {
            return;
        }
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        long current = ReportPeriod.startOf(next, today);
        long earliest = ReportPeriod.earliestDay(today);
        // 正在看「本期」时，切粒度落到今天所在的周期；回看旧周期时才沿用锚点。
        // 用 atCurrent 而不是 view.report()：聚合还没回来时也不会跳回本期。
        long base = atCurrent ? today : anchorStart;
        long s = ReportPeriod.startOf(next, base);
        if (ReportPeriod.endOf(next, s) < earliest) {
            // 锚点第一期已经出了 365 天窗口（如从去年的年报 ↓ 到月报）：退到窗口内最早的
            // 那一期，而不是直接跳到今天，否则看旧周期时按 ↓ 会莫名回到本期
            s = ReportPeriod.startOf(next, Math.max(base, earliest));
        }
        if (s > current) {
            s = current;
        }
        period = next;
        anchorStart = s;
        atCurrent = s == current;
        prefs.edit().putString(PREF_PERIOD, period.name()).apply();
        rebuildView();
        load();
    }

    private void backToCurrent() {
        if (atCurrent) {
            return;
        }
        long today = UsageDay.dayStartOf(System.currentTimeMillis());
        anchorStart = ReportPeriod.startOf(period, today);
        atCurrent = true;
        load();
    }

    private void rebuildView() {
        if (view != null) {
            root.removeAllViews();
        }
        switch (period) {
            case WEEK:
                view = new WeekReportView(this);
                break;
            case MONTH:
                view = new MonthReportView(this);
                break;
            default:
                view = new YearReportView(this);
                break;
        }
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void load() {
        final int my = ++token;
        final ReportPeriod requestPeriod = period;
        final long requestStart = anchorStart;
        requestedToday = UsageDay.dayStartOf(System.currentTimeMillis());
        if (view != null) {
            view.clearLoadError();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (my != token) {
                    return;   // 已经有更新的请求，跳过这次全量扫描
                }
                final Report report;
                try {
                    report = ReportEngine.load(getApplicationContext(), requestPeriod, requestStart);
                } catch (Throwable t) {
                    Log.w(TAG, "report load failed", t);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (my != token || isFinishing() || isDestroyed()) {
                                return;
                            }
                            if (view != null) {
                                view.setLoadError("报告加载失败，按 OK 重试");
                            }
                        }
                    });
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (my != token || isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (view != null) {
                            view.setReport(report);
                        }
                    }
                });
            }
        });
    }
}
