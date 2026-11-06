# references 索引

按需读，不要一上来全读。代理入口在 [../SKILL.md](../SKILL.md)，本文件只是目录说明。

## 本机事实

- [local.md](local.md) — **setup.sh 生成**，记录当前电视地址/型号/档案/TV HUD 版本/工具链状态。已 gitignore，换电视后重跑 `bash scripts/setup.sh`。没有这个文件时，`SKILL.md` 会先让你跑 setup。

## 操作方法论（operations/）

| 文件 | 讲什么 |
|---|---|
| [operations/usage-stats.md](operations/usage-stats.md) | 使用统计口径：亮屏、分应用/页面、播放内容来源与可信度 |
| [operations/screensaver.md](operations/screensaver.md) | 屏保识别、悬浮窗角标、今日使用长条（分类/刷新/版式坑） |
| [operations/child-mode.md](operations/child-mode.md) | 小米儿童模式配置键、已知弱点、第三方硬时限方案 |
| [operations/ad-packages.md](operations/ad-packages.md) | 广告/统计/推送组件三级清单与还原命令 |
| [operations/remote-battery.md](operations/remote-battery.md) | 遥控器电量口径、`--deep` 交叉验证、常见误读 |
| [operations/boot-autostart.md](operations/boot-autostart.md) | `stopped=true` 拦截、无障碍服务自启、`install -r` 清理条目 |
| [operations/enable-adb.md](operations/enable-adb.md) | ADB 开关路径、重启才监听、延时复位 |
| [operations/dev-workflow.md](operations/dev-workflow.md) | 改/装自研 App：自检→构建→部署→验证闭环 |
| [operations/build-mini-apk.md](operations/build-mini-apk.md) | 不装 Android Studio 写小 App 的最小结构 |

## 设备档案（devices/）

- [devices/README.md](devices/README.md) — 支持矩阵、能力等级、贡献方式
- `devices/<brand>-<model>.md` — 机型档案（frontmatter 给 `identify-tv.sh` 匹配）
- `devices/<brand>-<model>-apps.md` / `.txt` — 应用清单说明与快照（`apps-inventory.sh` 用）
- [devices/_template.md](devices/_template.md) — 新档案模板（`lint` 跳过 `_` 开头文件）

## 新机型接入

- [porting.md](porting.md) — 从「能连上但不知道能不能信」到可提交档案的完整流程；本地档案可放 `~/.config/tv-pilot/devices/`，不必改仓库。
