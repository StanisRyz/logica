// Browser check of the Web game sounds (developer tool, not part of the build).
//
// Serves a built Web app from a nested folder (/games/logica/index.html, the way Yandex serves the
// game), wraps the page's AudioContext before the app loads, plays 2048 through the UI, and checks:
// sounds load from the nested path and start with decoded buffers, one sound per move, a hidden tab
// suspends the context and plays nothing, a window blur silences it until the next tap while a page
// that simply has no focus keeps its sound, the Sound setting silences everything, a sound waiting
// for its file plays only when the file is ready within ~300 ms, and a tap does not wake the context
// while sound is not allowed.
//
// YANDEX=1 runs it the way Yandex Games hosts the game instead: the page sits in an iframe of
// another origin (a second local port), loads a stand-in `/sdk.js` (YaGames with game_api_pause /
// game_api_resume and a rewarded ad that holds the screen for a moment), starts without focus, and
// Web Audio follows mobile Safari's rule — a context made outside a tap or key press stays
// suspended and `resume()` outside one does nothing until a gesture has started it once. There it
// checks that the context is created and started inside a gesture (never resumed outside one before
// that), that the first moves sound, that a page without focus still plays while a blur silences it
// until the next tap, and that a Yandex pause, a hidden tab, a fullscreen ad, and the Sound setting
// each silence it.
//
// Run (the site folder holds index.html, the Wasm/JS bundle and its resources, e.g. the development
// executable plus processed resources, skiko and @js-joda/core as ./js-joda.mjs via an import map):
//   node tools/web/check_sounds.mjs <site-folder>
//   YANDEX=1 node tools/web/check_sounds.mjs <site-folder>
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
const YANDEX = !!process.env.YANDEX;

// A stand-in for the Yandex Games SDK: one Player, no saves of its own, a rewarded ad that holds the
// screen for 1.5 s, and window.__ya.emit(event) for game_api_pause / game_api_resume.
const FAKE_SDK = `(function () {
  var handlers = {};
  var player = { isAuthorized: function () { return true; }, getUniqueID: function () { return 'soundCheck'; }, getName: function () { return 'Test'; },
    getPhoto: function () { return ''; }, getData: function () { return Promise.resolve({}); }, setData: function () { return Promise.resolve(true); } };
  var sdk = {
    features: { LoadingAPI: { ready: function () {} }, GameplayAPI: { start: function () {}, stop: function () {} } },
    environment: { i18n: { lang: 'ru' } }, EVENTS: {},
    adv: {
      showRewardedVideo: function (o) { var c = o.callbacks || {}; c.onOpen && c.onOpen(); setTimeout(function () { c.onRewarded && c.onRewarded(); c.onClose && c.onClose(); }, 1500); },
      showFullscreenAdv: function (o) { var c = o.callbacks || {}; c.onClose && c.onClose(false); },
      showBannerAdv: function () { return Promise.resolve({}); }, hideBannerAdv: function () {},
      getBannerAdvStatus: function () { return Promise.resolve({ stickyAdvIsShowing: false }); } },
    getPlayer: function () { return Promise.resolve(player); },
    on: function (e, f) { (handlers[e] = handlers[e] || []).push(f); },
    off: function (e, f) { handlers[e] = (handlers[e] || []).filter(function (x) { return x !== f; }); },
    serverTime: function () { return Date.now(); },
    leaderboards: { setScore: function () { return Promise.resolve(); }, getEntries: function () { return Promise.resolve({ entries: [], userRank: 0 }); },
      getPlayerEntry: function () { return Promise.reject({}); } },
  };
  window.__ya = { emit: function (e) { (handlers[e] || []).forEach(function (f) { f(); }); } };
  window.YaGames = { init: function () { return Promise.resolve(sdk); } };
})();`;

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (YANDEX && url.pathname === '/sdk.js') { res.writeHead(200, { 'content-type': 'text/javascript' }); res.end(FAKE_SDK); return; }
  if (!url.pathname.startsWith(PREFIX)) { res.writeHead(404); res.end(); return; }
  const file = path.join(site, decodeURIComponent(url.pathname.slice(PREFIX.length)) || 'index.html');
  if (!file.startsWith(site) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  if (YANDEX && file.endsWith('.html')) {
    // The page loads the SDK from /sdk.js like the uploaded index.html does.
    let html = fs.readFileSync(file, 'utf8');
    if (!html.includes('/sdk.js')) html = html.replace(/<script type="importmap">|<script type="module">/, (m) => `<script src="/sdk.js"></script>\n${m}`);
    res.writeHead(200, { 'content-type': 'text/html' });
    res.end(html);
    return;
  }
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, r));
// In Yandex mode the game is another origin than the page around it (127.0.0.1 inside localhost).
const origin = `http://${YANDEX ? '127.0.0.1' : 'localhost'}:${server.address().port}`;
const portal = http.createServer((req, res) => {
  res.writeHead(200, { 'content-type': 'text/html' });
  res.end(`<!doctype html><body style="margin:0"><input id="portal-input" style="position:fixed;left:-100px">
<iframe id="game" src="${origin}${PREFIX}${process.env.PAGE ?? 'index.html'}?lang=ru" style="position:fixed;inset:0;width:100%;height:100%;border:0"></iframe></body>`);
});
if (YANDEX) await new Promise((r) => portal.listen(0, r));

