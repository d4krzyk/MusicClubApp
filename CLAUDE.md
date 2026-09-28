# MusicClubApp — kontekst projektu

## Stan

**Projekt został oddany i zaliczony** (wrzesień 2026, „Programowanie w Javie III").
Checklista z `docs/WYMAGANIA.md` jest zamknięta: 20 zagadnień przy 17 wymaganych na 5.
To **przestało być praca na ocenę** — nie trzeba już pilnować punktów z listy ani
tłumaczyć decyzji wymaganiami przedmiotu.

## Cel na teraz

Zrobić z tego **prawdziwą aplikację na Androida w Google Play**, z backendem
postawionym na serwerze, tak żeby każdy mógł ją pobrać i się połączyć.
Kierunek prac: responsywność na telefonie → PWA → pakowanie na Androida → hosting.

## Jak pracujemy

**Claude nie commituje i nie pushuje.** Każda zmiana trafia do użytkownika jako
plik `.patch`, który sam nakłada, nazywa i wypuszcza. Hook po każdej turze prosi
o commit — grzecznie odmawiamy.

Łatki robimy względem tego, co jest wypchnięte na `origin/master`, i sprawdzamy
na świeżym klonie (`git apply --check`, `mvn clean test`, `npm run build`) przed
wysłaniem.

Po nałożeniu łatki zawsze `clean` przy testach (`mvnw.cmd clean test`) — bez tego
tłumaczenia nie przeładowują się do `target/classes` i testy fałszywie czerwienieją.

## Zasada sprawdzania

Zielone testy to nie to samo co działająca aplikacja. Każdą zmianę sprawdzamy
dodatkowo na prawdziwym PostgreSQL i w prawdziwej przeglądarce (Playwright +
Chromium). Przy podejrzeniu błędu — najpierw test, który czerwienieje, potem
poprawka. Historia takich wpadek jest w `docs/WYMAGANIA.md`.

## Do zrobienia przed wystawieniem na świat

- Zmienić `REMEMBER_ME_KEY` (domyślna wartość jest w repozytorium).
- Zmienić domyślne hasło administratora (`admin` / `admin12345`).
- `spring.jpa.hibernate.ddl-auto=update` nie nadaje się na produkcję — migracje.
- Wgrane pliki (`app.uploads.dir`) leżą na dysku lokalnym; na hostingu
  kontenerowym znikają przy każdym wdrożeniu.
- Logowanie stoi na ciasteczku sesji + CSRF. W WebView (Capacitor) to nie
  zadziała bez `SameSite=None; Secure`, czyli bez HTTPS.
