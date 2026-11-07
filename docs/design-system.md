# tv-pilot 视觉系统（v2 提案）

状态：**设计提案，未实现** · 适用版本：v1.24+ · 相关页面：首页 / 使用记录 / 使用报告 / 电子宠物 / 屏保角标

本文回答两个问题：

1. 现在这套 UI 为什么「不像一个产品」——用可核对的数据说明；
2. 如果要**统一样式**、并**支持主题色**，应该怎么设计、怎么落地、先做哪一步。

---

## 0. 结论

**统一的是语法，不是皮肤。** 全套界面共用一套 token、一套组件、一套焦点规则；
「主题色」不是把全站染成另一个颜色，而是**换掉唯一的强调色——灯**。
数据分类色、屏保角标不参与换肤，这是硬约束。

具体四条：

1. **先抽地基，再谈风格**。现在 413 处硬编码颜色、4 套重复的字体/尺寸/分割线 helper，
   任何「统一风格」如果不先做 token 层，三个月后一定漂回去。P0 是零视觉变化的纯重构。
2. **母语是「台账」，不是「数据大屏」**。首页和使用记录已经有一套安静、克制的台账语言；
   报告页的青紫渐变发光卡片是通用大屏模板，宠物页是另一套儿童玩具语言。以台账为底座，
   把报告拉回来，把宠物收编成同一套 token 下的「另一种语气」。
3. **一个签名元素：一天一条带（Day Tape）**。「今天看了多久」是这个产品唯一的事实，
   让它以同一种构造出现在首页、记录页、报告周期条和屏保角标上，四个尺度，学一次就懂。
4. **主题 = 客厅里留的那盏灯**。四选一（月白 / 琥珀 / 紫晶 / 冰蓝），只改焦点、选中、现在游标
   和极轻的表面染色；类别色固定。暗色唯一，不做浅色主题（原因见 §2.7）。

**如果只做一件事**：做 §5 的 `Theme` + `Ui` 抽取（P0），并把 `lamp` 接到现有焦点态上。
约 2–3 人日，界面几乎不变，但之后所有风格迭代都只是改数据。

| 目标 | 最少改动 | 工作量 |
|---|---|---|
| 只支持主题色 | P0 抽 `Palette` + 4 色系 × 4 灯，接到焦点/选中/游标 + 家长设置一组选择器 | ~3 人日 |
| 只统一样式 | P0 + 组件词汇（Row / Panel / Tape / Focus）+ 字阶与间距收敛 | ~6 人日 |
| 两者都做 + 视觉收敛 | P0–P4（本文路线） | ~9–10 人日（每多一个色系 +0.5） |

---

## 1. 现状盘点（可核对）

### 1.1 五种页面，至少三套视觉语言

| 页面 | 现状语言 | 底色 | 圆角 | 焦点 | 字号 |
|---|---|---|---|---|---|
| 首页 `HomeActivity` | 安静台账：编号行、发丝线、无卡片 | `#0e1114` | 1.5dp | 3dp 白脊 + 6% 白浮层 | 9–22dp |
| 使用记录 `MainActivity` | 同一套台账：时间轴 + 明细表 | `#0e1114` | 1.5dp | 8% 白浮层（无脊） | 9–26dp |
| 使用报告 `ReportCanvasView` | 企业数据大屏：渐变卡片、发光、青 + 粉 | `#06090f` | 6dp + pill 7–8 | **无焦点**（方向键直改视图） | 5.2–23dp |
| 宠物 `Pet*` | 儿童玩具：夜空、大圆角、暖黄反色 | `#0b0f1a` | 12–28dp + pill | 暖黄实底 + 白 3dp 环（另有 3 种变体） | 13–40dp |
| 屏保角标 `HudView` | 悬浮仪表：白字 + 黑投影 | 透明覆盖 | 0 | 无 | 14–34dp |

截图证据：`.build/pet-shots/`（真机）、`.build/design/impl-*.png`（v1.13）。方向稿见
[`design/home.png`](design/home.png)、[`design/report.png`](design/report.png)、
[`design/system.png`](design/system.png)。

### 1.2 颜色：413 个字面量、4 种底色、无 token 层

```
$ grep -rhoE "0x[0-9A-Fa-f]{8}" app/src | wc -l
413
```

- 有颜色的 Java 文件 18 个，共 413 次颜色字面量；其中含字面量的行：`ReportCanvasView` 50 行、`PetView` 120 行、`PetIconView` 44 行。
- 报告一组 5 个文件：129 处字面量 / 59 个不同值；同一角色的「白」有 6 种，
  发丝线有 10 档 alpha，青色系渐变端点 6 种（`#38bdf8` / `#7dd8ff` / `#9be6ff` / …）。
- 宠物一组 8 个文件：245 处 / 150 个不同值；`PetUi` 里定义了 `CARD_HOVER`、`PANEL_SOLID`、
  `PINK/MINT/BLUE/LILAC` 等 9 个常量 **0 引用**，同时各页又各复制一套角色色；
  `PetIconView.C_YELLOW` 实际等于 `PetUi.STAR`，命名互相打架。
- 同一角色跨页不同值：页面底 `#0e1114` / `#06090f` / `#0b0f1a` / `#141b2a`（面板）；
  正文白 `#e9eef3` / `#e8f0f8` / `#f6f8fb` / `#f4eee3`…；弱化文字 `#5f6b76` / `#5c6b7d` / `#697788`。

### 1.3 字体：3 套字体、至少 4 处重复加载、21 档字号

