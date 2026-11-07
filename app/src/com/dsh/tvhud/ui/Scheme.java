package com.dsh.tvhud.ui;

/**
 * 主题（底色系 / 材质）：一整套表面与文字决策，不含强调色。
 *
 * 八套材质全是「暗室」：电视是发光的，界面再亮一次会跟画面抢眼睛。
 * 差异落在三件事上：
 * - 色温：蓝黑 / 暖棕 / 纯黑 / 石墨 / 近黑 / 紫黑 / 墨绿 / 酒黑；
 * - 几何：面板圆角从 0（影院）到 20dp（森林），切角方尖本身就是材质；
 * - 辅色：每套材质自带一只「辅色」（主色之外的第二只灯），预览页用它做第二信息。
 *
 * 强调色（主色 / 灯）由 {@link Accent} 注入，仍是独立轴；精选外观见 {@link Looks}。
 */
public final class Scheme {

    public final String id;
    public final String name;
    public final String tagline;

    // ---- 应用层（首页 / 使用记录）----
    public final int appInk;
    public final int appRule;
    public final int appRowHover;
    public final int appText;
    public final int appText2;
    public final int appText3;
    public final int appSpine;

    // ---- 辅色（第二只灯：次级标记 / 状态条，不抢主色）----
    public final int secondary;
    public final String secondaryName;

    // ---- 报告层（Canvas 大屏）----
    public final int reportInk;
    public final int reportPanelTop;
    public final int reportPanelBot;
    public final int reportLine;
    public final int reportText;
    public final int reportText2;
    public final int reportText3;
    public final int reportTrack;

    /** 0 = 无光晕；1 = 单光晕；2 = 双光晕（现状）。 */
    public final int depth;

    /** 面板基础圆角（dp）：0 = 直角，16 = 圆角。 */
    public final float panelRadius;

    /** 该主题的默认强调色（用户可换）。 */
    public final String defaultAccentId;

    private Scheme(String id, String name, String tagline,
                   int appInk, int appRule, int appRowHover,
                   int appText, int appText2, int appText3, int appSpine,
                   int secondary, String secondaryName,
                   int reportInk, int reportPanelTop, int reportPanelBot, int reportLine,
                   int reportText, int reportText2, int reportText3, int reportTrack,
                   int depth, float panelRadius, String defaultAccentId) {
        this.id = id;
        this.name = name;
        this.tagline = tagline;
        this.appInk = appInk;
        this.appRule = appRule;
        this.appRowHover = appRowHover;
        this.appText = appText;
        this.appText2 = appText2;
        this.appText3 = appText3;
        this.appSpine = appSpine;
        this.secondary = secondary;
        this.secondaryName = secondaryName;
        this.reportInk = reportInk;
        this.reportPanelTop = reportPanelTop;
        this.reportPanelBot = reportPanelBot;
        this.reportLine = reportLine;
        this.reportText = reportText;
        this.reportText2 = reportText2;
        this.reportText3 = reportText3;
        this.reportTrack = reportTrack;
        this.depth = depth;
        this.panelRadius = panelRadius;
        this.defaultAccentId = defaultAccentId;
    }

    /** 默认：保留现状报告页的「仪表」材质。圆角 6dp，辅色暮蓝。 */
    public static final Scheme DASHBOARD = new Scheme(
            "dashboard", "仪表", "界面：卡片 + 光晕（默认，保留现状）",
            0xFF0E1114, 0x288A97A4, 0x1FFFFFFF,
            0xFFE9EEF3, 0xFF9AA7B2, 0xFF5F6B76, 0xE6FFFFFF,
            0xFF6B7FD7, "暮蓝",
            0xFF06090F, 0xFF131E2B, 0xFF090F17, 0x2469AACC,
            0xFFD5E5F5, 0xFF93A4B8, 0xFF5C6B7D, 0x1469AACC,
            2, 6f, "signal");

    /** 账本：暖石墨 + 发丝线，锐角 3dp，辅色鼠尾草。 */
    public static final Scheme LEDGER = new Scheme(
            "ledger", "账本", "界面：暖石墨 + 发丝线，无光晕",
            0xFF12100D, 0x2A2620, 0x1AF7ECD7,
            0xFFF4EEE3, 0xFFC0B6A6, 0xFF978C7B, 0xFFF7ECD7,
            0xFF8FA68E, "鼠尾草",
            0xFF12100D, 0xFF1A1713, 0xFF1A1713, 0xFF2A2620,
            0xFFF4EEE3, 0xFFC0B6A6, 0xFF978C7B, 0xFF2A2620,
            0, 3f, "moon");

