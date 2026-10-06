// Browser check of the Web game sounds (developer tool, not part of the build).
//
// Serves a built Web app from a nested folder (/games/logica/index.html, the way Yandex serves the
// game), wraps the page's AudioContext before the app loads, plays 2048 through the UI, and checks:
// sounds load from the nested path and start with decoded buffers, one sound per move, a hidden tab
// or a lost focus suspends the context and plays nothing, the Sound setting silences everything,
// a sound waiting for its file plays only when the file is ready within ~300 ms, and a tap does not
// wake the context while sound is not allowed.
//
// Run (the site folder holds index.html, the Wasm/JS bundle and its resources, e.g. the development
// executable plus processed resources, skiko and @js-joda/core as ./js-joda.mjs via an import map):
//   node tools/web/check_sounds.mjs <site-folder>
// Environment: PLAYWRIGHT (path of the playwright package, default: `playwright` from NODE_PATH or
// /opt/node22/lib/node_modules/playwright), CHROMIUM (browser executable), SHOTS=1 (screenshots),
// PAGE (the page under the folder, default index.html; PAGE= opens the folder itself), OLD_SAFARI=1
// (decodeAudioData answers through its callbacks only, like Safari before 14.1).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const site = path.resolve(process.argv[2] || '.');
const playwright = (() => {
  for (const id of [process.env.PLAYWRIGHT, 'playwright', '/opt/node22/lib/node_modules/playwright']) {
    if (!id) continue;
    try { return require(id); } catch { /* next */ }
  }
  throw new Error('Playwright is not installed; set PLAYWRIGHT to its package path.');
})();
const executablePath = process.env.CHROMIUM ||
  ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium'].find((p) => fs.existsSync(p) && fs.statSync(p).isFile());

const types = { '.html': 'text/html', '.mjs': 'text/javascript', '.js': 'text/javascript', '.wasm': 'application/wasm', '.wav': 'audio/wav', '.png': 'image/png', '.webp': 'image/webp', '.json': 'application/json' };
const PREFIX = '/games/logica/';
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (!url.pathname.startsWith(PREFIX)) { res.writeHead(404); res.end(); return; }
  const file = path.join(site, decodeURIComponent(url.pathname.slice(PREFIX.length)) || 'index.html');
  if (!file.startsWith(site) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, r));
const origin = `http://localhost:${server.address().port}`;

// Wraps Web Audio before the app runs: every event lands in window.__soundLog.
const audioSpy = (callbackOnly) => {
  const log = (window.__soundLog = []);
  const t0 = performance.now();
  const add = (ev, extra) => log.push({ t: Math.round(performance.now() - t0), ev, ...extra });
  const urls = new WeakMap();
  const arrayBuffer = Response.prototype.arrayBuffer;
  Response.prototype.arrayBuffer = function () {
    const url = this.url;
    return arrayBuffer.call(this).then((d) => { urls.set(d, url); return d; });
  };
  const C = window.AudioContext;
  const proto = C.prototype;
  const name = (u) => (u || '').replace(/^.*\/files\/sounds\//, '');
  const { resume, suspend, decodeAudioData, createBufferSource } = proto;
  proto.resume = function () { add('resume', { state: this.state }); return resume.call(this); };
  proto.suspend = function () { add('suspend', { state: this.state }); return suspend.call(this); };
  proto.decodeAudioData = function (d, ok, fail) {
    const url = urls.get(d);
    const decoding = decodeAudioData.call(this, d).then((b) => { b.__url = url; add('decoded', { sound: name(url), from: url }); ok && ok(b); return b; },
      (e) => { add('decodeFailed', { sound: name(url) }); fail && fail(e); throw e; });
    // Old webkitAudioContext (Safari before 14.1) answers through the callbacks only.
    if (!callbackOnly) return decoding;
    decoding.catch(() => {});
    return undefined;
  };
  proto.createBufferSource = function () {
    const src = createBufferSource.call(this);
    const start = src.start;
    src.start = function (...a) { add('start', { sound: name(src.buffer && src.buffer.__url), decoded: !!(src.buffer && src.buffer.length), ctx: this.context.state }); return start.apply(this, a); };
    return src;
  };
  window.__setHidden = (hidden) => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (hidden ? 'hidden' : 'visible') });
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
    document.dispatchEvent(new Event('visibilitychange'));
  };
};

const browser = await playwright.chromium.launch({ executablePath, args: ['--no-sandbox', '--autoplay-policy=user-gesture-required'] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
await page.addInitScript(audioSpy, !!process.env.OLD_SAFARI);
const failed = [];
page.on('requestfailed', (r) => failed.push(r.url()));
page.on('response', (r) => { if (r.status() >= 400) failed.push(`${r.status()} ${r.url()}`); });

let shot = 0;
const snap = async (label) => { if (process.env.SHOTS) await page.screenshot({ path: `sound-${++shot}-${label}.png` }); };
const wait = (ms) => page.waitForTimeout(ms);
const tap = async (x, y, ms = 1200) => { await page.mouse.click(x, y); await wait(ms); };
const key = async (k, ms = 400) => { await page.keyboard.press(k); await wait(ms); };
const log = () => page.evaluate(() => window.__soundLog.slice());
const since = async (n) => (await log()).slice(n);
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  ${detail}` : ''}`); };
const starts = (events) => events.filter((e) => e.ev === 'start');

// Coordinates are for a 390x844 portrait window (Russian UI).
const SETTINGS_GEAR = [358, 26];
const SOUND_SWITCH = [305, 440];
const SETTINGS_DONE = [295, 557];
const GAME_2048_CARD = [120, 385]; // «Играть» on the 2048 card with the hub scrolled to its end
const EASY_CARD = [195, 210];
const OFFER_PLAY = [168, 514]; // «Играть» in the first-play tutorial offer (later a harmless board tap)

