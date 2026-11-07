package com.dsh.tvhud.ui;

/**
 * 版式与形状的度量：字号、间距、圆角、焦点几何。
 *
 * 与颜色分离：主题色只换颜色，换主题才换这套数。
 * 单位全部是 dp（1080p、density=2 时 1dp = 2px）。
 */
public final class Metrics {

    // ---- 字号（报告/大屏）----
    public final float micro;      // 9   键位、图例、页脚
    public final float caption;    // 10  表头、标签
    public final float body;       // 12  正文、洞察
    public final float emphasis;   // 15  行标题
    public final float title;      // 22  页面标题
    public final float metric;     // 26  KPI 主值
    public final float metricLg;   // 34  合计

    // ---- 字号（首页 / 使用记录，现状）----
    public final float appMicro;    // 9
    public final float appCaption;  // 10
    public final float appBody;     // 10
    public final float appEmphasis; // 15
    public final float appTitle;    // 22
    public final float appValue;    // 26

    // ---- 形状 ----
    public final float radiusPanel;
    public final float radiusStory;   // 22
    public final float radiusControl; // 3
    public final float hairline;      // 1
    public final float focusSpineW;   // 5
    public final float focusSpineH;   // 44
    public final float focusRingW;    // 3

    // ---- 间距（4 的倍数）----
    public final int spaceXs;  // 4
    public final int spaceSm;  // 8
    public final int spaceMd;  // 12
    public final int spaceLg;  // 16
    public final int spaceXl;  // 24
    public final int space2Xl; // 32
    public final int space3Xl; // 48
    public final int space4Xl; // 64

    private Metrics(float radiusPanel, float focusSpineW) {
        micro = 9f;
        caption = 10f;
        body = 12f;
        emphasis = 15f;
        title = 22f;
        metric = 26f;
        metricLg = 34f;

        appMicro = 9f;
        appCaption = 10f;
        appBody = 10f;
        appEmphasis = 15f;
        appTitle = 22f;
        appValue = 26f;

        this.radiusPanel = radiusPanel;
        radiusStory = 22f;
        radiusControl = 3f;
        hairline = 1f;
        this.focusSpineW = focusSpineW;
        focusSpineH = 44f;
        focusRingW = 3f;

        spaceXs = 4;
        spaceSm = 8;
        spaceMd = 12;
        spaceLg = 16;
        spaceXl = 24;
        space2Xl = 32;
        space3Xl = 48;
        space4Xl = 64;
    }

    private static final Metrics DASHBOARD = new Metrics(Scheme.DASHBOARD.panelRadius, 3f);
    private static final Metrics LEDGER = new Metrics(Scheme.LEDGER.panelRadius, 5f);
    private static final Metrics CINEMA = new Metrics(Scheme.CINEMA.panelRadius, 4f);
    private static final Metrics PAPERLIGHT = new Metrics(Scheme.PAPERLIGHT.panelRadius, 4f);
    private static final Metrics DARK = new Metrics(Scheme.DARK.panelRadius, 3f);
    private static final Metrics NEON = new Metrics(Scheme.NEON.panelRadius, 4f);
    private static final Metrics FOREST = new Metrics(Scheme.FOREST.panelRadius, 4f);
    private static final Metrics SUNSET = new Metrics(Scheme.SUNSET.panelRadius, 4f);

    public static Metrics of(Scheme scheme) {
        if (scheme == Scheme.LEDGER) {
            return LEDGER;
        }
        if (scheme == Scheme.CINEMA) {
            return CINEMA;
        }
        if (scheme == Scheme.PAPERLIGHT) {
            return PAPERLIGHT;
        }
        if (scheme == Scheme.DARK) {
            return DARK;
        }
        if (scheme == Scheme.NEON) {
            return NEON;
        }
        if (scheme == Scheme.FOREST) {
            return FOREST;
        }
        if (scheme == Scheme.SUNSET) {
            return SUNSET;
        }
        return DASHBOARD;
    }
}
