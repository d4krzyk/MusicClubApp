/*
   Service worker: warstwa miedzy aplikacja a siecia.

   Robi dwie rzeczy. Po pierwsze sprawia, ze przegladarka uznaje strone za
   aplikacje mozliwa do zainstalowania - bez tego pliku Android nie proponuje
   "Dodaj do ekranu glownego". Po drugie trzyma kopie plikow aplikacji, dzieki
   czemu przy slabym zasiegu otwiera sie ona od razu, zamiast czekac na siec.

   NAJWAZNIEJSZA ZASADA jest ponizej przy /api: odpowiedzi serwera NIGDY nie
   trafiaja do pamieci podrecznej. Sa zwiazane z zalogowanym kontem, a pamiec
   podreczna jest wspolna dla calej przegladarki - zapisana odpowiedz moglaby
   pokazac jednej osobie dane drugiej, gdy obie korzystaja z tego samego
   telefonu.
*/

/* Zmiana numeru unieważnia cala poprzednia kopie - stad wersja w nazwie. */
const CACHE = 'musicclub-v2';

/*
   Co ma byc pod reka od pierwszego uruchomienia.

   Krój pisma jest tu celowo: bez niego aplikacja otwarta bez zasiegu
   rysowalaby sie czcionka systemowa, czyli inaczej niz zwykle. Wszystkie
   piec plikow wazy razem 48 kB.
*/
const SZKIELET = [
  '/',
  '/icon-192.png',
  '/icon-512.png',
  '/manifest.webmanifest',
  '/fonty/poppins-400.woff2',
  '/fonty/poppins-400-italic.woff2',
  '/fonty/poppins-500.woff2',
  '/fonty/poppins-600.woff2',
  '/fonty/poppins-700.woff2',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(SZKIELET))
      /* Nowa wersja wchodzi od razu, bez czekania na zamkniecie starych kart. */
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((nazwy) => Promise.all(
        nazwy.filter((n) => n !== CACHE).map((n) => caches.delete(n)),
      ))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const adres = new URL(request.url);

  /* Zapisywac mozna tylko pobieranie. POST czy DELETE zmieniaja stan na serwerze. */
  if (request.method !== 'GET') {
    return;
  }

  /* Cudze domeny (Deezer, Last.fm, Spotify) zostawiamy przegladarce. */
  if (adres.origin !== self.location.origin) {
    return;
  }

  /* Dane uzytkownika - patrz uwaga na gorze pliku. */
  if (adres.pathname.startsWith('/api/')) {
    return;
  }

  /*
     Pliki z /assets/ maja skrot tresci w nazwie: przy kazdej zmianie zmienia
     sie nazwa. Raz zapisanej kopii nie trzeba wiec nigdy sprawdzac.
  */
  if (adres.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(request).then((kopia) => kopia || pobierzIZapisz(request)),
    );
    return;
  }

  /*
     Wejscie na strone: najpierw siec, zeby zawsze dostac najnowsza wersje
     aplikacji. Dopiero gdy sieci nie ma - zapisana kopia.

     Kopie trzymamy pod jednym kluczem '/', a nie pod adresem, ktory ktos
     wpisal. W aplikacji jednostronicowej KAZDY adres dostaje z serwera ten sam
     index.html, wiec zapisywanie osobno dla /znajomi, /ustawienia i reszty
     oznaczaloby kilkanascie kopii tego samego pliku.
  */
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((odpowiedz) => {
          if (odpowiedz.ok) {
            const kopia = odpowiedz.clone();
            caches.open(CACHE).then((cache) => cache.put('/', kopia));
          }
          return odpowiedz;
        })
        .catch(() => caches.match('/')),
    );
    return;
  }

  /* Reszta (zdjecia z /uploads, ikony): siec, a przy jej braku kopia. */
  event.respondWith(
    pobierzIZapisz(request).catch(() => caches.match(request)),
  );
});

/** Pobiera z sieci i odkłada kopie - ale tylko odpowiedzi udane. */
function pobierzIZapisz(request) {
  return fetch(request).then((odpowiedz) => {
    if (odpowiedz.ok && odpowiedz.type === 'basic') {
      const kopia = odpowiedz.clone();
      caches.open(CACHE).then((cache) => cache.put(request, kopia));
    }
    return odpowiedz;
  });
}

/*
   POWIADOMIENIA PUSH

   Serwer szyfruje tresc kluczem tej przegladarki, usluga push (Google,
   Mozilla, Apple) ja przenosi, a tutaj przychodzi juz odszyfrowana: JSON
   z tytulem, trescia, adresem w aplikacji i znacznikiem. Ten sam znacznik
   zastepuje poprzednie powiadomienie - "jutro" wypiera "za 3 dni".
*/
self.addEventListener('push', (event) => {
  let dane = {};
  try {
    dane = event.data ? event.data.json() : {};
  } catch {
    dane = { title: event.data ? event.data.text() : '' };
  }

  const pokaz = () => self.registration.showNotification(dane.title || 'MusicClub', {
    body: dane.body || '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: dane.tag || undefined,
    renotify: Boolean(dane.tag),
    data: { url: dane.url || '/' },
  });

  /*
     Czat klanu: gdy aplikacja jest na ekranie, nowa wiadomosc i tak widac - brzeczyk
     bylby tylko halasem. Reszta powiadomien (zaproszenia, przypomnienia) idzie zawsze.
  */
  const czatKlanu = typeof dane.tag === 'string' && dane.tag.startsWith('clan-chat-');
  if (!czatKlanu) {
    event.waitUntil(pokaz());
    return;
  }
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((okna) => (okna.some((okno) => okno.visibilityState === 'visible') ? undefined : pokaz())),
  );
});

/*
   Klikniecie: jesli aplikacja jest juz otwarta - przechodzi w niej pod adres
   z powiadomienia, zamiast otwierac druga karte. Adres tylko z naszej
   domeny: powiadomienie nie moze wyprowadzic nikogo na obca strone.
*/
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const cel = new URL(event.notification.data?.url || '/', self.location.origin);
  const adres = cel.origin === self.location.origin ? cel.href : self.location.origin + '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((okna) => {
      const nasze = okna.find((okno) => new URL(okno.url).origin === self.location.origin);
      if (!nasze) {
        return self.clients.openWindow(adres);
      }
      // focus() bywa odrzucany (np. bez aktywacji uzytkownika) - przejscie i tak ma nastapic;
      // okno, ktorego ten worker nie obsluguje, nie da sie przestawic - wtedy nowe
      return nasze.focus()
        .catch(() => nasze)
        .then(() => nasze.navigate(adres))
        .catch(() => self.clients.openWindow(adres));
    }),
  );
});
