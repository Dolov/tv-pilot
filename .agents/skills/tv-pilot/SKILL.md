---
name: tv-pilot
description: 用局域网 ADB 操作电视（小米/Redmi MIUI TV 已实测，其它 Android TV 按设备档案接入）：发现设备、免数据线连接、只读探测、儿童模式与观看时长、去广告清理、安装第三方 APK、读遥控器电量、统计使用时长与观看内容、开机自启、屏保角标（含今日使用长条）、家长设置（PIN 与时间限制开关），以及给电视开发/改小 App。当用户提到小米电视、Redmi 电视、MIUI TV、PatchWall、电视去广告、电视儿童模式、限制孩子看电视、adb 连电视、给电视装 APK、开机自启、屏保角标、今日使用长条、遥控器电量，或问"电视今天看了多久""都看了什么""遥控器还有多少电""电视是什么型号/什么系统""电视上都装了哪些应用""家长设置的密码是多少"，或说"改一下家里的电视""电视上装个东西"，或要给电视写/改一个小 App 时使用。即使用户只说了"电视"但上下文是安卓电视，也先加载本 skill。Use this skill whenever the user mentions Xiaomi/Redmi TV, MIUI TV, PatchWall, Android TV via ADB, TV debloating, kids mode, screen-time limits, sideloading an APK, boot autostart, a screensaver HUD, remote battery, parent settings PIN, or TV usage stats — even if they only say "the TV".
compatibility: macOS（脚本用 dns-sd/ipconfig/arp 做发现）或 Linux（发现逻辑需按需替换）；电视需开启 ADB 调试并与电脑同一局域网；全程无需数据线。首次使用要下载 platform-tools（约 15MB），构建 App 还需 JDK 17/21 与 build-tools（约 50MB）。
---

# tv-pilot：用局域网 ADB 操作电视

本文件是**操作索引**：回答「这件事该跑哪条命令、读哪篇文档」。具体口径、踩坑、机型差异在 `references/`，不要在这里重复。

## 0. 先花 10 秒确认本机状态

- 读 `references/local.md`：里面是 `scripts/setup.sh` 生成的本机事实（电视地址、型号、匹配到的设备档案、tv-pilot 版本、工具链状态）。
- 没有这个文件（或换了电视/重装了工具链）：跑 `bash scripts/setup.sh`。
- 没有设备档案、或用户问的是别的品牌：先看第 3 节的三态规则。

## 1. 三条原则

1. **先拿连接，再干活**：`bash scripts/ensure-tv.sh` 在 1 秒内处理 90% 的情况（已有连接 → 缓存 IP → 扫描），stdout 直接给出可用的 `<ip>:5555`。
2. **一次往返取全数据**：脚本已经把查询合并好并给出结论。不要自己拼多条 `adb shell`，也不要把原始 `dumpsys`/`logcat` 拉回上下文解析——需要结构化数据时用 `--json`。
3. **默认只读**：任何写操作（`install` / `uninstall` / `settings put` / `disable`）先说清楚再执行。优先走带闸门的脚本，不要直接 `adb`。

## 2. 30 秒开工

```bash
bash scripts/ensure-tv.sh        # 拿连接，输出 <ip>:5555
bash scripts/identify-tv.sh      # 识别机型 + 匹配设备档案（matched / similar / unknown）
bash scripts/tv-state.sh         # 开机 / 熄屏待机 / 关机；待机时写操作会失败
```

## 3. 设备能力闸门（先识别，再动手）

| 结果 | 含义 | 能做什么 |
|---|---|---|
| `matched` | 型号在设备档案里 | 按档案执行；`capabilities` 里不是 `verified` 的项先只读 |
| `similar` | 同品牌不同型号 | 品牌经验可参考；包名/键值/坑不保证，写操作前先补齐档案 |
| `unknown` | 没有档案 | 只读可用；**禁止**禁包 / `settings put` / 装 App / 屏保角标 |

写操作闸门已经落到代码里：`scripts/app-deploy.sh` 会先跑 `scripts/require-profile.sh`，没有匹配档案就拒绝；其它写操作前先跑 `bash scripts/require-profile.sh <capability>`（如 `app_build`、`screensaver`、`child_mode`）。确实要冒险时用 `--force`，但要先向用户说明后果（黑屏/进不去设置/开不了机）。

## 4. 操作索引：用户问什么 → 跑哪条

