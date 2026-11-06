package com.dsh.tvhud.pet;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 宠物房间：2.5D 宠物 + 状态 + 吃饭/喝水/摸摸/洗澡/刷牙 + 换装。
 *
 * 遥控器：←→ 选按钮，OK 确认，返回回首页；长按返回 5 秒进家长设置。
 * 房间里的时间不算「看电视」——HudService 已经排除自己的包名。
 * 不在这里启动 Service：首页/服务本身已经负责，避免 pet 包反向依赖 hud。
 */
public final class PetActivity extends Activity {
    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    private static final long BACK_LONG_MS = 5000L;

    private static final String[] HATS = {"", "cap", "beanie"};
    private static final String[] CLOTHES = {"", "shirt", "cape"};
    private static final String[] WINGS = {"", "small", "big"};

    private PetState state;
    private Budget budget;
    private PetView petView;
    private TextView levelText;
    private TextView expText;
    private TextView starsText;
    private TextView statusText;
    private TextView sessionText;
    private TextView hintText;

    private final Handler handler = new Handler();
    private long backDownAt = 0L;
    private int wardrobeStep = 0;
    private final Runnable decay = new Runnable() {
        @Override
        public void run() {
            PetEngine.settle(state, System.currentTimeMillis());
            PetEngine.maybeStartTeething(state, System.currentTimeMillis());
            PetStore.save(PetActivity.this, state, budget);
            refresh();
            handler.postDelayed(this, 60000L);
        }
    };

