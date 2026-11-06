package com.dsh.tvhud.pet;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 家长设置：4 位 PIN（默认 2580）+ 每场时长 / 每天场次 / 答题续时 / 家长时间 / 重置。
 *
 * 入口有两个：宠物房间长按返回 5 秒、到点拦截页长按返回 5 秒。
 * 遥控器：←→ 移动，↑↓ 改数字，OK 确认。
 */
public final class PetSettingsActivity extends Activity {
    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    private static final int PIN_LEN = 4;

    private Typeface cjk = Typeface.DEFAULT;
    private LinearLayout root;
    private final int[] digits = new int[PIN_LEN];
    private TextView[] boxes;
    private int wrongCount;
    private long resetArmedAt;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            cjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
        showPin();
    }

    // ---------- PIN ----------

    private void showPin() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xff0e1114);
        root.setPadding(dp(40), dp(40), dp(40), dp(40));

        TextView title = label("家长设置", 26f, 0xffe9eef3);
        root.addView(title);
        TextView hint = label("输入 4 位密码", 13f, 0xff9aa7b2);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        root.addView(hint, hintLp);

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
            box.setTextColor(0xffe9eef3);
            box.setTypeface(cjk, Typeface.BOLD);
            box.setGravity(Gravity.CENTER);
            box.setFocusable(true);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.setBackground(boxBg(false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(78));
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            box.setLayoutParams(lp);
            box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackground(boxBg(hasFocus));
                    ((TextView) v).setTextColor(hasFocus ? 0xff0e1114 : 0xffe9eef3);
                }
            });
            boxes[i] = box;
            pinRow.addView(box);
        }

        TextView ok = label("确定", 20f, 0xff0e1114);
        ok.setGravity(Gravity.CENTER);
        ok.setFocusable(true);
        ok.setBackground(boxBg(true));
        ok.setPadding(dp(30), dp(14), dp(30), dp(14));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        okLp.topMargin = dp(30);
        root.addView(ok, okLp);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPin();
            }
        });

        setContentView(root);
        boxes[0].requestFocus();
    }

    private final class DigitBox extends TextView {
        private final int index;

        DigitBox(int index) {
            super(PetSettingsActivity.this);
            this.index = index;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
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
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
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

    private void checkPin() {
        StringBuilder sb = new StringBuilder();
        for (int d : digits) {
            sb.append(d);
        }
        if (sb.toString().equals(PetStore.pin(this))) {
            showSettings();
            return;
        }
        wrongCount++;
        Toast.makeText(this, "密码不对", Toast.LENGTH_SHORT).show();
        for (TextView b : boxes) {
            b.setText("0");
        }
        for (int i = 0; i < digits.length; i++) {
            digits[i] = 0;
        }
        boxes[0].requestFocus();
    }

    // ---------- 设置 ----------

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xff0e1114);
        panel.setPadding(dp(60), dp(40), dp(60), dp(40));

        TextView title = label("宠物设置", 26f, 0xffe9eef3);
        panel.addView(title);

        final TextView sessionRow = row(panel, "每场时长", "");
        final TextView countRow = row(panel, "每天场次", "");
        final TextView quizRow = row(panel, "答题续时", "");
        final TextView parentRow = row(panel, "家长时间（不计数）", "");
        final TextView resetRow = row(panel, "重置宠物", "按两次确认");
        TextView back = row(panel, "返回", "");

        sessionRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = PetStore.sessionMinutes(PetSettingsActivity.this);
                int next = cur >= 20 ? 10 : cur + 5;
                PetStore.setSessionMinutes(PetSettingsActivity.this, next);
                refreshRows(sessionRow, countRow, quizRow, parentRow);
            }
        });
        countRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = PetStore.maxSessions(PetSettingsActivity.this);
                int next = cur >= 4 ? 2 : cur + 1;
                PetStore.setMaxSessions(PetSettingsActivity.this, next);
                refreshRows(sessionRow, countRow, quizRow, parentRow);
            }
        });
        quizRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PetStore.setQuizEnabled(PetSettingsActivity.this,
                        !PetStore.quizEnabled(PetSettingsActivity.this));
                refreshRows(sessionRow, countRow, quizRow, parentRow);
            }
        });
        parentRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !PetStore.parentMode(PetSettingsActivity.this);
                PetStore.setParentMode(PetSettingsActivity.this, on);
                Budget b = PetStore.loadBudget(PetSettingsActivity.this);
                b.parentMode = on;
                PetStore.saveBudget(PetSettingsActivity.this, b);
                refreshRows(sessionRow, countRow, quizRow, parentRow);
            }
        });
        resetRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - resetArmedAt < 3000L) {
                    PetStore.reset(PetSettingsActivity.this);
                    resetArmedAt = 0L;
                    Toast.makeText(PetSettingsActivity.this, "宠物已重置", Toast.LENGTH_SHORT).show();
                } else {
                    resetArmedAt = now;
                    Toast.makeText(PetSettingsActivity.this, "再按一次确认重置", Toast.LENGTH_SHORT).show();
                }
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setContentView(panel);
        refreshRows(sessionRow, countRow, quizRow, parentRow);
        sessionRow.requestFocus();
    }

    private TextView row(LinearLayout parent, String name, String hint) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f);
        tv.setTextColor(0xffe9eef3);
        tv.setTypeface(cjk);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setFocusable(true);
        tv.setPadding(dp(24), dp(16), dp(24), dp(16));
        tv.setBackground(rowBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        tv.setLayoutParams(lp);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(rowBg(hasFocus));
            }
        });
        parent.addView(tv);
        return tv;
    }

    private void refreshRows(TextView sessionRow, TextView countRow, TextView quizRow,
                             TextView parentRow) {
        sessionRow.setText(String.format(Locale.CHINA, "每场时长     %d 分钟",
                PetStore.sessionMinutes(this)));
        countRow.setText(String.format(Locale.CHINA, "每天场次     %d 场",
                PetStore.maxSessions(this)));
        quizRow.setText(String.format(Locale.CHINA, "答题续时     %s",
                PetStore.quizEnabled(this) ? "开" : "关"));
        parentRow.setText(String.format(Locale.CHINA, "家长时间     %s",
                PetStore.parentMode(this) ? "开（不计数）" : "关"));
    }

    // ---------- 样式 ----------

    private GradientDrawable boxBg(boolean focus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(focus ? 0xffffffff : 0x33ffffff);
        return bg;
    }

    private GradientDrawable rowBg(boolean focus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(focus ? 0x33ffffff : 0x00000000);
        return bg;
    }

    private TextView label(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        v.setTextColor(color);
        v.setTypeface(cjk);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
