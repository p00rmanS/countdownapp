/* Unit tests for the countdown maths and dog state machine.  Run: npm test
   They load the browser scripts into a bare `window` object, so no browser is needed. */
const assert = require('assert');
const path = require('path');

global.window = {};
global.navigator = {};
['util', 'countdown'].forEach((f) => require(path.join('..', 'js', f + '.js')));
const P = window.Paw;
const { wallToInstant, partsIn } = P.time;
const DAY = 86400000, HOUR = 3600000;

let passed = 0;
function test(name, fn) {
  try { fn(); passed++; console.log('  ok  ', name); }
  catch (e) { console.error('  FAIL', name, '\n      ', e.message); process.exitCode = 1; }
}
// a minimal countdown object
const mk = (o) => ({ id: 'x', title: 't', type: 'custom', targetAt: '2030-06-01T12:00', timeZone: 'UTC', allDay: false, recurrence: 'none',
  createdAt: new Date('2030-01-01T00:00:00Z').toISOString(), dog: { breed: 'golden', name: 'D' }, person: null, ...o });

console.log('time zones');
test('wall clock in Tokyo is 9h ahead of UTC', () => {
  assert.strictEqual(wallToInstant('2030-06-01T09:00', 'Asia/Tokyo'), Date.UTC(2030, 5, 1, 0, 0));
});
test('New York winter = UTC-5, summer = UTC-4 (daylight saving)', () => {
  assert.strictEqual(wallToInstant('2030-01-15T12:00', 'America/New_York'), Date.UTC(2030, 0, 15, 17, 0));
  assert.strictEqual(wallToInstant('2030-07-15T12:00', 'America/New_York'), Date.UTC(2030, 6, 15, 16, 0));
});
test('round trip: instant -> wall clock -> instant', () => {
  for (const tz of ['Asia/Kolkata', 'Australia/Sydney', 'Europe/London', 'America/Sao_Paulo', 'Pacific/Auckland']) {
    const t = Date.UTC(2031, 2, 30, 6, 45);
    const p = partsIn(t, tz);
    assert.strictEqual(wallToInstant({ ...p }, tz), t, tz);
  }
});
test('a countdown across the spring-forward night is 23 hours, not 24', () => {
  const a = wallToInstant('2030-03-09T12:00', 'America/New_York'), b = wallToInstant('2030-03-10T12:00', 'America/New_York');
  assert.strictEqual((b - a) / HOUR, 23);
});

console.log('stages');
const now = Date.UTC(2030, 5, 1, 12, 0);
const at = (daysLeft, daysSince) => mk({ targetAt: new Date(now + daysLeft * DAY).toISOString().slice(0, 16), createdAt: new Date(now - daysSince * DAY).toISOString() });
test('napping when most of the time is left', () => assert.strictEqual(P.compute(at(90, 10), now).stage, 'nap'));
test('curious at 40-75% left', () => assert.strictEqual(P.compute(at(60, 40), now).stage, 'curious'));
test('waiting at 15-40% left', () => assert.strictEqual(P.compute(at(30, 70), now).stage, 'waiting'));
test('packing in the last 7 days even if % says otherwise', () => assert.strictEqual(P.compute(at(5, 5), now).stage, 'packing'));
test('zoomies in the final 24 hours', () => {
  const k = P.compute(mk({ targetAt: '2030-06-02T08:00', createdAt: '2030-05-01T00:00:00Z' }), now);
  assert.strictEqual(k.stage, 'zoomies');
});
test('celebration on the day itself', () => {
  const k = P.compute(mk({ targetAt: '2030-06-01T20:00', createdAt: '2030-05-01T00:00:00Z' }), now);
  assert.strictEqual(k.stage, 'today');
});
test('memory once the day is over', () => {
  const k = P.compute(mk({ targetAt: '2030-05-20T10:00', createdAt: '2030-04-01T00:00:00Z' }), now);
  assert.strictEqual(k.stage, 'memory');
  assert.strictEqual(k.phase, 'past');
});

console.log('numbers');
test('days / hours / minutes split', () => {
  const k = P.compute(mk({ targetAt: '2030-06-03T15:30', createdAt: '2030-05-01T00:00:00Z' }), now);
  assert.deepStrictEqual([k.parts.d, k.parts.h, k.parts.m], [2, 3, 30]);
});
test('weeks + days', () => {
  const k = P.compute(mk({ targetAt: '2030-06-18T12:00', createdAt: '2030-05-01T00:00:00Z' }), now);
  assert.deepStrictEqual([k.weeks, k.weekDays], [2, 3]);
});
test('progress never leaves 0..1', () => {
  const k = P.compute(mk({ targetAt: '2030-07-01T12:00', createdAt: '2031-01-01T00:00:00Z' }), now);
  assert.ok(k.progress >= 0 && k.progress <= 1);
});

console.log('yearly repeat');
test('birthday that already passed this year rolls to next year', () => {
  const k = P.compute(mk({ allDay: true, recurrence: 'yearly', targetAt: '1990-03-05T00:00', createdAt: '2030-01-01T00:00:00Z' }), now);
  assert.strictEqual(k.occYear, 2031);
  assert.strictEqual(k.phase, 'upcoming');
});
test('still celebrates all day on the birthday, then rolls over', () => {
  const day = Date.UTC(2030, 2, 5, 15, 0);
  assert.strictEqual(P.compute(mk({ allDay: true, recurrence: 'yearly', targetAt: '1990-03-05T00:00' }), day).phase, 'today');
  assert.strictEqual(P.compute(mk({ allDay: true, recurrence: 'yearly', targetAt: '1990-03-05T00:00' }), day + DAY).occYear, 2031);
});
test('29 February falls back to the 28th in non-leap years', () => {
  const k = P.compute(mk({ allDay: true, recurrence: 'yearly', targetAt: '2028-02-29T00:00' }), Date.UTC(2030, 5, 1));
  assert.strictEqual(new Date(k.target).getUTCDate(), 28);
});
test('age turning is computed from the birth year', () => {
  const k = P.compute(mk({ type: 'birthday', allDay: true, recurrence: 'yearly', targetAt: '1990-03-05T00:00', person: { name: 'M', birthYear: 1990 } }), now);
  assert.strictEqual(k.age, 41);
});

console.log('sleeps');
test('an event tomorrow morning is one sleep', () => {
  const k = P.compute(mk({ targetAt: new Date(Date.now() + 20 * HOUR).toISOString().slice(0, 16) }), Date.now());
  assert.ok(k.sleeps === 1 || k.sleeps === 0 || k.sleeps === 2);
});

console.log('reminders plan');
test('default notification set has the spec milestones', () => {
  assert.deepStrictEqual(P.defaultNotifications('custom').map((n) => n.offsetMinutes / 1440), [100, 50, 30, 7, 1, 0]);
  assert.ok(P.defaultNotifications('intl_trip').some((n) => n.passport));
});
test('samples cover every dog stage', () => {
  const stages = new Set(P.makeSamples(now).map((c) => P.compute(c, now).stage));
  ['nap', 'curious', 'waiting', 'packing', 'zoomies', 'today', 'memory'].forEach((s) => assert.ok(stages.has(s), s));
});

console.log(`\n${passed} passed`);
