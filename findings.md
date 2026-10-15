# Step 5 findings
- Existing project AGP 9.4.0 uses built-in Kotlin; compose plugin catalog 2.2.10.
- Google Maven stable androidx.room is 2.8.5; lifecycle-viewmodel-compose 2.11.0. Maven Central KSP stable 2.3.12.
- Context7 KSP source confirms KSP2 version independent of Kotlin compiler, with AGP 9 support; no KSP1.
- Supplied DAO/repository tests are seven each, JVM LIKE tests four. Need additional process-restart/UI evidence.
- Supplied MainActivity moves ReaderState to ShelfViewModel; preserve theme wrapper.
- Existing ReaderIntegrationTest expects Activity reuse and Markdown rendering and remains relevant, now imports persistent local copies.

- Room 2.8.5 + KSP2 2.3.12 + lifecycle-viewmodel-compose 2.11.0 compiled successfully; no Kotlin compiler migration needed. Schema generated at app/schemas/com.example.yhviewer.AppDatabase/1.json.
- No Git repository: schema saved, no commit performed.

Final review and acceptance: all supplied 18 new tests passed, all normal suites 15 JVM+25 device passed. Dedicated original deleted after force-stop, cold-started shelf still opened byte-identical private copy. Git schema commit unavailable (no repository); v1 JSON saved.

Portfolio audit: the 40-test total includes generated ExampleUnitTest and ExampleInstrumentedTest. The temporary INTERNET positive-control run supports WebView sandbox behavior without the OS permission safety net; it does not establish all layers independently block all attacks. Screenshot fixtures and staged recency are explicitly disclosed in SCREENSHOTS.md.
