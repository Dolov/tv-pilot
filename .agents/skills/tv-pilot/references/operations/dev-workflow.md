# 给这台电视开发一个小 App：闭环流程（环境已就绪）

用户说「给电视做个角标/悬浮窗/小工具」「改一下电视上那个 App」时读这份。
环境**已经装好**，不要重新下载 Android Studio / SDK；先自检，缺什么再补。

## 0. 30 秒自检

```bash
bash scripts/build-tools.sh status     # build-tools / android.jar / JDK / keystore / jadx / adb 一览
bash scripts/ensure-tv.sh              # 电视连接（<1s，不走全网扫描）
```

五项全绿就可以直接写代码。缺了再 `bash scripts/build-tools.sh ensure`（腾讯镜像 ~50MB/s）。

## 1. 源码从哪来

| 情况 | 做法 |
|---|---|
| 工作区有项目（如 `app/`） | 直接改，`./build.sh` 构建 |
| 工作区被清空了 | 先 `bash scripts/app-backup.sh restore app <目标目录>` 从 skill 的 `assets/` 恢复；再不行才从电视拉 APK 反编译（见下方「恢复源码」） |
| 新做一个小工具 | 照 `references/operations/build-mini-apk.md` 的最小结构（manifest + 1~3 个 Java + build.sh） |

构建成功后项目会自动同步备份到 `assets/<项目名>/`（`build.sh` 里调用 `app-backup.sh`，
`TVPILOT_AUTO_BACKUP=0` 可关闭），所以正常情况源码不会真丢。

恢复源码（备份里也没有时；jadx 已内置，不需要网络）：

```bash
P=$(scripts/adb.sh shell pm path <包名> | sed 's/package://' | tr -d '\r')
scripts/adb.sh pull "$P" /tmp/app.apk
.tools/jadx/bin/jadx -d /tmp/app-src /tmp/app.apk        # 需要 JDK：export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

jadx 出来的 `sources/` 是「能读但需要小改」的近似源码（`access$xxx`、合成方法），够用来恢复逻辑和样式常量。

## 2. 构建

项目里一般有 `build.sh`，内部链路是 `javac → d8 → aapt2 → zipalign → apksigner`。
关键约束：

- **keystore 固定**：`.tools/keystore/tv-pilot.jks`（自己的 App 可以另建，但**必须固定不动**）
  —— 换了签名就只能先卸载再装，appops、无障碍条目、数据全部重来
- **javac 要用 JDK ≤21**（26 已移除 `-source 8`）；`build-tools.sh env` 会自动选
- 有 assets 时 `aapt2 link` 必须加 `-A assets`
- `targetSdk 22`：免前台服务限制、`TYPE_SYSTEM_ALERT` 直接可用
- **版本号跟着 bump**：`build.sh` 里 `--version-code/--version-name` 和 manifest 同步改；装完用 `dumpsys package <pkg> | grep versionName` 核对，否则分不清电视上是哪版
- **启动组件是 `HomeActivity`（菜单）**，不是 `MainActivity`（详情页子页）；`app-deploy.sh --activity` / `am start` 用 `.HomeActivity`，要直接看详情页再单独 `am start -n com.dsh.tvhud/com.dsh.tvhud.usage.MainActivity`

## 3. 部署（顺序是坑，别手打）

```bash
bash scripts/app-deploy.sh \
  --apk /path/to/app.apk --pkg com.dsh.tvhud \
  --activity com.dsh.tvhud/com.dsh.tvhud.home.HomeActivity \
  --overlay --usage \
  --acc com.dsh.tvhud/com.dsh.tvhud.hud.BootAccessibilityService \
  --tag TVPILOT
