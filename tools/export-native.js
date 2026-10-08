/* Exports the dog and scene drawings (js/dogs.js, js/scenes.js) as JSON "scene graphs" that the Kotlin and
 * Swift apps draw natively, so all platforms show exactly the same art from one source.
 *
 *   node tools/export-native.js        -> native/assets/{dogs,scenes,meta}.json
 *
 * Colours that change at runtime stay symbolic: $fur $dark $light $paw (dog coat), $accent (event colour) and
 * mix(a,b,t) blends of them. The apps resolve those when drawing.
 * Output node: { t: 'g'|'ellipse'|'circle'|'rect'|'path'|'text', cls, tf:[["t",x,y],["s",x,y],["r",deg,cx,cy]],
 *                hid, op, clip, fill, stroke, sw, cap, join, dash, ...geometry, ch:[...] }
 */
const fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const root = path.join(__dirname, '..');
global.window = {}; global.navigator = {};
['util', 'countdown', 'dogs', 'scenes'].forEach((f) => require(path.join(root, 'js', f + '.js')));
const P = window.Paw;
P.tokenMode = true;
const parser = new (new JSDOM('').window.DOMParser)();

const num = (v, d = 0) => (v == null || v === '' ? d : parseFloat(v));

function parseTransform(s) {
  if (!s) return null;
  const out = [], re = /(translate|scale|rotate)\(([^)]*)\)/g;
  let m;
  while ((m = re.exec(s))) {
    const v = m[2].trim().split(/[\s,]+/).map(Number);
    if (m[1] === 'translate') out.push(['t', v[0], v[1] || 0]);
    else if (m[1] === 'scale') out.push(['s', v[0], v.length > 1 ? v[1] : v[0]]);
    else out.push(['r', v[0], v[1] || 0, v[2] || 0]);
  }
  return out;
}

