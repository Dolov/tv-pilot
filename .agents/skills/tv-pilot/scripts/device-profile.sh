#!/usr/bin/env bash
# 设备档案工具
#
#   device-profile.sh init [--name <slug>] [--local]
#       从 _template.md 生成骨架（读当前连接的设备）。
#       --local 写到 ${TVPILOT_DEVICES_DIR}（默认 ~/.config/tv-pilot/devices，自动 mkdir），
#       不污染仓库；同名本地档案会覆盖 identify-tv 的仓库档案匹配。
#   device-profile.sh lint [file...]
#       校验必填字段 / 章节 / 证据 / 脱敏。
#       默认同时校验仓库档案和本地覆盖档案（本地目录不存在时跳过，不报错）。
#   device-profile.sh index
#       重建 references/devices/README.md 支持矩阵（仍只统计仓库档案）。
#
# 只读：init 只读设备信息；lint/index 只读档案，index 只改本仓库 README。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"
DEV_DIR="$SKILL_DIR/references/devices"
TEMPLATE="$DEV_DIR/_template.md"
README="$DEV_DIR/README.md"
CAP_KEYS="usage_stats screensaver child_mode adb_keepalive app_build remote_battery"
CAP_VALUES="verified|partial|unknown|unsupported"
SECTIONS="设备身份|ADB 接入|桌面与屏保|使用统计|儿童模式|可禁用|已知坑|证据"

usage() {
  cat <<'EOF'
设备档案工具

  device-profile.sh init [--name <slug>] [--local]
      从 _template.md 生成骨架（读当前连接的设备）；--local 写到 $TVPILOT_DEVICES_DIR
  device-profile.sh lint [file...]
      校验必填字段 / 章节 / 证据 / 脱敏；默认仓库档案 + 本地覆盖档案
  device-profile.sh index
      重建 references/devices/README.md 支持矩阵（只统计仓库档案）
EOF
}

disp() { printf '%s' "${1#"$SKILL_DIR"/}"; }

fm_for() { awk 'NR==1 && $0=="---" {inside=1; next} inside && $0=="---" {exit} inside {print}' "$1" 2>/dev/null; }
fm_field() { fm_for "$1" | awk -F': *' -v k="$2" '$1==k {sub(/^[^:]*:[[:space:]]*/,""); print; exit}'; }
fm_caps() {
  fm_for "$1" | awk '
    /^capabilities:/ {in_caps=1; next}
    in_caps && /^[[:space:]]+[a-z_]+:/ {line=$0; sub(/^[[:space:]]+/,"",line); print line; next}
    in_caps && /^[^[:space:]]/ {in_caps=0}
  '
}
slugify() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -e 's/[^a-z0-9]\+/-/g' -e 's/^-//' -e 's/-$//'
}

