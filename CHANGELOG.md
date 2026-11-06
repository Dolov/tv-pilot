# Changelog

本文件格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布前的改动都记在 `Unreleased`。

## [Unreleased]

### Added

- 根目录 `AGENTS.md`：面向 AI 编码代理的仓库结构、命令、改动边界与提交红线。
- 根目录 `Makefile`：`help`（默认）/ `check` / `test` / `apk` / `index`。
- `SECURITY.md`：ADB 明文端口、导出广播、`WRITE_SECURE_SETTINGS` 与使用数据的威胁模型、
  已知限制和漏洞报告流程。
- `.github/workflows/check.yml`：Ubuntu 上安装 shellcheck，跑 `make check` 与 `make test`。
- `.github/PULL_REQUEST_TEMPLATE.md`：提交前自检清单。
- `.agents/skills/tv-pilot/scripts/check.sh`：仓库级自检，覆盖 bash 语法、
  shellcheck（仅 error）、JSON、设备档案 lint/index、文档引用、版本一致性、
  `SKILL.md` 结构与禁提交文件。
- `tests/`：基于 `tests/fake-adb` 与 `tests/fixtures/` 的离线测试，
  覆盖 `identify-tv`、`remote-battery`、`tv-state`、`apps-inventory`，不需要真实电视。

### Changed

- 重写 `CONTRIBUTING.md`：按「贡献类型 → 文件放哪 → 校验命令」组织，
  新增设备档案 / 通用脚本 / 品牌 provider / App / 文档五条路径，以及 PR 检查清单。
- `README.md` 与 `SKILL.md` 中的参考文档路径统一到 `references/operations/`、`docs/`；
  本机事实移到 `references/local.md`（生成且 gitignore）。
- 通用脚本与机型差异分层：品牌差异进 `scripts/providers/`，设备事实进设备档案；
  新增 `setup.sh`、`tv-hud.sh`、`require-profile.sh` 等入口。

### Fixed

- 写操作闸门：`app-deploy.sh` 之前把 `write` 当 capability 传给 `require-profile.sh`，
  导致 matched 设备也被拒绝（`write` 不是合法能力键）；改为只校验 matched。
- `lib.sh` 的 `.env` 加载循环触发 shellcheck SC2066，改为直接 `source`；CI 现在会跑
  `shellcheck -S error`。
- provider 支持本地覆盖：`$TVPILOT_DEVICES_DIR/providers/<slug>.sh` 优先于仓库同名文件，
  私有电视的代码支持不必提交；`remote-battery --json` 增加 `provider` / `provider_source`
  便于排查。
- `check.sh` 的版本检查改为「`version.properties` 与 Manifest 一致 + `build.sh` 读取
  properties」，避免只 bump 一处就误报；`AGENTS.md` / `CONTRIBUTING.md` / PR 模板同步。
- 文档引用在目录重组后的大量失效链接；`make check` 现在会拦截新增的失效引用。
- 版本号原先分散在 `AndroidManifest.xml` 与 `build.sh`，现由根 `version.properties` 提供，
  `make check` 校验它与 Manifest / build.sh 一致。
- 设备支持矩阵（`references/devices/README.md`）改为 `device-profile.sh index` 生成，
  自检会检测它是否与档案不一致。