- 字体来源：`DINPro_Medium.otf`（数字/标签）、`MIUI_EX_Light.ttf`（大数字）、
  `/system/fonts/MILanPro_Normal.ttf`（中文）。两个 asset 字体是专有字体，**不在仓库里**
  （`.gitignore` 禁止提交字体），换机器构建会静默回退 `Typeface.DEFAULT`。
- `HomeActivity`、`MainActivity`、`PetUi`、`ReportCanvasView` 各写一份 `initFonts()`（`HudService` 另有一份加载）
  `cjk()`；`label()`、`dp()`、`hairline()` 在首页和记录页各复制一份；宠物页另有一套 `dp/sp/round`。
- 字号：首页/记录 9–26dp；报告 21 档 5.2–23dp（960×540 逻辑坐标里 5.2dp ≈ 1080p 下 10px，
  3 米外基本不可读）；宠物 14 档 13–40dp。
- 字距（letterSpacing）0–2.2 随手给，没有角色定义；报告页连 `Paint.Align` 都靠调用点传。

### 1.4 形状、焦点、密度：三套语言

- **圆角**：台账 0–1.5dp；报告 6dp + pill 7–8；宠物 12/16/18/20/22/24/26/28 八档 + pill。
- **焦点**：首页白脊 + 6% 白底；记录页 8% 白底；报告页没有焦点；宠物页暖黄实底 + 白环，
  且内部还有「黄底无环 / 黄字 + 黄描边 / 禁用灰」三种变体。**同一个 App 有五种「选中」长相。**
- **列表**：首页 60dp／记录页 28dp 行高；宠物 46dp Tab、焦点即切页。
- **分割**：台账用发丝线；报告用卡片边框 + 渐变；宠物不用分割线靠色块。

### 1.5 对比度实测（WCAG，相对亮度比）

| 位置 | 前景 / 背景 | 实测 | 问题 |
|---|---|---|---|
| 首页弱化文字 | `#5f6b76` / `#0e1114` | **3.47 : 1** | 10dp 小字，3 米偏暗 |
| 报告注释 | `#5c6b7d` / `#131e2b` | **3.09 : 1** | 低于 4.5 |
| 宠物弱化文字 | `#697788` / `#141b2a` | **3.77 : 1** | 13–14dp 偏紧 |
| 报告「其他」类别 | `#607d8b` / `#06090f` | 4.56 : 1 | 换到暖底后 4.34，需提亮 |
| 宠物焦点黄字 | `#ffe27a` / `#141b2a` | 13.45 : 1 | 合格，但焦点仅靠浅色 |

方向稿把弱化文字提到 `#978C7B`（ink 底 5.75）、类别「其他」提到 `#7A8794`（5.17），
其余角色维持 7–16 的水准。

### 1.6 主题化的「接缝」评估

| 文件 | 现状 | 换主题时 |
|---|---|---|
| `ReportCanvasView` | 颜色 static final；**构造期**建 2 个 `RadialGradient`；`onDraw` 每帧 `new LinearGradient` 11 处、`DashPathEffect` 6 处 | 必须重建 View 或加 `applyTheme()`；每帧分配要一起修 |
| `PetView` | 底色/天幕/光晕在 `onSizeChanged` 烘进 Shader；147 处字面量 | 高难；但宠物语气固定，可只接 token 名 |
| `PetUi` | 颜色集中、`cardButton()` 统一焦点 | 最容易 |
| `HomeActivity` / `MainActivity` | 各自一组 `COL_*` + 复制 helper | 容易，先做它们 |
| `HudView` / `HudService` | 固定白色 + 黑投影，叠在任意画面上 | **不应该**接主题（见 §3.8） |
| `Categories` | 5 个用户指定的数据色 | 固定色相，只允许调明度 |

已有资产：`PetUi` 已经把宠物区的圆角卡片、焦点、字体、chip 收在一处；
`Categories` 是天然的「数据色 token」；报告页的几何（`BarStack` / `ChartAxis`）已经抽成纯函数。

### 1.7 诊断

1. **没有单一事实来源**：413 处颜色字面量、21 档字号、5 种焦点，任何「全局微调」都要逐文件改。
2. **品牌漂移**：应用已改名 `tv-pilot`，但首页标题是「使用台账」、菜单是「使用记录」，
   报告页却是「数据大屏」；用户看到的不是同一个产品。
3. **可读性有硬伤**：3.1–3.5 的对比度 + 5–8dp 注释，在 3 米沙发上不可读。
4. **电视特性被忽略**：报告页大面积径向渐变/发光，在 8bit 面板上会出色带；
   渐变每帧新建，低端机有掉帧风险。
5. **主题不可达**：所有颜色是编译期常量，Manifest 只有系统 `Theme.Black`，没有 `values/colors.xml`，
   连「换一组常量重新构建」都要改 18 个文件。

---

## 2. 设计方向

### 2.1 判断：统一语法，不统一皮肤

这个 App 有两类用户、同一个客厅：

- 家长：看「账」——今天多久、这周怎么样、孩子有没有按时休息；
- 6 岁孩子：看「宠物」——吃饭、换装、这一场结束了。

把两者刷成同一个样子是错的（孩子需要大按钮、暖色、强焦点）；但让它们各说各话也是错的。
正确的统一方式：**token 名、组件结构、焦点机制、字体角色、数据色完全共用；
语气（tone）只调形状、字重、密度、表面温度。**

### 2.2 概念：一条带子，一盏灯

> 夜里的客厅只有两样东西有光：**电视里的一天**，和**你正站在哪**。

- **带子**：一天 24 小时的时间带。它天然是这个产品的数据模型（`UsageDay.Seg`），
  也天然适合做跨屏的视觉母题。
