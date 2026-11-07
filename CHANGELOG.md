# Changelog

本文件格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布前的改动都记在 `Unreleased`。

## [Unreleased]

### Added

- **主题配置页打磨 + 方向键循环 + 报告图例加大（v1.28）**：外观页左上角保留小字「主题配置」
  （15dp）；左栏预览与右栏 4 × 2 等齐高，顶部是本周真实周报条（总时长 / 日均 / 七天节奏 / 类别构成），
  下面加回首页四行菜单；右侧漆片改成「左上角名字 → 主色/辅色 28dp 小色卡（圆角 = 材质圆角）→
  可折三行的 11dp 描述」，描述文案恢复成完整的一句。4 × 2 网格左右同排循环、上下换排循环；
  首页菜单与家长设置目录 / 分组页的上下键在两端回绕。类别色按用户要求**保持固定**
  （视频 #F43098 / 音乐 #00D3BB），不跟主题换；报告 / 使用记录 / 带子统一读 `palette.dataColor()`，
  `ThemeCoreTest` 断言这四个颜色在所有 look 下不变。报告大屏图例统一到 `LEGEND = 7.4`
  （分类图例 / 热力色阶 / 月报环形图例）；周报「日流水」行字号 5.8/6.2 → 6.4/6.8，
  「7 天节奏」右侧图例随之变大；月报「内容构成」圆环放大到半径 52 / 环宽 12，
  圆环与五行图例按内容区中线垂直居中，图例与圆环间隔 24dp。

- **主题 v3.1：8 个暗室 + 样板式外观页（v1.28）**：电视是发光的，八个 look 全部是暗室；
  删掉试用后觉得刺眼的「亮色 / light」，新增 **暗色**（近黑 + 素白 + 石墨）、**暮紫**（紫黑 + 紫晶 + 霓虹青）、
  **森林**（墨绿 + 薄荷 + 麦穗，20dp 大圆角）、**日落**（酒黑 + 珊瑚 + 杏黄）。
  差异化从一条轴（主色）扩到三条：色温 / 面板圆角 0–20dp / 辅色（每套材质自带第二只灯；
  `Palette` 新增 `secondary` / `secondaryWash` / `onSecondary` / `rowRadius`）。家长设置的面板、行、
  按钮圆角改读材质，「今天」状态条改用辅色。外观页重做：左边用当前 look 的真实 token 现场画一张
  左边是**本周周报摘要**（16:9 居中）：本周总时长 / 日均 / 记录天数 / 七天节奏柱 / 分类构成条，
  全部用当前 look 的 report token 现场画，数据取本周真实归档 + 今日实时快照；右边 4 × 2 块漆片，
  每块只写名字 + 主色/辅色两块大色块、用自己的底色 / 文字 / 圆角渲染。整页没有标题、键位说明、
  页脚和圆角标注（沉浸式），删掉假日历带和编造的使用数据，←→ 同排、↑↓ 换排。
  `ThemeCoreTest` 扩到 136 组合 / 1212 断言，`TapeCoreTest` 覆盖 8 个 look；真机逐一切换八套 look，
  首页 / 记录 / 报告 / 家长设置同时跟随（实拍 `.build/theme-v4/`，不入库）。
- **临时加场可加可退（v1.27 修复）**：家长设置「01 今天」里的「临时加一场」原来只能按 OK
  加一场、← 键没反应，加多了只能等第二天清零。现在改成可调行「临时加场」：→ / OK 加一场、
  ← 撤回一场（0–24，当天有效）；撤回只退家长加的、还没用掉的那部分——上限不会降到
  「今天已经用掉的场次」以下，退到 0、加满 24 场、用掉后退不动都有提示。
  `Budget.addSession()` 改为返回是否成功，并新增 `removeSession()`；`PetStore.loadBudget`
  把 `extra_sessions` 夹到 0–24；`PetShopTest` 覆盖加 / 退 / 用满撤不动 / 当天上限 / 跨天清零，
  e2e 断言从「临时加一场」改为「临时加场」，`docs/pet-design.md` 与
  `parent-settings.md` 同步口径。

