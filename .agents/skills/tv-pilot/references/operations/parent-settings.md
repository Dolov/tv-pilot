# 家长设置：PIN、各项开关、调试广播

tv-pilot 的家长端有两个入口，**都受同一个 4 位 PIN 保护**：

| 入口 | 怎么进 |
|---|---|
| 首页菜单「家长设置」（v1.23 起，推荐） | App 打开 → 首页第 4 项 → 输入 PIN |
| 宠物房间 / 到点拦截页长按返回 5 秒（老入口，仍保留） | 长按遥控器「返回」键 5 秒 → 输入 PIN |

## 一条命令拿密码

```bash
bash scripts/parent-pin.sh          # 人读：PIN、来源、今天免限状态
bash scripts/parent-pin.sh --json   # 机读：{pin, pin_source, default_pin, changed, free_pass_today, ...}
```

**出厂默认 PIN 是 `2580`**（4 位数字）。

- 定义位置：`app/src/com/dsh/tvhud/pet/PetStore.java` 的 `DEFAULT_PIN` 常量。
- App **目前没有「改 PIN」的界面**（`setPin()` 有实现但没有任何调用者），所以电视上跑的就是这个默认值。
- 连输错 **5 次锁 60 秒**，锁定状态写在存档里，杀进程重进也绕不掉，等 60 秒即可。

### 为什么脚本给的是「默认值」而不是「电视上实际的 PIN」

PIN 存在 App 的 `SharedPreferences("pet")` 的 `cfg_pin` 键里。App 不是 debuggable 的，
无 root 的 adb 读 `/data/data/com.dsh.tvhud/shared_prefs/pet.xml` 会 `Permission denied`，
所以脚本读不到真实值，只能给：

| pin_source | 含义 |
|---|---|
| `default` | 出厂默认值（当前状态：App 没改 PIN 的入口，所以它就是实际密码） |
| `local` | 本机在 `references/local.md` 里登记过 —— 说明家长改过 PIN，以登记值为准 |
| `unknown` | 两者都没有：家长改过但没登记，脚本读不到 |

家长改过 PIN 时，把新 PIN 写进 `references/local.md`（已 gitignore）的一行里：

```
| 家长设置 PIN | `1234` |
```

脚本会自动识别（`pin_source` 变 `local`）。

> **脱敏红线**：`CONTRIBUTING.md` 明确「儿童模式密码、家长控制真实配置值」不许进仓库。
> 仓库里只保留 `2580` 这个**已经公开在源码里的出厂默认值**；家长自己设的新 PIN
> 只能放 `references/local.md` / `.env`（两者都 gitignore）。

## 家长设置里有哪些开关

进入 PIN 页后，用遥控器 ↑↓ 选行、←→ 或 OK 改值、返回键退出。

### 时间规则

| 设置项 | 默认 | 说明 |
|---|---|---|
| 每场时长 | 15 分钟 | 10–20 |
| 每天场次 | 3 场 | 1–5；周末自动 +1 |
| 答题续时 | 开 | 关掉后到点硬停 |
| 每天续时次数 | 2 次 | 0–3；未过关不补考 |
| 周末多 1 场 | 开 | 敏感孩子可关 |
| 允许时段 | 关 | 只拦「新开的一场」，不掐断正在看的；跨零点自动支持，开始=结束表示全天 |
| 允许时段开始 / 结束 | 17:00 / 20:00 | 改时间时会顺手把「允许时段」打开 |
| **今天免限** | 关 | **开 = 当天不拦时段、场次也不限；明天自动恢复** |
| 临时加一场 | — | +1 场（15 分钟），当天有效 |

### 宠物行为

| 设置项 | 默认 | 说明 |
|---|---|---|
| 退化机制 | 开 | 关掉后连续不达标只影响当天成长值，不退回外观 |
| 温和模式 | 关 | 只提醒一次，不反复把拦截页拉到最前 |
| 家长时间（不计数） | 关 | 家长自己看时不计数、不拦截 |

### 其它

| 设置项 | 说明 |
|---|---|
| 宠物周报 | 本周观看时长、场次、按时休息、超时、答题正确率、成长/分支/变异；只统计时长与行为，**不记录内容、不上传** |
| 重置宠物 | 按两次确认；重置宠物状态和当天预算，**配置与 PIN 保留** |

## 「今天免限」是什么、什么时候用

允许时段开着时，时段外新开的一场会被立刻拦下，而且时段外休息**不计场次**，
于是形成死循环 —— 一开视频就弹「现在是休息时间」，孩子连 3 秒都看不了。
家长想临时放开就只能去改「每天场次」「允许时段」这些长期配置。

「今天免限」就是给这种情况准备的：**当天不拦允许时段、场次上限也不拦**，
明天 0 点自动恢复原来的限制。

- 存的是「发卡那天的 0 点时间戳」而不是「还剩多久」：跨天自然对不上，
  不用定时器去关，电视断电/重启也不会把昨天的卡带过来。
- 免限期间**照样累计观看时间**（宠物知道孩子在看、成长值照算），只是不拦、不弹「快到点了」。
- 收回卡的时候闸门会被关掉、正在看的这一场作废重来。
- 家长发卡时，屏幕上正开着的拦截页会自动放行并显示「今天可以继续看」。

## 调试广播（只给开发/排障用）

```bash
adb shell am broadcast -n com.dsh.tvhud/.hud.CtrlReceiver -a com.dsh.tvhud.<ACTION>
```

| 广播 | 作用 |
|---|---|
| `PET_SETTINGS` | 直接打开家长设置页（仍要过 PIN） |
| `PET_STATE` | 把当前存档打到 logcat（`sessions=` `gate=` `window=` `freePass=` 等） |
| `PET_FREE_PASS_ON` / `PET_FREE_PASS_OFF` | 开 / 关今天的免限卡 |
| `PET_WINDOW_ON` / `PET_WINDOW_OFF` | 开 / 关允许时段 |
| `PET_PARENT_ON` / `PET_PARENT_OFF` | 家长时间（不计数） |
| `PET_GATE [--ez window true]` | 强制弹拦截页（`--ez window true` 模拟时段外） |
| `PET_NEAR_END --ei seconds 180` | 把本场调到只剩 N 秒 |
| `PET_DEBUG --ei exp 450 --ei stars 200 --ei sessions 2` | 改成长值/星星/已用场次 |
| `PET_RESET` | 重置宠物状态（配置与 PIN 保留） |

新装的包 `stopped=true`，广播可能没响应，先 `am start` 拉起一次。

## 相关

- App 源码：`app/src/com/dsh/tvhud/pet/`（`PetSettingsActivity` 页面、`PetStore` 存档、`Budget` 场次逻辑）
- 交互设计：`docs/pet-design.md` §9 家长端、§12.1 为什么「今天免限」存日期而不是时长
- 自研 App 的完整开发闭环：`references/operations/dev-workflow.md`
