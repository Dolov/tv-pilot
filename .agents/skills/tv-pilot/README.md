# tv-pilot skill（中文说明）

这是给 AI 编码代理用的「电视运维」skill：把局域网 ADB 操作小米/Redmi 电视的经验，固化成一串**能直接跑的命令**和一套**按需读的文档**。它同时是仓库里 `app/` 电视端 App 的运维底座。

> 代理入口是 [SKILL.md](SKILL.md)；本文件给人看（怎么装、目录怎么读、坏了怎么修）。

## 前置条件

- macOS（设备发现用 `dns-sd` / `ipconfig` / `arp`）或 Linux（发现逻辑需按需替换，其余脚本通用）
- 电视已开启 ADB 调试，且与电脑在同一局域网；首次连接要在电视上按「允许」
- 能联网下载 platform-tools（首次约 15MB）；构建 App 另需 JDK 17/21 + build-tools（约 50MB）

## 安装

```bash
git clone https://github.com/Dolov/tv-pilot.git ~/tv-pilot
ln -s ~/tv-pilot/.agents/skills/tv-pilot ~/.agents/skills/tv-pilot
```

- 代理默认从 `~/.agents/skills/` 自动加载；不同宿主（Claude Code / Codex / Pi / Gemini）的目录不同，链到对应位置即可。
- 不愿放全局目录也行：跑脚本时设 `TVPILOT_SKILL=/path/to/tv-pilot/.agents/skills/tv-pilot`。

## 首次配置（一条命令）

```bash
cd ~/tv-pilot/.agents/skills/tv-pilot
bash scripts/setup.sh            # 生成 .env、探测电视、写 references/local.md
```

`local.md` 是**本机事实**（电视地址、型号、档案、App 版本、工具链状态），已 gitignore。换电视/重装工具链后重跑 `setup.sh` 即可，不需要改 `SKILL.md`。

## 目录结构

```
tv-pilot/
├── SKILL.md                 # 代理入口：操作索引 + 原则 + 高频坑
├── README.md                # 本文件：给人看的安装与结构说明
├── scripts/                 # 全部可执行入口（见下）
│   ├── providers/           # 品牌扩展点：每机型一个 .sh
│   └── lib.sh               # 公共库（adb 封装/超时/状态缓存/provider 加载）
├── references/
│   ├── local.md             # 本机事实（setup.sh 生成，gitignore）
│   ├── operations/          # 操作方法论：使用统计/屏保/儿童模式/广告/开发…
│   ├── devices/             # 设备档案：每型号一份 + 模板 + 支持矩阵 + 应用快照
│   └── porting.md           # 新机型接入流程
└── evals/                   # 触发集 + 行为评测集
```

## 常用命令

| 想做的事 | 命令 |
|---|---|
| 连接电视 | `bash scripts/ensure-tv.sh -v` |
| 识别机型 + 匹配档案 | `bash scripts/identify-tv.sh` |
| 只读探测（型号/固件/包/广告候选） | `bash scripts/probe.sh` |
| 今天看了多久 / 看了什么 | `bash scripts/usage.sh` |
| 遥控器电量 | `bash scripts/remote-battery.sh` |
| 开机 / 待机 / 关机 | `bash scripts/tv-state.sh [--json]` |
| tv-pilot 控制（状态/启停/构建/安装/截屏） | `bash scripts/app.sh status` |
| 写操作前确认能力 | `bash scripts/require-profile.sh <capability>` |
| 档案工具 | `bash scripts/device-profile.sh init\|lint\|index` |
| 仓库自检（贡献前） | `bash scripts/check.sh` |

## 支持哪些电视

已实测机型见 [references/devices/README.md](references/devices/README.md)（当前：MiTV-NSOP0 / MIUI TV Android 9）。其它品牌按 [references/porting.md](references/porting.md) 接入：先只读采集，再补设备档案和 provider，最后 `lint` + 提 PR。`identify-tv.sh` 会给出 `matched / similar / unknown`，写操作默认被 `require-profile.sh` 拦在 `unknown` 之外。

## 安全说明

- 脚本默认只读；写操作（install / uninstall / settings put / disable）先说明再执行，`app-deploy.sh` 带写闸门。
- 只用可逆手段：`pm uninstall --user 0` 配 `cmd package install-existing`；不 root、不改 `/system`、不刷机。
- 敏感信息只放 `.env` 和 `references/local.md`（都已 gitignore）；ADB 的 5555 是明文端口，用完建议关掉。
- 已知限制与威胁模型见仓库根 [SECURITY.md](../../../SECURITY.md)。

## 贡献

- 仓库级约定：[AGENTS.md](../../../AGENTS.md)
- 贡献流程：[CONTRIBUTING.md](../../../CONTRIBUTING.md)
- 提交前：`make check && make test`
- 品牌差异请加到 `scripts/providers/`，不要写进通用脚本或通用文档。
