#!/usr/bin/env bash
# 电视状态缓存（key=value）。所有脚本共用，让"第二次调用"不用重新发现设备。
#
# 用法:
#   state.sh show            打印全部缓存
#   state.sh get <key>       取一个值（没有则空）
#   state.sh set <key> <value>
#   state.sh clear [key]     清空全部 / 删一个
#   state.sh ip              只输出缓存的电视地址（没有则空）
#
# 缓存位置默认 <skill>/.tools/state.env；不可写时 lib.sh 会自动退到临时目录。
# 约定 key: tv_ip / tv_model / tv_last_ok / remote_mac / remote_battery / remote_updated_at
set -uo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cmd="${1:-show}"
shift || true

case "$cmd" in
  get)
    [[ $# -ge 1 ]] || { echo "用法: state.sh get <key>" >&2; exit 1; }
    state_get "$1"
    ;;
  set)
    [[ $# -ge 2 ]] || { echo "用法: state.sh set <key> <value>" >&2; exit 1; }
    state_set "$1" "$2"
    ;;
  clear)
    state_clear "$@"
    ;;
  ip)
    # 缓存优先；没有缓存就用 .env 里的 TV_IP（首次冷启动不扫网段）
    _ip="$(state_get tv_ip)"
    printf '%s\n' "${_ip:-${TV_IP:-}}"
    ;;
  show | *)
    if [[ -f "$TVPILOT_STATE_FILE" ]]; then
      cat "$TVPILOT_STATE_FILE"
    else
      echo "(无缓存)"
    fi
    ;;
esac
