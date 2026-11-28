<h1 align="center">Nautile</h1>

<p align="center">
  English · <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/status-R5%20%2B%20R6%20Phase%20A-blue.svg" alt="Status: R5 + R6 Phase A">
</p>

## Overview

Nautile is a personal planning, time-tracking and review app. It keeps todos, schedules, time logs and AI-assisted analysis on the same data, so you do not have to switch between several tools. The app is meant to stay small and focused, and it does not run a language model locally: cloud AI is planned through the official DeepSeek API. Nautile is intended to be released as open source.

## Status

First-release implementation is in progress (current `main` branch integrates **R5 todo interaction & visual alignment** and **R6 Schedule & Time Log Phase A**, aligned with requirements baseline **v2.16**). The project builds a debug APK and, when the external signing file exists, a signed release APK. Room 2.8.5 + KSP 2.3.14 are enabled; database version is **4** with **8 tables** (`tasks`, `projects`, `schedule_series`, `schedule_events`, `schedule_exceptions`, `schedule_tags`, `time_categories`, `time_logs`), migrations `MIGRATION_1_2` / `MIGRATION_2_3` / `MIGRATION_3_4`, and schemas `1.json`–`4.json` committed under `app/schemas/`.

Implemented slice on `main` (R5 todo/settings + R6 Phase A schedule/time log):

- **Navigation & settings**: four-tab shell (todo, schedule, log, AI) with a top-right settings entry on each primary page; settings root, default-page subpage, and full-screen two-level **Category Management** subpage; light/dark system-bar insets, predictive-back toggle, and startup default/last primary page mapping.
- **Todo (through R5)**: MD3 light/dark blue theme, project-grouped cards (including empty project groups and standalone tasks), circular FAB, real-time search bar, per-position view classification/sorting menu (manual sort entry suspended per `FR-V-03`, stored `manual` falls back to `created`); full-height GTD left drawer (240–320dp, right scrim, four close paths, tap-only switching) and main-page direction-locked boundary pull (reverse-past-zero cancellation, non-wrapping first/last hints); task rows with leading M3 Checkbox, inline detail and due date/time (overdue highlighted with `error` color + `"已逾期 · "` prefix without auto-closing), 220ms linear Checkbox complete exit animation, swapped right-swipe complete / left-swipe abandon (child-count confirmation on project rows), and disabled Checkbox/swipe on closed rows; unified new-task and isomorphic task-detail bottom sheets (fixed two-line title/detail, four icon+text attributes, scrim-free anchored menus, non-overlapping calendar + `"时间"` row opening an M3 24h `TimePicker` dial with strict `HH:mm` validation and layered back); project detail with inline name editing, icon-only chevron collapse toggle (`contentDescription`), and closed-project reopen confirmation.
- **Schedule (R6 Phase A)**: day, three-day, and fixed Mon–Sun week views switched via a view menu button; top date strip (tap date / Today shortcut, no horizontal swipe date change); fixed 60dp/hour time grid (no pinch-to-zoom); new-event and edit-as-you-go detail bottom sheets with strict 24h `HH:mm` validation, date/24h time dial, end strictly after start (cross-day allowed), and delete confirmation; three-state source filter (`课程表` / `合并` / `仅普通`, empty state when no imported classes exist, read-only `"普通"` source in detail) and label field (`课程表` / `无标签`, without tag filtering UI).
- **Time log (R6 Phase A)**: two-level category management (parent/child CRUD, rejects 3rd level, no bulk clear, blocks deletion with a prompt while children or logs exist); newest-first log records with backfill, edit-as-you-go detail, and deletion; parallel multi-category timing with wall-clock restoration (tap category quick-entry to start — recent first plus persistent `"全部分类"` — duplicate tap only prompts, no pause, tap active timer bar entry to stop and save); horizontally scrollable timer bar above bottom navigation (hides with navigation while timing continues); half-screen mode selector opened from the title button to switch between Record and Statistics (resets to today on exit); day/month/year/custom pie-chart statistics with legend, parent totals, untracked slice, live inclusion of running timers, `"含重叠累计"` indicator, and cross-day logs stored as one logical record while split at `00:00` in statistics.

