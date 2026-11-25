# G1-A 交付报告（待办模块打磨）

- 报告日期：2026-11-25
- 覆盖阶段：G0.0—G1-A3（含 G1-A3-R2 复验）
- 事实来源：仓库提交与工作区、`docs/验证记录/门禁/` 原始日志与 meta、`docs/需求基线.md` v2.14、`docs/需求变更记录.md`、`docs/任务进度.md`、设计 `.plans/设计/01_视觉与动效标准.md`
- 口径：下文“PASS”只对所列命令、设备条件与证据文件成立，不代替课程验收、真机结论或用户确认；“未执行/未自动化/待真机”按原样标注。

## 1. 目标与范围

- 目标：打磨待办模块及设计层，完成 G0.0—G1-A3 各闸门，交付设计 token、图标、基础组件、待办页头部与 GTD 左宽自适应等实现及模拟器验证。
- 阶段：G-start（试跑与模拟器基线）→ G0.0（环境）→ G0.1（需求基线 v2.14）→ G0.2（视觉与动效标准）→ G1-A1（token/图标/组件/静态检查）→ G1-A2（待办页接线）→ G1-A3（全量回归与 UI 收尾）→ G1-A3-R2（两项设计偏差修正复验）。
- 不在范围：G1-B（搜索命中、视图功能）、G2；不打 tag；未做真机、性能与 200% 字体验证。
- 边界：本报告只汇总既有证据；未重新执行构建、测试、lint、截图或真机实验。

## 2. 闸门总览

| 闸门 | 命令/方式 | 结果 | 证据路径 |
| --- | --- | --- | --- |
| G-start | 试跑 TodoScreen `settings_entry` testTag + `AcSettingsEntryP1Test` + 模拟器搭建 | 基线提交 `be9ead6`；原始截图未提交；比较时两端 `ls-remote` 均为 `be9ead6` | `docs/验证记录/门禁/emulator_setup_20261125_131224.*`、`emulator_accept_20261125T132349*` |
| G0.0 | emulator-5554 上跑 `TodoScreenSmokeTest`（`--rerun-tasks`） | 退出码 0；tests=1/failures=0；10s；模拟器非真机 | `docs/验证记录/门禁/g0_0_env_20261125T133901.log`、`.meta.txt` |
| G0.1 | 需求基线 v2.14 落档 + 独立验证两轮 | 首轮仅变更记录头信息 FAIL，修正后 PASS；FR-G-15、AC-G-04、4.23/C-33、6.4 计数 120/20 不变 | `docs/需求基线.md`、`docs/需求变更记录.md`、提交 `f6168a0`；验证结论见 `docs/任务进度.md` 第 1380 行（未单列独立报告文件） |
| G0.2 | 设计校订稿（九项+搜索/视图禁用态、显式动效 spec、`W_L=max+2×12dp`）+ 独立验证两轮 | 修正后 PASS；`.plans/` 不提交 | `.plans/设计/01_视觉与动效标准.md`；结论见 `docs/任务进度.md` 第 1381 行 |
| G1-A1 | `bash scripts/verify.sh G1-A1`、`G1-A1-R2`、`bash scripts/check_ui_tokens.sh` | 四步 EXIT 0；JVM 160/0；lint 0；静态 0 命中；加载态按架构裁定改静态形态 | `docs/验证记录/门禁/g1_a1_20261125T143503.*`、`g1_a1_r2_20261125T144056.*`、`verify_G1-A1*` |
| G1-A2 | `bash scripts/verify.sh G1-A2` + 模拟器筛选 4 类 | 首轮 `assembleDebugAndroidTest` FAIL（测试 import 错误），修正后 PASS；四步 0；JVM 160/0；38 例 0 失败（26s） | `docs/验证记录/门禁/g1_a2_20261125T151017.*`、`verify_G1-A2_20261125T151158.*`；失败日志 `verify_G1-A2_20261125T151055.log` |
| G1-A3 | `verify.sh G1-A3` + 全量 `connectedDebugAndroidTest` + 静态检查 + 4 张截图 | 四步 0；JVM 160/0；97/0/0/0（47s）；静态 0 命中；截图生成后发现两处非阻断偏差 | `docs/验证记录/门禁/g1_a3_20261125T152011.*`、`verify_G1-A3_20261125T151619.*` |
| G1-A3-R2 | 修正 GtdSelector/TodoScreen 两处偏差后全量复跑 | 静态 0 命中；四步 0；JVM 160/0；97/0/0/0（46s）；两项偏差以 dump/像素/点击核对消除 | `docs/验证记录/门禁/g1_a3r2_20261125T153205.*`、`verify_G1-A3-R2_20261125T152348.*` |

