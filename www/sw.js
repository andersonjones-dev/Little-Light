/*
 * LITTLE LIGHT — service worker (offline support for the iPhone web version)
 * ---------------------------------------------------------------
 * IMPORTANT: every time you change any app file and publish it,
 * raise VERSION below (e.g. 1.0.0 -> 1.0.1). That tells phones a
 * new version exists; the app then offers an "Update" button.
 * (The Android app doesn't use this file.)
 */
var VERSION = '1.0.0';
var CACHE = 'littlelight-' + VERSION;

// Everything the app needs to run with no internet connection.
var APP_FILES = [
  './',
  './index.html',
  '/App-Kit/v1/kit.js',
  './fonts/fonts.css',
  './fonts/baloo-2-latin-700-normal.woff2',
  './fonts/baloo-2-latin-800-normal.woff2',
  './fonts/nunito-latin-600-normal.woff2',
  './fonts/nunito-latin-700-normal.woff2',
  './fonts/nunito-latin-800-normal.woff2',
  './capacitor.js',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png',
  './icons/favicon-32.png'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE).then(function (cache) {
      // Save the app's own files; App-Kit is saved best-effort so a slow download never breaks install.
      var own = APP_FILES.filter(function (u) { return u.indexOf('/App-Kit/') !== 0; });
      return cache.addAll(own.map(function (url) { return new Request(url, { cache: 'reload' }); })).then(function () {
        return cache.add(new Request('/App-Kit/v1/kit.js', { cache: 'reload' })).catch(function () {});
      });
    })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      // Only remove Little Light's own old caches: all of Anderson's apps share one website address.
      return Promise.all(keys.map(function (key) {
        if (key.indexOf('littlelight-') === 0 && key !== CACHE) return caches.delete(key);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

// The page asks the waiting worker to take over when the user taps "Update".
self.addEventListener('message', function (event) {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', function (event) {
  var req = event.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);

  // Shared App-Kit (install strip, update bar): open the saved copy straight away
  // and save the newest copy in the background for next time.
  if (url.pathname.indexOf('/App-Kit/') === 0 && (url.origin === self.location.origin || url.hostname === 'andersonjones-dev.github.io')) {
    event.respondWith(caches.open(CACHE).then(function (c) {
      return c.match(req, { ignoreSearch: true }).then(function (hit) {
        var net = fetch(req, { cache: 'no-cache' }).then(function (r) {
          if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone());
          return r;
        });
        if (hit) { event.waitUntil(net.catch(function () {})); return hit; }
        return net;
      });
    }));
    return;
  }
  if (url.origin !== self.location.origin) return;

  // Page loads: serve the saved app so it opens instantly and offline.
  if (req.mode === 'navigate') {
    event.respondWith(
      caches.open(CACHE).then(function (c) {
        return c.match('./index.html').then(function (cached) {
          if (cached) return cached;
          return fetch(req).then(function (res) {
            if (res && res.ok) c.put('./index.html', res.clone());
            return res;
          });
        });
      }).catch(function () { return caches.match('./index.html'); })
    );
    return;
  }

  // Files: saved copy first, then network (and remember anything new).
  event.respondWith(
    caches.match(req, { ignoreSearch: true }).then(function (cached) {
      if (cached) return cached;
      return fetch(req).then(function (res) {
        if (res && res.ok && res.type === 'basic') {
          var copy = res.clone();
          caches.open(CACHE).then(function (cache) { cache.put(req, copy); });
        }
        return res;
      });
    })
  );
});
