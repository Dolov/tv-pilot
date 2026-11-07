package com.dsh.tvhud.ui;

/**
 * 主题 = 材质（Scheme）+ 强调色（Accent）+ 度量（Metrics）。
 *
 * 不可变；由 {@link Themes} 缓存，页面只读。
 */
public final class Theme {

    public final String id;
    public final String name;
    public final String tagline;
    public final Scheme scheme;
    public final Accent accent;
    public final Palette palette;
    public final Metrics metrics;

    Theme(Scheme scheme, Accent accent) {
        this.scheme = scheme;
        this.accent = accent;
        this.id = scheme.id + "." + accent.id;
        this.name = scheme.name + " · " + accent.name;
        this.tagline = scheme.tagline;
        this.palette = Palette.of(scheme, accent);
        this.metrics = Metrics.of(scheme);
    }

    public int depth() {
        return scheme.depth;
    }

    public float panelRadius() {
        return scheme.panelRadius;
    }

    @Override
    public String toString() {
        return id;
    }
}
