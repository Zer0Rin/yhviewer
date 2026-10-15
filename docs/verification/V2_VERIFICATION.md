# v2 reader integration verification

Date: 2026-10-15. Package `com.example.yhviewer`, versionCode 2 / versionName 2.0. Database v2. Local target: API 37 emulator; CI target: API 34.

## Scope

Integrated the supplied code reader, chapter-relative progress, encoded TXT and EPUB readers into the existing Compose shelf and sandbox. Kept the package, application theme, original HTML presentation and offline libraries. `template-4.html` supplies the code page; `template-5.html` supplies the Markdown page; shared CSS carries the four existing themes.

## Automated checks

The final command is:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug --no-configuration-cache
```

BUILD SUCCESSFUL. XML reports checked: **76 JVM + 43 instrumented tests**, zero failures/errors/skips on API 37. Lint: **zero errors, 18 warnings**. The final APK declares no `INTERNET` permission; all packaged assets match the restored source files. APK SHA-256: `b66aaabc82e16ce7c3d4c00996a103c8537edb5d6ccdea3cb9128f27c8891611`.

The 76 JVM cases are the previous 21, the supplied 49 and six additional parser/resource edge cases. The 43 device cases include the previous suites adapted to the new reader API, replacement repository coverage (14 total), one migration case and five v2 UI cases. Repeated permission/theme/fallback runs are not additional unique tests; the two generated smoke tests remain included.

The five v2 device cases exercise:

- Kotlin, Python and YAML highlighting; a short compact JSON; native Ctrl+C/ClipboardManager with an empty line and partial multi-line selection, no copied gutter, horizontal long-line scrolling with a fixed gutter; four themes and same-document sentinel; instant wrap and saved wrap preference.
- A 2.14 MiB / 32,000-line log: chunked DOM, no syntax highlighting, visible large-file note and a usable native menu. This establishes behavior for this fixture, not a performance guarantee for every file up to the import limit.
- GBK→UTF-8 private copy, duplicate TOC headings filtered into three chapters, TOC/previous/next controls, chapter 2 at approximately 45%, theme reflow, Activity recreation, shelf percentage and reopen.
- EPUB title and nav labels, chapter JS disabled, actual interceptor CSP (`script-src 'none'`) and injected styles, PNG bytes plus rendered image pixels, a real tap to another chapter's `#target`, all four themes and approximately 40% chapter position retained. The fixture script would remove the image if it ran.
- Orphan cleanup before import, an old same-chapter scroll callback rejected after switching books, and an immediate `onStop` save/reopen at approximately 65%.

`MigrationTestHelper` creates a database from committed `1.json`, inserts an old favorite, applies `MIGRATION_1_2`, validates the table against v2, and asserts metadata/favorite retained plus null locator/default-zero progress. Both exported schemas are committed. Serialization is aligned to 1.8.1 with a BOM: otherwise the application's transitive 1.7.3 core is forced into the test runtime while Room's migration helper brings 1.8.1 JSON, causing `AbstractMethodError`.

## Issues found and corrected

The supplied Kotlin compiled after package/theme integration; old instrumentation needed the `PageSource` and enum-theme API changes. Subsequent checks exposed and corrected:

- Missing spine chapters were silently dropped; they now reject import.
- Unsafe optional nav declarations were swallowed by fallback handling; entity checks run before optional parsing fallback and now also cover spine chapter XHTML.
- Percent-decoded absolute/scheme/backslash/NUL paths are validated again; malformed percent escapes fail closed.
- ZIP directory entries containing payload bytes bypassed ordinary byte accounting; they now reject immediately.
- TXT without any newline could avoid fallback chunk boundaries; it now splits at a bounded size without breaking UTF-16 surrogate pairs.
- Cancellation could leave a newly written file behind. Cleanup runs in `NonCancellable`, checks database references before deleting a target, and preserves the old copy if a transaction rolls back. A shared repository lock prevents startup cleanup racing another repository instance's import.
- Theme changes before page readiness were lost. Native changes wait for `onPageFinished`, use enum/JSON arguments and restore ratios with the actual WebView scale. Session/chapter tokens reject old callbacks. Position writes use immutable snapshots in an ordered app-lifetime queue, and opening waits for pending saves.
- EPUB cross-chapter links lost fragments; the fragment now survives chapter handoff and URL encoding.
- Default WebView copy omitted empty flex rows; a range-based copy handler preserves selected cell text, blank lines and partial endpoints. Chunk paint containment clipped unwrapped long lines; intrinsic-width chunks now expose horizontal scrolling, while wrap mode stays constrained to screen width. Placeholder sizing applies only to height, so offscreen blocks do not add thousands of blank horizontal pixels.
- JSON shorter than 120 characters was not formatted. Compact JSON up to 500,000 characters now formats, while larger inputs avoid expansion. Large plaintext logs also show the disabled-highlighting note.

