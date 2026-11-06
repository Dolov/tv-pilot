# 遥控器电量（只读）

小米蓝牙语音遥控器的电量**不走** `dumpsys battery`（那是电视本机的虚拟电池，永远 100%），而是由蓝牙 HID 服务写进 `Settings.Global`，键名带遥控器 MAC。BleRemoteService 在遥控器重连/按键时刷新。

## 前提

- ADB 已连上（电视亮屏、ADB 开关在）。电视待机时 `5555` 拒绝连接，先遥控唤醒，不是网络问题。
- 遥控器已与这台电视配对过。
- 每台遥控器的 MAC 不同，真实值写进 `.env` 的 `REMOTE_MAC`（可选，便于核对；脚本会自动发现）。

## 快路径（默认，<1s）

```bash
bash scripts/remote-battery.sh            # 自动拿连接，0.3–0.5s
bash scripts/remote-battery.sh --json     # 结构化输出
```

一条 adb shell 同时取回：型号、`Bonded devices`、`bluetooth_hid_battery_*`。**默认不拉 logcat**——整段 `logcat -d` 通过 adb 传回来实测要 15 秒左右，而 settings 键就是最近一次上报值，足够回答“还剩多少电”。

## 深查（--deep，约 10–15s）

需要确认低电量阈值、原始十六进制值、提醒是否已触发时才加：

```bash
bash scripts/remote-battery.sh --deep
```

它按时间窗（`logcat -d -T '<30 分钟前>'`）只取近期日志再过滤，比全量 dump 快，但仍然是最慢的一步。

## 手工步骤（脚本里的原理）

### 1. 找已配对的遥控器 MAC

```bash
adb shell 'dumpsys bluetooth_manager | grep -A8 -i "Bonded devices"'
#   Bonded devices:
#     <REMOTE_MAC> [  LE  ] 小米蓝牙语音遥控器
```

注意：Android 的 toybox `sed` 对 `sed -n "/Bonded devices:/,+8p"` 这种地址范围支持不可靠（实测返回空），用 `grep -A8` 更稳。

### 2. 直接读电量

键名 = `bluetooth_hid_battery_<MAC大写>`，值是 0-100 的百分比：

```bash
adb shell 'settings get global bluetooth_hid_battery_<REMOTE_MAC>'
# 29
```

列全部（多只遥控器/蓝牙输入设备每只一个键，`*_count_*` 不是电量）：

```bash
adb shell 'settings list global | grep bluetooth_hid_battery'
```

### 3. 交叉验证 + 拿型号和低电量阈值（慢，可选）

```bash
adb shell "logcat -d -T '$(date -v-30M '+%m-%d %H:%M:%S.000')' | grep -E 'hid_battery_callback|onCharacteristicRead.battery|Xaomi RC info' | tail -10"
# hid_battery_callback: hid battery=0x1d          ← 0x1d = 29
# BleRemoteService: onCharacteristicRead.battery= 29
# BtDeviceUpdateProcess: Xaomi RC info:battery=0x1d, low_battery_thr=0x19, attribute=0xa0, vendor_model=0x0
```

`low_battery_thr` 是遥控器固件里的低电量阈值（本机实测 `0x19` = 25%）。

### 4. 低电量提醒是否已经触发

```bash
adb shell "logcat -d -T '<时间窗>' | grep -E 'broadcastRcBattery|HID_BATTERY_WARRING'"
# BleRemoteService: broadcastRcBatteryWarring (<REMOTE_MAC>): battery=29
# StatusBar.BluetoothController: mBTReceiver onReceive ...HID_BATTERY_WARRING
```

## 读数口径与坑

- `bluetooth_hid_battery_<MAC>` 是**最近一次上报值**，会一直保留；电视重启后遥控器重连时会刷新，按键上报也会刷新。想立刻刷新就让用户按一下遥控器。
- **别信 `com.xiaomi.mitv.settings` 的 `BluetoothReceiver: HID_BATTERY ... battery=0` 日志**：这是设置 App 解析广播 extra 的 bug，会把已知电量打成 0。以系统服务回调、`settings` 键值、BleRemoteService 为准（实测三者一致，设置 App 单独打 0）。
- 电视本机 `dumpsys battery` 是虚拟值（`mBatteryLevel=100%`、`mIsPowered=true`），和遥控器无关。
- `bluetooth_input_device_priority_<MAC>` 是输入设备优先级（100 = 默认遥控器），**不是电量**；`bluetooth_hid_battery_count_<MAC>` 是计数，也不是电量。
- 没有 `bluetooth_hid_battery_*` 键时：可能遥控器是红外（不上报电量）、没配对，或这台电视从没收到过上报 —— 去电视 `设置 → 遥控器/蓝牙` 看 UI 显示值。
- 部分固件在 `Bonded devices` 只列 MAC 不列名字；名字一般来自 LE 广播（`小米蓝牙语音遥控器`）。
- `hid battery=0x1c` 这类日志是十六进制，28 = 0x1c，别按十进制读。
- **低电量提醒可能早于阈值触发**：2026-11-04 实测 29%（阈值 25%）时就出现了 `broadcastRcBatteryWarring`，固件有 3–4% 的提前量。所以看到 28–30% 时，提醒可能已经弹过了。

## 判定建议

| 电量 | 结论 |
|---|---|
| ≥ 50% | 正常 |
| 25–50% | 偏低，留意（接近 30% 时固件可能已开始提醒） |
| < 25%，或出现 `HID_BATTERY_WARRING` | 建议尽快换电池（多数小米语音遥控器是 2×AAA，按电池仓标注） |

## 实测记录（MiTV-NSOP0，2026-11-04）

- 遥控器 `<REMOTE_MAC>`，小米蓝牙语音遥控器，`pid=0x32ba`，固件 `soft_version=0x18`
- 电量 28%（`0x1c`，20:09）→ 29%（`0x1d`，20:27），`low_battery_thr=0x19`（25%）
- 20:09:43 与 20:27:53 各触发一次 `HID_BATTERY_WARRING` 低电量提醒（电量 28%/29%，阈值 25%，固件有提前提示）
- 电视重启后 6 分钟内遥控器重连，电量在重连时刷新（不是缓存旧值）
- 全量 `logcat -d` 经 adb 传输约 15s；`-T` 时间窗约 4–15s，所以默认快路径不拉日志
