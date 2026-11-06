#!/usr/bin/env bash
# tv-pilot 仓库自检（make check 调用）。只读仓库，不连电视、不下载工具链。
#
# 覆盖：bash 语法 / shellcheck(error) / JSON / 设备档案 lint+index / 文档引用 /
#      版本一致性 / 评测结构 / SKILL.md 结构 / git 中是否混入敏感或构建产物。
#
# 退出码：0 全部通过；1 有 FAIL；2 环境错误（找不到仓库根、临时目录不可写）。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  # 非 git 环境（如打包后的副本）按脚本位置回退：scripts -> tv-pilot -> skills -> .agents -> 根
  REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." 2>/dev/null && pwd)" || {
    echo "无法定位仓库根（脚本位置: ${SCRIPT_DIR}）" >&2
    exit 2
  }
fi
SKILL_DIR="$REPO_ROOT/.agents/skills/tv-pilot"

if [[ ! -f "$SKILL_DIR/SKILL.md" ]]; then
  echo "仓库根似乎不对：$REPO_ROOT 下没有 .agents/skills/tv-pilot/SKILL.md" >&2
  exit 2
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tvpilot-check.XXXXXX")" || exit 2
trap 'rm -rf "$TMP_DIR"' EXIT

PASS=0
FAIL=0
SKIP=0
FAILS=""

pass() {
  PASS=$((PASS + 1))
  printf 'PASS  %s\n' "$*"
}

fail() {
  FAIL=$((FAIL + 1))
  FAILS="${FAILS}
  - $*"
  printf 'FAIL  %s\n' "$*"
}

skip() {
  SKIP=$((SKIP + 1))
  printf 'SKIP  %s\n' "$*"
}

section() {
  printf '\n== %s ==\n' "$*"
}

# ---------- 1. bash -n ----------
section "1. Shell 语法（bash -n）"
sh_total=0
sh_bad=0
while IFS= read -r f; do
  sh_total=$((sh_total + 1))
  if ! bash -n "$f" 2>"$TMP_DIR/bashn.err"; then
    sh_bad=$((sh_bad + 1))
    fail "bash -n ${f#"$REPO_ROOT"/}"
    sed -n '1,3p' "$TMP_DIR/bashn.err" | sed 's/^/      /'
  fi
done < <(find "$REPO_ROOT" \
  \( -name .git -o -name .tools -o -name .build \) -prune -o \
  -name '*.sh' -print | sort)
if [[ "$sh_bad" -eq 0 ]]; then
  pass "bash -n：${sh_total} 个脚本通过"
fi

# ---------- 2. shellcheck（只报 error）----------
section "2. shellcheck（-S error -x）"
if command -v shellcheck >/dev/null 2>&1; then
  sc_bad=0
  while IFS= read -r f; do
    if ! sc_out="$(shellcheck -S error -x "$f" 2>&1)"; then
      sc_bad=$((sc_bad + 1))
      fail "shellcheck ${f#"$REPO_ROOT"/}"
      printf '%s\n' "$sc_out" | sed -n '1,8p' | sed 's/^/      /'
    fi
  done < <(find "$REPO_ROOT" \
    \( -name .git -o -name .tools -o -name .build \) -prune -o \
    -name '*.sh' -print | sort)
  if [[ "$sc_bad" -eq 0 ]]; then
    pass "shellcheck -S error -x：${sh_total} 个脚本通过"
  fi
else
  skip "未安装 shellcheck（CI 会安装；本地 macOS 可 brew install shellcheck）"
fi

# ---------- 3. JSON ----------
section "3. JSON 合法性（python3 -m json.tool）"
if ! command -v python3 >/dev/null 2>&1; then
  fail "找不到 python3，无法校验 JSON / 评测结构 / 文档引用"
else
  json_total=0
  json_bad=0
  while IFS= read -r f; do
    json_total=$((json_total + 1))
    if ! python3 -m json.tool "$f" >/dev/null 2>"$TMP_DIR/json.err"; then
      json_bad=$((json_bad + 1))
      fail "JSON 非法: ${f#"$REPO_ROOT"/}"
      sed -n '1,3p' "$TMP_DIR/json.err" | sed 's/^/      /'
    fi
  done < <(find "$REPO_ROOT" \
    \( -name .git -o -name .tools -o -name .build \) -prune -o \
    -name '*.json' -print | sort)
  if [[ "$json_bad" -eq 0 ]]; then
    pass "JSON：${json_total} 个文件合法"
  fi
fi

# ---------- 4. 设备档案 lint ----------
section "4. 设备档案 lint"
if lint_out="$(bash "$SKILL_DIR/scripts/device-profile.sh" lint 2>&1)"; then
  pass "$(printf '%s\n' "$lint_out" | tail -1)"
else
  fail "device-profile.sh lint 未通过"
  printf '%s\n' "$lint_out" | sed 's/^/      /'
fi

