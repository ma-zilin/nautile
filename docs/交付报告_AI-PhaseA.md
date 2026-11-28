# Nautile AI Phase A 交付报告

> 范围：AI Phase A（G0—G4），工作副本 `.worktrees/ai-chat`，分支 `feature/ai-chat`。本报告只记录已由验证报告、门禁日志、提交历史或源码核实的事实；模拟器结果不等于真机，Fake 客户端结果不等于真实 DeepSeek，代码级 PASS 不等于用户验收或整产品完成。报告整理日期：2026-11-28。

| 项目 | 内容 |
| --- | --- |
| 交付范围 | AI 全屏对话页、DeepSeek 官方 SSE 流式客户端、Key 设置与安全存储、单段会话持久化与清除、错误映射、键盘/返回/IME、无障碍与动效对齐 |
| 工作副本 | `/home/wordwoods/Projects/Tride/.worktrees/ai-chat` |
| 分支 | `feature/ai-chat` |
| 基点 | `c4bafdbd4ba2c08d1208e579a0687581729abc7e`（main，R5-G6） |
| 编写时 HEAD | `41b788ea941afed79938a46cc3c672266e074062` |
| 设备 | `emulator-5554`（nautile_api36，Android 16 / API 36，**模拟器非真机**） |
| 真机 | `10AF561Y5R002KL` 未使用；G3 期间 `V2458A` 曾接入，相关结果已排除 |
| 最终口径 | **核心功能代码级完成，限定范围为模拟器自动化 PASS**；真实 DeepSeek/Key、真机、TalkBack 全链路、IME/动画观感与 R6 日程/日志分支未验收 |

---

## 1 范围与结论

### 1.1 Phase A 的目标

Phase A 为后续“AI 辅助计划与分析”提供可用的对话底座。按 `.plans/需求/AI/AI-PhaseA_FR_AC子集.md`，本轮只交付：

- AI 全屏一级页与单段本地会话；
- DeepSeek 官方接口的 SSE 流式对话、发送/停止、单请求互斥；
- Key 的设置、清除、掩码与本地配置状态；
- 会话和消息的本地持久化、清除隔离；
- 错误映射、空态、滚动、键盘/返回/IME 基础行为；
- 无障碍语义与 06 规格中的动效对齐。

不交付数据查询/写入工具、授权确认、个人记忆、思考强度/联网、长按快捷输入/共享草稿、通知/角标、日志分析、多会话/附件/导出/备份、自动重试与工具调用。

### 1.2 结论

**G5 状态（2026-11-28）**：报告完成；报告提交前 HEAD `41b788e`；本报告提交为最终交付提交，最终 HEAD 与两端 ref 以提交后 `ls-remote` 为准；未 rebase、未合并 main、未打 tag。

- **已完成（代码级、模拟器自动化范围）**：G2—G4 的实现、测试与门禁均通过。JVM 290/0、模拟器全量 377/0、lint 0 error、schema v4、MigrationTest 11/11；G4 无障碍 8/8，AI 截图 21 张；各轮变异抽查均红→绿。
- **部分完成（自动化通过、验收分支未执行）**：AC-N-19（仅 Activity 重建，非冷启动）、AC-A-09/AC-AN-03（仅 `tasks`/`projects`，日程/日志待 R6）、AC-A-12（请求体与本地存储已验，备份文件验收未做）、AC-A-18（草稿与不重发已验，IME 人工未做）、AC-AN-02（Activity 重建与 Room 文件重开，非杀进程冷启动）、AC-AN-05（静态与日志检查，设备端文件导出未做）。
- **未验收**：AC-N-21 的 Key 子页真机预测性返回；真实 DeepSeek 请求与真实 Key；TalkBack 全链路；动画观感；系统 200% 字体；IME 无缝与底导显隐；R6 日程/日志不变分支。
- **不得表述为“全部产品完成”**：本分支只有 AI 两表与既有 `tasks`/`projects`，不含 R6 日程/日志；未合并 main、未打 tag、未推送 tag，也未完成用户验收。

### 1.3 本报告不代表的事项

本报告不构成用户验收结论，不替代真机、真实服务、人工观感与后续合并后的复验。所有“PASS”均指对应验证报告列明的命令、断言与模拟器条件，不外推到未执行项。

---

## 2 分支、版本与提交

### 2.1 提交序列

以下来自 `git -C /home/wordwoods/Projects/Tride/.worktrees/ai-chat log --oneline c4bafdb..HEAD`：

