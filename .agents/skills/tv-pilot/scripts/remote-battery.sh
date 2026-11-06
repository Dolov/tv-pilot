#!/usr/bin/env bash
# 只读：读小米电视蓝牙遥控器电量。
#
# 默认走快路径（<1 秒）：拿连接（ensure-tv 阶梯）→ 一次 adb shell 取型号/配对/电量键
# → 本地解析成结论。logcat 交叉验证是可选的重操作（--deep，约 10-15 秒），
# 只在需要确认低电量阈值/提醒是否触发时用。
#
# 用法: remote-battery.sh [IP[:PORT]] [--deep] [--json]
#   不传 IP 时自动找电视（已连接 → 缓存 IP → 扫描）。
#
# 原理：电量由蓝牙 HID 服务写进 Settings.Global 的 bluetooth_hid_battery_<MAC大写>，
# 遥控器重连/按键时刷新；--deep 时再看 logcat 的 hid_battery_callback /
# onCharacteristicRead.battery / Xaomi RC info 拿原始值与阈值。
#
# 退出码: 0 成功；1 连不上电视；2 连上了但读不到电量（红外遥控器/未配对）
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

JSON=0
DEEP=0
TARGET=""
for a in "$@"; do
  case "$a" in
    --json) JSON=1 ;;
    --deep) DEEP=1 ;;
    -*) ;;
    *) TARGET="$a" ;;
  esac
done

t0=$(now_ms)

# 1) 拿连接：显式 IP 直接连；否则交给 ensure-tv 走缓存/扫描阶梯
if [[ -n "$TARGET" ]]; then
  [[ "$TARGET" == *:* ]] || TARGET="$TARGET:${TV_PORT:-5555}"
  TV_ADB_TIMEOUT=5 tv_adb connect "$TARGET" >/dev/null 2>&1 || true
  st="$(tv_state_of "$TARGET")"
  if [[ "$st" != "device" ]]; then
    echo "连不上 ${TARGET}（状态: ${st:-未知}）。" >&2
    echo "unauthorized = 电视上按「允许」；offline = 电视待机/重启中，先唤醒。" >&2
    exit 1
  fi
  dev="$TARGET"
else
  dev="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null || true)"
  if [[ -z "$dev" ]]; then
    echo "找不到可用电视连接。运行 scripts/ensure-tv.sh -v 可看到卡在哪一级。" >&2
    exit 1
  fi
fi

# 2) 快路径：一次 adb shell 取回型号 / 配对列表 / 电量键（约 0.3s）
CMD='echo "@@MODEL@@"; getprop ro.product.model; echo "@@BOND@@"; dumpsys bluetooth_manager 2>/dev/null | grep -A8 -i "Bonded devices"; echo "@@BATT@@"; settings list global 2>/dev/null | grep bluetooth_hid_battery'
raw="$(tv_adb -s "$dev" shell "$CMD" 2>/dev/null | tr -d '\r')"

if [[ -z "$raw" ]]; then
  echo "adb 连接中断（${dev}）。电视可能刚待机或重启，重跑一次即可。" >&2
  exit 1
fi

model="$(printf '%s\n' "$raw" | sed -n '/^@@MODEL@@$/{n;p;}')"
bond="$(printf '%s\n' "$raw" | awk '/^@@BOND@@$/{f=1;next} /^@@BATT@@$/{f=0} f')"
batt="$(printf '%s\n' "$raw" | sed -n '/^@@BATT@@$/,$p' | tail -n +2)"

batt_line="$(printf '%s\n' "$batt" | grep -v 'count' | head -1)"
if [[ -z "$batt_line" ]]; then
  # 没有 bluetooth_hid_battery_* 键：红外遥控器 / 未配对 / 从未上报
  if [[ "$JSON" == 1 ]]; then
    printf '{"device":"%s","model":"%s","battery":null,"reason":"no_hid_battery_key"}\n' "$dev" "${model:-}"
  else
    echo "电视 ${dev} 上读不到遥控器电量：没有 bluetooth_hid_battery_* 键。"
    echo "可能原因：遥控器是红外（不报电量）、未配对，或这台电视从没收到过上报。"
    echo "去电视 UI 看：设置 → 遥控器/蓝牙。"
  fi
  exit 2
fi

key="${batt_line%%=*}"
pct="${batt_line#*=}"
mac="${key#bluetooth_hid_battery_}"
case "$pct" in
  '' | *[!0-9]*)
    echo "读到的电量值不是数字：${batt_line}" >&2
    exit 2 ;;
esac

# 遥控器名字（Bonded devices 里 MAC 同一行，取 ] 之后的部分）
name="$(printf '%s\n' "$bond" | grep -i "$mac" | head -1 | sed 's/.*\][[:space:]]*//')"
name="${name:-未知型号}"