## 3. 提交记录

| commit | 信息 | 内容 |
| --- | --- | --- |
| `be9ead6` | chore: baseline before todo polish (pilot + emulator setup evidence) | 试跑改动（TodoScreen `settings_entry` testTag、`AcSettingsEntryP1Test`）与模拟器/试跑证据日志；原始截图未提交 |
| `f6168a0` | docs(req): baseline v2.14 GTD left-width adaptive [G0.1] | 需求基线 v2.14、变更记录 4.23/C-33、G0.0 证据 |
| `ac5bb60` | feat(ui): design tokens and icon set [G1-A1-T1/T2] | Theme（浅/深）、NautileMotion、NautileSpacing、16 个 `ic_*.xml` |
| `78d45be` | feat(ui): base components with token static check [G1-A1-T3/T4] | 5 个 designsystem 组件、`scripts/check_ui_tokens.sh`（8 规则）、A1 证据 |
| `ae0a693` | docs: record G1-A1 gate runs evidence | G1-A1/A1-R2 门禁日志与 meta |
| `86fd4ac` | feat(ui): todo header icons, adaptive GTD split and motion [G1-A2] | 待办头部四图标、GTD 左宽自适应与开合、三面板迁移、页面切换动效 |
| `6ce0b11` | test(ui): AC-G-04/AC-N-11 header and GTD split tests [G1-A2] | 新增 `AcGtdHeaderT090Test`；两处设置文本断言改 tag/contentDescription；脚本范围扩展；A2 证据 |
| `2a47b22` | docs: complete G1-A2 gate evidence | G1-A2 通过轮门禁日志与 meta |
| 最终交付提交 | 包含本报告的提交 | 按授权计划含本报告、A3-R2 代码修正（GtdSelector/TodoScreen）、两处测试定位更新、A3/A3-R2 证据与任务进度登记；hash 以 gitee/github `refs/heads/main` 顶端为准 |

- 说明：截至 `2a47b22`，每轮提交后两端 `ls-remote refs/heads/main` 与本地 HEAD 一致；写入本报告前实查 gitee、github 两端均为 `2a47b22`。最终提交尚未产生，本报告不预写其 hash。
- 当前工作区状态：HEAD=`2a47b22`；已修改 4 个文件（2 业务 + 2 测试），A3/A3-R2 证据与截图未跟踪；尚未提交。

## 4. 改动清单

- 生产代码（新增/修改）：`ui/theme/Theme.kt`（改）、`NautileMotion.kt`（新）、`NautileSpacing.kt`（新）；`ui/designsystem/` 5 个文件（新：BottomSheet/Buttons/ListRow/StateViews/TopBar）；`res/drawable/ic_*.xml` 16 个（新）；`ui/screen/todo/{TodoScreen,GtdSelector,NewTaskSheet,TaskDetailSheet,ProjectDetailSheet,TodoUiState}.kt`（改）；`ui/AppRoot.kt`（改）；`MainActivity.kt`（改）。
- 测试：新增 `AcGtdHeaderT090Test.kt`；修改 `AcSettingsEntryP1Test.kt`、`AcUiT071Test.kt`（G0.2 授权：设置文本→图标/tag）；修改 `AcTodoT07Test.kt` 与 `AcGtdHeaderT090Test.kt`（A3-R2 授权：关闭定位改 contentDescription）。
- 脚本：`scripts/check_ui_tokens.sh`（新，8 条规则；规则 7 只查构建/依赖文件；扫描 theme、designsystem、AppRoot.kt、todo）。
- 文档：`docs/需求基线.md` v2.14、`docs/需求变更记录.md`（4.23/C-33）、`docs/任务进度.md`（G0—G1-A2 已登记；G1-A3 条目尚未写入）、本报告。
- 未提交：`docs/验证记录/门禁/` 下 A3/A3-R2 截图与日志（截图按规则不提交）、`.plans/`、当前 4 个已修改源文件（待最终提交）。

