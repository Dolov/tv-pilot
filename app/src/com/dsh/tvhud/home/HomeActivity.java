package com.dsh.tvhud.home;

import com.dsh.tvhud.hud.HudService;
import com.dsh.tvhud.pet.PetActivity;
import com.dsh.tvhud.pet.PetSettingsActivity;
import com.dsh.tvhud.report.ReportsActivity;
import com.dsh.tvhud.ui.Palette;
import com.dsh.tvhud.ui.Theme;
import com.dsh.tvhud.ui.ThemeStore;
import com.dsh.tvhud.ui.Ui;
import com.dsh.tvhud.usage.MainActivity;
import com.dsh.tvhud.usage.UsageDay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 首页菜单：打开 App 先到这里，再进详情页 / 使用记录。
 *
 * 为什么要菜单：详情页以后会长在「功能列表」里，
 * 所以入口做成一列可扩展的条目，加功能只需要改 {@link #ITEMS}。
 * 版式沿用详情页的安静台账：中性底色 + 小字号 + 发丝线，颜色只留给数据。
 */
public class HomeActivity extends Activity {

    private static final int ACTION_RECORD = 0;
    private static final int ACTION_REPORT = 1;
    private static final int ACTION_PET = 2;
    private static final int ACTION_PET_SETTINGS = 3;

    /** 一个菜单条目。加功能时在 ITEMS 里加一行，action 写 ACTION_* 之类的分发。 */
    private static final class Item {
        final String label;
        final String desc;
        final String tag;
        final int action;

        Item(String label, String desc, int action) {
            this(label, desc, action, "");
        }

        Item(String label, String desc, int action, String tag) {
            this.label = label;
            this.desc = desc;
            this.action = action;
            this.tag = tag;
        }
    }

    private static final Item[] ITEMS = {
            new Item("使用记录", "今日 · 历史 · 左右键切换日期", ACTION_RECORD),
            new Item("使用报告", "周报 · 月报 · 年报，趋势与构成", ACTION_REPORT),
            new Item("我的宠物", "商店 · 换装 · 装饰 · 进化", ACTION_PET),
            new Item("家长设置", "时间 · 时段 · 外观 · 周报与维护",
                    ACTION_PET_SETTINGS, "PIN"),
    };

    private Typeface cjk = Typeface.DEFAULT;
    private Typeface num = Typeface.DEFAULT;
    private Typeface din = Typeface.DEFAULT;

    /** 当前外观；onResume 里比对 revision，变了就重建。 */
    private Palette palette;
    private int themeRev;

    private TextView metaLine;
    private View firstRow;
    private View lastRow;
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd EEE", Locale.CHINA);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.ensureFonts(this);
        Theme theme = ThemeStore.get(this);
        palette = theme.palette;
        themeRev = ThemeStore.rev(this);
        initFonts();
        buildUi();
        startService(new Intent(this, HudService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeRev != ThemeStore.rev(this)) {
            recreate();
            return;
        }
        refreshMeta();
        if (firstRow != null && !firstRow.isFocused()) {
            firstRow.requestFocus();
        }
    }

    // ---------- 字体 / 尺寸 ----------

    private void initFonts() {
        Ui.ensureFonts(this);
        cjk = Ui.cjk();
        din = Ui.din();
        num = Ui.num();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

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
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    private View hairline() {
        View v = new View(this);
        v.setBackgroundColor(palette.appRule);
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // ---------- 界面 ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.appInk);
        root.setPadding(dp(64), dp(36), dp(64), dp(22));

        // 眉标行：tv-pilot …………………………… 日期 · 今日时长
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("tv-pilot", 10f, palette.appText2, din, 0.22f));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        metaLine = label("", 10f, palette.appText3, cjk, 0.04f);
        metaLine.setGravity(Gravity.END);
        header.addView(metaLine);
        root.addView(header);

        // 标题行已移除：品牌与日期足够定位，菜单居中像一张浮着的台账
        LinearLayout.LayoutParams headerRuleLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        headerRuleLp.topMargin = dp(18);
        root.addView(hairline(), headerRuleLp);

        // 菜单整体在剩余空间里垂直居中，像一个浮着的表格
        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams menuLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(menu, menuLp);

        for (int i = 0; i < ITEMS.length; i++) {
            LinearLayout.LayoutParams divLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1));
            divLp.leftMargin = dp(16);
            menu.addView(hairline(), divLp);
            View row = buildRow(i, ITEMS[i]);
            menu.addView(row);
            if (i == 0) {
                firstRow = row;
            }
            if (i == ITEMS.length - 1) {
                lastRow = row;
            }
        }
        LinearLayout.LayoutParams bottomRuleLp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        bottomRuleLp.leftMargin = dp(16);
        menu.addView(hairline(), bottomRuleLp);

        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        // 底部：版本号（右上角对齐，克制到几乎看不见）
        String ver = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = "v" + pi.versionName;
        } catch (Throwable ignored) {
        }
        TextView version = label(ver, 9f, palette.appText3, din, 0.10f);
        version.setGravity(Gravity.END);
        root.addView(version, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private View buildRow(final int index, final Item item) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(20), 0);
        row.setFocusable(true);
        row.setBackgroundColor(0x00000000);
        row.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        // 焦点色脊：不做彩色，保持「颜色只属于数据」的规矩
        final View accent = new View(this);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(0x00000000);
        accentBg.setCornerRadius(dp(1.5f));
        accent.setBackground(accentBg);
        LinearLayout.LayoutParams accentLp = lp(dp(3), dp(22));
        accentLp.rightMargin = dp(14);
        accentLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(accent, accentLp);

        final TextView idx = label(String.format(Locale.US, "%02d", index + 1), 10f,
                palette.appText3, din, 0.06f);
        row.addView(idx, lp(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT));

        final int nameBase = palette.appText2;
        final TextView name = label(item.label, 15f, nameBase, cjk, 0.04f);
        row.addView(name, lp(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView desc = label(item.desc, 10f, palette.appText3, cjk, 0.04f);
        row.addView(desc, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tag = label(item.tag.isEmpty() ? "›" : item.tag,
                item.tag.isEmpty() ? 15f : 9f, palette.appText3, cjk, 0.10f);
        tag.setGravity(Gravity.END);
        row.addView(tag, lp(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackgroundColor(hasFocus ? palette.appRowHover : 0x00000000);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? palette.appSpine : 0x00000000);
                bg.setCornerRadius(dp(1.5f));
                accent.setBackground(bg);
                idx.setTextColor(hasFocus ? palette.appText : palette.appText3);
                name.setTextColor(hasFocus ? palette.appText : nameBase);
                desc.setTextColor(hasFocus ? palette.appText2 : palette.appText3);
                tag.setTextColor(hasFocus ? palette.appText : palette.appText3);
            }
        });

        // 上下键在菜单两端循环：第一条按上 = 最后一条，最后一条按下 = 第一条
        row.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index == 0 && lastRow != null) {
                    lastRow.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && index == ITEMS.length - 1
                        && firstRow != null) {
                    firstRow.requestFocus();
                    return true;
                }
                return false;
            }
        });

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(item);
            }
        });

        return row;
    }

    private void open(Item item) {
        if (item.action == ACTION_REPORT) {
            startActivity(new Intent(this, ReportsActivity.class));
            return;
        }
        if (item.action == ACTION_PET) {
            startActivity(new Intent(this, PetActivity.class));
            return;
        }
        if (item.action == ACTION_PET_SETTINGS) {
            // 家长设置从首页直接进（不再只藏在宠物页长按返回里），PIN 在页内校验
            startActivity(new Intent(this, PetSettingsActivity.class));
            return;
        }
        // 使用记录 = 今日台账 + 左右键按天回看（两页合并成一个入口）
        startActivity(new Intent(this, MainActivity.class));
    }

    /** 顶栏右侧：日期 + 今日已看时长（服务没起来时读本地存档）。 */
    private void refreshMeta() {
        long now = System.currentTimeMillis();
        long todayStart = UsageDay.dayStartOf(now);
        UsageDay day = HudService.peekUsage();
        if (day == null || day.dayStart != todayStart) {
            day = UsageDay.load(this, todayStart);
        }
        long total = 0L;
        if (day != null) {
            for (UsageDay.Seg s : day.segments()) {
                total += Math.max(0L, s.end - s.start);
            }
        }
        metaLine.setText(String.format(Locale.CHINA, "%s · 今日 %s",
                dateFmt.format(new Date(now)), fmtClock(total)));
    }

    private static String fmtClock(long ms) {
        long sec = Math.max(0L, ms) / 1000L;
        if (sec >= 3600L) {
            return String.format(Locale.US, "%d:%02d:%02d", sec / 3600L, (sec % 3600L) / 60L,
                    sec % 60L);
        }
        return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L);
    }
}
