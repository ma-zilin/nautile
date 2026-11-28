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
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$SCHEMA_DIR" <<'PY'
import json
import os
import sys

base = sys.argv[1]
required = ["1.json", "2.json", "3.json", "4.json"]
missing = [n for n in required if not os.path.isfile(os.path.join(base, n))]
if missing:
    print("schema 产物缺失：" + ", ".join(missing))
    sys.exit(1)


def load(name):
    with open(os.path.join(base, name), encoding="utf-8") as handle:
        return json.load(handle)["database"]


def tables(db):
    return {entity["tableName"]: entity for entity in db["entities"]}


def fields(entity):
    return {field["columnName"]: field for field in entity["fields"]}


db1, db2, db3, db4 = (load(name) for name in required)
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


# 历史 schema 1/2/3 保留既有检查，防止被覆盖或回退。
check(db1["version"] == 1, "1.json 版本不是 1")
check({"tasks", "projects"} <= set(tables(db1)), "1.json 缺少 tasks/projects")
check(db2["version"] == 2, "2.json 版本不是 2")
check("detail" in fields(tables(db2)["tasks"]), "2.json 缺少 detail")
check("manual_order" not in fields(tables(db2)["tasks"]), "2.json 不应含 manual_order")
check(db3["version"] == 3, "3.json 版本不是 3")
check("manual_order" in fields(tables(db3)["tasks"]), "3.json 缺少 manual_order")

# 当前 schema v4：两张旧表 + 两张 AI 表。
check(db4["version"] == 4, "4.json 版本不是 4")
t4 = tables(db4)
check(
    set(t4) == {"tasks", "projects", "ai_sessions", "ai_messages"},
    "4.json 表集合不符：" + ",".join(sorted(t4)),
)

if "ai_sessions" in t4:
    session_fields = fields(t4["ai_sessions"])
    for column in ("id", "created_at", "updated_at"):
        check(
            column in session_fields and session_fields[column].get("notNull") is True,
            f"ai_sessions.{column} 应存在且 NOT NULL",
        )

if "ai_messages" in t4:
    message_entity = t4["ai_messages"]
    message_fields = fields(message_entity)
    for column in ("id", "session_id", "role", "content", "status", "created_at"):
        check(
            column in message_fields and message_fields[column].get("notNull") is True,
            f"ai_messages.{column} 应存在且 NOT NULL",
        )
    check("error_code" in message_fields, "ai_messages 缺少 error_code")
    if "error_code" in message_fields:
        check(message_fields["error_code"].get("affinity") == "TEXT", "error_code 应为 TEXT")
        check(message_fields["error_code"].get("notNull") is not True, "error_code 应可空")
    create_sql = message_entity["createSql"]
    check(
        "`error_code` TEXT" in create_sql and "`error_code` TEXT NOT NULL" not in create_sql,
        "ai_messages 建表 SQL 的 error_code 应为可空 TEXT",
    )
    check("DEFAULT" not in create_sql, "ai_messages 建表 SQL 不应含 DEFAULT")
    indices = {index["name"]: index for index in message_entity.get("indices", [])}
    check("index_ai_messages_session_id" in indices, "缺少 index_ai_messages_session_id")
    if "index_ai_messages_session_id" in indices:
        check(
            indices["index_ai_messages_session_id"]["columnNames"] == ["session_id"],
            "index_ai_messages_session_id 应建在 session_id",
        )
    foreign_key = next(
        (fk for fk in message_entity.get("foreignKeys", []) if fk.get("table") == "ai_sessions"),
        None,
    )
    check(foreign_key is not None, "ai_messages 缺少指向 ai_sessions 的外键")
    if foreign_key is not None:
        check(foreign_key.get("onDelete") == "RESTRICT", "ai_messages 外键应为 ON DELETE RESTRICT")
        check(foreign_key.get("onUpdate") == "NO ACTION", "ai_messages 外键应为 ON UPDATE NO ACTION")
        check(
            foreign_key.get("columns") == ["session_id"]
            and foreign_key.get("referencedColumns") == ["id"],
            "ai_messages 外键列应为 session_id → ai_sessions.id",
        )

if failures:
    for message in failures:
        print("SCHEMA FAIL: " + message)
    sys.exit(1)

print(
    "schema OK：1/2/3 历史保留；4.json version 4，"
    "tasks/projects/ai_sessions/ai_messages、AI 关键列/索引/外键（RESTRICT）通过",
)
PY
        SCHEMA_RC=$?
        echo "[STEP] schema 内容检查 EXIT=$SCHEMA_RC"
        if [ "$SCHEMA_RC" -ne 0 ]; then
            FAIL=1
        fi
    else
        echo "python3 不可用，无法校验 schema JSON；按 FAIL 处理。"
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