```

它按正确顺序做：`install -r` → `appops SYSTEM_ALERT_WINDOW` → `appops GET_USAGE_STATS` →
`pm grant WRITE_SECURE_SETTINGS` → 启动一次清 `stopped` → **最后**写无障碍条目 → 自检并打印回滚命令。
加 `--dry-run` 可以先看命令不执行。

升级现有 App 同样走 `app-deploy.sh`（`install -r` 保留 prefs/授权/无障碍）；`app.sh install` 是它的薄封装。签名不匹配时只能卸载重装，会清 `adb_keep`、长条历史、无障碍条目——所以 keystore 必须固定，常规升级优先用 `app-deploy.sh`。

> 为什么必须这个顺序：`adb install -r` 和 `am force-stop` 都会把该 App 的无障碍条目从
> `enabled_accessibility_services` 里删掉，写早了等于白写（详见 `references/operations/boot-autostart.md`）。

## 4. 验证（别只看日志说"应该行"）

```bash
scripts/adb.sh shell "ps -A | grep -i <包名>"                      # 进程在
scripts/adb.sh shell "dumpsys activity services <包名> | grep ServiceRecord"
scripts/adb.sh shell "logcat -d -s <TAG>:* | tail -20"             # 自定义 tag 的日志
scripts/adb.sh shell "dumpsys window | grep -m1 mCurrentFocus"     # 屏保时才显示的东西要看这个
scripts/adb.sh shell 'am broadcast -a com.duokan.intent.action.show.screensaver'   # 触发屏保
scripts/adb.sh shell screencap -p /sdcard/s.png && scripts/adb.sh pull /sdcard/s.png /tmp/s.png
```

对着截图核对：位置、字号、颜色、基线。像素级比对可以量字高/列位置（`hud_ref` 与新版差 ≤1px 才算还原）。
屏保长条要按像素核对：截图 → 找 bar 行 → 按颜色 run 反推时间段（颜色码见 `screensaver.md`），确认新时段颜色对，别只说"应该行"。

## 5. 高频坑

| 坑 | 现象 | 对策 |
|---|---|---|
| 装完无障碍条目没了 | 重启后 App 不再自启 | 条目必须**最后**写；App 内每 10s 自愈（`WRITE_SECURE_SETTINGS`） |
| 直接 `am startservice` 被拒 | `Requires permission not exported` | 服务 `exported=false` 是正常设计，用 Activity 拉起或 SHOW 广播 |
| 新装的包收不到广播 | `Broadcast completed` 但没反应 | 新装包 `stopped=true`；先 `am start` 一次 |
| `pm uninstall` 后配置全丢 | `adb_keep` / 偏好清空 | 卸载即清数据；重装后补发 `ADB_KEEP_ON` |
| `settings` 里 LIKE 查询抛异常 | `IllegalArgumentException: Supported SQL` | SettingsProvider 只支持 `name=?` 单键查 |
| 混排中文+数字排版诡异 | 某个 TextView 宽度恒 0 | 别嵌套 LinearLayout 混排，直接自绘 Canvas（见 `screensaver.md`） |
| 整段 `logcat -d` 很慢 | 一次 ~15s | 用 `-s <TAG>:*` 或 `-t 200` 限量 |
| `dumpsys meminfo` 卡住脚本 | >5s 超时 | 放到 `--deep` 之类的显式慢路径里 |
| 改完屏保角标没变化 | 屏保里还是旧内容 | `am broadcast …exit.screensaver` 再 `…show.screensaver`；必要时重启 App |
| `build.sh` 说成功但 `assets/<项目>` 没更新 | 受限沙箱写不了 skill 目录，自动备份被 `\|\| true` 吞掉 | 手动 `bash scripts/app-backup.sh backup <工作区>/app --name app`；核对 `find assets/app -type f \| wc -l` 和 `grep SCHEME assets/app/src/.../UsageDay.java` |
| 装完第一次触发屏保，角标没出现 | 服务还没 attach（实测可延迟 ~1 分钟） | `exit.screensaver` → `show.screensaver` 再来一次；`logcat -d \| grep TVPILOT` 看 `HUD attached` |
| 长条整条一个颜色 / 看片时段是灰蓝 | 首轮 tick 把 00:00→now 记成一段；或分类按包名 | 读 `screensaver.md`「今日使用长条」；改分类规则必须 bump `UsageDay.SCHEME` |
| 屏保黑屏（`mCurrentFocus=null`）时 `am start` 没反应 | 焦点不在任何窗口 | 用 `am start -W -n <pkg>/.HomeActivity`（或 `/.MainActivity` 直接进详情页），再 `dumpsys window \| grep mCurrentFocus` 确认 |

## 6. 收尾

- 把电视恢复到用户原来的界面（`input keyevent 3` 回桌面，退掉测试用的屏保）
- 写操作留下记录：改了哪个包、原值、还原命令
- 需要验证重启自启时**先问用户**（电视会黑屏 ~40s，且可能弹 ADB 授权框）
