---
profile: <brand>-<model-slug>
brand: <brand>
model: <model>
match: ["<ro.product.model>", "<ro.product.device>"]
sdk: <sdk>
verified_at: <YYYY-MM-DD>
verified_by: <github-handle>
capabilities:            # verified 实测可用 / partial 部分可用 / unknown 未验证 / unsupported 确认不可用
  usage_stats: unknown
  screensaver: unknown
  child_mode: unknown
  adb_keepalive: unknown
  app_build: unknown
  remote_battery: unknown
---

# <brand> <model> 设备档案

> 采集时间 / 固件 / 采集者：<填写>。
> 写法：只写**实际测过**的东西；没测的保持 `unknown`，没人会因为"没测"怪你，但猜错会误导别人。
> 隐私：不要写真实 IP / MAC / 序列号 / Wi-Fi 名 / 账号，用 `<占位符>` 或示例值（`lint` 会拦）。

## 设备身份

| 项 | 值 |
|---|---|
| 型号 | |
| 品牌 | |
| codename | |
| Android / SDK | |
| 固件 | |
| fingerprint | |
| 屏幕 | |
| 默认桌面 | |

## ADB 接入与坑

- 开关路径：
- 打开后是否需要重启：
- 重启后是否复位 / 延时复位：
- 授权框行为：
- 其它：

## 桌面与屏保

- 桌面包名：
- 屏保组件（`包名/Activity`）：
- 进入屏保的条件：
- 屏保上叠字的可行性（悬浮窗 / 无障碍 / 权限）：

## 使用统计来源

- `dumpsys usagestats` 是否可用：
- 亮屏 / 熄屏事件：
- 播放内容来源（品牌自带日志 / 媒体会话 / 其它）：
- 坑：

## 儿童模式

- 宿主包名 / Activity：
- 配置所在（`settings` 键 / 数据库）：
- 实测过的限制方式：
- 已知绕过方式（决定能不能依赖它）：

## 可禁用包 / 广告组件

- 广告 / 统计包（可逆禁用候选）：
- 风险高、别动的包：
- 恢复方式（`cmd package install-existing`）：

## 已知坑

-

## 证据

每条证据 = 命令 + 关键输出摘录 + 日期；至少 3 条。

```
$ adb shell getprop ro.product.model
MiTV-NSOP0            # 2026-11-04
```