// Walks the SVG and returns the node tree. `inh` carries inherited paint (a <g fill=".."> colours its children).
function convert(el, defs, inh) {
  const tag = el.tagName;
  if (['defs', 'linearGradient', 'clipPath', 'style', 'title'].includes(tag)) return null;
  const style = el.getAttribute('style') || '';
  const cur = {
    fill: el.hasAttribute('fill') ? el.getAttribute('fill') : inh.fill,
    stroke: el.hasAttribute('stroke') ? el.getAttribute('stroke') : inh.stroke,
    sw: el.hasAttribute('stroke-width') ? num(el.getAttribute('stroke-width')) : inh.sw,
    cap: el.getAttribute('stroke-linecap') || inh.cap,
    join: el.getAttribute('stroke-linejoin') || inh.join,
    fs: el.hasAttribute('font-size') ? num(el.getAttribute('font-size')) : inh.fs,
    fw: el.getAttribute('font-weight') || inh.fw,
  };
  const n = { t: tag === 'svg' ? 'g' : tag };
  const cls = el.getAttribute('class'); if (cls) n.cls = cls;
  const tf = parseTransform(el.getAttribute('transform')); if (tf) n.tf = tf;
  if (/display\s*:\s*none/.test(style)) n.hid = true;
  if (el.hasAttribute('opacity')) n.op = num(el.getAttribute('opacity'));
  const cp = /url\(#([^)]+)\)/.exec(el.getAttribute('clip-path') || '');
  if (cp && defs.clips[cp[1]]) n.clip = defs.clips[cp[1]];

  const paint = (v) => {
    if (v == null || v === 'none') return null;
    const g = /url\(#([^)]+)\)/.exec(v);
    if (g) return defs.grads[g[1]] || null;
    return v;
  };
  if (tag === 'g' || tag === 'svg') {
    n.ch = [...el.children].map((c) => convert(c, defs, cur)).filter(Boolean);
    return n;
  }
  n.fill = paint(cur.fill == null ? '#000000' : cur.fill); // SVG's default fill is black
  n.stroke = paint(cur.stroke);
  if (n.stroke) { n.sw = cur.sw == null ? 1 : cur.sw; if (cur.cap) n.cap = cur.cap; if (cur.join) n.join = cur.join; }
  const da = el.getAttribute('stroke-dasharray'); if (da) n.dash = da.split(/[\s,]+/).map(Number);
  if (el.hasAttribute('fill-opacity')) n.fop = num(el.getAttribute('fill-opacity'));
  switch (tag) {
    case 'ellipse': Object.assign(n, { cx: num(el.getAttribute('cx')), cy: num(el.getAttribute('cy')), rx: num(el.getAttribute('rx')), ry: num(el.getAttribute('ry')) }); break;
    case 'circle': Object.assign(n, { cx: num(el.getAttribute('cx')), cy: num(el.getAttribute('cy')), r: num(el.getAttribute('r')) }); break;
    case 'rect': Object.assign(n, { x: num(el.getAttribute('x')), y: num(el.getAttribute('y')), w: num(el.getAttribute('width')), h: num(el.getAttribute('height')), rx: num(el.getAttribute('rx')) }); break;
    case 'path': n.d = el.getAttribute('d'); break;
    case 'text': Object.assign(n, { x: num(el.getAttribute('x')), y: num(el.getAttribute('y')), fs: cur.fs || 16, fw: cur.fw || '400', anchor: el.getAttribute('text-anchor') || 'start', s: el.textContent }); break;
    default: return null;
  }
  return n;
}

function toGraph(svgText, opts = {}) {
  const svg = parser.parseFromString(svgText, 'image/svg+xml').documentElement;
  const defs = { grads: {}, clips: {} };
  svg.querySelectorAll('linearGradient').forEach((g) => {
    defs.grads[g.getAttribute('id')] = {
      g: [...g.querySelectorAll('stop')].map((s) => [num(s.getAttribute('offset')), s.getAttribute('stop-color')]),
      x1: num(g.getAttribute('x1')), y1: num(g.getAttribute('y1')), x2: num(g.getAttribute('x2')), y2: num(g.getAttribute('y2'), 1),
    };
  });
  svg.querySelectorAll('clipPath').forEach((c) => { defs.clips[c.getAttribute('id')] = convert(c.firstElementChild, { grads: {}, clips: {} }, {}); });
  const g = convert(svg, defs, {});
  if (opts.dropFirstRect && g.ch[0] && g.ch[0].t === 'rect') g.ch.shift();
  // give the children of repeating groups their own class (zzz-0, zzz-1 ...) so each can animate on its own beat
  (function tag(n) {
    if (!n.ch) return;
    if (n.cls && /^(zzz|hearts-f)$/.test(n.cls)) n.ch.forEach((c, i) => { c.cls = `${n.cls}-${i}`; });
    n.ch.forEach(tag);
  })(g);
  return g;
}

/* ---- dogs: 5 breeds + 4 mutt ear styles, in every stage ---- */
const variants = [];
['golden', 'corgi', 'shiba', 'dachshund', 'husky'].forEach((b) => variants.push([b, { breed: b, name: '' }]));
['floppy', 'pointy', 'tall', 'long'].forEach((e) => variants.push(['mutt-' + e, { breed: 'mutt', name: '', ears: e }]));
const dogs = {};
for (const [key, dog] of variants) {
  for (const stage of ['nap', 'memory', 'curious', 'waiting', 'zoomies']) dogs[`${key}/${stage}`] = toGraph(P.dogSVG(dog, stage, { type: 'custom', accent: '$accent' }));
  for (const stage of ['packing', 'today']) for (const type of P.TYPE_ORDER) dogs[`${key}/${stage}/${type}`] = toGraph(P.dogSVG(dog, stage, { type, accent: '$accent' }));
  dogs[`${key}/bell`] = toGraph(P.dogSVG(dog, 'waiting', { type: 'custom', accent: '$accent', bell: true }));
  dogs[`${key}/icon`] = toGraph(P.dogIconSVG(dog.breed, 'none', dog.ears), { dropFirstRect: true });
}

/* ---- scenes: one per event type, with and without the door/gate ---- */
const scenes = {};
for (const type of P.TYPE_ORDER) {
  scenes[type] = toGraph(P.sceneBgSVG(type, '$accent', false));
  scenes[type + '/door'] = toGraph(P.sceneBgSVG(type, '$accent', true));
}

/* ---- meta: the data tables ---- */
const meta = {
  breeds: P.BREEDS, breedOrder: P.BREED_ORDER, breedBest: P.BREED_BEST, muttFurs: P.MUTT_FURS, earTypes: P.EAR_TYPES,
  types: P.TYPES, typeOrder: P.TYPE_ORDER, accents: P.ACCENTS, countries: P.COUNTRIES, stages: P.STAGES, milestones: P.MILESTONES,
};
const dir = path.join(root, 'native/assets');
fs.mkdirSync(dir, { recursive: true });
const out = (n, o) => { const f = path.join(dir, n); fs.writeFileSync(f, JSON.stringify(o)); console.log(n, (fs.statSync(f).size / 1024).toFixed(0) + ' KB'); };
out('dogs.json', dogs); out('scenes.json', scenes); out('meta.json', meta);
console.log(Object.keys(dogs).length + ' dog graphs,', Object.keys(scenes).length + ' scenes');