# ---------- 5. 设备支持矩阵 index 是否过期 ----------
section "5. 设备支持矩阵（index）"
DEVICE_README_REL=".agents/skills/tv-pilot/references/devices/README.md"
pre_dirty="$(git -C "$REPO_ROOT" status --porcelain -- "$DEVICE_README_REL" 2>/dev/null || true)"
if ! index_out="$(bash "$SKILL_DIR/scripts/device-profile.sh" index 2>&1)"; then
  fail "device-profile.sh index 执行失败"
  printf '%s\n' "$index_out" | sed 's/^/      /'
elif ! git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  skip "不是 git 仓库，跳过 index diff 断言"
elif [[ -n "$pre_dirty" ]]; then
  skip "references/devices/README.md 在 index 前已有改动，跳过 diff 断言（先提交或还原再跑）"
elif git -C "$REPO_ROOT" diff --quiet -- "$DEVICE_README_REL" 2>/dev/null; then
  pass "references/devices/README.md 与 index 结果一致"
else
  fail "references/devices/README.md 与 index 结果不一致：运行 make index 并提交矩阵变更"
fi

# ---------- 6. 文档引用存在性 ----------
section "6. 文档引用存在性"
if command -v python3 >/dev/null 2>&1; then
  if doc_out="$(python3 - "$REPO_ROOT" "$SKILL_DIR" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1]).resolve()
skill = pathlib.Path(sys.argv[2]).resolve()

# 生成物 / 本机事实：clean checkout 中本来就不存在，不算失效引用。
EXEMPT = {"references/local.md"}
# 文档里明确作为示例的新机型 slug（docs/providers.md 以 hisense-a6g 为例），不是真实链接。
EXAMPLE_SLUGS = ("hisense-a6g",)

PATTERNS = [
    re.compile(r"(?<![A-Za-z0-9_.-])references/[A-Za-z0-9_./-]+\.md"),
    re.compile(r"(?<![A-Za-z0-9_.-])scripts/[A-Za-z0-9_./-]+\.sh"),
    re.compile(r"(?<![A-Za-z0-9_.-])docs/[A-Za-z0-9_./-]+\.md"),
    re.compile(r"(?<![A-Za-z0-9_.-])evals/[A-Za-z0-9_./-]+"),
    re.compile(r"(?<![A-Za-z0-9_.-])app/[A-Za-z0-9_./-]+"),
]

files = []
for path in root.rglob("*.md"):
    parts = set(path.parts)
    if ".git" in parts or ".tools" in parts or ".build" in parts:
        continue
    files.append(path)

missing = []
seen = set()
total = 0
for path in files:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        continue
    for pattern in PATTERNS:
        for match in pattern.finditer(text):
            ref = match.group(0).rstrip(".,;:)")
            # 反引号用 chr(96) 表示：bash 3.2 的 heredoc 在 $() 里会误判字面反引号。
            if any(ch in ref for ch in "<>*" + chr(96) + "$"):
                continue
            if "..." in ref or ref in EXEMPT:
                continue
            # 文档里的占位示例（references/xxx.md、新机型 hisense-a6g 等）不当作真实引用。
            if "xxx" in ref.lower() or "placeholder" in ref.lower():
                continue
            if any(slug in ref.lower() for slug in EXAMPLE_SLUGS):
                continue
            total += 1
            if (root / ref).exists() or (skill / ref).exists():
                continue
            key = (str(path.relative_to(root)), ref)
            if key in seen:
                continue
            seen.add(key)
            missing.append(key)

if missing:
    for rel, ref in sorted(missing):
        print(f"{rel}: {ref}")
    sys.exit(1)

print(f"{total} 处路径引用均可解析")
PY
)"; then
    pass "$doc_out"
  else
    fail "文档引用缺失（相对仓库根或 skill 根都找不到）"
    printf '%s\n' "$doc_out" | sed 's/^/      /'
  fi
fi

# ---------- 7. 版本一致性 ----------
section "7. 版本一致性（version.properties / Manifest / build.sh）"
VERSION_FILE="$REPO_ROOT/version.properties"
MANIFEST="$REPO_ROOT/app/AndroidManifest.xml"
BUILD_SH="$REPO_ROOT/app/build.sh"
if [[ ! -f "$VERSION_FILE" ]]; then
  fail "缺少根 version.properties（两行：versionName=1.11 / versionCode=12）"
else
  v_name="$(sed -n 's/^versionName=//p' "$VERSION_FILE" | head -1 | tr -d '[:space:]')"
  v_code="$(sed -n 's/^versionCode=//p' "$VERSION_FILE" | head -1 | tr -d '[:space:]')"
  man_code="$(sed -n 's/.*android:versionCode="\([0-9][0-9]*\)".*/\1/p' "$MANIFEST" | head -1)"
  man_name="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
  if [[ -z "$v_name" || -z "$v_code" ]]; then
    fail "version.properties 缺少 versionName= 或 versionCode="
  elif [[ "$v_name" != "$man_name" || "$v_code" != "$man_code" ]]; then
    fail "版本不一致：version.properties=${v_name}/${v_code}, manifest=${man_name}/${man_code}（build.sh 读取 properties，无需同步硬编码）"
  elif ! grep -q 'version.properties' "$BUILD_SH"; then
    fail "app/build.sh 没有读取 version.properties（版本号应只有一个来源）"
  else
    pass "版本一致：${v_name} (${v_code})，build.sh 读取 version.properties"
  fi