- **灯**：焦点 / 选中 / 「现在」。全 App 只有一个强调色来源，就是灯。
  换主题 = 换灯，不换别的。

这个方向来自产品本身（观看台账 + 儿童宠物 + 暗室 + 遥控器），不是「深色 + 荧光色」的模板。

### 2.3 签名元素：Day Tape（一天一条带）

同一构造，四个尺度：

| 尺度 | 用在哪 | 高度 | 标签 |
|---|---|---|---|
| 微 | 首页标题下、屏保角标 | 4–5dp | 无（首页） |
| 标准 | 使用记录时间轴 | 10dp | 每 3 小时 |
| 周期 | 报告底部周期条 | 40–52dp | 每期 |
| 生长 | 宠物成长/场次条（同一语法） | 8dp | 无 |

解剖：

```
轨道   纸色 8% 圆角 track
段     类别色，按时间比例排布，最小 2dp，圆角只在两端
游标   灯色 1.5dp 竖线 + 圆点，只表示「现在」
刻度   纸色 22%，每小时一根，3 小时间隔带 DIN 标签
```

规则：段与段之间不留缝（同一天是连续的）；没有任何装饰性渐变；
所有长度都从同一个 `UsageDay.DAY_MS` 映射，不允许每个页面自己算一遍百分比。

### 2.4 两种语气，同一套 token

| 参数 | ledger（家长） | story（孩子） |
|---|---|---|
| 底 | `ink` 暖石墨 `#12100D` | 夜空靛 `#0F1220` |
| 表面 | 1 层 slab + 1px 发丝线 | 2 层 slab + 22dp 圆角 |
| 焦点 | 10% 灯晕 + 5dp 色脊 | 灯色实底 + 白 3dp 环 |
| 字重 | 300 / 常规 | 600 / 加粗 |
| 字号 | 9 / 10 / 12 / 15 / 22 / 26–34 | 16 起，标题 34–40 |
| 灯 | 家长可选 4 种 | 固定琥珀，保证一致预期 |
| 数据色 | 同一套 `Categories` | 同一套 `Categories` |

### 2.5 五条可验收原则

1. **颜色只属于数据，灯光只属于焦点。** 除类别色和状态色，任何彩色都来自 `lamp`。
2. **一条带子贯穿四屏。** 首页 / 记录 / 报告 / 角标的时间可视化必须同构，只是尺度不同。
3. **覆盖层不跟随主题。** 角标叠在任意电视画面上，必须固定高对比白字 + 投影。
4. **用发丝线分层，不用阴影和大面积渐变。** 电视 8bit 面板会色带，3 米外阴影也看不见。
5. **焦点永远带几何。** 脊 / 环 / 下划线三选一，不允许「只变色」。

### 2.6 方向稿

首页（台账语气 + 首页眉标下的 Day Tape）：

![首页方向稿](design/home.png)

报告（同一底座，去掉发光，青→灯，数据色保留）：

![报告方向稿](design/report.png)

系统板（灯、表面、字阶、带子、焦点、语气）：

![系统板](design/system.png)

> 方向稿是 1920×1080 的 HTML/CSS 渲染，字体用 PingFang / DIN Alternate 近似 App 里的
> MILanPro / DIN Pro，**不是实现截图**；源码在同目录 `home.html` / `report.html` / `system.html`。

### 2.7 被否掉的方案

| 方案 | 为什么不做 |
|---|---|
| 全站统一成一个颜色（把报告刷成台账灰） | 数据图表失去类别色就不可读；报告页的信息密度需要自己的层级 |
| 浅色主题 / 跟随系统明暗 | 电视在暗室，浅色底刺眼；角标叠在亮画面上更不可控；OLED 上纯黑反而省电。**暗色唯一**，用「夜间调暗」替代明暗切换 |
| 动态取色（Material You / 取海报主色） | API 21 电视无此能力；品牌会被内容带跑，且无法保证对比度 |
| 每页一个皮肤（现状） | 用户学不到任何规律；每次加页面都重新发明一次样式 |
| 用户自选任意色 | 无法保证对比度与数据色区分；4 个策展灯色足够表达个性 |

---

## 3. Token 体系

### 3.1 三层

```
primitive（不进业务代码）   ink-900 / paper-100 / lamp-hues / data-hues
        ↓
semantic（页面只用这一层）  ink · slab · rule · text · text2 · text3 · lamp · data[5] · …
        ↓
component（组件默认值）     Row.focus = lampWash + spine(lamp)
```

约束：`ui/` 包以外不允许出现 ARGB 字面量（由 §6.2 的静态检查守住）。

### 3.2 颜色角色（warm 色系，默认值）

| token | 值 | 用途 |
|---|---|---|
| `ink` | `#12100D` | 页面底（暖石墨，不用纯黑） |
| `inkDeep` | `#0A0907` | 暗角 / 覆盖层底 |
| `slab` | `#1A1713` | 面板、KPI 卡 |
| `slabRaised` | `#221E19` | 焦点卡片、浮层 |
| `rule` | `#2A2620` | 1px 发丝线、轨道 |
| `ruleStrong` | `#3A342B` | 键帽边、选中底 |
| `text` | `#F4EEE3` | 正文 / 主数字（16.4:1） |
| `text2` | `#C0B6A6` | 次级（9.5:1） |
| `text3` | `#978C7B` | 注释 / 页脚（5.75:1，旧版 3.1–3.5） |
| `lamp` | 主题决定 | 焦点 / 选中 / 现在 |
| `lampWash` | lamp @ 10% | 行焦点底 |
| `lampGlow` | lamp @ 22% | 游标光晕（可选，最多一处） |
| `onLamp` | 自动推导 | 灯色实底上的文字：在 `ink` / `text` 中取对比度更高者（当前 4 个灯都取 ink，8.9–16.2） |
| `warn` | `#E8A35C` | 提醒（到点、电量低） |
| `danger` | `#F27C74` | 错误（加载失败、超时） |
| `data[0..4]` | 见 §3.9 | 视频 / 音乐 / 儿童学习 / 桌面系统 / 其他 |

