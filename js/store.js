/*
 * store.js - everything the user owns lives in one object that is saved to localStorage as JSON.
 * { onboarded, countdowns[], celebrated{}, lastNotified, settings{} }
 * commit() saves and tells subscribers (the notification scheduler listens, so changing a countdown
 * reschedules reminders). If storage is blocked or full, save() returns false and the UI shows a toast.
 * Export / import (backup) just serialise the same object.
 */
(function () {
  const P = (window.Paw = window.Paw || {});
  const KEY = 'pawcount:v1';
  const defaults = () => ({
    onboarded: false,
    countdowns: [],
    celebrated: {},
    lastNotified: '',
    settings: { displayMode: 'full', haptics: true, sound: false, motion: 'system', theme: 'system', notifications: false, icon: 'scott', profileName: '', profilePhoto: '', parentTitle: '' },
  });
  // settings come from storage / backups: keep only values of the right type
  function sanitizeSettings(s) {
    const b = defaults().settings, out = { ...b };
    for (const k of Object.keys(b)) if (typeof s[k] === typeof b[k]) out[k] = s[k];
    if (!['full', 'days', 'sleeps', 'weeks'].includes(out.displayMode)) out.displayMode = 'full';
    if (!['system', 'light', 'dark'].includes(out.theme)) out.theme = 'system';
    if (!['system', 'on', 'off'].includes(out.motion)) out.motion = 'system';
    if (!/^[a-z]+$/.test(out.icon)) out.icon = 'scott';
    out.profileName = out.profileName.slice(0, 30);
    if (!['', 'mama', 'papa', 'parent'].includes(out.parentTitle)) out.parentTitle = '';
    if (out.profilePhoto && !P.PHOTO_RE.test(out.profilePhoto) && !/^[\w.-]{1,80}$/.test(out.profilePhoto)) out.profilePhoto = '';
    return out;
  }
  let state = defaults();
  const subs = [];
  let memoryOnly = false;

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) {
        const d = JSON.parse(raw);
        const base = defaults();
        state = { ...base, ...d, countdowns: Array.isArray(d.countdowns) ? d.countdowns.map(P.cleanCountdown).filter(Boolean) : [], settings: sanitizeSettings({ ...base.settings, ...(d.settings || {}) }) };
      }
    } catch (e) { memoryOnly = true; }
  }
  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); return true; } catch (e) { return false; }
  }
  function emit() { subs.forEach((f) => f(state)); }

  P.store = {
    get state() { return state; },
    load, save,
    subscribe: (f) => subs.push(f),
    commit() { const ok = save(); emit(); return ok; },
    get: (id) => state.countdowns.find((c) => c.id === id),
    active: () => state.countdowns.filter((c) => !c.archived),
    upsert(c) {
      const i = state.countdowns.findIndex((x) => x.id === c.id);
      if (i >= 0) state.countdowns[i] = c; else state.countdowns.push(c);
      return this.commit();
    },
    remove(id) { state.countdowns = state.countdowns.filter((c) => c.id !== id); return this.commit(); },
    set(key, val) { state.settings[key] = val; return this.commit(); },
    loadSamples() {
      state.countdowns = state.countdowns.filter((c) => !c.sample).concat(P.makeSamples());
      state.onboarded = true;
      return this.commit();
    },
    clearSamples() { state.countdowns = state.countdowns.filter((c) => !c.sample); return this.commit(); },
    reset() { state = defaults(); return this.commit(); },
    exportJSON: () => JSON.stringify({ app: 'pawcount', version: 1, exportedAt: new Date().toISOString(), data: state }, null, 2),
    importJSON(text) {
      const j = JSON.parse(text);
      const d = j.data || j;
      if (!Array.isArray(d.countdowns)) throw new Error('Not a Pawcount backup');
      const base = defaults();
      state = { ...base, ...d, countdowns: d.countdowns.map(P.cleanCountdown).filter(Boolean), settings: sanitizeSettings({ ...base.settings, ...(d.settings || {}) }) };
      return this.commit();
    },
  };
})();