Verification scope: combined R5 + R6 regression on `main` passed on an **Android 16 (API 36) emulator (`emulator-5554`, not a physical device)** — JVM **60 suites / 387 tests / 0 failures** (`PackageBoundaryTest` 8/8), emulator instrumentation **64 suites / 413 tests (411 passed + 2 expected skips in `AcLogL05RestoreTest`, 0 failures, 0 errors)**, Lint **0 errors**, static UI token check (21 files, 8 rules) **0 hits**, Room DB v4 (8 tables) schema/migration verified, and debug APK cold-start verified with 0 fatal exceptions; see [docs/验证记录/R5-R6_合并主分支联合回归验证_2026-11-28.md](docs/验证记录/R5-R6_合并主分支联合回归验证_2026-11-28.md), [docs/交付报告_R5.md](docs/交付报告_R5.md), and [docs/交付报告_R6.md](docs/交付报告_R6.md). Earlier S1 instrumentation also passed on a vivo V2458A physical device (`docs/验证记录/T-085_第三轮复测_2026-11-24.md`). The merged R5 + R6 build has **not** yet been verified on the target physical device (vivo X200s / OriginOS 6 / Android 16), and does not constitute full-product or physical-device acceptance.

AI assistance and personal memory, reminders (todo & schedule), recurring schedules, class-schedule import, schedule tag filtering, schedule-todo linking, time-log task linking, single-activity timer mode switching, pull-to-switch between log record and statistics, todo tags, subtasks, project colors/icons, quadrant view, undo, backup/restore, and Dynamic Island timing are **not implemented**. Manual sort entry remains suspended per `FR-V-03`.

## Planned Features

The first-release scope is listed below. Only the todo, schedule (Phase A), time log (Phase A), and settings subset described under Status is implemented; the rest is planned and is not an acceptance result.

**Plan and organize**

- Lightweight GTD structure with five task positions (inbox, next actions, waiting for, someday/maybe, ended) plus projects as an independent container/view; schedules are reached only through the schedule entry, so GTD has no calendar position, and lists switch through the GTD left drawer or main-page boundary pull.
- Tags, complete/abandon/delete actions, and a completion rate based on finished versus abandoned items.
- Optional reminders on todos, off by default and set manually per todo.
- A standard todo page grouped by project, with selectable classification and sorting for each GTD module.

**Schedule**

- Built-in calendar with day, three-day and week views, plus source filtering for class schedule, both sources combined, or regular events only.
- Schedule tag filtering, with "Class" as the default tag for imported classes and "No tag" as the default otherwise.
- Real class schedule import from a school source; the technical path is still to be decided, and import provides a preview and validation step. Re-importing replaces every class event generated by the previous import, not only overlapping ones, while events manually or AI-tagged as class are kept.
- Recurring events (daily or selected weekdays, with an end date) with three edit/delete scopes: this occurrence, this and following, or the whole series.
- Multiple reminders per event, plus lightweight reminders at a time boundary or review time.

**Time tracking**

- Two-level category time logs with an optional linked task, newest-first records, later edits and backfilled entries, and a daily distribution.
- Timing starts when a category is tapped; tapping the active category on the timer bar ends the entry and saves it. Single-activity and parallel modes are exclusive, with parallel as the default.
- Statistics for a day, month, year or custom range, including untracked time.

**AI assistance**

- A full-screen AI page with a page title and settings, and no search; long-press on the AI entry opens quick input. One conversation shares the same draft, page context is not attached automatically, and there are no attachments.
- Query real data and create or adjust schedules through conversation; the first write of each operation type requires confirmation.
- Review time logs and completion status over a period and suggest next steps grounded in that data.
- Query and analyse time logs but never create, edit or delete them; time logs are written manually.
- Analyse the share of todo tags to show where attention goes.
- Remember personal preferences only after confirmation.
- Keep the DeepSeek API key in the system secure storage; AI sessions are stored locally and can be cleared.