| 阶段 | 提交 | 提交信息 | 说明 |
| --- | --- | --- | --- |
| 基点 | `c4bafdb` | main R5-G6 | 本分支基于此创建 |
| G0 | `2415cba` | `docs: AI Phase A G0 branch and base record [G0]` | 分支与基点记录 |
| G1 | `704d180` | `docs(ai): G1 requirement, architecture and design freeze evidence [G1]` | 需求/架构/设计冻结证据 |
| G2-T1 | `930734b` | `feat(ai): AI data layer, keystore and DeepSeek SSE client [G2-T1]` | 数据层、Keystore、SSE 客户端 |
| G2-T2 | `9997fff` | `feat(ai): DeepSeek key settings page and clear conversation [G2-T2]` | Key 页与清除会话 |
| G2 验证 | `c5366ea` | `test(ai): G2 key, client and data layer verification [G2]` | G2 验证测试与证据 |
| G3-T1 | `8706c43` | `feat(ai): chat coordinator, view model and safe clear [G3-T1]` | 协调器、ViewModel、安全清除 |
| G3-T2 | `96b7c66` | `feat(ai): AI screen with streaming chat [G3-T2]` | AI 页与流式对话 UI |
| G3 验证 | `6bf7f37` | `test(ai): G3 AI page and conversation core verification [G3]` | G3 测试与证据 |
| G4-T1 | `a711bf0` | `fix(ai): G4 motion and accessibility alignment [G4-T1]` | 动效与无障碍对齐 |
| G4 修正 | `efcb020` | `fix(ai): align DeepSeek key header with 06 spec [G4]` | Key 页头部按 06 §6.2 修正 |
| G4 验证 | `e8a8ca1` | `test(ai): G4 UI, accessibility and full regression verification [G4]` | G4 测试、无障碍、截图与回归 |
| G4 记录 | `41b788e` | `docs: record AI Phase A G4 results and environment [G4]` | 进度与环境记录 |

验证时 HEAD 与提交顺序需要区分：G2 门禁在 `9997fff` 执行，`c5366ea` 为其后的验证测试提交；G3 门禁在 `96b7c66` 执行，`6bf7f37` 为验证提交；G4 首轮在 `a711bf0` 执行，修正 `efcb020` 后由 G4-R2 复测，`e8a8ca1`/`41b788e` 为后续证据提交。

### 2.2 远端与工作区

编写本报告时实际执行：

```bash
git -C /home/wordwoods/Projects/Tride/.worktrees/ai-chat rev-parse HEAD
git -C /home/wordwoods/Projects/Tride/.worktrees/ai-chat ls-remote --heads gitee feature/ai-chat
git -C /home/wordwoods/Projects/Tride/.worktrees/ai-chat ls-remote --heads github feature/ai-chat
git -C /home/wordwoods/Projects/Tride/.worktrees/ai-chat status --porcelain
```

结果：

- 本地 HEAD：`41b788ea941afed79938a46cc3c672266e074062`。
- gitee `refs/heads/feature/ai-chat`：`41b788e`；github 同名 ref：`41b788e`。
- 工作区有 **22 个未跟踪文件**，全部是历史截图副本（`g10_*`、`g3r4_*`、`g6r4_*`、`g7r4_*`、`g9_*`），没有已跟踪文件改动。
- 本分支未 rebase、未合并 main、未打 tag、未 force push。报告文件写入后为新的未跟踪文件；报告本身未提交，最终 HEAD 与两端 ref 以报告提交后的 `ls-remote` 为准。

### 2.3 与 main 的当前关系（重要事实）

编写本报告时，main 已不是基点 `c4bafdb`：

- 两端 `refs/heads/main` 均为 `f24c706`（`docs: sync README after merging feature/schedule-log (R6) into main`）；
- main 含 `34f7307 Merge branch 'feature/schedule-log' into main (R6 Schedule & Time Log Phase A)`；
- `feature/ai-chat` 仍基于 `c4bafdb`，`git merge-base feature/ai-chat main` 为 `c4bafdb`，即 **R6 尚未合并进本分支**；
- `git rev-list --left-right --count main...HEAD` 实际输出 **`19	12`**：main 领先 19 个提交，本分支领先 12 个提交。

**用户 2026-11-28 决定暂不 rebase**：本分支保持基于 `c4bafdb`，本轮不 rebase 到 main `f24c706`，不做 DB v5 改造。因此当前状态是“AI Phase A 交付分支 + main 已含 R6”，不是可合并状态；合并前置见第 10 节。

因此，任务说明中的“R6 未合并”应准确理解为“R6 未合并进 `feature/ai-chat`”；R6 已完成合并进入 main。

---

## 3 完成范围与 AC/FR 对照

### 3.1 功能与验收映射

