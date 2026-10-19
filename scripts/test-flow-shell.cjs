#!/usr/bin/env node
// Execute the production shell in a deterministic layout/event model, not a copy of its algorithm.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(process.env.FLOW_SHELL_PATH || 'app/src/main/assets/book/flow.html', 'utf8');
const script = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)[1];
function shell(heights, start = 0, ratio = 0) {
  const jobs = [], intervals = [], events = {}, seen = new Set();
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
      this.url = url; this.index = Number(url.match(/\/(\d+)\.html/)[1]); requests++; seen.add(this.index);
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
      this.contentDocument = {
        location: { href: url }, readyState: 'complete',
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
    documentElement: { get scrollHeight() { return Math.max(800, body.offsetHeight); }, clientHeight: 800, getAttribute: () => 'paper', setAttribute() {} },
    getElementById: id => id === 'payload' ? { textContent: JSON.stringify({ count: heights.length, start, ratio, pattern: '/txt/{i}.html' }) } : id === 'flow' ? flow : continuation,
    createElement: tag => new El(tag)
  };
  const window = { innerHeight: 800, scrollY: 0,
    scrollTo(x, y) { const next = Math.max(0, Math.min(y, document.documentElement.scrollHeight - 800)); if (next !== this.scrollY) { this.scrollY = next; jobs.push(() => events.scroll?.()); } },
    addEventListener: (name, fn) => { events[name] = fn; }
  };
  vm.runInNewContext(script, { document, window, requestAnimationFrame: fn => jobs.push(fn), setInterval: fn => intervals.push(fn) });
  function pump() { let n = 0; while (jobs.length) { assert(++n < 1000, 'unbounded load/evict loop'); jobs.shift()(); } checkFrames(); }
  function tick() { intervals.forEach(fn => fn()); pump(); }
  pump(); tick();
  return { window, flow, seen, heights, tick, scroll(y) { window.scrollTo(0, y); pump(); tick(); },
    state() { return window.YH.state().split(',').map(Number); },
    requests: () => requests, maxFrames: () => maxFrames };
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
console.log('PASS actual flow shell: tiny forward/backward, bounded/stable frames, boundary/live/reopen offsets');
