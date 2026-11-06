#!/usr/bin/env bash
# 安装 Google 官方 platform-tools 到 skill 内的 .tools/，不污染系统。
# 关键：adb 启动会 mkdir ~/.android，受限环境里会被拒绝并直接 abort。
# 所以所有调用都通过 scripts/adb.sh，它把 HOME 指向 .tools/android-home。
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$SKILL_DIR/.tools"
mkdir -p "$TOOLS/android-home"
cd "$TOOLS"

if [[ -x platform-tools/adb ]]; then
  echo "platform-tools 已存在，跳过下载"
else
  echo "下载 platform-tools..."
  curl -fsSL --max-time 180 -o pt.zip \
    https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
  unzip -oq pt.zip
  rm -f pt.zip
fi

HOME="$TOOLS/android-home" ./platform-tools/adb version
