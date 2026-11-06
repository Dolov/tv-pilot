# JSON / 退出码契约

给脚本、代理和测试用的稳定接口。**人类可读输出不受影响**：不加 `--json` 时保持原来的中文结论。

## 退出码

| 码 | 含义 | 典型场景 |
|---|---|---|
| `0` | 成功 | 识别完成、探测完成、diff 无差异、电视状态读到 |
| `1` | 用法错误 / 校验失败 | 未知参数、`device-profile.sh lint` 不通过、`apps-inventory.sh diff` 有差异（历史契约） |
| `2` | 环境 / 连接问题 | adb 连不上、adb shell 中断、缺 python3、写文件失败 |
| `3` | 能力未验证 | `require-profile.sh` 拒绝：similar / unknown 档案，或 capability ≠ verified |
| `4` | 未找到设备或档案 | 没有可用电视、快照文件不存在、显式指定的档案不存在 |

说明：

- `apps-inventory.sh diff` 用 `1` 表示「有变化」而不是用法错误，这是保留的历史契约；调用方同时看 `--json` 的 `added` / `removed` 更稳妥。
- `find-tv.sh`、`ensure-tv.sh` 是较早的脚本，仍沿用「1 = 没找到 / 连不上」的历史退出码；需要严格区分时用 `probe` / `identify-tv` / `tv-state`。
- `tv-state.sh --json` 在连不上时也会输出 JSON（`state:"off"`），然后按上表返回 `4` 或 `2`；解析方应先读 JSON，再看退出码。
- `--json` 时诊断/提示走 stderr，stdout 只留 JSON（`find-tv.sh` 见其条目里的例外）。

所有字符串字段都可能为空字符串（表示「读到了但为空」）；结构性缺失用 `null`。

## identify-tv.sh --json

```bash
bash scripts/identify-tv.sh --json
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `device` | object | 设备身份，见下表 |
| `device.model` | string | `ro.product.model` |
| `device.brand` | string | `ro.product.brand` |
| `device.device` | string | `ro.product.device`（codename） |
| `device.sdk` | string | `ro.build.version.sdk` |
| `device.fingerprint` | string | `ro.build.fingerprint` |
| `device.home` | string | 默认桌面组件（`包名/Activity`，可能为空） |
| `device.target` | string | adb 串口（如 `192.168.1.50:5555`） |
| `status` | string | `matched` / `similar` / `unknown` |
| `match` | string | `status` 的兼容别名（写操作闸门 `require-profile.sh` 消费） |
| `model` | string | `device.model` 的顶层别名 |
| `profile` | string \| null | 命中档案路径；仓库档案是相对 skill 根的路径，本地覆盖档案是绝对路径 |
| `profile_path` | string \| null | 命中档案的绝对路径（`require-profile.sh` 用） |
| `profile_source` | string \| null | `repo` / `local`；未命中为 `null` |
| `profile_slug` | string \| null | 档案 slug（frontmatter `profile`，缺省用文件名） |
| `capabilities` | object | 档案 frontmatter 的 `capabilities` 键值；未命中为 `{}` |
| `similar` | array\<string\> | 同品牌候选档案路径（`similar` 时非空） |

`--kv` 输出同样的关键信息，每行 `key=value`：`status` / `model` / `brand` / `device` /
`sdk` / `fingerprint` / `home` / `dev` / `profile_slug` / `profile_path` / `profile_source`，
命中时另有 `profile=`（显示路径）、`cap.*=` 与 `similar=`。`lib.sh` 的 `tvp_profile_slug`
只依赖 `profile_slug`。

## probe.sh --json

```bash
bash scripts/probe.sh --json
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `model` | string | `ro.product.model` |
| `brand` | string | `ro.product.brand` |
| `sdk` | string | `ro.build.version.sdk` |
| `fingerprint` | string | `ro.build.fingerprint` |
| `packages` | number | `pm list packages` 行数；读不到为 `0` |
| `third_party` | number | `pm list packages -3` 行数；读不到为 `0` |
| `child_mode_keys` | number | `settings list system` 中匹配 child/kid/play_time/user_mode 的条数；读不到为 `0` |

## remote-battery.sh --json

成功：

