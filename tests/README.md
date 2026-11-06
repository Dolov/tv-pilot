# 测试

三层测试，按「改什么跑什么」选：

| 层 | 目录 | 覆盖 | 命令 |
|---|---|---|---|
| 离线脚本 | `tests/fake-adb` + `tests/run.sh` | skill 脚本的连接阶梯、识别、取数、`--json` 契约 | `make test` |
| 纯逻辑单元 | `tests/java/` + `tests/java/stubs/` | TV HUD 里不依赖 Android 的逻辑：周期边界、日汇总、聚合、洞察、百分比、片名解析、堆叠柱几何 | `make test` |
| 真机冒烟 | `tests/e2e/report-smoke.sh` | 装 APK → 打开报告 → 周/月/年导航 → 日志/崩溃/一致性断言 + 截图 | `make e2e` |
| 全量真机回归 | `tests/e2e/full-regression.sh` | 首页 / 今日使用 / 使用记录 / 报告三页 / 屏保服务 + 归档完整性 | `make regression` |

`make check && make test` 是提交门禁（CI 也跑）；`make e2e` 需要电视，不进 CI。

## 1. 离线脚本测试（fake adb）

用假的 `adb`（`tests/fake-adb`）回放固定输出，验证脚本的连接、识别、取数和 `--json` 契约。
它不能替代真机验证，但能在提交前抓住「输出格式变了 / 解析挂了 / 连接阶梯退化了」这类回归。

```text
tests/
├── fake-adb                 # 可执行：按 adb 参数匹配并输出 fixtures 里的内容
├── fixtures/                # 各脚本的固定 adb 输出（devices / identify / battery / packages …）
└── run.sh                   # 用 fake-adb 跑各脚本并断言关键字段
```

隔离方式：`run.sh` 给每个用例设临时目录（`TVPILOT_ADB_BIN`、`TVPILOT_STATE_FILE`、
`TVPILOT_TOOLS_DIR`、`TVPILOT_DEVICES_DIR`），不污染本机状态。

**加用例**：fixture 放 `tests/fixtures/` → `tests/fake-adb` 加一个匹配分支 →
`tests/run.sh` 加 `test_xxx`，用 `pass_case` / `fail_case` 断言。优先断言 `--json` 的关键字段，
不要断言整段人类可读文本——改措辞不该误报。

## 2. 纯逻辑单元测试（纯 Java，不需要 Android）

TV HUD 是「无 Gradle、直接 javac」的纯 Java 应用，没有 JUnit 基础设施。这里用
`tests/java/stubs/` 下最小的 `android.content.*` / `org.json.JSONArray` 桩，把纯数据/纯计算类
编出来，再用自带断言框架的 `ReportCoreTest` 跑用例：

```text
tests/java/
├── run.sh                       # 编译 + 运行（PATH 里没有可用 javac 时回退 skill 的 .tools JDK）
├── stubs/                       # 只为编译通过的最小 Android/JSON 桩，不会被真的调用
└── src/                         # 用例源码（package com.dsh.tvhud，入口 ReportCoreTest）
```

覆盖：`ReportPeriod`（周/月/年边界、ISO 周序号、跨年标签）、`DayAgg`（跨小时切分、裁剪、主类别）、
`ReportMath`（聚合、旧分类口径降级、百分比、峰值时段、周/月/年洞察、周内平均口径、`checkInvariants`）、
`PlayEntry.parse`（只认当天、有片名的播放事件）、`BarStack`（堆叠柱高度 = 总时长，v1.13 堆叠 bug 回归）、
`ChartAxis`（纵轴档位与轴高）、`UsageDay.snapshot`（跨线程快照）、`HistoryStore`（旧归档缺省 scheme 3、
播放记录越界自检）。

**加用例**：在 `ReportCoreTest` 里加一个 `runCase(...)`；测新逻辑时先把它写成不 import Android
的纯函数（参考 `BarStack`、`ReportMath`），否则离线测试编不过。`javac` 不可用时整层 SKIP，
不影响 `make test` 通过。

## 3. 真机冒烟（需要电视）

```bash
make apk        # 先出 APK
make e2e        # = bash tests/e2e/report-smoke.sh
```

脚本做的事：`app-deploy.sh` 装 APK → 打开「使用报告」→ `↑` 切到年报、`↓` 切回周报、
`←` 翻上一期、`OK` 回本期、`返回` 回首页 → 断言：

1. 三个粒度都真的触发过 `report load WEEK/MONTH/YEAR`；
2. `logcat` 无 `FATAL EXCEPTION`、进程还在；
3. 无 `REPORT_INVARIANT`（`ReportMath.checkInvariants`：`totalMs`/`catMs`/`hourMs` 自洽）
   与 `ARCHIVE_CORRUPT`（归档完整性）告警；
4. 返回键回到 `HomeActivity`；每个阶段截图到 `.build/e2e/` 供人眼复核。

常用参数：`--no-install`（测电视上当前版本，省一次安装）、`--apk <path>`。
断言的日志是**采集时后台过滤 `TVHUD:I`/`AndroidRuntime:E` 落文件**，不是事后 `logcat -d`——
电视系统日志很吵，4MB 缓冲几秒就滚掉。

## 4. 全量真机回归（改动碰多个页面时）

```bash
make apk && make regression
```

比 `make e2e` 覆盖更全，把**所有现有页面**走一遍并断言：

1. 首页：5 个菜单项都在，默认选中第 1 项；
2. 今日使用：合计 / 段数 / 分类图例 / 时间轴 / 明细都渲染；
3. 使用记录：按天回看（落到最近有归档的一天），左右键翻日期不崩；
4. 使用报告：周/月/年三档 + 翻页 + 回本期 + 返回首页；
5. 屏保角标：`HudService` 在跑、无障碍条目在（开机自启依赖）；
6. 数据侧：先发 `am broadcast -a com.dsh.tvhud.ARCHIVE_NOW` 让服务强制写一次当天归档，
   再校验文件可读且自洽（分段非负 / 不越界当天 / 不重叠 / 类别合法 / 播放记录不越界）；
   当天确实没看过电视时归档可能还不存在，这种情况记 `SKIP` 而不是失败；
   全程无 `ARCHIVE_CORRUPT`、无 `REPORT_INVARIANT`、无 `FATAL EXCEPTION`。

截图落在 `.build/regression/`（01-home / 02-today / 03-history / 04-report-*）。

**存储层改动（落盘节奏、归档格式）必须跑这个**，并额外做一次数字对账：
把 `history/*.json` pull 下来，用 Python 独立重算周/月/年总量，与屏幕上截图比对
（±1 分钟），确认改动没有影响统计口径。

## 边界

- 截图不做像素级断言：报告页是纯 Canvas 自绘，`uiautomator` 读不到文字；靠「流程 + 日志 + 人工看图」。
- 真机数据不可控：E2E 只断言结构/日志，不断言具体分钟数。
- 历史归档数据不在仓库里（避免真实观看记录入库），单元测试全部用合成数据。