## 5. 待办页“规格→实现→测试证据”对照

| 规格/AC | 实现 | 测试/证据 |
| --- | --- | --- |
| FR-N-14/BR-76 头部：GTD、搜索、视图、设置 | `TodoScreen` 头部四图标，搜索/视图禁用可见 | `AcGtdHeaderT090Test`、`AcUiT071Test`；截图 `g1_a3r2_todo_20261125T152628.png` |
| FR-G-15/AC-G-04 左宽自适应、五项、仅点按切换 | `GtdSelector` 用 `TextMeasurer` 计算 `W_L=max+24dp`，右区占余宽 | `AcGtdHeaderT090Test`、`AcTodoT07Test` 23 例；97 例全量回归 |
| AC-G-05/FR-G-18 边界拉动与形态保持 | 沿用既有手势实现，未改阈值 | `AcTodoT07Test`、既有 `AcGestureT07Test` |
| 设计 token 无硬编码 | Theme/NautileMotion/NautileSpacing/组件 | `check_ui_tokens.sh`：18 文件、8 规则、命中 0 |
| 16 个图标与 contentDescription | `res/drawable` 16 个矢量图 + 使用处 | 静态规则 4/7 命中 0；`AcGtdHeaderT090Test`；截图 |
| 48dp 触控 | `NautileIconButton`、列表行高 | 测试触控断言；静态核对 |
| 深色模式 | Theme 浅/深角色 | 深色截图 `g1_a3r2_dark_todo_20261125T152713.png`；对比度数值未测 |
| 三处 Expressive 动效 | `GtdSelector`、面板、`AppRoot` | 代码核对与静态规则 3 命中 0；中间帧/手感未自动化 |
| 待办空态组件 | `TodoScreen` 接 `NautileEmptyState`（A3-R2） | 文案断言保留；dump 行高 63px、图标区像素核对；截图 |
| GTD 右列空态 | 仍为 `Text(bodyMedium)`，未接组件（CMP-14） | dump 文本存在；非阻断偏差，未修正 |

## 6. 刚性验证结果

