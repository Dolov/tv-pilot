# MiTV-NSOP0 应用清单（快照）

> **快照时间**：2026-11-04 21:31　**固件**：`22.7.26.1365`（Android 9）
> **总数**：109 个包 = 系统 90 + 第三方/预装数据分区 19
> 机器档案（硬件/坑/开机行为）见 `references/devices/xiaomi-mitv-nsop0.md`。

## 维护：怎么刷新这份清单

```bash
bash scripts/apps-inventory.sh diff     # 和本快照比对，列出新增/卸载的包（日常先跑这个）
bash scripts/apps-inventory.sh update   # 把当前状态写回 references/devices/xiaomi-mitv-nsop0-apps.txt
bash scripts/apps-inventory.sh list     # 只看当前清单（不比对）
```

约定：
- **OTA / 装了新 App / 卸载东西之后**跑一次 `diff`；有变化就 `update`，并把本文件顶部的「快照时间」一起改掉
- 新增的第三方包要在下面的表里补「用途」，临时不确定就写 `（用途待确认，label=xxx）`
- 机器是 `user` 版、无 root；包名以 `pm list packages` 为准，**不要凭记忆删包**

## 第三方 / 预装数据分区应用（19）

`pm list packages -3` 里有一半是**出厂预装但放在 /data** 的组件（关怀模式、内容中心、商城插件、
办公模式等），不是用户手动装的 —— 判断"是不是新装的"用 `scripts/apps-inventory.sh diff`。

| 包名 | 版本 | 能干什么 | 备注 |
|---|---|---|---|
| `com.cibn.tv` | 14.0.1.15 | CIBN 系视频入口，含优酷少儿内容；片源类 | 国内影视/少儿片源，广告较多 |
| `com.dsh.tvhud` | 1.1 | **自研**：屏保右上角角标（开机天数/时间 + 遥控器电量），附带 ADB 守护、无障碍开机自启 | 改动走 `references/operations/dev-workflow.md`；源码在工作区 `app/` |
| `com.gitv.dvb.live` | 2.3.11 | DVB 直播（地面/有线频道） | 直播频道入口 |
| `com.gitvdemo.video` | 16.10.0.221990 | 出厂演示视频 App | 基本没用，可作为禁用候选（先确认没人用） |
| `com.hongen.app.word.ott.vip` | 1.3.6 | 洪恩识字：识字课程/小游戏，部分内容要会员 | 教育类；受系统时长限制约束 |
| `com.ihuman.pinyin.ott.vip` | 1.9.4 | 洪恩拼音：拼音启蒙课程 | 教育类；与识字 App 同一厂商 |
| `com.kugou.android.tv` | 2.1.0.2 | 酷狗音乐：听歌/K 歌入口 | K 歌配合 `com.xiaomi.mitv.karaoke.service` |
| `com.lutongnet.nldmxXiaomiChildren` | 4.01.06 | 儿童思维训练（逻辑/数感启蒙） | 教育类 |
| `com.mitv.care` | 1.0.52 | 关怀模式宿主（label 写的是 adcare）：大字体/简化界面 | 大字体模式宿主，别禁 |
| `com.mitv.codec.update` | 7.1 | 播放器解码库更新（videoplayer_lib_update） | 系统组件，别动 |
| `com.mitv.contenttemplate` | 1.3.0 | 内容中心/首页模板，决定首页区块 | 禁了首页可能空/变形，先确认 |
| `com.mitv.shoplugin` | 9.9.9 | 商城插件（电商/广告位） | 可禁（见 ad-packages.md） |
| `com.miui.analytics` | 2.48.0 | MIUI 统计埋点 | 可禁 |
| `com.sohuott.tv.vod.xiaomi` | 6.8.2 | 搜狐视频 | 影视点播 |
| `com.tencent.qqmusictv` | 9.2.0.4 | QQ 音乐 | 听歌 |
| `com.xiaodianshi.tv.yst` | 1.8.9 | 云视听小电视：B 站内容（番剧/动画/UP 主） | 动画/番剧内容 |
| `com.xiaomi.eetv.litehome` | 50.0.1338 | 极简/办公模式首页（label 写「办公模式」） | 备选桌面，不是日常桌面 |
| `com.xiaomi.upnp` | 1.4.6 | UPnP/DLNA 投屏服务 | 手机投屏/局域网媒体用 |
| `com.xm.webcontent` | 3.19 | MiWebView 网页容器 | 系统组件，别动 |

可动性分级（哪些能禁）见 `references/operations/ad-packages.md`：
`com.miui.systemAdSolution` / `com.miui.analytics` / `com.miui.tv.analytics` / `com.xiaomi.statistic` /
`com.xiaomi.mitv.tvpush.tvpushservice` / `com.mitv.shoplugin` 可安全禁；`com.mitv.contenttemplate`、
`com.mitv.alarmcenter`、`com.xiaomi.mitv.tvmanager` 要先确认。