fi

# ---------- 8. 评测集结构 ----------
section "8. 评测集结构（evals/）"
if command -v python3 >/dev/null 2>&1; then
  if evals_out="$(python3 - "$SKILL_DIR" <<'PY'
import json
import pathlib
import sys

skill = pathlib.Path(sys.argv[1])
errors = []


def load(name):
    path = skill / name
    try:
        with path.open(encoding="utf-8") as fh:
            return json.load(fh)
    except FileNotFoundError:
        errors.append(f"缺少 {name}")
    except json.JSONDecodeError as exc:
        errors.append(f"{name} 不是合法 JSON: {exc}")
    return None


evals = load("evals/evals.json")
queries = load("evals/trigger-queries.json")

if isinstance(evals, dict):
    if not isinstance(evals.get("skill_name"), str) or not evals.get("skill_name"):
        errors.append("evals.json: skill_name 必须是非空字符串")
    items = evals.get("evals")
    if not isinstance(items, list) or not items:
        errors.append("evals.json: evals 必须是非空数组")
    else:
        for i, item in enumerate(items):
            where = f"evals.json: evals[{i}]"
            if not isinstance(item, dict):
                errors.append(f"{where} 必须是对象")
                continue
            if not isinstance(item.get("id"), int):
                errors.append(f"{where}.id 必须是整数")
            if not isinstance(item.get("prompt"), str) or not item.get("prompt"):
                errors.append(f"{where}.prompt 必须是非空字符串")
            expectations = item.get("expectations")
            if not isinstance(expectations, list) or not expectations:
                errors.append(f"{where}.expectations 必须是非空数组")
            elif any(not isinstance(x, str) or not x for x in expectations):
                errors.append(f"{where}.expectations 只能包含非空字符串")
elif evals is not None:
    errors.append("evals.json: 顶层必须是对象")

if isinstance(queries, dict):
    qs = queries.get("queries")
    if not isinstance(qs, list) or not qs:
        errors.append("trigger-queries.json: queries 必须是非空数组")
    else:
        for i, item in enumerate(qs):
            where = f"trigger-queries.json: queries[{i}]"
            if not isinstance(item, dict):
                errors.append(f"{where} 必须是对象")
                continue
            if not isinstance(item.get("query"), str) or not item.get("query"):
                errors.append(f"{where}.query 必须是非空字符串")
            if not isinstance(item.get("should_trigger"), bool):
                errors.append(f"{where}.should_trigger 必须是布尔值")
elif queries is not None:
    errors.append("trigger-queries.json: 顶层必须是对象")

if errors:
    print("\n".join(errors))
    sys.exit(1)

print(f"evals {len(evals['evals'])} 条；trigger-queries {len(queries['queries'])} 条")
PY
)"; then
    pass "$evals_out"
  else
    fail "评测集结构不合法"
    printf '%s\n' "$evals_out" | sed 's/^/      /'
  fi
fi

# ---------- 9. SKILL.md 结构 ----------
section "9. SKILL.md 结构"
SKILL_MD="$SKILL_DIR/SKILL.md"
skill_lines="$(wc -l <"$SKILL_MD" | tr -d '[:space:]')"
fm_ok=1
grep -q '^name:' "$SKILL_MD" || fm_ok=0
grep -q '^description:' "$SKILL_MD" || fm_ok=0
if [[ "$skill_lines" -lt 400 && "$fm_ok" -eq 1 ]]; then
  pass "SKILL.md ${skill_lines} 行（<400），frontmatter 含 name/description"
else
  fail "SKILL.md 结构不合格：${skill_lines} 行（要求 <400），frontmatter name/description=${fm_ok}"
fi

# ---------- 10. 禁止提交的文件 ----------
section "10. git 跟踪文件中是否混入敏感/构建产物"
if ! git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  skip "当前目录不是 git 仓库，跳过 git ls-files 检查"
else
  tracked_bad="$(git -C "$REPO_ROOT" ls-files | grep -E '(^|/)\.env$|\.apk$|(^|/)\.tools/|^app/assets/.*\.(otf|ttf)$' || true)"
  tracked_total="$(git -C "$REPO_ROOT" ls-files | wc -l | tr -d '[:space:]')"
  if [[ -z "$tracked_bad" ]]; then
    pass "git ls-files 干净（${tracked_total} 个文件；无 .env / APK / .tools / 字体）"
  else
    fail "以下文件不应被 git 跟踪"
    printf '%s\n' "$tracked_bad" | sed 's/^/      /'
  fi
fi

# ---------- 汇总 ----------
printf '\n================ 汇总 ================\n'
printf 'PASS %d  FAIL %d  SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
if [[ "$FAIL" -gt 0 ]]; then
  printf '失败项：%s\n' "$FAILS"
  exit 1
fi
echo "全部检查通过。"
