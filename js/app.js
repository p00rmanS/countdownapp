/*
 * app.js - screens, navigation and interactions.
 *
 * How it works: there is no framework. Each screen is a function (viewHome, viewDetail, ...) that returns an
 * HTML string. render() picks one from location.hash (#/ home, #/c/<id> detail, #/new, #/edit/<id>,
 * #/memories, #/settings, #/welcome), puts it in #view and then attaches behaviour. Buttons carry
 * data-action="name"; ONE click listener at the bottom looks the name up in the `actions` table.
 * Forms in the create flow write into `ui.draft`, which is only turned into a real countdown when
 * you press the last button.
 *
 * Every second tick() updates the rolling digits in place (cheap) and, if a countdown has just moved to a
 * new stage or hit zero, re-renders so the dog changes mood without a reload.
 * bindScene() wires the dog: tap = bark, hold = pet, shake = sneeze, pull down = fetch.
 */
(function () {
  const P = window.Paw, S = P.store, { esc, icon } = P;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const view = $('#view'), tabbar = $('#tabbar'), sheetRoot = $('#sheet-root'), appEl = $('#app');

  const ui = { stack: [location.hash || '#/'], sort: 'soonest', draft: null, step: 0, flow: 'new', welcome: 0, welcomeBreed: 'scott', preview: 'waiting', sceneApi: null, onFetch: null };
  const MODES = [['full', 'Full timer'], ['days', 'Days'], ['sleeps', 'Sleeps'], ['weeks', 'Weeks']];

  /* =============================== helpers =============================== */
  const seg = (action, opts, val, extra = '') =>
    `<div class="seg" role="radiogroup" ${extra}>${opts.map(([v, l]) => `<button type="button" role="radio" aria-checked="${v === val}" class="${v === val ? 'on' : ''}" data-action="${action}" data-v="${v}">${l}</button>`).join('')}</div>`;
  const sw = (key, on, label, extra = '') =>
    `<button type="button" role="switch" aria-checked="${!!on}" aria-label="${esc(label)}" class="switch ${on ? 'on' : ''}" data-action="toggle" data-k="${key}" ${extra}><span></span></button>`;
  const sysDark = () => matchMedia('(prefers-color-scheme: dark)').matches;
  const sysReduce = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
  const accentOf = (c) => c.accent || P.TYPES[c.type].accent;
  const styleVars = (c) => `--accent:${accentOf(c)};--on:${P.onColor(accentOf(c))};--accent-soft:color-mix(in srgb, ${accentOf(c)} 18%, var(--surface))`;
  const announce = (msg) => { const el = $('#sr-live'); if (el) el.textContent = msg; };

  function applySettings() {
    const st = S.state.settings;
    const dark = st.theme === 'dark' || (st.theme === 'system' && sysDark());
    const root = document.documentElement;
    root.dataset.theme = dark ? 'dark' : 'light';
    root.dataset.motion = st.motion === 'on' || (st.motion === 'system' && sysReduce()) ? 'reduce' : 'full';
    const tc = $('meta[name="theme-color"]');
    if (tc) tc.content = dark ? '#171B28' : '#FFF8EE';
    P.native.applyTheme(dark);
    const fav = $('#favicon');
    if (fav) { fav.type = st.icon && st.icon !== 'scott' ? 'image/svg+xml' : 'image/png'; fav.href = st.icon && st.icon !== 'scott' ? P.dogIconURI(st.icon) : 'icons/favicon-32.png'; }
  }
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applySettings);
  matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', applySettings);

  let toastT;
  function toast(msg, ms = 2600) {
    const t = $('#toast');
    t.textContent = msg; t.classList.add('show');
    clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), ms);
  }

  /* ---------- sheets ---------- */
  let lastFocus;
  function openSheet(html, label) {
    lastFocus = document.activeElement;
    sheetRoot.innerHTML = `<div class="sheet-backdrop" data-action="close-sheet"></div><div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(label)}"><div class="grab"></div>${html}</div>`;
    requestAnimationFrame(() => { sheetRoot.classList.add('open'); const f = $('.sheet button, .sheet input', sheetRoot); if (f) f.focus({ preventScroll: true }); });
  }
  function closeSheet() {
    if (!sheetRoot.classList.contains('open')) return;
    sheetRoot.classList.remove('open');
    setTimeout(() => { if (!sheetRoot.classList.contains('open')) sheetRoot.innerHTML = ''; }, 280);
    if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  }

  /* =============================== countdown bits =============================== */
  const digits = (v, pad) => [...String(v).padStart(pad, '0')].map((ch) => `<span class="dg" style="--d:${ch}"><span class="strip">${'0123456789'.replace(/./g, '<i>$&</i>')}</span></span>`).join('');

  function numeralsHTML(c, k, cls = '') {
    const key = P.stageKey(k);
    if (k.phase === 'past') return `<div class="numerals words ${cls}" data-cid="${c.id}" data-key="${key}"><span class="big-words">It happened! 💛</span></div>`;
    if (k.phase === 'today' && k.remaining <= 0) return `<div class="numerals words ${cls}" data-cid="${c.id}" data-key="${key}"><span class="big-words">It's today! 🎉</span></div>`;
    const units = P.units(k, c.displayMode);
    return `<div class="numerals m-${c.displayMode} ${cls}" role="timer" data-cid="${c.id}" data-mode="${c.displayMode}" data-key="${key}" aria-label="${esc(P.spoken(k))} left">${units
      .map((u) => `<span class="unit" data-k="${u.k}" data-n="${String(u.v).padStart(u.pad, '0').length}"><span class="num" aria-hidden="true">${digits(u.v, u.pad)}</span><span class="lab">${u.label}</span></span>`)
      .join('')}</div>`;
  }
  function updateNumerals(el) {
    const c = S.get(el.dataset.cid); if (!c) return false;
    const k = P.compute(c);
    if (el.dataset.key !== P.stageKey(k)) return true;
    if (!el.dataset.mode) return false;
    P.units(k, el.dataset.mode).forEach((u) => {
      const ue = el.querySelector(`.unit[data-k="${u.k}"]`); if (!ue) return;
      const s = String(u.v).padStart(u.pad, '0');
      if (+ue.dataset.n !== s.length) { ue.querySelector('.num').innerHTML = digits(u.v, u.pad); ue.dataset.n = s.length; }
      else [...ue.querySelectorAll('.dg')].forEach((d, i) => d.style.setProperty('--d', s[i]));
      const lab = ue.querySelector('.lab'); if (lab.textContent !== u.label) lab.textContent = u.label;
    });
    if (k.parts.s === 0) el.setAttribute('aria-label', P.spoken(k) + ' left');
    return false;
  }

  function shortCount(c, k) {
    if (k.phase === 'today') return "It's today! 🎉";
    if (k.phase === 'past') return P.plural(k.daysSince, 'day') + ' ago';
    switch (c.displayMode) {
      case 'days': return P.plural(k.daysCeil, 'day');
      case 'sleeps': return P.plural(k.sleeps, 'sleep');
      case 'weeks': return k.weeks ? `${k.weeks} wk${k.weeks === 1 ? '' : 's'} ${k.weekDays} d` : P.plural(k.parts.d, 'day');
      default: return k.parts.d ? `${k.parts.d}d ${k.parts.h}h` : `${k.parts.h}h ${String(k.parts.m).padStart(2, '0')}m`;
    }
  }

  function leashHTML(c, k, cls = '') {
    const pct = Math.round(k.progress * 100);
    const label = k.phase === 'upcoming' ? `Day ${k.daysIn} of ${k.daysTotal}` : k.phase === 'today' ? 'Today is the day!' : 'Made it!';
    return `<div class="leash ${cls}" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${pct}" aria-label="Countdown progress">
      <div class="leash-row"><div class="leash-track"><div class="leash-fill" style="width:${pct}%"></div><span class="leash-paw" style="left:${pct}%">${icon('paw')}</span></div><span class="leash-end" aria-hidden="true">${icon(P.TYPES[c.type].icon)}</span></div>
      <div class="leash-cap"><span>${label}</span><span>${pct}%</span></div></div>`;
  }

  const chip = (txt, cls = '') => `<span class="chip ${cls}">${txt}</span>`;
  const stageChip = (c, k) => { const i = P.stageInfo(c, k); return chip(`${icon(i.icon)} ${i.label}`, 'stage'); };

  /* =============================== SHARING ===============================
     A shared countdown is just its data, base64url-encoded in a link:  https://.../#/import/<code>
     Opening the link adds a copy to the other person's app (web, Android or iPhone - same format). No server. */
  const SHARE_WEB = 'https://p00rmans.github.io/countdownapp/';
  const b64url = (str) => btoa(unescape(encodeURIComponent(str))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const unb64url = (b) => decodeURIComponent(escape(atob(b.replace(/-/g, '+').replace(/_/g, '/'))));
  function shareLink(c) {
    const o = { ...c }; ['id', 'createdAt', 'archived', 'memoryPhotos', 'sample', 'notifications'].forEach((k) => delete o[k]); o.v = 1;
    return SHARE_WEB + '#/import/' + b64url(JSON.stringify(o));
  }
  /** returns the new countdown's id, or null if the text isn't a shared countdown */
  function importShared(text) {
    try {
      let raw = String(text).trim(); const i = raw.lastIndexOf('/import/'); if (i >= 0) raw = raw.slice(i + 8);
      const j = raw.indexOf('d='); if (j >= 0) raw = raw.slice(j + 2);
      raw = raw.split(/[&#]/)[0];
      const o = JSON.parse(unb64url(raw));
      const c = P.cleanCountdown({ ...o, id: P.uuid(), createdAt: new Date().toISOString(), archived: false, sample: false, memoryPhotos: [], notifications: undefined });
      if (!c) return null;
      S.upsert(c); S.state.onboarded = true; S.save();
      return c.id;
    } catch (e) { return null; }
  }

  /* =============================== HOME =============================== */
  function greeting() { const h = new Date().getHours(); return h < 5 ? 'Late night, huh?' : h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'; }

  function viewHome() {
    const now = Date.now();
    const all = S.active().map((c) => ({ c, k: P.compute(c, now) }));
    const items = all.filter((x) => x.k.phase !== 'past');
    const mem = all.length - items.length + S.state.countdowns.filter((c) => c.archived).length;
    items.sort((a, b) => (a.k.phase === 'today' ? 0 : 1) - (b.k.phase === 'today' ? 0 : 1) || a.k.target - b.k.target);
    const date = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(now);

    let body;
    if (!items.length) {
      const empty = { id: 'empty', type: 'custom', dog: { breed: 'scott', name: 'Scott' }, accent: '#E8A15C', title: '', displayMode: 'full' };
      body = `<div class="empty">
        <div class="empty-scene">${P.sceneHTML(empty, { stage: 'nap', phase: 'upcoming', remaining: 1 }, { stage: 'nap', size: 'hero' })}</div>
        <h2>Nothing to wait for… yet</h2>
        <p class="muted">${mem ? 'Your past adventures live in Memories. ' : ''}Start a countdown and ${empty.dog.name} will do the rest.</p>
        <div class="stack"><a class="btn btn-primary" href="#/new">${icon('plus')} New countdown</a><button class="btn btn-ghost" data-action="samples">Peek at sample adventures</button></div></div>`;
    } else {
      const hero = items[0];
      let rest = items.slice(1);
      if (ui.sort === 'type') rest.sort((a, b) => P.TYPE_ORDER.indexOf(a.c.type) - P.TYPE_ORDER.indexOf(b.c.type) || a.k.target - b.k.target);
      body = `${heroCard(hero.c, hero.k)}
        ${rest.length ? `<div class="section-head"><h2>Coming up</h2>${seg('sort', [['soonest', 'Soonest'], ['type', 'By type']], ui.sort, 'aria-label="Sort countdowns"')}</div>
        <div class="grid">${rest.map((x) => gridCard(x.c, x.k)).join('')}</div>` : ''}`;
    }
    return `<section class="screen home">
      <header class="topbar"><div><p class="eyebrow">${esc(date)}</p><h1 class="h-title">${greeting()}${S.state.settings.profileName ? ', ' + esc(S.state.settings.profileName) : ''}</h1>${items.length ? `<p class="sub">${items.length === 1 ? '1 adventure' : items.length + ' adventures'} on the way</p>` : ''}</div>
        <div class="top-actions"><a class="icon-btn avatar-btn" href="#/settings" aria-label="Your profile">${avatar(40)}</a><a class="icon-btn" href="#/settings" aria-label="Settings">${icon('sliders')}</a></div></header>
      ${body}
      ${items.length ? `<p class="foot-hint">Pull down to throw the ball 🎾</p>` : ''}
    </section>`;
  }

  function heroCard(c, k) {
    const tp = P.TYPES[c.type], w = P.when(c, k);
    return `<a class="hero-card" href="#/c/${c.id}" style="${styleVars(c)}" aria-label="${esc(c.title)}. ${esc(P.spoken(k))} left. ${esc(P.describeDog(c, k))}">
      ${P.sceneHTML(c, k, { size: 'hero' })}
      <div class="hero-body">
        <div class="chip-row">${stageChip(c, k)}${chip(`${icon(tp.icon)} ${tp.short}`, 'ghost')}</div>
        <h2 class="hero-title">${esc(c.title)}</h2>
        <p class="muted small">${k.phase === 'today' ? 'Happening today' : esc(w.dest)}</p>
        ${numeralsHTML(c, k, 'sz-hero')}
        ${leashHTML(c, k, 'compact')}
      </div></a>`;
  }
  function gridCard(c, k) {
    const tp = P.TYPES[c.type];
    return `<a class="card" href="#/c/${c.id}" style="${styleVars(c)}" aria-label="${esc(c.title)}. ${esc(shortCount(c, k))}. ${esc(P.stageInfo(c, k).desc)}">
      ${P.sceneHTML(c, k, { size: 'card' })}
      <div class="card-body"><h3>${esc(c.title)}</h3><p class="card-count" data-short="${c.id}">${esc(shortCount(c, k))}</p>
      <div class="chip-row">${chip(`${icon(P.STAGES[k.stage].icon)} ${P.stageInfo(c, k).label}`, 'tiny stage')}</div></div></a>`;
  }

  /* =============================== DETAIL =============================== */
  function checklistLabel(t) { return t === 'intl_trip' || t === 'vacation' ? 'Packing list' : t === 'birthday' ? 'Gift ideas' : 'Checklist'; }

  function viewDetail(id) {
    const c = S.get(id);
    if (!c) { location.replace('#/'); return ''; }
    const k = P.compute(c), tp = P.TYPES[c.type], info = P.stageInfo(c, k), w = P.when(c, k);
    const past = k.phase === 'past';
    const facts = [];
    if (c.type === 'birthday' && k.age != null) facts.push(`${esc(c.person.name || 'They')} turn${(c.person.name || '') && !/^(you|they)$/i.test(c.person.name) ? 's' : ''} <b>${k.age}</b>`);
    if (c.type === 'anniversary' && k.together != null) facts.push(`Together for <b>${k.together.toLocaleString()}</b> days`);
    if (c.recurrence === 'yearly') facts.push('Repeats every year');
    const done = c.checklist.filter((x) => x.done).length;
    const earned = P.MILESTONES.filter((m) => (past || k.phase === 'today') ? true : k.daysCeil <= m && k.daysTotal >= m);
    return `<section class="detail" style="${styleVars(c)}">
      <div class="detail-scene">
        <div class="detail-bar"><button class="glass-btn" data-action="back" aria-label="Back">${icon('back')}</button>
          <span class="glass-pill">${icon(info.icon)} ${esc(info.label)}</span>
          <button class="glass-btn" data-action="menu" aria-label="More options for ${esc(c.title)}">${icon('more')}</button></div>
        ${P.sceneHTML(c, k, { size: 'detail', interactive: true })}
        <p class="scene-hint" aria-hidden="true">Tap to bark · Hold to pet · Shake</p>
        ${playTray(c)}
      </div>
      <div class="detail-sheet">
        <div class="chip-row center">${chip(`${icon(tp.icon)} ${tp.short}`, 'ghost')}${c.destination ? chip(`<span aria-hidden="true">${c.destination.flag}</span> ${esc(c.destination.city || c.destination.country)}`, 'ghost') : ''}${facts.map((f) => chip(f, 'ghost')).join('')}</div>
        <h1 class="detail-title">${esc(c.title)}</h1>
        <p class="when">${past ? esc(w.dest) : k.phase === 'today' ? 'Happening today' : esc(w.dest)}${w.local && !past ? `<br><span class="muted small">${esc(w.local)} your time</span>` : ''}</p>
        ${past ? `<div class="numerals words sz-detail"><span class="big-words">${P.plural(k.daysSince, 'day')} ago 💛</span></div>` : numeralsHTML(c, k, 'sz-detail')}
        ${past ? '' : leashHTML(c, k)}
        <p class="dog-says" data-says>${esc(info.desc)}.</p>
      </div>

      <div class="detail-more">
        ${past ? photosSection(c) : ''}
        <div class="panel"><h2 class="panel-h">Display</h2>${seg('detail-mode', MODES, c.displayMode, 'aria-label="Countdown display mode"')}</div>
        <div class="panel"><h2 class="panel-h">${icon('globe')} When</h2>
          <dl class="kv"><div><dt>${w.sameTz ? 'Date' : esc((c.destination && c.destination.city) || w.destCity) + ' time'}</dt><dd>${esc(w.dest)}</dd></div>
          ${w.local ? `<div><dt>Your time</dt><dd>${esc(w.local)}</dd></div>` : ''}
          <div><dt>Time zone</dt><dd>${esc(P.tzLabel(k.tz))}</dd></div>
          ${c.recurrence === 'yearly' ? `<div><dt>Repeats</dt><dd>Every year · next is ${k.occYear}</dd></div>` : ''}</dl></div>
        <div class="panel"><div class="panel-top"><h2 class="panel-h">${checklistLabel(c.type)}</h2><span class="muted small">${done}/${c.checklist.length}</span></div>
          <ul class="checks">${c.checklist.map((it, i) => `<li><button class="check ${it.done ? 'done' : ''}" role="checkbox" aria-checked="${it.done}" data-action="check" data-i="${i}"><span class="box">${icon('check')}</span><span class="txt">${esc(it.text)}</span></button><button class="x" data-action="del-check" data-i="${i}" aria-label="Remove ${esc(it.text)}">${icon('close')}</button></li>`).join('')}</ul>
          <form class="add-check" data-form="check"><input name="text" placeholder="Add something…" aria-label="Add to ${checklistLabel(c.type).toLowerCase()}" maxlength="80" autocomplete="off"><button class="btn btn-small" type="submit">Add</button></form></div>
        <div class="panel"><h2 class="panel-h">Notes</h2><textarea class="notes" data-notes rows="3" placeholder="Itinerary, ideas, inside jokes…" aria-label="Notes">${esc(c.notes || '')}</textarea></div>
        <div class="panel"><h2 class="panel-h">${icon('bell')} Reminders</h2><p class="muted small">At most one per day, never spammy.</p>
          <ul class="rows">${c.notifications.map((n, i) => `<li><span>${esc(n.label)}</span>${sw('n' + i, n.enabled, n.label, `data-i="${i}" data-nt="1"`)}</li>`).join('')}</ul></div>
        <div class="panel"><h2 class="panel-h">Milestones</h2><div class="miles">${P.MILESTONES.map((m) => `<div class="mile ${earned.includes(m) ? 'got' : ''}" title="${m} days"><span class="mile-n">${m}</span><span class="mile-l">${m === 1 ? 'day' : 'days'}</span>${earned.includes(m) ? `<span class="mile-p">${icon('paw')}</span>` : ''}</div>`).join('')}</div></div>
        <div class="panel panel-dog"><div class="dog-card-ic">${P.dogIconSVG(c.dog.breed)}</div><div><p class="strong">${esc(c.dog.name)}</p><p class="muted small">${esc(P.BREEDS[c.dog.breed].label)} · ${esc(P.BREEDS[c.dog.breed].vibe)}</p></div></div>
      </div></section>`;
  }

  function photosSection(c) {
    const ph = c.memoryPhotos || [];
    return `<div class="panel"><div class="panel-top"><h2 class="panel-h">${icon('camera')} Memory photos</h2><button class="btn btn-small" data-action="photo-pick" data-id="${c.id}">Add photos</button></div>
      ${ph.length ? `<div class="photo-grid">${ph.map((p, i) => `<figure class="polaroid"><img src="${p}" alt="Photo ${i + 1} from ${esc(c.title)}"><button class="x" data-action="photo-del" data-i="${i}" aria-label="Remove photo ${i + 1}">${icon('close')}</button></figure>`).join('')}</div>` : `<p class="muted">${esc(c.dog.name)} is keeping this spot warm. Add a few photos and this countdown becomes a memory card.</p>`}
      <input type="file" accept="image/*" multiple hidden data-photo-input data-id="${c.id}"></div>`;
  }

  const playTray = (c) => `<div class="play-tray" role="group" aria-label="Play with ${esc(c.dog.name)}">
      <div class="joy" data-joy role="status" aria-live="polite"><span class="joy-h" aria-hidden="true">♥</span><span class="joy-bar"><i data-joy-fill></i></span><span class="joy-t" data-joy-text></span></div>
          <div class="needs" aria-live="polite"><span class="need" data-need="food">${icon('bowl')}<i><b data-need-fill="food"></b></i></span><span class="need" data-need="water">${icon('drop')}<i><b data-need-fill="water"></b></i></span></div>
      <div class="play-btns">
        <button class="play-btn" data-play="treat">${icon('bone')}Treat</button>
        <button class="play-btn" data-play="feed">${icon('bowl')}Feed</button>
        <button class="play-btn" data-play="drink">${icon('drop')}Water</button>
        <button class="play-btn" data-play="ball">${icon('ball')}Ball</button>
        <button class="play-btn" data-play="tickle">${icon('smile')}Tickle</button>
      </div>
        </div>`;

  /* =============================== MEMORIES =============================== */
  function viewMemories() {
    const now = Date.now();
    const list = S.state.countdowns.map((c) => ({ c, k: P.compute(c, now) })).filter((x) => x.k.phase === 'past' || x.c.archived).sort((a, b) => b.k.target - a.k.target);
    const yr = new Date().getFullYear(), thisYear = list.filter((x) => new Date(x.k.target).getFullYear() === yr).length;
    return `<section class="screen memories">
      <header class="topbar"><div><p class="eyebrow">Keepsakes</p><h1 class="h-title">Memories</h1></div><a class="icon-btn" href="#/settings" aria-label="Settings">${icon('sliders')}</a></header>
      ${list.length ? `<div class="year-card"><span class="year-n">${list.length}</span><p>${list.length === 1 ? 'adventure' : 'adventures'} counted down together${thisYear ? ` · <b>${thisYear}</b> in ${yr}` : ''}.</p></div>
        <div class="polaroids">${list.map(({ c, k }, i) => {
          const tp = P.TYPES[c.type], ph = (c.memoryPhotos || [])[0];
          return `<a class="polaroid-card" href="#/c/${c.id}" style="${styleVars(c)};--tilt:${i % 2 ? 1.6 : -1.6}deg" aria-label="${esc(c.title)}, ${esc(P.fmtInstant(k.target, k.tz, { allDay: true }))}">
            <div class="pol-img">${ph ? `<img src="${ph}" alt="">` : P.sceneHTML(c, k, { size: 'card', stage: 'memory' })}</div>
            <p class="pol-t">${esc(c.title)}</p><p class="pol-d muted small">${icon(tp.icon, 'inline')} ${esc(P.fmtInstant(k.target, k.tz, { allDay: true, noYear: false }))}${c.archived ? ' · archived' : ''}</p></a>`;
        }).join('')}</div>`
        : (() => { const demo = ui.demo = { ...P.makeSamples().find((x) => x.key === 'lisbon' || x.id === 'sample-lisbon'), id: 'demo-memory', title: 'Memories', dog: { breed: 'scott', name: 'Scott' }, accent: '#EE8FA0', joy: { v: 40, t: Date.now() } }; const k = P.compute(demo);
          return `<div class="empty"><div class="empty-scene mem-play">${P.sceneHTML(demo, k, { size: 'detail', interactive: true, stage: 'memory' })}</div>${playTray(demo).replace('class="play-tray"', 'class="play-tray static"')}<h2>No memories yet</h2><p class="muted">When a countdown ends, it lands here as a keepsake — with your photos and a very proud dog. Until then, Scott is happy to be petted.</p></div>`; })()}
    </section>`;
  }

  /* =============================== SETTINGS =============================== */
  /* profile picture: the person's own photo if they added one, otherwise their app-icon dog */
  function avatar(px) {
    const st = S.state.settings;
    return /^data:image\//.test(st.profilePhoto || '') ? `<img class="avatar" width="${px}" height="${px}" src="${st.profilePhoto}" alt="">` : `<span class="avatar" style="width:${px}px;height:${px}px">${P.dogIconSVG(st.icon || 'scott')}</span>`;
  }
  function viewSettings() {
    const st = S.state.settings;
    const perm = P.native.notifyState();
    return `<section class="screen settings">
      <header class="topbar"><button class="icon-btn" data-action="back" aria-label="Back">${icon('back')}</button><h1 class="h-title mid">Settings</h1><span class="icon-btn ghost-slot"></span></header>
      <div class="set-hero"><div class="set-logo">${P.dogIconSVG(st.icon || 'scott')}</div><div><p class="strong">Pawcount</p><p class="muted small">Every day closer is another tail wag.</p></div></div>

      <h2 class="group-h">Profile</h2>
      <div class="panel profile">
        <button class="avatar-edit" data-action="profile-photo" aria-label="Choose a profile photo">${avatar(72)}<span class="avatar-cam">${icon('camera')}</span></button>
        <div class="profile-body"><label class="field"><span>Your name</span>
          <input id="profileName" data-profile-name maxlength="30" autocomplete="given-name" placeholder="What should Scott call you?" value="${esc(st.profileName || '')}"></label>
          <div class="field"><span>What does your dog call you?</span>${seg('set-parent', [['mama', 'Mama'], ['papa', 'Papa'], ['parent', 'Fur parent']], st.parentTitle || 'parent', 'aria-label="What your dog calls you"')}</div>
          ${/^data:image\//.test(st.profilePhoto || '') ? `<button class="btn btn-small btn-ghost" data-action="profile-photo-clear">Remove photo</button>` : ''}</div>
        <input type="file" accept="image/*" hidden data-profile-input>
      </div>
      <p class="muted small">Stays on this device and in your backup. Sign-in with Google or Apple, so your profile follows you between phones, is planned.</p>

      <h2 class="group-h">Look &amp; feel</h2>
      <div class="panel flush">
        <div class="set-row col"><span class="set-t">Default display</span>${seg('set-default-mode', MODES, st.displayMode, 'aria-label="Default display mode"')}</div>
        <div class="set-row col"><span class="set-t">Theme</span>${seg('set-theme', [['system', 'System'], ['light', 'Light'], ['dark', 'Dark']], st.theme, 'aria-label="Theme"')}</div>
        <div class="set-row col"><span class="set-t">Reduce motion</span><span class="muted small">Swaps loops for gentle cross-fades. The dog keeps their expression.</span>${seg('set-motion', [['system', 'System'], ['on', 'On'], ['off', 'Off']], st.motion, 'aria-label="Reduce motion"')}</div>
      </div>

      <h2 class="group-h">Feedback</h2>
      <div class="panel flush">
        <div class="set-row"><div><span class="set-t">Haptics</span><span class="muted small block">Taps, purrs and milestone bursts</span></div>${sw('haptics', st.haptics, 'Haptics')}</div>
        <div class="set-row"><div><span class="set-t">Sounds</span><span class="muted small block">Soft barks &amp; chimes · off by default</span></div>${sw('sound', st.sound, 'Sounds')}</div>
        <div class="set-row"><div><span class="set-t">Reminders</span><span class="muted small block">${perm === 'denied' ? 'Blocked in your device settings' : perm === 'unsupported' ? (P.native.isIOS && !P.native.standalone ? 'Add to Home Screen first (iPhone)' : 'Not supported in this browser') : 'Milestones &amp; the morning of'}</span></div>${sw('notifications', st.notifications && perm === 'granted', 'Reminders')}</div>
      </div>

      ${installPanel()}
      <h2 class="group-h">App icon</h2>
      <div class="panel"><div class="icon-picker" role="radiogroup" aria-label="App icon">${P.BREED_ORDER.map((b) => `<button class="icon-opt ${st.icon === b ? 'on' : ''}" role="radio" aria-checked="${st.icon === b}" data-action="set-icon" data-v="${b}" aria-label="${esc(P.BREEDS[b].label)} icon"><span class="ic">${P.dogIconSVG(b)}</span><span class="small">${esc(P.BREEDS[b].name)}</span>${st.icon === b ? `<span class="tick">${icon('check')}</span>` : ''}</button>`).join('')}</div></div>

      <h2 class="group-h">Your data</h2>
      <div class="panel flush">
        <p class="set-note">Everything stays on this device. No account, no tracking, no ads.</p>
        <button class="set-link" data-action="export">${icon('download')}<span>Export backup</span>${icon('chevron')}</button>
        <button class="set-link" data-action="import">${icon('upload')}<span>Import backup</span>${icon('chevron')}</button>
        <button class="set-link" data-action="samples">${icon('paw')}<span>Load sample adventures</span>${icon('chevron')}</button>
        ${S.state.countdowns.some((c) => c.sample) ? `<button class="set-link" data-action="clear-samples">${icon('trash')}<span>Remove sample adventures</span>${icon('chevron')}</button>` : ''}
        <button class="set-link danger" data-action="reset">${icon('trash')}<span>Erase everything</span>${icon('chevron')}</button>
        <input type="file" accept="application/json,.json" hidden data-import-input>
      </div>

      <div class="teaser"><div class="teaser-dogs">${['corgi', 'dachshund'].map((b) => `<span>${P.dogIconSVG(b)}</span>`).join('')}</div><div><p class="strong">For two · coming in v1.1</p><p class="muted small">Share a countdown with your person. Both phones stay in sync and you'll see who petted the dog today.</p></div></div>
      <p class="foot-hint">Pawcount prototype · v1.0</p>
    </section>`;
  }

  function installPanel() {
    if (P.native.standalone) return '';
    const N = P.native;
    let inner;
    if (ui.installEvt) inner = `<p class="set-note">Install Pawcount for a full-screen app, offline use and reminders.</p><div class="pad"><button class="btn btn-primary" data-action="install">${icon('download')} Install app</button></div>`;
    else if (N.isIOS) inner = `<ol class="steps"><li>Tap the <b>Share</b> button <span class="kbd-ic" aria-hidden="true">⎋</span> in Safari</li><li>Choose <b>Add to Home Screen</b></li><li>Open Pawcount from your Home Screen</li></ol>`;
    else if (N.isAndroid) inner = `<ol class="steps"><li>Tap the <b>⋮</b> menu in Chrome</li><li>Choose <b>Install app</b> or <b>Add to Home screen</b></li></ol>`;
    else return '';
    return `<h2 class="group-h">Install</h2><div class="panel">${inner}</div>`;
  }

  /* =============================== WELCOME =============================== */
  function viewWelcome() {
    const step = ui.welcome;
    const dogs = P.BREED_ORDER;
    const mk = (b, stage = 'waiting', extra = {}) => P.dogSVG({ breed: b, name: P.BREEDS[b].name, ...(b === 'mutt' ? { ears: 'floppy' } : {}) }, stage, { type: 'custom', accent: '#E8A15C', ...extra });
    let inner;
    if (step === 0) {
      inner = `<div class="w-pack" aria-hidden="true">${dogs.map((b, i) => `<div class="w-dog d${i}" style="--i:${i}">${mk(b, ['waiting', 'curious', 'waiting', 'packing', 'curious', 'waiting'][i])}</div>`).join('')}</div>
        <h1 class="w-title">Countdowns with a dog who <em>can't wait</em> either.</h1>
        <p class="w-sub">Every trip, birthday and big day gets a companion that gets more excited as it gets closer.</p>`;
    } else if (step === 1) {
      inner = `<h1 class="w-title sm">Pick your first companion</h1><p class="w-sub">Every breed has its own personality. You can mix and match later.</p>
        <div class="w-pick" role="radiogroup" aria-label="First companion">${dogs.map((b) => `<button class="pick ${ui.welcomeBreed === b ? 'on' : ''}" role="radio" aria-checked="${ui.welcomeBreed === b}" data-action="w-breed" data-v="${b}"><div class="pick-dog">${mk(b, ui.welcomeBreed === b ? 'curious' : 'waiting', { label: P.BREEDS[b].label })}</div><b>${esc(P.BREEDS[b].name)}</b><span class="small muted">${esc(P.BREEDS[b].label)}</span><span class="small vibe">${esc(P.BREEDS[b].vibe)}</span></button>`).join('')}</div>`;
    } else {
      inner = `<div class="w-bell">${mk(ui.welcomeBreed, 'waiting', { bell: true, label: `${P.BREEDS[ui.welcomeBreed].name} wearing a little bell` })}</div>
        <h1 class="w-title sm">${esc(P.BREEDS[ui.welcomeBreed].name)} will ring the bell</h1><p class="w-sub">Allow reminders so ${esc(P.BREEDS[ui.welcomeBreed].name)} can tap you on the shoulder at 100, 50, 30, 7 and 1 days — and the morning of. One a day, tops.</p>`;
    }
    const cta = step === 0 ? ['w-next', "Let's go"] : step === 1 ? ['w-next', `Choose ${P.BREEDS[ui.welcomeBreed].name}`] : ['w-bell', 'Allow reminders'];
    return `<section class="welcome">
      <div class="w-top"><ol class="trail small" aria-label="Step ${step + 1} of 3">${[0, 1, 2].map((i) => `<li class="${i <= step ? 'on' : ''}">${icon('paw')}</li>`).join('')}</ol>${step < 2 ? `<button class="link-btn" data-action="w-skip">Skip</button>` : '<span></span>'}</div>
      <div class="w-body step-${step}">${inner}</div>
      <div class="w-cta">
        <button class="btn btn-primary btn-xl" data-action="${cta[0]}">${cta[1]}</button>
        ${step === 2 ? `<button class="btn btn-ghost" data-action="w-later">Maybe later</button>` : step === 0 ? `<button class="btn btn-ghost" data-action="samples">Peek at sample adventures</button>` : ''}
      </div></section>`;
  }

  /* =============================== CREATE / EDIT FLOW =============================== */
  const STEPS = ['What?', 'When?', 'Which dog?', 'Style', 'Done!'];
  const HEADS = ['What are you counting down to?', 'When is the big moment?', 'Choose their dog', 'Make it yours', ''];
  const todayStr = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
  const TZS = (() => { try { return Intl.supportedValuesOf('timeZone'); } catch (e) { return P.COUNTRIES.map((x) => x[2]); } })();

  function newDraft(type = 'intl_trip', breed) {
    const d = new Date(Date.now() + 30 * 86400000);
    const dr = {
      id: null, type: '', title: '', titleAuto: true, date: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
      time: '09:00', allDay: false, tz: P.localTz(), recurrence: 'none', country: '', personName: '', birthYear: '',
      breed: breed || '', breedTouched: !!breed, dogName: '', nameTouched: false, fur: '#B98A62', ears: 'floppy', accent: '', accentTouched: false,
      displayMode: S.state.settings.displayMode, notifications: null, notes: '', checklist: [], createdAt: null, sample: false, memoryPhotos: [],
    };
    applyType(dr, type);
    return dr;
  }
  function applyType(dr, type) {
    const tp = P.TYPES[type];
    dr.type = type;
    if (!dr.accentTouched) dr.accent = tp.accent;
    dr.recurrence = tp.repeat ? 'yearly' : 'none';
    dr.allDay = type === 'birthday' || type === 'anniversary' || type === 'holiday';
    if (!dr.breedTouched) { dr.breed = P.suggestedBreed(type); }
    if (!dr.nameTouched) dr.dogName = P.BREEDS[dr.breed].name;
    dr.notifications = P.defaultNotifications(type);
    if (type !== 'intl_trip') dr.country = '';
  }
  function draftFromCountdown(c) {
    const [date, time] = c.targetAt.split('T');
    return {
      id: c.id, type: c.type, title: c.title, titleAuto: false, date, time: time || '09:00', allDay: c.allDay, tz: c.timeZone, recurrence: c.recurrence,
      country: c.destination ? c.destination.country : '', personName: c.person ? c.person.name : '', birthYear: c.person && c.person.birthYear ? c.person.birthYear : '',
      breed: c.dog.breed, breedTouched: true, dogName: c.dog.name, nameTouched: true, fur: (c.dog.colors && c.dog.colors.fur) || '#B98A62', ears: c.dog.ears || 'floppy',
      accent: accentOf(c), accentTouched: true, displayMode: c.displayMode, notifications: c.notifications.map((n) => ({ ...n })), notes: c.notes || '', checklist: c.checklist || [],
      createdAt: c.createdAt, sample: !!c.sample, memoryPhotos: c.memoryPhotos || [], origTarget: c.targetAt + '|' + c.timeZone,
    };
  }
  function draftToCountdown(d) {
    const country = P.COUNTRIES.find((x) => x[0] === d.country);
    const targetAt = `${d.date}T${d.allDay ? '00:00' : d.time || '09:00'}`;
    const keepCreated = d.createdAt && d.origTarget === targetAt + '|' + d.tz;
    const dog = { breed: d.breed, name: d.dogName.trim() || P.BREEDS[d.breed].name };
    if (d.breed === 'mutt') { dog.colors = { fur: d.fur }; dog.ears = d.ears; }
    const c = {
      id: d.id || P.uuid(), title: d.title.trim(), type: d.type, targetAt, timeZone: d.tz, allDay: d.allDay, recurrence: d.recurrence,
      createdAt: keepCreated ? d.createdAt : new Date().toISOString(), dog, accent: d.accent, displayMode: d.displayMode, notes: d.notes,
      checklist: d.checklist, notifications: d.notifications, archived: false, memoryPhotos: d.memoryPhotos, sample: d.sample || undefined,
    };
    if (d.type === 'intl_trip' && country) c.destination = { country: country[0], city: country[3], flag: country[1] };
    if (d.type === 'birthday' && (d.personName || d.birthYear)) c.person = { name: d.personName.trim(), birthYear: d.birthYear ? +d.birthYear : undefined };
    const old = d.id && S.get(d.id); if (old) { c.archived = old.archived; if (old.joy) c.joy = old.joy; }
    return c;
  }
  function draftComp(forceStage) {
    const c = draftToCountdown(ui.draft);
    let k;
    try { k = P.compute({ ...c, createdAt: new Date(Date.now() - 20 * 86400000).toISOString() }); } catch (e) { k = null; }
    if (!k) k = { stage: 'waiting', phase: 'upcoming', remaining: 864e6, sleeps: 10, daysCeil: 10, parts: { d: 10, h: 0, m: 0, s: 0 }, weeks: 1, weekDays: 3, progress: 0.4, daysIn: 8, daysTotal: 20, target: Date.now() + 864e6, tz: c.timeZone, occYear: new Date().getFullYear() };
    return { c: { ...c, title: c.title || P.TYPES[c.type].ph }, k: { ...k, stage: forceStage || k.stage } };
  }
  function whenNote() {
    const d = ui.draft; if (!d.date) return { ok: false, text: 'Pick a date to continue.' };
    let c, k;
    try { c = draftToCountdown(d); k = P.compute(c); } catch (e) { k = null; }
    if (!k) return { ok: false, text: 'That date doesn’t look right.' };
    if (k.phase === 'past' && ui.flow === 'new') return { ok: false, text: 'That moment has already passed — pick a date in the future.' };
    if (k.phase === 'today') return { ok: true, text: "That's today! 🎉" };
    const days = Math.ceil(k.remaining / 86400000);
    const tzNote = k.tz !== P.localTz() ? ` Counts to the moment in ${k.tz.split('/').pop().replace(/_/g, ' ')}.` : '';
    return { ok: true, text: `${c.recurrence === 'yearly' && k.occYear !== +d.date.slice(0, 4) ? 'Next up is' : "That's"} in <b>${P.plural(days, 'day')}</b>${days >= 14 ? ` — about ${Math.round(days / 7)} weeks` : ''}.${tzNote}` };
  }
  function stepValid() {
    const d = ui.draft;
    if (ui.step === 0) return d.title.trim().length > 0;
    if (ui.step === 1) return whenNote().ok;
    if (ui.step === 2) return !!d.breed && d.dogName.trim().length > 0;
    return true;
  }

  function viewFlow() {
    const d = ui.draft, step = ui.step;
    const accent = d.accent || P.TYPES[d.type].accent;
    const cta = step === 4 ? (ui.flow === 'edit' ? 'Save changes' : `Meet ${esc(d.dogName || 'your dog')}`) : step === 3 ? 'Finish' : 'Next';
    return `<section class="flow" style="--accent:${accent};--on:${P.onColor(accent)};--accent-soft:color-mix(in srgb, ${accent} 18%, var(--surface))">
      <header class="flow-top"><button class="icon-btn" data-action="flow-back" aria-label="${step === 0 ? 'Close' : 'Previous step'}">${icon(step === 0 ? 'close' : 'back')}</button>
        <span class="icon-btn ghost-slot"></span></header>
      <div class="flow-head">${step < 4 ? `<p class="eyebrow">Step ${step + 1} of 5 · ${STEPS[step]}</p><h1>${HEADS[step]}</h1>` : ''}</div>
      <div id="flow-body" class="flow-body">${stepBody()}</div>
      <footer class="flow-cta"><button id="flowNext" class="btn btn-primary btn-xl" data-action="flow-next" ${stepValid() ? '' : 'disabled'}>${cta}</button></footer>
    </section>`;
  }

  function stepBody() {
    const d = ui.draft, step = ui.step;
    if (step === 0) {
      const tp = P.TYPES[d.type];
      return `<div class="type-grid" role="radiogroup" aria-label="Event type">${P.TYPE_ORDER.map((t) => { const x = P.TYPES[t]; return `<button class="type-card ${d.type === t ? 'on' : ''}" role="radio" aria-checked="${d.type === t}" data-action="type-pick" data-v="${t}" style="--ta:${x.accent}"><span class="te" aria-hidden="true">${icon(x.icon)}</span><b>${x.label}</b><span class="small muted">${x.hint}</span>${d.type === t ? `<span class="tick">${icon('check')}</span>` : ''}</button>`; }).join('')}</div>
        <label class="field"><span>Name it</span><input data-bind="title" value="${esc(d.title)}" placeholder="${esc(tp.ph)}" maxlength="48" autocomplete="off" enterkeyhint="next"></label>
        ${d.type === 'intl_trip' ? `<label class="field"><span>Destination country</span><select data-bind="country"><option value="">Choose a country…</option>${P.COUNTRIES.map((x) => `<option value="${esc(x[0])}" ${d.country === x[0] ? 'selected' : ''}>${x[1]} ${esc(x[0])}</option>`).join('')}</select><small>We'll set the time zone for you.</small></label>` : ''}
        ${d.type === 'birthday' ? `<div class="field-row"><label class="field"><span>Whose birthday?</span><input data-bind="personName" value="${esc(d.personName)}" placeholder="Mia" maxlength="30" autocomplete="off"></label><label class="field narrow"><span>Birth year</span><input data-bind="birthYear" value="${esc(d.birthYear)}" inputmode="numeric" placeholder="1996" maxlength="4"></label></div>` : ''}`;
    }
    if (step === 1) {
      const n = whenNote();
      const tzs = TZS.includes(d.tz) ? TZS : [d.tz, ...TZS];
      return `<div class="field-row"><label class="field"><span>${icon('clock')} Date</span><input type="date" data-bind="date" value="${d.date}" ${ui.flow === 'new' && d.recurrence !== 'yearly' ? `min="${todayStr()}"` : ''}></label>
        ${d.allDay ? '' : `<label class="field narrow"><span>Time</span><input type="time" data-bind="time" value="${d.time}"></label>`}</div>
        <div class="panel flush"><div class="set-row"><div><span class="set-t">All day</span><span class="muted small block">Counts down to midnight</span></div>${sw('allDay', d.allDay, 'All day', 'data-draft="1"')}</div>
        <div class="set-row"><div><span class="set-t">${icon('repeat')} Repeat every year</span><span class="muted small block">Birthdays &amp; anniversaries roll over</span></div>${sw('recur', d.recurrence === 'yearly', 'Repeat every year', 'data-draft="1"')}</div></div>
        <label class="field"><span>${icon('globe')} Time zone</span><select data-bind="tz">${tzs.map((z) => `<option value="${esc(z)}" ${z === d.tz ? 'selected' : ''}>${esc(z === P.localTz() ? 'My time zone — ' + z.replace(/_/g, ' ') : z.replace(/_/g, ' '))}</option>`).join('')}</select><small>Pick where the moment happens. Pawcount counts to it exactly, even across daylight-saving changes.</small></label>
        <p id="when-note" class="when-note ${n.ok ? '' : 'bad'}" role="status">${n.text}</p>`;
    }
    if (step === 2) {
      const mut = d.breed === 'mutt';
      return `<div class="dog-preview" id="dog-preview">${previewScene()}</div>
        <div class="mood-row" role="radiogroup" aria-label="Preview mood">${['nap', 'curious', 'waiting', 'packing', 'zoomies', 'today'].map((s) => `<button class="mood ${ui.preview === s ? 'on' : ''}" role="radio" aria-checked="${ui.preview === s}" data-action="mood" data-v="${s}">${icon(P.STAGES[s].icon)}${P.STAGES[s].label.replace("It's today!", 'Party')}</button>`).join('')}</div>
        <div class="breed-scroll" role="radiogroup" aria-label="Dog breed">${P.BREED_ORDER.map((b) => { const B = P.BREEDS[b], sug = P.BREED_BEST[b].includes(d.type); return `<button class="breed ${d.breed === b ? 'on' : ''}" role="radio" aria-checked="${d.breed === b}" data-action="breed-pick" data-v="${b}"><span class="b-ic">${P.dogIconSVG(b)}</span><b>${esc(B.name)}</b><span class="small">${esc(B.label)}</span><span class="small muted">${esc(B.vibe)}</span>${sug ? '<span class="sug">Great match</span>' : ''}</button>`; }).join('')}</div>
        <label class="field"><span>Dog's name</span><input data-bind="dogName" value="${esc(d.dogName)}" maxlength="18" autocomplete="off"></label>
        ${mut ? `<div class="panel"><h2 class="panel-h">Customize ${esc(d.dogName || 'Lucky')}</h2><p class="muted small">Coat colour</p><div class="swatches" role="radiogroup" aria-label="Coat colour">${P.MUTT_FURS.map((f) => `<button class="swatch ${d.fur === f ? 'on' : ''}" role="radio" aria-checked="${d.fur === f}" style="--s:${f}" data-action="fur-pick" data-v="${f}" aria-label="Coat ${f}">${d.fur === f ? icon('check') : ''}</button>`).join('')}</div><p class="muted small">Ears</p>${seg('ear-pick', P.EAR_TYPES, d.ears, 'aria-label="Ear style"')}</div>` : ''}`;
    }
    if (step === 3) {
      const { c, k } = draftComp();
      return `<div class="preview-card" style="--accent:${c.accent};--on:${P.onColor(c.accent)}">${P.sceneHTML(c, k, { size: 'card', stage: 'waiting' })}<div class="card-body"><h3>${esc(c.title)}</h3><p class="card-count">${esc(shortCount(c, k))}</p></div></div>
        <div class="panel"><h2 class="panel-h">Accent colour</h2><div class="swatches" role="radiogroup" aria-label="Accent colour">${P.ACCENTS.map((a) => `<button class="swatch ${d.accent === a.v ? 'on' : ''}" role="radio" aria-checked="${d.accent === a.v}" style="--s:${a.v};--sc:${P.onColor(a.v)}" data-action="accent-pick" data-v="${a.v}" aria-label="${a.n}">${d.accent === a.v ? icon('check') : ''}</button>`).join('')}</div></div>
        <div class="panel"><h2 class="panel-h">Show the countdown as</h2>${seg('draft-mode', MODES, d.displayMode, 'aria-label="Display mode"')}</div>
        <div class="panel"><h2 class="panel-h">${icon('bell')} Reminders</h2><ul class="rows">${d.notifications.map((n, i) => `<li><span>${esc(n.label)}</span>${sw('dn' + i, n.enabled, n.label, `data-i="${i}" data-draft-n="1"`)}</li>`).join('')}</ul></div>`;
    }
    const { c, k } = draftComp('today');
    const B = P.BREEDS[c.dog.breed];
    return `<div class="done-scene">${P.sceneHTML(c, k, { size: 'hero', stage: 'today' })}</div>
      <div class="done-copy"><h1>${esc(c.dog.name)} is already excited!</h1><p class="muted">${ui.flow === 'edit' ? 'Looking good. Save your changes and tail-wags will update.' : `${esc(c.title)} is ready. ${esc(c.dog.name)} the ${esc(B.label)} will nap, wait, pack and finally lose their mind as the day gets closer.`}</p></div>`;
  }
  function previewScene() {
    const { c, k } = draftComp(ui.preview);
    return P.sceneHTML(c, k, { size: 'preview', stage: ui.preview });
  }

  /* ---------- flow state mutations ---------- */
  function setDraft(key, val) {
    const d = ui.draft;
    if (key === 'title') { d.title = val; d.titleAuto = false; }
    else if (key === 'country') {
      d.country = val; const x = P.COUNTRIES.find((c) => c[0] === val);
      if (x) { d.tz = x[2]; if (d.titleAuto || !d.title.trim()) { d.title = `${x[0]} ${x[1]}`; d.titleAuto = true; } }
    } else if (key === 'personName') {
      d.personName = val; if ((d.titleAuto || !d.title.trim()) && d.type === 'birthday') { d.title = val.trim() ? `${val.trim()}'s birthday` : ''; d.titleAuto = true; }
    } else if (key === 'dogName') { d.dogName = val; d.nameTouched = true; }
    else if (key === 'birthYear') d.birthYear = val.replace(/\D/g, '').slice(0, 4);
    else d[key] = val;
  }
  function flowLight() {
    const btn = $('#flowNext'); if (btn) btn.disabled = !stepValid();
    const wn = $('#when-note'); if (wn) { const n = whenNote(); wn.innerHTML = n.text; wn.classList.toggle('bad', !n.ok); }
  }
  function rerenderFlowBody(keepFocus) {
    const body = $('#flow-body'); if (!body) return;
    const y = view.scrollTop;
    body.innerHTML = stepBody();
    const flow = $('.flow'); if (flow) { const a = ui.draft.accent || P.TYPES[ui.draft.type].accent; flow.style.setProperty('--accent', a); flow.style.setProperty('--on', P.onColor(a)); flow.style.setProperty('--accent-soft', `color-mix(in srgb, ${a} 18%, var(--surface))`); }
    view.scrollTop = y; flowLight(); centerBreed();
  }
  function centerBreed() {
    const on = $('.breed.on'), sc = $('.breed-scroll');
    if (on && sc) sc.scrollLeft = on.offsetLeft - (sc.clientWidth - on.clientWidth) / 2;
  }
  function goStep(n) {
    ui.step = n; render({ noAnim: true });
    view.scrollTop = 0; centerBreed();
    if (n === 4) { setTimeout(() => { P.confetti({ y: appEl.clientHeight * 0.3, count: 110 }); P.haptic('success'); P.sound('chime'); }, 250); }
  }
  function saveDraft() {
    const c = draftToCountdown(ui.draft);
    const ok = S.upsert(c);
    if (!ok) toast('Storage is full — couldn’t save.');
    S.state.onboarded = true; S.save();
    ui.draft = null;
    location.hash = '#/c/' + c.id;
  }

  /* =============================== ROUTER =============================== */
  function route() {
    const seg = (location.hash.replace(/^#/, '') || '/').split('/').filter(Boolean);
    return { name: seg[0] || 'home', id: seg[1] };
  }
  let prevRoute = '';
  function render(opts = {}) {
    const r = route();
    if (!S.state.onboarded && !S.state.countdowns.length && r.name !== 'welcome' && r.name !== 'new') { location.replace('#/welcome'); return; }
    const y = opts.keepScroll ? view.scrollTop : 0;
    let html = '', tab = '', title = 'Pawcount';
    ui.onFetch = null; ui.sceneApi = null;
    switch (r.name) {
      case 'import': {
        const id = importShared(location.hash.slice('#/import/'.length));
        toast(id ? 'Added to your countdowns 🐾' : 'That link isn’t a Pawcount countdown');
        location.replace(id ? '#/c/' + id : '#/'); return;
      }
      case 'welcome': html = viewWelcome(); title = 'Welcome'; break;
      case 'memories': html = viewMemories(); tab = 'memories'; title = 'Memories'; break;
      case 'settings': html = viewSettings(); title = 'Settings'; break;
      case 'c': html = viewDetail(r.id); { const c = S.get(r.id); title = c ? c.title : 'Countdown'; } break;
      case 'new':
        if (!ui.draft || ui.flow !== 'new') { ui.draft = newDraft(S.state.onboarded ? 'intl_trip' : 'intl_trip', ui.welcomeBreedChosen); ui.flow = 'new'; ui.step = 0; }
        html = viewFlow(); title = 'New countdown'; break;
      case 'edit': {
        const c = S.get(r.id);
        if (!c) { location.replace('#/'); return; }
        if (!ui.draft || ui.flow !== 'edit' || ui.draft.id !== c.id) { ui.draft = draftFromCountdown(c); ui.flow = 'edit'; ui.step = 0; }
        html = viewFlow(); title = 'Edit ' + c.title; break;
      }
      default: html = viewHome(); tab = 'home';
    }
    if (r.name !== 'new' && r.name !== 'edit' && ui.draft && r.name !== 'welcome') { ui.draft = null; }
    document.title = (title === 'Pawcount' ? '' : title + ' · ') + 'Pawcount';
    view.innerHTML = html;
    view.scrollTop = y;
    view.className = 'view' + (opts.noAnim || sameRoute(r) ? '' : ' enter');
    prevRoute = r.name + '/' + (r.id || '');
    tabbar.classList.toggle('hidden', !tab);
    $$('.tab', tabbar).forEach((t) => { const on = t.dataset.tab === tab; t.classList.toggle('on', on); t.toggleAttribute('aria-current', on); if (on) t.setAttribute('aria-current', 'page'); });
    appEl.dataset.route = r.name;
    if (!opts.noAnim && !sameRoute(r)) { view.focus({ preventScroll: true }); announce(title); }
    // hooks
    const scene = $('.scene[data-cid][data-stage] .dog-hit', view);
    if (scene) bindScene(view);
    if (r.name === 'c' || r.name === 'home') ui.onFetch = () => fetchBall();
  }
  function sameRoute(r) { return prevRoute === r.name + '/' + (r.id || '') && view.classList.contains('view'); }

  /* =============================== SCENE INTERACTIONS =============================== */
  function bindScene(root) {
    const scene = $('.scene[data-cid]', root); if (!scene) return;
    const c = S.get(scene.dataset.cid) || (ui.demo && ui.demo.id === scene.dataset.cid ? ui.demo : null); if (!c) return;
    const hit = $('[data-dog]', scene), bubble = $('.bubble', scene), fx2 = $('.scene-fx2', scene), hint = $('.scene-hint', root);
    let hold, loop, petting = false, pt = null, sayT, wakeT, napHTML = null;
    const svgEl = () => $('.dog', scene);
    // a sleeping dog opens his eyes for a few seconds when you play with him
    const wake = () => {
      if (K().stage !== 'nap') return;
      if (napHTML == null) napHTML = hit.innerHTML;
      hit.innerHTML = P.dogSVG(c.dog, 'curious', { type: c.type, accent: P.accentOf ? P.accentOf(c) : c.accent, label: c.dog.name });
      clearTimeout(wakeT);
      wakeT = setTimeout(() => { if (napHTML != null) { hit.innerHTML = napHTML; napHTML = null; } }, 6000);
    };
    const K = () => P.compute(c);
    const say = (txt, ms = 2400) => { bubble.textContent = txt; bubble.classList.add('show'); clearTimeout(sayT); sayT = setTimeout(() => bubble.classList.remove('show'), ms); };
    const used = () => hint && hint.classList.add('gone');
    const flash = (cls, ms) => { wake(); const el = svgEl(); el.classList.add(cls); setTimeout(() => el.classList.remove(cls), ms); };

    const bark = () => { used(); flash('bark', 520); say(P.dogLine(c, K(), 'tap')); P.haptic('tick'); P.sound('bark'); };
    const startPet = () => {
      petting = true; used(); wake(); svgEl().classList.add('petting'); say(P.dogLine(c, K(), 'pet'), 3000);
      let n = 0;
      loop = setInterval(() => { P.hearts(fx2, 1, pt && pt.x, pt && pt.y); if (n++ % 3 === 0) P.haptic('purr'); }, 280);
      P.hearts(fx2, 2, pt && pt.x, pt && pt.y);
    };
    const endPet = () => { clearTimeout(hold); clearInterval(loop); if (petting) { petting = false; const el = svgEl(); if (el) el.classList.remove('petting'); } };
    hit.addEventListener('pointerdown', (e) => {
      if (e.button > 0) return; pt = { x: e.clientX, y: e.clientY }; petting = false;
      initMotion(true);
      hold = setTimeout(startPet, 380);
    });
    hit.addEventListener('pointermove', (e) => { pt = { x: e.clientX, y: e.clientY }; });
    const up = () => { const was = petting; const pending = hold != null; clearTimeout(hold); hold = null; if (was) endPet(); };
    hit.addEventListener('pointerup', (e) => { const wasPetting = petting; up(); if (!wasPetting && e.pointerType !== undefined) bark(); });
    ['pointerleave', 'pointercancel'].forEach((ev) => hit.addEventListener(ev, up));
    hit.addEventListener('click', (e) => { if (e.detail === 0) bark(); });
    hit.addEventListener('contextmenu', (e) => e.preventDefault());

    const shake = () => { used(); flash('sneeze', 1000); scene.classList.add('shaken'); setTimeout(() => scene.classList.remove('shaken'), 900); say(P.dogLine(c, K(), 'sneeze'), 2600); P.haptic('success'); P.sound('bark'); };
    const fetchB = () => {
      used(); scene.classList.add('fetching');
      const ball = document.createElement('i'); ball.className = 'fetch-ball'; fx2.appendChild(ball);
      setTimeout(() => { say(P.dogLine(c, K(), 'fetch')); P.haptic('soft'); }, 900);
      setTimeout(() => { scene.classList.remove('fetching'); ball.remove(); }, 1900);
    };
    /* ---- play: treat / feed / ball / tickle, and a joy meter that fades while you are away ---- */
    const joyNow = () => { const j = c.joy; return j ? Math.max(0, j.v - ((Date.now() - j.t) / 3600000) * 6) : 20; };
    const joyLabel = (v) => (v < 25 ? 'Sleepy' : v < 55 ? 'Content' : v < 85 ? 'Happy' : 'Over the moon');
    const showJoy = () => { const v = Math.round(joyNow()), f = $('[data-joy-fill]', root), t = $('[data-joy-text]', root); if (f) f.style.width = v + '%'; if (t) t.textContent = `${c.dog.name} is ${joyLabel(v).toLowerCase()}`; };
    const addJoy = (n) => {
      const before = joyNow(); c.joy = { v: Math.min(100, before + n), t: Date.now() }; S.save(); showJoy();
      if (before < 100 && c.joy.v >= 100) { P.confetti({ count: 70, power: 0.8 }); P.haptic('success'); say(P.dogLine(c, K(), 'love'), 3000); }
    };
    const drop = (cls, emoji, ms) => { const el = document.createElement('i'); el.className = cls; el.innerHTML = icon(emoji); fx2.appendChild(el); setTimeout(() => el.remove(), ms); return el; };
    /* hunger and thirst: they run down while you are away (food ~5 points an hour, water ~8) and Feed / Water fill them */
    const needNow = () => { const n = c.needs || { food: 70, water: 70, t: Date.now() }, h = (Date.now() - n.t) / 3600000; return { food: Math.max(0, n.food - h * 5), water: Math.max(0, n.water - h * 8) }; };
    const showNeeds = () => { const n = needNow(); ['food', 'water'].forEach((k) => { const f = $(`[data-need-fill="${k}"]`, root), w = $(`[data-need="${k}"]`, root); if (f) { f.style.width = Math.round(n[k]) + '%'; w.classList.toggle('low', n[k] < 30); } }); };
    const fill = (food, water) => { const n = needNow(); c.needs = { food: Math.min(100, n.food + food), water: Math.min(100, n.water + water), t: Date.now() }; S.save(); showNeeds(); };
    const nudge = () => { const n = needNow(); if (n.water < 30) say(P.dogLine(c, K(), 'thirsty'), 3200); else if (n.food < 30) say(P.dogLine(c, K(), 'hungry'), 3200); };
    const play = {
      drink() { used(); drop('bowl', 'drop', 2400); flash('munch', 2000); say(P.dogLine(c, K(), 'drink'), 2400); P.haptic('purr'); fill(0, 60); addJoy(6); },
      treat() { used(); drop('treat-drop', 'bone', 1000); setTimeout(() => { fill(8, 0); flash('munch', 1100); P.hearts(fx2, 2); say(P.dogLine(c, K(), 'treat')); P.haptic('tick'); P.sound('chime'); addJoy(8); }, 750); },
      feed() { used(); const b = drop('bowl', 'bowl', 2600); flash('munch', 2300); say(P.dogLine(c, K(), 'feed'), 2800); P.haptic('purr'); fill(55, 0); addJoy(10); },
      ball() { fetchB(); addJoy(6); },
      tickle() { used(); flash('tickle', 1600); P.hearts(fx2, 5); say(P.dogLine(c, K(), 'tickle'), 2400); P.haptic('purr'); addJoy(8); },
    };
    const k0 = (a) => a[Math.floor(Math.random() * a.length)];
    $$('[data-play]', root).forEach((b) => b.addEventListener('click', () => play[b.dataset.play]()));
    showJoy(); showNeeds();
    if (root === view && route().name === 'c') setTimeout(nudge, 2600);
    ui.sceneApi = { bark, shake, fetch: fetchB, play, pet: () => { pt = null; startPet(); setTimeout(endPet, 2200); } };
    initMotion(false);
    // gentle first hello
    if (root === view && route().name === 'c') {
      setTimeout(() => { if (!ui.sceneApi || ui.sceneApi.bark !== bark) return; const k = K(); say(k.stage === 'nap' ? 'Zzz…' : k.stage === 'today' ? "IT'S TODAY!!! 🎉" : k.stage === 'zoomies' ? 'ZOOM!' : k.stage === 'memory' ? 'Remember this? 💛' : 'Woof!', 1800); }, 600);
      checkMilestones();
    }
  }
  function fetchBall() {
    const r = route();
    if (r.name === 'c' && ui.sceneApi) { ui.sceneApi.fetch(); return; }
    const scene = $('.hero-card .scene');
    if (scene) { scene.classList.add('fetching'); const ball = document.createElement('i'); ball.className = 'fetch-ball'; $('.scene-fx2', scene).appendChild(ball); setTimeout(() => { scene.classList.remove('fetching'); ball.remove(); }, 1900); P.haptic('soft'); }
    render({ keepScroll: true, noAnim: true });
    const sc = $('.hero-card .scene'); if (sc) { sc.classList.add('fetching'); const b = document.createElement('i'); b.className = 'fetch-ball'; $('.scene-fx2', sc).appendChild(b); setTimeout(() => { sc.classList.remove('fetching'); b.remove(); }, 1900); }
  }

  /* Shake detection.
     We compare each accelerometer sample with the previous one; a real shake is a big, sudden change
     (a phone resting on a table, or being carried, changes slowly). iOS only hands out motion data
     after the user grants permission from inside a tap, so on iOS this is started from the first
     press on the dog; everywhere else it starts as soon as the scene is shown. */
  let motionStarted = false, lastShake = 0, prev = null;
  const SHAKE_JERK = 18; // m/s^2 change between two samples
  function onMotion(e) {
    const a = e.accelerationIncludingGravity;
    if (!a || !ui.sceneApi) return;
    const cur = [a.x || 0, a.y || 0, a.z || 0];
    if (prev) {
      const jerk = Math.hypot(cur[0] - prev[0], cur[1] - prev[1], cur[2] - prev[2]);
      if (jerk > SHAKE_JERK && Date.now() - lastShake > 1500) { lastShake = Date.now(); ui.sceneApi.shake(); }
    }
    prev = cur;
  }
  function initMotion(fromGesture) {
    if (motionStarted || typeof DeviceMotionEvent === 'undefined') return;
    const needsPermission = typeof DeviceMotionEvent.requestPermission === 'function';
    if (needsPermission && !fromGesture) return;
    motionStarted = true;
    if (!needsPermission) { window.addEventListener('devicemotion', onMotion); return; }
    DeviceMotionEvent.requestPermission()
      .then((state) => { if (state === 'granted') window.addEventListener('devicemotion', onMotion); else motionStarted = false; })
      .catch(() => { motionStarted = false; });
  }

  /* ---------- pull to refresh (touch + mouse) ---------- */
  (function pull() {
    const el = $('#pull'); let y0 = null, dy = 0, active = false;
    const set = (v) => { el.style.setProperty('--pull', v); el.classList.toggle('ready', v >= 62); el.classList.toggle('show', v > 6); };
    const ok = (t) => !t.closest('a,button,input,textarea,select,[data-nodrag],.sheet') || t.closest('.hero-card');
    const start = (y, t) => { if (!ui.onFetch || view.scrollTop > 0 || !ok(t) || sheetRoot.classList.contains('open')) return; y0 = y; dy = 0; active = false; };
    const move = (y, e) => { if (y0 == null) return; dy = y - y0; if (dy > 8 && view.scrollTop <= 0) { active = true; set(Math.min(dy * 0.5, 96)); if (e && e.cancelable) e.preventDefault(); } else if (dy < 0) { y0 = null; set(0); } };
    const end = () => { if (y0 == null) return; const fire = active && dy * 0.5 >= 62; y0 = null; active = false; set(0); if (fire && ui.onFetch) { el.classList.add('fetch'); setTimeout(() => el.classList.remove('fetch'), 900); ui.onFetch(); } };
    view.addEventListener('touchstart', (e) => start(e.touches[0].clientY, e.target), { passive: true });
    view.addEventListener('touchmove', (e) => move(e.touches[0].clientY, e), { passive: false });
    view.addEventListener('touchend', end); view.addEventListener('touchcancel', end);
    view.addEventListener('mousedown', (e) => { if (e.button === 0) start(e.clientY, e.target); });
    window.addEventListener('mousemove', (e) => { if (y0 != null && e.buttons) move(e.clientY, e); });
    window.addEventListener('mouseup', end);
  })();

  /* =============================== MILESTONES & TICK =============================== */
  function checkMilestones() {
    const fresh = [];
    S.active().forEach((c) => {
      const k = P.compute(c);
      if (k.phase !== 'upcoming') return;
      if (P.MILESTONES.includes(k.daysCeil)) {
        const key = `${c.id}:${k.occYear}:${k.daysCeil}`;
        if (!S.state.celebrated[key]) { S.state.celebrated[key] = 1; fresh.push({ c, k }); }
      }
    });
    if (!fresh.length) return;
    S.save();
    const f = fresh[0], r = route();
    const msg = f.k.daysCeil === 1 ? `⚡ Zoomies activated! ${f.c.title} is tomorrow.` : `🐕 ${f.c.dog.name} just counted: ${f.k.daysCeil} days until ${f.c.title.replace(/\s*[\p{Extended_Pictographic}\u{1F1E6}-\u{1F1FF}️]+\s*/gu, ' ').trim()}!`;
    if (r.name === 'c' && r.id === f.c.id) { setTimeout(() => P.confetti({ count: 80 }), 700); }
    setTimeout(() => { toast(msg, 4200); P.haptic('success'); P.sound('chime'); }, r.name === 'c' ? 900 : 500);
    notifyUser(f.c.dog.name + ' · ' + f.c.title, msg);
  }
  function notifyUser(title, body) {
    try {
      const day = new Date().toDateString();
      if (S.state.settings.notifications && !P.native.isNative && P.native.notifyState() === 'granted' && S.state.lastNotified !== day) {
        P.notify(title, body); S.state.lastNotified = day; S.save();
      }
    } catch (e) { /* ignore */ }
  }

  let tickN = 0;
  function tick() {
    tickN++;
    let stale = false;
    $$('.numerals[data-cid]', view).forEach((el) => { if (updateNumerals(el)) stale = true; });
    if (tickN % 10 === 0) {
      $$('.scene[data-cid][data-fixed="0"]', view).forEach((sc) => {
        const c = S.get(sc.dataset.cid); if (!c) return;
        if (P.compute(c).stage !== sc.dataset.stage) stale = true;
      });
      $$('[data-short]', view).forEach((el) => { const c = S.get(el.dataset.short); if (c) el.textContent = shortCount(c, P.compute(c)); });
    }
    if (stale && !['new', 'edit', 'welcome', 'settings'].includes(route().name)) {
      render({ keepScroll: true, noAnim: true });
      const r = route();
      if (r.name === 'c') { P.confetti({ count: 120 }); P.haptic('success'); }
    }
    if (tickN % 3600 === 0) checkMilestones();
  }

  /* =============================== EVENTS =============================== */
  const actions = {
    back() { if (ui.stack.length > 1) history.back(); else location.hash = '#/'; },
    sort(el) { ui.sort = el.dataset.v; render({ keepScroll: true, noAnim: true }); },
    samples() { S.loadSamples(); toast('Seven sample adventures added 🐾'); location.hash = '#/'; render({ noAnim: false }); },
    'clear-samples'() { S.clearSamples(); toast('Samples removed'); render({ keepScroll: true, noAnim: true }); },
    menu() {
      const c = S.get(route().id); if (!c) return;
      openSheet(`<h2 class="sheet-t">${esc(c.title)}</h2><div class="sheet-list">
        <a class="sheet-item" href="#/edit/${c.id}" data-action="close-sheet">${icon('edit')}<span>Edit countdown</span></a>
        <button class="sheet-item" data-action="share">${icon('upload')}<span>Share with your person</span></button>
        <button class="sheet-item" data-action="${c.archived ? 'unarchive' : 'archive'}">${icon('archive')}<span>${c.archived ? 'Move back to Home' : 'Archive to Memories'}</span></button>
        <button class="sheet-item danger" data-action="delete-ask">${icon('trash')}<span>Delete…</span></button></div>`, 'Countdown options');
    },
    share() {
      const c = S.get(route().id); if (!c) return;
      const url = shareLink(c), text = `${c.dog.name} is counting down to ${c.title}!`;
      closeSheet();
      if (navigator.share) navigator.share({ title: 'Pawcount', text, url }).catch(() => {});
      else if (navigator.clipboard) navigator.clipboard.writeText(url).then(() => toast('Link copied — send it to your person 💛'), () => prompt('Copy this link', url));
      else prompt('Copy this link', url);
    },
    archive() { const c = S.get(route().id); c.archived = true; S.commit(); closeSheet(); toast('Archived to Memories'); location.hash = '#/memories'; },
    unarchive() { const c = S.get(route().id); c.archived = false; S.commit(); closeSheet(); toast('Back on Home'); render({ keepScroll: true, noAnim: true }); },
    'delete-ask'() {
      const c = S.get(route().id);
      openSheet(`<h2 class="sheet-t">Delete “${esc(c.title)}”?</h2><p class="muted center-t">${esc(c.dog.name)} will be very confused. This can’t be undone.</p><div class="stack"><button class="btn btn-danger" data-action="delete-yes">Delete forever</button><button class="btn btn-ghost" data-action="close-sheet">Keep it</button></div>`, 'Confirm delete');
    },
    'delete-yes'() { const id = route().id; closeSheet(); S.remove(id); toast('Deleted'); location.hash = '#/'; },
    'close-sheet': closeSheet,
    async install() { const e = ui.installEvt; if (!e) return; e.prompt(); const r = await e.userChoice; ui.installEvt = null; if (r.outcome === 'accepted') toast('Installed! Find Pawcount on your Home Screen'); render({ keepScroll: true, noAnim: true }); },
    'detail-mode'(el) { const c = S.get(route().id); c.displayMode = el.dataset.v; S.commit(); render({ keepScroll: true, noAnim: true }); },
    check(el) { const c = S.get(route().id), it = c.checklist[+el.dataset.i]; it.done = !it.done; S.commit(); P.haptic('tick'); render({ keepScroll: true, noAnim: true }); },
    'del-check'(el) { const c = S.get(route().id); c.checklist.splice(+el.dataset.i, 1); S.commit(); render({ keepScroll: true, noAnim: true }); },
    toggle(el) {
      const k = el.dataset.k;
      if (el.dataset.nt) { const c = S.get(route().id), n = c.notifications[+el.dataset.i]; n.enabled = !n.enabled; S.commit(); el.classList.toggle('on', n.enabled); el.setAttribute('aria-checked', n.enabled); P.haptic('tick'); return; }
      if (el.dataset.draftN) { const n = ui.draft.notifications[+el.dataset.i]; n.enabled = !n.enabled; el.classList.toggle('on', n.enabled); el.setAttribute('aria-checked', n.enabled); return; }
      if (el.dataset.draft) {
        const d = ui.draft;
        if (k === 'allDay') d.allDay = !d.allDay; else if (k === 'recur') d.recurrence = d.recurrence === 'yearly' ? 'none' : 'yearly';
        rerenderFlowBody(); return;
      }
      if (k === 'notifications') { enableNotifications(); return; }
      const v = !S.state.settings[k]; S.set(k, v); el.classList.toggle('on', v); el.setAttribute('aria-checked', v);
      if (k === 'haptics' && v) P.haptic('purr'); if (k === 'sound' && v) P.sound('bark', true);
    },
    'set-default-mode'(el) { S.set('displayMode', el.dataset.v); render({ keepScroll: true, noAnim: true }); },
    'set-parent'(el) { S.set('parentTitle', el.dataset.v === 'parent' ? 'parent' : el.dataset.v); render({ keepScroll: true, noAnim: true }); },
    'set-theme'(el) { S.set('theme', el.dataset.v); applySettings(); render({ keepScroll: true, noAnim: true }); },
    'set-motion'(el) { S.set('motion', el.dataset.v); applySettings(); render({ keepScroll: true, noAnim: true }); },
    'set-icon'(el) { S.set('icon', el.dataset.v); applySettings(); render({ keepScroll: true, noAnim: true }); toast(`${P.BREEDS[el.dataset.v].name} is your new icon (shown in the browser tab here)`); },
    export() {
      const blob = new Blob([S.exportJSON()], { type: 'application/json' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'pawcount-backup.json'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
      toast('Backup downloaded');
    },
    import() { $('[data-import-input]').click(); },
    reset() {
      openSheet(`<h2 class="sheet-t">Erase everything?</h2><p class="muted center-t">All countdowns, memories and settings on this device will be removed.</p><div class="stack"><button class="btn btn-danger" data-action="reset-yes">Erase everything</button><button class="btn btn-ghost" data-action="close-sheet">Cancel</button></div>`, 'Confirm erase');
    },
    'reset-yes'() { closeSheet(); S.reset(); applySettings(); ui.draft = null; ui.welcome = 0; location.hash = '#/welcome'; render(); },
    'profile-photo'() { $('[data-profile-input]').click(); },
    'profile-photo-clear'() { S.set('profilePhoto', ''); render({ keepScroll: true, noAnim: true }); },
    'photo-pick'(el) { $(`[data-photo-input][data-id="${el.dataset.id}"]`).click(); },
    'photo-del'(el) { const c = S.get(route().id); c.memoryPhotos.splice(+el.dataset.i, 1); S.commit(); render({ keepScroll: true, noAnim: true }); },

    /* welcome */
    'w-next'() { if (ui.welcome === 1) ui.welcomeBreedChosen = ui.welcomeBreed; ui.welcome++; render({ noAnim: true }); view.classList.add('enter'); },
    'w-breed'(el) { ui.welcomeBreed = el.dataset.v; ui.welcomeBreedChosen = el.dataset.v; render({ noAnim: true }); P.haptic('tick'); },
    'w-skip'() { ui.welcome = 2; render({ noAnim: true }); },
    async 'w-bell'() { P.sound('jingle'); await enableNotifications(true); finishWelcome(); },
    'w-later'() { finishWelcome(); },

    /* flow */
    'flow-back'() { if (ui.step === 0) { const was = ui.draft; ui.draft = null; if (ui.flow === 'edit' && was && was.id) location.hash = '#/c/' + was.id; else location.hash = '#/'; } else goStep(ui.step - 1); },
    'flow-next'() { if (!stepValid()) return; if (ui.step === 4) saveDraft(); else goStep(ui.step + 1); },
    'type-pick'(el) { applyType(ui.draft, el.dataset.v); if (ui.draft.titleAuto) ui.draft.title = ''; P.haptic('tick'); rerenderFlowBody(); },
    'breed-pick'(el) {
      const d = ui.draft; d.breed = el.dataset.v; d.breedTouched = true; if (!d.nameTouched) d.dogName = P.BREEDS[d.breed].name; P.haptic('tick');
      rerenderFlowBody();
    },
    mood(el) { ui.preview = el.dataset.v; rerenderFlowBody(); const m = $('.mood.on'); if (m) m.focus({ preventScroll: true }); },
    'fur-pick'(el) { ui.draft.fur = el.dataset.v; rerenderFlowBody(); },
    'ear-pick'(el) { ui.draft.ears = el.dataset.v; rerenderFlowBody(); },
    'accent-pick'(el) { ui.draft.accent = el.dataset.v; ui.draft.accentTouched = true; P.haptic('tick'); rerenderFlowBody(); },
    'draft-mode'(el) { ui.draft.displayMode = el.dataset.v; rerenderFlowBody(); },
  };

  async function enableNotifications(fromWelcome) {
    const p = await P.native.requestNotifications();
    if (p === 'unsupported') { if (!fromWelcome) toast(P.native.isIOS && !P.native.standalone ? 'On iPhone, add Pawcount to your Home Screen first, then turn on reminders' : 'Reminders aren’t supported in this browser'); return false; }
    S.set('notifications', p === 'granted');
    if (p === 'granted') {
      if (!fromWelcome) toast('Reminders are on 🔔');
      P.native.reschedule();
      if (!P.native.isNative) P.notify('Pawcount', 'Woof! Reminders are on.');
    } else if (!fromWelcome) toast('Reminders are blocked in your device settings');
    if (route().name === 'settings') render({ keepScroll: true, noAnim: true });
    return p === 'granted';
  }
  function finishWelcome() {
    S.state.onboarded = true; S.save();
    ui.draft = null; ui.flow = 'new';
    location.hash = '#/new';
  }

  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-action]');
    if (!el) return;
    const fn = actions[el.dataset.action];
    if (fn) { if (el.tagName === 'A' && el.dataset.action === 'close-sheet') closeSheet(); else { e.preventDefault(); fn(el, e); } if (el.dataset.action === 'close-sheet' && el.tagName === 'A') { /* let link navigate */ } }
  });

  document.addEventListener('input', (e) => {
    const t = e.target;
    if (t.matches('[data-profile-name]')) { S.state.settings.profileName = t.value.trim().slice(0, 30); S.save(); return; }
    if (t.dataset.bind) {
      setDraft(t.dataset.bind, t.value);
      if (['country'].includes(t.dataset.bind) || t.tagName === 'SELECT') { rerenderFlowBody(); if (t.dataset.bind === 'tz') { /* keep focus */ } }
      else flowLight();
    }
  });
  document.addEventListener('change', (e) => {
    const t = e.target;
    if (t.matches('[data-notes]')) { const c = S.get(route().id); c.notes = t.value; S.commit(); }
    if (t.matches('[data-bind="date"],[data-bind="time"]')) flowLight();
    if (t.matches('[data-photo-input]')) addPhotos(t);
    if (t.matches('[data-profile-input]')) {
      const f = t.files[0]; t.value = ''; if (!f) return;
      shrink(f, 192).then((url) => { if (!url) return toast('Could not read that photo'); if (!S.set('profilePhoto', url)) toast('Storage is full'); render({ keepScroll: true, noAnim: true }); P.haptic('success'); });
    }
    if (t.matches('[data-import-input]')) {
      const f = t.files[0]; if (!f) return;
      f.text().then((txt) => { try { S.importJSON(txt); applySettings(); toast('Backup restored 🐾'); render({ keepScroll: true, noAnim: true }); } catch (err) { toast('That file isn’t a Pawcount backup'); } });
      t.value = '';
    }
  });
  document.addEventListener('submit', (e) => {
    const f = e.target.closest('[data-form="check"]'); if (!f) return;
    e.preventDefault();
    const txt = f.elements.text.value.trim(); if (!txt) return;
    const c = S.get(route().id); c.checklist.push({ id: P.uuid(), text: txt, done: false }); S.commit(); P.haptic('tick');
    render({ keepScroll: true, noAnim: true }); const inp = $('.add-check input'); if (inp) inp.focus({ preventScroll: true });
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSheet();
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target.tagName || '')) || e.target.isContentEditable;
    if (e.key === 'Enter' && typing && e.target.tagName === 'INPUT' && ['new', 'edit'].includes(route().name) && e.target.type !== 'date') { e.preventDefault(); if (stepValid() && ui.step < 4) actions['flow-next'](); }
    if (typing || e.metaKey || e.ctrlKey || e.altKey || !ui.sceneApi) return;
    if (e.key === 's' || e.key === 'S') ui.sceneApi.shake();
    if (e.key === 'p' || e.key === 'P') ui.sceneApi.pet();
    if (e.key === 'f' || e.key === 'F') ui.sceneApi.fetch();
  });
  // notes: save on blur even without change
  document.addEventListener('focusout', (e) => { if (e.target.matches && e.target.matches('[data-notes]')) { const c = S.get(route().id); if (c && c.notes !== e.target.value) { c.notes = e.target.value; S.commit(); } } });

  /* photos: downscale to keep storage small */
  function addPhotos(input) {
    const c = S.get(input.dataset.id); if (!c) return;
    const files = [...input.files].slice(0, 6 - (c.memoryPhotos || []).length);
    Promise.all(files.map(shrink)).then((urls) => {
      c.memoryPhotos = (c.memoryPhotos || []).concat(urls.filter(Boolean));
      if (!S.commit()) toast('Storage is full — try fewer photos');
      render({ keepScroll: true, noAnim: true }); P.haptic('success'); toast('Added to ' + c.title + ' 💛');
    });
    input.value = '';
  }
  function shrink(file, max = 720) {
    return new Promise((res) => {
      const img = new Image(), url = URL.createObjectURL(file);
      img.onload = () => {
        const m = max, s = Math.min(1, m / Math.max(img.width, img.height)), cv = document.createElement('canvas');
        cv.width = Math.round(img.width * s); cv.height = Math.round(img.height * s);
        cv.getContext('2d').drawImage(img, 0, 0, cv.width, cv.height); URL.revokeObjectURL(url); res(cv.toDataURL('image/jpeg', 0.72));
      };
      img.onerror = () => res(null); img.src = url;
    });
  }

  /* =============================== BOOT =============================== */
  S.load(); applySettings();
  window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); ui.installEvt = e; if (route().name === 'settings') render({ keepScroll: true, noAnim: true }); });
  S.subscribe(() => P.native.reschedule()); P.native.reschedule();
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { tick(); P.native.reschedule(); } });
  $('#brandLogo').innerHTML = P.dogIconSVG('scott');
  $('[data-tab="home"] .tab-ic').innerHTML = icon('home');
  $('[data-tab="memories"] .tab-ic').innerHTML = icon('album');
  $('.tab-fab span').innerHTML = icon('plus');
  $('#brandSamples').addEventListener('click', () => { S.loadSamples(); location.hash = '#/'; render(); });
  // Keep our own list of visited screens. Going back to the previous entry pops it, anything else pushes.
  // That lets the on-screen Back button know whether there is somewhere inside the app to go
  // (history.back() from the first screen would leave the app / close the PWA).
  // the home screen lives at the plain address (no trailing "#/")
  const tidyUrl = () => { if (location.hash === '#/' || location.hash === '#') { try { history.replaceState(history.state, '', location.pathname + location.search); } catch (e) { /* ignore */ } } };
  window.addEventListener('hashchange', () => {
    const h = location.hash || '#/', st = ui.stack;
    if (st.length > 1 && st[st.length - 2] === h) st.pop(); else st.push(h);
    closeSheet(); P.confettiClear(); render(); tidyUrl();
  });
  const live = document.createElement('div'); live.id = 'sr-live'; live.className = 'sr-only'; live.setAttribute('aria-live', 'polite'); document.body.appendChild(live);
  render(); tidyUrl();
  setInterval(tick, 1000);
  setTimeout(() => { if (!['welcome', 'new', 'edit'].includes(route().name) && route().name !== 'c') checkMilestones(); }, 1200);
  if ('serviceWorker' in navigator && location.protocol.startsWith('http') && !P.native.isNative) navigator.serviceWorker.register('sw.js').catch(() => {});
  window.Pawcount = { S, P, ui, render, shareLink, importShared };
})();
