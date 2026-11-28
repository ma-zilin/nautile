<h1 align="center">Nautile</h1>

<p align="center">
  English · <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/status-todo%20slice%20(R5)-blue.svg" alt="Status: todo slice (R5)">
</p>

## Overview

Nautile is a personal planning, time-tracking and review app. It keeps todos, schedules, time logs and AI-assisted analysis on the same data, so you do not have to switch between several tools. The app is meant to stay small and focused, and it does not run a language model locally: cloud AI is planned through the official DeepSeek API. Nautile is intended to be released as open source.

## Status

First-release implementation is in progress (current milestone: **R5 todo interaction & visual alignment**, aligned with requirements baseline **v2.16**). The project builds a debug APK and, when the external signing file exists, a signed release APK. Room 2.8.5 + KSP 2.3.14 are enabled; database version is **3** with `tasks` and `projects`, nullable `tasks.detail` (v1→v2) and `tasks.manual_order` (v2→v3), and schemas `1.json`/`2.json`/`3.json` committed under `app/schemas/`.

Implemented todo & settings slice (through R5):

- four-tab shell (todo, schedule, log, AI) with a top-right settings entry on each primary page;
- todo screen: MD3 light/dark blue theme, project-grouped cards (including empty project groups and standalone tasks), circular FAB, real-time search bar, and per-position view classification/sorting menu (manual sort entry suspended per `FR-V-03`, with stored `manual` falling back to `created`);
- GTD full-height left drawer (240–320dp, right scrim, four close paths, tap-only switching while open) and main-page boundary pull with initial direction lock, reverse-past-zero cancellation, and non-wrapping first/last hints;
- task rows and swipe: leading M3 Checkbox, inline detail and due date/time (overdue highlighted with `error` color plus a non-color-only `"已逾期 · "` prefix without auto-closing), 220ms linear Checkbox complete exit animation (same completion path as right swipe), swapped right-swipe complete / left-swipe abandon (with child-count confirmation on project rows), and disabled Checkbox/swipe on closed rows;
- unified new-task sheet and isomorphic task-detail bottom sheet: fixed two-line title/detail, four icon+text attributes (date, priority, project, GTD checklist) with scrim-free anchored menus, shared calendar date picker (weekday header non-overlapping) and `"时间"` row opening an M3 24h `TimePicker` dial (strict `HH:mm` validation, clear action, layered system back); task detail saves fields/attributes immediately, blocks closing on invalid fields, and supports reopening closed tasks (separate edit sheets and header edit icons removed);
- project detail sheet: inline edit-as-you-go project name, icon-only chevron collapse toggle (with `contentDescription`), and closed-project reopen confirmation;
- settings root and default-page subpage with light/dark system-bar insets, predictive-back toggle, and startup default/last primary page mapping.

Verification scope: R5 G1–G6 independent verification passed on an **Android 16 (API 36) emulator (`emulator-5554`, not a physical device)** — JVM 240/240, emulator instrumentation 313/313, Lint 0 errors, static UI token check 0 hits, and 32 light/dark screenshots reviewed; see [docs/交付报告_R5.md](docs/交付报告_R5.md). Earlier S1 instrumentation also passed on a vivo V2458A physical device (`docs/验证记录/T-085_第三轮复测_2026-11-24.md`). R5 has **not** yet been verified on the target physical device (vivo X200s / OriginOS 6 / Android 16), and does not constitute full-product or physical-device acceptance.

Schedule and class-schedule import, time logs/timing/statistics, AI assistance and personal memory, tags, reminders, recurrence, subtasks, project colors/icons, quadrant view, undo, and backup/restore are **not implemented**. Manual sort entry remains suspended per `FR-V-03`.

## Planned Features

The first-release scope is listed below. Only the todo/settings subset described under Status is implemented; the rest is planned and is not an acceptance result.

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

