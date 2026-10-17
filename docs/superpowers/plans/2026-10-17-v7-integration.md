# v7 integration plan

Goal: integrate the supplied v7 Android design into the current app without losing editor, encoding, sandbox or lifecycle protections.

Requirements: folder-style groups with validation and non-destructive deletion; long-press selection with move/favorite/details/delete; content classification independent of reader routing; retain recent-open sort; author search and safe EPUB thumbnails; reader-theme background and explicit-only chapter drawer. Room v3 to v4 must preserve existing data. Prepare v0.4.0, without publishing.

## Tasks
- [x] Integrate Book/DAO/schema migration and repository metadata, covers, classification, groups and batch operations. Preserve storage mutex, cancellation cleanup, editor updateContent, profiles, permissions and positions. Add migration/repository tests.
- [x] Integrate ShelfScreen, ShelfSelection, Groups, ContentClassifier, Covers, YHTheme and ViewModel APIs. Adopt supplied unit tests and update UI tests for long-press selection. Add licensed fonts.
- [x] Merge EPUB author/cover parsing and BookReader chapter drawer/theme changes into current files, preserving session IDs, renderer recovery and system bar restoration. Add EPUB and reader tests.
- [x] Build both flavors and unit/instrumentation APKs, validate exported schema, run available emulator/device integration tests and APK permission checks. Review diff and resolve findings. Record device limitations and verification evidence.

Implementation reference: /Users/guyue/Downloads/INTEGRATION-6.md (reference, not independent authority). Latest Shelf-5.kt/ShelfRepository-5.kt supersede -4 versions. ShelfScreen-2.kt is latest screen.

Verification complete for locally available environment; physical-device acceptance remains unavailable (no phone connected). See docs/verification/V04_VERIFICATION.md.
