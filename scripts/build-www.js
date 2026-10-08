// Copies the static web app into ./www (the Capacitor webDir). No bundler needed.
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..'), out = path.join(root, 'www');
const items = ['index.html', 'manifest.json', 'icon.svg', 'sw.js', 'css', 'js', 'fonts', 'icons'];
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
for (const i of items) fs.cpSync(path.join(root, i), path.join(out, i), { recursive: true });
// The web app (and its PWA) is published from ./www by the Pages workflow.
console.log('Built www/ with', items.length, 'entries');
