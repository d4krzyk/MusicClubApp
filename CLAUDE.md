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
- Napis w pasku to **NapisMC w układzie „pionowy”** (dwie linie, M i C ze znaku, krzywe z poprzedniej wersji) — nie znak
  + tekst. Użytkownik wprost tego chciał; nie zastępować tekstem.
- UX ma być płynny, nie jak „stara strona”: zamiast natywnych `<select>` — `ui/Przelacznik` (pigułka z `useWskaznik`)
  i `ui/Wybor`/`ui/Arkusz` (arkusz od dołu, Esc, fokus, przeciąganie w dół). Animacje tylko transform/opacity,
  „ogranicz ruch” wyłącza wszystko jedną regułą na końcu `ui.css`.
- Źródła: Ticketmaster (główne) i kalendarze `.ics` (plik albo subskrypcja).
  **Bandsintown usunięte** na prośbę użytkownika: ich wsparcie odmówiło klucza (API tylko dla artystów, nie dla projektów
  prywatnych/edukacyjnych). Nie dodawać z powrotem. Ślady w danych: `bitKey` wycinany w `store.js` (`DAWNE`), wpis `bit`
  w IndexedDB kasowany przy starcie; `source: 'bit'` zostaje tylko w kopiach zapisanych wydarzeń (podpis na karcie).
  `lib/merge.js` łączy duplikaty (port `EventMerger`); źródło poboczne tylko uzupełnia braki.
- Wydarzenia z kalendarzy (Facebook) oceniane „koncert czy nie” (`lib/eventKind.js`); nie-koncerty tylko w „Moje”,
  poprawka ręczna w `settings.kindOverride`. Wykonawcy z tytułu potwierdzani przez Last.fm, gatunki z tekstu.
  Użytkownik: „nie każde wydarzenie z Facebooka jest stricte muzyczne” — nie pokazywać ich jako koncertów.
- Udział: `settings.udzial[id]` = GOING / INTERESTED / NONE + kopia w `saved` (`lib/udzial.js`); „Moje” filtruje i eksportuje
  przefiltrowane do jednego `.ics`. Gwiazdka na karcie = zainteresowany.
- Przycisk „wstecz” Androida (`src/wstecz.js`, `@capacitor/app`): arkusz → poprzedni ekran → wyjście. Bez tego Capacitor
  zamyka aplikację od razu (zgłoszenie użytkownika).
- Bota do zbierania wydarzeń z Facebooka (konto „dummy”, scraping wyszukiwarki) **nie robimy** — regulamin Facebooka
  zabrania fałszywych kont i automatycznego zbierania danych. Legalnie: eksport `.ics` własnych wydarzeń.
- Miasto: `ui/MiastoWybor` (arkusz z wyszukiwaniem bez polskich znaków, miasto spoza listy, „Moja lokalizacja” przez
  `@capacitor/geolocation` — jednorazowo, przybliżenie, zapisuje się tylko nazwa miasta). Nazwy miast z Ticketmastera
  po polsku z listy miast („Warsaw” → „Warszawa”).
- Poprawki wydarzeń z kalendarzy: `settings.edits[id]` = tylko zmienione pola (`lib/edycja.js`), nakładane przy wczytaniu.
- Arkusz trzyma `onClose` w refie: efekt fokusu zależny od `onClose` (nowa funkcja przy każdym przerysowaniu) ustawiał
  fokus na pierwszym polu po każdej literze — „po jednej literce przeskakuje do innej kolumny” (zgłoszenie użytkownika).
  Esc zamyka tylko arkusz na wierzchu (`naWierzchu` w `wstecz.js`). Test: `edycja2-e2e.mjs` (pisze litera po literze).
- Podpowiedzi miejsca/adresu w poprawkach: `lib/adresy.js` (Photon, photon.komoot.io, OSM — bez klucza, bez `lang`,
  od 3 liter, 350 ms po pisaniu) + `ui/PolePodpowiedzi.jsx` (combobox, lista pod polem, `mousedown` bez domyślnej akcji).
  Miasto w formularzu jest przed miejscem i adresem; wybór podpowiedzi zapisuje też `lat`/`lon`, ręczna zmiana adresu
  albo miasta je kasuje.
- Mapa i trasa: Google Maps po nazwie i adresie (`mapsUrl`/`routeUrl` w `lib/calendar.js`), nie OpenStreetMap — życzenie
  użytkownika; same współrzędne w trasie Google pokazywał jako niezrozumiały punkt.
