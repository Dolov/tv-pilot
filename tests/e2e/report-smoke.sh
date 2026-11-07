#!/usr/bin/env bash
# 真机 E2E 冒烟：装当前 APK → 打开「使用报告」→ 周/月/年翻页 + 回本期 + 返回 → 断言。
#
# 为什么不做像素级断言：报告页是纯 Canvas 自绘，uiautomator 读不到文字。
# 这里断言「能看到的流程」+ 「日志里的数据」：
#   1. 每个粒度都真的触发了一次 report load（WEEK/MONTH/YEAR）；
#   2. 没有崩溃（FATAL EXCEPTION）；
#   3. 没有 REPORT_INVARIANT / ARCHIVE_CORRUPT 告警（报告自洽 + 归档完整）；
#   4. 返回键回到首页；截图留档到 .build/e2e/ 供人眼复核。
#
# 用法：
#   bash tests/e2e/report-smoke.sh                 # 装 .build/out/tv-pilot.apk 再测
#   bash tests/e2e/report-smoke.sh --no-install    # 测电视上当前安装版本
#   bash tests/e2e/report-smoke.sh --apk <path>    # 指定 APK
# 需要：电视已连上 adb（先跑 scripts/ensure-tv.sh）。退出码 0=通过，1=失败，2=环境不满足。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SKILL="$ROOT/.agents/skills/tv-pilot"
APK="$ROOT/.build/out/tv-pilot.apk"
INSTALL=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-install) INSTALL=0 ;;
    --apk)
      [[ $# -ge 2 ]] || { echo "--apk 需要一个路径" >&2; exit 2; }
      APK="$2"
      shift
      ;;
    -h|--help)
      sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "未知参数：$1（试 --help）" >&2
      exit 2
      ;;
  esac
  shift
done

command -v python3 >/dev/null 2>&1 || { echo "需要 python3" >&2; exit 2; }
ADB_SH="$SKILL/scripts/adb.sh"
[[ -x "$ADB_SH" ]] || { echo "缺少 $ADB_SH" >&2; exit 2; }

PASS=0
FAIL=0
FAILED=""

pass() {
  PASS=$((PASS + 1))
  printf 'PASS  %s\n' "$*"
}

fail() {
  FAIL=$((FAIL + 1))
  FAILED="${FAILED}
  - $1"
  printf 'FAIL  %s\n' "$1"
  [[ -n "${2:-}" ]] && printf '      %s\n' "$2"
}

step() {
  printf '\n== %s ==\n' "$*"
}

adb_sh() {
  bash "$ADB_SH" -s "$SERIAL" "$@"
}

key() {
  adb_sh shell input keyevent "$1" >/dev/null 2>&1
  sleep "${2:-2.6}"
}

screenshot() {
  local name="$1"
  local file="$SHOTS/$name.png"
  adb_sh exec-out screencap -p >"$file" 2>/dev/null || true
  local size=0
  [[ -f "$file" ]] && size=$(wc -c <"$file" | tr -d ' ')
  if [[ "${size:-0}" -gt 10000 ]]; then
    pass "截图 $name.png（${size} bytes）"
  else
    fail "截图 $name.png 为空或过小（${size:-0} bytes；电视可能在待机）"
  fi
}

resumed_activity() {
  adb_sh shell dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity' || true
}

log_has() {
  printf '%s' "$LOGS" | grep -q "$1"
}

# ---------- 前置：连电视 ----------
step "连接电视"
SERIAL="$(bash "$SKILL/scripts/ensure-tv.sh" 2>/dev/null | tail -1)"
if [[ -z "$SERIAL" ]]; then
  echo "没有连上电视：先跑 bash $SKILL/scripts/ensure-tv.sh -v" >&2
  exit 2
fi
printf '设备：%s\n' "$SERIAL"

SHOTS="$ROOT/.build/e2e"
mkdir -p "$SHOTS"
LOG_FILE="$SHOTS/logcat.txt"

# ---------- 安装 ----------
if [[ "$INSTALL" -eq 1 ]]; then
  step "安装 APK"
  if [[ ! -f "$APK" ]]; then
    echo "APK 不存在：$APK（先跑 make apk，或用 --apk 指定）" >&2
    exit 2
  fi
  if bash "$SKILL/scripts/app-deploy.sh" --apk "$APK" --pkg com.dsh.tvhud \
      --overlay --usage --acc com.dsh.tvhud/com.dsh.tvhud.hud.BootAccessibilityService \
      >"$SHOTS/deploy.log" 2>&1; then
    pass "安装 + 授权完成（$(basename "$APK")，$(wc -c <"$APK" | tr -d ' ') bytes）"
  else
    fail "app-deploy 失败" "$(tail -3 "$SHOTS/deploy.log" | tr '\n' ' ')"
    printf '\n== 汇总 ==\nPASS %d  FAIL %d\n' "$PASS" "$FAIL"
    exit 1
  fi
fi

# ---------- 打开报告 ----------
step "打开使用报告"
adb_sh logcat -c >/dev/null 2>&1 || true
# 后台只收 TVPILOT / AndroidRuntime 两个 tag，避免被电视系统日志刷爆 4MB 环形缓冲
: >"$LOG_FILE"
adb_sh logcat -v time -s TVPILOT:I AndroidRuntime:E >"$LOG_FILE" 2>/dev/null &
LOG_PID=$!
trap 'kill "$LOG_PID" >/dev/null 2>&1 || true' EXIT
key 224 1.2            # KEYCODE_WAKEUP：待机时先亮屏
adb_sh shell am start -n com.dsh.tvhud/.home.HomeActivity >/dev/null 2>&1
sleep 2.5
key 20 0.4
key 23 4.5            # 菜单第 2 项「使用报告」
if resumed_activity | grep -q 'com.dsh.tvhud/.report.ReportsActivity'; then
  pass "报告页已打开（ReportsActivity）"
else
  fail "报告页没打开" "$(resumed_activity)"
fi
screenshot "report-open"

# ---------- 粒度：周 → 月 → 年，再回到周 ----------
step "切换粒度：周报 / 月报 / 年报"
for i in 1 2 3; do
  key 19 3.2          # ↑ 变粗：周→月→年（边界停住）
done
screenshot "report-year"
for i in 1 2; do
  key 20 3.2          # ↓ 变细：年→月→周
done
screenshot "report-week"

# ---------- 翻页 + 回到本期 + 返回 ----------
step "翻页 / 回到本期 / 返回"
key 21 3.0            # ← 上一期
screenshot "report-prev-period"
key 23 3.0            # OK 回到本期
key 4 2.5             # 返回首页

sleep 1
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
trap - EXIT
LOGS="$(cat "$LOG_FILE" 2>/dev/null || true)"
printf '\n日志样本（%s 行）：\n' "$(printf '%s' "$LOGS" | wc -l | tr -d ' ')"
printf '%s' "$LOGS" | grep -E 'report load|REPORT_INVARIANT|FATAL' | tail -8 || true

# ---------- 断言 ----------
step "断言"
if log_has 'report load WEEK'; then
  pass "周报加载过一次"
else
  fail "日志里没有 report load WEEK"
fi
if log_has 'report load MONTH'; then
  pass "月报加载过一次"
else
  fail "日志里没有 report load MONTH"
fi
if log_has 'report load YEAR'; then
  pass "年报加载过一次"
else
  fail "日志里没有 report load YEAR"
fi
if log_has 'REPORT_INVARIANT'; then
  fail "报告一致性自检告警" "$(printf '%s' "$LOGS" | grep -m2 'REPORT_INVARIANT' | tr '\n' ' ')"
else
  pass "无不变量告警（totalMs / catMs / hourMs 自洽）"
fi
if log_has 'ARCHIVE_CORRUPT'; then
  fail "归档完整性告警" "$(printf '%s' "$LOGS" | grep -m2 'ARCHIVE_CORRUPT' | tr '\n' ' ')"
else
  pass "归档无完整性告警"
fi
if log_has 'FATAL EXCEPTION'; then
  fail "进程崩溃" "$(printf '%s' "$LOGS" | grep -m1 -A3 'FATAL EXCEPTION' | tr '\n' ' ')"
else
  pass "无 FATAL EXCEPTION"
fi
if [[ -n "$(adb_sh shell pidof com.dsh.tvhud 2>/dev/null)" ]]; then
  pass "进程仍在运行"
else
  fail "进程不在了（可能崩溃/被杀）"
fi
if resumed_activity | grep -q 'com.dsh.tvhud/.home.HomeActivity'; then
  pass "返回键回到首页"
else
  fail "返回键没有回到首页" "$(resumed_activity)"
fi

printf '\n截图与日志：%s\n' "$SHOTS"
printf '\n== 汇总 ==\nPASS %d  FAIL %d\n' "$PASS" "$FAIL"
if [[ "$FAIL" -gt 0 ]]; then
  printf '失败项：%s\n' "$FAILED"
  exit 1
fi
echo "真机冒烟通过。"
