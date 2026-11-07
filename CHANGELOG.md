# Changelog

本文件格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布前的改动都记在 `Unreleased`。

## [Unreleased]

### Added

- **合并「今日使用 / 使用记录」为一个菜单（v1.17）**：首页少一项，点「使用记录」先看今天，
  遥控器 ←→ 就地按天回看历史（最多 365 天）；详情页默认今天，不再有两个入口/两种打开模式。
  同步清理 `MainActivity` 的 `OPEN_TODAY/OPEN_HISTORY` 分支与最近归档日查找死代码；
  `full-regression.sh` 首页导航与断言更新。
- **电子宠物 P1/P2（v1.16）**：把「小电视兽」从 P0 补完到可长期养。
  - **商店 / 衣柜 / 装饰**：星星买 3 种食物（狗粮/小零食/甜食）、4 帽子、4 衣服、3 翅膀、
    6 房间装饰；买错原价退；同类型互斥；房间最多同时摆 3 件装饰。纯逻辑在 `Shop`，离线单测覆盖。
  - **分支进化**：Lv24 按累计偏好锁定智慧 / 疾风 / 美食三线（眼镜/闪电尾/厨师帽带等只改外观），
    30 星可换线。
  - **变异**：完全体后每 7 天检定一次（基础 15%，连续达标最高 30%），6 个部位随机 2 个
    + 6 套 pastel 配色，记入图鉴；重复变异保留最新外观。
  - **退化（可逆、不羞辱）**：连续 3 天不达标只回退外观 2 天、EXP −20 但保底不掉阶，
    次日达标立即恢复；连续 2 天没照顾会躲进被子，一次互动恢复；家长可整体关闭。
  - **允许时段 / 周末 / 温和模式**：时段外只拦「新开的一场」、休息不计场次；周末默认多 1 场
    可关；温和模式只提醒一次、不反复拉回。
  - **家长周报**：本周观看时长、场次、按时休息天数、超时天数、答题正确率、成长/分支/变异；
    数据源 = 归档 + `PetJournal` 天级流水；只统计时长与行为，不记录内容、不上传。
  - **界面重做（孩子审美 + 遥控器）**：宠物房间改为「照顾 / 商店 / 衣柜 / 房间」四个大分区，
    顶部等级/成长条/星星，暖黄焦点大卡片；2.5D 渲染新增分支/变异/退化/躲壳/新装备/房间装饰
    与变异/分支/退化庆祝动画；商店/衣柜/照顾按钮全部用 Canvas 矢量图标（零图片）。
  - **性能**：`PetView` onDraw 零分配、渐变/滤镜/星星坐标预计算，30fps 自绘；面板切换只
    `setVisibility`，不重建 View；真机 1080p 实测 90th 帧 19ms、无 Missed Vsync。
  - **可配置**：`PetSettingsActivity` 支持 PIN（连错 5 次锁 60 秒）、每场时长/场次/续时次数、
    周末加场、允许时段（跨零点）、退化/温和/家长时间、临时加场、周报、重置。
  - **调试广播**：新增 `PET_SHOP` / `PET_BRANCH` / `PET_MUTATE` / `PET_DEGRADE` /
    `PET_GIVE_STARS` / `PET_WINDOW_ON|OFF` / `PET_WEEKEND_ON|OFF` / `PET_GENTLE_ON|OFF` /
    `PET_ROOM` / `PET_REPORT` / `PET_STATE`；`full-regression.sh` 增加宠物房间四分区冒烟。
  - **单测**：新增 `PetShopTest`（10 用例 / 100 断言），`make test` 现为 27+10+10 用例。
  - **未做**：多孩家庭（当前单孩，需要时按 profile 拆存档）。

- **应用改名 tv-pilot（v1.15）**：桌面名称/首页眉标/TV banner 从 `TV HUD` 改为 `tv-pilot`；
  包名仍为 `com.dsh.tvhud`（保持覆盖升级与本地数据不丢）。
