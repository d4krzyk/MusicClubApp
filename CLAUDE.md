# MusicClubApp — kontekst projektu

## Stan

**Projekt został oddany i zaliczony** (wrzesień 2026, „Programowanie w Javie III").
Checklista z `docs/WYMAGANIA.md` jest zamknięta: 20 zagadnień przy 17 wymaganych na 5.
To **przestało być praca na ocenę** — nie trzeba już pilnować punktów z listy ani
tłumaczyć decyzji wymaganiami przedmiotu.

## Cel na teraz

Zrobić z tego **prawdziwą aplikację na Androida w Google Play**, z backendem
postawionym na serwerze, tak żeby każdy mógł ją pobrać i się połączyć.

Zrobione: responsywność na telefonie, PWA, logo i napis MusicClub, krój Poppins,
konfiguracja produkcyjna z HTTPS i migracjami (`docs/WDROZENIE.md`),
zakładka Wydarzenia — etap 1 (niżej).
Zostało: stały adres → sprawdzenie PWA na prawdziwym telefonie → TWA przez
Bubblewrap → Google Play.

## Jak pracujemy

Praca idzie na gałąź `claude/music-club-app-restart-op04i1`, z której jest
**PR #1**. Kolejne commity na tę gałąź aktualizują ten PR. Na `master` nie
wchodzimy — scalenie zostaje po stronie użytkownika.

Wcześniej obowiązywała zasada „Claude nie commituje, tylko oddaje `.patch`".
Użytkownik ją zmienił, gdy nie miał jak nakładać łatek. Jeśli poprosi o łatkę
zamiast commita — robimy łatkę względem tego, co jest na zdalnej gałęzi.

Po nałożeniu łatki zawsze `clean` przy testach (`mvnw.cmd clean test`) — bez tego
tłumaczenia nie przeładowują się do `target/classes` i testy fałszywie czerwienieją.

## Zasada sprawdzania

Zielone testy to nie to samo co działająca aplikacja. Każdą zmianę sprawdzamy
dodatkowo na prawdziwym PostgreSQL i w prawdziwej przeglądarce (Playwright +
Chromium). Przy podejrzeniu błędu — najpierw test, który czerwienieje, potem
poprawka. Historia takich wpadek jest w `docs/WYMAGANIA.md`.

Mierzyć, nie szacować. Kilka rzeczy w tym projekcie wyglądało dobrze i było
zepsute: `getBBox()` na tekście SVG zwraca pudełko wiersza, a nie wysokość
liter; `gradientUnits="userSpaceOnUse"` liczy gradient **po** transformacji
elementu; nginx nadpisywał `X-Forwarded-Proto`, przez co znikała flaga `Secure`
na ciasteczku, a logowanie działało dalej i nikt by tego nie zauważył.

Dwie kolejne z zakładki Wydarzenia:
- **Przelew strony mierzy się bez `isMobile`.** Przy emulacji telefonu
  Playwright/Chromium poszerza układ do szerokości treści, więc
  `scrollWidth - innerWidth` wychodzi 0 nawet wtedy, gdy pasek wystaje o 11 px.
  Mierzyć w oknie o sztywnej szerokości: prawa krawędź najdalszego elementu
  minus `clientWidth`.
- **Blok tekstowy w Javie (`"""`) obcina spacje na końcach linii.**
  `"WHERE " + warunek` w bloku dał `WHEREe.start_date`. Tam, gdzie spacja na
  końcu linii jest potrzebna, pisze się `\s`.
- **`text-overflow: ellipsis` nie działa na kontenerze `flex`** — goły tekst
  staje się anonimowym elementem flex i jest ucinany w pół słowa bez
  wielokropka. Tekst trzeba włożyć we własny `span` z obcięciem (sprawdzać:
  `scrollWidth > clientWidth` i `textOverflow` na tym spanie).
- **Zegar testowy a północ w Polsce.** Testy importu stoją na 28.09 10:00 UTC;
  +12 h to już 29.09 w Warszawie, więc „dziś" się przesuwa i udawany
  Ticketmaster przestaje odpowiadać. Przesunięcia w testach — w obrębie dnia.

## Konfiguracja produkcyjna

Opisana w `docs/WDROZENIE.md`. W skrócie:

- `docker-compose.prod.yml` z dwoma profilami: `tunel` (Cloudflare, darmowy,
  bez domeny, adres losowy) i `domena` (Caddy z Let's Encrypt).
- Profil Springa `prod` (`application-prod.properties`): `ddl-auto=validate`
  plus Flyway, `Secure`/`HttpOnly`/`SameSite` na ciasteczku,
  `forward-headers-strategy`, ciche logi, Swagger wyłączony, pusty CORS.
- Migracje w `src/main/resources/db/migration/`. Poza produkcją Flyway jest
  **wyłączony** — w pracy nad kodem zostaje `ddl-auto=update`, a testy chodzą
  na H2 z `create-drop`. Migracji już wypuszczonej się nie edytuje.

## Do zrobienia przed wystawieniem na świat

- **Ustawić własne `REMEMBER_ME_KEY` i `ADMIN_PASSWORD` w `.env`.** Na produkcji
  nie mają wartości domyślnych, więc bez nich nic nie wystartuje — ale wartość
  trzeba wygenerować (`openssl rand -base64 48`). Domyślny klucz z repozytorium
  pozwala podpisać ciasteczko „zapamiętaj mnie" na dowolne konto.
- Wgrane pliki (`app.uploads.dir`) leżą na wolumenie Dockera. Na jednym serwerze
  to wystarcza; na hostingu kontenerowym bez trwałego dysku znikną przy każdym
  wdrożeniu — wtedy potrzebny będzie magazyn obiektowy.
- Manifest PWA ma `background_color` i `theme_color` tylko jasne, więc ekran
  startowy jest biały także w ciemnym motywie.

## Wydarzenia

Koncerty w Polsce z **Ticketmaster Discovery API** (darmowy klucz,
5000 zapytań/dzień, `TICKETMASTER_API_KEY`). Inne źródła sprawdzone i odrzucone:
Facebook nie daje cudzych wydarzeń od 2018, Songkick tylko płatnie,
Bandsintown wymaga pisemnej zgody, Eventbrite wyłączył wyszukiwanie w 2020,
Going./eBilet nie mają API. Ticketmaster zwrócił 801 koncertów w Polsce,
także klubowych (Progresja, Hydrozagadka, Drizzly Grizzly).

Etap 1 (zrobiony): import co 6 h do `music_events` (migracja V2; po nieudanym
imporcie ponowna próba co kwadrans), lista z grupowaniem serii („Koncert przy
świecach" grany co wieczór to jedna karta z „+3 terminy"), filtr miasta,
szukanie, strona wydarzenia z mapą i biletami.

Etap 2 (zrobiony, migracja V3): „Zainteresowany" / „Biorę udział" / rezygnacja,
lista uczestników i trzy widoki listy — **Dla ciebie**, Najbliższe, Moje.
- Lista uczestników: tylko „idę"; widzą ją wszyscy zalogowani, a kto zaznaczy
  „nie pokazuj mnie", liczy się do licznika bez nazwy konta (wariant A —
  przyjęty domyślnie, użytkownik nie wybrał innego). Siebie widzi się zawsze.
- „Dla ciebie" (`EventMatchService`): ulubiony artysta w składzie albo w nazwie
  wydarzenia 100 pkt, wykonawca ulubionego utworu 70, wspólny gatunek 15
  (maks. 3), wspólna rodzina gatunków 8 (maks. 2), zapisany znajomy 20
  (maks. 3). Przy każdej pozycji lista powodów. Nazwy porównywane po
  `NameKeys` (bez wielkości liter, polskich znaków i „The").
- Gatunki wykonawców koncertów z Last.fm w `performer_tags` — do 300 na przebieg
  importu, ważne 60 dni; awaria Last.fm nie zapisuje pustych tagów. Bez
  `LASTFM_API_KEY` zostają ogólne etykiety Ticketmastera (a gust użytkownika
  i tak ma gatunki tylko z Last.fm).
- Wydarzenie, które zniknęło z Ticketmastera, a ktoś był na nie zapisany, nie
  jest kasowane: dostaje `withdrawn_at`, znika z list poza „Moje", a jego
  strona to wyjaśnia. Wraca, gdy Ticketmaster znów je pokaże. Po 30 dniach od
  daty znika razem z zapisami.

Na telefonie (< 576 px) ikony administratora przechodzą do menu konta, a licznik
zgłoszeń na awatar — z ikoną Wydarzeń pasek administratora wychodził poza
ekran o 41 px przy 360 px. Teraz mieści się od 320 px w górę (zmierzone).

Zostało:
4. Przypomnienie w dzwonku kilka dni przed wydarzeniem (dla zainteresowanych
   i idących).
5. Opcjonalnie Web Push na telefon.

## Wybory, do których nie wracamy

- **TWA, nie Capacitor.** Frontend i API stoją pod jednym adresem (nginx
  przekazuje `/api` i `/uploads` do backendu), więc ciasteczko sesji i CSRF
  działają bez żadnych zmian w kodzie. Capacitor dałby WebView z własnym
  originem i wróciłyby `SameSite=None`, CORS i osobne ustawienia ciasteczek.
- **Napis MusicClub jest na krzywych**, nie tekstem — nie zależy od doładowania
  kroju. Kształty w `frontend/src/components/napisMCKsztalty.js` są generowane;
  nie poprawiamy ich ręcznie.
