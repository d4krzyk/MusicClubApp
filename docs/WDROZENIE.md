# Wystawienie MusicClub na świat

Droga od „działa u mnie" do „da się zainstalować z telefonu".
Ten plik opisuje etapy 1 i 2 — serwer z HTTPS i ustawienia produkcyjne.
Pakowanie na Androida (TWA) i Google Play są opisane na końcu, jako to,
co czeka dalej.

---

## Najpierw: dlaczego to w ogóle działa

Najtrudniejszy kawałek był zrobiony od początku. `frontend/nginx.conf`
przekazuje `/api` i `/uploads` do backendu, a `src/api/client.js` woła
`/api` adresem względnym. Przeglądarka widzi więc **wszystko pod jednym
adresem**: ciasteczko sesji i token CSRF są pierwszej strony, a CORS
w ogóle nie wchodzi do gry.

To samo jest warunkiem sensownej aplikacji na Androida — i dlatego dalej
wybieramy TWA, a nie Capacitor.

---

## Etap 1 — HTTPS. Dwie drogi

### Na testy, bez domeny i za darmo — tunel Cloudflare

```bash
cp .env.example .env          # i uzupełnij wartości
docker compose -f docker-compose.prod.yml --profile tunel up -d --build
docker compose -f docker-compose.prod.yml logs -f tunel
```

W logach pojawi się adres `https://coś-tam.trycloudflare.com`. Działa od
razu, z prawdziwym certyfikatem — wystarczy, żeby wejść z telefonu
i zainstalować aplikację z Chrome.

Nie trzeba domeny, konta ani otwierania portów na serwerze. Tunel sam
łączy się na zewnątrz.

**Czego to nie załatwia:** adres jest losowy i zmienia się przy każdym
restarcie tunelu. Do testów w sam raz, do Google Play nie — tam adres
wchodzi do manifestu i do `assetlinks.json` i musi być stały.

### Gdy masz już domenę — Caddy

```bash
DOMENA=twoja-domena.pl docker compose -f docker-compose.prod.yml \
    --profile domena up -d --build
```

Caddy sam występuje o certyfikat Let's Encrypt i sam go odnawia.
Potrzebuje dwóch rzeczy: domena ma wskazywać na adres IP serwera,
a porty 80 i 443 mają być otwarte — po porcie 80 przychodzi sprawdzenie,
czy domena naprawdę jest Twoja.

Certyfikaty leżą na wolumenie `caddy-data`. Nie kasuj go przy
`docker compose down -v`, bo Let's Encrypt ma tygodniowe limity
na wydawanie certyfikatów dla tej samej domeny.

---

## Etap 2 — co się zmienia na serwerze

Profil `prod` (`SPRING_PROFILES_ACTIVE=prod`, ustawiany przez
`docker-compose.prod.yml`) przestawia osiem rzeczy.

### Schemat bazy: koniec z `ddl-auto=update`

Do tej pory schemat powstawał sam: Hibernate oglądał encje i dopisywał
brakujące tabele. Wygodne przy pisaniu, nie do utrzymania na serwerze —
`update` potrafi zmiany **nie wykonać** (nie zwęzi kolumny, bo mogłoby to
uciąć dane), a wykonanych nie da się cofnąć ani powtórzyć gdzie indziej.

Od teraz schemat jest w `src/main/resources/db/migration/` i zmienia się
wyłącznie przez migracje. Profil produkcyjny ma `ddl-auto=validate`:
Hibernate niczego nie tworzy, tylko sprawdza zgodność z encjami i **nie
wstaje**, jeśli się rozjechały.

Plik `V1__schemat_poczatkowy.sql` powstał maszynowo — aplikacja
z `ddl-auto=create` zbudowała schemat na pustej bazie, a `pg_dump` zrzucił
wynik. Dzięki temu jest dokładnie tym, czego oczekuje Hibernate.

