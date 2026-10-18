# v0.7 native acceptance

`V10ExternalPackAcceptanceTest` uses a DocumentsProvider installed only in the instrumentation APK. Provider bootstrap temporarily adopts the shell document-picker permission only around initial setup and immediately drops it. The provider then gives temporary read grants for two test trees; it never enters the release APK. Generic fixtures are created by the provider and test cleanup deletes only rows whose source belongs to this provider.

Normal runs execute generated-fixture SAF/import/resource/navigation/edit, multi-share, find/dialog, bookmark/footnote/SVG and bundle-reimport regressions. The `externalThirtyFixturesImportAndFindEveryTailMarker` test is opt-in and skipped unless instrumentation receives `-e v10ExternalPack true`; the original supplied fixture data is kept outside the public repository. Its absence is explicitly reported, never counted as an external-pack pass.

To reproduce the local30-case run, build/install the Strict or Standard debug application and its matching instrumentation APK on a dedicated emulator. Always specify the emulator serial; never use the connected phone. Copy the unpacked supplied directory (not the zip itself) into the test APK's private fixture directory. This avoids shell-owned scoped-storage files:

```bash
adb -s emulator-5556 shell run-as io.github.zer0rin.yhviewer.test mkdir -p files/v10-pack
tar -cf - -C "/path/to/unpacked/Android书架测试文件_30例" . | \
  adb -s emulator-5556 exec-in run-as io.github.zer0rin.yhviewer.test tar -xf - -C files/v10-pack
```

The directory must directly contain `测试清单.csv`, `01_HTML/`, …, `06_其他格式/`. Then run:

```bash
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.example.yhviewer.V10ExternalPackAcceptanceTest \
  -e v10ExternalPack true \
  -e v7Screenshots true \
  io.github.zer0rin.yhviewer.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshot export is optional and contains only supplied synthetic documents. Captures are written to Download/YHViewerV7Screenshots. CI without the private local pack exercises the generated regressions; the verification report records the actual local pack outcome separately. Native tests use WebView find callbacks even for script-disabled EPUB chapters; SVG search is exercised in its explicitly selected source view. JSON01 has no END marker, so its tail `lastBook` property is searched instead.

Production signing, release publication, physical-phone installation and phone tests are not part of this PR request.
