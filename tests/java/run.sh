#!/usr/bin/env bash
# 报告核心纯 Java 单元测试（不需要 Android SDK、不需要电视，只需要 javac）。
#
# 为什么单独一摊：tvhud 是「无 Gradle、直接 javac」的纯 Java 应用，没有 JUnit 基础设施。
# 这里用最小的 android/org.json 桩（tests/java/stubs）把纯数据/纯计算类编出来，
# 再用一个自带断言框架的 main（tests/java/src/.../ReportCoreTest.java）跑用例。
#
# 运行：bash tests/java/run.sh   （make test 会调用）
# 退出码：0=全部通过，1=有失败，2=环境不满足（调用方按 SKIP 处理）。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
APP_SRC="$ROOT/tvhud/src/com/dsh/tvhud"

# 找到「真的能跑」的 javac/java：PATH 里的 /usr/bin/javac 在 macOS 上可能是个桩，
# 所以除了 command -v 还要真的执行一次 -version。
java_tool_works() {
  "$1" -version >/dev/null 2>&1
}

JAVAC="${JAVAC:-}"
JAVA="${JAVA:-}"
if [[ -z "$JAVAC" ]] && command -v javac >/dev/null 2>&1 && java_tool_works javac; then
  JAVAC=javac
  JAVA=java
fi

# 本机 JDK 不可用时，回退到 skill 固化的 .tools JDK（make apk 用的那个）
if [[ -z "$JAVAC" ]]; then
  for f in "$ROOT/.agents/skills/tv-pilot/scripts/build-tools.sh" \
           "${HOME:-/nonexistent}/.agents/skills/tv-pilot/scripts/build-tools.sh"; do
    [[ -f "$f" ]] || continue
    eval "$(bash "$f" env 2>/dev/null)" || true
    if [[ -n "${TVPILOT_JAVA_HOME:-}" && -x "$TVPILOT_JAVA_HOME/bin/javac" ]] \
        && java_tool_works "$TVPILOT_JAVA_HOME/bin/javac"; then
      JAVAC="$TVPILOT_JAVA_HOME/bin/javac"
      JAVA="$TVPILOT_JAVA_HOME/bin/java"
      break
    fi
  done
fi

if [[ -z "$JAVAC" ]]; then
  echo "SKIP 需要可用的 javac（设置 JAVAC/JAVA，或先跑 scripts/build-tools.sh ensure）"
  exit 2
fi
if [[ -z "$JAVA" ]]; then
  JAVA="${JAVAC%/javac}/java"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/tvhud-core-tests.XXXXXX")" || exit 2
trap 'rm -rf "$TMP"' EXIT
OUT="$TMP/classes"
mkdir -p "$OUT"

# 只编「纯逻辑 + 需要 Android 桩的纯数据类」，UI / Service / Storage 不在离线覆盖范围
SOURCES=(
  "$APP_SRC/Categories.java"
  "$APP_SRC/DayAgg.java"
  "$APP_SRC/BarStack.java"
  "$APP_SRC/ChartAxis.java"
  "$APP_SRC/PlayEntry.java"
  "$APP_SRC/Report.java"
  "$APP_SRC/ReportMath.java"
  "$APP_SRC/ReportPeriod.java"
  "$APP_SRC/UsageDay.java"
  "$APP_SRC/ArchivePolicy.java"
  "$APP_SRC/ArchiveBackfill.java"
  "$APP_SRC/HistoryStore.java"
)

# 用数组收集桩文件：路径带空格也不会被拆开（macOS bash 3.2 也支持）
STUBS=()
while IFS= read -r f; do
  STUBS+=("$f")
done < <(find "$HERE/stubs" -name '*.java')

"$JAVAC" -encoding UTF-8 -d "$OUT" \
  "${STUBS[@]}" \
  "${SOURCES[@]}" \
  "$HERE/src/com/dsh/tvhud/ReportCoreTest.java" 2>"$TMP/javac.err"
if [[ $? -ne 0 ]]; then
  echo "FAIL 单元测试编译失败：" >&2
  sed 's/^/      /' "$TMP/javac.err" >&2
  exit 1
fi

# 固定时区，和电视一致（报告按本地时区做日界运算）
"$JAVA" -Duser.timezone=Asia/Shanghai -cp "$OUT" com.dsh.tvhud.ReportCoreTest
