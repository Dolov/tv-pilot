---
profile: xiaomi-mitv-nsop0
brand: xiaomi
model: MiTV-NSOP0
match: ["MiTV-NSOP0", "furyroad"]
sdk: 28
verified_at: 2026-11-04
verified_by: Dolov
capabilities:
  usage_stats: verified
  screensaver: verified
  child_mode: verified
  adb_keepalive: verified
  app_build: verified
  remote_battery: verified
---

# MiTV-NSOP0 设备档案

> 采集时间：2026-11-04，来源：局域网 ADB 只读探测。真实 IP/MAC/SSID 用占位符，本机值在 `.env`。

## 设备身份

| 项 | 值 |
|---|---|
| 型号 | MiTV-NSOP0 |
| 品牌 | Xiaomi |
| codename | furyroad |
| Android / SDK | 9（SDK 28） |
| 固件 | `22.7.26.1365`（2022-07-26） |
| fingerprint | `Xiaomi/furyroad/furyroad:9/PPR1.180610.011/1365:user/release-keys` |
| 屏幕 | 1920×1080，320dpi |
| 默认桌面 | `com.mitv.tvhome`（PatchWall） |
| 应用总数 | 108（含 114 个带残留数据的包） |

网络：

- 局域网地址：`<TV_IP>:5555`（真实值写 `.env` 的 `TV_IP`）
- AirPlay 名称 / RAOP MAC：`<你的电视名>` / `<RAOP_MAC>`（从 RAOP 广播读，因机而异）
- 用 `scripts/find-tv.sh` 可通过 RAOP 广播名反查 IP

二次采集补充（2026-11-04）：

| 项 | 值 |
|---|---|
| SoC | Novatek（`ro.board.platform=furyroad`，armeabi-v7a 32 位） |
| 面板 | `ro.boot.mi.panel_size=65`、`panel_vendor=csot`；boot 属性标 `3840x2160`，但系统显示模式只有 1920×1080@60，且未上报 HDR |
| 内存 | 1.43GB；23 天不重启时占用 ~90%、swap 512MB 用掉 87% |
| 存储 | /data 26GB，已用 8.3GB（33%） |
| Wi-Fi | 2.4G `<你的 Wi-Fi 名>` 144Mbps；5G 可连但常掉 |
| 屏保 | `com.mitv.screensaver/.RenderActivity`，8 分钟无操作进入 |
| 字体 | `/system/fonts/MILanPro_*.ttf`（中文）；屏保 APK 内 `assets/fonts/MIUI_EX_Light_0.ttf`（数字） |

## ADB 接入与坑

- 开关路径：`设置 → 账号与安全 → 安全 → ADB 调试`；打开后通常要重启电视，5555 才开始监听
- 重启后 **ADB 延时复位**：前 ~30 秒可用，随后端口 refuse、授权密钥也失效；由自研 App 用 `WRITE_SECURE_SETTINGS` 把 `adb_enabled` 写回（见 `references/operations/boot-autostart.md`）
- 授权框：首次连接在电视上选「允许」，建议勾「一律允许」；`unauthorized` 就重新按一次
- `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` 在本机可用
- 通用排查顺序见 `references/operations/enable-adb.md` 与 `scripts/ensure-tv.sh -v`

## 桌面与屏保

- 桌面：`com.mitv.tvhome`（PatchWall）；`com.mitv.tvhome.permission.HOME_STATE` 有保护，换桌面不要直接禁它
- 屏保：`com.mitv.screensaver/.RenderActivity`，8 分钟无操作进入；广播 `com.duokan.intent.action.show.screensaver` / `exit.screensaver`（偶尔要发两次才生效）
- 屏保上叠字：`SYSTEM_ALERT_WINDOW` 悬浮窗 + `TYPE_APPLICATION_OVERLAY`；屏保检测走 UsageStats 增量查询（细节与坑见 `references/operations/screensaver.md`）

## 使用统计来源

- `dumpsys usagestats` 可用：亮屏/熄屏事件 `SCREEN_INTERACTIVE` / `SCREEN_NON_INTERACTIVE`，前台包名与 class 名都有（`com.mitv.tvhome.DetailsActivity` 内嵌播放时播放器不进前台）
- 播放内容：米家日志 `/sdcard/miio/miothost.txt`（转义 JSON，`video_name` / `ci` / `total_ci` / `total_duration`）与 `MiTVOtService_PW` 日志；口径见 `references/operations/usage-stats.md`
- 不要用 `play_time_today_watchtime` 当观看时长（那是儿童模式计时）

## 儿童模式

- 宿主：`com.xiaomi.mibox.lockscreen` v2.1.1（`.childmode.*` Activity）
- 配置：`Settings.System` 明文键；实测过儿童模式，时长/密码因家庭而异，不在此记录
- 弱点：密码明文、答题可找回、到点只靠锁屏 UI 兜底（见 `references/operations/child-mode.md`）

## 可禁用包 / 广告组件

- 广告/统计候选：`com.miui.systemAdSolution`、`com.miui.analytics`、`com.miui.tv.analytics`、`com.xiaomi.statistic`、`com.xiaomi.mitv.tvpush.tvpushservice`、`com.mitv.shoplugin`
- 风险高、别直接动：`com.mitv.tvhome`（桌面）、`com.xiaomi.mitv.settings`、`com.mitv.contenttemplate`、`com.xiaomi.mitv.tvmanager`、`com.mitv.alarmcenter`
- 恢复：`cmd package install-existing <pkg>`；禁用统一用 `pm uninstall --user 0`（可逆）
- 完整包清单与分级见 `references/devices/xiaomi-mitv-nsop0-apps.md`；漂移检查 `bash scripts/apps-inventory.sh diff`

## 已知坑

- 所有第三方应用开机被置 `stopped=true`（实测 18/18），`BOOT_COMPLETED` 收不到 → `references/operations/boot-autostart.md`
- `screen_off_timeout=2147483647`（永不休眠），改儿童模式相关设置时注意别误改
- 部分系统设置键值明文存密码（`child_mode_digital_pwd`），演示/截图注意打码
- `adb devices` 偶发 "device not found" 但端口开着：`adb kill-server` 后重连即可
- 有小米「应用合规检查」（`AppComplianceUtils` / `LocalAppBlockCreater`）：侧载包会被判 `compliance:false`，但实测不影响安装与手动启动
- `pm list packages -3` 里有 8 个是出厂预装到 /data 的组件（关怀模式、内容中心、商城插件、办公模式等），不等于"用户后装的"

## 证据

```
$ adb shell getprop ro.product.model
MiTV-NSOP0                                   # 2026-11-04

$ adb shell getprop ro.build.version.sdk
28                                           # 2026-11-04

$ adb shell wm size
Physical size: 1920x1080                     # 2026-11-04

$ adb shell wm density
Physical density: 320                        # 2026-11-04

$ adb shell settings get system screen_off_timeout
2147483647                                   # 2026-11-04
```
