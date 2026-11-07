package com.dsh.tvhud.pet;

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
 * 两种闸门：
 * - GATE_NORMAL：这一场看满了。两个大按钮，答题是减速带不是收费站；
 * - GATE_WINDOW：现在不在家长允许的时段。只有「好，去休息」一个按钮，
 *   不走答题、不计场次、不给星星（PetEngine.onQuietRest），60 秒后自动休息。
 *
 * 6 岁体验：题干 34dp、选项 26dp 大卡片；默认焦点在中间；题目和选项都读出来；
 * 答错先给一次机会，第二次才揭晓答案；60 秒无操作只重复读题，不判错、不倒计时；
 * 没有失败音效、没有羞辱文案。
 *
 * 返回键无效（避免误触退出），长按返回 5 秒进家长设置。
 */
public final class PetGateActivity extends Activity {
    private static final long BACK_LONG_MS = 5000L;
    private static final int SECONDS = 60;
    private static final long IDLE_MS = 60000L;
    private static final long ANSWER_NEXT_MS = 1000L;
    private static final long ANSWER_REVEAL_MS = 1800L;
    private static final long RESULT_FINISH_MS = 3000L;

    private static final int OPTION_NORMAL = 0;
    private static final int OPTION_WRONG = 1;
    private static final int OPTION_CORRECT = 2;

    private static final int DOT_PENDING = 0;
    private static final int DOT_CORRECT = 1;
    private static final int DOT_WRONG = 2;

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
    private LinearLayout card;
    private TextView celebrationText;
    private TextView titleText;
    private TextView messageText;
    private TextView countdownText;
    private TextView restButton;
    private TextView quizButton;
    private LinearLayout choiceRow;
    private LinearLayout quizPanel;
    private LinearLayout dotsRow;
    private TextView questionText;
    private TextView feedbackText;
    private final List<TextView> optionViews = new ArrayList<TextView>();
    private final List<View> dotViews = new ArrayList<View>();

