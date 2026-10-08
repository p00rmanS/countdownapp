# Releasing Pawcount

## Google Play (Android)

Built and tested already: `dist/Pawcount-release.aab` (upload this) and `dist/Pawcount-release.apk` (install directly on a phone to try it).

1. **Back up your signing key now.** `android/pawcount-upload.jks` + the passwords in `android/keystore.properties`.
   Both are git-ignored. Copy them to a password manager or encrypted drive. If you lose them you cannot update the app
   under this key (Play can reset an *upload* key, but only if you enrolled in Play App Signing - step 3).
2. Create a Google Play Console account (one-time US$25) and *Create app* → name "Pawcount", free, app.
3. *Setup → App signing*: keep the default **Play App Signing** (Google holds the real key, ours is only the upload key).
4. *Testing → Internal testing → Create release →* upload `Pawcount-release.aab`. Add yourself as a tester and install from the link.
   (New personal accounts must run a closed test with 12 testers for 14 days before Production.)
5. Fill in the store listing: short + full description, 512×512 icon (`icons/icon-512.png`), a 1024×500 feature graphic,
   2-8 phone screenshots, privacy policy URL, Data safety form ("no data collected"), content rating (everyone), target audience.
6. Submit for review.

Next release: raise `versionCode` (and `versionName`) in `android/app/build.gradle`, then
```bash
npm run release:android      # rebuilds the web app, syncs, makes the signed .aab and .apk
```
The bundle is in `android/app/build/outputs/bundle/release/`.

Application id is `app.pawcount.countdown`. It is permanent once published, so change it in `capacitor.config.json` and
`android/app/build.gradle` (+ move `MainActivity.java`) **before** the first upload if you want a different one.

## Checking the iPhone version

You don't need to own a Mac to try it. Easiest first:

**1. Right now, on any iPhone - the web app.** Host the folder on any HTTPS static host
(Netlify Drop, Vercel, GitHub Pages) or run `python serve.py` and open `http://<your PC's LAN IP>:5173` in Safari
(works for looking around; install + reminders need HTTPS). Safari → Share → *Add to Home Screen*. This runs the same code
as the app, minus background reminders.

**2. Without a Mac - build in the cloud.** `.github/workflows/build.yml` compiles the iOS app on a GitHub macOS machine
and uploads a simulator build. Push this folder to GitHub → *Actions* tab → open the `ios` job → download `ios-simulator-app`.
It only proves the app compiles. To *see* it, use a cloud Mac service such as Codemagic (free tier) or MacinCloud.

**3. On a Mac with Xcode (the real thing).**
```bash
npm install
npx cap sync ios
cd ios/App && pod install && cd ../..
npx cap open ios          # opens Xcode
```
Pick an iPhone simulator at the top and press ▶. For a real phone: Signing & Capabilities → choose your Apple ID team,
plug in the phone, press ▶. To ship: Apple Developer Program (US$99/yr), then Product → Archive → upload to TestFlight.

**What to look at first on iOS:** notch/Dynamic Island spacing, the home-indicator gap under the tab bar, tap/hold/shake
(iOS asks permission for motion the first time you press the dog), haptics, and Settings → Reminders.
