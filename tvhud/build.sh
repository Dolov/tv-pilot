#!/bin/bash
# 构建 TV HUD APK（工具链固化在 skill 的 .tools/，首次跑 scripts/build-tools.sh ensure）
# - 独立使用：默认用 ~/.agents/skills/tv-pilot；也可用 TVPILOT_SKILL 覆盖
# - 仓库内使用：<repo>/tvhud/build.sh 会自动定位 <repo>/.agents/skills/tv-pilot
# - 字体 DINPro / MIUI_EX 为专有字体，不随仓库分发；缺失时 App 运行时回退系统字体
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
SKILL="${TVPILOT_SKILL:-$HOME/.agents/skills/tv-pilot}"
if [[ ! -f "$SKILL/scripts/build-tools.sh" && -f "$HERE/../.agents/skills/tv-pilot/scripts/build-tools.sh" ]]; then
    SKILL="$(cd "$HERE/../.agents/skills/tv-pilot" && pwd)"
fi

# 本机敏感配置（.env 不进仓库）：TVPILOT_KS_PASS 等
if [[ -f "$SKILL/.env" ]]; then
    set -a
    . "$SKILL/.env"
    set +a
fi

eval "$(bash "$SKILL/scripts/build-tools.sh" env)"
JAVA_HOME="$TVPILOT_JAVA_HOME"
BT="$TVPILOT_BUILD_TOOLS"
AJ="$TVPILOT_ANDROID_JAR"
KS="$TVPILOT_KEYSTORE"
KS_PASS="${TVPILOT_KS_PASS:-android}"
OUT="$HERE/../.build/out"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# 版本号单一来源：仓库根 version.properties。skill 位于 <repo>/.agents/skills/tv-pilot 时
# 从 SKILL 上溯 3 层；在仓库内直接跑 tvhud/build.sh 时也可以用 HERE/..。
# 独立使用（没有仓库）时回退到硬编码 1.11/12，行为与旧版一致。
VERSION_FILE=""
for f in "$HERE/../version.properties" "$SKILL/../../../version.properties"; do
    if [[ -f "$f" ]]; then
        VERSION_FILE="$f"
        break
    fi
done
if [[ -n "$VERSION_FILE" ]]; then
    vc="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*//p' "$VERSION_FILE" | tr -d '[:space:]' | head -1)"
    vn="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*//p' "$VERSION_FILE" | tr -d '[:space:]' | head -1)"
    [[ "$vc" =~ ^[0-9]+$ ]] || vc=12
    [[ -n "$vn" ]] || vn=1.11
    VERSION_ARGS=(--version-code "$vc" --version-name "$vn")
else
    VERSION_ARGS=(--version-code 12 --version-name 1.11)
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

"$JAVA_HOME/bin/javac" -source 8 -target 8 -Xlint:-options -classpath "$AJ" \
    -d "$OUT/classes" $(find "$HERE/src" -name '*.java')

"$BT/d8" --min-api 21 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

AAPT_ASSETS=""
[[ -d "$HERE/assets" ]] && AAPT_ASSETS="-A $HERE/assets"
# 资源（应用图标 + TV banner）：aapt2 需要先 compile 再 link
AAPT_RES=""
if [[ -d "$HERE/res" ]]; then
    "$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
    AAPT_RES="-R $OUT/res.zip"
fi
"$BT/aapt2" link -o "$OUT/unsigned.apk" --manifest "$HERE/AndroidManifest.xml" -I "$AJ" \
    $AAPT_RES \
    --min-sdk-version 21 --target-sdk-version 22 \
    "${VERSION_ARGS[@]}" $AAPT_ASSETS
( cd "$OUT/dex" && zip -qj "$OUT/unsigned.apk" classes.dex )

"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$OUT/tvhud.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/tvhud.apk" >/dev/null
"$BT/apksigner" verify --print-certs "$OUT/tvhud.apk" | head -3
echo "OK  $OUT/tvhud.apk  $(stat -f%z "$OUT/tvhud.apk") bytes"

# 构建成功后把源码同步到 skill 的 assets/ 保底备份（防工作区被清空；TVPILOT_AUTO_BACKUP=0 关闭）
if [[ "${TVPILOT_AUTO_BACKUP:-1}" == "1" ]]; then
    bash "$SKILL/scripts/app-backup.sh" backup "$HERE" --name tvhud >/dev/null 2>&1 || true
fi
