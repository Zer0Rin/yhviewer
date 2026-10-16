// Rebuild the committed offline assets: npm ci && npm run build
import { build } from 'esbuild';
import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const assetDir = resolve(here, '../../app/src/main/assets/edit');
const outfile = resolve(assetDir, 'cm6.bundle.js');
const result = await build({
  absWorkingDir: here,
  entryPoints: ['src/editor-entry.js'],
  bundle: true,
  minify: true,
  format: 'iife',
  target: ['chrome80'],
  legalComments: 'eof',
  metafile: true,
  outfile,
});

// Include full license texts, including dependencies whose sources have no license comment.
const packages = new Set(Object.keys(result.metafile.inputs).filter(p => p.startsWith('node_modules/')).map(p => {
  const parts = p.slice('node_modules/'.length).split('/');
  return parts[0].startsWith('@') ? parts.slice(0, 2).join('/') : parts[0];
}));
const notices = [...packages].sort().map(name => {
  const dir = resolve(here, 'node_modules', name);
  const pkg = JSON.parse(readFileSync(resolve(dir, 'package.json'), 'utf8'));
  if (pkg.license !== 'MIT') throw new Error(`Review new dependency license: ${name} (${pkg.license})`);
  return `${name}@${pkg.version} — ${pkg.license}\n\n${readFileSync(resolve(dir, 'LICENSE'), 'utf8').trim()}\n`;
});
writeFileSync(resolve(assetDir, 'LICENSES.txt'), `YHViewer offline CodeMirror editor — third-party licenses\n\n${notices.join('\n---\n\n')}`);
console.log(`built app/src/main/assets/edit/cm6.bundle.js (${statSync(outfile).size} bytes); ${packages.size} runtime package licenses`);
