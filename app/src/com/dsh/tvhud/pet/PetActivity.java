package com.dsh.tvhud.pet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 宠物房间：顶部状态 + 中间 2.5D 宠物 + 底部四个 Tab（照顾 / 商店 / 衣柜 / 房间）。
 *
 * 面向 6 岁孩子：遥控器 ←→ 选按钮、OK 确认、返回回首页、长按返回 5 秒进家长设置。
 * 所有面板在 onCreate 里建一次，切换 Tab 只改 setVisibility，不重建 View；
 * 60 秒的衰减只改文字和卡片状态，不动 View 树。
 *
 * 房间里的时间不算「看电视」——HudService 已经排除自己的包名。
 * 不在这里启动 Service：首页/服务本身已经负责，避免 pet 包反向依赖 hud。
 */
public final class PetActivity extends Activity {
    private static final long BACK_LONG_MS = 5000L;
    private static final long DAY_MS = 86400000L;

    // ---------- 底部布局尺寸（单位 dp） ----------
    /** 内容面板固定高度：放得下「分类行 + 卡片行」，且不来回跳。 */
    private static final int PANEL_H = 172;
    private static final int TABS_H = 46;
    private static final int HINT_H = 18;
    /** 面板 + 间距 + Tab + 提示行 = 底部总高，PetView 的高度按它算。 */
    private static final int BOTTOM_BLOCK_H = PANEL_H + 6 + TABS_H + 2 + HINT_H + 4;

    private static final int CARD_W = 132;
    private static final int CARD_H = 90;
    private static final int REFUND_H = 32;
    private static final int CARE_H = 98;

    /**
     * 顶部 HUD 占的高度（一行：等级+阶段 / 成长条 / 星星）。房间从它下面开始画，
     * 这样 PetView 的墙面装饰（海报、吊灯、气球、窗）不会被 HUD 挡住。
     */
    private static final int PET_TOP_DP = 54;

    /**
     * PetView 把地面画在高度 0.74 处、宠物重心在 0.62 处；
     * 可见区域取自身高度的 0.90，让地毯刚好出现在面板上沿，宠物也尽量大。
     */
    private static final float PET_HEIGHT_RATIO = 0.82f;

    private static final int TAB_CARE = 0;
    private static final int TAB_SHOP = 1;
    private static final int TAB_WARDROBE = 2;
    private static final int TAB_ROOM = 3;
    private static final String[] TAB_NAMES = {"照顾", "商店", "衣柜", "房间"};

    private static final int KIND_SHOP = 1;
    private static final int KIND_WARDROBE = 2;
    private static final int KIND_ROOM = 3;

    private static final String[] SHOP_TYPES = {
            Shop.TYPE_FOOD, Shop.TYPE_HAT, Shop.TYPE_CLOTHES, Shop.TYPE_WINGS, Shop.TYPE_DECOR};
    private static final String[] SHOP_NAMES = {"食物", "帽子", "衣服", "翅膀", "装饰"};
    private static final String[] WEAR_TYPES = {
            Shop.TYPE_HAT, Shop.TYPE_CLOTHES, Shop.TYPE_WINGS};
    private static final String[] WEAR_NAMES = {"帽子", "衣服", "翅膀"};

    // ---------- 数据 ----------
    private PetState state;
    private Budget budget;
    private PetView petView;

    // ---------- 顶部状态 ----------
    private TextView levelText;
    private TextView growthLabel;
    private TextView starsText;
    private TextView hintText;
    private LinearLayout growthTrack;
    private View growthFill;
    private View growthRest;

    // ---------- Tab / 面板 ----------
    private final TextView[] tabs = new TextView[4];
    private final FrameLayout[] panels = new FrameLayout[4];
    private int selectedTab = TAB_CARE;

    // ---------- 照顾页 ----------
    private final List<Card> careCards = new ArrayList<Card>();
    private TextView careStatus;

    // ---------- 商店页 ----------
    private final List<Card> shopCards = new ArrayList<Card>();
    private final TextView[] shopChips = new TextView[5];
    private final HorizontalScrollView[] shopScrollers = new HorizontalScrollView[5];
    private int shopGroup = 0;

    // ---------- 衣柜页 ----------
    private final List<Card> wardrobeCards = new ArrayList<Card>();
    private final TextView[] wardrobeChips = new TextView[3];
    private final HorizontalScrollView[] wardrobeScrollers = new HorizontalScrollView[3];
    private final TextView[] wardrobeEmpty = new TextView[3];
    private int wardrobeGroup = 0;
    private TextView branchButton;
    private TextView wardrobeInfo;

    // ---------- 房间页 ----------
    private final List<Card> roomCards = new ArrayList<Card>();
    private TextView roomInfo;

    // ---------- 反馈 ----------
    private TextView tipText;
    private View celebrateLayer;
    private TextView celebrateText;

    private final Handler handler = new Handler();
    private long backDownAt = 0L;

    private final Runnable hideTip = new Runnable() {
        @Override
        public void run() {
            if (tipText != null) {
                tipText.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable hideCelebration = new Runnable() {
        @Override
        public void run() {
            if (celebrateLayer != null) {
                celebrateLayer.setVisibility(View.GONE);
            }
        }
    };

    /** 每 60 秒结算一次衰减；只刷新文字和卡片状态，不重建 View。 */
    private final Runnable decay = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            PetStore.ensureDay(PetActivity.this, state, budget);
            PetEngine.settle(state, now);
            if (PetEngine.maybeStartTeething(state, now)) {
                tip("换牙啦，掉了一颗门牙，过两天就长回来");
            }
            save();
            syncPet();
            refreshAll();
            maybeCelebrate();
            handler.postDelayed(this, 60000L);
        }
    };

    // ================= 生命周期 =================

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (petView != null) {
            petView.setState(state);
        }
        refreshAll();
        if (state.isShell()) {
            tip("它躲进壳里啦，摸摸它就会出来");
        }
        maybeCelebrate();
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

    // ---------- 遥控器：返回 / 长按返回 ----------

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

    // ================= 建界面（只建一次） =================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PetUi.BG);

        // 1) 房间：从顶部 HUD 下面开始，高度按「整屏 - 顶部 - 底部面板」再放大一点，
        //    让 PetView 的墙面装饰露出来、地毯又刚好落在面板上沿。
        petView = new PetView(this);
        petView.setState(state);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int topPx = dp(PET_TOP_DP);
        int avail = Math.max(dp(220), screenH - topPx - dp(BOTTOM_BLOCK_H));
        int petH = Math.min(screenH - topPx, Math.round(avail / PET_HEIGHT_RATIO));
        FrameLayout.LayoutParams petLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, petH, Gravity.TOP);
        petLp.topMargin = topPx;
        root.addView(petView, petLp);

