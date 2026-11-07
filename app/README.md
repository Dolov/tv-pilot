# tv-pilot（`com.dsh.tvhud`）

电视端 App **tv-pilot**（包名 `com.dsh.tvhud`，包名保持不变以兼容覆盖升级与本地数据）：
屏保右上角角标 + 使用记录（今日台账 + 历史回看）+ 周/月/年使用报告 + 电子宠物「小电视兽」。
纯 Java + 系统 API，没有 Gradle / Android Studio，`build.sh` 直接出 APK。

## 源码分层（AI agent 先看这里）

```text
app/src/com/dsh/tvhud/
├── home/     HomeActivity：首页菜单与导航
├── usage/    UsageDay / HistoryStore / Categories / PlayEntry / MainActivity / TimelineView
├── report/   ReportsActivity / ReportEngine / 周月年 Canvas / ChartAxis / BarStack
├── hud/      HudService / HudView / CtrlReceiver / BootReceiver / BootAccessibilityService
└── pet/      PetActivity / PetGate / PetSettings / PetReport / PetView / PetEngine / Shop / PetStore
```

依赖方向：`home → {usage, report, pet, hud}`；`hud → {usage, pet}`；`usage` / `report` 会
`startService(HudService)` 并读 `peekUsage()/peekPlays()`（Android Service 边界允许的反向）；
`report → usage`；`pet → usage`（只读 `dayStartOf`）。纯逻辑（`PetEngine` / `Budget` /
`QuizBank` / `Shop` / `PetJournal` / `ReportMath`）不 import Android。新增功能先决定落在哪个包，再补
`tests/java/<包>/` 用例。

## 电子宠物（P0/P1/P2）

- **时间规则**：看视频时 `HudService` 只累计 `Categories.VIDEO`：默认 15 分钟一场、每天 3 场
  （周末自动 +1，可关）；剩 3 分钟 / 1 分钟弹右上角气泡，到点由 `PetGateActivity` 全屏拦截。
  两个出口：休息（+3 星、记 1 场）或答 3 题对 2 题（本场 +5 分钟，每天最多 N 次，3 场用完不再出题）。
- **答题**：语音读题 + 4 个大卡片，第一次答错还有一次机会，答错不惩罚、无失败音效。
- **照顾**：吃饭/喝水/摸摸/洗澡/刷牙；体重 0–100 影响体型，牙齿 0–100 影响表情，Lv 10 换牙。
- **商店/衣柜**：星星买狗粮/零食/甜食、帽子/衣服/翅膀/房间装饰；原价退；装饰最多摆 3 个。
- **成长**：Lv = EXP/10，Lv 3/10/24/45 进化；Lv24 按偏好分智慧/疾风/美食三线（30 星可换）；
  Lv45 后每 7 天检定变异（6 部位 × 6 配色，进图鉴）。
- **退化（可逆）**：连续 3 天不达标只回退外观 2 天、EXP −20 不掉阶；次日达标即恢复；
  连续 2 天没照顾会躲进被子，一次互动就回来。家长可整体关闭。
- **家长设置**：4 位 PIN（默认 2580）；每场时长 / 每天场次 / 答题续时与次数 / 周末加场 /
  允许时段（只拦新开场）/ 退化 / 温和模式 / 家长时间 / 临时加场 / 宠物周报 / 重置。
- **宠物周报**：本周观看时长、场次、按时休息、超时、答题正确率、成长/分支/变异；
  只统计时长与行为，不记录看了什么、不上传。
- 调试广播（`adb shell am broadcast -a com.dsh.tvhud.PET_...`）：
  `PET_GATE [--ez window true]` 强制到点/时间窗、`PET_NEAR_END --ei seconds 180` 模拟剩 3 分钟、
  `PET_DEBUG --ei exp 450 --ei stars 200` 改数值、`PET_SHOP --es item hat_cap` 买/喂、
  `PET_BRANCH --ei branch 1`、`PET_MUTATE`、`PET_DEGRADE`、`PET_GIVE_STARS --ei stars 50`、
  `PET_WINDOW_ON/OFF`、`PET_WEEKEND_ON/OFF`、`PET_GENTLE_ON/OFF`、`PET_ROOM`、`PET_REPORT`、
  `PET_STATE`（当前存档打到 logcat）、`PET_PARENT_ON/OFF`、`PET_RESET`、`PET_SETTINGS`。

## 页面与组件

