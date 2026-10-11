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
| **Koncerty** | „Dla ciebie” (tylko pasujące do gustu, z powodem przy każdym), „Najbliższe” (wszystkie), „Zapisane” (gwiazdka). Szukanie, filtr miasta, promień od twojego miasta. Terminy tej samej serii zwinięte w jedną kartę („+2 terminów”). |
| **Wydarzenie** | Termin, miejsce, bilety, mapa (OpenStreetMap), trasa, plik do kalendarza (`.ics`), skład z gatunkami i linkami wykonawców, „Kim jest?” (opis z Last.fm), opis wydarzenia bez formułek sprzedażowych, „Od organizatora” (ceny, wiek, start sprzedaży), inne terminy. |
| **Twój gust** | Import artystów z Last.fm, ręczna lista, twoje gatunki (z tagów Last.fm). |
| **Ustawienia** | Klucze API, kraj (19) i zakres miesięcy, miasto i promień, język (PL/EN), motyw, kopia ustawień, usunięcie wszystkich danych. |

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

**Najprościej — przez GitHub Actions:** każdy push na `release/solo` uruchamia workflow „Android APK”
(`.github/workflows/android.yml`). Gotowy plik `app-debug.apk` pobierzesz ze strony przebiegu
(*Actions → Android APK → Artifacts → musicclub-debug-apk*), przerzucasz na telefon i instalujesz (Android poprosi o
zgodę na instalację z nieznanego źródła).

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

- **Sprawdzone:** 38 testów logiki (`npm test`) oraz cały przepływ w prawdziwym Chromium (Playwright) na udawanych
  Ticketmasterze i Last.fm: ekran powitalny, klucze, pobranie, „Dla ciebie”, szukanie, zapis, strona wydarzenia, „Kim
  jest?”, `.ics`, import i ręczna lista, praca offline po przeładowaniu (service worker + IndexedDB), błędny klucz, usunięcie
  danych, brak przelewu strony przy 320 px.
- **Niesprawdzone (blokada w środowisku):** prawdziwe odpowiedzi Ticketmastera i Last.fm przez przeglądarkę (CORS),
  budowa APK i działanie aplikacji na telefonie — workflow i projekt Androida są przygotowane, ale nie zostały uruchomione.
  Kształt odpowiedzi odtworzono z dokumentacji i z prawdziwej odpowiedzi Ticketmastera z fixture (`src/lib/fixtures`).
- Ticketmaster nie oddaje opisów wykonawców przez publiczne API, a opisy wydarzeń to zwykle formułki sprzedażowe —
  aplikacja je odfiltrowuje (`src/lib/boilerplate.js`) i pokazuje sekcję „O wydarzeniu” tylko wtedy, gdy zostaje prawdziwy tekst.
  Opis artysty pochodzi z Last.fm (licencja CC BY-SA, z podpisem).

## Dane i źródła

Dane o koncertach: Ticketmaster. Gatunki i opisy artystów: Last.fm. Mapy: OpenStreetMap (tylko odnośnik, aplikacja nie
ładuje kafelków). Krój pisma: Poppins (SIL OFL).
