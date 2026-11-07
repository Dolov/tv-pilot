package com.dsh.tvhud.ui;

/**
 * 精选外观（look）：用户选的是「今晚客厅什么光」，不是色号。
 *
 * 主题仍然是 8 材质 × 17 主题色 = 136 个不可变 {@link Theme}（开发/预览用），
 * 但家长设置里只暴露 8 个精选组合：一套材质配一只灯，名字用房间/时段，不用颜色名。
 * 电视是发光的，所以八个 look 都是暗室；「暗色」是唯一的经典 neutral 选项。
 *
 * 为什么收窄：电视在 3 米外、遥控器 5 个键，16 阶色卡不是观感差异；而且每多一个组合，
 * 就要在带子、报告、宠物上各验一遍。少而准，才接得完、也才记得住。
 *
 * 「账本」是可选的第 4 个 look：保留 4 套材质各一条，旧数据也能原样迁移。
 */
public final class Looks {

    /** 一个精选外观：材质 + 灯。 */
    public static final class Look {
        public final String id;
        public final String name;
        public final String desc;
        public final String lampName;
        public final Scheme scheme;
        public final Accent accent;
        public final Theme theme;

        private Look(String id, String name, String desc, String lampName,
                     Scheme scheme, Accent accent) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.lampName = lampName;
            this.scheme = scheme;
            this.accent = accent;
            this.theme = Themes.of(scheme, accent);
        }

        public Palette palette() {
            return theme.palette;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** 默认：关灯后开着电视的那点蓝。 */
    public static final Look NIGHT = new Look(
            "night", "夜班", "关灯后开着电视的那一点蓝", "信号青",
            Scheme.DASHBOARD, Accent.SIGNAL);

    /** 冬天客厅的琥珀色暖光。 */
    public static final Look WARM = new Look(
            "warm", "暖房", "冬天客厅里的一盏琥珀光", "琥珀",
            Scheme.PAPERLIGHT, Accent.EMBER);

    /** 看片时只留屏幕的光。 */
    public static final Look CINEMA = new Look(
            "cinema", "影院", "看片时只留屏幕的那点光", "冰蓝",
            Scheme.CINEMA, Accent.ICE);

    /** 白天 / 长辈：安静台账。 */
    public static final Look LEDGER = new Look(
            "ledger", "账本", "白天或长辈看的安静台账", "月白",
            Scheme.LEDGER, Accent.MOON);

    /** 经典 dark：近黑 + 素白，4dp 圆角 + 石墨辅色。 */
    public static final Look DARK = new Look(
            "dark", "暗色", "经典 dark：近黑底素白字", "素白",
            Scheme.DARK, Accent.SNOW);

    /** 暮紫：紫黑 + 紫晶，夜里的霓虹。 */
    public static final Look NEON = new Look(
            "neon", "暮紫", "紫黑底上的一点霓虹光", "紫晶",
            Scheme.NEON, Accent.VIOLET);

    /** 森林：墨绿 + 薄荷，最柔的 20dp 圆角。 */
    public static final Look FOREST = new Look(
            "forest", "森林", "像窗外树影的那种墨绿", "薄荷",
            Scheme.FOREST, Accent.MINT);

    /** 日落：酒黑 + 珊瑚，傍晚那阵橙。 */
    public static final Look SUNSET = new Look(
            "sunset", "日落", "傍晚落在客厅墙上的那阵橙", "珊瑚",
            Scheme.SUNSET, Accent.CORAL);

    /** 顺序即设置页顺序：夜 → 暖 → 影院 → 账本 → 暗色 → 暮紫 → 森林 → 日落。 */
    public static final Look[] ALL = {NIGHT, WARM, CINEMA, LEDGER, DARK, NEON, FOREST, SUNSET};

    private Looks() {
    }

    public static Look byId(String id) {
        if (id != null) {
            for (int i = 0; i < ALL.length; i++) {
                if (ALL[i].id.equals(id)) {
                    return ALL[i];
                }
            }
        }
        return NIGHT;
    }

    public static int indexOf(Look look) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i] == look) {
                return i;
            }
        }
        return 0;
    }

    public static Look defaultLook() {
        return NIGHT;
    }

    /**
     * 旧版本只存了 scheme / accent 两个 id：迁移时优先找完全匹配的 look，
     * 否则按材质找同一套材质的默认 look，再不行回默认。
     */
    public static Look fromLegacy(String schemeId, String accentId) {
        Scheme scheme = Scheme.byId(schemeId);
        Accent accent = Accent.byId(accentId);
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].scheme == scheme && ALL[i].accent == accent) {
                return ALL[i];
            }
        }
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].scheme == scheme) {
                return ALL[i];
            }
        }
        return NIGHT;
    }
}
