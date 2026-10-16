# Native showcase captures

Use only a dedicated disposable Standard-edition emulator. The fixture creates
fictional documents, captures the real editor, Python console and permission UI,
and deletes only its own documents and shelf rows. It never uses a phone's files.

Temporarily copy `ShowcaseCaptureTest.kt` into the app's androidTest source folder,
build and install the Standard debug APK and test APK on the explicit emulator
serial, and run `com.example.yhviewer.ShowcaseCaptureTest` with `am instrument`.
Pull the three PNGs from the app's external `files/showcase/` directory into
`docs/screenshots/`, inspect them, then remove the temporary test source and rebuild.
This capture fixture is outside the normal CI test suite.
