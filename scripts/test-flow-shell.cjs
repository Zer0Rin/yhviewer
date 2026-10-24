#!/usr/bin/env node
// Execute the production shell in a deterministic layout/event model, not a copy of its algorithm.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(process.env.FLOW_SHELL_PATH || 'app/src/main/assets/book/flow.html', 'utf8');
const script = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)[1];
// bar: native top-bar height in CSS px reported through yhGlass before load (0 = toolbar hidden / immersive).
function shell(heights, start = 0, ratio = 0, pattern = '/txt/{i}.html', nativeHandshake = true, bar = 0) {
  const jobs = [], intervals = [], events = {}, seen = new Set();
  const clock = { now: 0 }; // the shell's Date.now(); tests advance it explicitly
  const baseHeights = heights.slice();
  let requests = 0, maxFrames = 0;
  class El {
    constructor(tag) { this.tag = tag; this.children = []; this.style = {}; this.attrs = {}; this.listeners = {}; }
    get isConnected() { return this === body || !!this.parent?.isConnected; }
    get firstChild() { return this.children[0]; }
    get offsetTop() { return this.parent ? this.parent.offsetTop + this.parent.children.slice(0, this.parent.children.indexOf(this)).reduce((n, e) => n + e.offsetHeight, 0) : 0; }
    get offsetHeight() {
      if (this.tag === 'iframe') return parseFloat(this.style.height) || 0;
      return Math.max(parseFloat(this.style.height) || 0, parseFloat(this.style.minHeight) || 0,
        this.className === 'gap' ? 36 : this.className === 'edge' ? 96 : this.children.reduce((n, e) => n + e.offsetHeight, 0));
    }
    appendChild(e) { e.parent = this; this.children.push(e); checkFrames(); return e; }
    insertBefore(e, before) { e.parent = this; this.children.splice(this.children.indexOf(before), 0, e); checkFrames(); }
    remove() { this.parent.children.splice(this.parent.children.indexOf(this), 1); this.parent = null; }
    setAttribute(k, v) { this.attrs[k] = v; }
    getAttribute(k) { return this.attrs[k]; }
    addEventListener(k, f) { this.listeners[k] = f; }
    set src(url) {
      this.url = url; this.index = Number(url.match(/(?:\/|c)(\d+)\.(?:html|xhtml)/)[1]); requests++; seen.add(this.index);
      const frame = this;
      let readingCss = '';
      // Browser layout changes when applyLive writes the style, after relayout
      // captures its old anchor. Keep this timing identical in the model.
      const readingStyle = {
        get textContent() { return readingCss; },
        set textContent(css) {
          readingCss = css;
          const scale = css.match(/--read-scale\s*:\s*([0-9.]+)/);
          heights[frame.index] = baseHeights[frame.index] * (scale ? Number(scale[1]) : 1);
        }
      };
      const childListeners = {};
      this.contentDocument = {
        location: { href: new URL(url, 'https://appassets.androidplatform.net').href }, readyState: 'complete',
        addEventListener(type, listener) { childListeners[type] = listener; },
        dispatchClick(anchor, options = {}) {
          let prevented = false;
          const event = { isTrusted: options.trusted ?? true, button: options.button ?? 0,
            defaultPrevented: false, target: anchor, preventDefault() { prevented = true; this.defaultPrevented = true; } };
          childListeners.click?.(event);
          return prevented;
        },
        documentElement: { get scrollHeight() { return heights[frame.index]; }, setAttribute() {} },
        body: { get scrollHeight() { return heights[frame.index]; } },
        elementFromPoint(x, y) {
          if (y >= heights[frame.index]) return null;
          const block = Math.floor(y / (heights[frame.index] / 8));
          return { get isConnected() { return frame.isConnected; },
            getBoundingClientRect() { return { top: block * heights[frame.index] / 8, height: heights[frame.index] / 8 }; } };
        },
        getElementById: id => id === 'yh-reading' ? readingStyle : null, getElementsByName: () => []
      };
      jobs.push(() => { if (this.isConnected) this.listeners.load(); });
    }
  }
  const body = new El('body'), flow = new El('flow'), continuation = new El('continuation');
  body.appendChild(flow); body.appendChild(continuation);
  function checkFrames() { const n = flow?.children?.length || 0; maxFrames = Math.max(maxFrames, n); assert(n <= 5, `frame cap ${n}`); }
  const document = {
    documentElement: { get scrollHeight() { return Math.max(800, body.offsetHeight); }, clientHeight: 800, clientWidth: 400, getAttribute: () => 'paper', setAttribute() {}, appendChild() {} },
    getElementById: id => id === 'payload' ? { textContent: JSON.stringify({ count: heights.length, start, ratio, pattern }) } : id === 'flow' ? flow : continuation,
    createElement: tag => new El(tag)
  };
  const window = { innerHeight: 800, innerWidth: 400, scrollY: 0, location: { origin: 'https://appassets.androidplatform.net' },
    scrollTo(x, y) { const next = Math.max(0, Math.min(y, document.documentElement.scrollHeight - 800)); if (next !== this.scrollY) { this.scrollY = next; jobs.push(() => events.scroll?.()); } },
    addEventListener: (name, fn) => { events[name] = fn; }
  };
  vm.runInNewContext(script, { document, window, URL, Date: { now: () => clock.now }, requestAnimationFrame: fn => jobs.push(fn), setInterval: fn => intervals.push(fn) });
  function pump() { let n = 0; while (jobs.length) { assert(++n < 1000, 'unbounded load/evict loop'); jobs.shift()(); } checkFrames(); }
  function tick() { intervals.forEach(fn => fn()); pump(); }
  if (bar !== null) window.yhGlass(bar ? [{ x: 0, y: 0, w: 400, h: bar, r: 0 }] : []);
  pump(); tick();
  if (nativeHandshake) { window.YH.hold(false); tick(); }
  return { window, flow, start, seen, heights, tick, clock, scroll(y) { window.scrollTo(0, y); pump(); tick(); },
    state() { return window.YH.state().split(',').map(Number); },
    requests: () => requests, maxFrames: () => maxFrames };
}
{
  // Native can request Find before YH exists. The shell must remain held
  // during that load gap, then honor the latest native hold and resume on close.
  const earlyYH = undefined;
  assert.equal(earlyYH?.hold(true), undefined);
  const s = shell(Array(12).fill(1600), 5, .9, '/txt/{i}.html', false);
  assert.equal(s.flow.children.length, 1, 'unacknowledged shell cannot add chapters');
  s.window.YH.hold(true);
  s.scroll(s.window.scrollY + 80);
  assert.equal(s.flow.children.length, 1, 'early Find keeps corpus fixed after shell readiness');
  s.window.YH.hold(false);
  s.tick();
  assert(s.flow.children.length > 1, 'closing Find resumes chapter additions');
}
function link(s, href, attrs = {}, options = {}) {
  const d = s.flow.children.find(e => e.attrs['data-ch'] === String(s.start)).children[0].contentDocument;
  const anchor = { tagName: 'A', href: new URL(href, d.location.href).href,
    getAttribute: key => key === 'href' ? href : attrs[key] ?? null,
    hasAttribute: key => Object.hasOwn(attrs, key), closest: selector => selector === 'a[href]' ? anchor : null };
  return d.dispatchClick(anchor, options);
}
{
  const s = shell(Array(8).fill(1600), 4, 0, '/book/OPS/c{i}.xhtml');
  // Production-shell handler must stop ordinary iframe navigation and expose
  // the single trusted destination for native consumption.
  assert.equal(link(s, 'c7.xhtml#p12'), true, 'trusted relative chapter link captured');
  assert.equal(s.window.YH.takeLink(), '/book/OPS/c7.xhtml#p12');
  assert.equal(s.window.YH.takeLink(), '', 'link consumed once');
  assert.equal(link(s, '#note'), true, 'same-chapter anchor captured');
  assert.equal(s.window.YH.takeLink(), '/book/OPS/c4.xhtml#note');
  for (const [href, attrs, options] of [
    ['/book/OPS/c7.xhtml', {}, { trusted: false }],
    ['https://example.invalid/escape', {}, {}],
    ['https://evil.appassets.androidplatform.net/book/OPS/c7.xhtml', {}, {}],
    ['/txt/7.html', {}, {}],
    ['/book/OPS/c7.xhtml', { target: '_top' }, {}],
    ['/book/OPS/c7.xhtml', { target: '_parent' }, {}],
    ['/book/OPS/c7.xhtml', { target: '_blank' }, {}],
    ['/book/OPS/c7.xhtml', { download: '' }, {}],
    ['javascript:alert(1)', {}, {}],
    ['/doc/index.html', {}, {}],
    ['/book/OPS/c7.xhtml', {}, { button: 1 }],
  ]) {
    assert.equal(link(s, href, attrs, options), false, `blocked route ${href}`);
    assert.equal(s.window.YH.takeLink(), '', `nothing queued for ${href}`);
  }
}
function traverseTiny(start) {
  const s = shell(Array(18).fill(20), start);
  for (let i = 0; i < 80; i++) s.scroll(s.window.scrollY + 35);
  assert.equal(s.state()[0], 17, `forward traversal from ${start}`);
  for (let i = 0; i < 80; i++) s.scroll(s.window.scrollY - 35);
  assert.equal(s.state()[0], 0, `backward traversal from ${start}`);
  assert.equal(s.seen.size, 18);
  const count = s.requests(); for (let i = 0; i < 20; i++) s.tick();
  assert.equal(s.requests(), count, 'stationary tiny window must settle');
  assert(s.maxFrames() <= 5);
}
if (process.env.FLOW_CASE !== 'boundary') { traverseTiny(0); traverseTiny(8); }
for (const y of [900, 1000, 1500, 1620, 1635, 1636, 1650, 2500]) {
  const s = shell(Array(8).fill(1600));
  s.scroll(y);
  const before = s.state(), position = s.window.scrollY;
  s.window.setTheme('ink'); s.window.setReading(':root{--read-scale:1}'); s.tick();
  assert(Math.abs(s.window.scrollY - position) < 0.01, `live reflow offset ${y}`);
  const reopened = shell(Array(8).fill(1600), before[0], before[1]);
  const after = reopened.state();
  assert.equal(after[0], before[0], `reopen chapter ${y}`);
  assert(Math.abs(after[1] - before[1]) < 0.0002, `reopen ratio ${y}: ${after} vs ${before}`);
}
const final = shell(Array(8).fill(1600), 7, 1.1);
const finalBefore = final.state(), finalY = final.window.scrollY;
final.window.setTheme('ink'); final.window.setReading(':root{--read-scale:1}'); final.tick();
assert(Math.abs(final.window.scrollY - finalY) < .01, 'final viewport live offset');
assert.deepEqual(shell(Array(8).fill(1600), ...finalBefore).state(), finalBefore, 'final viewport reopen');
// Reopen and bookmark jumps both supply chapter/ratio payloads. Cover offsets
// inside tiny/medium/exact-height chapters and gaps, plus subpixel legacy spans.
for (const height of [20, 400, 800, 800.5]) {
  for (const chapter of [0, 4, 7]) {
    for (const offset of [.5, 10, 18, height + 10]) {
      const s = shell(Array(8).fill(height), chapter);
      const target = s.flow.children.find(e => Number(e.attrs['data-ch']) === chapter);
      s.scroll(target.offsetTop + offset);
      const before = s.state();
      const active = s.flow.children.find(e => Number(e.attrs['data-ch']) === before[0]);
      const actualOffset = s.window.scrollY - active.offsetTop;
      if (chapter !== 7) assert(Math.abs(actualOffset - offset) < .001, 'requested nonfinal offset must be reached');
      // The final runway intentionally stops at the last chapter's usable bottom;
      // final cases round-trip that actual boundary rather than fictitious scroll.
      for (const kind of ['reopen', 'bookmark']) {
        const restored = shell(Array(8).fill(height), before[0], before[1]);
        const frame = restored.flow.children.find(e => Number(e.attrs['data-ch']) === before[0]);
        assert.equal(restored.state()[0], before[0], `${kind} short chapter ${height}/${chapter}/${offset}`);
        assert(Math.abs(restored.window.scrollY - frame.offsetTop - actualOffset) < .001,
          `${kind} short offset ${height}/${chapter}/${offset}`);
      }
    }
    for (const ratio of [0, .5, 1]) {
      const legacy = shell(Array(8).fill(height), chapter, ratio);
      const frame = legacy.flow.children.find(e => Number(e.attrs['data-ch']) === chapter);
      assert(Math.abs(legacy.window.scrollY - frame.offsetTop - Math.max(0, height - 800) * ratio) < .001,
        `legacy short ratio ${height}/${chapter}/${ratio}`);
    }
  }
}
// Font reflow preserves the chapter-relative visible anchor, including a tail
// whose legacy ratio exceeds one; theme-only changes above preserve exact pixels.
for (const y of [900, 1500, 1700, 2500]) {
  const s = shell(Array(8).fill(1600)); s.scroll(y);
  const [chapter] = s.state();
  const section = s.flow.children.find(e => Number(e.attrs['data-ch']) === chapter);
  const fraction = (s.window.scrollY - section.offsetTop) / s.heights[chapter];
  s.window.setReading(':root{--read-scale:1.2}'); s.tick();
  assert.equal(s.heights[chapter], 1920, 'font style must actually change modeled layout');
  const after = s.flow.children.find(e => Number(e.attrs['data-ch']) === chapter);
  assert.equal(s.state()[0], chapter);
  assert(Math.abs((s.window.scrollY - after.offsetTop) / s.heights[chapter] - fraction) < .0001, `font anchor ${y}`);
}
// 1.0 FIX5: with the top bar shown, a chapter-start jump puts the chapter top at the bar's
// lower edge and reports that chapter; the chapter line follows the bar edge, tolerating
// WebView's sub-pixel snapping below a chapter top.
{
  const BAR = 92;
  for (const chapter of [1, 2, 5]) {
    const s = shell(Array(8).fill(1600), chapter, 0, '/txt/{i}.html', true, BAR);
    const frame = s.flow.children.find(e => Number(e.attrs['data-ch']) === chapter);
    assert(Math.abs(frame.offsetTop - s.window.scrollY - BAR) < .001, `chapter ${chapter} top sits under the bar`);
    assert.deepEqual(s.state(), [chapter, 0], `chapter-start jump reports chapter ${chapter}`);
    s.scroll(frame.offsetTop - BAR - .3);
    assert.equal(s.state()[0], chapter, 'sub-pixel snap above the chapter line keeps the target chapter');
    s.scroll(frame.offsetTop - BAR - 3);
    assert.equal(s.state()[0], chapter - 1, 'heading still below the bar edge belongs to the next scroll');
    assert.deepEqual(shell(Array(8).fill(1600), ...[chapter, 0], '/txt/{i}.html', true, BAR).state(), [chapter, 0], 'reopen at chapter start is stable');
  }
  // Glass arrives after the first layout: the jump uses a guess, then realigns once to the real bar.
  const late = shell(Array(8).fill(1600), 3, 0, '/txt/{i}.html', true, null);
  late.window.yhGlass([{ x: 0, y: 0, w: 400, h: BAR, r: 0 }]); late.tick();
  const lateFrame = late.flow.children.find(e => Number(e.attrs['data-ch']) === 3);
  assert(Math.abs(lateFrame.offsetTop - late.window.scrollY - BAR) < .001, 'late bar realigns the chapter-start jump');
  assert.deepEqual(late.state(), [3, 0]);
  // 1.0.1: on a slow device the first real bar height can arrive long after the jump. The jump
  // must not expire before then, or it stays on the guessed height (CI: 96.9 vs a real 88).
  const slow = shell(Array(8).fill(1600), 3, 0, '/txt/{i}.html', true, null);
  slow.clock.now += 10_000; for (let k = 0; k < 5; k++) slow.tick();
  slow.window.yhGlass([{ x: 0, y: 0, w: 400, h: 88, r: 0 }]); slow.tick();
  const slowFrame = slow.flow.children.find(e => Number(e.attrs['data-ch']) === 3);
  assert(Math.abs(slowFrame.offsetTop - slow.window.scrollY - 88) < .001, 'first real bar height realigns even after a slow load');
  // 1.0.1: native pushes setReading right after load; its deferred restore must not undo a
  // realignment that happened in between (CI trace: jump at guess 96, bar 88 arrives, restore -> 96.9).
  const restyled = shell(Array(8).fill(1600), 3, 0, '/txt/{i}.html', true, null);
  restyled.window.setReading(':root{--read-scale:1}');
  restyled.window.yhGlass([{ x: 0, y: 0, w: 400, h: 88, r: 0 }]);
  restyled.tick();
  const restyledFrame = restyled.flow.children.find(e => Number(e.attrs['data-ch']) === 3);
  assert(Math.abs(restyledFrame.offsetTop - restyled.window.scrollY - 88) < .001, `restyle restore keeps the realigned jump (${restyledFrame.offsetTop - restyled.window.scrollY})`);
  // Later bar changes (toggling the toolbar) still only realign within the short window.
  slow.clock.now += 10_000; slow.window.yhGlass([]); slow.tick();
  assert(Math.abs(slowFrame.offsetTop - slow.window.scrollY - 88) < .001, 'toolbar toggle long after the jump does not move the page');
  // 1.0 FIX6: the chapter above grows after the jump (late image) while the viewport top sits
  // in its tail behind the bar; compensation must keep the target chapter under the bar.
  for (const bar of [BAR, 0]) {
    const g = shell([1600, 300, 250], 2, 0, '/txt/{i}.html', true, bar);
    g.heights[1] = 2300; g.tick(); g.tick();
    const gf = g.flow.children.find(e => Number(e.attrs['data-ch']) === 2);
    assert(Math.abs(gf.offsetTop - g.window.scrollY - bar) < .001, `growing previous chapter keeps the target (bar ${bar})`);
    assert.equal(g.state()[0], 2, `target chapter still current after growth (bar ${bar})`);
  }
  // Mid-chapter progress is not shifted by the bar.
  const mid = shell(Array(8).fill(1600), 4, .5, '/txt/{i}.html', true, BAR);
  const midFrame = mid.flow.children.find(e => Number(e.attrs['data-ch']) === 4);
  assert(Math.abs(mid.window.scrollY - midFrame.offsetTop - 400) < .001, 'mid-chapter restore ignores the bar');
}
console.log('PASS actual flow shell: trusted/blocked links, one-shot consume, tiny forward/backward, bounded/stable frames, boundary/live/reopen offsets, top-bar chapter line');
