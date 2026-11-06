# 架构

两层结构，一层给代理用，一层跑在电视上。理解这张图，改代码时就知道改动该放哪。

```
用户自然语言（"今天看了多久 / 去广告 / 装个 B 站 / 角标加天气"）
        │
        ▼
┌────────────────────────────────────────────────────────────┐
│ skill 层  .agents/skills/tv-pilot/                          │
│  SKILL.md        操作索引（不写本机事实）                    │
│  scripts/        连接 / 探测 / 统计 / 部署 / 档案 / 自检      │
│  providers/      品牌差异扩展点（每机型一个 .sh）             │
│  references/     口径、坑、设备档案、本机事实 local.md        │
│  evals/          触发集 + 行为评测集                         │
└───────────────────────────┬────────────────────────────────┘
                            │ 只读探测 / 写操作（过 require-profile 闸门）
                            ▼
                    局域网 ADB :5555
                            │
                            ▼
┌────────────────────────────────────────────────────────────┐
│ App 层  app/  com.dsh.tvhud（按功能分包）                   │
│  home/    HomeActivity 菜单                                  │
│  usage/   UsageDay / HistoryStore / MainActivity / 台账视图   │
│  report/  ReportsActivity / 周月年 Canvas / ReportEngine      │
│  hud/     HudService / HudView / 广播 / 无障碍                │
│  pet/     PetActivity / PetGateActivity / PetEngine / Budget  │
└────────────────────────────────────────────────────────────┘
```

## skill 层

| 层 | 职责 | 关键文件 |
|---|---|---|
| 入口 | 把「用户问题」路由到「一条命令 + 一篇文档」 | `SKILL.md` |
| 连接 | 阶梯式拿连接（已有连接 → 缓存 IP → 扫描），带超时 | `scripts/ensure-tv.sh`、`find-tv.sh`、`lib.sh` |
| 识别 | 读设备身份，匹配仓库档案与本地覆盖档案，缓存结果 | `scripts/identify-tv.sh`、`references/devices/`、`$TVPILOT_DEVICES_DIR` |
| 只读能力 | 状态、应用清单、使用统计、遥控器电量 | `scripts/probe.sh`、`usage.sh`、`tv-state.sh`、`remote-battery.sh`、`apps-inventory.sh` |
| 写能力 | 装 APK、部署权限与无障碍条目；写前过档案闸门 | `scripts/app-deploy.sh`、`require-profile.sh` |
| 机型扩展 | 品牌差异函数（屏保包名、播放日志、电量键、应用快照） | `scripts/providers/` |
| 本机事实 | 电视地址/型号/App 版本/工具链状态，由 setup 生成 | `scripts/setup.sh` → `.agents/skills/tv-pilot/references/local.md` |

## App 层模块

| 包 | 职责 | 不放在这里 |
|---|---|---|
| `home` | 首页菜单与导航 | 业务逻辑 |
| `usage` | 使用采样模型、归档、台账页 | 报告聚合 |
| `report` | 周/月/年聚合与 Canvas 视图 | 采样/归档写盘 |
| `hud` | 常驻 Service、屏保角标、广播、无障碍 | 页面 UI |
| `pet` | 场次预算、答题、2.5D 渲染、宠物存档 | 使用统计 |

依赖方向：`home → {usage, report, pet, hud}`；`hud → {usage, pet}`；`usage` / `report` 会
`startService(HudService)` 并读 `peekUsage()/peekPlays()`，这是 Android Service 边界允许的反向；
`report → usage`；`pet → usage`（只读 `dayStartOf`）。纯逻辑（`PetEngine` / `Budget` / `QuizBank` /
`ReportMath`）不 import Android。

## App 层数据流

```
UsageStatsManager.queryEvents（增量，1s）
  + PowerManager.isInteractive()      ──► HudService.sampleUsage()
                                              │
                        ┌─────────────────────┼─────────────────────┐
                        ▼                     ▼                     ▼
              UsageDay（当天分段）      HudView（屏保角标）    HistoryStore（按天归档）
                        │                     │                     │
                        ▼                     ▼                     ▼
              SharedPreferences("hud")   0:00→24:00 长条      history/yyyy-MM-dd.json
                        │                                           │
                        ▼                                           ▼
              MainActivity（今日台账）                         历史模式（左右键翻 365 天）

/sdcard/miio/miothost.txt（米家上报，5s 增量 tail）──► PlayEntry（片名/集数）
```

要点：

- 只记「亮屏 + 非屏保 + 非自己」的时间；屏保/熄屏自然留空。
- 服务重启或 tick 空档用 `replayEvents()` 从 UsageEvents 重建，最多回看 24h。
- 屏保判定看 UsageStats 最近一次 `MOVE_TO_FOREGROUND` 是否为屏保包（不能用截图/焦点猜测）。
- 分类规则版本 `UsageDay.SCHEME` 变更时必须 bump，旧数据会被丢弃而不是画错颜色。

## 扩展点

| 想改什么 | 改哪里 | 不要动 |
|---|---|---|
| 某品牌/型号的差异（电量键、屏保包、播放日志、广告包） | `scripts/providers/<slug>.sh` + `references/devices/<slug>.md` | 通用脚本里的 if/else |
| 新操作流程（口径/坑） | `references/operations/<topic>.md`，在 `SKILL.md` 索引里加一行 | SKILL.md 里写长文 |
| 新脚本 | `scripts/`，加进 `SKILL.md` 操作索引与 `README.md` 命令表 | 把本机 IP/型号写死 |
| 新的机器（本机事实） | 重跑 `scripts/setup.sh` | 改 SKILL.md |
| App 内新页面 | `home/HomeActivity.ITEMS` + `open()` 分发 | 直接改别的页面启动逻辑 |
| 电子宠物玩法/外观 | `pet/PetEngine` / `pet/Budget` / `pet/PetView` / `pet/QuizBank`（纯逻辑先加 `tests/java/pet/PetCoreTest`） | 把场次/进化逻辑写进 Activity 或 HudService |
| 新增一个功能模块 | 在 `app/src/com/dsh/tvhud/` 下新包 + 在本文补依赖方向 + 补测试 | 继续往根包堆文件 |
| 报告页/聚合 | `ReportsActivity` + `ReportEngine/ReportMath` | 在 View 里另读文件 / 在 onDraw 里重算派生数据 |

## 不变量（改代码时保持）

1. 脚本默认只读；写操作必须先过 `require-profile.sh` 或带 `--dry-run`。
2. 阻塞操作必须有超时（macOS 无 `timeout`，用 `lib.sh` 的 `run_timeout`）。
3. 人类可读输出给结论；机器可读走 `--json`，字段见 `docs/json-contract.md`。
4. 本机事实只进 `.env` / `references/local.md`（skill 内）；机型事实只进设备档案/provider。
5. bash 3.2 兼容（macOS 自带）：不用关联数组、`${var,,}` 等 4.x 语法。
6. 纯计算口径（周期边界、聚合、百分比、堆叠柱几何）必须能在 `tests/java` 里离线断言；
   报告新增派生量时同步扩 `ReportMath.checkInvariants`，真机 E2E 会把告警当失败。
7. 提交门禁是 `make check && make test`；`make e2e` 需要电视，改动交互/布局时人工跑一次。
