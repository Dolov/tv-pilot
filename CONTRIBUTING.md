# 贡献指南

这个仓库收集「用局域网 ADB 操作电视」的实测经验，并按机型组织成**设备档案**；
`tvhud/` 是电视上实际运行的 App。欢迎补充新机型、修正旧档案、改进通用脚本与 App。

**提交前必跑：**

```bash
make check && make test
```

`make check` 不连电视；`make test` 用 `tests/fake-adb` 离线跑，不需要真实设备、不下载工具链。

## 贡献类型 → 文件放哪 → 校验命令

| 类型 | 放哪里 | 校验 |
|---|---|---|
| 新机型 / 修正档案 | `.agents/skills/tv-pilot/references/devices/<brand>-<model>.md` | `device-profile.sh lint` + `index` + `make check` |
| 通用脚本 | `.agents/skills/tv-pilot/scripts/*.sh` | `bash -n`、`shellcheck -S error`、`make check && make test` |
| 品牌 provider | `.agents/skills/tv-pilot/scripts/providers/<brand>-<model>.sh` | 按 `docs/providers.md` 契约 + `make check && make test` |
| App（TV HUD） | `tvhud/src/`、`tvhud/AndroidManifest.xml`、`tvhud/build.sh` | `make apk` + 真机部署验证（见下） |
| 文档 | `references/operations/`、`references/porting.md`、`docs/` | `make check`（含文档引用存在性） |

## 1. 设备档案（新机型 / 修正）

每个型号一份，只写**实测过**的机型差异；通用方法论留在 `references/`。

```bash
cd .agents/skills/tv-pilot
bash scripts/identify-tv.sh              # 确认 matched / similar / unknown
bash scripts/device-profile.sh init      # 生成骨架（自动带当前设备信息）
# 填内容：只写实测过的；没测的保持 unknown
bash scripts/device-profile.sh lint      # 必填字段 + 章节 + 证据 + 脱敏
bash scripts/device-profile.sh index     # 重建 references/devices/README.md 支持矩阵
```

详细采集步骤见 `.agents/skills/tv-pilot/references/porting.md`。
只改档案时，`index` 产生的支持矩阵要和档案一起提交，否则 `make check` 会报矩阵过期。

## 2. 通用脚本

- 放 `.agents/skills/tv-pilot/scripts/`，并在 `.agents/skills/tv-pilot/SKILL.md` 的脚本清单补一行。
- 只写跨机型逻辑；包名、settings 键、Activity 名等机型事实进设备档案或 provider。
- 兼容 macOS 自带 **bash 3.2**：不用关联数组、`${var,,}`、`mapfile`。
- 以 `#!/usr/bin/env bash` 开头、`set -uo pipefail`；所有可能阻塞的 adb 调用要有超时。
- **默认只读**；新增写操作要可逆、先打印将执行的命令，并说明还原方式。
- 新脚本至少被 `tests/` 里一个离线用例覆盖，或说明为什么只能真机验证。

## 3. 品牌 provider

品牌差异（电量键、屏保组件、包名规则等）放
`.agents/skills/tv-pilot/scripts/providers/<brand>-<model>.sh`，通用脚本不要写死机型事实。
契约、加载顺序和必选函数见 `docs/providers.md`；改 provider 后同样跑 `make check && make test`。

## 4. App（`tvhud/`）

```bash
make apk                                  # 产出 .build/out/tvhud.apk
cd .agents/skills/tv-pilot
bash scripts/app-deploy.sh --apk ../../.build/out/tvhud.apk --pkg com.dsh.tvhud \
  --activity com.dsh.tvhud/.HomeActivity --overlay --usage \
  --acc com.dsh.tvhud/com.dsh.tvhud.BootAccessibilityService
```

- 装 App 一律走 `scripts/app-deploy.sh`：顺序错了会丢 appops / 无障碍条目。
- 发版改版本号：根 `version.properties`（单一来源）与 `tvhud/AndroidManifest.xml`；
  `tvhud/build.sh` 直接读取 properties（文件缺失才用 fallback 硬编码）；`make check` 会校验三者一致。
- 真机验证至少给出：进程/服务在、`logcat` 无崩溃、关键界面截图或 `screencap`。

## 5. 文档

- 操作方法论放 `references/operations/`，架构/契约放 `docs/`；不要写进 `SKILL.md` 之外的本机事实。
- 引用文件时用真实相对路径（如 `references/operations/screensaver.md`、`scripts/ensure-tv.sh`）；`make check` 会校验引用存在。
- 本机型号/IP/App 版本写 `references/local.md`（`setup.sh` 生成、已 gitignore），不要写进公共文档。

## 证据要求

- 每条结论尽量带 **命令 + 输出摘录 + 日期**，例如：

  ```
  $ adb shell getprop ro.build.version.sdk
  28          # 2026-11-04
  ```

- 没证据的结论写成「推测」，不要写进 `capabilities` 的 `verified`。
- 能力等级四级：`verified` 实测可用 / `partial` 部分可用 / `unknown` 未验证 / `unsupported` 确认不可用。
- 同一型号不同固件结论冲突时，写明固件版本与差异，不要互相覆盖。

## 脱敏红线（PR 会被拒绝）

- 真实局域网 IP、公网 IP、DDNS 域名
- MAC 地址（遥控器、Wi-Fi/RAOP、蓝牙）
- 设备序列号、ADB 授权密钥、账号/邮箱/手机号
- Wi-Fi 名称（SSID）、路由器/宽带信息
- 儿童模式密码、家长控制真实配置值
- 截图里的账号头像、观看记录、家庭照片

需要展示时用占位符：`<TV_IP>`、`<REMOTE_MAC>`、`<片名>`、`<YYYY-MM-DD>`。
本仓库的敏感配置一律放本地 `.env` / `references/local.md`（均已 gitignore）。

## 提交信息

用 `<type>(<scope>): <summary>`，例如：

```
docs(device): add hisense-a6g profile
feat(script): add tv-state --json
fix(tvhud): keep focus when list refreshes
```

常用 type：`feat` / `fix` / `docs` / `test` / `chore`。一个 PR 聚焦一件事。

## PR 检查清单

- [ ] 已跑 `make check && make test`，并在 PR 里贴结果（或说明失败项及原因）
- [ ] 设备档案附了「命令 + 输出摘录 + 日期」证据；没测的保持 `unknown`
- [ ] 已脱敏：无真实 IP / MAC / 序列号 / SSID / 账号 / 观看记录
- [ ] 通用脚本与机型事实已分离（机型差异在档案或 `scripts/providers/`）
- [ ] 没有提交 `.env`、`.tools/`、keystore、`.build/`、APK、`tvhud/assets/*.otf|ttf`
- [ ] 新增/移动文件后，文档与 `SKILL.md` 的引用路径仍然有效
- [ ] 改 App 时 `version.properties` 与 `AndroidManifest.xml` 一致，并说明真机验证方式
- [ ] 改设备档案时 `index` 的矩阵变更一并提交

## 不建议的贡献

- 把某个机型的包名/键值直接写进通用脚本或通用文档
- 提交厂商固件、APK、jadx 反编译产物
- 无证据地「提升」别人档案里的能力等级
- 默认自动执行写操作（禁包、改设置、装 App）
