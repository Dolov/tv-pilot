#!/usr/bin/env bash
# 把 TV mini-APK 按「正确顺序」装到电视上并自检。
#
# 用法：
#   app-deploy.sh --apk <apk路径> --pkg <包名> [选项]
# 选项：
#   --activity <组件>   例如 com.dsh.tvhud/com.dsh.tvhud.usage.MainActivity：装完启动一次，清掉 stopped 状态
#   --overlay           授予 SYSTEM_ALERT_WINDOW（悬浮窗/角标必需）
#   --usage             授予 GET_USAGE_STATS（读前台应用/屏保识别必需）
#   --acc <组件>        无障碍服务组件，例如 com.dsh.tvhud/com.dsh.tvhud.hud.BootAccessibilityService
#                       会**最后**追加到 enabled_accessibility_services（install 会把它删掉）
#   --tag <TAG>         装完从 logcat 抓这个 tag 最近几行（自检用）
#   --dry-run           只打印将执行的命令
#   --force             跳过设备档案校验（未验证机型默认拒绝写操作，见 require-profile.sh）
#
# 为什么要按这个顺序：adb install -r 会移除该 App 的无障碍条目，am force-stop 也会；
# 所以条目必须最后写，写早了等于白写（详见 references/boot-autostart.md）。
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="bash $SKILL_DIR/scripts/adb.sh"
APK="" PKG="" ACTIVITY="" ACC="" TAG="" OVERLAY=0 USAGE=0 DRY=0 FORCE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --pkg) PKG="$2"; shift 2 ;;
    --activity) ACTIVITY="$2"; shift 2 ;;
    --acc) ACC="$2"; shift 2 ;;
    --tag) TAG="$2"; shift 2 ;;
    --overlay) OVERLAY=1; shift ;;
    --usage) USAGE=1; shift ;;
    --dry-run) DRY=1; shift ;;
    --force) FORCE=1; shift ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

[[ -n "$APK" && -n "$PKG" ]] || { echo "用法: $0 --apk <apk> --pkg <包名> [--activity ...] [--overlay] [--usage] [--acc ...] [--tag ...] [--dry-run]" >&2; exit 1; }
[[ -f "$APK" ]] || { echo "APK 不存在: $APK" >&2; exit 1; }

# 1) 连接（走 ensure-tv 阶梯，不扫网段）
if [[ $DRY -eq 0 ]]; then
  bash "$SKILL_DIR/scripts/ensure-tv.sh" >/dev/null
  # 写操作闸门：没有匹配档案的电视默认拒绝安装（--force 显式跳过）。
  GATE_ARGS=""
  [[ $FORCE -eq 1 ]] && GATE_ARGS="--force"
  # 只要求 matched（有档案）；能力级校验需要时由调用方自行跑 require-profile.sh <capability>
  bash "$SKILL_DIR/scripts/require-profile.sh" $GATE_ARGS
fi

run() {
  if [[ $DRY -eq 1 ]]; then echo "[dry] $*"; else eval "$*"; fi
}

echo "[1/6] 安装 $APK"
run "$ADB install -r '$APK'"

if [[ $OVERLAY -eq 1 ]]; then
  echo "[2/6] appops SYSTEM_ALERT_WINDOW allow"
  run "$ADB shell appops set $PKG SYSTEM_ALERT_WINDOW allow"
fi
if [[ $USAGE -eq 1 ]]; then
  echo "[3/6] appops GET_USAGE_STATS allow"
  run "$ADB shell appops set $PKG GET_USAGE_STATS allow"
fi
echo "[4/6] pm grant WRITE_SECURE_SETTINGS（自愈写设置用）"
run "$ADB shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || true"

# 写无障碍条目前必须先清 stopped：用 activity 或 SHOW 广播把包拉起一次
if [[ -n "$ACTIVITY" ]]; then
  echo "[5/6] 启动 $ACTIVITY 清 stopped，然后回桌面"
  run "$ADB shell am start -n $ACTIVITY >/dev/null 2>&1 || true"
  run "$ADB shell input keyevent 3"
  run "sleep 2"
fi

if [[ -n "$ACC" ]]; then
  echo "[6/6] 写入无障碍条目（最后一步）"
  run "$ADB shell '
    CUR=\$(settings get secure enabled_accessibility_services | tr -d \"\\r\")
    ME=$ACC
    NEW=\$(echo \"\$CUR\" | tr \":\" \"\\n\" | grep -v \"^\$ME\$\" | paste -sd: -)
    if [ -n \"\$NEW\" ]; then NEW=\"\$NEW:\$ME\"; else NEW=\"\$ME\"; fi
    settings put secure enabled_accessibility_services \"\$NEW\"
    settings put secure accessibility_enabled 1'"
fi

if [[ $DRY -eq 0 ]]; then
  echo "--- 自检 ---"
  $ADB shell "ps -A | grep -i $PKG || echo '(进程未运行)'"
  $ADB shell "dumpsys package $PKG | grep -o 'stopped=[a-z]*' || true"
  [[ -n "$ACC" ]] && $ADB shell "settings get secure enabled_accessibility_services | tr ':' '\n' | grep -F '$ACC' || echo '(无障碍条目缺失!)'"
  [[ -n "$TAG" ]] && $ADB shell "logcat -d -s $TAG:* | tail -5"
  echo "--- 回滚 ---"
  echo "$ADB shell pm uninstall $PKG"
fi