### 3.3 灯：四选一，派生规则固定

| id | 名称 | 值 | ink 上对比度 | 气质 |
|---|---|---|---|---|
| `moon` | 月白 | `#F7ECD7` | 16.2 | 默认，像纸的反光 |
| `ember` | 琥珀 | `#FFC97A` | 12.6 | 晚间低蓝光 |
| `amethyst` | 紫晶 | `#B9A5FF` | 8.9 | 个性，不与任何类别色相邻 |
| `ice` | 冰蓝 | `#9FD3FF` | 12.0 | 冷色偏好 |

- `onLamp` 在 `ink` / `text` 中按对比度取高者，不手填（当前 4 灯均取 ink，8.9–16.2）；`lampWash` / `lampGlow` 由 lamp 推 alpha。
- 灯只作用于焦点、选中、「现在」游标、极轻的表头 2dp 亮条；**不允许**大面积铺底。
- 报告页的「洞察」、当前 Tab、周期条选中、首页焦点脊，全部引用同一个 `lamp`。

### 3.4 字体与字阶

字体角色（两个就够）：

- **中文**：`MILanPro_Normal`（系统字体）→ 回退 `Typeface.DEFAULT`。
- **数字/英文**：`DINPro_Medium` → 回退 `Typeface.DEFAULT` + `fontFeatureSettings("tnum")`，
  保证时间码和表格数字等宽。

字阶（dp；1080p、density = 2 时 1dp = 2px；报告 Canvas 也按 dp 定义）：

| 角色 | 字号 | 用途 |
|---|---|---|
| `micro` | 9 | 键位提示、图例、页脚、角标注释 |
| `caption` | 10 | 表头、KPI 标签、元信息 |
| `body` | 12 | 列表正文、洞察句 |
| `emphasis` | 15 | 行标题、卡片标题 |
| `title` | 22 | 页面标题、面板标题 |
| `metric` | 26 / 30 / 34 | KPI 主值、合计时长 |

规则：

- 9dp 是**注释下限**（报告页现有的 5.2–8.5dp 全部上移）；
- 中文用 `letterSpacing .04–.10`，英文/DIN 小标签用 `.18–.30`；
- 报告页 KPI 主值统一 DIN；不要再用「白→青」文字渐变（旧版 `#9ec9e8`）；
- 字体缺失时回退后必须仍然对齐：所有数字列用等宽数字，不用「靠字距硬凑」。

### 3.5 间距、形状、描边

- 间距基数 4：`4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`；页边距 48dp（1920 下 96px，落在 5% 安全区内）。
- 圆角三档：`0`（发丝线、表格）、`10–12`（面板/卡片，ledger）、`22–28 + pill`（story）。
- 描边：1dp 发丝线（`rule`）、2dp 强分割（`ruleStrong`）、3dp 焦点环（story）。
- 不用 `elevation` / drop shadow；需要「浮起」时升一档表面色 + 一条 `rule`。

### 3.6 焦点

| 组件 | ledger | story |
|---|---|---|
| 行（菜单、明细） | `lampWash` 底 + 5dp 左脊 + 文字升一级 | 不适用（用卡片） |
| 卡片 / 按钮 | 2dp `lamp` 环 + 表面升一档 | `lamp` 实底 + `onLamp` 字 + 白 3dp 环 |
| Tab | 2dp `lamp` 下划线 + 文字变 `lamp` | 同左 |
| 报告页 | 无焦点：方向键直接改视图，底部常驻键位提示 | — |

规则：焦点移动 120ms 淡入；键盘/遥控器焦点必须可被看到；**焦点信息不能只靠颜色**（保留脊/环）。

### 3.7 动效

- 焦点：120ms `FastOutSlowIn`；
- 面板切换：180–220ms 淡入 + 4dp 上移（宠物 Tab）；
- 带子数据更新：不做数字滚动，直接换值（电视 30fps、低端机掉帧更难修）；
- 遵循系统「动画时长缩放 = 0」时全部关闭（读 `Settings.Global.ANIMATOR_DURATION_SCALE`）。

### 3.8 覆盖层 token（不进主题）

屏保角标叠在**别人的画面**上，主题色在这里没有意义，只有可读性：

| token | 值 | 说明 |
|---|---|---|
| `overlay.text` | `#F4EEE3` | 主文字 |
| `overlay.dim` | `#C6BFB2` | 次级文字 |
| `overlay.shadow` | `#CC000000`，6px 模糊、y+2 | 保证亮画面上也能读 |
| `overlay.track` | `#1AFFFFFF` | 24h 长条轨道 |
| `overlay.now` | `#66FFFFFF` | 现在刻度 |
| `overlay.data[]` | 同 `data[]` | 长条色块仍用类别色 |
| `dim` | 0.72 alpha（夜间调暗，用户可关） | 只作用于角标 |

角标不接受 `lamp`，只在类别色和「调暗」上跟随设置——这是 §2.5 原则 3。

### 3.9 数据色纪律

`Categories.COLORS` 是用户明确指定、且「颜色就是图例」的语义色：

