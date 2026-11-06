#!/usr/bin/env bash
# 电视应用清单：现状 / 与快照比对 / 更新快照
#
#   apps-inventory.sh list [--all] [--json]
#       当前清单（默认只列第三方 + 总数；--json 输出全部包 + type）
#   apps-inventory.sh diff [--json]
#       和解析出的快照比对（有变化退出码 1）
#   apps-inventory.sh update
#       用当前状态覆盖快照（会先打印差异）
#   apps-inventory.sh <cmd> --profile <slug>
#       强制使用指定机型档案（仓库 references/devices/<slug>-apps.txt 或本地覆盖）
#
# 快照解析优先级：
#   --profile <slug> > provider tvp_apps_snapshot > 仓库 <slug>-apps.txt
#   > 本地 $TVPILOT_DEVICES_DIR/<slug>-apps.txt > 仓库只有一份 *-apps.txt 时用它
# 快照文件格式：  类型<TAB>包名     T=第三方/预装数据分区  S=系统
# 文档（人看的表格 + 维护约定）：references/devices/<slug>-apps.md
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"
DEV_DIR="$SKILL_DIR/references/devices"
LOCAL_DIR="$TVPILOT_DEVICES_DIR"
ADB="bash $SKILL_DIR/scripts/adb.sh"

PROFILE=""
JSON=0
ALL=0
CMD=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    --profile=*)
      PROFILE="${1#*=}"
      shift
      ;;
    --json)
      JSON=1
      shift
      ;;
    --all)
      ALL=1
      shift
      ;;
    list | diff | update)
      CMD="$1"
      shift
      ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done