| # | 完成项 | 对应 FR/AC（派生清单） | 实现落点 | 实际证据 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | AI 全屏一级页：大标题“AI”、右上设置、无搜索/附件/模型名/上下文标签 | FR-A-16、FR-N-14、AC-N-23、AC-A-15（仅入口） | `ui/screen/ai/AiScreen.kt`、`AiViewModel.kt`、`AppRoot.kt` | `AiG3ScreenStateTest` 11/11、`AiG3EntryAndKeyLinkageTest` 2/2；G4 真实 App 浅/深截图 | PASS（模拟器） |
| 2 | DeepSeek 官方 SSE 流式增量 | FR-AN-01、AC-AN-01 | `platform/network/DeepSeekAiGateway.kt`、`SseStreamParser.kt`、`DeepSeekSseParser.kt` | `SseStreamParserTest` 9、`DeepSeekSseParserTest` 4、`DeepSeekAiGatewayTest` 2；`AiG3ChatFlowTest` 7/7 | PASS（Fake/纯函数层） |
| 3 | 发送/停止、单请求互斥、不排队、停止收尾前不新发 | FR-A-22、AC-A-19、AC-AN-01 | `domain/usecase/AiChatCoordinator.kt`（`sendGate`、`terminalWritten`、`NonCancellable` 终态） | `AiChatCoordinatorTest` 11、`AiViewModelTest` 8、`AiG3ChatFlowTest` 的停止场景 | PASS（模拟器/Fake） |
| 4 | 错误映射：未配置 Key、无效 Key、断网、超时、HTTP 错误、流中断可区分 | FR-AN-04、AC-A-09、AC-AN-06 | `DeepSeekErrorMapping.kt`、`AiError.kt`、`AiScreen.kt` 文案 | `DeepSeekErrorMappingTest` 11、`AiG3ScreenStateTest` 7 类失败态 | PASS（映射/UI 层） |
| 5 | Key 设置页：未配置/已保存、掩码输入、保存、清除、“已清除”瞬时反馈、无模型名 | FR-AN-03、AC-AN-04、AC-AN-05 | `DeepSeekKeyScreen.kt`、`AiKeyCommands.kt`、`AiSettingsViewModel.kt` | `AiKeyPageG2Test` 8/8（G4-R2 复跑 8/8）、`AiSettingsClearConversationG2Test` 5/5；G4-R2 掩码截图 | PASS（模拟器） |
| 6 | 会话与消息持久化、重启恢复、清除只删 AI 表 | FR-AN-02、AC-AN-02、AC-AN-03、AC-A-12、AC-A-14 | `AiSessionEntity`、`AiMessageEntity`、`AiConversationDao`、`RoomAiConversationRepository`、DB v4 | `AiConversationRepositoryTest` 8/8、`AiG3RestartTest` 2/2、`MigrationTest` 11/11 | PASS（Activity 重建/Room 文件重开；冷启动未验） |
| 7 | 键盘、返回、IME 基础行为：发送后收键盘、返回先收 IME、AI 页恢复不自动弹键盘 | FR-A-21、AC-A-18、AC-N-19、AC-N-21（普通返回部分） | `AiScreen.kt`、`AppRoot.kt`、`KeyboardCoordinator.kt`、`PredictiveBackCoordinator.kt` | `AiG3RestartTest`（`ai_input` 未聚焦、`imeVisible=false`）、`AiG3EntryAndKeyLinkageTest` | 部分 PASS（IME 系统行为与真机预测性返回未验） |
| 8 | 无障碍与动效：消息角色合并、流式状态、停止按钮 48dp、Key 密码语义、状态行淡入/发送停止 Crossfade | 06 §8/§10；AC-N-23、AC-AN-01 的语义部分 | `AiScreen.kt`、`DeepSeekKeyScreen.kt`、`NautileMotion.kt` | `AiG4A11ySemanticsTest` 8/8；G4 动效代码核对与 21 张截图 | PASS（语义自动化；动画观感未验） |

### 3.2 逐 AC 状态（按 G1 子集）

| AC | 状态 | 依据与边界 |
| --- | --- | --- |
| AC-N-23 | PASS | 大标题、右上设置、无搜索；`AiG3ScreenStateTest` |
| AC-N-19 | 部分 PASS | Activity 重建后 AI tab 恢复、输入框不聚焦、IME 不可见；真实冷启动与系统 IME 未验 |
| AC-A-09 | 部分/条件 | `tasks`/`projects` 不变、三种提示可区分；日程/日志/计时分支待 R6；真实 401/断网未验 |
| AC-A-12 | 部分 PASS | 请求体只含本次对话与固定系统提示、日志无 Key、清除后不恢复；真实备份文件验收未做 |
| AC-A-14 | PASS | 设置入口、确认、执行、停留设置页、重建后为空；个人记忆延后 |
| AC-A-17 | PASS | 请求体无页面/日期/筛选/可见内容；模型“先澄清或查询”行为人工/延后 |
| AC-A-18 | 部分 PASS | 失败保留新草稿、不自动重发；发送后收键盘与导航恢复需人工 |
| AC-A-19 | PASS | 单请求、不排队、停止后不启动新请求；确认/写操作延后 |
| AC-A-15 | 部分 PASS | 点 AI 入口进入全屏页；长按快捷输入延后 |
| AC-N-21 | 未验收 | Key 子页普通返回已测；真机预测性返回手势未执行 |
| AC-AN-01 | PASS | 流式增量、停止后不再追加、保留已生成内容、停止完成前不可发送 |
| AC-AN-02 | 部分 PASS | 角色/内容/顺序/时间/状态在 Activity 重建后保持；杀进程冷启动未验 |
| AC-AN-03 | 部分 PASS | 清除不影响 `tasks`/`projects`；日程/日志待 R6 |
| AC-AN-04 | PASS | 未配置/掩码/已保存/已清除反馈/无模型名 |
| AC-AN-05 | 部分 PASS | 静态检查与日志检查通过；设备端导出 Room/DataStore 明文扫描未做 |
| AC-AN-06 | PASS（Fake 层） | 超时、5xx、流中断与未配置/无效 Key/断网可区分；真实 HTTP 映射由 G2 纯函数测试覆盖，真实接口未调 |
| AC-AN-07 | PASS | 空态与发送后滚动到底；24 条历史贴底由 `AiG3ChatFlowTest` 验证 |