| 组件 | 作用 |
|---|---|
| `HomeActivity` | 启动页菜单：使用记录 / 使用报告 / 我的宠物 / AI 语音助手（占位）/ 设置（占位）。加功能改 `ITEMS` |
| `MainActivity` | 使用记录：今日台账（总时长/分类/时间轴/明细）+ ←→ 按天回看历史；每分钟刷新，保持焦点与滚动 |
| `ReportsActivity` | 使用报告：周报 / 月报 / 年报三套 Canvas 布局；←→ 回看、↑↓ 切粒度、OK 回本期；长按只认第一下，加载中保留上一帧，失败按 OK 重试 |
| `report/ReportEngine` / `ReportMath` / `DayAgg` / `Report` | 365 天归档聚合、上期对比、周期条与洞察；后台线程取快照聚合，纯计算与 Android 读取分层 |
| `report/ChartAxis` / `BarStack` | 纵轴刻度与堆叠柱几何（周/月/年共用，纯函数，`tests/java` 覆盖） |
| `pet/PetActivity` | 宠物房间：2.5D 宠物 + 照顾 / 商店 / 衣柜 / 房间 四个分区 + 等级/星星 |
| `pet/PetGateActivity` | 场次到点拦截：休息 / 答 3 题续 5 分钟；60 秒默认休息；时间窗模式；返回键无效 |
| `pet/PetSettingsActivity` | 家长设置：4 位 PIN（默认 2580）+ 时间规则 / 行为开关 / 周报入口 / 重置 |
| `pet/PetReportActivity` | 宠物周报：本周时长、场次、按时休息、超时、答题正确率、进化进度 |
| `pet/PetView` / `PetState` / `PetEngine` / `Budget` / `QuizBank` / `PetStore` / `PetBubble` | 2.5D 骨骼渲染（分支/变异/退化/装备/装饰）、纯逻辑、场次预算、题库、存档、观看中预警气泡 |
| `pet/Shop` / `PetJournal` / `PetUi` / `PetIconView` | 商店与衣柜纯逻辑、天级流水、统一视觉、Canvas 小图标 |
| `hud/HudService` | 唯一业务服务：1s 采样使用状态、屏保时挂悬浮窗、按天归档、ADB/无障碍自愈；宠物场次计时/预警/拦截也在这里 |
| `hud/HudView` | 自绘角标：开机状态 / 遥控器电量 / 今日使用长条（无文字，颜色即图例） |
| `usage/UsageDay` | 当天分段模型（`[start,end)` + 类别），落盘 `SharedPreferences("hud")` |
| `usage/HistoryStore` | 按天归档到 `files/history/yyyy-MM-dd.json`，保留 365 天；带 `v` 分类版本号 |
| `usage/Categories` | 包名/类名 → 类别；改映射 bump `Categories.RULES_REV`（只重建今天），改分段口径才 bump `UsageDay.SCHEME` |
| `usage/PlayEntry` | 从米家上报日志解析「片名/集数」 |
| `hud/CtrlReceiver` / `BootReceiver` / `BootAccessibilityService` | 广播控制、开机广播、无障碍开机自启（MIUI 的 `stopped=true` 会让 `BOOT_COMPLETED` 收不到） |

## 数据在哪

- 当天：`SharedPreferences("hud")` 的 `usage_day_start` / `usage_scheme` / `usage_json` / `play_day_start` / `play_json` / `adb_keep` / `usage_preview`
- 宠物：`SharedPreferences("pet")`（状态 / 场次预算 / 家长设置 / 14 天流水）；题库 `assets/quiz.json`
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
- TV 桌面 banner：`res/drawable-xhdpi/tv_banner.png`（320×180，黑底 + 图标 + tv-pilot）。
- `AndroidManifest.xml` 的 `<application>` 与启动页 `HomeActivity` 都引用 `@mipmap/ic_launcher`
  和 `@drawable/tv_banner`；`build.sh` 会先 `aapt2 compile --dir res` 再 link。
- 换图标后 `make apk` + 重装；MIUI 桌面可能缓存旧图标，必要时重启电视或强制停止桌面。

## 构建与部署

```bash
# 首次：下载 build-tools / android.jar（需要 JDK 17/21）
bash ../.agents/skills/tv-pilot/scripts/build-tools.sh ensure

bash build.sh                                   # 产出 ../.build/out/tv-pilot.apk
bash ../.agents/skills/tv-pilot/scripts/app.sh install   # 部署（权限/无障碍顺序内置）
```

构建链：`javac → d8 → aapt2 link → zip classes.dex → zipalign → apksigner`。约束：

- **keystore 固定**（`../.agents/skills/tv-pilot/.tools/keystore/tv-pilot.jks`）：换签名只能卸载重装，appops/无障碍/数据全丢。
- `javac` 用 JDK ≤ 21（26 已移除 `-source 8`）。
- `targetSdk 22` 是刻意：免运行时权限/前台服务限制，让 `TYPE_SYSTEM_ALERT` 直接可用。
- 版本号来自仓库根 `version.properties`（同时改 Manifest 里的 `versionCode/versionName`）。

## 测试

- `make test`：离线。纯计算逻辑在 `tests/java/report`（周期/聚合/洞察/几何）和
  `tests/java/pet`（等级/照顾/场次预算/题库/商店/分支/退化/变异/时间窗/流水）里用纯 Java
  断言跑，不需要 Android/电视；
  改哪层先补对应包的用例。
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
- 或者只复用 skill 的只读能力，不在该机型上部署 tv-pilot。

## 已知限制

- `hud/CtrlReceiver` / `usage/MainActivity` 在 Manifest 里 `exported=true` 且无自定义权限：电视上任意 App 可发 `RESET_USAGE` / `ADB_KEEP_ON` / `PET_*` 等广播。自用可接受，公开分发前应收口（见 `../../../SECURITY.md`）。
- `adb_keep` 只在 `HudService.onCreate` 读取：运行中发 `ADB_KEEP_ON/OFF` 改 prefs 后要进程重启才生效。
- 无前台服务，1s 采样在主线程；长期常驻在低端机上有卡顿风险。
- 归档 JSON 的 `v` 只从 1.12 起写入；更早的归档按 scheme 3 兼容，改分类后旧数据颜色仍可能对不上。
