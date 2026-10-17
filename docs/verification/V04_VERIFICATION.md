# v0.4.0 integration verification

Date: 2026-10-17. Branch: `codex/v7-shelf-reader`. Package: `io.github.zer0rin.yhviewer`; versionName `0.4.0`, versionCode `5`; database schema `4`.

## Scope

Merged supplied v7 author/cover metadata and chapter drawer into the current EPUB/reader implementation. Retained charset decoding, EPUB entity/path limits, documentSession/renderer callbacks, anchors, position persistence, editor functionality, profile isolation and system-bar restoration. Added shelf groups, multi-selection, independent content classification, author search, cover backfills and Overture-style app colors/fonts. Reader routing and default recent-open sorting remain unchanged.

New metadata is optional after the content transaction commits. Cover decoding rejects sources above 20 MiB or 40 million pixels, samples both dimensions, caps output at 300×900 while retaining aspect ratio, and atomically replaces thumbnails. Group writes validate inside transactions, deletion preserves members' files/positions, and batch SQL updates are chunked. Batch document deletion finishes each started item across cancellation and clears only that item's profile after deletion. Selection drops hidden items after filters change.

A bounded input with 5,000 leading HTML comments reproduced a StackOverflowError in the supplied classifier regex. A forward-only comment scan replaces that repetition; timeout-bounded tests cover valid, malformed and sample-boundary inputs.

## Environment and commands

Local JDK: Android Studio bundled JBR 25.0.3. Android SDK: `/Users/guyue/Library/Android/sdk`. Dedicated emulator: `YHViewer_V03_Test`, serial `emulator-5556`, API 37. No physical Android device was connected during this run. No real user document was used for acceptance.

Builds use `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew ... --no-configuration-cache`.

Initial build: both debug APKs and both instrumentation APKs compiled; 177 JVM tests per edition passed before final regressions were added. Room exported `app/schemas/com.example.yhviewer.AppDatabase/4.json`; new table/index SQL matches the migration after table-name substitution. Initial targeted migration run passed all four tests, including 3→4 and 1→4 with data preservation.

The expanded JVM suite passed 183 tests per edition (0 failures/errors/skips). Both debug instrumentation APKs and optimized unsigned Release APKs built successfully; both debug Lint tasks passed with 0 errors and 30 warnings each (the new cover code has one UseKtx suggestion). Release APK permissions: Strict has no INTERNET; Standard has INTERNET. Bundled Python assets verified present only in Standard.

The first full Strict run executed 108 tests and exposed five failures: incomplete synthetic PNG header, ambiguous Cancel test selector, old directory-dialog text, retained filter keyboard focus, and native pixel sampling through system/closed-drawer overlays. The fixture/selectors were corrected. Production drawer close now clears input focus and hides the keyboard; its border is entirely inside the drawer, removing a 1–2 px closed-state ghost edge. The native color test acknowledges/restores Android immersive onboarding and enables/restores the tall-cutout overlay. Both reader acceptance tests subsequently passed with the exact terminal RGB assertion unchanged.

Latest complete Strict run: 108 executed, 107 passed, 1 test-only failure in the shelf UI scenario. That scenario pressed Back while the search IME was still open, then long-pressed the already-selected item and deselected it. The repaired test explicitly dismisses the keyboard, presses Back, and waits for the selection header to disappear before entering selection again. Production code was unchanged after this full run. The complete ShelfUiTest class then passed 3/3 tests (0 failures/errors/skips); all 108 distinct Strict scenarios are validated across the full run plus this test-only repair/rerun. Evidence XML: `build/verification-v04/strict-full.xml` and `strict-shelf-rerun.xml`. Standard full suite passed 117/117 (0 failures/errors/skips) on API 37. Evidence: `build/verification-v04/standard-full.xml`. The same final production code was exercised by both editions. The Strict suite's only repaired failure was in test sequencing, with no production change afterward.

Native screenshots use generated fixtures only: [light shelf](../screenshots/shelf.png), [dark shelf](../screenshots/v4-shelf-dark.png), [multi-selection](../screenshots/v4-selection.png), [chapter drawer](../screenshots/toc.png), [terminal reader with native inset background](../screenshots/v4-terminal-reader.png). Dark shelf acceptance passed 1/1 and explicit reader screenshot acceptance passed 2/2. All captures were inspected. The exact terminal background test kept its original `#1d2a32` assertion. Captures are opt-in via instrumentation argument `v7Screenshots=true`; normal test runs do not leave images.

## Physical-device acceptance remaining

The API 37 emulator checks native reading background pixels and closed-drawer gestures; actual OEM cutout rendering, physical system back gestures, memory pressure and an in-place upgrade of a populated physical-device library still require a connected phone. No publishing, release tag or official signed release was performed.

## Final artifacts and review

Final post-fix builds and 183 JVM tests per edition passed again. Both Lint tasks pass with 0 errors (30 warnings each). `git diff --check` is clean. Independent source review has no outstanding actionable findings; its fixes include safe per-item profile deletion, filtered selection pruning, classifier bounds and drawer focus/border handling.

Both final R8 APKs were signed with the local Android debug key only for emulator verification. `verify-release.sh` confirmed package/version/signature and each edition's permission/runtime assets. Both were installed on the dedicated emulator and passed `smoke-release.py`: native shelf rendered and the process remained alive. This is not the official release signature; these artifacts cannot replace an installation signed with the production key.

Local optimized test artifacts:
- `build/verification-v04/apk/yhviewer-v0.4.0-strict-r8-debug-signed.apk`
- `build/verification-v04/apk/yhviewer-v0.4.0-standard-r8-debug-signed.apk`

Full-suite XML and screenshot copies are retained in `build/verification-v04/`. Source/schema/font licenses and inspected screenshots are in the repository. Changes are on candidate branch `codex/v7-shelf-reader`. After local verification the user requested installation using the previous GitHub signed-candidate workflow. This candidate is not merged, tagged or publicly released; physical-device tests are excluded from the installation request.


## Official candidate installation

The earlier v0.3.0 installation used a GitHub Actions manual signed-candidate build with existing Secrets, followed by a local artifact download and transfer to the phone. The phone previously rejected the shell-origin installer Intent, so the user installed from the phone file manager. The same candidate-only method is used for v0.4.0; no new signing credential or key is created. Signing certificate must match the installed production package before transfer/installation. No physical-device test suite is run.
