/*
 * dogs.js - draws the dogs as SVG strings.
 *
 * There is one parametric "sitting" drawing and one "lying down" drawing (used for napping and memory).
 * A breed is a small table of colours + ear shape + face markings + tail style; sit() / lie() assemble the
 * parts. The mood is NOT animated here - the svg gets a class like "stage-zoomies" and css/styles.css
 * animates the labelled parts (.tail, .head, .eye, .ear, .hop, .runner ...). Variants that should be
 * hidden by default (open mouth, closed eyes) carry an inline display:none so a static copy of the svg
 * (used for app icons) still looks right with no stylesheet.
 * Props (party hat, suitcase, passport...) depend on the event type and show in the packing/party stages.
 */
(function () {
  const P = (window.Paw = window.Paw || {});
  let uid = 0;
  const INK = '#2B1B14';

  const BREEDS = {
    golden: { name: 'Sunny', label: 'Golden Retriever', vibe: 'Pure joy, zoomies', best: 'Vacations & beach trips', fur: '#E2A558', dark: '#C4803A', light: '#F8DFAA', paw: '#E2A558', ears: 'floppy', face: 'plain', tail: 'normal' },
    corgi: { name: 'Biscuit', label: 'Corgi', vibe: 'Dramatic & bossy', best: 'Birthdays', fur: '#EE9B45', dark: '#D8802C', light: '#FFF4E2', paw: '#FFF4E2', ears: 'tall', face: 'blaze', tail: 'stub' },
    shiba: { name: 'Miso', label: 'Shiba Inu', vibe: 'Cool, secretly thrilled', best: 'International travel', fur: '#DE8A45', dark: '#C0712F', light: '#FFF1DC', paw: '#FFF1DC', ears: 'pointy', face: 'shiba', tail: 'curl' },
    dachshund: { name: 'Noodle', label: 'Dachshund', vibe: 'Snuggly & patient', best: 'Anniversaries & date nights', fur: '#8A5236', dark: '#5E3622', light: '#C28A5E', paw: '#6E4129', ears: 'long', face: 'tan', tail: 'thin', build: 'long' },
    husky: { name: 'Blizzard', label: 'Husky', vibe: 'Loud, talks back', best: 'Ski trips & adventures', fur: '#6F7B90', dark: '#4A5568', light: '#F8F8F6', paw: '#F8F8F6', ears: 'pointy', face: 'mask', tail: 'bushy' },
    scott: { name: 'Scott', label: 'Fluffy Black Pup', vibe: 'Sweet soul, fluffy chaos', best: 'Anything with Scott', fur: '#2A2630', dark: '#17141B', light: '#F4EEE4', paw: '#2A2630', ears: 'fluffy', face: 'scott', tail: 'plume' },
    bulldog: { name: 'Duke', label: 'American Bulldog', vibe: "Gentle tank, huge heart", best: 'Anything with them', fur: '#F5F0E8', dark: '#A86B3C', light: '#FFFFFF', paw: '#F5F0E8', ears: 'rose', face: 'patch', tail: 'normal', build: 'stocky' },
    bully: { name: 'Hugo', label: 'American Bully', vibe: 'Muscles outside, marshmallow inside', best: 'Anything with them', fur: '#8E96A3', dark: '#5C6573', light: '#F4F4F4', paw: '#F4F4F4', ears: 'rose', face: 'blaze', tail: 'normal', build: 'stocky' },
    labrador: { name: 'Maple', label: 'Labrador', vibe: "Eager, eats everything", best: 'Anything with them', fur: '#E4C28A', dark: '#C79B58', light: '#F6E7C7', paw: '#E4C28A', ears: 'floppy', face: 'plain', tail: 'normal' },
    poodle: { name: 'Bijou', label: 'Poodle', vibe: "Fancy and very clever", best: 'Anything with them', fur: '#F3E3CB', dark: '#E0C9A6', light: '#FFF6E8', paw: '#F3E3CB', ears: 'fluffy', face: 'plain', tail: 'plume' },
    beagle: { name: 'Hazel', label: 'Beagle', vibe: "Nose first, questions later", best: 'Anything with them', fur: '#C88B4E', dark: '#6B4426', light: '#FFFFFF', paw: '#FFFFFF', ears: 'long', face: 'blaze', tail: 'normal' },
    pug: { name: 'Waffle', label: 'Pug', vibe: "Snorts, snacks and snuggles", best: 'Anything with them', fur: '#E6C99B', dark: '#3C312D', light: '#F4E4C6', paw: '#E6C99B', ears: 'rose', face: 'pug', tail: 'curl' },
    shepherd: { name: 'Rex', label: 'German Shepherd', vibe: "Loyal and watchful", best: 'Anything with them', fur: '#B98A52', dark: '#2A2018', light: '#D8B27E', paw: '#B98A52', ears: 'tall', face: 'darkmuzzle', tail: 'bushy' },
    chihuahua: { name: 'Pepper', label: 'Chihuahua', vibe: "Tiny, mighty, dramatic", best: 'Anything with them', fur: '#DDAA6C', dark: '#B9864A', light: '#F8E6C9', paw: '#DDAA6C', ears: 'tall', face: 'plain', tail: 'thin' },
    rottweiler: { name: 'Tank', label: 'Rottweiler', vibe: "A big softie", best: 'Anything with them', fur: '#2A2220', dark: '#15100F', light: '#B5703A', paw: '#2A2220', ears: 'floppy', face: 'blacktan', tail: 'stub' },
    doberman: { name: 'Duchess', label: 'Doberman', vibe: "Sleek and sensitive", best: 'Anything with them', fur: '#2B2422', dark: '#17110F', light: '#C27C3E', paw: '#2B2422', ears: 'pointy', face: 'blacktan', tail: 'stub' },
    pomeranian: { name: 'Ember', label: 'Pomeranian', vibe: "Small fluff, big opinions", best: 'Anything with them', fur: '#EE9A48', dark: '#D0782A', light: '#FBE3C0', paw: '#EE9A48', ears: 'pointy', face: 'plain', tail: 'bushy' },
    collie: { name: 'Tilly', label: 'Border Collie', vibe: "Brilliant and always busy", best: 'Anything with them', fur: '#2B2B34', dark: '#1B1B22', light: '#FFFFFF', paw: '#FFFFFF', ears: 'floppy', face: 'collie', tail: 'bushy' },
    frenchie: { name: 'Gizmo', label: 'French Bulldog', vibe: "Bat ears, big personality", best: 'Anything with them', fur: '#D4B48A', dark: '#8F6C45', light: '#F3E6D2', paw: '#D4B48A', ears: 'tall', face: 'pug', tail: 'stub', build: 'stocky' },
    yorkie: { name: 'Pixie', label: 'Yorkshire Terrier', vibe: "Tiny and fearless", best: 'Anything with them', fur: '#66758A', dark: '#3C485A', light: '#D9A35E', paw: '#66758A', ears: 'pointy', face: 'tan', tail: 'thin' },
    shihtzu: { name: 'Mochi', label: 'Shih Tzu', vibe: "Fluffy royalty", best: 'Anything with them', fur: '#F2EADF', dark: '#C9AE86', light: '#FFFFFF', paw: '#F2EADF', ears: 'fluffy', face: 'plain', tail: 'plume' },
    boxer: { name: 'Rocky', label: 'Boxer', vibe: "Goofy, bouncy, loyal", best: 'Anything with them', fur: '#C98443', dark: '#7A4A22', light: '#FFFFFF', paw: '#FFFFFF', ears: 'floppy', face: 'blaze', tail: 'stub' },
    cocker: { name: 'Cooper', label: 'Cocker Spaniel', vibe: "Soft ears, soft heart", best: 'Anything with them', fur: '#C9873F', dark: '#8F5A24', light: '#F6E0BB', paw: '#C9873F', ears: 'long', face: 'plain', tail: 'normal' },
    dalmatian: { name: 'Domino', label: 'Dalmatian', vibe: "Spotted and spirited", best: 'Anything with them', fur: '#F7F4EF', dark: '#2A2A2A', light: '#FFFFFF', paw: '#F7F4EF', ears: 'floppy', face: 'spots', tail: 'thin' },
    samoyed: { name: 'Cloud', label: 'Samoyed', vibe: "A smiling fluffball", best: 'Anything with them', fur: '#F5F3EE', dark: '#DAD3C8', light: '#FFFFFF', paw: '#F5F3EE', ears: 'pointy', face: 'plain', tail: 'bushy' },
    mutt: { name: 'Lucky', label: 'Mutt', vibe: 'Your colours, your ears', best: 'Anything', fur: '#B98A62', dark: '#8A5F3F', light: '#F0DDC4', paw: '#F0DDC4', ears: 'floppy', face: 'plain', tail: 'normal' },
  };
  P.BREEDS = BREEDS;
  P.BREED_ORDER = ["scott", "golden", "corgi", "shiba", "dachshund", "husky", "bulldog", "bully", "labrador", "poodle", "beagle", "pug", "shepherd", "chihuahua", "rottweiler", "doberman", "pomeranian", "collie", "frenchie", "yorkie", "shihtzu", "boxer", "cocker", "dalmatian", "samoyed", "mutt"];
  // the breeds that also exist as an app icon on the phones
  P.ICON_BREEDS = ['scott', 'golden', 'corgi', 'shiba', 'dachshund', 'husky', 'mutt'];
  P.MUTT_FURS = ['#B98A62', '#8A5F3F', '#E2A558', '#4A3B35', '#9A9A9A', '#E8D2B0', '#C46B3C'];
  P.EAR_TYPES = [['floppy', 'Floppy'], ['pointy', 'Pointy'], ['tall', 'Big'], ['long', 'Long']];

  function palette(dog) {
    const b = BREEDS[dog.breed] || BREEDS.mutt;
    // Native export: keep colours symbolic ($fur ...) so the Kotlin/Swift apps can recolour the same drawing
    if (P.tokenMode) return { ...b, fur: '$fur', dark: '$dark', light: '$light', paw: '$paw', inner: b.face === 'mask' ? '$light' : '#F6B7B0', ears: dog.ears || b.ears };
    const c = { ...b, inner: b.face === 'mask' ? b.light : '#F6B7B0' };
    if (dog.breed === 'mutt') {
      const fur = (dog.colors && dog.colors.fur) || b.fur;
      c.fur = fur; c.dark = P.mix(fur, '#000000', 0.2); c.light = P.mix(fur, '#FFFFFF', 0.62); c.paw = P.mix(fur, '#FFFFFF', 0.55);
      c.ears = dog.ears || b.ears;
    }
    return c;
  }

  /* girls wear a little pink bow. In the native export the bow is always present but hidden, and the apps show it for girl dogs */
  const bow = (dog, x, y, k) => {
    if (!(P.tokenMode || (dog && dog.sex === 'girl'))) return '';
    return `<g class="bow"${P.tokenMode ? ' style="display:none"' : ''} transform="translate(${x} ${y}) scale(${k})"><path d="M0 0-22-13Q-28-1-18 11z" fill="#FF7DAA"/><path d="M0 0 22-13Q28-1 18 11z" fill="#FF7DAA"/><path d="M-20-8Q-14-2-17 6M20-8Q14-2 17 6" stroke="#E5457A" stroke-width="2" fill="none" stroke-linecap="round"/><circle cx="0" cy="0" r="6" fill="#E5457A"/></g>`;
  };

  /* ---------- parts ---------- */
  function ear(type, c, side) {
    const S = {
      floppy: `<path d="M80 66C54 58 34 84 40 124c4 20 26 24 36 6 8-14 12-38 4-64z" fill="${c.dark}"/>`,
      pointy: `<path d="M66 76 58 18Q96 24 110 62z" fill="${c.dark}"/><path d="M71 66 67 36Q86 40 97 60z" fill="${c.inner}"/>`,
      tall: `<path d="M62 80 40 4Q96 14 112 62z" fill="${c.dark}"/><path d="M67 68 55 28Q82 36 98 60z" fill="${c.inner}"/>`,
      rose: `<path d="M78 64C56 56 42 76 50 98c12-2 26-10 34-24z" fill="${c.dark}"/><path d="M70 74C62 74 56 82 58 92 66 90 74 84 78 78z" fill="${c.inner}" opacity=".7"/>`,
      fluffy: `<path d="M82 64C54 50 26 74 30 112c-8 8-4 22 6 26-2 10 6 20 16 16 4 8 18 8 22-2 12-6 12-22 6-32 6-22 4-40 2-56z" fill="${c.dark}"/><path d="M44 120c4-14 6-26 14-38M56 140c2-14 4-26 10-40" stroke="${c.fur}" stroke-width="5" fill="none" stroke-linecap="round" opacity=".55"/>`,
      long: `<path d="M78 66C44 56 24 100 30 156c4 28 32 28 38 2 6-30 18-62 10-92z" fill="${c.dark}"/>`,
    };
    const g = `<g class="ear ear-${side} t-${type}">${S[type] || S.floppy}</g>`;
    return side === 'r' ? `<g transform="translate(240 0) scale(-1 1)">${g}</g>` : g;
  }

  // last 22% of the husky tail curve (de Casteljau split at t = .78) drawn in the light colour
  function tailTip() {
    const P0 = [164, 204], P1 = [208, 214], P2 = [228, 176], P3 = [206, 136], t = 0.78;
    const lerp = (a, b) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
    const a = lerp(P0, P1), b = lerp(P1, P2), c = lerp(P2, P3), d = lerp(a, b), e = lerp(b, c), f = lerp(d, e);
    const r = (v) => Math.round(v * 10) / 10;
    return `M${r(f[0])} ${r(f[1])}C${r(e[0])} ${r(e[1])} ${r(c[0])} ${r(c[1])} ${P3[0]} ${P3[1]}`;
  }

  function tail(c) {
    const w = { normal: 16, curl: 18, stub: 0, thin: 9, bushy: 24, plume: 22 }[c.tail];
    const path = {
      normal: 'M166 206C204 208 220 180 210 146',
      curl: 'M166 202C216 208 222 150 192 150C176 150 176 166 190 168',
      thin: 'M166 208C196 214 212 190 216 160',
      bushy: 'M164 204C208 214 228 176 206 136',
      plume: 'M166 206C212 214 230 172 200 138',
    }[c.tail];
    if (c.tail === 'stub') return `<g class="tail"><ellipse cx="170" cy="204" rx="13" ry="10" fill="${c.fur}"/></g>`;
    const tip = c.tail === 'bushy' ? `<path d="${tailTip()}" stroke="${c.light}" stroke-width="10" fill="none" stroke-linecap="round" opacity=".9"/>` : '';
    return `<g class="tail"><path d="${path}" stroke="${c.fur}" stroke-width="${w}" fill="none" stroke-linecap="round"/>${tip}</g>`;
  }

  function faceLayers(c) {
    switch (c.face) {
      case 'blaze':
        return `<ellipse cx="120" cy="130" rx="42" ry="26" fill="${c.light}"/><path d="M120 52C112 76 108 96 103 120h34c-5-24-9-44-17-68z" fill="${c.light}"/>`;
      case 'shiba':
        return `<ellipse cx="84" cy="124" rx="27" ry="23" fill="${c.light}"/><ellipse cx="156" cy="124" rx="27" ry="23" fill="${c.light}"/><ellipse cx="120" cy="131" rx="28" ry="22" fill="${c.light}"/><circle cx="96" cy="81" r="5.5" fill="${c.light}"/><circle cx="144" cy="81" r="5.5" fill="${c.light}"/>`;
      case 'mask':
        return `<path d="M120 58C112 78 104 88 84 98 66 108 62 134 88 148c18 10 46 10 64 0 26-14 22-40 4-50-20-10-28-20-36-40z" fill="${c.light}"/><ellipse cx="98" cy="84" rx="8" ry="4.5" fill="${c.fur}"/><ellipse cx="142" cy="84" rx="8" ry="4.5" fill="${c.fur}"/>`;
      case 'patch':
        // American Bulldog: broad muzzle, one dark patch over an eye, heavy jowls
        return `<ellipse cx="98" cy="98" rx="21" ry="18" fill="${c.dark}"/><ellipse cx="120" cy="128" rx="36" ry="26" fill="${c.light}"/><path d="M90 134Q120 156 150 134" stroke="#000" stroke-opacity=".14" stroke-width="4" fill="none" stroke-linecap="round"/>`;
      case 'darkmuzzle':
        // German Shepherd: dark mask and muzzle, tan eyebrows
        return `<ellipse cx="120" cy="127" rx="33" ry="25" fill="#5B4E49"/><path d="M84 96C90 80 106 80 120 90 134 80 150 80 156 96 148 112 92 112 84 96z" fill="${c.dark}" opacity=".55"/><ellipse cx="97" cy="81" rx="6" ry="4" fill="${c.light}"/><ellipse cx="143" cy="81" rx="6" ry="4" fill="${c.light}"/>`;
      case 'pug':
        // Pug / French Bulldog: flat dark muzzle and forehead wrinkles
        return `<ellipse cx="120" cy="126" rx="32" ry="24" fill="#5B4E49"/><path d="M100 70q20-8 40 0M104 79q16-6 32 0" stroke="${c.dark}" stroke-opacity=".45" stroke-width="3.4" fill="none" stroke-linecap="round"/><ellipse cx="97" cy="101" rx="14" ry="12" fill="${c.dark}" opacity=".25"/><ellipse cx="143" cy="101" rx="14" ry="12" fill="${c.dark}" opacity=".25"/>`;
      case 'blacktan':
        // Rottweiler / Doberman: black with tan cheeks and eyebrows; soft eye patches so the eyes read
        return `<ellipse cx="98" cy="100" rx="15" ry="14" fill="#3A3541"/><ellipse cx="142" cy="100" rx="15" ry="14" fill="#3A3541"/><ellipse cx="120" cy="128" rx="34" ry="24" fill="${c.light}"/><ellipse cx="96" cy="80" rx="7" ry="4.5" fill="${c.light}"/><ellipse cx="144" cy="80" rx="7" ry="4.5" fill="${c.light}"/>`;
      case 'collie':
        // Border Collie: white blaze and muzzle on a black face
        return `<ellipse cx="98" cy="100" rx="15" ry="14" fill="#3A3A46"/><ellipse cx="142" cy="100" rx="15" ry="14" fill="#3A3A46"/><ellipse cx="120" cy="130" rx="38" ry="26" fill="${c.light}"/><path d="M120 52C112 76 108 96 103 120h34c-5-24-9-44-17-68z" fill="${c.light}"/>`;
      case 'spots':
        // Dalmatian: black spots and a clean muzzle
        return `<ellipse cx="120" cy="128" rx="31" ry="23" fill="${c.light}"/>${[[84, 76, 8], [152, 84, 7], [112, 64, 5], [134, 68, 6], [88, 104, 4.5], [156, 112, 5], [122, 82, 3.5]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${c.dark}"/>`).join('')}`;
      case 'scott':
        // charcoal muzzle + soft eye patches so the dark eyes read on black fur, a cream beard, and a few wispy brow hairs
        return `<ellipse cx="120" cy="126" rx="33" ry="24" fill="#4B4452"/><ellipse cx="98" cy="100" rx="15" ry="14" fill="#3A3541"/><ellipse cx="142" cy="100" rx="15" ry="14" fill="#3A3541"/><path d="M100 134Q120 168 140 134Q130 146 120 147Q110 146 100 134z" fill="${c.light}"/><ellipse cx="120" cy="143" rx="11" ry="7" fill="${c.light}"/><path d="M92 70C96 56 104 52 112 54M128 54C136 52 144 56 148 70" stroke="${c.fur}" stroke-width="7" fill="none" stroke-linecap="round"/>`;
      case 'tan':
        return `<ellipse cx="120" cy="128" rx="34" ry="24" fill="${c.light}"/><ellipse cx="97" cy="81" rx="6" ry="4" fill="${c.light}"/><ellipse cx="143" cy="81" rx="6" ry="4" fill="${c.light}"/>`;
      default:
        return `<ellipse cx="120" cy="128" rx="31" ry="23" fill="${c.light}"/>`;
    }
  }

  const eyeOpen = (x) => `<g class="eye"><ellipse cx="${x}" cy="100" rx="8" ry="9.4" fill="${INK}"/><circle cx="${x + 2.6}" cy="96.4" r="3" fill="#fff"/><circle cx="${x - 2.4}" cy="103.4" r="1.4" fill="#fff" opacity=".8"/></g>`;
  const eyeShut = (x) => `<path class="eye-shut" style="display:none" d="M${x - 10} 103Q${x} 90 ${x + 10} 103" stroke="${INK}" stroke-width="4.2" fill="none" stroke-linecap="round"/>`;

  /* ---------- head & side props per event type ---------- */
  const PROP_OF = { gotcha: 'anniversary' };
  function headProp(type, accent) {
    type = PROP_OF[type] || type;
    switch (type) {
      case 'birthday':
        return `<g class="hat" transform="rotate(-12 120 60)"><path d="M120 4 96 60q24 9 48 0z" fill="${accent}"/><path d="m108 34 24 0M101 50h38" stroke="#fff" stroke-width="5" stroke-linecap="round" opacity=".85"/><circle cx="120" cy="6" r="7" fill="#D4E157"/></g>`;
      case 'holiday':
        return `<g class="hat" transform="rotate(-6 120 60)"><path d="M90 64Q92 22 126 18Q130 40 150 64z" fill="#E5574F"/><ellipse cx="120" cy="64" rx="33" ry="9" fill="#fff"/><circle cx="130" cy="17" r="8" fill="#fff"/></g>`;
      case 'anniversary':
        return `<g class="hat">${[[86, 70, '#F6B7B0'], [99, 62, '#FFE08A'], [112, 57, '#fff'], [128, 57, '#F6B7B0'], [141, 62, '#FFE08A'], [154, 70, '#fff']].map(([x, y, f]) => `<circle cx="${x}" cy="${y}" r="7.5" fill="${f}"/><circle cx="${x}" cy="${y}" r="2.6" fill="#E8A15C"/>`).join('')}<path d="M82 74Q120 44 158 74" stroke="#6BB26B" stroke-width="3" fill="none" opacity=".7"/></g>`;
      case 'vacation':
        return `<g class="shades"><rect x="80" y="88" width="34" height="22" rx="9" fill="#2B2B35"/><rect x="126" y="88" width="34" height="22" rx="9" fill="#2B2B35"/><path d="M114 98h12" stroke="#2B2B35" stroke-width="4"/><path d="m87 94 8-2M133 94l8-2" stroke="#fff" stroke-width="2.6" stroke-linecap="round" opacity=".5"/></g>`;
      default:
        return '';
    }
  }
  function sideProp(type, accent) {
    type = PROP_OF[type] || type;
    const suit = `<g class="prop suitcase" transform="translate(172 176) rotate(-4)"><path d="M16 6V-1a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v7" fill="none" stroke="#6B4A33" stroke-width="4"/><rect x="0" y="4" width="48" height="40" rx="8" fill="${accent}"/><rect x="0" y="21" width="48" height="4" fill="#000" opacity=".14"/><circle cx="13" cy="14" r="5" fill="#fff" opacity=".85"/><path d="m33 11 3 6-6-3h6z" fill="#fff" opacity=".7"/><circle cx="10" cy="46" r="4" fill="#3B2A22"/><circle cx="38" cy="46" r="4" fill="#3B2A22"/></g>`;
    switch (type) {
      case 'intl_trip':
        return suit + `<g class="prop passport" transform="translate(52 190) rotate(-14)"><rect width="27" height="36" rx="4" fill="#2B4A7A"/><circle cx="13.5" cy="15" r="7" fill="none" stroke="#F4C542" stroke-width="2"/><path d="M13.5 8v14M6.5 15h14" stroke="#F4C542" stroke-width="1.4"/><rect x="6" y="27" width="15" height="3" rx="1.5" fill="#F4C542" opacity=".8"/></g>`;
      case 'vacation':
        return suit;
      case 'birthday':
        return `<g class="prop gift" transform="translate(174 180)"><rect width="42" height="38" rx="6" fill="${accent}"/><rect x="18" width="6" height="38" fill="#fff" opacity=".9"/><rect y="14" width="42" height="6" fill="#fff" opacity=".9"/><path d="M21 0C12-12 4-4 21 0 38-4 30-12 21 0z" fill="#fff"/></g>`;
      case 'holiday':
        return `<g class="prop gift" transform="translate(174 180)"><rect width="42" height="38" rx="6" fill="#E5574F"/><rect x="18" width="6" height="38" fill="#FFE08A"/><rect y="14" width="42" height="6" fill="#FFE08A"/><path d="M21 0C12-12 4-4 21 0 38-4 30-12 21 0z" fill="#FFE08A"/></g>`;
      case 'anniversary':
        return `<g class="prop bouquet" transform="translate(172 168)"><path d="M26 54 18 20M26 54 34 18M26 54 26 12" stroke="#5FA55F" stroke-width="3.5" stroke-linecap="round"/><path d="M12 52h28l-4 12H16z" fill="${accent}"/>${[[18, 16, '#F6B7B0'], [34, 14, '#FFE08A'], [26, 8, '#fff']].map(([x, y, f]) => `<circle cx="${x}" cy="${y}" r="8" fill="${f}"/><circle cx="${x}" cy="${y}" r="2.8" fill="#E8A15C"/>`).join('')}</g>`;
      default:
        return `<g class="prop ball" transform="translate(180 196)"><circle cx="16" cy="16" r="16" fill="#D4E157"/><path d="M4 8c8 4 8 14 0 20M28 8c-8 4-8 14 0 20" stroke="#fff" stroke-width="2.4" fill="none" opacity=".85"/></g>`;
    }
  }

  /* ---------- sitting pose ---------- */
  function sit(dog, stage, o) {
    const c = palette(dog), id = 'h' + ++uid;
    const accent = o.accent || '#E8A15C';
    const happy = stage === 'packing' || stage === 'zoomies' || stage === 'today';
    const wild = stage === 'zoomies' || stage === 'today';
    const props = stage === 'packing' || stage === 'today';
    const hp = (stage === 'packing' || stage === 'today') ? headProp(stage === 'today' && o.type !== 'holiday' && o.type !== 'anniversary' ? 'birthday' : o.type, accent) : '';
    const sp = stage === 'packing' ? sideProp(o.type, accent) : '';
    const body = `
      <ellipse class="shadow" cx="120" cy="223" rx="66" ry="8" fill="#000" opacity=".15"/>
      ${tail(c)}
      ${c.build === 'long' ? `<ellipse cx="170" cy="198" rx="50" ry="27" fill="${c.fur}"/><rect x="184" y="200" width="18" height="24" rx="9" fill="${c.fur}"/><ellipse cx="193" cy="222" rx="13" ry="7" fill="${c.paw}"/>` : ''}
      <ellipse cx="76" cy="203" rx="27" ry="20" fill="${c.fur}"/><ellipse cx="164" cy="203" rx="27" ry="20" fill="${c.fur}"/>
      ${c.build === 'stocky' ? `<ellipse cx="120" cy="186" rx="60" ry="44" fill="${c.fur}"/><ellipse cx="72" cy="206" rx="22" ry="18" fill="${c.fur}"/><ellipse cx="168" cy="206" rx="22" ry="18" fill="${c.fur}"/>` : ''}
      <g class="torso"><ellipse cx="120" cy="178" rx="47" ry="49" fill="${c.fur}"/>${c.face === 'scott' ? `<path d="M120 146C100 150 94 166 100 178 108 188 132 188 140 178 146 166 140 150 120 146z" fill="${c.light}"/>` : `<ellipse cx="120" cy="184" rx="29" ry="37" fill="${c.light}"/>`}</g>
      <rect x="100" y="172" width="19" height="44" rx="9.5" fill="${c.fur}"/><rect x="121" y="172" width="19" height="44" rx="9.5" fill="${c.fur}"/>
      <ellipse cx="109.5" cy="215" rx="13" ry="8" fill="${c.paw}"/><ellipse cx="130.5" cy="215" rx="13" ry="8" fill="${c.paw}"/>
      <path d="M109 215v-4M130 215v-4" stroke="#000" stroke-opacity=".12" stroke-width="2" stroke-linecap="round"/>
      ${sp}
      <path d="M85 151Q120 170 155 151" stroke="${o.bell ? '#E5574F' : '#E5574F'}" stroke-width="9" fill="none" stroke-linecap="round"/>
      ${o.bell
        ? `<g class="bell-g"><path d="M107 175a13 13 0 0 1 26 0v7h-26z" fill="#F4C542" stroke="#C99A1E" stroke-width="2.4"/><path d="M110 180h20" stroke="#C99A1E" stroke-width="2.4"/><circle cx="120" cy="187" r="3.6" fill="#8A5A1A"/></g>`
        : `<g class="tag"><circle cx="120" cy="170" r="6.5" fill="#F4C542" stroke="#C99A1E" stroke-width="2"/></g>`}`;

    const eyes = `<g class="eyes">${eyeOpen(98)}${eyeOpen(142)}</g>${eyeShut(98)}${eyeShut(142)}`;
    const brows = stage === 'curious' ? `<path class="brow" d="M86 80Q98 71 110 78M130 78Q142 71 154 80" stroke="${c.dark}" stroke-width="4" fill="none" stroke-linecap="round"/>` : '';
    const blush = `<ellipse class="blush" cx="76" cy="121" rx="9" ry="6" fill="#F6B7B0" opacity="${happy ? 0.7 : 0}"/><ellipse class="blush" cx="164" cy="121" rx="9" ry="6" fill="#F6B7B0" opacity="${happy ? 0.7 : 0}"/>`;
    const nose = `<path d="M108 113Q120 105 132 113Q130 126 120 129Q110 126 108 113z" fill="${INK}"/><ellipse cx="116" cy="112" rx="4" ry="1.8" fill="#fff" opacity=".45"/>`;
    const mouthClosed = `<g class="mouth-closed"><path d="M120 129v6M120 135c-3 7-12 8-17 2M120 135c3 7 12 8 17 2" stroke="${INK}" stroke-width="3.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>`;
    const mouthOpen = `<g class="mouth-open" style="display:none"><path d="M103 133Q120 ${wild ? 166 : 158} 137 133Q120 140 103 133z" fill="#8E3B3B"/><path class="tongue" d="M111 143q9 ${wild ? 20 : 14} 18 0z" fill="#F08A8A"/></g>`;

    const head = `
      <g class="head">
        ${ear(c.ears, c, 'l')}${ear(c.ears, c, 'r')}
        <clipPath id="${id}"><ellipse cx="120" cy="104" rx="58" ry="50"/></clipPath>
        <ellipse cx="120" cy="104" rx="58" ry="50" fill="${c.fur}"/>
        <g clip-path="url(#${id})">${faceLayers(c)}</g>
        ${blush}${eyes}${brows}${nose}${mouthClosed}${mouthOpen}
        ${bow(dog, 86, 58, 1)}
        ${hp}
      </g>`;
    return body + head;
  }

  /* ---------- napping / memory pose (lying down) ---------- */
  function lie(dog, stage, o) {
    const c = palette(dog), memory = stage === 'memory';
    const earSvg = {
      floppy: `<path d="M98 150C128 148 134 188 112 206 100 198 94 176 98 150z" fill="${c.dark}"/>`,
      rose: `<path d="M98 148C118 146 130 160 124 176 110 174 100 164 98 148z" fill="${c.dark}"/>`,
      fluffy: `<path d="M98 148C132 146 140 184 114 208 106 214 94 206 92 192 88 176 90 158 98 148z" fill="${c.dark}"/>`,
      long: `<path d="M98 152C132 150 138 192 108 214 94 200 90 176 98 152z" fill="${c.dark}"/>`,
      pointy: `<path d="M82 156 84 118Q108 128 114 160z" fill="${c.dark}"/><path d="M88 152 90 130Q102 136 106 152z" fill="${c.inner}"/>`,
      tall: `<path d="M78 158 78 108Q112 118 116 162z" fill="${c.dark}"/><path d="M85 152 86 124Q102 130 108 152z" fill="${c.inner}"/>`,
    }[c.ears] || '';
    const souvenir = memory
      ? `<g transform="rotate(-3 120 214)"><rect x="18" y="204" width="204" height="20" rx="7" fill="#fff"/><rect x="18" y="204" width="204" height="20" rx="7" fill="none" stroke="${o.accent}" stroke-width="3" stroke-dasharray="7 5"/><rect x="190" y="207" width="24" height="14" rx="3" fill="${o.accent}" opacity=".85"/></g>`
      : '';
    const eyeInk = c.face === 'scott' ? '#E4DCEA' : INK; // dark lids vanish on black fur
    const eye = memory
      ? `<path d="M72 181Q82 170 92 181" stroke="${eyeInk}" stroke-width="4.2" fill="none" stroke-linecap="round"/>`
      : `<path d="M72 179Q82 189 92 179" stroke="${eyeInk}" stroke-width="4.2" fill="none" stroke-linecap="round"/>`;
    const faceBits = c.face === 'shiba' ? `<ellipse cx="70" cy="196" rx="22" ry="18" fill="${c.light}"/>` : c.face === 'mask' ? `<path d="M52 196C52 176 70 160 84 156c14 10 22 24 20 40 0 12-20 20-34 18-12-2-18-10-18-18z" fill="${c.light}"/>` : '';
    return `
      <ellipse class="shadow" cx="120" cy="224" rx="92" ry="8" fill="#000" opacity=".15"/>
      ${souvenir}
      <g class="tail tail-nap"><path d="M186 204C228 206 234 166 204 160" stroke="${c.fur}" stroke-width="${c.tail === 'bushy' ? 24 : c.tail === 'thin' ? 10 : 17}" fill="none" stroke-linecap="round"/></g>
      <g class="breathe">
        <ellipse cx="132" cy="193" rx="76" ry="33" fill="${c.fur}"/>
        <path d="M72 176C106 158 164 158 202 186 172 176 112 172 72 176z" fill="${c.dark}" opacity=".35"/>
        <ellipse cx="178" cy="206" rx="27" ry="17" fill="${c.fur}" stroke="${c.dark}" stroke-opacity=".25" stroke-width="2"/>
      </g>
      <ellipse cx="60" cy="217" rx="24" ry="9" fill="${c.paw}"/><ellipse cx="94" cy="219" rx="20" ry="8" fill="${c.paw}"/>${c.face === 'scott' ? `<ellipse cx="114" cy="212" rx="14" ry="9" fill="${c.light}"/>` : ''}
      <g class="nap-head">
        <circle cx="88" cy="184" r="37" fill="${c.fur}"/>
        ${faceBits}
        <ellipse cx="58" cy="197" rx="25" ry="17" fill="${c.face === 'scott' ? '#4B4452' : c.face === 'darkmuzzle' || c.face === 'pug' ? '#5B4E49' : c.light}"/>${c.face === 'scott' ? `<ellipse cx="60" cy="209" rx="13" ry="6" fill="${c.light}"/>` : ''}
        <ellipse cx="37" cy="192" rx="7.5" ry="5.8" fill="${INK}"/><ellipse cx="35" cy="190" rx="2.6" ry="1.3" fill="#fff" opacity=".5"/>
        ${c.face === 'scott' ? `<circle cx="70" cy="153" r="11" fill="${c.fur}"/><circle cx="86" cy="148" r="12" fill="${c.fur}"/><circle cx="103" cy="152" r="11" fill="${c.fur}"/><circle cx="55" cy="164" r="9" fill="${c.fur}"/>` : ''}
        ${eye}
        <ellipse cx="94" cy="199" rx="8" ry="5" fill="#F6B7B0" opacity=".65"/>
        <g class="ear ear-l t-${c.ears}">${earSvg}</g>
        ${bow(dog, 74, 152, 0.8)}
      </g>
      <ellipse cx="52" cy="219" rx="19" ry="8" fill="${c.paw}"/>
      ${memory
        ? `<g class="hearts-f"><text x="112" y="130" font-size="22" fill="#E5574F">♥</text><text x="146" y="110" font-size="15" fill="#F6B7B0">♥</text></g>`
        : `<g class="zzz" fill="${o.zColor || '#fff'}" font-family="Fredoka, sans-serif" font-weight="700"><text x="108" y="132" font-size="22">z</text><text x="128" y="108" font-size="28">z</text><text x="152" y="80" font-size="34">Z</text></g>`}`;
  }

  /* ---------- public ---------- */
  P.dogSVG = function (dog, stage, o = {}) {
    const b = BREEDS[dog.breed] || BREEDS.mutt;
    const inner = stage === 'nap' || stage === 'memory' ? lie(dog, stage, { accent: o.accent || '#E8A15C', zColor: o.zColor }) : `<g class="runner"><g class="hop">${sit(dog, stage, o)}</g></g>`;
    const label = o.label || `${dog.name}, a ${b.label}`;
    return `<svg class="dog stage-${stage} breed-${dog.breed}" viewBox="0 0 240 240" role="img" aria-label="${P.esc(label)}" focusable="false">${inner}</svg>`;
  };

  // little head portrait for app icons / favicon
  P.dogIconSVG = function (breed, bg, ears) {
    const dog = { breed, name: '', ears };
    const c = palette(dog);
    const id = 'ic' + ++uid;
    const inner = `${ear(c.ears, c, 'l')}${ear(c.ears, c, 'r')}<clipPath id="${id}"><ellipse cx="120" cy="104" rx="58" ry="50"/></clipPath><ellipse cx="120" cy="104" rx="58" ry="50" fill="${c.fur}"/><g clip-path="url(#${id})">${faceLayers(c)}</g>${eyeOpen(98)}${eyeOpen(142)}<path d="M108 113Q120 105 132 113Q130 126 120 129Q110 126 108 113z" fill="${INK}"/><path d="M120 129v6M120 135c-3 7-12 8-17 2M120 135c3 7 12 8 17 2" stroke="${INK}" stroke-width="3.2" fill="none" stroke-linecap="round"/><path d="M85 151Q120 170 155 151" stroke="#E5574F" stroke-width="9" fill="none" stroke-linecap="round"/>`;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240"><rect width="240" height="240" rx="56" fill="${bg || '#FFF1DC'}"/><g transform="translate(120 128) scale(.98) translate(-120 -108)">${inner}</g></svg>`;
  };
  P.dogIconURI = (breed) => 'data:image/svg+xml,' + encodeURIComponent(P.dogIconSVG(breed));
  if (P.BREED_BEST) P.BREED_ORDER.forEach((b) => { if (!P.BREED_BEST[b]) P.BREED_BEST[b] = []; });
})();
