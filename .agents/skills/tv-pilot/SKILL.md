---
name: tv-pilot
description: 用局域网 ADB 操作小米/Redmi 电视（MIUI TV / PatchWall）——发现设备、免数据线连接、只读探测、配置儿童模式与观看时长、去广告清理、安装第三方 APK、读遥控器电量、统计电视使用时长和观看内容、做开机自启与屏保角标（含角标上的「今日使用长条」）。当用户提到小米电视、Redmi 电视、MIUI TV、PatchWall、电视去广告、电视儿童模式、限制孩子看电视时长、adb 连电视、给电视装 APK、开机自启、屏保角标、今日使用长条、遥控器电量，或说"改一下家里的电视""电视上装个东西"，或者问"电视今天看了多久""都看了什么""遥控器还有多少电""电视上那个 App 是干什么的""电视是什么型号/什么系统""电视上都装了哪些应用"，或者要给电视开发/改一个小 App（构建 APK、屏幕角标、悬浮窗）时使用。即使用户只说了"电视"但上下文是安卓电视，也应先加载本 skill——它能省掉每次重新试错设备发现和 adb 环境的时间。其它品牌/型号的 Android TV 同样适用：先跑 scripts/identify-tv.sh 匹配设备档案，没有档案时按 references/porting.md 只读接入并贡献档案。
compatibility: macOS（脚本用 dns-sd/ipconfig/arp）或 Linux（脚本需小改）；本机可联网下载 platform-tools；电视需开启 ADB 调试且与电脑同一局域网；全程无需数据线。
---

# 电视 ADB 操作（tv-pilot）

## 三条原则

1. **先拿连接，再干活**：`bash scripts/ensure-tv.sh` 会在 1 秒内处理掉 90% 的情况（已有连接 → 缓存 IP → 扫描），stdout 直接给出可用的 `<ip>:5555`。
2. **一次往返取全数据**：脚本已经把查询合并好并给出结论，不要把原始 `dumpsys`/`logcat` 拉回上下文自己解析。
3. **默认只读**：任何写操作（`install` / `uninstall` / `settings put` / `disable`）先说清楚再执行。

## 设备适配（先识别，再动手）

```bash
bash scripts/identify-tv.sh          # 只读识别 + 匹配 references/devices/ 里的设备档案
```

三种结果，处理方式不同：

| 结果 | 含义 | 怎么做 |
|---|---|---|
| `matched` | 型号在设备档案里 | 按档案执行；`capabilities` 里不是 `verified` 的项先只读、写操作前跟用户确认 |
| `similar` | 同品牌不同型号 | 品牌经验可参考，但包名/键值/坑不保证；按 `references/porting.md` 补齐这份型号 |
| `unknown` | 品牌或型号没档案 | 只读能力可用（连接/状态/通用探测/通用使用统计）；**禁止**禁包、`settings put`、装 App、做屏保角标 |

未验证机型上做写操作，代价可能是黑屏、进不去设置、开机起不来——宁可先只读，也不要拿别的机型的档案硬套。

## 配置（敏感信息不进仓库）

复制 `.env.example` 为 `.env`（`.gitignore` 已排除），填自家电视信息：

- `TV_IP` / `TV_PORT`：电视局域网地址；冷启动时 `ensure-tv.sh` 拿它做第一跳（不扫网段）
- `REMOTE_MAC`：遥控器 MAC（可选，便于核对电量键；脚本会自动发现，不是必填）
- `TVPILOT_KS_PASS`：签名 keystore 密码（可选，默认 `android`）

## 本机现状（本机 = MiTV-NSOP0；换机型看设备档案）

