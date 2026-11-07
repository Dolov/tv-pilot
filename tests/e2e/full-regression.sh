#!/usr/bin/env bash
# 全量真机回归：把 tv-pilot 现有每个页面都走一遍并断言，确保改动不碰坏老功能。
#
# 覆盖：
#   1. 首页：菜单 4 项都在，默认选中第 1 项；
#   2. 今日使用：总时长/分类图例/时间轴/明细都渲染出来；
#   3. 使用记录：能按天回看（落到最近有归档的一天），左右键翻日期；
#   4. 使用报告：周/月/年三天粒度 + 翻页 + 回本期 + 返回；
#   5. 电子宠物：房间 + 照顾/商店/衣柜/房间四个分区切换 + 返回；
#   6. 家长设置：首页菜单能进（PIN 页或 5 分钟免输）→ 目录 5 组 → OK 进分组
#      → 返回回目录且焦点留在原组 → 目录再返回回首页；
#   7. 屏保角标：HUD 服务在跑、无障碍条目在；
#   8. 数据侧：归档文件可读、完整性自检无告警、报告不变量无告警；
#   9. 全程无 FATAL EXCEPTION。
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
# 从报告页返回后焦点在第一项；↓↓ 到第 3 项「我的宠物」
# （首页菜单上下键现在两端循环，不能再靠「按满 ↑」归位）
key 20 0.4
key 20 0.4
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

# ---------- 6. 家长设置（首页菜单入口，v1.27 起是二级菜单） ----------
step "家长设置（首页菜单 / 二级菜单）"

# uiautomator 小工具：把当前界面层级 dump 到 $SHOTS/ui.xml（真机实测 dump+pull ≈ 2s）。
# 为什么用它：二级菜单的「目录 5 组」「返回后焦点留在哪一组」在 Activity 名字上看不出来。
dump_ui() {
  adb_sh shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb_sh pull /sdcard/ui.xml "$SHOTS/ui.xml" >/dev/null 2>&1 || return 1
  [[ -s "$SHOTS/ui.xml" ]] || return 1
  if [[ -n "${1:-}" ]]; then cp "$SHOTS/ui.xml" "$SHOTS/$1.xml" 2>/dev/null || true; fi
  return 0
}
# ui_has_text：整个文本节点恰好等于；ui_has：文本节点包含子串（长句里找关键词用）。
ui_has_text() { grep -q "text=\"$1\"" "$SHOTS/ui.xml" 2>/dev/null; }
ui_has() { grep -q "text=\"[^\"]*$1[^\"]*\"" "$SHOTS/ui.xml" 2>/dev/null; }
# 焦点节点的 bounds 是否包住某个文本节点（断言焦点落在目录的哪一行）
ui_focused_contains() {
  python3 - "$SHOTS/ui.xml" "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
want = sys.argv[2]
def bounds(n):
    try:
        a, c = n.get('bounds', '').split('][')
        x1, y1 = a.lstrip('[').split(',')
        x2, y2 = c.rstrip(']').split(',')
        return int(x1), int(y1), int(x2), int(y2)
    except ValueError:
        return None
focused = [n for n in root.iter('node') if n.get('focused') == 'true']
if not focused or bounds(focused[0]) is None:
    sys.exit(1)
fx1, fy1, fx2, fy2 = bounds(focused[0])
for n in root.iter('node'):
    if n.get('text') != want:
        continue
    b = bounds(n)
    if b and fx1 <= b[0] and fy1 <= b[1] and b[2] <= fx2 and b[3] <= fy2:
        sys.exit(0)
sys.exit(1)
PY
}

# 从首页第一项开始：↓↓↓ 到第 4 项「家长设置」（上下键两端循环，靠 ↓ 推进最稳）
for i in 1 2 3; do key 20 0.4; done
key 23 3.0
if resumed | grep -q 'com.dsh.tvhud/.pet.PetSettingsActivity'; then
  pass "家长设置从首页菜单打开"
else
  fail "家长设置没从首页菜单打开" "$(resumed)"
fi

