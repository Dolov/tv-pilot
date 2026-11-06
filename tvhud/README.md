# TV HUD（`com.dsh.tvhud`）

跑在电视上的小 App：屏保右上角角标 + 今日使用台账 + 使用记录 + 周/月/年使用报告 +
电子宠物「小电视兽」。纯 Java + 系统 API，没有 Gradle / Android Studio，`build.sh` 直接出 APK。

## 电子宠物（P0）

- 打开电视看视频时，`HudService` 按 `Categories.VIDEO` 累计场次：15 分钟一场、每天 3 场；
  剩 3 分钟 / 1 分钟弹右上角气泡，到点由 `PetGateActivity` 全屏拦截。
- 两个出口：休息（+3 星、记 1 场）或答 3 题对 2 题（本场 +5 分钟，每天最多 2 次，
  3 场用完不再出题）。
- 宠物房间：吃饭/喝水/摸摸/洗澡/刷牙，体重 0–100 影响体型，牙齿 0–100 影响表情，
  Lv 10 换牙；Lv 3/10/24 解锁帽子/衣服/翅膀；Lv 3/10/24/45 进化。
- 调试广播（`adb shell am broadcast -a com.dsh.tvhud.PET_...`）：
  `PET_GATE` 强制到点、`PET_NEAR_END --ei seconds 180` 模拟剩 3 分钟、
  `PET_DEBUG --ei exp 100 --ei stars 40` 改数值、`PET_PARENT_ON/OFF` 家长时间、
  `PET_RESET` 重置、`PET_SETTINGS` 打开家长设置。

## 页面与组件

| 组件 | 作用 |
|---|---|
| `HomeActivity` | 启动页菜单：今日使用 / 使用记录 / 使用报告 / 我的宠物 / AI 语音助手（占位）/ 设置（占位）。加功能改 `ITEMS` |
| `MainActivity` | 台账详情：合计、分类图例、0:00→24:00 时间轴、明细；每分钟刷新，保持焦点与滚动 |
| `ReportsActivity` | 使用报告：周报 / 月报 / 年报三套 Canvas 布局；←→ 回看、↑↓ 切粒度、OK 回本期；长按只认第一下，加载中保留上一帧，失败按 OK 重试 |
| `ReportEngine` / `ReportMath` / `DayAgg` / `Report` | 365 天归档聚合、上期对比、周期条与洞察；后台线程取快照聚合，纯计算与 Android 读取分层 |
| `ChartAxis` / `BarStack` | 纵轴刻度与堆叠柱几何（周/月/年共用，纯函数，`tests/java` 覆盖） |
| `PetActivity` | 宠物房间：2.5D 宠物 + 吃饭/喝水/摸摸/洗澡/刷牙 + 换装 + 等级/星星 |
| `PetGateActivity` | 场次到点拦截：休息 / 答 3 题续 5 分钟；60 秒默认休息；返回键无效 |
| `PetSettingsActivity` | 家长设置：4 位 PIN（默认 2580）+ 每场时长 / 场次 / 答题续时 / 家长时间 / 重置 |
| `PetView` / `PetState` / `PetEngine` / `Budget` / `QuizBank` / `PetStore` / `PetBubble` | 2.5D 骨骼渲染、纯逻辑、场次预算、题库、存档、观看中预警气泡 |
| `HudService` | 唯一业务服务：1s 采样使用状态、屏保时挂悬浮窗、按天归档、ADB/无障碍自愈；宠物场次计时/预警/拦截也在这里 |
| `HudView` | 自绘角标：开机状态 / 遥控器电量 / 今日使用长条（无文字，颜色即图例） |
| `UsageDay` | 当天分段模型（`[start,end)` + 类别），落盘 `SharedPreferences("hud")` |
| `HistoryStore` | 按天归档到 `files/history/yyyy-MM-dd.json`，保留 365 天；带 `v` 分类版本号 |
| `Categories` | 包名/类名 → 类别；改规则必须 bump `UsageDay.SCHEME` |
| `PlayEntry` | 从米家上报日志解析「片名/集数」 |
| `CtrlReceiver` / `BootReceiver` / `BootAccessibilityService` | 广播控制、开机广播、无障碍开机自启（MIUI 的 `stopped=true` 会让 `BOOT_COMPLETED` 收不到） |

## 数据在哪

- 当天：`SharedPreferences("hud")` 的 `usage_day_start` / `usage_scheme` / `usage_json` / `play_day_start` / `play_json` / `adb_keep` / `usage_preview`
- 宠物：`SharedPreferences("pet")`（状态 / 场次预算 / 家长设置）；题库 `assets/quiz.json`
- 历史：`/sdcard/Android/data/com.dsh.tvhud/files/history/yyyy-MM-dd.json`（adb 可读，卸载即清）
- 播放内容唯一可靠来源：`/sdcard/miio/miothost.txt`（会轮转，必须运行时就抓）

### 写入节奏（省 NAND 磨损）

| 数据 | 什么时候写 | 崩溃最多丢 |
|---|---|---|
| `SharedPreferences`（当天） | 每 60 秒，且数据真的变了 | 60 秒 |
| 归档 JSON（当天） | 5 分钟节流 + 数据变了；跨天/观看结束/服务销毁时强制写 | 5 分钟（SP 仍兜住 60 秒） |

- 判断「变了」用的是 `UsageDay.revision()`：只有新增/延长分段才 +1，空转不写
  （`SharedPreferences` 与归档各自比对版本号，电视开着没人看时两者都是 0 次写）。
