/* Checks that the native apps stay in step with the web app's breed list and the exported art. */
const assert = require('assert');
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
global.window = {};
['util', 'countdown', 'dogs'].forEach((f) => require(path.join(root, 'js', f + '.js')));
const P = window.Paw;
let passed = 0;
function test(name, fn) { try { fn(); passed++; console.log('  ok  ', name); } catch (e) { console.error('  FAIL', name, '\n      ', e.message); process.exitCode = 1; } }

const kotlin = fs.readFileSync(path.join(root, 'apps/android/app/src/main/kotlin/app/pawcount/Model.kt'), 'utf8');
const swift = fs.readFileSync(path.join(root, 'apps/ios/Pawcount/Model.swift'), 'utf8');
const listOf = (src, re) => (re.exec(src) || [, ''])[1].split(',').map((x) => x.trim().replace(/"/g, '')).filter(Boolean);
const kBreeds = listOf(kotlin, /BREED_KEYS = setOf\(([^)]*)\)/), sBreeds = listOf(swift, /let breeds: Set<String> = \[([^\]]*)\]/);

test('every breed is allowed by the Android and iPhone import check (or an imported dog turns into a mutt)', () => {
  assert.deepStrictEqual(P.BREED_ORDER.filter((b) => !kBreeds.includes(b)), [], 'missing on Android');
  assert.deepStrictEqual(P.BREED_ORDER.filter((b) => !sBreeds.includes(b)), [], 'missing on iPhone');
});
test('every breed has its exported drawing file with every pose', () => {
  const variants = P.BREED_ORDER.filter((b) => b !== 'mutt');
  for (const v of variants) {
    const f = path.join(root, 'native/assets', `dog-${v}.json`); assert.ok(fs.existsSync(f), 'missing ' + f);
    const g = JSON.parse(fs.readFileSync(f, 'utf8'));
    for (const k of ['nap', 'memory', 'curious', 'waiting', 'zoomies', 'bell', 'icon', 'packing/custom', 'today/vet']) assert.ok(g[`${v}/${k}`], `${v}/${k}`);
  }
  ['floppy', 'pointy', 'tall', 'long'].forEach((e) => assert.ok(fs.existsSync(path.join(root, 'native/assets', `dog-mutt-${e}.json`)), 'mutt-' + e));
  assert.ok(!fs.existsSync(path.join(root, 'native/assets/dogs.json')), 'the old single dogs.json should be gone');
});
test('meta.json lists the same breeds, in the same order', () => {
  const m = JSON.parse(fs.readFileSync(path.join(root, 'native/assets/meta.json'), 'utf8'));
  assert.deepStrictEqual(m.breedOrder, P.BREED_ORDER); assert.ok(Object.keys(m.breeds).length === P.BREED_ORDER.length);
});
console.log(`\n${passed} passed`);
