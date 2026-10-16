# Bundled Python runtime

The Standard flavor contains official Pyodide **0.26.4**, CPython **3.12.1**,
Emscripten 3.1.58, wasm32 ABI 2024_0. Strict contains no Python assets.
Only the five core runtime files are packaged (13,780,118 bytes uncompressed).
No package wheels are bundled, no `loadPackagesFromImports` is invoked, and no
package downloads are allowed by the CSP. The lock file describes upstream
packages but does not include them.

Pinning the existing classic-worker release avoids newer Pyodide's module-worker
requirement. Pyodide 0.26's documented tested browser baseline is Chrome 112,
Firefox 112 and Safari 16.4. Android API level alone does not fix a WebView version;
the installed WebView must support that browser baseline and WebAssembly.

Sources, exact sizes, and SHA-256 checksums are in `provenance.json`. The upstream
core release is copied without modification. Retained licenses are MPL-2.0
(`LICENSE.pyodide`) and CPython's full PSF and incorporated notices (`LICENSE.cpython`).
Upstream source: https://github.com/pyodide/pyodide/tree/0.26.4 and
https://github.com/python/cpython/tree/v3.12.1.

Reproduce or check the assets from repository root:

```sh
python3 tools/pyodide/fetch.py
python3 tools/pyodide/fetch.py --verify
```

Run the real browser acceptance harness with an installed `playwright` package:

```sh
PLAYWRIGHT_MODULE=/absolute/path/to/node_modules/playwright node tools/pyodide/verify-runner.mjs
```

`CHROME_PATH` can override the executable (defaults to macOS Google Chrome).
The harness serves the real runner and the bundled WASM/stdlib with the Kotlin CSP
as an HTTP response header. Every test creates a clean browser context. External
requests fail the test; there are no runtime mocks or CSP bypasses.

Python execution uses a fresh Worker, cannot access the DOM or native bridges,
and its in-memory filesystem ends with the Worker. A private startup MessagePort
is closed before running user code; only that handshake starts the run budget.
The parent caps output and message processing even when Python calls raw
`js.postMessage`. Native WebView settings, asset interception, storage isolation,
and renderer crash recovery remain the host's responsibility.