```java
0xfff43098 VIDEO   // 电视/视频（用户指定）
0xff00d3bb MUSIC   // 音乐/K 歌（用户指定）
0xffffb74d KIDS    // 儿童学习
0xff90a4ae SYSTEM  // 桌面/系统（建议保持）
0xff607d8b OTHER   // 其他（建议提亮到 #7A8794）
```

规则：

- 换主题**只允许**调明度/饱和度，不允许换色相；
- 报告热力图不要用「青 → 粉」双色渐变（会与类别色抢语义），改成单灯色明度梯度；
- 类别色作为小色块出现时，必须同时有文字标签（`Categories.name()`），不能只靠颜色；
- 加一个纯 Java 的 ΔE / 对比度测试，防止以后加类别时撞色。

### 3.10 色系：第二个可配置轴

灯是**强调色轴**；「色系」是**底色轴**：不改组件与数据色，只换 ink / slab / rule / text 的温度与明度台阶。

| id | 名称 | ink | slab | slabRaised | rule | text | text2 | text3 | 适合 |
|---|---|---|---|---|---|---|---|---|---|
| `warm` | 暖石墨 | `#12100D` | `#1A1713` | `#221E19` | `#2A2620` | `#F4EEE3` | `#C0B6A6` | `#978C7B` | 默认；客厅暖光 / 白炽灯 |
| `cool` | 冷石墨 | `#0E1116` | `#161A21` | `#1D222B` | `#262C36` | `#EAF0F7` | `#B4C0CE` | `#8E9AA8` | 客厅冷白光 / 白天 |
| `indigo` | 靛夜 | `#0F1020` | `#181A30` | `#20233D` | `#2B2F4C` | `#F2F0FA` | `#C2C2DA` | `#9A9BB6` | 影院感 / 夜间 |
| `black` | 纯黑 | `#000000` | `#0C0C0C` | `#141414` | `#262626` | `#F2F2EE` | `#BFBFB8` | `#93938C` | OLED / 想省电 |

色系与灯是正交的：**4 色系 × 4 灯 = 16 个组合**，全部可配置。方向板见
[`design/schemes.png`](design/schemes.png)。

规则：

- 每套组合都必须通过同一组断言：`text/text2/text3` 对 `ink` 与 `slab` ≥ 4.5、
  `lamp` 对 `ink` ≥ 4.5、`data[i]` 对 `ink` ≥ 4.5。实测最差值：`text3` 5.4、`data.other` 5.1、`lamp` 8.9。
- **数据类别色固定**：16 个组合共用同一组类别 hex（允许按色系微调明度，不允许换色相）。
- 色系不改圆角、字号、间距——那是 tone 的事；色系只改「底 + 表面 + 线 + 文字」。
- 纯黑是「OLED / 省电」选项，不做默认：LCD 上黑位发灰，近黑渐变更容易出现色带。
- 新增色系 = 4–6 个 token 值 + 一条断言，约 **0.5 人日**，不碰任何组件。
- 配置入口只在家长设置（PIN）；宠物区固定 story 语气，屏保角标不跟色系。
- **不提供任意取色**：16 个组合足以覆盖「暖 / 冷 / 影院 / 纯黑」的偏好；任意 hex 无法保证
  3 米可读、类别可辨与面板无色带，还会让回归截图矩阵失控。

---

## 4. 组件词汇（收敛到 8 个）

| 组件 | 结构 | 焦点 | 备注 |
|---|---|---|---|
| `Header` | 眉标（DIN micro）+ 标题（CJK title）+ 右侧元信息 | — | 首页/报告共用；标题字距 .10 |
| `MenuRow` | 序号 + 名称 + 描述 + 尾标/箭头 | 左脊 + 灯晕底 | 行高 56–60dp；序号 DIN |
| `Stat` | 标签 + 主值（DIN metric）+ 脚注 | — | KPI 卡内用；固定 4 列栅格 |
| `Panel` | 表面 + 1px 边 + 顶部 2dp 灯条（可选）+ 标题行 | 卡片环（story） | 报告的所有图表容器 |
| `Tape` | 轨道 + 段 + 游标 + 刻度 | — | 四尺度同构；提供 `TapeView` |
| `KeyHint` | `← →` 键帽 + 动作文案 | — | 底部常驻，格式统一 |
| `Chip` | 类别圆点/数值或标签 | — | 图例、集数、PIN |
| `Focus` | 工具类：`row/card/tab` 三种样式 | — | 一处实现，禁止各页手写 |

暂不引入：通用 `Dialog`、`Toast`（现有 `PetBubble`/`tipText` 够用）、图标库（宠物是 Canvas 手绘，
其它页面用文字/几何，不新增位图资源）。

---

## 5. 主题架构（纯 Java / 无 Gradle）

### 5.1 模块

```
app/src/com/dsh/tvhud/（新增 ui/ 包）
├── Theme.java        不可变主题：id、name、tone、Palette、Metrics
├── Palette.java      颜色角色 + 派生（onLamp / wash / glow / heat）
├── Metrics.java      dp、字阶、间距、圆角、描边
├── Tone.java         LEDGER / STORY / OVERLAY
├── Themes.java       内置主题与 lamp 表；parent(kid) 解析
├── ThemeStore.java   SharedPreferences + 缓存 + revision
├── Ui.java           dp/sp、Typeface 缓存、label()、hairline()、round()
├── Focus.java        row/card/tab 焦点工具
├── OverlayTokens.java 角标固定色 / 夜间调暗（不进主题）
└── TapeView.java     一天一条带（可选，Canvas 小组件）
```

