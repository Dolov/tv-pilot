#!/usr/bin/env bash
# 在局域网里找小米电视：从 AirPlay/RAOP 的 mDNS 广播里拿「MAC@设备名」，
# 再用 ARP 表把 MAC 换成 IP。只在「不知道电视 IP」时用。
#
# 已知 IP 或 adb devices 里已有设备时，请直接用 scripts/ensure-tv.sh 的快路径，
# 别跑这个脚本——它天生要 6-15 秒。
#
# 用法:
#   find-tv.sh [--fast] [--list] [--json] [关键词]
#     --fast   跳过 5555 扫描和 ARP 明细，只做 ping 扫段 + mDNS（约 6s）
#     --list   只输出发现的 IP，一行一个（给脚本消费，ensure-tv.sh 用这个）
#     --json   输出 JSON 数组
#     关键词   按设备名过滤，如 "电视"、"客厅"
#   默认：设备名 / IP / MAC + ARP 存活主机 + 5555 状态
#
# 每一步都有硬上限：ping 并行、mDNS 只等 5 秒、dns-sd 用 SIGKILL 收尾，不会挂死。
set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SKILL_DIR/scripts/lib.sh"

FAST=0
LIST=0
JSON=0
KEYWORD=""
for a in "$@"; do
  case "$a" in
    --fast) FAST=1 ;;
    --list) LIST=1 ;;
    --json) JSON=1 ;;
    -*) ;;
    *) KEYWORD="$a" ;;
  esac
done

t0=$(now_ms)
IFACE="$(route -n get default 2>/dev/null | awk '/interface:/{print $2}')"
SUBNET="$(ipconfig getifaddr "$IFACE" 2>/dev/null | sed 's/\.[0-9]*$//')"
if [[ -z "$SUBNET" ]]; then
  echo "拿不到本机网段（检查是否联网，或终端是否有本地网络权限）" >&2
  exit 1
fi
[[ "$LIST" == 0 ]] && echo "本机网段: ${SUBNET}.0/24 (接口 ${IFACE})"

# 1) mDNS 监听挂到后台，先不阻塞
mdns_file="$(mktemp)"
dpid=""
if command -v dns-sd >/dev/null 2>&1; then
  dns-sd -B _raop._tcp local >"$mdns_file" 2>&1 &
  dpid=$!
fi

# 2) 并行 ping 扫段，把在线设备塞进 ARP 缓存（与 mDNS 并行，省时间）
(
  i=1
  while [[ "$i" -le 254 ]]; do
    ping -c1 -W 300 "${SUBNET}.${i}" >/dev/null 2>&1 &
    i=$((i + 1))
  done
  wait
) 2>/dev/null

# 3) 等 mDNS 结果：最多 5 秒，但一发现设备就提前收工（通常 1.5–2s）。
#    收尾用 SIGKILL：dns-sd 可能忽略 TERM，用 kill 后 wait 会永久卡死。
if [[ -n "$dpid" ]]; then
  i=0
  while [[ "$i" -lt 10 ]]; do
    sleep 0.5
    grep -q '@' "$mdns_file" 2>/dev/null && break
    i=$((i + 1))
  done
  kill -9 "$dpid" 2>/dev/null || true
  wait "$dpid" 2>/dev/null || true
fi
mdns_window=$(elapsed_fmt "$t0")

# 4) 解析：dns-sd 输出最后一列是实例名，形如 <12位MAC>@<设备名>
entries="$(sed -n 's/.*_raop\._tcp\. *//p' "$mdns_file" 2>/dev/null \
  | sed 's/[[:space:]]*$//' | sort -u)"
rm -f "$mdns_file"

found_ips=""
json_items=""
while IFS= read -r entry; do
  [[ -z "$entry" ]] && continue
  case "$entry" in
    [0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]@*) ;;
    *) continue ;;
  esac
  mac_hex="${entry%%@*}"
  name="${entry#*@}"
  [[ -n "$KEYWORD" && "$name" != *"$KEYWORD"* ]] && continue
  mac="$(printf '%s' "$mac_hex" | tr 'A-F' 'a-f' | sed 's/\(..\)/\1:/g; s/:$//')"
  ip="$(arp -an 2>/dev/null | grep -i " $mac " | head -1 | awk '{gsub(/[()]/,"",$2); print $2}')"
  [[ -n "$ip" ]] && found_ips="$found_ips $ip"
  if [[ "$JSON" == 1 ]]; then
    json_items="$json_items${json_items:+,}{\"name\":\"$name\",\"mac\":\"$mac\",\"ip\":\"${ip:-}\"}"
  elif [[ "$LIST" == 0 ]]; then
    printf '  %-16s %-20s %s\n' "${ip:-ARP中未找到}" "$mac" "$name"
  fi
  [[ "$LIST" == 1 && -n "$ip" ]] && echo "$ip"
done <<<"$entries"

# 5) 全量模式：再看看 ARP 里还有谁、哪些开了 5555
if [[ "$FAST" == 0 && "$LIST" == 0 ]]; then
  arp_hosts="$(arp -an 2>/dev/null | grep -E "${SUBNET//./\\.}\." | grep -v incomplete \
    | awk '{gsub(/[()]/,"",$2); print $2}')"
  echo
  echo "=== ARP 表中的存活主机 ==="
  printf '%s\n' "$arp_hosts" | sed 's/^/  /'
  echo
  echo "=== 5555 (ADB) 状态 ==="
  # 不用 nc：macOS 的 nc 对丢包主机会无视 -w 1 卡很久（实测 14 台扫了 75s）。
  # /dev/tcp + perl alarm 给每台 1 秒硬上限，并行扫完约 1s。
  for ip in $arp_hosts; do
    ( run_timeout 1 bash -c "exec 3<>/dev/tcp/${ip}/5555" 2>/dev/null \
      && echo "  ${ip}  5555 OPEN" ) 2>/dev/null &
  done
  wait
fi

if [[ "$JSON" == 1 ]]; then
  printf '[%s]\n' "$json_items"
fi

[[ "$LIST" == 0 ]] && echo "[find-tv] 耗时 $(elapsed_fmt "$t0")（mDNS 窗口 ${mdns_window}）" >&2
if [[ -z "${entries//[[:space:]]/}" ]]; then
  [[ "$LIST" == 0 ]] && echo "没找到 AirPlay 设备。可能是：电视没开、不在同一网段，或它不是 AirPlay 电视。" >&2
  exit 1
fi
exit 0