Baza, która już istnieje i powstała ze starego `update`, nie ma tabeli
historii Flyway. Obsługuje to `baseline-on-migrate`: taka baza zostaje
zapisana jako „stan V1 już osiągnięty" i liczenie rusza od V2.

**W trybie deweloperskim nic się nie zmienia** — tam dalej działa
`ddl-auto=update`, a Flyway jest wyłączony. Testy chodzą na H2
z `create-drop` i też go nie widzą.

Kolejna zmiana w encjach = nowy plik z kolejnym numerem. `V2__wydarzenia.sql`
już jest (tabele zakładki Wydarzenia), więc następna to `V3__opis_zmiany.sql`.
Migracji już wypuszczonej **nie edytuje się** — Flyway pilnuje sum
kontrolnych i odmówi startu.

### Ciasteczka

```
server.servlet.session.cookie.secure=true      # tylko po HTTPS
server.servlet.session.cookie.http-only=true   # JavaScript go nie odczyta
server.servlet.session.cookie.same-site=lax    # nie leci z obcych stron
```

`lax` wystarcza i dla TWA: to ten sam Chrome i ten sam adres, więc
ciasteczko jest pierwszej strony.

### Praca za pośrednikiem

```
server.forward-headers-strategy=framework
```

Ruch idzie przez Caddy albo tunel, które kończą HTTPS i dalej rozmawiają
zwykłym HTTP. Bez tego Spring uznaje, że stoi na nieszyfrowanym
połączeniu, **nie ustawia flagi Secure** i buduje przekierowania z adresem
`http`.

Z tym samym wiąże się blok `map` na górze `nginx.conf`. Nginx wcześniej
nadpisywał `X-Forwarded-Proto` własnym `$scheme` — czyli `http`, bo do
niego faktycznie przychodzi HTTP. Flaga Secure nie powstawałaby, a nikt by
tego nie zauważył, bo logowanie działa dalej. Teraz nagłówek jest
przepuszczany bez zmian, a `$scheme` wchodzi tylko wtedy, gdy nagłówka
nie ma.

### Logi

`logging.level.org.hibernate.orm.jdbc.bind=TRACE` z trybu deweloperskiego
loguje **wartości parametrów zapytań** — czyli adresy e-mail i skróty haseł
przekazywane do bazy. Na serwerze zjeżdża do `WARN`, razem z `show-sql`.

### Swagger

Domyślnie wyłączony (`SWAGGER_ENABLED=false`). To kompletna mapa API
razem z kształtem każdego zapytania.

### CORS

Pusta lista źródeł — i tak ma być. Wszystko stoi pod jednym adresem, więc
przeglądarka nie wysyła nagłówka `Origin`. Zostawienie tam `localhost`
pozwalałoby czyjemuś lokalnemu frontendowi wołać to API razem
z ciasteczkiem sesji.

### Co przestaje być widoczne z zewnątrz

W `docker-compose.yml` baza wisi na 5432, backend na 8080, Adminer na 8081
— dla wygody. Na serwerze z publicznym adresem oznaczałoby to wystawiony
PostgreSQL i przeglądarkę bazy dostępne z całego internetu.
W `docker-compose.prod.yml` sekcji `ports` nie ma nigdzie poza tym, co
kończy HTTPS.

### Zmienne bez wartości domyślnej

`REMEMBER_ME_KEY` i `ADMIN_PASSWORD` nie mają na produkcji wartości
domyślnych — ani w `application-prod.properties`, ani w compose. Przy ich
braku **nic nie wystartuje** i powie, czego brakuje.

To jest celowe. Domyślny klucz „zapamiętaj mnie" leży w repozytorium,
a kto go zna, podpisze sobie ciasteczko na dowolną nazwę użytkownika
i zaloguje się jako ktokolwiek — nie znając żadnego hasła.

### Wydarzenia (Ticketmaster)