---

## 4 测试与门禁统计（只列实际执行）

### 4.1 闸门总表

| 闸门 | 命令/方式 | 结果 | 统计 | 证据 |
| --- | --- | --- | --- | --- |
| G1 | 文档一致性与代码事实只读核对；未运行构建/测试 | 首轮“有条件 PASS”（0 高/4 中/5 低），修正后 R2 **PASS**（9/9 关闭，新问题 0 高/0 中/3 低） | 不适用 | `docs/验证记录/AI-G1_需求架构设计冻结验证_2026-11-28.md`、`AI-G1-R2_需求架构设计冻结验证_2026-11-28.md` |
| G2 | `bash scripts/verify.sh AI-G2`；`VERIFY_CONNECTED=1 bash scripts/verify.sh AI-G2` | PASS（四步 EXIT=0；connected EXIT=0） | JVM **45 suites / 271 tests / 0 failures**；connected **51 suites / 343 tests / 0 failures**；MigrationTest **11/11**；lint 0 Error（26 Warning） | `docs/验证记录/门禁/verify_AI-G2_20261128T134050.{log,meta.txt}`、`verify_AI-G2_20261128T134128.{log,meta.txt}` |
| G3 | `ANDROID_SERIAL=emulator-5554 VERIFY_CONNECTED=1 bash scripts/verify.sh AI-G3` | PASS（全部 STEP EXIT=0） | JVM **47 suites / 290 tests / 0**；connected **365 tests / 0**；lint 0 Error；schema v4 | `docs/验证记录/门禁/verify_AI-G3_20261128T154235.{log,meta.txt}` |
| G4 | `bash scripts/verify.sh AI-G4`；模拟器全量 instrumentation；无障碍/截图单类 | PASS（自动化与回归范围） | JVM **47 suites / 290 tests / 0**；模拟器 **57 类 / 377 tests / 0**；无障碍 **8/8**；截图 4/4（产出 21 张）；lint 0 Error；schema v4 | `docs/验证记录/门禁/verify_AI-G4_20261128T162124.log`、`instrument_AI-G4_full_final_emulator_20261128T163501.txt`、`instrument_AI-G4_a11y_emulator_20261128T162353.txt` |
| G4-R2 | `bash scripts/verify.sh AI-G4-R2`；Key 类复跑；全量 instrumentation | PASS；首轮 P1 解除 | JVM **290/0**；模拟器 **377/0**；Key 类 **8/8（两次）**；无障碍 8/8；lint 0 Error | `docs/验证记录/门禁/verify_AI-G4-R2_20261128T165346.log`、`instrument_AI-G4-R2_full_emulator_20261128T165837.txt` |

### 4.2 设备与执行条件

- 所有设备测试均在 `emulator-5554`（Android 16 / API 36）执行，报告与日志均标注“模拟器（非真机）”。
- G3 期间真机 `V2458A` 曾接入：一次 connected 尝试被主动中止，另一次虽返回 RC=0 但结果不计入；最终在仅模拟器、无并发时全绿。
- G4 期间真机 `10AF561Y5R002KL` 保持连接但未使用；真机 hang 日志作为环境失败证据保留，不计入模拟器结果。
- G4-R2 再次确认全程只用 `emulator-5554`，未运行 `VERIFY_CONNECTED=1` 的无设备限定命令。

### 4.3 变异抽查（先红后绿）

| 轮次 | 注入点 | 红 | 还原与绿 |
| --- | --- | --- | --- |
| G2 | `DeepSeekErrorMapping` 的 402 分支改为 `4020` | `http402MapsToInsufficientBalance` 失败，EXIT=1 | 精确还原，sha256 前后一致，同命令 EXIT=0 |
| G3 | 助手消息过滤 `status == COMPLETE` 改为 `status != STREAMING` | 请求体多出半截助手回复，测试失败 | 还原后同测试类 11/11 通过，`git diff app/src/main` 为空 |
| G4 | 测试夹具 `streamingMessageId` 由 `AiMessageId(2)` 改为 `null` | `streamingMessageExposesStateDescription` 失败 | 还原后单测通过，最终 APK 复跑 377/377 |

G4 的变异施加在测试夹具而非业务代码，原因是当轮角色边界禁止修改 `app/src/main/**`；报告已明确该限制。

### 4.4 失败与重试记录（保留原始证据，不写成通过）

