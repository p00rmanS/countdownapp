/*
 * countdown.js - the brain of the app. No DOM in here, so it is unit-tested in tests/countdown.test.js.
 *
 * 1. Time zones. A countdown stores the target as a wall-clock string ("2027-03-14T09:30") plus an
 *    IANA zone ("Asia/Tokyo"). wallToInstant() turns that into a real moment in time. We ask Intl what
 *    the zone's UTC offset is at that moment (twice, in case the offset changes at that very hour), which is
 *    what makes daylight-saving changes come out right.
 * 2. compute(c, now) works out everything the UI needs: time left split into d/h/m/s, "sleeps"
 *    (local midnights), progress, which of the 7 dog stages applies, and where yearly repeats roll over.
 * 3. Dog personality text (what it says, how the stage is described for screen readers).
 * 4. Default reminder list and the sample data used for demos.
 *
 * Stage rules (from the spec): nap >75% time left, curious 75-40%, waiting 40-15%, packing <15% or
 * the last 7 days, zoomies the last 24 h, today on the day itself, memory afterwards.
 */
(function () {
  const P = (window.Paw = window.Paw || {});
  const DAY = 86400000, HOUR = 3600000, MIN = 60000;
  P.DAY = DAY; P.HOUR = HOUR; P.MIN = MIN;
  P.MILESTONES = [100, 50, 30, 10, 7, 1];

  /* ---------- time-zone safe math (wall-clock + IANA zone -> instant) ---------- */
  const cache = {};
  function dtf(tz) {
    if (!cache[tz]) cache[tz] = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
    return cache[tz];
  }
  function validTz(tz) { try { dtf(tz); return tz; } catch (e) { return P.localTz(); } }
  function partsIn(ms, tz) {
    const o = {};
    dtf(tz).formatToParts(new Date(ms)).forEach((p) => { o[p.type] = p.value; });
    return { y: +o.year, mo: +o.month, d: +o.day, h: +o.hour % 24, mi: +o.minute, s: +o.second };
  }
  const offset = (ms, tz) => { const p = partsIn(ms, tz); return Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi, p.s) - Math.floor(ms / 1000) * 1000; };
  function parseWall(w) { const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(w); return { y: +m[1], mo: +m[2], d: +m[3], h: +(m[4] || 0), mi: +(m[5] || 0) }; }
  function wallToInstant(w, tz) {
    const p = typeof w === 'string' ? parseWall(w) : w;
    const g = Date.UTC(p.y, p.mo - 1, p.d, p.h || 0, p.mi || 0);
    const o1 = offset(g, tz);
    let t = g - o1;
    const o2 = offset(t, tz);
    if (o2 !== o1) t = g - o2;
    return t;
  }
  const pad = (n, l = 2) => String(n).padStart(l, '0');
  const wallStr = (p) => `${p.y}-${pad(p.mo)}-${pad(p.d)}T${pad(p.h || 0)}:${pad(p.mi || 0)}`;
  const addDays = (p, n) => { const t = new Date(Date.UTC(p.y, p.mo - 1, p.d + n)); return { y: t.getUTCFullYear(), mo: t.getUTCMonth() + 1, d: t.getUTCDate() }; };
  const dim = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();
  P.time = { wallToInstant, partsIn, wallStr, parseWall, validTz, addDays };

  /* ---------- the model ---------- */
  /**
   * Everything the UI needs to know about one countdown at moment `now` (ms since epoch).
   * Yearly repeats: walk forward year by year until we find an occurrence whose day has not finished yet;
   * the previous occurrence's end becomes the start of the progress bar.
   */
  P.compute = function (c, now = Date.now()) {
    const tz = validTz(c.timeZone || P.localTz());
    const base = parseWall(c.targetAt);
    const mk = (p) => wallToInstant(c.allDay ? { ...p, h: 0, mi: 0 } : p, tz);
    const bounds = (p) => {
      const a = wallToInstant({ y: p.y, mo: p.mo, d: p.d, h: 0, mi: 0 }, tz);
      const n = addDays(p, 1);
      return [a, wallToInstant({ ...n, h: 0, mi: 0 }, tz)];
    };
    const occAt = (y) => ({ ...base, y, d: Math.min(base.d, dim(y, base.mo)) });

    let occ = base, prevEnd = null;
    if (c.recurrence === 'yearly') {
      for (let y = base.y, i = 0; i < 400; i++, y++) {
        occ = occAt(y);
        const end = bounds(occ)[1];
        if (end > now) break;
        prevEnd = end;
      }
    }
    const target = mk(occ);
    const [dayStart, dayEnd] = bounds(occ);
    const created = Date.parse(c.createdAt) || now;
    const start = prevEnd ? Math.max(created, prevEnd) : created;
    const total = Math.max(target - start, HOUR);
    const remaining = target - now;
    const leftPct = Math.min(1, Math.max(0, remaining / total));

    const phase = now < dayStart ? 'upcoming' : now < dayEnd ? 'today' : 'past';
    let stage;
    if (phase === 'today') stage = 'today';
    else if (phase === 'past') stage = 'memory';
    else if (remaining <= DAY) stage = 'zoomies';
    else if (remaining <= 7 * DAY || leftPct <= 0.15) stage = 'packing';
    else if (leftPct <= 0.4) stage = 'waiting';
    else if (leftPct <= 0.75) stage = 'curious';
    else stage = 'nap';

    const rem = Math.max(0, remaining);
    const parts = { d: Math.floor(rem / DAY), h: Math.floor((rem % DAY) / HOUR), m: Math.floor((rem % HOUR) / MIN), s: Math.floor((rem % MIN) / 1000) };
    const A = new Date(now), B = new Date(target);
    const sleeps = Math.max(0, Math.round((Date.UTC(B.getFullYear(), B.getMonth(), B.getDate()) - Date.UTC(A.getFullYear(), A.getMonth(), A.getDate())) / DAY));
    const progress = phase === 'upcoming' ? 1 - leftPct : 1;
    const daysTotal = Math.max(1, Math.round(total / DAY));
    const daysIn = Math.min(daysTotal, Math.max(0, Math.floor((now - start) / DAY) + 1));
    const age = c.person && c.person.birthYear ? occ.y - c.person.birthYear : null;
    const together = c.type === 'anniversary' ? Math.max(0, Math.floor((now - wallToInstant({ ...base, h: 0, mi: 0 }, tz)) / DAY)) : null;

    return {
      tz, target, start, total, remaining, leftPct, progress, phase, stage, parts, sleeps,
      daysCeil: Math.ceil(rem / DAY), weeks: Math.floor(parts.d / 7), weekDays: parts.d % 7,
      daysTotal, daysIn, age, together, occYear: occ.y, excitement: Math.round(progress * 100), dayStart, dayEnd,
      daysSince: phase === 'past' ? Math.max(1, Math.floor((now - dayEnd) / DAY) + 1) : 0,
    };
  };

  P.stageKey = (k) => k.stage + ':' + k.phase + ':' + (k.remaining <= 0 ? 'z' : 'n');

  const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
  P.plural = plural;

  P.spoken = (k) => {
    if (k.phase === 'today' && k.remaining <= 0) return "It's today";
    if (k.phase === 'past') return plural(k.daysSince, 'day') + ' ago';
    const { d, h, m } = k.parts;
    if (d >= 1) return d + (d === 1 ? ' day' : ' days') + (h ? ` and ${plural(h, 'hour')}` : '');
    if (h >= 1) return plural(h, 'hour') + (m ? ` and ${plural(m, 'minute')}` : '');
    return plural(Math.max(1, m), 'minute');
  };

  /* number model for the chosen display mode */
  P.units = (k, mode) => {
    const u = (key, v, w, padTo = 1) => ({ k: key, v, label: v === 1 ? w : w + 's', pad: padTo });
    if (mode === 'days') return [u('d', k.daysCeil, 'day')];
    if (mode === 'sleeps') return [u('sl', k.sleeps, 'sleep')];
    if (mode === 'weeks') return [u('w', k.weeks, 'week'), u('wd', k.weekDays, 'day')];
    const p = k.parts;
    return [u('d', p.d, 'day'), { k: 'h', v: p.h, label: 'hrs', pad: 2 }, { k: 'm', v: p.m, label: 'min', pad: 2 }, { k: 's', v: p.s, label: 'sec', pad: 2 }];
  };

  /* ---------- formatting ---------- */
  P.fmtInstant = (ms, tz, o = {}) => {
    const opt = { timeZone: tz, weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' };
    if (o.noYear) delete opt.year;
    if (!o.allDay) { opt.hour = 'numeric'; opt.minute = '2-digit'; }
    return new Intl.DateTimeFormat(undefined, opt).format(new Date(ms));
  };
  P.tzLabel = (tz) => {
    const city = tz.split('/').pop().replace(/_/g, ' ');
    try {
      const n = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
      return `${city} (${n ? n.value : ''})`;
    } catch (e) { return city; }
  };
  P.when = (c, k) => {
    const dest = P.fmtInstant(k.target, k.tz, { allDay: c.allDay });
    const sameTz = k.tz === P.localTz();
    return { dest, destCity: k.tz.split('/').pop().replace(/_/g, ' '), local: sameTz || c.allDay ? null : P.fmtInstant(k.target, P.localTz(), {}), sameTz };
  };

  /* ---------- dog personality ---------- */
  P.STAGES = {
    nap: { label: 'Napping', emoji: '😴', icon: 'moon' },
    curious: { label: 'Curious', emoji: '👀', icon: 'eye' },
    waiting: { label: 'Waiting', emoji: '🐕', icon: 'hourglass' },
    packing: { label: 'Packing', emoji: '🎒', icon: 'suitcase' },
    zoomies: { label: 'Zoomies', emoji: '⚡', icon: 'bolt' },
    today: { label: "It's today!", emoji: '🎉', icon: 'sparkles' },
    memory: { label: 'Memory', emoji: '💛', icon: 'heart' },
  };
  const PACK = {
    intl_trip: ['Packing', 'dragging a suitcase and clutching a passport'],
    vacation: ['Packing', 'wearing sunglasses beside a suitcase'],
    birthday: ['Party prep', 'wearing a party hat beside a present'],
    anniversary: ['Flowers ready', 'holding a bouquet with a flower crown'],
    holiday: ['Wrapping', 'in a holiday hat beside a present'],
    custom: ['Ready', 'ready with a tennis ball'],
  };
  P.stageInfo = (c, k) => {
    const dn = c.dog.name;
    const base = P.STAGES[k.stage];
    const desc = {
      nap: 'curled up asleep, dreaming of the big day',
      curious: 'sitting up with a curious head tilt',
      waiting: 'sitting by the door, tail wagging slowly',
      packing: (PACK[c.type] || PACK.custom)[1],
      zoomies: 'running laps with a tail that is a blur',
      today: 'celebrating with confetti',
      memory: 'lying on a souvenir, remembering',
    }[k.stage];
    return { ...base, label: k.stage === 'packing' ? (PACK[c.type] || PACK.custom)[0] : base.label, desc: `${dn} is ${desc}` };
  };
  P.describeDog = (c, k) => `${P.stageInfo(c, k).desc}. ${k.phase === 'past' ? P.spoken(k) : P.spoken(k) + ' left'}.`;

  const pick = (a) => a[Math.floor(Math.random() * a.length)];
  /** what the dog calls the person: Mama / Papa (their choice in Settings > Profile), otherwise "hooman" */
  P.callName = () => ({ mama: 'Mama', papa: 'Papa' }[(P.store && P.store.state.settings.parentTitle) || ''] || 'hooman');
  P.dogLine = (c, k, kind = 'tap') => {
    const who = P.callName();
    if (kind === 'pet') return pick(['*happy sigh* 💛', `Best ${who}. Ever.`, `Mmm… more pets, ${who}.`, '*leans in*']);
    if (kind === 'hungry') return pick([`${who}, my tummy is rumbling…`, `${who}… is it dinner time?`, 'I would very much like a snack.']);
    if (kind === 'thirsty') return pick([`${who}, I'm so thirsty…`, 'My water bowl is looking empty…', '*pants* water, please?']);
    if (kind === 'drink') return pick(['Glug glug glug!', 'Ahh, so refreshing!', `Thank you, ${who}! 💧`]);
    if (kind === 'full') return pick(['Too full! …okay, one more bite.', `I love you, ${who}.`]);
    if (kind === 'sneeze') return pick(['Ah… ah… ACHOO!', 'Achoo! Who shook the room?!']);
    if (kind === 'fetch') return pick(['Got it! Throw again?', 'Fetched! Good dog? GOOD DOG.']);
    const n = k.sleeps, t = c.title.replace(/\s*[\p{Extended_Pictographic}\u{1F1E6}-\u{1F1FF}️]+\s*/gu, ' ').trim() || 'the big day';
    const sl = n === 1 ? '1 more sleep' : n + ' more sleeps';
    const L = {
      nap: [`Zzz… ${sl}… zzz…`, '*snore* five more minutes…', `Mmf. Wake me for ${t}.`],
      curious: [`Is it ${t} yet? ${sl}!`, `*head tilt* ${sl}?`, `Ooh! ${who}! You came to visit!`],
      waiting: [`I'm by the door! ${sl}!!`, `Woof! ${k.daysCeil} days to go!`, 'Still waiting… tail says hi.'],
      packing: [`Packed! ${sl}!!`, `Did you zip the suitcase? ${sl}!`, 'Almost time!! Almost time!!'],
      zoomies: ['ZOOOOM! Almost here!!', `${k.parts.h + (k.parts.d ? 24 : 0)} hours!! I can't sit still!!`, 'WOOF WOOF WOOF!'],
      today: ["IT'S TODAY!!! 🎉", 'BEST. DAY. EVER.', `WOOF! ${t.toUpperCase()}!`],
      memory: ['That was the best. 💛', `I remember ${t}!`, 'Can we do it again?'],
    };
    return pick(L[k.stage]);
  };

  /* ---------- notifications plan (defaults) ---------- */
  P.defaultNotifications = (type) => [
    { offsetMinutes: 100 * 1440, label: '100 days to go', enabled: true },
    { offsetMinutes: 50 * 1440, label: '50 days to go', enabled: true },
    { offsetMinutes: 30 * 1440, label: '30 days to go', enabled: true },
    { offsetMinutes: 7 * 1440, label: 'One week to go', enabled: true },
    { offsetMinutes: 1440, label: 'Tomorrow (zoomies!)', enabled: true },
    { offsetMinutes: 0, label: 'Morning of', enabled: true },
  ].concat(type === 'intl_trip' ? [{ offsetMinutes: 90 * 1440, label: 'Passport check · 90 days', enabled: true, passport: true }] : []);

  /* ---------- samples ---------- */
  P.suggestedBreed = (type) => ({ intl_trip: 'shiba', vacation: 'golden', birthday: 'corgi', anniversary: 'dachshund', holiday: 'husky', custom: 'mutt' }[type] || 'golden');
  P.BREED_BEST = { scott: [], golden: ['vacation'], corgi: ['birthday'], shiba: ['intl_trip'], dachshund: ['anniversary'], husky: ['holiday'], mutt: ['custom'] };

  P.makeSamples = (now = Date.now()) => {
    const tzL = P.localTz();
    const iso = (ms) => new Date(ms).toISOString();
    const wallIn = (ms, tz, h, mi) => { const p = partsIn(ms, tz); return wallStr({ ...p, h, mi }); };
    const mkc = (o) => ({
      id: 'sample-' + o.key, sample: true, title: o.title, type: o.type, targetAt: o.targetAt, timeZone: o.tz || tzL, allDay: !!o.allDay,
      recurrence: o.rec || 'none', createdAt: iso(now - o.since * DAY),
      dog: o.dog, accent: o.accent || P.TYPES[o.type].accent, displayMode: o.mode || 'full', notes: o.notes || '',
      checklist: o.checklist || [], destination: o.destination, person: o.person, notifications: P.defaultNotifications(o.type), archived: false, memoryPhotos: [],
    });
    const list = [];
    list.push(mkc({ key: 'japan', title: 'Japan 🇯🇵', type: 'intl_trip', tz: 'Asia/Tokyo', targetAt: wallIn(now + 190 * DAY, 'Asia/Tokyo', 9, 30), since: 30, dog: { breed: 'scott', name: 'Scott' }, destination: { country: 'Japan', city: 'Tokyo', flag: '🇯🇵' }, mode: 'full', notes: 'Book the ryokan. Learn how to say "good dog" in Japanese.', checklist: [{ id: 'a', text: 'Renew passport', done: true }, { id: 'b', text: 'JR Pass', done: false }, { id: 'c', text: 'Portable Wi-Fi', done: false }] }));
    const bd = now + 52 * DAY, bp = partsIn(bd, tzL);
    list.push(mkc({ key: 'mia', title: "Mia's birthday", type: 'birthday', targetAt: wallStr({ ...bp, y: bp.y - 30, h: 0, mi: 0 }), allDay: true, rec: 'yearly', since: 40, dog: { breed: 'corgi', name: 'Biscuit' }, person: { name: 'Mia', birthYear: bp.y - 30 }, mode: 'days', notes: 'Gift ideas: pottery class, the blue scarf.' }));
    list.push(mkc({ key: 'bali', title: 'Bali getaway', type: 'vacation', tz: 'Asia/Makassar', targetAt: wallIn(now + 28 * DAY, 'Asia/Makassar', 14, 0), since: 52, dog: { breed: 'scott', name: 'Scott' }, destination: { country: 'Indonesia', city: 'Bali', flag: '🇮🇩' }, mode: 'sleeps', checklist: [{ id: 'a', text: 'Sunscreen', done: true }, { id: 'b', text: 'Reef-safe snorkel gear', done: false }] }));
    const ap = partsIn(now + 5 * DAY, tzL);
    list.push(mkc({ key: 'anniv', title: 'Our anniversary', type: 'anniversary', targetAt: wallStr({ ...ap, y: ap.y - 3, h: 19, mi: 0 }), rec: 'yearly', since: 60, dog: { breed: 'scott', name: 'Scott' }, mode: 'weeks', notes: 'Book the little place with the candles.' }));
    // tomorrow at (current hour - 2): always inside the last 24 h but not "today"
    const hNow = partsIn(now, tzL).h;
    list.push(mkc({ key: 'ski', title: 'Ski weekend', type: 'vacation', targetAt: wallIn(now + DAY, tzL, Math.max(0, hNow - 2), 0), since: 18, dog: { breed: 'husky', name: 'Blizzard' }, accent: '#5DADE8', mode: 'full' }));
    const tp = partsIn(now, tzL);
    list.push(mkc({ key: 'dad', title: "Dad's birthday", type: 'birthday', targetAt: wallStr({ ...tp, y: tp.y - 61, h: 0, mi: 0 }), allDay: true, rec: 'yearly', since: 21, dog: { breed: 'mutt', name: 'Lucky', colors: { fur: '#B98A62' }, ears: 'floppy' }, person: { name: 'Dad', birthYear: tp.y - 61 }, mode: 'full' }));
    list.push(mkc({ key: 'lisbon', title: 'Lisbon 🇵🇹', type: 'intl_trip', tz: 'Europe/Lisbon', targetAt: wallIn(now - 30 * DAY, 'Europe/Lisbon', 11, 0), since: 100, dog: { breed: 'scott', name: 'Scott' }, destination: { country: 'Portugal', city: 'Lisbon', flag: '🇵🇹' }, mode: 'full', notes: 'Pastéis de nata at midnight. Worth it.' }));
    return list;
  };
  /* ---------- untrusted data: shared links, backup files and storage all go through here ----------
     Values end up inside HTML attributes and class names, so only known-good shapes are kept. */
  const str = (v, max) => (typeof v === 'string' ? v.slice(0, max) : '');
  const HEX = /^#[0-9a-fA-F]{6}$/;
  const PHOTO = /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/;
  P.PHOTO_RE = PHOTO;
  /** returns a safe countdown, or null if it has no usable title / date */
  P.cleanCountdown = (o) => {
    if (!o || typeof o !== 'object' || Array.isArray(o)) return null;
    const title = str(o.title, 80).trim(), targetAt = str(o.targetAt, 16);
    if (!title || !/^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2})?$/.test(targetAt)) return null;
    const type = P.TYPES[o.type] ? o.type : 'custom';
    const d = o.dog && typeof o.dog === 'object' ? o.dog : {};
    const dog = { breed: P.BREEDS && P.BREEDS[d.breed] ? d.breed : 'mutt', name: str(d.name, 30).trim() || 'Buddy' };
    if (d.colors && HEX.test(d.colors.fur || '')) dog.colors = { fur: d.colors.fur };
    if (P.EAR_TYPES && P.EAR_TYPES.some((e) => e[0] === d.ears)) dog.ears = d.ears;
    const c = {
      id: str(o.id, 64).replace(/[^\w-]/g, '') || P.uuid(), title, type, targetAt,
      timeZone: validTz(str(o.timeZone, 64) || P.localTz()), allDay: !!o.allDay, recurrence: o.recurrence === 'yearly' ? 'yearly' : 'none',
      createdAt: Number.isFinite(Date.parse(o.createdAt)) ? new Date(o.createdAt).toISOString() : new Date().toISOString(),
      dog, accent: HEX.test(o.accent || '') ? o.accent : P.TYPES[type].accent,
      displayMode: ['full', 'days', 'sleeps', 'weeks'].includes(o.displayMode) ? o.displayMode : 'full',
      notes: str(o.notes, 2000), archived: !!o.archived, sample: !!o.sample,
      checklist: (Array.isArray(o.checklist) ? o.checklist : []).slice(0, 100).filter((i) => i && typeof i.text === 'string').map((i) => ({ id: str(i.id, 64).replace(/[^\w-]/g, '') || P.uuid(), text: str(i.text, 200), done: !!i.done })),
      memoryPhotos: (Array.isArray(o.memoryPhotos) ? o.memoryPhotos : []).slice(0, 6).filter((u) => typeof u === 'string' && PHOTO.test(u)),
      notifications: (Array.isArray(o.notifications) ? o.notifications : P.defaultNotifications(type)).slice(0, 12).filter((n) => n && Number.isFinite(n.offsetMinutes)).map((n) => ({ offsetMinutes: n.offsetMinutes, label: str(n.label, 60), enabled: !!n.enabled })),
    };
    if (o.destination && typeof o.destination === 'object') c.destination = { country: str(o.destination.country, 60), city: str(o.destination.city, 60), flag: str(o.destination.flag, 8) };
    if (o.person && typeof o.person === 'object') { c.person = { name: str(o.person.name, 40) }; if (Number.isFinite(o.person.birthYear)) c.person.birthYear = Math.trunc(o.person.birthYear); }
    if (o.needs && Number.isFinite(o.needs.food) && Number.isFinite(o.needs.water) && Number.isFinite(o.needs.t)) c.needs = { food: Math.max(0, Math.min(100, o.needs.food)), water: Math.max(0, Math.min(100, o.needs.water)), t: o.needs.t };
    if (o.joy && Number.isFinite(o.joy.v) && Number.isFinite(o.joy.t)) c.joy = { v: Math.max(0, Math.min(100, o.joy.v)), t: o.joy.t };
    return c;
  };
})();
