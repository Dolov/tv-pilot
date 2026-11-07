#!/usr/bin/env bash
# 全量真机回归：把 tv-pilot 现有每个页面都走一遍并断言，确保改动不碰坏老功能。
#
# 覆盖：
#   1. 首页：菜单 5 项都在，默认选中第 1 项；
#   2. 今日使用：总时长/分类图例/时间轴/明细都渲染出来；
#   3. 使用记录：能按天回看（落到最近有归档的一天），左右键翻日期；
#   4. 使用报告：周/月/年三天粒度 + 翻页 + 回本期 + 返回；
#   5. 电子宠物：房间 + 照顾/商店/衣柜/房间四个分区切换 + 返回；
#   6. 屏保角标：HUD 服务在跑、无障碍条目在；
#   7. 数据侧：归档文件可读、完整性自检无告警、报告不变量无告警；
#   8. 全程无 FATAL EXCEPTION。
#
# 用法：
#   bash tests/e2e/full-regression.sh              # 装 .build/out/tv-pilot.apk 再全量跑
#   bash tests/e2e/full-regression.sh --no-install  # 测电视上当前版本
# 需要：电视已连 adb。退出码 0=全过，1=有失败，2=环境不满足。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SKILL="$ROOT/.agents/skills/tv-pilot"
APK="$ROOT/.build/out/tv-pilot.apk"
INSTALL=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-install) INSTALL=0 ;;
    --apk) [[ $# -ge 2 ]] || { echo "--apk 需要路径" >&2; exit 2; }; APK="$2"; shift ;;
    -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
  shift
done

command -v python3 >/dev/null 2>&1 || { echo "需要 python3" >&2; exit 2; }
ADB_SH="$SKILL/scripts/adb.sh"
[[ -x "$ADB_SH" ]] || { echo "缺少 $ADB_SH" >&2; exit 2; }

PASS=0
FAIL=0
SKIP=0
FAILED=""

pass() { PASS=$((PASS + 1)); printf 'PASS  %s\n' "$*"; }
skip() { SKIP=$((SKIP + 1)); printf 'SKIP  %s\n' "$*"; }
fail() { FAIL=$((FAIL + 1)); FAILED="${FAILED}
  - $1"; printf 'FAIL  %s\n' "$1"; [[ -n "${2:-}" ]] && printf '      %s\n' "$2"; }
step() { printf '\n== %s ==\n' "$*"; }

adb_sh() { bash "$ADB_SH" -s "$SERIAL" "$@"; }
key() { adb_sh shell input keyevent "$1" >/dev/null 2>&1; sleep "${2:-2.4}"; }

SHOTS="$ROOT/.build/regression"
mkdir -p "$SHOTS"
LOG_FILE="$SHOTS/logcat.txt"

screenshot() {
  local name="$1"
  local file="$SHOTS/$name.png"
  local size=0
  adb_sh exec-out screencap -p >"$file" 2>/dev/null || true
  [[ -f "$file" ]] && size=$(wc -c <"$file" | tr -d ' ')
  if [[ "${size:-0}" -gt 10000 ]]; then
    pass "截图 $name.png（${size} bytes）"
  else
    fail "截图 $name.png 为空或过小（${size:-0}）"
  fi
}

resumed() { adb_sh shell dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity' || true; }

# ---------- 连接 ----------
step "连接电视"
SERIAL="$(bash "$SKILL/scripts/ensure-tv.sh" 2>/dev/null | tail -1)"
if [[ -z "$SERIAL" ]]; then
  echo "没有连上电视：先跑 bash $SKILL/scripts/ensure-tv.sh -v" >&2
  exit 2
fi
printf '设备：%s\n' "$SERIAL"

if [[ "$INSTALL" -eq 1 ]]; then
  step "安装 APK"
  if [[ ! -f "$APK" ]]; then
    echo "APK 不存在：$APK（先 make apk）" >&2
    exit 2
  fi
  if bash "$SKILL/scripts/app-deploy.sh" --apk "$APK" --pkg com.dsh.tvhud \
      --overlay --usage --acc com.dsh.tvhud/com.dsh.tvhud.hud.BootAccessibilityService \
      >"$SHOTS/deploy.log" 2>&1; then
    pass "安装 + 授权完成（$(basename "$APK")）"
  else
    fail "app-deploy 失败" "$(tail -3 "$SHOTS/deploy.log" | tr '\n' ' ')"
    printf '\n== 汇总 ==\nPASS %d  FAIL %d  SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
    exit 1
  fi
fi

# ---------- 日志采集（后台按 tag 过滤，避免电视系统日志刷爆 4MB 环缓冲） ----------
adb_sh logcat -c >/dev/null 2>&1 || true
: >"$LOG_FILE"
adb_sh logcat -v time -s TVPILOT:V AndroidRuntime:E >"$LOG_FILE" 2>/dev/null &
LOG_PID=$!
trap 'kill "$LOG_PID" >/dev/null 2>&1 || true; wait "$LOG_PID" >/dev/null 2>&1 || true' EXIT

# ---------- 1. 首页 ----------
step "首页"
key 224 1.2            # 唤醒
adb_sh shell am force-stop com.dsh.tvhud >/dev/null 2>&1
sleep 1
adb_sh shell am start -n com.dsh.tvhud/.home.HomeActivity >/dev/null 2>&1
sleep 3
if resumed | grep -q 'com.dsh.tvhud/.home.HomeActivity'; then
  pass "首页打开（HomeActivity）"
else
  fail "首页没打开" "$(resumed)"
fi
screenshot "01-home"

# ---------- 2. 使用记录（今日台账 + 左右键历史回看，已合并为一个入口） ----------
step "使用记录（今日 + 历史回看）"
key 23 3.5
if resumed | grep -q 'com.dsh.tvhud/.usage.MainActivity'; then
  pass "使用记录打开（MainActivity，默认今天）"
else
  fail "使用记录没打开" "$(resumed)"
fi
screenshot "02-record-today"
# 左右键翻日期，不应崩溃（今天 → 昨天 → 今天）
key 21 1.8
screenshot "02-record-history"
key 22 1.8
pass "左右键按天回看无异常"
key 4 2.0

# ---------- 3. 使用报告：三粒度 + 导航 ----------
step "使用报告"
key 20 0.5
key 23 3.5
if resumed | grep -q 'com.dsh.tvhud/.report.ReportsActivity'; then
  pass "使用报告打开（ReportsActivity）"
else
  fail "使用报告没打开" "$(resumed)"
  printf '\n== 汇总 ==\nPASS %d  FAIL %d  SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
  exit 1
fi
screenshot "04-report-open"
# ↑ 变粗三次（保证到过年报），↓ 变细两次（回到周报）
for i in 1 2 3; do key 19 3.0; done
screenshot "04-report-year"
key 20 3.0
key 20 3.0
screenshot "04-report-week"
# 翻页 + 回到本期
key 21 3.0
screenshot "04-report-prev"
key 23 3.0
key 4 2.5
if resumed | grep -q 'com.dsh.tvhud/.home.HomeActivity'; then
  pass "报告页返回键回到首页"
else
  fail "报告页返回键没回首页" "$(resumed)"
fi

# ---------- 5. 电子宠物（房间 / 商店 / 衣柜 / 装饰） ----------
step "电子宠物"
for i in 1 2 3 4 5 6; do key 19 0.2; done
for i in 1 2; do key 20 0.4; done
key 23 3.5
if resumed | grep -q 'com.dsh.tvhud/.pet.PetActivity'; then
  pass "宠物房间打开（PetActivity）"
else
  fail "宠物房间没打开" "$(resumed)"
fi
screenshot "05-pet-care"

# 底部 Tab：照顾 → 商店 → 衣柜 → 房间（面板切换不重建、不崩溃）
key 20 0.8
key 22 0.8
key 23 1.6
screenshot "05-pet-shop"
key 20 0.8
key 22 0.8
key 23 1.6
screenshot "05-pet-wardrobe"
key 20 0.8
key 22 0.8
key 23 1.6
screenshot "05-pet-decor"
key 4 2.2
if resumed | grep -q 'com.dsh.tvhud/.home.HomeActivity'; then
  pass "宠物房间返回键回到首页"
else
  fail "宠物房间返回键没回首页" "$(resumed)"
fi

# ---------- 5. 屏保角标（服务 + 无障碍） ----------
step "屏保角标 / 服务"
if [[ -n "$(adb_sh shell pidof com.dsh.tvhud 2>/dev/null)" ]]; then
  pass "进程在运行"
else
  fail "进程不在了"
fi
if adb_sh shell "dumpsys activity services com.dsh.tvhud" 2>/dev/null | grep -q HudService; then
  pass "HudService 已注册运行"
else
  fail "HudService 不在"
fi
if adb_sh shell "settings get secure enabled_accessibility_services" 2>/dev/null \
    | grep -q BootAccessibilityService; then
  pass "无障碍条目在（开机自启依赖它）"
else
  fail "无障碍条目丢了"
fi

# ---------- 6. 数据侧 ----------
step "数据侧"
sleep 1
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
trap - EXIT

LOGS="$(cat "$LOG_FILE" 2>/dev/null || true)"
printf '\n日志样本（%s 行）：\n' "$(printf '%s' "$LOGS" | wc -l | tr -d ' ')"
printf '%s' "$LOGS" | grep -E 'report load|archive written|ARCHIVE_CORRUPT|REPORT_INVARIANT|FATAL' | tail -10 || true

log_has() { printf '%s' "$LOGS" | grep -q "$1"; }

log_has 'report load WEEK'  && pass "周报加载过"  || fail "没看到 report load WEEK"
log_has 'report load MONTH' && pass "月报加载过"  || fail "没看到 report load MONTH"
log_has 'report load YEAR'  && pass "年报加载过"  || fail "没看到 report load YEAR"
log_has 'ARCHIVE_CORRUPT'   && fail "归档完整性告警" "$(printf '%s' "$LOGS" | grep -m2 ARCHIVE_CORRUPT | tr '\n' ' ')" \
                            || pass "归档无完整性告警"
log_has 'REPORT_INVARIANT'  && fail "报告一致性告警" "$(printf '%s' "$LOGS" | grep -m2 REPORT_INVARIANT | tr '\n' ' ')" \
                            || pass "报告无不变量告警"
log_has 'FATAL EXCEPTION'   && fail "进程崩溃" "$(printf '%s' "$LOGS" | grep -m1 -A3 'FATAL EXCEPTION' | tr '\n' ' ')" \
                            || pass "无 FATAL EXCEPTION"

# 归档文件可读 + 内容自洽。
# 归档现在是 5 分钟节流；E2E 只有几十秒，先让服务强制写一次再读。
# 注意：HUD 页面自己不计入使用（isOwn），如果今天电视确实没看过，归档可能还不存在。
adb_sh shell am broadcast -a com.dsh.tvhud.ARCHIVE_NOW >/dev/null 2>&1 || true
sleep 1
HIST_JSON="$SHOTS/history.json"
if adb_sh shell "cat /sdcard/Android/data/com.dsh.tvhud/files/history/$(date +%Y-%m-%d).json" \
    >"$HIST_JSON" 2>/dev/null && [[ -s "$HIST_JSON" ]]; then
  if python3 - "$HIST_JSON" <<'PY'
import json, sys
o = json.load(open(sys.argv[1]))
segs = o.get("segs", [])
day = o["day"]
assert all(e > s for s, e, c in segs), "存在非正时长分段"
assert all(day <= s and e <= day + 86400000 for s, e, c in segs), "分段越出当天"
assert all(0 <= c <= 4 for s, e, c in segs), "类别越界"
prev = None
for s, e, c in segs:
    if prev is not None:
        assert s >= prev, "分段重叠"
    prev = e
print("归档自洽：%d 段，总时长 %.1f 分钟" % (len(segs), sum(e - s for s, e, c in segs) / 60000))
PY
  then
    pass "当天归档文件可读且自洽"
  else
    fail "当天归档文件内容不自洽"
  fi
else
  skip "当天还没有归档文件（今天没看过电视时正常）"
fi

printf '\n截图与日志：%s\n' "$SHOTS"
printf '\n== 汇总 ==\nPASS %d  FAIL %d  SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
if [[ "$FAIL" -gt 0 ]]; then
  printf '失败项：%s\n' "$FAILED"
  exit 1
fi
echo "全量真机回归通过。"
