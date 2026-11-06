# AGENTS.md

面向在本仓库工作的 AI 编码代理。先读本文件；运行时操作细节读
`.agents/skills/tv-pilot/SKILL.md`，给人看的贡献流程读 `CONTRIBUTING.md`。

## 仓库是两层

1. **Skill**（`.agents/skills/tv-pilot/`）：代理可执行的操作手册 + bash 脚本 + 实测档案 + 评测。
   安装到 `~/.agents/skills/tv-pilot` 后由代理自动加载。
2. **TV HUD**（`tvhud/`）：电视上运行的 Android App（纯 Java，无 Gradle），`build.sh` 直接出 APK。

两层通过 skill 的脚本协作：发现/识别设备 → 构建 → 安装授权 → 真机验证。

## 目录地图

```text
AGENTS.md / CONTRIBUTING.md / SECURITY.md / CHANGELOG.md / Makefile
version.properties                  # 版本号单一来源（versionName / versionCode）
.agents/skills/tv-pilot/
├── SKILL.md                        # 代理入口：操作索引、输出契约、高频坑
├── scripts/                        # 通用脚本（lib/ensure/identify/probe/usage/...）
│   ├── providers/                  # 品牌扩展点：每机型一个 .sh（契约见 docs/providers.md）
│   ├── check.sh                    # 仓库自检（make check 调用）
│   └── lib.sh                      # adb 封装、超时、状态缓存、provider 加载
├── references/
│   ├── operations/                 # 方法论：使用统计/屏保/儿童模式/广告/开发...
│   ├── devices/                    # 设备档案 + 模板 + 支持矩阵 + 应用快照
│   ├── porting.md                  # 新机型接入流程
│   └── local.md                    # 本机事实（setup.sh 生成，gitignore）
└── evals/                          # 触发集 + 行为评测集
docs/                               # 架构 / JSON 契约 / provider 契约
tests/                              # 离线测试（fake-adb + fixtures，不需要真电视）
tvhud/                              # 电视端 App：src/ + AndroidManifest.xml + build.sh + assets/
.github/                            # CI（check.yml）、PR 模板、issue 模板
```

## 常用命令

| 目的 | 命令 |
|---|---|
| 列命令 | `make help`（默认 target） |
| 仓库自检 | `make check` → `.agents/skills/tv-pilot/scripts/check.sh` |
| 离线测试 | `make test` → `tests/run.sh`（fake adb，不连电视） |
| 构建 APK | `make apk` → `bash tvhud/build.sh`（需 `.tools/` 工具链） |
| 重建设备矩阵 | `make index` → `device-profile.sh index` |
| 连电视 / 识别 | `bash .agents/skills/tv-pilot/scripts/ensure-tv.sh -v` / `identify-tv.sh` |
| 档案校验 | `bash .agents/skills/tv-pilot/scripts/device-profile.sh lint` |

提交前至少跑：`make check && make test`。

## 改动边界

- **通用脚本**（`scripts/*.sh`）只写跨机型逻辑：连接阶梯、adb 封装、输出格式。
  **不要**把某个机型的包名、settings 键、Activity 名写进通用脚本。
- **设备档案**（`references/devices/<brand>-<model>.md`）只写该机型**实测过**的事实，
  按 `references/porting.md` 采集；`capabilities` 四级见 `CONTRIBUTING.md`。
- **品牌 provider**（`scripts/providers/<brand>-<model>.sh`）承接品牌/机型差异，
  按 `docs/providers.md` 的契约实现；通用脚本通过 provider 取差异。
- **App**（`tvhud/`）改源码后 `make apk`，用 `scripts/app-deploy.sh` 部署（顺序内置）。
  版本号单一来源是根 `version.properties`；改版本时同步 `tvhud/AndroidManifest.xml`（
  `build.sh` 直接读取 properties，只有文件缺失时才回退硬编码）。
- `references/devices/README.md` 是 `make index` 生成的矩阵，不要手改。
- 不要动 `references/local.md`（本机生成、已 gitignore）。

## 编码约定

- 脚本必须兼容 macOS 自带 **bash 3.2**：不用关联数组、`${var,,}`、`mapfile`。
- 以 `#!/usr/bin/env bash` 开头，`set -uo pipefail`；失败要有可执行的下一步提示。
- 默认只读。写操作（install/uninstall/settings put/disable/broadcast）先说清楚再执行，
  装 App 一律走 `scripts/app-deploy.sh`，其它写操作先过 `require-profile.sh`。
- 面向用户的输出用中文、先给结论；机器可读输出走 `--json`（契约见 `docs/json-contract.md`）。
- 不联网下载（除用户明确要求跑 `install-adb.sh` / `build-tools.sh ensure`），不提交构建产物。

## 提交与红线

- 提交信息：`<type>(<scope>): <summary>`，例如 `docs(device): add hisense-a6g profile`。
- **禁止提交**：`.env`、`.tools/`（含 keystore）、`*.apk` / `.build/`、`tvhud/assets/*.otf|ttf`、
  `references/local.md`、真实 IP/MAC/序列号/账号/观看记录。
- 改到设备档案、provider、通用脚本或版本号时，**先跑 `make check`** 再继续；
  它覆盖 bash 语法、shellcheck(error)、JSON、档案 lint/index、文档引用、版本一致性和禁提交文件。
- 不要把历史 warning 或 TODO 当成通过：`check.sh` 只放行 error，但新增代码应自己收敛。