| 用户可能说 | 跑这条 | 耗时 / 细节 |
|---|---|---|
| 电视连得上吗 / 帮我连一下 | `bash scripts/ensure-tv.sh -v` | <1s；失败会告诉你卡在哪一级 |
| 电视的 IP 是多少 / 找不到电视 | `ensure-tv.sh`（已连）；冷启动用 `bash scripts/find-tv.sh --fast` | 6–15s；有缓存时不要跑 find-tv |
| 电视什么型号 / 多大内存 / 什么系统 | `identify-tv.sh` + `probe.sh` | 档案在 `references/devices/` |
| 装了哪些应用 / 有没有广告组件 | `bash scripts/probe.sh` | 3–8s；`--json` 给脚本消费 |
| 最近是不是多装/少装了应用 | `bash scripts/apps-inventory.sh diff` | 2–4s；有变化 exit 1 |
| 电视现在开机/熄屏/关机 | `bash scripts/tv-state.sh [--json]` | <1s；`--deep` 查深睡 ~7s |
| 今天看了多久 / 看了什么 | `bash scripts/usage.sh` | 5–10s；口径见 `references/operations/usage-stats.md` |
| 家长设置的密码是多少 / 进家长设置 | `bash scripts/parent-pin.sh` | <2s；出厂默认 **2580**；各项开关见 `references/operations/parent-settings.md` |
| 遥控器还有多少电 | `bash scripts/remote-battery.sh [--json]` | <2s；`--deep` 交叉验证 ~10s |
| 打开 / 停止 / 构建 / 截图 tv-pilot | `bash scripts/app.sh status\|on\|off\|build\|install\|screenshot` | App 控制面 |
| 装一个 APK / 升级自研 App | `bash scripts/app-deploy.sh --apk ... --pkg ... --acc ...` | 顺序内置；不要手拼 |
| 开机自启 / 重启后 App 不起来 | 读 `references/operations/boot-autostart.md` | `stopped=true` 坑 |
| 屏保角标 / 今日使用长条 | 读 `references/operations/screensaver.md` | 分类、刷新、版式坑 |
| 配儿童模式 / 限制观看时长 | 读 `references/operations/child-mode.md` | 官方模式的弱点先说清 |
| 去广告 / 禁统计组件 | 读 `references/operations/ad-packages.md` | 三级清单 + 还原命令 |
| ADB 开关怎么开 / 开了连不上 | 读 `references/operations/enable-adb.md` | 多数固件要重启才监听 5555 |
| 给电视写/改一个小 App | 读 `references/operations/dev-workflow.md`，构建用 `app.sh build` | 也可用 `references/operations/build-mini-apk.md` |
| 我的电视不是小米 / 型号不在列表 | `identify-tv.sh` → 读 `references/porting.md` | `unknown` 时只读 |
| 想给某个 App 加能力/改分类 | 读 `references/operations/dev-workflow.md` + `docs/providers.md` | 品牌差异进 provider |

## 5. 输出契约（给脚本和代理消费）

- 机器可读输出统一用 `--json`：`identify-tv`、`probe`、`remote-battery`、`find-tv`、`tv-state`、`apps-inventory`。
- 字段与退出码见 `docs/json-contract.md`；退出码约定：`0` 成功，`1` 用法错误，`2` 环境/连接问题，`3` 能力未验证，`4` 未找到设备或档案。
- 人类可读输出默认中文、给结论；不要把原始输出整段贴进推理。需要细节时看脚本落盘文件（如 `probe/`、`usage` 输出目录）。

## 6. 目录地图（找东西用）

```
.agents/skills/tv-pilot/
├── SKILL.md               # 本文件：操作索引（不写本机事实）
├── README.md              # 给人看的安装/结构说明
├── scripts/
│   ├── setup.sh           # 首次配置 → 生成 references/local.md
│   ├── ensure-tv.sh       # 连接阶梯（L0 已有连接 → L1 缓存 IP → L2 扫描）
│   ├── identify-tv.sh     # 识别 + 匹配档案（仓库 + 本地覆盖层）
│   ├── probe.sh / tv-state.sh / usage.sh / remote-battery.sh
│   ├── parent-pin.sh       # 家长设置 PIN（出厂默认 2580）+ 今天免限状态
│   ├── apps-inventory.sh  # 应用清单快照 / diff / update
│   ├── app-deploy.sh      # 装 APK 的顺序 + 权限 + 无障碍条目（含写闸门）
│   ├── app-backup.sh      # 源码保底备份/恢复
│   ├── build-tools.sh     # 下载/定位 JDK、build-tools、android.jar、keystore
│   ├── app.sh          # tv-pilot 控制：状态/启停/构建/安装/截屏/ADB 守护
│   ├── require-profile.sh # 写操作闸门（matched / capability=verified）
│   ├── device-profile.sh  # 档案 init / lint / index
│   ├── providers/         # 品牌扩展点（每机型一个 .sh）
│   ├── check.sh           # 仓库自检（语法/链接/版本/评测结构）
│   └── lib.sh             # 公共库（adb 封装、超时、状态缓存、provider 加载）
├── references/
│   ├── local.md           # 本机事实（setup.sh 生成，gitignore）
│   ├── operations/        # 操作方法论（使用统计/屏保/儿童模式/广告/家长设置/开发…）
│   ├── devices/           # 设备档案（每型号一份 + 模板 + 支持矩阵 + 应用快照）
│   └── porting.md         # 新机型接入流程
└── evals/                 # 触发集 + 行为评测集
```