- 执行 HEAD：`2a47b22e621f5080f5d75b1ef71f0d8f29c69568`；工作区 dirty：A3 首轮 7 条（仅未跟踪证据），A3-R2 验证时 13 条（2 业务 + 2 测试 + 未跟踪证据），写 meta 后 19/20 条。
- 静态检查 `bash scripts/check_ui_tokens.sh`：exit 0；文件 18 个；规则 8 条；命中 0；A3 首轮 0.14s，A3-R2 约 0.1s；扫描范围 `ui/theme`、`ui/designsystem`、`ui/AppRoot.kt`、`ui/screen/todo/`。
- `bash scripts/verify.sh G1-A3`：exit 0；四步均 `--rerun-tasks`，assembleDebug=0、assembleDebugAndroidTest=0、testDebugUnitTest=0、lintDebug=0；JVM suites=28/tests=160/failures=0/errors=0/skipped=0；schema version 2（tasks/projects/detail，`1.json` 保留）EXIT=0；GATE PASS，23.6s。
- `bash scripts/verify.sh G1-A3-R2`：同上四步与 JVM 160/0；schema EXIT=0；GATE PASS，约 24s；脚本内 connected 默认不跑（需 `VERIFY_CONNECTED=1` 与人工在场）。
- 全量设备回归（单独执行）：`timeout -s INT 1200 ./gradlew :app:connectedDebugAndroidTest --rerun-tasks`；A3 exit 0、97/0/0/0、47s；A3-R2 exit 0、97/0/0/0、46s；XML `app/build/outputs/androidTest-results/connected/debug/TEST-nautile_api36(AVD) - 16.xml`。
- 分类统计：TodoScreenSmokeTest=1、RoomProjectRepositoryContractTest=5、RoomTaskRepositoryContractTest=5、MigrationTest=3、ProjectDaoTest=3、TaskDaoTest=3、RoomProjectRepositoryTest=2、RoomTaskRepositoryTest=3、AppRootTest=5、AcAppRootT06Test=5、AcDetailNewTaskUiT08T09Test=8、AcDetailPersistenceT085Test=1、AcDetailPullT088Test=7、AcGtdHeaderT090Test=4、AcSettingsEntryP1Test=2、AcTodoT07Test=23、AcUiT071Test=9、AcUiT077T081Test=8，合计 97。
- 设备条件：emulator-5554 / nautile_api36 / Android 16（API36）/ x86_64 / KVM / `ro.kernel.qemu=1`；font_scale=1.0；window/transition/animator scale=0；1080×2400 / 420dpi；深色截图临时切换 Night mode 后恢复。

## 7. UI 验收清单（设计 §12 逐项）

| 设计项 | 结论 | 说明 |
| --- | --- | --- |
| 1 颜色 COL-01—12 | PASS（限浅/深切换） | Theme 角色化、无颜色字面量；深色截图已验证切换生效（我记录亮度 251→21，日志未复核该数值） |
| 2 字体 TYP-01—07 | 部分 PASS | 字体层级 token 已建；200% 字体未执行；中文行高待确认 |
| 3 形状 SHP-01—06 | PASS | 圆角使用 shape token；像素级圆角测量未做 |
| 4 间距 SPC-01—06 | PASS | 4dp 间距 token；`NautileSpacing` 统一入口 |
| 5 动效 MOT-01—14 | 终态 PASS，过程未自动化 | 仅三处 Expressive；中间帧、可打断手感、观测观感未自动化；MOT-07.1 未实现 |
| 6 图标 ICO-01—10 | PASS（非阻断偏差） | 16 图标齐全、默认无文本图标；为手绘矢量近似，未做官方字形逐像素比对；check/cancel/chevron/expand_more 部分动作图标未接线 |
| 7 组件 CMP-01—26 | PASS（非阻断偏差） | 5 个基础组件；待办空态已接 `NautileEmptyState`；GTD 右列空态仍为 Text（CMP-14）；错误/加载组件存在、加载为静态形态、无页面接线 |
| 8 无障碍 A11Y-01—22 | 部分 PASS | 48dp 有测试/静态依据；TalkBack、深色对比度数值、200% 字体未自动化/未执行 |
| 9 禁用项 NO-01—14 | PASS（限扫描范围） | 8 条静态规则命中 0；未覆盖 theme/designsystem/AppRoot/todo 之外的页面 |
| 附加 搜索/视图 RDY-01—07 | PASS（禁用态） | 搜索、视图图标存在且 disabled 语义正确；搜索命中（AC-T-19）属 G1-B |

## 8. 未自动化与待真机事项

- 真机（vivo X200s 等）：未执行；模拟器结论不外推；性能结论不得由模拟器得出。
- 200% 字体下 GTD 左宽与布局（设计 §11.2-4、A11Y-13/15/21）：未执行。
- 动画中间帧、可打断续播、边界拉动手感、面板下拉与预测返回：未自动化，需人工评审。
- 深色对比度数值：未测（仅证明切换生效与角色色值）。
- TalkBack 语义朗读、像素级面板/圆角测量：未执行。
- 本轮未做新增测试先红后绿与变异抽查（A1 无新测试；A2/A3 测试为增量，无红灯记录）。

