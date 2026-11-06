#!/usr/bin/env bash
# 只读识别电视，并匹配设备档案。
#
# 档案来源（先本地覆盖层，再仓库）：
#   $TVPILOT_DEVICES_DIR/*.md       默认 ~/.config/tv-pilot/devices（本地档案，不提交）
#   references/devices/*.md         仓库档案
# 同名档案以本地为准（本地覆盖层）；匹配优先级：model/device/fingerprint 精确 >
# 同品牌 similar > unknown。
#
# 用法: identify-tv.sh [IP[:PORT]] [--json|--kv] [-v]
#   不传 IP 时自动走 ensure-tv 阶梯
#   --json  给脚本/代理消费的 JSON（字段见 docs/json-contract.md）
#   --kv    每行 key=value（device-profile.sh init / lib.sh 用）
#
# 命中后把 tv_profile（slug）/ tv_brand / tv_model / tv_match 写入 state 缓存；
# state 不可写时静默忽略，不影响本次输出。
#
# 这一步只读：不写设置、不装应用、不禁包。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"
DEV_DIR="$SKILL_DIR/references/devices"
LOCAL_DEV_DIR="$TVPILOT_DEVICES_DIR"

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
  exit 4
fi

# 2) 一次 adb shell 往返取设备指纹（只读）
CMD='echo @@MODEL@@; getprop ro.product.model; echo @@BRAND@@; getprop ro.product.brand; echo @@DEVICE@@; getprop ro.product.device; echo @@SDK@@; getprop ro.build.version.sdk; echo @@FP@@; getprop ro.build.fingerprint; echo @@HOME@@; cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tail -1'
raw="$(tv_adb -s "$dev" shell "$CMD" 2>/dev/null | tr -d '\r')"
if [[ -z "$raw" ]]; then
  echo "adb 连接中断（${dev}）。电视可能刚待机/重启，重跑一次即可。" >&2
  exit 2
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
fm_model() { fm_field "$1" model; }
profile_slug_of() {
  local s
  s="$(fm_field "$1" profile)"
  [[ -n "$s" ]] || s="$(basename "$1" .md)"
  printf '%s' "$s"
}

# 精确匹配：frontmatter match 里的 token（通常是 ro.product.model / device / fingerprint 片段）
# 以及 frontmatter model 本身。大小写不敏感。
match_exact() { # file
  local f="$1" p pl
  for p in $(fm_match_tokens "$f") $(fm_model "$f"); do
    pl="$(lower "$p")"
    [[ -z "$pl" ]] && continue
    if [[ "$L_MODEL" == "$pl" || "$L_DEVICE" == "$pl" || "$L_FP" == *"$pl"* ]]; then
      return 0
    fi
  done
  return 1
}

skip_profile_file() { # basename
  case "$1" in
    _* | README.md | *-apps.md) return 0 ;;
  esac
  return 1
}

