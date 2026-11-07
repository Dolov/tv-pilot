# 前端生态的主题配色是怎么设计的（调研参考）

状态：**调研笔记 + 对 tv-pilot 方案的校正依据** · 调研日期 2026-11-07

目的：在动手实现 tv-pilot 的主题层之前，先看成熟组件库怎么解决同一个问题——**「一套组件，多套配色」**。
本文只写一手文档确认的事实；社区做法单独标注。

---

## 0. 五家的一句话

| 库 | 核心机制 | 一句话 |
|---|---|---|
| **shadcn/ui**（Tailwind v4） | CSS 变量 + 语义 token + `@theme inline` | 组件只写 `bg-background text-foreground`，颜色全在 `:root` / `.dark` 两处 |
| **Radix Themes** | 12 步色阶 + accent/gray 双轴 | 主题 = `accentColor`（强调）+ `grayColor`（中性），自动配对 |
| **daisyUI** | 一整包 CSS 变量当主题 | 主题 = 一组 `--color-*` + `--radius-*`，`data-theme` 切换 |
| **Chakra UI v3** | primitive token → semantic token | `tokens` 定原子值，`semanticTokens` 按 light/dark 派生语义值 |
| **Ant Design v5** | Seed → Map → Alias 三层算法 | 改 1 个 seed，算法算出整条梯度 |

**五家的共识：主题 = 少量「种子」+ 一个派生算法 + 一层语义别名。**
没有一家是「每个页面写一套颜色」。

---

## 1. shadcn/ui —— 语义 token 的标准答案

