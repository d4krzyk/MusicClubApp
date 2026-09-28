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
konfiguracja produkcyjna z HTTPS i migracjami (`docs/WDROZENIE.md`).
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
- Pasek górny przy 320 px i koncie administratora nadal wychodzi poza ekran
  o 40 px: ikony (170 px) i prawa strona (130 px) same nie mieszczą się w oknie.
  Naprawa wymaga zmniejszenia przycisków poniżej 40 px, czyli poniżej wygodnego
  celu dotykowego.

## Wybory, do których nie wracamy

- **TWA, nie Capacitor.** Frontend i API stoją pod jednym adresem (nginx
  przekazuje `/api` i `/uploads` do backendu), więc ciasteczko sesji i CSRF
  działają bez żadnych zmian w kodzie. Capacitor dałby WebView z własnym
  originem i wróciłyby `SameSite=None`, CORS i osobne ustawienia ciasteczek.
- **Napis MusicClub jest na krzywych**, nie tekstem — nie zależy od doładowania
  kroju. Kształty w `frontend/src/components/napisMCKsztalty.js` są generowane;
  nie poprawiamy ich ręcznie.