# 输出 "source<TAB>path"：本地覆盖层在前；仓库里与本地同名的文件跳过（本地优先）。
profile_files() {
  local f base
  if [[ -d "$LOCAL_DEV_DIR" ]]; then
    for f in "$LOCAL_DEV_DIR"/*.md; do
      [[ -f "$f" ]] || continue
      base="$(basename "$f")"
      skip_profile_file "$base" && continue
      printf 'local\t%s\n' "$f"
    done
  fi
  for f in "$DEV_DIR"/*.md; do
    [[ -f "$f" ]] || continue
    base="$(basename "$f")"
    skip_profile_file "$base" && continue
    [[ -f "$LOCAL_DEV_DIR/$base" ]] && continue
    printf 'repo\t%s\n' "$f"
  done
}

P_SRC=()
P_PATH=()
P_COUNT=0
while IFS=$'\t' read -r src path; do
  [[ -n "$path" ]] || continue
  P_SRC[$P_COUNT]="$src"
  P_PATH[$P_COUNT]="$path"
  P_COUNT=$((P_COUNT + 1))
done < <(profile_files)

# 3) 匹配：先精确（model/device/fingerprint），再同品牌 similar
PROFILE=""
PROFILE_SRC=""
i=0
while [[ "$i" -lt "$P_COUNT" ]]; do
  if match_exact "${P_PATH[$i]}"; then
    PROFILE="${P_PATH[$i]}"
    PROFILE_SRC="${P_SRC[$i]}"
    break
  fi
  i=$((i + 1))
done

SIMILAR_PATH=()
SIMILAR_SRC=()
SIMILAR_COUNT=0
i=0
while [[ "$i" -lt "$P_COUNT" ]]; do
  f="${P_PATH[$i]}"
  if [[ "$f" != "$PROFILE" ]]; then
    pb="$(lower "$(fm_field "$f" brand)")"
    if [[ -n "$pb" && "$pb" == "$L_BRAND" ]]; then
      SIMILAR_PATH[$SIMILAR_COUNT]="$f"
      SIMILAR_SRC[$SIMILAR_COUNT]="${P_SRC[$i]}"
      SIMILAR_COUNT=$((SIMILAR_COUNT + 1))
    fi
  fi
  i=$((i + 1))
done

if [[ -n "$PROFILE" ]]; then
  STATUS=matched
elif [[ "$SIMILAR_COUNT" -gt 0 ]]; then
  STATUS=similar
else
  STATUS=unknown
fi

rel() { printf '%s' "${1#"$SKILL_DIR"/}"; }
abs_path() {
  local d b
  d="$(cd "$(dirname "$1")" 2>/dev/null && pwd)" || { printf '%s' "$1"; return 0; }
  b="$(basename "$1")"
  printf '%s/%s' "${d%/}" "$b"
}
profile_display() { # path source
  if [[ "$2" == "local" ]]; then abs_path "$1"; else rel "$1"; fi
}
jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
jstr() { printf '"%s"' "$(jesc "$1")"; }
jstr_or_null() { if [[ -n "$1" ]]; then jstr "$1"; else printf 'null'; fi; }

PROFILE_SLUG=""
PROFILE_SOURCE=""
PROFILE_PATH=""
if [[ -n "$PROFILE" ]]; then
  PROFILE_SLUG="$(profile_slug_of "$PROFILE")"
  PROFILE_SOURCE="$PROFILE_SRC"
  PROFILE_PATH="$(abs_path "$PROFILE")"
fi

# 4) 写 state 缓存（失败不影响输出）
state_set tv_profile "$PROFILE_SLUG"
state_set tv_brand "$BRAND"
state_set tv_model "$MODEL"
state_set tv_match "$STATUS"

# 5) 输出
if [[ "$MODE" == kv ]]; then
  echo "status=$STATUS"
  echo "model=$MODEL"
  echo "brand=$BRAND"
  echo "device=$DEVICE"
  echo "sdk=$SDK"
  echo "fingerprint=$FP"
  echo "home=$HOME_PKG"
  echo "dev=$dev"
  echo "profile_slug=$PROFILE_SLUG"
  echo "profile_path=$PROFILE_PATH"
  echo "profile_source=$PROFILE_SOURCE"
  [[ -n "$PROFILE" ]] && echo "profile=$(profile_display "$PROFILE" "$PROFILE_SRC")"
  if [[ -n "$PROFILE" ]]; then
    fm_caps "$PROFILE" | while IFS= read -r kvline; do echo "cap.${kvline}"; done
  fi
  i=0
  while [[ "$i" -lt "$SIMILAR_COUNT" ]]; do
    echo "similar=$(profile_display "${SIMILAR_PATH[$i]}" "${SIMILAR_SRC[$i]}")"
    i=$((i + 1))
  done
  exit 0
fi

if [[ "$MODE" == json ]]; then
  printf '{\n'
  printf '  "device": {"model": "%s", "brand": "%s", "device": "%s", "sdk": "%s", "fingerprint": "%s", "home": "%s", "target": "%s"},\n' \
    "$(jesc "$MODEL")" "$(jesc "$BRAND")" "$(jesc "$DEVICE")" "$(jesc "$SDK")" "$(jesc "$FP")" "$(jesc "$HOME_PKG")" "$(jesc "$dev")"
  printf '  "model": "%s",\n' "$(jesc "$MODEL")"
  printf '  "status": "%s",\n' "$STATUS"
  printf '  "match": "%s",\n' "$STATUS"
  printf '  "profile": %s,\n' "$(jstr_or_null "$(profile_display "$PROFILE" "$PROFILE_SRC")")"
  printf '  "profile_path": %s,\n' "$(jstr_or_null "$PROFILE_PATH")"
  printf '  "profile_source": %s,\n' "$(jstr_or_null "$PROFILE_SOURCE")"
  printf '  "profile_slug": %s,\n' "$(jstr_or_null "$PROFILE_SLUG")"
  printf '  "capabilities": {'
  if [[ -n "$PROFILE" ]]; then
    first=1
    while IFS= read -r kvline; do
      [[ -z "$kvline" ]] && continue
      k="${kvline%%:*}"
      v="${kvline#*: }"
      [[ "$first" == 1 ]] || printf ', '
      printf '"%s": "%s"' "$(jesc "$k")" "$(jesc "$v")"
      first=0
    done < <(fm_caps "$PROFILE")
  fi
  printf '},\n'
  printf '  "similar": ['
  first=1
  i=0
  while [[ "$i" -lt "$SIMILAR_COUNT" ]]; do
    [[ "$first" == 1 ]] || printf ', '
    printf '"%s"' "$(jesc "$(profile_display "${SIMILAR_PATH[$i]}" "${SIMILAR_SRC[$i]}")")"
    first=0
    i=$((i + 1))
  done
  printf ']\n}\n'
  exit 0
fi

echo "设备: ${MODEL:-?}（brand=${BRAND:-?}, codename=${DEVICE:-?}, sdk=${SDK:-?}）"
[[ -n "$HOME_PKG" ]] && echo "桌面: ${HOME_PKG%%/*}"
case "$STATUS" in
  matched)
    if [[ "$PROFILE_SRC" == "local" ]]; then
      echo "档案: $(profile_display "$PROFILE" "$PROFILE_SRC")（本地覆盖）  ✅ matched"
    else
      echo "档案: $(profile_display "$PROFILE" "$PROFILE_SRC")  ✅ matched"
    fi
    echo "能力:"
    fm_caps "$PROFILE" | while IFS= read -r kvline; do echo "  $kvline"; done
    echo "下一步: 按档案执行；capabilities 不是 verified 的项先只读、写操作前跟用户确认。"
    ;;
  similar)
    echo "档案: 未精确匹配（没有型号等于 ${MODEL} 的档案）"
    echo "同品牌候选（未在本机实测，包名/键值可能不同）:"
    i=0
    while [[ "$i" -lt "$SIMILAR_COUNT" ]]; do
      echo "  - $(profile_display "${SIMILAR_PATH[$i]}" "${SIMILAR_SRC[$i]}")"
      i=$((i + 1))
    done
    echo "下一步: 可参考品牌通用经验；要正式支持该型号，按 references/porting.md 接入。"
    ;;
  unknown)
    echo "档案: 无（库里没有这个品牌/型号）"
    echo "下一步: 只读能力可用（连接/状态/通用探测/通用使用统计）；"
    echo "        禁止禁包、settings put、装 App、做屏保角标——这些依赖实测过的档案。"
    echo "        新机型接入: 读 references/porting.md，或 bash scripts/device-profile.sh init"
    ;;
esac
