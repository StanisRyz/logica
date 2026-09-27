// Minimal describe/it runner plus a local-file fetch so kotlin-test and Skiko load under plain Node.
const fs = require('fs');
const path = require('path');

const realFetch = globalThis.fetch;
globalThis.fetch = async (url, opts) => {
  const target = String(url);
  if (/^https?:/.test(target)) return realFetch(url, opts);
  const file = target.startsWith('file:') ? new URL(target) : path.resolve(process.cwd(), target);
  return new Response(fs.readFileSync(file), { headers: { 'Content-Type': 'application/wasm' } });
};
// Skiko (Compose graphics) cannot start without a browser; tests never render, so ignore that.
process.on('unhandledRejection', () => {});

const tests = [];
const suites = [];
global.describe = (name, fn) => { suites.push(name); fn(); suites.pop(); };
global.xdescribe = () => {};
global.it = (name, fn) => tests.push({ name: [...suites, name].join(' > '), fn });
global.xit = (name) => tests.push({ name, skip: true });

let finished = false;
process.on('beforeExit', async () => {
  if (finished) return;
  finished = true;
  let passed = 0;
  let failed = 0;
  let skipped = 0;
  for (const test of tests) {
    if (test.skip) { skipped++; continue; }
    try {
      await new Promise((resolve, reject) => {
        if (test.fn.length > 0) test.fn((error) => (error ? reject(error) : resolve()));
        else Promise.resolve(test.fn()).then(resolve, reject);
      });
      passed++;
    } catch (error) {
      failed++;
      console.log(`FAIL ${test.name}\n  ${error && error.message}`);
    }
  }
  console.log(`web tests: passed=${passed} failed=${failed} skipped=${skipped}`);
  process.exitCode = failed ? 1 : 0;
});
