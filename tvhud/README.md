# TV HUD（`com.dsh.tvhud`）

跑在电视上的小 App：屏保右上角角标 + 今日使用台账 + 使用记录。纯 Java + 系统 API，没有 Gradle / Android Studio，`build.sh` 直接出 APK。

## 页面与组件

| 组件 | 作用 |
|---|---|
| `HomeActivity` | 启动页菜单：今日使用 / 使用记录 / AI 语音助手（占位）/ 设置（占位）。加功能改 `ITEMS` |
| `MainActivity` | 台账详情：合计、分类图例、0:00→24:00 时间轴、明细；每分钟刷新，保持焦点与滚动 |
| `HudService` | 唯一业务服务：1s 采样使用状态、屏保时挂悬浮窗、按天归档、ADB/无障碍自愈 |
| `HudView` | 自绘角标：开机状态 / 遥控器电量 / 今日使用长条（无文字，颜色即图例） |
| `UsageDay` | 当天分段模型（`[start,end)` + 类别），落盘 `SharedPreferences("hud")` |
| `HistoryStore` | 按天归档到 `files/history/yyyy-MM-dd.json`，保留 365 天 |
| `Categories` | 包名/类名 → 类别；改规则必须 bump `UsageDay.SCHEME` |
| `PlayEntry` | 从米家上报日志解析「片名/集数」 |
| `CtrlReceiver` / `BootReceiver` / `BootAccessibilityService` | 广播控制、开机广播、无障碍开机自启（MIUI 的 `stopped=true` 会让 `BOOT_COMPLETED` 收不到） |

## 数据在哪

- 当天：`SharedPreferences("hud")` 的 `usage_day_start` / `usage_scheme` / `usage_json` / `play_day_start` / `play_json` / `adb_keep` / `usage_preview`
- 历史：`/sdcard/Android/data/com.dsh.tvhud/files/history/yyyy-MM-dd.json`（adb 可读，卸载即清）
- 播放内容唯一可靠来源：`/sdcard/miio/miothost.txt`（会轮转，必须运行时就抓）

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

## 加一个页面/菜单项

1. `HomeActivity.ITEMS` 加一行 `new Item("名字", "一句话说明", ACTION_XXX)`；
2. 在 `open()` 里分发到新 Activity / 或复用 `MainActivity`；
3. 需要新数据时走 `HudService.peekUsage()/peekPlays()`，不要在 Activity 里另起一套采样；
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
- 归档 JSON 没有 schema 版本；`UsageDay.SCHEME` 只保护当天 prefs，改分类后历史颜色可能对不上。