# ---------- init ----------
cmd_init() {
  local name="" local_mode=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --name) name="$2"; shift 2 ;;
      --local) local_mode=1; shift ;;
      *) shift ;;
    esac
  done
  local kv
  kv="$(bash "$SKILL_DIR/scripts/identify-tv.sh" --kv 2>/dev/null)" || true
  if [[ -z "$kv" ]]; then
    echo "没拿到设备信息：先确保电视连得上（bash scripts/ensure-tv.sh -v）" >&2
    exit 4
  fi
  local brand model device sdk fp home dev
  brand="$(printf '%s\n' "$kv" | awk -F= '$1=="brand"{sub(/^[^=]*=/,""); print; exit}')"
  model="$(printf '%s\n' "$kv" | awk -F= '$1=="model"{sub(/^[^=]*=/,""); print; exit}')"
  device="$(printf '%s\n' "$kv" | awk -F= '$1=="device"{sub(/^[^=]*=/,""); print; exit}')"
  sdk="$(printf '%s\n' "$kv" | awk -F= '$1=="sdk"{sub(/^[^=]*=/,""); print; exit}')"
  fp="$(printf '%s\n' "$kv" | awk -F= '$1=="fingerprint"{sub(/^[^=]*=/,""); print; exit}')"
  home="$(printf '%s\n' "$kv" | awk -F= '$1=="home"{sub(/^[^=]*=/,""); print; exit}')"
  dev="$(printf '%s\n' "$kv" | awk -F= '$1=="dev"{sub(/^[^=]*=/,""); print; exit}')"
  local id_slug
  id_slug="$(printf '%s\n' "$kv" | awk -F= '$1=="profile_slug"{sub(/^[^=]*=/,""); print; exit}')"

  if [[ -z "$name" && -n "$id_slug" ]]; then
    name="$id_slug"
  fi
  if [[ -z "$name" ]]; then
    name="$(slugify "$brand")-$(slugify "$model")"
    [[ "$name" == - ]] && name="$(slugify "$device")"
  fi
  [[ -n "$name" ]] || { echo "无法自动命名，请用 --name 指定 slug（如 hisense-a6g）" >&2; exit 1; }
  local out_dir="$DEV_DIR"
  if [[ "$local_mode" == "1" ]]; then
    out_dir="$TVPILOT_DEVICES_DIR"
    mkdir -p "$out_dir" 2>/dev/null || {
      echo "无法创建本地档案目录：$out_dir" >&2
      exit 2
    }
  fi
  local out="$out_dir/$name.md"
  [[ -e "$out" ]] && { echo "档案已存在：$(disp "$out")（改名或先删除）" >&2; exit 1; }
  if [[ "$local_mode" == "1" && -e "$DEV_DIR/$name.md" ]]; then
    echo "注意：仓库已有同名档案 $(disp "$DEV_DIR/$name.md")；本地档案会覆盖它的匹配结果。" >&2
  fi
  [[ -f "$TEMPLATE" ]] || { echo "缺少模板：$TEMPLATE" >&2; exit 4; }

  local s brand_slug
  brand_slug="$(slugify "$brand")"
  s="$(cat "$TEMPLATE")"
  s="${s/brand: <brand>/brand: ${brand_slug}}"
  s="${s//<brand>-<model-slug>/$name}"
  s="${s//<brand>/$brand}"
  s="${s//<model-slug>/$name}"
  s="${s//<model>/$model}"
  s="${s//<ro.product.model>/$model}"
  s="${s//<ro.product.device>/$device}"
  s="${s//<sdk>/$sdk}"
  s="${s//<YYYY-MM-DD>/$(date +%Y-%m-%d)}"
  s="${s//<github-handle>/$(git -C "$SKILL_DIR" config user.name 2>/dev/null || echo 'your-github-name')}"
  # 顺手填设备身份表
  s="${s/| 型号 | |/| 型号 | ${model} |}"
  s="${s/| 品牌 | |/| 品牌 | ${brand} |}"
  s="${s/| codename | |/| codename | ${device} |}"
  s="${s/| Android \/ SDK | |/| Android \/ SDK | SDK ${sdk} |}"
  s="${s/| fingerprint | |/| fingerprint | \`${fp}\` |}"
  s="${s/| 默认桌面 | |/| 默认桌面 | ${home} |}"
  printf '%s\n' "$s" >"$out"

  echo "已生成: $(disp "$out")"
  echo "  - 连接: ${dev}"
  echo "  - 只填实际测过的；没测的保持 unknown"
  echo "  - 填完跑: bash scripts/device-profile.sh lint $(disp "$out")"
  echo "  - 再跑:   bash scripts/device-profile.sh index"
}

