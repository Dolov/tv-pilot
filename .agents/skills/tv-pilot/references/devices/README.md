# 设备档案（Device Profiles）

每个型号一份档案，记录**经过实测**的机型差异：ADB 接入、桌面/屏保、使用统计来源、儿童模式、可禁用包、已知坑。
通用方法论在上一层 `references/`，这里只放机型事实。

## 支持的设备

<!-- DEVICE-MATRIX:BEGIN -->

| 档案 | 品牌 | 型号 | SDK | 验证日期 | verified 能力 |
|---|---|---|---|---|---|
| [xiaomi-mitv-nsop0](<xiaomi-mitv-nsop0.md>) | xiaomi | MiTV-NSOP0 | 28 | 2026-11-04 | usage_stats screensaver child_mode adb_keepalive app_build remote_battery  |
<!-- DEVICE-MATRIX:END -->

能力等级：`verified` 实测可用 · `partial` 部分可用 · `unknown` 未验证 · `unsupported` 确认不可用。
`unknown` 不代表不能用，只是没验证——代理会按「先只读、写操作前确认」处理，写操作脚本也会被 `scripts/require-profile.sh` 拦住。

## 你的型号不在列表里？

1. `bash scripts/identify-tv.sh` —— 先确认是不是 `unknown`
2. 只读能力（连接、状态、通用探测、通用使用统计）照常可用，**写操作默认会被拒绝**
3. 想把它变成正式支持：按 `references/porting.md` 采集 → `bash scripts/device-profile.sh init` 生成档案 → 填写 → `lint` 通过后提 PR（流程见仓库根 `CONTRIBUTING.md`）
4. 只想自己用、不想提 PR：`device-profile.sh init --local` 写到 `~/.config/tv-pilot/devices/`（`identify-tv.sh` 会合并扫描仓库档案与本地档案，`lint` 两者都校验）

## 档案约定

- 命名：`<brand>-<model>.md`，全小写、连字符，例如 `xiaomi-mitv-nsop0.md`
- 同机应用清单：`<brand>-<model>-apps.md` + `-apps.txt`（快照供 `apps-inventory.sh diff` 用）
- frontmatter 是给 `identify-tv.sh` 匹配用的，**不要删字段**；`capabilities` 只填实测结论
- 模板：`_template.md`（`lint` 会跳过 `_` 开头的文件）
- 只有代码才算「操作能力」时：品牌差异放 `scripts/providers/<slug>.sh`（契约见 `docs/providers.md`），不要把包名/键值写进通用脚本
- 本地档案目录：`$TVPILOT_DEVICES_DIR`（默认 `~/.config/tv-pilot/devices`），适合不想公开的私有机型