Test fixtures were corrected rather than weakening behavior: MediaStore `text/plain` appended `.txt` to unknown extensions, so code fixtures use generic MIME and assert their real display names; the Markdown size-limit fixture uses `text/markdown`. Rotation assertions retain every import/identity field while allowing the new progress fields to change. Tests await native content geometry, asynchronous shelf rows, a non-null copied clipboard value and actual night-mode Activity replacement.

## Sandbox and fallback acceptance

A temporary debug Manifest declared `INTERNET`; `SandboxIntegrationTest` ran with `expectInternet=true`: **3 cases, zero failures/errors/skips**. A native HTTPS positive control returned **200**. The sandbox page's initial and reopened results were each **9/9 PASS**. Independent interceptor assertions include external resources, file/content URLs, missing resources, lookalike hosts, HTTP on the virtual host and a nonstandard port. Real taps and script redirects retain their distinct navigation behavior.

The temporary Manifest was removed. With `markdown-it.min.js` temporarily absent, `MarkdownRenderingIntegrationTest#libraryAvailabilityMatchesBuildVariant` ran with `missingMarkdown=true`: **1 case, zero failures/errors/skips**, verifying the warning plus readable raw source. The original library was restored before the final normal build. Final APK permission verification is recorded below.

## Actual upgrade and process death

An actual device upgrade installed the saved v1 APK/data, then used `adb install -r` for v2, with **no uninstall between versions**. `PRAGMA user_version` changed 1→2. All four original book identities/metadata and the existing favorite were unchanged; every referenced private copy retained its SHA-256. Locator defaults were null and progress defaults zero. One pre-existing, unreferenced empty file was removed by startup cleanup; no referenced file was removed.

A real GBK novel was imported through a granted MediaStore `content://` URI. After navigating to chapter 2 and scrolling, the app was backgrounded, the saved locator checked, the process force-stopped and the original Download file deleted. A cold launch opened the shelf copy at **第二章 山间来信**: locator `1:0.0904` → `1:0.0904`, with the same book ID and private copy. Process PID changed `13092` → `13394`; Terminal remained selected. [Before stop](../screenshots/v2-txt.png) / [after cold start](../screenshots/v2-txt-restored.png).

A separate real force-stop/cold-start cycle retained the code wrap preference, confirmed by the checked **自动换行** menu item. The original Auto theme and default unwrapped preference were restored after captures. The emulator's animation settings were restored too. The original four shelf books remain, alongside the three generated demonstration documents.

[Code](../screenshots/v2-code.png), [EPUB with a local SVG](../screenshots/v2-epub.png), and [shelf with progress](../screenshots/v2-shelf.png) are native captures. A script in the EPUB fixture did not replace its visible chapter content.

## CI

The [CI workflow](https://github.com/Zer0Rin/yhviewer/actions/workflows/ci.yml) runs JVM tests, lint and a debug build on pushes/PRs, and the full instrumented suite on API 34 for pushes to main/manual runs. Live run conclusions and downloadable XML/HTML reports are available there (14-day artifact retention); the local counts above are not inflated by additional API/permission/fallback executions.

## Practical limits

- Big5 detection can select GB18030 and produce incorrect text; the detector is heuristic.
- EPUB XML/XHTML is currently decoded as UTF-8. Metadata/chapter XML is limited to 10 MiB, archive entries to 50 MiB, extracted total to 200 MiB and entry count to 3,000; compressed EPUB import caps at 100 MiB. Text caps are 20 MiB, or 32 MiB for TXT.
- EPUB support is for unencrypted reflowable books; scripts/DRM and publisher-specific interactive features are unsupported.
- Progress is a chapter plus approximate scroll ratio, not a semantic paragraph anchor. Background-save verification backgrounds before force-stop; an abrupt foreground kill before the debounce writes cannot guarantee the latest gesture is persisted.
- Code chunks use `content-visibility`; all line DOM nodes are still created. This is not a fully virtualized editor.
- Relative resources in standalone HTML still need folder authorization; EPUB resources are served within the extracted book root.
- Real-device/OEM/WebView-version coverage is still pending.

Screenshots are native emulator captures using generated fictional code, novel and EPUB fixtures. Development scripts, raw logs, backups and temporary test artifacts remain under ignored `build/v2-integration/`.