- **家长设置页跟随「外观」换肤（v1.27 修复）**：此前设置页的颜色全部来自 `PetUi` 里写死的
  深蓝 / 暖黄，只把 look 名显示在「05 外观」这一行，换外观后首页、记录、报告都变了，设置页还是
  宠物皮肤。现在整页改成从 `Palette` 取 token：页面底色 `appInk`、面板抬色由 `appText` 派生、
  发丝线用 `appRule`、焦点 = 灯色实底 + `onLamp` 反白字、24 小时时段带用 `tapeTrack` / `lamp`
  （「现在」用正文色，避免和时段抢灯色）；`showPin/showMenu/showPage` 先取 look，
  从外观预览返回时 `onResume` 重建即跟随，PIN 页、目录页、四个分组页一起生效。
  真机逐一切换夜班 / 暖房 / 影院 / 账本四个 look 核对（截图在 `.build/settings-theme/`，不入库）。
  宠物房间 / 商店 / 到点拦截页保留童趣语气（story），不跟主题换色。
  `make check` 的主题守卫新增「家长设置页零颜色字面量」，全仓字面量预算 277 → 263；
  `docs/design-system.md` §9 / §10 同步标记设置页已接入。

- **家长时间规则全部可配**：每场时长 5 分钟–3 小时（默认 15）、每天场次 1–24（默认 3）、
  周末加场 0–24 场（默认 5，0 = 不加）、新增「每场间隔」5 分钟–12 小时（默认 30 分钟）；
  答题续时开启后才显示「每次续时」（5 分钟–3 小时，默认 5）与「每天续时次数」（1–24，默认 2），
  关闭时两行自动隐藏。续时按钮、题干、结果页与 TTS 的分钟数都跟配置走，不再写死 5 分钟。
  新增拦截页第三种闸门 `GATE_INTERVAL`：上一场结束后，间隔内继续看视频不累计，宠物页显示
  「休息还剩 X 分 Y 秒」，到点自动放行（不计场次、不给星星）；间隔也落盘，重启不丢。
  休息间隔的长度与 `rest_until` 都做了持久化；`PetCoreTest` / `SettingsFactsTest` / `PetShopTest`
  覆盖新范围、步进回绕、间隔闸门与续时配置。**首页菜单顶部的时间轴按产品要求移除**，
  带子只保留在「使用记录」页。
- **主题 v3：4 个房间 + 一条带子（v1.28）**：外观从「4 材质 × 16 色号 = 64 组合」收窄成 4 个精选 look——
  **夜班**（仪表·信号青，默认）/ **暖房**（纸光·琥珀）/ **影院**（影院·冰蓝）/ **账本**（账本·月白）；
  名字按房间与时段，不按颜色名。`Looks` 自带 theme/palette，旧 `theme_scheme` / `theme_accent`
  自动迁移成最接近的 look，不丢设置。新增 `Tape.java`（纯几何：24h→像素、刻度、游标）与
  `TapeView`（COMPACT 4dp / STANDARD 10dp），首页眉标下加了今天的一条带子，使用记录的时间轴
  换成同一个组件并接入 `Palette`（`COL_*` 全删、`TimelineView` 删除）；报告页 129 处颜色字面量
  全部换成 `Palette` 的 report token（`C_*` 静态常量改为实例字段），删掉构造期/每帧径向光晕，
  Activity 读 look + revision 重建。外观预览页改为 4 个房间选择，家长设置摘要显示 look 名。`make check` 新增主题守卫：`home/`、`usage/`（`Categories.java` 除外）
  禁止颜色字面量，全仓字面量预算只降不升；新增 `TapeCoreTest`（7 用例 / 90 断言）。
  真机验证：MiTV-NSOP0 上切换夜班/暖房，首页、使用记录、周/月/年报告全部跟随；
  `tests/e2e/report-smoke.sh` PASS 13 / FAIL 0，`make regression` PASS 40 / FAIL 0。
  宠物 / HUD 的接入与「灯亮起来」过渡见 `docs/design-system.md` §10.4。

  今天 / 时间与场次 / 允许时段 / 宠物与成长 / 外观，每组右侧直接写当前规则（例如「已用 2/5 场 ·
  现在可以看」「15 分钟/场 · 每天 5 场」），不进去就能判断要不要改；进分组后 ←→ 改值，
  返回键回目录并把焦点留在刚才那一组，目录再返回才退出。目录页「今天」被限制时摘要是橙色、
  免限时是黄色；「今天免限 / 临时加一场」收进「01 今天」，到点拦截时两按可达。
  「家长时间（不计数）」从宠物组移到「01 今天」；「05 外观」不再套一层单行页，目录里 OK 直接打开全屏预览。
  「03 允许时段」分组页加一条 24 小时带（亮起来的部分 = 允许看的时段，白点 = 现在），与首页/台账共用同一套时间语法。
  状态判定与摘要文案抽成纯类 `SettingsFacts`，新增 `SettingsFactsTest`（6 用例 / 30 断言）
  覆盖「免限 > 家长时间 > 休息 > 用完 > 时段外 > 可看」优先级与场次倒挂边界。
