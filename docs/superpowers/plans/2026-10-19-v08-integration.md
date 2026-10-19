# v0.8 TXT indexing and continuous reading integration plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute the tasks in order with task review and final whole-branch review.

**Goal:** Integrate supplied v11 on main9e78f19, verify native behavior/performance, and open a reviewable PR.

**Architecture:** Retain the provided byte-range TXT index, bounded continuous iframe shell and TOC drag scroller. Compile and repair their integration with the existing repository, lifecycle, native find, permissions and test infrastructure. Default continuous-mode acceptance is separate from preserved single-chapter regression coverage.

**Tech Stack:** Kotlin/Compose, Android WebView/asset-loader, Room5, Gradle, two APK flavors, JVM and Android instrumentation.

## Global Constraints

- Version0.8.0/code9. PR only; do not merge, publish, tag or operate a physical phone.
- TXT titles/order/body exactly match v0.7; old reading locators must remain valid. Open a valid TXT index without reading the full text; clean deleted/orphan indexes. Existing 32MiB TXT import limit remains.
- Default continuous=true for TXT and EPUB. At most5 frames, preserve chapter/ratio under append/prepend/eviction/images/theme/font/reopen. Scroll-driven chapter changes retain the WebView; explicit jumps target the requested location.
- Trusted shell scripts only; book scripts/onerror/meta-refresh/top-navigation cannot escape book isolation or alter the shell. Preserve strict/offline resource policy.
- Native find must search iframe contents. An incompatible WebView requires reporting the finding and human design input; do not silently substitute another search architecture. Find holds appending and nth-result jumps count only the target chapter.
- TOC fastscroll threshold40, original chapter number under filtering, 28dp track/4dp thumb/6dp dragging. No weakening existing assertions; legacy per-chapter tests explicitly save/restore continuous=false.
- Measure about30MB/4000chapters on an emulator, including import/indexedopen/legacy rebuild. Desktop timings are not Android evidence. Preserve private original30pack outside Git.
- Both-flavor JVM, Lint, debug/instrumentation compilation; new continuous native cases, existing regression suites, actual T05, native screenshots and full API34 CI. Main schema remains5.

### Task 1: Integrate patch and verify source/contracts

**Files:** supplied21-file patch; relevant main Kotlin/assets and JVM tests; existing androidTest classes for explicit legacy preferences. Requirements detail: supplied `/Users/guyue/Downloads/INTEGRATION.md`; working brief `build/verification-v08/global-constraints.md`.

- [x] Fresh baseline: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testStrictDebugUnitTest :app:testStandardDebugUnitTest --no-configuration-cache` succeeds at9e78f19.
- [ ] Apply exactly `git apply --index /Users/guyue/Downloads/yhviewer-v11.patch` on codex/v08-reading-flow.
- [ ] Compile both variants and fix type/lifecycle/resource integration defects. Add a failing regression for each substantive discovered defect before repair; preserve split equivalence, index invalidation/cleanup and book isolation.
- [ ] Search old tests with `rg 'ReadingPrefs|/doc/index|/book/|chapters|read_keep_on' app/src/androidTest`; migrate only chapter-page-dependent setups to continuous=false and restore old read_continuous value. Preserve default-flow new cases.
- [ ] Run both JVM suites, both debug/androidTest APK assemblies and both debug Lint tasks, with no concurrent Gradle builds. Capture command/status/count evidence under build/verification-v08/.
- [ ] Commit source and test fixes. Generate task diff from9e78f19 and independent spec+quality review; close required findings.

**Interfaces:** OpenedDoc.Txt.book:TxtBook, titles:List<String>; TxtBook.text/chapter/allTexts; FlowShell.render/parseState/bookPath; PageSource.Flow; VM.onFlowPosition/onFlowLink/jumpTick; ReadingPrefs.continuous.

### Task 2: Native continuous-mode acceptance and performance

**Files:** new androidTest V11 flow acceptance/performance/probe classes and generated fixture helper; docs/verification/V08_VERIFICATION.md, docs/testing/v11-acceptance.md, docs/screenshots/v08-*.png, candidate notes and README.

- [ ] First run direct native iframe-find compatibility probe on explicit dedicated emulator serial. Keep the result separate from actual-shell acceptance. No connected-phone operations.
- [ ] Generate deterministic TXT/EPUB fixtures with chapter markers and sufficient lines. Exercise real app defaultcontinuous rendering: start middle chapter, cross down/up, chrome and repository position/reopen; assert <=5 loaded frames and prepend viewport text/offset preservation.
- [ ] Change font/theme through Aa and assert same WebView and same chapter/ratio; toggle continuous off and confirm original one-page reader. Preserve/restore preferences in every test.
- [ ] Exercise bookmark, directory and exact nth whole-book result in continuous mode; verify native count equals the selected chapter. Show hold prevents additions and closing search resumes.
- [ ] EPUB user link→other chapter anchor; malicious inline script/onerror/meta refresh/target_top leave shell URL and DOM unchanged, and book scripts remain disabled. Assert actual outcome, not merely policy strings.
- [ ] T05 from local30pack: continuous fallback chapters and tail native find work. Do not commit original files.
- [ ] Generate 30MiB-ish,4000chapter TXT using buffered writes; measure repository import, indexedopen and legacy index rebuild with monotonic clock, record actual bytes/time/chapters. Do not assert desktop latency or increase file limits.
- [ ] Capture and visually inspect native boundary and dragged fast-scroll bubble screenshots. Verify filter uses original chapter numbering and dragging chooses the intended chapter.
- [ ] Execute both-flavor targeted native cases, original migration/sandbox/permission and legacy reader regression; full API34 CI runs all generated tests. Address discovered defects with covering regressions, not relaxed assertions.
- [ ] Commit and independently review task, record evidence/limits and feature known limitations: find suspends additions; vertical EPUB normalized horizontal; late large images compensate layout.

### Task 3: Final review and PR

- [ ] Generate one complete diff package from9e78f19 to final head and request most-capable available whole-branch review; batch any required final fixes in one wave and recheck.
- [ ] Confirm fresh checks match final source/test commits, worktree clean and artifacts/screenshots contain only synthetic data.
- [ ] Push codex/v08-reading-flow, open PR against main with feature/validation/limitations description, attach PR to chat. No merge/release/phone operation.
