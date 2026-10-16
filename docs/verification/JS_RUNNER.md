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

The dedicated API 37 emulator passed all 6 runner engine tests and all 3 runner UI tests. Checks cover awaited/delayed console output, circular objects/Map/Set, source escaping, syntax/runtime/rejection errors, Unicode, infinite-loop timeout, normal/forged/clear/character floods, real CSP denial of fetch/importScripts/WebSocket/XHR/file/content, fresh IndexedDB/CacheStorage, live themes, stop/restart/close, document replacement, chrome://crash and an allocation bomb without app-process death. Full-suite and API 34 results are recorded below when complete.

An independent broad source review found no P0/P1/P2 blocking issue. No formal v0.3.0 tag or release was created.
