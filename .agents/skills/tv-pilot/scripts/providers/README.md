# 品牌 provider（scripts/providers/）

设备档案写「这台机器是什么、测过什么」，provider 写「这台机器该去哪个键/哪个日志取数据」。
通用脚本不写机型的包名、settings 键、日志路径，而是通过这里的函数拿差异。

完整说明与新增机型步骤见 [`docs/providers.md`](../../../../../docs/providers.md)；
新机型的数据采集流程见 [`references/porting.md`](../../references/porting.md)。

## 加载方式

provider 不是独立可执行脚本，而是被 `scripts/lib.sh` **source** 的 bash 片段：

```bash
source scripts/lib.sh

slug="$(tvp_profile_slug)"        # state/env > identify-tv --kv（限时）> 空
tvp_load_provider "$slug"         # 幂等；找不到 <slug>.sh 就只用 _default.sh
prefix="$(tvp_provider_call tvp_battery_key_prefix)"
```

- 文件名 `<slug>.sh`，slug 与设备档案 frontmatter 的 `profile`、`state.tv_profile` 一致
  （例如 `xiaomi-mitv-nsop0.md` ↔ `xiaomi-mitv-nsop0.sh`）。
- 查找顺序：`$TVPILOT_DEVICES_DIR/providers/<slug>.sh`（本地覆盖，优先）→
  本目录 `<slug>.sh`（仓库）→ `_default.sh`。不想公开的机型支持放本地即可，不必进仓库。
- `TVPILOT_PROVIDER_SOURCE` 记录实际来源（`local` / `repo` / `default`），排查时先看它。
- `tvp_load_provider` 先 source `_default.sh`，再 source 具体 provider；没实现的函数自然回落到默认实现。
- `tvp_provider_call <fn>` 在函数不存在时输出空并返回 0，调用方不需要判断 provider 是否存在。
- 加载顺序是幂等的；切换 slug 时会先清掉上一份 provider 的六个契约函数，不会残留。

## 函数契约

所有函数：

- 只往 **stdout** 打**一个值（一行）**；不要输出日志、颜色、多行表格。
- 不知道/不适用时输出**空**，不要猜。
- 不修改电视、不读网络、不写 state；需要 adb 的探测留在调用方脚本里。
- 兼容 macOS bash 3.2：不用关联数组、`${var,,}`、`mapfile`。

| 函数 | 输出 | 为空时调用方怎么办 |
|---|---|---|
| `tvp_screensaver_pkg()` | 屏保包名（不含 Activity） | 屏保能力按未验证处理 |
| `tvp_play_log_path()` | 电视端播放日志绝对路径 | `usage.sh` 跳过播放记录并提示 |
| `tvp_battery_key_prefix()` | 遥控器电量 Settings 键前缀 | `remote-battery.sh` 回退 `bluetooth_hid_battery_` 并提示 |
| `tvp_apps_snapshot()` | 该机型 apps 快照绝对路径 | 继续尝试仓库/本地 `<slug>-apps.txt` |
| `tvp_ad_patterns()` | 广告包名 grep 正则（`grep -iE`） | `probe.sh` 回退 `ad|push|analy|stat` |
| `tvp_home_pkg()` | 默认桌面包名 | 由调用方/文档按未知处理 |

已接入的调用点：`apps-inventory.sh`（`tvp_apps_snapshot`）、`remote-battery.sh`
（`tvp_battery_key_prefix`）、`usage.sh`（`tvp_play_log_path`）、`probe.sh`（`tvp_ad_patterns`）。
`tvp_screensaver_pkg()` / `tvp_home_pkg()` 目前是保留扩展点，供操作文档和后续脚本使用。

## 新增一台电视

1. 先按 `references/porting.md` 采集并写设备档案（仓库或 `device-profile.sh init --local`）。
2. 确认档案 slug（frontmatter `profile`，或文件名去掉 `.md`）。
3. 新建 `scripts/providers/<slug>.sh`，只实现有实测依据的函数；其余留空自动回落。
4. 验证：

```bash
bash -n scripts/providers/<slug>.sh
bash -c 'source scripts/lib.sh; tvp_load_provider <slug>; \
  tvp_provider_call tvp_battery_key_prefix; tvp_provider_call tvp_apps_snapshot'

# 用假 adb 跑一遍接入脚本（不要连真电视）
TVPILOT_ADB_BIN=/path/to/fake-adb TVPILOT_STATE_FILE=/tmp/state.env \
  bash scripts/identify-tv.sh --json
TVPILOT_ADB_BIN=/path/to/fake-adb TVPILOT_STATE_FILE=/tmp/state.env \
  bash scripts/apps-inventory.sh diff --json
```

5. 跑 `make check`（bash -n / shellcheck error / 文档引用）与 `make test`。

## 命名规则

- `<brand>-<model>.sh`，全小写、连字符，与档案 slug 完全一致。
- 一个型号一个文件；不要建 `xiaomi.sh` 之类的品牌级 provider（同品牌不同型号的键值也可能不同）。
- 不要提交真实 IP / MAC / 序列号 / 账号；provider 里只放包名、键名、路径这类机型事实。

## 与本地覆盖层的关系

`TVPILOT_DEVICES_DIR`（默认 `~/.config/tv-pilot/devices`）放私人档案、快照和 provider，
不用提交。provider 可以用它作为兜底路径（如 `xiaomi-mitv-nsop0.sh` 的 `tvp_apps_snapshot`），
但**不要**把个人路径写死成 `$HOME` 下的固定值——用 `$TVPILOT_DEVICES_DIR`。
本地 provider 放在 `$TVPILOT_DEVICES_DIR/providers/<slug>.sh`，优先于仓库同名文件。
