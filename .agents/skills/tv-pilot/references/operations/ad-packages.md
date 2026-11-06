# 小米电视广告/统计/推送组件分级

基于 MiTV-NSOP0（Android 9，固件 22.7.26.1365）实测。其它型号/固件请先 `pm list packages` 确认包存在再动。

## 可安全禁用（不影响日常使用）

| 包名 | 作用 |
|---|---|
| `com.miui.systemAdSolution` | 系统广告引擎（横幅/开屏），核心广告组件 |
| `com.miui.analytics` | MIUI 行为统计 |
| `com.miui.tv.analytics` | 电视端统计 |
| `com.xiaomi.statistic` | 埋点上报 |
| `com.xiaomi.mitv.tvpush.tvpushservice` | 消息推送弹窗 |
| `com.mitv.shoplugin` | 商城插件 |

```bash
# 禁用（可逆，不删系统分区）
scripts/adb.sh shell pm uninstall --user 0 com.miui.systemAdSolution
# 还原
scripts/adb.sh shell cmd package install-existing com.miui.systemAdSolution
```

## 需要先确认（可能影响首页/功能）

| 包名 | 风险 |
|---|---|
| `com.mitv.contenttemplate` | 内容模板，可能影响首页区块 |
| `com.duokan.videodaily` | 今日头屏，可能影响开机画面 |
| `com.mitv.alarmcenter` | 闹钟/提醒功能 |
| `com.xiaomi.mitv.tvmanager` | 电视管家，可能被设置页依赖 |
| `com.mitv.care` | 关怀模式（实测已禁用） |

## 绝对不要动

| 包名 | 原因 |
|---|---|
| `com.mitv.tvhome` | 桌面（PatchWall），禁了黑屏/无 HOME |
| `com.xiaomi.mitv.settings` | 系统设置 |
| `com.mitv.mivideoplayer` | 视频播放器 |
| `com.xiaomi.mitv.systemui` | 系统 UI |
| `com.android.*` | 系统框架组件 |

## 操作纪律

- **分批**：一次只禁 1~3 个，重启验证正常再下一批
- **记录**：每项记下原状态和还原命令
- **OTA 会复活**：升级后组件会被装回来，重跑即可
- **开机广告另说**：它由固件启动流程加载，禁上面这些包只能"部分机型"跳过，不能保证
