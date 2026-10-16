# Editor and Python integration (v0.3.0 candidate)

This records acceptance of the v6 integration, based on merged PR #2, on
2026-10-16. The user installed the officially signed Standard candidate over the
existing app and confirmed all eight shelf records and reading progress remained.
Public publication is performed separately by the tag-triggered release workflow.

## Assets and build

- CodeMirror 6 is built with `npm ci && npm run build` from the committed lockfile;
  repeat builds produced the same 738,924-byte bundle. Full MIT texts for all 30
  runtime dependencies are bundled. `npm audit` reported zero vulnerabilities.
- The real browser editor harness exercised all 25 configured language modes,
  CJK/emoji and CRLF/CR exact round trips, safe payload embedding, wrap/theme and
  undo preservation, saved-snapshot races, size limits and missing components.
- Standard bundles the unmodified Pyodide 0.26.4 core / CPython 3.12.1: five files,
  13,780,118 bytes uncompressed, plus full upstream licenses. `fetch.py --verify`
  checks each hash and the exact allowed file set, including both copies of notices.
- The final APK gate checks Strict has no Pyodide entries and Standard has all
  expected hashes/licenses. Synthetic leaked-Strict, missing-Standard and
  tampered-Standard archives were all rejected.
- Both editions passed 145 JVM tests, Lint and debug compilation. R8 release builds
  with a development validation signature passed version/applicationId, signing,
  final INTERNET permission and Python asset checks. That development signature
  is not the official release candidate signature.

## Problems found during acceptance

1. Native saves updated the copy but the editor's Compose coroutine/handle did not
   reliably complete saved-baseline handling. The editor now owns a retained
   native session and operation scope.
2. Rotation destroyed unsaved drafts, and external opens bypassed leave checks.
   Six regressions failed before the fix. The VM-owned application-context WebView
   retains the exact CodeMirror document and saved baseline across Activity
   recreation, without polling or a JS bridge. Incoming documents wait for
   Save/Discard/Cancel; same-source Save opens the saved copy instead of immediately
   reimporting and overwriting it. Teardown detaches native pulls and releases the
   ephemeral profile once. Process death and renderer failure do not retain drafts.
3. Real Pyodide batched output omitted `print(..., end='')`. Bounded streaming UTF-8
   writers now deliver partial stdout/stderr as writes happen; original Python
   stream flush methods are retained. A second regression proved a flushed partial
   line was lost before an infinite-loop timeout, then passed after delivery was
   made synchronous. The real CSP/browser suite passes all 23 scenarios, including
   standard library, await, Unicode, EOF, user line mapping, partial output before
   error/timeout, network denial, no-newline flood and hostile protocol messages.
4. `ActivityScenario.recreate()` tried to force a stopped Activity to RESUMED while
   the SAF picker owned the foreground. The harness now recreates the actual
   stopped Activity, observes the new instance and retained export snapshot, then
   cancels the picker. This was a test lifecycle problem, not a draft mismatch.
5. Real native toolbar touches found a zero-height percentage-sized editor host:
   overflowing CodeMirror content covered the visible bar and intercepted touches.
   The editor now synchronizes its root height to positive viewport pixels on
   initial/load/resize events and bounds child overflow. Native hit-target and
   IME-focus assertions cover the failure; CSS viewport units alone also resolved
   to zero on the retained native WebView and were insufficient.

## Scope of device acceptance

Tests use newly generated MediaStore documents and temporary/in-memory database
fixtures. Physical API 35 runs use a separate `io.github.zer0rin.yhviewer.qa` app;
no original phone documents or official application private data are read. The
phone's existing officially signed app remains separate until the user performs
the final upgrade check. Chinese composition tests use the actual Chromium input
connection; they do not stand in for every OEM keyboard's candidate UI.

Historical v0.2.0 results are not used as proof of this version's formal upgrade
acceptance. Current results and reproducible captures follow below.


## Final focused evidence

- API 37 native InputConnection / toolbar suite: 3/3 passed after waiting for the
  real resized native view before touch. The test acts as a second IME and uses
  `beginBatchEdit` / `endBatchEdit` to avoid the emulator's active Latin IME
  terminating its composition between exact intermediate assertions. Candidate
  replacement still checks `ni` → `你` → `你好`, then Unicode commit.
- API 35 physical device: all three native IME / toolbar tests passed. The user
  additionally used the actual Sogou candidate UI in a newly created QA document;
  the resulting `你好，世界(中文)` was inspected on screen.
- Eight lifecycle tests cover exact retained draft/baseline, incoming Save/Discard/
  Cancel and same-source Save, actual stopped-Activity recreation behind SAF,
  export snapshot retention, and final view/handle disposal.
- The initial complete local device rounds exposed two old expectations: the SAF
  harness incorrectly forced RESUMED, and a v5 UI assertion still expected Python
  to have no Run button in Standard. Both were corrected and independently rerun;
  fresh complete suites and CI subsequently passed.
- Editor, Python console and permissions PNGs are native API 37 captures of
  fictional documents. Capturing waits for the WebView visual-state fence and a
  visible draw instead of assuming a ready JS API means pixels were painted.


## Completed integration gates

| Check | Result |
|---|---|
| JVM tests | 145 per edition; zero failures/errors/skips |
| Lint and compilation | Both editions passed |
| API 37 complete native suites | Strict 93/93, Standard 102/102 |
| API 34 GitHub CI | Both complete native suites and build/unit/Lint jobs passed |
| Native API 35 editor input | 3/3; Sogou candidate entry additionally checked by the user |
| Real Pyodide browser/CSP harness | 23/23 |
| API 37 development-signed R8 | Native startup, JS output, real Python WASM/stdlib, editor input/pull decoding/atomic save passed |
| Official signed candidate | Both APKs passed signature, version, applicationId, final permission and exact Python asset/hash checks; API 34 optimized startup passed |
| Official API 35 upgrade | versionName 0.3.0 / versionCode 4; original first-install time retained; user confirmed eight records and progress remained |

Evidence: [complete API 34 CI](https://github.com/Zer0Rin/yhviewer/actions/runs/38912478875),
[PR checks](https://github.com/Zer0Rin/yhviewer/actions/runs/38912585005),
[official candidate build](https://github.com/Zer0Rin/yhviewer/actions/runs/38912555528),
and [PR #3](https://github.com/Zer0Rin/yhviewer/pull/3). Manual candidate dispatch
successfully skipped Create GitHub Release; it did not publish a release or tag.

Candidate source: `d1c827da43a692d289c3d893024bfae438dff04f`.
Standard SHA-256: `16fba6206142fa99639f3b62a6917564a35ee86f11b51792bc65f883c21eaf70`.
Strict SHA-256: `ef225f370fca707c4ffeaaf4175a428942fef35e9bef15198499377085f8c194`.
The candidate is separate from any subsequent tag-build APK; use each published
artifact's own SHA256SUMS rather than assuming identical build bytes.

The physical upgrade was human-verified. Automation read only package version/
installation-time metadata, not the user's official database or documents.
