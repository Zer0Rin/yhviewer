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

## Validation follow-up

- Initial API34 full CI39017645086: Strict118/119 and Standard127/128. Only new bottom-corner native pixel assertion failed; Python suite passed remotely.
- LocalAPI37 Standard full128:120passed, seven Python startup timeouts and one IME/back sequencing failure; all11 v8 scenarios passed. Investigate in isolation; no production timeout relaxation.
- [x] Restore full device runner from build/verification-v05/run-device-tests-full.sh after temporary diagnostic commit8764288 (CI39019093531). Require full CI before PR/merge.