现有包只改「取颜色/字号/圆角」的位置，不改数据逻辑（`UsageDay` / `ReportEngine` / `PetEngine` 零改动）。

### 5.2 API 骨架

```java
public final class Palette {
    public final int ink, inkDeep, slab, slabRaised, rule, ruleStrong;
    public final int text, text2, text3;
    public final int lamp, lampWash, lampGlow, onLamp;
    public final int warn, danger;
    public final int[] data;            // 顺序同 Categories

    public static Palette of(int lamp, int[] data) { /* 派生 wash/glow/onLamp */ }
    public int heat(float v) { /* 0..1 → lamp 8%..82% 明度梯度 */ }
}

public final class Theme {
    public final String id;             // "ledger.moon" / "story" / "overlay"
    public final String name;           // "月白"
    public final Tone tone;
    public final Palette p;
    public final Metrics m;
    public final int rev;               // 主题切换时递增，用于 View 判断是否重建
}

public enum Scheme { WARM, COOL, INDIGO, BLACK }   // 底色轴

public final class Themes {
    public static final String LAMP_MOON = "moon";
    // ember / amethyst / ice
    public static Theme ledger(Context c);            // 读 prefs 的 scheme + lamp
    public static Theme ledger(Scheme s, int lamp);   // 组合由测试遍历
    public static Theme kid(Context c);               // 固定 STORY
    public static Theme overlay(Context c);           // 不接 lamp / scheme
    public static java.util.List<Theme> lampOptions(Context c);
    public static java.util.List<Scheme> schemes();
}

public final class ThemeStore {
    private static Theme cached; private static int cachedRev;
    public static Theme forScope(Context c, Tone scope);
    public static void setLamp(Context c, String lampId);   // 写 prefs + rev++
    public static void setScheme(Context c, Scheme s);  // 4 选 1
    public static void setDim(Context c, boolean on);
    public static int rev(Context c);
}
```

### 5.3 Canvas 视图怎么接

```java
public abstract class ThemedCanvasView extends View {
    protected Palette p;
    protected Metrics m;
    private String applied = "";

    public final void apply(Theme t) {
        if (t.id.equals(applied)) return;      // 同主题不重建
        applied = t.id; p = t.p; m = t.m;
        onThemeChanged();                      // 子类在这里重建 Shader / Bitmap
        invalidate();
    }

    protected abstract void onThemeChanged();
}
```

`ReportCanvasView` 要做的三件事：

1. `C_BG` / `C_CYAN` / `C_TEXT` 等 11 个 static final → 换成 `p.*`；
2. 构造期的 2 个 `RadialGradient` 挪进 `onThemeChanged()`，字段置空由 `onDraw` 判空；
3. 把 `onDraw` 里 11 处 `new LinearGradient` / 6 处 `DashPathEffect` 缓存进字段
   （现在周报峰值每帧约 50 个临时对象，换主题时正好一并修掉）。

`HudView` 不接 `Theme`，只接 `OverlayTokens` + `Categories` + `dim` 因子。

### 5.4 运行时切换

```java
// 家长设置里改灯色
ThemeStore.setLamp(this, lampId);                    // 写 prefs + rev++
sendBroadcast(new Intent("com.dsh.tvhud.THEME_CHANGED"));

// 各 Activity
@Override protected void onResume() {
    super.onResume();
    if (theme.rev != ThemeStore.rev(this)) recreate();  // 主题切换不在本页发生，重建一次最稳
}

// HudService
registerReceiver(new BroadcastReceiver() {
    public void onReceive(Context c, Intent i) { hudView.applyOverlay(overlayTokens(c)); }
}, new IntentFilter("com.dsh.tvhud.THEME_CHANGED"));
```

约束：主题切换只允许在家长设置（PIN 后）发生；宠物区固定 story 语气，孩子不会看到「换肤」。
切换时给设置页一次 `recreate()` 是最省事且不会状态错乱的做法（页面本身无列表状态）。

### 5.5 持久化

`SharedPreferences("hud")`：

| key | 值 | 默认 | 备注 |
|---|---|---|---|
| `theme_scheme` | `warm` / `cool` / `indigo` / `black` | `warm` | 底色轴，只影响 ledger 区 |
| `theme_lamp` | `moon` / `ember` / `amethyst` / `ice` | `moon` | 强调色轴 |
| `theme_dim` | `0` / `1` | `0` | 角标夜间调暗（不影响主题） |
| `theme_rev` | int | `0` | 每次写 +1，用于缓存失效 |

不迁移任何数据（纯 UI）；旧版本无 key 时走默认。`version.properties` 走一个 minor bump。

### 5.6 资源与 Manifest

- 仍然不给每个 Activity 写 `values/themes.xml`：所有界面是代码绘制的，唯一需要 XML 的是
  `windowBackground` 和 TV banner。
- 建议加一个 `res/values/colors.xml` 只放 `window_bg`（`#12100D`）与启动背景，
  避免冷启动白闪；主题色**不**进 XML，保持 Java 单一来源。
- Manifest 的 `Theme.Black.NoTitleBar.Fullscreen` 保留；如需 `windowBackground` 用自定义 style。

### 5.7 性能

- 主题切换：只重建当前 Activity + 角标，目标 < 150ms；报告页 Shader 重建一次。
- 报告 `onDraw` 目标零分配（现在已经因为每帧渐变不达标，本次顺带修）。
- 电视 30fps 的宠物视图不受影响（story 语气固定，不参与换灯）。
- 大面积渐变改为平面 + 最多一处 3% 光晕，规避 8bit 色带。

---

## 6. 落地路线

