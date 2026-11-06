#!/usr/bin/env bash
# 小米 MIUI TV / PatchWall（实测机型 MiTV-NSOP0，codename furyroad）provider。
#
# 只实现有实测依据的差异；通用逻辑仍留在 scripts/ 里。改动前先对照
# references/devices/xiaomi-mitv-nsop0.md 与 references/operations/remote-battery.md。

# 屏保包名（不含 Activity）：com.mitv.screensaver/.RenderActivity
tvp_screensaver_pkg() { printf '%s\n' 'com.mitv.screensaver'; }

# 米家上报的播放日志；MiTVOtService_PW 日志由 usage.sh 的 logcat 过滤兜底。
tvp_play_log_path() { printf '%s\n' '/sdcard/miio/miothost.txt'; }

# Settings.Global 键 bluetooth_hid_battery_<MAC大写>；冷启动/重连/按键时刷新。
tvp_battery_key_prefix() { printf '%s\n' 'bluetooth_hid_battery_'; }

# 应用快照：仓库优先；本地覆盖层（$TVPILOT_DEVICES_DIR）作为兜底。
tvp_apps_snapshot() {
  local repo="$TVPILOT_SKILL_DIR/references/devices/xiaomi-mitv-nsop0-apps.txt"
  local local_snap="$TVPILOT_DEVICES_DIR/xiaomi-mitv-nsop0-apps.txt"
  if [[ -f "$repo" ]]; then
    printf '%s\n' "$repo"
  elif [[ -f "$local_snap" ]]; then
    printf '%s\n' "$local_snap"
  fi
}

# 广告/统计候选包名正则（probe.sh 用它筛 pm list packages）。
tvp_ad_patterns() { printf '%s\n' 'ad|push|analy|stat'; }

# 默认桌面（PatchWall）。
tvp_home_pkg() { printf '%s\n' 'com.mitv.tvhome'; }