- G2 预检：`assembleDebugAndroidTest` 因 `AcSettingsFullScreenG2R4Test` 的 `when` 缺 `DeepSeekKey` 分支失败；补分支后通过，未删除或削弱既有断言。日志：`verify_AI-G2_pre_fix_androidtest_compile_20261128T131936.log`。
- G3：`AiG3ChatFlowTest.emptyStateClearAndScrollToBottom` 首轮在 ViewModel 尚未收到完整 24 条快照时组合页面，产生夹具时序假失败；修正测试侧等待条件后 7/7 通过，原断言保留。另有设备争用与真机误接入导致的失败/中止，均不计入最终结果。日志：`verify_AI-G3_20261128T152034.log`、`...T152708.log`、`...T153224.log`。
- G4：真机 hang 现场保留为 `AI-G4_hang_logcat_20261128T1610.txt`、`AI-G4_hang_recents_20261128T1610.txt`；不计入模拟器 377/377。
- G4 首轮 P1：Key 页返回为 `TextButton("返回")`、标题 `titleLarge`，与 06 §6.2 不符；`efcb020` 改为 48dp `NautileIconButton(ic_arrow_back)` + `headlineSmall`，G4-R2 实测 48.0dp/32.0dp 并 8/8、377/377 复测通过。

### 4.5 截图

本轮 AI 截图 **21 张**：19 张状态截图（空态有/无 Key、对话、流式、停止中、失败、Key 页掩码、Key 页已保存、清除确认、200% 字体对话/Key 缺失空态等浅/深组合）加 2 张真实 App 浅/深截图。文件在 `docs/验证记录/截图/`，前缀 `g4_ai_*` 与 `g4_real_app_*`；`g4r2_*` 另有 7 张复测截图。目录中 `g4_real_todo_light.png` 为待办截图，不计入本轮 AI 21 张。

Key 页截图只引用掩码状态；未引用明文截图，也未使用 `FLAG_SECURE`。

---

## 5 Key 安全与隐私

### 5.1 实际实现

- `AndroidKeystoreSecretStore` 使用 AndroidKeyStore `AES/GCM/NoPadding` 256 位，随机 IV，AAD 为 `nautile.ai.deepseek.key.v1`。
- 密文文件位于 `context.noBackupFilesDir/secrets/<keyName>.bin`，格式为 `版本(1B) + IV(12B) + 密文+tag`；写入使用临时文件后原子替换。
- 读取/解密失败（Keystore 重置、别名丢失、文件损坏）删除密文并回落“未配置”，不崩溃、不打印敏感值。
- `AiKeyCommands` 只读写 `SecretStore`；`AiRequest` 不含 Key，网关在请求头临时读取。
- Manifest 仅新增 `INTERNET` 权限；`android:allowBackup="false"`；`data_extraction_rules` 对 cloud-backup/device-transfer 的 root/file/database/sharedpref/external 全部 exclude。

### 5.2 验证结果

| 检查项 | 结论 | 依据 |
| --- | --- | --- |
| Key 不入 Room/schema | 通过 | AI 表无 key/secret/token 列；G2 §4 |
| Key 不入 DataStore | 通过 | `UserSettingsStore` 键列表无 Key 类键；G2 §4 |
| 日志不含 Key/请求体/响应原文 | 通过 | `src/main` 无 `Log.`/`println`/`printStackTrace`；G3/G4 logcat 搜索 0 条 |
| 截图不含明文 | 通过（引用范围） | Key 页 `PasswordVisualTransformation` + `password()`；G4-R2 探针串只显示圆点 |
| 备份排除 | 通过（配置层面） | `allowBackup=false`；`data_extraction_rules` 全排除；密文在 `noBackupFilesDir` |
| 无 destructive fallback | 通过 | 源码无 `fallbackToDestructiveMigration`/`dropAllTables`/`clearAllTables` |
| 真实 Key | 未写入 | 测试仅用 `sk-fake-g3-key`、`sk-g4r2-probe` 等假值 |
| 真实 DeepSeek | 未调用 | G2—G4 均为 Fake/纯函数；报告与进度记录均声明未调真实接口 |

### 5.3 未完成的安全验收

- 设备端导出 Room 数据库与 DataStore 文件做 Key 明文扫描：未执行（G2 §8）。
- Keystore 在设备上的实际加解密、别名丢失/文件损坏恢复：仅代码审查与静态检查，未做 instrumentation（G2-P5）。
- 真实 Key 的 401/403、真实断网/超时/5xx：未执行。
- 备份文件的实际恢复验收：未执行，归后期。

---

## 6 数据库与 R6 协调

### 6.1 本分支 v4 事实

- `NautileDatabase` version 4，实体为 `tasks`、`projects`、`ai_sessions`、`ai_messages`。
- 迁移链：`MIGRATION_1_2`、`MIGRATION_2_3`、`MIGRATION_3_4`；`MIGRATION_3_4` 只建 AI 两表。
- `app/schemas/.../4.json` 为 v4，保留 `1/2/3.json`；无 destructive fallback。
- `verify.sh` 的 schema 段已支持 v4，校验 4 张表、AI 关键列、`index_ai_messages_session_id`、`RESTRICT` 外键、无 `DEFAULT`。

### 6.2 MigrationTest 当前覆盖

`MigrationTest` 11/11 通过，覆盖：

