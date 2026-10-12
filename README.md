# MusicClub — wersja solo

Koncerty dobrane do **twojego** gustu muzycznego. Aplikacja dla jednej osoby: **bez konta, bez logowania i bez serwera**.
Działa w przeglądarce (PWA, także offline) i jako plik APK na Androida.

To gałąź `release/solo`: pełna wersja społecznościowa MusicClub (znajomi, klany, ekipy na koncert, czat) została
stąd usunięta — zostaje w gałęzi, z której ta powstała.

## Jak to działa

- Koncerty pobiera **bezpośrednio z urządzenia** z [Ticketmaster Discovery API](https://developer.ticketmaster.com/) —
  **twoim własnym, darmowym kluczem** (5000 zapytań na dobę, pełne odświeżenie to kilkanaście zapytań).
- Gust bierze z [Last.fm](https://www.last.fm/api): wpisujesz swój login i aplikacja pobiera twoich najczęściej słuchanych
  artystów. Możesz też dodać artystów ręcznie. Gatunki artystów i opisy („Kim jest?”) też są z Last.fm — **twoim
  własnym kluczem**.
- Każdy płaci własnym limitem: nikt nie korzysta z cudzego klucza, a autor aplikacji nie ma żadnego serwera, który
  mógłby cokolwiek zobaczyć.
- Wszystko, co zapisuje (klucze, gust, zapisane koncerty, pobrane koncerty), zostaje **na tym urządzeniu**
  (`localStorage` i IndexedDB). Aplikacja nie ma analityki ani reklam. Pytania do Ticketmastera i Last.fm idą z twojego
  urządzenia prosto do nich; zdjęcia koncertów ładują się z ich serwerów.
- Po pobraniu koncertów działa **bez internetu**: lista, szukanie, strony wydarzeń, „Zapisane”. Bez internetu nie
  odświeżysz danych i nie pobierzesz opisu artysty, którego jeszcze nie otwierałeś.

## Co jest w środku

| Ekran | Co robi |
|---|---|
| **Koncerty** | „Dla ciebie” (tylko pasujące do gustu, z powodem przy każdym), „Najbliższe” (wszystkie), „Moje” (**Biorę udział** / **Zainteresowany** + wydarzenia z twoich kalendarzy, z filtrem i eksportem do jednego pliku `.ics`). Szukanie, miasto w arkuszu, zasięg suwakiem. Terminy tej samej serii zwinięte w jedną kartę („+2 terminów”). |
| **Wydarzenie** | Termin, miejsce, bilety, **Do kalendarza** (na Androidzie od razu Kalendarz Google, w przeglądarce Kalendarz Google albo plik `.ics`), mapa i trasa w Mapach Google (po nazwie i adresie klubu), **„Popraw dane”** przy wydarzeniach z twoich kalendarzy (godzina, miejsce, miasto, opis, bilety — poprawki zostają po odświeżeniu), skład z gatunkami i linkami wykonawców, „Kim jest?” (opis z Last.fm), opis bez formułek sprzedażowych, „Od organizatora”, inne terminy, podpis źródeł danych. |
| **Twój gust** | Import artystów z Last.fm, ręczna lista, twoje gatunki (z tagów Last.fm). |
| **Ustawienia** | Klucze API, **źródła wydarzeń** (twoje kalendarze `.ics`), kraj i zakres miesięcy, miasto (wyszukiwanie albo **„Moja lokalizacja”** — jednorazowo, zapisuje się tylko nazwa miasta) i promień, język (PL/EN), motyw, kopia ustawień, usunięcie wszystkich danych. |

### Źródła wydarzeń

- **Ticketmaster** — główne źródło (twój klucz).
- **Twoje kalendarze (`.ics`)** — plik importowany raz (np. wydarzenie z Facebooka: „Dodaj do kalendarza / Eksportuj”)
  albo adres subskrypcji (`https://…` lub `webcal://…`: kalendarz Google „tajny adres iCal”, strona klubu), odświeżany razem
  z koncertami. Wydarzenia z kalendarza są w „Moje” i wyżej w „Dla ciebie”.
- **Ten sam koncert z kilku źródeł = jedna karta** (`src/lib/merge.js`, port łączenia z wersji serwerowej: ten sam dzień
  i kraj, ta sama sala — do 500 m albo ta sama nazwa w tym samym (albo nieznanym) mieście — i ten sam wykonawca albo
  nazwa; bez pewnej sali wystarczy ten sam wykonawca o tej samej godzinie; godziny różne o ponad 3 h = dwa koncerty).
  Ticketmaster sam ze sobą też (np. bilety i „pakiet VIP” jako dwa wydarzenia). Źródło poboczne uzupełnia braki
  (adres, godzina, bilety), a krótki albo pusty opis z Ticketmastera zastępuje dłuższym — z Facebooka bywa dużo lepszy.
- **Bandsintown** było w aplikacji jako źródło opcjonalne, ale zostało usunięte: Bandsintown wydaje klucze API tylko
  artystom i ich zespołom, nie aplikacjom fanów ani projektom prywatnym/edukacyjnym (odpowiedź ich wsparcia).
  Stary klucz znika z ustawień, a pobrane z niego koncerty z pamięci urządzenia; zapisane („Biorę udział”) zostają.
- Facebook nie ma publicznego API wydarzeń; eksport `.ics` jest jedyną legalną drogą. Kształtu jego eksportu nie dało się
  stąd sprawdzić na żywo — parser trzyma się standardu (RFC 5545) i jest przetestowany na typowym pliku.

### Wydarzenia z Facebooka: koncert czy nie

Twoja lista „zainteresowany” na Facebooku miesza koncerty z protestami, spacerami i atrakcjami. Aplikacja ocenia każde
wydarzenie z kalendarza (`src/lib/eventKind.js`): słowa muzyczne („koncert”, „support”, „zagrają”, link do Bandcampa),
gatunki w tekście („post punk”, „darkwave'owym”, „gotycki”), wykonawcy z tytułu potwierdzeni przez Last.fm — na plus;
protest, wykład, warsztaty, spacer, wystawa itp. i wydarzenie trwające ponad dobę (chyba że festiwal) — na minus.
Nie-koncerty nie trafiają do list koncertów, są tylko w „Moje” z plakietką „Nie koncert”; na stronie wydarzenia ocenę
zmienisz jednym przyciskiem. Wykonawcy z tytułu („A (Argentyna), B + C / 13.12.26 / Klub, Poznań” → A, B, C — bez dat,
miasta, klubu i listy gatunków) dostają gatunki z Last.fm i „Kim jest?”, a gatunki z tekstu liczą się w „Dla ciebie”.
Odpowiedź z Facebooka („Wezmę udział” / „Zainteresowany”, `PARTSTAT` w pliku) przechodzi do „Moje”.

### Powiadomienia o nowych koncertach (Android)

W Ustawieniach → **Powiadomienia** włączasz dwie rzeczy:
- **Nowe koncerty dla ciebie**: gdy zagra ktoś z twoich artystów (zawsze) albo pojawi się koncert w twoich gatunkach
  w okolicy (zasięg albo 100 km od twojego miasta),
- **Zmiany w „Biorę udział”**: odwołanie, przełożenie, nowa data albo godzina.

Działa to w tle bez otwierania aplikacji: [Capacitor Background Runner](https://github.com/ionic-team/capacitor-background-runner)
uruchamia `src/runner/background.js` (budowany osobno do `dist/runners/background.js`) mniej więcej co 4 godziny, gdy
Android pozwoli. Ten kod pobiera 3 miesiące koncertów z Ticketmastera twoim kluczem, porównuje z tym, co już widziałeś
w aplikacji, i wysyła najwyżej jedno zbiorcze powiadomienie o nowych plus jedno na każdą zmianę. O tym samym nie
powiadamia drugi raz. Aplikacja po każdym odświeżeniu przekazuje do pracy w tle klucz, gust, miasto i to, co już znasz
(zostaje na telefonie). Na części telefonów (Xiaomi, Huawei, Samsung) trzeba wyłączyć oszczędzanie baterii dla
MusicClub — patrz [dontkillmyapp.com](https://dontkillmyapp.com/). W przeglądarce powiadomień nie ma.

Kod w tle działa w osobnym silniku QuickJS bez `URL`, `URLSearchParams` i `Intl`, więc logika, której używa
(`lib/ticketmaster.js`, `lib/match.js`, `lib/powiadomienia.js`), ich nie potrzebuje. Sprawdzone w prawdziwym QuickJS
(WebAssembly) — wynik identyczny jak w Node — i całym plikiem w odizolowanym kontekście z udawanymi API Androida.

### Kalendarz Google na Androidzie

Przycisk „Do kalendarza” w APK otwiera **Kalendarz Google z wypełnionym wydarzeniem** (nazwa, miejsce, opis z linkiem do
biletów, początek i koniec — koncert bez godziny końca trwa 3 h, bez godziny startu jest całodniowy). To zwykły ekran
„Nowe wydarzenie”: zapisujesz jednym dotknięciem, a aplikacja **nie dostaje uprawnień do twojego kalendarza**. Robi to
mała wtyczka `android/app/src/main/java/pl/musicclub/solo/KalendarzPlugin.java` (Intent `ACTION_INSERT` z pakietem
`com.google.android.calendar`); gdy Kalendarza Google nie ma, Android pokazuje inne kalendarze.

### Dopasowanie („Dla ciebie”)

Port logiki z wersji serwerowej (`src/lib/match.js`): ulubiony artysta w składzie albo w nazwie wydarzenia — 100 pkt
(najwyżej dwóch), ten sam gatunek — 15 (najwyżej trzy), ta sama rodzina gatunków — 8 (najwyżej dwie). Bliskość miasta
(0–5 poziomów × 8 pkt) **tylko przesuwa kolejność** — o tym, czy koncert pasuje, decyduje gust. Nazwy porównywane bez
wielkości liter, polskich znaków i „The”. Miasta Polski ze współrzędnymi: `src/lib/miastaDane.js` (miasto spoza listy
działa tylko jako „to samo miasto”, bez odległości).

## Klucze (2 minuty)

1. **Ticketmaster** — załóż konto na <https://developer.ticketmaster.com/>, w „My Apps” skopiuj *Consumer Key*.
2. **Last.fm** — <https://www.last.fm/api/account/create> (wystarczy nazwa aplikacji), skopiuj *API key*.
   Last.fm jest opcjonalny, ale bez niego gust trzeba wpisać ręcznie i nie ma gatunków.

W aplikacji przy każdym kluczu jest **„Wklej”** (ze schowka — wklejona cała linijka typu „API key: …” zostaje
obcięta do samego klucza), **„Sprawdź”** (jedno próbne zapytanie: działa / odrzucony / limit / brak połączenia),
ostrzeżenie, gdy wkleisz „Consumer Secret” zamiast „Consumer Key”, i **„Gdzie znaleźć klucz?”** z linkami prosto do
właściwych stron (Ticketmaster: „My Apps” → „Consumer Key”; Last.fm: <https://www.last.fm/api/accounts> → „API key”).
Logowanie do tych serwisów za ciebie odpada: aplikacja musiałaby znać twoje hasła, a automatyczne zakładanie kluczy łamie
warunki obu serwisów.

Klucz Ticketmaster jest wymagany. **Nie wklejaj kluczy do czatów ani do repozytorium** — kto je zobaczy, wyczerpie
ci limit. Jeśli klucz wyciekł, wygeneruj nowy u dostawcy.

## Uruchomienie

```bash
npm install
npm run dev        # http://localhost:5174
npm test           # testy logiki (node --test, bez bibliotek)
npm run build      # gotowa strona w dist/
npm run preview    # podgląd zbudowanej wersji (service worker działa tylko tu)
```

### Wersja webowa

`dist/` to zwykłe pliki statyczne (adresy względne) — można je wystawić gdziekolwiek. Workflow
`.github/workflows/pages.yml` publikuje je na GitHub Pages (ustaw *Settings → Pages → Source: GitHub Actions*).

> **Uwaga o CORS.** W przeglądarce zapytania do Ticketmastera i Last.fm muszą zostać dopuszczone przez te serwisy
> (nagłówek `Access-Control-Allow-Origin`). Z środowiska, w którym powstał ten kod, oba serwisy były zablokowane, więc
> **tego nie sprawdzono na żywo** — testy chodzą na udawanych serwerach. Jeśli przeglądarka zgłosi „Failed to fetch”,
> użyj aplikacji na Androida: tam zapytania idą natywnie (Capacitor `CapacitorHttp`) i CORS nie ma znaczenia.

### Android (APK)

Aplikacja jest opakowana w [Capacitor](https://capacitorjs.com/) (`android/`, id `pl.musicclub.solo`). Kopie zapasowe Androida są wyłączone (`allowBackup=false`) — klucze nie trafiają do chmury Google.

**Najprościej — gotowy plik z GitHuba.** Otwórz na telefonie:

**https://github.com/d4krzyk/MusicClubApp/releases/latest/download/MusicClub.apk**

i zainstaluj pobrany plik (Android poprosi o zgodę na instalację z tego źródła). Każdy push na `release/solo` uruchamia
workflow „Android APK” (`.github/workflows/android.yml`), który buduje APK i podmienia wydanie „najnowsza” — link zawsze
prowadzi do ostatniej wersji. Plik jest też w *Actions → Android APK → Artifacts → musicclub-debug-apk*.

**Podpis i aktualizacje.** Wersje z CI są podpisane stałym kluczem z repozytorium (`android/app/musicclub-debug.keystore`,
hasło `android`) i mają rosnący `versionCode` (numer przebiegu), więc kolejne instalują się jako aktualizacja i dane
zostają. APK sprzed tej zmiany były podpisane losowym kluczem — przy pierwszej aktualizacji Android zgłosi konflikt
podpisu: zrób *Ustawienia → Kopia ustawień*, odinstaluj starą aplikację, zainstaluj nową i wczytaj kopię.
Klucz w repozytorium jest jawny (to wersja testowa). Własny, prywatny klucz: sekrety repozytorium `MC_KEYSTORE_BASE64`
(plik keystore w base64) i `MC_KEYSTORE_PASSWORD` (alias `androiddebugkey`) — workflow użyje go zamiast jawnego (zmiana
klucza znów wymaga jednorazowej reinstalacji).

**Lokalnie** (potrzebne JDK 17 i Android SDK, np. Android Studio):

```bash
npm install
npm run android          # build + cap sync
cd android && ./gradlew assembleDebug
# plik: android/app/build/outputs/apk/debug/app-debug.apk
```

Kalendarz (`.ics`) i kopia ustawień w aplikacji otwierają systemowe okno „Udostępnij / Zapisz”
(WebView nie pobiera plików sam).

**Do sklepu Google Play** potrzebny jest podpisany pakiet `.aab` (własny keystore — *nie commitować*) i
`targetSdk` zgodny z aktualnym wymogiem Google (Capacitor 6 celuje w 34; nowsza wersja Capacitora podnosi to do 35).

## Co sprawdzono, a czego nie

- **Sprawdzone:** 81 testów logiki (`npm test`, każdy z ~75 sztucznie wprowadzonych błędów w logice zostaje wykryty) oraz cały przepływ w prawdziwym Chromium (Playwright) na udawanych
  Ticketmasterze i Last.fm: ekran powitalny, klucze, pobranie, „Dla ciebie”, szukanie, zapis, strona wydarzenia, „Kim
  jest?”, `.ics`, import i ręczna lista, praca offline po przeładowaniu (service worker + IndexedDB), błędny klucz, usunięcie
  danych, brak przelewu strony przy 320 px.
  Druga runda w Chromium: napis w pasku, płynące przełączniki, arkusze (Esc, fokus), kalendarz Google (adres szablonu),
  import pliku z Facebooka i subskrypcja, łączenie duplikatów, „Moje”, offline z kalendarzami, 320 i 390 px.
  Poprawki wydarzenia: pisanie litera po literze w każdym polu (fokus zostaje — test odtwarza zgłoszony błąd na starym
  kodzie), podpowiedzi miejsca i adresu w wybranym mieście (udawany Photon), klawiatura, Esc, brak sieci.
- **Niesprawdzone (blokada w środowisku):** praca w tle i powiadomienia na prawdziwym Androidzie (harmonogram, zgoda,
  oszczędzanie baterii), prawdziwe odpowiedzi Ticketmastera, Last.fm i eksportu z Facebooka,
  CORS w przeglądarce (subskrypcje `.ics` z Google/Facebooka w przeglądarce prawie na pewno nie przejdą — w APK tak),
  wtyczka kalendarza na prawdziwym telefonie,
  działanie aplikacji na prawdziwym telefonie (APK buduje się w GitHub Actions), prawdziwe odpowiedzi Photona (podpowiedzi adresów).
  Kształt odpowiedzi odtworzono z dokumentacji i z prawdziwej odpowiedzi Ticketmastera z fixture (`src/lib/fixtures`).
- Ticketmaster nie oddaje opisów wykonawców przez publiczne API, a opisy wydarzeń to zwykle formułki sprzedażowe —
  aplikacja je odfiltrowuje (`src/lib/boilerplate.js`) i pokazuje sekcję „O wydarzeniu” tylko wtedy, gdy zostaje prawdziwy tekst.
  Opis artysty pochodzi z Last.fm (licencja CC BY-SA, z podpisem).

## Dane i źródła

Dane o koncertach: Ticketmaster, twoje kalendarze. Gatunki i opisy artystów: Last.fm. Mapy: odnośniki do Map Google (aplikacja nie ładuje map).
Podpowiedzi miejsc i adresów przy poprawianiu wydarzenia: [Photon](https://photon.komoot.io) na danych © OpenStreetMap
(ODbL) — wpisywana fraza i nazwa wybranego miasta idą do photon.komoot.io (bez kluczy i bez innych danych), dopiero od
3 liter i po przerwie w pisaniu; bez sieci pole działa jak zwykłe. Krój pisma: Poppins (SIL OFL).
