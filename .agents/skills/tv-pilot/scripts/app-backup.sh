#!/usr/bin/env bash
# TV mini-App 项目保底备份/恢复（防止工作区被清空后源码丢失）
#
#   app-backup.sh backup  <项目目录> [--name tvhud]   备份到 assets/<name>/
#   app-backup.sh restore <name> [<目标目录>]         恢复到目标目录（默认当前目录）
#   app-backup.sh list                                已有哪些备份
#
# 工作区项目里的 build.sh 成功后会默认调用一次 backup（TVPILOT_AUTO_BACKUP=0 可关闭）。
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_ROOT="$SKILL_DIR/assets"

cmd_backup() {
  local src="${1:-}" name=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      *) src="$1"; shift ;;
    esac
  done
  [[ -d "$src" ]] || { echo "项目目录不存在: $src" >&2; exit 1; }
  [[ -n "$name" ]] || name="$(basename "$src")"
  mkdir -p "$DEST_ROOT/$name"
  rsync -a --delete --exclude '.build' --exclude 'out' "$src"/ "$DEST_ROOT/$name"/
  echo "已备份 ${src} → assets/${name}/（$(du -sh "$DEST_ROOT/$name" | cut -f1)）"
}

cmd_restore() {
  local name="${1:-}" dest="${2:-.}"
  [[ -n "$name" ]] || { echo "用法: $0 restore <name> [目标目录]" >&2; exit 1; }
  [[ -d "$DEST_ROOT/$name" ]] || { echo "没有这个备份: assets/$name" >&2; exit 1; }
  mkdir -p "$dest"
  rsync -a "$DEST_ROOT/$name"/ "$dest"/
  echo "已恢复 assets/$name → $dest"
}

cmd_list() {
  echo "备份目录: $DEST_ROOT"
  for d in "$DEST_ROOT"/*/; do
    [[ -d "$d" ]] || continue
    echo "  - $(basename "$d")  ($(du -sh "$d" | cut -f1), $(find "$d" -type f | wc -l | tr -d ' ') 个文件)"
  done
}

case "${1:-list}" in
  backup)  shift; cmd_backup "$@" ;;
  restore) shift; cmd_restore "$@" ;;
  list)    cmd_list ;;
  *) echo "用法: $0 backup <项目目录> [--name x] | restore <name> [目标] | list" >&2; exit 1 ;;
esac
