#!/usr/bin/env bash
# 只读识别电视，并匹配 references/devices/ 里的设备档案。
# 输出三种结论：matched（精确命中）/ similar（同品牌不同型号）/ unknown（没有档案）。
#
# 用法: identify-tv.sh [IP[:PORT]] [--json|--kv] [-v]
#   不传 IP 时自动走 ensure-tv 阶梯
#   --json  给脚本/代理消费的 JSON
#   --kv    每行 key=value（device-profile.sh init 用）
#
# 这一步只读：不写设置、不装应用、不禁包。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"
DEV_DIR="$SKILL_DIR/references/devices"

MODE=human
TARGET=""
VERBOSE=0
for a in "$@"; do
  case "$a" in
    --json) MODE=json ;;
    --kv) MODE=kv ;;
    -v | --verbose) VERBOSE=1 ;;
    -*) ;;
    *) TARGET="$a" ;;
  esac
done
log() { [[ "$VERBOSE" == 1 ]] && echo "[identify] $*" >&2; return 0; }

# 1) 拿连接
if [[ -n "$TARGET" ]]; then
  [[ "$TARGET" == *:* ]] || TARGET="$TARGET:${TV_PORT:-5555}"
  dev="$TARGET"
  TV_ADB_TIMEOUT=5 tv_adb connect "$dev" >/dev/null 2>&1 || true
else
  log "找连接"
  dev="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null || true)"
fi
if [[ -z "$dev" ]]; then
  echo "找不到可用电视连接；先跑 scripts/ensure-tv.sh -v 看卡在哪一级。" >&2
  exit 1
fi

# 2) 一次 adb shell 往返取设备指纹（只读）
CMD='echo @@MODEL@@; getprop ro.product.model; echo @@BRAND@@; getprop ro.product.brand; echo @@DEVICE@@; getprop ro.product.device; echo @@SDK@@; getprop ro.build.version.sdk; echo @@FP@@; getprop ro.build.fingerprint; echo @@HOME@@; cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tail -1'
raw="$(tv_adb -s "$dev" shell "$CMD" 2>/dev/null | tr -d '\r')"
if [[ -z "$raw" ]]; then
  echo "adb 连接中断（${dev}）。电视可能刚待机/重启，重跑一次即可。" >&2
  exit 1
fi
field() { printf '%s\n' "$raw" | awk -v k="$1" '$0==k {getline; print; exit}'; }
MODEL="$(field @@MODEL@@)"
BRAND="$(field @@BRAND@@)"
DEVICE="$(field @@DEVICE@@)"
SDK="$(field @@SDK@@)"
FP="$(field @@FP@@)"
HOME_PKG="$(field @@HOME@@)"

lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
L_MODEL="$(lower "$MODEL")"
L_DEVICE="$(lower "$DEVICE")"
L_BRAND="$(lower "$BRAND")"
L_FP="$(lower "$FP")"

fm_for() {
  awk 'NR==1 && $0=="---" {inside=1; next} inside && $0=="---" {exit} inside {print}' "$1" 2>/dev/null
}
fm_field() { # file key
  fm_for "$1" | awk -F': *' -v k="$2" '$1==k {sub(/^[^:]*:[[:space:]]*/,""); print; exit}'
}
fm_match_tokens() { # file -> 空格分隔的匹配串
  fm_field "$1" match | sed -e 's/^\[//' -e 's/\]$//' -e 's/"//g' -e 's/,/ /g'
}
fm_caps() { # file -> "k=v" 每行
  fm_for "$1" | awk '
    /^capabilities:/ {in_caps=1; next}
    in_caps && /^[[:space:]]+[a-z_]+:/ {line=$0; sub(/^[[:space:]]+/,"",line); print line; next}
    in_caps && /^[^[:space:]]/ {in_caps=0}
  '
}

