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
i `V3__zapisy_i_gatunki.sql` już są, więc następna to `V4__opis_zmiany.sql`.
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

**`LASTFM_API_KEY` przydaje się tu drugi raz.** Widok „Dla ciebie" porównuje
gatunki ulubionych artystów z gatunkami wykonawców koncertów — jedne i drugie
bierze z Last.fm. Po imporcie serwer sprawdza w Last.fm do 300 nowych
wykonawców (reszta w kolejnych przebiegach) i pamięta wynik przez 60 dni. Bez
klucza „Dla ciebie" dalej działa, ale gatunki porównuje tylko z ogólnymi
etykietami Ticketmastera („Rock", „Hip-Hop/Rap").

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

#### Kraje

Każdy może w zakładce wybrać kraj (19 krajów: Europa, USA, Kanada). Wybór
jest zapisany na koncie. Serwer importuje Polskę i każdy kraj, który ktoś
wybrał — kraj wybrany po raz pierwszy pobiera się od razu w tle, a lista
przez ten czas pokazuje „Pobieramy wydarzenia”.

Żeby duże kraje (USA, Niemcy) nie zjadły dziennego limitu 5000 zapytań,
na jeden kraj w jednym przebiegu przypada najwyżej 60 zapytań
(`app.events.import.max-requests-per-country`). Gdy limit się skończy,
import kończy na tym, co zdążył pobrać — a z bazy znika tylko to, czego
nie było w **pokrytym** zakresie dat; dalsze miesiące zostają bez zmian.

### Poczta i potwierdzanie adresów e-mail

Z pocztą każde nowe konto dostaje wiadomość z linkiem i **nie zaloguje
się, dopóki go nie kliknie**. To chroni przed kontami na zmyślone adresy.
Bez `MAIL_HOST` poczta jest wyłączona, a konta działają od razu —
aplikacja jest w pełni używalna bez tego kroku.

Zasady:

- link działa 24 godziny i raz; w bazie leży tylko jego skrót SHA-256;
- ponowna wysyłka najwcześniej po minucie, najwyżej 5 na dobę na konto
  i 20 na godzinę z jednego adresu IP (żeby nie dało się nami zasypywać
  cudzych skrzynek);
- niepotwierdzone konto znika po 7 dniach — inaczej ktoś, kto zarejestrował
  cudzy adres, zablokowałby go właścicielowi na zawsze;
- zmiana adresu w ustawieniach wymaga **hasła, zgody ze starej skrzynki
  i potwierdzenia nowej** (w dowolnej kolejności); do tego czasu obowiązuje
  stary adres. Ze starej skrzynki można kliknąć „To nie ja” — zmiana
  przepada, a wszystkie urządzenia są wylogowane. Bez zgody ze starej
  skrzynki ktoś, kto przejął sesję, podmieniłby adres na swój, a potem
  resetem hasła zabrał konto;
- „Nie pamiętasz hasła?” wysyła link ważny godzinę, tylko na obecny adres
  konta. Odpowiedź jest zawsze ta sama, więc formularz nie zdradza, kto ma
  konto;
- nowe hasło (z resetu albo w ustawieniach), „To nie ja” i przycisk
  „Wyloguj z innych urządzeń” zmieniają znacznik bezpieczeństwa konta:
  inne sesje i ciasteczka „zapamiętaj mnie” przestają działać. Po zmianie
  hasła przychodzi powiadomienie z przyciskiem „To nie ja — ustaw nowe
  hasło”;
- skrzynki jednorazowe (mailinator, 10minutemail, yopmail…) są odrzucane
  (`src/main/resources/mail/disposable-domains.txt`);
- **konta założone przed włączeniem poczty nie muszą niczego potwierdzać**
  (migracja V4 i sam serwer przy starcie bez poczty uznają je za
  potwierdzone).

#### Skąd wysyłać

**Gmail** — najprościej na start, do ok. 500 wiadomości dziennie:

1. Na koncie Google włącz weryfikację dwuetapową.
2. Konto Google → Bezpieczeństwo → *Hasła aplikacji* → utwórz hasło
   (16 znaków). To nie jest hasło do Gmaila i tylko ono tu zadziała.
3. W `.env`:
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=twoj.adres@gmail.com
   MAIL_PASSWORD=<haslo aplikacji>
   MAIL_FROM=MusicClub <twoj.adres@gmail.com>
   ```

**Brevo** (dawniej Sendinblue) — darmowo 300 wiadomości dziennie, lepiej
wygląda przy własnej domenie. W panelu: *SMTP & API* → login i klucz SMTP
(`MAIL_HOST=smtp-relay.brevo.com`, `MAIL_USERNAME` = login SMTP z panelu,
`MAIL_PASSWORD` = klucz SMTP). Adres nadawcy w `MAIL_FROM` musi być
zweryfikowany w Brevo. Z własną domeną dopisz w DNS rekordy SPF i DKIM
z panelu — bez nich wiadomości częściej lądują w spamie.

Gdy login SMTP nie jest adresem (np. SendGrid używa loginu `apikey`),
`MAIL_FROM` jest obowiązkowe. Bez poprawnego nadawcy backend **nie
wystartuje** i powie dlaczego — celowo: inaczej każda wiadomość ginęłaby
po cichu, a ludzie czekaliby na nie w nieskończoność.

#### Adres w linku

```
APP_PUBLIC_URL=https://musicclub.twojadomena.pl
```

Przy własnej domenie ustaw zawsze. Przy tunelu z losowym adresem zostaw
puste — wtedy serwer bierze adres, pod którym przyszło zapytanie.

Tu jest pułapka, którą zmierzyliśmy. Spring za pośrednikiem wierzy
nagłówkom `X-Forwarded-Host`, `Forwarded` i `X-Forwarded-Prefix`, a nginx
domyślnie przepuszcza je od klienta bez zmian. Zapytanie rejestracyjne
z podrobionymi nagłówkami dawało w wiadomości link
`http://zly2.example/zly/potwierdz-email?token=…` — właściciel skrzynki
klikałby w prawdziwy token na obcej stronie. Dlatego `nginx.conf` teraz
ustawia `X-Forwarded-Host` na `$host`, a pozostałe czyści; ta sama próba
daje `https://musicclub.example.com/potwierdz-email`.

