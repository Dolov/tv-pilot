#!/usr/bin/env bash
# 统一 adb 入口。解决两个问题：
# 1. adb 会写 ~/.android，受限环境下被拒绝直接崩溃 → 把 HOME 重定向到 skill 目录
# 2. adb 二进制位置不固定 → 默认用 skill 的 .tools/，也接受环境变量覆盖
#
# 用法：scripts/adb.sh <adb 子命令...>
# 例：  scripts/adb.sh devices
#       scripts/adb.sh connect <TV_IP>:5555    # TV_IP 在 .env 里配置
#       scripts/adb.sh shell pm list packages
# 环境变量：TVPILOT_ADB_BIN / TVPILOT_HOME 可覆盖二进制和 HOME（测试或复用系统 adb 时用）
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$SKILL_DIR/.tools"

export HOME="${TVPILOT_HOME:-$TOOLS/android-home}"
mkdir -p "$HOME" 2>/dev/null || true

ADB="${TVPILOT_ADB_BIN:-$TOOLS/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then
  echo "platform-tools 未安装，先运行: bash $SKILL_DIR/scripts/install-adb.sh" >&2
  exit 1
fi

exec "$ADB" "$@"
