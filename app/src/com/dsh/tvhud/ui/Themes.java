package com.dsh.tvhud.ui;

/**
 * 主题仓库：4 材质 × 16 强调色 = 64 个不可变主题，启动时一次算好。
 *
 * 切换主题只是换引用；页面拿到的 {@link Palette} 永远同一个对象，
 * 不会在 onResume / onDraw 里重新计算颜色。
 */
public final class Themes {

    private static final Theme[][] CACHE =
            new Theme[Scheme.ALL.length][Accent.ALL.length];

    static {
        for (int s = 0; s < Scheme.ALL.length; s++) {
            for (int a = 0; a < Accent.ALL.length; a++) {
                CACHE[s][a] = new Theme(Scheme.ALL[s], Accent.ALL[a]);
            }
        }
    }

    private Themes() {
    }

    public static Theme of(String schemeId, String accentId) {
        return of(Scheme.byId(schemeId), Accent.byId(accentId));
    }

    public static Theme of(Scheme scheme, Accent accent) {
        return CACHE[Scheme.indexOf(scheme.id)][Accent.indexOf(accent.id)];
    }

    /** 默认：仪表 · 信号青（与现状报告页一致）。 */
    public static Theme defaultTheme() {
        return of(Scheme.DASHBOARD, Accent.SIGNAL);
    }

    /** 每套材质的推荐配色，用于设置页第一眼的预览。 */
    public static Theme recommended(Scheme scheme) {
        return of(scheme, Accent.byId(scheme.defaultAccentId));
    }

    public static Theme[] recommendedThemes() {
        Theme[] out = new Theme[Scheme.ALL.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = recommended(Scheme.ALL[i]);
        }
        return out;
    }

    /** 16 个主题色在指定材质下的全部组合（设置页色环）。 */
    public static Theme[] accentRow(Scheme scheme) {
        Theme[] out = new Theme[Accent.ALL.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = of(scheme, Accent.ALL[i]);
        }
        return out;
    }
}
