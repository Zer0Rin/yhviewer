# v7 reader and EPUB integration

Owned files: ReaderScreen.kt, Epub.kt, new ChapterDrawer.kt, EpubCoverTest.kt, and V7ReaderIntegrationTest.kt.

## Changes

Merged optional author and coverPath into the existing EPUB model/parser, with defaults for source compatibility. Author whitespace is collapsed and length bounded at 80 characters. Cover preference is EPUB3 cover-image, EPUB2 cover meta, then a named cover bitmap. Only existing JPEG/PNG/GIF/WebP files are accepted. Manifest hrefs still use the current strict decoder; canonical containment additionally rejects cover symlinks pointing outside the book directory.

Preserved existing SafeUnzip limits and directory-content rejection, strict spine-reference validation and missing/empty chapter rejection, chapter/XML entity and size checks, optional TOC security checks, charset detection and fail-closed XML entity resolver. The supplied whole-file EPUB replacement would have lost these protections.

ReaderScreen now wraps book reading and its settings in ReaderChromeTheme. A full-screen background paints the reading color underneath the unchanged stable displayCutout inset. Top/bottom chrome and the new directory follow the selected reader theme. Drawer opens by the explicit 目录 button; gestures are enabled only when already open. Back closes the drawer before navigating to the shelf. Chapter drawer highlights the current chapter, dims earlier chapters, filters titles/numbers above 20 chapters, and retains original indexes during filtering. Query resets on opening; scrolling waits one frame for the unfiltered list to compose, then clamps safely.

Preserved PageReader/editor/run/permission handling, documentSession keys and captured session callbacks, onRendererGone, EPUB chapterAnchor, TXT style updates without reload, EPUB reload keys, system bar previous visibility/behavior restoration, and KeepScreenOn previous state restoration. Extended system bar handling to use light/dark icon appearances from reader palette and restore both prior appearances on disposal.

## Verification

- Context7 official Compose drawer docs fetched: https://developer.android.com/develop/ui/compose/components/drawer. Confirmed rememberDrawerState, composition CoroutineScope and gesturesEnabled behavior.
- Supplied EpubCoverTest adopted with extra encoded-path, invalid-declaration fallback and author-length tests (10 total, including a real external symlink).
- New device regression tests exercise actual ReaderScreen: closed drawer ignores horizontal drag; explicit opening/filtering selects original chapter 23; reopened filter clears and selected row scrolls onscreen; Back closes drawer without exiting reader; terminal native background pixel follows reader color.
- Existing reader instrumentation suites remain unchanged for lifecycle/session/reading regression coverage.
- git diff --check passed at handoff.
- Gradle/test/device execution is coordinated by root, so no concurrent Gradle process was started here. EpubCoverTest was staged before metadata implementation; no isolated failing test run was available before implementation. Do not claim a completed red-green cycle.

## Remaining validation

Root must compile both flavors and instrumentation APKs, run unit suites and available device tests, and record runtime verification. Device pixel/gesture acceptance requires emulator or hardware execution. Font resources and YHTheme.kt are owned by root; database/repository metadata integration is owned separately.

## Runtime follow-ups from root's strict suite

The first full strict instrumentation run exposed two V7 checks plus stale V2 selectors. V2ReaderIntegrationTest now expects the drawer heading, waits target chapter DOM, scopes previous/next buttons to native reader chrome, and uses displayed BookTitles on shelf reopen.

The drawer filter retained input focus while its offscreen content stayed composed. Closing target now clears focus and hides the IME, so reopening the directory after picking a filtered chapter does not carry an old keyboard into the reader. This does not intercept Back while a user is actively typing in the open directory.

The original terminal pixel check sampled visible TopAppBar/system-bar treatment on an emulator without a cutout. The corrected test enables the available tall cutout overlay for this method (restoring original state in finally), hides reader/system chrome, waits the out-of-Compose bar animation to finish at the expected color, and samples the actual native inset background. No live overlay change was performed during editing. Root must rerun these cases against the rebuilt APK.

The subsequent focused run confirmed the directory/IME regression fixed. Terminal exact pixel validation was still obscured by Android's first-use ImmersiveModeConfirmation system popup. Root identified that foreground window in a direct native screenshot. The test now preserves/restores the secure immersive confirmation setting while acknowledging onboarding for the acceptance run. Exact native-color assertion remains unchanged. Failure diagnostics log ARGB/color space/bounds/cutout/insets and optionally export terminal-reader-failure.png when screenshots are requested.

After suppressing onboarding, root's native screenshot showed the final pixel issue: x=0 was the drawer border and x=1 its antialiased edge; x>=2 was already the exact reader theme background. A centered right-edge stroke extended outside the drawer bounds and bled into the reader when the closed drawer was translated offscreen. Replaced it with a filled 1dp rectangle wholly inside the drawer width. The exact x=1 pixel test remains unchanged and now covers this real visual regression.
