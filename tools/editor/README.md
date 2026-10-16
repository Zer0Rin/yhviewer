# Offline editor assets

From this directory, run `npm ci && npm run build` with Node.js. The exact dependency
versions are in `package-lock.json`; the bundled CodeMirror 6 editor and full runtime
MIT license texts are committed in `app/src/main/assets/edit/`. Android builds do
not need Node or npm. Run `npm audit` when updating dependencies and review the
regenerated bundle and licenses together.

The build uses esbuild 0.28.2 and targets Chromium 80 syntax. The editor loads only
bundled assets under a fixed CSP, treats document text as JSON data, and exposes
only a page API queried by native `evaluateJavascript`. No native bridge is added.

The 1,000,000 UTF-16 character limit applies to initial content and later edits.
LF is the only split character, preserving CRLF and standalone CR on round trips.
`markSaved(snapshot)` records the persisted snapshot rather than later keystrokes.
