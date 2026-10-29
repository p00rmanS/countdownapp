# 🐾 Pawcount

Countdowns with a dog who can't wait either. Full product spec: [PROJECT_1.md](PROJECT_1.md).

Three apps, one design:

| | Tech | Folder |
|---|---|---|
| **Android** | Kotlin + Jetpack Compose (native) | `apps/android` |
| **iPhone** | Swift + SwiftUI (native) | `apps/ios` |
| **Web / PWA** | HTML + CSS + JS (installable) | repo root (`index.html`, `js/`, `css/`) |

Live web app: https://p00rmans.github.io/countdownapp/ (on a computer, the page has a **Try the app** window with QR codes for the web app and the Android test build; `node tools/make-qr.js` regenerates the QR pictures)

## How the three stay the same
- **Art.** The dogs and scenes are drawn once, in `js/dogs.js` and `js/scenes.js`. `node tools/export-native.js` turns them into
  JSON scene graphs in `native/assets/` (plus `anim.json`, the animation table, and `meta.json`, the data tables). Both native
  apps load those files and draw them with their own renderer (`Render.kt` / `Render.swift`), so a corgi looks identical everywhere.
  CI fails if `native/assets` is out of date.
- **Logic.** The countdown maths and dog stages live in `js/countdown.js`, `Logic.kt` and `Logic.swift`, covered by the same
  tests on each platform (time zones, daylight saving, stages, yearly repeats, backups).
- **Data.** All three read and write the same backup JSON.

## What it does
Every countdown gets a dog whose mood follows how close the date is (napping → curious → waiting → packing → zoomies → party → memory).
Tap it to bark, hold to pet, shake the phone to make it sneeze. On top of that:

| | Android | iPhone | Web |
|---|---|---|---|
| Animated dogs, 6 event scenes, rolling numerals | ✅ | ✅ | ✅ |
| Time-zone-true countdowns, yearly repeats | ✅ | ✅ | ✅ |
| Reminders that fire with the app closed | ✅ | ✅ | in-app |
| **Home-screen widget** (soonest countdown, dog in its mood) | ✅ | ✅ small / medium / lock screen | – |
| **Alternate app icon** (pick your dog) | ✅ | ✅ | tab icon |
| **Share picture card** (image for chats / stories) | ✅ | ✅ | – |
| **Share with your person** (link adds the countdown to their app, any platform) | ✅ | ✅ | ✅ |
| Live ticking countdown notification in the last 24 h | ✅ | – | – |
| Long-press shortcuts / Siri | ✅ New · Memories | ✅ "How long until my next countdown" | – |
| Memories with photos, backup export / import | ✅ | ✅ | ✅ |

### For fur parents
Made for people who love their dogs. Scott (a fluffy black pup) is the default companion, with 40 breeds to choose from (American Bully, Great Dane, Saint Bernard, Bernese, Akita, American Bulldog, Dachshund, Pug, Beagle, Poodle, Labrador, German Shepherd, Border Collie and more), and every dog can be played with:

| | |
|---|---|
| **Play** | Treat, Feed, Water, Ball and Tickle, each with its own reaction and over a hundred things the dog says (`{who}` becomes Mama, Papa or your name) |
| **Needs** | Hunger and thirst run down while you are away; a sleeping dog wakes when you play with him. Switch the needs off in Settings > Care |
| **Care streak** | Days in a row you cared for your dog, on Home and in Settings |
| **Daily check-in** | A nudge from your dog at a time you pick (Android and iPhone) |
| **My dog** | Name, birthday or gotcha date, age, and a one-tap yearly birthday countdown |
| **Profile** | Your name and photo, and what your dog calls you (Mama / Papa / Fur parent), asked on first launch |
| **Care log** | Vet visits, vaccines, weight (with a chart, kg or lb), medicine and notes, in Settings > My dog |
| **Event types** | Trip, vacation, birthday, anniversary, holiday, **gotcha day**, **vet & grooming**, custom |
| **Repeats** | Never, weekly (walks, baths), monthly (pills, flea & tick), yearly (birthdays) |
| **Organising** | Pin a countdown to the top of Home; duplicate a countdown |

Plans for the next steps: [docs/SIGNIN_PLAN.md](docs/SIGNIN_PLAN.md) (Google / Apple sign-in) and [docs/FOR_TWO_PLAN.md](docs/FOR_TWO_PLAN.md)
(shared countdowns that stay in sync, which needs the project's first server).

Sharing needs no account or server: a shared link *is* the countdown. Live two-way sync (seeing your partner's paw prints) would
need a server and is not built.

## Run and test
```bash
npm install
npm start              # web app on http://localhost:5173  (python serve.py --lan to open it on a phone)
npm test               # web: date-maths tests + screen and interaction tests
npm run fuzz          # stress test: odd dates and time zones, every dog and scene, a click on every button
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
