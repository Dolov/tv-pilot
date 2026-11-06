#!/usr/bin/env bash
# 写操作闸门：先确认「这台电视有档案、且当前能力经过验证」，再允许装 App / 禁包 / 改设置。
#
#   bash scripts/require-profile.sh                 # 只要求 matched（有设备档案）
#   bash scripts/require-profile.sh app_build       # 额外要求该能力=verified
#   bash scripts/require-profile.sh --force         # 显式跳过（会打印警告）
#
# 退出码：0 通过 / 2 连不上或识别失败 / 3 档案不存在、未匹配或能力未验证。
#
# 为什么放代码里而不是只写在提示词里：SKILL.md 的三态规则（matched/similar/unknown）
# 只有在每个入口都被执行时才真正拦得住人；一个不读文档的代理直接跑 adb 是最危险的场景。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

CAP=""
FORCE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE=1; shift ;;
    -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "未知参数: $1" >&2; exit 1 ;;
    *) CAP="$1"; shift ;;
  esac
done

if [[ "$FORCE" -eq 1 ]]; then
  echo "警告：--force 跳过设备档案校验，写操作风险自负（未验证机型可能黑屏/进不去设置）" >&2
  exit 0
fi

ID_JSON_FILE="$(mktemp "${TMPDIR:-/tmp}/tvpilot-gate.XXXXXX")" || exit 2
if ! bash "$SKILL_DIR/scripts/identify-tv.sh" --json >"$ID_JSON_FILE" 2>/dev/null; then
  rm -f "$ID_JSON_FILE"
  echo "无法识别当前电视（连接失败或没响应）。先 bash scripts/ensure-tv.sh -v" >&2
  exit 2
fi

MATCH=""; PROFILE_PATH=""; MODEL=""
if command -v python3 >/dev/null 2>&1; then
  eval "$(python3 - "$ID_JSON_FILE" <<'PY' 2>/dev/null || true
import json, sys, shlex
d = json.load(open(sys.argv[1]))
for k in ("match", "profile_path", "model"):
    print("G_%s=%s" % (k.upper(), shlex.quote(str(d.get(k) or ""))))
PY
)"
  MATCH="${G_MATCH:-}"; PROFILE_PATH="${G_PROFILE_PATH:-}"; MODEL="${G_MODEL:-}"
else
  MATCH="$(sed -n 's/.*"match"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$ID_JSON_FILE" | head -1)"
  PROFILE_PATH="$(sed -n 's/.*"profile_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$ID_JSON_FILE" | head -1)"
  MODEL="$(sed -n 's/.*"model"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$ID_JSON_FILE" | head -1)"
fi
rm -f "$ID_JSON_FILE"

case "$MATCH" in
  matched) : ;;
  similar)
    echo "拒绝：$MODEL 只有同品牌参考档案（similar），包名/键值/坑都不保证。" >&2
    echo "按 references/porting.md 补齐本型号档案，或加 --force 明确承担风险。" >&2
    exit 3 ;;
  *)
    echo "拒绝：没有这台电视（${MODEL:-未知型号}）的设备档案（$MATCH）。" >&2
    echo "只读能力可用；禁包 / settings put / 装 App / 屏保角标请先按 references/porting.md 接入。" >&2
    exit 3 ;;
esac

if [[ -n "$CAP" ]]; then
  [[ -n "$PROFILE_PATH" && -f "$PROFILE_PATH" ]] || {
    echo "拒绝：找不到设备档案文件（$PROFILE_PATH）" >&2; exit 3; }
  VERIFIED="$(awk -v k="$CAP" '
    /^capabilities:/ { inside=1; next }
    inside && /^[^[:space:]]/ { inside=0 }
    inside && $1 == k":" { print $2; exit }
  ' "$PROFILE_PATH")"
  if [[ "$VERIFIED" != "verified" ]]; then
    echo "拒绝：${MODEL} 的 $CAP = ${VERIFIED:-未声明}，不是 verified。" >&2
    echo "先只读验证并更新档案（references/porting.md），或加 --force。" >&2
    exit 3
  fi
fi

echo "通过：${MODEL}（${MATCH}${CAP:+，$CAP=verified}）"
