# 🐾 Pawcount

Countdowns with a dog who can't wait either. Full product spec: [PROJECT_1.md](PROJECT_1.md).

Three apps, one design:

| | Tech | Folder |
|---|---|---|
| **Android** | Kotlin + Jetpack Compose (native) | `apps/android` |
| **iPhone** | Swift + SwiftUI (native) | `apps/ios` |
| **Web / PWA** | HTML + CSS + JS (installable) | repo root (`index.html`, `js/`, `css/`) |

Live web app: https://p00rmans.github.io/countdownapp/

## How the three stay the same
- **Art.** The dogs and scenes are drawn once, in `js/dogs.js` and `js/scenes.js`. `node tools/export-native.js` turns them into
  JSON scene graphs in `native/assets/` (plus `anim.json`, the animation table, and `meta.json`, the data tables). Both native
  apps load those files and draw them with their own renderer (`Render.kt` / `Render.swift`), so a corgi looks identical everywhere.
  CI fails if `native/assets` is out of date.
- **Logic.** The countdown maths and dog stages live in `js/countdown.js`, `Logic.kt` and `Logic.swift`, covered by the same
  tests on each platform (time zones, daylight saving, stages, yearly repeats, backups).
- **Data.** All three read and write the same backup JSON.

## Run and test
```bash
npm install
npm start              # web app on http://localhost:5173  (python serve.py --lan to open it on a phone)
npm test               # web: 21 maths tests + 24 screen/interaction tests
```
**Android** (JDK 21 + Android SDK): `cd apps/android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
**iPhone** (Mac + Xcode): `brew install xcodegen && cd apps/ios && xcodegen generate && open Pawcount.xcodeproj`

GitHub Actions builds and tests all three on every push, runs the Swift tests in an iPhone simulator, and uploads simulator
screenshots of every screen (artifact `ios-screenshots`). The web app is published to GitHub Pages automatically.

## Releasing
See [RELEASING.md](RELEASING.md) (Google Play, App Store / TestFlight).

## Where things are
`js/countdown.js` · `apps/android/.../Logic.kt` · `apps/ios/Pawcount/Logic.swift` - the countdown brain
`tools/export-native.js`, `native/` - shared art, animation table, fonts
`apps/*/…/Render.*`, `SceneView.*` - the shared-art renderer and the animated scene
`apps/*/…/Services.*` - haptics, sounds, shake, reminders, photos, saving