**Navigation and editing**

- Bottom navigation has four fixed entries: todo, schedule, log and AI; entries cannot be hidden or reordered, and settings opens from the top of each primary page.
- Details open in a half-screen panel and each field is saved as it is edited; new items are created only by an explicit create action; item actions use anchored menus, and choices open in a bottom panel.
- Android predictive back is supported: the system back gesture or back key previews the previous page before completing, it is enabled by default and can be turned off in settings, and devices without support fall back to normal back.

## Planned for Later

These are outside the first-release scope and are not implemented yet.

- Quadrant view: positions are assigned automatically from due date and priority by default; they can be adjusted manually, and AI changes them only after authorisation.
- Undo of the most recent batch of AI changes to todos and schedules.
- Backup and restore, including the handling of sensitive data.
- Rescheduling after a repeated class-schedule import.
- Dragging todos onto the schedule to create or move events.
- Dynamic Island / system capsule timing: OriginOS is supported first; it shows only the category and elapsed-time summary, a tap returns to the log page, and timing cannot be switched or ended inside the island.
- Richer statistics and advanced charts.

## Known limitations

- Combined R5 + R6 verification ran on an Android 16 (API 36) emulator (`emulator-5554`), not on the target physical device (vivo X200s / OriginOS 6 / Android 16); lock-screen, background/recents kill, and device-reboot timer persistence (`AC-L-05` / `AC-L-17`) have not been verified on a physical device.
- In full connected regression runs, 2 tests in `AcLogL05RestoreTest` are intentionally skipped unless `-e l05_manual true` is supplied for the manual `force-stop` sequence.
- Motion feel and physical gestures are not automated: 220ms linear completion exit smoothness, 1:1 follow-through/rebound on drawer/boundary/swipe/shared `NautileBottomSheet` content-top pull, 24h dial touch feel, record↔stats / calendar↔form crossfade, pie-chart & timer-bar animations, reduced-motion (`AnimatorDurationScale = 0`) degradation, real IME keyboard insets/transitions, and edge-back conflicts require physical-device review.
- 200% font scaling, 360dp week-view density, TalkBack screen-reader walkthrough (`liveRegion` announcements), Accessibility Scanner checks, light/dark contrast ratio measurements (including pie-chart palette), and performance metrics (FPS/memory/startup) have not been measured.
- AC-N-21 predictive-back preview composes an independent read-only instance of the lower page rather than an exact state snapshot, and still needs physical-device review. Whole-process restart UI restoration is not automated (database close/reopen persistence is covered by tests).
- Manual sort entry is suspended per `FR-V-03` (stored `manual` falls back to `created`; `manual_order` data and DB v4 schema are preserved). `DatePicker` currently renders in `en-US` locale. KSP emits a non-blocking missing-index warning on `schedule_exceptions.override_event_id` (table unused in Phase A).
- AI assistance/memory, reminders, recurring schedules, class-schedule import, schedule tag filtering, time-log task linking, single-activity timer mode switching, log boundary pull-to-switch statistics, todo tags, subtasks, project colors/icons, quadrant view, undo, and backup/restore are not implemented. Manual-review ACs are not acceptance results in this README.

## Screenshots

32 light and dark emulator verification screenshots from R5 are archived under `docs/验证记录/门禁/r5*.png` (indexed in [docs/交付报告_R5.md](docs/交付报告_R5.md)); R6 verification screenshots are kept outside version control for manual review (listed in [docs/交付报告_R6.md](docs/交付报告_R6.md)). Curated screenshots have not yet been embedded directly into this README.

## Getting Started

Nautile builds and runs the current todo, schedule (Phase A), time log (Phase A), and settings slice on `main`. AI assistance and the remaining first-release features are not available yet.

### Prerequisites

Android 12 (API 31) or later. The project uses compileSdk 37, targetSdk 36, JDK 17 (configured outside the repository) and the Gradle wrapper 9.6.0.

### Installation

Build with `./gradlew assembleDebug` (or `./gradlew assembleRelease` when the external signing file exists):

