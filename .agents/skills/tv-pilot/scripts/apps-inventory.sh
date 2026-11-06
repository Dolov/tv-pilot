#!/usr/bin/env bash
# 电视应用清单：现状 / 与快照比对 / 更新快照
#
#   apps-inventory.sh list          当前清单（默认只列第三方 + 总数）
#   apps-inventory.sh list --all    连系统包一起列
#   apps-inventory.sh diff          和 references/devices/xiaomi-mitv-nsop0-apps.txt 比对（有变化退出码 1）
#   apps-inventory.sh update        用当前状态覆盖快照（会先打印差异）
#
# 快照文件格式：  类型<TAB>包名     T=第三方/预装数据分区  S=系统
# 文档（人看的表格 + 维护约定）：references/devices/xiaomi-mitv-nsop0-apps.md
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAP="$SKILL_DIR/references/devices/xiaomi-mitv-nsop0-apps.txt"
ADB="bash $SKILL_DIR/scripts/adb.sh"

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

cmd_list() {
  local cur; cur="$(fetch_current)"
  local total thirdn
  total=$(printf '%s\n' "$cur" | wc -l | tr -d ' ')
  thirdn=$(printf '%s\n' "$cur" | grep -c '^T' || true)
  echo "总数 ${total}（系统 $((total - thirdn)) / 第三方+预装 ${thirdn}）"
  echo "--- 第三方 + 预装数据分区 ---"
  printf '%s\n' "$cur" | grep '^T' | cut -f2
  if [[ "${1:-}" == "--all" ]]; then
    echo "--- 系统 ---"
    printf '%s\n' "$cur" | grep '^S' | cut -f2
  fi
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

cmd_diff() {
  [[ -f "$SNAP" ]] || { echo "快照不存在：$SNAP（先跑 update）" >&2; exit 2; }
  local cur; cur="$(fetch_current)"
  local out rc=0
  out="$(diff_lines "$SNAP" "$cur")" || rc=1
  if [[ $rc -eq 0 ]]; then
    echo "与快照一致（$(snapshot_pkgs "$SNAP" | wc -l | tr -d ' ') 个包）"
  else
    echo "和快照不一致："
    printf '%s\n' "$out"
    echo
    echo "确认后跑 apps-inventory.sh update 更新快照，并同步修改"
    echo "$(dirname "$SNAP")/$(basename "$SNAP" .txt).md 顶部快照时间，新增包补用途"
  fi
  return $rc
}

cmd_update() {
  local cur fw date
  cur="$(fetch_current)"
  fw="$($ADB shell getprop ro.build.version.incremental 2>/dev/null | tr -d '\r')"
  date="$(date +%Y-%m-%d)"
  local total thirdn
  total=$(printf '%s\n' "$cur" | wc -l | tr -d ' ')
  thirdn=$(printf '%s\n' "$cur" | grep -c '^T' || true)
  if [[ -f "$SNAP" ]]; then
    local out rc=0
    out="$(diff_lines "$SNAP" "$cur")" || rc=1
    if [[ $rc -eq 1 ]]; then echo "本次变化："; printf '%s\n' "$out"; else echo "无变化"; fi
  fi
  {
    echo "# 快照: $date 固件 ${fw:-未知}  总数 ${total}（三方 ${thirdn}）"
    echo "# 格式: 类型<TAB>包名   T=第三方/预装数据分区  S=系统"
    printf '%s\n' "$cur"
  } > "$SNAP"
  echo "已写入 $SNAP"
  echo "记得同步 $(dirname "$SNAP")/xiaomi-mitv-nsop0-apps.md 的表格与快照时间"
}

case "${1:-list}" in
  list)   ensure_tv; cmd_list "${2:-}" ;;
  diff)   ensure_tv; cmd_diff ;;
  update) ensure_tv; cmd_update ;;
  *) echo "用法: $0 list [--all] | diff | update" >&2; exit 1 ;;
esac
