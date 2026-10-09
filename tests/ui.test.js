/* Smoke test for every screen, plus the create-flow, the dog interactions and the back stack.
   Loads index.html + scripts into jsdom (a fake browser) and drives the app through its own API. */
const { JSDOM, VirtualConsole } = require('jsdom');
const assert = require('assert');
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');

const errors = [];
const vc = new VirtualConsole();
vc.on('jsdomError', (e) => errors.push(e.message));
const dom = new JSDOM(fs.readFileSync(path.join(root, 'index.html'), 'utf8'), { url: 'http://localhost/', runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: vc });
const w = dom.window;
w.matchMedia = w.matchMedia || (() => ({ matches: false, addEventListener() {}, addListener() {} }));
w.Element.prototype.scrollIntoView = function () {};
['util', 'countdown', 'store', 'dogs', 'scenes', 'fx', 'native', 'app'].forEach((f) => {
  try { w.eval(fs.readFileSync(path.join(root, 'js', f + '.js'), 'utf8')); } catch (e) { errors.push(f + ': ' + e.message); }
});
const A = w.Pawcount, doc = w.document;
const go = (h) => { w.location.hash = h; A.render(); return doc.querySelector('#view').innerHTML; };

let passed = 0;
function test(name, fn) { try { fn(); passed++; console.log('  ok  ', name); } catch (e) { console.error('  FAIL', name, '\n      ', e.message); process.exitCode = 1; } }

test('first launch shows the welcome flow', () => assert.ok(go('#/welcome').includes('can\'t wait')));
test('sample adventures load', () => { A.S.loadSamples(); assert.strictEqual(A.S.state.countdowns.length, 7); });
test('home has a hero card and a grid', () => { const h = go('#/'); assert.ok(h.includes('hero-card') && h.includes('class="grid"')); });
['#/memories', '#/settings', '#/new', '#/c/sample-japan', '#/c/sample-ski', '#/c/sample-lisbon', '#/c/sample-dad', '#/edit/sample-mia']
  .forEach((h) => test('renders ' + h, () => assert.ok(go(h).length > 500)));

test('every dog has a full detail page', () => {
  A.S.state.countdowns.forEach((c) => { const h = go('#/c/' + c.id); assert.ok(h.includes(c.dog.name), c.id); });
});
test('detail page has the interactive dog button', () => { go('#/c/sample-bali'); assert.ok(doc.querySelector('[data-dog]')); });
test('tapping the dog button makes it bark', () => {
  go('#/c/sample-bali');
  A.ui.sceneApi.bark();
  assert.ok(doc.querySelector('.dog.bark'));
});
test('shake triggers the sneeze', () => { A.ui.sceneApi.shake(); assert.ok(doc.querySelector('.dog.sneeze')); });
test('pet shows the petting pose', () => { A.ui.sceneApi.pet(); assert.ok(doc.querySelector('.dog.petting')); });

test('create flow saves a countdown', () => {
  const before = A.S.state.countdowns.length;
  go('#/new'); A.ui.draft.title = 'Test trip'; A.render({ noAnim: true });
  for (let i = 0; i < 5; i++) doc.getElementById('flowNext').click();
  assert.strictEqual(A.S.state.countdowns.length, before + 1);
});
test('the create flow refuses a past date', () => {
  go('#/new'); A.ui.draft.title = 'x'; A.ui.draft.date = '2001-01-01'; A.ui.step = 1; A.render({ noAnim: true });
  assert.strictEqual(doc.getElementById('flowNext').disabled, true);
});
test('birthdays may use a past date because they repeat', () => {
  go('#/new'); const d = A.ui.draft; d.type = 'birthday'; d.recurrence = 'yearly'; d.title = 'M'; d.date = '1995-04-02'; A.ui.step = 1; A.render({ noAnim: true });
  assert.strictEqual(doc.getElementById('flowNext').disabled, false);
});
test('back stack remembers screens', () => { assert.ok(A.ui.stack.length >= 1); });

test('backup export / import round trip', () => {
  const json = A.S.exportJSON(); A.S.reset(); assert.strictEqual(A.S.state.countdowns.length, 0);
  A.S.importJSON(json); assert.ok(A.S.state.countdowns.length >= 7);
});
test('bad backup is rejected', () => assert.throws(() => A.S.importJSON('{"x":1}')));
test('reminder plan: at most one per day, in the future', () => {
  const plan = A.P.native.plan(); const days = plan.map((n) => new w.Date(n.at).toDateString());
  assert.strictEqual(new Set(days).size, days.length); assert.ok(plan.every((n) => n.at > Date.now()));
});
test('a shared link adds a copy of the countdown', () => {
  const c = A.S.get('sample-japan'); const before = A.S.state.countdowns.length;
  const link = A.shareLink(c); assert.ok(link.includes('#/import/'));
  const id = A.importShared(link);
  assert.ok(id && id !== c.id); assert.strictEqual(A.S.state.countdowns.length, before + 1);
  const copy = A.S.get(id); assert.strictEqual(copy.title, c.title); assert.strictEqual(copy.timeZone, c.timeZone); assert.strictEqual(copy.dog.breed, c.dog.breed);
  assert.strictEqual(A.importShared('https://example.com/nothing'), null);
});
test('profile: name shows in the greeting, photo replaces the dog avatar, both survive a backup', () => {
  assert.ok(go('#/').includes('class="avatar"') && !go('#/').includes('<img class="avatar"'));
  A.S.set('profileName', 'Mia'); A.S.set('profilePhoto', 'data:image/jpeg;base64,AAAA');
  assert.ok(go('#/').includes(', Mia</h1>'));
  assert.ok(go('#/').includes('<img class="avatar"'));
  const s = go('#/settings'); assert.ok(s.includes('data-profile-name') && s.includes('Remove photo'));
  const backup = A.S.exportJSON(); A.S.set('profileName', ''); A.S.importJSON(backup);
  assert.strictEqual(A.S.state.settings.profileName, 'Mia'); assert.strictEqual(A.S.state.settings.profilePhoto, 'data:image/jpeg;base64,AAAA');
  A.S.set('profilePhoto', ''); A.S.set('profileName', '');
});
test('no script errors were thrown', () => assert.deepStrictEqual(errors, []));

console.log(`\n${passed} passed`);
process.exit(process.exitCode || 0);
