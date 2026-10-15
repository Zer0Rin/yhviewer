# Step 5 shelf integration
Goal: integrate supplied shelf code, verify persistence/navigation/UI, retain offline sandbox and Markdown.

1. Requirements and dependency compatibility — complete
2. Integrate sources, Room/KSP, export schema; compile and run 18 supplied tests — complete
3. Verify shelf UI, source deletion/restart, rotation and back stack — complete
4. Run prior regressions, review, inspect final APK and deliver evidence — complete

Constraints: preserve YHViewerTheme and no INTERNET; no duplicate import on recreation; supplied tests isolate DAO/repository storage. Room 2 artifacts (androidx.room), not a Room 3 migration. No known planned exceptions.

Errors: one manual UiAutomator dump returned null root; resolved with screenshot and DB/private-copy snapshots. No build or test failures.
