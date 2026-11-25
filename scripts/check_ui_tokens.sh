#!/usr/bin/env bash
#
# G1-A1 设计 token 静态检查（本次授权范围：ui/theme 与 ui/designsystem）
#
# 依据：.plans/设计/01_视觉与动效标准.md 的 COL-01/COL-11、SHP-01、MOT-03、ICO-02/ICO-08/ICO-09、NO-01/NO-02/NO-03/NO-05。
# 规则采用“命中即 FAIL”；只扫描本阶段范围，A2 接线页面后再扩展扫描路径。
#
# 不新增依赖、不改构建配置、不修改业务代码。
# 退出码：0 = 全部规则 0 命中；1 = 至少一条规则命中；2 = 环境或路径问题（测试自身错误）。
#
# 用法：bash scripts/check_ui_tokens.sh

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

THEME_DIR="app/src/main/java/io/github/mazilin/nautile/ui/theme"
DS_DIR="app/src/main/java/io/github/mazilin/nautile/ui/designsystem"

if ! command -v rg >/dev/null 2>&1; then
    echo "ERROR: 需要 rg（ripgrep），当前不可用。" >&2
    exit 2
fi
for d in "$THEME_DIR" "$DS_DIR"; do
    if [ ! -d "$d" ]; then
        echo "ERROR: 检查目录不存在：$d" >&2
        exit 2
    fi
done

FILE_COUNT="$(find "$THEME_DIR" "$DS_DIR" -type f -name '*.kt' | wc -l | tr -d ' ')"
TOTAL_HITS=0
FAIL=0

# 打印单条规则结果：$1=规则号 $2=说明 $3=匹配输出（可为空）
report_rule() {
    local id="$1" desc="$2" out="$3"
    local n=0
    if [ -n "$out" ]; then
        n="$(printf '%s\n' "$out" | wc -l | tr -d ' ')"
    fi
    echo "[规则 $id] $desc"
    if [ "$n" -eq 0 ]; then
        echo "  命中 0"
    else
        printf '%s\n' "$out"
        echo "  命中 $n"
        FAIL=1
    fi
    TOTAL_HITS=$((TOTAL_HITS + n))
}

echo "============================================================"
echo "G1-A1 设计 token 静态检查"
echo "时间：$(date -Iseconds)"
echo "HEAD：$(git rev-parse HEAD 2>/dev/null || echo unknown)"
echo "扫描目录：$THEME_DIR"
echo "          $DS_DIR"
echo "============================================================"
echo

# 规则 1：颜色字面量（COL-01 / NO-01）
out1="$(rg -n --no-heading --no-messages \
    -e 'Color\(0x' -e 'Color\.Black' -e 'Color\.White' -e 'Color\.Red' \
    "$THEME_DIR" "$DS_DIR" || true)"
report_rule "1" "颜色字面量：Color(0x / Color.Black / Color.White / Color.Red（COL-01、NO-01）" "$out1"

# 规则 2：圆角字面量（SHP-01 / NO-02），豁免 theme/Theme.kt
out2="$(rg -n --no-heading --no-messages \
    -e 'RoundedCornerShape\([0-9]' \
    "$THEME_DIR" "$DS_DIR" || true)"
out2="$(printf '%s\n' "$out2" | grep -v -e '/theme/Theme\.kt:' | sed '/^$/d' || true)"
report_rule "2" "圆角字面量：RoundedCornerShape(<数字>（SHP-01、NO-02；豁免 theme/Theme.kt）" "$out2"

# 规则 3：动效字面量（MOT-03 / NO-03），豁免 theme/NautileMotion.kt
out3="$(rg -n --no-heading --no-messages \
    -e '\btween\(' -e '\bspring\(' -e 'durationMillis' \
    "$THEME_DIR" "$DS_DIR" || true)"
out3="$(printf '%s\n' "$out3" | grep -v -e '/theme/NautileMotion\.kt:' | sed '/^$/d' || true)"
report_rule "3" "动效字面量：tween( / spring( / durationMillis（MOT-03、NO-03；豁免 theme/NautileMotion.kt）" "$out3"

# 规则 4：文本图标（ICO-02/ICO-08 / NO-05）
out4="$(rg -n --no-heading --no-messages -F \
    -e 'Text("☰")' -e 'Text("+")' -e 'Text("✓")' -e 'Text("×")' \
    "$THEME_DIR" "$DS_DIR" || true)"
report_rule "4" "文本图标：Text(\"☰/+/✓/×\")（ICO-02、ICO-08、NO-05）" "$out4"

# 规则 5：固定比例分栏（CMP-10 / NO-10）
out5="$(rg -n --no-heading --no-messages \
    -e 'weight\(0\.(38|62)f' \
    "$THEME_DIR" "$DS_DIR" || true)"
report_rule "5" "固定比例分栏：weight(0.38f/0.62f)（CMP-10、NO-10）" "$out5"

# 规则 6：遮罩 alpha 字面量（COL-10），豁免 theme/NautileSpacing.kt
out6="$(rg -n --no-heading --no-messages \
    -e '0\.32f' \
    "$THEME_DIR" "$DS_DIR" || true)"
out6="$(printf '%s\n' "$out6" | grep -v -e '/theme/NautileSpacing\.kt:' | sed '/^$/d' || true)"
report_rule "6" "遮罩 alpha 字面量：0.32f（COL-10；豁免 theme/NautileSpacing.kt）" "$out6"

# 规则 7：禁止 material-icons-extended 依赖（ICO-02/ICO-09、NO-13）
# 排除本检查脚本自身（其规则文本包含该关键字，不是依赖声明）。
out7="$(rg -n --no-heading --no-messages \
    -e 'material-icons-extended' \
    --glob '!build/**' --glob '!.gradle/**' --glob '!.plans/**' --glob '!materials/**' \
    --glob '!scripts/check_ui_tokens.sh' \
    . || true)"
report_rule "7" "依赖：material-icons-extended 全仓库出现（排除 build/.gradle/.plans/materials）" "$out7"

# 规则 8：无限动画 API（MOT-13 架构裁定：加载态与按钮 loading 不使用 indeterminate 进度环）
out8="$(rg -n --no-heading --no-messages \
    -e 'CircularProgressIndicator' -e 'infiniteRepeatable' -e 'rememberInfiniteTransition' \
    "$THEME_DIR" "$DS_DIR" || true)"
report_rule "8" "无限动画 API：CircularProgressIndicator / infiniteRepeatable / rememberInfiniteTransition（MOT-13）" "$out8"

echo
echo "------------------------------------------------------------"
echo "检查文件数：$FILE_COUNT（ui/theme + ui/designsystem 的 .kt）"
echo "命中总数：$TOTAL_HITS"
if [ "$FAIL" -eq 0 ]; then
    echo "RESULT: PASS"
    exit 0
else
    echo "RESULT: FAIL"
    exit 1
fi
