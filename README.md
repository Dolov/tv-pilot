# tv-pilot

[中文](README.md) | [English](README.en.md)

用**局域网 ADB** 操作电视（已实测小米 / Redmi MIUI TV，其它 Android TV 可按设备档案接入）：免数据线发现设备、只读探测、配置儿童模式与观看时长、清理广告组件、安装第三方 APK、读遥控器电量、统计电视使用时长与观看内容，以及在电视上跑一个自研小 App「**TV HUD**」（屏保右上角角标 + 今日使用台账 + 使用记录 + 周/月/年使用报告）。

整套东西由两层组成：

- **`.agents/skills/tv-pilot/`**：给 AI 编码代理用的 skill（脚本 + 参考文档 + 评测 + 品牌扩展点）。入口是 [SKILL.md](.agents/skills/tv-pilot/SKILL.md)，给人看的安装/结构说明在 [skill README](.agents/skills/tv-pilot/README.md)。
- **`tvhud/`**：电视上真正运行的小 App（纯 Java + 系统 API，无 Gradle/Android Studio，`build.sh` 直接出 APK）。

## 快速开始

```bash
git clone https://github.com/Dolov/tv-pilot.git ~/tv-pilot
ln -s ~/tv-pilot/.agents/skills/tv-pilot ~/.agents/skills/tv-pilot   # 让代理能自动加载
cd ~/tv-pilot/.agents/skills/tv-pilot
bash scripts/install-adb.sh        # 首次：下载 platform-tools
bash scripts/setup.sh              # 生成 .env + 探测电视 + 写 references/local.md
bash scripts/probe.sh              # 只读看看型号/应用/广告候选
bash scripts/usage.sh              # 今天看了多久、看了什么
```

电视侧先把 ADB 调试打开（`设置 → 账号与安全 → 安全 → ADB 调试`，部分固件要重启电视 5555 才监听），首次连接在电视上按「允许」。

## TV HUD 能做什么

- 屏保右上角角标三行：开机状态 / 遥控器电量 / 今日使用长条（24 小时时间轴，按类别着色）
- 打开 App 是首页菜单：今日使用 / 使用记录（按天翻历史，保留 365 天）/ 使用报告（周报、月报、年报数据大屏）/ AI 语音助手与设置（即将上线）
- 「今日使用」台账：合计、分类图例、0:00→24:00 时间轴、明细（时间 / 看了什么 / 时长 / 类别），每分钟刷新
- 「使用报告」：三套布局（周报 7 天节奏 + 日流水 / 月报日历热力 + 周内平均 / 年报 12 月走势 + 季度构成），←→ 回看上一期、↑↓ 切粒度、OK 回到本期
- 「我的宠物」：15 分钟一场 × 3 场，到点宠物全屏拦截；休息或答 3 题续 5 分钟；
  2.5D 宠物可吃饭/喝水/摸摸/洗澡/刷牙、变胖变瘦、换帽子/衣服/翅膀，升级进化
- 附带 ADB 守护（保持 `adb_enabled`）与无障碍开机自启

构建与部署：

```bash
bash .agents/skills/tv-pilot/scripts/build-tools.sh ensure   # 下载 build-tools / android.jar（~50MB）
bash .agents/skills/tv-pilot/scripts/tv-hud.sh build         # 产出 .build/out/tvhud.apk
bash .agents/skills/tv-pilot/scripts/tv-hud.sh install       # 权限/无障碍顺序内置
```

详细口径与坑见 `references/operations/`（使用统计、屏保、儿童模式、去广告、开机自启、开发闭环）。

## 关于观看统计

TV HUD 会记录观看时长和内容，但**这不是为了监控孩子**，也不想把它变成查岗工具。它的本意是让全家对电视的使用心里有数：孩子能看到自己今天看了多久，大人可以据此一起商量节奏，而不是一遍遍催促。

这份统计在开发和使用前已经征求过孩子的同意；数据只留在自家电视本地，不上传、不共享。我们更希望它帮孩子把时间花在喜欢的内容上——看电视本来就该是件开心的事。如果哪一天这些数字让家里变得紧张，那一定是哪里用错了。

## 仓库结构

```
tv-pilot/
├── AGENTS.md                     # 仓库级代理指南（改代码前读）
├── CONTRIBUTING.md               # 贡献流程：档案 / 脚本 / provider / App
├── README.md                     # 本文件
├── CHANGELOG.md / SECURITY.md
├── Makefile                      # check / test / apk / index
├── version.properties            # 版本单一来源
├── .github/                      # CI + PR/issue 模板
├── docs/                         # 架构、JSON 契约、provider 规范
├── tests/                        # 测试：fake-adb 脚本 / tests/java 单元 / tests/e2e 真机冒烟
├── tvhud/                        # 电视端 App 源码
│   ├── AndroidManifest.xml
│   ├── build.sh                  # javac + d8 + aapt2 + apksigner，一条命令出 APK
│   └── src/com/dsh/tvhud/        # HomeActivity / MainActivity / HudService / UsageDay ...
└── .agents/skills/tv-pilot/      # skill 本体（见 skill README）
    ├── SKILL.md                  # 代理入口：操作索引
    ├── scripts/                  # 连接/探测/统计/部署/档案/自检 + providers/
    ├── references/               # operations/ + devices/ + porting.md + local.md
    └── evals/                    # 触发集与行为评测集
```

## 支持哪些电视 / 怎么贡献

- 已实测机型：见 [设备支持矩阵](.agents/skills/tv-pilot/references/devices/README.md)（当前：MiTV-NSOP0 / MIUI TV Android 9）。
- 型号不匹配时：`identify-tv.sh` 给出 `similar`（同品牌）或 `unknown`（没档案）。此时只读能力可用；禁包 / 改设置 / 装 App / 屏保角标会被 `require-profile.sh` 挡住，需要先验证。
- 接入一台新电视：按 [porting.md](.agents/skills/tv-pilot/references/porting.md) 采集 → `device-profile.sh init` → 填档案 → `lint` → 提 PR；品牌差异用 `scripts/providers/` 扩展，不必改通用脚本。
- 贡献代码前读 [AGENTS.md](AGENTS.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)，并跑 `make check && make test`。

## 安全与隐私

仓库**不包含**：

- `.tools/`：本机工具链（adb、build-tools、android.jar、jadx）与签名私钥，均可按脚本重建
- `.env` / `.agents/skills/tv-pilot/references/local.md`：局域网 IP、遥控器 MAC、本机电视型号等
- 专有字体（DIN Pro / MIUI EX）：不公开分发，缺失时代码自动回退系统字体
- 电视截图、应用清单中的真实账号信息

使用建议：

- 脚本**默认只读**；写操作会先说明再执行，装 App 走 `app-deploy.sh`（带设备档案闸门）
- ADB 的 `5555` 是明文端口、且无密码；不用时在电视上关掉 ADB 调试
- 不要用「卸载重装」来重置数据：会连带丢掉 appops、无障碍条目与 App 偏好；重置使用统计用广播 `com.dsh.tvhud.RESET_USAGE`
- 已知限制与威胁模型见 [SECURITY.md](SECURITY.md)

## 许可

代码以 [MIT](LICENSE) 发布。`tvhud/assets/` 下的字体文件不随仓库分发，版权归各自厂商。
