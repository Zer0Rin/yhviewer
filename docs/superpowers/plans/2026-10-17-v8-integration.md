# v8 integration plan

User authorizes applying supplied patch on codex/v7-shelf-reader at 2a64d95, compiling/testing, synthetic screenshots, PR and merge into main. Prior physical-device restriction persists: no phone launch, tests, data clearing, or installation for this task.

- [x] Verify clean base and apply 14-file patch with index.
- [x] Adopt suggested 0.5.0 / versionCode 6, retain database v4.
- [x] Review production changes and resolve correctness/spec gaps.
- [x] Build both editions; JVM tests and Lint; optimized APK permission/runtime checks.
- [ ] Run regression and acceptance only on dedicated emulator with explicit serial.
- [x] Capture and inspect six requested synthetic screenshots; update README.
- [ ] Record verification, commit, push, create/attach PR, wait for CI, merge main.

Reference: /Users/guyue/Downloads/INTEGRATION-7.md. Independent source review and acceptance fixture work delegated under subagent-driven-development skill.
