package com.dsh.tvhud.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 外观持久化：SharedPreferences("hud") 只存一个 look id + revision。
 *
 * 用户选的是 {@link Looks.Look}（房间/时段），不是 scheme + accent 两个色号；
 * 旧版本存的两个 id 会被 {@link Looks#fromLegacy} 迁移成最接近的 look。
 *
 * 页面在 onResume 比对 revision，变了才重建（只有首页/使用记录/设置页需要，
 * 屏保角标不跟主题）。
 */
public final class ThemeStore {

    private static final String PREFS = "hud";
    private static final String KEY_LOOK = "theme_look";
    /** 旧版本字段，只读迁移；新代码不再写。 */
    private static final String KEY_SCHEME = "theme_scheme";
    private static final String KEY_ACCENT = "theme_accent";
    private static final String KEY_REV = "theme_rev";

    private ThemeStore() {
    }

    /** 当前精选外观；没有存过时从旧 scheme/accent 迁移并落盘。 */
    public static Looks.Look look(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        String id = p.getString(KEY_LOOK, null);
        if (id != null) {
            return Looks.byId(id);
        }
        Looks.Look migrated = Looks.fromLegacy(
                p.getString(KEY_SCHEME, Scheme.defaultScheme().id),
                p.getString(KEY_ACCENT, Accent.defaultAccent().id));
        p.edit().putString(KEY_LOOK, migrated.id).apply();
        return migrated;
    }

    /** 当前外观对应的完整主题（材质 + 灯 + 度量）。 */
    public static Theme get(Context ctx) {
        return look(ctx).theme;
    }

    public static String schemeId(Context ctx) {
        return look(ctx).scheme.id;
    }

    public static String accentId(Context ctx) {
        return look(ctx).accent.id;
    }

    public static int rev(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getInt(KEY_REV, 0);
    }

    /** 选一个 look；写入并 +1 revision，返回新 revision。 */
    public static int setLook(Context ctx, Looks.Look look) {
        return put(ctx, look.id, look.scheme.id, look.accent.id);
    }

    /**
     * 兼容入口：只有「材质 + 灯」正好是精选组合时才存成 look；
     * 否则按材质落到该材质默认 look，避免旧调用点把外观写脏。
     *
     * @deprecated 用 {@link #setLook(Context, Looks.Look)}。
     */
    @Deprecated
    public static int set(Context ctx, Theme theme) {
        for (int i = 0; i < Looks.ALL.length; i++) {
            Looks.Look look = Looks.ALL[i];
            if (look.scheme == theme.scheme && look.accent == theme.accent) {
                return setLook(ctx, look);
            }
        }
        Looks.Look fallback = Looks.fromLegacy(theme.scheme.id, theme.accent.id);
        return put(ctx, fallback.id, theme.scheme.id, theme.accent.id);
    }

    /** 兼容入口：旧 code 用两个 id 存外观。 */
    public static int set(Context ctx, String schemeId, String accentId) {
        return set(ctx, Themes.of(schemeId, accentId));
    }

    private static int put(Context ctx, String lookId, String schemeId, String accentId) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        int rev = p.getInt(KEY_REV, 0) + 1;
        p.edit()
                .putString(KEY_LOOK, lookId)
                .putString(KEY_SCHEME, schemeId)
                .putString(KEY_ACCENT, accentId)
                .putInt(KEY_REV, rev)
                .apply();
        return rev;
    }
}
