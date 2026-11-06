#!/usr/bin/env bash
# 用最便宜的方式拿到一台可用的电视连接。成功时 stdout 只打印 "<ip>:<port>"，
# 可以直接 `dev=$(bash scripts/ensure-tv.sh)` 取用；诊断信息全走 stderr。
#
# 阶梯（命中即停，每级都有硬超时）：
#   L0 adb devices 已有 device            ~0.1s（最常见）
#   L1 缓存 IP 直接 connect               ~1-5s
#   L2 find-tv.sh --fast + connect        ~2-7s
# 任一级失败后都会再看一眼 adb devices：电视可能刚好唤醒，能直接用就不折腾。
#
# 用法: ensure-tv.sh [-v|--verbose]
# 环境变量: TV_PORT(默认 5555)、TV_ADB_TIMEOUT(默认 10s)
# 失败: exit 1，并在 stderr 说明是 unauthorized / offline / 没开 ADB 中的哪一种。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

VERBOSE="${VERBOSE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    -v | --verbose) VERBOSE=1 ;;
    *) ;;
  esac
  shift
done

PORT="${TV_PORT:-5555}"
t0=$(now_ms)
log() { [[ "$VERBOSE" == 1 ]] && echo "[ensure-tv] $*" >&2; return 0; }

# 等某个串口变成 device。电视刚唤醒时 adb 状态会抖，给它最多 2.5s。
wait_for_device() {
  local serial="$1" i
  for i in 1 2 3 4 5; do
    [[ "$(tv_state_of "$serial")" == "device" ]] && return 0
    sleep 0.5
  done
  return 1
}

# 成功出口：写缓存并打印串口。
succeed() {
  local serial="$1" ip="${1%%:*}"
  state_set tv_ip "$ip"
  state_set tv_last_ok "$(date +%Y-%m-%dT%H:%M:%S%z)"
  echo "$serial"
  exit 0
}

# 任一级失败后兜底：adb devices 里冒出可用设备就直接用（可能刚唤醒/自动重连）。
use_any_device() {
  local d
  d="$(tv_device)"
  [[ -n "$d" ]] && { log "兜底命中：$d"; succeed "$d"; }
  return 1
}

report_fail() {
  local serial="$1" st
  st="$(tv_state_of "$serial")"
  case "$st" in
    unauthorized)
      echo "电视上弹了授权框：用遥控器选「允许」，建议勾「一律允许」，然后重试。" >&2 ;;
    offline)
      echo "adb 看到设备但 offline：电视在待机或刚重启。先按遥控器唤醒，等 10 秒再试。" >&2 ;;
    "")
      echo "连不上 ${serial}：电视待机/关机，或 ADB 开关没生效（小米固件通常要重启电视）。" >&2 ;;
    *)
      echo "设备 ${serial} 当前状态为 ${st}。" >&2 ;;
  esac
}

# L0：已经有 device 状态（同一台电脑刚连过，最常见）
d="$(tv_device)"
if [[ -n "$d" ]]; then
  log "L0 命中：$d"
  echo "$d"
  exit 0
fi
log "L0 未命中（没有已连接设备），尝试缓存地址"

# L1：用上次成功的 IP 直连，不扫网段（缓存没有就用 .env 的 TV_IP）
ip="$(state_get tv_ip)"
[[ -n "$ip" ]] || ip="${TV_IP:-}"
if [[ -n "$ip" ]]; then
  log "L1 用缓存 IP connect ${ip}:${PORT}"
  TV_ADB_TIMEOUT=5 tv_adb connect "${ip}:${PORT}" >/dev/null 2>&1 || true
  wait_for_device "${ip}:${PORT}" && { log "L1 命中"; succeed "${ip}:${PORT}"; }
  use_any_device || true
  log "L1 未命中，开始扫描"
fi

# L2：扫描（只做 --fast，mDNS+ARP 足够；全量模式是给人排查用的，不在这里跑）
log "L2 find-tv.sh --fast"
found="$(bash "$SKILL_DIR/scripts/find-tv.sh" --fast --list 2>/dev/null | head -1)"
if [[ -n "$found" ]]; then
  log "扫描发现 ${found}，连接中"
  TV_ADB_TIMEOUT=5 tv_adb connect "${found}:${PORT}" >/dev/null 2>&1 || true
  wait_for_device "${found}:${PORT}" && { log "L2 命中"; succeed "${found}:${PORT}"; }
  # 只有"完全没登记"才值得重试；offline/unauthorized 重试也是白等
  if [[ -z "$(tv_state_of "${found}:${PORT}")" ]]; then
    log "重试一次连接"
    TV_ADB_TIMEOUT=5 tv_adb connect "${found}:${PORT}" >/dev/null 2>&1 || true
    wait_for_device "${found}:${PORT}" && { log "L2 重试命中"; succeed "${found}:${PORT}"; }
  fi
fi

use_any_device || true

echo "没有找到电视（耗时 $(elapsed_fmt "$t0")）。排查顺序：" >&2
echo "  1. 电视是否开机/亮屏？待机时 adbd 不监听，先按遥控器唤醒" >&2
echo "  2. 电视设置里 ADB 调试是否还开着？小米固件重启后可能复位" >&2
echo "  3. 电脑和电视是否同一网段？可用 scripts/find-tv.sh 直接排查" >&2
[[ -n "$ip" ]] && report_fail "${ip}:${PORT}"
exit 1
