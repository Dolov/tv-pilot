# 屏保识别与「屏保时在屏幕上叠字」

用户问「电视现在是不是屏保」「能不能在屏保上显示点东西」「做个只在屏保时出现的角标」「角标长条怎么分类 / 为什么某段没记上」时读这份。

## 本机屏保是什么

| 项 | 值 |
|---|---|
| 组件 | `com.mitv.screensaver/.RenderActivity`（另有 `.ScreenSaverActivity`） |
| 进入条件 | 无操作约 8 分钟（`screen_saver_timeout=480000`） |
| AOSP Dream 通道 | **关闭**：`screensaver_enabled=0`，`screensaver_components` 指向未安装的 `com.google.android.deskclock/...` |
| 屏幕休眠 | `screen_off_timeout=2147483647`（永不休眠），屏保期间背光仍然全开 |

**不要试图用 `DreamService` 做屏保**：本机标准 Dream 通道没启用，`cmd dreams` 也没有实现。

## 判断「现在是不是屏保」

唯一可靠的信号：当前焦点是 `com.mitv.screensaver` 的 Activity。

```bash
adb shell dumpsys window | grep -m1 mCurrentFocus
# Window{... u0 com.mitv.screensaver/com.mitv.screensaver.RenderActivity}
```

App 内检测走 `UsageStatsManager.queryEvents` 的 `MOVE_TO_FOREGROUND`，**必须增量查询**：

- 错误做法：只查最近 20 秒 —— 屏保停留超过 20 秒后窗口里就没有事件，会误判成「不在屏保」
  （实测：角标在屏保第 20 秒自己消失）
- 正确做法：首轮查 24 小时，之后只查 `lastQueryEnd → now`，把最近一次前台包名持久化在
  变量里；查询窗口里没有新事件时沿用上次结果

```java
long begin = (lastQueryEnd == 0) ? now - 86400000L : lastQueryEnd - 2000L;
lastQueryEnd = now;
UsageEvents ev = usm.queryEvents(begin, now);
while (ev.hasNextEvent()) { ev.getNextEvent(e);
    if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) lastTop = e.getPackageName(); }
boolean isScreensaver = "com.mitv.screensaver".equals(lastTop);
```

需要授权：`adb shell appops set <pkg> GET_USAGE_STATS allow`。

## 主动触发 / 退出屏保（调试用）

```bash
adb shell am broadcast -a com.duokan.intent.action.show.screensaver
adb shell am broadcast -a com.duokan.intent.action.exit.screensaver
# 偶尔第一次不生效，重发一次；也可以 input keyevent 3 回桌面
```

## 在屏保上叠字（悬浮窗）

```java
WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        WRAP_CONTENT, WRAP_CONTENT,
        2003, // TYPE_SYSTEM_ALERT
        FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
lp.gravity = Gravity.TOP | Gravity.RIGHT;
wm.addView(view, lp);
```

- 权限：`SYSTEM_ALERT_WINDOW`（`appops set <pkg> SYSTEM_ALERT_WINDOW allow`）
- 只在屏保时 `addView`，切走就 `removeView`（用窗口数 1 ↔ 0 验证）
- 窗口不可聚焦、不可触摸，不会挡遥控器操作

### 字体：跟原屏保同款

| 用途 | 来源 |
|---|---|
| 中文 | `/system/fonts/MILanPro_Normal.ttf`（小米兰亭），`Typeface.createFromFile` 直接用 |
| 数字 | 从 `com.mitv.screensaver` 的 APK 里抽 `assets/fonts/MIUI_EX_Light_0.ttf`（息屏时钟同款细体），打进自己 APK 的 assets 用 `Typeface.createFromAsset` |

混排（中文一种字号、数字另一种）用横向 `LinearLayout` + 多个 `TextView` 最简单，
`baselineAligned` 自动对齐基线，不需要 Spannable。

## 坑

- 屏保 Activity 可能被系统按内存压力杀重启，检测逻辑要能反复 attach/detach
- 不能靠截图判断「是不是屏保」——截图只能拿到当前画面；要看焦点 Activity
- 屏保期间背光全开，长时间挂角标不会额外耗电，但如果要"熄屏显示"需要另改 `screen_off_timeout`

## 扩展：角标第二行显示遥控器电量（2026-11 实测）

需求：屏保右上角「开机 N 天 HH:MM」下面再加一行「遥控器 NN%」。

- 数据源与 `scripts/remote-battery.sh` 同源：`Settings.Global` 的 `bluetooth_hid_battery_<MAC大写>`
- App 内**可以直接读**：`Settings.Global.getString(cr, key)`，targetSdk 22 在该机型实测可读（不需要
  WRITE_SECURE_SETTINGS 之外的新权限；写仍要 pm grant）
