# 🐾 Pawcount

Countdowns with a dog who can't wait either. Full spec: [PROJECT_1.md](PROJECT_1.md).

## Run it (any device)
```bash
python serve.py        # http://localhost:5173
```
Open it on your phone via your computer's LAN address (same Wi-Fi), or host the folder on any static host (Netlify, Vercel, GitHub Pages — needs HTTPS for install/notifications).

## Install as an app (PWA)
- **iPhone/iPad (Safari):** Share → *Add to Home Screen*. Reminders work once installed (iOS 16.4+).
- **Android (Chrome):** Settings → *Install app* button, or ⋮ → *Install app*.

## Ship to the App Store / Google Play (Capacitor)
Same code, real native shell, with real haptics and **scheduled local notifications that fire with the app closed**.
```bash
npm install
npx cap add ios        # needs a Mac + Xcode
npx cap add android    # needs Android Studio
npm run ios            # builds www/, syncs, opens Xcode
npm run android        # builds www/, syncs, opens Android Studio
```
Then set your signing team / bundle id (`app.pawcount.countdown` in `capacitor.config.json`) and archive.
Android notification icon: add a white-on-transparent `ic_stat_paw` drawable in `android/app/src/main/res/drawable`.

## Layout
`js/countdown.js` time-zone-safe math + dog state machine · `js/dogs.js` SVG dogs · `js/scenes.js` backdrops ·
`js/native.js` haptics/notification bridge · `js/app.js` screens · `css/styles.css` design system.

## Tests
```bash
npm test     # countdown maths (time zones, DST, stages, yearly repeat) + every screen and interaction
```
