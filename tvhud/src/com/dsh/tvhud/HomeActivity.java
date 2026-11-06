package com.dsh.tvhud;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 首页菜单：打开 App 先到这里，再进详情页 / 使用记录。
 *
 * 为什么要菜单：详情页以后会长在「功能列表」里（AI 语音、设置…），
 * 所以入口做成一列可扩展的条目，加功能只需要改 {@link #ITEMS}。
 * 版式沿用详情页的安静台账：中性底色 + 小字号 + 发丝线，颜色只留给数据。
 */
public class HomeActivity extends Activity {
    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";

    private static final int COL_BG = 0xff0e1114;
    private static final int COL_PRIMARY = 0xffe9eef3;
    private static final int COL_SECOND = 0xff9aa7b2;
    private static final int COL_TERTIARY = 0xff5f6b76;
    private static final int COL_HAIRLINE = 0x288a97a4;
    private static final int COL_ROW_HOVER = 0x1fffffff;

    private static final int ACTION_TODAY = 0;
    private static final int ACTION_HISTORY = 1;
    private static final int ACTION_SOON = 2;

    /** 一个菜单条目。加功能时在 ITEMS 里加一行，action 写 ACTION_TODAY 之类的分发。 */
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

        boolean enabled() {
            return action != ACTION_SOON;
        }
    }

    private static final Item[] ITEMS = {
            new Item("今日使用", "时间轴 · 分段明细 · 看了什么", ACTION_TODAY),
            new Item("使用记录", "按天回看，保留最近 365 天", ACTION_HISTORY),
            new Item("AI 语音助手", "用手机说话，问电视看了什么", ACTION_SOON, "即将上线"),
            new Item("设置", "ADB 守护 · 屏保角标", ACTION_SOON, "即将上线"),
    };

    private Typeface cjk = Typeface.DEFAULT;
    private Typeface num = Typeface.DEFAULT;
    private Typeface din = Typeface.DEFAULT;

    private TextView metaLine;
    private View firstRow;
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd EEE", Locale.CHINA);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        initFonts();
        buildUi();
        startService(new Intent(this, HudService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMeta();
        if (firstRow != null && !firstRow.isFocused()) {
            firstRow.requestFocus();
        }
    }

    // ---------- 字体 / 尺寸 ----------

    private void initFonts() {
        try {
            cjk = Typeface.createFromFile(CJK_FONT);
        } catch (Throwable ignored) {
        }
        try {
            num = Typeface.createFromAsset(getAssets(), "MIUI_EX_Light.ttf");
        } catch (Throwable ignored) {
        }
        try {
            din = Typeface.createFromAsset(getAssets(), "DINPro_Medium.otf");
        } catch (Throwable ignored) {
        }
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
        v.setBackgroundColor(COL_HAIRLINE);
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // ---------- 界面 ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COL_BG);
        root.setPadding(dp(64), dp(36), dp(64), dp(22));

        // 眉标行：TV HUD …………………………… 日期 · 今日时长
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("TV HUD", 10f, COL_SECOND, din, 0.22f));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        metaLine = label("", 10f, COL_TERTIARY, cjk, 0.04f);
        metaLine.setGravity(Gravity.END);
        header.addView(metaLine);
        root.addView(header);

        // 标题
        TextView title = label("使用台账", 22f, COL_PRIMARY, cjk, 0.08f);
        LinearLayout.LayoutParams titleLp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(12);
        root.addView(title, titleLp);

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
        TextView version = label(ver, 9f, COL_TERTIARY, din, 0.10f);
        version.setGravity(Gravity.END);
        root.addView(version, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private View buildRow(final int index, final Item item) {
        final boolean enabled = item.enabled();

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
                COL_TERTIARY, din, 0.06f);
        row.addView(idx, lp(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT));

        final int nameBase = enabled ? 0xffcfd8e0 : COL_SECOND;
        final TextView name = label(item.label, 15f, nameBase, cjk, 0.04f);
        row.addView(name, lp(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView desc = label(item.desc, 10f, COL_TERTIARY, cjk, 0.04f);
        row.addView(desc, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tag = label(item.tag.isEmpty() ? "›" : item.tag,
                item.tag.isEmpty() ? 15f : 9f, COL_TERTIARY, cjk, 0.10f);
        tag.setGravity(Gravity.END);
        row.addView(tag, lp(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackgroundColor(hasFocus ? COL_ROW_HOVER : 0x00000000);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? 0xe6ffffff : 0x00000000);
                bg.setCornerRadius(dp(1.5f));
                accent.setBackground(bg);
                idx.setTextColor(hasFocus ? COL_PRIMARY : COL_TERTIARY);
                name.setTextColor(hasFocus ? COL_PRIMARY : nameBase);
                desc.setTextColor(hasFocus ? COL_SECOND : COL_TERTIARY);
                tag.setTextColor(hasFocus ? COL_PRIMARY : COL_TERTIARY);
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
        if (!item.enabled()) {
            Toast.makeText(this, "「" + item.label + "」还没做，下个版本加上", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN,
                item.action == ACTION_HISTORY ? MainActivity.OPEN_HISTORY
                        : MainActivity.OPEN_TODAY);
        startActivity(i);
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
