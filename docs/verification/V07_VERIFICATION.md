# v0.7.0 integration verification

Date:2026-10-18 (Asia/Taipei). Base main868b6b2/v0.6.0; source branch codex/v07-import-search. Version0.7.0/code8, Room schema5. User requests integration, compilation/test-pack acceptance and a PR; no merge, tag, release or physical-phone operation is authorized for this task.

## Integration and repairs

Applied supplied23-file v10 patch cleanly. Bundled markdown-it-footnote4.0.0 from official npm registry, MIT license and notice; actual markdown-it14.1.0 runtime smoke verifies superscript references and footnote body. Bundle SHA256:d6fee58a3b56c5742fa18f3e01f1d317cc99975683ebd39c9195cb2aff0c2e42.

Review and targeted regressions repaired old referenced bundle-file deletion during reimport, deterministic edit-temp name collision, unsafe bundle paths, provider-row/directory/cycle traversal limits, actual-byte copy budget and successful-resource reporting, rejected-file quota accounting and live Markdown bundle theme updates. Additional independent review required active search replay after page replacement and atomic bookmark dedup; both were corrected. All bookmark add feedback now comes from the transaction result, with distinct added/duplicate/failure states. Book add-bookmark moved into Aa settings to preserve narrow-toolbar space. Source commit8e897cb records these changes; independent task review approved spec compliance and code quality.

Both debug/application and instrumentation editions compile. Both JVM suites pass217 tests, zero failures/errors/skips. Generated schema5 table/index SQL exactly matches MIGRATION_4_5; books/shelf_groups definitions unchanged fromv4. A SQLite seed check preserves existing book metadata; native migration execution is recorded separately when complete. Final debug permission checks:Strict INTERNET=false, Standard INTERNET=true. Further CI/Lint/R8 evidence follows below when available.

## Native acceptance and fixtures

Only dedicated API37 emulator-5556 is operated; no connected phone is installed/launched/tested/cleared. Instrumentation-only DocumentsProvider gives temporary read access to generated fixtures and an optional private original30-case pack. The test-provider bootstrap briefly adopts MANAGE_DOCUMENTS (shell/picker permission) then immediately drops it; actual import/render checks use ordinary URI grants. Provider is pure Java because its separate test-APK process cannot borrow the target APK's Kotlin runtime. This fixture infrastructure never enters the production APK. Original pack data is not committed; reproduction is documented in ../testing/v10-acceptance.md.

Final Strict six-case class passed in31.047seconds, including the private real30 pack. Generic native acceptance passed folder resource/cross-page navigation/edit/report, single-page missing-resource/find replay/repeated-dialog blocking, multiple/single/share import, nth-result book search/bookmark drawer navigation/footnotes/SVG switching and referenced-old-bundle reimport checks. The initial real-pack attempt failed before reading due shell-owned scoped-storage files; fixtures now copy through run-as into test-app private files. Another probe issued find during SVG source replacement before page readiness; an explicit source-DOM readiness condition corrected this test-only race. Original assertions remain.

The real30-case run succeeded in13.955seconds:34 imported shelf items,2 resource files,0 failures/skips; every sample opens and native WebView find locates its tail marker (JSON01 has no END marker, so searches lastBook). SVG marker search uses explicitly selected source view. H02 external CSS and SVG image decode, all TXT encodings including the242nd split of the12000-line sample, five EPUB final chapters, E02 cover and E04 horizontal-only scroll normalization pass. Evidence:thirty-svg-ready.log and V10Pack logcat lines under ignored build/verification-v07/.

## Remaining evidence

Final paired acceptance, seeded migration, sandbox/permission regression, full API34 CI and whole-branch review are recorded here when complete. CI without the locally supplied pack intentionally skips its one opt-in external-pack method per edition, while generated regressions run normally; a skip is never reported as a30-case pass. Physical-device acceptance remains unperformed under the retained user restriction.
