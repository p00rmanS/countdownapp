/*
 * fx.js - feedback effects: haptics, small synthesised sounds (Web Audio, no audio files), the confetti
 * canvas and floating hearts. Haptics go through native.js first (real vibration on the phone),
 * then fall back to the browser Vibration API. Everything respects the Haptics / Sounds settings and
 * "reduce motion".
 */
(function () {
  const P = (window.Paw = window.Paw || {});

  /* ---------- haptics (Vibration API where available) ---------- */
  const PATTERNS = { tick: [8], purr: [12, 40, 12, 40, 12], success: [18, 50, 18, 50, 70], soft: [5], warn: [30, 40, 30] };
  P.haptic = (kind = 'tick') => {
    try {
      if (!P.store.state.settings.haptics) return;
      if (P.native && P.native.haptic(kind)) return;
      if (navigator.vibrate) navigator.vibrate(PATTERNS[kind] || PATTERNS.tick);
    } catch (e) { /* ignore */ }
  };

  /* ---------- sound (optional; off by default) ---------- */
  let ctx;
  const ac = () => { try { ctx = ctx || new (window.AudioContext || window.webkitAudioContext)(); if (ctx.state === 'suspended') ctx.resume(); return ctx; } catch (e) { return null; } };
  function tone(freq, t0, dur, type = 'triangle', vol = 0.12, slideTo) {
    const a = ac(); if (!a) return;
    const o = a.createOscillator(), g = a.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, a.currentTime + t0);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, a.currentTime + t0 + dur);
    g.gain.setValueAtTime(0.0001, a.currentTime + t0);
    g.gain.exponentialRampToValueAtTime(vol, a.currentTime + t0 + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, a.currentTime + t0 + dur);
    o.connect(g).connect(a.destination); o.start(a.currentTime + t0); o.stop(a.currentTime + t0 + dur + 0.05);
  }
  P.sound = (kind, force) => {
    if (!force && !P.store.state.settings.sound) return;
    if (kind === 'bark') { tone(520, 0, 0.12, 'sawtooth', 0.08, 260); tone(480, 0.16, 0.14, 'sawtooth', 0.07, 240); }
    else if (kind === 'chime') [523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.09, 0.4, 'sine', 0.1));
    else if (kind === 'jingle') [1568, 1976, 1568].forEach((f, i) => tone(f, i * 0.07, 0.22, 'sine', 0.06));
  };

  /* ---------- confetti (canvas) ---------- */
  const COLORS = ['#FF7DAA', '#FFD35C', '#5DADE8', '#D4E157', '#E8A15C', '#F6B7B0', '#E5574F', '#fff'];
  let parts = [], raf = 0, cvs, cx;
  function size() {
    if (!cvs) return;
    const r = cvs.parentElement.getBoundingClientRect(), d = Math.min(2, window.devicePixelRatio || 1);
    cvs.width = r.width * d; cvs.height = r.height * d; cx.setTransform(d, 0, 0, d, 0, 0);
  }
  function loop() {
    const w = cvs.clientWidth, h = cvs.clientHeight;
    cx.clearRect(0, 0, w, h);
    parts = parts.filter((p) => p.y < h + 30 && p.life > 0);
    for (const p of parts) {
      p.vy += 0.16; p.vx *= 0.992; p.x += p.vx; p.y += p.vy; p.r += p.vr; p.life -= 1;
      cx.save(); cx.translate(p.x, p.y); cx.rotate(p.r); cx.globalAlpha = Math.min(1, p.life / 30); cx.fillStyle = p.c;
      if (p.shape === 0) cx.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2);
      else if (p.shape === 1) { cx.beginPath(); cx.arc(0, 0, p.s / 2.4, 0, 7); cx.fill(); }
      else { cx.beginPath(); cx.roundRect(-p.s / 2, -p.s / 5, p.s, p.s / 2.5, 4); cx.arc(-p.s / 2, -p.s / 5, p.s / 4, 0, 7); cx.arc(-p.s / 2, p.s / 5, p.s / 4, 0, 7); cx.arc(p.s / 2, -p.s / 5, p.s / 4, 0, 7); cx.arc(p.s / 2, p.s / 5, p.s / 4, 0, 7); cx.fill(); } // bone
      cx.restore();
    }
    raf = parts.length ? requestAnimationFrame(loop) : 0;
    if (!parts.length) cx.clearRect(0, 0, w, h);
  }
  P.confettiClear = () => { parts = []; };
  P.confetti = (o = {}) => {
    if (document.documentElement.dataset.motion === 'reduce') return;
    cvs = cvs || document.getElementById('confetti'); if (!cvs) return;
    cx = cx || cvs.getContext('2d'); size();
    const w = cvs.clientWidth, h = cvs.clientHeight;
    const x = o.x == null ? w / 2 : o.x, y = o.y == null ? h * 0.38 : o.y, n = o.count || 90, power = o.power || 1;
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2, v = (2 + Math.random() * 7) * power;
      parts.push({ x, y, vx: Math.cos(a) * v * (o.spread || 1), vy: Math.sin(a) * v - 4 * power, s: 6 + Math.random() * 8, r: Math.random() * 6, vr: (Math.random() - 0.5) * 0.4, c: COLORS[i % COLORS.length], life: 140 + Math.random() * 80, shape: Math.random() < 0.12 ? 2 : Math.random() < 0.5 ? 0 : 1 });
    }
    if (!raf) raf = requestAnimationFrame(loop);
  };
  window.addEventListener('resize', () => { if (cvs && cx) size(); });

  /* ---------- floating hearts (pet) ---------- */
  P.hearts = (host, n = 1, x, y) => {
    if (!host || document.documentElement.dataset.motion === 'reduce') return;
    for (let i = 0; i < n; i++) {
      const el = document.createElement('span');
      el.className = 'float-heart'; el.textContent = Math.random() < 0.2 ? '🐾' : '♥';
      const r = host.getBoundingClientRect();
      el.style.left = (x == null ? r.width * (0.4 + Math.random() * 0.2) : x - r.left) + 'px';
      el.style.top = (y == null ? r.height * 0.5 : y - r.top) + 'px';
      el.style.setProperty('--dx', ((Math.random() - 0.5) * 70).toFixed(0) + 'px');
      el.style.fontSize = 16 + Math.random() * 16 + 'px';
      host.appendChild(el);
      setTimeout(() => el.remove(), 1500);
    }
  };
})();
