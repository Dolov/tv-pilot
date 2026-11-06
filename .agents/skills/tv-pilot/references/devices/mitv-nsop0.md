# MiTV-NSOP0 实测档案

> 采集时间：2026-11-04，来源：局域网 ADB 只读探测。

## 硬件/系统

| 项 | 值 |
|---|---|
| 型号 | MiTV-NSOP0 |
| 品牌 | Xiaomi |
| Android | 9（SDK 28） |
| 固件 | `22.7.26.1365`（2022-07-26） |
| fingerprint | `Xiaomi/furyroad/furyroad:9/PPR1.180610.011/1365:user/release-keys` |
| 屏幕 | 1920×1080，320dpi |
| 应用总数 | 108（含 114 个带残留数据的包） |
| 默认桌面 | `com.mitv.tvhome`（PatchWall） |

## 网络

- 局域网 IP：示例 `192.168.1.123`（真实值写 `.env` 的 `TV_IP`）
- AirPlay 名称：`<你的电视名>`，RAOP MAC：`<RAOP_MAC>`（从 RAOP 广播读，因机而异；可选记入 `.env`）
- 用 `scripts/find-tv.sh` 可通过 RAOP 广播名反查 IP

## 儿童模式

宿主 `com.xiaomi.mibox.lockscreen` v2.1.1，配置见 `references/child-mode.md`。实测过儿童模式（时长/密码因家庭而异，不在此记录）；开机自动进儿童模式=1。

## 广告/统计组件

见 `references/ad-packages.md`，本机存在：`com.miui.systemAdSolution`、`com.miui.analytics`、`com.miui.tv.analytics`、`com.xiaomi.statistic`、`com.xiaomi.mitv.tvpush.tvpushservice`、`com.mitv.shoplugin`。

## 已装应用

完整清单（109 包，系统/三方/广告分级）见 `references/devices/mitv-nsop0-apps.md`，
漂移检查 `bash scripts/apps-inventory.sh diff`。注意 `pm list packages -3` 里有 8 个是出厂预装到 /data 的组件
（关怀模式、内容中心、商城插件、办公模式等），不等于"用户后装的"。

## 已知坑

- 默认桌面有 `com.mitv.tvhome.permission.HOME_STATE` 保护，换桌面不要直接禁它，先装好替代桌面并验证
- `screen_off_timeout=2147483647`（永不休眠），改儿童模式相关设置时注意别误改
- 部分系统设置键值明文存密码（`child_mode_digital_pwd`），演示/截图注意打码
- `adb devices` 偶发 "device not found" 但端口开着：`adb kill-server` 后重连即可
- 屏保广播 `com.duokan.intent.action.show.screensaver` 偶尔要发两次才生效

## 二次采集补充（2026-11-04）

| 项 | 值 |
|---|---|
| SoC | Novatek（`ro.board.platform=furyroad`，armeabi-v7a 32 位） |
| 面板 | `ro.boot.mi.panel_size=65`、`panel_vendor=csot`；boot 属性标 `3840x2160`，但系统显示模式只有 1920×1080@60，且未上报 HDR |
| 内存 | 1.43GB；23 天不重启时占用 ~90%、swap 512MB 用掉 87% |
| 存储 | /data 26GB，已用 8.3GB（33%） |
| Wi-Fi | 2.4G「长的好看的才能连」144Mbps；5G 可连但常掉 |
| 屏保 | `com.mitv.screensaver/.RenderActivity`，8 分钟无操作进入 |
| 字体 | `/system/fonts/MILanPro_*.ttf`（中文）；屏保 APK 内 `assets/fonts/MIUI_EX_Light_0.ttf`（数字） |

## 开机/重启行为（关键）

- **所有第三方应用开机被置 `stopped=true`**（实测 18/18），`BOOT_COMPLETED` 收不到 →
  见 `references/boot-autostart.md`
- 重启后 ADB **延时复位**：前 ~30 秒可用，随后端口 refuse，授权密钥也失效
- `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` 在本机可用
- 有小米「应用合规检查」（`AppComplianceUtils` / `LocalAppBlockCreater`）：侧载包会被判
  `compliance:false`，但实测不影响安装与手动启动