- 跨天定稿（`history/昨天.json` 的最终版）永远是强制写，不受节流影响。
- 归档写失败或当天还没有数据时 `HistoryStore.write` 返回 false，不占用节流窗口，
  下一份数据到了会立刻写（`archive written` 只在真正写成功时打）。
- 服务启动时 `backfillPendingArchive()` 会补写「午夜关机导致没定稿的那一天」，
  合并规则见 `ArchiveBackfill`（分段取更全的一份、播放记录取并集）。
- 读取归档时 `HistoryStore.verify()` 会自检，坏文件打 `ARCHIVE_CORRUPT`，E2E 当失败。
- 调策略：`ArchivePolicy.DEFAULT_INTERVAL_MS`（默认 5 分钟）。

## 图标与 TV banner

- 源图：`design/icon-512.png`（512×512，白底彩色三瓣）。
- 桌面图标：`res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`（48/72/96/144/192）。
- TV 桌面 banner：`res/drawable-xhdpi/tv_banner.png`（320×180，黑底 + 图标 + TV HUD）。
- `AndroidManifest.xml` 的 `<application>` 与启动页 `HomeActivity` 都引用 `@mipmap/ic_launcher`
  和 `@drawable/tv_banner`；`build.sh` 会先 `aapt2 compile --dir res` 再 link。
- 换图标后 `make apk` + 重装；MIUI 桌面可能缓存旧图标，必要时重启电视或强制停止桌面。

## 构建与部署

```bash
# 首次：下载 build-tools / android.jar（需要 JDK 17/21）
bash ../.agents/skills/tv-pilot/scripts/build-tools.sh ensure

bash build.sh                                   # 产出 ../.build/out/tvhud.apk
bash ../.agents/skills/tv-pilot/scripts/tv-hud.sh install   # 部署（权限/无障碍顺序内置）
```

构建链：`javac → d8 → aapt2 link → zip classes.dex → zipalign → apksigner`。约束：

- **keystore 固定**（`../.agents/skills/tv-pilot/.tools/keystore/tvhud.jks`）：换签名只能卸载重装，appops/无障碍/数据全丢。
- `javac` 用 JDK ≤ 21（26 已移除 `-source 8`）。
- `targetSdk 22` 是刻意：免运行时权限/前台服务限制，让 `TYPE_SYSTEM_ALERT` 直接可用。
- 版本号来自仓库根 `version.properties`（同时改 Manifest 里的 `versionCode/versionName`）。

## 测试

- `make test`：离线。纯计算逻辑（周期/聚合/洞察/百分比/片名解析/堆叠柱几何/纵轴刻度/
  覆盖区间/周内平均/旧归档兼容/快照）在 `tests/java` 里用纯 Java 断言跑，不需要 Android/电视；
  改报告逻辑先补用例。
- `make e2e`：真机冒烟。装当前 APK → 打开报告 → 周/月/年导航 + 回本期 + 返回，
  断言 `report load`/无崩溃/无 `REPORT_INVARIANT`，截图落 `.build/e2e/`。
- 报告聚合的派生前记得扩 `ReportMath.checkInvariants`（真机日志一出现 `REPORT_INVARIANT`
  E2E 就失败）；Canvas 里可算的几何抽成纯函数（参考 `BarStack` / `ChartAxis`），别在 View 里复制公式。
- 派生数据（KPI/月汇总/热力）在 `onReportChanged()` 里算一次，不要放进 `onDraw`。
- 分层与「加用例」说明见 `tests/README.md`。

## 加一个页面/菜单项

1. `HomeActivity.ITEMS` 加一行 `new Item("名字", "一句话说明", ACTION_XXX)`；
2. 在 `open()` 里分发到新 Activity / 或复用 `MainActivity`；
3. 需要新数据时走 `HudService.peekUsage()/peekPlays()`（主线程）或
   `peekUsageSnapshot()`（后台线程，避免跟每秒 append 抢同一个 ArrayList），
   不要在 Activity 里另起一套采样；
4. 新 Activity 用 `@android:style/Theme.Black.NoTitleBar.Fullscreen`，字号用 dp（不要 sp）。

## 机型的耦合（换品牌要改这里）

当前实现以小米 MIUI TV 为主：屏保包名 `com.mitv.screensaver`、桌面 `com.mitv.tvhome`、
播放日志 `/sdcard/miio/miothost.txt`、包名分类白名单都在代码里。给别的电视适配时：

- 屏保包名 / 播放日志 / 分类表 → 抽成 `DeviceAdapter` 或 assets 配置（待办，见 `docs/architecture.md`）；
- 或者只复用 skill 的只读能力，不在该机型上部署 TV HUD。

## 已知限制

- `CtrlReceiver` / `MainActivity` 在 Manifest 里 `exported=true` 且无自定义权限：电视上任意 App 可发 `RESET_USAGE` / `ADB_KEEP_ON` 等广播。自用可接受，公开分发前应收口（见 `../../../SECURITY.md`）。
- `adb_keep` 只在 `HudService.onCreate` 读取：运行中发 `ADB_KEEP_ON/OFF` 改 prefs 后要进程重启才生效。
- 无前台服务，1s 采样在主线程；长期常驻在低端机上有卡顿风险。
- 归档 JSON 的 `v` 只从 1.12 起写入；更早的归档按 scheme 3 兼容，改分类后旧数据颜色仍可能对不上。