- MAC 从已配对设备拿：`BluetoothAdapter.getDefaultAdapter().getBondedDevices()`（清单里加
  `<uses-permission android:name="android.permission.BLUETOOTH"/>`，普通权限装即授）；
  `bluetooth_hid_battery_count_<MAC>` 是噪声键，跳过
- **`content://settings/global` 不支持 LIKE 查询**：会抛
  `IllegalArgumentException: Supported SQL: uri content://some_table with query name=? ...`
  只能按 `name=?` 单键读，或遍历已配对设备拼键名
- 渲染：**别用嵌套 LinearLayout 混排字体**（实测 vertical 里套 horizontal 行时，第 4 个 TextView
  测量宽度恒为 0）。改用自绘 `View + Canvas.drawText`：
  按段 `Paint.measureText` 累加宽度 → 从 `getWidth()-total` 右对齐画；
  基线用各段 `FontMetrics` 的 `max(ascent)/max(descent)` 对齐；
  `Paint.setLetterSpacing(0.1f/0.03f/0.06f)` 保留原字距。
  实测自绘与原 TextView 混排版逐像素一致（字高 27/48/31 与列位置差 ≤1px）

## 扩展：角标第三行「今日使用长条」（v1.2 起，v1.3 修分类）

需求：屏保角标上直接看到「今天电视用了多久」，按内容类型上色。

| 项 | 值 |
|---|---|
| 位置 | 角标第三行，开机/电量两行文字下方，右对齐 |
| 尺寸 | 220dp × 8dp，圆角 4dp；`0:00 → 24:00` 线性映射（`UsageDay.DAY_MS`） |
| 绘制 | 2x 离屏位图（圆角底槽 + 整高色块 + 圆角裁剪）再降采样，避免硬缝；每段最小 2px（位图 2x = 屏幕 1px） |
| 颜色 | 视频 `#F43098` / 音乐 `#00D3BB` / 儿童学习 `#FFB74D` / 桌面系统 `#90A4AE` / 其他 `#607D8B` |
| 现在位置 | 白线 `0x66FFFFFF`，上下各出头 3dp（`BAR_TICK_DP`） |
| 不写文字 | 颜色就是图例（用户定的规则） |

**数据从哪来**（`HudService`）：

- 每 1s 采样：`UsageStatsManager.queryEvents` 增量拿 `MOVE_TO_FOREGROUND`（包名+类名）+ `PowerManager.isInteractive()`
- 只记「人在看」：屏保、熄屏、关机都不记 → 长条上自然留空
- 落盘 `SharedPreferences("hud")` 的 `usage_json`（当天 0 点起的毫秒偏移 + 类别码）；每 60s 或切空闲时保存
- 服务重启/升级后 `backfillToday()` 用 UsageEvents 重建 `[上次记录结束, 现在]`；24h 窗口足够覆盖「今天」
- `UsageDay.SCHEME` 是类别编码版本：**改分类规则必须 +1**（v1.3：2 → 3），否则旧数据会按新颜色画错；bump 后当天数据作废，靠 backfill 重建

**分类的关键**（v1.3 修复）：

- 看动画/看片时 PatchWall 播放器**不切 Activity**，前台一直是 `com.mitv.tvhome.DetailsActivity`（包名 = 桌面）。只按包名分类会记成 `SYSTEM` 灰蓝 → 长条上「看不到看动画的片段」。
- 解法：`UsageEvents.Event.getClassName()`——api-28 `android.jar` 里有这个公开方法，**本机 Android 9 实测可用**（拿得到 `DetailsActivity` / `MainActivityUserMode`）；`dumpsys usagestats` 里也带 `class=`，可先用 adb 核对。
- 实现：`Categories.of(pkg, className)`——包名分类为 SYSTEM、pkg 是 tvhome 时，类名含 `Detail`/`Play`/`Video`/`Theater` → 改记 `VIDEO`；类名是插件宿主 `ProxySingleTaskActivity`（2026-11 实测：PatchWall 内嵌酷狗音乐，媒体会话 `Kugou_Media`）→ 改记 `MUSIC`；取不到类名退回按包名（老固件安全）。改映射要 bump `Categories.RULES_REV`，服务升级后只重建「今天」的分段。
- 近似性：详情页停留 ≠ 一定在播片（选集、看简介也算视频粉）。对「孩子看动画」场景够用；要精确播放态见 `references/operations/usage-stats.md` 末尾。

