#!/usr/bin/env bash
# 只读统计：电视今天亮了多久、用了哪些应用、播了什么内容。
# 用法: bash scripts/usage.sh [电视IP] [输出目录]（不传 IP 时自动走 ensure-tv 找电视）
# 产出: <输出目录>/ 下 usagestats.txt / events.txt / power.txt / display.txt / miothost.txt ...
#       并在终端打印今日摘要（亮屏窗口、分应用、播放记录、当前状态）
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IP="${1:-}"
if [[ -z "$IP" ]]; then
  IP="$(bash "$SKILL_DIR/scripts/ensure-tv.sh" 2>/dev/null | cut -d: -f1)" || true
fi
if [[ -z "$IP" ]]; then
  echo "找不到电视；可显式传入 IP：bash scripts/usage.sh <IP> [输出目录]" >&2
  exit 1
fi

ADB="$SKILL_DIR/scripts/adb.sh"
OUT="${2:-$PWD/probe/usage-$(date +%Y%m%d)}"
mkdir -p "$OUT"

if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3 做统计（macOS 自带或 brew install python）" >&2
  exit 1
fi

run() { "$ADB" -s "${IP}:5555" shell "$@" 2>/dev/null | tr -d '\r'; }

echo "连接 ${IP}:5555 ..."
"$ADB" connect "${IP}:5555" >/dev/null 2>&1 || true
if ! "$ADB" devices | grep -q "${IP}:5555[[:space:]]*device"; then
  echo "未连接成功。当前设备列表：" >&2
  "$ADB" devices >&2
  echo "unauthorized = 电视上授权框没按；offline/refused = 电视待机或 ADB 开关复位" >&2
  exit 1
fi

TODAY="$(run date +%Y-%m-%d)"
NOW="$(run date +%H:%M:%S)"
echo "电视时间: ${TODAY} ${NOW}"

echo "抓取原始数据 -> ${OUT}"
run 'dumpsys usagestats' > "$OUT/usagestats.txt"
run 'logcat -b events -d' > "$OUT/events.txt"
run 'dumpsys power' > "$OUT/power.txt"
run 'dumpsys display' > "$OUT/display.txt"
run 'dumpsys window | grep -m1 mCurrentFocus' > "$OUT/focus.txt"
run 'cat /proc/uptime' > "$OUT/uptime.txt"
if run 'test -f /sdcard/miio/miothost.txt && echo yes' | grep -q yes; then
  run 'cat /sdcard/miio/miothost.txt' > "$OUT/miothost.txt"
fi
run "logcat -b main -d | grep -E 'MiTVOtService_PW|video_name|PLAY_VIDEO_START' | tail -300" > "$OUT/main-video.txt"

echo "分析 ..."
python3 - "$OUT" "$TODAY" "$NOW" <<'PY'
import re, sys, datetime, collections
from pathlib import Path

out = Path(sys.argv[1])
today = datetime.datetime.strptime(sys.argv[2], '%Y-%m-%d').date()
now = datetime.datetime.strptime(sys.argv[2] + ' ' + sys.argv[3], '%Y-%m-%d %H:%M:%S')
day_start = datetime.datetime.combine(today, datetime.time.min)

def read(name):
    p = out / name
    return p.read_text(encoding='utf-8', errors='ignore') if p.exists() else ''

def fmt(sec):
    sec = int(round(sec))
    m, s = divmod(sec, 60)
    if m >= 60:
        h, m = divmod(m, 60)
        return f'{h}h{m:02d}m'
    return f'{m}m{s:02d}s'

# ---------- 1. 亮屏窗口（screen_toggled） ----------
ev = []
for line in read('events.txt').splitlines():
    if 'screen_toggled:' not in line:
        continue
    try:
        t = datetime.datetime.strptime(line[:18], '%m-%d %H:%M:%S.%f')
    except ValueError:
        continue
    t = t.replace(year=today.year)
    if (t.date() - today).days > 1:      # 跨年
        t = t.replace(year=today.year - 1)
    ev.append((t, int(line.split('screen_toggled:')[1].strip())))
ev.sort()

wins, on = [], None
for t, state in ev:
    if state == 1 and on is None:
        on = t
    elif state == 0 and on is not None:
        wins.append((on, t)); on = None
if on is not None:
    wins.append((on, now))
wins = [(max(a, day_start), min(b, now)) for a, b in wins]
wins = [(a, b) for a, b in wins if b > a]
total = sum((b - a).total_seconds() for a, b in wins)

print()
print('=== 今日亮屏 ===')
if not wins:
    print(f'  今天没有亮屏记录（或 events 缓冲区已清空，{today}）')
else:
    for a, b in wins:
        print(f'  {a:%H:%M:%S} – {b:%H:%M:%S}   {fmt((b - a).total_seconds())}')
    print(f'  合计 {fmt(total)}（截止 {now:%H:%M:%S}，最后一段可能还在进行）')