    private Typeface cjk = Typeface.DEFAULT;
    private Typeface din = Typeface.DEFAULT;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initFonts();
        state = PetStore.loadState(this);
        budget = PetStore.loadBudget(this);
        PetStore.ensureDay(this, state, budget);
        PetEngine.settle(state, System.currentTimeMillis());
        PetEngine.maybeStartTeething(state, System.currentTimeMillis());
        PetStore.save(this, state, budget);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        state = PetStore.loadState(this);
        budget = PetStore.loadBudget(this);
        PetStore.ensureDay(this, state, budget);
        PetEngine.settle(state, System.currentTimeMillis());
        PetStore.save(this, state, budget);
        petView.setState(state);
        refresh();
        handler.removeCallbacks(decay);
        handler.postDelayed(decay, 60000L);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(decay);
        if (state != null && budget != null) {
            PetStore.save(this, state, budget);
        }
        super.onPause();
    }

    // ---------- 遥控器 ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) {
                backDownAt = System.currentTimeMillis();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long held = System.currentTimeMillis() - backDownAt;
            if (held >= BACK_LONG_MS) {
                startActivity(new Intent(this, PetSettingsActivity.class));
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    // ---------- 界面 ----------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff0b0f1a);

        petView = new PetView(this);
        petView.setState(state);
        root.addView(petView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(40), dp(26), dp(40), 0);
        root.addView(top, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        levelText = label("", 22f, 0xfff2f6fa, cjk, 0.06f);
        titleRow.addView(levelText);
        titleRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        starsText = label("", 16f, 0xffffe27a, cjk, 0.04f);
        titleRow.addView(starsText);
        top.addView(titleRow);

        expText = label("", 12f, 0xff9aa7b2, cjk, 0.04f);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expLp.topMargin = dp(4);
        top.addView(expText, expLp);

        statusText = label("", 11f, 0xff9aa7b2, cjk, 0.03f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(10);
        top.addView(statusText, statusLp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(32), 0, dp(32), dp(26));
        root.addView(bottom, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        sessionText = label("", 11f, 0xff9aa7b2, cjk, 0.03f);
        sessionText.setGravity(Gravity.CENTER);
        bottom.addView(sessionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(10);
        bottom.addView(actions, actionsLp);

        addAction(actions, "吃饭", PetEngine.ACTION_EAT);
        addAction(actions, "喝水", PetEngine.ACTION_DRINK);
        addAction(actions, "摸摸", PetEngine.ACTION_PET);
        addAction(actions, "洗澡", PetEngine.ACTION_BATH);
        addAction(actions, "刷牙", PetEngine.ACTION_BRUSH);
        addAction(actions, "换装", -1);

        hintText = label("← → 选择 · OK 确认 · 长按返回 家长设置", 10f, 0xff5f6b76, cjk, 0.03f);
        hintText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(10);
        bottom.addView(hintText, hintLp);

        setContentView(root);
        View first = actions.getChildAt(0);
        if (first != null) {
            first.requestFocus();
        }
    }

    private void addAction(LinearLayout parent, final String name, final int action) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
        tv.setTextColor(0xfff2f6fa);
        tv.setTypeface(cjk);
        tv.setGravity(Gravity.CENTER);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setPadding(dp(6), dp(16), dp(6), dp(16));
        tv.setBackground(buttonBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        tv.setLayoutParams(lp);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(buttonBg(hasFocus));
                ((TextView) v).setTextColor(hasFocus ? 0xff0e1114 : 0xfff2f6fa);
            }
        });
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action < 0) {
                    cycleWardrobe();
                } else {
                    doCare(action);
                }
            }
        });
        parent.addView(tv);
    }

    private GradientDrawable buttonBg(boolean focus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(focus ? 0xffe8f1ff : 0x33ffffff);
        return bg;
    }

    // ---------- 行为 ----------

    private void doCare(int action) {
        PetEngine.Result r = PetEngine.care(state, action, System.currentTimeMillis());
        switch (action) {
            case PetEngine.ACTION_EAT:
                petView.play(PetView.ANIM_EAT);
                toast("好吃！");
                break;
            case PetEngine.ACTION_DRINK:
                petView.play(PetView.ANIM_DRINK);
                toast("咕咚咕咚");
                break;
            case PetEngine.ACTION_BATH:
                petView.play(PetView.ANIM_BATH);
                toast("洗香香");
                break;
            case PetEngine.ACTION_BRUSH:
                petView.play(PetView.ANIM_BRUSH);
                toast("牙齿亮晶晶");
                break;
            default:
                petView.play(PetView.ANIM_PET);
                toast("它蹭了蹭你");
                break;
        }
        if (r.allCareDone) {
            toast("今天的照顾全做完啦，+1 星");
        }
        if (r.evolved) {
            petView.play(PetView.ANIM_EVOLVE);
            toast("进化成 " + PetEngine.stageName(r.newStage) + "！");
        } else if (r.leveled) {
            toast("升到 Lv " + r.newLevel);
        }
        if (PetEngine.maybeStartTeething(state, System.currentTimeMillis())) {
            toast("换牙啦，掉了一颗门牙");
        }
        PetStore.save(this, state, budget);
        petView.setState(state);
        refresh();
    }

    /** 帽子 / 衣服 / 翅膀轮流换，每按一次换一个槽位，保证三个都能换到。 */
    private void cycleWardrobe() {
        for (int tries = 0; tries < 3; tries++) {
            int slot = wardrobeStep % 3;
            wardrobeStep = (wardrobeStep + 1) % 3;
            if (slot == 0 && state.canWearHat()) {
                state.hat = next(HATS, state.hat);
                toast(state.hat.isEmpty() ? "收起帽子" : "戴上帽子");
                saveWardrobe();
                return;
            }
            if (slot == 1 && state.canWearClothes()) {
                state.clothes = next(CLOTHES, state.clothes);
                toast(state.clothes.isEmpty() ? "换回原来的样子" : "换上新衣服");
                saveWardrobe();
                return;
            }
            if (slot == 2 && state.canWearWings()) {
                state.wings = next(WINGS, state.wings);
                toast(state.wings.isEmpty() ? "收起翅膀" : "长出翅膀啦");
                saveWardrobe();
                return;
            }
        }
        if (!state.canWearHat()) {
            toast("Lv 3 解锁帽子，继续加油");
        } else if (!state.canWearClothes()) {
            toast("Lv 10 解锁衣服，继续加油");
        } else if (!state.canWearWings()) {
            toast("Lv 24 解锁翅膀，继续加油");
        } else {
            toast("都换过一轮啦");
        }
    }

    private void saveWardrobe() {
        PetStore.save(this, state, budget);
        petView.setState(state);
        refresh();
    }

    private static String next(String[] arr, String current) {
        int idx = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(current)) {
                idx = i;
                break;
            }
        }
        return arr[(idx + 1) % arr.length];
    }

    private void refresh() {
        int level = state.level();
        levelText.setText(String.format(Locale.CHINA, "Lv %d · %s", level, state.stageName()));
        int nextThreshold = nextStageThreshold(state.exp);
        if (nextThreshold > 0) {
            expText.setText(String.format(Locale.CHINA, "成长值 %d / %d", state.exp, nextThreshold));
        } else {
            expText.setText(String.format(Locale.CHINA, "成长值 %d · 完全体", state.exp));
        }
        starsText.setText(String.format(Locale.CHINA, "★ %d", state.stars));
        String teeth = state.teethingUntil > System.currentTimeMillis()
                ? "换牙中" : state.teethLabel();
        statusText.setText(String.format(Locale.CHINA,
                "饱 %d   渴 %d   牙 %s   体重 %s   心情 %s",
                state.hunger, state.thirst, teeth, state.weightLabel(), state.moodLabel()));
        if (budget.isFinished()) {
            sessionText.setText(String.format(Locale.CHINA,
                    "今天 %d / %d 场 · 每场 %d 分钟 · 今天玩完啦",
                    budget.sessionsUsed, budget.maxSessions, budget.sessionMinutes));
        } else {
            sessionText.setText(String.format(Locale.CHINA,
                    "今天 %d / %d 场 · 每场 %d 分钟 · 第 %d 场",
                    budget.sessionsUsed, budget.maxSessions, budget.sessionMinutes,
                    budget.sessionsUsed + 1));
        }
    }

    private static int nextStageThreshold(int exp) {
        for (int threshold : PetState.STAGE_EXP) {
            if (exp < threshold) {
                return threshold;
            }
        }
        return 0;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- 字体 / 尺寸 ----------

    private void initFonts() {
        try {
            cjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
        try {
            din = Typeface.createFromAsset(getAssets(), "MIUI_EX_Light.ttf");
        } catch (Throwable ignored) {
        }
        if (din == null) {
            din = Typeface.DEFAULT;
        }
    }

    private TextView label(String s, float size, int color, Typeface tf, float tracking) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        v.setTextColor(color);
        v.setTypeface(tf);
        v.setLetterSpacing(tracking);
        v.setSingleLine(true);
        return v;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
