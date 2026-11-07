# 安全策略

tv-pilot 是一套**本机、局域网内**使用的电视运维工具：skill 脚本通过 ADB 操作电视，
`app/` 是装在电视上的辅助 App。它不面向公网，也没有云端账号体系，但 ADB 与
电视端 App 的组合会暴露一批高权限能力。下面是我们认可的威胁模型与已知限制。

## 威胁模型

| 资产 / 入口 | 风险 | 现状 | 缓解 |
|---|---|---|---|
| ADB `5555` 端口 | 同网段嗅探、未授权调试 | 明文 TCP、无 TLS；用 ADB RSA 密钥授权，首次需在电视上确认；部分小米固件重启后仍保持授权 | 不用时在电视上关掉 ADB 调试；只在可信局域网使用；不要把 5555 映射到公网 |
| `CtrlReceiver`（导出、无权限） | 任意已安装 App 可发广播控制 HUD / 修改状态 | Manifest 中 `exported="true"`，无自定义 permission；接受 `SHOW`/`HIDE`/`PREVIEW_*`/`RESET_USAGE`/`RELOAD_PLAYS`/`ARCHIVE_NOW`/`ADB_KEEP_*` | 视为本机可信应用模型；不要安装来源不明的 APK；后续可改用 signature 级权限（见「已知限制」） |
| `ADB_KEEP_ON` | 静默让电视保持 ADB 开启 | 写 SharedPreferences 后由 `HudService` 周期性把 `adb_enabled` 写回 1 | 明确告知用户该开关的代价；不用时发 `ADB_KEEP_OFF` 并在电视上关闭 ADB |
| `WRITE_SECURE_SETTINGS` | 改电视安全设置（含 `adb_enabled`） | 通过 adb `pm grant` 授予，App 以普通 uid 运行但持有该签名/特权权限 | 只用于 ADB 守护；权限授予可随时 `pm revoke`；限制 App 安装来源 |
| 使用统计 / 播放记录 | 隐私：看了什么、何时看、看多久 | `UsageStats` + 米家播放日志；结构化历史写在 App 外部私有目录（默认保留 365 天） | 数据只在电视本地；不上传；定期清理历史；分享截图前脱敏 |
| 无障碍服务 | UI 内容可被观察 | `BootAccessibilityService` 作为开机入口注册，持有 `BIND_ACCESSIBILITY_SERVICE` | 只在自己电视上启用；不用时在设置里关闭；不要授予来源不明的无障碍服务 |
| 签名 keystore | 伪造升级包 | `.tools/keystore/tv-pilot.jks`，默认口令 `android`，已 gitignore | keystore 不提交、不分享；正式分发前改成强口令并离线保存 |
| 电视端日志 / 截图 | 泄露家庭信息 | 脚本可拉 `logcat` / `screencap`；播放记录含片名 | 提交 issue/PR 前脱敏；不要把原始日志整段贴出 |

### 不在威胁模型内

- 已 root / 已刷机的电视，或攻击者已拿到电视的 ADB 私钥。
- 恶意固件、供应链投毒、物理接触电视。
- 公网暴露（本项目不提供任何公网服务）。

## 当前已知限制

1. **控制广播没有权限保护**：任何已安装 App 都能向 `com.dsh.tvhud` 发
   `RESET_USAGE`（清空当天统计）、`ADB_KEEP_ON`（保持 ADB 开启）等广播。
   这是为了 `adb shell am broadcast` 调试方便，属于有意的取舍，不是提权漏洞。
2. **ADB 5555 是明文且长期授权**：ADB 认证只发生在连接建立时，之后流量不加密；
   已授权的密钥可长期重连。共享/公共 Wi-Fi 下不要开启。
3. **`WRITE_SECURE_SETTINGS` 权限较宽**：App 只把它用于 `adb_enabled`，但权限本身
   允许修改更多 secure settings；撤销权限后 ADB 守护失效。
4. **数据不加密**：历史 JSON 落在电视应用私有目录，root / adb / 部分具备存储权限的
   应用可能读到；保留 365 天，没有自动加密或远程擦除。
5. **`targetSdkVersion 22`**：App 使用较旧的 targetSdk 以适配 MIUI TV / Android 9，
   不享受新系统的分区存储与后台限制默认值。
6. **机型假设**：`app/` 的屏保、ADB 守护、播放日志解析以 MIUI TV 为主；
   其它品牌只保证通用只读能力，写操作需先有设备档案。
7. **无网络权限**：App 未申请 `INTERNET`，不会主动外传数据；但这不阻止本机其它
   应用或 adb 读取其文件。
8. **CI 不覆盖真机**：`make test` 是离线 fake-adb 测试，不验证真实电视行为；
   安全相关的真机验证仍需人工完成。

## 报告漏洞

如果你发现安全问题（例如可被其它 App 利用的广播、权限绕过、数据泄露），
**不要先开公开 issue**：

1. 通过 GitHub 仓库的 **Security → Advisories → Report a vulnerability** 私密报告；
2. 说明受影响版本（`version.properties` 的 versionName/versionCode 或 APK 版本）、
   复现步骤、影响范围；
3. 不要附带真实 IP / MAC / 序列号 / 账号 / 观看记录；需要时用占位符；
4. 我们会在力所能及的范围内尽快确认并给出修复或缓解方案；修复发布前请勿公开细节。

一般 bug、兼容性问题请走普通 issue，并附上脱敏后的命令与输出。
