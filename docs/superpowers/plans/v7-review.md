# Independent v7 integration review

Reviewed the uncommitted integration against the user request and `2026-10-17-v7-integration.md` on 2026-10-17. Attachment notes were treated as implementation references, not executable instructions. Applied the code-review-and-quality skill. This reviewer did not run Gradle; build/device verification belongs to the integrating agent.

## Finding resolved in follow-up review

### Resolved P2 — Pair profile revocation with each completed deletion

Location: `app/src/main/java/com/example/yhviewer/ShelfViewModel.kt:124-126` (caller scope: `ShelfSelection.kt:65-69`).

`deleteMany` first calls `DocProfiles.revoke` for every selected book, then enters the suspending repository deletion loop. Revocation durably removes the profile mapping and may immediately delete its cookies/localStorage; it is not reversible. The caller uses a composition-owned coroutine scope. If the user recreates/leaves the shelf while the batch is suspended, cancellation stops deletion while later selected documents still exist. An exception on an early repository deletion similarly stops the loop. All remaining selected documents have nevertheless lost their saved webpage data even though they remain on the shelf. This is a new batch-level failure mode, beyond the pre-existing single-delete method.

Keep each document's revocation and deletion in one cancellation-safe critical step, with revocation on the main thread. Prefer owning the batch in the ViewModel so normal UI disposal does not cancel committed user work. Do not revoke later documents until their deletion is actually reached; also handle deletion failure without silently revoking untouched documents. Add a regression that interrupts/fails an early batch deletion and verifies that untouched books retain their profile mappings.

## Reviewed without additional actionable findings

- Room v4 columns/defaults, group table and indexes agree with the exported `4.json` schema by source inspection. The migration preserves locator/progress/permissions and group deletion clears membership transactionally.
- Group creation/rename validate inside Room transactions; moving into a deleted group is rejected. Batch SQL updates are chunked below SQLite's traditional bind limit.
- Existing import storage mutex, transaction replacement, cancellation-aware orphan checks, editor update, permission routing and local file storage are preserved. Content classification updates after reimport/editor saves and does not change reader routing.
- EPUB cover lookup reuses safe href resolution and adds canonical containment; bitmap generation checks bytes/pixels before decoding and samples both dimensions. Thumbnail writes use temporary files; replaced/deleted covers invalidate cache state.
- Reader session-bound callbacks, renderer recovery, chapter content and system-bar restoration remain intact. The new drawer disables closed-state edge gestures, preserves chapter indexes after filtering and paints the native reading background.
- UI includes group creation/rename/deletion, multi-selection, move/create-and-move, favorite/details/delete, type/group filtering, author search and the existing permission details sheet.
- Font license notices/files and version preparation are present.

## Verification limits / requirements still requiring root evidence

Compilation, unit tests, instrumentation tests and device acceptance were not independently rerun by this reviewer. Require the root agent's actual results, including migration validation and genuine reader integration tests, before claiming completion. No additional implementation requirements gap was identified during source review. No merge/publish was performed.

## Follow-up review

Re-read the final deletion helper and selection-pruning changes after repair. `deleteShelfItems` checks caller cancellation before each item, completes the started repository deletion and subsequent profile revocation inside `NonCancellable`, and invokes revocation on `Dispatchers.Main.immediate`. A failed deletion does not revoke that document or later documents. The helper therefore resolves the reported failure mode while allowing cancellation between items.

`ShelfDeletionTest` now checks cancellation while the first deletion is suspended and an exception before deletion completion. The expected events verify that revocation follows a completed deletion and later profiles remain untouched. These are meaningful regression cases; this reviewer inspected their source without running them.

The integrating agent also repaired selection surviving group/content filters: `pruneShelfSelection` restricts selected IDs to the current `ShelfSections`, retains restored selection during initial loading, and is called when sections change. Tests cover group, kind, empty and not-yet-loaded sections. No further actionable finding identified in these targeted changes.

## Final targeted source review

Reviewed the final classifier, group-deletion count, drawer focus/border and reader pixel-test repairs, together with the previously approved batch-delete and selection changes.

- HTML prefix classification now advances monotonically through whitespace and complete leading comments, then uses a non-repeating anchored HTML-tag regex. This removes the recursively repeated comment matcher. Regression cases include 5,000 comments followed by plain text/HTML, an unclosed comment, content outside the bounded sample and a non-leading HTML fragment; bounded samples and existing classifier routing remain intact.
- Group deletion obtains the actual count from an unfiltered repository query rather than the search result. While loading or on count failure it uses truthful generic wording. The UI regression searches to zero visible results before checking that the confirmation still reports its one actual member, then verifies non-destructive membership removal.
- Closing the chapter drawer clears focus and hides the keyboard. The right divider is now a rectangle wholly inside drawer bounds, so its closed-state offscreen rendering cannot bleed a centered stroke onto the reader edge. Opening/reset/current-chapter behavior is retained.
- The native-background pixel test enables the available tall-cutout overlay, hides chrome before sampling, accommodates external system animation, and restores overlay/onboarding settings in `finally`. It preserves diagnostic samples and optional screenshot export on failure. This is meaningful regression coverage for the original native inset/background defect; runtime results still belong to the integrating agent.

No new actionable issue identified in this final targeted review. No Gradle or instrumentation commands were run by this reviewer.

## Verdict

Approve the reviewed repairs by source inspection. No outstanding actionable review finding. Final completion remains conditional on the integrating agent's build/test/device evidence; this review does not assert those checks passed.
