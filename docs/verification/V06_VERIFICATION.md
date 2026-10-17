# v0.6.0 release verification

Date:2026-10-17 (Asia/Taipei). Version0.6.0/code7, applicationId io.github.zer0rin.yhviewer, Room schema4 unchanged. User explicitly confirmed the prior Standard candidate's physical-device acceptance and authorized public v0.6.0 publication with both editions and SHA256SUMS.txt.

## Scope and local verification

Integrated the supplied A eye/V foreground, background and monochrome vectors, both adaptive-icon XML resources, and docs/icon.svg. Deleted exactly10 legacy launcher WebP assets. README uses the supplied SVG; no reader, storage, permission or runtime code changed.

Both debug APKs compiled;188 JVM tests per edition passed with zero failures/errors/skips. Both Lint reports have zero errors and32 warnings. Unzipped both APKs: all three new drawable resources are present and no ic_launcher*.webp remains. Source XML parsed successfully; Manifest icon references remain unchanged.

Dedicated emulator-5556/API37 rendered the new icon in Android App info with a circular adaptive mask: both eye corners and V are intact. Native capture and local build evidence are retained under ignored build/verification-v06/. This verifies the platform-rendered colored icon; physical launchers, all mask settings and wallpaper-themed icon behavior were not retested by this agent. Foreground and monochrome geometry match, monochrome strokes are opaque and compiled resources reference it from both adaptive variants.

Independent code review approved the asset/version/release-workflow diff with no blocking findings. The release shell passes bash syntax validation. The workflow reads docs/releases/<tag>.md when present, preserving generated-note fallback for future versions. The v0.6.0 notes cover all previously unpublished v0.4/v0.5 features plus new icons.

## Publication evidence

PR and tag workflow links/state, signed APK checks, optimized API34 startup verification and published asset checks are authoritative in GitHub. Tag publication must follow successful PR checks. Previous functional visual verification remains in V05_VERIFICATION.md and V05_POLISH_VERIFICATION.md; this icon/version-only change does not rerun or claim new full functional/device acceptance.

No new physical-phone operation is part of this release task.
