#!/usr/bin/env bash
#
# 刚性验证门禁（T-051）
#
# 用法：
#   bash scripts/verify.sh <任务号>
#   VERIFY_CONNECTED=1 bash scripts/verify.sh <任务号>   # 额外跑真机 instrumentation
#
# 步骤：assembleDebug → assembleDebugAndroidTest（只编译）→ testDebugUnitTest → lintDebug
# 任一非 0 即 FAIL；全部使用 --rerun-tasks，避免 up-to-date 冒充执行。
# 记录 git HEAD 与 dirty；输出 PASS/FAIL，并把日志与元数据归档到 docs/验证记录/门禁/。
# 不安装依赖、不提交、不修改业务代码；真机测试默认不跑并明确标“未执行”。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TASK_ID="${1:-manual}"
STAMP="$(date +%Y%m%dT%H%M%S)"
ARCHIVE_DIR="$ROOT/docs/验证记录/门禁"
mkdir -p "$ARCHIVE_DIR"

LOG="$ARCHIVE_DIR/verify_${TASK_ID}_${STAMP}.log"
META="$ARCHIVE_DIR/verify_${TASK_ID}_${STAMP}.meta.txt"

HEAD="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DIRTY_COUNT="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
DIRTY_COUNT="${DIRTY_COUNT:-0}"

FAIL=0

run_step() {
    local name="$1"
    shift
    echo
    echo "===== [STEP] $name ====="
    "$@"
    local rc=$?
    echo "[STEP] $name EXIT=$rc"
    if [ "$rc" -ne 0 ]; then
        FAIL=1
    fi
}

main() {
    echo "============================================================"
    echo "T-051 刚性验证门禁"
    echo "任务号：$TASK_ID"
    echo "时间：$(date -Iseconds)"
    echo "HEAD：$HEAD"
    echo "工作区未提交条目数：$DIRTY_COUNT"
    echo "日志：$LOG"
    echo "============================================================"

    run_step "assembleDebug" ./gradlew :app:assembleDebug --rerun-tasks
    run_step "assembleDebugAndroidTest(compile-only)" ./gradlew :app:assembleDebugAndroidTest --rerun-tasks
    run_step "testDebugUnitTest" ./gradlew :app:testDebugUnitTest --rerun-tasks
    run_step "lintDebug" ./gradlew :app:lintDebug --rerun-tasks

    echo
    echo "===== [STEP] 测试结果统计 ====="
    if command -v python3 >/dev/null 2>&1; then
        python3 - <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

paths = glob.glob("app/build/test-results/testDebugUnitTest/*.xml")
tests = failures = errors = skipped = 0
for path in paths:
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
print(f"suites={len(paths)} tests={tests} failures={failures} errors={errors} skipped={skipped}")
if not paths or tests == 0:
    sys.exit(3)
if failures or errors:
    sys.exit(4)
PY
        SUMMARY_RC=$?
        echo "[STEP] 测试结果统计 EXIT=$SUMMARY_RC"
        if [ "$SUMMARY_RC" -ne 0 ]; then
            FAIL=1
        fi
    else
        echo "python3 不可用，无法解析测试 XML；按 FAIL 处理。"
        FAIL=1
    fi

    echo
    echo "===== [STEP] schema 产物 ====="
    SCHEMA_DIR="app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase"
    SCHEMA_V1="$SCHEMA_DIR/1.json"
    SCHEMA_V2="$SCHEMA_DIR/2.json"
    SCHEMA_V3="$SCHEMA_DIR/3.json"
    if [ -f "$SCHEMA_V1" ] && [ -f "$SCHEMA_V2" ] && [ -f "$SCHEMA_V3" ]; then
        echo "schema 存在：$SCHEMA_V1（历史）、$SCHEMA_V2（历史）与 $SCHEMA_V3（当前）"
        if grep -q '"version": 3' "$SCHEMA_V3" \
            && grep -q '"tableName": "tasks"' "$SCHEMA_V3" \
            && grep -q '"tableName": "projects"' "$SCHEMA_V3" \
            && grep -q '"columnName": "detail"' "$SCHEMA_V3" \
            && grep -q '"columnName": "manual_order"' "$SCHEMA_V3" \
            && grep -q 'manual_order` INTEGER' "$SCHEMA_V3"; then
            echo "[STEP] schema 内容检查 EXIT=0（version 3，tasks/projects/detail/manual_order；1/2.json 保留）"
        else
            echo "[STEP] schema 内容检查 EXIT=1（缺 version 3、tasks/projects/detail/manual_order）"
            FAIL=1
        fi
    else
        echo "[STEP] schema 产物检查 EXIT=1（1.json、2.json 或 3.json 不存在）"
        FAIL=1
    fi

    echo
    if [ "${VERIFY_CONNECTED:-0}" = "1" ]; then
        run_step "connectedDebugAndroidTest" ./gradlew :app:connectedDebugAndroidTest --rerun-tasks
    else
        echo "===== [STEP] connectedDebugAndroidTest ====="
        echo "未执行：需要真机/模拟器与人工在场；设置 VERIFY_CONNECTED=1 才会运行。"
    fi

    RESULT="FAIL"
    if [ "$FAIL" -eq 0 ]; then
        RESULT="PASS"
    fi

    {
        echo "task=$TASK_ID"
        echo "time=$(date -Iseconds)"
        echo "head=$HEAD"
        echo "dirty_entries=$DIRTY_COUNT"
        echo "connected_requested=${VERIFY_CONNECTED:-0}"
        echo "androidtest_compile=executed"
        echo "result=$RESULT"
        echo "log=$LOG"
    } > "$META"

    echo
    echo "============================================================"
    echo "GATE RESULT: $RESULT"
    echo "日志：$LOG"
    echo "元数据：$META"
    echo "============================================================"

    if [ "$FAIL" -eq 0 ]; then
        return 0
    else
        return 1
    fi
}

main 2>&1 | tee -a "$LOG"
exit "${PIPESTATUS[0]}"