#### Gdy wiadomości nie dochodzą

```bash
docker compose -f docker-compose.prod.yml logs backend | grep Poczta
```

`Poczta: wyslano ...` — serwer SMTP przyjął wiadomość; szukaj jej w spamie.
`nie udalo sie wyslac ... Authentication failed` — złe `MAIL_USERNAME`
albo `MAIL_PASSWORD` (przy Gmailu: zwykłe hasło zamiast hasła aplikacji).
Adresy odbiorców w logu są zamaskowane (`j***i@gmail.com`).

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

Przy zapisach i widoku „Dla ciebie" (wrzesień 2026):

- migracja V3 na **pustej** bazie (V1 + V2 + V3) i na bazie **po V2** (samo V3)
  → `validate` przechodzi;
- testy wydarzeń, zapisów, dopasowania i usuwania konta na PostgreSQL 16;
- przeglądarka na trzech kontach: kolejność i powody „Dla ciebie", zapis,
  ukrycie na liście (drugie konto widzi tylko licznik), rezygnacja, „Moje",
  pusty profil; 320–390 px, jasny i ciemny motyw;
- `mvnw clean test` → 449 testów.

Przy krajach, potwierdzaniu adresów i uzupełnianiu gatunków (wrzesień 2026):

- migracja V4 na **pustej** bazie, na bazie **po V3 z danymi** (stare
  wydarzenie dostało kraj `PL`, stare konto — potwierdzenie) i po V2;
  schemat po migracjach zgadza się blok po bloku z tym, co buduje Hibernate;
- cała droga poczty na udawanym serwerze SMTP: rejestracja → wiadomość
  (HTML, wersja tekstowa, logo w środku) → logowanie odrzucone → ponowna
  wysyłka z limitem → link → logowanie; drugi raz ten sam link; zmiana
  adresu w ustawieniach; skrzynka jednorazowa;
- podrobione nagłówki przez prawdziwy nginx (wyżej, „Adres w linku”);
- `/actuator/health` = UP z pocztą i bez (Actuator sam dokładał sprawdzanie
  SMTP — przy pustym `MAIL_HOST` zdrowie wychodziłoby DOWN);
- wybór kraju w Chromium: import w tle, lista odświeżana sama, 320–1280 px;
- `mvnw clean test` → 474 testy; klasy wydarzeń, krajów, poczty i usuwania
  kont także na PostgreSQL 16.

Przy resecie hasła i zmianie adresu (wrzesień 2026):

- migracja V5 na pustej bazie i na bazie po V4; schemat zgodny z encjami;
- testy przez całe API: reset (także ciasteczko „zapamiętaj mnie” i inna
  sesja wylogowane), brak zdradzania kont, zmiana adresu z dwiema zgodami
  w dowolnej kolejności, „To nie ja”, rezygnacja bez zerowania limitu;
  także na PostgreSQL 16. Test z usuniętym filtrem znacznika czerwienieje;
- Chromium: reset od linku na logowaniu do zalogowania nowym hasłem,
  zmiana adresu z hasłem i obiema zgodami, „To nie ja” wylogowujące sesję,
  „Wyloguj z innych urządzeń”;
- `mvnw clean test` → 487 testów.

Przy blokadach i ustawieniach prywatności (wrzesień 2026):

- migracja V6 na pustej bazie i po V5; schemat zgodny z encjami;
- test przez całe API: blokada w obie strony (profil, tablica, post,
  reakcja, zaproszenia, czat, propozycje, uczestnicy), odblokowanie, profil
  tylko dla znajomych (i wgląd administratora), zasady zaproszeń, ukryta
  aktywność, domyślne ukrycie na wydarzeniach; także na PostgreSQL 16.
  Test z wyłączonym filtrem blokad na tablicy czerwienieje;
- Chromium: okno blokady, profil zablokowanej osoby, 404 u blokowanego,
  lista zablokowanych z odblokowaniem, ustawienia prywatności, profil
  tylko dla znajomych;
- `mvnw clean test` → 493 testy.

Ciasteczka „zapamiętaj mnie” wystawione przed tą wersją przestaną działać
(podpis zawiera teraz znacznik bezpieczeństwa) — każdy zaloguje się raz
jeszcze.

**Nie sprawdzone stąd:** prawdziwy Ticketmaster. To środowisko nie miało
klucza. Format odpowiedzi i liczbę koncertów (801 w Polsce) potwierdziło
zapytanie z laptopa.

**Nie sprawdzone stąd:** prawdziwa skrzynka Gmail/Brevo i to, jak
wiadomość wygląda w Gmailu i Outlooku — sprawdzony był wygląd
w przeglądarce (jasny, ciemny, 375 px) i budowa wiadomości.

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