- **源码按功能分包（AI agent 友好）**：`app/src/com/dsh/tvhud/{home,usage,report,hud,pet}/`；
  测试拆到 `tests/java/{report,pet}/`；目录 `tvhud/` → `app/`、脚本 `tv-hud.sh` → `app.sh`、
  构建产物 `tv-pilot.apk`、keystore `tv-pilot.jks`。
- tv-pilot「我的宠物」P0（v1.14）：15 分钟一场 × 3 场的场次计时、到点全屏拦截、
  答 3 题对 2 题再玩 5 分钟（每天最多 2 次、3 场用完即止）、2.5D 分层宠物
  （吃饭/喝水/摸摸/洗澡/刷牙、胖瘦、牙齿/换牙、帽子/衣服/翅膀）、星星/等级/进化、
  家长 PIN 设置；新增 `PET_GATE` / `PET_DEBUG` / `PET_NEAR_END` 等调试广播。
- tv-pilot「使用报告」：周报 / 月报 / 年报三套数据大屏布局，共用 KPI 卡、周期导航条与遥控器交互。
  - 周报：7 天节奏大柱 + 日流水 + 7×24 作息热力；月报：日历热力 + 每日趋势 + 周内平均 + 环形构成；
    年报：12 个月走势 + 月份×时段热力 + 季度构成。
  - `←→` 回看上一期、`↑↓` 切换周/月/年、`OK` 回到本期；365 天窗口边界钳制。
  - 数据层：`ReportPeriod` / `DayAgg` / `Report` / `ReportMath` / `ReportEngine`，一次扫描多视图复用。
- 归档 JSON 写入分类版本号 `v`（`UsageDay.SCHEME`），旧归档缺省按 3 兼容。
- tv-pilot 应用图标与 TV banner：`app/design/icon-512.png` → `app/res/` 下各密度 `ic_launcher.png`
  + `res/drawable-xhdpi/tv_banner.png`；Manifest 的 application/HomeActivity 均已引用，`build.sh` 增加 res 编译。

- 测试三层：`tests/java` 纯 Java 单元测试（27 用例 / 178 条断言，覆盖报告周期、聚合、洞察、
  百分比、片名解析、堆叠柱几何、纵轴刻度、覆盖区间、周内平均、旧归档兼容、快照与归档自检）、
  `tests/e2e/report-smoke.sh` 真机冒烟（`make e2e`：报告页周/月/年导航 + 日志/崩溃/一致性断言
  + 截图）；两者都接进 `make test` / `make e2e`。
- 报告内部一致性自检 `ReportMath.checkInvariants`：聚合后校验每天 `totalMs`/`catMs`/`hourMs`
  与周期汇总自洽，真机日志出现 `REPORT_INVARIANT` 即视为回归。
- 抽出纯 Java 的 `BarStack`（堆叠柱几何）、`ChartAxis`（纵轴刻度，周/月/年三张图共用）、
  `ReportMath.weekdaySumMs/weekdayCount/weekdayAverageMs`（周内平均，图与洞察同口径）
  和 `UsageDay.KEEP_DAYS`（历史保留天数单一来源），让报告几何/口径能被离线单元测试覆盖。

- 存储写入优化（省 NAND 磨损，数据口径不变）：
  - `UsageDay` 增加数据版本号 `revision`，`SharedPreferences` / 归档只在数据真的变了时才写，
    电视开着但没人看时一天写盘从 1440 次降到 0 次；
  - 归档文件从「每 60 秒全量重写」改为「5 分钟节流 + 跨天/空闲/销毁强制写」，
    实测 5 分钟内数据持续变化也只写 1 次（`archive written` 日志可核对）；
  - 服务启动时补写「午夜关机/进程被强杀导致没定稿的那一天」（`ArchiveBackfill`），
    合并规则：分段取更全的一份、播放记录按「片名+集号」取并集；
  - 读取归档时做完整性自检（分段非负、不越界当天、不重叠、类别不越界，
    播放记录不越出当天，`day` 字段与文件名一致），坏文件打 `ARCHIVE_CORRUPT` 告警，
    真机 E2E 把它当失败。