- debug: `app/build/outputs/apk/debug/app-debug.apk`
- release: `app/build/outputs/apk/release/app-release.apk`

Install the debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Release builds read signing properties from outside the repository; when they are absent, the release variant is skipped.

### Configuration

DeepSeek is planned as the only cloud AI provider, using the official API with a key supplied by the user; no model runs locally. The exact configuration format is not decided yet. Never commit a real key; a placeholder would look like this:

```text
DEEPSEEK_API_KEY=<your-key>
```

### Database

Storage uses Room and KSP (Room 2.8.5, KSP 2.3.14). Database version **4** defines **8 tables**: `tasks`, `projects`, `schedule_series`, `schedule_events`, `schedule_exceptions`, `schedule_tags`, `time_categories`, and `time_logs` — built via explicit non-destructive migrations `MIGRATION_1_2` (`tasks.detail`), `MIGRATION_2_3` (`tasks.manual_order`), and `MIGRATION_3_4` (the 6 schedule/time-log tables and indexes). Schemas `1.json`–`4.json` are committed under `app/schemas/`. Migration and repository contract tests run on JVM and on an Android 16 emulator (see verification records).

## Usage

Implemented workflow on `main`:
- **Todo**: switch GTD positions via the left GTD drawer or direction-locked boundary pull; complete tasks via the leading Checkbox (220ms linear exit) or right swipe, left-swipe to abandon; tap a task for the isomorphic bottom detail sheet (instant save for title/detail, date/24h time dial, priority, project, checklist) or tap a project to rename inline / collapse children with the chevron icon; search, adjust view grouping/sorting, or create items via the circular FAB.
- **Schedule (Phase A)**: switch day/three-day/week views from the view button, pick dates from the top strip, filter sources (`课程表` / `合并` / `仅普通`), and create/edit/delete regular events via bottom sheets with strict 24h time validation.
- **Time log (Phase A)**: manage two-level categories from Settings → Category Management; tap a category quick-entry on the Log page to start parallel timing and tap the active timer bar entry to stop and save; backfill/edit/delete records; open the mode selector from the title button to view day/month/year/custom pie-chart statistics (including untracked time and cross-day splitting).
- **Settings**: open from the top-right entry on each primary page to configure the default page, predictive-back switch, and category management. Preferences are stored in DataStore.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/作业要求.md](docs/作业要求.md) | Course requirements and grading criteria (Chinese) |
| [docs/需求分析指导.md](docs/需求分析指导.md) | Requirements analysis guide and standards (advisory, no added grading criteria; Chinese) |
| [docs/产品构想.md](docs/产品构想.md) | Product vision, decisions and first-release scope (Chinese) |
| [docs/需求基线.md](docs/需求基线.md) | Requirements baseline v2.16: scope and acceptance criteria (Chinese) |
| [docs/开发架构设计.md](docs/开发架构设计.md) | Development workflow, roles, verification and evidence rules (Chinese) |
| [docs/交付报告_R5.md](docs/交付报告_R5.md) | R5 delivery report and emulator verification summary (Chinese) |
| [docs/交付报告_R6.md](docs/交付报告_R6.md) | R6 Schedule & Time Log (Phase A) delivery report (Chinese) |
| [docs/验证记录/R5-R6_合并主分支联合回归验证_2026-11-28.md](docs/验证记录/R5-R6_合并主分支联合回归验证_2026-11-28.md) | R5 + R6 merged `main` combined regression verification report (Chinese) |

`docs/任务进度.md` and `docs/项目初始化工作纪要.md` are internal process records and are not listed here.

## Roadmap

Future directions, not commitments and without dates: bring the workflow to university students; desktop and cross-device sync; in-app academic calendar login; external calendar integration.

## Contributing

Nautile is at an early development stage. Contribution guidelines have not been defined, and contributions are not open yet.

## License

[MIT](LICENSE) © 2026 Ma Zilin

## Acknowledgements

The task workflow takes the Getting Things Done (GTD) method as a reference, and the timing interaction is informed by Simple Time Tracker. This project is not affiliated with either of them.
