# 使用统计与观看记录（"今天看了多久 / 看了什么"）

用户问"今天电视用了多久""都看了什么""谁在半夜看电视"时走这份。**全程只读**，不改电视。

## 口径

- **亮没亮**：`logcat -b events` 里的 `screen_toggled`（比 `dumpsys usagestats` 的 24h 窗口存得久，能对齐"今天"）
- **用了哪些应用/页面**：`dumpsys usagestats` 的 `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND`，按 package+class 配对，再和亮屏窗口求交
- **看了什么片**：`/sdcard/miio/miothost.txt`（米家上报，最持久）> `logcat -b main` 的 `MiTVOtService_PW` > `dumpsys media_session`（只在播放中才有）
- **别用** `play_time_today_watchtime` 当今日时长（见坑）

## 数据源与保留期

| 来源 | 命令 | 能拿到 | 保留期 / 坑 |
|---|---|---|---|
| 使用事件 | `dumpsys usagestats` | 近 24h 的前台/后台/亮灭屏事件 | 只留 24h；同一秒常有重复事件 |
| 屏幕开关 | `logcat -b events -d` | `screen_toggled`、`power_screen_state`、`power_sleep_requested` | 4MB 环形缓冲，一般 1~2 天；电视重启就清 |
| 实时电源 | `dumpsys power` | `mWakefulness`、`mLastWakeTime` / `mLastSleepTime` / `mLastUserActivityTime`（elapsedRealtime ms） | 只有当前值；`now - 值` = 多久以前 |
| 屏幕状态 | `dumpsys display` | `mScreenState=ON/OFF` | 只读 |
| 主日志 | `logcat -b main -d` | `MiTVOtService_PW` 的 `playerInfo==>{...}`、播放器/MediaSession 日志 | **约 1 小时**（4MB）——下午的播放记录晚上就没了，要当天抓 |
| 系统日志 | `logcat -b system -d` | AMS 等系统事件 | 约 2 天，但**不含播放内容日志** |
| 米家上报 | `cat /sdcard/miio/miothost.txt` | `siid:3,piid:3` 的 value：`video_name` / `ci` / `total_ci` / `total_duration` / `play_resolution` / `media_id` | 文本日志，会轮转（实测当天只从 17:03 开始）；**回溯播放内容的首选** |

一条命令抓齐并出摘要：

```bash
bash scripts/usage.sh <IP> [输出目录]
```

## 计算口径

1. **先用电视时间定"今天"**（`shell date`），不要用电脑时区去猜。
2. **亮屏窗口**：`screen_toggled: 1` 开、`screen_toggled: 0` 关，配对成区间；00:00 之前最后一次状态决定 00:00 时是否已经亮着；最后一段没关就截到 now。
3. **分应用/页面时长**：`MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` 按 package+class 配对成区间，再和亮屏窗口求交累加。同一秒内的重建/抖动要合并。按 **package+class（页面）** 统计，才能分出"详情页 / 桌面 / 设置页"。
4. **会话**：相邻亮屏窗口可以合并成"这次开机看了多久"。
5. 输出：亮屏总时长 + 时间线 + 分应用/页面 Top + 最新播放记录（若有）+ 数据可信度提示。

## 坑（都是踩过的，按踩坑代价排序）

- **`play_time_today_watchtime` 不是今日观看时长**。MiTV-NSOP0 实测：当天 20:03、20:04 两次采样都是 `207:0`，屏幕亮着也不增长；儿童模式锁没开（`child_mode_lock_switch=off`）时这个计数根本不更新。当天电源日志算出来只有 67 分钟，两边差 3 倍。引用它时必须说明来源，别直接答"今天看了 207 分钟"。
- **ADB 能连 ≠ 电视亮着**。网络待机时 ADB 也可能通。判亮必须 `mWakefulness=Awake` + `mScreenState=ON`。
- **`com.mitv.mivideoplayer` 没进前台 ≠ 没看视频**。详情页的内嵌/插件播放器不切 Activity，`usagestats` 里只有 `DetailsActivity`，播放器包永远不出现——所以"应用使用时长"里看不到播放器是正常的，别据此下结论。（v1.3 起可用 class 名确认，见下节「按内容类型分类」）
- **`panel_display_time` 不是分钟数**，实测长时间不变，别拿来算时长。
- **`dumpsys usagestats` 是最近 24h 窗口，会跨天**。问"今天"一定要按时间戳过滤，别把昨晚的算进来。
- **同秒事件**：`MOVE_TO_FOREGROUND` 后 1 秒内 `MOVE_TO_BACKGROUND` 再 `MOVE_TO_FOREGROUND` 很常见（页面重建），不合并会多算/少算几秒。
- **电视刚重启**：`/proc/uptime` 小于 24h 时，今天的统计不完整，要跟用户说明。

