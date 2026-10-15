# v0.2.0: signed release physical-device acceptance

Date: 2026-10-15 (Asia/Taipei).

## Device and artifact

- Redmi 23049RAD8C, Android API 35, 1080×2400.
- System WebView: `com.google.android.webview` 153.0.8010.39.
- Officially signed R8 APK: `io.github.zer0rin.yhviewer`, versionName `0.2.0`, versionCode `3`.
- Signing certificate SHA-256: `7d04d1564661330df2029624151d50826c3bcc0ada217d77b5889ae411ef1794` (RSA 4096).
- Local pre-publication APK SHA-256: `9d3cb83c293699c5d10c986d69b81e6b773b80f21ed7da9dde657df202c5abe9`. CI rebuilds the artifact; use its attached SHA256SUMS for the published download.
- Signature verification passed; the final APK declares no INTERNET permission.

Neither the development nor official package was installed on this phone before acceptance. No app was uninstalled or had its data cleared. The user confirmed signing-key/password backup; four repository Secrets were configured without printing or reading back their values. The official APK's 45 non-META-INF ZIP entries matched the previously smoke-tested temporary-key build.

## Synthetic fixtures only

At the user's request, no pre-existing phone document was opened, imported or modified. Fixture operations used a newly created `Download/YHViewer-release-check-20261015/` directory and the new application's private shelf. Sources came from project acceptance samples: HTML/Markdown pages, generated Kotlin, a fictional GBK novel, a small fictional EPUB and a generated injection sentinel.

A phone-specific sandbox variant directs file/content URL attempts at the new directory's own `hello.html`. This variant passed 9/9 on opening and reopening. Only the synthetic TXT source was deleted for the private-copy check. Screenshots and raw device logs remain in ignored local build output, not in this public repository.

## Observations

| Area | Evidence |
|---|---|
| R8 startup | Official package rendered the native shelf |
| HTML | JS-normal indicator and counter button worked; no Aa menu |
| Markdown/themes | Heading, table and code rendered; all four theme choices operable |
| Injection sentinel | Raw script and closing-script payloads appeared literally; heading and safe-end marker remained visible |
| Code | Source and line numbers rendered with Terminal highlighting |
| TXT | GBK decoded correctly; chapter navigation worked |
| EPUB | Embedded illustration and body text visually confirmed; chapter navigation worked; a script that would replace the body did not execute |
| Reading state | EPUB reopened at chapter two after backgrounding and process stop |
| Private copy | TXT reopened at chapter two with body text intact after process stop and deletion of only its synthetic original |
| Sandbox | Normal page and directory-isolated variant each passed 9/9 on opening/reopening; scripted redirect remained blocked |
| External links | Real tap triggered MIUI cross-app confirmation; a subsequent real tap successfully launched ChromeTabbedActivity. Automation did not select an always-allow option or change the default browser |
| Return | Explicit shelf control and sandbox-page back returned to shelf |

Native accessibility dumps occasionally needed retries. The small injection page rendered correctly but omitted WebView virtual accessibility nodes; its screenshot supplied the literal-text/end-marker evidence. Missing accessibility nodes were not treated as a blank-screen or security result. Detailed clipboard, rotation, pixel and database assertions remain in the existing instrumented suites; this smoke run is not a complete OEM compatibility matrix.

## Tests and publication

The count remains **76 JVM + 43 instrumented tests**. The [tested code revision's CI](https://github.com/Zer0Rin/yhviewer/actions/runs/38737324211) passed with 43/43 device cases on API 34; API 37 results and the optimizer correction are in [release preparation](RELEASE_PREPARATION.md). These physical checkpoints do not add new test cases to that count.

The code tested here is `eb27e832688dac482ac87946efc8ec952076b57e`; the follow-up changes release documentation only. The tag workflow must still pass unit tests, APK signature/version/permission gates and an API 34 optimized-startup check before publishing APK + SHA256SUMS.

See [release notes](../releases/v0.2.0.md) and [release guide](../../RELEASE.md).
