package com.dsh.tvhud.usage;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类映射单测：视频 / 音乐 / 儿童 / 桌面 / 其他，以及 PatchWall 内嵌音乐插件。
 * 纯 Java，不需要 Android（tests/java/run.sh 编译）。
 */
public final class CategoriesTest {
    private static int passed = 0;
    private static int failed = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private interface Body {
        void run();
    }

    public static void main(String[] args) {
        runCase("视频：视频 App / 投屏 / 输入源 / PatchWall 播放页", new Body() {
            @Override
            public void run() {
                eq("CIBN", Categories.VIDEO, Categories.of("com.cibn.tv"));
                eq("优酷", Categories.VIDEO, Categories.of("com.cibn.tv", "com.youku.tv.home.activity.HomeActivity"));
                eq("投屏", Categories.VIDEO, Categories.of("com.xiaomi.miplay"));
                eq("HDMI", Categories.VIDEO, Categories.of("com.novatek.hdmi1tvinput"));
                eq("PatchWall 播放页", Categories.VIDEO,
                        Categories.of("com.mitv.tvhome", "com.mitv.tvhome.DetailsActivity"));
                eq("PatchWall 播放页（Play）", Categories.VIDEO,
                        Categories.of("com.mitv.tvhome", "com.mitv.oneplayer.PlayActivity"));
            }
        });

        runCase("音乐：独立音乐 App + PatchWall 内嵌酷狗插件", new Body() {
            @Override
            public void run() {
                eq("酷狗 TV", Categories.MUSIC, Categories.of("com.kugou.android.tv"));
                eq("QQ 音乐 TV", Categories.MUSIC, Categories.of("com.tencent.qqmusictv"));
                eq("K 歌", Categories.MUSIC, Categories.of("com.xiaomi.mitv.karaoke.service"));
                // 2026-11-07 实测：PatchWall 音乐插件前台是代理宿主页，媒体会话名 Kugou_Media
                eq("PatchWall 插件宿主（酷狗）", Categories.MUSIC,
                        Categories.of("com.mitv.tvhome",
                                "com.mitv.plugin.sdk.host.launcher.ProxySingleTaskActivity"));
                eq("PatchWall 音乐页", Categories.MUSIC,
                        Categories.of("com.mitv.tvhome", "com.mitv.tvhome.music.MusicActivity"));
                check("宿主判定", Categories.isPatchWallMusicHost("com.mitv.tvhome",
                        "com.mitv.plugin.sdk.host.launcher.ProxySingleTaskActivity"));
                check("非 PatchWall 包不算宿主", !Categories.isPatchWallMusicHost("com.kugou.android.tv",
                        "com.kugou.android.tv.KugouActivity"));
            }
        });

        runCase("桌面 / 儿童 / 其他：不误伤", new Body() {
            @Override
            public void run() {
                eq("PatchWall 桌面", Categories.SYSTEM, Categories.of("com.mitv.tvhome"));
                eq("PatchWall 用户中心", Categories.SYSTEM,
                        Categories.of("com.mitv.tvhome", "com.mitv.tvhome.business.user.UserCenterSubPageActivity"));
                eq("设置", Categories.SYSTEM, Categories.of("com.android.tv.settings"));
                eq("洪恩识字", Categories.KIDS, Categories.of("com.hongen.app.word.ott.vip"));
                eq("未知", Categories.OTHER, Categories.of("com.example.unknown"));
                eq("空包名", Categories.OTHER, Categories.of(null));
                check("分类规则版本 >= 2", Categories.RULES_REV >= 2);
                eq("颜色数量", 5, countColors());
            }
        });

        System.out.println("用例 PASS " + passed + "  FAIL " + failed + "（断言 " + checks + " 条）");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("  FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static int countColors() {
        int n = 0;
        for (int i = 0; i < 5; i++) {
            Categories.color(i);
            n++;
        }
        return n;
    }

    private static void runCase(String name, Body body) {
        int before = failed;
        try {
            body.run();
        } catch (Throwable t) {
            failed++;
            failures.add(name + "：抛异常 " + t);
        }
        if (failed == before) {
            passed++;
            System.out.println("  ok  " + name);
        } else {
            System.out.println("  FAIL " + name);
        }
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failed++;
            failures.add(what);
        }
    }

    private static void eq(String what, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failed++;
            failures.add(what + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