## 看片记录：怎么确定"看了什么"

按可靠性排序：

1. **米家日志** `/sdcard/miio/miothost.txt`（首选，能回溯当天几小时）

   行格式（注意 value 是**转义后的 JSON 字符串**）：

   ```
   <时间戳> [D] {"id":<id>,"method":"properties_changed","params":[{...,"siid":3,"piid":3,"value":"{\"play_status\":1004,\"media_id\":\"<media_id>\",\"video_name\":\"<片名>\",\"ci\":31,\"total_ci\":50,\"total_duration\":124246,...}"}]}
   ```

   - 直接 grep 中文片名最快；要结构化就先把 `\"` 换回 `"` 再按 `"video_name":"..."` 正则
   - 字段：`video_name` 片名、`ci`/`total_ci` 第几集/共几集、`total_duration` 单集时长 ms、`play_resolution`、`play_status`（1004 播放 / 1006 暂停 / 1008 停止）、`current_position`

2. **主日志** `logcat -b main -d | grep MiTVOtService_PW`

   - 同款 `playerInfo==>{...}` **未转义** JSON，含 `media_id`、`video_name`、`ci`、`current_position`、`total_duration`、`time_ms`
   - 只有约 1 小时，事后补不了；分析当天要趁早抓

3. **`dumpsys media_session`**：只在播放中才有元数据；停止后 session 消失，别指望事后查。

4. **`content query --uri content://com.mitv.tvhome.PlayStatusProvider`**：可读、无需权限，但只在播放时有值。

5. **播放挂在哪个页面**：`MiTVOtService` 的 `handlePlaySate: new task: ComponentInfo{...}` 会给出前台 Activity，再结合 `usagestats` 停留时长，判断是"详情页内嵌播放"还是"独立播放器"。

已经试过、走不通的路（别浪费时间）：

- `run-as com.mitv.tvhome` → "package not an application"，非 debuggable，读不到 `/data/data`
- `content://com.mitv.home.video/<table>` → `Invalid tables`（不知道表名）
- `content://com.mitv.tvhome.usergroup.provider` → 需要 `com.mitv.tvhome.permission.READ_USERGROUP`
- `dumpsys notification` 归档里没有历史通知正文，只有当前通知
- 主日志 1 小时以前的场次没有别的本地副本可回溯（`/sdcard/mitvlogzip`、`logs_*` 都是空的）

## 结论怎么写

把"**确认到的**"和"**推断的**"分开写，别把推断说成事实：

- 确认：19:26 的米家/播放器日志显示《某剧集》第 31 集（全 50 集，单集 2 分 04 秒，720P），从 0:01.7 播到 0:35 切走
- 推断：下午 16:12–16:39 一直停在同一个影片详情页（同一个 Activity 实例，26 分钟没进屏保 → 有媒体在播），按单集 2 分钟算约 13 集，但无日志无法确认具体集数
- 没看到：全天没有一次独立播放器（`com.mitv.mivideoplayer`）前台会话

## 按内容类型分类：看片 vs 桌面（v1.3 HUD 长条用这个）

- `MOVE_TO_FOREGROUND` 事件带 **class 名**：adb 侧 `dumpsys usagestats` 直接打印 `class=com.mitv.tvhome.DetailsActivity`；App 侧用 `UsageEvents.Event.getClassName()`（api-28 `android.jar` 里的公开方法，**本机 Android 9 实测可用**；取不到时 try/catch 退回按包名）。
- 看片时前台常驻 `com.mitv.tvhome.DetailsActivity`（内嵌播放器不切 Activity）；桌面是 `MainActivityUserMode` / `NewMyAppActivity`，启动页 `BootActivity`，用户中心 `UserCenterSubPageActivity`，儿童锁 `LockControlsActivity`。
- **只按包名分类会把看片记成「桌面/系统」**——这是 v1.2 长条「看不到看动画片段」的根因。规则：包名分类为 SYSTEM 且 pkg 是 tvhome 时，类名含 `Detail`/`Play`/`Video`/`Theater` → VIDEO。
- 详情页停留 ≠ 一定在播（选集/看简介也会算）；要精确播放态：`content://com.mitv.tvhome.PlayStatusProvider` 只在播放中有值（`play_status` 1004 播放 / 1006 暂停 / 1008 停止），或 `AudioManager.isMusicActive()` 看有没有在出声。