原则：**先零视觉变化，再换主题，最后才改外观**。任何一期都能单独发布、单独回滚。

| 期 | 内容 | 改动文件 | 验收 | 人日 |
|---|---|---|---|---|
| **P0** | 抽 `ui/`：Theme / Palette / Metrics / Themes / ThemeStore / Ui / Focus；把四处重复的字体、dp、label、hairline 收进 `Ui`（值完全不变） | 新增 `ui/*`；`HomeActivity`、`MainActivity`、`ReportCanvasView`、`PetUi`、`HudService` 各删一份重复 helper | `make check && make test`；6 张基线截图逐像素差 < 阈值 | 1 |
| **P1** | 首页 + 使用记录接 `Theme`（只有 `ledger.moon`，值等于现状） | `home/`、`usage/` | 截图同上；`make e2e` 通过 | 0.5 |
| **P2** | 报告接 `Theme`；shader/渐变按 §5.3 重建与缓存；顺手把每帧分配清零 | `report/` 5 个文件 | 周/月/年三页截图 + `make e2e`；`REPORT_INVARIANT` 无告警 | 1.5 |
| **P3** | 宠物接 story 语气；合并 `PetUi` / `PetIconView` / `PetBubble` / `PetView` 的四套调色板；删 0 引用常量；修正 4 种焦点为 1 种 | `pet/` 7 个文件 | 宠物四分区 + 拦截页 + 设置页截图；`make regression` | 2 |
| **P4** | 视觉收敛：字阶/间距/圆角/焦点统一；Day Tape 上位（首页眉标 + 宠物生长条）；报告去发光；新增 4 色系 × 4 灯 + 设置页选择器 + 夜间调暗；文案统一 | 全部 UI 文件 + 设置页 | 4 灯 × 6 页截图矩阵；对比度测试；3 米暗室人工验收 | 2.5 |

总计约 **8–9 人日**（不含美术资源，因为本项目零位图图标）。

### 6.1 每期的「不变量」

- P0–P2 结束时：**界面必须看起来和现在一样**（允许 ±2 的色彩量化误差）。这样才能把
  「重构 bug」和「设计改动」分开定位。
- P3 结束时：只允许宠物区出现可解释的变化（对比度提升、焦点统一），不允许布局重排。
- P4 才允许动版式。

### 6.2 自动化守卫

1. **颜色字面量检查**（加进 `scripts/check.sh`）：

   ```bash
   # app/src 下除 ui/、usage/Categories.java 外，不允许出现 0xAARRGGBB
   # grep 无匹配时退出码为 1，接在 if 里判断；命中才失败
   if grep -rnE '0x[0-9A-Fa-f]{8}' app/src \
        | grep -qvE 'app/src/com/dsh/tvhud/(ui/|usage/Categories.java)'; then
       fail '发现 ui/ 之外的颜色字面量'
   fi
   ```

2. **`tests/java/theme/ThemeContrastTest`**（纯 Java，不进 Android）：
   对每个内置主题断言 `text/text2/text3/lamp/data/warn` 对其表面的对比度；
   `onLamp` 推导正确；4 个 lamp 的色相距离足够；`heat()` 单调。
3. **`tests/e2e/theme-matrix.sh`**：装 APK 后按 4 灯 × 6 页截图，落到 `.build/theme/`；
   断言无 crash / 无 `REPORT_INVARIANT`，人工看一眼图。
4. **基线图对比**：P0 前先跑一次 `make regression` 留底，每期用 PIL 做像素差，
   阈值内视为「零视觉变化」。

### 6.3 完成定义

- 全仓 `0x` 颜色只剩 `ui/`、`Categories` 两处（角标 token 也在 `ui/`）；
- 4 个 lamp 在首页 / 记录 / 报告都正确生效，宠物区不受影响，角标不跟主题；
- 对比度测试全绿，`make check && make test && make regression` 通过；
- 在真实电视上以 3 米距离、夜间、4 个 lamp 各看一遍；无字幕级小字、无不可读焦点。

---

## 7. 测试与验收

### 7.1 纯逻辑单测

`tests/java/theme/ThemeContrastTest`（不 import Android）：

- 对 `Themes` 的每个主题（4 色系 × 4 灯 + story，共 17 组）断言：
  - `text` / `text2` / `text3` 对 `ink` 与 `slab` ≥ 4.5；
  - `lamp` 对 `ink` ≥ 4.5；`onLamp` 对 `lamp` ≥ 4.5；
  - `data[i]` 对 `ink` ≥ 4.5（`other` 提亮后 5.17）；
  - `heat(0) < heat(0.5) < heat(1)` 且 alpha 在 [0.05, 0.85]。
- 主题 id 唯一、4 个色系 × 4 个 lamp 都在 `schemes()` / `lampOptions()` 里、`forScope` 不返回 null。
- 测试直接读 `Palettes` 的静态表与纯函数 `Palette.of()`，不依赖 Android `Context`。

### 7.2 截图矩阵

| 页面 | 截图时机 |
|---|---|
| 首页（4 个 lamp） | 焦点在第一/第三行 |
| 使用记录（今日 / 空日） | 有数据 / 无数据 |
| 周报 / 月报 / 年报 | 进行中 / 回看旧周期 |
| 宠物房间 / 商店 / 拦截页 / 设置 | 默认焦点 + 卡片焦点 |
| 屏保角标 | 亮画面 + 暗画面 + 调暗开 |

### 7.3 人工检查清单（3 米、暗室、1080p、普通电视）

