#!/usr/bin/env bash
# 公共库（被 source，不单独执行）：路径解析、可移植超时、adb 封装、状态缓存、计时。
#
# 设计约束：
# - 一切可能阻塞的操作都必须有上限。macOS 没有 `timeout` 命令，用 perl alarm 实现。
# - 状态读写失败不能影响主流程：skill 目录在受限沙箱里可能是只读的，
#   此时自动退到平台临时目录，最坏情况只是失去缓存。
set -uo pipefail

TVPILOT_SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TVPILOT_TOOLS_DIR="${TVPILOT_TOOLS_DIR:-$TVPILOT_SKILL_DIR/.tools}"
TVPILOT_ADB_BIN="${TVPILOT_ADB_BIN:-$TVPILOT_TOOLS_DIR/platform-tools/adb}"
TVPILOT_HOME="${TVPILOT_HOME:-$TVPILOT_TOOLS_DIR/android-home}"

# 状态文件：优先 skill 内 .tools/；不可写时退到临时目录（跨脚本、跨会话复用）。
if [[ -z "${TVPILOT_STATE_FILE:-}" ]]; then
  TVPILOT_STATE_FILE="$TVPILOT_TOOLS_DIR/state.env"
  if ! ( : >>"$TVPILOT_STATE_FILE" ) 2>/dev/null; then
    _tvp_tmp="${TMPDIR:-/tmp}/tv-pilot"
    mkdir -p "$_tvp_tmp" 2>/dev/null || true
    TVPILOT_STATE_FILE="$_tvp_tmp/state.env"
  fi
fi

