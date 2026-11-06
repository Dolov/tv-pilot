package com.dsh.tvhud;

/**
 * 使用类型 → 长条颜色。长条上不写文字，颜色就是图例。
 *
 * 规则（按用户要求）：
 * - 所有「看电视/视频」类（含直播、动画、投屏、HDMI/DVB 输入）统一用 #F43098
 * - 所有「听音乐/K歌」类统一用 #00D3BB
 * - 儿童学习类单独橙色；桌面/系统灰蓝；其余深灰
 */
public final class Categories {
    public static final int VIDEO = 0;   // 看电视/视频（不区分 App）
    public static final int MUSIC = 1;   // 听音乐/K 歌（不区分 App）
    public static final int KIDS = 2;    // 儿童学习（洪恩 / 思维）
    public static final int SYSTEM = 3;  // 桌面 / 设置 / 系统
    public static final int OTHER = 4;   // 其他（广告/统计/账号/未知）

    private static final int[] COLORS = {
            0xfff43098, // 电视（用户指定）
            0xff00d3bb, // 音乐（用户指定）
            0xffffb74d, // 橙（儿童学习）
            0xff90a4ae, // 灰蓝（桌面/系统）
            0xff607d8b  // 深灰（其他）
    };

    private Categories() {
    }

    public static int color(int cat) {
        if (cat < 0 || cat >= COLORS.length) {
            return COLORS[OTHER];
        }
        return COLORS[cat];
    }

    /**
     * 带页面类名的分类：tvhome 的「详情页/播放页」= 内嵌播放器正在放片，算 VIDEO；
     * 桌面/启动页/用户中心仍按 SYSTEM。拿不到类名时退回按包名分类。
     *
     * 为什么需要：PatchWall 的播放器不切 Activity，看动画片时前台一直是
     * com.mitv.tvhome.DetailsActivity（包名 = 桌面），只按包名会全记成灰蓝「系统」。
     */
    public static int of(String pkg, String className) {
        int base = of(pkg);
        if (base == SYSTEM && pkg != null && pkg.startsWith("com.mitv.tvhome")
                && isPlayerPage(className)) {
            return VIDEO;
        }
        return base;
    }

    /** 详情/播放类页面：DetailsActivity、PlayActivity、PlayerActivity、VideoDetailActivity… */
    private static boolean isPlayerPage(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return simple.contains("Detail")
                || simple.contains("Play")
                || simple.contains("Video")
                || simple.contains("Theater");
    }

    /** 类别中文名（详情页明细用）。 */
    public static String name(int cat) {
        switch (cat) {
            case VIDEO:
                return "视频";
            case MUSIC:
                return "音乐";
            case KIDS:
                return "儿童学习";
            case SYSTEM:
                return "桌面/系统";
            default:
                return "其他";
        }
    }

    public static int of(String pkg) {
        if (pkg == null) {
            return OTHER;
        }
        // 看电视/视频（不区分哪个 App）
        switch (pkg) {
            case "com.cibn.tv":
            case "com.sohuott.tv.vod.xiaomi":
            case "com.xiaodianshi.tv.yst":
            case "com.gitv.dvb.live":
            case "com.gitvdemo.video":
            case "com.mitv.mivideoplayer":
            case "com.xiaomi.mitv.tvplayer":
            case "com.duokan.videodaily":
            // 投屏（手机投到电视，本质也是看视频）
            case "com.xiaomi.upnp":
            case "com.duokan.airkan.tvbox":
            case "com.xiaomi.miplay":
            case "com.xiaomi.mitv.smartshare":
            // 电视输入源：看地面/有线/HDMI 时前台是这些
            case "com.novatek.ATVTVinput":
            case "com.novatek.DVBTTVinput":
            case "com.novatek.cvbstvinput":
            case "com.novatek.hdmi1tvinput":
            case "com.novatek.hdmi2tvinput":
            case "com.novatek.hdmi3tvinput":
                return VIDEO;
            // 听音乐 / K 歌（不区分哪个 App）
            case "com.kugou.android.tv":
            case "com.tencent.qqmusictv":
            case "com.xiaomi.mitv.karaoke.service":
                return MUSIC;
            // 儿童学习
            case "com.hongen.app.word.ott.vip":
            case "com.ihuman.pinyin.ott.vip":
            case "com.lutongnet.nldmxXiaomiChildren":
                return KIDS;
            // 备选桌面 / 儿童模式宿主 / 办公模式
            case "com.xiaomi.eetv.litehome":
            case "com.xiaomi.mibox.lockscreen":
            case "com.android.tv.settings":
                return SYSTEM;
            default:
                break;
        }
        if (pkg.startsWith("com.mitv.")
                || pkg.startsWith("com.xiaomi.mitv.")
                || pkg.equals("com.android.systemui")) {
            return SYSTEM;
        }
        return OTHER;
    }
}