# 3) 匹配档案：先精确（model/device/fingerprint），再同品牌
PROFILE=""
SIMILAR=""
for f in "$DEV_DIR"/*.md; do
  [[ -f "$f" ]] || continue
  case "$(basename "$f")" in _* | README.md) continue ;; esac
  hit=0
  for p in $(fm_match_tokens "$f"); do
    pl="$(lower "$p")"
    [[ -z "$pl" ]] && continue
    if [[ "$L_MODEL" == "$pl" || "$L_DEVICE" == "$pl" || "$L_FP" == *"$pl"* ]]; then
      hit=1
      break
    fi
  done
  if [[ "$hit" == 1 ]]; then
    PROFILE="$f"
    break
  fi
  pb="$(lower "$(fm_field "$f" brand)")"
  if [[ -n "$pb" && "$pb" == "$L_BRAND" ]]; then
    SIMILAR="$SIMILAR $f"
  fi
done

if [[ -n "$PROFILE" ]]; then
  STATUS=matched
elif [[ -n "$SIMILAR" ]]; then
  STATUS=similar
else
  STATUS=unknown
fi

rel() { printf '%s' "${1#"$SKILL_DIR"/}"; }
jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# 4) 输出
if [[ "$MODE" == kv ]]; then
  echo "status=$STATUS"
  echo "model=$MODEL"
  echo "brand=$BRAND"
  echo "device=$DEVICE"
  echo "sdk=$SDK"
  echo "fingerprint=$FP"
  echo "home=$HOME_PKG"
  echo "dev=$dev"
  [[ -n "$PROFILE" ]] && echo "profile=$(rel "$PROFILE")"
  if [[ -n "$PROFILE" ]]; then
    fm_caps "$PROFILE" | while IFS= read -r kvline; do echo "cap.${kvline}"; done
  fi
  for f in $SIMILAR; do echo "similar=$(rel "$f")"; done
  exit 0
fi

if [[ "$MODE" == json ]]; then
  printf '{\n'
  printf '  "device": {"model": "%s", "brand": "%s", "device": "%s", "sdk": "%s", "fingerprint": "%s", "home": "%s", "target": "%s"},\n' \
    "$(jesc "$MODEL")" "$(jesc "$BRAND")" "$(jesc "$DEVICE")" "$(jesc "$SDK")" "$(jesc "$FP")" "$(jesc "$HOME_PKG")" "$(jesc "$dev")"
  printf '  "status": "%s",\n' "$STATUS"
  if [[ -n "$PROFILE" ]]; then
    printf '  "profile": "%s",\n' "$(rel "$PROFILE")"
  else
    printf '  "profile": null,\n'
  fi
  printf '  "capabilities": {'
  if [[ -n "$PROFILE" ]]; then
    first=1
    while IFS= read -r kvline; do
      [[ -z "$kvline" ]] && continue
      k="${kvline%%:*}"
      v="${kvline#*: }"
      [[ "$first" == 1 ]] || printf ', '
      printf '"%s": "%s"' "$k" "$v"
      first=0
    done < <(fm_caps "$PROFILE")
  fi
  printf '},\n'
  printf '  "similar": ['
  first=1
  for f in $SIMILAR; do
    [[ "$first" == 1 ]] || printf ', '
    printf '"%s"' "$(rel "$f")"
    first=0
  done
  printf ']\n}\n'
  exit 0
fi

echo "设备: ${MODEL:-?}（brand=${BRAND:-?}, codename=${DEVICE:-?}, sdk=${SDK:-?}）"
[[ -n "$HOME_PKG" ]] && echo "桌面: ${HOME_PKG%%/*}"
case "$STATUS" in
  matched)
    echo "档案: $(rel "$PROFILE")  ✅ matched"
    echo "能力:"
    fm_caps "$PROFILE" | while IFS= read -r kvline; do echo "  $kvline"; done
    echo "下一步: 按档案执行；capabilities 不是 verified 的项先只读、写操作前跟用户确认。"
    ;;
  similar)
    echo "档案: 未精确匹配（没有型号等于 ${MODEL} 的档案）"
    echo "同品牌候选（未在本机实测，包名/键值可能不同）:"
    for f in $SIMILAR; do echo "  - $(rel "$f")"; done
    echo "下一步: 可参考品牌通用经验；要正式支持该型号，按 references/porting.md 接入。"
    ;;
  unknown)
    echo "档案: 无（库里没有这个品牌/型号）"
    echo "下一步: 只读能力可用（连接/状态/通用探测/通用使用统计）；"
    echo "        禁止禁包、settings put、装 App、做屏保角标——这些依赖实测过的档案。"
    echo "        新机型接入: 读 references/porting.md，或 bash scripts/device-profile.sh init"
    ;;
esac
