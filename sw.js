// Islam Weaving — service worker: instant app shell + offline fallback.
// Google Apps Script (sheet sync) requests are never cached or touched.
const VERSION = 'iwf-v33';
const SHELL = ['./', 'index.html', 'logo-v4.png', 'icon-v4-512.png', 'manifest.json'];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(VERSION)
      // cache: 'reload' = always take a fresh copy from the server, never from the browser's own HTTP cache
      .then(cache => Promise.all(SHELL.map(u => fetch(new Request(u, { cache: 'reload' })).then(r => { if (r && r.ok) return cache.put(u, r); }).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function timeout(ms) { return new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms)); }

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.pathname.endsWith('news.json')) return;

  if (url.hostname.endsWith('script.google.com') || url.hostname.endsWith('googleusercontent.com')) return;

  // Page itself: always ask the server for the newest copy (cache: 'no-cache' skips the browser's
  // stale HTTP cache, so a new index.html shows up on the very next open).
  // If the network is slow or offline, show the saved copy; a slow download still finishes in the
  // background and is saved for the next open.
  if (req.mode === 'navigate') {
    event.respondWith((async () => {
      const cache = await caches.open(VERSION);
      const net = fetch(new Request(req.url, { cache: 'no-cache', credentials: 'same-origin' }))
        .then(res => { if (res && res.ok) { cache.put('index.html', res.clone()); } return res; });
      event.waitUntil(net.catch(() => {}));
      try {
        return await Promise.race([net, timeout(5000)]);
      } catch (e) {
        return (await cache.match('index.html')) || (await cache.match('./')) || Response.error();
      }
    })());
    return;
  }

  // Icons, manifest, fonts: stale-while-revalidate
  const isFont = url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com';
  if (url.origin === self.location.origin || isFont) {
    event.respondWith((async () => {
      const cache = await caches.open(VERSION);
      const cached = await cache.match(req);
      const network = fetch(req).then(res => { if (res && res.ok) cache.put(req, res.clone()); return res; }).catch(() => null);
      return cached || (await network) || Response.error();
    })());
  }
});