Zakładka *Wydarzenia* bierze koncerty z Ticketmaster Discovery API. Klucz
jest **opcjonalny** — bez niego aplikacja działa normalnie, a zakładka jest
pusta i administrator widzi tam, czego brakuje.

1. Załóż konto na developer.ticketmaster.com.
2. *My Apps* → aplikacja `<login>-App` → **Consumer Key**.
3. Dopisz do `.env`: `TICKETMASTER_API_KEY=...` i uruchom backend ponownie.

Serwer pobiera koncerty sam: pierwszy raz 30 sekund po starcie. Udany import
powtarza się co 6 godzin, nieudany — przy najbliższym sprawdzeniu, co
kwadrans. Jeden przebieg to 13–20 zapytań (rok naprzód, miesiąc po
miesiącu), czyli poniżej 100 dziennie przy limicie 5000. Administrator widzi
nad listą, kiedy było ostatnie pobranie i ile przyszło wydarzeń albo
dlaczego się nie udało.

Klucza nie ma w logach: tam, gdzie mógłby się pojawić, stoją gwiazdki.

#### Gdy import się nie udaje

```bash
docker compose -f docker-compose.prod.yml logs backend | grep "Import wydarzen"
```

Po słowie „odpowiada:" albo „odmowil:" stoi przyczyna:

| W logu | Co to znaczy |
|---|---|
| `HTTP 401 Invalid ApiKey` | zły klucz — sprawdź `TICKETMASTER_API_KEY` w `.env` |
| `HTTP 429` | przekroczony limit zapytań — minie sam |
| `UnknownHostException` | kontener nie może rozwiązać nazwy — DNS w Dockerze |
| `SunCertPathBuilderException`, `SSLHandshakeException` | coś po drodze podmienia certyfikat (np. antywirus skanujący HTTPS) |
| `SocketTimeoutException`, `ConnectException` | brak połączenia z serwerem Ticketmastera |

Sprawdzenie z wnętrza kontenera, niezależnie od Javy:

```bash
docker compose -f docker-compose.prod.yml exec backend getent hosts app.ticketmaster.com
docker compose -f docker-compose.prod.yml exec backend \
  curl -sS -o /dev/null -w "HTTP %{http_code}\n" https://app.ticketmaster.com/discovery/v2/events.json
```

`HTTP 401` znaczy, że sieć działa (brak klucza w tym zapytaniu jest celowy).
Pusty wynik `getent` albo `Could not resolve host` to DNS — na WSL zdarza
się, że kontenery dostają adres serwera DNS, do którego nie mają dostępu.
Pomaga wtedy wpis w usłudze `backend` w `docker-compose.prod.yml`:

```yaml
    dns:
      - 1.1.1.1
      - 8.8.8.8
```

```bash
openssl rand -base64 48
```

---

## Gdy backend nie wstaje

### `password authentication failed for user ...`

Baza zgłasza się jako zdrowa, a backend po kilkudziesięciu sekundach pada
na uwierzytelnianiu.

Obraz Postgresa zakłada użytkownika i bazę **wyłącznie przy pierwszym
starcie na pustym katalogu danych**. Przy istniejącym po prostu ignoruje
`POSTGRES_USER` i `POSTGRES_PASSWORD`. Jeśli więc wolumen powstał wcześniej
z innymi danymi logowania, nowy użytkownik z `.env` nigdy nie powstaje.

Uruchomienie produkcyjne ma własną nazwę projektu (`name: musicclub-prod`),
więc nie dzieli wolumenu z deweloperskim. Gdyby mimo to trafić na ten błąd:

```bash
docker volume ls | grep musicclub          # zobacz, co istnieje
docker compose -f docker-compose.prod.yml down -v   # skasuj I ZACZNIJ OD ZERA
```