## 9. AC 缺口与范围外

- AC-N-10（设置页分类/标签管理、改名、四页一致）：未实现，本 goal 范围外。
- AC-N-11 仅完成待办页头部；日程/日志/AI/设置页头部未迁移，只继承 token。
- AC-T-19 搜索命中未实现（G1-B）。
- AC-N-23 AI 页仅继承底导图标；其余属 G2。
- AC-G-04 已在模拟器验证左宽、五项、点按切换与右宽；全屏边界拉动（T54-02）仍非本轮。
- 真机、性能、200% 字体未执行，不计入通过项。

## 10. 偏差与风险

1. MOT-07.1“当前清单右移 W_L”未实现：半屏面板结构下主列表被遮罩；已登记。
2. 关闭后底部导航在退出动画期间仍可见，未做底导延迟恢复。
3. 可打断从当前值续播，未保留 spring 速度。
4. 新建面板未新增下拉关闭，仅保留原有关闭路径。
5. 品牌种子色、动态取色、中文行高、200% 宽度、RTL 仍【待确认】（设计 §11.2）。
6. 图标为按 Material Symbols Outlined 风格手绘的矢量近似，未做官方字形逐像素比对。
7. 静态检查范围仅 theme/designsystem/AppRoot/todo，未覆盖其他页面。
8. 本轮无变异抽查、无新增测试先红后绿记录。
9. 模拟器结论不等同真机；动画、触感、性能需真机复核。

## 11. 待归档清单

- 截图（未提交，均在 `docs/验证记录/门禁/`）：`g1_a3_todo_20261125T151828.png`、`g1_a3_gtd_selector_20261125T151834.png`、`g1_a3_new_task_20261125T151842.png`、`g1_a3_dark_todo_20261125T151852.png`、`g1_a3r2_todo_20261125T152628.png`、`g1_a3r2_gtd_selector_20261125T152650.png`、`g1_a3r2_dark_todo_20261125T152713.png`、`emulator_boot_20261125T132329.png`。
- 已提交日志（`be9ead6`—`2a47b22`）：`g0_0_env_20261125T133901.*`、`g1_a1_20261125T143503.*`、`g1_a1_r2_20261125T144056.*`、`verify_G1-A1*`、`g1_a2_20261125T151017.*`、`verify_G1-A2*`。
- 待随最终提交归档（当前未跟踪）：`g1_a3_20261125T152011.*`、`verify_G1-A3_20261125T151619.*`、`g1_a3r2_20261125T153205.*`、`verify_G1-A3-R2_20261125T152348.*`。
- 截图不提交；原始截图由我保存，证据整理角色归档，不移动、不改名。

## 12. 回滚说明

- 按提交顺序从最终提交向前逐个 `git revert <hash>`：最终交付提交 → `2a47b22` → `6ce0b11` → `86fd4ac` → `ae0a693` → `78d45be` → `ac5bb60` → `f6168a0`；是否回退到 `be9ead6` 由我决定。
- 禁止 force push，不改写已推送历史；回滚后需重新执行 `check_ui_tokens.sh` 与 `verify.sh`。
- 未提交截图可直接删除；`.plans/` 不纳入版本控制，无需回滚。

## 13. 结论

- 在 G1-A 范围内（待办模块 + 设计层 + 模拟器验证），G0.0—G1-A3 各闸门按本报告证据 PASS；A3-R2 两项指定偏差已消除。
- 未进入 G1-B/G2，未打 tag；真机、性能、200% 字体、搜索命中与跨页头部等缺口见第 8、9 节；偏差与风险见第 10 节。
- 本报告不宣称课程验收或最终交付完成；是否验收由我依据原始证据决定。
- 待核对：① 最终提交尚未产生，其 hash 与远端 main 顶端需提交后回填；② `docs/任务进度.md` 尚未登记 G1-A3/A3-R2，若最终提交未包含该登记需补正；③ 深色亮度 251→21 为我的记录，未在门禁日志中复核。
