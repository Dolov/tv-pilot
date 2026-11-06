#!/usr/bin/env bash
# 离线测试：用 tests/fake-adb 模拟电视，不需要真设备、不需要 .tools/、不联网。
#
# 运行：bash tests/run.sh   或   make test
# 退出码：0 全部通过；1 有用例失败；2 环境/前置条件不满足。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKILL="$ROOT/.agents/skills/tv-pilot"
FAKE="$ROOT/tests/fake-adb"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/tvpilot-tests.XXXXXX")" || exit 2
trap 'rm -rf "$TMP"' EXIT

# 所有脚本都从环境变量取 adb / 状态文件 / 工具目录；这里把它们指向假实现。
export TVPILOT_ADB_BIN="$FAKE"
TVPILOT_STATE_FILE="$(mktemp "$TMP/state.XXXXXX")" || exit 2
TVPILOT_TOOLS_DIR="$(mktemp -d "$TMP/tools.XXXXXX")" || exit 2
export TVPILOT_STATE_FILE TVPILOT_TOOLS_DIR
export TVPILOT_HOME="$TVPILOT_TOOLS_DIR/android-home"
# 隔离开发者本机的本地档案（~/.config/tv-pilot/devices），保证测试结果可复现。
export TVPILOT_DEVICES_DIR="$TMP/devices"
export TVPILOT_AUTO_BACKUP=0
export TV_ADB_TIMEOUT=5

PASS=0
FAIL=0
FAILED=""

pass_case() {
  PASS=$((PASS + 1))
  printf 'PASS  %s\n' "$*"
}

fail_case() {
  FAIL=$((FAIL + 1))
  FAILED="${FAILED}
  - $1"
  printf 'FAIL  %s\n' "$1"
  [[ -n "${2:-}" ]] && printf '      %s\n' "$2"
}

# 单条命令最多 30s，避免 fake 配置错误时卡在真实网络扫描。
run_limited() {
  perl -e 'alarm shift; exec @ARGV' 30 "$@"
}

# ---------- 前置条件 ----------
[[ -x "$FAKE" ]] || {
  echo "tests/fake-adb 不可执行：chmod +x tests/fake-adb" >&2
  exit 2
}
command -v python3 >/dev/null 2>&1 || {
  echo "需要 python3 来解析 --json 输出" >&2
  exit 2
}
command -v perl >/dev/null 2>&1 || {
  echo "需要 perl 实现测试超时" >&2
  exit 2
}

printf '== tv-pilot 离线测试 ==\n'
printf 'TVPILOT_ADB_BIN=%s\n' "$TVPILOT_ADB_BIN"

# ---------- 用例 1：identify-tv --json ----------
test_identify() {
  local name="identify-tv --json → matched + profile"
  local out rc msg
  out="$(run_limited bash "$SKILL/scripts/identify-tv.sh" --json 2>"$TMP/identify.err")"
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    fail_case "$name" "退出码 ${rc}：$(tr '\n' ' ' <"$TMP/identify.err" | cut -c1-200)"
    return
  fi
  if msg="$(printf '%s' "$out" | python3 -c '
import json, sys

d = json.load(sys.stdin)
match = d.get("match")
if isinstance(match, dict):
    match = match.get("status") or match.get("result")
if isinstance(match, bool):
    match = "matched" if match else "unknown"
if match is None:
    match = d.get("status")
assert match == "matched", "match/status=%r" % (match,)
profile = str(d.get("profile") or "")
assert profile.endswith("xiaomi-mitv-nsop0.md") or profile == "xiaomi-mitv-nsop0", "profile=%r" % (profile,)
print("profile=" + profile)
' 2>&1)"; then
    pass_case "${name}（${msg}）"
  else
    fail_case "$name" "$msg"
  fi
}