// Wraps Web Audio before the app runs: every event lands in window.__soundLog.
const audioSpy = ({ callbackOnly, strict }) => {
  if (strict && !location.pathname.startsWith('/games/')) return; // the portal page itself has no game
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
  // Mobile Safari's rule, emulated in strict mode: a gesture is a tap or key press being dispatched
  // (these listeners run first, before the app's); a context made outside one starts suspended, and
  // resume() outside one does nothing until a gesture has started that context once.
  let inGesture = false;
  if (strict) {
    for (const e of ['pointerdown', 'pointerup', 'touchend', 'click', 'keydown', 'mousedown', 'mouseup']) {
      window.addEventListener(e, () => { inGesture = true; setTimeout(() => { inGesture = false; }, 0); }, true);
    }
    const Wrapped = function (...args) {
      const ctx = new C(...args);
      ctx.__unlocked = inGesture;
      add('create', { gesture: inGesture });
      if (!inGesture) suspend.call(ctx);
      return ctx;
    };
    Wrapped.prototype = proto;
    window.AudioContext = Wrapped;
    window.webkitAudioContext = Wrapped;
  }
  window.__inGesture = () => inGesture;
  proto.resume = function () {
    add('resume', { state: this.state, gesture: inGesture });
    if (strict && !this.__unlocked && !inGesture) { add('resumeBlocked'); return Promise.reject(new DOMException('Not allowed', 'NotAllowedError')); }
    if (inGesture) this.__unlocked = true;
    return resume.call(this);
  };
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
    // Audible only on a running context that a gesture has started (always so outside strict mode).
    src.start = function (...a) { add('start', { sound: name(src.buffer && src.buffer.__url), decoded: !!(src.buffer && src.buffer.length), ctx: this.context.state, audible: this.context.state === 'running' && (!strict || this.context.__unlocked === true) }); return start.apply(this, a); };
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
await page.addInitScript(audioSpy, { callbackOnly: !!process.env.OLD_SAFARI, strict: YANDEX });
const failed = [];
page.on('requestfailed', (r) => failed.push(r.url()));
page.on('response', (r) => { if (r.status() >= 400) failed.push(`${r.status()} ${r.url()}`); });
const warnings = [];
page.on('console', (m) => { if (m.type() === 'warning' && m.text().includes('WebSound')) warnings.push(m.text()); });

// The frame the game runs in: the page itself, or the iframe inside the portal in Yandex mode.
const app = () => (YANDEX ? page.frames().find((f) => f.url().includes(PREFIX)) : page.mainFrame());
let shot = 0;
const snap = async (label) => { if (process.env.SHOTS) await page.screenshot({ path: `sound-${++shot}-${label}.png` }); };
const wait = (ms) => page.waitForTimeout(ms);
const tap = async (x, y, ms = 1200) => { await page.mouse.click(x, y); await wait(ms); };
const key = async (k, ms = 400) => { await page.keyboard.press(k); await wait(ms); };
const log = () => app().evaluate(() => window.__soundLog.slice());
const since = async (n) => (await log()).slice(n);
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  ${detail}` : ''}`); };
const starts = (events) => events.filter((e) => e.ev === 'start');
const audible = (events) => starts(events).filter((e) => e.audible);