| 字段 | 类型 | 说明 |
|---|---|---|
| `device` | string | adb 串口 |
| `provider` | string | 生效的 provider 名（`_default` 表示未匹配机型） |
| `provider_source` | string | `local` / `repo` / `default` |
| `model` | string | 电视型号（可能为空） |
| `mac` | string | 遥控器 MAC（小写冒号分隔） |
| `name` | string | 蓝牙配对名；取不到时为 `未知型号` |
| `battery` | number | 电量百分比 0–100 |
| `percent` | number | `battery` 的兼容别名 |
| `source` | string | 成功时固定为 `settings` |
| `level` | string | `正常` / `偏低` / `低电量` |
| `low_threshold` | number \| null | 低电量阈值（默认 25；`--deep` 读到 0 时为 `null`） |
| `last_report` | string | `--deep` 时日志里的最近上报时间；未跑 `--deep` 为空字符串 |
| `warning_seen` | boolean | `--deep` 时日志里是否出现过 `HID_BATTERY_WARRING` |
| `deep` | boolean | 是否跑了 `--deep` |
| `updated_at` | string | 本次读取时间，`YYYY-MM-DDTHH:MM:SS±HHMM` |
| `elapsed_ms` | number | 本次耗时（毫秒） |

读不到键（红外遥控器 / 未配对 / 从未上报，退出码 2）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `device` | string | adb 串口 |
| `provider` | string | 生效的 provider 名 |
| `provider_source` | string | `local` / `repo` / `default` |
| `model` | string | 电视型号（可能为空） |
| `battery` | null | 固定 `null` |
| `percent` | null | 固定 `null` |
| `source` | null | 固定 `null` |
| `reason` | string | 固定 `no_hid_battery_key` |
| `updated_at` | string | 本次读取时间 |

## find-tv.sh --json

输出一个 JSON 数组；每个元素：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | mDNS/RAOP 设备名 |
| `mac` | string | RAOP MAC（小写冒号分隔） |
| `ip` | string | ARP 里解析到的 IP；没解析到为空字符串 |

注意：只有 `--json --list` 同时使用时 stdout 才是纯 JSON。单独的 `--json` 仍会先打印
「本机网段」等人类可读头部（历史实现）；需要机器解析时用 `--list --json`。
没找到设备或拿不到网段时退出码为 `1`（历史契约）。

## tv-state.sh --json

```bash
bash scripts/tv-state.sh --json
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `state` | string | `booted`（亮屏运行）/ `standby`（熄屏待机）/ `off`（关机、断电、连不上或状态未知） |
| `wakefulness` | string \| null | `mWakefulness`（`Awake` / `Dozing` / `Asleep`） |
| `screen` | string \| null | `mScreenState`（`ON` / `OFF`） |
| `focus` | string \| null | 当前焦点组件 |
| `uptime_sec` | number \| null | 开机秒数（整数）；读不到为 `null` |
| `model` | string \| null | 电视型号；当前查询失败时回退 state 缓存 |

判定：`Awake` + 屏幕 `ON` → `booted`；`Awake` / `Dozing` / `Asleep` 的其他屏幕状态 →
`standby`；其余 → `off`。连不上时 JSON 仍会输出，退出码 `4`；adb 连上但命令失败退出码 `2`。

## apps-inventory.sh --json

### list

```bash
bash scripts/apps-inventory.sh list --json
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `packages` | array\<object\> | 全部包（不只第三方），按包名排序 |
| `packages[].type` | string | `T` = 第三方 / 预装数据分区；`S` = 系统 |
| `packages[].pkg` | string | 包名 |
| `total` | number | 包总数 |
| `third_party` | number | `type=T` 的数量 |

`--all` 只影响人类可读输出；JSON 始终包含全部包（用 `type` 区分）。

### diff

```bash
bash scripts/apps-inventory.sh diff --json
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `added` | array\<string\> | 当前有、快照里没有的包名 |
| `removed` | array\<string\> | 快照里有、当前没有的包名 |
| `snapshot` | string | 本次使用的快照绝对路径 |

有变化时退出码仍为 `1`；无变化为 `0`。快照不存在时退出码 `4`，并提示先 `update`
或用 `--profile <slug>` 指定机型。

### update

保持原行为：先打印差异，再用当前状态覆盖解析出的快照文件；不接受 `--json`。

## 相关文档

- 品牌差异怎么进 provider：`docs/providers.md`
- 新机型怎么采集档案：`references/porting.md`
- 写操作闸门：`scripts/require-profile.sh`