await page.goto(`${origin}${PREFIX}${process.env.PAGE ?? 'index.html'}?lang=ru`);
await wait(9000);
await snap('hub');

const toSettings = async () => { await tap(...SETTINGS_GEAR); await snap('settings'); await tap(...SOUND_SWITCH, 500); await tap(...SETTINGS_DONE); };
// Leaving a game with progress asks to confirm, so phases restart from a reload instead (no penalty).
const reload = async () => { await page.reload(); await wait(9000); };
const into2048 = async () => {
  await page.mouse.move(195, 500);
  await page.mouse.wheel(0, 5000);
  await wait(1500);
  await tap(...GAME_2048_CARD, 1500); await snap('difficulty');
  await tap(...EASY_CARD, 1500); await snap('offer-or-game');
  // The first play offers the tutorial once; «Играть» starts the level.
  await tap(...OFFER_PLAY, 2500); await snap('game');
};
const moves = async () => { for (const k of ['ArrowLeft', 'ArrowUp', 'ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp']) await key(k, 450); };

// 1. Sound on: the sounds load from the nested folder and every move starts one decoded buffer.
await into2048();
let mark = (await log()).length;
await moves();
let ev = await since(0);
const decoded = ev.filter((e) => e.ev === 'decoded');
check('all 8 sounds decoded from the nested folder', decoded.length === 8 && decoded.every((e) => e.from.includes(PREFIX) && !e.from.includes('index.html')), decoded.map((e) => e.sound).join(','));
let played = starts(await since(mark));
check('moves start decoded buffers, one per move', played.length >= 1 && played.length <= 6 && played.every((e) => e.decoded && ['tap.wav', 'merge.wav'].includes(e.sound)), played.map((e) => e.sound).join(','));
check('no failed sound requests', !failed.some((u) => u.includes('.wav')), failed.join(' '));

// 2. A hidden tab, then a lost focus: the context suspends and moves play nothing.
mark = (await log()).length;
await page.evaluate(() => window.__setHidden(true));
await wait(300);
await moves();
ev = await since(mark);
check('hidden tab suspends the context', ev.some((e) => e.ev === 'suspend'));
check('hidden tab plays nothing', starts(ev).length === 0, starts(ev).map((e) => e.sound).join(','));
// Keys (above) and a tap while the tab is still hidden (sound not allowed) must not wake the context.
await page.mouse.click(195, 120);
await wait(300);
ev = await since(mark);
const hiddenState = await page.evaluate(() => window.__logicaAudio.ctx.state);
check('keys and taps do not resume a context the host has not allowed', !ev.some((e) => e.ev === 'resume') && hiddenState === 'suspended', `state=${hiddenState} ${JSON.stringify(ev.filter((e) => e.ev !== 'start'))}`);
await page.evaluate(() => window.__setHidden(false));
await tap(195, 120, 500);
mark = (await log()).length;
await page.evaluate(() => window.dispatchEvent(new Event('blur')));
await wait(300);
// Any key or tap counts as focus again, so a blurred page is checked without input: the host has
// withdrawn its permission and a play request starts nothing.
const blurred = await page.evaluate(() => {
  const a = window.__logicaAudio;
  a.play(Object.keys(a.buffers)[0], 0.1, 300);
  return a.allowed;
});
ev = await since(mark);
check('blur suspends the context', ev.some((e) => e.ev === 'suspend'));
check('blur plays nothing', !blurred && starts(ev).length === 0, starts(ev).map((e) => e.sound).join(','));
// A tap in the page counts as focus again (the Yandex iframe case), so the host resumes the context.
await tap(195, 120, 600);
mark = (await log()).length;
await moves();
check('sound returns after a tap refocuses the page', starts(await since(mark)).length >= 1);

// 3. A sound waiting for its file plays only if the file is ready within ~300 ms.
await page.route('**/late-*.wav', async (route) => {
  const delay = route.request().url().includes('late-fast') ? 80 : 900;
  await new Promise((r) => setTimeout(r, delay));
  route.fulfill({ status: 200, contentType: 'audio/wav', body: fs.readFileSync(path.join(site, 'composeResources/com.stanisryz.logica.shared.ui.generated.resources/files/sounds/tap.wav')) });
});
mark = (await log()).length;
await page.evaluate(() => { const a = window.__logicaAudio; a.play(new URL('late-fast.wav', document.baseURI).href, 0.1, 300); a.play(new URL('late-slow.wav', document.baseURI).href, 0.1, 300); });
await wait(1500);
ev = await since(mark);
check('a sound ready within 300 ms plays', starts(ev).some((e) => e.sound.includes('late-fast')));
check('a sound later than 300 ms is dropped', !starts(ev).some((e) => e.sound.includes('late-slow')) && ev.some((e) => e.ev === 'decoded' && e.sound.includes('late-slow')));

// 4. The Sound setting off: nothing starts and the context is suspended; back on: the next move sounds.
await reload();
await toSettings();
mark = (await log()).length;
await into2048();
await moves();
ev = await since(mark);
check('Sound off plays nothing', starts(ev).length === 0, starts(ev).map((e) => e.sound).join(','));
check('Sound off loads nothing', !ev.some((e) => e.ev === 'decoded'));
await reload();
await toSettings();
await into2048();
mark = (await log()).length;
await moves();
check('Sound back on: the next moves sound', starts(await since(mark)).length >= 1);

await snap('end');
const all = await log();
console.log('\nlog (last 40):');
for (const e of all.slice(-40)) console.log(JSON.stringify(e));
await browser.close();
server.close();
const bad = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - bad}/${results.length} checks passed`);
process.exit(bad ? 1 : 0);
