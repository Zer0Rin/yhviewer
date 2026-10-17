# Independent v8 integration review

Reviewed the v8 patch against `2a64d95`, `/Users/guyue/Downloads/INTEGRATION-7.md`, and the full branch diff against `origin/main`. Applied the code-review-and-quality skill. Final follow-up reviewed the production repairs and contrast regression source.

## Findings resolved

- **P2 — Stale system-bar icon restoration:** the reader previously restored the appearance captured before a reading-session theme change. Disposal now uses `rememberUpdatedState(darkIcons)`, retaining the latest theme while restoring system-bar visibility and behavior.
- **P2 — Small-text contrast:** grid authors and progress labels used subtle colors below 4.5:1. Subtle now uses readable secondary colors. The follow-up also addresses Paper secondary text and Paper/Ink actions on surface backgrounds. Paper native muted/quote/link colors agree with shared CSS. Regression assertions require muted, subtle and action colors to reach 4.5:1 against both background and surface.
- **P2 — Continue-card clipping:** three-line titles plus author, progress and the continue action exceeded the fixed 150dp column. The column now uses `heightIn(min = 150.dp)` and can grow with its content.
- **P2 — Narrow selection-capsule actions:** sequential unweighted labels could consume the width before the final delete action. All four actions now receive equal weights, centered text and a minimum 48dp height, allowing labels to wrap while retaining every action.
- **Accessibility — Progress semantics:** `DotProgress` now exposes `ProgressBarRangeInfo` using the current clamped fraction, including merged grid cells with author text instead of a percentage.

## Reviewed scope

Unified native/reader themes, system bars, cover-wall sizing and last-row spacers, persisted grid/list preference, selection and bottom spacing, chapter/selection capsules, input dots and press animations, font removal, and v8 interactions with v7 storage and reader state were inspected. No additional concrete security or state-isolation defect was identified: classification remains separate from reader/security routing; cover containment and bounded decoding, selection pruning, and per-item deletion/profile revocation remain intact.

## Verification limits

This reviewer did not run builds, tests, screenshot capture, emulator commands or physical-device operations. The integrating agent reports red/green regressions for icon restoration, progress semantics, enlarged-font hero/action visibility and surface contrast, plus 188 passing JVM tests per flavor. Those runtime results and final compile, instrumentation, screenshots, navigation-mode acceptance, sandbox/permission checks and merge evidence belong to the integrating agent; this report does not independently certify them.

## Verdict

Approve the reviewed source repairs. No outstanding actionable review finding remains. Release and merge completion remain conditional on the integrating agent's required verification evidence.

## Final test and CI follow-up

Inspected test/CI changes against `df437ca`. The navigation pixel probe now filters the shelf to an empty result and explicitly shows system bars before sampling, avoiding cover-shadow contamination on hardware-key profiles without navigation insets. It retains exact RGB equality, the original 15-second wait, all three themes, shelf/Markdown coverage and icon assertions; failure diagnostics add inset, window and pixel evidence. CI requests software navigation with `qemu.hw.mainkeys=0`.

`scripts/run-device-tests.sh` has no final diff against `df437ca`: both complete Strict and Standard instrumentation suites run without class/method filters, and any failed suite preserves a failing exit status. Python changes add bounded diagnostic logging only; assertions and startup, execution and harness deadlines remain unchanged. No hidden test weakening or residual suite filter was identified.

The integrating agent reports that the same API37/WebView `145.0.7632.218` startup timeout occurs with the old `2a64d95` APK (61.626s) and current APK (61.971s), both without output/messages; isolated shelf acceptance and remote full Python tests pass. This supports a pre-existing environment/runtime limitation rather than a v8 source regression, without establishing its root cause. This reviewer inspected source only and did not independently run those checks. Final suite outcomes remain the integrating agent's responsibility.

## Small-screen selection follow-up

Reviewed the `V8AcceptanceTest` helper diff against `303dcad`. Selection now scrolls the vertical shelf list explicitly, matching both scroll-to-index capability and a vertical scroll range so the horizontal kind-filter row cannot be selected accidentally. After normal item scrolling, it moves the target center above the floating selection bar with 32dp clearance, checks positive bounds and a center inside the list viewport, then performs the click and requires the next selection count. The final three-item count and capsule assertions remain. No assertion bypass or blocking issue was identified; production code is unchanged by this repair.

The integrating agent reports that CI now passes navigation and all Python tests, reproduced the original selection failure on the local API37 320×640/density160 three-button profile, and observed the repaired focused Strict test pass in 25.198 seconds. These runtime results were not independently rerun by this reviewer; full final-suite verification remains with the integrating agent.
