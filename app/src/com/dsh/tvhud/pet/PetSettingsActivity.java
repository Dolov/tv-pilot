package com.dsh.tvhud.pet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.tvhud.ui.Contrast;
import com.dsh.tvhud.ui.Palette;
import com.dsh.tvhud.ui.ThemePreviewActivity;
import com.dsh.tvhud.ui.ThemeStore;
import com.dsh.tvhud.ui.Ui;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 家长设置：4 位 PIN（默认 2580）+ 二级菜单（目录 → 分组页）。
 *
 * 为什么分两级：一页塞 15 行，遥控器要滚很久才能找到目标；目录页只放 5 组，
 * 每组右侧直接写「现在的规则」，不进去就知道今天能不能看、每场多久
 * （文案与状态判定在 {@link SettingsFacts}，可离线单测）。
 * 进分组后 ←→ 改值；返回键回目录（焦点留在刚才那一组），目录再按返回退出。
 *
 * 入口有三个：首页菜单「家长设置」、宠物房间长按返回 5 秒、到点拦截页长按返回 5 秒
 * （入口不在本页改；首页入口是给家长用的明面入口，PIN 仍在本页校验）。
 * 输对一次后 5 分钟内再进免输（{@link PinGrace}），连错 5 次仍锁 60 秒。
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
    /** 免输入窗口的截止时间戳：输对一次后 5 分钟内再进不用重输。 */
    private static final String K_OK_UNTIL = "cfg_pin_ok_until";

    // 设置行 id（只用于 onRowKey 分发，不持久化）
    private static final int R_SESSION_MIN = 1;
    private static final int R_MAX_SESSIONS = 2;
    private static final int R_QUIZ = 3;
    private static final int R_EXTENSIONS = 4;
    private static final int R_WEEKEND = 5;
    private static final int R_WINDOW_ON = 6;
    private static final int R_WINDOW_START = 7;
    private static final int R_WINDOW_END = 8;
    private static final int R_FREE_PASS = 9;
    private static final int R_ADD_SESSION = 10;
    private static final int R_DEGRADE = 11;
    private static final int R_GENTLE = 12;
    private static final int R_PARENT = 13;
    private static final int R_REPORT = 14;
    private static final int R_RESET = 15;
    private static final int R_REST_INTERVAL = 16;
    private static final int R_EXT_MINUTES = 17;

    // 页面：0 = 目录，1..4 = 四个分组页（page = group + 1）；「外观」没有分组页
    private static final int PAGE_MENU = 0;
    private static final int PAGE_TODAY = 1;
    private static final int PAGE_TIME = 2;
    private static final int PAGE_WINDOW = 3;
    private static final int PAGE_PET = 4;

    private static final int GROUP_TODAY = 0;
    private static final int GROUP_TIME = 1;
    private static final int GROUP_WINDOW = 2;
    private static final int GROUP_PET = 3;
    private static final int GROUP_LOOK = 4;

    /** 目录五项；前四项进分组页，「外观」是直接打开全屏预览的动作行。 */
    private static final String[] GROUP_TITLE = {
            "今天", "时间与场次", "允许时段", "宠物与成长", "外观"};
    /** 分组页的副标题 / 页脚说明；下标 = group（0..3，外观没有分组页）。 */
    private static final String[] GROUP_QUESTION = {
            "今天能不能看，临时放开",
            "能看多久、一天几场",
            "什么时候能看",
            "退化、温和、周报与重置"};
    private static final String[] GROUP_HINT = {
            "免限当天有效，明天 0 点自动恢复；临时加场只加今天，←→ 加减，用掉的场退不回来。",
            "每场 5 分钟–3 小时 · 间隔 5 分钟–12 小时；关闭答题续时后两行自动隐藏。",
            "只拦新开的一场，不掐断正在看的；开始=结束表示全天。",
            "关掉退化后，连续不达标只影响当天成长值，不退回外观；重置只清宠物与当天预算。"};

    /** 允许时段带上的四个整点刻度（00 / 06 / 12 / 18 / 24）。 */
    private static final String[] WINDOW_HOURS = {"00", "06", "12", "18", "24"};

    // 行类型：可调值（←→ 循环）、开关（←→ 或 OK 切换）、动作（OK 执行）
    private static final int KIND_ADJUST = 0;
    private static final int KIND_SWITCH = 1;
    private static final int KIND_ACTION = 2;

    private final int[] digits = new int[PIN_LEN];
    private TextView[] boxes;
    private TextView pinHint;
    private int wrongCount;
    private long lockUntil;
    private long pinOkUntil;
    private boolean locked;
    private final Handler handler = new Handler();
    private Runnable lockTick;

    /** 当前页的行，key = R_*；LinkedHashMap 保住插入顺序（首个 = 默认焦点）。 */
    private final Map<Integer, Row> rowsById = new LinkedHashMap<Integer, Row>();
    private View[] menuRows;
    private int currentPage = PAGE_MENU;
    private int menuFocusGroup = GROUP_TODAY;
    private boolean showingSettings;
    /** onCreate 后的第一次 onResume 不重建页面，避免刚进来就重画一次。 */
    private boolean firstResume = true;
    private TextView statusLeft;
    private TextView statusRight;
    private TextView statusSub;
    private WindowTape windowTape;
    private int todayUsed;
    private int todayMax;
    private int todayState;
    private Typeface din;
    /** 当前外观的语义色板：设置页是家长配置面，跟着「外观」换肤（宠物房间/拦截页保留童趣语气）。 */
    private Palette palette;

    private long resetArmedAt;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.ensureFonts(this);
        din = Ui.din();
        SharedPreferences sp = getSharedPreferences(PetStore.PREFS, 0);
        long now = System.currentTimeMillis();
        wrongCount = sp.getInt(K_WRONG, 0);
        lockUntil = sp.getLong(K_LOCK_UNTIL, 0L);
        pinOkUntil = sp.getLong(K_OK_UNTIL, 0L);
        if (lockUntil <= now) {
            lockUntil = 0L;
            wrongCount = 0;
            persistPinState();
        }
        locked = lockUntil > now;
        if (!PinGrace.needPin(locked, pinOkUntil, now)) {
            // 5 分钟内输对过：直接进设置，不再问一次
            showMenu();
        } else {
            showPin();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locked && System.currentTimeMillis() >= lockUntil) {
            unlockPin();
        }
        if (firstResume) {
            firstResume = false;
            return;
        }
        // 从外观预览 / 周报返回：重建当前页，值才不会停在旧状态
        if (!showingSettings) {
            return;
        }
        if (currentPage == PAGE_MENU) {
            showMenu(menuFocusGroup);
        } else {
            showPage(currentPage);
        }
    }

    /** 返回键：分组页回目录（焦点留在该组），目录页才退出。 */
    @Override
    public void onBackPressed() {
        if (showingSettings && currentPage != PAGE_MENU) {
            showMenu(currentPage - 1);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- PIN ----------

    private void showPin() {
        loadLook();
        handler.removeCallbacksAndMessages(null);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(palette.appInk);
        int pad = dp(40);
        root.setPadding(pad, pad, pad, pad);

        // 不写「家长设置」标题，也不写「输入 4 位密码」副标题：
        // 四个密码格本身已经说明这是要输密码，再加两行字是噪音。
        // 家长从首页菜单进来时知道自己在哪；从拦截页长按返回进来时，
        // 突兀的标题反而会让孩子以为进了什么界面。
        LinearLayout pinRow = new LinearLayout(this);
        pinRow.setOrientation(LinearLayout.HORIZONTAL);
        pinRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(pinRow, pinLp);

        boxes = new TextView[PIN_LEN];
        for (int i = 0; i < PIN_LEN; i++) {
            final DigitBox box = new DigitBox(i);
            digits[i] = 0;
            box.setText("0");
            box.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40f);
            box.setTextColor(palette.appText);
            box.setTypeface(Ui.cjk(), Typeface.BOLD);
            box.setGravity(Gravity.CENTER);
            box.setFocusable(true);
            box.setFocusableInTouchMode(false);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.setBackground(round(cardFill(), palette.rowRadius));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(84));
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            box.setLayoutParams(lp);
            box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackground(hasFocus
                            ? round(palette.lamp, palette.rowRadius)
                            : round(cardFill(), palette.rowRadius));
                    ((TextView) v).setTextColor(hasFocus ? palette.onLamp : palette.appText);
                }
            });
            boxes[i] = box;
            pinRow.addView(box);
        }

        TextView ok = cardButton("确定", 22f);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPin();
            }
        });
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(200), dp(60));
        okLp.topMargin = dp(28);
        root.addView(ok, okLp);

        pinHint = text("", 15f, palette.appText2, false);
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
            pinOkUntil = PinGrace.okUntil(System.currentTimeMillis());
            persistPinState();
            showMenu();
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
            pinHint.setTextColor(palette.danger);
        } else {
            pinHint.setText("5 分钟内再进免输 · 连续输错 " + MAX_PIN_TRIES + " 次锁定 60 秒");
            pinHint.setTextColor(palette.appText2);
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
                .putLong(K_OK_UNTIL, pinOkUntil)
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

    // ---------- 设置页：目录 ----------

    private void showMenu() {
        showMenu(menuFocusGroup);
    }

    /**
     * 目录页：五组规则各占一行，右侧是「现在的值」。
     * 一行都不进也能回答家长最常问的三个问题：今天还能不能看、每场多久、几点到几点。
     */
    private void showMenu(int focusGroup) {
        loadLook();
        handler.removeCallbacksAndMessages(null);
        locked = false;
        showingSettings = true;
        currentPage = PAGE_MENU;
        menuFocusGroup = clampGroup(focusGroup);
        rowsById.clear();
        loadTodayFacts();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.appInk);
        scroll.setFillViewport(true);
        LinearLayout list = contentList();
        scroll.addView(list, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        list.addView(text("家长设置", 30f, palette.appText, true));
        TextView keyHint = text("↑↓ 选择 · OK 进入 · 返回键退出",
                13f, palette.appText2, false);
        keyHint.setPadding(0, dp(8), 0, 0);
        list.addView(keyHint);

        LinearLayout panel = panel(list, dp(22));
        menuRows = new View[GROUP_TITLE.length];
        for (int g = 0; g < GROUP_TITLE.length; g++) {
            if (g > 0) {
                addHairline(panel);
            }
            View row = buildMenuRow(g);
            menuRows[g] = row;
            panel.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        }

        // 页脚贴底：短页面也像一页完整的设置，而不是浮在中间
        list.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        // PIN 状态放页脚：家长知道这次是免输进来的，不用再输一遍
        TextView foot = text("PIN 已验证 · 5 分钟内再进免输",
                13f, palette.appText3, false);
        foot.setPadding(0, dp(16), 0, 0);
        list.addView(foot);

        installMenuWrap();
        menuRows[menuFocusGroup].requestFocus();
    }

    /**
     * 上下键在列表两端循环：第 1 行按上 = 最后 1 行，最后 1 行按下 = 第 1 行。
     * 首页菜单 / 分组目录 / 分组页都按这条规矩走，遥控器不会「顶死」。
     */
    private void installMenuWrap() {
        if (menuRows == null || menuRows.length == 0) {
            return;
        }
        for (int g = 0; g < menuRows.length; g++) {
            View up = g == 0 ? menuRows[menuRows.length - 1] : null;
            View down = g == menuRows.length - 1 ? menuRows[0] : null;
            if (up != null || down != null) {
                menuRows[g].setOnKeyListener(wrapListener(up, down));
            }
        }
    }

    private View.OnKeyListener wrapListener(final View upTarget, final View downTarget) {
        return new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && upTarget != null) {
                    upTarget.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && downTarget != null) {
                    downTarget.requestFocus();
                    return true;
                }
                return false;
            }
        };
    }

    private static int clampGroup(int group) {
        return group < 0 || group >= GROUP_TITLE.length ? GROUP_TODAY : group;
    }

    /** 目录里的一行：编号 + 分组名 + 当前规则摘要 + ›。 */
    private View buildMenuRow(final int group) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(18), 0);
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);

        final TextView no = text(String.format(Locale.CHINA, "%02d", group + 1),
                14f, palette.appText3, true);
        no.setTypeface(din, Typeface.BOLD);
        row.addView(no, new LinearLayout.LayoutParams(dp(44),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView title = text(GROUP_TITLE[group], 22f, palette.appText, true);
        row.addView(title, new LinearLayout.LayoutParams(dp(250),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView summary = text(menuSummary(group), 15f,
                menuSummaryColor(group), false);
        summary.setGravity(Gravity.END);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(summary, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView chevron = text("›", 22f, palette.appText3, false);
        chevron.setGravity(Gravity.END);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(26),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackground(hasFocus ? round(palette.lamp, palette.rowRadius) : null);
                int dark = palette.onLamp;
                no.setTextColor(hasFocus ? dark : palette.appText3);
                title.setTextColor(hasFocus ? dark : palette.appText);
                summary.setTextColor(hasFocus ? palette.onLamp : menuSummaryColor(group));
                chevron.setTextColor(hasFocus ? dark : palette.appText3);
                if (hasFocus) {
                    menuFocusGroup = group;
                }
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (group == GROUP_LOOK) {
                    // 外观只有一件事：直接开全屏预览，不再套一层单行页
                    startActivity(new Intent(PetSettingsActivity.this,
                            ThemePreviewActivity.class));
                    return;
                }
                showPage(group + 1);
            }
        });
        return row;
    }

    /** 目录右侧的一句结论；只有「今天」被限制时才用提醒色。 */
    private String menuSummary(int group) {
        switch (group) {
            case GROUP_TODAY:
                return SettingsFacts.todaySummary(todayState, todayUsed, todayMax);
            case GROUP_TIME:
                return SettingsFacts.timeSummary(PetStore.sessionMinutes(this),
                        PetStore.maxSessions(this), PetStore.weekendExtra(this),
                        PetStore.restIntervalMinutes(this), PetStore.quizEnabled(this),
                        PetStore.extensionMinutes(this), PetStore.extensionsPerDay(this));
            case GROUP_WINDOW:
                return SettingsFacts.windowSummary(PetStore.windowEnabled(this),
                        PetStore.windowStart(this), PetStore.windowEnd(this));
            case GROUP_PET:
                return SettingsFacts.petSummary(PetStore.degradeEnabled(this),
                        PetStore.gentleMode(this));
            case GROUP_LOOK:
                return ThemeStore.look(this).name;
            default:
                return "";
        }
    }

    private int menuSummaryColor(int group) {
        if (group != GROUP_TODAY) {
            return palette.appText2;
        }
        switch (todayState) {
            case SettingsFacts.TODAY_FREE_PASS:
                return palette.lamp;
            case SettingsFacts.TODAY_RESTING:
            case SettingsFacts.TODAY_USED_UP:
            case SettingsFacts.TODAY_OUTSIDE:
                return palette.warn;
            default:
                return palette.appText2;
        }
    }

    // ---------- 设置页：分组 ----------

    private void showPage(int page) {
        loadLook();
        handler.removeCallbacksAndMessages(null);
        locked = false;
        showingSettings = true;
        currentPage = page;
        rowsById.clear();
        statusLeft = null;
        statusRight = null;
        statusSub = null;
        windowTape = null;
        loadTodayFacts();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.appInk);
        scroll.setFillViewport(true);
        LinearLayout list = contentList();
        scroll.addView(list, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        int group = Math.max(0, Math.min(page - 1, GROUP_QUESTION.length - 1));
        // 面包屑：让家长知道自己在第几组、返回键会回到哪
        list.addView(text("家长设置 ／ " + String.format(Locale.CHINA, "%02d", page),
                13f, palette.appText3, false));
        TextView title = text(GROUP_TITLE[group], 28f, palette.appText, true);
        title.setPadding(0, dp(6), 0, 0);
        list.addView(title);
        TextView question = text(GROUP_QUESTION[group], 14f, palette.appText2, false);
        question.setPadding(0, dp(3), 0, 0);
        list.addView(question);

        if (page == PAGE_TODAY) {
            LinearLayout strip = buildTodayStrip();
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(18);
            list.addView(strip, slp);
        }
        if (page == PAGE_WINDOW) {
            // 一天的 24 小时带：亮起来的部分就是允许看的时段，白点 = 现在
            windowTape = new WindowTape();
            windowTape.setWindow(PetStore.windowEnabled(this), PetStore.windowStart(this),
                    PetStore.windowEnd(this));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            tlp.topMargin = dp(16);
            list.addView(windowTape, tlp);
        }

        LinearLayout panel = panel(list, dp(page == PAGE_TODAY ? 14 : 20));
        switch (page) {
            case PAGE_TODAY:
                addRow(panel, R_FREE_PASS, KIND_SWITCH, "今天免限");
                addRow(panel, R_ADD_SESSION, KIND_ADJUST, "临时加场");
                addRow(panel, R_PARENT, KIND_SWITCH, "家长时间（不计数）");
                break;
            case PAGE_TIME:
                addRow(panel, R_SESSION_MIN, KIND_ADJUST, "每场时长");
                addRow(panel, R_MAX_SESSIONS, KIND_ADJUST, "每天场次");
                addRow(panel, R_WEEKEND, KIND_ADJUST, "周末加场");
                addRow(panel, R_REST_INTERVAL, KIND_ADJUST, "每场间隔");
                addRow(panel, R_QUIZ, KIND_SWITCH, "答题续时");
                // 续时相关的两行只在答题续时开着时出现：关掉后它们没有意义，
                // 留着只会让家长以为「关了也还能续」。
                if (PetStore.quizEnabled(this)) {
                    addRow(panel, R_EXT_MINUTES, KIND_ADJUST, "每次续时");
                    addRow(panel, R_EXTENSIONS, KIND_ADJUST, "每天续时次数");
                }
                break;
            case PAGE_WINDOW:
                addRow(panel, R_WINDOW_ON, KIND_SWITCH, "允许时段");
                addRow(panel, R_WINDOW_START, KIND_ADJUST, "开始时间");
                addRow(panel, R_WINDOW_END, KIND_ADJUST, "结束时间");
                break;
            case PAGE_PET:
                addRow(panel, R_DEGRADE, KIND_SWITCH, "退化机制");
                addRow(panel, R_GENTLE, KIND_SWITCH, "温和模式");
                addRow(panel, R_REPORT, KIND_ACTION, "宠物周报");
                addRow(panel, R_RESET, KIND_ACTION, "重置宠物");
                break;
            default:
                break;
        }

        addHint(list, GROUP_HINT[group]);
        list.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView foot = text("←→ 改值 · OK 确认 · 返回键 回目录",
                13f, palette.appText3, false);
        foot.setPadding(0, dp(14), 0, 0);
        list.addView(foot);

        installPageWrap();
        refreshPageValues();
        if (!rowsById.isEmpty()) {
            rowsById.values().iterator().next().requestFocus();
        }
    }

    /** 今天页顶部：状态 + 一句解释（而不是一屏数字）。 */
    private LinearLayout buildTodayStrip() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundStroke(Contrast.alpha(palette.secondary, 0.10f),
                Contrast.alpha(palette.secondary, 0.24f), palette.panelRadius, 1f));
        card.setPadding(dp(18), dp(14), dp(18), dp(13));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        statusLeft = text("", 18f, palette.appText, true);
        line.addView(statusLeft, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusRight = text("", 17f, palette.lamp, true);
        statusRight.setGravity(Gravity.END);
        line.addView(statusRight);
        card.addView(line);

        statusSub = text("", 13f, palette.appText3, false);
        statusSub.setSingleLine(false);
        statusSub.setMaxLines(2);
        statusSub.setLineSpacing(0, 1.15f);
        statusSub.setPadding(0, dp(6), 0, 0);
        card.addView(statusSub);
        return card;
    }

    private int statusColor() {
        switch (todayState) {
            case SettingsFacts.TODAY_FREE_PASS:
                return palette.lamp;
            case SettingsFacts.TODAY_PARENT_TIME:
                return palette.appText;
            case SettingsFacts.TODAY_RESTING:
            case SettingsFacts.TODAY_USED_UP:
            case SettingsFacts.TODAY_OUTSIDE:
                return palette.warn;
            default:
                return palette.lamp;
        }
    }

    private String todaySubLine() {
        switch (todayState) {
            case SettingsFacts.TODAY_FREE_PASS:
                return "今天不限制时段与场次，明天 0 点恢复原来的规则。";
            case SettingsFacts.TODAY_PARENT_TIME:
                return "家长看的时候不计数、不拦截。";
            case SettingsFacts.TODAY_RESTING:
                return "本场到点了，孩子在选择答题续时或休息。";
            case SettingsFacts.TODAY_USED_UP:
                return "明天 0 点恢复 " + todayMax + " 场；也可以临时加一场。";
            case SettingsFacts.TODAY_OUTSIDE:
                return "现在只能看完手上这一场，新开一场会被拦下。";
            default:
                return "每场 " + Budget.formatMinutes(PetStore.sessionMinutes(this)) + " · 今天还剩 "
                        + Math.max(0, todayMax - todayUsed) + " 场";
        }
    }

    // ---------- 布局小工具 ----------

    private LinearLayout contentList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(56), dp(24), dp(56), dp(30));
        return list;
    }

    /** 一块圆角面板；行之间用发丝线分隔（和目录页同一种结构）。 */
    private LinearLayout panel(LinearLayout parent, int topMargin) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundStroke(panelFill(), palette.appRule, palette.panelRadius, 1f));
        panel.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        parent.addView(panel, lp);
        return panel;
    }

    private void addHairline(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(palette.appRule);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        llp.leftMargin = dp(16);
        llp.rightMargin = dp(16);
        parent.addView(line, llp);
    }

    private void addHint(LinearLayout parent, String hint) {
        TextView v = text(hint, 13f, palette.appText3, false);
        v.setSingleLine(false);
        v.setMaxLines(3);
        v.setLineSpacing(0, 1.15f);
        v.setPadding(dp(4), dp(12), dp(4), 0);
        parent.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void installPageWrap() {
        if (rowsById.isEmpty()) {
            return;
        }
        Row[] rows = rowsById.values().toArray(new Row[0]);
        for (int i = 0; i < rows.length; i++) {
            View up = i == 0 ? rows[rows.length - 1] : null;
            View down = i == rows.length - 1 ? rows[0] : null;
            if (up != null || down != null) {
                rows[i].setOnKeyListener(wrapListener(up, down));
            }
        }
    }

    private Row addRow(LinearLayout panel, int id, int kind, String name) {
        if (panel.getChildCount() > 0) {
            addHairline(panel);
        }
        Row row = new Row(id, kind, name);
        row.setValue(valueFor(id));
        panel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        rowsById.put(id, row);
        return row;
    }

    /**
     * 一行设置：整行可聚焦，焦点态用暖黄底 + 深色字（3 米外一眼看到）。
     * 可调 / 开关行在焦点态把值包成 ‹ ›，告诉家长「这一行能左右改」。
     */
    private final class Row extends LinearLayout {
        private final int id;
        private final int kind;
        private final TextView nameView;
        private final TextView valueView;
        private String rawValue = "";
        private boolean focusedNow;

        Row(int id, int kind, String name) {
            super(PetSettingsActivity.this);
            this.id = id;
            this.kind = kind;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setFocusableInTouchMode(false);
            setPadding(dp(18), dp(12), dp(18), dp(12));
            setMinimumHeight(dp(58));

            nameView = text(name, 21f, palette.appText, true);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            valueView = text("", 21f, palette.appText2, true);
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
            rawValue = text == null ? "" : text;
            renderValue(focusedNow);
        }

        private void renderValue(boolean focus) {
            if (focus && kind != KIND_ACTION && rawValue.length() > 0) {
                valueView.setText("‹ " + rawValue + " ›");
            } else {
                valueView.setText(rawValue);
            }
        }

        private void applyFocus(boolean focus) {
            focusedNow = focus;
            setBackground(focus ? round(palette.lamp, palette.rowRadius) : null);
            nameView.setTextColor(focus ? palette.onLamp : palette.appText);
            valueView.setTextColor(focus ? palette.onLamp : palette.appText2);
            renderValue(focus);
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

    // ---------- 改值 ----------

    /** dir：-1 变小 / 1 变大 / 0 OK。 */
    private void onRowKey(int id, int dir) {
        switch (id) {
            case R_SESSION_MIN:
                PetStore.setSessionMinutes(this, SettingsFacts.stepSessionMinutes(
                        PetStore.sessionMinutes(this), dir));
                break;
            case R_MAX_SESSIONS:
                PetStore.setMaxSessions(this, SettingsFacts.stepMaxSessions(
                        PetStore.maxSessions(this), dir));
                break;
            case R_WEEKEND:
                PetStore.setWeekendExtra(this, SettingsFacts.stepWeekendExtra(
                        PetStore.weekendExtra(this), dir));
                break;
            case R_REST_INTERVAL:
                PetStore.setRestIntervalMinutes(this, SettingsFacts.stepRestMinutes(
                        PetStore.restIntervalMinutes(this), dir));
                break;
            case R_QUIZ: {
                // 开关变化会改变这一页的行数（续时两行显示/隐藏），
                // 整页重建最省事，也不用担心焦点停在已经删掉的行上。
                PetStore.setQuizEnabled(this, !PetStore.quizEnabled(this));
                persistAndRefreshBudget();
                showPage(PAGE_TIME);
                return;
            }
            case R_EXT_MINUTES:
                PetStore.setExtensionMinutes(this, SettingsFacts.stepExtensionMinutes(
                        PetStore.extensionMinutes(this), dir));
                break;
            case R_EXTENSIONS:
                PetStore.setExtensionsPerDay(this, SettingsFacts.stepExtensionsPerDay(
                        PetStore.extensionsPerDay(this), dir));
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
            case R_FREE_PASS:
                if (dir != 0) {
                    return;
                }
                toggleFreePass();
                refreshPageValues();
                return;
            case R_ADD_SESSION:
                // ← 撤回、→ 加一场、OK 也加一场：到点现场一按就能给孩子加，
                // 进设置发现加多了也能退回来（退不动时给出原因）。
                if (dir < 0) {
                    removeSessionNow();
                } else {
                    addSessionNow();
                }
                refreshPageValues();
                return;
            case R_DEGRADE:
                PetStore.setDegradeEnabled(this, !PetStore.degradeEnabled(this));
                break;
            case R_GENTLE:
                PetStore.setGentleMode(this, !PetStore.gentleMode(this));
                break;
            case R_PARENT:
                PetStore.setParentMode(this, !PetStore.parentMode(this));
                break;
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
            default:
                return;
        }
        persistAndRefreshBudget();
        refreshPageValues();
    }

    private static int wrapMinute(int minute) {
        int v = minute % 1440;
        return v < 0 ? v + 1440 : v;
    }

    /** 家长临时加一场：只加今天；跨天先把预算滚到新的一天再加。 */
    private void addSessionNow() {
        long today = PetStore.todayStart();
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart != today) {
            b.rollDay(today);
        }
        PetStore.applyConfig(this, b, today);
        if (!b.addSession()) {
            Toast.makeText(this, "今天最多加 " + Budget.MAX_EXTRA_SESSIONS + " 场",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        PetStore.saveBudget(this, b);
        Toast.makeText(this, "今天多一场，共 " + b.maxSessions + " 场", Toast.LENGTH_SHORT).show();
    }

    /**
     * 撤回一场今天临时加的场次：只退家长加的「还没用掉」的部分。
     * 已经用掉的场次不会因为撤回而消失（否则孩子看完的场次会被倒扣）。
     */
    private void removeSessionNow() {
        long today = PetStore.todayStart();
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart != today) {
            b.rollDay(today);
        }
        PetStore.applyConfig(this, b, today);
        if (b.extraSessions <= 0) {
            Toast.makeText(this, "今天没有临时加的场次", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!b.removeSession()) {
            Toast.makeText(this, "这一场已经用掉了，退不回来", Toast.LENGTH_SHORT).show();
            return;
        }
        PetStore.saveBudget(this, b);
        Toast.makeText(this, "撤回一场，今天共 " + b.maxSessions + " 场", Toast.LENGTH_SHORT).show();
    }

    /**
     * 今天免限卡：开 = 今天不拦（时段 + 场次），关 = 立刻收回。
     *
     * 收回时会把已经打开的闸门关掉，孩子正在看的这一场不算数；
     * 开的时候同样要关闸门，否则孩子会一直卡在「现在是休息时间」那页上，
     * 后面 Service 也不会再拉起它（gateOpen 还挂着）。
     */
    private void toggleFreePass() {
        long today = PetStore.todayStart();
        boolean on = PetStore.hasFreePass(this);
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart != today) {
            b.rollDay(today);
        }
        if (on) {
            PetStore.revokeFreePass(this);
            Toast.makeText(this, "已收回今天的免限，明天照常", Toast.LENGTH_SHORT).show();
        } else {
            PetStore.grantFreePass(this);
            Toast.makeText(this, "今天不限制了，明天自动恢复", Toast.LENGTH_SHORT).show();
        }
        PetStore.applyConfig(this, b, today);
        // 关闸门：不管开还是关都重置成「没拦住」的状态
        b.gateOpen = false;
        b.gateReason = Budget.GATE_NORMAL;
        b.sessionMs = 0L;
        b.extensionMs = 0L;
        PetStore.saveBudget(this, b);
    }

    private void armReset() {
        if (resetArmed()) {
            resetArmedAt = 0L;
            PetStore.reset(this);   // 只重置宠物状态和当天预算，配置与 PIN 不动
            persistAndRefreshBudget();
            refreshPageValues();
            Toast.makeText(this, "宠物已重置，设置和密码保留", Toast.LENGTH_SHORT).show();
            return;
        }
        resetArmedAt = System.currentTimeMillis();
        refreshPageValues();
        Toast.makeText(this, "再按一次确认重置", Toast.LENGTH_SHORT).show();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (resetArmed()) {
                    return;
                }
                if (rowsById.containsKey(R_RESET)) {
                    refreshPageValues();
                }
            }
        }, 3100L);
    }

    /** 重置的二次确认窗口：3 秒内再按一次才真的重置。 */
    private boolean resetArmed() {
        if (resetArmedAt == 0L) {
            return false;
        }
        if (System.currentTimeMillis() - resetArmedAt >= 3000L) {
            resetArmedAt = 0L;
            return false;
        }
        return true;
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
     * HudService 下一 tick 自动重载。跨天时先把预算滚到今天，免得周末/临时场次算错。
     */
    private void persistAndRefreshBudget() {
        long today = PetStore.todayStart();
        Budget b = PetStore.loadBudget(this);
        if (b.dayStart != today) {
            b.rollDay(today);
        }
        PetStore.applyConfig(this, b, today);
        PetStore.saveBudget(this, b);
    }

    // ---------- 刷新值 ----------

    /** 重新读盘，刷新当前页所有行的值（今天页连状态条一起刷新）。 */
    private void refreshPageValues() {
        loadTodayFacts();
        for (Map.Entry<Integer, Row> e : rowsById.entrySet()) {
            e.getValue().setValue(valueFor(e.getKey()));
        }
        if (windowTape != null) {
            windowTape.setWindow(PetStore.windowEnabled(this), PetStore.windowStart(this),
                    PetStore.windowEnd(this));
        }
        if (statusLeft != null) {
            statusLeft.setText("今天已用 " + todayUsed + " / " + todayMax + " 场");
            statusRight.setText(SettingsFacts.todayStatus(todayState));
            statusRight.setTextColor(statusColor());
            statusSub.setText(todaySubLine());
        }
    }

    private String valueFor(int id) {
        switch (id) {
            case R_SESSION_MIN:
                return Budget.formatMinutes(PetStore.sessionMinutes(this));
            case R_MAX_SESSIONS:
                return PetStore.maxSessions(this) + " 场";
            case R_WEEKEND: {
                int extra = PetStore.weekendExtra(this);
                return extra == 0 ? "关" : "+" + extra + " 场";
            }
            case R_REST_INTERVAL:
                return Budget.formatMinutes(PetStore.restIntervalMinutes(this));
            case R_QUIZ:
                return onOff(PetStore.quizEnabled(this));
            case R_EXT_MINUTES:
                return Budget.formatMinutes(PetStore.extensionMinutes(this));
            case R_EXTENSIONS:
                // 旧存档里可能是 0（旧 UI 允许 0 = 关），现在下限是 1 次，显示也按有效值来
                return Budget.clamp(PetStore.extensionsPerDay(this),
                        Budget.MIN_EXTENSIONS, Budget.MAX_EXTENSIONS) + " 次";
            case R_WINDOW_ON:
                return onOff(PetStore.windowEnabled(this));
            case R_WINDOW_START:
                return Budget.formatMinute(PetStore.windowStart(this));
            case R_WINDOW_END:
                return Budget.formatMinute(PetStore.windowEnd(this));
            case R_FREE_PASS:
                return onOff(PetStore.hasFreePass(this));
            case R_ADD_SESSION: {
                int extra = todayExtraSessions();
                return extra > 0 ? "今天 +" + extra + " 场" : "今天不加";
            }
            case R_DEGRADE:
                return onOff(PetStore.degradeEnabled(this));
            case R_GENTLE:
                return onOff(PetStore.gentleMode(this));
            case R_PARENT:
                return onOff(PetStore.parentMode(this));
            case R_REPORT:
                return "查看 ›";
            case R_RESET:
                return resetArmed() ? "再按一次" : "按两次确认";
            default:
                return "";
        }
    }

    /** 今天真实追加的场次；跨天后旧存档里的 +N 不算。 */
    private int todayExtraSessions() {
        Budget b = PetStore.loadBudget(this);
        return b.dayStart == PetStore.todayStart() ? b.extraSessions : 0;
    }

    /** 今天的场次 / 状态一次性读出来，目录页和今天页共用。 */
    private void loadTodayFacts() {
        long now = System.currentTimeMillis();
        long today = PetStore.todayStart();
        Budget b = PetStore.loadBudget(this);
        boolean isToday = b.dayStart == today;
        todayUsed = isToday ? b.sessionsUsed : 0;
        int extra = isToday ? b.extraSessions : 0;
        int max = Budget.effectiveMaxSessions(PetStore.maxSessions(this),
                PetStore.weekendExtra(this), now) + extra;
        todayMax = Math.max(max, todayUsed);
        boolean windowEnabled = PetStore.windowEnabled(this);
        boolean inWindow = !windowEnabled
                || Budget.inWindow(now, PetStore.windowStart(this), PetStore.windowEnd(this));
        todayState = SettingsFacts.todayState(PetStore.hasFreePass(this),
                PetStore.parentMode(this), isToday && b.gateOpen, todayUsed, todayMax,
                windowEnabled, inWindow);
    }


    /** 允许时段的一天 24 小时带：亮起来的部分 = 允许看的时段，白点 = 现在。 */
    private final class WindowTape extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean enabled;
        private int startMin = 17 * 60;
        private int endMin = 20 * 60;
        private int nowMin;

        WindowTape() {
            super(PetSettingsActivity.this);
            label.setTypeface(Ui.cjk());
            label.setTextSize(dp(12f));
            nowMin = currentMinute();
        }

        void setWindow(boolean enabled, int startMin, int endMin) {
            this.enabled = enabled;
            this.startMin = startMin;
            this.endMin = endMin;
            nowMin = currentMinute();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = dp(14);
            float top = dp(8);
            float radius = h / 2f;
            rect.set(0, top, w, top + h);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(palette.tapeTrack);
            canvas.drawRoundRect(rect, radius, radius, fill);

            if (!enabled) {
                // 关 = 不限制时段：整条带子都算可看，压暗表示「没在管」
                fill.setColor(Contrast.alpha(palette.lamp, 0.20f));
                canvas.drawRoundRect(rect, radius, radius, fill);
            } else {
                fill.setColor(Contrast.alpha(palette.lamp, 0.78f));
                if (startMin == endMin) {
                    canvas.drawRoundRect(rect, radius, radius, fill);
                } else if (startMin < endMin) {
                    drawSegment(canvas, w, top, h, startMin, endMin);
                } else {
                    drawSegment(canvas, w, top, h, startMin, 1440);
                    drawSegment(canvas, w, top, h, 0, endMin);
                }
            }

            // 现在：2dp 竖线 + 圆点（和台账页的「现在」同一语法）
            float x = w * nowMin / 1440f;
            float lineTop = top - dp(3);
            float lineBottom = top + h + dp(3);
            fill.setColor(palette.appText);
            canvas.drawRect(x - dp(1), lineTop, x + dp(1), lineBottom, fill);
            canvas.drawCircle(x, top + h / 2f, dp(3.2f), fill);

            // 一天 24 小时刻度
            label.setColor(palette.appText3);
            float baseline = top + h + dp(16);
            for (int i = 0; i < WINDOW_HOURS.length; i++) {
                float tx = w * i / 4f;
                label.setTextAlign(i == 0 ? Paint.Align.LEFT
                        : (i == WINDOW_HOURS.length - 1 ? Paint.Align.RIGHT : Paint.Align.CENTER));
                canvas.drawText(WINDOW_HOURS[i], tx, baseline, label);
            }
        }

        private void drawSegment(Canvas canvas, float w, float top, float h,
                                 int fromMin, int toMin) {
            float r = h / 2f;
            rect.set(w * fromMin / 1440f, top, w * toMin / 1440f, top + h);
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }

    private static int currentMinute() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static String onOff(boolean on) {
        return on ? "开" : "关";
    }

    /** 进页面/重建前重新取色板：从「外观」预览返回时 onResume 会重建，颜色才不会停在旧 look。 */
    private void loadLook() {
        palette = ThemeStore.look(this).palette();
    }

    /** 面板底：比页面底色抬一档，不改色相。 */
    private int panelFill() {
        return Contrast.alpha(palette.appText, 0.045f);
    }

    /** 卡片 / 密码格：比面板再抬一档。 */
    private int cardFill() {
        return Contrast.alpha(palette.appText, 0.10f);
    }

    private GradientDrawable round(int color, float radiusDp) {
        return Ui.round(color, dp(radiusDp));
    }

    private GradientDrawable roundStroke(int color, int strokeColor,
                                         float radiusDp, float strokeDp) {
        return Ui.roundStroke(color, dp(radiusDp), strokeColor, Math.max(1, dp(strokeDp)));
    }

    /** 基础文字：统一字体，颜色只从色板来。 */
    private TextView text(CharSequence s, float sizeDp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        v.setTextColor(color);
        v.setTypeface(Ui.cjk(), bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setSingleLine(true);
        return v;
    }

    /** 大按钮：焦点从「面板抬色 + 正文色」变「灯色实底 + 反白字」。 */
    private TextView cardButton(String label, float sizeDp) {
        final TextView v = text(label, sizeDp, palette.appText, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.setBackground(round(cardFill(), palette.rowRadius));
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focus) {
                TextView tv = (TextView) view;
                if (focus) {
                    tv.setBackground(round(palette.lamp, palette.rowRadius));
                    tv.setTextColor(palette.onLamp);
                } else {
                    tv.setBackground(round(cardFill(), palette.rowRadius));
                    tv.setTextColor(palette.appText);
                }
            }
        });
        return v;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
