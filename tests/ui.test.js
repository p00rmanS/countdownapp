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
test('play tray: treat, feed, ball and tickle raise the joy meter', () => {
  go('#/c/sample-bali'); const c = A.S.get('sample-bali'); delete c.joy;
  assert.strictEqual(doc.querySelectorAll('[data-play]').length, 5);
  A.ui.sceneApi.play.tickle(); assert.ok(c.joy.v > 20 && doc.querySelector('.dog.tickle'));
  const v = c.joy.v; A.ui.sceneApi.play.feed(); assert.ok(c.joy.v > v && doc.querySelector('.bowl'));
  A.ui.sceneApi.play.ball(); A.ui.sceneApi.play.treat();
  assert.ok(doc.querySelector('[data-joy-text]').textContent.includes(c.dog.name));
});
test('profile photo from a phone backup (a file name, not an image) falls back to the dog', () => {
  A.S.set('profilePhoto', 'abc123.jpg'); assert.ok(!go('#/').includes('<img class="avatar"')); A.S.set('profilePhoto', '');
});
test('empty Memories: Scott can be petted and played with', () => {
  const keep = A.S.state.countdowns; A.S.state.countdowns = [];
  const h = go('#/memories'); assert.ok(h.includes('No memories yet') && doc.querySelector('[data-dog]') && doc.querySelectorAll('[data-play]').length === 5);
  A.ui.sceneApi.pet(); assert.ok(doc.querySelector('.dog.petting')); A.ui.sceneApi.play.tickle();
  A.S.state.countdowns = keep; go('#/');
});
test('hostile shared links are neutralised (no markup injection, no crash on unknown values)', () => {
  const evil = { title: 'x', targetAt: '2031-01-01T10:00', type: 'nope', accent: 'red" onmouseover="alert(1)', displayMode: 'zzz', timeZone: 'Mars/Base', dog: { breed: 'x" onload="1', name: '<img src=x onerror=alert(1)>' }, notes: 'n', memoryPhotos: ['javascript:alert(1)'] };
  const link = 'https://p00rmans.github.io/countdownapp/#/import/' + w.btoa(unescape(encodeURIComponent(JSON.stringify(evil)))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const id = A.importShared(link); assert.ok(id); const c = A.S.get(id);
  assert.strictEqual(c.type, 'custom'); assert.ok(/^#[0-9a-f]{6}$/i.test(c.accent)); assert.strictEqual(c.dog.breed, 'mutt'); assert.strictEqual(c.memoryPhotos.length, 0);
  const h = go('#/c/' + id); for (const bad of ['onmouseover', 'onload="1']) assert.ok(!h.includes(bad), bad + ' leaked');
  assert.strictEqual(doc.querySelectorAll('#view img[src="x"], #view [onerror], #view [onmouseover], #view [onload]').length, 0);
  assert.strictEqual(A.importShared('#/import/' + w.btoa('{"title":"a"}')), null);
  A.S.remove(id);
});
test('needs: water and food fill up, and the dog calls you Mama or Papa', () => {
  go('#/c/sample-bali'); const c = A.S.get('sample-bali'); c.needs = { food: 10, water: 10, t: Date.now() };
  A.ui.sceneApi.play.drink(); assert.ok(c.needs.water > 60 && c.needs.food <= 12);
  A.ui.sceneApi.play.feed(); assert.ok(c.needs.food > 50);
  A.S.set('parentTitle', 'mama'); assert.strictEqual(A.P.callName(), 'Mama'); assert.ok(/Mama/.test(A.P.dogLine(c, A.P.compute(c), 'thirsty') + A.P.dogLine(c, A.P.compute(c), 'hungry') + 'Mama'));
  A.S.set('parentTitle', 'papa'); assert.strictEqual(A.P.callName(), 'Papa'); A.S.set('parentTitle', ''); assert.strictEqual(A.P.callName(), 'hooman');
  assert.ok(go('#/settings').includes('data-action="set-parent"'));
});
test('Scott has lots of lines for every kind of play, and none say "best human"', () => {
  const c = A.S.get('sample-bali'), k = A.P.compute(c); A.S.set('parentTitle', 'papa');
  for (const kind of ['treat', 'feed', 'drink', 'ball', 'tickle', 'pet', 'love']) {
    assert.ok(A.P.LINES[kind].length >= 12, kind);
    for (let i = 0; i < 40; i++) { const l = A.P.dogLine(c, k, kind); assert.ok(!/best human/i.test(l) && !l.includes('{who}'), l); }
  }
  assert.ok(A.P.LINES.love.some((l) => /I love you/.test(l)) && /Papa/.test(A.P.LINES.love.map((l) => l.replace('{who}', A.P.callName())).join(' ')));
  A.S.set('parentTitle', '');
});
test('new fur-parent types: gotcha day and vet visit work end to end', () => {
  const mk = (type, title) => A.P.cleanCountdown({ title, type, targetAt: '2030-05-01T10:00', dog: { breed: 'scott', name: 'Scott' } });
  const g = mk('gotcha', "Scott's gotcha day"), v = mk('vet', "Scott's checkup");
  assert.strictEqual(g.type, 'gotcha'); assert.strictEqual(v.type, 'vet');
  for (const c of [g, v]) { A.S.upsert(c); const h = go('#/c/' + c.id); assert.ok(h.includes(c.title) && h.includes('data-dog')); A.S.remove(c.id); }
  assert.ok(go('#/new').includes('Gotcha day') && doc.querySelectorAll('.type-card').length === 8);
});
test('care streak counts days in a row; hunger & thirst can be switched off; settings survive a backup', () => {
  const key = (n) => { const d = new w.Date(); d.setDate(d.getDate() - n); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
  A.S.state.careDays = [key(3), key(2), key(1)]; A.S.save();   // today still open: yesterday keeps three days alive
  assert.ok(go('#/').includes('3-day care streak'));
  A.S.state.careDays = [key(5), key(4), key(1)]; A.S.save(); assert.ok(!go('#/').includes('care streak'));
  go('#/c/sample-bali'); A.ui.sceneApi.play.treat();
  A.S.set('needsOn', false); assert.ok(go('#/c/sample-bali').includes('class="needs"') && doc.querySelector('.needs[hidden]'));
  assert.ok(go('#/settings').includes('Hunger &amp; thirst'));
  A.S.set('careTime', '21:00'); const b = A.S.exportJSON(); A.S.set('careTime', '08:00'); A.S.importJSON(b);
  assert.strictEqual(A.S.state.settings.careTime, '21:00'); assert.strictEqual(A.S.state.settings.needsOn, false);
  A.S.importJSON(JSON.stringify({ data: { countdowns: [], settings: { careTime: 'zzz', needsOn: 'yes' }, careDays: ['bad', '2030-01-01'] } }));
  assert.strictEqual(A.S.state.settings.careTime, '18:00'); assert.strictEqual(A.S.state.settings.needsOn, true); assert.strictEqual(A.S.state.careDays.length, 1);
  A.S.state.careDays = []; A.S.loadSamples();
});
test('pin puts a countdown first on Home; duplicate makes a fresh copy to edit', () => {
  const heroOf = () => { go('#/'); return (doc.querySelector('.hero-card .card-title, .hero-card h2, .hero-card [class*="title"]') || {}).textContent || ''; };
  go('#/c/sample-ski'); A.actions.pin(); assert.ok(A.S.get('sample-ski').pinned);
  const html = go('#/'); assert.ok(html.indexOf('Ski weekend') < html.indexOf('Bali getaway') && html.includes('Pinned'));
  go('#/c/sample-ski'); A.actions.pin(); assert.ok(!A.S.get('sample-ski').pinned);
  go('#/c/sample-bali'); const before = A.S.state.countdowns.length; A.S.get('sample-bali').memoryPhotos = ['data:image/jpeg;base64,AAAA'];
  A.actions.duplicate(); assert.strictEqual(A.S.state.countdowns.length, before + 1);
  const copy = A.S.state.countdowns[A.S.state.countdowns.length - 1];
  assert.ok(copy.title.endsWith('(copy)') && copy.id !== 'sample-bali' && copy.memoryPhotos.length === 0 && !copy.sample && !copy.pinned);
  A.S.remove(copy.id); A.S.get('sample-bali').memoryPhotos = [];
});
test('monthly repeat: pick it in the flow, it shows on the countdown and survives import', () => {
  const c = A.P.cleanCountdown({ title: 'Flea & tick', type: 'vet', targetAt: '2030-01-31T09:00', recurrence: 'monthly', dog: { breed: 'scott', name: 'Scott' } });
  assert.strictEqual(c.recurrence, 'monthly'); A.S.upsert(c);
  assert.ok(go('#/c/' + c.id).includes('Repeats every month'));
  go('#/edit/' + c.id); A.ui.step = 1; A.render(); assert.ok(doc.querySelector('[data-action="set-recur"][data-v="monthly"].on'));
  A.actions['set-recur']({ dataset: { v: 'yearly' } }); assert.strictEqual(A.ui.draft.recurrence, 'yearly');
  A.actions['set-recur']({ dataset: { v: 'nonsense' } }); assert.strictEqual(A.ui.draft.recurrence, 'none');
  A.ui.draft = null; A.S.remove(c.id); go('#/');
});
test('My dog: name and birthday show an age and make a yearly birthday countdown', () => {
  const y = new w.Date().getFullYear() - 2, bday = y + '-01-02';
  A.S.set('dogName', 'Scott'); A.S.set('dogBirthday', bday);
  const s = go('#/settings'); assert.ok(s.includes('data-dog-name') && s.includes('Scott</b> is 2 years') && s.includes('dog-bday-add'));
  const before = A.S.state.countdowns.length; A.actions['dog-bday-add']();
  assert.strictEqual(A.S.state.countdowns.length, before + 1);
  const c = A.S.state.countdowns[A.S.state.countdowns.length - 1]; assert.ok(c.title === "Scott's birthday" && c.recurrence === 'yearly' && c.allDay && c.type === 'birthday');
  A.actions['dog-bday-add'](); assert.strictEqual(A.S.state.countdowns.length, before + 1);   // no duplicates
  A.S.remove(c.id); A.S.importJSON(JSON.stringify({ data: { countdowns: [], settings: { dogBirthday: '2999-01-01', dogName: 'x'.repeat(99) } } }));
  assert.strictEqual(A.S.state.settings.dogBirthday, ''); assert.strictEqual(A.S.state.settings.dogName.length, 30);
  A.S.state.settings.dogName = ''; A.S.loadSamples();
});
test('welcome asks who the person is (name + Mama/Papa) before the reminders step', () => {
  A.ui.welcome = 2; go('#/welcome'); const h = doc.querySelector('#view').innerHTML;
  assert.ok(h.includes('data-profile-name') && h.includes('data-action="set-parent"') && h.includes('Step 3 of 4'));
  A.actions['set-parent']({ dataset: { v: 'papa' } }); assert.strictEqual(A.P.callName(), 'Papa'); A.S.set('parentTitle', '');
  A.ui.welcome = 3; go('#/welcome'); assert.ok(doc.querySelector('#view').innerHTML.includes('ring the bell') && doc.querySelector('[data-action="w-bell"]'));
  A.ui.welcome = 0; go('#/');
});
test('no script errors were thrown', () => assert.deepStrictEqual(errors, []));

console.log(`\n${passed} passed`);
process.exit(process.exitCode || 0);
