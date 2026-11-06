#!/usr/bin/env bash
# 首次使用 / 换电视后跑一次：准备 .env、探测电视、生成「本机事实」references/local.md。
#
#   bash scripts/setup.sh                 # 自动发现电视（ensure-tv 阶梯）
#   bash scripts/setup.sh --ip 192.168.1.23
#   bash scripts/setup.sh --offline       # 电视不在线也先落一份骨架
#
# 为什么要有这一步：SKILL.md 是给所有用户看的通用文档，具体是哪台电视、装了什么、
# 工具链在哪，属于「本机事实」。把这些写进 SKILL.md 会让每个使用者都要改入口文档，
# 一改就和上游分叉。所以入口文档只写通用地图，本机事实统一生成到 local.md（已 gitignore）。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

IP_ARG=""
OFFLINE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ip) IP_ARG="${2:-}"; shift 2 ;;
    --ip=*) IP_ARG="${1#*=}"; shift ;;
    --offline) OFFLINE=1; shift ;;
    -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$*"; }

echo "== 1/4 配置 .env =="
ENV_FILE="$SKILL_DIR/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$SKILL_DIR/.env.example" "$ENV_FILE" && ok "已从 .env.example 创建 .env"
else
  ok ".env 已存在（不覆盖）"
fi
if [[ -n "$IP_ARG" ]]; then
  if grep -q '^TV_IP=' "$ENV_FILE"; then
    sed -i '' "s|^TV_IP=.*|TV_IP=$IP_ARG|" "$ENV_FILE" 2>/dev/null || sed -i "s|^TV_IP=.*|TV_IP=$IP_ARG|" "$ENV_FILE"
  else
    printf 'TV_IP=%s\n' "$IP_ARG" >>"$ENV_FILE"
  fi
  export TV_IP="$IP_ARG"
  ok "TV_IP 已写入 $IP_ARG"
fi
if grep -q '^TV_IP=192\.168\.1\.123' "$ENV_FILE" 2>/dev/null; then
  warn "TV_IP 还是示例值 192.168.1.123；用 --ip <地址> 改成真实地址，或让 ensure-tv 自动发现"
fi

echo "== 2/4 连接电视 =="
SERIAL=""
if [[ "$OFFLINE" -eq 0 ]]; then
  if SERIAL="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null)"; then
    ok "已连接 $SERIAL"
  else
    SERIAL=""
    fail "没连上；先唤醒电视 / 确认 ADB 调试已开（bash scripts/ensure-tv.sh -v 看卡在哪）"
  fi
else
  warn "--offline：跳过连接探测"
fi

echo "== 3/4 识别机型 =="
MATCH="unknown"; MODEL=""; BRAND=""; SDK=""; PROFILE=""; PROFILE_PATH=""; PROFILE_SOURCE=""
if [[ -n "$SERIAL" ]]; then
  ID_JSON_FILE="$(mktemp "${TMPDIR:-/tmp}/tvpilot-identify.XXXXXX")"
  if bash "$SKILL_DIR/scripts/identify-tv.sh" --json >"$ID_JSON_FILE" 2>/dev/null; then
    eval "$(python3 - "$ID_JSON_FILE" <<'PY' 2>/dev/null || true
import json,sys,shlex
d=json.load(open(sys.argv[1]))
for k in ("match","model","brand","sdk","profile","profile_path","profile_source"):
    v=d.get(k,"")
    print("V_%s=%s" % (k.upper(), shlex.quote(str(v))))
PY
)"
    MATCH="${V_MATCH:-unknown}"; MODEL="${V_MODEL:-}"; BRAND="${V_BRAND:-}"
    SDK="${V_SDK:-}"; PROFILE="${V_PROFILE:-}"; PROFILE_PATH="${V_PROFILE_PATH:-}"
    PROFILE_SOURCE="${V_PROFILE_SOURCE:-}"
    ok "识别: $MATCH  model=${MODEL:-?}  profile=${PROFILE:-—}"
    case "$MATCH" in
      matched) : ;;
      similar) warn "同品牌不同型号：品牌经验可用，但包名/键值/坑不保证；写操作前先确认" ;;
      *)       warn "没有设备档案：只读可用；禁包 / settings put / 装 App / 屏保角标请先按 references/porting.md 接入" ;;
    esac
  else
    fail "identify-tv.sh 执行失败（可单独跑一次看错误）"
  fi
  rm -f "$ID_JSON_FILE"
else
  warn "未连接，跳过识别"
fi

echo "== 4/4 生成 references/local.md =="
APP_VER=""
if [[ -n "$SERIAL" ]]; then
  APP_VER="$(tv_adb -s "$SERIAL" shell dumpsys package com.dsh.tvhud 2>/dev/null \
    | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1)"
fi
TOOL_STATUS="未安装"
if [[ -f "$SKILL_DIR/.tools/platform-tools/adb" ]]; then
  TOOL_STATUS="adb 已装"
  [[ -f "$SKILL_DIR/.tools/android-sdk/platform-28/android.jar" ]] && TOOL_STATUS="${TOOL_STATUS}，build-tools 已装" || TOOL_STATUS="${TOOL_STATUS}，build-tools 未装（bash scripts/build-tools.sh ensure）"
fi
SNAP=""
for f in "$SKILL_DIR"/references/devices/*-apps.md; do
  [[ -f "$f" ]] && SNAP="$(basename "$f")"
done 2>/dev/null

LOCAL_MD="$SKILL_DIR/references/local.md"
{
  echo "# 本机事实（自动生成，勿手改）"
  echo
  echo "> 由 \`scripts/setup.sh\` 于 $(date '+%Y-%m-%d %H:%M') 生成。本文件已 gitignore。"
  echo "> 换电视 / 重装工具链后重跑：\`bash scripts/setup.sh\`。"
  echo
  echo "## 电视"
  echo
  echo "| 项 | 值 |"
  echo "|---|---|"
  echo "| 地址 | \`${SERIAL:-未连接}\` |"
  echo "| 匹配结果 | \`$MATCH\` |"
  echo "| 型号 | \`${MODEL:-未知}\` |"
  echo "| 品牌 / SDK | \`${BRAND:-未知}\` / \`${SDK:-未知}\` |"
  echo "| 设备档案 | \`${PROFILE_PATH:-无}\`（${PROFILE_SOURCE:-—}） |"
  echo
  echo "## 自研 App"
  echo
  echo "| 项 | 值 |"
  echo "|---|---|"
  echo "| tv-pilot 版本 | \`${APP_VER:-未安装或未连接}\` |"
  echo "| 应用快照 | \`${SNAP:-无}\` |"
  echo
  echo "## 工具链"
  echo
  echo "- $TOOL_STATUS"
  echo "- 状态缓存：\`$TVPILOT_STATE_FILE\`"
  echo
  echo "## 下一步"
  echo
  echo '```bash'
  echo "bash scripts/ensure-tv.sh -v      # 连接（<1s）"
  echo "bash scripts/probe.sh             # 只读探测"
  echo "bash scripts/usage.sh             # 今天看了多久 / 看了什么"
  echo '```'
} >"$LOCAL_MD"
ok "已写入 references/local.md"

echo
echo "完成。SKILL.md 会优先读 references/local.md 了解本机情况；通用文档不需要因换电视而修改。"
