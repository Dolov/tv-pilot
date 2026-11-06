#!/usr/bin/env bash
# TV HUD（com.dsh.tvhud）日常控制：状态 / 启停 / 构建 / 安装 / 截屏 / ADB 守护。
#
#   bash scripts/tv-hud.sh status                 # 版本、进程、当前前台、历史文件数
#   bash scripts/tv-hud.sh on|off                 # 启停 HudService（角标只在屏保时出现）
#   bash scripts/tv-hud.sh build                  # 调 tvhud/build.sh 构建 APK
#   bash scripts/tv-hud.sh install [--apk <path>] # 走 app-deploy.sh（权限/无障碍顺序内置）
#   bash scripts/tv-hud.sh screenshot [输出目录]   # 电视截屏拉回本地
#   bash scripts/tv-hud.sh adbkeep on|off         # ADB 常开守护（WRITE_SECURE_SETTINGS）
#   bash scripts/tv-hud.sh reset                  # 清空当天统计并用 UsageEvents 重建
#   bash scripts/tv-hud.sh preview on|off         # 用设计预览数据渲染角标（不写真实数据）
#
# 为什么单独有这个脚本：SKILL.md 把「改自研 App」列成一类常见请求，但构建、装、
# 截屏、看状态各是一条命令；固化成一个入口，代理不用每次拼 adb 和路径，也不会漏部署顺序。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

PKG="com.dsh.tvhud"
ACT_HOME="$PKG/.HomeActivity"
ACC="$PKG/com.dsh.tvhud.BootAccessibilityService"

# 仓库根：skill 在 <repo>/.agents/skills/tv-pilot 时上溯 3 层；独立安装时尝试 ~/tv-pilot。
REPO="$(cd "$SKILL_DIR/../../.." 2>/dev/null && pwd)"
if [[ ! -f "$REPO/tvhud/build.sh" ]]; then
  if [[ -f "$HOME/tv-pilot/tvhud/build.sh" ]]; then REPO="$HOME/tv-pilot"; else REPO=""; fi
fi

need_device() {
  local serial
  serial="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null)" || {
    echo "没连上电视。先唤醒电视并确认 ADB 调试已开：bash scripts/ensure-tv.sh -v" >&2
    exit 2
  }
  printf '%s' "$serial"
}

adb_sh() { local s="$1"; shift; tv_adb -s "$s" "$@"; }

cmd_status() {
  local serial
  serial="$(need_device)"
  local ver running focus hist
  ver="$(adb_sh "$serial" shell dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1)"
  running="$(adb_sh "$serial" shell ps -A 2>/dev/null | awk -v p="$PKG" '$NF==p || $9==p {print "running"; exit}')"
  focus="$(adb_sh "$serial" shell dumpsys window 2>/dev/null | sed -n 's/.*mCurrentFocus=Window{[^ ]* [^ ]* \([^ }]*\).*/\1/p' | head -1)"
  hist="$(adb_sh "$serial" shell 'ls /sdcard/Android/data/com.dsh.tvhud/files/history 2>/dev/null | wc -l' 2>/dev/null | tr -d ' \r')"
  echo "设备    : $serial"
  echo "版本    : ${ver:-未安装}"
  echo "HudService: ${running:-未运行}"
  echo "前台    : ${focus:-未知}"
  echo "历史归档: ${hist:-0} 天"
  if [[ -n "$ver" ]]; then
    echo "电池守护: $(adb_sh "$serial" shell settings get global adb_enabled 2>/dev/null | tr -d '\r')（1=ADB 常开）"
  fi
}

cmd_on()   { local s; s="$(need_device)"; adb_sh "$s" shell am startservice -n "$PKG/.HudService" >/dev/null 2>&1 || adb_sh "$s" shell am start-foreground-service -n "$PKG/.HudService" >/dev/null 2>&1 || true; echo "HudService 已启动（角标只在屏保时出现）"; }
cmd_off()  { local s; s="$(need_device)"; adb_sh "$s" shell am stopservice -n "$PKG/.HudService" >/dev/null 2>&1 || true; echo "HudService 已停止"; }

