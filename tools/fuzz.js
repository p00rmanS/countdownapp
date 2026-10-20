/* Stress test: throws odd data at the shared logic and the drawings and reports anything that throws or produces NaN.
 *   node tools/fuzz.js
 */
const { JSDOM } = require('jsdom');
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost/', runScripts: 'outside-only' });
const w = dom.window;
['util', 'countdown', 'store', 'dogs', 'scenes'].forEach((f) => w.eval(fs.readFileSync(path.join(root, 'js', f + '.js'), 'utf8')));
const P = w.Paw;
let bad = 0, checks = 0;
const fail = (m) => { bad++; if (bad <= 40) console.error('  PROBLEM', m); };
const ok = (c, m) => { checks++; if (!c) fail(m); };

// 1. every breed x stage x type draws, and the svg is well formed
const parser = new w.DOMParser();
for (const breed of Object.keys(P.BREEDS)) {
  for (const stage of ['nap', 'memory', 'curious', 'waiting', 'packing', 'zoomies', 'today']) {
    for (const type of P.TYPE_ORDER) {
      let svg; try { svg = P.dogSVG({ breed, name: 'X', ears: 'floppy' }, stage, { type, accent: '#E8A15C', bell: true }); } catch (e) { fail(`dogSVG ${breed}/${stage}/${type}: ${e.message}`); continue; }
      const doc = parser.parseFromString(svg, 'image/svg+xml');
      ok(!doc.querySelector('parsererror'), `bad svg ${breed}/${stage}/${type}`);
      ok(!/NaN|undefined|\[object/.test(svg), `NaN/undefined in svg ${breed}/${stage}/${type}`);
    }
  }
  ok(!parser.parseFromString(P.dogIconSVG(breed), 'image/svg+xml').querySelector('parsererror'), 'icon ' + breed);
}
for (const type of P.TYPE_ORDER) for (const door of [true, false]) {
  const s = P.sceneBgSVG(type, '#5DADE8', door);
  ok(!parser.parseFromString(s, 'image/svg+xml').querySelector('parsererror') && !/NaN|undefined/.test(s), `scene ${type}/${door}`);
}

// 2. compute() over many odd inputs
const tzs = ['UTC', 'Asia/Tokyo', 'America/New_York', 'Pacific/Auckland', 'Pacific/Kiritimati', 'Asia/Kolkata', 'Europe/London', 'America/Sao_Paulo', 'Pacific/Pago_Pago'];
const recs = ['none', 'weekly', 'monthly', 'yearly'];
const targets = ['2024-02-29T00:00', '2030-12-31T23:59', '2000-01-01T00:00', '2031-01-31T09:30', '2027-03-14T02:30', '2027-11-07T01:30', '2999-12-31T23:59', '2026-10-19T00:00'];
const nows = [Date.UTC(2026, 9, 19, 12), Date.UTC(2028, 1, 29, 23, 59, 59), Date.UTC(2027, 2, 14, 7), Date.UTC(2035, 5, 1), Date.UTC(2026, 9, 19, 0, 0, 0), 0];
for (const tz of tzs) for (const rec of recs) for (const targetAt of targets) for (const allDay of [true, false]) for (const now of nows) {
  const c = { id: 'f', title: 't', type: 'custom', targetAt, timeZone: tz, allDay, recurrence: rec, createdAt: new Date(now - 40 * 864e5).toISOString(), dog: { breed: 'scott', name: 'D' } };
  let k; try { k = P.compute(c, now); } catch (e) { fail(`compute threw ${tz} ${rec} ${targetAt} ${allDay} ${now}: ${e.message}`); continue; }
  const nums = [k.target, k.remaining, k.leftPct, k.progress, k.sleeps, k.daysCeil, k.weeks, k.daysTotal, k.daysIn, k.parts.d, k.parts.h, k.parts.m, k.parts.s];
  ok(nums.every((n) => Number.isFinite(n)), `non-finite number ${tz} ${rec} ${targetAt} ${allDay} ${now}: ${JSON.stringify(nums)}`);
  ok(k.leftPct >= 0 && k.leftPct <= 1 && k.progress >= 0 && k.progress <= 1, `percent out of range ${tz} ${rec} ${targetAt}`);
  ok(k.parts.h < 24 && k.parts.m < 60 && k.parts.s < 60, `time parts out of range ${tz} ${targetAt}`);
  ok(['nap', 'curious', 'waiting', 'packing', 'zoomies', 'today', 'memory'].includes(k.stage), `stage ${k.stage}`);
  if (rec !== 'none' && now < Date.UTC(2090, 0, 1)) ok(k.phase !== 'past' || targetAt.startsWith('2999') === false, `repeating countdown ended: ${tz} ${rec} ${targetAt} ${now}`);
  for (const kind of ['tap', 'pet', 'treat', 'feed', 'drink', 'ball', 'tickle', 'love', 'hungry', 'thirsty', 'sneeze', 'fetch']) {
    const line = P.dogLine(c, k, kind);
    ok(typeof line === 'string' && line.length > 0 && !/undefined|NaN|\{who\}/.test(line), `dog line ${kind}: ${line}`);
  }
  ok(typeof P.spoken(k) === 'string' && !/NaN/.test(P.spoken(k)), 'spoken');
}

// 3. the cleaner never throws on junk, and always returns safe shapes
const junk = [null, undefined, 0, 'x', [], {}, { title: 1 }, { title: 'a', targetAt: '2030-01-01' }, { title: 'a', targetAt: '2030-13-45T99:99' }, { title: 'a'.repeat(500), targetAt: '2030-01-01T10:00', dog: 7, checklist: 'no', notifications: [null, 3, { offsetMinutes: 'x' }], destination: 'x', person: [] },
  { title: 'a', targetAt: '2030-01-01T10:00', timeZone: { a: 1 }, recurrence: ['yearly'], accent: 5, memoryPhotos: [1, null, 'data:image/png;base64,AAAA'] }];
for (const j of junk) { try { const c = P.cleanCountdown(j); if (c) { P.compute(c); P.dogSVG(c.dog, 'waiting', { type: c.type, accent: c.accent }); } checks++; } catch (e) { fail('cleanCountdown/compute threw on ' + JSON.stringify(j) + ': ' + e.message); } }

// 4. dog age edge cases
for (const [iso, now, want] of [['2024-02-29', new Date(2025, 1, 28), '11 months old'], ['2024-01-31', new Date(2024, 2, 1), '1 month old'], ['2020-12-31', new Date(2021, 0, 1), '1 day old']]) {
  const t = P.dogAgeText(P.dogAge(iso, now)); ok(t === want, `dog age ${iso} -> "${t}" want "${want}"`);
}

console.log(`${checks} checks, ${bad} problem${bad === 1 ? '' : 's'}`);
process.exit(bad ? 1 : 0);
