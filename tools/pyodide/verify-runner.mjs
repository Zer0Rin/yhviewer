// Real Chromium + real bundled Pyodide acceptance. No runtime stubs or CSP bypass.
// NODE_PATH is unnecessary: set PLAYWRIGHT_MODULE to an installed playwright entry if needed.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = fileURLToPath(new URL('../../', import.meta.url));
const template = await readFile(root + 'app/src/main/assets/run/runner.html', 'utf8');
const kotlin = await readFile(root + 'app/src/main/java/com/example/yhviewer/CodeRunner.kt', 'utf8');
function csp(lang) {
  // Derive the real policy from Kotlin rather than relaxing the browser for tests.
  const directives = [...kotlin.matchAll(/^\s*"((?:default|script|worker|style|img|font|connect|frame|form-action|base-uri|object)[^\n]+)",/gm)].map(m => m[1]);
  assert(directives.length >= 10, 'Kotlin CSP policy is readable');
  return directives.join('; ').replace('$connect', lang === 'python' ? "'self'" : "'none'")
    .replace('${if (wasm) "\'wasm-unsafe-eval\' " else ""}', lang === 'python' ? "'wasm-unsafe-eval' " : '');
}
let active, delayAssets = 0;
const hits = [];
const server = createServer(async (req, res) => {
  hits.push(req.url);
  if (req.url === '/') {
    res.writeHead(200, {'Content-Type':'text/html', 'Content-Security-Policy':csp(active.lang)});
    res.end(template.replace('__THEME__', 'paper').replace('/*PAYLOAD*/', JSON.stringify(active).replaceAll('<', '\\u003c')));
    return;
  }
  try {
    assert(req.url.startsWith('/assets/') && !req.url.includes('..'));
    const relative = req.url.slice('/assets/'.length);
    const base = relative.startsWith('pyodide/') ? 'standard' : 'main';
    const data = await readFile(root + `app/src/${base}/assets/` + relative);
    const type = relative.endsWith('.wasm') ? 'application/wasm' : relative.endsWith('.js') ? 'text/javascript' : relative.endsWith('.json') ? 'application/json' : relative.endsWith('.css') ? 'text/css' : 'application/octet-stream';
    if (relative.startsWith('pyodide/') && delayAssets) await new Promise(r => setTimeout(r, delayAssets));
    res.writeHead(200, {'Content-Type':type}); res.end(data);
  } catch { res.writeHead(404); res.end(); }
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
console.log('Browser:', browser.version());
let count = 0;
async function run(name, lang, code, check, options = {}) {
  active = {lang, code, timeoutMs:options.timeoutMs || 1500, maxLines:1000, maxChars:200000};
  delayAssets = options.delayAssets || 0;
  const context = await browser.newContext();
  // Fail closed on requests that CSP should prevent, without allowing external traffic.
  const external = [];
  await context.route('**/*', route => {
    if (!route.request().url().startsWith(origin)) { external.push(route.request().url()); return route.abort(); }
    return route.continue();
  });
  const page = await context.newPage();
  const start = Date.now();
  await page.goto(origin);
  await page.waitForFunction(() => window.__runResult && window.__runResult.state !== 'running', null, {timeout:75000});
  const result = await page.evaluate(() => ({...window.__runResult, text:document.getElementById('out').innerText, rows:document.querySelectorAll('#out .line:not(.sys)').length}));
  try { await check(result, Date.now() - start); assert.deepEqual(external, [], 'CSP blocks external traffic before request dispatch'); }
  catch (e) { console.error('FAIL', name, {...result, text:result.text.slice(0,240)}); throw e; }
  finally { await context.close(); }
  console.log('PASS', ++count, name, JSON.stringify({...result, text:result.text.slice(0,240)}));
}
try {
  const done = r => assert.equal(r.state, 'done');
  const bounds = r => { assert.equal(r.state, 'limit'); assert(r.lines <= 1000); assert(r.chars <= 200000); assert(r.messages <= 4000); assert(r.rows <= 1000); };
  const py = (n,c,f,o) => run(n,'python',c,f,o), js = (n,c,f,o) => run(n,'javascript',c,f,o);
  await py('real CPython and Unicode', 'import sys, pyodide\nprint("你好 🌏", sys.version, pyodide.__version__)', r => { done(r); assert.match(r.text,/你好 🌏/); assert.match(r.text,/0\.26\.4/); });
  await py('standard library and top-level await', 'import json, re, math, datetime, collections, asyncio\nawait asyncio.sleep(0.01)\nprint(json.dumps({"x":math.sqrt(81)}), re.match("a", "abc")[0], datetime.date(2024,1,1), collections.Counter("aba")["a"])', r => { done(r); assert.match(r.text,/9.0.*a.*2024-01-01.*2/); });
  await py('clean actual user line 3', 'x = 1\ny = 2\nraise ValueError("boom")', r => { assert.equal(r.state,'error'); assert.match(r.text,/第 3 行/); assert.match(r.text,/ValueError: boom/); assert.doesNotMatch(r.text, /_pyodide|eval_code|run_until_complete/); });
  await py('stdin EOF explanation', 'input("name? ")', r => { assert.equal(r.state,'error'); assert.match(r.text,/EOFError/); assert.match(r.text,/没有键盘输入/); });
  await py('stderr is captured', 'import sys\nprint("err",file=sys.stderr)', r => { done(r); assert.match(r.text,/err/); });
  await py('output without trailing newline is captured', 'print("partial",end="")', r => { done(r); assert.match(r.text,/partial/); });
  await py('partial Unicode output before exception', 'print("你好 🌏",end="")\nraise ValueError("after-partial")', r => { assert.equal(r.state,'error'); assert.match(r.text,/你好 🌏/); assert.match(r.text,/after-partial/); });
  await py('flushed partial output survives timeout', 'print("before-loop",end="",flush=True)\nwhile True: pass', r => { assert.equal(r.state,'timeout'); assert.match(r.text,/before-loop/); }, {timeoutMs:800});
  await py('external fetch denied', 'import js\ntry:\n await js.fetch("https://example.com")\nexcept Exception:\n print("network blocked")', r => { done(r); assert.match(r.text,/network blocked/); });
  await py('no DOM or native bridge', 'import js\nprint(hasattr(js,"document"),hasattr(js,"Android"))', r => { done(r); assert.match(r.text,/False False/); });
  await py('infinite loop terminated after loading', 'while True: pass', (r,ms) => { assert.equal(r.state,'timeout'); assert(ms >= 900); }, {timeoutMs:1000});
  await py('loading excluded from execution budget', 'print("loaded")', (r,ms) => { done(r); assert.match(r.text,/loaded/); assert(ms >= 1800); }, {timeoutMs:600, delayAssets:700});
  await py('forged ready cannot extend deadline', 'import js, asyncio\nwhile True:\n js.postMessage(js.JSON.parse(\'{"t":"ready"}\'))\n await asyncio.sleep(.01)', r => assert.equal(r.state,'timeout'), {timeoutMs:800});
  await py('stdout cumulative limit', 'for i in range(2000): print(i)', bounds);
  await py('forged sys and clear messages bounded', 'import js\nfor i in range(6000):\n js.postMessage(js.JSON.parse(\'{"t":"log","level":"sys","text":"spoof"}\'))\n js.postMessage(js.JSON.parse(\'{"t":"clear"}\'))', r => { bounds(r); assert.doesNotMatch(r.text,/spoof/); });
  await py('huge Python output is bounded without newline', 'print("x"*300000,end="")', bounds);
  await js('Unicode and source literals', 'console.log("你好 🌏", `a\nb`);', r => { done(r); assert.match(r.text,/你好 🌏 a\nb/); });
  await js('circular formatter', 'var a={};a.a=a; console.log(a);', r => { done(r); assert.match(r.text,/Circular/); });
  await js('actual JS line 3', 'var a=1;\nvar b=2;\nthrow new Error("boom");', r => { assert.equal(r.state,'error'); assert.match(r.text,/第 3 行/); });
  await js('JS network denied', 'try {await fetch("https://example.com")} catch(e) { console.log("blocked") }', r => { done(r); assert.match(r.text,/blocked/); });
  await js('clear retains cumulative limits', 'for(var i=0;i<1500;i++){ console.log(i); console.clear(); }', bounds);
  await js('forged message flood bounded', 'for(var i=0;i<6000;i++) postMessage({t:"ready"});', bounds);
  await js('infinite JS timeout', 'while(true){}', r => assert.equal(r.state,'timeout'), {timeoutMs:600});
  assert(hits.some(h => h.endsWith('pyodide.asm.wasm')), 'real WASM fetched');
  assert(hits.some(h => h.endsWith('python_stdlib.zip')), 'real CPython stdlib fetched');
  console.log(`PASS ${count} real-CSP acceptance cases; Pyodide 0.26.4`);
} finally { await browser.close(); await new Promise(r => server.close(r)); }
