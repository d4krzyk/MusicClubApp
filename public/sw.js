/*
  Service worker: dzieki niemu aplikacja otwiera sie bez internetu.
  - powloka aplikacji (HTML, skrypty, style, czcionki, ikony) jest zapisywana przy instalacji i odswiezana po cichu,
  - zdjecia koncertow (inne domeny) zapisuja sie, gdy sie je obejrzy - najwyzej 150 sztuk,
  - zapytania do Ticketmastera i Last.fm NIGDY tu nie trafiaja: dane aplikacja trzyma sama (IndexedDB).
*/
const WERSJA = 'musicclub-solo-v1';
const OBRAZY = 'musicclub-solo-obrazy-v1';
const MAX_OBRAZOW = 150;
const POWLOKA = ['./', './manifest.webmanifest', './icon-192.png', './icon-512.png', './fonty/poppins-400.woff2',
  './fonty/poppins-400-italic.woff2', './fonty/poppins-500.woff2', './fonty/poppins-600.woff2', './fonty/poppins-700.woff2'];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(WERSJA);
    await cache.addAll(POWLOKA);
    // pliki z hashem w nazwie (skrypt i styl aplikacji) - odczytane z zapisanego HTML-a
    const html = await (await cache.match('./')).text();
    const zasoby = [...html.matchAll(/["'](\.\/assets\/[^"']+)["']/g)].map((m) => m[1]);
    await cache.addAll([...new Set(zasoby)]);
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const nazwa of await caches.keys()) if (nazwa !== WERSJA && nazwa !== OBRAZY) await caches.delete(nazwa);
    await self.clients.claim();
  })());
});

async function przytnij(cache) {
  const klucze = await cache.keys();
  for (let i = 0; i < klucze.length - MAX_OBRAZOW; i++) await cache.delete(klucze[i]);
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const adres = new URL(request.url);

  if (adres.origin !== self.location.origin) {
    // cudze obrazy: z pamieci, a gdy ich nie ma - z sieci i do pamieci. Reszta (API) idzie prosto do sieci.
    if (request.destination !== 'image') return;
    event.respondWith((async () => {
      const cache = await caches.open(OBRAZY);
      const jest = await cache.match(request);
      if (jest) return jest;
      try {
        const odp = await fetch(request);
        if (odp.ok || odp.type === 'opaque') { cache.put(request, odp.clone()); przytnij(cache); }
        return odp;
      } catch { return Response.error(); }
    })());
    return;
  }

  // nawigacja: sieć najpierw (nowa wersja), a bez internetu zapisany HTML
  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      const cache = await caches.open(WERSJA);
      try {
        const odp = await fetch(request);
        if (odp.ok) cache.put('./', odp.clone());
        return odp;
      } catch { return (await cache.match('./')) ?? Response.error(); }
    })());
    return;
  }

  // reszta swoich plikow: z pamieci, w tle odswiezana
  event.respondWith((async () => {
    const cache = await caches.open(WERSJA);
    const jest = await cache.match(request);
    const siec = fetch(request).then((odp) => { if (odp.ok) cache.put(request, odp.clone()); return odp; }).catch(() => null);
    return jest ?? (await siec) ?? Response.error();
  })());
});
