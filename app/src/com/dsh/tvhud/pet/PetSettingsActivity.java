package com.dsh.tvhud.pet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 家长设置：4 位 PIN（默认 2580）+ 时间规则 / 宠物行为 / 周报入口 / 重置。
 *
 * 入口有两个：宠物房间长按返回 5 秒、到点拦截页长按返回 5 秒（入口不在本页改）。
 * 遥控器：↑↓ 移焦点；←→ 或 OK 改值；返回键退出。
 *
 * 每次改完立即写 PetStore（家长配置）并 loadBudget + applyConfig + saveBudget
 * 刷新当天内存预算，HudService 靠 {@link PetStore#version()} 自动重载，不用重启服务。
 */
public final class PetSettingsActivity extends Activity {
    private static final int PIN_LEN = 4;
    /** 连续输错几次后锁定。 */
    private static final int MAX_PIN_TRIES = 5;
    /** 锁定时长：60 秒。 */
    private static final long PIN_LOCK_MS = 60000L;
    /** PIN 错误计数/锁定截止时间放 pet 存档，退出重进不会把锁定绕掉。 */
    private static final String K_WRONG = "cfg_pin_wrong";
    private static final String K_LOCK_UNTIL = "cfg_pin_lock_until";

    // 设置行 id
    private static final int R_SESSION_MIN = 1;
    private static final int R_MAX_SESSIONS = 2;
    private static final int R_QUIZ = 3;
    private static final int R_EXTENSIONS = 4;
    private static final int R_WEEKEND = 5;
    private static final int R_WINDOW_ON = 6;
    private static final int R_WINDOW_START = 7;
    private static final int R_WINDOW_END = 8;
    private static final int R_ADD_SESSION = 9;
    private static final int R_DEGRADE = 10;
    private static final int R_GENTLE = 11;
    private static final int R_PARENT = 12;
    private static final int R_REPORT = 13;
    private static final int R_RESET = 14;
    private static final int R_BACK = 15;

    private final int[] digits = new int[PIN_LEN];
    private TextView[] boxes;
    private TextView pinHint;
    private int wrongCount;
    private long lockUntil;
    private boolean locked;
    private final Handler handler = new Handler();
    private Runnable lockTick;

    private Row sessionMinRow;
    private Row maxSessionsRow;
    private Row quizRow;
    private Row extensionsRow;
    private Row weekendRow;
    private Row windowOnRow;
    private Row windowStartRow;
    private Row windowEndRow;
    private Row addSessionRow;
    private Row degradeRow;
    private Row gentleRow;
    private Row parentRow;
    private Row reportRow;
    private Row resetRow;
    private Row backRow;

    private long resetArmedAt;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        SharedPreferences sp = getSharedPreferences(PetStore.PREFS, 0);
        wrongCount = sp.getInt(K_WRONG, 0);
        lockUntil = sp.getLong(K_LOCK_UNTIL, 0L);
        if (lockUntil <= System.currentTimeMillis()) {
            lockUntil = 0L;
            wrongCount = 0;
            persistPinState();
        }
        locked = lockUntil > System.currentTimeMillis();
        showPin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locked && System.currentTimeMillis() >= lockUntil) {
            unlockPin();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- PIN ----------

    private void showPin() {
        handler.removeCallbacksAndMessages(null);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(PetUi.BG);
        int pad = dp(40);
        root.setPadding(pad, pad, pad, pad);

        root.addView(PetUi.text(this, "家长设置", 34f, PetUi.TEXT, true));
        TextView sub = PetUi.text(this, "输入 4 位密码", 16f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(10);
        root.addView(sub, subLp);

        LinearLayout pinRow = new LinearLayout(this);
        pinRow.setOrientation(LinearLayout.HORIZONTAL);
        pinRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pinLp.topMargin = dp(28);
        root.addView(pinRow, pinLp);

        boxes = new TextView[PIN_LEN];
        for (int i = 0; i < PIN_LEN; i++) {
            final DigitBox box = new DigitBox(i);
            digits[i] = 0;
            box.setText("0");
            box.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40f);
            box.setTextColor(PetUi.TEXT);
            box.setTypeface(PetUi.cjk(this), Typeface.BOLD);
            box.setGravity(Gravity.CENTER);
            box.setFocusable(true);
            box.setFocusableInTouchMode(false);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.setBackground(PetUi.round(this, PetUi.CARD, 12f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(84));
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            box.setLayoutParams(lp);
            box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackground(PetUi.round(PetSettingsActivity.this,
                            hasFocus ? PetUi.FOCUS : PetUi.CARD, 12f));
                    ((TextView) v).setTextColor(hasFocus ? PetUi.TEXT_DARK : PetUi.TEXT);
                }
            });
            boxes[i] = box;
            pinRow.addView(box);
        }

        TextView ok = PetUi.cardButton(this, "确定", 22f);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPin();
            }
        });
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(200), dp(60));
        okLp.topMargin = dp(28);
        root.addView(ok, okLp);

        pinHint = PetUi.text(this, "", 15f, PetUi.TEXT_DIM, false);
        pinHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(24);
        root.addView(pinHint, hintLp);

        setContentView(root);
        if (locked) {
            updatePinHint();
            startLockCountdown();
        } else {
            updatePinHint();
            boxes[0].requestFocus();
        }
    }

    private final class DigitBox extends TextView {
        private final int index;

        DigitBox(int index) {
            super(PetSettingsActivity.this);
            this.index = index;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (locked) {
                if (isPinKey(keyCode)) {
                    updatePinHint();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int d = digits[index];
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    d = (d + 1) % 10;
                } else {
                    d = (d + 9) % 10;
                }
                digits[index] = d;
                setText(String.valueOf(d));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (index < PIN_LEN - 1) {
                    boxes[index + 1].requestFocus();
                } else {
                    checkPin();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }

    private static boolean isPinKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    /** 会改值的键：长按重复事件要吞掉，避免一次按键连跳好几档。 */
    private static boolean isValueKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    private void checkPin() {
        if (locked) {
            updatePinHint();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int d : digits) {
            sb.append(d);
        }
        if (sb.toString().equals(PetStore.pin(this))) {
            wrongCount = 0;
            lockUntil = 0L;
            persistPinState();
            showSettings();
            return;
        }
        wrongCount++;
        clearDigits();
        if (wrongCount >= MAX_PIN_TRIES) {
            wrongCount = 0;
            lockUntil = System.currentTimeMillis() + PIN_LOCK_MS;
            locked = true;
            persistPinState();
            updatePinHint();
            startLockCountdown();
            Toast.makeText(this, "密码错误次数过多，锁定 60 秒", Toast.LENGTH_SHORT).show();
            return;
        }
        persistPinState();
        updatePinHint();
        Toast.makeText(this, "密码不对，还可以试 " + (MAX_PIN_TRIES - wrongCount) + " 次",
                Toast.LENGTH_SHORT).show();
        boxes[0].requestFocus();
    }

    /** 锁定结束后解锁：清空错误计数和截止时间，焦点回到第一格。 */
    private void unlockPin() {
        locked = false;
        wrongCount = 0;
        lockUntil = 0L;
        persistPinState();
        updatePinHint();
        if (boxes != null && boxes[0] != null) {
            boxes[0].requestFocus();
        }
    }

    private void startLockCountdown() {
        if (lockTick != null) {
            handler.removeCallbacks(lockTick);
        }
        lockTick = new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                if (System.currentTimeMillis() >= lockUntil) {
                    unlockPin();
                } else {
                    updatePinHint();
                    handler.postDelayed(this, 500L);
                }
            }
        };
        handler.postDelayed(lockTick, 500L);
    }

    private void updatePinHint() {
        if (pinHint == null) {
            return;
        }
        boolean nowLocked = System.currentTimeMillis() < lockUntil;
        locked = nowLocked;
        if (nowLocked) {
            long sec = Math.max(1L, (lockUntil - System.currentTimeMillis() + 999L) / 1000L);
            pinHint.setText("尝试次数过多，" + sec + " 秒后再试");
            pinHint.setTextColor(PetUi.RED);
        } else {
            pinHint.setText("连续输错 " + MAX_PIN_TRIES + " 次会锁定 60 秒");
            pinHint.setTextColor(PetUi.TEXT_DIM);
        }
        if (boxes != null) {
            float alpha = nowLocked ? 0.35f : 1f;
            for (TextView b : boxes) {
                b.setAlpha(alpha);
            }
        }
    }

    private void persistPinState() {
        getSharedPreferences(PetStore.PREFS, 0).edit()
                .putInt(K_WRONG, wrongCount)
                .putLong(K_LOCK_UNTIL, lockUntil)
                .apply();
    }

    private void clearDigits() {
        for (TextView b : boxes) {
            b.setText("0");
        }
        for (int i = 0; i < digits.length; i++) {
            digits[i] = 0;
        }
    }

    // ---------- 设置页 ----------

    private void showSettings() {
        handler.removeCallbacksAndMessages(null);
        locked = false;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PetUi.BG);
        scroll.setFillViewport(true);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(56);
        list.setPadding(padH, dp(30), padH, dp(40));
        scroll.addView(list, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        list.addView(PetUi.text(this, "宠物设置", 32f, PetUi.TEXT, true));
        TextView keyHint = PetUi.text(this, "↑↓ 选择　←→ 或 OK 修改　返回键退出",
                15f, PetUi.TEXT_DIM, false);
        keyHint.setPadding(0, dp(8), 0, dp(6));
        list.addView(keyHint);

        addSection(list, "时间规则");
        sessionMinRow = addRow(list, R_SESSION_MIN, "每场时长");
        maxSessionsRow = addRow(list, R_MAX_SESSIONS, "每天场次");
        quizRow = addRow(list, R_QUIZ, "答题续时");
        extensionsRow = addRow(list, R_EXTENSIONS, "每天续时次数");
        weekendRow = addRow(list, R_WEEKEND, "周末多 1 场");
        windowOnRow = addRow(list, R_WINDOW_ON, "允许时段");
        addHint(list, "只拦新开的一场，不掐断正在看的；跨零点自动支持，开始=结束表示全天。");
        windowStartRow = addRow(list, R_WINDOW_START, "允许时段开始");
        windowEndRow = addRow(list, R_WINDOW_END, "允许时段结束");
        addSessionRow = addRow(list, R_ADD_SESSION, "临时加一场");
        addHint(list, "+1 场，只在今天有效。");

        addSection(list, "宠物行为");
        degradeRow = addRow(list, R_DEGRADE, "退化机制");
        addHint(list, "关掉后连续不达标只影响当天成长值，不退回外观。");
        gentleRow = addRow(list, R_GENTLE, "温和模式");
        addHint(list, "只提醒一次，不反复拉回。");
        parentRow = addRow(list, R_PARENT, "家长时间（不计数）");
        addHint(list, "家长自己看时不计数、不拦截。");

        addSection(list, "周报");
        reportRow = addRow(list, R_REPORT, "宠物周报");
        addHint(list, "只看时长和行为，不记录内容、不上传。");

        addSection(list, "其它");
        resetRow = addRow(list, R_RESET, "重置宠物");
        addHint(list, "按两次确认；设置和密码都会保留。");
        backRow = addRow(list, R_BACK, "返回");

        refresh();
        sessionMinRow.requestFocus();
    }

    private void addSection(LinearLayout parent, String text) {
        TextView v = PetUi.text(this, text, 16f, PetUi.TEXT_DIM, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(2);
        parent.addView(v, lp);
    }

    private void addHint(LinearLayout parent, String text) {
        TextView v = PetUi.text(this, text, 14f, PetUi.TEXT_FAINT, false);
        v.setPadding(dp(24), dp(2), dp(12), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        parent.addView(v, lp);
    }

    private Row addRow(LinearLayout parent, int id, String name) {
        Row row = new Row(id, name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(row, lp);
        return row;
    }

    /**
     * 一行设置：整行可聚焦，焦点态用暖黄底 + 深色字（3 米外一眼看到）。
     * ←→ 改值，OK 对开关等于切换、对数字等于 +1、对动作行等于执行。
     */
    private final class Row extends LinearLayout {
        private final int id;
        private final TextView nameView;
        private final TextView valueView;

        Row(int id, String name) {
            super(PetSettingsActivity.this);
            this.id = id;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setFocusableInTouchMode(false);
            setPadding(dp(24), dp(14), dp(24), dp(14));
            setBackground(PetUi.round(PetSettingsActivity.this, PetUi.CARD, 12f));

            nameView = PetUi.text(PetSettingsActivity.this, name, 22f, PetUi.TEXT, true);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            valueView = PetUi.text(PetSettingsActivity.this, "", 22f, PetUi.TEXT_DIM, true);
            valueView.setGravity(Gravity.END);
            addView(nameView);
            addView(valueView);

            setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    applyFocus(hasFocus);
                }
            });
        }

        void setValue(String text) {
            valueView.setText(text);
        }

        private void applyFocus(boolean focus) {
            setBackground(focus
                    ? PetUi.roundStroke(PetSettingsActivity.this, PetUi.FOCUS, 0xffffffff, 12f, 3f)
                    : PetUi.round(PetSettingsActivity.this, PetUi.CARD, 12f));
            nameView.setTextColor(focus ? PetUi.TEXT_DARK : PetUi.TEXT);
            valueView.setTextColor(focus ? 0xff4a3c00 : PetUi.TEXT_DIM);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            // 遥控器长按会重复派发 ACTION_DOWN；改值只认第一下，否则会连跳很多档
            // （↑↓ 不吞，保留按住连续移焦点的原生手感）
            if (event.getRepeatCount() > 0 && isValueKey(keyCode)) {
                return true;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    onRowKey(id, -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    onRowKey(id, 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    onRowKey(id, 0);
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
    }

    /** dir：-1 变小 / 1 变大 / 0 OK。 */
    private void onRowKey(int id, int dir) {
        switch (id) {
            case R_SESSION_MIN: {
                int[] values = {10, 15, 20};
                int cur = PetStore.sessionMinutes(this);
                int index = cur == 10 ? 0 : (cur == 20 ? 2 : 1);
                int step = dir < 0 ? -1 : 1;
                PetStore.setSessionMinutes(this, values[(index + step + values.length)
                        % values.length]);
                break;
            }
            case R_MAX_SESSIONS: {
                int next = PetStore.maxSessions(this) + (dir < 0 ? -1 : 1);
                if (next < 1) {
                    next = 5;
                } else if (next > 5) {
                    next = 1;
                }
                PetStore.setMaxSessions(this, next);
                break;
            }
            case R_QUIZ:
                PetStore.setQuizEnabled(this, !PetStore.quizEnabled(this));
                break;
            case R_EXTENSIONS: {
                int next = PetStore.extensionsPerDay(this) + (dir < 0 ? -1 : 1);
                if (next < 0) {
                    next = 3;
                } else if (next > 3) {
                    next = 0;
                }
                PetStore.setExtensionsPerDay(this, next);
                break;
            }
            case R_WEEKEND:
                PetStore.setWeekendBonus(this, !PetStore.weekendBonus(this));
                break;
            case R_WINDOW_ON:
                PetStore.setWindowEnabled(this, !PetStore.windowEnabled(this));
                break;
            case R_WINDOW_START:
            case R_WINDOW_END: {
                // 改时间时顺手把时段打开，少按一次
                if (!PetStore.windowEnabled(this)) {
                    PetStore.setWindowEnabled(this, true);
                }
                int step = dir < 0 ? -30 : 30;
                int start = PetStore.windowStart(this);
                int end = PetStore.windowEnd(this);
                if (id == R_WINDOW_START) {
                    start = wrapMinute(start + step);
                } else {
                    end = wrapMinute(end + step);
                }
                PetStore.setWindow(this, start, end);
                break;
            }
            case R_ADD_SESSION:
                if (dir != 0) {
                    return;
                }
                addSessionNow();
                refresh();
                return;
            case R_REPORT:
                if (dir != 0) {
                    return;
                }
                openReport();
                return;
            case R_RESET:
                if (dir != 0) {
                    return;
                }
                armReset();
                return;
            case R_BACK:
                if (dir != 0) {
                    return;
                }
                finish();
                return;
            default:
                return;
        }
        persistAndRefreshBudget();
        refresh();
    }

    private static int wrapMinute(int minute) {
        int v = minute % 1440;
        return v < 0 ? v + 1440 : v;
    }

    /** 家长临时加一场：直接调 Budget.addSession，当天有效。 */
    private void addSessionNow() {
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart <= 0L) {
            b.dayStart = PetStore.todayStart();
        }
        PetStore.applyConfig(this, b, b.dayStart);
        b.addSession();
        PetStore.saveBudget(this, b);
        Toast.makeText(this, "今天多一场，共 " + b.maxSessions + " 场", Toast.LENGTH_SHORT).show();
    }

    private void armReset() {
        long now = System.currentTimeMillis();
        if (now - resetArmedAt < 3000L) {
            resetArmedAt = 0L;
            PetStore.reset(this);   // 只重置宠物状态和当天预算，配置与 PIN 不动
            persistAndRefreshBudget();
            refresh();
            Toast.makeText(this, "宠物已重置，设置和密码保留", Toast.LENGTH_SHORT).show();
        } else {
            resetArmedAt = now;
            Toast.makeText(this, "再按一次确认重置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openReport() {
        try {
            startActivity(new Intent(this, PetReportActivity.class));
        } catch (Throwable t) {
            // Manifest 还没注册时不要带崩设置页
            Toast.makeText(this, "周报暂时打不开", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 改完配置刷新当天内存预算：loadBudget 会读回配置，applyConfig 重算
     * maxSessions（周末 + 临时加场）并保证不低于已用场次，saveBudget 落盘并 +version，
     * HudService 下一 tick 自动重载。
     */
    private void persistAndRefreshBudget() {
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart <= 0L) {
            b.dayStart = PetStore.todayStart();
        }
        PetStore.applyConfig(this, b, b.dayStart);
        PetStore.saveBudget(this, b);
    }

    // ---------- 刷新值 ----------

    private void refresh() {
        if (sessionMinRow == null) {
            return;
        }
        sessionMinRow.setValue(PetStore.sessionMinutes(this) + " 分钟");
        maxSessionsRow.setValue(PetStore.maxSessions(this) + " 场");
        quizRow.setValue(onOff(PetStore.quizEnabled(this)));
        extensionsRow.setValue(PetStore.extensionsPerDay(this) + " 次");
        weekendRow.setValue(onOff(PetStore.weekendBonus(this)));
        windowOnRow.setValue(onOff(PetStore.windowEnabled(this)));
        windowStartRow.setValue(Budget.formatMinute(PetStore.windowStart(this)));
        windowEndRow.setValue(Budget.formatMinute(PetStore.windowEnd(this)));
        int extra = PetStore.loadBudget(this).extraSessions;
        addSessionRow.setValue(extra > 0 ? "今天 +" + extra + " 场" : "今天 +0 场");
        degradeRow.setValue(onOff(PetStore.degradeEnabled(this)));
        gentleRow.setValue(onOff(PetStore.gentleMode(this)));
        parentRow.setValue(PetStore.parentMode(this) ? "开" : "关");
        reportRow.setValue("查看 ›");
        resetRow.setValue("按两次确认");
        backRow.setValue("");
    }

    private static String onOff(boolean on) {
        return on ? "开" : "关";
    }

    private int dp(float v) {
        return PetUi.dp(this, v);
    }
}
