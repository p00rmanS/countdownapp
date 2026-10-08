/* Bridge to native capabilities.
   - Inside the Capacitor iOS/Android shell: real haptics + scheduled local notifications (work with the app closed).
   - In a browser / installed PWA: Vibration API (Android), iOS 17.4+ switch-haptic trick, and in-app notifications. */
(function () {
  const P = (window.Paw = window.Paw || {});
  const cap = window.Capacitor;
  const isNative = !!(cap && cap.isNativePlatform && cap.isNativePlatform());
  const plug = (n) => (cap && cap.Plugins && cap.Plugins[n]) || null;
  const ua = navigator.userAgent || '';
  const isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const isAndroid = /Android/i.test(ua);
  const standalone = isNative || matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  P.native = { isNative, isIOS, isAndroid, standalone, plugin: plug };
  document.documentElement.classList.add(isIOS ? 'ios' : isAndroid ? 'android' : 'desktop');
  if (standalone) document.documentElement.classList.add('standalone');

  /* ---------- safe areas ----------
     iOS reports notch / home-indicator sizes through env(safe-area-inset-*) on its own.
     Android's WebView doesn't, so MainActivity exposes them through window.PawInsets and we copy
     them into the same CSS variables the stylesheet already uses (--safe-top etc.). */
  function applyInsets() {
    if (!window.PawInsets) return;
    try {
      const [t, r, b, l] = window.PawInsets.insets().split(',').map(Number);
      const st = document.documentElement.style;
      st.setProperty('--safe-top', t + 'px'); st.setProperty('--safe-right', r + 'px');
      st.setProperty('--safe-bottom', b + 'px'); st.setProperty('--safe-left', l + 'px');
    } catch (e) { /* ignore */ }
  }
  applyInsets();
  // the native side measures after the first layout pass, so ask again a couple of times
  [150, 600, 1500].forEach((ms) => setTimeout(applyInsets, ms));
  window.addEventListener('resize', applyInsets);

  /* ---------- haptics ---------- */
  let sw;
  function iosSwitchTick() {
    try {
      if (!sw) {
        sw = document.createElement('label');
        sw.setAttribute('aria-hidden', 'true');
        sw.style.cssText = 'position:fixed;left:-99px;top:-99px;opacity:0;pointer-events:none';
        sw.innerHTML = '<input type="checkbox" switch>';
        document.body.appendChild(sw);
      }
      sw.click();
    } catch (e) { /* ignore */ }
  }
  P.native.haptic = (kind) => {
    const H = plug('Haptics');
    if (H) {
      try {
        if (kind === 'success') H.notification({ type: 'SUCCESS' });
        else if (kind === 'purr') H.vibrate({ duration: 40 });
        else if (kind === 'warn') H.notification({ type: 'WARNING' });
        else H.impact({ style: kind === 'soft' ? 'LIGHT' : 'MEDIUM' });
      } catch (e) { /* ignore */ }
      return true;
    }
    if (navigator.vibrate) return false;
    if (isIOS) {
      iosSwitchTick();
      if (kind === 'success' || kind === 'purr') setTimeout(iosSwitchTick, 90);
      return true;
    }
    return true;
  };

  /* ---------- notifications ---------- */
  P.notify = async (title, body) => {
    try {
      const reg = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : null;
      if (reg && reg.showNotification) return reg.showNotification(title, { body, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', tag: 'pawcount' });
      if ('Notification' in window && Notification.permission === 'granted') new Notification(title, { body, icon: 'icons/icon-192.png' });
    } catch (e) { /* ignore */ }
  };

  P.native.requestNotifications = async () => {
    const LN = plug('LocalNotifications');
    if (LN) { const r = await LN.requestPermissions(); return r.display === 'granted' ? 'granted' : 'denied'; }
    if (!('Notification' in window)) return 'unsupported';
    if (Notification.permission === 'default') { try { return await Notification.requestPermission(); } catch (e) { return 'denied'; } }
    return Notification.permission;
  };
  P.native.notifyState = () => (plug('LocalNotifications') ? (P.store.state.settings.notifications ? 'granted' : 'default') : 'Notification' in window ? Notification.permission : 'unsupported');

  const hash = (s) => { let h = 7; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h) % 2147483000 + 1; };
  const clean = (t) => t.replace(/\s*[\p{Extended_Pictographic}\u{1F1E6}-\u{1F1FF}️]+\s*/gu, ' ').trim();

  /** Build the upcoming reminder plan (max one per local day, iOS caps pending notifications at 64). */
  P.native.plan = (now = Date.now()) => {
    const list = [];
    P.store.active().forEach((c) => {
      const k = P.compute(c, now);
      if (k.phase === 'past') return;
      const name = c.dog.name, title = clean(c.title) || 'the big day';
      (c.notifications || []).forEach((n) => {
        if (!n.enabled) return;
        let at;
        if (n.offsetMinutes === 0) {
          const p = P.time.partsIn(k.target, k.tz);
          at = new Date(p.y, p.mo - 1, p.d, 9, 0, 0).getTime();
        } else at = k.target - n.offsetMinutes * 60000;
        if (at <= now + 60000 || at > now + 400 * 86400000) return;
        const d = Math.round(n.offsetMinutes / 1440);
        const body = n.passport ? '🛂 Quick check — are your passports valid 6+ months past the trip?'
          : n.offsetMinutes === 0 ? `🎉 IT'S TODAY! ${name} is losing it.`
          : d === 1 ? `⚡ Zoomies activated. Tomorrow is ${title}!`
          : d === 7 ? `🎒 ${name} started packing. One week until ${title}!`
          : `🐕 ${name} just counted: ${d} days until ${title}!`;
        list.push({ id: hash(c.id + ':' + n.offsetMinutes), title: `${name} · ${c.title}`, body, at });
      });
    });
    list.sort((a, b) => a.at - b.at);
    const seen = new Set(), out = [];
    for (const n of list) {
      const day = new Date(n.at).toDateString();
      if (seen.has(day)) continue;
      seen.add(day); out.push(n);
    }
    return out.slice(0, 60);
  };

  let t;
  P.native.reschedule = () => {
    clearTimeout(t);
    t = setTimeout(async () => {
      const LN = plug('LocalNotifications');
      if (!LN || !P.store.state.settings.notifications) return;
      try {
        const perm = await LN.checkPermissions();
        if (perm.display !== 'granted') return;
        const pending = await LN.getPending();
        if (pending.notifications && pending.notifications.length) await LN.cancel({ notifications: pending.notifications });
        const plan = P.native.plan();
        if (plan.length) await LN.schedule({ notifications: plan.map((n) => ({ id: n.id, title: n.title, body: n.body, schedule: { at: new Date(n.at), allowWhileIdle: true }, smallIcon: 'ic_stat_paw' })) });
      } catch (e) { /* ignore */ }
    }, 600);
  };

  /* status bar + splash polish when running natively */
  P.native.applyTheme = (dark) => {
    const SB = plug('StatusBar');
    if (SB) {
      try {
        SB.setStyle({ style: dark ? 'DARK' : 'LIGHT' });
      } catch (e) { /* ignore */ }
    }
  };
})();
