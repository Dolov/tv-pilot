# 贡献指南

这个仓库收集「用局域网 ADB 操作电视」的实测经验，并按机型组织成**设备档案**。
欢迎补充新机型、修正旧档案、改进通用脚本。

## 能贡献什么

| 类型 | 放哪里 | 说明 |
|---|---|---|
| 新机型档案 | `.agents/skills/tv-pilot/references/devices/<brand>-<model>.md` | 按 `_template.md` 写，`lint` 通过 |
| 修正已有档案 | 同上 | 附上你的固件版本与证据；别人实测不同的地方要说明差异 |
| 通用脚本 / 文档改进 | `scripts/`、`references/*.md` | 保持跨机型通用；机型事实不要写进通用文档 |
| 电视 App 改进 | `tvhud/` | App 目前以 MIUI TV 为主要目标，其它机型适配在档案 `app_build` 能力里标注 |

## 新增/修改设备档案的流程

```bash
bash scripts/identify-tv.sh              # 确认 matched / similar / unknown
bash scripts/device-profile.sh init      # 生成骨架（自动带当前设备信息）
# 填内容：只写实测过的；没测的保持 unknown
bash scripts/device-profile.sh lint      # 必填字段 + 章节 + 证据 + 脱敏
bash scripts/device-profile.sh index     # 重建 devices/README.md 的支持矩阵
git add . && git commit -m "docs(device): add <brand> <model> profile" && git push
```

详细采集步骤见 `.agents/skills/tv-pilot/references/porting.md`。

## 证据要求

- 每条结论尽量带 **命令 + 输出摘录 + 日期**，例如：
  ```
  $ adb shell getprop ro.build.version.sdk
  28          # 2026-11-04
  ```
- 没证据的结论写成「推测」，不要写进 `capabilities` 的 `verified`
- 能力等级：`verified` 实测可用 / `partial` 部分可用 / `unknown` 未验证 / `unsupported` 确认不可用

## 脱敏红线（PR 会被拒绝）

- 真实局域网 IP、公网 IP、DDNS 域名
- MAC 地址（遥控器、Wi-Fi/RAOP、蓝牙）
- 设备序列号、ADB 授权密钥、账号/邮箱/手机号
- Wi-Fi 名称（SSID）、路由器/宽带信息
- 儿童模式密码、家长控制真实配置值
- 截图里的账号头像、观看记录、家庭照片

需要展示时用占位符：`<TV_IP>`、`<REMOTE_MAC>`、`<片名>`、`<YYYY-MM-DD>`。
本仓库的敏感配置一律放本地 `.env`（已 gitignore），**不要提交 `.env` / `.tools/` / keystore / 字体**。

## 代码约定

- 脚本要兼容 macOS 自带的 **bash 3.2**（不要用关联数组、`${var,,}` 等 4.x 语法）
- 只读优先；写操作必须可逆、并先打印要做什么
- 新脚本放到 `scripts/`，在 `SKILL.md` 的脚本清单里补一行
- 提交信息用 `<type>(<scope>): <summary>`，例如 `docs(device): add hisense-a6g profile`

## 不建议的贡献

- 把某个机型的包名/键值直接写进通用文档（应进对应档案）
- 提交厂商固件、APK、jadx 反编译产物
- 无证据地"提升"别人档案里的能力等级
- 默认自动执行写操作（禁包、改设置、装 App）