来源：[ui.shadcn.com/docs/theming](https://ui.shadcn.com/docs/theming)（官方文档，已核对原文）

### 1.1 token 命名约定

> 文档原文：用**语义化的 background / foreground 配对**。base token 控制表面颜色，`-foreground` 控制其上的文字与图标。
> 表面 token 省略 background 后缀，例如 `primary` 与 `primary-foreground` 配对。

即：`<role>` 与 `<role>-foreground` 是一对，组件写成 `bg-primary text-primary-foreground`。

### 1.2 完整 token 表（文档原表）

| token | 控制什么 | 用在哪 |
|---|---|---|
| `background` / `foreground` | 默认应用背景与文字 | 页面外壳、区块、默认文字 |
| `card` / `card-foreground` | 抬起的表面及其内容 | Card、dashboard 面板、设置面板 |
| `popover` / `popover-foreground` | 浮层表面及其内容 | Popover、DropdownMenu、ContextMenu |
| `primary` / `primary-foreground` | **高强调**动作与品牌表面 | 默认 Button、选中态、badge、激活强调 |
| `secondary` / `secondary-foreground` | 次强调填充动作与支撑表面 | 次级按钮、次级 badge |
| `muted` / `muted-foreground` | 弱表面与低强调内容 | 描述、占位符、空状态、辅助文字 |
| `accent` / `accent-foreground` | **交互 hover / focus / active 表面** | Ghost 按钮、菜单高亮、hover 行、选中项 |
| `destructive` | 破坏性动作与错误强调 | 危险按钮、非法状态 |
| `border` | 默认边框与分隔线 | 卡片、菜单、表格、分隔线 |
| `input` | 表单控件边框与表面处理 | Input、Textarea、Select |
| `ring` | **焦点环与轮廓** | 按钮、输入框、checkbox、菜单等可聚焦控件 |
| `chart-1` … `chart-5` | 默认图表配色 | 图表与图表驱动的面板 |
| `sidebar` / `sidebar-foreground` 等 7 个 | 侧栏专属 | 侧栏容器、内容、主/强调/边框/焦点环 |
| `radius` | 基础圆角刻度 | 由它派生 `radius-*` |

### 1.3 三个关键设计决定

**（1）`accent` 与 `primary` 的区别** —— 这是最常被搞混的一对：

- `primary`：品牌色，**填充式**主按钮、选中态、badge（需要高对比文字，配 `-foreground`）；
- `accent`：交互态的**表面色**（hover / focus / active 的背景），通常与 `muted` 同色系但更突出；
- `ring`：焦点环，**独立 token**，不跟 `primary`/`accent` 走。

这直接回答了我们方案里「灯到底管什么」的问题：**灯 ≈ primary + ring 两个角色**，而不是一个颜色随便用。

**（2）圆角是派生刻度，不是各组件各写一个数**：

```css
@theme inline {
  --radius-sm:  calc(var(--radius) * 0.6);
  --radius-md:  calc(var(--radius) * 0.8);
  --radius-lg:  var(--radius);
  --radius-xl:  calc(var(--radius) * 1.4);
  --radius-2xl: calc(var(--radius) * 1.8);
  --radius-3xl: calc(var(--radius) * 2.2);
  --radius-4xl: calc(var(--radius) * 2.6);
}
```

改一个 `--radius`，整条刻度跟着变。

**（3）新增 token 的完整步骤**（文档「Adding New Tokens」）：

```css
:root {
  --warning: oklch(0.84 0.16 84);
  --warning-foreground: oklch(0.28 0.07 46);
}
.dark {
  --warning: oklch(0.41 0.11 46);
  --warning-foreground: oklch(0.99 0.02 95);
}
@theme inline {
  --color-warning: var(--warning);
  --color-warning-foreground: var(--warning-foreground);
}
```

三步：定义 → 在暗色变体里覆盖 → 用 `@theme inline` 暴露给 utility。**这就是我们 `Palette` + `Scheme` 需要的结构。**

### 1.4 暗色与运行时切换

- 暗色 = 在 `.dark` 选择器里**覆盖同一批 token**（`@custom-variant dark (&:is(.dark *))`）。
- 组件代码完全不变 —— 因为组件引用的是 `var(--primary)` 这类**间接引用**，不是字面量。
- 切换主题 = 换 class，**不需要重新渲染组件**，CSS 变量一变，全站即时生效。

### 1.5 base color 是什么

`components.json` 的 `tailwind.baseColor` 决定 `init` 生成的默认 token 取值。文档列出的可选值：
**Neutral、Stone、Zinc、Mauve、Olive、Mist、Taupe**（都是**中性色**系，没有彩色）。

含义：**「底色系」在 shadcn 里是一个独立旋钮**（base color = 中性灰的色调），与「primary 品牌色」分开。
这与我们「色系 × 灯色」的两轴设计完全同构。

### 1.6 图表色

`--chart-1..5` 是**序号色板**，不语义化（不是「视频色」「音乐色」），也不随 light/dark 换相，只换明度。

> 对我们的意义：shadcn 允许「图表色跟着主题走」；但我们的图表色是**业务语义**（视频/音乐/儿童学习），
> 属于 `Categories`，不该跟主题联动。**我们比 shadcn 多一层「不可配置色」。**

### 1.7 局限与坑

- **对比度没人保证**：`oklch(0.205 0 0)` 和 `oklch(0.985 0 0)` 是官方默认值，但换成自定义色后，
  `-foreground` 要自己配；文档不提供自动推导。
- `base color` 只是一次性生成默认值，之后不跟 `primary` 联动。
- `--no-css-variables` 模式（`init --no-css-variables`）会把 token 展开成 `bg-zinc-950 text-zinc-50 dark:bg-white`，
  **可读性变差且不可换肤**，文档明确说切换已有项目要删掉重装组件。

---

## 2. Radix Themes —— 12 步色阶与「双轴」

来源：[radix-ui.com/themes/docs/theme/color](https://www.radix-ui.com/themes/docs/theme/color)、[.../theme/overview](https://www.radix-ui.com/themes/docs/theme/overview)、[.../theme/radius](https://www.radix-ui.com/themes/docs/theme/radius)、[.../theme/dark-mode](https://www.radix-ui.com/themes/docs/theme/dark-mode)

### 2.1 12 步色阶的官方分工

文档给出的 accent 色阶解剖（`--accent-1` … `--accent-12`）：

| 区间 | 官方注释 |
|---|---|
| 1–2 | Backgrounds |
| 3–5 | Interactive components |
| 6–8 | Borders and separators |
| 9–10 | Solid colors |
| 11–12 | Accessible text |

外加 4 个功能色：`--accent-surface`、`--accent-indicator`、`--accent-track`、`--accent-contrast`。

灰阶同构：`--gray-1..12` + `--gray-surface/indicator/track/contrast`。

### 2.2 双轴：accent + gray

```tsx
<Theme accentColor="indigo" grayColor="gray" panelBackground="solid" scaling="100%" radius="full">
```

- `accentColor`：主题里最主导的颜色 → 主按钮、链接、交互元素。**27 种可选**（Gray/Gold/Bronze/Brown/Yellow/Amber/Orange/Tomato/Red/Ruby/Crimson/Pink/Plum/Purple/Violet/Iris/Indigo/Blue/Cyan/Teal/Jade/Green/Grass/Lime/Mint/Sky）。
- `grayColor`：6 种中性灰（Gray/Mauve/Slate/Sage/Olive/Sand）。
  文档明确：**accent 会自动配一个协调的灰**，也可以自己指定。
- 底层是 Radix Colors，**P3 广色域**。

### 2.3 主题级参数（不止颜色）

`radius` 有 6 档：`none / small / medium / large / full`，文档强调「**结果是上下文相关的**」：
`radius="full"` 时按钮变胶囊，但 **Checkbox 永远不会变成全圆**（避免和 Radio 混淆）。组件可用自己的 `radius` prop 覆盖。

其它主题级参数：`panelBackground`（translucent / solid）、`scaling`（间距缩放）、`appearance`（light/dark/inherit）。

### 2.4 局部作用域与派生

- `<Theme>` 可嵌套：子组件继承，组件的 `color` prop 可临时换 accent，**嵌套自动继承新的 accent**。
- `highContrast` prop：让元素在页面背景上「跳出来」。
- 文档特别说明：Radix 会**根据当前 accent 自动调整 focus 与 selection 颜色**，避免色相冲突。

### 2.5 暗色

`appearance="light" | "dark"`，默认 `light`；`inherit` 跟随系统。
推荐用 next-themes 的 class 切换（`attribute="class"`），文档明确**不要**写 `appearance={resolvedTheme}`，
理由是 class 切换能避免首屏闪烁。

### 2.6 对我们的启示

- **12 步色阶**是「一个色相 → 12 个语义槽位」的成熟解法，比我们现在的「每个角色手写 hex」强。
- **accent 自动配灰** 正是我们「色系 × 灯色」想要的效果，但方向相反：他们是「灯自动定灰」，
  我们是「用户选灰 + 选灯」。**我们的做法更可控，但需要断言来保证两者不打架。**
- **`--accent-contrast`** 是他们专门留给「灯色上的文字」的槽位 —— 和我们 `onLamp` 是同一个需求。

---

## 3. daisyUI —— 主题就是一组 CSS 变量

来源：[daisyui.com/docs/themes](https://daisyui.com/docs/themes/)

### 3.1 主题结构（自定义主题的官方模板）

```css
@plugin "daisyui/theme" {
  name: "mytheme";
  default: true;
  prefersdark: false;
  color-scheme: light;
  --color-base-100: oklch(98% 0.02 240);
  --color-base-200: oklch(95% 0.03 240);
  --color-base-300: oklch(92% 0.04 240);
  --color-base-content: oklch(20% 0.05 240);
  --color-primary: oklch(55% 0.3 240);
  --color-primary-content: oklch(98% 0.01 240);
  --color-secondary: …   --color-secondary-content: …
  --color-accent: …     --color-accent-content: …
  --color-neutral: …    --color-neutral-content: …
  --color-info: …       --color-info-content: …
  --color-success: …    --color-success-content: …
  --color-warning: …    --color-warning-content: …
  --color-error: …      --color-error-content: …
  --radius-selector: 1rem;
  --radius-field: 0.25rem;
  --radius-box: 0.5rem;
  --size-selector: 0.25rem;
  --size-field: 0.25rem;
  --border: 1px;
  --depth: 1;
  --noise: 0;
}
```

要点：

- **表面是三阶**：`base-100 / 200 / 300`（底 → 抬一层 → 抬两层），内容色 `base-content`；
- **强调色是 7 个角色**（primary/secondary/accent/neutral/info/success/warning/error），
  **每个都强制配一个 `-content`**（其上的文字色）；
- **圆角分三类**：`radius-selector`（选择器）/ `radius-field`（输入）/ `radius-box`（容器）—— 按**角色**分，不是一个刻度；
- `depth` / `noise` 是**质感开关**（阴影强度、噪点），不是颜色 token。

### 3.2 切换与作用域

- 全局：`<html data-theme="cupcake">`；
- 局部：给任意元素加 `data-theme`，**可无限嵌套**，内层覆盖外层；
- 主题列表：`@plugin "daisyui" { themes: light --default, dark --prefersdark, cupcake; }`；
  `themes: all` 打开全部 35 个内置主题，`themes: false` 关闭所有。

### 3.3 对我们的启示

- **`-content` 强制配对** = 我们 `onLamp` 的通用版：任何「表面色」都必须声明「其上的文字色」。
- **圆角按角色分三档**（selector / field / box）比我们的「三档圆角」更可操作；
  我们可以对应成 `hairline(0) / control(10) / container(22)`。
- **`depth` / `noise` 这类「质感开关」** 我们完全没有对应物 —— 这是个缺口：
  我们的报告页用「发光」模拟层次，可以改成显式的 `depth` 开关（0/1/2）。

---

## 4. Chakra UI v3 —— primitive / semantic 两层 + 条件

来源：[chakra-ui.com/docs/theming/tokens](https://chakra-ui.com/docs/theming/tokens)、[.../theming/colors](https://chakra-ui.com/docs/theming/colors)、[.../theming/overview](https://chakra-ui.com/docs/theming/overview)

### 4.1 两层 token

**primitive（`theme.tokens`）**：原子值，形状是 `{ value }`：

```ts
defineConfig({
  theme: {
    tokens: { colors: { primary: { value: "#0FEE0F" } } },
    semanticTokens: {
      colors: { danger: { value: "{colors.red}" } },   // 引用另一个 token
    },
  },
})
```

**semantic（`theme.semanticTokens`）**：带条件（`light` / `dark`），例如官方内置的：

```
bg          light:{white}        dark:{black}
bg.subtle   light:{gray.50}      dark:{gray.950}
bg.muted    light:{gray.100}     dark:{gray.900}
bg.emphasized light:{gray.200}   dark:{gray.800}
bg.inverted light:{black}        dark:{white}
bg.panel    light:{white}        dark:{gray.950}
bg.error    light:{red.50}       dark:{red.950}
border      light:{gray.200}     dark:{gray.800}
border.muted/subtle/emphasized/inverted/error/warning/success/info
fg          light:{black}        dark:{gray.50}
fg.muted    light:{gray.600}     dark:{gray.400}
fg.subtle   light:{gray.400}     dark:{gray.500}
fg.inverted light:{gray.50}      dark:{black}
fg.error/warning/success/info
gray.contrast / gray.fg / gray.subtle / gray.muted / gray.emphasized / gray.solid / gray.focusRing / gray.border
（每种颜色再分 .contrast / .fg / .subtle / .muted / .emphasized / .solid / .focusRing / .border）
```

**这个命名法最值得抄**：`fg` / `fg.muted` / `fg.subtle` / `fg.inverted`，
以及 `border` / `border.muted` / `border.emphasized`。

注意 `gray.focusRing` 是**独立槽位**，与 `gray.fg` 分开 —— 和 shadcn 的 `ring` 一个道理。

### 4.2 token 引用语法

复合值里用 `{path.to.token}`，必须写**完整路径**（`{colors.red.300}`，不能写 `red.300`）。

### 4.3 其它

- `cssVarsRoot`（默认 `:where(:root, :host)`）、`cssVarsPrefix`（默认 `ck`）控制变量作用域与前缀；
- `conditions` 定义 light/dark 的判定；
- `recipes` / `slotRecipes` 是组件级配方；
- 官方明确**推荐用 semantic token**，理由是「自动适配 color mode 且全应用风格一致」。

### 4.4 对我们的启示

- **命名法直接可用**：`text` / `text.muted` / `text.subtle` / `text.inverted`，
  `border` / `border.muted` / `border.emphasized`。
  我们现在的 `text / text2 / text3` 是位置命名，扩展性差（加第 4 级就没法叫了）。
- **`fg.inverted` = 我们 `onLamp` 的通用位置**。
- **`focusRing` 独立槽位**：我们的灯应该拆成 `lamp`（表面）+ `lampRing`（焦点环），
  因为 ledger 的焦点是「灯晕底 + 脊」，story 的焦点是「灯实底 + 白环」—— **几何不同，颜色角色也不同**。

---

## 5. Ant Design v5 —— Seed / Map / Alias 三层算法

来源：[ant.design/docs/react/customize-theme](https://ant.design/docs/react/customize-theme)（含中文版「基本概念」章节）

### 5.1 三层派生关系（中文版原文）

> 这三组 Token 并不是简单的分组，而是一个三层的派生关系，由 **Seed Token 派生 Map Token，再由 Map Token 派生 Alias Token**。

- **Seed Token**：所有设计意图的起源。如 `colorPrimary`、`borderRadius`、`fontSize`。
- **Map Token**：基于 Seed 派生的**梯度**变量。如 `colorPrimaryBg`、`colorPrimaryBorder`。
  官方建议：**定制 Map Token 要用 `theme.algorithm`**，以保证梯度关系不被破坏。
- **Alias Token**：批量控制共性组件样式，基本是 Map Token 的别名。如 `colorLink`。

### 5.2 算法

预设三套：`theme.defaultAlgorithm`、`theme.darkAlgorithm`、`theme.compactAlgorithm`；
**可组合**，按顺序生效：`algorithm: [darkAlgorithm, compactAlgorithm]`。

### 5.3 其它

- 组件级 `Component Token`：`theme.components.Button.colorPrimary`，**互不影响**；
  ≥5.8.0 支持 `algorithm: true` 让组件 token 也参与派生；
- `useToken()` 取当前主题下的 token；
- `ConfigProvider` 可**嵌套**实现局部主题，子主题未改动的 token 继承父主题；
- 动态切换：直接改 `theme` 属性即可（CSS-in-JS 运行时生成）；
- `cssVar` 选项开 CSS 变量模式；
- `token.motion: false` 可关动画（**性能兜底开关**，我们有类似诉求）。

### 5.4 对我们的启示

- **「改 1 个 seed → 算法算出整条梯度」** 是我们最该抄的一条：
  我们现在的 `Palette.of(lamp, data)` 只是手写派生，应该改成**种子 + 算法**：
  ```
  seed: { lamp, neutralHue, dataHue[] }
    → map:   { lamp1..lamp12, gray1..gray12, data1..data12 }   // 12 步
    → alias: { ink, slab, rule, text, text2, text3, lamp, lampWash, onLamp, ... }
  ```
- **算法可组合** = 我们的「色系 × 灯色」本质是两个算法：`neutralAlgorithm(scheme)` + `accentAlgorithm(lamp)`。
  组合执行就是 16 个组合 —— 和 Ant 的 `[darkAlgorithm, compactAlgorithm]` 同构。
- **组件级 token 互不影响** → 我们的 `story` 语气（宠物区）就该是独立的一组 component token，
  不受 ledger 主题影响。这正好印证「孩子区固定 story」的设计。

---

## 6. 横向对比

| 维度 | shadcn/ui | Radix Themes | daisyUI | Chakra v3 | Ant Design v5 |
|---|---|---|---|---|---|
| token 分层 | primitive → semantic → component | 12 步色阶 → 组件 | 一包 CSS 变量 | primitive → semantic → recipes | Seed → Map → Alias → Component |
| 强调色 | `primary` | `accentColor`（27 种） | `--color-primary` | palette | `colorPrimary`（seed） |
| 中性色 | `baseColor`（7 种） | `grayColor`（6 种，自动配对） | `--color-base-100/200/300` | `gray.*` | `colorBgBase` / `colorTextBase` |
| 表面层级 | background / card / popover / muted / accent | 12 步 1–2 + panel | base-100/200/300 | bg / bg.subtle / bg.muted / bg.emphasized / bg.panel | colorBgLayout / Container / Elevated |
| 文字层级 | foreground / muted-foreground / accent-foreground | gray-11 / 12 | base-content | fg / fg.muted / fg.subtle / fg.inverted | colorText / Secondary / Tertiary / Quaternary |
| 焦点环 | `ring`（独立） | 自动随 accent 调整 | `--color-*` 无独立 ring | `gray.focusRing`（独立） | 无独立 token |
| 表面上的文字 | `-foreground` 强制配对 | `--accent-contrast` | `-content` 强制配对 | `fg.inverted` | `colorTextLightSolid` 等 |
| 圆角 | 1 个 `--radius` 派生刻度 | 6 档 radius，按上下文 | 3 类（selector/field/box） | token | `borderRadius` seed 派生 |
| 对比度保证 | ❌ 手工配 | ⚠️ 12 步 + P3 设计保证 | ⚠️ 每个 `-content` 手工写 | ⚠️ 条件值手工写 | ⚠️ 算法按 3:1 阈值算 `contrastText` |
| 运行时切换 | class 切换 CSS 变量 | `<Theme appearance>` | `data-theme` | 系统 + provider | 改 `theme` 对象 |
| 局部作用域 | CSS 天然支持 | `<Theme>` 嵌套 | `data-theme` 任意嵌套 | Provider 嵌套 | `ConfigProvider` 嵌套 |
| 图表色 | `--chart-1..5` | `--accent-*` 12 步 | 无专用 | 无 | 无 |

---

## 7. 六条可以直接抄进 tv-pilot 的结论

### 结论 1：token 命名改成「语义 + 修饰」，不要用位置序号

我们现在是 `text / text2 / text3`，加到第 4 级就没法命名了。
抄 Chakra 的命名法：

```
text            正文
text.muted      次级
text.subtle     注释
text.inverted   反白（灯色实底上的文字）
border          发丝线
border.muted    弱分隔
border.emphasis 强分割 / 键帽
surface         面板
surface.raised  抬起表面
```

Java 里用嵌套类或 `TEXTS` 数组 + 语义常量都可以，关键是**名字表达用途，不表达顺序**。

### 结论 2：`onLamp` 不是特例，是通用规则

daisyUI 的 `-content`、Radix 的 `--accent-contrast`、Chakra 的 `fg.inverted`
都是同一条规则：**任何表面色都要声明其上的文字色**。
我们把它固化成一个函数：`contrastOn(int surface)`，所有「实底表面」都走它，
不要再手写 `#141210` 这种「灯色上的深字」。

### 结论 3：焦点环要是独立 token，不能和强调色共用

shadcn 的 `ring`、Chakra 的 `gray.focusRing` 都是独立槽位。
我们的现状是「灯色既做焦点脊、又做选中底、又做游标」——**应该拆**：

```
lamp        强调色本体（实底表面、选中、游标）
lamp.ring   焦点环 / 焦点脊（ledger 用）
lamp.wash   焦点背景晕（ledger 用）
```

理由：ledger 的焦点是「10% 晕 + 5dp 脊」，story 的焦点是「实底 + 白 3dp 环」，
**几何不同 → 颜色角色不同 → 必须是不同 token**。

### 结论 4：把「种子 + 算法」写进 Palette，而不是手写派生

我们现在 `Palette.of(lamp, data)` 是手写 4 个派生值。
抄 Ant Design 的三层：seed → map → alias。

```
seed（用户选的 2 个值）
  scheme: WARM | COOL | INDIGO | BLACK
  lamp:   moon | ember | amethyst | ice
        ↓ neutralAlgorithm(scheme) + accentAlgorithm(lamp)
map（12 步色阶，两轴各一套）
  gray1..gray12 / accent1..accent12
        ↓ aliasAlgorithm()
alias（页面真正用的）
  ink / surface / surfaceRaised / border / text / textMuted / textSubtle /
  lamp / lampRing / lampWash / onLamp / warn / danger / data[5]
```

好处：加一个新色系只需实现一个 `neutralAlgorithm`；对比度由算法保证，不靠人工核对。
Radix 的 12 步与 Ant 的 map 层在这里合流。

### 结论 5：组件级 token 独立于全局主题 —— 印证「孩子区固定 story」

Ant 的 Component Token、shadcn 的 `sidebar-*` 都是同一思路：
**某个区域的样式不该被全局主题改写**。
我们的宠物区固定 story 语气（固定琥珀灯、大圆角、粗字），
就是这个模式；实现上把它做成独立的 `Palette` 实例而不是「story 下的特殊值」。

### 结论 6：主题色不能跟图表色混在一起

shadcn 的 `--chart-1..5` 是序号板、随主题变；我们的 5 个类别色是**业务语义**（用户明确指定），
必须留在「不可配置色」区。这是我们比通用组件库多出来的一层，
也正是为什么我们的主题切换**不能**做成「一套 CSS 变量全换」。

---

## 8. 对现有方案的修正清单

| 现有方案 | 修正 | 依据 |
|---|---|---|
| token 名 `text / text2 / text3` | → `text / textMuted / textSubtle / textInverted` | Chakra v3 命名法 |
| `lampWash` / `lampGlow` / `lamp` | → `lamp` + `lamp.wash` + `lamp.ring`（焦点环独立） | shadcn `ring`、Chakra `focusRing` |
| `Palette.of(lamp, data)` 手写派生 | → seed → map(12 步) → alias 三层算法 | Ant Design、Radix 12 步 |
| `onLamp` 手写「取对比度高者」 | → 通用 `contrastOn(surface)`，所有实底表面统一走 | daisyUI `-content`、Radix `--accent-contrast` |
| 圆角三档 `0/10/22` 写死 | → 一条 `radius` 种子派生刻度，按角色取（hairline / control / container） | shadcn `--radius` 派生、daisyUI 三类 radius |
| 无质感开关 | → 加 `depth`（0/1/2），报告页的「发光」改由它控制 | daisyUI `depth` / `noise` |
| 16 个组合手工枚举 | → 两个算法组合生成，测试遍历 | Ant 的算法可组合 |
| 角标固定白字 | → 保持；并明确它属于「不可配置色」层 | 结论 6 |

**对工作量估算的影响**：增加「12 步色阶 + 三层算法」会让 P0 从「纯搬颜色」变成「写一套色阶算法」，
约 **+0.5–1 人日**；但换来的是「新增色系不用人工核对对比度」，长期划算。
建议 P0 仍然只做**搬迁 + 手写派生**（保零视觉变化），
把三层算法放到 P2 与报告页 shader 改造一起做。

---

## 9. 一手来源

- shadcn/ui Theming：<https://ui.shadcn.com/docs/theming>
- shadcn/ui Dark Mode：<https://ui.shadcn.com/docs/dark-mode>
- Tailwind CSS v4 Theme variables：<https://tailwindcss.com/docs/theme>
- Radix Themes Color：<https://www.radix-ui.com/themes/docs/theme/color>
- Radix Themes Overview / Radius / Dark mode：<https://www.radix-ui.com/themes/docs/theme/overview> · `.../radius` · `.../dark-mode`
- daisyUI Themes：<https://daisyui.com/docs/themes/>
- Chakra UI Tokens / Colors / Theming：<https://chakra-ui.com/docs/theming/tokens> · `.../colors` · `.../overview`
- Ant Design Customize Theme：<https://ant.design/docs/react/customize-theme>（中文版 `.../customize-theme-cn`）

---

## 附：给本项目的一句结论

> 五家组件库解决的是同一个问题：**怎么让一套组件吃到多套配色，而不写第二遍代码。**
> 答案高度一致 —— **少量种子 + 派生算法 + 语义别名 + 组件级覆盖**。
>
> tv-pilot 的额外约束是两条：
> **① 数据类别色是业务语义，必须冻结在不可配置层**（通用组件库没有这一层）；
> **② 焦点几何按语气分两种**（ledger 灯晕+脊 / story 实底+白环），所以焦点环必须是独立 token。
>
> 这两条决定了我们不能照抄任何一家的完整方案，只能取其骨架：
> **Ant 的三层算法 + Radix 的 12 步色阶 + shadcn 的语义命名 + Chakra 的焦点环独立 + daisyUI 的 `-content` 配对规则。**
