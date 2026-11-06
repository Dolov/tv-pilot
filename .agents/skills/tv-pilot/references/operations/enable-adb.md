# 开启电视 ADB 调试

## 小米 / Redmi（MIUI TV / PatchWall）

**路径一（新固件）**：`设置 → 账号与安全 → 安全 → ADB 调试 → 开启`

**路径二（老固件，找不到开发者选项时）**：
1. `设置 → 关于`
2. 光标停在「产品型号」上，连按「确定」5~7 次
3. 提示「已进入开发者模式」
4. 返回上一级 → `开发者选项` → `ADB 调试` → 开启

**路径三（部分固件）**：`设置 → 通用设置 → 开发者选项 → ADB 调试`

### 三个必须记住的事实

1. **开关打开后通常要重启电视**，5555 才开始监听。这是最常见的"明明开了却连不上"。
2. 部分固件**重启后把 ADB 开关复位**，需要重新开。
3. 首次连接电视会弹授权框，必须用遥控器按「允许」，勾「一律允许」可少一次弹窗。

### 验证

```bash
scripts/adb.sh connect <IP>:5555
scripts/adb.sh devices
# 期望: <IP>:5555  device
# 如果 unauthorized → 电视上按允许
# 如果 Connection refused → 重启电视/确认开关
```

## 重启后 ADB 会延时复位（MiTV-NSOP0 实测）

- 开机后 5555 会**短暂可用**（约前 30 秒），随后被 MIUI 关掉，变成 `Connection refused`
- 复位同时也**清掉授权密钥**，需要在电视上重新按「允许」；勾了「一律允许」也不一定跨重启保留
- 调试技巧：重启后尽早连（`scripts/ensure-tv.sh` 的 L0/L1 很快），能抢到开机早期状态
- `adb devices` 里设备突然消失、报 "device not found" 但端口还开着：
  `adb kill-server` 后重新 `start-server` + `connect` 即可恢复

### 让 App 自己守住开关（可选，本机实测可用）

```bash
adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS
```

拿到权限后，App 每 10 秒检查并把 `adb_enabled` 写回 1，重启后就不用再手动开 ADB。
注意 5555 是明文端口，长期开着要自己权衡。

## 其它品牌（供参考）

- **索尼 / TCL / 海信 / 创维 / 康佳**：一般是 `设置 → 关于 → 版本号/产品型号` 连按 5~7 次，再去开发者选项开 ADB 调试，端口同为 5555
- **华为/荣耀智慧屏**：`设置 → 系统 → 开发者选项`；没有就先连点版本号 7 次
- **三星（Tizen）/ LG（webOS）不是 Android**，没有 ADB，用各自的 sdb / ares 工具，本 skill 不适用
- **Roku / 海信 VIDAA**：也非 Android，没有 ADB

## 找不到开关时

拍一张电视设置界面照片，或者让用户报型号，去 `references/devices/` 里看有没有同型号记录，再按品牌查具体路径。不要凭猜测让用户乱点。
