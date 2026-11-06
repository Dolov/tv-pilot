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
  local f rc=0
  for f in "$TVPILOT_SKILL_DIR/.env"; do
    [[ -f "$f" ]] && { set -a; . "$f"; set +a; }
  done
  local root
  root="$(cd -P "$TVPILOT_SKILL_DIR/../../.." 2>/dev/null && pwd)" || return 0
  if [[ -d "$root/.git" && -f "$root/.env" && "$root" != "$TVPILOT_SKILL_DIR" ]]; then
    set -a; . "$root/.env"; set +a
  fi
  return 0
}
_tvp_load_env || true

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

# 起始毫秒 -> "1.23s"
elapsed_fmt() {
  local ms=$(( $(now_ms) - $1 ))
  printf '%d.%02ds' $((ms / 1000)) $(((ms % 1000) / 10))
}
