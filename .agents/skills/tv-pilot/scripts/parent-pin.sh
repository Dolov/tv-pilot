#!/usr/bin/env bash
# 一条命令回答「tv-pilot 家长设置的密码是多少」，顺带报出当天限制是否被豁免。只读。
#
# 用法: bash scripts/parent-pin.sh [串口] [--json]
#   --json  输出 {pin, pin_source, default_pin, changed, free_pass_today, locked_until, note}
#
# pin_source 取值:
#   default  App 里的出厂默认值（PetStore.DEFAULT_PIN，公开常量）
#   local    本机在 references/local.md 里登记过（说明家长改过 PIN）
#   unknown  两者都没有：App 里已改过但没登记，脚本读不到（见下方「为什么读不到」）
#
# 为什么读不到实际 PIN：PIN 存在 App 的 SharedPreferences("pet") 的 cfg_pin 里，
# App 不是 debuggable 的，adb（无 root）读 /data/data/com.dsh.tvhud/shared_prefs/pet.xml
# 会 Permission denied。所以这里只能给「出厂默认值」+「本机登记值」。
# 家长在电视上改过 PIN 的话，把新 PIN 写进 references/local.md（已 gitignore），
# 本脚本就会以 local 为准——这是本机事实，不该进仓库。
#
# 退出码：0 成功；2 环境/连接问题；4 连不上电视。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

SERIAL=""
JSON=0
for a in "$@"; do
  case "$a" in
    --json) JSON=1 ;;
    *) SERIAL="$a" ;;
  esac
done

jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# ---------- 出厂默认值：从源码常量里读，不写死第二份 ----------
# 仓库根的 app/ 是源码准；skill 目录下的 assets/ 是本地生成副本（已 gitignore），
# 两边都找不到时回退到本脚本内置的兜底值。
DEFAULT_PIN=""
for src in "$SKILL_DIR/../../../app/src/com/dsh/tvhud/pet/PetStore.java" \
           "$SKILL_DIR/assets/app/src/com/dsh/tvhud/pet/PetStore.java"; do
  if [[ -f "$src" ]]; then
    DEFAULT_PIN="$(sed -n 's/.*DEFAULT_PIN[[:space:]]*=[[:space:]]*"\([0-9]\{4\}\)".*/\1/p' "$src" | head -1)"
    [[ -n "$DEFAULT_PIN" ]] && break
  fi
done
if [[ -z "$DEFAULT_PIN" ]]; then
  DEFAULT_PIN="2580"
fi

# ---------- 本机登记值（家长改过 PIN 时写这里） ----------
# 格式：references/local.md 里一行 | 家长设置 PIN | `1234` |
LOCAL_MD="$SKILL_DIR/references/local.md"
LOCAL_PIN=""
if [[ -f "$LOCAL_MD" ]]; then
  LOCAL_PIN="$(sed -n 's/^.*家长设置 *PIN.*`\([0-9]\{4\}\)`.*$/\1/p' "$LOCAL_MD" | head -1)"
fi

# ---------- 电视连上时：读当天限制状态（免限卡） ----------
FREE_PASS=0
LOCKED_UNTIL=0
CONNECTED=0
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(tv_device)"
  if [[ -z "$SERIAL" ]]; then
    SERIAL="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null)" || true
  fi
fi
if [[ -n "$SERIAL" ]]; then
  CONNECTED=1
  # 用 App 自己的调试广播把存档打到 logcat：freePass=<当天 0 点时间戳>，0 = 没有免限卡。
  # 断的是 pin 不打印——凭据不进 logcat。
  tv_adb -s "$SERIAL" logcat -c >/dev/null 2>&1 || true
  tv_adb -s "$SERIAL" shell am broadcast -a com.dsh.tvhud.PET_STATE >/dev/null 2>&1 || true
  sleep 1
  state_line="$(tv_adb -s "$SERIAL" logcat -d -s TVPILOT 2>/dev/null | grep -m1 'pet state' | tr -d '\r')"
  fp="$(printf '%s' "$state_line" | sed -n 's/.*freePass=\([0-9]*\).*/\1/p')"
  [[ -n "$fp" ]] && FREE_PASS="$fp"
  # PIN 连错 5 次会锁 60 秒，锁定截止时间存在存档里（cfg_pin_lock_until）
  lock="$(printf '%s' "$state_line" | sed -n 's/.*pinLockUntil=\([0-9]*\).*/\1/p')"
  [[ -n "$lock" ]] && LOCKED_UNTIL="$lock"
fi

# ---------- 决定用哪个 PIN ----------
if [[ -n "$LOCAL_PIN" ]]; then
  PIN="$LOCAL_PIN"; SOURCE="local"
elif [[ -n "$DEFAULT_PIN" ]]; then
  PIN="$DEFAULT_PIN"; SOURCE="default"
else
  PIN=""; SOURCE="unknown"
fi

# changed=1 表示「电视上的 PIN 不是出厂默认值」（本机登记过，或用户改过没登记）
if [[ "$SOURCE" == "local" ]]; then
  CHANGED=1
elif [[ "$SOURCE" == "default" ]]; then
  CHANGED=0
else
  CHANGED=1
fi

NOTE=""
if [[ "$SOURCE" == "default" ]]; then
  NOTE="App 未提供改 PIN 的入口，出厂默认值就是实际密码"
elif [[ "$SOURCE" == "local" ]]; then
  NOTE="家长改过 PIN，取本机 references/local.md 登记值"
else
  NOTE="读不到：PIN 存在 App 私有 prefs（无 root 读不了），请登记到 references/local.md"
fi

if [[ "$JSON" == 1 ]]; then
  printf '{"pin":%s,"pin_source":"%s","default_pin":"%s","changed":%s,"free_pass_today":%s,"locked_until":%s,"connected":%s,"note":"%s"}\n' \
    "$( [[ -n "$PIN" ]] && printf '"%s"' "$PIN" || printf 'null' )" \
    "$SOURCE" "$DEFAULT_PIN" \
    "$( [[ "$CHANGED" == 1 ]] && echo true || echo false )" \
    "$FREE_PASS" "$LOCKED_UNTIL" \
    "$( [[ "$CONNECTED" == 1 ]] && echo true || echo false )" \
    "$(jesc "$NOTE")"
  exit 0
fi

echo "家长设置 PIN: ${PIN:-读不到}"
echo "来源: ${SOURCE}（${NOTE}）"
if [[ "$SOURCE" == "default" ]]; then
  echo "说明: 代码里 PetStore.DEFAULT_PIN 就是它；App 目前没有「改 PIN」的界面，"
  echo "      所以电视上跑的就是这个值。定位：app/src/com/dsh/tvhud/pet/PetStore.java"
fi
if [[ "$CHANGED" == 1 && "$SOURCE" != "local" ]]; then
  echo "提示: 如果家长在电视上改过 PIN，把它登记到 references/local.md 的一行里："
  echo "      | 家长设置 PIN | \`1234\` |"
fi
if [[ "$FREE_PASS" != 0 && "$FREE_PASS" != "0" ]]; then
  echo "今天免限: 已开启（当天不拦时段/场次）"
elif [[ "$CONNECTED" == 1 ]]; then
  echo "今天免限: 未开启"
fi
[[ "$LOCKED_UNTIL" != 0 && "$LOCKED_UNTIL" != "0" ]] && echo "PIN 锁定: 连错 5 次会锁 60 秒（锁定截止 ${LOCKED_UNTIL}）"
exit 0
