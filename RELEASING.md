# Releasing Pawcount

Both store apps are fully native (Kotlin and Swift). Application id / bundle id: `app.pawcount.countdown`.
It is permanent once published; change it in `apps/android/app/build.gradle.kts` and `apps/ios/project.yml` **before** the first upload
if you want a different one.

## Google Play (Android)

Built and tested: `dist/Pawcount-native-release.aab` (upload this) and `dist/Pawcount-native-release.apk` (install on a phone to try).

1. **Back up your signing key.** `apps/android/pawcount-upload.jks` and `apps/android/keystore.properties`. Both are git-ignored.
   Put them in a password manager or an encrypted drive. Lose them and you cannot update the app unless you enrol in Play App Signing (step 3).
2. Create a Google Play Console account (one-time US$25) → *Create app* → name "Pawcount", free, app.
3. *Setup → App signing*: keep the default **Play App Signing** (Google keeps the real key; ours is only the upload key).
4. *Testing → Internal testing → Create release →* upload the `.aab`. Add yourself as a tester and install from the link.
   (New personal accounts must run a closed test with 12 testers for 14 days before Production.)
5. Store listing: short + full description, 512×512 icon (`icons/icon-512.png`), 1024×500 feature graphic, 2–8 phone screenshots,
   privacy policy URL, Data safety form ("no data collected"), content rating (everyone).

Next release: raise `versionCode` / `versionName` in `apps/android/app/build.gradle.kts`, then
```bash
cd apps/android && ./gradlew :app:bundleRelease
```
The bundle appears in `app/build/outputs/bundle/release/`.

## App Store (iPhone)

You need a Mac with Xcode and an Apple Developer Program membership (US$99/yr).
```bash
brew install xcodegen
cd apps/ios && xcodegen generate && open Pawcount.xcodeproj
```
1. In Xcode: *Pawcount target → Signing & Capabilities →* pick your Team. Keep "Automatically manage signing".
2. Pick a real iPhone or "Any iOS Device", then *Product → Archive → Distribute App → App Store Connect → Upload*.
3. In App Store Connect: create the app (same bundle id), add screenshots (6.9" and 6.5" iPhone sizes), description, privacy policy URL,
   age rating, and the "Data Not Collected" privacy label. Test with TestFlight first, then *Submit for Review*.

To see it without a Mac: open the latest GitHub Actions run → artifact **ios-screenshots** (every screen, rendered by an iPhone simulator).
To try it on your phone with no store at all, use the web app and *Add to Home Screen*: https://p00rmans.github.io/countdownapp/

## Before the first public release
- Replace the placeholder privacy policy / support URLs in the store consoles.
- Alternate app icons per dog, widgets, Live Activities and partner sharing (v1.1+) are not built yet.
