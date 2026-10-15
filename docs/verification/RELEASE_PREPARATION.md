# Release preparation verification — 2026-10-15

## Scope and identity

Integrated the supplied README/release workflow/manual, with factual corrections to the testing and security claims. Prepared version **0.2.0 / code 3**, application ID **io.github.zer0rin.yhviewer**; namespace and Room schema namespace remain `com.example.yhviewer`.

The user selected the new application ID and temporary-key validation only. **No official key, GitHub Secrets, release tag or GitHub Release was created.** A repository secret-name listing was empty. Only an API 37 emulator was connected; real-device acceptance remains pending.

## Build and tests

- JDK 25, Gradle 9.6.0, AGP 9.4.0, SDK 37.0, build-tools 36.0.0.
- Unsigned release build passed with all signing variables absent.
- Signed R8 release built using a disposable local validation key.
- JVM XML results: **76 tests, 0 failures, 0 errors, 0 skipped**.
- API 37 debug instrumentation XML results after the ID change: **43 tests, 0 failures, 0 errors, 0 skipped**.
- Release lint: **0 errors, 20 warnings**; these warnings are not claimed fixed.
- `actionlint` v1.7.7 checked both workflows; Bash syntax check passed for the APK verifier.
- Device tests exercise debug builds. The separate release checks below exercise the optimized artifact; they are not 43 release instrumentation tests.

## Optimizer-only startup failure and correction

The first optimized APK crashed twice in AndroidX Startup before showing an Activity:

```text
IllegalAccessError: Class kotlin.collections.h extended by class yj is inaccessible
```

R8 mapping identified `kotlin.collections.CollectionsKt__IterablesKt` → `kotlin.collections.h`, and its subclass `CollectionsKt__IteratorsKt` → `yj`. The previous experimental `packageScope` configuration produced a repackaged subclass that could not access its superclass. Removing the partial scope, while retaining `optimization.enable = true`, produced an APK that cold-started, rendered the shelf and passed format smoke checks.

The release workflow now invokes `scripts/smoke-release.py` on an API 34 emulator before publishing. It installs without uninstalling existing data, cold-starts the app, checks native shelf rendering and verifies that the same process stays alive. Local execution used API 37; the actual tag-triggered workflow has **not** run yet.

Reference: [Android optimization documentation](https://developer.android.com/build/shrink-code) (new optimization DSL, experimental package scopes), [R8 repackaging](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

## APK publication gate

`scripts/verify-release.sh` checks the final artifact with `apksigner` and `aapt2`, not the source Manifest. All eight local checks produced their expected result:

| Input | Expected / observed |
|---|---|
| Valid signed APK, matching tag and application ID | Accepted |
| Wrong version tag | Rejected |
| Wrong application ID | Rejected |
| Malformed tag | Rejected |
| Missing APK | Rejected |
| Real unsigned APK | Rejected by signature verification |
| Real signed APK with binary Manifest declaring INTERNET | Rejected by permission gate |
| Partial signing environment | Gradle configuration rejected |

The network-negative fixture was built with `aapt2` and signed with the same temporary key; the test asserted that rejection reached the INTERNET check, rather than failing earlier on signature or version. These eight preparation scenarios are additional manual/script checks, not additions to the 119 Android/JVM test count.

Final APK package: `io.github.zer0rin.yhviewer`, versionName `0.2.0`, versionCode `3`. Signature verification passed. Its only uses-permission is the app's generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; **INTERNET is absent**.

Temporary-key APK SHA256: `6c1bb0f209561bb11415bc9cfa68c97bb6b4e1b75de38635f74ba821aa0b9b3f`. This is a local validation artifact, **not an official release or upgrade key**.

## R8 API 37 format smoke

- HTML JS button usable; no Aa menu
- Markdown rendered, four theme choices usable
- Code rendered with line numbers in Terminal; fresh code screenshot
- GBK TXT rendered, next chapter opens
- EPUB chapter content and next chapter rendered; fresh EPUB screenshot
- Release cold start reopens private EPUB at saved chapter
- Sandbox open 1: 9/9, scripted redirect blocked
- Sandbox open 2: 9/9, scripted redirect blocked
- Reader back returns to shelf

Code and EPUB screenshots were captured from this optimized APK at 1080×2400, using fictional documents. Native accessibility/UI checks confirmed rendering and chapter navigation. Earlier debug instrumentation supplies the detailed clipboard, image-pixel, script-sentinel and storage assertions; a popup not appearing alone is not treated as proof that a script did not execute.

The format smoke helper initially searched the shelf by EPUB filename; the shelf correctly displays the book metadata title, `山间手记`. Resuming with that title confirmed cold-start chapter restoration. An initial transient accessibility dump was retried. Neither was an application failure.

## Existing data

The previous `com.example.yhviewer` app was retained. Before/after snapshots matched all **7 database rows** and all **17 private content/preferences files** by SHA256. The new package is a separate sandbox; this is not a cross-package migration. Old data is not automatically copied into the new app.

## Remaining publication work

Create and back up an official signing key, configure the four repository Secrets, run the complete release smoke checklist on a real phone, and explicitly push a version tag when ready. The release workflow's GitHub publication path cannot be called verified until it actually runs. See [RELEASE.md](../../RELEASE.md).

Local diagnostic logs, temporary fixtures and scripts are under ignored `build/release-integration/`; no passwords or keystores are committed. The disposable keystore and password file were deleted after validation; the installed validation app contains only test documents. A future officially signed APK will have a different certificate, so use a dedicated device or preserve/export needed documents before replacing that validation install.
