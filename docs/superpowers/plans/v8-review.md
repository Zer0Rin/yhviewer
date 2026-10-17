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