| 项 | 值 |
|---|---|
| 电视 | MiTV-NSOP0（`furyroad`，65"，Android 9 / 固件 `22.7.26.1365`），1920×1080@320dpi |
| 设备档案 | `references/devices/xiaomi-mitv-nsop0.md`（能力表在 frontmatter） |
| 地址 | `<TV_IP>:5555`（在 `.env` 配 `TV_IP`；运行后缓存在 `.tools/state.env`，连不上先 `ensure-tv.sh -v`） |
| 自研 App | `com.dsh.tvhud` **v1.11**（versionCode 12，2026-11-05 装）——屏保右上角角标三行（开机 / 遥控器电量 / 今日使用长条）；**打开 App = 首页菜单**（`HomeActivity`：今日使用 / 使用记录 / AI 语音助手·即将上线 / 设置·即将上线，加功能只改 `HomeActivity.ITEMS`）；「今日使用」是编辑式台账详情页：合计 + 分类图例 + 0:00→24:00 时间轴 + 明细「时间/看了什么/时长/类别」（视频按每集开始拆行；**相邻同类行自动合并**，间隔 ≤5 分钟算同一段）；页面**每分钟刷新**一次，刷新时保持焦点行与滚动位置；「使用记录」左右键按天翻历史、保留 365 天；返回键回菜单。附带 ADB 守护 + 无障碍开机自启。详见 `references/screensaver.md`「首页菜单」「详情页」 |
| App 源码 | 仓库根 `tvhud/`（本机 `~/tv-pilot/tvhud`；`build.sh` 可构建；构建成功会自动备份到 skill `assets/tvhud/`，但受限沙箱写不了 skill 目录时会**静默跳过**，需要时手动 `bash scripts/app-backup.sh backup tvhud --name tvhud`）。工作区被清空时 `bash scripts/app-backup.sh restore tvhud <目录>` 恢复；再不行从电视拉 APK 用 `.tools/jadx` 反编译（见 `references/dev-workflow.md`）。控制脚本 `tv-hud.sh`：`status/on/off/build/screenshot/adbkeep on\|off`；`install` 已改成**先增量安装、签名不匹配才卸载**（卸载会清 prefs/无障碍），常规升级仍建议 `scripts/app-deploy.sh` |
| 装了什么 | `references/devices/xiaomi-mitv-nsop0-apps.md`（109 包快照）；漂移检查：`bash scripts/apps-inventory.sh diff` |
| 构建工具链 | `~/.agents/skills/tv-pilot/.tools/`（adb / build-tools / android.jar / **keystore** / jadx），状态：`bash scripts/build-tools.sh status` |
| ADB 常开 | 电视端 `adb_enabled` 由角标 App 每 10s 写回 1；无障碍条目里已含 `com.dsh.tvhud/com.dsh.tvhud.BootAccessibilityService` |

## 开工顺序（30 秒）

1. `bash scripts/ensure-tv.sh` —— 拿连接（输出 `<ip>:5555`；连不上看它给的下一步，别自己扫网段）
2. `bash scripts/identify-tv.sh` —— 识别机型并匹配设备档案，按上一节的 matched/similar/unknown 决定能做什么
3. `bash scripts/tv-state.sh` —— 确认电视在开机状态；待机/关机时写操作会失败
4. 按下面「常见请求」找脚本：读操作直接跑；写操作（install / settings put / disable）先跟用户说明再做

## 常见请求：一条命令

| 用户问 | 跑这条 | 正常耗时 |
|---|---|---|
| 遥控器还有多少电 | `bash scripts/remote-battery.sh` | **<2s** |
| 电视今天用了多久 / 看了什么 | `bash scripts/usage.sh` | 5–10s |
| 装了哪些应用 / 有没有广告 | `bash scripts/probe.sh` | 3–8s |
| 最近是不是多装/少装了应用 | `bash scripts/apps-inventory.sh diff` | 2–4s |
| 电视连得上吗 | `bash scripts/ensure-tv.sh -v` | **<1s** |
| 电视现在开机 / 熄屏 / 关机 | `bash scripts/tv-state.sh`（`--deep` 查深睡，~7s） | **<1s** |
| 为什么自研 App 重启后起不来 / 想开机自启 | 读 `references/boot-autostart.md` | — |
| 屏保识别 / 在屏保上显示东西 | 读 `references/screensaver.md` | — |
| 屏保角标内容要加一行（如遥控器电量） | 读 `references/screensaver.md` | — |
| 角标长条某段颜色不对 / 看片时段没记上 | 读 `references/screensaver.md`「今日使用长条」 | — |
| 想看今天具体哪几段、各多久 | 电视上打开「TV HUD」→「今日使用」；或 `bash scripts/usage.sh` | — |
| 某个 App 是干什么的 / 该用哪个 | 读当前机型的设备档案（如 `references/devices/xiaomi-mitv-nsop0-apps.md`） | — |
| 我的电视不是小米 / 型号不在支持列表 | `bash scripts/identify-tv.sh`；未匹配读 `references/porting.md` | **<1s** |
| 给电视做/改一个小 App | 读 `references/dev-workflow.md`，部署用 `bash scripts/app-deploy.sh` | 构建 ~5s |