    /** 影院：纯黑 + 零圆角，辅色钢灰。 */
    public static final Scheme CINEMA = new Scheme(
            "cinema", "影院", "界面：纯黑 + 直角，对比优先",
            0xFF000000, 0xFF262626, 0x1FFFFFFF,
            0xFFF2F2EE, 0xFFBFBFB8, 0xFF8A8A84, 0xFFDCE8F2,
            0xFF7F8C99, "钢灰",
            0xFF000000, 0xFF0C0C0C, 0xFF0C0C0C, 0xFF262626,
            0xFFF2F2EE, 0xFFBFBFB8, 0xFF8A8A84, 0xFF1A1A1A,
            0, 0f, "frost");

    /** 纸光（暖房）：暖纸黑 + 16dp 大圆角，辅色陶土。 */
    public static final Scheme PAPERLIGHT = new Scheme(
            "paperlight", "纸光", "界面：暖纸黑 + 柔和表面",
            0xFF171512, 0xFF322C24, 0x1AF2E3C9,
            0xFFF0E8DA, 0xFFC4B8A4, 0xFF9C8F7C, 0xFFF2E3C9,
            0xFFC97C5D, "陶土",
            0xFF171512, 0xFF201C17, 0xFF201C17, 0xFF322C24,
            0xFFF0E8DA, 0xFFC4B8A4, 0xFF9C8F7C, 0xFF322C24,
            0, 16f, "ivory");

    /** 经典 dark：近黑 + 素白，辅色石墨。 */
    public static final Scheme DARK = new Scheme(
            "dark", "暗色", "界面：近黑 + 素白，经典 dark",
            0xFF111214, 0x2EFFFFFF, 0x1AFFFFFF,
            0xFFF2F2F0, 0xFFB8B8B4, 0xFF85857F, 0xFFEDEDED,
            0xFF8B93A8, "石墨",
            0xFF0C0D0E, 0xFF17181A, 0xFF121314, 0x2A2B2D,
            0xFFF2F2F0, 0xFFB8B8B4, 0xFF85857F, 0xFF202123,
            0, 4f, "snow");

    /** 暮紫：紫黑底 + 霓虹紫，辅色霓虹青；圆角 10dp。 */
    public static final Scheme NEON = new Scheme(
            "neon", "暮紫", "界面：紫黑 + 霓虹，夜里的一点电",
            0xFF14101F, 0x2AB9A5FF, 0x18B9A5FF,
            0xFFEDE9FE, 0xFFA9A4C4, 0xFF7C779A, 0xFFC4B5FD,
            0xFF5EEAD4, "霓虹青",
            0xFF0D0A16, 0xFF1B1630, 0xFF120E1E, 0x2A5EEAD4,
            0xFFEDE9FE, 0xFFA9A4C4, 0xFF7C779A, 0x1C5EEAD4,
            1, 10f, "violet");

    /** 森林：墨绿底 + 薄荷，辅色麦穗；圆角 20dp，最柔的一套。 */
    public static final Scheme FOREST = new Scheme(
            "forest", "森林", "界面：墨绿 + 薄荷，像窗外的树影",
            0xFF0B1410, 0x2A6EE7B7, 0x186EE7B7,
            0xFFE8F5EE, 0xFFA7C2B4, 0xFF78907F, 0xFF6EE7B7,
            0xFFE8C468, "麦穗",
            0xFF07100C, 0xFF0F1B15, 0xFF0A120E, 0x2A6EE7B7,
            0xFFE8F5EE, 0xFFA7C2B4, 0xFF78907F, 0x1C6EE7B7,
            0, 20f, "mint");

    /** 日落：酒黑底 + 珊瑚，辅色杏黄；圆角 12dp。 */
    public static final Scheme SUNSET = new Scheme(
            "sunset", "日落", "界面：酒黑 + 珊瑚，傍晚那阵橙",
            0xFF1A0E12, 0x33FFB27A, 0x1AFFB27A,
            0xFFFDEDE4, 0xFFC9A99A, 0xFF96796B, 0xFFFF8A80,
            0xFFFFD166, "杏黄",
            0xFF12070A, 0xFF241318, 0xFF1A0E12, 0x2AFFB27A,
            0xFFFDEDE4, 0xFFC9A99A, 0xFF96796B, 0x1CFFB27A,
            1, 12f, "coral");

    public static final Scheme[] ALL = {
            DASHBOARD, LEDGER, CINEMA, PAPERLIGHT, DARK, NEON, FOREST, SUNSET};

    public static Scheme byId(String id) {
        if (id != null) {
            for (int i = 0; i < ALL.length; i++) {
                if (ALL[i].id.equals(id)) {
                    return ALL[i];
                }
            }
        }
        return DASHBOARD;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(id)) {
                return i;
            }
        }
        return 0;
    }

    public static Scheme defaultScheme() {
        return DASHBOARD;
    }
}