CMD="${CMD:-list}"
[[ -n "$PROFILE" && "$PROFILE" == */* ]] && {
  echo "--profile 需要档案 slug（如 xiaomi-mitv-nsop0），不是路径: $PROFILE" >&2
  exit 1
}

ensure_tv() {
  bash "$SKILL_DIR/scripts/ensure-tv.sh" >/dev/null 2>&1 || {
    echo "连不上电视：先跑 scripts/ensure-tv.sh -v 看卡在哪一级" >&2
    exit 2
  }
}

fetch_current() {  # stdout: 类型<TAB>包名（已排序）
  local third all p
  third="$(mktemp)"; all="$(mktemp)"
  $ADB shell pm list packages -3 2>/dev/null | sed 's/package://' | tr -d '\r' | sort > "$third"
  $ADB shell pm list packages    2>/dev/null | sed 's/package://' | tr -d '\r' | sort > "$all"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    if grep -qxF "$p" "$third"; then printf 'T\t%s\n' "$p"; else printf 'S\t%s\n' "$p"; fi
  done < "$all"
  rm -f "$third" "$all"
}

snapshot_pkgs() {  # 从快照文件取「类型<TAB>包名」
  grep -v '^#' "$1" | grep -v '^$'
}

jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# 解析快照路径；$1 = slug（可为空，--profile 会把它钉住）。
# 优先级：provider > 仓库 <slug>-apps.txt > 本地 <slug>-apps.txt；
# 只有 slug 未知时才用「仓库唯一一份 *-apps.txt」兜底。成功时 stdout 是路径。
resolve_snapshot() {
  local slug="${1:-}"
  local repo local_snap prov f n=0 matches=()

  # 0) 显式 --profile：优先用这个 slug 的仓库/本地快照（参数本身高于自动 provider）。
  if [[ -n "$PROFILE" ]]; then
    repo="$DEV_DIR/$PROFILE-apps.txt"
    if [[ -f "$repo" ]]; then printf '%s\n' "$repo"; return 0; fi
    local_snap="$LOCAL_DIR/$PROFILE-apps.txt"
    if [[ -f "$local_snap" ]]; then printf '%s\n' "$local_snap"; return 0; fi
    tvp_load_provider "$PROFILE"
    prov="$(tvp_provider_call tvp_apps_snapshot)"
    if [[ -n "$prov" && -f "$prov" ]]; then printf '%s\n' "$prov"; return 0; fi
    return 1
  fi

  # 1) provider
  tvp_load_provider "$slug"
  prov="$(tvp_provider_call tvp_apps_snapshot)"
  if [[ -n "$prov" ]]; then
    if [[ -f "$prov" ]]; then
      printf '%s\n' "$prov"
      return 0
    fi
    echo "[apps-inventory] provider 返回的快照不存在，继续找仓库/本地: $prov" >&2
  fi

  # 2) 仓库 <slug>-apps.txt；3) 本地 <slug>-apps.txt
  if [[ -n "$slug" ]]; then
    repo="$DEV_DIR/$slug-apps.txt"
    if [[ -f "$repo" ]]; then printf '%s\n' "$repo"; return 0; fi
    local_snap="$LOCAL_DIR/$slug-apps.txt"
    if [[ -f "$local_snap" ]]; then printf '%s\n' "$local_snap"; return 0; fi
  fi

  # 4) 只有 slug 未知（没识别到档案）时才用「仓库唯一一份快照」兜底，
  #    避免把 xiaomi 的快照误当成别的机型。
  if [[ -z "$slug" ]]; then
    for f in "$DEV_DIR"/*-apps.txt; do
      [[ -f "$f" ]] || continue
      matches[$n]="$f"
      n=$((n + 1))
    done
    if [[ "$n" -eq 1 ]]; then
      printf '%s\n' "${matches[0]}"
      return 0
    fi
  fi
  return 1
}

diff_lines() {  # $1=旧  $2=新  → 输出 +新增 / -移除
  local old new
  old="$(mktemp)"; new="$(mktemp)"
  snapshot_pkgs "$1" | cut -f2 | sort > "$old"
  printf '%s\n' "$2" | cut -f2 | sort > "$new"
  local changed=0
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    if ! grep -qxF "$p" "$old"; then echo "+ $p"; changed=1; fi
  done < "$new"
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    if ! grep -qxF "$p" "$new"; then echo "- $p"; changed=1; fi
  done < "$old"
  rm -f "$old" "$new"
  return $changed
}

diff_json() {  # $1=旧快照  $2=新内容；stdout JSON，返回 1 表示有变化
  local old new p added="" removed="" first changed=0
  old="$(mktemp)"; new="$(mktemp)"
  snapshot_pkgs "$1" | cut -f2 | sort > "$old"
  printf '%s\n' "$2" | cut -f2 | sort > "$new"
  first=1
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    if ! grep -qxF "$p" "$old"; then
      [[ "$first" == 1 ]] || added="$added,"
      added="$added\"$(jesc "$p")\""
      first=0
      changed=1
    fi
  done < "$new"
  first=1
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    if ! grep -qxF "$p" "$new"; then
      [[ "$first" == 1 ]] || removed="$removed,"
      removed="$removed\"$(jesc "$p")\""
      first=0
      changed=1
    fi
  done < "$old"
  printf '{"added":[%s],"removed":[%s],"snapshot":"%s"}\n' \
    "$added" "$removed" "$(jesc "$1")"
  rm -f "$old" "$new"
  return $changed
}

cmd_list() {
  local cur; cur="$(fetch_current)"
  local total thirdn
  total=$(printf '%s\n' "$cur" | awk 'NF {n++} END {print n+0}')
  thirdn=$(printf '%s\n' "$cur" | awk -F'\t' '$1=="T" {n++} END {print n+0}')
  if [[ "$JSON" == 1 ]]; then
    local sep="" type pkg
    printf '{"packages":['
    while IFS=$'\t' read -r type pkg; do
      [[ -z "$pkg" ]] && continue
      printf '%s{"type":"%s","pkg":"%s"}' "$sep" "$(jesc "$type")" "$(jesc "$pkg")"
      sep=","
    done <<<"$cur"
    printf '],"total":%s,"third_party":%s}\n' "$total" "$thirdn"
    return 0
  fi
  echo "总数 ${total}（系统 $((total - thirdn)) / 第三方+预装 ${thirdn}）"
  echo "--- 第三方 + 预装数据分区 ---"
  printf '%s\n' "$cur" | grep '^T' | cut -f2
  if [[ "$ALL" == 1 ]]; then
    echo "--- 系统 ---"
    printf '%s\n' "$cur" | grep '^S' | cut -f2
  fi
}

cmd_diff() {
  local slug="${PROFILE:-}"
  [[ -z "$slug" ]] && slug="$(tvp_profile_slug)"
  local snap
  if ! snap="$(resolve_snapshot "$slug")"; then
    if [[ -n "$PROFILE" ]]; then
      echo "profile '$PROFILE' 没有应用快照（provider / 仓库 / 本地都没找到）。" >&2
    else
      echo "找不到应用快照：先跑 apps-inventory.sh update，或用 --profile <slug> 指定机型。" >&2
    fi
    echo "  仓库: $DEV_DIR/<slug>-apps.txt" >&2
    echo "  本地: $LOCAL_DIR/<slug>-apps.txt" >&2
    exit 4
  fi
  if [[ ! -f "$snap" ]]; then
    echo "快照不存在：${snap}（先跑 update）" >&2
    exit 4
  fi
  local cur; cur="$(fetch_current)"
  if [[ "$JSON" == 1 ]]; then
    local rc=0
    diff_json "$snap" "$cur" || rc=1
    return $rc
  fi
  local out rc=0
  out="$(diff_lines "$snap" "$cur")" || rc=1
  if [[ $rc -eq 0 ]]; then
    echo "与快照一致（$(snapshot_pkgs "$snap" | wc -l | tr -d ' ') 个包）"
  else
    echo "和快照不一致："
    printf '%s\n' "$out"
    echo
    echo "确认后跑 apps-inventory.sh update 更新快照，并同步修改"
    echo "${snap%.txt}.md 顶部快照时间，新增包补用途"
  fi
  return $rc
}

cmd_update() {
  local slug="${PROFILE:-}"
  [[ -z "$slug" ]] && slug="$(tvp_profile_slug)"
  local snap
  if ! snap="$(resolve_snapshot "$slug")"; then
    local want="${PROFILE:-$slug}"
    if [[ -n "$want" ]]; then
      snap="$DEV_DIR/$want-apps.txt"
      echo "快照不存在，将新建: $snap" >&2
    else
      echo "找不到应用快照，且无法确定机型；用 --profile <slug> 指定后重试。" >&2
      exit 4
    fi
  fi
  local cur fw date
  cur="$(fetch_current)"
  fw="$($ADB shell getprop ro.build.version.incremental 2>/dev/null | tr -d '\r')"
  date="$(date +%Y-%m-%d)"
  local total thirdn
  total=$(printf '%s\n' "$cur" | awk 'NF {n++} END {print n+0}')
  thirdn=$(printf '%s\n' "$cur" | awk -F'\t' '$1=="T" {n++} END {print n+0}')
  if [[ -f "$snap" ]]; then
    local out rc=0
    out="$(diff_lines "$snap" "$cur")" || rc=1
    if [[ $rc -eq 1 ]]; then echo "本次变化："; printf '%s\n' "$out"; else echo "无变化"; fi
  fi
  mkdir -p "$(dirname "$snap")" 2>/dev/null || true
  {
    echo "# 快照: $date 固件 ${fw:-未知}  总数 ${total}（三方 ${thirdn}）"
    echo "# 格式: 类型<TAB>包名   T=第三方/预装数据分区  S=系统"
    printf '%s\n' "$cur"
  } > "$snap" || {
    echo "写入快照失败：$snap" >&2
    exit 2
  }
  echo "已写入 $snap"
  echo "记得同步 ${snap%.txt}.md 的表格与快照时间"
}

case "$CMD" in
  list)
    ensure_tv
    cmd_list
    ;;
  diff)
    ensure_tv
    cmd_diff
    ;;
  update)
    ensure_tv
    cmd_update
    ;;
esac