脚本都不要求传 IP；不传就走 `ensure-tv.sh` 的阶梯。刚连过时是纯本地操作，不会扫网段。

## 连接阶梯（ensure-tv.sh 内部，命中即停）

| 级别 | 做什么 | 耗时 | 触发条件 |
|---|---|---|---|
| L0 | `adb devices` 已有 device | ~0.1s | 刚连过，最常见 |
| L1 | 用 `.tools/state.env` 缓存的 IP `adb connect` | 1–5s | 上次成功过 |
| L2 | `find-tv.sh --fast`（ping 扫段 + mDNS） | ~7s | 换网络 / 首次 |
| L3 | `find-tv.sh` 全量（多扫 5555 与 ARP 明细） | ~15s | 排查网络问题时 |

状态判定（unauthorized / offline / 连不上）和下一步建议已写在脚本输出里，照着做即可。

## 时间预算：超过说明走错路了

| 操作 | 正常 | 超时说明 |
|---|---|---|
| 读电量 | <2s | >10s：多半在扫网段或反复 connect，先 `ensure-tv.sh -v` 看卡在哪级 |
| ensure-tv（L0/L1） | <1s | >20s：电视可能待机，先让用户唤醒 |
| find-tv | 6–15s | >25s：中止，用缓存 IP 或让用户直接报 IP |

## 不要做（这些是最主要的时间浪费）

- **已知/缓存 IP 且 `adb devices` 有 device 时，不要跑 `find-tv.sh`**：它是为冷启动准备的，天生 6–15 秒。
- **单个读数不要多源交叉**：`bluetooth_hid_battery_*` 键 + 一条日志就够。只有读到 0 / 缺失 / 与上次缓存差 >10% 时才展开验证。
- **连不上时不要反复 `adb connect`**：先看 `adb devices` 的状态——`unauthorized` 去电视上按允许，`offline` 是待机或重启中，`Connection refused` 是 ADB 开关没生效。
- **不要把原始输出贴进推理**：脚本已经给出结论，需要细节时用 `--json` 或直接看落盘文件。
- **写操作别自己拼 adb 命令**：装 App 一律走 `scripts/app-deploy.sh`（顺序错了会把无障碍条目写丢）。
- **别对 settings 做 LIKE 查询**：SettingsProvider 只支持 `name=?` 单键查，LIKE 会抛 IllegalArgumentException。
- **别整段 `logcat -d`**：一次 ~15s；用 `-s <TAG>:*` 或 `-t 200` 限量。
- **新装的包不会立刻响应广播**：`stopped=true`，先用 `am start` 拉起一次。
- **别用卸载/清数据来重置长条**：发 `am broadcast -a com.dsh.tvhud.RESET_USAGE` 即可清空当天并用 UsageEvents 重建；卸载会连带丢无障碍条目、appops、ADB 守护。
- **别把新功能直接塞进 `MainActivity`**：启动页已经是 `HomeActivity` 菜单，新功能先加 `HomeActivity.ITEMS` 一行、再在 `open()` 分发，`MainActivity` 只做「今日/历史」详情页。
- **别按包名判断「没在看视频」**：tvhome 详情页内嵌播放时，播放器不进前台、前台一直是 `com.mitv.tvhome.DetailsActivity`；要看 class 名（`UsageEvents.Event.getClassName()` 本机可用），见 `references/usage-stats.md`。

## 首次使用（冷启动）

```bash
bash scripts/install-adb.sh     # 下载 platform-tools 到 .tools/（只需一次）
bash scripts/ensure-tv.sh -v    # 找电视并连接；unauthorized 时去电视上按「允许」
```

开 ADB 开关的步骤见 `references/enable-adb.md`。要点：新固件在 `设置 → 账号与安全 → 安全 → ADB 调试`；**打开开关后通常要重启电视**，5555 才开始监听；部分固件重启后开关会复位。

## 遥控器电量（只读）

```bash
bash scripts/remote-battery.sh          # 快路径：0.3–0.5s，返回结论+证据
bash scripts/remote-battery.sh --deep   # 需要确认阈值/低电量提醒时（约 10–15s）
bash scripts/remote-battery.sh --json   # 结构化结果
```