### 查某个 App 用了多久 / 什么时候在看

用 `bash scripts/usage.sh`（分应用时长、亮屏窗口、播放记录），口径见 `references/operations/usage-stats.md`。
注意：`com.mitv.screensaver` 的时长**不是**观看时长，别混进去。

## 关键系统包（动之前必看）

| 包名 | 说明 |
|---|---|
| `com.mitv.tvhome` | PatchWall 桌面（HOME 角色，别禁） |
| `com.xiaomi.mitv.settings` | 系统设置 |
| `com.mitv.mivideoplayer` | 视频播放器 |
| `com.xiaomi.mitv.systemui` | 系统 UI |
| `com.mitv.screensaver` | SmartScreenSaver 屏保（角标就画在它上面） |
| `com.xiaomi.mitv.remotecontroller.service` | 手机遥控器服务（Milink/UDT 收按键，不含手机电量） |
| `com.xiaomi.mibox.lockscreen` | 儿童模式宿主 |
| `com.xiaomi.mitv.tvmanager` | 电视管家 |
| `com.mitv.alarmcenter` | 闹钟/提醒 |
| `com.duokan.videodaily` | 今日头屏 |
| `com.xiaomi.mitv.karaoke.service` | K 歌（带一个无障碍服务） |
| `com.xiaomi.voicecontrol` | 语音控制（带一个无障碍服务） |
| `com.miui.systemAdSolution` | 系统广告引擎（可禁，见 ad-packages.md） |
| `com.xiaomi.mitv.tvpush.tvpushservice` | 推送弹窗（可禁） |
| `com.xiaomi.statistic` | 埋点上报（可禁） |
| `com.miui.tv.analytics` | 电视端统计（可禁） |

## 全部系统包（90）

```
android
android.ext.services
android.ext.shared
com.android.backupconfirm
com.android.bluetooth
com.android.captiveportallogin
com.android.certinstaller
com.android.companiondevicemanager
com.android.cts.ctsshim
com.android.cts.priv.ctsshim
com.android.defcontainer
com.android.documentsui
com.android.dreams.basic
com.android.externalstorage
com.android.htmlviewer
com.android.inputdevices
com.android.keychain
com.android.location.fused
com.android.packageinstaller
com.android.pacprocessor
com.android.printspooler
com.android.providers.calendar
com.android.providers.contacts
com.android.providers.downloads
com.android.providers.media
com.android.providers.settings
com.android.providers.tv
com.android.providers.userdictionary
com.android.proxyhandler
com.android.se
com.android.settings.intelligence
com.android.sharedstoragebackup
com.android.shell
com.android.statementservice
com.android.systemui
com.android.tv.settings
com.android.vpndialogs
com.android.wallpaperbackup
com.android.webview
com.duokan.airkan.tvbox
com.duokan.videodaily
com.mitv.alarmcenter
com.mitv.cloudcontrol
com.mitv.mivideoplayer
com.mitv.screensaver
com.mitv.tvhome
com.miui.systemAdSolution
com.miui.tv.analytics
com.novatek.ATVTVinput
com.novatek.DVBTTVinput
com.novatek.cvbstvinput
com.novatek.frameworkpackagestubs
com.novatek.hdmi1tvinput
com.novatek.hdmi2tvinput
com.novatek.hdmi3tvinput
com.sohu.inputmethod.sogou.tv
com.xiaomi.account
com.xiaomi.account.auth
com.xiaomi.android.TV.audio
com.xiaomi.gamecenter.sdk.service.mibox
com.xiaomi.mi_connect_service
com.xiaomi.mibox.lockscreen
com.xiaomi.mico.mqtt.service.application.tv
com.xiaomi.miplay
com.xiaomi.mitv.appstore
com.xiaomi.mitv.debug
com.xiaomi.mitv.handbook
com.xiaomi.mitv.karaoke.service
com.xiaomi.mitv.legal.webview
com.xiaomi.mitv.mediaexplorer
com.xiaomi.mitv.pay
com.xiaomi.mitv.payment
com.xiaomi.mitv.providers.settings
com.xiaomi.mitv.remotecontroller.service
com.xiaomi.mitv.settings
com.xiaomi.mitv.smartpanel
com.xiaomi.mitv.smartshare
com.xiaomi.mitv.systemui
com.xiaomi.mitv.tvmanager
com.xiaomi.mitv.tvplayer
com.xiaomi.mitv.tvpush.tvpushservice
com.xiaomi.mitv.upgrade
com.xiaomi.screenrecorder
com.xiaomi.smarthome.tv
com.xiaomi.statistic
com.xiaomi.tvqs
com.xiaomi.voicecontrol
com.xiaomi.wakeupservice
com.xiaomi.wfdsinkhelperservice
mitv.service
```