**坑**

- **别按包名判断「没看视频」**：详情页内嵌播放时 usagestats 只有 `DetailsActivity`，`com.mitv.mivideoplayer` 永远不进前台。
- **24h 条上 10 分钟 ≈ 3px**（1080p、220dp）：短时段极小，10 秒级会被 minW 吞成 1px。别说「没记录」，先截屏取色再下结论：找 bar 行 → 按颜色 run 取 RGB → 映射回时间（v1.3 的粉是 `244,48,152`）。
- **服务重启首轮 tick 不能直接 append**：`lastTickWall == 0` 时没有「上一个时间点」，直接累计会把 `00:00 → 现在` 记成一整段。首轮只记时间戳，空档交给 `backfillToday()`。
- **App 没开 `debuggable`**：`run-as com.dsh.tvhud` 读不到 `shared_prefs/hud.xml`，想核对长条数据只能看日志（`TVPILOT` tag）+ 截图取色，别在 `run-as` 上耗时间。
- **部署后第一次 `show.screensaver` 可能不 attach**（实测延迟约 1 分钟）：`exit.screensaver` → `show.screensaver` 再来一次，`logcat -d | grep TVPILOT` 看到 `HUD attached` 即可。
- **升级用 `app-deploy.sh`**（`install -r` 保数据，最稳）；`app.sh install` 只是它的薄封装。签名不匹配时安装会失败，需要先卸载再装——卸载会清 prefs/无障碍/授权，尽量别走到这一步。

## 首页菜单：App 启动页（v1.9 起）

打开 tv-pilot 先到首页菜单（`com.dsh.tvhud/com.dsh.tvhud.home.HomeActivity`），**不再直接进详情页**——功能入口都长在菜单里，加功能只需要改 `HomeActivity.ITEMS`。

- 启动组件：`HomeActivity`（Manifest 里 `MAIN` + `LEANBACK_LAUNCHER` + `LAUNCHER`）；`MainActivity` 变成子页面（`exported=true`，仍可 `am start -n com.dsh.tvhud/com.dsh.tvhud.usage.MainActivity` 单独调试）
- 条目（v1.17 起「今日使用 / 使用记录」合并为一个入口）：`01 使用记录`（`ACTION_RECORD`）→ 详情页今天，遥控器 ←→ 就地按天回看历史；`02 使用报告` → 周/月/年报；`03 我的宠物`；`04 家长设置`（`ACTION_PET_SETTINGS`，右侧 `PIN`，进 PIN 页）
- 加功能：`ITEMS` 里加一行 `new Item("名字", "一句话说明", ACTION_XXX)`（可选第 4 个参数是右侧标签），再在 `open()` 里分发
- 遥控器：上下键换焦点、确认打开；**返回键回菜单**（再按返回退出到桌面/屏保）
- 顶栏：左「tv-pilot」（DIN 10dp 字距 0.22）+ 右「日期 · 今日 36:25」；今日时长取 `HudService.peekUsage()`，服务没起来时回落 `UsageDay.load()`，`onResume` 刷新
- 版式：标题「使用台账」22dp；菜单是一张浮在中间的表（上下各一条发丝线、行间一条），行高 60dp，行内 序号(10dp DIN) / 名称(15dp) / 一句话说明(10dp 第三字色) / 右侧箭头或小标签（如 `PIN`）；焦点 = 整行 `0x1FFFFFFF` 底 + 行首 3×22dp 白色色脊（不再用彩色，颜色只留给数据）

## 详情页：时间轴 + 分段明细（v1.4 起；v1.5 视觉改版；v1.9 起从首页菜单进）

屏保角标只是摘要；打开 **tv-pilot** →「使用记录」是完整详情页（组件 `com.dsh.tvhud/com.dsh.tvhud.usage.MainActivity`，从菜单进有返回栈，`am start -n com.dsh.tvhud/com.dsh.tvhud.usage.MainActivity` 也能直接调试）：

