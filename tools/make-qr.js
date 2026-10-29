/* Makes the QR code pictures used by the "Try Pawcount" window (icons/qr-*.svg).
 *   node tools/make-qr.js
 * The Android link points at the latest test build that the GitHub build publishes as a release asset. */
const fs = require('fs'), path = require('path');
const QRCode = require('qrcode');
const OWNER_REPO = 'p00rmanS/countdownapp';
const TARGETS = {
  'qr-web.svg': 'https://p00rmans.github.io/countdownapp/',
  'qr-android.svg': `https://github.com/${OWNER_REPO}/releases/download/android-latest/pawcount.apk`,
};
(async () => {
  for (const [file, url] of Object.entries(TARGETS)) {
    const svg = await QRCode.toString(url, { type: 'svg', margin: 1, errorCorrectionLevel: 'M', color: { dark: '#2B1B14', light: '#FFFFFF' } });
    fs.writeFileSync(path.join(__dirname, '..', 'icons', file), svg);
    console.log(file, '->', url);
  }
})();