- [ ] 最小注释（9dp）能否读出来？
- [ ] 焦点是否即使眯眼也能看出在哪（不只靠颜色）？
- [ ] 4 个 lamp 下，类别色是否仍然分得清（尤其紫晶 vs 视频粉）？
- [ ] 报告页有没有可见的渐变色带？
- [ ] 屏保角标叠在白色/亮色画面上是否仍然可读？
- [ ] 换主题后回到首页，焦点是否还在原来的行？

---

## 8. 风险与取舍

| 风险 | 影响 | 对策 |
|---|---|---|
| 413 处字面量机械替换出错 | 界面悄悄变色 | 分期 + 基线截图逐像素对比 + 先 P0 零变化 |
| 专有字体不随仓库分发 | 新机器构建回退系统字体，版式走样 | 字体角色定义回退策略；数字等宽；用「有无字体」两种构建各跑一次截图 |
| 电视 8bit 面板色带 | 大方渐变出现条纹 | 去掉报告发光，最多保留一处 3% 光晕 + `setDither(true)` |
| 换主题重建 Activity | 闪一下 / 焦点丢 | 换主题只在 PIN 设置页发生；重建后恢复焦点；窗口底色与主题一致 |
| 儿童区被家长主题影响 | 孩子界面不可预期 | story 语气固定，不接 `theme_lamp` |
| 报告页每帧渐变 | 低端机掉帧 | P2 顺手改成主题切换时缓存 |
| 「统一」变成「抹平」 | 孩子区失去年龄适配 | tone 参数只调形状/字重/密度，保留暖黄反色焦点与大按钮 |

**明确不做**：浅色主题、动态取色、任意 hex 取色（只提供 4 色系 × 4 灯的策展组合）、全站统一单一色相、位图图标体系、3D/粒子动效。

---

## 附录 A · 主题值表

**色系（底色轴）**

| token | warm 暖石墨 | cool 冷石墨 | indigo 靛夜 | black 纯黑 |
|---|---|---|---|---|
| ink | `#12100D` | `#0E1116` | `#0F1020` | `#000000` |
| inkDeep | `#0A0907` | `#090C11` | `#0A0B16` | `#000000` |
| slab | `#1A1713` | `#161A21` | `#181A30` | `#0C0C0C` |
| slabRaised | `#221E19` | `#1D222B` | `#20233D` | `#141414` |
| rule | `#2A2620` | `#262C36` | `#2B2F4C` | `#262626` |
| text | `#F4EEE3` | `#EAF0F7` | `#F2F0FA` | `#F2F2EE` |
| text2 | `#C0B6A6` | `#B4C0CE` | `#C2C2DA` | `#BFBFB8` |
| text3 | `#978C7B` | `#8E9AA8` | `#9A9BB6` | `#93938C` |

**灯（强调色轴，四选一）**：`moon #F7ECD7` / `ember #FFC97A` / `amethyst #B9A5FF` / `ice #9FD3FF`；
`onLamp` 自动取 ink/text 中对比度更高者（16 个组合全部取 ink，8.9–17.9）。

**story（孩子区，固定）**：ink `#0F1220`、slab `#1B2033`、slabRaised `#252B42`、rule `#2E3650`、
text `#F7F3EA`、text2 `#C3C9D8`、text3 `#A9B0C2`、lamp `#FFD98A`、onLamp `#141020`；
圆角 22/28，焦点实底 + 白环，字号 16–40。

## 附录 B · 方向稿文件

| 文件 | 说明 |
|---|---|
| [`design/system.html`](design/system.html) / `.png` | 系统板：灯、表面、字阶、带子、焦点、语气、数据色 |
| [`design/home.html`](design/home.html) / `.png` | 首页方向稿（Day Tape + 台账菜单） |
| [`design/report.html`](design/report.html) / `.png` | 周报方向稿（去发光、灯色强调、数据色保留） |
| [`design/schemes.html`](design/schemes.html) / `.png` | 色系板：4 底色系 × 4 灯、配置面板、16 组合断言 |
| [`design/app.css`](design/app.css) | 两套 tone 的 token 与组件样式 |

## 附录 C · 命名与文案

| 场景 | 现状 | 建议 |
|---|---|---|
| 首页标题 | 使用台账 | 保留（品牌词） |
| 菜单 | 使用记录 / 使用报告 / 我的宠物 / 家长设置 | 保留 |
| 日期 | `2026-11-07 EEE` | 统一 `2026-11-07 周六` |
| 时长 | `H:MM:SS` / `MM:SS` / `1h14m` 混用 | `H:MM`（≥1h）/ `MM:SS`；KPI 可用 `1h 14m` |
| 键位提示 | 各页格式不一 | `← → 切换周期 · ↑ ↓ 周 / 月 / 年 · OK 回到本期 · 返回 退出` |
| 空状态 | 今天还没有记录到使用时间 / 屏保、熄屏的时间不计入 | 保留（语气好，方向明确） |
| 错误 | 报告加载失败，按 OK 重试 | 保留；补「返回 退出」 |
| 儿童区 | 到点拦截、答题 | 不出现「监控 / 限制 / 惩罚」，用「该休息了 / 这一场结束」 |

---

## 附：参考的既有实现与约束

- 构建：`bash app/build.sh`（无 Gradle，javac + d8 + aapt2）；主题改动必须能通过同一构建。
- 业务模型：`UsageDay.Seg`（24h 分段）、`Categories`（5 类）、`Report`（周期聚合）、`PetState`（成长/场次）。
- 测试三层：`tests/java`（纯 Java）、`tests/e2e`（真机）、`scripts/check.sh`（仓库自检）。
- 电视安全区：48dp（1080p 下 96px），已满足 5% overscan。
