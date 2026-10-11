# MusicClub solo — kontekst projektu

Gałąź `release/solo`: **aplikacja dla jednej osoby**, bez serwera, konta i warstwy społecznościowej. Pełna wersja
serwerowa (Spring Boot + PostgreSQL, znajomi, klany, ekipy na koncert) została z tej gałęzi usunięta — żyje w gałęzi
`claude/music-club-app-restart-op04i1` (PR #1). Użytkownik pozwolił pracować na osobnej gałęzi (`release/solo`);
na `master` nie wchodzimy.

## Założenia (od użytkownika)

- Działa **webowo** i jako **APK** (Capacitor, `android/`), po pobraniu danych także **offline**.
- Każdy używa **własnych** kluczy Ticketmaster i Last.fm („żeby to było fair”) — nic nie idzie przez serwer autora.
  Klucze zostają na urządzeniu; **nigdy do repozytorium, logów ani komunikatów błędów** (`bezKlucza`).
- Gust: login Last.fm (top artyści) + ręczna lista. Gatunki z tagów Last.fm.

## Układ

- `src/lib/` — czysta logika (testy `npm test`, `node --test`, bez bibliotek): `ticketmaster.js` (odpowiedź → wydarzenie,
  okna po miesiącu, strony), `lastfm.js`, `match.js` (dopasowanie — port `EventMatchService`), `genres.js`, `nameKeys.js`,
  `location.js` + `miastaDane.js` (miasta Polski ze współrzędnymi), `plainText.js` + `boilerplate.js` (opisy bez formułek),
  `store.js` (localStorage + IndexedDB, w testach pamięć), `sync.js`.
- `src/screens/`, `src/App.jsx` (routing po `#hash` — działa z pliku, GitHub Pages i Androida), `src/useApp.js` (cały stan).
- `public/sw.js` — powłoka offline + zdjęcia koncertów; **nigdy** nie cache'uje API.
- `scripts` w `package.json`: `npm run android` = build + `cap sync`. Budowa APK: `.github/workflows/android.yml`.

## Zasady sprawdzania (z poprzedniej wersji, nadal obowiązują)

- Zielone testy to nie to samo co działająca aplikacja: sprawdzamy w prawdziwym Chromium (Playwright) na udawanych
  Ticketmasterze i Last.fm (`page.route`; prawdziwe hosty bywają zablokowane w środowisku Claude).
- Przelew strony mierzymy w oknie o sztywnej szerokości (bez `isMobile`), względem karty, nie strony.
- `text-overflow: ellipsis` nie działa na kontenerze `flex` — tekst we własnym `span`.
- `\w` nie łapie polskich liter — w wyrażeniach `\p{L}` z flagą `u`.
- Wzorzec do `pkill -f` pasuje też do własnego polecenia powłoki — zabijać po porcie (`ss -ltnp`), nie po nazwie.

## Niesprawdzone na żywo

CORS Ticketmastera i Last.fm w przeglądarce, budowa APK i działanie na telefonie (środowisko nie ma dostępu do tych
hostów ani do `dl.google.com`). Pierwszy prawdziwy przebieg: workflow „Android APK” i próba na własnym telefonie.