- 顶部：日期 / 已开机时长，右侧「今日合计」（与长条同一份数据求和）
- 分类小计：视频 / 音乐 / 儿童学习 / 桌面系统 / 其他，按类别颜色列时长
- 时间轴：0:00→24:00 大号长条，3 小时一档刻度，白线是「现在」
- 明细：每段「起 – 止 / 时长 / 类别」，行可获焦，遥控器上下滚动
- 明细列：时间 / **看了什么** / 时长 / 类别。视频时段按「每集开始时间」切成多行，显示 `片名 第N集`；没有片名靠前的那段视频显示「—」，非视频行的标题列留空
- **相邻同类行自动合并（v1.10）**：类别相同、片名/集数相同、中间空档 ≤5 分钟的行并成一行（屏保/熄屏的空档会让同一类使用碎成很多小行）；时间列显示首尾跨度，**时长列是各段实际前台时长之和**（空档不计），所以合并行可能「跨度 30 分钟、时长 3 分钟」，这是正常的
- **每分钟刷新**（v1.11 起；之前是每秒——秒级重建列表会让遥控器焦点被打断）；数据来自 `HudService` 内存快照（`HudService.peekUsage()` / `peekPlays()`），服务没起来时回落读 prefs；刷新时按「焦点行起始时间」找回焦点、并恢复滚动位置；「共 N 段」按显示行数算（拆分/合并后行数与 UsageStats 段数不同）
- **历史**：遥控器左右键按天翻（最远 365 天，不能翻到未来）；非今天的日期从归档文件读，表头变「使用记录 / 当日合计」，时间轴的「现在」游标隐藏，没数据时显示「这一天没有记录到使用时间」

**按天归档（v1.8）**

- 路径：`/sdcard/Android/data/com.dsh.tvhud/files/history/2026-11-05.json`（App 外部私有目录，adb 可读，卸载即清）
- 内容：`{"day":当天0点,"segs":[[起,止,类别],...],"plays":[[时间,状态,片名,集,总集,单集时长],...]}`，结构化数据，**每天约 2KB**
- 写入时机：服务启动、每 60s、跨天前（写前一天）、服务销毁；`am broadcast -a com.dsh.tvhud.ARCHIVE_NOW` 可强制写一次
- 清理：服务启动时删掉超过 `HistoryStore.KEEP_DAYS`（365 天）的归档
- 容量口径：结构化记录一年约 **2–6MB**；**不要存原始 miothost**（约 1MB/小时，一年好几 GB）

**播放内容从哪来（v1.7）**

- 片名/集数只有一个来源可靠：米家上报日志 `/sdcard/miio/miothost.txt`（行内转义 JSON，含 `video_name` / `ci` / `total_ci` / `total_duration`）；App 需要 `READ_EXTERNAL_STORAGE`（targetSdk 22 → 装即授权，实测 granted=true）
- `HudService` 每 5s 增量读该文件（只处理完整行，文件被替换「开头变了」会自动从头重扫），按「片名+集号」去重，落 prefs（`play_json`/`play_day_start`）
- **这个日志会频繁轮转**（实测一天里换过好几次，旧内容直接没了）——App 必须在运行时就抓，不能事后补；被轮转掉的部分可把备份日志放到 `/sdcard/Android/data/com.dsh.tvhud/files/play-import.txt`，`am broadcast -a com.dsh.tvhud.RELOAD_PLAYS` 触发导入（服务启动时也会自动导入一次）
- 播放器/媒体库不同，`video_name` 可能是「集标题」而不是剧名；用 `ci/total_ci` 标出第几集/共几集
- 详情页那行显示的是「每集第一次出现的时间」，同一天重看不重复列

**视觉语言（v1.5，改字号/美化时按这套来，别回到"大字报"）**

| 项 | 值 |
|---|---|
| 底色 / 主字 / 次字 / 三字 | `#0E1114` / `#E9EEF3` / `#9AA7B2` / `#5F6B76` |
| 发丝线 / 焦点底 | `0x288A97A4`（1px）/ `0x18FFFFFF` |
| 字体角色 | 合计数字＝`MIUI_EX_Light`（assets，26dp，与屏保同款）；时间码/英文标签＝`DINPro_Medium`（assets）；中文＝系统兰亭；字号克制：合计 26dp、明细 10dp、其余标签 8–10dp |
| 版式 | 页边距左右都 64dp；但列表行内再留 padding（左 12dp / 右 16dp），类别列不贴行尾（参考左侧时间列）；顺序：眉标(10dp 字距0.18) → 合计(26dp)+分类图例(9dp) → 发丝线 → 时间轴(条高10dp，3h 刻度+1h 短刻度+现在游标) → 发丝线 → 列头(9dp) → 明细(10dp 字、28dp/行、行首 3×14dp 色脊，色脊垂直居中且高度对齐文字墨迹) |
| 首页菜单（v1.9） | 页边距左右 64dp；眉标 tv-pilot(10dp 字距 0.22) → 标题「使用台账」(22dp) → 垂直居中的菜单表（上下发丝线 + 行间发丝线，行高 60dp）→ 右下角版本号(9dp)；焦点整行底 `0x1FFFFFFF` + 3×22dp 白色色脊 |
| 颜色纪律 | **颜色只做数据**：时间轴色块、分类小圆点、行首色脊；正文/标签一律中性色 |
| 列宽 | 时间列自适应（weight=1），时长 90dp 右对齐，类别 150dp 右对齐 |