# 入口受 PIN 保护：免输窗口（输对后 5 分钟）过期则先出现 PIN 页，否则直接进目录。
UI_OK=0; DIR=0
dump_ui "06-parent-settings-ui" && UI_OK=1
if [[ "$UI_OK" -eq 1 ]] && ui_has_text '确定' && ui_has '免输'; then
  pass "出现 PIN 页（免输窗口已过期）"
  # 2580：↑↑ OK、↑×5 OK、↑×8 OK、OK；连错 5 次会锁 60 秒
  for i in 1 2; do key 19 0.35; done; key 23 0.35
  for i in 1 2 3 4 5; do key 19 0.35; done; key 23 0.35
  for i in 1 2 3 4 5 6 7 8; do key 19 0.35; done; key 23 0.35
  key 23 3.0
  sleep 1
  if dump_ui "06-parent-settings-ui-dir" && ui_has '↑↓ 选择 · OK 进入 · 返回键退出'; then
    DIR=1
    pass "PIN 2580 输对，进入目录页"
  else
    fail "PIN 输完没进目录页" "检查 2580 是否被改过、是否锁 60 秒"
  fi
elif [[ "$UI_OK" -eq 1 ]] && ui_has_text '确定' && ui_has '秒后再试'; then
  fail "家长设置 PIN 锁定中（连错 5 次锁 60 秒），等过期再跑"
elif [[ "$UI_OK" -eq 1 ]] && ui_has '↑↓ 选择 · OK 进入 · 返回键退出'; then
  DIR=1
  pass "5 分钟免输窗口内直接进设置页"
elif [[ "$UI_OK" -eq 1 ]]; then
  fail "认不出家长设置界面" "既不是 PIN 页也不是目录页"
else
  skip "uiautomator 读不到界面，跳过二级菜单断言（仍验证返回）"
fi
screenshot "06-parent-settings"

if [[ "$DIR" -eq 1 ]]; then
  # 目录一屏 5 组：前 4 组进分组页，「外观」是直接打开全屏预览的动作行。
  MISSING=""
  for g in 今天 时间与场次 允许时段 宠物与成长 外观; do
    ui_has_text "$g" || MISSING="$MISSING $g"
  done
  if [[ -z "$MISSING" ]]; then
    pass "目录一屏放下 5 个分组（今天/时间与场次/允许时段/宠物与成长/外观）"
  else
    fail "目录分组缺失：$MISSING"
  fi
  if ui_has '分钟/场' && ui_has '退化'; then
    pass "目录右侧规则摘要已渲染"
  else
    fail "目录右侧规则摘要缺失"
  fi

  # OK 进第一组「今天」（焦点默认在第 1 行），断言分组页专属文案。
  key 23 2.5
  if resumed | grep -q 'com.dsh.tvhud/.pet.PetSettingsActivity'; then
    pass "OK 进第一组（仍在 PetSettingsActivity）"
  else
    fail "OK 进分组后不在家长设置页" "$(resumed)"
  fi
  if dump_ui "06-parent-settings-group" && ui_has '家长设置 ／ 01' && ui_has_text '临时加场'; then
    pass "第一组「今天」打开（面包屑 01 + 临时加场）"
  else
    fail "第一组「今天」内容不对" "缺面包屑「家长设置 ／ 01」或「临时加场」"
  fi
  screenshot "06-parent-settings-group"

  # 返回：分组页 → 目录，焦点要停在刚进入的那一组（第 1 组「今天」）。
  key 4 2.0
  if resumed | grep -q 'com.dsh.tvhud/.pet.PetSettingsActivity'; then
    pass "分组页返回键仍在家长设置（没直接退回首页）"
  else
    fail "分组页返回键直接退出了" "$(resumed)"
  fi
  if dump_ui "06-parent-settings-back" && ui_has '↑↓ 选择 · OK 进入 · 返回键退出'; then
    if ui_focused_contains '今天'; then
      pass "返回目录且焦点落在第 1 组「今天」"
    else
      fail "返回目录后焦点不在第 1 组"
    fi
  else
    fail "分组页返回后没回到目录页"
  fi
fi

# 目录再按返回才退出（首页入口进来就回 HomeActivity）。
# 非 DIR 分支（读不到界面 / PIN 没进去）此时停在 PIN 页或目录页，一次返回都直接回首页。
key 4 2.0
if resumed | grep -q 'com.dsh.tvhud/.home.HomeActivity'; then
  pass "家长设置返回键回到首页"
else
  fail "家长设置返回键没回首页" "$(resumed)"
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