- 既有 v1→v2、v1→v3、v2→v3、v3 打开；
- 3→4：AI 两表存在且为空，旧 project/task（含 `detail`/`manual_order`）不变；
- 1→4 全链：旧任务字段为 null，AI 表存在；
- 4 打开：`error_code` 可空，其余关键列 NOT NULL、无 DEFAULT；
- 4 外键/索引：孤儿消息插入失败、有消息 session 删除失败、先删消息再删 session 成功、索引存在；
- 4.json 静态：version 4、四表、AI 关键列/索引/RESTRICT、1/2/3 保留。

未覆盖：4→5、3→4→5、1→5。这些依赖 R6 合并后的 v5，当前未执行，不得写成通过。

### 6.3 与 R6 的协调

- R6 分支为 v4、6 张日程/日志表（`schedule_series`、`schedule_events`、`schedule_exceptions`、`schedule_tags`、`time_categories`、`time_logs`），与本分支 AI 两表合计 10 张。
- 编写报告时 R6 已合并进 main（`f24c706`，含 `34f7307`），但 **未合并进 `feature/ai-chat`**；本分支仍以 `c4bafdb` 为基点。
- 后续若用户授权 rebase：本分支再 rebase 到 main，DB 升 v5；AI 的 `MIGRATION_3_4` 改名并改为 `MIGRATION_4_5`，DDL 不变；保留 R6 的 `3→4`；生成 `5.json`；`MigrationTest` 补 4→5、3→4→5、1→5；`verify.sh` 同时检查 `4.json`（R6）与 `5.json`（10 表）。本轮按用户 2026-11-28 决定暂不执行。
- 禁止把 AI 建表 DDL 同时留在 `MIGRATION_3_4` 与 `MIGRATION_4_5`；禁止用 AI 分支的 `4.json` 覆盖 R6 的 `4.json`。

---

## 7 未完成与延后清单

以下各项均**未完成或未验收**，不得写成通过。

### 7.1 Phase A 明确不做的能力

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 数据查询/写入工具 | 未实现 | FR-A-02—06、FR-A-10、FR-A-13/14、FR-A-24 延后；无工具入口 |
| 授权与写入确认 | 未实现 | AC-A-02/03/04/13 延后 |
| 个人记忆 | 未实现 | FR-A-07—09、AC-A-07 延后；设置无入口 |
| 思考强度/联网 | 未实现 | FR-A-19、AC-A-15 菜单部分延后 |
| 长按快捷输入/共享草稿 | 未实现 | FR-A-17、AC-A-15 长按部分、AC-A-16、AC-A-18 快捷输入部分延后 |
| 通知/角标 | 未实现 | FR-A-23、FR-N-22/28、AC-A-20、AC-N-17/20 延后 |
| 日志分析 | 未实现 | FR-A-10、AC-A-08/21 延后 |
| 多会话/附件/导出/备份 | 未实现 | 单会话；无附件入口；FR-B-01/02、AC-B-01—03 后期 |
| 自动重试 | 未实现（明确不做） | 失败保留草稿，不自动重发 |
| 工具调用 | 未实现 | 无工具与调用设计 |

### 7.2 本轮未自动化/未验收

| 项 | 状态 | 依据 |
| --- | --- | --- |
| R6 日程/日志不变分支（AC-A-09、AC-AN-03） | 未执行 | 待 rebase 到 v5 后补验 |
| 真实 DeepSeek 请求、真实 Key、401/403/402/429/5xx、断网/超时 | 未执行 | G2/G3/G4 报告均列“未自动化” |
| Key 子页真机预测性返回（AC-N-21） | 未验收 | 仅普通返回与导航映射自动化 |
| TalkBack 全链路（焦点顺序、朗读、Key 不明文） | 未验收 | 需真机人工 |
| 动画观感、停止手感、发送/停止切换闪烁 | 未验收 | 代码与语义可测，观感需人工 |
| 系统 200% 字体（Key 页/对话框组合） | 未验收 | 组件级 `fontScale=2` 已测，系统设置未验 |
| IME 无缝、发送后收键盘、底导随 IME 显隐 | 未验收 | 仅断言输入框不聚焦 |
| 杀进程冷启动恢复 | 未验收 | 仅 Activity 重建与 Room 文件重开 |
| 设备端 Room/DataStore 明文扫描 | 未执行 | G2 §8 |
| Keystore 设备端加解密/损坏恢复 | 未执行 | G2-P5 |
| 真机 10AF561Y5R002KL | 未使用 | G4/G4-R2 均明确 |

---

## 8 已知偏差、观察与问题处理

