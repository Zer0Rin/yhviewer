# v0.5.0 visual integration verification

Date: 2026-10-17. Source branch: `codex/v7-shelf-reader`, supplied patch base `2a64d95`. Package: `io.github.zer0rin.yhviewer`; versionName `0.5.0`, versionCode `6`; Room schema remains `4`.

## Scope

Applied the supplied 14-file v8 patch using `git apply --index`. Unified shelf, reader, dialogs and editor chrome with the reading palette; removed Bodoni assets and notice; added persisted three-column/list book layout, dotted search/progress, capsule actions, press feedback, filename prettification and “刚刚”. Default recent-open ordering, reader routing, per-document permissions and existing v7 metadata/group migrations remain intact.

Independent review found and resolved stale system-bar appearance after changing themes inside a book, low-contrast small labels, long-title continue-card clipping and narrow large-font selection actions. Reader disposal now uses the latest appearance; progress exposes accessibility semantics; the continue card grows with content; selection actions share width and retain a 48dp minimum height. Paper secondary/link text and Ink action text meet 4.5:1 against both page and raised backgrounds. Paper CSS muted/quote/link tokens match native colors.

## Build and local verification

JDK: Android Studio bundled JBR 25.0.3. SDK: `/Users/guyue/Library/Android/sdk`, build-tools `36.0.0`. Builds use `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew ... --no-configuration-cache`.

Both debug APKs, both instrumentation APKs and both R8 release APKs compiled. JVM suites passed **188 tests per edition, 0 failures/errors/skips**. Both Lint tasks passed with **0 errors, 32 warnings** each. Final APK checks confirmed Strict excludes INTERNET and Python runtime assets while Standard includes the intended permission/runtime.

Both optimized APKs were locally signed with the Android debug key exclusively for emulator verification. `verify-release.sh` passed for each, including package/version/signature, permissions and Python assets. Both passed `smoke-release.py` on dedicated `emulator-5556`: native shelf rendered and process survived. These local artifacts are not production-signed installation packages.

Evidence is retained under ignored `build/verification-v05/`: build/test/Lint logs, contrast-red logs, `systembars-progress-red.log`, `layout-red.log`, `layout-green.log`, APK checks and release smoke logs. Red/green checks reproduced missing progress semantics, stale system-bar icons, hidden continue button and collapsed delete action before their production fixes.

## Emulator acceptance and screenshots

Only the dedicated `YHViewer_V03_Test` API 37 emulator was operated, always through explicit serial `emulator-5556`. A connected physical phone was not launched, installed, cleared or tested for this task. All acceptance documents and EPUB cover images are generated fixtures. Screenshot runs refuse a nonempty shelf and clean only their owned records/groups and restore preferences.

The initial broad local regression was interrupted by an Android System UI service ANR and focus-stealing system dialog; it is not a passing run. A cold boot and dismissal of the failed System UI instance restored the test environment. The first v8 capture run exposed two test-fixture issues: MediaStore appended a TXT extension to a Kotlin file with `text/plain`, and the editor exit test clicked before its JavaScript API was ready. The Kotlin fixture now uses an opaque MIME type and asserts its actual name/type; editor tests wait for clean readiness. TXT captures wait for DOM/theme content and WebView visual-state readiness. EPUB retains scripts disabled: acceptance verifies intercepted themed XHTML, native loading/content height, compositor readiness and rendered background pixels. This corrected a test-only DOM polling mistake and prevents blank first-frame screenshots.

The final v8 class exercised 11 scenarios: 10 passed in the complete run, and its one test-only EPUB-readiness repair then passed on rerun. Production code was unchanged between them. This validates grid alignment/persistence, generated covers, three-item selection, long-title and font-scale-2 layouts, explicit/AUTO themes, system-bar restoration, accessible progress, Markdown/editor backgrounds and chapter capsules. Full-suite evidence: `v8-11-acceptance-before-epub-fixture-fix.log`; repaired capture evidence: `epub-screenshot-rerun.log`.

The exact native bottom-corner RGB was checked for Paper/Ink/Terminal on both shelf and Markdown. This scenario passed with gestural navigation and again after enabling the three-button navigation overlay (`threebutton-navigation.log`). Both navigation modes keep contrast enforcement disabled and icon appearance matches the palette; the gestural overlay was restored afterward.

All six captures were regenerated from fictional fixtures and visually inspected: [paper shelf](../screenshots/shelf.png), [ink shelf](../screenshots/shelf-ink.png), [three selected items](../screenshots/v4-selection.png), [Chinese chapter drawer](../screenshots/toc.png), [terminal EPUB](../screenshots/v4-terminal-reader.png), and [paper Markdown](../screenshots/markdown-paper.png). The shelf contains two covered EPUBs, two TXT books including a half-read hero, two HTML files (one relaxed), Markdown, Kotlin, JSON and two groups. README references match.

GitHub complete-regression results will be recorded here before merge.

## Review

[Independent review](../superpowers/plans/v8-review.md) approved the final source repairs, with no outstanding actionable findings. No production signing credentials, release tag or public Release is created by this integration task.
