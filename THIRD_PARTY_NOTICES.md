# Bundled third-party libraries

- markdown-it 14.1.0: MIT. License: app/src/main/assets/md/licenses/markdown-it-LICENSE.txt
- highlight.js 11.10.0: BSD-3-Clause. License: app/src/main/assets/md/licenses/highlight.js-LICENSE.txt

All runtime assets are packaged locally; the app does not fetch these libraries from a CDN.

| Asset | Download source | SHA-256 |
| --- | --- | --- |
| markdown-it.min.js | https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js | 38c70a1e7ca91ab40e2d9e6e60129851a717ed1c7d4acbbdd41bf9503791cf68 |
| highlight.min.js | https://cdn.jsdelivr.net/npm/@highlightjs/cdn-assets@11.10.0/highlight.min.js | 471ef9ae90c407af440fcdc48edfeeb562106b3267bd12d99071c162fb52ed32 |

## CodeMirror editor

CodeMirror 6 and its bundled runtime dependencies use MIT licenses. Exact versions
are locked in [tools/editor/package-lock.json](tools/editor/package-lock.json);
the full notices for all 30 bundled packages are distributed in
[app/src/main/assets/edit/LICENSES.txt](app/src/main/assets/edit/LICENSES.txt).
The reproducible local build is described in [tools/editor/README.md](tools/editor/README.md).

## Python runtime (Standard edition only)

Pyodide 0.26.4 is distributed under MPL-2.0; its bundled CPython 3.12.1 includes
the PSF license and incorporated notices. The unmodified upstream core files,
source URLs and SHA-256 hashes are recorded in
[tools/pyodide/provenance.json](tools/pyodide/provenance.json).
Full [Pyodide license](tools/pyodide/LICENSE.pyodide) and
[CPython notices](tools/pyodide/LICENSE.cpython) are also packaged with Standard.
See [runtime reproduction and compatibility](tools/pyodide/README.md).
Strict contains none of these Python runtime assets. Python packages are not
downloaded at runtime.

## Interface fonts

- Manrope variable font, Google Fonts `ofl/manrope`: SIL Open Font License 1.1.
  Font: `app/src/main/res/font/manrope.ttf`; full license bundled in
  `app/src/main/assets/fonts/licenses/Manrope-OFL.txt`.

Unmodified upstream files are from https://github.com/google/fonts.

| Font | SHA-256 |
| --- | --- |
| manrope.ttf | 3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6 |

## Markdown footnotes

- markdown-it-footnote 4.0.0: MIT. Unmodified upstream browser bundle and license
  from the official npm artifact `https://registry.npmjs.org/markdown-it-footnote/-/markdown-it-footnote-4.0.0.tgz`.
  License: `app/src/main/assets/md/licenses/markdown-it-footnote-LICENSE.txt`.
  Bundle: `app/src/main/assets/md/markdown-it-footnote.min.js`.
  SHA-256: `d6fee58a3b56c5742fa18f3e01f1d317cc99975683ebd39c9195cb2aff0c2e42`.