| 编号 | 事实 | 影响/处理 | 状态 |
| --- | --- | --- | --- |
| D-1 | 06 §10.1 要求“停止后焦点回输入框”，实现未做，以避免自动弹键盘 | 与冻结规格存在偏差；已登记观察项，待设计澄清是“焦点”还是“不弹键盘” | 未关闭 |
| D-2 | `AiChatCoordinator.buildRequest` 取 `_state.messages` 快照；极端快速连发时，若上一条助手 COMPLETE 尚未发射，可能少带一轮回复 | G3-R1 低风险，未复现；真实 Room 第二轮请求测试通过 | 观察项 |
| D-3 | G4 首轮 Key 页头部为 `TextButton("返回")` + `titleLarge`，与 06 §6.2 的 48dp 图标 + `headlineSmall` 不符 | `efcb020` 修正；G4-R2 实测 48.0dp 返回、32.0dp 标题，Key 类 8/8、全量 377/377 | 已关闭 |
| D-4 | 工作区保留 22 个历史截图副本（`g10_*`、`g3r4_*`、`g6r4_*`、`g7r4_*`、`g9_*`），未入库；哈希未与原件逐一确认 | 按“不删除唯一证据”保留，待用户决定删除/归档/保留；本报告不引用 | 待裁决 |
| D-5 | “已清除”只是瞬时反馈，持久状态只有“未配置/已保存” | 与 06/需求口径一致；不得把“已清除”做成常驻状态 | 已按规格 |
| D-6 | 组件级截图未套 `AppRoot` insets，状态栏图标可能压住标题 | 测试宿主现象，不代表产品布局；真实 App 截图已核对 insets | 非产品缺陷 |
| D-7 | G2 观察项 `ai_key_screen` testTag 缺失 | G3 实现中已补（源码 `DeepSeekKeyScreen` 含该 tag）；G4-R2 未再报 | 已关闭（代码事实） |
| D-8 | G2-P4：`AiSettingsViewModel` 异步成功/失败映射未直接单测；G2-P5：Keystore 未设备实测 | 覆盖限制，已在未自动化清单登记 | 未关闭 |

---

## 9 证据索引

### 9.1 验证报告

| 报告 | 路径 |
| --- | --- |
| G1 首轮 | `docs/验证记录/AI-G1_需求架构设计冻结验证_2026-11-28.md` |
| G1 复测 | `docs/验证记录/AI-G1-R2_需求架构设计冻结验证_2026-11-28.md` |
| G2 | `docs/验证记录/AI-G2_Key安全_客户端_会话数据层验证_2026-11-28.md` |
| G3 | `docs/验证记录/AI-G3_AI页面与对话核心验证_2026-11-28.md` |
| G4 | `docs/验证记录/AI-G4_UI动效无障碍与回归验证_2026-11-28.md` |
| G4-R2 | `docs/验证记录/AI-G4-R2_Key页头部复测_2026-11-28.md` |

### 9.2 门禁日志与 instrumentation

- G2：`docs/验证记录/门禁/verify_AI-G2_20261128T134050.{log,meta.txt}`（JVM/lint/schema）、`verify_AI-G2_20261128T134128.{log,meta.txt}`（connected）、`verify_AI-G2_pre_fix_androidtest_compile_20261128T131936.log`（预检失败）、`verify_AI-G2_mutation_red_20261128T133029.log`（变异红）。
- G3：`docs/验证记录/门禁/verify_AI-G3_20261128T154235.{log,meta.txt}`（最终 PASS）、`verify_AI-G3_20261128T152034.log`、`...T152708.log`、`...T153224.log`（失败/中止现场）、`verify_AI-G3_mutation_{red,green}_20261128T1451*.log`。
- G4：`docs/验证记录/门禁/verify_AI-G4_20261128T162124.{log,meta.txt}`、`instrument_AI-G4_full_final_emulator_20261128T163501.txt`、`instrument_AI-G4_a11y_emulator_20261128T162353.txt`、`instrument_AI-G4_screenshot_emulator_20261128T162400.txt`、`mutation_AI-G4_{red,green}_20261128T1632*.txt`、`AI-G4_hang_*.txt`（真机 hang，不计入）。
- G4-R2：`docs/验证记录/门禁/verify_AI-G4-R2_20261128T165346.{log,meta.txt}`、`instrument_AI-G4-R2_key_20261128T165551.txt`、`..._key_repeat_20261128T170545.txt`、`..._a11y_20261128T165603.txt`、`..._screenshot_20261128T165611.txt`、`..._full_emulator_20261128T165837.txt`、`AI-G4-R2_key_header_measure_20261128T170600.txt`、`AI-G4-R2_screenshot_cleanup_20261128T170500.tsv`。

### 9.3 截图

- G4 状态与真实 App：`docs/验证记录/截图/g4_ai_*.png`（19 张）、`g4_real_app_ai_key_missing_{light,dark}.png`（2 张）。
- G4-R2：`docs/验证记录/截图/g4r2_ai_key_page_*.png`、`g4r2_real_app_key_page_*.png`（7 张）。
- Key 页只引用 `*_masked_*`、`*_saved_*` 等掩码状态；不引用明文。

### 9.4 需求、架构与设计依据（`.plans`，不入库）

| 类型 | 路径 |
| --- | --- |
| 需求子集 | `.plans/需求/AI/AI-PhaseA_FR_AC子集.md` |
| 架构提案 | `.plans/架构/AI/AI-PhaseA_架构提案.md` |
| 交互与动效规格 | `.plans/设计/06_AI交互与动效规格.md` |
| 视觉标准 | `.plans/设计/01_视觉与动效标准.md` |
| 协调记录与任务书 | `.plans/交接/AI/` |

