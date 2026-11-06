#!/usr/bin/env bash
# 一条命令回答「电视现在是开机 / 熄屏待机 / 关机」，顺带开机时长。只读。
# 默认只跑快查询（一次 adb shell 往返，<1s）。
#
# 用法: bash scripts/tv-state.sh [串口] [--deep] [--json]
#   --deep  额外读 dumpsys meminfo（~6s）判断是否进过深度睡眠
#   --json  输出 {state, wakefulness, screen, focus, uptime_sec, model}（见 docs/json-contract.md）
#
# state 取值：booted（亮屏运行）/ standby（熄屏待机）/ off（关机、断电或连不上）。
# 连不上时仍输出 JSON，退出码 4；adb 连上但命令失败退出码 2。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

SERIAL=""
DEEP=0
JSON=0
for a in "$@"; do
  case "$a" in
    --deep) DEEP=1 ;;
    --json) JSON=1 ;;
    *) SERIAL="$a" ;;
  esac
done

jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
jstr_or_null() { if [[ -n "$1" ]]; then printf '"%s"' "$(jesc "$1")"; else printf 'null'; fi; }

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(tv_device)"
  if [[ -z "$SERIAL" ]]; then
    SERIAL="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null)" || true
  fi
fi

if [[ -z "$SERIAL" ]]; then
  if [[ "$JSON" == 1 ]]; then
    printf '{"state":"off","wakefulness":null,"screen":null,"focus":null,"uptime_sec":null,"model":%s}\n' \
      "$(jstr_or_null "$(state_get tv_model)")"
    exit 4
  fi
  echo "状态: 连不上电视（关机/断电，或 ADB 没开）"
  echo "说明: 遥控器按「关机」通常只是熄屏待机、ADB 往往还通；彻底断电才会连不上。"
  exit 4
fi

out="$(TV_ADB_TIMEOUT=6 tv_adb -s "$SERIAL" shell '
echo "UP=$(cut -d. -f1 /proc/uptime)"
echo "WAKE=$(dumpsys power | grep -m1 "mWakefulness=" | sed "s/.*mWakefulness=//")"
echo "SCREEN=$(dumpsys display | grep -o "mScreenState=[A-Z]*" | head -1 | cut -d= -f2)"
echo "FOCUS=$(dumpsys window | grep -m1 "mCurrentFocus" | sed "s/.*u0 //;s/}.*//")"
echo "MODEL=$(getprop ro.product.model)"
' 2>/dev/null | tr -d '\r')"

get() { printf '%s\n' "$out" | awk -F= -v k="$1" '$1==k {sub(/^[^=]*=/,""); print; exit}'; }

up=$(get UP); wake=$(get WAKE); screen=$(get SCREEN); focus=$(get FOCUS); model=$(get MODEL)

# 不同 ROM/fake adb 可能给出小数 uptime；统一取整数秒，后面的算术和 JSON 都安全。
case "$up" in
  *.*) up="${up%%.*}" ;;
esac
case "$up" in
  *[!0-9]*) up="" ;;
esac

if [[ -z "$up" ]]; then
  if [[ "$JSON" == 1 ]]; then
    printf '{"state":"off","wakefulness":null,"screen":null,"focus":null,"uptime_sec":null,"model":%s}\n' \
      "$(jstr_or_null "$(state_get tv_model)")"
    exit 2
  fi
  echo "状态: adb 连上了但命令失败（电视可能正在重启/待机）"
  exit 2
fi

# 老电视/一次性取回失败时，模型再补一次（只影响 --json 的 model 字段）。
if [[ -z "$model" ]]; then
  model="$(state_get tv_model)"
  if [[ -z "$model" ]]; then
    model="$(TV_ADB_TIMEOUT=4 tv_adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  fi
fi

case "$wake/$screen" in
  Awake/ON) state="开机中（亮屏）" ;;
  Awake/OFF | Dozing/OFF | Asleep/OFF)
    state="熄屏待机（系统仍在跑，可被遥控器/网络唤醒）" ;;
  *) state="未知（mWakefulness=${wake:-?} mScreenState=${screen:-?}）" ;;
esac

if [[ "$JSON" == 1 ]]; then
  if [[ "$wake" == "Awake" && "$screen" == "ON" ]]; then
    jstate=booted
  elif [[ "$wake" == "Awake" || "$wake" == "Dozing" || "$wake" == "Asleep" ]]; then
    jstate=standby
  else
    jstate=off
  fi
  if [[ "$up" =~ ^[0-9]+$ ]]; then
    uptime_json="$up"
  else
    uptime_json=null
  fi
  printf '{"state":"%s","wakefulness":%s,"screen":%s,"focus":%s,"uptime_sec":%s,"model":%s}\n' \
    "$jstate" "$(jstr_or_null "$wake")" "$(jstr_or_null "$screen")" \
    "$(jstr_or_null "$focus")" "$uptime_json" "$(jstr_or_null "$model")"
  exit 0
fi

d=$((up / 86400)); h=$(((up % 86400) / 3600)); m=$(((up % 3600) / 60))
echo "状态: $state"
echo "开机: ${d} 天 ${h} 小时 ${m} 分（uptime ${up}s）"
[[ -n "$focus" ]] && echo "前台: $focus"

if [[ "$DEEP" == 1 ]]; then
  mem="$(TV_ADB_TIMEOUT=10 tv_adb -s "$SERIAL" shell 'dumpsys meminfo 2>/dev/null | grep -m1 "Realtime"' 2>/dev/null | tr -d '\r')"
  rt=$(printf '%s' "$mem" | sed -n 's/.*Realtime: \([0-9]*\).*/\1/p')
  ut=$(printf '%s' "$mem" | sed -n 's/.*Uptime: \([0-9]*\).*/\1/p')
  if [[ -n "$rt" && -n "$ut" ]]; then
    diff=$(((rt - ut) / 1000))
    if ((diff > 120)); then
      echo "睡眠: 累计深度睡眠约 $((diff / 60)) 分钟"
    else
      echo "睡眠: 从未深度睡眠（Uptime≈Realtime；熄屏也只是系统不休眠的待机）"
    fi
  else
    echo "睡眠: 读不到（meminfo 失败）"
  fi
fi