- **家长设置 PIN 5 分钟免输（v1.26）**：输对一次后 5 分钟内再进家长设置（首页入口、宠物房间/拦截页长按返回）
  不再重复输入。窗口按「输对那一刻」的绝对时间戳算（`cfg_pin_ok_until`），退出重进、切到别的 App 再回来
  都有效，到点自然失效；连错 5 次锁 60 秒仍然优先。窗口判定抽成纯类 `PinGrace`，
  新增 `PinGraceTest` 覆盖边界（4:59.999 免输 / 5:00 失效）与「锁定优先」。
- **「今天免限」开关（v1.25）**：家长设置里新增一行「今天免限」，开 = 当天不拦允许时段、场次上限也不限，
  明天自动恢复原来的限制。存的是「发卡那天的 0 点时间戳」而不是「还剩多久」，
  所以跨天自然失效，电视断电/重启也不会把昨天的卡带过来。
  免限期间照样累计观看时间（成长值照算），只是不拦、不提醒；收回卡会把挂着的闸门关掉、
  正在看的这一场作废重来。拦截页也会每秒检查一次：家长发了卡，屏幕上那页自动放行。
  新增调试广播 `PET_FREE_PASS_ON/OFF`；`PetCoreTest` 覆盖「时段外不拦 / 场次用完不拦 / 跨天失效 / 收回」四条口径。
- **移除两个占位菜单（v1.24）**：首页去掉「AI 语音助手」「设置」两项（同时删掉 `ACTION_SOON` 的置灰与「还没做」toast 逻辑），菜单只剩 使用记录 / 使用报告 / 我的宠物 / 家长设置；以后真做了再加回 `ITEMS`。
- **家长设置入口提到首页菜单（v1.23）**：首页新增「家长设置」一项（右侧标 `PIN`），遥控器一按即进，不用再在宠物房间/拦截页长按返回 5 秒；PIN 校验与设置内容不变，老入口保留。
  `full-regression.sh` 增加首页菜单进家长设置的断言。
- **使用报告周期条去掉右上角提示（v1.22）**：不再显示「当前周期 · 柱高 = 总时长」，选中项由柱子本身表示。
- **使用记录去掉「今日合计 · N 段」（v1.21）**：顶部只保留总时长大数字，不再显示「今日合计/当日合计 · N 段」。
- **音乐统计修正（v1.20）**：PatchWall 内嵌酷狗音乐跑在桌面进程的插件宿主页
  （`ProxySingleTaskActivity`），原先被记成「桌面/系统」。现在按插件宿主页 + 系统音乐流识别为「音乐」；
  升级后首次启动会用 UsageEvents 只重建「今天」的插件音乐时段（历史归档不动），
  新增 `CategoriesTest` 覆盖映射，并加 `Categories.RULES_REV` 作为一次性迁移版本。
- **使用报告周期条文字再上移一点（v1.19）**：底部柱状图下的日期/周期文字上移 3 逻辑像素，
  离基线更近一点，仍保持在柱子下方。
- **使用报告底部版式微调（v1.18）**：三种报告（周/月/年）的页脚提示与「更新于…」再下压到贴近屏幕底边；
  底部周期条的日期/周期文字统一放到基线下面（并加一条淡基线），选中的高柱不再压住文字。
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

- **退役方向稿与主题渲染工具**：主题系统落地（§9 / §10，见 `docs/design-system.md`）后，
  `docs/design/` 下的方向稿源码（`home.html` / `report.html` / `system.html` / `schemes.html` /
  `app.css`）与 `tools/theme-preview/` 的渲染工具一并删除：它们描述的是实现前的提案，
  继续留着只会和 `app/src/com/dsh/tvhud/ui/` 的真实 token 漂移。渲染图（PNG）此前已删。
  `docs/design-system.md` 同步改写：§1.1 / §2.6 / §3.10 / §9 / §10 / 附录 B 不再指向已删文件，
  附录 B 改为列出当前的视觉资料来源（`ui/` token、§9 / §10 落地记录、真机截图、git 历史）。
  需要回看历史方向稿：`git log --diff-filter=D -- docs/design/`。
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
