#!/usr/bin/env bash
# 只读探测：把电视型号、固件、前台应用、包列表、儿童模式配置一次性读出来。
# 不修改电视上的任何东西。
#
# 用法: scripts/probe.sh [电视IP] [输出目录]（不传 IP 时自动走 ensure-tv 找电视）
#        scripts/probe.sh --json [电视IP]       只输出一段 JSON（给脚本/代理消费）
# 默认输出到 ./probe/
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JSON=0
IP=""
for a in "$@"; do
  case "$a" in
    --json) JSON=1 ;;
    -*) ;;
    *) IP="$a" ;;
  esac
done
if [[ -z "$IP" ]]; then
  IP="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null | cut -d: -f1)" || true
fi
if [[ -z "$IP" ]]; then
  echo "找不到电视；可显式传入 IP：scripts/probe.sh <IP> [输出目录]" >&2
  exit 1
fi

ADB="$SKILL_DIR/scripts/adb.sh"
OUT="${2:-$PWD/probe}"
mkdir -p "$OUT"

run() { "$ADB" -s "${IP}:5555" shell "$@" 2>/dev/null | tr -d '\r'; }

echo "连接 ${IP}:5555 ..."
"$ADB" connect "${IP}:5555" >/dev/null 2>&1 || true

if ! "$ADB" devices | grep -q "${IP}:5555[[:space:]]*device"; then
  echo "未连接成功。当前设备列表：" >&2
  "$ADB" devices >&2
  echo "unauthorized = 电视上还有授权框没按；offline = 开关没生效，重启电视再试" >&2
  exit 1
fi

if [[ "$JSON" == 1 ]]; then
  CMD='echo @@MODEL@@; getprop ro.product.model; echo @@BRAND@@; getprop ro.product.brand; echo @@SDK@@; getprop ro.build.version.sdk; echo @@FP@@; getprop ro.build.fingerprint; echo @@PKG@@; pm list packages | wc -l; echo @@TPKG@@; pm list packages -3 | wc -l; echo @@CHILD@@; settings list system | grep -icE "child|kid|play_time|user_mode"'
  raw="$("$ADB" -s "${IP}:5555" shell "$CMD" 2>/dev/null | tr -d '\r')"
  field() { printf '%s\n' "$raw" | awk -v k="$1" '$0==k {getline; print; exit}'; }
  jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
  printf '{"model":"%s","brand":"%s","sdk":"%s","fingerprint":"%s","packages":%s,"third_party":%s,"child_mode_keys":%s}\n' \
    "$(jesc "$(field @@MODEL@@)")" "$(jesc "$(field @@BRAND@@)")" "$(jesc "$(field @@SDK@@)")" \
    "$(jesc "$(field @@FP@@)")" "$(field @@PKG@@)" "$(field @@TPKG@@)" "$(field @@CHILD@@)"
  exit 0
fi

echo "=== 基本信息 ===" | tee "$OUT/info.txt"
for k in ro.product.model ro.product.brand ro.build.version.release \
         ro.build.version.sdk ro.build.display.id ro.build.fingerprint ro.build.date; do
  printf "%-28s %s\n" "$k" "$(run getprop "$k")" | tee -a "$OUT/info.txt"
done

echo | tee -a "$OUT/info.txt"
echo "=== 屏幕 ===" | tee -a "$OUT/info.txt"
run wm size | tee -a "$OUT/info.txt"
run wm density | tee -a "$OUT/info.txt"

echo
echo "=== 当前前台 ===" | tee "$OUT/focus.txt"
run dumpsys window | grep -E 'mCurrentFocus' | head -2 | tee -a "$OUT/focus.txt"

echo
echo "=== 包列表 ==="
run pm list packages > "$OUT/packages.txt"
echo "  共 $(wc -l < "$OUT/packages.txt" | tr -d ' ') 个，已存 $OUT/packages.txt"

echo "=== 第三方应用 ==="
run pm list packages -3 | sed 's/package://' | tee "$OUT/third-party.txt"

echo
echo "=== 儿童模式相关设置 ==="
run settings list system | grep -iE 'child|kid|play_time|user_mode' | tee "$OUT/child-mode.txt"

echo
echo "=== 广告/统计相关包（候选） ==="
grep -iE 'ad|push|analy|stat' "$OUT/packages.txt" 2>/dev/null \
  | grep -viE 'download|provider|settings|media|launcher|packageinstaller' \
  | sed 's/package://' | tee "$OUT/ad-candidates.txt"

echo
echo "完成。输出目录: $OUT"