仓库根还有 `app/`（电视端 App 源码）、`docs/`（架构/契约/provider）、`tests/`（离线测试）、`AGENTS.md`（贡献者代理指南）。

## 7. 高频坑（先看这些，能省很多时间）

- **已知 IP 且 `adb devices` 有 device 时不要跑 `find-tv.sh`**：它是冷启动用的，天生 6–15 秒。
- **家长设置 PIN 是 `2580`**（出厂默认，4 位）：跑 `bash scripts/parent-pin.sh` 拿，别在代码里翻。
  App 目前**没有改 PIN 的界面**，所以电视上跑的就是这个值；连错 5 次锁 60 秒。
  家长自己改过的 PIN 只能放 `references/local.md`（gitignore）——「家长控制真实配置值」是脱敏红线，不许进仓库。
- **连不上时不要反复 `adb connect`**：先看 `adb devices` 状态——`unauthorized` 去电视按允许、`offline` 是待机/重启中、`Connection refused` 是 ADB 开关没生效。
- **`play_time_today_watchtime` 不是今日观看时长**（儿童模式计时，锁没开时根本不累加）。
- **ADB 能连 ≠ 电视亮着**：判断要看 `mWakefulness=Awake` + `mScreenState=ON`。
- **看电视时播放器不进前台**：PatchWall 详情页内嵌播放时前台一直是 `com.mitv.tvhome.DetailsActivity`，要按 class 名分类。
- **`SettingsProvider` 只支持单键查询**：LIKE 查询会抛 `IllegalArgumentException`。
- **不要整段 `logcat -d`**：一次约 15s；用 `-s <TAG>:*` 或 `-t 200` 限量。
- **新装的包不会立刻响应广播**：`stopped=true`，先用 `am start` 拉起一次。
- **不要用卸载/清数据来重置长条**：发 `am broadcast -a com.dsh.tvhud.RESET_USAGE`。
- **改分类规则必须 bump `UsageDay.SCHEME`**，否则旧数据会按新颜色画错。
- **写操作别自己拼 adb**：装 App 走 `app-deploy.sh`（顺序错了会丢无障碍条目）；其它写操作先 `require-profile.sh`。
- **别把本机事实写进 SKILL.md**：电视型号/IP/App 版本进 `references/local.md`（setup.sh 生成）。换机器只改 local.md。
- **动 `com.mitv.tvhome`（桌面）/ `com.xiaomi.mitv.settings`（设置）/ `com.android.*` 前单独确认**——禁错会黑屏或进不去设置。

## 8. 改这个 skill / 贡献代码

- 仓库级约定与命令：`AGENTS.md`；贡献流程：`CONTRIBUTING.md`。
- 提交前跑 `make check && make test`（语法、shellcheck、文档引用、版本一致性、离线测试）。
- **品牌差异进 provider**：`scripts/providers/<brand>-<model>.sh`，契约见 `docs/providers.md`；通用脚本不要写机型事实。
- 新增/修正设备档案：`bash scripts/device-profile.sh init|lint|index`，流程见 `references/porting.md`。
- 本机专属信息一律放 `.env` 或 `references/local.md`，两者都在 `.gitignore` 里。

## 9. 参考文档（按需读，不要一上来全读）

| 文件 | 什么时候读 |
|---|---|
| `references/local.md` | 每次开工先看本机事实（没有就先跑 setup.sh） |
| `references/operations/parent-settings.md` | 家长设置：PIN（2580）、各项开关、「今天免限」、调试广播 |
| `references/operations/usage-stats.md` | 做使用统计前对齐口径 |
| `references/operations/screensaver.md` | 屏保识别、角标、今日使用长条 |
| `references/operations/child-mode.md` | 配置儿童模式/时长限制 |
| `references/operations/ad-packages.md` | 去广告前确认包名与风险 |
| `references/operations/boot-autostart.md` | 开机自启、`stopped=true` 拦截 |
| `references/operations/remote-battery.md` | 电量读数异常时核口径 |
| `references/operations/enable-adb.md` | 还没开 ADB 调试时 |
| `references/operations/dev-workflow.md` | 构建→部署→验证闭环（改自研 App 必读） |
| `references/operations/build-mini-apk.md` | 不装 Android Studio 写小 App |
| `references/devices/README.md` | 支持哪些机型、能力等级、贡献档案 |
| `references/porting.md` | 新机型接入：从 0 采出一份档案 |
| `docs/architecture.md` | 想理解两层结构与数据流 |
| `docs/json-contract.md` | 写脚本/解析输出前 |
| `docs/providers.md` | 给新品牌加操作能力 |
