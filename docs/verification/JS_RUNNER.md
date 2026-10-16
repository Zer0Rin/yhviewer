# JavaScript runner verification

The v5 runner is separate from the document reader. All fixtures were generated for this verification; existing phone documents and the installed production app were excluded.

## Implementation

- A source snapshot runs only after the user presses Run, in an ephemeral WebView and a blob Worker. The native runner CSP is fixed and does not inherit document permission grants.
- Parent-side checks enforce the time limit, message budget and cumulative output limits, including forged messages and console clearing. Terminal diagnostics cannot be erased by queued messages. Source/output are embedded safely and rendered with textContent.
- Stop, restart, timeout changes, close and document replacement dispose the execution view. The close test waits for the dialog disposal frame rather than asserting during its dismissal.
- Renderer failure is handled per affected WebView. The runner sheet remains visible even if the reader sharing a renderer fails; restart creates a fresh environment.
- Code remains verbatim, including valid Unicode line-separator characters inside strings. Error mapping accounts for the wrapper and JavaScript line terminators.

## Local checks (2026-10-16)

Both editions: **127 JVM tests**, zero failures/errors/skips; debug Lint, debug APK, optimized R8 release APK and instrumentation compilation passed. Final release APK inspection confirms Strict has no INTERNET permission and Standard has INTERNET.

The dedicated API 37 emulator passed all 6 runner engine tests and all 4 runner UI tests. Checks cover awaited/delayed console output, circular objects/Map/Set, source escaping, syntax/runtime/rejection errors, Unicode, infinite-loop timeout, normal/forged/clear/character floods, real CSP denial of fetch/importScripts/WebSocket/XHR/file/content, fresh IndexedDB/CacheStorage, live themes, stop/restart/close, document replacement, chrome://crash and an allocation bomb without app-process death. The full API 37 suite passed **Strict 65/65** and **Standard 67/67**, with no failures or skipped tests. Both optimized R8 APKs, signed with the local development certificate only for this disposable emulator check, also rendered the native shelf and kept the app process alive.

An independent broad source review found no P0/P1/P2 blocking issue. No formal v0.3.0 tag or release was created.

## Physical API 35 device

On the connected Xiaomi/Redmi 23049RAD8C, WebView 153.0.8010.39, an independent **io.github.zer0rin.yhviewer.qa** debug application ran the 6 engine, 4 UI and 3 recovery checks: **Standard 13/13, Strict 13/13**, no failures/skips. The allocation-bomb assertion accepts controlled Worker failure/timeout or a recovered renderer crash; the separate chrome://crash assertion explicitly verifies the native crash message and rerun path. These results do not claim that every allocation attempt triggers an OS renderer kill.

The device initially rejected the test process’s activity launch (START_ABORTED / MIUIOP 10021). A temporary QA-only allow enabled instrumentation; the original ignore setting was restored afterward. Synthetic MediaStore documents were created under Download/YHViewerV5Tests and each created URI/book row was removed by test cleanup. No pre-existing phone document was read, changed or deleted. The installed production package remained **v0.2.0 / code 3**; its storage, permissions and signature were not changed. The independent Standard QA app remains installed; its instrumentation package was removed after testing.

Physical checks used debug QA builds rather than the official release signing key. R8 startup checks above used only the disposable emulator.

## R8 runner smoke

The optimized Standard APK also opened a newly created JavaScript document via content URI, displayed the Run action, and completed console output, array mapping, object formatting and top-level await in the native run sheet. Screenshot: [JS runner](../screenshots/js-runner.png).

## API 34 CI follow-up

The first [runner CI execution](https://github.com/Zer0Rin/yhviewer/actions/runs/38870211423) passed build/unit/Lint but failed only the allocation-bomb test in each edition (63/64 and 65/66 passed). Per-test Logcat confirms both shared WebViews received renderer-gone callbacks and the app survived. The test submitted evaluateJavascript just before renderer death, then failed waiting for a callback the dead renderer could not deliver. The check now polls terminal Worker state asynchronously while continuing to check the native crash message. It still requires a terminal outcome, unchanged app PID and usable Close action; no production recovery condition was relaxed.

The [second API 34 execution](https://github.com/Zer0Rin/yhviewer/actions/runs/38871832290) passed both jobs at code revision `065912e`: Strict 64/64 and Standard 66/66, zero failures/errors/skips. The allocation bomb actually crashed the shared renderer on this engine; both WebViews recovered and the test recorded `renderer-crash; app PID unchanged`. API 35 and API 37 instead recorded controlled timeout for that allocation workload.

## AUTO theme follow-up

The native screenshot exposed a dialog-context discrepancy: a light Android configuration produced a dark `prefers-color-scheme` inside the bottom-sheet WebView. A new real-dialog test reproduced the wrong computed background before the fix. RunSheet now resolves AUTO from Compose’s Android configuration **before** entering the dialog, passing an explicit Paper/Ink theme to the page. The saved choice remains AUTO. The test checks actual backgrounds for light/dark configuration and Paper/Ink/Terminal/AUTO, retains the same WebView and a JavaScript marker referencing the completed result, and therefore detects accidental reexecution during theme changes. Both edition builds, JVM/Lint/R8 checks and physical API 35 13/13 checks passed after this fix.

The [final API 34 execution](https://github.com/Zer0Rin/yhviewer/actions/runs/38873604547) passed both jobs at source revision `ae219e6`: **Strict 65/65, Standard 67/67**, zero failures/errors/skips in the downloaded JUnit reports. This includes the actual-color AUTO theme regression. In this run the allocation bomb timed out in Strict and crashed the renderer in Standard; both retained the app PID and a usable native Close action.