cmd_build() {
  [[ -n "$REPO" ]] || { echo "找不到 tvhud/ 源码（skill 不在仓库内？）。设置 TVPILOT_SKILL 或直接用仓库内 skill。" >&2; exit 2; }
  ( cd "$REPO/tvhud" && bash build.sh )
}

cmd_install() {
  local apk="$REPO/.build/out/tvhud.apk"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --apk) apk="${2:-}"; shift 2 ;;
      --apk=*) apk="${1#*=}"; shift ;;
      *) echo "未知参数: $1" >&2; exit 1 ;;
    esac
  done
  [[ -f "$apk" ]] || { echo "APK 不存在: ${apk}（先 bash scripts/tv-hud.sh build）" >&2; exit 2; }
  bash "$SKILL_DIR/scripts/app-deploy.sh" --apk "$apk" --pkg "$PKG" \
    --activity "$ACT_HOME" --overlay --usage --acc "$ACC" --tag TVHUD
}

cmd_screenshot() {
  local out="${1:-.}"
  local serial ts remote
  serial="$(need_device)"
  ts="$(date '+%Y%m%d-%H%M%S')"
  remote="/sdcard/tvhud-shot-$ts.png"
  adb_sh "$serial" shell screencap -p "$remote" 2>/dev/null || { echo "截屏失败（电视可能处于待机）" >&2; exit 2; }
  mkdir -p "$out" 2>/dev/null || true
  adb_sh "$serial" pull "$remote" "$out/tvhud-shot-$ts.png" >/dev/null 2>&1 || {
    echo "拉取截图失败" >&2; exit 2; }
  adb_sh "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true
  echo "$out/tvhud-shot-$ts.png"
}

cmd_broadcast() { # action
  local s; s="$(need_device)"
  adb_sh "$s" shell am broadcast -a "$1" >/dev/null 2>&1 || {
    echo "广播失败：$1（新版 Android 或系统限制可能拦截；先确认 App 在运行）" >&2; exit 2; }
}

cmd_adbkeep() {
  case "${1:-}" in
    on)  cmd_broadcast "$PKG.ADB_KEEP_ON";  echo "已请求开启 ADB 守护（服务需运行；开关改动需进程重启才完全生效，见 SECURITY.md）" ;;
    off) cmd_broadcast "$PKG.ADB_KEEP_OFF"; echo "已请求关闭 ADB 守护" ;;
    *) echo "用法: tv-hud.sh adbkeep on|off" >&2; exit 1 ;;
  esac
}

cmd_preview() {
  case "${1:-}" in
    on)  cmd_broadcast "$PKG.PREVIEW_ON";  echo "已开启设计预览（角标显示示例数据，不写真实使用记录）" ;;
    off) cmd_broadcast "$PKG.PREVIEW_OFF"; echo "已关闭设计预览" ;;
    *) echo "用法: tv-hud.sh preview on|off" >&2; exit 1 ;;
  esac
}

cmd_reset() { cmd_broadcast "$PKG.RESET_USAGE"; echo "已请求重置当天统计（用 UsageEvents 重建）"; }

case "${1:-help}" in
  status)      cmd_status ;;
  on)          cmd_on ;;
  off)         cmd_off ;;
  build)       cmd_build ;;
  install)     shift; cmd_install "$@" ;;
  screenshot)  shift; cmd_screenshot "${1:-.}" ;;
  adbkeep)     shift; cmd_adbkeep "${1:-}" ;;
  reset)       cmd_reset ;;
  preview)     shift; cmd_preview "${1:-}" ;;
  help|-h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' ;;
  *) echo "未知命令: $1（可用：status on off build install screenshot adbkeep reset preview）" >&2; exit 1 ;;
esac
