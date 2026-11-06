# 开机自启：第三方 App 在小米电视上怎么做

用户问「电视重启后能不能自动拉起某个 App」「我写的 App 为什么收不到 BOOT_COMPLETED」
「装了 App 但重启后就不见了」时读这份。

## 结论先说

MIUI TV 在**开机 PackageManager 扫描阶段会把所有第三方应用置为 `stopped=true`**，
而 stopped 状态的应用**收不到任何广播**——`BOOT_COMPLETED` 永远不派发。

不 root 能走通的路只有两条：

1. **无障碍服务**（推荐，本机实测可用）：系统在开机时**主动绑定**已启用的无障碍服务，
   不走广播，因此绕开 stopped 拦截
2. **让 App 成为系统会直接启动的角色**：默认桌面（会顶掉 PatchWall，代价大）

## 先确认是不是这个坑

```bash
# 单个包
adb shell dumpsys package <pkg> | grep -o 'stopped=[a-z]*'

# 全体第三方应用（MiTV-NSOP0 实测 18/18 全是 stopped=true，不是你的 App 的问题）
adb shell 'for p in $(pm list packages -3 | sed "s/package://"); do
  echo "$p $(dumpsys package $p 2>/dev/null | grep -o "stopped=[a-z]*" | head -1)"; done'

# 开机扫描阶段的证据（时间戳可能因 RTC 未同步而偏）
adb shell logcat -b all | grep 'PackageManager: stop:'
```

判定：`stopped=true` 且收不到广播 → 就是这个坑；如果 `stopped=false`，
先用 `logcat -b all | grep <pkg>` 确认 receiver 本身有没有被调用。

## 方案 A：无障碍服务当开机入口（推荐）

### App 侧

声明一个**最小**无障碍服务，不做任何无障碍操作，只在被绑定那一刻拉起自己的 Service：

```xml
<service
    android:name=".BootAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
</service>
```

```java
public class BootAccessibilityService extends AccessibilityService {
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        startService(new Intent(this, HudService.class));      // 拉起真正的业务服务
        // 开机初期系统可能还在抢前台，延迟再补一次
        new Handler().postDelayed(() -> startService(new Intent(this, HudService.class)), 8000);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
}
```

不写 `res/xml` 配置也行：在 `onServiceConnected()` 里 `setServiceInfo(new AccessibilityServiceInfo())`
用运行时信息，省掉资源编译。

### adb 侧（追加，不能覆盖原值）

```bash
cur=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
adb shell settings put secure enabled_accessibility_services "$cur:<pkg>/<pkg>.BootAccessibilityService"
adb shell settings put secure accessibility_enabled 1
```

实测（MiTV-NSOP0）：重启后 **~30 秒内**绑定并拉起进程。

### 两个必踩的坑

1. **`adb install -r` 和 `am force-stop` 都会把该 App 的无障碍条目从启用列表删掉**。
   正确顺序：`install` → `force-stop` → **最后**再写条目。写反了就是白写，
   而且下次重启必然收不到绑定。
2. 列表里的其他服务要**原样保留**（本机原有 karaoke 与 voicecontrol 两个服务），
   拼接前先 `settings get` 备份原值。

### App 侧自愈（推荐）

`pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` 在 MiTV-NSOP0 上实测可用
（不是所有机型都行）。拿到后 App 可以每 10 秒检查：

- `enabled_accessibility_services` 里自己的条目被删了 → 补回
- `adb_enabled` 被小米关成 0 → 写回 1（见 `references/operations/enable-adb.md`）

## 方案 B：让系统直接启动你

- 成为默认桌面（`HOME`）会顶掉 PatchWall，家庭用户不要这么做
- 本机没有「开机启动某应用」的用户设置项（设置/桌面/电视管家的资源里都没有这个入口）

## 已排除的路（别浪费时间）

| 方案 | 为什么不行 |
|---|---|
| `BOOT_COMPLETED` 广播 | stopped 状态收不到任何广播 |
| JobScheduler `setPersisted(true)` | force-stop 取消任务，开机不恢复 |
| `AlarmManager` 定时 | 不跨重启，force-stop 会清 |
| AOSP `DreamService` 屏保 | 本机 `screensaver_enabled=0`、组件指向未安装的 deskclock，标准通道未启用 |
| 其他系统绑定服务（通知监听等） | 同类思路可行，但无障碍副作用最小、最稳 |
| root / 刷机 / 改 /system | 本机 user 版、`ro.debuggable=0`，不碰 |

## 验证清单

```bash
adb shell ps -A | grep -i <pkg>                                  # 进程在
adb shell dumpsys activity services <pkg> | grep ServiceRecord  # 服务在
adb shell settings get secure enabled_accessibility_services | grep <pkg>
adb shell logcat -d | grep -E "Start proc.*<pkg>|BootAccessibilityService"
```

重启后 `uptime` 很小（<60s）且进程已经在，才算真的自启成功。

## 参考

- 同代机型（MiTV-ASTP0 / Android 9）的完整实测：
  <https://zhuanlan.zhihu.com/p/2082145496689283146>（含「广告开关」`boot_ad_switch` 等顺带发现）
- 社区公认「不 root 门都没有」的原帖：<https://www.znds.com/tv-1266763-1-1.html>
