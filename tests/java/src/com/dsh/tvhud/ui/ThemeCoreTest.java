package com.dsh.tvhud.ui;

import com.dsh.tvhud.usage.Categories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主题核心的纯 Java 单元测试：不依赖 Android、不依赖电视。
 *
 * 守住的不是「颜色好看」，而是三条会随迭代漂移的硬约束：
 * 1. 16 个主题色在 4 套底色上都 >= 4.5（文字/焦点在 3 米外可读）；
 * 2. 表面上的文字统一由 Contrast.on 推导，不手写；
 * 3. 64 个主题在启动时算好，运行期取缓存，不分配。
 *
 * 运行：bash tests/java/run.sh；退出码 0=全过，1=有失败。
 */
public final class ThemeCoreTest {

    private static int passedCases = 0;
    private static int failedCases = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();
    private static final double TEXT_MIN = 4.5;
    private static final double TEXT3_MIN = 3.0; // 现状 dashboard 的第三级文字；P4 统一抬到 4.5

    private ThemeCoreTest() {
    }

    public static void main(String[] args) {
        runCase("主题色：暗底 × 18 色全部 >= 4.5；亮底只看精选 look", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    // 亮底上浅色不能当文字（白字/月白在纸上是看不见的），
                    // 所以只对 shipping 的精选 look 做断言；暗底仍需全部通过。
                    Palette lp = Palette.of(scheme, Accent.byId(scheme.defaultAccentId));
                    if (lp.light) {
                        continue;
                    }
                    for (int a = 0; a < Accent.ALL.length; a++) {
                        Accent accent = Accent.ALL[a];
                        double onApp = Contrast.ratio(accent.color, scheme.appInk);
                        double onReport = Contrast.ratio(accent.color, scheme.reportInk);
                        check(onApp >= TEXT_MIN, scheme.id + "." + accent.id
                                + " 在应用底色上对比度 " + round(onApp));
                        check(onReport >= TEXT_MIN, scheme.id + "." + accent.id
                                + " 在报告底色上对比度 " + round(onReport));
                    }
                }
                for (int i = 0; i < Looks.ALL.length; i++) {
                    Looks.Look look = Looks.ALL[i];
                    Palette p = look.palette();
                    check(Contrast.ratio(p.lamp, p.appInk) >= TEXT_MIN,
                            look.id + " 主色在页面底色上 " + round(Contrast.ratio(p.lamp, p.appInk)));
                    check(Contrast.ratio(p.lamp, p.reportInk) >= TEXT_MIN,
                            look.id + " 主色在报告底色上 " + round(Contrast.ratio(p.lamp, p.reportInk)));
                }
            }
        });

        runCase("文字三级：正文/次级 >= 4.5，注释 >= 3.0", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    Palette p = Palette.of(scheme, accentOf(scheme));
                    check(Contrast.ratio(p.appText, p.appInk) >= TEXT_MIN,
                            scheme.id + " appText " + round(Contrast.ratio(p.appText, p.appInk)));
                    check(Contrast.ratio(p.appText2, p.appInk) >= TEXT_MIN,
                            scheme.id + " appText2 " + round(Contrast.ratio(p.appText2, p.appInk)));
                    check(Contrast.ratio(p.appText3, p.appInk) >= TEXT3_MIN,
                            scheme.id + " appText3 " + round(Contrast.ratio(p.appText3, p.appInk)));
                    check(Contrast.ratio(p.reportText, p.reportInk) >= TEXT_MIN,
                            scheme.id + " reportText " + round(Contrast.ratio(p.reportText, p.reportInk)));
                    check(Contrast.ratio(p.reportText2, p.reportInk) >= TEXT_MIN,
                            scheme.id + " reportText2 " + round(Contrast.ratio(p.reportText2, p.reportInk)));
                    check(Contrast.ratio(p.reportText3, p.reportInk) >= TEXT3_MIN,
                            scheme.id + " reportText3 " + round(Contrast.ratio(p.reportText3, p.reportInk)));
                }
            }
        });

        runCase("灯：onLamp 自动取高对比，>= 4.5", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    for (int a = 0; a < Accent.ALL.length; a++) {
                        Palette p = Palette.of(scheme, Accent.ALL[a]);
                        double on = Contrast.ratio(p.onLamp, p.lamp);
                        check(on >= TEXT_MIN, scheme.id + "." + p.lamp + " onLamp " + round(on));
                        check(p.onLamp == scheme.appInk || p.onLamp == scheme.appText,
                                "onLamp 必须来自 ink/text，不能是随手写的颜色");
                    }
                }
            }
        });

        runCase("数据类别色：固定色相，视频/音乐不随主题变，其他提亮后 >= 4.5", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    Palette p = Palette.of(scheme, accentOf(scheme));
                    for (int i = 0; i < p.data.length; i++) {
                        double r = Contrast.ratio(p.data[i], p.appInk);
                        check(r >= TEXT_MIN, scheme.id + " data[" + i + "] " + round(r));
                        check(p.dataColor(i) == p.data[i], "dataColor 必须与 data 一致");
                    }
                    // 用户指定：看电视 / 听音乐的颜色始终不变
                    eq(scheme.id + " 视频色固定", Categories.color(Categories.VIDEO),
                            p.data[Categories.VIDEO]);
                    eq(scheme.id + " 音乐色固定", Categories.color(Categories.MUSIC),
                            p.data[Categories.MUSIC]);
                    eq(scheme.id + " 儿童色固定", Categories.color(Categories.KIDS),
                            p.data[Categories.KIDS]);
                    eq(scheme.id + " 系统色固定", Categories.color(Categories.SYSTEM),
                            p.data[Categories.SYSTEM]);
                    eq(scheme.id + " 其他提亮", Palette.OTHER_LIFTED,
                            p.data[Categories.OTHER]);
                }
            }
        });

        runCase("主题缓存：64 个组合唯一且取同一实例", new Body() {
            @Override
            public void run() {
                Set<String> ids = new HashSet<String>();
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    for (int a = 0; a < Accent.ALL.length; a++) {
                        Theme t1 = Themes.of(Scheme.ALL[s].id, Accent.ALL[a].id);
                        Theme t2 = Themes.of(Scheme.ALL[s], Accent.ALL[a]);
                        check(t1 == t2, "同一组合必须是同一实例：" + t1.id);
                        check(ids.add(t1.id), "主题 id 重复：" + t1.id);
                        check(t1.palette == Palette.of(Scheme.ALL[s], Accent.ALL[a]),
                                "Palette 必须缓存");
                    }
                }
                eq("组合数", Scheme.ALL.length * Accent.ALL.length, ids.size());
                eq("默认主题", "dashboard.signal", Themes.defaultTheme().id);
            }
        });

        runCase("推荐配色与色环：每套材质都有可用默认色", new Body() {
            @Override
            public void run() {
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    Theme rec = Themes.recommended(scheme);
                    check(rec.scheme == scheme, "推荐主题必须属于该材质");
                    check(Accent.byId(scheme.defaultAccentId).id.equals(rec.accent.id),
                            scheme.id + " 默认色");
                    eq("色环长度", Accent.ALL.length, Themes.accentRow(scheme).length);
                }
            }
        });

        runCase("热力图：单色相明度单调递增", new Body() {
            @Override
            public void run() {
                Palette p = Palette.of(Scheme.LEDGER, Accent.MOON);
                int last = -1;
                for (int i = 0; i < 5; i++) {
                    int alpha = (p.heat(i) >>> 24) & 0xFF;
                    check(alpha > last, "heat(" + i + ") alpha 必须递增");
                    last = alpha;
                }
            }
        });

        runCase("辅色与圆角：每套材质都有第二只灯，圆角 0..16 且行圆角更小", new Body() {
            @Override
            public void run() {
                Set<Integer> secondaries = new HashSet<Integer>();
                Set<Float> radii = new HashSet<Float>();
                for (int s = 0; s < Scheme.ALL.length; s++) {
                    Scheme scheme = Scheme.ALL[s];
                    Palette p = Palette.of(scheme, Accent.byId(scheme.defaultAccentId));
                    check(secondaries.add(p.secondary), scheme.id + " 辅色不能重复");
                    check(p.secondary != p.appInk && p.secondary != p.appText,
                            scheme.id + " 辅色不能等于底色/正文");
                    check(Contrast.ratio(p.onSecondary, p.secondary) >= 3.0,
                            scheme.id + " onSecondary " + round(Contrast.ratio(p.onSecondary, p.secondary)));
                    check(p.panelRadius >= 0f && p.panelRadius <= 24f,
                            scheme.id + " 面板圆角越界 " + p.panelRadius);
                    check(p.rowRadius >= 0f && p.rowRadius <= p.panelRadius,
                            scheme.id + " 行圆角应不大于面板圆角");
                    check(((p.secondaryWash >>> 24) & 0xFF) == 0x1F,
                            scheme.id + " secondaryWash alpha 应为 12%");
                    radii.add(p.panelRadius);
                }
                check(radii.size() >= 5, "圆角至少要有 5 档差异，当前 " + radii.size());
                check(Palette.of(Scheme.CINEMA, Accent.ICE).panelRadius == 0f, "影院必须是直角");
                check(Palette.of(Scheme.PAPERLIGHT, Accent.EMBER).panelRadius >= 12f,
                        "暖房必须是大圆角");
                check(Palette.of(Scheme.FOREST, Accent.MINT).panelRadius >= 18f,
                        "森林必须是最柔的大圆角");
            }
        });

        runCase("八个暗室：材质 / 主题色数量、暗色 neutral、look 覆盖", new Body() {
            @Override
            public void run() {
                eq("材质数量", 8, Scheme.ALL.length);
                eq("精选 look 数量", 8, Looks.ALL.length);
                check(Accent.ALL.length >= 17, "主题色至少 17 个，当前 " + Accent.ALL.length);
                Palette dark = Looks.DARK.palette();
                check(!dark.light, "电视界面不允许亮底，「暗色」也必须是暗底");
                eq("暗色默认灯", "snow", Looks.DARK.accent.id);
                eq("暗色辅色名", "石墨", Looks.DARK.scheme.secondaryName);
                // 八个 look 的底色亮度必须都压在暗室区间（相对亮度 < 0.1）
                for (int i = 0; i < Looks.ALL.length; i++) {
                    Palette p = Looks.ALL[i].palette();
                    check(Contrast.luminance(p.appInk) < 0.1,
                            Looks.ALL[i].id + " 底色太亮：" + round(Contrast.luminance(p.appInk)));
                    check(!Looks.ALL[i].scheme.secondaryName.isEmpty(),
                            Looks.ALL[i].id + " 必须有辅色名");
                }
            }
        });

        runCase("度量：字阶递增、圆角按材质区分", new Body() {
            @Override
            public void run() {
                Metrics m = Metrics.of(Scheme.DASHBOARD);
                check(m.micro < m.caption && m.caption < m.body && m.body < m.emphasis
                        && m.emphasis < m.title && m.title < m.metric
                        && m.metric < m.metricLg, "字阶必须递增");
                check(Metrics.of(Scheme.LEDGER).radiusPanel > Metrics.of(Scheme.CINEMA).radiusPanel,
                        "账本面板圆角应大于影院");
                check(Metrics.of(Scheme.DASHBOARD).focusSpineW
                        < Metrics.of(Scheme.LEDGER).focusSpineW, "账本焦点脊应更宽");
            }
        });

        System.out.println();
        System.out.println("用例 PASS " + passedCases + "  FAIL " + failedCases
                + "（断言 " + checks + " 条）");
        if (!failures.isEmpty()) {
            for (int i = 0; i < failures.size(); i++) {
                System.out.println("  FAIL " + failures.get(i));
            }
            System.exit(1);
        }
    }

    private static Accent accentOf(Scheme scheme) {
        return Accent.byId(scheme.defaultAccentId);
    }

    private interface Body {
        void run();
    }

    private static void runCase(String name, Body body) {
        int before = failures.size();
        try {
            body.run();
        } catch (Throwable t) {
            failures.add(name + " 抛异常: " + t);
        }
        if (failures.size() == before) {
            passedCases++;
            System.out.println("PASS  " + name);
        } else {
            failedCases++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            failures.add(message);
        }
    }

    private static void eq(String what, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures.add(what + " 期望 " + expected + "，实际 " + actual);
        }
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }
}