`.plans/` 已由 `.gitignore` 忽略，不进入提交；本报告只引用，不移动或改写。

### 9.5 复现命令

```bash
cd /home/wordwoods/Projects/Tride/.worktrees/ai-chat
git log --oneline c4bafdb..HEAD
git status --porcelain
git ls-remote --heads gitee feature/ai-chat
git ls-remote --heads github feature/ai-chat
```

---

## 10 合并说明（未执行）

### 10.1 当前实际状态

- `feature/ai-chat`：`41b788e`，基于 `c4bafdb`。
- `main`：`f24c706`，已包含 R6 合并 `34f7307`。
- 本分支与 main 的 merge-base 仍为 `c4bafdb`；`git rev-list --left-right --count main...HEAD` 为 `19	12`（main 领先 19，本分支领先 12）。
- **用户 2026-11-28 决定暂不 rebase**：不在本轮 rebase 到 main `f24c706`，不做 DB v5 改造。
- **G5 状态**：报告文件已完成但未提交；分支已推送（`41b788e`）；未 rebase、未合并、未打 tag。
- 本报告是交付说明，不是“可合并状态”的证明；直接合并会因 AI 与 R6 都占 v4 而冲突。

### 10.2 后续前置（用户未授权本轮 rebase，均未执行）

由于 main 已包含 R6，直接执行“merge feature/ai-chat”会把 AI 的 v4 与 R6 的 v4 冲突。以下为后续前置，本轮均未执行：

1. 待用户另行授权后，在独立工作副本中 `git rebase main`（或先合并 main 到分支再处理），保留 R6 的 `MIGRATION_3_4`。
2. AI 建表迁移改名并改为 `MIGRATION_4_5`，DB version 5；删除重复的 AI 版 `3→4`。
3. 生成 `5.json`，保留 R6 的 `4.json` 与 `1/2/3.json`。
4. `MigrationTest` 补 4→5、3→4→5、1→5；`verify.sh` 同时校验 4/5.json。
5. 重新执行 `verify.sh` 与全量模拟器回归；日程/日志相关 AC 分支补验。
6. 冲突处理需用户确认；若后续 rebase 遇到 `ic_stop.xml` 冲突，**采用 R6 官方 Material Symbols 图标**。当前 AI 分支仍保留自绘版本，本轮未改代码。
7. 用户确认冲突处理后，再由用户授权执行：

```bash
git switch main
git pull gitee main
git merge --no-ff feature/ai-chat
```

冲突处理、提交、推送与打 tag 均需用户明确授权；本报告不自动执行。当前不能直接合并，必须先完成 rebase 与 v5 迁移改造（若用户届时授权）。

---

## 11 事实、建议与未执行声明

### 11.1 已核实事实

- 提交序列、HEAD `41b788e`、两端 `feature/ai-chat` ref `41b788e`、工作区 22 个未跟踪截图副本。
- `git rev-list --left-right --count main...HEAD` 实际为 `19	12`：main 领先 19，本分支领先 12。
- 用户 2026-11-28 决定暂不 rebase：本分支保持基于 `c4bafdb`，未 rebase 到 main `f24c706`，未做 DB v5 改造。
- G2/G3/G4/G4-R2 的测试统计、lint 0 Error、schema v4、MigrationTest 11/11、变异红→绿。
- 设备均为模拟器；真机未用于结论。
- Key 存储实现、Manifest `allowBackup=false`、仅 `INTERNET` 权限、无 destructive fallback。
- 本分支 DB v4 四表；R6 已合并 main 但未合并本分支。

### 11.2 建议

- 待用户另行授权 rebase 与 v5 改造后，再补 4→5、3→4→5 迁移测试与日程/日志不变分支；本轮不执行。
- 若后续 rebase 遇到 `ic_stop.xml` 冲突，采用 R6 官方 Material Symbols 图标；当前 AI 分支保留自绘版本，未改代码。
- 对“停止后焦点回输入框”做设计澄清：若坚持焦点回输入框，需要明确是否允许自动弹键盘；若不允许，应回写 06 §10.1。
- 对 22 个未跟踪截图副本做哈希核对后决定删除、归档或保留；在决定前不删除。
- 真实 DeepSeek、真实 Key、TalkBack、IME、200% 字体与动画观感另行安排人工验收。

### 11.3 未执行

- 未运行真实 DeepSeek 请求，未写入真实 Key。
- 未做真机测试、真机预测性返回、TalkBack 全链路、IME 人工、系统 200% 字体、动画观感。
- 未做杀进程冷启动、设备端存储导出扫描、Keystore 设备端加解密。
- 未执行 rebase、DB v5 改造、4→5/3→4→5 迁移与日程/日志断言。
- 未修改 `ic_stop.xml` 或任何业务代码。
- 未合并、未打 tag、未推送 tag；报告文件未提交。
- 本报告不构成用户验收结论；最终验收由用户确认。
