# Renderer recovery follow-up

The supplied v5 review was checked against PR #1 before adding the JavaScript runner.

- A synthetic `chrome://crash` test reproduced application process termination in the original build. With recovery handling, the same process remains alive, the reader shows an error, and reopening creates a working WebView. Stale document-session errors are ignored.
- Each affected WebView removes/destroys its own instance and releases its session once. Scroll restoration and delayed gesture work are invalidated before destruction.
- EPUB permissions are fixed to Strict before profile attachment, including callers supplying Trusted/storage grants. A native regression checks the ephemeral policy and a BOM-marked UTF-16 chapter response.
- XML/chapters are decoded before entity checks. New JVM tests reproduce UTF-16 metadata parsing failure and an entity declaration missed by the old UTF-8-only check; both now pass. A character-stream XML parser avoids reinterpreting the decoded document according to its original declaration.
- Profile mutations reject non-main threads before changing preferences/WebKit. Failed attachment destroys the unreturned view and rolls back only newly allocated storage state. Startup clears old default-profile storage. Read-only feature queries remain callable off main.
- README explains that inline/eval scripts in the Standard preset are intentional capability grants, not an XSS defense.

Local validation on 2026-10-16 used only generated documents on the dedicated API 37 emulator (`emulator-5556`): 119 JVM tests per edition; Strict 55/55 and Standard 57/57 device tests, no failures/skips. Both Lint, debug and optimized release builds pass. Independent scoped review found no Critical/Important issues. [API 34 CI revalidation](https://github.com/Zer0Rin/yhviewer/actions/runs/38852034866) also passed: Strict 55/55 and Standard 57/57, with no failures/errors/skips. This record covers PR #1; JS runner and later physical-device validation are recorded separately. No formal v0.3.0 release was made.
