# 新机型接入（Porting）

目标：把一台**没见过的电视**从「能连上但不知道能不能信」变成一份可提交的设备档案。
原则：**先只读、后写**；每一步都留证据；没有实测的能力一律保持 `unknown`。

## 0. 先判断现状

```bash
bash scripts/ensure-tv.sh -v        # 能不能连上？卡在 unauthorized / offline / refused 哪一步
bash scripts/identify-tv.sh         # matched / similar / unknown
```

结果处理：

| 结果 | 怎么做 |
|---|---|
| matched | 正常用；档案里 `unknown` 的能力按「先只读、写操作前确认」处理 |
| similar（同品牌不同型号） | 品牌级经验可参考，但**包名/键值/坑都不保证**；按本文补齐这份型号的档案 |
| unknown | 只能走只读路径；本文从头走一遍 |

**unverified 机型的红线**：不要禁包、不要 `settings put`、不要装 App、不要开发屏保角标。
这些动作依赖实测过的档案，猜错可能让电视黑屏、进不去设置、开机起不来。

## 1. 收设备身份（只读）

```bash
bash scripts/probe.sh              # 型号/固件/包列表/儿童模式键值一次取回
adb shell getprop | grep -E 'product.(model|brand|device|name)|build.version.sdk|build.fingerprint'
adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
```

把这些写进档案 frontmatter：`brand`、`model`、`match`（`ro.product.model` 与 `ro.product.device` 原值）、`sdk`。

## 2. ADB 接入的差异

- 开关路径在哪（设置里的层级各品牌不同）
- 打开后是否需要重启，重启后是否复位、是否有「开机 30 秒能连、之后 refuse」的延时复位
- 授权框行为（是否可勾「一律允许」）

写进档案「ADB 接入与坑」；通用排查仍走 `scripts/ensure-tv.sh -v`。

## 3. 桌面 / 屏保

```bash
adb shell dumpsys activity activities | grep -E 'mResumedActivity|mFocusedActivity'
adb shell dumpsys usagestats | head -50          # 看 MOVE_TO_FOREGROUND 的包名
adb shell settings list secure | grep -i dream   # 屏保相关（不同 ROM 键名不同）
```

判断屏保组件：等电视进入屏保后 `dumpsys window | grep mCurrentFocus`，或在使用事件里看进入屏保前后的前台包名。
**不要**假设所有电视都用 `DreamService` 或某个固定包名。

## 4. 使用统计来源

通用基础是 `dumpsys usagestats`（亮屏/熄屏事件、前台包名与 class 名），这部分跨品牌差异小；差异在**播放内容**：

- 国产 ROM 常有自家日志（如小米的 `/sdcard/miio/miothost.txt`）或多播服务日志
- 原生 Android TV 一般只有 `dumpsys media_session`（停止后消失，不能事后回溯）

在档案里写清楚：哪些来源可用、能回溯多久、字段格式。

## 5. 儿童模式

```bash
adb shell settings list system | grep -iE 'child|kid|play_time|parent'
adb shell settings list secure | grep -iE 'child|kid|parent'
```

不同品牌实现差别很大（小米在屏保应用里、有的在独立"儿童模式" App、有的没有）。
只记录**实测确认**的键名与行为，并写清能否被孩子绕过——这比"有儿童模式"重要。

## 6. 可禁用包 / 广告组件

先 `adb shell pm list packages` 摸清成分，再逐个判断：

```bash
adb shell pm list packages -3                          # 第三方 + 预装到 /data 的
adb shell cmd package dump <pkg> | head -20            # 看是不是系统关键组件
adb shell pm uninstall --user 0 <pkg>                  # 可逆禁用（推荐先 --user 0）
adb shell cmd package install-existing <pkg>           # 还原
```

档案里区分「可安全禁 / 先确认 / 别动」，并记录还原命令。动桌面、设置、系统 UI 的包必须单独标红。

## 7. 写档案并校验

```bash
bash scripts/device-profile.sh init          # 从 _template.md 生成骨架（自动带当前设备信息）
# 按模板填写
bash scripts/device-profile.sh lint          # 必填字段 + 章节完整 + 证据 + 脱敏
bash scripts/device-profile.sh index         # 重建 references/devices/README.md 的支持矩阵
```

### 7.1 只想自己用：本地档案覆盖层

不必改仓库。`device-profile.sh init --local` 会把档案写到 `$TVPILOT_DEVICES_DIR`（默认
`~/.config/tv-pilot/devices/`），`identify-tv.sh` 会同时扫描仓库档案和本地档案（精确匹配优先），
`device-profile.sh lint` 两者都校验，`index` 只重建仓库支持矩阵。

```bash
bash scripts/device-profile.sh init --local --name hisense-a6g
bash scripts/device-profile.sh lint
bash scripts/identify-tv.sh --json     # profile_source 应为 local
```

## 8. 品牌操作差异用 provider 表达

档案是数据（身份、能力、坑），provider 是代码（这台机器该去哪个键取电量、播放日志在哪、
屏保包名是什么）。通用脚本不写机型 if/else，而是调用 provider 函数：

```bash
scripts/providers/<slug>.sh      # 实现需要的 tvp_* 函数；缺省回落 _default.sh
```

契约、函数清单、示例见 `docs/providers.md`。只有确实需要不同命令/解析逻辑时才加 provider；
纯事实（包名、键名、能力）继续写档案 frontmatter 或正文。

## 9. 提交

- 一个 PR 只加/改一份档案（外加 `index` 生成的矩阵；有 provider 时一并提交）
- 证据至少 3 条（命令 + 输出摘录 + 日期）
- 不要提交固件、APK、厂商反编译产物
- 提交前跑 `make check && make test`
- 流程与脱敏红线见仓库根 `CONTRIBUTING.md`