- R5 verification ran on an Android 16 (API 36) emulator (`emulator-5554`), not on the target physical device (vivo X200s / OriginOS 6 / Android 16).
- Motion feel and physical gestures are not automated: 220ms linear completion exit animation smoothness, 1:1 follow-through/rebound on drawer/boundary/swipe/sheets, 24h dial touch feel, reduced-motion (`AnimatorDurationScale = 0`) degradation, real IME keyboard transition, and edge-back conflicts require physical-device review.
- 200% font scaling, TalkBack screen-reader walkthrough, Accessibility Scanner checks, dark-theme contrast ratios, and performance metrics (FPS/memory/startup) have not been measured.
- AC-N-21 predictive-back preview composes an independent read-only instance of the lower page rather than an exact state snapshot, and still needs physical-device review.
- Whole-process restart UI restoration is not automated (database close/reopen persistence is covered by tests).
- Manual sort entry is suspended per `FR-V-03` (stored `manual` falls back to `created`; `manual_order` data and DB v3 schema are preserved). `DatePicker` currently renders in `en-US` locale.
- Schedule/import, time log/timing/statistics, AI/memory, tags, reminders, recurrence, subtasks, and project colors/icons are not implemented. AC-D-07 covers todo creation only. Manual-review ACs are not acceptance results in this README.

## Screenshots

32 light and dark emulator verification screenshots from R5 are archived under `docs/验证记录/门禁/r5*.png` (indexed in [docs/交付报告_R5.md](docs/交付报告_R5.md)); curated screenshots have not yet been embedded directly into this README.

## Getting Started

Nautile builds and runs the current todo/settings slice (through R5). Schedule, log, AI and the remaining first-release features are not available yet.

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

Storage uses Room and KSP (Room 2.8.5, KSP 2.3.14). Database version **3** defines `tasks` and `projects`, with nullable `tasks.detail` added in migration 1→2 (`ALTER TABLE tasks ADD COLUMN detail TEXT`) and nullable `tasks.manual_order` added in migration 2→3 (`ALTER TABLE tasks ADD COLUMN manual_order INTEGER`). Schemas `1.json`, `2.json` and `3.json` are committed under `app/schemas/`. Migration and DAO tests run on JVM and on an Android 16 emulator/device (see verification records).

## Usage

Implemented todo & settings workflow: open the todo page, switch GTD positions via the left GTD drawer or direction-locked boundary pull, complete tasks via the leading Checkbox (220ms linear exit) or right swipe, left-swipe to abandon, tap a task for the isomorphic bottom detail sheet (edit title/detail or pick date/24h time dial, priority, project, or checklist with instant save), tap a project to edit its name inline or collapse children with the chevron icon, search or adjust view grouping/sorting from the top bar, use the circular FAB to create a todo or project, and open settings from the top-right entry. Predictive-back preference, default page, last page/position, and per-position view options are stored in DataStore.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/作业要求.md](docs/作业要求.md) | Course requirements and grading criteria (Chinese) |
| [docs/需求分析指导.md](docs/需求分析指导.md) | Requirements analysis guide and standards (advisory, no added grading criteria; Chinese) |
| [docs/产品构想.md](docs/产品构想.md) | Product vision, decisions and first-release scope (Chinese) |
| [docs/需求基线.md](docs/需求基线.md) | Requirements baseline v2.16: scope and acceptance criteria (Chinese) |
| [docs/开发架构设计.md](docs/开发架构设计.md) | Development workflow, roles, verification and evidence rules (Chinese) |
| [docs/交付报告_R5.md](docs/交付报告_R5.md) | R5 delivery report and emulator verification summary (Chinese) |

`docs/任务进度.md` and `docs/项目初始化工作纪要.md` are internal process records and are not listed here.

## Roadmap

Future directions, not commitments and without dates: bring the workflow to university students; desktop and cross-device sync; in-app academic calendar login; external calendar integration.

## Contributing

Nautile is at an early development stage. Contribution guidelines have not been defined, and contributions are not open yet.

## License

[MIT](LICENSE) © 2026 Ma Zilin

## Acknowledgements

The task workflow takes the Getting Things Done (GTD) method as a reference, and the timing interaction is informed by Simple Time Tracker. This project is not affiliated with either of them.
