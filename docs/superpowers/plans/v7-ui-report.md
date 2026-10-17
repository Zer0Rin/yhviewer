# v7 shelf UI integration

## Changes

- Adapted supplied ShelfScreen-2 into app package, added ShelfSelection and YHTheme and changed MainActivity to YHTheme.
- Preserved database recent-open ordering within book/document sections. No reader routing changes. Search stays in repository, now includes metadata authors from storage integration.
- Added folder scopes, content chips, group creation/rename/non-destructive deletion, long-press selection, select all/cancel, move/create-and-move, bulk favorites/delete and one-item details with existing DocumentSheet permission controls.
- Added Result-based suspended bulk/group ViewModel APIs. New-group-and-move uses storage agent's transactional createGroupAndMove. Failed operations keep dialogs open and display errors. Cancellation is rethrown. Bulk deletion revokes DocProfiles for every selected document before repository deletion.
- Startup cleanup still returns its exception to load/save flows. Backfills wait for successful cleanup and log recoverable failures without swallowing cancellation. Existing editor/session/permission/position code remains intact.
- Group dialog prevents duplicate submissions. Missing selected content kind remains visible as a zero-count chip so user can clear its filter.
- Await initial query/group loading before pruning restored selected IDs/group scope, preserving selection and active group across rotation.

## Tests

- Adopted supplied ShelfDesignTest first: palette/title/sample size/TOC/sections/scopes/category/theme tests.
- Updated ShelfUiTest favorites and delete interactions for long-press selection.
- Added group UI test: long press, selection survives recreate, create-and-move, active scope survives recreate, group deletion clears membership while preserving complete document record/source.
- Gradle intentionally not run by this agent: root owns serialized compilation/testing. Static diff inspection only; no passing build/test claims.

## Dependencies and limits

- YHTheme requires res/font/bodoni_moda.ttf and manrope.ttf supplied by root.
- Reader agent owns ChapterDrawer shared ProgressLine/UnderlineField/filterToc.
- Root should exercise ShelfUiTest and ShelfDesignTest after compilation, particularly Compose semantics matching on physical/emulated device.
- App retains original single-book toggleFavorite API for DocumentSheet. Bulk operation paths report failures; legacy permission mutation behavior unchanged.

## Review follow-up

- Reworked bulk deletion into per-item critical sections. Each started repository deletion and subsequent profile revocation completes under NonCancellable. Revocation runs on Main, after successful deletion; failures leave the failing and not-yet-started documents' profiles untouched. Cancellation stops before the next item. Existing shelfResult continues propagating cancellation and reporting other failures to dialogs.
- Added ShelfDeletionTest regression cases for cancellation during suspended deletion and repository deletion failure. These tests use coroutine barriers, not timing delays; root owns test execution.
- Reviewed other group operations: Room transactions protect create-and-move, rename, move and delete-group against partial writes on rotation cancellation. Those operations do not delete profile data. No extra noncancellable region added.
- Selection now intersects the actual visible sections on scope/category/search/data changes, retaining saved IDs only while initial data is unknown. Added pure tests for group/category/empty filtering and initial-load preservation.
- Added optional real-device screenshot export to the group/selection instrumentation test only when `v7Screenshots=true`: MediaStore Downloads/YHViewerV7Screenshots/light-shelf.png and selection.png. Normal suites create no screenshots. Captures the current system theme; root should set light mode before explicitly requesting the light screenshot.
- Device-test follow-up: disambiguated selection-header cancel by its content description and confirmation-dialog cancel via `hasAnyAncestor(isDialog())`; fixes the two-matching-nodes failure found in the Strict full device suite. Root owns rerun.
- Group-deletion confirmation now loads membership from an unfiltered repository observation via ViewModel, rather than the current search results. Loading/query failure uses accurate generic non-destructive wording; resolved counts show actual membership. Existing group lifecycle UI test now searches for no matches before deleting the populated group and asserts the confirmation still reports one member. No DAO/schema change.
- Device-test follow-up: explicitly close the search keyboard before sending Back to cancel selection after dismissing delete confirmation, then wait for the selection header to disappear before reentering selection. This distinguishes legitimate IME Back handling from selection Back handling; production behavior unchanged.
