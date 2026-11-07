package com.dsh.tvhud.ui;

import com.dsh.tvhud.usage.Categories;

/**
 * 语义色板：页面只认这里的角色，不认具体 hex。
 *
 * 由「主题（Scheme）+ 主题色（Accent）」派生一次后缓存；
 * onDraw 只读字段，不做任何计算与分配。
 *
 * 命名约定（参考 shadcn / Chakra）：
 * - 表面用 xxx，其上的文字用 onXxx；实底文字统一走 {@link Contrast#on}
 * - 强调色叫 lamp（灯），只用于焦点 / 选中 / 现在游标 / 图表强调
 * - 数据类别色固定，不随主题变
 */
public final class Palette {

    // ---- 应用层（首页 / 使用记录）----
    public final int appInk;
    public final int appRule;
    public final int appRowHover;
    public final int appText;
    public final int appText2;
    public final int appText3;
    public final int appSpine;

    // ---- 报告层（Canvas 大屏）----
    public final int reportInk;
    public final int reportPanelTop;
    public final int reportPanelBot;
    public final int reportRule;
    public final int reportText;
    public final int reportText2;
    public final int reportText3;
    public final int reportTrack;

    // ---- 强调色（灯）----
    public final int lamp;
    public final int lampWash;
    public final int lampGlow;
    public final int lampRing;
    public final int onLamp;

    // ---- 带子（Day Tape）：全 App 同构的 24 小时时间带 ----
    /** 轨道底：比发丝线更淡，只交代「这里有一条时间」。 */
    public final int tapeTrack;
    /** 刻度 / 整点参考线。 */
    public final int tapeRule;
    /** 短刻度。 */
    public final int tapeTick;
    /** 刻度数字与轴标签。 */
    public final int tapeLabel;
    /** 「现在」游标：全 App 唯一的发光体，所以直接用灯色。 */
    public final int tapeNow;

    // ---- 状态 ----
    public final int warn;
    public final int danger;

    // ---- 数据类别色（固定，不参与换肤）----
    public final int[] data;

    // ---- 辅色（第二只灯）----
    /** 主色之外的第二只灯：次级标记 / 状态条 / 预览里的第二块色。 */
    public final int secondary;
    public final int secondaryWash;
    public final int onSecondary;

    /** 0 = 无光晕；1 = 单光晕；2 = 双光晕。 */
    public final int depth;

    /** 面板基础圆角（dp）：0 = 直角，16 = 大圆角。 */
    public final float panelRadius;
    /** 行 / 输入框 / 小卡片的圆角（dp），由面板圆角派生，不另设主题。 */
    public final float rowRadius;
    /** 亮底材质（经典 light）：数据类别色要压暗才能过对比度。 */
    public final boolean light;

    /** 数据色「其他」的提亮版：在暖底/纯黑底上仍能过 4.5。 */
    public static final int OTHER_LIFTED = 0xFF7A8794;

    private Palette(Scheme s, Accent a) {
        appInk = s.appInk;
        appRule = s.appRule;
        appRowHover = Contrast.alpha(a.color, 0.10f);
        appText = s.appText;
        appText2 = s.appText2;
        appText3 = s.appText3;
        appSpine = a.color;

        reportInk = s.reportInk;
        reportPanelTop = s.reportPanelTop;
        reportPanelBot = s.reportPanelBot;
        reportRule = s.reportLine;
        reportText = s.reportText;
        reportText2 = s.reportText2;
        reportText3 = s.reportText3;
        reportTrack = s.reportTrack;

        lamp = a.color;
        lampWash = Contrast.alpha(a.color, 0.10f);
        lampGlow = Contrast.alpha(a.color, 0.22f);
        lampRing = a.color;
        onLamp = Contrast.on(a.color, s.appInk, s.appText);

        tapeTrack = Contrast.alpha(appText, 0.08f);
        tapeRule = Contrast.alpha(appText, 0.11f);
        tapeTick = Contrast.alpha(appText, 0.25f);
        tapeLabel = s.appText3;
        tapeNow = a.color;

        warn = 0xFFE8A35C;
        danger = 0xFFF27C74;

        light = Contrast.luminance(s.appInk) > 0.5;

        // 类别色固定，不跟主题换：用户指定「看电视 = #F43098 / 听音乐 = #00D3BB」，
        // 换了外观这两条颜色也不能变，否则家长会认不出内容类型。
        int[] src = new int[5];
        for (int i = 0; i < src.length; i++) {
            src[i] = Categories.color(i);
        }
        // 「其他」在暖底 / 纯黑底上原色偏低，统一提亮；色相不变。
        if (!light) {
            src[4] = OTHER_LIFTED;
        }
        data = src;

        secondary = s.secondary;
        secondaryWash = Contrast.alpha(s.secondary, 0.12f);
        onSecondary = Contrast.on(s.secondary, s.appInk, s.appText);

        depth = s.depth;
        panelRadius = s.panelRadius;
        rowRadius = s.panelRadius <= 1f ? 0f : Math.min(10f, s.panelRadius * 0.6f);
    }

    private static Palette[] cache = new Palette[Scheme.ALL.length * Accent.ALL.length];

    /** 取缓存实例；同一组合永远返回同一个对象，切换主题零分配。 */
    public static Palette of(Scheme scheme, Accent accent) {
        int si = indexOfScheme(scheme);
        int ai = indexOfAccent(accent);
        int k = si * Accent.ALL.length + ai;
        Palette p = cache[k];
        if (p == null) {
            p = new Palette(scheme, accent);
            cache[k] = p;
        }
        return p;
    }

    private static int indexOfScheme(Scheme s) {
        for (int i = 0; i < Scheme.ALL.length; i++) {
            if (Scheme.ALL[i] == s) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOfAccent(Accent a) {
        for (int i = 0; i < Accent.ALL.length; i++) {
            if (Accent.ALL[i] == a) {
                return i;
            }
        }
        return 0;
    }

    /** 热力图用：灯色 8% → 82% 的五档明度梯度，单色相，不跟类别色抢语义。 */
    public int heat(int level) {
        int l = level < 0 ? 0 : (level > 4 ? 4 : level);
        float alpha = 0.08f + 0.185f * l;
        return Contrast.alpha(lamp, alpha);
    }

    public int dataColor(int category) {
        if (category < 0 || category >= data.length) {
            return data[4];
        }
        return data[category];
    }
}