**版式坑（v1.5 踩过）**

- `MIUI_EX_Light` 数字细，只用在合计/大数字；明细时间码太多细体会发虚，用 DIN Medium
- **DIN Pro 的 descent 偏小**：`setIncludeFontPadding(false)` 会把数字底部裁掉；明细行保持默认 font padding，行高 26dp
- **中文 + en dash 混排**（如「起 – 止」）在小字号 DIN 下会因 fallback 字形撑高被裁；列头直接用纯中文「时间/时长/类别」
- 文字尺寸统一用 **dp**（`ts()`），不用 sp：系统 fontScale 偏大时 sp 会撑爆页面
- 行用固定 28dp 高 + 1px 发丝分隔；行内 padding 左 12dp / 右 16dp、色脊 3dp + 间距 10dp，列头左内边距 24dp 才能和行首对齐；列头右边距同样 16dp，类别列和行内数据才对齐
- 色脊高度要**接近文字墨迹高度**（10dp 文字配 14dp 色脊），太短会看起来像贴顶；靠行内 `CENTER_VERTICAL` 居中即可（实测墨迹与色脊中心差 ≤1px）
- **列表视口高度要吸附成整数行**（`snapListHeight()`：layout 后实测行距 `childAt(2).getTop() - childAt(0).getTop()`——dp 取整常差 1px，别硬算 `dp(28)+1`——`rows = avail / pitch` 后设固定高度、`weight=0`），否则滚动后边缘会切掉半行字；同时 `ScrollView.setFocusable(false)`，把焦点交给行，滚动量才会按整行对齐
- 底部提示行（"上下键滚动 · 返回退出"）用户明确不要，已删除；页面底部只留 22dp 空白
- 菜单垂直居中靠「标题后 + 菜单后各一个 weight=1、0 高的占位 View」；多留一个占位会把菜单往下推、少留会贴顶（v1.9 踩过两次，改完数一下占位个数）
- **空日期不显示空状态**：`shownSignature` 初值必须是哨兵（`"\u0001"`）而不是 `""`——空一天 rows 签名本来就是 `""`，用空串初值会「相等 → 跳过渲染」（v1.9 修）
- **行重建后要重新吸附视口**：`snapListHeight()` 只在 `buildUi` 后跑一次不够，从菜单直接进「有内容的日期」时列表是后来才填的，底部会切半行；在 `rebuildList()` 末尾再 `scrollView.post(snapListHeight)`（v1.9 修）
- **刷新会重建列表 → 焦点/滚动丢失**：`rebuildList()` 里先记下焦点行（`row.setTag(Row)`）和 `scrollView.getScrollY()`，重建后 `findRowAt(起始时间)` 找回焦点再恢复滚动；刷新间隔改成 60s（`REFRESH_MS`），秒级刷新会让人没法用遥控器上下浏览（v1.11）
- **字号实际是代码值的约 2 倍**：`ts(v)` 返回 px，但 `TextView.setTextSize(float)` 按 sp 解释 → 本机实际 ≈ v×2dp。改字号以截图为准，别看代码里的数字猜

实现要点 / 坑：

- 页面文字尺寸用 **dp**（`MainActivity.textSize()`）而不是 sp：系统 fontScale 放大时 sp 会把页面撑爆、明细被挤没（本机实测 fontScale 偏大）
- Activity 必须用 `@android:style/Theme.Black.NoTitleBar.Fullscreen`，否则默认主题会顶一条 ActionBar 写着 "tv-pilot"
- 明细行要给 `LayoutParams(MATCH_PARENT, 26dp)`；`LinearLayout` 里 width=0+weight 的子 View 如果不给 row 明确 MATCH_PARENT，会按内容量算错、类别列换行
- **详情页自己的时间不计入使用**：`HudService.isUsing()` 与 `replayEvents()` 都跳过 `getPackageName()`（否则看明细的每一秒都会被记成「其他」）
- 重置当天数据：`adb shell am broadcast -a com.dsh.tvhud.RESET_USAGE` → 服务清空 `UsageDay` 并用 UsageEvents 重建（换口径/误记后用；别用卸载清数据）
- 调试启动：屏保黑屏、`mCurrentFocus=null` 时裸 `am start` 可能没把页面带到前台，用 `am start -W -n ...`，再 `dumpsys window | grep mCurrentFocus` 确认
