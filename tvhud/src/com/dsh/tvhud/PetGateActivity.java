package com.dsh.tvhud;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 到点拦截页：宠物出来说「该休息啦」，孩子只有两个出口——
 * 休息，或答 3 题对 2 题再玩 5 分钟。60 秒不选默认休息。
 *
 * 返回键无效（避免误触退出），长按返回 5 秒进家长设置。
 */
public final class PetGateActivity extends Activity {
    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    private static final long BACK_LONG_MS = 5000L;
    private static final int SECONDS = 60;

    /** 已经有一个拦截页在前台/设置页压在它上面时，Service 不要重复拉起。 */
    private static volatile boolean sVisible;
    private static volatile boolean sSettingsOpen;

    public static boolean isBlocking() {
        return sVisible || sSettingsOpen;
    }

    private PetState state;
    private Budget budget;
    private QuizBank quizBank;
    private PetView petView;
    private TextView titleText;
    private TextView messageText;
    private TextView countdownText;
    private LinearLayout choiceRow;
    private LinearLayout quizPanel;
    private TextView questionText;
    private final List<TextView> optionViews = new ArrayList<TextView>();

    private List<QuizBank.Question> questions = new ArrayList<QuizBank.Question>();
    private int quizIndex;
    private int quizCorrect;
    private boolean quizMode;
    private boolean finishedChoice;
    private boolean answering;
    private int secondsLeft = SECONDS;
    private long backDownAt;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            secondsLeft--;
            if (secondsLeft <= 0) {
                doRest(true);
                return;
            }
            updateCountdown();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        state = PetStore.loadState(this);
        budget = PetStore.loadBudget(this);
        PetStore.ensureDay(this, state, budget);
        PetEngine.settle(state, System.currentTimeMillis());
        quizBank = PetStore.loadQuiz(this);
        buildUi();
        updateCountdown();
        handler.postDelayed(tick, 1000L);
        initTts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sVisible = true;
        sSettingsOpen = false;
    }

    @Override
    protected void onPause() {
        sVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sVisible = false;
        sSettingsOpen = false;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
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
            if (System.currentTimeMillis() - backDownAt >= BACK_LONG_MS) {
                sSettingsOpen = true;
                startActivity(new android.content.Intent(this, PetSettingsActivity.class));
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // 拦截页不允许直接退出
    }

    // ---------- 界面 ----------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff0b0f1a);

        petView = new PetView(this);
        petView.setState(state);
        petView.setTranslationY(-dp(110));
        root.addView(petView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(32), dp(22), dp(32), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xf0141b2a);
        bg.setCornerRadius(dp(26));
        card.setBackground(bg);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                dp(760), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardLp.bottomMargin = dp(36);
        root.addView(card, cardLp);

        titleText = label("这一场结束啦", 30f, 0xfff6f8fb, true);
        card.addView(titleText);

        messageText = label("让眼睛和电视都休息一下", 15f, 0xff9aa7b2, false);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(8);
        card.addView(messageText, msgLp);

        countdownText = label("", 11f, 0xff697788, false);
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cdLp.topMargin = dp(8);
        card.addView(countdownText, cdLp);

        choiceRow = new LinearLayout(this);
        choiceRow.setOrientation(LinearLayout.HORIZONTAL);
        choiceRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(20);
        card.addView(choiceRow, rowLp);

        TextView rest = choiceButton("好，休息一下");
        rest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRest(false);
            }
        });
        choiceRow.addView(rest);

        TextView quiz = choiceButton("答题再玩 5 分钟");
        quiz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQuiz();
            }
        });
        choiceRow.addView(quiz);
        if (!budget.canQuiz() || !budget.quizEnabled) {
            quiz.setVisibility(View.GONE);
        }

        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);
        quizPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        quizPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams qpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qpLp.topMargin = dp(18);
        card.addView(quizPanel, qpLp);

        questionText = label("", 26f, 0xfff6f8fb, true);
        questionText.setGravity(Gravity.CENTER);
        quizPanel.addView(questionText);

        LinearLayout opts1 = new LinearLayout(this);
        opts1.setOrientation(LinearLayout.HORIZONTAL);
        opts1.setGravity(Gravity.CENTER);
        quizPanel.addView(opts1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout opts2 = new LinearLayout(this);
        opts2.setOrientation(LinearLayout.HORIZONTAL);
        opts2.setGravity(Gravity.CENTER);
        quizPanel.addView(opts2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView opt = new TextView(this);
            opt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f);
            opt.setTextColor(0xff0e1114);
            opt.setTypeface(cjk);
            opt.setGravity(Gravity.CENTER);
            opt.setFocusable(true);
            opt.setPadding(dp(10), dp(14), dp(10), dp(14));
            opt.setBackground(optionBg(false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(6);
            lp.rightMargin = dp(6);
            lp.topMargin = dp(6);
            opt.setLayoutParams(lp);
            opt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackground(optionBg(hasFocus));
                }
            });
            opt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    answer(index);
                }
            });
            optionViews.add(opt);
            if (i < 2) {
                opts1.addView(opt);
            } else {
                opts2.addView(opt);
            }
        }

        setContentView(root);
        rest.requestFocus();
    }

    private TextView choiceButton(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
        tv.setTextColor(0xff0e1114);
        tv.setTypeface(cjk);
        tv.setGravity(Gravity.CENTER);
        tv.setFocusable(true);
        tv.setPadding(dp(20), dp(14), dp(20), dp(14));
        tv.setBackground(optionBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        lp.rightMargin = dp(8);
        tv.setLayoutParams(lp);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(optionBg(hasFocus));
            }
        });
        return tv;
    }

    private GradientDrawable optionBg(boolean focus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(focus ? 0xffffe27a : 0xffe8eef5);
        return bg;
    }

    private void updateCountdown() {
        countdownText.setText(String.format(Locale.CHINA, "%d 秒后自动休息", secondsLeft));
    }

    // ---------- 选择 ----------

    private void doRest(boolean auto) {
        if (finishedChoice) {
            return;
        }
        finishedChoice = true;
        handler.removeCallbacksAndMessages(null);
        budget.onRest(System.currentTimeMillis());
        PetEngine.Result r = PetEngine.onSessionRest(state, auto, System.currentTimeMillis());
        PetStore.save(this, state, budget);
        messageText.setText(auto ? "电视自动休息啦，明天见" : "好，休息一下");
        titleText.setText("晚安");
        if (r.evolved) {
            petView.play(PetView.ANIM_EVOLVE);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, auto ? 1200L : 900L);
    }

    private void startQuiz() {
        if (!budget.canQuiz()) {
            doRest(false);
            return;
        }
        questions = quizBank.pick(3);
        if (questions.isEmpty()) {
            // 题库没加载出来：不拿孩子卡住，直接休息
            doRest(false);
            return;
        }
        quizMode = true;
        petView.setVisibility(View.GONE);
        quizIndex = 0;
        quizCorrect = 0;
        handler.removeCallbacksAndMessages(null);
        choiceRow.setVisibility(View.GONE);
        countdownText.setText("答对 2 题，再玩 5 分钟");
        quizPanel.setVisibility(View.VISIBLE);
        showQuestion();
    }

    private void showQuestion() {
        if (quizIndex >= questions.size()) {
            finishQuiz();
            return;
        }
        QuizBank.Question q = questions.get(quizIndex);
        titleText.setText(String.format(Locale.CHINA, "第 %d / %d 题", quizIndex + 1, questions.size()));
        messageText.setText("想一想，别着急");
        questionText.setText(q.prompt);
        for (int i = 0; i < optionViews.size(); i++) {
            TextView v = optionViews.get(i);
            if (i < q.options.length) {
                v.setVisibility(View.VISIBLE);
                v.setText(q.options[i]);
                v.setBackground(optionBg(false));
            } else {
                v.setVisibility(View.INVISIBLE);
            }
        }
        speak(q.voice);
        if (!optionViews.isEmpty()) {
            optionViews.get(0).requestFocus();
        }
    }

    private void answer(int index) {
        if (!quizMode || quizIndex >= questions.size()) {
            return;
        }
        if (answering) {
            return;
        }
        answering = true;
        QuizBank.Question q = questions.get(quizIndex);
        if (q.isCorrect(index)) {
            quizCorrect++;
            optionViews.get(index).setBackground(optionBg(true));
            messageText.setText("答对啦");
        } else {
            optionViews.get(index).setBackground(optionBg(true));
            optionViews.get(q.answer).setBackground(optionBg(true));
            messageText.setText("正确答案是 " + q.options[q.answer]);
            speak("正确答案是 " + q.options[q.answer]);
        }
        quizIndex++;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                answering = false;
                showQuestion();
            }
        }, 900L);
    }

    private void finishQuiz() {
        boolean pass = quizCorrect >= QuizBank.PASS_CORRECT && budget.canQuiz();
        PetEngine.Result r = PetEngine.onQuiz(state, quizCorrect, questions.size(), System.currentTimeMillis());
        if (pass) {
            budget.onQuizPass();
            PetStore.save(this, state, budget);
            titleText.setText("答对啦！");
            messageText.setText("再玩 5 分钟，到点记得休息");
            quizPanel.setVisibility(View.GONE);
            petView.play(PetView.ANIM_HAPPY);
            speak("答对啦，再玩五分钟");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 1500L);
        } else {
            budget.onQuizFail();
            PetStore.save(this, state, budget);
            titleText.setText("差一点点");
            messageText.setText("今天先休息，明天我们再试");
            quizPanel.setVisibility(View.GONE);
            petView.play(PetView.ANIM_HAPPY);
            speak("差一点点，明天再试");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 2200L);
        }
        if (r.evolved) {
            petView.play(PetView.ANIM_EVOLVE);
        }
    }

    // ---------- TTS ----------

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        ttsReady = false;
                        return;
                    }
                    try {
                        int lang = tts.setLanguage(Locale.CHINA);
                        ttsReady = lang != TextToSpeech.LANG_NOT_SUPPORTED
                                && lang != TextToSpeech.LANG_MISSING_DATA;
                        tts.setSpeechRate(0.86f);
                        if (pendingSpeech != null) {
                            tts.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null);
                            pendingSpeech = null;
                        }
                    } catch (Throwable t) {
                        ttsReady = false;
                    }
                }
            });
        } catch (Throwable t) {
            tts = null;
        }
    }

    private void speak(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!ttsReady || tts == null) {
            pendingSpeech = text;
            return;
        }
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        } catch (Throwable ignored) {
        }
    }

    // ---------- 字体 / 尺寸 ----------

    private Typeface cjk = Typeface.DEFAULT;

    private void initCjk() {
        try {
            cjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
    }

    private TextView label(String s, float size, int color, boolean bold) {
        if (cjk == Typeface.DEFAULT) {
            initCjk();
        }
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        v.setTextColor(color);
        v.setTypeface(cjk, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
