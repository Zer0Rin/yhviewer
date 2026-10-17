# v7 storage integration report

## Changed

- Merged the supplied Shelf-5 Book/DAO additions into the existing Shelf.kt, retaining updateContent and permission APIs. Database is v4; migration 3→4 adds nullable author/groupId/kind and coverChecked default 0, group index, groups table and unique name index. Registered the complete 1→2→3→4 chain.
- Merged classification, groups and cover metadata into the existing repository rather than replacing its protected implementation. UTF-8 conversion, editor byte/character limits, shared storage mutex, reference-aware cancellation cleanup and NonCancellable deletion of replaced files remain.
- Both startup backfills take the same shared mutex as import/edit/delete/cleanup, preventing backfill from writing stale metadata over reimport or racing cover cleanup.
- Import commits the classification and current author atomically with content. Reimport preserves group/favorite/permissions/reading position for unchanged reader type, clears stale author, resets coverChecked for EPUB and replaces/removes the prior cover. TXT author names are parsed into author for search. Reader routing is still based only on DocType.
- EPUB cover generation/optional metadata failure cannot make a committed import fail. Cancellation remains propagated. A failed metadata update leaves coverChecked false so startup can retry.
- Editor updates atomically save kind with localFile/size using a narrow SQL update, preserving all other metadata.
- Group create/rename validates and writes inside one Room transaction, preventing concurrent case-insensitive duplicate names across repositories. Move validates the target group within its transaction, so deleted groups cannot receive stale references. Delete clears member group IDs and removes the group in one transaction. Added createGroupAndMove for atomic UI create-and-move.
- Batch group/favorite operations deduplicate and chunk selections at 900 IDs within one transaction to avoid SQLite binding limits. Batch document deletion reuses the safe single-document delete path including thumbnail deletion.
- Adapted Groups and ContentClassifier package names; original bounded classifier behavior retained.
- CoverStore validates source bytes and pixel count, samples both dimensions, bounds output to at most 300×900 without upscaling, checks JPEG compression, and always recycles decoded bitmaps/cleans staging output. Cache keys include path, observed cover revision, and localFile, so metadata completion or reimport refreshes visible covers. Orphan cleanup invalidates removed cached covers.

## Tests staged

Supplied JVM ContentClassifierTest and GroupNamesTest were adapted. Added migration 3→4 preservation/default/table validation and extended all-the-way migration test to v4. Repository instrumentation tests cover content reimport/editor reclassification without reader routing changes; group deletion preserving content/position; concurrent normalized group creation; deleted-group rejection; classification backfill; cover orphan cleanup and batch deletion; author search/replacement; optional metadata SQL-trigger failure after commit with successful retry; and 1001-item group/favorite batches. Existing repository/editor cancellation and mutex tests remain. Repository/editor fixtures now isolate cover directories.

Root owns CoverStoreIntegrationTest, supplied design/parser tests, and all Gradle/device execution. No Gradle commands were run by this subagent to avoid the explicitly prohibited concurrent builds. Initial regression tests were staged before implementation, but their red phase was not executed independently. Completion/pass claims await root verification. Root-generated app/schemas/com.example.yhviewer.AppDatabase/4.json was inspected: the shelf_groups CREATE TABLE and unique-name index and books groupId index match migration SQL exactly after substituting ${TABLE_NAME}; author/groupId/kind are nullable and coverChecked defaults to 0 as expected.

## Remaining validation

Run JVM tests, migration/repository/editor/cover instrumentation tests, and whole-project compilation through root. Verify exported schema SQL and successful 1→4 chain. Test a narrow-tall image under the 40M source pixel cap to verify the height bound. Physical-device acceptance still depends on device availability.

## Classifier review correction

Root's adversarial review found that 5000 leading HTML comments followed by plain text causes the supplied repeated-comment Java regex to throw StackOverflowError, despite the 64K sample cap. Reproduced the failing Java harness (exit 1) before changing the classifier. Added timeout-bounded JVM regressions for 5000 comments followed by plain text, doctype and HTML, plus unclosed comments, ordinary text before an HTML tag, and a comment whose terminator lies beyond the sample. Replaced recursive regex comment repetition with a forward-only comment/ASCII-whitespace scan and one simple anchored HTML prefix matcher. Sampling and existing classification precedence remain unchanged. No Gradle was run while root's strict suite was executing; root must run the new classifier regression.