# ---------- 用例 2：remote-battery --json ----------
test_remote_battery() {
  local name="remote-battery --json → percent=85"
  local out rc msg
  out="$(run_limited bash "$SKILL/scripts/remote-battery.sh" --json 2>"$TMP/battery.err")"
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    fail_case "$name" "退出码 ${rc}：$(tr '\n' ' ' <"$TMP/battery.err" | cut -c1-200)"
    return
  fi
  if msg="$(printf '%s' "$out" | python3 -c '
import json, sys

d = json.load(sys.stdin)
pct = d.get("percent", d.get("battery"))
assert pct is not None, "缺少 percent/battery 字段"
assert int(pct) == 85, "percent/battery=%r，期望 85" % (pct,)
print("percent=%d" % int(pct))
' 2>&1)"; then
    pass_case "${name}（${msg}）"
  else
    fail_case "$name" "$msg"
  fi
}

# ---------- 用例 3：tv-state --json（无 --json 时退回人类可读输出）----------
test_tv_state() {
  local name="tv-state --json → 开机态"
  local out rc msg
  out="$(run_limited bash "$SKILL/scripts/tv-state.sh" --json 2>"$TMP/state.err")"
  rc=$?
  if [[ "$rc" -eq 0 ]]; then
    if msg="$(printf '%s' "$out" | python3 -c '
import json, sys

d = json.load(sys.stdin)
state = d.get("state")
assert state, "缺少 state 字段"
s = str(state)
ok = ("开机" in s) or (s.lower() in ("booted", "on", "awake", "running", "screen_on", "active"))
assert ok, "state=%r 不是开机态" % (s,)
print("state=" + s)
' 2>/dev/null)"; then
      pass_case "${name}（${msg}）"
      return
    fi
  fi

  # 兼容尚未实现 --json 的版本：用人类可读输出断言。
  out="$(run_limited bash "$SKILL/scripts/tv-state.sh" 2>"$TMP/state-human.err")"
  rc=$?
  # 兼容旧版 tv-state：最后一条 [[ ... ]] 为假时退出码是 1，但输出已经是结论。
  if [[ "$rc" -le 1 ]] && printf '%s' "$out" | grep -q '状态:.*开机'; then
    pass_case "${name}（人类可读输出：$(printf '%s' "$out" | head -1)）"
  else
    fail_case "$name" "退出码 ${rc}；输出：$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-200)"
  fi
}

# ---------- 用例 4：apps-inventory list ----------
test_apps_inventory() {
  local name="apps-inventory.sh list → 应用清单"
  local out rc
  out="$(run_limited bash "$SKILL/scripts/apps-inventory.sh" list 2>"$TMP/apps.err")"
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    fail_case "$name" "退出码 ${rc}：$(tr '\n' ' ' <"$TMP/apps.err" | cut -c1-200)"
    return
  fi
  if printf '%s' "$out" | grep -q '总数 5' && printf '%s' "$out" | grep -q 'com.example.thirdparty'; then
    pass_case "${name}（$(printf '%s' "$out" | head -1)）"
  else
    fail_case "$name" "输出缺少「总数 5」或第三方包：$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-200)"
  fi
}

# ---------- 用例 5：ensure-tv 连接阶梯 L0 ----------
test_ensure_tv() {
  local name="ensure-tv.sh → 复用已有连接"
  local out rc
  out="$(run_limited bash "$SKILL/scripts/ensure-tv.sh" 2>"$TMP/ensure.err")"
  rc=$?
  if [[ "$rc" -eq 0 && "$out" == "192.168.1.50:5555" ]]; then
    pass_case "${name}（${out}）"
  else
    fail_case "$name" "退出码 ${rc}；输出：$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-200)"
  fi
}

test_identify
test_remote_battery
test_tv_state
test_apps_inventory
test_ensure_tv

printf '\n== 汇总 ==\n'
printf 'PASS %d  FAIL %d\n' "$PASS" "$FAIL"
if [[ "$FAIL" -gt 0 ]]; then
  printf '失败用例：%s\n' "$FAILED"
  exit 1
fi
echo "全部离线测试通过。"