默认不拉 logcat（整段 `logcat -d` 经 adb 要 ~15s），用 `Settings.Global` 的键值给结论；`--deep` 才做日志交叉验证。

口径要点（细节见 `references/remote-battery.md`）：

- 电量在 `Settings.Global` 的 `bluetooth_hid_battery_<MAC大写>`（0–100），遥控器重连/按键时刷新；电视重启后重连也会刷新。
- `com.xiaomi.mitv.settings` 日志里的 `HID_BATTERY ... battery=0` 是解析 bug，别当真；`dumpsys battery` 是电视虚拟电池（恒 100%），与遥控器无关。
- 阈值实测 `low_battery_thr=0x19`（25%）；固件有提前量，实测 29% 就会触发 `HID_BATTERY_WARRING`（28–30% 时提醒可能已经弹过）。
- 电视待机 / ADB 未开时读不到，先唤醒电视；没有 `bluetooth_hid_battery_*` 键时，按红外遥控器或未配对处理。

## 改东西之前

| 想做什么 | 先读 |
|---|---|
| 换了一台没见过的电视 | 先读 `references/porting.md` |
| 去广告 / 禁统计组件 | `references/ad-packages.md` |
| 儿童模式 / 观看时长限制 | `references/child-mode.md` |
| 了解这台机器（型号、固件、已装应用、坑） | 先 `identify-tv.sh`，再读 `references/devices/<brand>-<model>.md` |
| 电视上都装了哪些应用 / 有没有新装 | 先读 `references/devices/<brand>-<model>-apps.md`；漂移查 `bash scripts/apps-inventory.sh diff` |
| ADB 开关怎么开 | `references/enable-adb.md` |
| 使用统计口径 / 常见误读 | `references/usage-stats.md` |
| 让 App 开机自启 / 收不到 BOOT_COMPLETED | `references/boot-autostart.md` |
| 屏保识别 / 在屏保上叠字 / 今日使用长条分类 | `references/screensaver.md` |
| 给电视写个小 App（不装 Android Studio） | `references/build-mini-apk.md` |
| 改/重装电视上的自研 App（构建→部署→验证闭环） | `references/dev-workflow.md` |

安全边界：

- 只用可逆手段：`pm uninstall --user 0` 配 `cmd package install-existing` 还原；不改 `/system`、不 root、不刷机。
- 动 `com.mitv.tvhome`（桌面）、`com.xiaomi.mitv.settings`（设置）、`com.android.*` 前单独确认——禁错会黑屏或进不去设置。
- 分批执行，每批后重启验证；每步记录原值和还原命令，写进 `probe/changes-<日期>.md`。
- 收尾提醒用户关掉 ADB（5555 是明文端口）。

## 故障速查

| 现象 | 先查什么 |
|---|---|
| `Connection refused` | ADB 开关没生效：重启电视；确认端口是 5555 |
| `unauthorized` / `failed to authenticate` | 电视上有授权框没按：遥控器选「允许」，建议勾「一律允许」 |
| `adb` 启动即 abort，报 `Cannot mkdir '~/.android'` | 用了裸 adb；改用 `scripts/adb.sh`（已重定向 HOME） |
| macOS `No route to host` | 系统「本地网络」权限未开：设置 → 隐私与安全性 → 本地网络 放行终端 |
| 电视重启后连不上 | 小米部分固件会复位 ADB 开关，重开一次 |
| ping 通但 5555 拒绝 | 电视待机/关机，adbd 没起；先唤醒，仍拒绝就重开 ADB |
| 遥控器电量 0 / 读不到 | 先看是不是红外遥控器；再确认已配对、且电视收到过上报 |
| 重启后 5555 拒绝，但开机 30 秒内能连 | ADB 延时复位（小米固件）；重开 ADB，或让 App 用 `WRITE_SECURE_SETTINGS` 守住 |
| `adb devices` 里设备突然消失（device not found） | `adb kill-server` 后重新 `start-server` + `connect` |
| 自研 App 重启后不启动 / 收不到广播 | 开机被置 `stopped=true`，读 `references/boot-autostart.md` |
| 装完/重启后第一次触发屏保，角标没出现 | 服务还没 attach：`exit.screensaver` → `show.screensaver` 再来一次，`logcat -d \| grep TVHUD` 看到 `HUD attached` |
| 打开 App 直接进详情页 / 想加个新菜单项 | 启动页是 `HomeActivity`（菜单），条目在 `HomeActivity.ITEMS`；详情页 `MainActivity` 只当子页面用 |
| 详情页底部切半行 / 空日期不显示空状态 | 看 `references/screensaver.md`「版式坑（v1.9）」 |
| 长条整条一个颜色 / 看片时段是灰蓝「系统」 | 分类或首轮 tick 问题：读 `references/screensaver.md`「今日使用长条」；改分类规则要 bump `UsageDay.SCHEME` |
| 长条/明细想按新口径重算 | `adb shell am broadcast -a com.dsh.tvhud.RESET_USAGE`（清当天并用 UsageEvents 重建） |

