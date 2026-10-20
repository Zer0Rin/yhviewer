# v0.8 continuous-reading acceptance

The v11 candidate separates two kinds of native coverage: existing per-chapter regressions explicitly save/set/restore `continuous=false`; new continuous tests retain the new default. A temporary absence of WebView during asynchronous chapter replacement is not readiness. Tests must match the current chapter DOM and current live view without relaxing behavior assertions or timeouts. Direct native WebView scrolling does not itself drive the Compose test clock: the helper advances queued Compose work, then rechecks the same target shell and loaded frames within one original readiness deadline. See Android’s [test synchronization guidance](https://developer.android.com/develop/ui/compose/testing/synchronization).

`V11FindCompatibilityTest` is a small native API capability probe: native `findAllAsync` must find text in a same-origin sandboxed iframe while book scripts remain blocked. Its pass does not establish the actual app shell, hold, native count or nth-result behavior; those require the continuous acceptance suite.

`node scripts/test-flow-shell.cjs` executes the production shell with a deterministic DOM/layout model. It covers short-chapter traversal, the five-frame cap, stable settled requests, live font/theme anchoring and persisted boundary offsets. It complements native layout tests and is run by CI; model geometry is not Android timing evidence.

Performance acceptance generates its own roughly 30 MiB, 4000-chapter TXT below the existing 32 MiB import limit. Import, indexed opening, legacy-index rebuild and the following indexed opening are measured with a monotonic clock. Results are observations on the named emulator, without a fixed latency pass threshold. Source chapter count/content and unchanged index metadata must establish that indexed opening used the intended path.

The original 30-file external pack stays outside Git. Its T05 file is an opt-in local case, recorded separately from generated CI fixtures; exclusion does not count as a pass. Copy/bootstrap instructions are in [v0.7 acceptance](v10-acceptance.md).

Native boundary and fast-scroll bubble screenshots must contain generated documents only. Captures and actual native/performance outcomes are recorded in [v0.8 verification](../verification/V08_VERIFICATION.md).

The initial v11 task opened PR #8 only. Later user-authorized signed TEST prereleases and the v12 same-fixture comparison are recorded in the verification report; no physical-phone operation, main merge or stable v0.8 publication.
