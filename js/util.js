/*
 * util.js - small shared helpers and the app's fixed data tables.
 *
 * Everything in the app hangs off one global object, window.Paw, because the scripts are plain
 * <script> tags (no bundler). Load order matters and is set in index.html:
 *   util -> countdown -> store -> dogs -> scenes -> fx -> native -> app
 *
 * Contents: HTML escaping, the icon set, event types (TYPES), accent colours, the country list used
 * for trips, a uuid generator and a few colour helpers (mix / onColor).
 */
(function () {
  const P = (window.Paw = window.Paw || {});

  P.esc = (s) =>
    String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const ICONS = {
    home: '<path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10v9.5h13V10"/><path d="M10 19.5v-5h4v5"/>',
    album: '<path d="M12 20s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.3C19.5 15.4 12 20 12 20z"/>',
    sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    back: '<path d="m15 5-7 7 7 7"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    more: '<circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="12" cy="12" r="1.4" fill="currentColor"/><circle cx="19" cy="12" r="1.4" fill="currentColor"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    trash: '<path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13"/>',
    edit: '<path d="M4 20h4L19.5 8.5l-4-4L4 16z"/>',
    bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 2H4.2z"/><path d="M10 21a2 2 0 0 0 4 0"/>',
    camera: '<path d="M4 8h3l1.8-2.6h6.4L17 8h3v11H4z"/><circle cx="12" cy="13.2" r="3.5"/>',
    archive: '<path d="M4 6h16v4H4z"/><path d="M6 10v10h12V10M10 14h4"/>',
    shake: '<path d="m4 9-1.5 3L4 15M20 9l1.5 3L20 15"/><rect x="8" y="5" width="8" height="14" rx="2"/>',
    download: '<path d="M12 4v11m0 0-4-4m4 4 4-4M5 20h14"/>',
    upload: '<path d="M12 16V5m0 0L8 9m4-4 4 4M5 20h14"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c3 3.2 3 13.8 0 17M12 3.5c-3 3.2-3 13.8 0 17"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    repeat: '<path d="M4 11V9a3 3 0 0 1 3-3h11l-3-3M20 13v2a3 3 0 0 1-3 3H6l3 3"/>',
  };
  P.icon = (name, cls = '') =>
    name === 'paw'
      ? `<svg class="i i-fill ${cls}" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="6.2" cy="10.4" rx="2.2" ry="2.9"/><ellipse cx="10.2" cy="6.4" rx="2.2" ry="3"/><ellipse cx="14.8" cy="6.4" rx="2.2" ry="3"/><ellipse cx="18.8" cy="10.4" rx="2.2" ry="2.9"/><path d="M12.5 11.2c3.2 0 6 3.6 6 6.2 0 2-1.8 2.7-3.2 2.4-1.2-.3-1.9-.7-2.8-.7s-1.6.4-2.8.7c-1.4.3-3.2-.4-3.2-2.4 0-2.6 2.8-6.2 6-6.2z"/></svg>`
      : `<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;

  P.TYPES = {
    intl_trip: { label: 'International trip', short: 'Trip abroad', emoji: '✈️', accent: '#5DADE8', hint: 'Time zones, flags & packing', ph: 'Japan', repeat: false },
    vacation: { label: 'Vacation', short: 'Vacation', emoji: '🏝️', accent: '#3FBFA8', hint: 'Sun, sand & a packing list', ph: 'Bali getaway', repeat: false },
    birthday: { label: 'Birthday', short: 'Birthday', emoji: '🎂', accent: '#FF7DAA', hint: 'Repeats every year', ph: "Mia's birthday", repeat: true },
    anniversary: { label: 'Anniversary', short: 'Anniversary', emoji: '💍', accent: '#EE8FA0', hint: 'Together for X days', ph: 'Our anniversary', repeat: true },
    holiday: { label: 'Holiday', short: 'Holiday', emoji: '🎄', accent: '#3E9C6C', hint: 'Cozy seasonal scene', ph: 'Christmas Eve', repeat: true },
    custom: { label: 'Custom', short: 'Custom', emoji: '⭐', accent: '#E8A15C', hint: 'Anything you are excited about', ph: 'Concert night', repeat: false },
  };
  P.TYPE_ORDER = ['intl_trip', 'vacation', 'birthday', 'anniversary', 'holiday', 'custom'];

  P.ACCENTS = [
    { v: '#E8A15C', n: 'Kibble' },
    { v: '#5DADE8', n: 'Sky' },
    { v: '#3FBFA8', n: 'Lagoon' },
    { v: '#FF7DAA', n: 'Confetti' },
    { v: '#EE8FA0', n: 'Blush' },
    { v: '#3E9C6C', n: 'Pine' },
    { v: '#9A7BE0', n: 'Lavender' },
    { v: '#E5574F', n: 'Collar' },
  ];

  P.COUNTRIES = [
    ['Japan', '🇯🇵', 'Asia/Tokyo', 'Tokyo'], ['France', '🇫🇷', 'Europe/Paris', 'Paris'], ['Italy', '🇮🇹', 'Europe/Rome', 'Rome'],
    ['United Kingdom', '🇬🇧', 'Europe/London', 'London'], ['Spain', '🇪🇸', 'Europe/Madrid', 'Madrid'], ['Portugal', '🇵🇹', 'Europe/Lisbon', 'Lisbon'],
    ['Greece', '🇬🇷', 'Europe/Athens', 'Athens'], ['Germany', '🇩🇪', 'Europe/Berlin', 'Berlin'], ['Netherlands', '🇳🇱', 'Europe/Amsterdam', 'Amsterdam'],
    ['Iceland', '🇮🇸', 'Atlantic/Reykjavik', 'Reykjavik'], ['Turkey', '🇹🇷', 'Europe/Istanbul', 'Istanbul'], ['Egypt', '🇪🇬', 'Africa/Cairo', 'Cairo'],
    ['Morocco', '🇲🇦', 'Africa/Casablanca', 'Marrakech'], ['South Africa', '🇿🇦', 'Africa/Johannesburg', 'Cape Town'], ['United Arab Emirates', '🇦🇪', 'Asia/Dubai', 'Dubai'],
    ['India', '🇮🇳', 'Asia/Kolkata', 'Delhi'], ['Thailand', '🇹🇭', 'Asia/Bangkok', 'Bangkok'], ['Vietnam', '🇻🇳', 'Asia/Ho_Chi_Minh', 'Hanoi'],
    ['Singapore', '🇸🇬', 'Asia/Singapore', 'Singapore'], ['Indonesia', '🇮🇩', 'Asia/Makassar', 'Bali'], ['South Korea', '🇰🇷', 'Asia/Seoul', 'Seoul'],
    ['China', '🇨🇳', 'Asia/Shanghai', 'Shanghai'], ['Australia', '🇦🇺', 'Australia/Sydney', 'Sydney'], ['New Zealand', '🇳🇿', 'Pacific/Auckland', 'Auckland'],
    ['United States', '🇺🇸', 'America/New_York', 'New York'], ['Canada', '🇨🇦', 'America/Toronto', 'Toronto'], ['Mexico', '🇲🇽', 'America/Mexico_City', 'Mexico City'],
    ['Brazil', '🇧🇷', 'America/Sao_Paulo', 'Rio'], ['Argentina', '🇦🇷', 'America/Argentina/Buenos_Aires', 'Buenos Aires'], ['Peru', '🇵🇪', 'America/Lima', 'Cusco'],
  ];

  P.localTz = () => {
    try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch (e) { return 'UTC'; }
  };

  P.uuid = () => (crypto.randomUUID ? crypto.randomUUID() : 'id-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8));

  // colour helpers
  const hex = (s) => { s = s.replace('#', ''); if (s.length === 3) s = s.split('').map((x) => x + x).join(''); return [0, 2, 4].map((i) => parseInt(s.substr(i, 2), 16)); };
  const toHex = (a) => '#' + a.map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
  P.mix = (a, b, t) => { const x = hex(a), y = hex(b); return toHex(x.map((v, i) => v + (y[i] - v) * t)); };
  const lum = (c) => { const [r, g, b] = hex(c).map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
  P.onColor = (c) => {
    const L = lum(c), dark = (L + 0.05) / (lum('#3B2314') + 0.05), light = 1.05 / (L + 0.05);
    return dark >= light ? '#3B2314' : '#FFFFFF';
  };
})();
