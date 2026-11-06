# 品牌 provider 机制

设备档案回答「这台电视是什么、测过什么」，provider 回答「这台电视的数据该去哪里取」。
两层分开后，通用脚本（`probe` / `remote-battery` / `usage` / `apps-inventory`）不再出现
某个品牌的包名、settings 键或日志路径。

- 档案：`references/devices/<slug>.md`（可提交）或 `$TVPILOT_DEVICES_DIR/<slug>.md`（本地覆盖）
- provider：`scripts/providers/<slug>.sh`（可提交）或 `$TVPILOT_DEVICES_DIR/providers/<slug>.sh`（本地覆盖，优先）
- 默认回落：`scripts/providers/_default.sh`

新机型的数据采集流程见 `references/porting.md`；JSON 字段与退出码见 `docs/json-contract.md`。

## 查找与加载

```bash
source scripts/lib.sh

slug="$(tvp_profile_slug)"
#   env TVPILOT_PROFILE / TV_PROFILE
#   > state 缓存 tv_profile
#   > identify-tv.sh --kv（硬超时，默认 6s）
#   找不到时输出空

tvp_load_provider "$slug"
#   幂等；先 source _default.sh，再 source 具体 provider
#   查找顺序：$TVPILOT_DEVICES_DIR/providers/<slug>.sh（本地）
#           → scripts/providers/<slug>.sh（仓库）→ 只用 _default.sh
#   TVPILOT_PROVIDER_NAME 记录实际使用的 provider（slug 或 _default）
#   TVPILOT_PROVIDER_SOURCE 记录来源（local / repo / default）

prefix="$(tvp_provider_call tvp_battery_key_prefix)"
#   函数不存在时输出空、返回 0
```

设计约束：

- `lib.sh` 被所有脚本 source，**加载器本身不能有副作用、不能跑 adb**；只有调用
  `tvp_profile_slug` 时才会（限时）触发 `identify-tv.sh`。
- 识别结果会写回 state 的 `tv_profile` / `tv_brand` / `tv_model` / `tv_match`，
  同一台机器后续脚本不再重复识别。
- `TVPILOT_PROFILE_AUTODETECT=0` 可关闭自动识别（离线测试、只读仓库时用）。
- 切换 slug 时会清掉上一份 provider 的六个函数，不会残留。

## 函数契约

provider 是被 source 的 bash 片段，不是可执行脚本。所有函数：

- 只往 **stdout** 输出**一个值（一行）**；不打印日志、提示或多行内容。
- 不知道 / 不适用时输出**空**，不要猜。
- 不修改电视、不读网络、不写 state。
- 兼容 macOS bash 3.2：不用关联数组、`${var,,}`、`mapfile`。

| 函数 | 输出 | 当前调用点 | 为空时 |
|---|---|---|---|
| `tvp_screensaver_pkg()` | 屏保包名（不含 Activity） | 保留扩展点 | 屏保能力按未验证处理 |
| `tvp_play_log_path()` | 电视端播放日志绝对路径 | `usage.sh` | 跳过播放记录并提示（stderr） |
| `tvp_battery_key_prefix()` | 遥控器电量 Settings 键前缀 | `remote-battery.sh` | 回退 `bluetooth_hid_battery_` 并提示（stderr） |
| `tvp_apps_snapshot()` | 该机型 apps 快照绝对路径 | `apps-inventory.sh` | 继续找仓库 / 本地 `<slug>-apps.txt` |
| `tvp_ad_patterns()` | 广告包名 grep 正则（`grep -iE`） | `probe.sh` | 回退 `ad\|push\|analy\|stat` |
| `tvp_home_pkg()` | 默认桌面包名 | 保留扩展点 | 由调用方 / 文档按未知处理 |

`tvp_screensaver_pkg()` 与 `tvp_home_pkg()` 目前还没有脚本消费，保留给屏保、桌面相关操作；
新增调用点时直接 `tvp_provider_call`，不要再写品牌 if/else。

## 命名规则

- 文件名 `<slug>.sh`，slug 与档案 frontmatter 的 `profile` 完全一致：
  `xiaomi-mitv-nsop0.md` ↔ `xiaomi-mitv-nsop0.sh`。
- 一个型号一个文件；不要建品牌级 `xiaomi.sh`（同品牌不同型号的键值也可能不同）。
- 全小写、连字符；不要带版本号或日期。
- 只放机型事实（包名、键名、路径、正则），不放本机 IP / MAC / 账号。

## 只想自己用：本地 provider

和本地档案一样，不想公开的机型支持可以放 `$TVPILOT_DEVICES_DIR/providers/<slug>.sh`
（默认 `~/.config/tv-pilot/providers/`），本地文件优先于仓库同名文件；`tvp_load_provider`
会把来源记在 `TVPILOT_PROVIDER_SOURCE`（`local` / `repo` / `default`）。这样私有电视的
代码支持不必进仓库，也不会和上游冲突。

## 新增一台电视的完整步骤

以 `hisense-a6g` 为例。