        // 2) 顶部状态
        buildTopBar(root);

        // 3) 底部 Tab + 固定高度面板
        buildBottom(root);

        // 4) 宠物头顶的轻提示气泡：按宠物头顶位置算，避免挡住脸
        float unitPx = Math.min(getResources().getDisplayMetrics().widthPixels, petH * 2.4f) / 2100f;
        int headTopPx = topPx + Math.round(petH * 0.62f - 268f * unitPx);
        buildTip(root, Math.max(dp(90), screenH - headTopPx + dp(10)));

        // 5) 进化/变异/分支/退化的庆祝大字
        buildCelebrate(root);

        setContentView(root);
        selectTab(TAB_CARE, false);
        final FrameLayout focusRoot = root;
        root.post(new Runnable() {
            @Override
            public void run() {
                View first = firstFocusOf(TAB_CARE);
                if (first != null) {
                    first.requestFocus();
                } else if (focusRoot != null) {
                    focusRoot.requestFocus();
                }
            }
        });
    }

    private void buildTopBar(FrameLayout root) {
        // 一行 HUD：等级+阶段 / 成长条 / 星星。矮一点，宠物和房间都能多露出来。
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackground(PetUi.round(this, 0x99101826, 22f));
        top.setPadding(dp(18), dp(8), dp(18), dp(8));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        tlp.leftMargin = dp(16);
        tlp.rightMargin = dp(16);
        tlp.topMargin = dp(10);
        root.addView(top, tlp);

        levelText = PetUi.text(this, "", 24f, PetUi.TEXT, true);
        top.addView(levelText);

        growthLabel = PetUi.text(this, "", 16f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams glp = wrap();
        glp.leftMargin = dp(16);
        top.addView(growthLabel, glp);

        growthTrack = new LinearLayout(this);
        growthTrack.setOrientation(LinearLayout.HORIZONTAL);
        growthTrack.setBackground(PetUi.pill(this, 0x33ffffff));
        growthTrack.setWeightSum(1f);
        LinearLayout.LayoutParams gtlp = new LinearLayout.LayoutParams(0, dp(8), 1f);
        gtlp.leftMargin = dp(12);
        top.addView(growthTrack, gtlp);

        growthFill = new View(this);
        growthFill.setBackground(PetUi.pill(this, PetUi.YELLOW));
        growthRest = new View(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f);
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f);
        growthTrack.addView(growthFill, flp);
        growthTrack.addView(growthRest, rlp2);

        starsText = chip("", PetUi.STAR, 20f);
        LinearLayout.LayoutParams slp = wrap();
        slp.leftMargin = dp(16);
        top.addView(starsText, slp);
    }

    private void buildBottom(FrameLayout root) {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(0, 0, 0, dp(4));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        blp.leftMargin = dp(16);
        blp.rightMargin = dp(16);
        root.addView(bottom, blp);

        // 面板容器：固定高度；四个面板都建好，切换只改 visibility
        FrameLayout panelHost = new FrameLayout(this);
        bottom.addView(panelHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(PANEL_H)));

        panels[TAB_CARE] = buildCarePanel();
        panels[TAB_SHOP] = buildShopPanel();
        panels[TAB_WARDROBE] = buildWardrobePanel();
        panels[TAB_ROOM] = buildRoomPanel();
        for (int i = 0; i < panels.length; i++) {
            panelHost.addView(panels[i], new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        // 四个大 Tab
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TABS_H));
        trlp.topMargin = dp(6);
        bottom.addView(tabRow, trlp);
        for (int i = 0; i < 4; i++) {
            tabs[i] = buildTab(i);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            tabLp.leftMargin = dp(6);
            tabLp.rightMargin = dp(6);
            tabRow.addView(tabs[i], tabLp);
        }

        hintText = PetUi.text(this, "", 13f, PetUi.TEXT_FAINT, false);
        hintText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(HINT_H));
        hlp.topMargin = dp(2);
        bottom.addView(hintText, hlp);
    }

    private void buildTip(FrameLayout root, int bottomMarginPx) {
        tipText = PetUi.text(this, "", 22f, PetUi.TEXT, true);
        tipText.setGravity(Gravity.CENTER);
        tipText.setPadding(dp(22), dp(10), dp(22), dp(10));
        tipText.setMaxWidth(dp(720));
        tipText.setBackground(PetUi.roundStroke(this, 0xf0141b2a, PetUi.FOCUS_SOFT, 20f, 2f));
        tipText.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottomMarginPx;
        root.addView(tipText, lp);
    }

    private void buildCelebrate(FrameLayout root) {
        FrameLayout layer = new FrameLayout(this);
        layer.setVisibility(View.GONE);
        layer.setFocusable(false);
        layer.setClickable(false);
        root.addView(layer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackgroundColor(0x77000000);
        layer.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        celebrateText = PetUi.text(this, "", 34f, PetUi.TEXT_DARK, true);
        celebrateText.setGravity(Gravity.CENTER);
        celebrateText.setPadding(dp(30), dp(16), dp(30), dp(16));
        celebrateText.setMaxWidth(dp(780));
        celebrateText.setBackground(PetUi.roundStroke(this, PetUi.FOCUS, 0xffffffff, 26f, 4f));
        layer.addView(celebrateText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        celebrateLayer = layer;
    }

    // ---------- 四个面板 ----------

    private FrameLayout newPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(PetUi.round(this, 0xee141b2a, 24f));
        panel.setVisibility(View.GONE);
        return panel;
    }

    private LinearLayout newPanelContent(FrameLayout panel) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        panel.addView(content, lp);
        return content;
    }

    private LinearLayout newChipsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        return row;
    }

    private HorizontalScrollView newScroller(int heightDp) {
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        sc.setVerticalScrollBarEnabled(false);
        sc.setFocusable(false);
        sc.setFocusableInTouchMode(false);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return sc;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        return row;
    }

    /** 照顾页：5 个圆润大按钮，吃饭免费；顶部一行显示肚子/心情/牙齿。 */
    private FrameLayout buildCarePanel() {
        FrameLayout panel = newPanel();
        LinearLayout content = newPanelContent(panel);

        careStatus = PetUi.text(this, "", 16f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20));
        content.addView(careStatus, clp);

        HorizontalScrollView sc = newScroller(CARE_H);
        LinearLayout row = newRow();

        careCards.add(makeCareCard("吃饭", "免费", "eat", PetEngine.ACTION_EAT));
        careCards.add(makeCareCard("喝水", "解渴", "drink", PetEngine.ACTION_DRINK));
        careCards.add(makeCareCard("摸摸", "开心", "pet", PetEngine.ACTION_PET));
        careCards.add(makeCareCard("洗澡", "香喷喷", "bath", PetEngine.ACTION_BATH));
        careCards.add(makeCareCard("刷牙", "亮晶晶", "brush", PetEngine.ACTION_BRUSH));
        for (Card c : careCards) {
            row.addView(c.wrapper);
        }
        sc.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(CARE_H));
        slp.topMargin = dp(4);
        content.addView(sc, slp);
        return panel;
    }

    /** 商店页：分类 Tab + 卡片行；食物立即喂，耐用品可买/穿/退。 */
    private FrameLayout buildShopPanel() {
        FrameLayout panel = newPanel();
        LinearLayout content = newPanelContent(panel);

        LinearLayout chips = newChipsRow();
        for (int i = 0; i < SHOP_NAMES.length; i++) {
            final int group = i;
            TextView chip = makeChoiceChip(SHOP_NAMES[i]);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectShopGroup(group);
                }
            });
            chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    paintChoice(v, group == shopGroup, hasFocus);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(30));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            chips.addView(chip, lp);
            shopChips[i] = chip;
        }
        content.addView(chips);

        for (int g = 0; g < SHOP_TYPES.length; g++) {
            HorizontalScrollView sc = newScroller(CARD_H + REFUND_H + 2);
            sc.setVisibility(g == 0 ? View.VISIBLE : View.GONE);
            LinearLayout row = newRow();
            List<Shop.Item> items = Shop.ofType(SHOP_TYPES[g]);
            for (Shop.Item item : items) {
                Card c = makeItemCard(item, KIND_SHOP);
                shopCards.add(c);
                row.addView(c.wrapper);
            }
            sc.addView(row, new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(CARD_H + REFUND_H + 2));
            slp.topMargin = dp(4);
            content.addView(sc, slp);
            shopScrollers[g] = sc;
        }
        return panel;
    }

    /** 衣柜页：按类型看已拥有物品，OK 穿上/脱下；底部显示分支和变异图鉴。 */
    private FrameLayout buildWardrobePanel() {
        FrameLayout panel = newPanel();
        LinearLayout content = newPanelContent(panel);

        LinearLayout chips = newChipsRow();
        for (int i = 0; i < WEAR_NAMES.length; i++) {
            final int group = i;
            TextView chip = makeChoiceChip(WEAR_NAMES[i]);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectWardrobeGroup(group);
                }
            });
            chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    paintChoice(v, group == wardrobeGroup, hasFocus);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(30));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            chips.addView(chip, lp);
            wardrobeChips[i] = chip;
        }

        // 30 星换线入口
        branchButton = makeChoiceChip("换条线 30★");
        branchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchBranch();
            }
        });
        branchButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                paintBranchButton();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(144), dp(30));
        blp.leftMargin = dp(4);
        blp.rightMargin = dp(4);
        chips.addView(branchButton, blp);
        content.addView(chips);

        for (int t = 0; t < WEAR_TYPES.length; t++) {
            final String type = WEAR_TYPES[t];
            HorizontalScrollView sc = newScroller(CARD_H);
            sc.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
            LinearLayout row = newRow();
            TextView empty = PetUi.text(this, "还没有" + WEAR_NAMES[t] + "，去商店挑一个吧",
                    18f, PetUi.TEXT_FAINT, false);
            empty.setVisibility(View.GONE);
            row.addView(empty);
            wardrobeEmpty[t] = empty;
            List<Shop.Item> items = Shop.ofType(type);
            for (Shop.Item item : items) {
                Card c = makeItemCard(item, KIND_WARDROBE);
                wardrobeCards.add(c);
                row.addView(c.wrapper);
            }
            sc.addView(row, new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(CARD_H));
            slp.topMargin = dp(4);
            content.addView(sc, slp);
            wardrobeScrollers[t] = sc;
        }

        wardrobeInfo = PetUi.text(this, "", 16f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20));
        ilp.topMargin = dp(2);
        content.addView(wardrobeInfo, ilp);
        return panel;
    }

    /** 房间页：6 个装饰，已买的可以摆上/收起，最多 3 个。 */
    private FrameLayout buildRoomPanel() {
        FrameLayout panel = newPanel();
        LinearLayout content = newPanelContent(panel);

        roomInfo = PetUi.text(this, "", 17f, PetUi.TEXT_DIM, false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20));
        content.addView(roomInfo, ilp);

        HorizontalScrollView sc = newScroller(CARD_H);
        LinearLayout row = newRow();
        for (Shop.Item item : Shop.ofType(Shop.TYPE_DECOR)) {
            Card c = makeItemCard(item, KIND_ROOM);
            roomCards.add(c);
            row.addView(c.wrapper);
        }
        sc.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(CARD_H));
        slp.topMargin = dp(4);
        content.addView(sc, slp);
        return panel;
    }

    // ---------- Tab ----------

    private TextView buildTab(final int index) {
        final TextView tab = PetUi.text(this, TAB_NAMES[index], 22f, PetUi.TEXT, true);
        tab.setGravity(Gravity.CENTER);
        tab.setFocusable(true);
        tab.setFocusableInTouchMode(false);
        tab.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // 焦点落到 Tab 上就切分区（不整页重建，只改 visibility）
                if (hasFocus) {
                    selectTab(index, false);
                }
                styleTab(index, tab);
            }
        });
        tab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(index, true);
            }
        });
        return tab;
    }

    private void selectTab(int index, boolean moveFocus) {
        if (index < 0 || index > 3 || panels[index] == null) {
            return;
        }
        selectedTab = index;
        for (int i = 0; i < panels.length; i++) {
            if (panels[i] != null) {
                panels[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
        }
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                styleTab(i, tabs[i]);
            }
        }
        if (moveFocus) {
            View first = firstFocusOf(index);
            if (first != null) {
                first.requestFocus();
            }
        }
    }

    private void styleTab(int index, TextView tab) {
        if (tab.isFocused()) {
            tab.setBackground(PetUi.roundStroke(this, PetUi.FOCUS, 0xffffffff, 18f, 3f));
            tab.setTextColor(PetUi.TEXT_DARK);
            return;
        }
        boolean selected = index == selectedTab;
        if (selected) {
            tab.setBackground(PetUi.roundStroke(this, PetUi.FOCUS_SOFT, PetUi.FOCUS, 18f, 2f));
            tab.setTextColor(PetUi.FOCUS);
        } else {
            tab.setBackground(PetUi.round(this, PetUi.CARD, 18f));
            tab.setTextColor(PetUi.TEXT);
        }
    }

    private View firstFocusOf(int tabIndex) {
        switch (tabIndex) {
            case TAB_CARE:
                return careCards.isEmpty() ? null : careCards.get(0).main;
            case TAB_SHOP: {
                for (Card c : shopCards) {
                    if (c.item.type.equals(SHOP_TYPES[shopGroup])) {
                        return c.main;
                    }
                }
                return shopChips[shopGroup];
            }
            case TAB_WARDROBE: {
                for (Card c : wardrobeCards) {
                    if (c.item.type.equals(WEAR_TYPES[wardrobeGroup]) && state.owns(c.item.id)) {
                        return c.main;
                    }
                }
                return wardrobeChips[wardrobeGroup];
            }
            default:
                return roomCards.isEmpty() ? null : roomCards.get(0).main;
        }
    }

    // ---------- 卡片 ----------

    private static final class Card {
        LinearLayout wrapper;
        LinearLayout main;
        PetIconView icon;
        TextView title;
        TextView sub;
        TextView refund;
        Shop.Item item;
        int kind;
        boolean enabled = true;
    }

    private Card makeItemCard(final Shop.Item item, final int kind) {
        final Card c = new Card();
        c.item = item;
        c.kind = kind;

        c.wrapper = new LinearLayout(this);
        c.wrapper.setOrientation(LinearLayout.VERTICAL);
        c.wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean refundable = kind == KIND_SHOP;
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                dp(CARD_W), dp(CARD_H + (refundable ? REFUND_H + 2 : 0)));
        wlp.leftMargin = dp(4);
        wlp.rightMargin = dp(4);
        c.wrapper.setLayoutParams(wlp);

        c.main = new LinearLayout(this);
        c.main.setOrientation(LinearLayout.VERTICAL);
        c.main.setGravity(Gravity.CENTER_HORIZONTAL);
        c.main.setFocusable(true);
        c.main.setFocusableInTouchMode(false);
        c.main.setPadding(dp(4), dp(4), dp(4), dp(4));
        c.main.setBackground(PetUi.round(this, PetUi.CARD, 18f));

        c.icon = new PetIconView(this);
        c.icon.setItem(item.type, item.id);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
        c.main.addView(c.icon, ilp);

        c.title = PetUi.text(this, item.name, 18f, PetUi.TEXT, true);
        c.title.setGravity(Gravity.CENTER);
        c.main.addView(c.title);

        c.sub = PetUi.text(this, "", 18f, PetUi.TEXT_DIM, false);
        c.sub.setGravity(Gravity.CENTER);
        c.main.addView(c.sub);

        c.wrapper.addView(c.main, new LinearLayout.LayoutParams(dp(CARD_W), dp(CARD_H)));

        if (refundable) {
            c.refund = PetUi.text(this, "", 18f, PetUi.TEXT_DIM, true);
            c.refund.setGravity(Gravity.CENTER);
            c.refund.setFocusable(true);
            c.refund.setFocusableInTouchMode(false);
            c.refund.setBackground(PetUi.round(this, 0x2effffff, 12f));
            c.refund.setVisibility(View.GONE);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(CARD_W), dp(REFUND_H));
            rlp.topMargin = dp(2);
            c.wrapper.addView(c.refund, rlp);
            c.refund.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    paintRefund(c);
                }
            });
            c.refund.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refundClick(c);
                }
            });
        }

        c.main.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                paintCard(c);
            }
        });
        c.main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (kind == KIND_SHOP) {
                    shopClick(c);
                } else if (kind == KIND_WARDROBE) {
                    wardrobeClick(c);
                } else if (kind == KIND_ROOM) {
                    roomClick(c);
                }
            }
        });
        return c;
    }

    private Card makeCareCard(String name, String sub, String iconId, final int action) {
        final Card c = new Card();
        c.kind = 0;

        c.wrapper = new LinearLayout(this);
        c.wrapper.setOrientation(LinearLayout.VERTICAL);
        c.wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(CARD_W), dp(CARE_H));
        wlp.leftMargin = dp(4);
        wlp.rightMargin = dp(4);
        c.wrapper.setLayoutParams(wlp);

        c.main = new LinearLayout(this);
        c.main.setOrientation(LinearLayout.VERTICAL);
        c.main.setGravity(Gravity.CENTER_HORIZONTAL);
        c.main.setFocusable(true);
        c.main.setFocusableInTouchMode(false);
        c.main.setPadding(dp(4), dp(4), dp(4), dp(4));
        c.main.setBackground(PetUi.round(this, PetUi.CARD, 20f));

        c.icon = new PetIconView(this);
        c.icon.setItem(PetIconView.TYPE_CARE, iconId);
        c.main.addView(c.icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        c.title = PetUi.text(this, name, 20f, PetUi.TEXT, true);
        c.title.setGravity(Gravity.CENTER);
        c.main.addView(c.title);

        c.sub = PetUi.text(this, sub, 18f, PetUi.TEXT_DIM, false);
        c.sub.setGravity(Gravity.CENTER);
        c.main.addView(c.sub);

        c.wrapper.addView(c.main, new LinearLayout.LayoutParams(dp(CARD_W), dp(CARE_H)));
        c.main.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                paintCard(c);
            }
        });
        c.main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCare(action);
            }
        });
        return c;
    }

    private void paintCard(Card c) {
        if (c == null) {
            return;
        }
        boolean focused = c.main.isFocused();
        if (focused) {
            c.main.setBackground(PetUi.roundStroke(this, PetUi.FOCUS, 0xffffffff, 18f, 3f));
        } else {
            c.main.setBackground(PetUi.round(this, c.enabled ? PetUi.CARD : 0x12ffffff, 18f));
        }
        c.title.setTextColor(focused ? PetUi.TEXT_DARK : (c.enabled ? PetUi.TEXT : PetUi.TEXT_FAINT));
        c.sub.setTextColor(focused ? 0xff6b4a00 : (c.enabled ? PetUi.TEXT_DIM : PetUi.TEXT_FAINT));
        c.main.setAlpha(c.enabled || focused ? 1f : 0.82f);
        if (c.refund != null) {
            paintRefund(c);
        }
    }

    private void paintRefund(Card c) {
        if (c.refund == null || c.refund.getVisibility() != View.VISIBLE) {
            return;
        }
        boolean focused = c.refund.isFocused();
        if (focused) {
            c.refund.setBackground(PetUi.round(this, PetUi.FOCUS, 12f));
            c.refund.setTextColor(PetUi.TEXT_DARK);
        } else {
            c.refund.setBackground(PetUi.round(this, 0x2effffff, 12f));
            c.refund.setTextColor(PetUi.TEXT_DIM);
        }
    }

    private TextView makeChoiceChip(String label) {
        TextView v = PetUi.text(this, label, 18f, PetUi.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setBackground(PetUi.round(this, PetUi.CARD, 16f));
        return v;
    }

    /** 只读小标签：分支、图鉴、星星。 */
    private TextView chip(String label, int color, float sizeDp) {
        TextView v = PetUi.text(this, label, sizeDp, color, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(3), dp(10), dp(3));
        v.setBackground(PetUi.round(this, 0x26ffffff, 12f));
        return v;
    }

    private void paintChoice(View v, boolean selected, boolean focused) {
        if (!(v instanceof TextView)) {
            return;
        }
        TextView tv = (TextView) v;
        if (focused) {
            tv.setBackground(PetUi.roundStroke(this, PetUi.FOCUS, 0xffffffff, 16f, 3f));
            tv.setTextColor(PetUi.TEXT_DARK);
        } else if (selected) {
            tv.setBackground(PetUi.roundStroke(this, PetUi.FOCUS_SOFT, PetUi.FOCUS, 16f, 2f));
            tv.setTextColor(PetUi.FOCUS);
        } else {
            tv.setBackground(PetUi.round(this, PetUi.CARD, 16f));
            tv.setTextColor(PetUi.TEXT);
        }
    }

    // ================= 行为：照顾 =================

    private void doCare(int action) {
        long now = System.currentTimeMillis();
        PetEngine.Result r = PetEngine.care(state, action, now);
        int anim = PetView.ANIM_PET;
        String msg = "它蹭了蹭你的手";
        switch (action) {
            case PetEngine.ACTION_EAT:
                anim = PetView.ANIM_EAT;
                msg = "吃得饱饱的！";
                break;
            case PetEngine.ACTION_DRINK:
                anim = PetView.ANIM_DRINK;
                msg = "咕咚咕咚，喝够啦";
                break;
            case PetEngine.ACTION_BATH:
                anim = PetView.ANIM_BATH;
                msg = "洗得香喷喷";
                break;
            case PetEngine.ACTION_BRUSH:
                anim = PetView.ANIM_BRUSH;
                msg = "牙齿亮晶晶";
                break;
            default:
                break;
        }
        petView.play(anim);
        if (r.evolved) {
            petView.play(PetView.ANIM_EVOLVE);
            String text = "进化成 " + PetState.stageName(r.newStage) + " 啦！";
            if (r.branchLocked) {
                text += "还分叉成" + PetEngine.branchName(r.branch) + "！";
            }
            showCelebration(text);
        } else if (r.branchLocked) {
            petView.play(PetView.ANIM_BRANCH);
            showCelebration("分叉成" + PetEngine.branchName(r.branch) + "啦！");
        } else if (r.mutated) {
            petView.play(PetView.ANIM_MUTATE);
            showCelebration("长出新样子啦：" + PetState.mutationPartName(r.mutationA)
                    + " + " + PetState.mutationPartName(r.mutationB));
        } else if (r.allCareDone) {
            tip("今天的照顾全做完啦，+1 星！");
        } else if (r.leveled) {
            tip("升到 Lv " + r.newLevel + " 啦！");
        } else {
            tip(msg);
        }
        if (PetEngine.maybeStartTeething(state, now)) {
            tip("换牙啦，掉了一颗门牙，过两天就长回来");
        }
        state.celebrateEvolution = false;
        save();
        syncPet();
        refreshAll();
    }

    // ================= 行为：商店 =================

    private void shopClick(Card c) {
        Shop.Item it = c.item;
        if (Shop.TYPE_FOOD.equals(it.type)) {
            buyFood(it);
            return;
        }
        boolean owned = state.owns(it.id);
        if (owned) {
            if (Shop.isEquipped(state, it.id)) {
                tip("已经" + doneVerb(it.type) + "啦");
                return;
            }
            if (Shop.equip(state, it.id)) {
                petView.play(PetView.ANIM_HAPPY);
                tip(doneVerb(it.type) + "啦！");
                save();
                syncPet();
                refreshAll();
            } else {
                tip("Lv " + it.unlockLevel + " 才能" + actionVerb(it.type) + "哦");
            }
            return;
        }
        int check = Shop.checkBuy(state, it.id);
        if (check == Shop.ERR_OWNED) {
            tip("已经拥有啦，去衣柜看看");
            return;
        }
        if (check == Shop.ERR_NOT_ENOUGH) {
            tip("星星还不够，再攒一攒");
            return;
        }
        if (check == Shop.ERR_LEVEL) {
            tip("Lv " + it.unlockLevel + " 才能" + actionVerb(it.type) + "哦");
            return;
        }
        if (check != Shop.OK) {
            return;
        }
        if (Shop.buy(state, it.id) == Shop.OK) {
            petView.play(PetView.ANIM_HAPPY);
            if (Shop.TYPE_DECOR.equals(it.type)) {
                tip("摆好啦，房间更好看啦");
            } else {
                tip("买好啦，去衣柜穿上吧");
            }
            save();
            syncPet();
            refreshAll();
        }
    }

    private void buyFood(Shop.Item it) {
        long now = System.currentTimeMillis();
        int oldStage = state.stage();
        int oldLevel = state.level();
        int oldCare = state.careMask;
        int oldBranch = state.branch;
        int r = Shop.feed(state, it.id, now);
        if (r == Shop.ERR_NOT_ENOUGH) {
            tip("星星还不够，再攒一攒");
            return;
        }
        if (r != Shop.OK) {
            return;
        }
        petView.play(PetView.ANIM_EAT);
        if (state.stage() != oldStage) {
            petView.play(PetView.ANIM_EVOLVE);
            String text = "进化成 " + PetState.stageName(state.stage()) + " 啦！";
            if (state.branch != oldBranch) {
                text += "还分叉成" + state.branchName() + "线！";
            }
            showCelebration(text);
        } else if (state.branch != oldBranch) {
            petView.play(PetView.ANIM_BRANCH);
            showCelebration("分叉成" + state.branchName() + "线啦！");
        } else if (state.level() != oldLevel) {
            tip("升到 Lv " + state.level() + " 啦！");
        } else if (oldCare != PetState.CARE_ALL && state.careMask == PetState.CARE_ALL) {
            tip("今天的照顾全做完啦，+1 星！");
        } else if ("candy".equals(it.slot)) {
            tip("超开心！等会儿记得刷刷牙");
        } else {
            tip("吃好啦，肚子舒服多了");
        }
        if (PetEngine.maybeStartTeething(state, now)) {
            tip("换牙啦，掉了一颗门牙，过两天就长回来");
        }
        state.celebrateEvolution = false;
        save();
        syncPet();
        refreshAll();
    }

    private void refundClick(Card c) {
        if (c == null || c.item == null) {
            return;
        }
        if (Shop.sell(state, c.item.id)) {
            tip("退掉啦，星星回来了");
            save();
            syncPet();
            refreshAll();
            final Card card = c;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (card.main != null && card.main.isShown()) {
                        card.main.requestFocus();
                    }
                }
            });
        }
    }

    // ================= 行为：衣柜 =================

    private void wardrobeClick(Card c) {
        if (!state.owns(c.item.id)) {
            tip("去商店买一个吧");
            return;
        }
        if (Shop.isEquipped(state, c.item.id)) {
            Shop.unequip(state, c.item.type);
            tip("收起来啦");
        } else if (Shop.equip(state, c.item.id)) {
            petView.play(PetView.ANIM_HAPPY);
            tip(doneVerb(c.item.type) + "啦！");
        } else {
            tip("Lv " + c.item.unlockLevel + " 才能" + actionVerb(c.item.type) + "哦");
            return;
        }
        save();
        syncPet();
        refreshAll();
    }

    private void switchBranch() {
        if (state.stage() < PetState.STAGE_ADULT || state.branch == PetState.BRANCH_NONE) {
            tip("到 Lv 24 才能选线哦");
            return;
        }
        if (state.stars < Shop.BRANCH_SWITCH_COST) {
            tip("星星还不够，再攒一攒");
            return;
        }
        int target = state.branch % 3 + 1;
        PetEngine.Result r = PetEngine.switchBranch(state, target, System.currentTimeMillis());
        if (r.branchLocked) {
            petView.play(PetView.ANIM_BRANCH);
            showCelebration("换成" + PetEngine.branchName(state.branch) + "啦！");
            state.celebrateEvolution = false;
            save();
            syncPet();
            refreshAll();
        }
    }

    // ================= 行为：房间 =================

    private void roomClick(Card c) {
        if (!state.owns(c.item.id)) {
            tip("先去商店买一个吧");
            return;
        }
        boolean placed = state.decorHas(c.item.slot);
        if (placed) {
            Shop.placeDecor(state, c.item.slot, false);
            tip("收起来啦");
        } else {
            int before = state.decorList().length;
            Shop.placeDecor(state, c.item.slot, true);
            if (before >= Shop.MAX_DECOR) {
                tip("摆好啦！房间最多 3 样，换下了一样旧的");
            } else {
                tip("摆上啦！");
            }
        }
        save();
        syncPet();
        refreshAll();
    }

    // ================= 刷新 =================

    private void refreshAll() {
        if (petView == null) {
            return;
        }
        refreshTop();
        bindShop();
        bindWardrobe();
        bindRoom();
        refreshTabs();
    }

    private void refreshTop() {
        long now = System.currentTimeMillis();
        levelText.setText(String.format(Locale.CHINA, "Lv %d · %s",
                state.level(), state.displayName()));
        starsText.setText(String.format(Locale.CHINA, "★ %d", state.stars));

        int stage = state.stage();
        int base = PetState.STAGE_EXP[stage];
        int next = stage + 1 < PetState.STAGE_EXP.length ? PetState.STAGE_EXP[stage + 1] : 0;
        float pct;
        if (next > base) {
            growthLabel.setText(String.format(Locale.CHINA, "成长 %d / %d",
                    state.exp - base, next - base));
            pct = (state.exp - base) / (float) (next - base);
        } else {
            growthLabel.setText(String.format(Locale.CHINA, "成长 %d · 完全体", state.exp));
            pct = 1f;
        }
        setGrowth(pct);

        StringBuilder sb = new StringBuilder();
        sb.append(hungerLabel());
        sb.append(" · ").append(thirstLabel());
        if (state.isTeething()) {
            sb.append(" · 换牙中");
        } else {
            sb.append(" · 牙齿").append(state.teethLabel());
        }
        sb.append(" · 心情").append(state.moodLabel());
        if (state.isDegraded(now)) {
            sb.append(" · 它有点累");
        }
        if (state.isShell()) {
            sb.append(" · 躲进壳里啦");
        }
        if (careStatus != null) {
            careStatus.setText(sb.toString());
        }

        if (budget.isFinished()) {
            hintText.setText("← → 选择 · OK 确认 · ↓ 换别的玩 · 今天玩完啦 · 长按返回 家长设置");
        } else {
            hintText.setText(String.format(Locale.CHINA,
                    "← → 选择 · OK 确认 · ↓ 换别的玩 · 第 %d 场 · 每场 %d 分钟 · 长按返回 家长设置",
                    budget.sessionsUsed + 1, budget.sessionMinutes));
        }
    }

    private void setGrowth(float pct) {
        if (growthFill == null || growthRest == null) {
            return;
        }
        if (pct < 0f) {
            pct = 0f;
        }
        if (pct > 1f) {
            pct = 1f;
        }
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) growthFill.getLayoutParams();
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) growthRest.getLayoutParams();
        fp.weight = Math.max(0.0001f, pct);
        rp.weight = Math.max(0.0001f, 1f - pct);
        growthFill.setLayoutParams(fp);
        growthRest.setLayoutParams(rp);
    }

    private void refreshTabs() {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                styleTab(i, tabs[i]);
            }
        }
        for (int i = 0; i < shopChips.length; i++) {
            if (shopChips[i] != null) {
                paintChoice(shopChips[i], i == shopGroup, shopChips[i].isFocused());
            }
        }
        for (int i = 0; i < wardrobeChips.length; i++) {
            if (wardrobeChips[i] != null) {
                paintChoice(wardrobeChips[i], i == wardrobeGroup, wardrobeChips[i].isFocused());
            }
        }
        paintBranchButton();
    }

    private void bindShop() {
        for (Card c : shopCards) {
            Shop.Item it = c.item;
            boolean owned = state.owns(it.id);
            boolean equipped = Shop.isEquipped(state, it.id);
            int check = Shop.checkBuy(state, it.id);
            if (Shop.TYPE_FOOD.equals(it.type)) {
                boolean afford = state.stars >= it.price;
                c.enabled = afford;
                c.sub.setText(afford ? (it.price + "★ 喂它") : (it.price + "★ 星星不够"));
            } else if (owned) {
                c.enabled = true;
                if (equipped) {
                    c.sub.setText(it.price + "★ " + wearVerb(it.type));
                } else {
                    c.sub.setText(it.price + "★ " + actionVerb(it.type) + "上它");
                }
            } else if (check == Shop.ERR_LEVEL) {
                c.enabled = false;
                c.sub.setText("Lv " + it.unlockLevel + " 才能" + actionVerb(it.type));
            } else if (check == Shop.ERR_NOT_ENOUGH) {
                c.enabled = false;
                c.sub.setText(it.price + "★ 星星不够");
            } else {
                c.enabled = true;
                c.sub.setText(it.price + "★ 买");
            }
            if (c.refund != null) {
                boolean canRefund = owned && !equipped && it.durable();
                c.refund.setVisibility(canRefund ? View.VISIBLE : View.GONE);
                if (canRefund) {
                    c.refund.setText("退掉 " + it.price + "★");
                }
            }
            paintCard(c);
        }
    }

    private void bindWardrobe() {
        int[] ownedCount = new int[WEAR_TYPES.length];
        for (Card c : wardrobeCards) {
            boolean owned = state.owns(c.item.id);
            c.wrapper.setVisibility(owned ? View.VISIBLE : View.GONE);
            if (!owned) {
                continue;
            }
            for (int t = 0; t < WEAR_TYPES.length; t++) {
                if (WEAR_TYPES[t].equals(c.item.type)) {
                    ownedCount[t]++;
                }
            }
            c.enabled = true;
            c.sub.setText(Shop.isEquipped(state, c.item.id) ? "穿着中" : "点一下穿上");
            paintCard(c);
        }
        for (int t = 0; t < WEAR_TYPES.length; t++) {
            if (wardrobeEmpty[t] != null) {
                wardrobeEmpty[t].setVisibility(ownedCount[t] == 0 ? View.VISIBLE : View.GONE);
            }
        }

        String branch;
        if (state.branch == PetState.BRANCH_NONE) {
            branch = state.stage() >= PetState.STAGE_ADULT
                    ? "分支：还没定" : "分支：到 Lv 24 才能选线";
        } else {
            branch = "分支：" + state.branchName() + "线";
        }
        int seen = state.mutationSeenCount();
        String mutation;
        if (seen > 0) {
            mutation = "变异图鉴 " + seen + " 种";
            if (state.mutationCount > 0) {
                mutation += " · 现在：" + state.mutationLabel();
            }
        } else {
            mutation = "变异图鉴：还没见过";
        }
        wardrobeInfo.setText(branch + " · " + mutation);
    }

    private void bindRoom() {
        for (Card c : roomCards) {
            boolean owned = state.owns(c.item.id);
            boolean placed = state.decorHas(c.item.slot);
            c.enabled = owned;
            if (!owned) {
                c.sub.setText("还没买");
            } else {
                c.sub.setText(placed ? "摆着" : "点一下摆上");
            }
            paintCard(c);
        }
        if (roomInfo != null) {
            roomInfo.setText(String.format(Locale.CHINA, "已经摆了 %d / %d 样",
                    state.decorList().length, Shop.MAX_DECOR));
        }
    }

    private void paintBranchButton() {
        if (branchButton == null) {
            return;
        }
        boolean canSwitch = state.branch != PetState.BRANCH_NONE
                && state.stage() >= PetState.STAGE_ADULT
                && state.stars >= Shop.BRANCH_SWITCH_COST;
        if (state.branch == PetState.BRANCH_NONE) {
            branchButton.setText("Lv 24 选线");
        } else {
            branchButton.setText("换条线 " + Shop.BRANCH_SWITCH_COST + "★");
        }
        boolean focused = branchButton.isFocused();
        if (focused) {
            branchButton.setBackground(PetUi.roundStroke(this, PetUi.FOCUS, 0xffffffff, 16f, 3f));
            branchButton.setTextColor(PetUi.TEXT_DARK);
        } else if (canSwitch) {
            branchButton.setBackground(PetUi.roundStroke(this, PetUi.FOCUS_SOFT, PetUi.FOCUS, 16f, 2f));
            branchButton.setTextColor(PetUi.FOCUS);
        } else {
            branchButton.setBackground(PetUi.round(this, 0x12ffffff, 16f));
            branchButton.setTextColor(PetUi.TEXT_FAINT);
        }
        branchButton.setAlpha(canSwitch || focused ? 1f : 0.82f);
    }

    // ================= 庆祝 / 提示 / 存档 =================

    /** 进房间时的庆祝：只在 state.celebrateEvolution 为 true 时播一次，然后清零存档。 */
    private void maybeCelebrate() {
        if (state == null || !state.celebrateEvolution) {
            return;
        }
        long now = System.currentTimeMillis();
        int anim;
        String text;
        if (state.isDegraded(now)) {
            anim = PetView.ANIM_DEGRADE;
            text = "它有点累了，休息一下就好";
        } else if (state.mutationCount > 0 && state.lastMutationCheck > 0
                && now - state.lastMutationCheck < DAY_MS) {
            anim = PetView.ANIM_MUTATE;
            text = "长出新样子啦：" + state.mutationLabel();
        } else if (state.branch != PetState.BRANCH_NONE
                && state.stage() >= PetState.STAGE_ADULT) {
            anim = PetView.ANIM_BRANCH;
            text = "分叉成" + state.branchName() + "啦！";
        } else {
            anim = PetView.ANIM_EVOLVE;
            text = "进化成 " + state.displayName() + " 啦！";
        }
        petView.play(anim);
        showCelebration(text);
        state.celebrateEvolution = false;
        save();
    }

    private void showCelebration(String msg) {
        if (celebrateLayer == null || celebrateText == null) {
            return;
        }
        celebrateText.setText(msg);
        celebrateLayer.setVisibility(View.VISIBLE);
        if (tipText != null) {
            tipText.setVisibility(View.GONE);
        }
        handler.removeCallbacks(hideCelebration);
        handler.postDelayed(hideCelebration, 3200L);
    }

    private void tip(String msg) {
        if (tipText == null) {
            return;
        }
        tipText.setText(msg);
        tipText.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideTip);
        handler.postDelayed(hideTip, 2600L);
    }

    private void save() {
        if (state != null && budget != null) {
            PetStore.save(this, state, budget);
        }
    }

    private void syncPet() {
        if (petView != null && state != null) {
            petView.setState(state);
        }
    }

    // ================= 小工具 =================

    private static String actionVerb(String type) {
        if (Shop.TYPE_HAT.equals(type)) {
            return "戴";
        }
        if (Shop.TYPE_CLOTHES.equals(type)) {
            return "穿";
        }
        if (Shop.TYPE_WINGS.equals(type)) {
            return "背";
        }
        if (Shop.TYPE_DECOR.equals(type)) {
            return "摆";
        }
        return "用";
    }

    private static String doneVerb(String type) {
        if (Shop.TYPE_HAT.equals(type)) {
            return "戴好";
        }
        if (Shop.TYPE_CLOTHES.equals(type)) {
            return "穿好";
        }
        if (Shop.TYPE_WINGS.equals(type)) {
            return "背好";
        }
        if (Shop.TYPE_DECOR.equals(type)) {
            return "摆好";
        }
        return "弄好";
    }

    /** 已经穿着时的状态词。 */
    private static String wearVerb(String type) {
        if (Shop.TYPE_HAT.equals(type)) {
            return "戴着";
        }
        if (Shop.TYPE_CLOTHES.equals(type)) {
            return "穿着";
        }
        if (Shop.TYPE_WINGS.equals(type)) {
            return "背着";
        }
        if (Shop.TYPE_DECOR.equals(type)) {
            return "摆着";
        }
        return "用着";
    }

    private String hungerLabel() {
        if (state.hunger >= 70) {
            return "肚子饱饱";
        }
        if (state.hunger >= 40) {
            return "肚子还行";
        }
        return "有点饿";
    }

    private String thirstLabel() {
        // 需求值口径见 PetState：口渴掉得比饿快，这里只说孩子听得懂的话
        if (state.thirst >= 70) {
            return "水喝够了";
        }
        if (state.thirst >= 40) {
            return "想喝点水";
        }
        return "口渴啦";
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void selectShopGroup(int group) {
        if (group < 0 || group >= SHOP_TYPES.length) {
            return;
        }
        shopGroup = group;
        for (int i = 0; i < shopScrollers.length; i++) {
            if (shopScrollers[i] != null) {
                shopScrollers[i].setVisibility(i == group ? View.VISIBLE : View.GONE);
            }
        }
        for (int i = 0; i < shopChips.length; i++) {
            if (shopChips[i] != null) {
                paintChoice(shopChips[i], i == group, shopChips[i].isFocused());
            }
        }
    }

    private void selectWardrobeGroup(int group) {
        if (group < 0 || group >= WEAR_TYPES.length) {
            return;
        }
        wardrobeGroup = group;
        for (int i = 0; i < wardrobeScrollers.length; i++) {
            if (wardrobeScrollers[i] != null) {
                wardrobeScrollers[i].setVisibility(i == group ? View.VISIBLE : View.GONE);
            }
        }
        for (int i = 0; i < wardrobeChips.length; i++) {
            if (wardrobeChips[i] != null) {
                paintChoice(wardrobeChips[i], i == group, wardrobeChips[i].isFocused());
            }
        }
    }

    private int dp(float v) {
        return PetUi.dp(this, v);
    }
}
