/*
 * scenes.js - the illustrated backdrop behind each dog, one per event type (airport window, beach,
 * party room, picnic, snowy night, custom). sceneHTML() stacks, bottom to top:
 *   background svg -> stage atmosphere (stars, sun rays, confetti...) -> the dog -> hearts / ball -> speech bubble
 * The stage also tints the scene through CSS (dim and starry for napping, sepia for memories).
 * Scenes are cropped with preserveAspectRatio="slice" so one drawing fits hero cards, small cards and full screen.
 */
(function () {
  const P = (window.Paw = window.Paw || {});
  let uid = 0;

  const cloud = (x, y, s = 1, o = 0.92) =>
    `<g class="cloud" transform="translate(${x} ${y}) scale(${s})" opacity="${o}"><ellipse rx="28" ry="11" fill="#fff"/><ellipse cx="-14" cy="-7" rx="14" ry="10" fill="#fff"/><ellipse cx="10" cy="-10" rx="17" ry="12" fill="#fff"/></g>`;

  function bg(type, accent, u) {
    switch (type) {
      case 'intl_trip':
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6FB6EC"/><stop offset="1" stop-color="#DDF1FC"/></linearGradient><clipPath id="${u}w"><rect x="36" y="38" width="328" height="164" rx="12"/></clipPath></defs>
        <rect width="400" height="300" fill="#F2E8D8"/>
        <rect x="24" y="24" width="352" height="190" rx="20" fill="#fff"/>
        <g clip-path="url(#${u}w)"><rect x="36" y="38" width="328" height="164" fill="url(#${u}s)"/>${cloud(90, 78, 1.1)}${cloud(280, 62, 0.8, 0.8)}
          <rect x="36" y="160" width="328" height="42" fill="#7D889A"/><path d="M36 181h328" stroke="#fff" stroke-width="3" stroke-dasharray="18 14" opacity=".8"/>
          <g class="plane"><g transform="translate(0 112)"><ellipse cx="0" cy="0" rx="34" ry="8" fill="#fff"/><path d="M-30 -2-44-18h10l12 14z" fill="#E5574F"/><path d="M-4 0 14 20h10L10 0z" fill="#E7EDF3"/><path d="M-4 -2 12-18h8L8-2z" fill="#E7EDF3"/><g fill="#8CB8DA"><circle cx="-12" cy="-1" r="2"/><circle cx="-4" cy="-1" r="2"/><circle cx="4" cy="-1" r="2"/><circle cx="12" cy="-1" r="2"/></g></g></g>
        </g>
        <path d="M200 38v164M36 120h328" stroke="#fff" stroke-width="7"/>
        <rect x="14" y="212" width="372" height="14" rx="7" fill="#fff"/>
        <rect y="226" width="400" height="74" fill="#DCC9AE"/><path d="M0 252h400M0 280h400M70 226 40 300M200 226v74M330 226l30 74" stroke="#C9B493" stroke-width="2"/>`;
      case 'vacation':
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6CC3F2"/><stop offset="1" stop-color="#FDEBB8"/></linearGradient></defs>
        <rect width="400" height="300" fill="url(#${u}s)"/>
        <circle class="sun" cx="314" cy="72" r="36" fill="#FFD35C"/><circle cx="314" cy="72" r="54" fill="#FFD35C" opacity=".25"/>
        ${cloud(84, 62, 1.1)}${cloud(220, 40, 0.8, 0.85)}
        <rect y="150" width="400" height="86" fill="#35B8D8"/><rect y="150" width="400" height="10" fill="#7DDCF0" opacity=".7"/>
        <g class="waves" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".8"><path d="M20 178q10-8 20 0t20 0 20 0M210 196q10-8 20 0t20 0 20 0M120 166q10-8 20 0t20 0M300 174q10-8 20 0t20 0"/></g>
        <path d="M0 212C90 196 170 232 400 206V300H0z" fill="#F7DFA7"/><path d="M0 236C120 224 250 252 400 232V300H0z" fill="#F1D08A" opacity=".7"/>
        <g class="prop palm"><path d="M54 232C58 190 54 156 66 118" stroke="#9A6B43" stroke-width="11" fill="none" stroke-linecap="round"/><g fill="#3FA867"><path d="M66 118C40 100 18 112 12 128 36 116 52 118 66 118z"/><path d="M66 118C60 92 82 76 100 84 80 90 74 104 66 118z"/><path d="M66 118C96 100 120 114 124 130 98 118 82 120 66 118z"/><path d="M66 118C44 98 36 76 50 64 54 86 62 100 66 118z"/></g><circle cx="62" cy="124" r="5" fill="#7A4A2A"/><circle cx="72" cy="126" r="5" fill="#7A4A2A"/></g>`;
      case 'birthday':
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE0EC"/><stop offset="1" stop-color="#FFF0D8"/></linearGradient></defs>
        <rect width="400" height="300" fill="url(#${u}s)"/>
        <path d="M-10 22Q100 66 200 28T410 22" stroke="#B88B6A" stroke-width="2.5" fill="none"/>
        ${[[24, 34, '#FF7DAA'], [72, 48, '#FFD35C'], [120, 56, '#5DADE8'], [170, 46, '#D4E157'], [224, 38, '#FF7DAA'], [274, 46, '#FFD35C'], [326, 54, '#5DADE8'], [374, 38, '#D4E157']].map(([x, y, f]) => `<path d="M${x - 11} ${y - 8}h22l-11 24z" fill="${f}"/>`).join('')}
        <g class="prop balloons">${[[52, 126, '#FF7DAA'], [86, 108, '#5DADE8'], [328, 120, '#D4E157'], [358, 100, '#FFD35C']].map(([x, y, f], i) => `<g class="balloon b${i}"><ellipse cx="${x}" cy="${y}" rx="17" ry="21" fill="${f}"/><ellipse cx="${x - 6}" cy="${y - 8}" rx="4" ry="6" fill="#fff" opacity=".5"/><path d="M${x} ${y + 21}q-4 18 2 40" stroke="#B88B6A" stroke-width="1.8" fill="none"/></g>`).join('')}</g>
        <rect y="226" width="400" height="74" fill="#EBC9A0"/><path d="M0 250h400M0 274h400M120 226v74M270 226v74" stroke="#D9B183" stroke-width="2"/>
        <g class="prop cake"><rect x="300" y="196" width="80" height="40" rx="6" fill="#B07A4A"/><rect x="310" y="168" width="60" height="30" rx="8" fill="#FFB3CC"/><path d="M310 180q8 8 15 0t15 0 15 0 15 0v-8H310z" fill="#fff"/><rect x="338" y="152" width="4" height="16" fill="#5DADE8"/><path class="flame" d="M340 140c-5 6-3 11 0 12 3-1 5-6 0-12z" fill="#FFB020"/></g>`;
      case 'anniversary':
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFD3CF"/><stop offset="1" stop-color="#FFF0D2"/></linearGradient></defs>
        <rect width="400" height="300" fill="url(#${u}s)"/>
        <circle cx="320" cy="70" r="30" fill="#FFE29A" opacity=".9"/>
        <ellipse cx="90" cy="206" rx="200" ry="50" fill="#CBE5B6"/><ellipse cx="330" cy="214" rx="190" ry="44" fill="#A9D49A"/>
        <g class="prop"><rect x="326" y="120" width="10" height="70" fill="#9A6B43"/><circle cx="331" cy="108" r="40" fill="#8CC57F"/><circle cx="306" cy="124" r="26" fill="#9FD292"/></g>
        <path d="M-20 300 40 232H360L420 300z" fill="#fff"/><g opacity=".36" fill="#EE8FA0">${gingham()}</g><g class="prop"><rect x="330" y="236" width="44" height="30" rx="6" fill="#C98A52"/><path d="M336 236q16-22 32 0" stroke="#9A6B43" stroke-width="4" fill="none"/></g>
        <g class="prop">${[[44, 252, '#F6B7B0'], [58, 244, '#FFE08A'], [32, 242, '#fff']].map(([x, y, f]) => `<path d="M${x} ${y}v22" stroke="#5FA55F" stroke-width="3"/><circle cx="${x}" cy="${y}" r="8" fill="${f}"/><circle cx="${x}" cy="${y}" r="2.8" fill="#E8A15C"/>`).join('')}</g>
        <g class="hearts-bg" fill="#E5574F"><text x="70" y="110" font-size="22" class="hf h1">♥</text><text x="240" y="90" font-size="16" class="hf h2">♥</text><text x="150" y="140" font-size="13" class="hf h3">♥</text></g>`;
      case 'holiday':
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1F2F5C"/><stop offset="1" stop-color="#6683BC"/></linearGradient></defs>
        <rect width="400" height="300" fill="url(#${u}s)"/>
        <circle cx="70" cy="64" r="22" fill="#FFF1C0"/><circle cx="80" cy="58" r="20" fill="#2A3C70"/>
        ${[[130, 50], [200, 90], [260, 40], [330, 70], [110, 120], [370, 130]].map(([x, y]) => `<circle class="twinkle" cx="${x}" cy="${y}" r="2" fill="#fff"/>`).join('')}
        <path d="M0 214C80 190 160 222 250 202S360 196 400 208V300H0z" fill="#E9F0FA"/><path d="M0 240C100 228 220 252 400 234V300H0z" fill="#fff"/>
        <g class="prop tree"><rect x="336" y="206" width="12" height="26" fill="#7A4A2A"/><path d="M342 112 304 170h76z" fill="#2F7D55"/><path d="M342 140 296 206h92z" fill="#38916A"/><path d="M342 172 288 236h108z" fill="#2F7D55"/><circle class="twinkle" cx="330" cy="168" r="3.4" fill="#FFD35C"/><circle class="twinkle" cx="354" cy="196" r="3.4" fill="#FF7D7D"/><circle class="twinkle" cx="320" cy="214" r="3.4" fill="#5DADE8"/><circle class="twinkle" cx="360" cy="226" r="3.4" fill="#FFD35C"/><path d="m342 100 4 9 10 1-7 7 2 10-9-5-9 5 2-10-7-7 10-1z" fill="#FFD35C"/></g>
        ${Array.from({ length: 16 }, (_, i) => `<circle class="snow s${i % 4}" cx="${(i * 53) % 400}" cy="${(i * 37) % 200}" r="${1.6 + (i % 3)}" fill="#fff" opacity=".9"/>`).join('')}`;
      default: {
        const a = accent, soft = P.mix(a, '#FFFFFF', 0.62), softer = P.mix(a, '#FFF8EE', 0.82), deep = P.mix(a, '#8A5A3B', 0.35);
        return `
        <defs><linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${soft}"/><stop offset="1" stop-color="${softer}"/></linearGradient></defs>
        <rect width="400" height="300" fill="url(#${u}s)"/>
        <circle cx="70" cy="80" r="46" fill="#fff" opacity=".4"/><circle cx="340" cy="60" r="30" fill="#fff" opacity=".45"/><circle cx="300" cy="150" r="56" fill="${a}" opacity=".18"/>
        ${cloud(110, 46, 0.9, 0.7)}
        <g class="prop emoji-float"><g transform="translate(262 72) scale(2.6)"><path d="M3.5 8.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 4v1.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V14a2 2 0 0 0 0-4z" fill="#fff" opacity=".92"/><path d="M14 6.5v11" stroke="${a}" stroke-width="1.2" stroke-dasharray="1.6 1.6" fill="none"/><path d="m8.6 9.2.8 1.7 1.8.2-1.3 1.2.4 1.8-1.7-.9-1.7.9.4-1.8-1.3-1.2 1.8-.2z" fill="${a}"/></g></g>
        <path d="M0 232C120 218 280 246 400 226V300H0z" fill="${deep}" opacity=".35"/><rect y="238" width="400" height="62" fill="${P.mix(a, '#8A5A3B', 0.15)}" opacity=".5"/>`;
      }
    }
  }

  // gingham picnic blanket as plain polygons (no <pattern>, so the native renderers can draw it too)
  function gingham() {
    const left = (y) => 40 - (60 * (y - 232)) / 68, right = (y) => 360 + (60 * (y - 232)) / 68;
    let out = '';
    for (let i = 0; i < 12; i++) {
      const t0 = 40 + 28 * i, t1 = t0 + 14, b0 = -20 + 36 * i, b1 = b0 + 18;
      if (t1 <= 360 + 14) out += `<path d="M${t0} 232H${Math.min(t1, 360)}L${Math.min(b1, 420)} 300H${b0}z"/>`;
    }
    [236, 252, 268, 284].forEach((y) => { out += `<path d="M${left(y).toFixed(1)} ${y}H${right(y).toFixed(1)}L${right(y + 8).toFixed(1)} ${y + 8}H${left(y + 8).toFixed(1)}z"/>`; });
    return out;
  }

  function door(type) {
    if (type === 'vacation' || type === 'anniversary' || type === 'holiday')
      return `<g class="door"><rect x="8" y="132" width="12" height="104" rx="4" fill="#B98456"/><rect x="74" y="132" width="12" height="104" rx="4" fill="#B98456"/><path d="M20 152h54M20 196h54" stroke="#D2A06E" stroke-width="10" stroke-linecap="round"/><path d="M22 150 72 198M72 150 22 198" stroke="#C48F5E" stroke-width="6" stroke-linecap="round"/><circle cx="14" cy="128" r="7" fill="#D2A06E"/><circle cx="80" cy="128" r="7" fill="#D2A06E"/></g>`;
    return `<g class="door"><rect x="6" y="96" width="82" height="140" rx="6" fill="#A9784B"/><rect x="14" y="106" width="66" height="122" rx="4" fill="#BD8A58"/><rect x="22" y="116" width="50" height="42" rx="4" fill="#A9784B" opacity=".55"/><rect x="22" y="168" width="50" height="52" rx="4" fill="#A9784B" opacity=".55"/><circle cx="68" cy="170" r="5" fill="#F4C542"/><rect x="6" y="230" width="82" height="6" fill="#FFE9B0" opacity=".7"/></g>`;
  }

  // used by the native export (tools/export-native.js): just the backdrop drawing, optionally with the door/gate
  P.sceneBgSVG = (type, accent, withDoor) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300">${bg(type, accent, 's0')}${withDoor ? door(type) : ''}</svg>`;

  const rand = (a, b) => a + Math.random() * (b - a);
  function fx(stage, size) {
    const mini = size === 'card';
    if (stage === 'nap')
      return `<div class="moon"></div>${Array.from({ length: mini ? 4 : 8 }, () => `<i class="star" style="left:${rand(6, 94).toFixed(0)}%;top:${rand(6, 46).toFixed(0)}%;animation-delay:${rand(0, 3).toFixed(1)}s"></i>`).join('')}`;
    if (stage === 'today') {
      const cols = ['#FF7DAA', '#FFD35C', '#5DADE8', '#D4E157', '#fff', '#E5574F'];
      return `<div class="rays"></div>` + Array.from({ length: mini ? 9 : 18 }, (_, i) => `<i class="confetti-bit" style="left:${rand(2, 98).toFixed(0)}%;background:${cols[i % cols.length]};animation-delay:${rand(0, 3).toFixed(1)}s;animation-duration:${rand(3, 5.5).toFixed(1)}s"></i>`).join('');
    }
    if (stage === 'zoomies') return `<i class="speed s1"></i><i class="speed s2"></i><i class="speed s3"></i>`;
    if (stage === 'curious') return `<span class="qmark" aria-hidden="true">?</span>`;
    if (stage === 'memory') return `<i class="mem-glow"></i>`;
    return '';
  }

  /**
   * Scene = illustrated backdrop + animated dog + effects. size: hero | card | detail | preview
   */
  P.sceneHTML = function (c, k, o = {}) {
    const stage = o.stage || k.stage;
    const size = o.size || 'card';
    const u = 's' + ++uid;
    const accent = c.accent || P.TYPES[c.type].accent;
    const showDoor = stage === 'waiting' || stage === 'packing';
    const dogSvg = P.dogSVG(c.dog, stage, {
      type: c.type, accent, bell: o.bell,
      label: o.interactive ? `${c.dog.name} the ${(P.BREEDS[c.dog.breed] || {}).label}. ${P.describeDog(c, k)}` : `${c.dog.name}, ${P.stageInfo(c, k).desc}`,
      zColor: '#fff',
    });
    const interactive = o.interactive;
    return `<div class="scene sz-${size}" data-type="${c.type}" data-stage="${stage}" data-cid="${c.id}" data-fixed="${o.stage ? 1 : 0}" style="--accent:${accent};--on:${P.onColor(accent)}">
      <svg class="scene-bg" viewBox="0 0 400 300" preserveAspectRatio="xMidYMax slice" aria-hidden="true" focusable="false">${bg(c.type, accent, u)}${showDoor ? door(c.type) : ''}</svg>
      <div class="scene-fx" aria-hidden="true">${fx(stage, size)}</div>
      <div class="dog-wrap">${interactive ? `<button type="button" class="dog-hit" data-dog aria-label="${P.esc(c.dog.name)}: tap to bark, press and hold to pet. Press P to pet, S to shake.">${dogSvg}</button>` : dogSvg}</div>
      <div class="scene-fx2" aria-hidden="true"></div>
      ${interactive ? `<div class="bubble" role="status" aria-live="polite"></div>` : ''}
    </div>`;
  };
})();
