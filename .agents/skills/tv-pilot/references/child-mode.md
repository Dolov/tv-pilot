# 小米电视儿童模式（MIUI TV）

## 官方儿童模式在哪

不是 PatchWall 桌面，而是屏保/锁屏应用里的 childmode 模块：

- 包名：`com.xiaomi.mibox.lockscreen`
- 关键 Activity：
  - `.childmode.ParentsControlActivity` 家长控制
  - `.childmode.PlayingTimeLimitActivity` 全天播放时长
  - `.childmode.OncePlayingTimeLimitActivity` 单次播放时长
  - `.childmode.InputPasswordActivity` 密码输入
  - `.childmode.BabyInfoActivity` / `BirthdayActivity` / `GenderActivity` 宝宝资料
- 入口：主页「模式」切换，或 `设置 → 通用设置 → 家长设置`

## 配置存在 system settings 里，明文可读

| 键 | 含义 | MiTV-NSOP0 实测值 |
|---|---|---|
| `play_time_limit` | 全天播放时长（分钟） | 示例 60 |
| `play_time_limit_once` | 单次播放时长（分钟） | 示例 15 |
| `play_time_today_watchtime` | 儿童模式计时（**不是今日观看时长**） | `207:0`（实测长时间不更新） |
| `child_mode_digital_pwd` | 家长密码（**明文**） | `******`（4 位数字，因机而异） |
| `child_mode_lock_switch` | 锁开关 | `child_mode_lock_off` |
| `panel_display_time` | 面板时间计数（单位不明，长期不变，别拿来算时长） | 171952 |
| `childmode_autoin` | 开机自动进儿童模式 | 1 |
| `xiaomi_kids_rules` | 儿童规则 | 1 |
| `disable_time_period_switch` | 禁用时段开关 | 0 |
| `child_lock_controls` | 儿童锁控制 | 1 |
| `child_enter_kids_status` | 进入儿童状态 | 1 |

读写：

```bash
scripts/adb.sh shell settings get system play_time_limit
scripts/adb.sh shell settings put system play_time_limit 60
scripts/adb.sh shell settings put system child_mode_digital_pwd '你的新密码'
```

> 改完建议重启电视或重新进出儿童模式，让锁屏应用重新读配置。改之前先 `settings get` 记原值。

> **`play_time_today_watchtime` 不是"今天看了多久"**：它只在儿童模式生效时累加；`child_mode_lock_switch=off` 时根本不动（实测一晚采样几次都是同一个值，和电源日志差 3 倍）。要真正的使用时长，走 `references/usage-stats.md`。

## 已知弱点（决定要不要依赖它）

1. **密码明文存在 system settings**，任何应用和 ADB 都能读
2. 出厂/常见密码是 `1234`，孩子猜两下就中
3. 到点后的「答题找回密码」流程没有独立开关可以关——答对就拿到密码，可以退出儿童模式
4. 时长限制靠锁屏应用 UI 兜底，不是系统级强制，绕开 UI 就绕开了限制

## 结论：官方模式适合做外层，不适合做硬时限

- **外层**：官方儿童模式限制可用内容、阻止孩子进系统设置（退出儿童模式要密码）
- **内层**：另一个本地计时的第三方 App 做硬时限，到点直接拦截，没有答题口子
- 两者可以叠加，不冲突：官方模式的答题只解除它自己的计时，第三方 App 照样拦截

## 第三方方案（无服务器、纯本地）

- **KidLock** `github.com/yanmasharski/kidlock-tv`：Kotlin 开源，用 UsageStats + 无障碍服务本地计时，到每日限额拦截，6 位 PIN，开机自启。装法：`scripts/adb.sh install kidlock.apk`，然后在电视上授权「使用情况访问」和「无障碍服务」。侧载 App 在 Android 13+ 上无障碍开关会弹回，需要 `adb shell appops set <包名> ACCESS_RESTRICTED_SETTINGS allow`（本机 Android 9 不涉及）
- **ScreenTimeController** `github.com/yohananha/ScreenTimeController`：电视端 + 家长网页端 + Firebase，能远程设限额、批加时、发一次性解锁码。适合想在手机上看/管的场景，但需要 Firebase 项目

## 装 App 前先想清楚的问题

孩子会不会自己进设置、翻应用列表、卸载东西？如果会，纯 App 方案会被卸掉。要么保留官方儿童模式当外壳（防止进设置），要么把 App 设成 Device Owner（`dpm set-device-owner`，需要恢复出厂，代价大）。先问清楚再动手。