- `ArchivePolicy` / `ArchiveBackfill` 抽成纯 Java 类，让落盘策略和补写合并能离线单元测试。

### Fixed

- 报告数据层两处跨线程共享：归档日期格式化改用 `ThreadLocal`；报告后台聚合改为读
  `UsageDay.snapshot()` 快照，不再遍历 Service 里每秒还在 append 的活对象（今日数字可能撕裂）。
- 报告最早一期被 365 天保留窗口截断时（如 2025 年报只剩 11/07 起），原先仍按整段周期算
  日均/覆盖率，把窗口外「不可能有数据」的天当成没看电视，日均被稀释数倍；改为
  `Report.coverageStartDay` 按窗口内天数计算，页头区间也显示真实覆盖起点。
  新增 `checkInvariants` 校验覆盖区间与 `elapsedDays` 自洽。
- 归档写入失败/无数据可写时不再占用 5 分钟节流窗口（否则当天第一份数据会被推迟落盘），
  `HistoryStore.write` 返回是否真的写成功，日志只在成功时打 `archive written`。
- 归档读取不再单次 `read()`：读不满时会截断 JSON 并被当成「没有归档」，报告悄悄少一天。
- 旧归档（没有 `v` 字段）的分类版本缺省值写死 `3`：原先用 `UsageDay.SCHEME`，
  以后 bump SCHEME 时会把 1.12 前的归档误认成新口径、按错颜色画。
- 归档自检也覆盖播放记录越界与 `day` 字段/文件名不一致。
- 报告的堆叠柱状图（周报「7 天节奏」、月报「每日趋势」、年报「全年走势」）只画出最高那一类的
  高度、柱子整体变矮：堆叠偏移误把「分钟」又除以 60000，各层全叠在柱底。修复后柱高 = 当天总时长。
- 周报「日流水」某些天的片名空白：原先只在全局 Top5 里按天找片名，Top5 被别的日子占满就找不到；
  改为按天缓存当天最后看的那一集。

### Changed

- 报告页交互与性能：
  - 遥控器长按只认第一下（忽略 `repeatCount`）；后台任务开始前比对 token，过期请求不再
    把一串 365 天扫描串行跑完；加载中保留上一帧画面，不再每次按键都清屏。
  - 后台聚合异常会显示「报告加载失败，按 OK 重试」，不再永久停在「正在汇总」。
  - 派生数据（KPI / 月汇总 / 月×时热力 / 7×24 热力）在 `setReport` 时算一次，
    `onDraw` 不再重复全量扫描；背景径向光晕的 Shader 复用，字体只加载一次。
  - 960×540dp 设计稿改为等比缩放居中：目标机 1920×1080@320dpi 像素级不变，
    720p 或其它 density 不再裁掉右侧/底部。
  - 无记录时「单日峰值/最高月」显示「—」；周报上期覆盖不足 80% 时不画「对比 第 N 周」；
    月报「周内平均」与洞察统一为只统计有记录的日子。
- 版本号 1.13（versionCode 14）；首页菜单新增「使用报告」入口。
- 使用报告的周期步进器改为显示**具体周期名**（「第 45 周」/「11 月」/「2025 年」；
  不在当年时补归属年份，如「2024 年 11 月」），不再出现「上周 / 3 周前 / 去年」这类相对表述；
  周报图例把对比期标成具体周序号。
- 修复报告页粒度切换的边界回落：锚点第一期已出 365 天窗口时（如从去年年报按 ↓ 进月报）
  落到窗口内最早一期，不再直接跳回本期。

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
  新增 `setup.sh`、`app.sh`、`require-profile.sh` 等入口。

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
