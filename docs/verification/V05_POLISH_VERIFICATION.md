# v0.5.0 screenshot-review follow-up

Date:2026-10-17. Source branch:`codex/v05-review-polish`; production source:`c28b3b220433793932e3c7ddc1d7787d4b227ced`. Version stays0.5.0/code6; Room schema4 unchanged.

## Changes and review

FloatingCapsule uses the fully opaque theme surface, preventing document text from overlapping chapter/selection actions. ContinueCard displays the saved chapter below title/author, using the canonical Locator decoder and Long addition to avoid an invalid Int wrap. Existing card growth and large-font button accessibility remain intact. The terminal fixture capture verifies chapter controls, hides reader chrome through the normal center-tap gesture, waits for chrome disappearance/Compose idle, then captures the complete chapter heading.

Independent code review approved the source/test diff with no blocking findings. The stale opacity comment was corrected. No storage, security-routing, permission or runtime changes were introduced.

## Verification

Both editions' debug and instrumentation APKs compiled. JVM reports confirm188 tests per edition, zero failures/errors/skips. Existing acceptance was extended to assert chapter13 on the authorless continue card and on its long-title/author/font-scale2 variation. Before the production fix it failed on the missing chapter label (`polish-red.log`); after the fix, all11 Strict V8AcceptanceTest scenarios passed in379.364seconds. Standard independently passed the three affected scenarios: authorless/large-font chapter label, generated shelf/multi-selection, and chapter controls/unobstructed terminal capture. Logs:polish-strict-acceptance.log and polish-standard-acceptance.log.

Four affected native screenshots were regenerated from fictional documents on dedicated emulator-5556: paper shelf, ink shelf, multi-selection and terminal reader. Visual inspection confirmed the saved chapter, opaque capsule and unobstructed heading. The chapter drawer and Markdown screenshots do not require replacement.

Only the dedicated API37 emulator is used for native tests. The previous API37 Python limitation and successful247-case API34 baseline are documented in [the integration report](V05_VERIFICATION.md); this narrow UI follow-up does not modify Python code or deadlines.

## Candidate and phone installation

[Manual signing run39035039846](https://github.com/Zer0Rin/yhviewer/actions/runs/39035039846) targets sourcec28b3b2, produces signed candidate artifacts and executes optimized API34 smoke checks. The workflow succeeded, including unit suites, optimized signed builds, final artifact checks and API34 startup smoke for both editions; Create GitHub Release was skipped. Its manual event cannot create a public Release. [PR#5](https://github.com/Zer0Rin/yhviewer/pull/5) contains the follow-up; GitHub state is the authoritative merge record.

User authorizes Standard overlay installation only: no launch, phone tests, uninstall, data clearing, tag or public release. Both downloaded APK checksums match the workflow SHA256SUMS. Standard's signing-certificate SHA256 is7d04d1564661330df2029624151d50826c3bcc0ada217d77b5889ae411ef1794, matching the installed0.4.0 package. Local verify-release.sh confirms the Standard package/version, signature, permissions and runtime assets.

ADB overlay installation succeeded on the authorized phone at21:16:24 Asia/Taipei. Package metadata confirms0.5.0/code6; pulling the installed base APK and comparing its SHA256 proves exact candidate bytes. First installation remains2026-10-15 21:12:55 and both user0 data-directory inodes are unchanged; stopped=true remains set. No launch, uninstall, clearing, phone test, tag or public Release was performed. Actual shelf/progress display and migration-on-launch remain for the user's own acceptance. Before/after metadata, verification and install logs are retained under ignored build/verification-v05/.
