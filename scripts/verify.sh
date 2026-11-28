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
    SCHEMA_V4="$SCHEMA_DIR/4.json"
    SCHEMA_V5="$SCHEMA_DIR/5.json"
    if [ -f "$SCHEMA_V1" ] && [ -f "$SCHEMA_V2" ] && [ -f "$SCHEMA_V3" ] && [ -f "$SCHEMA_V4" ] && [ -f "$SCHEMA_V5" ]; then
        echo "schema 存在：$SCHEMA_V1/$SCHEMA_V2/$SCHEMA_V3/$SCHEMA_V4（历史）与 $SCHEMA_V5（当前）"
        if grep -q '"version": 3' "$SCHEMA_V3" \
            && grep -q '"tableName": "tasks"' "$SCHEMA_V3" \
            && grep -q '"tableName": "projects"' "$SCHEMA_V3" \
            && grep -q '"columnName": "detail"' "$SCHEMA_V3" \
            && grep -q '"columnName": "manual_order"' "$SCHEMA_V3" \
            && grep -q 'manual_order` INTEGER' "$SCHEMA_V3"; then
            echo "[STEP] schema v3 内容检查 EXIT=0（version 3，tasks/projects/detail/manual_order；1/2.json 保留）"
        else
            echo "[STEP] schema v3 内容检查 EXIT=1（缺 version 3、tasks/projects/detail/manual_order）"
            FAIL=1
        fi

        # R6-G2：v4 必须含 8 张表与关键列；time_logs.end_at 可空、无默认；保留历史 schema。
        if python3 - "$SCHEMA_V4" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    data = json.load(handle)
database = data["database"]
tables = {table["tableName"]: table for table in database["entities"]}
expected = [
    "tasks", "projects", "schedule_series", "schedule_events",
    "schedule_exceptions", "schedule_tags", "time_categories", "time_logs",
]
if database["version"] != 4:
    print(f"version={database['version']}，期望 4")
    sys.exit(1)
missing = [name for name in expected if name not in tables]
if missing:
    print(f"缺少表：{missing}")
    sys.exit(1)


def column(table, name):
    for field in tables[table]["fields"]:
        if field["columnName"] == name:
            return field
    print(f"缺少列：{table}.{name}")
    sys.exit(1)


end_at = column("time_logs", "end_at")
if end_at.get("notNull", False):
    print("time_logs.end_at 不应 NOT NULL")
    sys.exit(1)
if end_at.get("defaultValue") is not None:
    print("time_logs.end_at 不应有默认值")
    sys.exit(1)
for table, name in [
    ("schedule_events", "tag_id"),
    ("schedule_events", "series_id"),
    ("time_categories", "parent_id"),
]:
    if column(table, name).get("notNull", False):
        print(f"{table}.{name} 应可空")
        sys.exit(1)
PY
        then
            echo "[STEP] schema v4 内容检查 EXIT=0（version 4，8 表，end_at/tag_id/parent_id 可空，end_at 无默认）"
        else
            echo "[STEP] schema v4 内容检查 EXIT=1（见上方原因）"
            FAIL=1
        fi

        # R7-G1：v5 必须含 8 张表；schedule_events.color_index 与 time_logs.color_index 可空无默认 INTEGER；
        # schedule_tags 与 time_categories 不含 color_index；既有可空列约束保持不变。
        if python3 - "$SCHEMA_V5" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    data = json.load(handle)
database = data["database"]
tables = {table["tableName"]: table for table in database["entities"]}
expected = [
    "tasks", "projects", "schedule_series", "schedule_events",
    "schedule_exceptions", "schedule_tags", "time_categories", "time_logs",
]
if database["version"] != 5:
    print(f"version={database['version']}，期望 5")
    sys.exit(1)
missing = [name for name in expected if name not in tables]
if missing:
    print(f"缺少表：{missing}")
    sys.exit(1)
if len(tables) != len(expected):
    print(f"表数量={len(tables)}，期望 {len(expected)}")
    sys.exit(1)


def find_column(table, name):
    for field in tables[table]["fields"]:
        if field["columnName"] == name:
            return field
    return None


def column(table, name):
    found = find_column(table, name)
    if found is None:
        print(f"缺少列：{table}.{name}")
        sys.exit(1)
    return found


for table in ("schedule_events", "time_logs"):
    color = column(table, "color_index")
    if color.get("affinity") != "INTEGER":
        print(f"{table}.color_index affinity={color.get('affinity')}，期望 INTEGER")
        sys.exit(1)
    if color.get("notNull", False):
        print(f"{table}.color_index 不应 NOT NULL")
        sys.exit(1)
    if color.get("defaultValue") is not None:
        print(f"{table}.color_index 不应有默认值")
        sys.exit(1)

for table in ("schedule_tags", "time_categories"):
    if find_column(table, "color_index") is not None:
        print(f"{table} 不应包含 color_index 列")
        sys.exit(1)

end_at = column("time_logs", "end_at")
if end_at.get("notNull", False):
    print("time_logs.end_at 不应 NOT NULL")
    sys.exit(1)
if end_at.get("defaultValue") is not None:
    print("time_logs.end_at 不应有默认值")
    sys.exit(1)
for table, name in [
    ("schedule_events", "tag_id"),
    ("schedule_events", "series_id"),
    ("time_categories", "parent_id"),
]:
    if column(table, name).get("notNull", False):
        print(f"{table}.{name} 应可空")
        sys.exit(1)
PY
        then
            echo "[STEP] schema v5 内容检查 EXIT=0（version 5，8 表，schedule_events/time_logs.color_index 可空无默认 INTEGER，tag/category 无 color_index，既有可空列保持）"
        else
            echo "[STEP] schema v5 内容检查 EXIT=1（见上方原因）"
            FAIL=1
        fi
    else
        echo "[STEP] schema 产物检查 EXIT=1（1.json、2.json、3.json、4.json 或 5.json 不存在）"
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