- Gradient na przyciskach z przezroczystą ramką: `border-box no-repeat`, inaczej pod ramką widać pasek z drugiego końca
  gradientu („jakby ucinał je gradient” — zgłoszenie użytkownika).
- Powiadomienia (Android): `@capacitor/background-runner` uruchamia `src/runner/background.js` (esbuild → `dist/runners/`,
  `npm run build` robi oba). **Silnik w tle to QuickJS bez `URL`, `URLSearchParams`, `Intl` i DOM** — wszystko, co
  importuje runner (`lib/ticketmaster.js`, `lib/match.js`, `lib/powiadomienia.js` i ich zależności), nie może ich używać.
  Sprawdzać w `quickjs-emscripten` (scratchpad), nie tylko w Node. Stan z aplikacji: zdarzenie „stan” (`stanDoTla`);
  „seen” = serie z „Dla ciebie” widziane w aplikacji; runner pamięta, o czym już powiadomił.
- „Do kalendarza” na Androidzie = wtyczka `KalendarzPlugin.java` (Intent `ACTION_INSERT`, pakiet Kalendarza Google, bez
  uprawnień do kalendarza); w przeglądarce link szablonu Google Calendar albo plik `.ics`.

## Układ

- `src/lib/` — czysta logika (testy `npm test`, `node --test`, bez bibliotek): `ticketmaster.js` (odpowiedź → wydarzenie,
  okna po miesiącu, strony), `lastfm.js`, `match.js` (dopasowanie — port `EventMatchService`), `genres.js`, `nameKeys.js`,
  `location.js` + `miastaDane.js` (miasta Polski ze współrzędnymi), `plainText.js` + `boilerplate.js` (opisy bez formułek),
  `store.js` (localStorage + IndexedDB, w testach pamięć), `sync.js`.
- `src/screens/`, `src/App.jsx` (routing po `#hash` — działa z pliku, GitHub Pages i Androida), `src/useApp.js` (cały stan).
- `public/sw.js` — powłoka offline + zdjęcia koncertów; **nigdy** nie cache'uje API.
- `scripts` w `package.json`: `npm run android` = build + `cap sync`. Budowa APK: `.github/workflows/android.yml` — po
  każdym pushu podmienia wydanie „najnowsza”; link dla użytkownika:
  `https://github.com/d4krzyk/MusicClubApp/releases/latest/download/MusicClub.apk`. Podpis: stały, jawny
  `android/app/musicclub-debug.keystore` (wyjątek w `.gitignore`) albo sekrety `MC_KEYSTORE_*`; `versionCode` =
  `GITHUB_RUN_NUMBER`. Zmiana klucza = jednorazowa reinstalacja u użytkownika.

## Zasady sprawdzania (z poprzedniej wersji, nadal obowiązują)

- Zielone testy to nie to samo co działająca aplikacja: sprawdzamy w prawdziwym Chromium (Playwright) na udawanych
  Ticketmasterze i Last.fm (`page.route`; prawdziwe hosty bywają zablokowane w środowisku Claude).
- Przelew strony mierzymy w oknie o sztywnej szerokości (bez `isMobile`), względem karty, nie strony.
- `text-overflow: ellipsis` nie działa na kontenerze `flex` — tekst we własnym `span`.
- `\w` nie łapie polskich liter — w wyrażeniach `\p{L}` z flagą `u`.
- Wzorzec do `pkill -f` pasuje też do własnego polecenia powłoki — zabijać po porcie (`ss -ltnp`), nie po nazwie.
- `backdrop-filter` (i `transform`) na przodku robi z niego punkt odniesienia dla `position: fixed` — dolne menu w pasku
  z rozmyciem stało na górze ekranu. Na telefonie pasek nie ma rozmycia.
- Przyciski z konkretnym napisem („Wszystkie koncerty”) to zwykłe linki, nie `history.back()`.
- `npm test` = samo `node --test` (wzorzec w cudzysłowie nie działa na Node 20, `src/` nie działa na Node 22).

## Niesprawdzone na żywo

CORS Ticketmastera, Last.fm i subskrypcji `.ics` w przeglądarce, eksport z Facebooka, wtyczka kalendarza na telefonie (środowisko nie ma dostępu do tych
hostów ani do `dl.google.com`). Pierwszy prawdziwy przebieg: workflow „Android APK” i próba na własnym telefonie.
