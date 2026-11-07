#!/usr/bin/env bash
# 电视小 App 构建工具链（build-tools + platform android.jar）：
#   build-tools.sh ensure   # 缺什么下什么（腾讯镜像，~50MB/s）
#   build-tools.sh env      # 输出可 eval 的环境变量
#   build-tools.sh status   # 看装了什么、JDK 用哪个
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$SKILL_DIR/.tools"
SDK="$TOOLS/android-sdk"
BT="$SDK/build-tools"
AJ="$SDK/platform-28/android.jar"
KS="$TOOLS/keystore/tv-pilot.jks"
MIRROR="https://mirrors.cloud.tencent.com/AndroidSDK"
mkdir -p "$SDK" "$TOOLS/keystore"

find_jdk() {
  local c
  for c in "${JAVA_HOME:-}" \
           /opt/homebrew/opt/openjdk@21 \
           /opt/homebrew/opt/openjdk@17 \
           /opt/homebrew/opt/openjdk \
           /Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home; do
    [[ -n "$c" && -x "$c/bin/javac" ]] && { echo "$c"; return 0; }
  done
  local jh
  jh="$(/usr/libexec/java_home -v 21 2>/dev/null)" && [[ -x "$jh/bin/javac" ]] && { echo "$jh"; return 0; }
  jh="$(/usr/libexec/java_home -v 17 2>/dev/null)" && [[ -x "$jh/bin/javac" ]] && { echo "$jh"; return 0; }
  return 1
}

need_download() {
  [[ ! -x "$BT/aapt2" || ! -f "$AJ" ]]
}

ensure() {
  if need_download; then
    echo "[build-tools] 缺失，从腾讯镜像下载…" >&2
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL --max-time 600 -o "$tmp/bt.zip" "$MIRROR/build-tools_r35.0.1_macosx.zip"
    curl -fsSL --max-time 600 -o "$tmp/pf.zip" "$MIRROR/platform-28_r06.zip"
    rm -rf "$BT" "$SDK/platform-28"
    unzip -q -o "$tmp/bt.zip" -d "$SDK"
    [[ -d "$SDK/android-15" ]] && mv "$SDK/android-15" "$BT"
    unzip -q -o "$tmp/pf.zip" -d "$SDK/platform-28"
    rm -rf "$tmp"
  fi
  if ! find_jdk >/dev/null; then
    echo "[build-tools] 没找到 JDK（需要 17/21，javac 26 不支持 -source 8）；" >&2
    echo "  brew install openjdk@21   然后重试" >&2
    exit 1
  fi
}

env_out() {
  ensure
  local jdk; jdk="$(find_jdk)"
  echo "export TVPILOT_JAVA_HOME='$jdk'"
  echo "export TVPILOT_BUILD_TOOLS='$BT'"
  echo "export TVPILOT_ANDROID_JAR='$AJ'"
  echo "export TVPILOT_KEYSTORE='$KS'"
}

status() {
  local jdk
  jdk="$(find_jdk || true)"
  echo "build-tools : $([[ -x "$BT/aapt2" ]] && echo "$BT" || echo '（缺失）')"
  echo "android.jar : $([[ -f "$AJ" ]] && echo "$AJ" || echo '（缺失）')"
  echo "keystore    : $([[ -f "$KS" ]] && echo "$KS" || echo '（缺失，重新生成会导致签名变更）')"
  echo "JDK         : ${jdk:-（缺失）}"
  echo "jadx        : $([[ -x "$TOOLS/jadx/bin/jadx" ]] && echo "$TOOLS/jadx/bin/jadx" || echo '（未安装）')"
  echo "adb         : $([[ -x "$TOOLS/platform-tools/adb" ]] && echo "$TOOLS/platform-tools/adb" || echo '（未安装，跑 install-adb.sh）')"
}

case "${1:-status}" in
  ensure) ensure ;;
  env)    env_out ;;
  status) status ;;
  *) echo "用法: $0 ensure|env|status" >&2; exit 1 ;;
esac