### 1. 采集并写档案

```bash
bash scripts/ensure-tv.sh -v
bash scripts/identify-tv.sh          # 先看是 matched / similar / unknown
bash scripts/probe.sh                # 型号、固件、包列表、儿童模式键

# 想提交到仓库：
bash scripts/device-profile.sh init --name hisense-a6g
# 只想自己用（写到 $TVPILOT_DEVICES_DIR）：
bash scripts/device-profile.sh init --local --name hisense-a6g
```

按 `references/porting.md` 填写 frontmatter（`brand` / `model` / `match` / `sdk` / `capabilities`）
和正文；没实测的能力保持 `unknown`。然后：

```bash
bash scripts/device-profile.sh lint
bash scripts/identify-tv.sh --json    # 确认 profile_source / profile_slug
```

### 2. 加 provider

新建 `hisense-a6g.sh`（放在 `scripts/providers/` 下），只实现有依据的函数：

```bash
#!/usr/bin/env bash

tvp_screensaver_pkg() { printf '%s\n' 'com.hisense.screensaver'; }
tvp_play_log_path() { :; }                       # 没有已知日志就留空
tvp_battery_key_prefix() { printf '%s\n' 'bluetooth_hid_battery_'; }
tvp_apps_snapshot() {
  local f="$TVPILOT_SKILL_DIR/references/devices/hisense-a6g-apps.txt"
  [[ -f "$f" ]] && printf '%s\n' "$f"
}
tvp_ad_patterns() { printf '%s\n' 'ad|push|analy|stat'; }
tvp_home_pkg() { printf '%s\n' 'com.hisense.launcher'; }
```

没写的函数会自动回落到 `_default.sh`（输出空），不需要重复空实现。

### 3. 本地覆盖层（可选）

不想公开私有型号时，档案和快照都放：

```text
$TVPILOT_DEVICES_DIR/                 # 默认 ~/.config/tv-pilot/devices
├── hisense-a6g.md                    # 与仓库同名时覆盖仓库档案
└── hisense-a6g-apps.txt
```

`identify-tv.sh` 先扫本地再扫仓库；同名文件本地优先，`profile_source` 为 `local`。
`device-profile.sh lint` 默认两边都校验，本地目录不存在时跳过。
`device-profile.sh index` 只重建仓库支持矩阵，不碰本地文件。

### 4. 验证

```bash
# 语法
bash -n scripts/providers/xiaomi-mitv-nsop0.sh   # 换成你的 slug 文件名

# 函数输出（不连电视）
bash -c 'source scripts/lib.sh; tvp_load_provider hisense-a6g; \
  tvp_provider_call tvp_apps_snapshot; tvp_provider_call tvp_ad_patterns'

# 离线跑接入脚本（fake adb，不碰真电视）
TVPILOT_ADB_BIN=tests/fake-adb \
TVPILOT_STATE_FILE=/tmp/tvp-state.env \
TVPILOT_DEVICES_DIR=/tmp/tvp-devices \
  bash scripts/identify-tv.sh --json

TVPILOT_ADB_BIN=tests/fake-adb \
TVPILOT_STATE_FILE=/tmp/tvp-state.env \
TVPILOT_DEVICES_DIR=/tmp/tvp-devices \
  bash scripts/apps-inventory.sh diff --json

# 仓库自检
make check
```

跑真机时再分别验证：

```bash
bash scripts/identify-tv.sh
bash scripts/remote-battery.sh
bash scripts/usage.sh
bash scripts/apps-inventory.sh diff
```

## 与 `references/porting.md` 的关系

`references/porting.md` 是**数据采集流程**：怎么从一台陌生电视测出型号、ADB 行为、
桌面/屏保、统计来源、儿童模式、可禁用包，最后写进档案。

provider 是**采集结果里「需要代码」的那部分**：同样是屏保包名，写在档案里给人看，
写在 provider 里给脚本用。流程上先按 porting 采集、写档案，确认某一项需要被脚本消费时再加 provider；
只有数据、没有脚本消费的项留在档案里即可，不必为了「完整」造 provider。

## 兼容性要点

- bash 3.2：不要用关联数组（`declare -A`）、`${var,,}`、`mapfile`、`readarray`。
- 所有阻塞操作要有超时；provider 自己不应该发起 adb/网络调用。
- 新增脚本调用 provider 时，先 `tvp_load_provider`，再用 `tvp_provider_call`；
  不要在通用脚本里判断品牌名。
- 改 provider 或调用点后跑 `make check`（bash -n / shellcheck error / 文档引用）和 `make test`。
- `remote-battery.sh --deep` 的日志 tag（`Xaomi RC info` / `HID_BATTERY_WARRING`）仍是小米实测口径，
  provider 目前只覆盖快路径的「Settings.Global 键前缀」；新品牌没有这些日志时 `--deep` 只影响交叉验证，
  不影响默认快路径。若新品牌电量不在 `Settings.Global`，需要先在 issue/PR 里讨论扩展契约（例如 `tvp_battery_read`）。