    private List<QuizBank.Question> questions = new ArrayList<QuizBank.Question>();
    private final int[] optionState = new int[4];
    private final int[] dotState = new int[3];
    private int quizIndex;
    private int quizCorrect;
    private int wrongOption = -1;
    private boolean retryAvailable;
    private boolean quizMode;
    private boolean finishedChoice;
    private boolean answering;
    /** 页面初始化/切题时批量设置焦点，不要把每个焦点变化都读一遍。 */
    private boolean suppressFocusSpeech;
    /** 时段外被拦下：只给「好，去休息」。 */
    private boolean windowGate;
    private int secondsLeft = SECONDS;
    private long backDownAt;
    private boolean destroyed;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    private final Handler handler = new Handler();
    private final Runnable finishRunnable = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };
    private final Runnable hideCelebrationRunnable = new Runnable() {
        @Override
        public void run() {
            if (celebrationText != null) {
                celebrationText.setVisibility(View.GONE);
            }
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (finishedChoice) {
                return;
            }
            if (!sVisible) {
                // 被设置页/桌面挡住时不偷偷倒计时，回到页面再接着算
                handler.postDelayed(this, 500L);
                return;
            }
            // 家长在设置页发了「今天免限」：这页拦得就没意义了，直接放孩子回去
            if (PetStore.hasFreePass(PetGateActivity.this)) {
                releaseForFreePass();
                return;
            }
            secondsLeft--;
            if (secondsLeft <= 0) {
                doRest(true);
                return;
            }
            updateCountdown();
            handler.postDelayed(this, 1000L);
        }
    };
    /** 60 秒没操作：只重复读题，不判错、不自动进屋。 */
    private final Runnable idleRepeat = new Runnable() {
        @Override
        public void run() {
            if (finishedChoice || !quizMode || quizIndex >= questions.size()) {
                return;
            }
            if (!sVisible || answering) {
                handler.postDelayed(this, 1000L);
                return;
            }
            messageText.setText("慢慢想，不着急");
            feedbackText.setText("慢慢想，不着急");
            speakQuestion(questions.get(quizIndex));
            handler.postDelayed(this, IDLE_MS);
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
        windowGate = budget.gateReason == Budget.GATE_WINDOW;
        buildUi();
        applyMode();
        updateCountdown();
        initTts();
        speakIntro();
        celebrateIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sVisible = true;
        sSettingsOpen = false;
        if (finishedChoice) {
            return;
        }
        if (quizMode) {
            startIdle();
        } else {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 1000L);
        }
    }

    @Override
    protected void onPause() {
        sVisible = false;
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignored) {
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sVisible = false;
        sSettingsOpen = false;
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
        ttsReady = false;
        pendingSpeech = null;
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
        root.setBackgroundColor(PetUi.BG);

        petView = new PetView(this);
        petView.setState(state);
        petView.setTranslationY(-dp(130));
        root.addView(petView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(36), dp(22), dp(36), dp(24));
        card.setBackground(PetUi.round(this, PetUi.PANEL, 28f));
        int cardWidth = Math.min(dp(820),
                getResources().getDisplayMetrics().widthPixels - dp(48));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardLp.bottomMargin = dp(32);
        root.addView(card, cardLp);

        celebrationText = label("", 26f, PetUi.YELLOW, true);
        celebrationText.setVisibility(View.GONE);
        card.addView(celebrationText);

        titleText = label("这一场结束啦", 34f, PetUi.TEXT, true);
        card.addView(titleText);

        messageText = label("让眼睛和电视都休息一下", 24f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams msgLp = wrapLp();
        msgLp.topMargin = dp(10);
        card.addView(messageText, msgLp);

        countdownText = label("", 24f, PetUi.TEXT_FAINT, false);
        LinearLayout.LayoutParams cdLp = wrapLp();
        cdLp.topMargin = dp(8);
        card.addView(countdownText, cdLp);

        choiceRow = new LinearLayout(this);
        choiceRow.setOrientation(LinearLayout.HORIZONTAL);
        choiceRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(22);
        card.addView(choiceRow, rowLp);

        restButton = choiceButton("好，休息一下");
        restButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRest(false);
            }
        });
        choiceRow.addView(restButton);

        quizButton = choiceButton("答题再玩 5 分钟");
        quizButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQuiz();
            }
        });
        choiceRow.addView(quizButton);

        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);
        quizPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        quizPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams qpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qpLp.topMargin = dp(12);
        card.addView(quizPanel, qpLp);

        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        quizPanel.addView(dotsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            dotLp.leftMargin = dp(6);
            dotLp.rightMargin = dp(6);
            dot.setLayoutParams(dotLp);
            dot.setBackground(PetUi.circle(this, 0x33ffffff));
            dot.setVisibility(View.GONE);
            dotViews.add(dot);
            dotsRow.addView(dot);
        }

        questionText = label("", 34f, PetUi.TEXT, true);
        questionText.setGravity(Gravity.CENTER);
        questionText.setMaxLines(3);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qLp.topMargin = dp(10);
        quizPanel.addView(questionText, qLp);

        feedbackText = label("想一想，别着急", 24f, PetUi.TEXT_DIM, true);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fLp.topMargin = dp(8);
        quizPanel.addView(feedbackText, fLp);

        LinearLayout opts1 = optionRow();
        LinearLayout opts2 = optionRow();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView opt = optionCard();
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
        restButton.requestFocus();
    }

    private LinearLayout optionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        quizPanel.addView(row, lp);
        return row;
    }

    private TextView optionCard() {
        final TextView tv = label("", 26f, PetUi.TEXT_DARK, true);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(84));
        tv.setPadding(dp(14), dp(18), dp(14), dp(18));
        tv.setBackground(optionBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                int index = optionViews.indexOf(v);
                paintOption(index);
                if (hasFocus && !suppressFocusSpeech && quizMode && !finishedChoice) {
                    speak(((TextView) v).getText().toString());
                    resetIdle();
                }
            }
        });
        return tv;
    }

    private TextView choiceButton(String text) {
        TextView tv = label(text, 26f, PetUi.TEXT_DARK, true);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(dp(240));
        tv.setMinHeight(dp(76));
        tv.setPadding(dp(26), dp(18), dp(26), dp(18));
        tv.setBackground(optionBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        tv.setLayoutParams(lp);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(optionBg(hasFocus));
            }
        });
        return tv;
    }

    /** 暖黄焦点 / 浅色未选中；答错红、答对绿在 {@link #paintOption(int)} 里覆盖。 */
    private GradientDrawable optionBg(boolean focus) {
        return PetUi.round(this, focus ? PetUi.FOCUS : 0xffe8eef5, 20f);
    }

    private void paintOption(int index) {
        if (index < 0 || index >= optionViews.size()) {
            return;
        }
        TextView v = optionViews.get(index);
        v.setTextColor(PetUi.TEXT_DARK);
        if (optionState[index] == OPTION_WRONG) {
            v.setBackground(PetUi.round(this, PetUi.RED, 20f));
        } else if (optionState[index] == OPTION_CORRECT) {
            v.setBackground(PetUi.round(this, PetUi.GREEN, 20f));
        } else {
            v.setBackground(optionBg(v.hasFocus()));
        }
    }

    private void updateCountdown() {
        countdownText.setText(String.format(Locale.CHINA, "%d 秒后自动休息", secondsLeft));
    }

    private void applyMode() {
        if (windowGate) {
            titleText.setText("现在是休息时间");
            messageText.setText("到点再看吧，宠物在等你");
            restButton.setText("好，去休息");
            quizButton.setVisibility(View.GONE);
            return;
        }
        titleText.setText("这一场结束啦");
        messageText.setText("让眼睛和电视都休息一下");
        restButton.setText("好，休息一下");
        int left = Math.max(0, budget.extensionsPerDay - budget.extensionsToday);
        boolean canQuiz = budget.canQuiz() && left > 0;
        quizButton.setText(String.format(Locale.CHINA, "今天还能答题 %d 次", left));
        if (canQuiz) {
            quizButton.setVisibility(View.VISIBLE);
        } else {
            quizButton.setVisibility(View.GONE);
            if (budget.sessionsUsed >= budget.maxSessions) {
                messageText.setText("今天玩过啦，明天见");
            }
        }
    }

    // ---------- 选择 ----------

    private void doRest(boolean auto) {
        if (finishedChoice) {
            return;
        }
        if (sSettingsOpen) {
            // 家长正在设置页里操作，先别替孩子做决定
            return;
        }
        finishedChoice = true;
        handler.removeCallbacksAndMessages(null);
        long now = System.currentTimeMillis();
        budget.onRest(now);
        // 时段外只是「现在不是看电视的时间」，不该奖励星星/成长
        PetEngine.Result r = windowGate
                ? PetEngine.onQuietRest(state, now)
                : PetEngine.onSessionRest(state, auto, now);
        boolean celebrated = playResultAnim(r);
        if (celebrated) {
            state.celebrateEvolution = false;
        } else {
            petView.play(PetView.ANIM_HAPPY);
        }
        PetStore.save(this, state, budget);
        choiceRow.setVisibility(View.GONE);
        quizPanel.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        celebrationText.setVisibility(View.GONE);
        petView.setVisibility(View.VISIBLE);
        if (windowGate) {
            titleText.setText("好，去休息");
            messageText.setText(auto ? "电视先休息啦，到点再来" : "到点再来，宠物在等你");
            speak("好，去休息");
        } else {
            titleText.setText("晚安");
            messageText.setText(auto ? "电视自动休息啦，明天见" : "好，休息一下");
            speak("晚安，明天见");
        }
        handler.postDelayed(finishRunnable, auto ? 1200L : 900L);
    }

    /**
     * 家长发了「今天免限」：直接放孩子回去看，不计场次、不罚星星。
     *
     * 和 {@link #doRest} 的区别：doRest 是「休息」——会计 1 场并可能给成长值；
     * 这里只是「这次拦截作废」，所以既不计数也不奖励，只把闸门关掉然后退出这一页。
     */
    private void releaseForFreePass() {
        if (finishedChoice) {
            return;
        }
        finishedChoice = true;
        handler.removeCallbacksAndMessages(null);
        budget.gateOpen = false;
        budget.gateReason = Budget.GATE_NORMAL;
        budget.sessionMs = 0L;
        budget.extensionMs = 0L;
        PetStore.saveBudget(this, budget);
        choiceRow.setVisibility(View.GONE);
        quizPanel.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        celebrationText.setVisibility(View.GONE);
        petView.setVisibility(View.VISIBLE);
        titleText.setText("今天可以继续看");
        messageText.setText("家长今天给你放开了");
        petView.play(PetView.ANIM_HAPPY);
        speak("今天可以继续看");
        handler.postDelayed(finishRunnable, 900L);
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
        handler.removeCallbacks(tick);
        handler.removeCallbacks(idleRepeat);
        handler.removeCallbacks(hideCelebrationRunnable);
        petView.setVisibility(View.GONE);
        celebrationText.setVisibility(View.GONE);
        quizIndex = 0;
        quizCorrect = 0;
        wrongOption = -1;
        retryAvailable = false;
        answering = false;
        for (int i = 0; i < dotState.length; i++) {
            dotState[i] = DOT_PENDING;
        }
        for (int i = 0; i < optionState.length; i++) {
            optionState[i] = OPTION_NORMAL;
        }
        choiceRow.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        quizPanel.setVisibility(View.VISIBLE);
        showQuestion();
    }

    private void showQuestion() {
        if (quizIndex >= questions.size()) {
            finishQuiz();
            return;
        }
        answering = false;
        retryAvailable = false;
        wrongOption = -1;
        QuizBank.Question q = questions.get(quizIndex);
        titleText.setText(String.format(Locale.CHINA, "第 %d / %d 题", quizIndex + 1, questions.size()));
        if (windowGate) {
            messageText.setText("现在是休息时间");
        } else {
            messageText.setText("答对 2 题，再玩 5 分钟");
        }
        messageText.setVisibility(View.VISIBLE);
        feedbackText.setTextColor(PetUi.TEXT_DIM);
        feedbackText.setText("想一想，别着急");
        questionText.setText(q.prompt);
        for (int i = 0; i < optionViews.size(); i++) {
            TextView v = optionViews.get(i);
            optionState[i] = OPTION_NORMAL;
            if (i < q.options.length) {
                v.setVisibility(View.VISIBLE);
                v.setText(q.options[i]);
                v.setFocusable(true);
                v.setFocusableInTouchMode(false);
                paintOption(i);
            } else {
                v.setVisibility(View.INVISIBLE);
                v.setFocusable(false);
            }
        }
        updateDots();
        int focusIndex = q.options.length >= 2 ? 1 : 0;
        suppressFocusSpeech = true;
        optionViews.get(focusIndex).requestFocus();
        suppressFocusSpeech = false;
        speakQuestion(q);
        startIdle();
    }

    private void answer(int index) {
        if (!quizMode || answering || finishedChoice || quizIndex >= questions.size()) {
            return;
        }
        if (index < 0 || index >= optionViews.size()) {
            return;
        }
        if (index == wrongOption) {
            // 已经选错过的选项不再让他重复点，避免一次误触直接算第二次错
            return;
        }
        QuizBank.Question q = questions.get(quizIndex);
        handler.removeCallbacks(idleRepeat);
        if (q.isCorrect(index)) {
            optionState[index] = OPTION_CORRECT;
            paintOption(index);
            quizCorrect++;
            dotState[quizIndex] = DOT_CORRECT;
            updateDots();
            feedbackText.setTextColor(PetUi.GREEN);
            feedbackText.setText("答对啦");
            speak("答对啦");
            answering = true;
            handler.postDelayed(nextQuestionRunnable, ANSWER_NEXT_MS);
            return;
        }
        if (!retryAvailable) {
            // 第一次答错：只提示再想想，不揭晓答案，也不记错
            retryAvailable = true;
            wrongOption = index;
            optionState[index] = OPTION_WRONG;
            optionViews.get(index).setFocusable(false);
            paintOption(index);
            feedbackText.setTextColor(PetUi.ORANGE);
            feedbackText.setText("再想想，还有一次机会");
            speak("再想想，还有一次机会");
            focusOtherOption(index);
            resetIdle();
            return;
        }
        // 第二次还错：温和揭晓答案，进下一题（本题记错）
        optionState[index] = OPTION_WRONG;
        optionState[q.answer] = OPTION_CORRECT;
        paintOption(index);
        paintOption(q.answer);
        dotState[quizIndex] = DOT_WRONG;
        updateDots();
        feedbackText.setTextColor(PetUi.TEXT_DIM);
        feedbackText.setText("没关系，答案是 " + q.options[q.answer]);
        speak("没关系，答案是 " + q.options[q.answer] + "，我们看下一题");
        answering = true;
        handler.postDelayed(nextQuestionRunnable, ANSWER_REVEAL_MS);
    }

    private final Runnable nextQuestionRunnable = new Runnable() {
        @Override
        public void run() {
            answering = false;
            quizIndex++;
            showQuestion();
        }
    };

    private void focusOtherOption(int wrong) {
        int n = optionViews.size();
        for (int step = 1; step < n; step++) {
            int i = (wrong + step) % n;
            if (optionViews.get(i).getVisibility() == View.VISIBLE) {
                suppressFocusSpeech = true;
                optionViews.get(i).requestFocus();
                suppressFocusSpeech = false;
                return;
            }
        }
    }

    private void updateDots() {
        int count = quizMode ? questions.size() : 0;
        for (int i = 0; i < dotViews.size(); i++) {
            View dot = dotViews.get(i);
            if (i >= count) {
                dot.setVisibility(View.GONE);
                continue;
            }
            dot.setVisibility(View.VISIBLE);
            int color;
            if (dotState[i] == DOT_CORRECT) {
                color = PetUi.GREEN;
            } else if (dotState[i] == DOT_WRONG) {
                color = PetUi.RED;
            } else if (i == quizIndex) {
                color = PetUi.FOCUS;
            } else {
                color = 0x33ffffff;
            }
            dot.setBackground(PetUi.circle(this, color));
        }
    }

    private void finishQuiz() {
        if (finishedChoice) {
            return;
        }
        finishedChoice = true;
        handler.removeCallbacksAndMessages(null);
        boolean pass = questions.size() > 0 && quizCorrect * 2 >= questions.size();
        PetEngine.Result r = PetEngine.onQuiz(state, quizCorrect, questions.size(),
                System.currentTimeMillis());
        if (pass) {
            budget.onQuizPass();
        } else {
            budget.onQuizFail();
        }
        boolean celebrated = playResultAnim(r);
        if (celebrated) {
            state.celebrateEvolution = false;
        }
        PetStore.save(this, state, budget);
        quizPanel.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        petView.setVisibility(View.VISIBLE);
        if (!celebrated) {
            petView.play(pass ? PetView.ANIM_HAPPY : PetView.ANIM_IDLE);
        }
        if (pass) {
            titleText.setText("答对啦，再玩 5 分钟");
            messageText.setText("到点记得休息哦");
            speak("答对啦，再玩五分钟");
        } else {
            titleText.setText("差一点点，明天我们再试");
            messageText.setText("宠物会等你的");
            speak("差一点点，明天我们再试");
        }
        handler.postDelayed(finishRunnable, RESULT_FINISH_MS);
    }

    /** 按结算结果播外观变化动画；返回 true 表示播了需要「庆祝」的动画。 */
    private boolean playResultAnim(PetEngine.Result r) {
        if (r == null) {
            return false;
        }
        if (r.mutated) {
            petView.play(PetView.ANIM_MUTATE);
            return true;
        }
        if (r.branchLocked) {
            petView.play(PetView.ANIM_BRANCH);
            return true;
        }
        if (r.degraded) {
            petView.play(PetView.ANIM_DEGRADE);
            return true;
        }
        if (r.evolved) {
            petView.play(PetView.ANIM_EVOLVE);
            return true;
        }
        return false;
    }

    // ---------- 庆祝 ----------

    /**
     * 进页面时如果有待庆祝的进化/变异/分支/退化，播一次对应动画并清掉标记。
     * 只放一段小字提示，不占用 60 秒倒计时，也不挡两个大按钮。
     */
    private void celebrateIfNeeded() {
        if (state == null || !state.celebrateEvolution) {
            return;
        }
        long now = System.currentTimeMillis();
        int anim;
        String text;
        if (state.isDegraded(now)) {
            anim = PetView.ANIM_DEGRADE;
            text = "宠物有点累了，休息好就回来";
        } else if (state.mutationCount > 0) {
            anim = PetView.ANIM_MUTATE;
            text = "宠物变异啦！" + state.mutationLabel();
        } else if (state.branch != PetState.BRANCH_NONE
                && state.stage() >= PetState.STAGE_ADULT) {
            anim = PetView.ANIM_BRANCH;
            text = "分叉成" + state.branchName() + "啦！";
        } else if (state.stage() > PetState.STAGE_EGG) {
            anim = PetView.ANIM_EVOLVE;
            text = "进化成 " + PetState.stageName(state.stage()) + " 啦！";
        } else {
            anim = PetView.ANIM_HAPPY;
            text = "宠物长大一点点啦";
        }
        state.celebrateEvolution = false;
        // 先落盘再播，进程被杀也不会每次进来都庆祝一遍
        PetStore.saveState(this, state);
        if (petView != null) {
            petView.play(anim);
        }
        celebrationText.setText(text);
        celebrationText.setVisibility(View.VISIBLE);
        handler.postDelayed(hideCelebrationRunnable, 6000L);
    }

    // ---------- TTS ----------

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (destroyed) {
                        return;
                    }
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
                            String pending = pendingSpeech;
                            pendingSpeech = null;
                            tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null);
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
        if (text == null || text.isEmpty() || destroyed) {
            return;
        }
        if (!ttsReady || tts == null) {
            // TTS 还没初始化好：只记最后一次，初始化完再读
            pendingSpeech = text;
            return;
        }
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        } catch (Throwable ignored) {
        }
    }

    private void speakIntro() {
        StringBuilder sb = new StringBuilder();
        sb.append(titleText.getText());
        CharSequence msg = messageText.getText();
        if (msg != null && msg.length() > 0) {
            sb.append('。').append(msg);
        }
        speak(sb.toString());
    }

    private void speakQuestion(QuizBank.Question q) {
        if (q == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (q.voice != null && q.voice.length() > 0) {
            sb.append(q.voice);
        } else {
            sb.append(q.prompt);
        }
        sb.append("。选项：");
        for (int i = 0; i < q.options.length; i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(q.options[i]);
        }
        speak(sb.toString());
    }

    private void startIdle() {
        handler.removeCallbacks(idleRepeat);
        if (quizMode && !finishedChoice) {
            handler.postDelayed(idleRepeat, IDLE_MS);
        }
    }

    private void resetIdle() {
        handler.removeCallbacks(idleRepeat);
        if (quizMode && !finishedChoice && !answering) {
            handler.postDelayed(idleRepeat, IDLE_MS);
        }
    }

    // ---------- 字体 / 尺寸 ----------

    private TextView label(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        v.setTextColor(color);
        v.setTypeface(PetUi.cjk(this), bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