// Coordinates are for a 390x844 portrait window (Russian UI).
const SETTINGS_GEAR = [358, 26];
const SOUND_SWITCH = [305, 440];
const SETTINGS_DONE = [295, 557];
const GAME_2048_CARD = [120, 385]; // «Играть» on the 2048 card with the hub scrolled to its end
const EASY_CARD = [195, 210];
const OFFER_PLAY = [168, 514]; // «Играть» in the first-play tutorial offer (later a harmless board tap)
const STORE_TAB = [325, 815];
const STORE_GEM_AD = [287, 194]; // «Смотреть рекламу» on the rewarded +1 gem row of the Store

const open = async () => {
  await page.goto(YANDEX ? `http://localhost:${portal.address().port}/` : `${origin}${PREFIX}${process.env.PAGE ?? 'index.html'}?lang=ru`);
  await wait(10000);
};
await open();
await snap('hub');

const toSettings = async () => { await tap(...SETTINGS_GEAR); await snap('settings'); await tap(...SOUND_SWITCH, 500); await tap(...SETTINGS_DONE); };
// Leaving a game with progress asks to confirm, so phases restart from a reload instead (no penalty).
const reload = async () => { await open(); };
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
let mark;
let ev;
let played;

if (YANDEX) {
  // 1. Before any gesture: no focus, and nothing has made or started an audio context.
  const before = await app().evaluate(() => ({ focus: document.hasFocus(), ctx: !!(window.__logicaAudio && window.__logicaAudio.ctx) }));
  check('the game starts without focus inside the portal', !before.focus, JSON.stringify(before));
  check('no audio context before the first gesture', !before.ctx);

  // 2. Into 2048: the context is made and started inside a gesture, and the moves sound.
  await into2048();
  mark = (await log()).length;
  await moves();
  ev = await since(0);
  const created = ev.filter((e) => e.ev === 'create');
  check('the audio context is created inside a tap', created.length === 1 && created[0].gesture === true, JSON.stringify(created));
  check('no resume() outside a gesture before a gesture started the context', !ev.some((e) => e.ev === 'resumeBlocked'), JSON.stringify(ev.filter((e) => e.ev === 'resume' || e.ev === 'resumeBlocked')));
  played = await since(mark);
  check('the first moves sound', audible(played).length >= 1, JSON.stringify(starts(played)));
  check('all 8 sounds decoded from the nested folder', ev.filter((e) => e.ev === 'decoded').length === 8);

  // 3. A page without window focus (none was ever given, no blur) keeps its sound.
  mark = (await log()).length;
  const unfocused = await app().evaluate(() => {
    document.hasFocus = () => false;
    const a = window.__logicaAudio;
    a.play(Object.keys(a.buffers)[0], 0.1, 300);
    return a.allowed;
  });
  await wait(300);
  ev = await since(mark);
  check('a page without window focus keeps its sound', unfocused && audible(ev).length >= 1 && !ev.some((e) => e.ev === 'suspend'), JSON.stringify(ev));
  await app().evaluate(() => { delete document.hasFocus; });

  // 3b. The portal takes the focus (a blur event): silence until the next tap, whose first move sounds.
  mark = (await log()).length;
  await page.focus('#portal-input');
  await wait(300);
  const blurred = await app().evaluate(() => {
    const a = window.__logicaAudio;
    a.play(Object.keys(a.buffers)[0], 0.1, 300);
    return { allowed: a.allowed, ctx: a.ctx.state, blockedBy: a.blockedBy };
  });
  ev = await since(mark);
  check('a window blur silences the game', !blurred.allowed && starts(ev).length === 0 && blurred.ctx === 'suspended', JSON.stringify(blurred));
  await tap(195, 120, 300);
  mark = (await log()).length;
  // A key may hit a direction the board cannot move in, so the first sound of the next moves counts.
  await moves();
  ev = await since(mark);
  check('the first move after a tap sounds again', starts(ev).length >= 1 && starts(ev)[0].audible, JSON.stringify(starts(ev).slice(0, 2)));

  // 4. game_api_pause silences the game until game_api_resume.
  await tap(195, 120, 400); // focus back into the game for the keys
  await app().evaluate(() => window.__ya.emit('game_api_pause'));
  await wait(300);
  mark = (await log()).length;
  await moves();
  ev = await since(mark);
  check('game_api_pause plays nothing', starts(ev).length === 0, JSON.stringify(starts(ev)));
  check('game_api_pause suspends the context', (await app().evaluate(() => window.__logicaAudio.ctx.state)) === 'suspended');
  await app().evaluate(() => window.__ya.emit('game_api_resume'));
  await wait(300);
  mark = (await log()).length;
  await moves();
  check('game_api_resume brings the sound back', audible(await since(mark)).length >= 1);

  // 5. A hidden tab plays nothing; visible again, the moves sound.
  mark = (await log()).length;
  await app().evaluate(() => window.__setHidden(true));
  await wait(300);
  await moves();
  ev = await since(mark);
  check('a hidden tab plays nothing', starts(ev).length === 0 && ev.some((e) => e.ev === 'suspend'), JSON.stringify(starts(ev)));
  await app().evaluate(() => window.__setHidden(false));
  await wait(300);
  mark = (await log()).length;
  await moves();
  check('visible again, the moves sound', audible(await since(mark)).length >= 1);

  // 6. A fullscreen (rewarded) ad: silent while it holds the screen.
  await reload();
  await tap(...STORE_TAB, 1500); await snap('store');
  await tap(...STORE_GEM_AD, 300); await snap('ad');
  mark = (await log()).length;
  const during = await app().evaluate(() => {
    const a = window.__logicaAudio;
    if (a.ctx) a.play(Object.keys(a.buffers)[0] || '', 0.1, 300);
    return { allowed: a.allowed, ctx: a.ctx && a.ctx.state };
  });
  ev = await since(mark);
  check('a fullscreen ad plays nothing', !during.allowed && starts(ev).length === 0, JSON.stringify(during));
  await wait(2500);
  check('after the ad sound is allowed again', await app().evaluate(() => window.__logicaAudio.allowed));

  // 7. The Sound setting off: nothing starts; back on: the next moves sound.
  await reload();
  await toSettings();
  mark = (await log()).length;
  await into2048();
  await moves();
  ev = await since(mark);
  check('Sound off plays nothing', starts(ev).length === 0, JSON.stringify(starts(ev)));
  await reload();
  await toSettings();
  await into2048();
  mark = (await log()).length;
  await moves();
  check('Sound back on: the next moves sound', audible(await since(mark)).length >= 1);
  check('no sound warnings', warnings.length === 0, warnings.join(' | '));
} else {
// 1. Sound on: the sounds load from the nested folder and every move starts one decoded buffer.
await into2048();
mark = (await log()).length;
await moves();
ev = await since(0);
const decoded = ev.filter((e) => e.ev === 'decoded');
check('all 8 sounds decoded from the nested folder', decoded.length === 8 && decoded.every((e) => e.from.includes(PREFIX) && !e.from.includes('index.html')), decoded.map((e) => e.sound).join(','));
played = starts(await since(mark));
check('moves start decoded buffers, one per move', played.length >= 1 && played.length <= 6 && played.every((e) => e.decoded && ['tap.wav', 'merge.wav'].includes(e.sound)), played.map((e) => e.sound).join(','));
check('no failed sound requests', !failed.some((u) => u.includes('.wav')), failed.join(' '));

// 2. A hidden tab, then a lost focus: the context suspends and moves play nothing.
mark = (await log()).length;
await app().evaluate(() => window.__setHidden(true));
await wait(300);
await moves();
ev = await since(mark);
check('hidden tab suspends the context', ev.some((e) => e.ev === 'suspend'));
check('hidden tab plays nothing', starts(ev).length === 0, starts(ev).map((e) => e.sound).join(','));
// Keys (above) and a tap while the tab is still hidden (sound not allowed) must not wake the context.
await page.mouse.click(195, 120);
await wait(300);
ev = await since(mark);
const hiddenState = await app().evaluate(() => window.__logicaAudio.ctx.state);
check('keys and taps do not resume a context the host has not allowed', !ev.some((e) => e.ev === 'resume') && hiddenState === 'suspended', `state=${hiddenState} ${JSON.stringify(ev.filter((e) => e.ev !== 'start'))}`);
await app().evaluate(() => window.__setHidden(false));
await tap(195, 120, 500);
// A page without window focus (none given, no blur event: the Yandex iframe at start) keeps its sound.
mark = (await log()).length;
const unfocusedAllowed = await app().evaluate(() => {
  document.hasFocus = () => false;
  const a = window.__logicaAudio;
  a.play(Object.keys(a.buffers)[0], 0.1, 300);
  return a.allowed;
});
await wait(200);
ev = await since(mark);
check('a page without window focus keeps its sound', unfocusedAllowed && starts(ev).length >= 1 && !ev.some((e) => e.ev === 'suspend'), JSON.stringify(ev));
await app().evaluate(() => { delete document.hasFocus; });

// A window blur silences it (requirement 1.3) until the next tap, and the first move after that sounds.
mark = (await log()).length;
await app().evaluate(() => window.dispatchEvent(new Event('blur')));
await wait(300);
const blurredAllowed = await app().evaluate(() => {
  const a = window.__logicaAudio;
  a.play(Object.keys(a.buffers)[0], 0.1, 300);
  return a.allowed;
});
ev = await since(mark);
check('a window blur silences the game', !blurredAllowed && starts(ev).length === 0 && ev.some((e) => e.ev === 'suspend'), JSON.stringify(ev));
await tap(195, 120, 300);
mark = (await log()).length;
// A key may hit a direction the board cannot move in, so the first sound of the next moves counts.
await moves();
ev = await since(mark);
check('the first move after a tap sounds again', starts(ev).length >= 1 && starts(ev)[0].ctx === 'running', JSON.stringify(starts(ev).slice(0, 2)));

// 3. A sound waiting for its file plays only if the file is ready within ~300 ms.
await page.route('**/late-*.wav', async (route) => {
  const delay = route.request().url().includes('late-fast') ? 80 : 900;
  await new Promise((r) => setTimeout(r, delay));
  route.fulfill({ status: 200, contentType: 'audio/wav', body: fs.readFileSync(path.join(site, 'composeResources/com.stanisryz.logica.shared.ui.generated.resources/files/sounds/tap.wav')) });
});
mark = (await log()).length;
await app().evaluate(() => { const a = window.__logicaAudio; a.play(new URL('late-fast.wav', document.baseURI).href, 0.1, 300); a.play(new URL('late-slow.wav', document.baseURI).href, 0.1, 300); });
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

}

await snap('end');
const all = await log();
console.log('\nlog (last 40):');
for (const e of all.slice(-40)) console.log(JSON.stringify(e));
await browser.close();
server.close();
if (YANDEX) portal.close();
const bad = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - bad}/${results.length} checks passed`);
process.exit(bad ? 1 : 0);