`down -v` kasuje **także wgrane zdjęcia** — to osobny wolumen, ale flaga
usuwa oba. Jeśli w bazie jest coś, czego nie chcesz stracić, zamiast kasować
dopasuj `POSTGRES_USER` i `POSTGRES_PASSWORD` w `.env` do tego, czym baza
została założona.

### Co jeszcze warto sprawdzić

```bash
docker compose -f docker-compose.prod.yml ps -a       # wyszedl czy zyje?
docker compose -f docker-compose.prod.yml logs backend | tail -60
```

Na WSL z repozytorium na dysku Windows (`/mnt/c/...`) warto sprawdzić końce
linii w `.env` — przy CRLF wartości dostają na końcu znak `\r`:

```bash
cat -A .env | grep POSTGRES_PASSWORD      # ^M$ na koncu = CRLF
sed -i 's/\r$//' .env                     # naprawa
```

---

## Co zostało sprawdzone, a co nie

Sprawdzone naprawdę:

- migracja V1 na **pustej** bazie → aplikacja wstaje, `validate` przechodzi,
  zakłada administratora;
- profil `prod` na **istniejącej** bazie z czasów `ddl-auto=update` →
  Flyway robi baseline, nie uruchamia migracji, aplikacja wstaje;
- `mvnw clean test` → 369 testów, Flyway w testach w ogóle nie rusza;
- składnia `nginx.conf` (parser crossplane) i obu plików compose.

Przy zakładce Wydarzenia (wrzesień 2026):

- migracja V2 na **pustej** bazie (V1 + V2) i na bazie **po V1** (samo V2)
  → w obu przypadkach `validate` przechodzi i aplikacja wstaje;
- testy wydarzeń także na prawdziwym PostgreSQL 16, nie tylko na H2 —
  zapytanie z funkcjami okna, `ESCAPE` i `LOWER` na polskich literach;
- cała droga w przeglądarce: import w tle → lista → filtr → strona
  wydarzenia, na udawanym serwerze Ticketmastera w ich formacie odpowiedzi
  i z koncertami z prawdziwego wyniku dla Polski;
- `mvnw clean test` → 414 testów.

**Nie sprawdzone stąd:** prawdziwy Ticketmaster. To środowisko nie miało
klucza. Format odpowiedzi i liczbę koncertów (801 w Polsce) potwierdziło
zapytanie z laptopa.

**Nie sprawdzone**, bo w środowisku, w którym to powstawało, nie ma
Dockera: pełne `docker compose -f docker-compose.prod.yml up`. Obrazy
i przekazywanie ruchu trzeba potwierdzić przy pierwszym uruchomieniu na
serwerze.

---

## Co dalej, żeby aplikacja trafiła na Google Play

1. **Stały adres.** Tunel testowy wystarczy do sprawdzenia PWA, ale nie do
   aplikacji: adres wchodzi do manifestu i do `assetlinks.json`.

2. **PWA na prawdziwym telefonie.** Propozycja instalacji, ikona maskowalna
   na ekranie głównym, tryb pełnoekranowy, start bez zasięgu.
   Do poprawienia po drodze: manifest ma `background_color` i `theme_color`
   tylko jasne, więc ekran startowy będzie biały także w ciemnym motywie.

3. **TWA przez Bubblewrap.**
   ```bash
   npx @bubblewrap/cli init --manifest https://twoja-domena/manifest.webmanifest
   npx @bubblewrap/cli build
   ```
   Keystore z podpisem trzeba zachować — jego utrata oznacza koniec
   aktualizacji tej aplikacji. Plik `/.well-known/assetlinks.json` musi
   wisieć pod tą samą domeną, inaczej TWA pokaże pasek adresu jak zwykła
   strona.

4. **Google Play.** Konto deweloperskie 25 USD jednorazowo, polityka
   prywatności pod publicznym adresem, formularz „Data safety", a dla
   nowych kont osobistych — testy zamknięte z 12 testerami przez 14 dni
   ciągiem, zanim w ogóle wolno złożyć wniosek o produkcję.