# ---------- lint ----------
count_section_lines() { # file, section-title-prefix
  awk -v want="$2" '
    /^## / {
      if (found) exit
      if (index($0, want) > 0) found=1
      next
    }
    found && $0 !~ /^[[:space:]]*$/ && $0 !~ /^```/ {n++}
    END {print n+0}
  ' "$1"
}

cmd_lint() {
  local files=() f
  local count=0 missing=0
  if [[ $# -gt 0 ]]; then
    for f in "$@"; do
      [[ -f "$f" ]] || { echo "✗ 不存在: $f" >&2; missing=1; continue; }
      files[$count]="$f"
      count=$((count + 1))
    done
  else
    for f in "$DEV_DIR"/*.md; do
      [[ -f "$f" ]] || continue
      files[$count]="$f"
      count=$((count + 1))
    done
    if [[ "$TVPILOT_DEVICES_DIR" != "$DEV_DIR" && -d "$TVPILOT_DEVICES_DIR" ]]; then
      for f in "$TVPILOT_DEVICES_DIR"/*.md; do
        [[ -f "$f" ]] || continue
        files[$count]="$f"
        count=$((count + 1))
      done
    fi
  fi

  local fail=0 checked=0 i=0
  while [[ "$i" -lt "$count" ]]; do
    f="${files[$i]}"
    i=$((i + 1))
    case "$(basename "$f")" in _* | README.md | *-apps.md) continue ;; esac
    checked=$((checked + 1))
    local errs=""
    # frontmatter 必填
    local fm; fm="$(fm_for "$f")"
    if [[ -z "$fm" ]]; then errs="$errs\n  - 缺 frontmatter（文件必须以 --- 开头）"; fi
    local k
    for k in profile brand model match sdk verified_at verified_by capabilities; do
      grep -q "^${k}:" <<<"$fm" || errs="$errs\n  - frontmatter 缺字段: ${k}"
    done
    # match 至少一个 token
    local mt; mt="$(fm_field "$f" match | sed -e 's/^\[//' -e 's/\]$//' -e 's/"//g' -e 's/,/ /g')"
    [[ -n "${mt// /}" ]] || errs="$errs\n  - match 为空（identify-tv.sh 无法匹配）"
    # capabilities 键和值
    local caps; caps="$(fm_caps "$f")"
    local ck
    for ck in $CAP_KEYS; do
      if ! grep -q "^${ck}:" <<<"$caps"; then
        errs="$errs\n  - capabilities 缺 ${ck}"
      else
        local cv; cv="$(printf '%s\n' "$caps" | awk -F': *' -v k="$ck" '$1==k{print $2; exit}')"
        [[ "$cv" =~ ^($CAP_VALUES)$ ]] || errs="$errs\n  - capabilities.${ck} 值非法: '${cv}'（允许 verified/partial/unknown/unsupported）"
      fi
    done
    # 章节
    local sec oldifs="$IFS"
    IFS='|'
    for sec in $SECTIONS; do
      grep -qE "^## .*${sec}" "$f" || errs="$errs\n  - 缺章节: ## ${sec}"
    done
    IFS="$oldifs"
    # 证据
    local ev; ev="$(count_section_lines "$f" "证据")"
    [[ "$ev" -ge 3 ]] || errs="$errs\n  - 「## 证据」内容太少（${ev} 行，至少 3 行命令/输出）"
    # 脱敏
    local leaks
    leaks="$(grep -nE '(^|[^0-9.])(([0-9]{1,3})\.){3}([0-9]{1,3})([^0-9]|$)|([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$f" 2>/dev/null || true)"
    [[ -n "$leaks" ]] && errs="$errs\n  - 疑似敏感信息（IP/MAC/邮箱）:\n${leaks//$'\n'/$'\n'    }"
    if [[ -n "$errs" ]]; then
      echo "✗ $(disp "$f")"
      printf '%b\n' "$errs"
      fail=1
    else
      echo "✓ $(disp "$f")"
    fi
  done
  [[ "$checked" -eq 0 && "$missing" -eq 0 ]] && echo "（没有可检查的档案；排除 _ 开头和 README）"
  [[ "$fail" == 0 ]] || exit 1
  [[ "$missing" == 0 ]] || exit 4
  echo "lint 通过：${checked} 份档案"
}

# ---------- index ----------
cmd_index() {
  local rows="" f
  for f in "$DEV_DIR"/*.md; do
    [[ -f "$f" ]] || continue
    case "$(basename "$f")" in _* | README.md | *-apps.md) continue ;; esac
    local brand model sdk ver prof caps verified
    brand="$(fm_field "$f" brand)"
    model="$(fm_field "$f" model)"
    sdk="$(fm_field "$f" sdk)"
    ver="$(fm_field "$f" verified_at)"
    prof="$(fm_field "$f" profile)"
    [[ -z "$prof" ]] && prof="$(basename "$f" .md)"
    verified="$(fm_caps "$f" | awk -F': *' '$2=="verified"{printf "%s ", $1}')"
    [[ -z "${verified// /}" ]] && verified="—"
    rows="${rows}| [${prof}](<${f##*/}>) | ${brand} | ${model} | ${sdk} | ${ver} | ${verified} |\n"
  done
  [[ -f "$README" ]] || { echo "缺少 $README" >&2; exit 4; }
  local tmp; tmp="$(mktemp "${README}.XXXXXX")"
  awk -v rows="$(printf '%b' "$rows")" '
    /<!-- DEVICE-MATRIX:BEGIN -->/ {print; print ""; print "| 档案 | 品牌 | 型号 | SDK | 验证日期 | verified 能力 |"; print "|---|---|---|---|---|---|"; printf "%s", rows; print ""; skip=1; next}
    /<!-- DEVICE-MATRIX:END -->/ {skip=0}
    skip {next}
    {print}
  ' "$README" >"$tmp" && mv "$tmp" "$README"
  echo "已更新 ${README#"$SKILL_DIR"/}（$(($(grep -c '^| \[' "$README" 2>/dev/null || echo 0))) 份档案）"
}

case "${1:-help}" in
  init) shift; cmd_init "$@" ;;
  lint) shift; cmd_lint "$@" ;;
  index) shift; cmd_index "$@" ;;
  help | -h | --help | *) usage; exit 1 ;;
esac
