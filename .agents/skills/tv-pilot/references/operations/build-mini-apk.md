# 不装 Android Studio，给电视写一个小 App

适用：悬浮窗/角标、开机自启入口、小工具。本机产物体积 ~74KB。
本文只讲**怎么搭出能装的 APK**；构建→部署→验证的完整闭环、工具链自检、源码恢复见
`references/operations/dev-workflow.md`（改电视上已有 App 时优先读那份）。

## 最小结构

```
app/
├── AndroidManifest.xml   # targetSdk 22、minSdk 21、组件声明
├── src/.../1~3 个 .java  # Service/Receiver/AccessibilityService 就够
├── assets/               # 可选：字体等（aapt2 要加 -A）
└── build.sh              # javac → d8 → aapt2 → zip dex → zipalign → apksigner
```

## 工具链

已固化在 skill（`.tools/`），不要重新下载 SDK / Android Studio：

```bash
bash scripts/build-tools.sh status   # 自检：build-tools / android.jar / JDK / keystore / jadx
bash scripts/build-tools.sh env      # 输出 TVPILOT_JAVA_HOME / BUILD_TOOLS / ANDROID_JAR / KEYSTORE
```

JDK 要求 ≤21（javac 26 已移除 `-source/-target 8`）；脚本会自动找 `$JAVA_HOME` → brew `openjdk@21/@17`。

## 构建链路

```bash
eval "$(bash "$SKILL/scripts/build-tools.sh" env)"
JAVA_HOME="$TVPILOT_JAVA_HOME"; BT="$TVPILOT_BUILD_TOOLS"; AJ="$TVPILOT_ANDROID_JAR"
KS="$TVPILOT_KEYSTORE"; KS_PASS="${TVPILOT_KS_PASS:-android}"; export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

javac -source 8 -target 8 -Xlint:-options -classpath "$AJ" -d out/classes $(find src -name '*.java')
d8 --min-api 21 --lib "$AJ" --output out/dex $(find out/classes -name '*.class')
aapt2 link -o out/unsigned.apk --manifest AndroidManifest.xml -I "$AJ" \
    --min-sdk-version 21 --target-sdk-version 22 \
    --version-code 1 --version-name 1.0 -A assets/
( cd out/dex && zip -qj ../unsigned.apk classes.dex )
zipalign -f 4 out/unsigned.apk out/aligned.apk
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --out app.apk out/aligned.apk
```

## 关键坑（都踩过）

- **keystore 必须固定**（现在固定在 `.tools/keystore/tvhud.jks`）。换了签名只能先 uninstall 再装，
  appops、无障碍条目、App 数据全部重来。丢 keystore = 必须走一次卸载重装。
  签名密码用环境变量 `TVPILOT_KS_PASS`（默认 `android`），可写进本地 `.env`，不要硬编码进仓库。
- 有 assets 必须给 `aapt2 link` 加 `-A assets/`，否则运行时 `Typeface.createFromAsset` 找不到文件。
- 没有 `res/` 也能打包（manifest + dex 就够）；要资源就先 `aapt2 compile --dir res -o res.zip`，
  `link` 时加 `-R res.zip`。
- `targetSdk 22` 的取舍：免去前台服务/后台执行限制，`TYPE_SYSTEM_ALERT` 可直接用；
  悬浮窗仍需 `appops` 授权，运行时权限按旧规则安装即授。
- 电视是 32 位 `armeabi-v7a` 无所谓：dex 与 CPU 架构无关。
- 装完要补授权（顺序很重要，直接看 `dev-workflow.md` 的 `app-deploy.sh`）：
  `appops SYSTEM_ALERT_WINDOW` / `appops GET_USAGE_STATS` / `pm grant WRITE_SECURE_SETTINGS`。
- 开机自启：`references/operations/boot-autostart.md`（无障碍服务入口，`BOOT_COMPLETED` 收不到）。
- 屏保上叠字：`references/operations/screensaver.md`（含中英混排为什么必须自绘 Canvas）。