# 本地敏感配置（不进仓库）：局域网 IP / 遥控器 MAC / 签名密码等。见 .env.example。
# 位置优先级：skill 根 .env → 仓库根 .env（仅在能确认是 tv-pilot 仓库时读取，避免误加载家目录 .env）。
_tvp_load_env() {
  if [[ -f "$TVPILOT_SKILL_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1090  # 路径因安装位置而异
    . "$TVPILOT_SKILL_DIR/.env"
    set +a
  fi
  local root
  root="$(cd -P "$TVPILOT_SKILL_DIR/../../.." 2>/dev/null && pwd)" || return 0
  if [[ -d "$root/.git" && -f "$root/.env" && "$root" != "$TVPILOT_SKILL_DIR" ]]; then
    set -a; . "$root/.env"; set +a
  fi
  return 0
}
_tvp_load_env || true

# 本地档案覆盖层：<TVPILOT_DEVICES_DIR>/<slug>.md 与 <slug>-apps.txt。
# 默认放用户配置目录，仓库里的 references/devices/ 保持只读、可提交。
TVPILOT_DEVICES_DIR="${TVPILOT_DEVICES_DIR:-$HOME/.config/tv-pilot/devices}"

# 毫秒时间戳（macOS date 不支持 %N，用 perl）。
now_ms() { perl -MTime::HiRes=time -e 'printf "%d", time*1000'; }

# 可移植 timeout：perl 设 alarm 后 exec，超时由 SIGALRM 终止。
run_timeout() {
  local secs="$1"; shift
  perl -e 'alarm shift; exec @ARGV' "$secs" "$@"
}

# 所有 adb 调用统一入口：HOME 重定向（adb 会 mkdir ~/.android，受限环境会崩）+ 默认超时。
tv_adb() {
  ( export HOME="$TVPILOT_HOME"
    mkdir -p "$HOME" 2>/dev/null || true
    run_timeout "${TV_ADB_TIMEOUT:-10}" "$TVPILOT_ADB_BIN" "$@" )
}

# 当前处于 device 状态的串口（不带参数取第一个，带参数只查指定的）。
tv_device() {
  local want="${1:-}" out
  out="$(tv_adb devices 2>/dev/null || true)"
  if [[ -n "$want" ]]; then
    printf '%s\n' "$out" | awk -F'\t' -v w="$want" '$1==w && $2 ~ /^device/ {print $1; exit}'
  else
    printf '%s\n' "$out" | awk -F'\t' '$2 ~ /^device/ {print $1; exit}'
  fi
}

# 某串口在 adb devices 里的状态：device / offline / unauthorized / 空。
tv_state_of() {
  tv_adb devices 2>/dev/null | awk -F'\t' -v w="$1" '$1==w {print $2; exit}'
}

state_get() { # key -> value（无则输出空）
  [[ -f "$TVPILOT_STATE_FILE" ]] || return 0
  awk -F= -v k="$1" '$1==k {sub(/^[^=]*=/,""); print; exit}' "$TVPILOT_STATE_FILE"
}

state_set() { # key value（写失败静默放弃，只影响缓存，不影响结果）
  local k="$1" v="$2" tmp
  tmp="$(mktemp "${TVPILOT_STATE_FILE}.XXXXXX" 2>/dev/null)" || return 0
  { [[ -f "$TVPILOT_STATE_FILE" ]] && grep -v "^${k}=" "$TVPILOT_STATE_FILE"
    printf '%s=%s\n' "$k" "$v"; } >"$tmp" 2>/dev/null || { rm -f "$tmp"; return 0; }
  mv "$tmp" "$TVPILOT_STATE_FILE" 2>/dev/null || rm -f "$tmp" 2>/dev/null
  return 0
}

state_clear() { # 无参数清空；带参数删单个 key
  [[ -f "$TVPILOT_STATE_FILE" ]] || return 0
  local tmp
  if [[ $# -eq 0 ]]; then
    : >"$TVPILOT_STATE_FILE" 2>/dev/null || true
    return 0
  fi
  tmp="$(mktemp "${TVPILOT_STATE_FILE}.XXXXXX" 2>/dev/null)" || return 0
  grep -v "^$1=" "$TVPILOT_STATE_FILE" >"$tmp" 2>/dev/null || true
  mv "$tmp" "$TVPILOT_STATE_FILE" 2>/dev/null || rm -f "$tmp" 2>/dev/null
  return 0
}

# ---------- 品牌 provider（契约见 scripts/providers/README.md、docs/providers.md）----------
# 这里只定义加载器，不 source 任何 provider、不跑 adb，source lib.sh 本身没有副作用。
TVPILOT_PROFILE_CACHE=""
TVPILOT_PROFILE_RESOLVED=0
TVPILOT_PROVIDER_NAME="_default"
TVPILOT_PROVIDER_RESOLVED=0
TVPILOT_PROVIDER_SOURCE="default"

# 当前设备档案 slug：env（TVPILOT_PROFILE / TV_PROFILE）> state.tv_profile > identify-tv 自动识别。
# identify 调用有硬超时（默认 6s），且结果会写回 state，后续调用不再付这个成本。
# TVPILOT_PROFILE_AUTODETECT=0 可关闭自动识别（测试/离线脚本用）。
tvp_profile_slug() {
  if [[ "$TVPILOT_PROFILE_RESOLVED" == "1" ]]; then
    if [[ -n "$TVPILOT_PROFILE_CACHE" ]]; then
      printf '%s\n' "$TVPILOT_PROFILE_CACHE"
    fi
    return 0
  fi

  local slug="${TVPILOT_PROFILE:-${TV_PROFILE:-}}"
  [[ -n "$slug" ]] || slug="$(state_get tv_profile 2>/dev/null || true)"

  if [[ -z "$slug" && "${TVPILOT_PROFILE_AUTODETECT:-1}" != "0" \
        && -f "$TVPILOT_SKILL_DIR/scripts/identify-tv.sh" ]]; then
    local kv="" status="" brand="" model=""
    # 子 shell 里 export，保证 identify-tv.sh 及其 adb 子进程都拿到短超时。
    kv="$( export TV_ADB_TIMEOUT="${TVPILOT_IDENTIFY_ADB_TIMEOUT:-3}"
           run_timeout "${TVPILOT_IDENTIFY_TIMEOUT:-6}" \
             bash "$TVPILOT_SKILL_DIR/scripts/identify-tv.sh" --kv 2>/dev/null || true )"
    if [[ -n "$kv" ]]; then
      slug="$(printf '%s\n' "$kv" | awk -F= '$1=="profile_slug" {sub(/^[^=]*=/,""); print; exit}')"
      status="$(printf '%s\n' "$kv" | awk -F= '$1=="status" {sub(/^[^=]*=/,""); print; exit}')"
      brand="$(printf '%s\n' "$kv" | awk -F= '$1=="brand" {sub(/^[^=]*=/,""); print; exit}')"
      model="$(printf '%s\n' "$kv" | awk -F= '$1=="model" {sub(/^[^=]*=/,""); print; exit}')"
      # identify-tv 自己也会写；这里再写一次，兼容它写失败或旧版本的情况。
      state_set tv_profile "$slug"
      state_set tv_brand "$brand"
      state_set tv_model "$model"
      state_set tv_match "$status"
    fi
  fi

  TVPILOT_PROFILE_CACHE="$slug"
  TVPILOT_PROFILE_RESOLVED=1
  if [[ -n "$slug" ]]; then
    printf '%s\n' "$slug"
  fi
  return 0
}

# 幂等加载 <slug>.sh；找不到/非法 slug 时只用 _default.sh。
# 每次重新加载前先清掉契约函数，避免上一次 provider 的实现残留。
tvp_load_provider() {
  local slug="${1:-}"
  # 显式传空串表示「只要默认 provider」；不传参数才自动识别。
  if [[ $# -eq 0 ]]; then
    slug="$(tvp_profile_slug)"
  fi
  case "$slug" in
    "" | . | .. | *[!A-Za-z0-9._-]*) slug="" ;;
  esac
  local want="${slug:-_default}"
  if [[ "$TVPILOT_PROVIDER_RESOLVED" == "1" && "$TVPILOT_PROVIDER_NAME" == "$want" ]]; then
    return 0
  fi

  unset -f tvp_screensaver_pkg tvp_play_log_path tvp_battery_key_prefix \
    tvp_apps_snapshot tvp_ad_patterns tvp_home_pkg 2>/dev/null || true

  local dir="$TVPILOT_SKILL_DIR/scripts/providers"
  local local_dir="$TVPILOT_DEVICES_DIR/providers"
  if [[ -f "$dir/_default.sh" ]]; then
    . "$dir/_default.sh"
  fi
  TVPILOT_PROVIDER_SOURCE="default"
  if [[ -n "$slug" && -f "$local_dir/$slug.sh" ]]; then
    # 本地覆盖层优先：不想公开的机型支持只放本地，不进仓库。
    . "$local_dir/$slug.sh" || true
    TVPILOT_PROVIDER_NAME="$slug"
    TVPILOT_PROVIDER_SOURCE="local"
  elif [[ -n "$slug" && -f "$dir/$slug.sh" ]]; then
    # provider 有语法错误时不要让调用方（可能 set -e）直接退出。
    . "$dir/$slug.sh" || true
    TVPILOT_PROVIDER_NAME="$slug"
    TVPILOT_PROVIDER_SOURCE="repo"
  else
    TVPILOT_PROVIDER_NAME="_default"
  fi
  TVPILOT_PROVIDER_RESOLVED=1
  return 0
}

# 调用 provider 函数；未定义时输出空、返回 0。参数原样传给 provider。
tvp_provider_call() {
  local fn="${1:-}"
  [[ -n "$fn" ]] || return 0
  shift || true
  if declare -F "$fn" >/dev/null 2>&1; then
    "$fn" "$@"
  fi
  return 0
}

# 起始毫秒 -> "1.23s"
elapsed_fmt() {
  local ms=$(( $(now_ms) - $1 ))
  printf '%d.%02ds' $((ms / 1000)) $(((ms % 1000) / 10))
}
