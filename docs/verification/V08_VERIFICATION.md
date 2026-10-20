# v0.8.0 candidate verification

Base: main `9e78f19` / v0.7.0. Candidate: `codex/v08-reading-flow`, version **0.8.0 / versionCode 9**, Room schema **5** unchanged. This work prepares a PR only; no phone operation, merge, tag or publication.

## Build and source checks

- Strict and Standard: **248 JVM tests each**, zero failures/errors/skips.
- Both debug APKs, both instrumentation APKs and both debug Lint tasks pass. Lint: zero errors,34 warnings and1 hint per edition; the new warning is a `UseKtx` suggestion for parsing a queued link.
- `node scripts/test-flow-shell.cjs` executes the production shell and passes trusted/blocked link, one-shot route consumption, short-chapter traversal, five-frame bounds, settled requests, live reflow and short/long boundary position round-trips. Its geometry model complements native tests.
- Final APK permission checks: Strict has no INTERNET, Standard has INTERNET. Python assets are absent from Strict and match the bundled provenance hashes in Standard. AAPT confirms0.8.0/code9.
- Independent Task1 source review, Task2 spec/quality review and final whole-branch review approve. Production is reviewed through `b5b5b07`; subsequent test-only readiness changes are reviewed through `fca8004`. Documentation review also approves.
- Final-source [API34 CI39345002625](https://github.com/Zer0Rin/yhviewer/actions/runs/39345002625), commit `fca8004`, passes both jobs. Downloaded instrumentation XML: **Strict145/145, Standard154/154**, zero failures/errors/skips. Downloaded JVM XML confirms248 passes per edition. The first device attempt failed before compilation/tests when Maven Central reset the Kotlin plugin download; only that job was retried, without a code change. The earlier baseline CI is not substituted for these final results.

## Native acceptance

Dedicated API37 arm64 emulator `YHViewer_V03_Test`, WebView145,840×1680px at density420 (320dp wide). Every local device command explicitly targets `emulator-5556`; no phone was used. The local emulator display overrides were reset and the task-started emulator was stopped after testing.

| Matrix | Result |
|---|---|
| Strict continuous suite |Final15 generated cases + private T05 pass16/16 at `fca8004`|
| Standard continuous suite |Final15 generated cases + private T05 pass16/16 at `fca8004`|
| Strict existing v8 acceptance |11/11 pass, original assertions retained with explicit legacy chapter mode|
| Standard existing v8 acceptance |11/11 pass, original assertions retained with explicit legacy chapter mode|
| Original private30 pack |Strict passes all30 document tail searches plus H02 resource/native navigation checks; Standard also passes the complete original-pack test at `194ca49`; subsequent changes affect only V11 test synchronization|
| Direct native iframe-find capability probe |Pass; actual TXT/EPUB Flow find is also covered below|
| Full final-source API34 regression |Strict145/145 and Standard154/154 pass, including generated continuous/performance cases and migration/sandbox/permissions/legacy readers; private external cases explicitly excluded|

Continuous cases cover TXT and EPUB forward/backward chapter crossing, matching native title/count, retained WebView, persisted chapter/ratio and reopen; prepend and late image-height compensation; stable short windows; live theme/font controls and legacy-mode toggle; bookmark add/jump/delete, TOC selection and filtered native fast scrolling; target-only native3/3 search with the third occurrence visible and fill held/resumed; EPUB internal native link plus anchor; script/onerror/meta-refresh/top-link/trusted-shell isolation; and long-line fallback search. Additional native cases verify that idle link polling does not starve automatic debounced position saving, and Find opened before shell readiness holds frame additions until explicitly closed. Strengthened Aa assertions verify settled rendered CSS/font size, the same visible paragraph and chapter/ratio within0.04 after both theme/font-family and120% font-size changes; Both editions pass these strengthened checks in the final16-case matrices (63.838s Strict,63.955s Standard).

The original30 pack remains outside Git. T05 is a separate opt-in `@ExternalFixturePack` case; it uses tree-URI grants, an owned cache copy and real default-Flow search. Ordinary CI excludes private-pack cases and never counts them as passes. See [reproduction notes](../testing/v11-acceptance.md).

## Large TXT measurement

Unchanged buffered-generated fixture: **30,665,783 bytes (29.245MiB),4000 chapters**, within the existing32MiB import limit. Normal repository operations, monotonic timing:

| Edition | Import + index | Open valid index | Missing-index legacy rebuild | Following indexed open |
|---|---:|---:|---:|---:|
| Strict |29.297s|67.753ms|29.770s|37.135ms|
| Standard |29.214s|64.086ms|30.431s|33.047ms|

Both cases pass1/1 (59.239s Strict,59.818s Standard). They additionally verify4000 chapters, matching sampled content, unchanged valid-index timestamp/bytes, file-backed storage and empty chapter cache before body reads, rebuilt-index reuse and index deletion with the document. These are repository timings in an instrumented process, **not cold app startup or physical-phone measurements**. The patch author's desktop benchmark is not used as Android evidence.

## v12 heading-window correction (2026-10-20)

The previous table records the earlier matcher-region implementation and remains as the before measurement. The v12 patch replaces full-book matcher regions with candidate line windows. JVM tests include the supplied3,000 adversarial documents. A new Android comparison against the original full-text regex exposed a supplied-patch mismatch at `Chapter\u00A0\n12`: Android recognizes the nonbreaking space as regex whitespace. The scoped repair uses the active runtime’s `\s*` on short slices and scans added whitespace lines once. The original native assertion then passes in both editions; chapter titles, bodies and ordering are compared directly.

Same dedicated API37 emulator / WebView145, same unchanged test generator, **30,665,783 bytes /4000 chapters**, same heap and32MiB cap. No body/index assertion or benchmark fixture was relaxed.

| Edition / implementation | Import + index | Open valid index | Missing-index rebuild | Following indexed open |
|---|---:|---:|---:|---:|
| Strict before |29.297s|67.753ms|29.770s|37.135ms|
| Strict v12 |1.328s|66.423ms|0.697s|35.991ms|
| Standard before |29.214s|64.086ms|30.431s|33.047ms|
| Standard v12 |1.444s|63.867ms|0.722s|35.953ms|

Both v12 benchmark cases pass, including4000 chapters, indexed-open metadata/cache checks, sampled content, missing-index rebuild, reuse and cleanup. Both rebuilds are below the requested5-second diagnostic threshold, so no alternative optimization or stage instrumentation was introduced. These remain repository-operation timings on an emulator, not physical-device startup claims. Old books rebuild once; subsequent opens read the index.

Both editions now pass249 JVM tests, debug/androidTest builds and Lint. Android whitespace/title parity passes Strict1/1 and Standard1/1; production Flow shell checks also pass. Independent v12 delta review approves spec and quality with no actionable findings. Final v12 CI and signed-test publication are pending. The line-window optimization is not an unconditional linear-time guarantee for adversarial LF-free inputs containing many Unicode-separated failed candidates; overlapping windows can still be revisited.

## Corrections established during integration

- Preserve exact legacy TXT splitting, including CRLF/lone CR, Unicode line starts, long heading numbers and cross-line `chapter` whitespace. Index v2 Base64 preserves tabs/newlines in titles, bounds private metadata reads and validates byte ranges. Existing chapter numbers and0–1 locators retain their meaning; optional Flow offsets preserve continuous chapter tails/gaps without a Room change.
- The initial unchanged large fixture exposed Java OOM, then a native low-memory kill. Strict validation now uses bounded workspace; TXT import avoids redundant full-text decode/output buffers, and heading scans reuse one native ICU matcher. Earlier failed runs remain failures; the successful measurements above use the same fixture and heap limit. Reusing the matcher addressed native allocation accumulation but left the repeated full-input region cost; the later v12 table records the line-window correction.
- Tiny windows originally could not advance; bounded nonsearchable continuation space now permits progress with at most five frames. Boundary serialization/reflow now preserves actual positions, including short chapters.
- Native WebView did not deliver the expected subframe navigation callback. A real native tap changed the child URL while leaving the model stale. The trusted outer shell now captures real same-origin book-link clicks and exposes a bounded one-shot route to native polling; synthetic/external/top-target routes are excluded. Book scripts remain disabled and no JS bridge is added. Repeated unchanged position polls are deduplicated so progress saving still settles.
- Find initialization starts held until native acknowledges the live Find state; readiness retries prevent an early open request from being lost. The final reviewer approved this repair.
- Native test corrections retain existing behavior assertions: read WebView properties on the UI thread; wait for the target chapter payload, current document/layout and replacement view; drain Compose test-clock work after direct native WebView actions and recheck readiness within the original deadline; clear completed native-find state before an unrelated H02 link tap; wait for visible hit-testable coordinates and a committed visual frame before one real input gesture; restore explicit legacy mode in old chapter-page suites. A screenshot-flag run intentionally refused V8 capture on a nonempty shelf; rerunning without capture passes its11 cases.

## Native screenshots

Generated documents only, visually inspected. The boundary capture shows the previous chapter tail, dotted separator and next chapter heading. The TOC capture shows the held native drag bubble with original chapter51 after filtering100 chapters to50 rows.

| Continuous boundary | Filtered TOC while dragging |
|---|---|
| ![](../screenshots/v08-continuous-boundary.png) | ![](../screenshots/v08-toc-fast-scroll.png) |

## Known limits

Find pauses automatic chapter additions until closed. Vertical EPUBs remain normalized to horizontal reading. Late large images compensate layout once dimensions resolve. Five short chapters may leave continuation space at the bounded window end. Full-book UI entry and routing to a real nth search hit are tested, but the new suite selects that hit through the ViewModel rather than tapping its result row. Native frame-count checks sample readiness/scroll checkpoints; the production-shell model also checks every insertion. No physical-device speed, layout or acceptance claim is made.