# 3) 可选深查：拿日志里的原始值、阈值、低电量提醒（约 10-15s，只在需要时跑）
log_pct=""
hex_pct=""
hex_val=""
thr=""
last_report=""
warring=""
if [[ "$DEEP" == 1 ]]; then
  since="$(date -v-30M '+%m-%d %H:%M:%S.000' 2>/dev/null || true)"
  if [[ -n "$since" ]]; then
    LOGCMD="logcat -d -T '$since' 2>/dev/null"
  else
    LOGCMD="logcat -d -t 12000 2>/dev/null"
  fi
  logtxt="$(TV_ADB_TIMEOUT=30 tv_adb -s "$dev" shell "$LOGCMD | grep -E 'hid_battery_callback|onCharacteristicRead.battery|Xaomi RC info|broadcastRcBattery|HID_BATTERY_WARRING' | tail -12" 2>/dev/null | tr -d '\r')"
  last_report="$(printf '%s\n' "$logtxt" | grep -E 'characteristicRead\.battery|hid battery=' | tail -1 \
    | sed -E 's/^([0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}).*/\1/')"
  log_pct="$(printf '%s\n' "$logtxt" | grep -oE 'onCharacteristicRead\.battery= *[0-9]+' | tail -1 | grep -oE '[0-9]+$')"
  hex_pct="$(printf '%s\n' "$logtxt" | grep -oE 'hid battery=0x[0-9A-Fa-f]+' | tail -1 | sed 's/.*0x//')"
  [[ -n "$hex_pct" ]] && hex_val=$((16#$hex_pct))
  thr_hex="$(printf '%s\n' "$logtxt" | grep -oE 'low_battery_thr=0x[0-9A-Fa-f]+' | tail -1 | sed 's/.*0x//')"
  if [[ -n "$thr_hex" ]]; then
    thr=$((16#$thr_hex))
    [[ "$thr" -eq 0 ]] && thr=""
  fi
  printf '%s\n' "$logtxt" | grep -q 'HID_BATTERY_WARRING' && warring="是"
fi
# 没跑深查时用实测阈值兜底（这台机器实测 0x19=25%，见 references/remote-battery.md）
[[ -z "$thr" ]] && thr=25

# 4) 判读
if ((pct >= 50)); then level="正常"; advice="无需处理。"
elif ((pct >= 25)); then level="偏低"; advice="还在阈值之上；备 2×AAA 电池，出现低电量弹窗或掉到 25% 以下就换。"
else level="低电量"; advice="尽快换电池（多数小米语音遥控器是 2×AAA，按电池仓标注）。"; fi

prev="$(state_get remote_battery)"
prev_at="$(state_get remote_updated_at)"
thr_txt="（低电量阈值 ${thr}%）"

# 5) 输出：结论在前，证据其次，≤20 行
if [[ "$JSON" == 1 ]]; then
  printf '{"device":"%s","model":"%s","mac":"%s","name":"%s","battery":%s,"level":"%s","low_threshold":%s,"last_report":"%s","warning_seen":%s,"deep":%s,"elapsed_ms":%s}\n' \
    "$dev" "${model:-}" "$mac" "$name" "$pct" "$level" "${thr:-null}" "$last_report" \
    "$([[ "$warring" == "是" ]] && echo true || echo false)" \
    "$([[ "$DEEP" == 1 ]] && echo true || echo false)" "$(( $(now_ms) - t0 ))"
else
  if [[ -n "$model" ]]; then echo "电视: ${dev}（${model}）"; else echo "电视: ${dev}"; fi
  echo "遥控器: ${mac}  ${name}"
  echo "电量: ${pct}%  —  ${level}${thr_txt}"
  ev="settings 键=${pct}"
  [[ -n "$log_pct" ]] && ev="$ev | 蓝牙回调=${log_pct}"
  [[ -n "$hex_val" ]] && ev="$ev | hid battery=0x${hex_pct}(${hex_val})"
  [[ -n "$last_report" ]] && ev="$ev | 最近上报 ${last_report}"
  [[ -n "$prev" && "$prev" != "$pct" ]] && ev="$ev | 上次读取 ${prev}%（${prev_at}）"
  echo "证据: ${ev}"
  [[ -n "$log_pct" && "$log_pct" -ne "$pct" ]] && echo "注意: 回调值 ${log_pct}% 与 settings 值 ${pct}% 不一致，以 settings（最近一次上报）为准。"
  [[ "$warring" == "是" ]] && echo "注意: 日志里出现过低电量提醒（HID_BATTERY_WARRING）。"
  echo "建议: ${advice}"
  [[ "$DEEP" == 0 ]] && echo "提示: 加 --deep 可查日志原始值/低电量提醒（约 10-15s）。"
  echo "耗时: $(elapsed_fmt "$t0")"
fi

# 6) 写缓存（失败不影响结果）
state_set tv_ip "${dev%%:*}"
state_set tv_model "$model"
state_set remote_mac "$mac"
state_set remote_battery "$pct"
state_set remote_updated_at "$(date +%Y-%m-%dT%H:%M:%S%z)"
exit 0
