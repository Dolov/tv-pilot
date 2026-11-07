package com.dsh.tvhud.ui;

/**
 * 主题色（强调色）：全 App 唯一的「灯」。
 *
 * 只影响焦点、选中、「现在」游标、图表强调线；不影响数据类别色，
 * 也不改变表面与文字层级——那是 {@link Scheme} 的事。
 *
 * 16 个取值分四组：暖白 / 冷白 / 饱和 / 深色。
 * 全部已在 4 套底色上验证对比度 ≥ 4.5（见 tests/java/.../ThemeCoreTest）。
 */
public final class Accent {

    public final String id;
    public final String name;
    public final int color;

    private Accent(String id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    // ---- 暖白组：像纸的反光，默认 ----
    public static final Accent MOON = new Accent("moon", "月白", 0xFFF7ECD7);
    public static final Accent IVORY = new Accent("ivory", "象牙", 0xFFF2E3C9);
    public static final Accent SHELL = new Accent("shell", "米白", 0xFFEDE4D3);
    public static final Accent EMBER = new Accent("ember", "琥珀", 0xFFFFC97A);

    // ---- 冷白组：冷光 / 白天 ----
    public static final Accent ICE = new Accent("ice", "冰蓝", 0xFF9FD3FF);
    public static final Accent FROST = new Accent("frost", "霜白", 0xFFDCE8F2);
    public static final Accent STEEL = new Accent("steel", "钢蓝", 0xFF7FA8D4);
    public static final Accent SIGNAL = new Accent("signal", "信号青", 0xFF38BDF8);

    // ---- 饱和组：个性 ----
    public static final Accent VIOLET = new Accent("violet", "紫晶", 0xFFB9A5FF);
    public static final Accent CORAL = new Accent("coral", "珊瑚", 0xFFFF8A80);
    public static final Accent MINT = new Accent("mint", "薄荷", 0xFF7EE0C3);
    public static final Accent LEMON = new Accent("lemon", "柠檬", 0xFFFFE066);

    // ---- 深色组：夜间不刺眼 ----
    public static final Accent OCEAN = new Accent("ocean", "深海", 0xFF4FA3D1);
    public static final Accent PINE = new Accent("pine", "松绿", 0xFF6FCF97);
    public static final Accent WINE = new Accent("wine", "酒红", 0xFFD96B8A);
    public static final Accent LAVENDER = new Accent("lavender", "薰衣草", 0xFF9E8CD0);

    // ---- 经典组：经典 dark 的灯是白的，不是彩色 ----
    /** 暗底上的素白：经典 dark 的「灯」是白的。 */
    public static final Accent SNOW = new Accent("snow", "素白", 0xFFEDEDED);

    /** 顺序即设置页顺序：暖白 → 冷白 → 饱和 → 深色 → 经典。 */
    public static final Accent[] ALL = {
            MOON, IVORY, SHELL, EMBER,
            ICE, FROST, STEEL, SIGNAL,
            VIOLET, CORAL, MINT, LEMON,
            OCEAN, PINE, WINE, LAVENDER,
            SNOW,
    };

    private static final Accent DEFAULT = SIGNAL;

    public static Accent byId(String id) {
        if (id != null) {
            for (int i = 0; i < ALL.length; i++) {
                if (ALL[i].id.equals(id)) {
                    return ALL[i];
                }
            }
        }
        return DEFAULT;
    }

    public static Accent byIndex(int index) {
        if (index < 0 || index >= ALL.length) {
            return DEFAULT;
        }
        return ALL[index];
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(id)) {
                return i;
            }
        }
        return indexOf(DEFAULT.id);
    }

    public static Accent defaultAccent() {
        return DEFAULT;
    }
}
