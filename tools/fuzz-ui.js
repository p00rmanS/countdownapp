/* Clicks every button on every screen (except the ones that erase data) and reports script errors.
 *   node tools/fuzz-ui.js
 */
const { JSDOM, VirtualConsole } = require('jsdom');
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const errors = [];
const vc = new VirtualConsole();
vc.on('jsdomError', (e) => errors.push((e.detail && e.detail.message) || e.message));
const dom = new JSDOM(fs.readFileSync(path.join(root, 'index.html'), 'utf8'), { url: 'http://localhost/', runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: vc });
const w = dom.window;
w.matchMedia = w.matchMedia || (() => ({ matches: false, addEventListener() {}, addListener() {} }));
w.Element.prototype.scrollIntoView = function () {};
['util', 'countdown', 'store', 'dogs', 'scenes', 'fx', 'native', 'app'].forEach((f) => { try { w.eval(fs.readFileSync(path.join(root, 'js', f + '.js'), 'utf8')); } catch (e) { errors.push(f + ': ' + e.message); } });
const A = w.Pawcount, doc = w.document;
// things the fake browser (jsdom) cannot do, not app bugs
const LIMITS = /createObjectURL|Window's prompt\(\)|Not implemented: (navigation|HTMLCanvasElement|window\.scrollTo)/;
const SKIP = new Set(['reset-yes', 'delete-yes', 'reset', 'import-yes', 'erase']);
let clicks = 0, screens = 0;
const snapshot = () => JSON.stringify(A.S.state);

A.S.loadSamples();
const routes = ['#/', '#/memories', '#/settings', '#/new', '#/welcome', ...A.S.state.countdowns.flatMap((c) => ['#/c/' + c.id, '#/edit/' + c.id])];
for (const r of routes) {
  const base = snapshot();
  for (let pass = 0; ; pass++) {
    w.location.hash = r; try { A.render(); } catch (e) { errors.push('render ' + r + ': ' + e.message); break; }
    screens++;
    const els = [...doc.querySelectorAll('[data-action], [data-play], .tab, a[href^="#/"]')];
    if (pass >= els.length) break;
    const el = els[pass];
    const act = el.dataset && el.dataset.action;
    if (act && SKIP.has(act)) continue;
    const before = errors.length;
    try { el.dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true })); clicks++; } catch (e) { errors.push(`click ${act || el.className} on ${r}: ${e.message}`); }
    if (errors.length > before) errors[errors.length - 1] += `   [route ${r}, element ${act || el.className || el.tagName}]`;
    // close any sheet the click opened so the next click starts clean
    const close = doc.querySelector('[data-action="close-sheet"]'); if (close) close.dispatchEvent(new w.MouseEvent('click', { bubbles: true }));
    if (pass > 200) break;
  }
  // keep the data intact between routes (some buttons archive / pin / duplicate)
  A.S.importJSON(JSON.stringify({ data: JSON.parse(base) }));
}
for (let i = errors.length - 1; i >= 0; i--) if (LIMITS.test(errors[i])) errors.splice(i, 1);
console.log(`${routes.length} routes, ${screens} renders, ${clicks} clicks, ${errors.length} error${errors.length === 1 ? '' : 's'}`);
[...new Set(errors)].slice(0, 25).forEach((e) => console.error('  ERROR', e));
process.exit(errors.length ? 1 : 0);
