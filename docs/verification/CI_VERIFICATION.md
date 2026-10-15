# GitHub Actions verification

Date: 2026-10-15. Tested source commit: `797d2f5`.

Successful run: https://github.com/Zer0Rin/yhviewer/actions/runs/38694146788

## Configuration

- JDK 25, matching the Gradle daemon toolchain; Ubuntu 24.04.
- Compile SDK package: `platforms;android-37.0`; application compile/target API remains 37.
- Build/unit/lint job: pushes, pull requests and manual dispatch.
- Device job: pushes to main and manual dispatch; API 34, Google APIs, x86_64, KVM.
- Actions are pinned to commit SHAs. Token permission is `contents: read`.
- Reports and debug APK retained as artifacts for 14 days.

The proposed `ci.yml` was not present in the Downloads directory or project. The workflow was written from the supplied job requirements and checked with actionlint 1.7.12 before pushing.

## Results

| Check | Result |
|---|---|
| JVM tests | 15 passed, zero failures/errors/skips; downloaded XML checked |
| Lint | Zero errors, 17 warnings; downloaded XML checked |
| Debug build | Successful; debug APK artifact downloaded |
| Device tests | 25 passed, zero failures/errors/skips on API 34; downloaded XML checked |
| APK permissions | `aapt2 dump permissions` confirms no `INTERNET` permission |
| Artifact uploads | `unit-and-lint-reports`, `debug-apk`, `device-test-reports-api34` |

Device suite breakdown: ExampleInstrumentedTest 1, MarkdownRenderingIntegrationTest 2, ReaderIntegrationTest 3, SandboxIntegrationTest 3, ShelfDaoTest 7, ShelfRepositoryTest 7, ShelfUiTest 2. This is the existing 25-case suite, not 25 newly added tests. The normal no-network-permission APK was tested; this CI run does not repeat the temporary-INTERNET positive-control acceptance round documented in STEP3_VERIFICATION.md.

## First-run issue and correction

The initial run failed before any Gradle test ran:
https://github.com/Zer0Rin/yhviewer/actions/runs/38693982845

`sdkmanager "platforms;android-37"` reported package not found. Both the installed platform metadata and Google's SDK repository XML identify the published package as `platforms;android-37.0`. Correcting that package ID in both jobs resolved the failure without changing application SDK settings.

The successful device job emitted a non-fatal cache-save warning: the build job had reserved the same cache key. Both jobs and all tests still passed; a cold or unavailable cache only requires downloading dependencies again.