## 脚本清单

| 脚本 | 作用 |
|---|---|
| `scripts/lib.sh` | 公共库：adb 封装、超时、状态缓存（被其他脚本 source） |
| `scripts/state.sh` | 读写 `.tools/state.env`（`show` / `get` / `set` / `clear` / `ip`） |
| `scripts/ensure-tv.sh` | 连接阶梯，输出 `<ip>:5555` |
| `scripts/find-tv.sh` | 冷启动发现（`--fast` / `--list` / `--json`） |
| `scripts/adb.sh` | 裸 adb 入口（HOME 重定向，避免受限环境崩溃） |
| `scripts/install-adb.sh` | 安装 platform-tools 到 `.tools/` |
| `scripts/probe.sh` | 只读探测（型号/固件/包列表/广告候选/儿童模式；`--json` 给脚本消费） |
| `scripts/identify-tv.sh` | 只读识别 + 匹配设备档案（`matched`/`similar`/`unknown`，`--json`/`--kv`） |
| `scripts/device-profile.sh` | 设备档案工具：`init` 生成骨架 / `lint` 校验 / `index` 重建支持矩阵 |
| `scripts/usage.sh` | 使用统计（亮屏窗口、分应用时长、播放记录） |
| `scripts/remote-battery.sh` | 遥控器电量 |
| `scripts/tv-state.sh` | 状态一句话结论（开机/熄屏待机/关机、开机时长；`--deep` 查深睡） |
| `scripts/build-tools.sh` | 电视小 App 构建工具链（`status`/`ensure`/`env`；含 JDK 检测、keystore 位置） |
| `scripts/apps-inventory.sh` | 应用清单：`list` / `diff`（对快照） / `update`（写回快照） |
| `scripts/app-deploy.sh` | 装 TV mini-APK 并自检：`--apk/--pkg/--activity/--overlay/--usage/--acc/--tag`，顺序内置 |
| `scripts/app-backup.sh` | 项目保底备份：`backup <项目>` / `restore <name> [目标]` / `list`（存 `assets/`） |

## 参考文档

按需读，不要一上来全读：

| 文件 | 什么时候读 |
|---|---|
| `references/remote-battery.md` | 电量读数异常（0 / 缺失 / 与 UI 不一致）时核口径 |
| `references/usage-stats.md` | 做使用统计前对齐口径 |
| `references/child-mode.md` | 配置儿童模式 / 时长限制前 |
| `references/ad-packages.md` | 去广告前确认包名与风险 |
| `references/enable-adb.md` | 用户还没开 ADB 调试时 |
| `references/devices/README.md` | 支持哪些机型、能力等级、怎么贡献档案 |
| `references/devices/<brand>-<model>.md` | 当前机型的实测档案（型号/固件/桌面屏保/统计来源/坑） |
| `references/devices/<brand>-<model>-apps.md` | 该机型的应用清单（快照 + 分类） |
| `references/porting.md` | 新机型接入：怎么从 0 采出一份设备档案 |
| `references/boot-autostart.md` | 开机自启、`stopped=true` 拦截、无障碍入口 |
| `references/screensaver.md` | 屏保识别、触发广播、屏保上叠字、今日使用长条（分类/坑） |
| `references/build-mini-apk.md` | 不装 Android Studio 给电视写小 App |
| `references/dev-workflow.md` | 开发闭环：源码来源→构建→部署→验证→坑（改自研 App 必读） |