# ---------- 2. 分应用 / 分页面（usagestats 事件） ----------
pat = re.compile(r'time="([^"]+)" type=(\w+) package=([\w\.]+)(?: class=([\w\.\$]+))?')
app_t, act_t = collections.defaultdict(float), collections.defaultdict(float)
active = None

def clip(s, e):
    res = []
    for a, b in wins:
        s2, e2 = max(s, a), min(e, b)
        if e2 > s2:
            res.append((s2, e2))
    return res

def close(t):
    global active
    if active:
        s, pkg, cls = active
        for a, b in clip(s, t):
            d = (b - a).total_seconds()
            app_t[pkg] += d
            act_t[(pkg, cls)] += d
    active = None

for line in read('usagestats.txt').splitlines():
    m = pat.search(line)
    if not m:
        continue
    t = datetime.datetime.strptime(m.group(1), '%Y-%m-%d %H:%M:%S')
    if t.date() != today:
        continue
    typ, pkg, cls = m.group(2), m.group(3), m.group(4)
    if typ == 'MOVE_TO_FOREGROUND':
        close(t)
        active = (t, pkg, cls)
    elif typ == 'MOVE_TO_BACKGROUND' and active and active[1] == pkg:
        close(t)
close(now)

print()
print('=== 分应用（前台时长）===')
shown = [x for x in sorted(app_t.items(), key=lambda kv: -kv[1]) if x[1] >= 5][:10]
if not shown:
    print('  （今天没有前台应用记录）')
for pkg, v in shown:
    print(f'  {v/60:6.1f} 分  {pkg}')

print()
print('=== 分页面（Top）===')
shown = [x for x in sorted(act_t.items(), key=lambda kv: -kv[1]) if x[1] >= 5][:10]
for (pkg, cls), v in shown:
    print(f'  {v/60:6.1f} 分  {cls or pkg}')

# ---------- 3. 播放记录（米家上报 / 主日志） ----------
def parse_ts(line):
    m = re.match(r'(\d{4}):(\d{2}):(\d{2}):(\d{2}):(\d{2}):(\d{2})', line)
    if m:
        y, mo, d, h, mi, s = map(int, m.groups())
        return datetime.datetime(y, mo, d, h, mi, s)
    m = re.match(r'(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})', line)
    if m:
        mo, d, h, mi, s = map(int, m.groups())
        return datetime.datetime(today.year, mo, d, h, mi, s)
    return None

def field(norm, key, cast=int):
    m = re.search(rf'"{key}"\s*:\s*"?([\w\.\-]+)"?', norm)
    if not m:
        return None
    try:
        return cast(m.group(1))
    except Exception:
        return None

plays = []
for fn in ('miothost.txt', 'main-video.txt'):
    for line in read(fn).splitlines():
        if 'video_name' not in line:
            continue
        norm = line.replace('\\"', '"')
        vn = re.search(r'"video_name"\s*:\s*"([^"]+)"', norm)
        if not vn:
            continue
        plays.append({
            'time': parse_ts(line) or now,
            'name': vn.group(1),
            'ci': field(norm, 'ci'),
            'total_ci': field(norm, 'total_ci'),
            'duration': field(norm, 'total_duration'),
            'status': field(norm, 'play_status'),
            'pos': field(norm, 'current_position'),
            'src': fn,
        })

print()
print('=== 播放记录（当天）===')
today_plays = [p for p in plays if p['time'].date() == today]
if not today_plays:
    if plays:
        print(f'  今天没有（缓冲区里最近一条是 {max(p["time"] for p in plays):%m-%d %H:%M}，可能已跨天）')
    else:
        print('  没找到（米家日志可能已轮转，或当天没播放）')
else:
    last = {}
    for p in today_plays:
        key = (p['name'], p['ci'])
        if key not in last or p['time'] > last[key]['time']:
            last[key] = p
    for (_, ci), p in sorted(last.items(), key=lambda kv: kv[1]['time']):
        dur = f"{p['duration']/1000:.0f}s" if p['duration'] else '?'
        ep = f"第{ci}集" if ci else ''
        tot = f"/全{p['total_ci']}集" if p['total_ci'] else ''
        print(f"  {p['time']:%H:%M:%S}  《{p['name']}》{ep}{tot}   单集 {dur}   [{p['src']}]")
    print('  提示：米家日志会轮转、主日志只留约 1 小时；更早的场次可能查不到。')

# ---------- 4. 当前状态 ----------
print()
print('=== 当前状态 ===')
for line in read('power.txt').splitlines():
    if any(k in line for k in ('mWakefulness=', 'mLastWakeTime', 'mLastSleepTime', 'mLastUserActivityTime')):
        print('  ' + line.strip())
for line in read('display.txt').splitlines():
    if 'mScreenState=' in line:
        print('  ' + line.strip())
        break
for line in read('focus.txt').splitlines():
    print('  ' + line.strip())
up = read('uptime.txt').split()
if up:
    print(f'  系统已运行 {float(up[0])/86400:.1f} 天')
PY

echo
echo "原始数据: $OUT"
echo "口径与坑: $SKILL_DIR/references/usage-stats.md"
