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

**I trzeci raz — „Kim jest?” przy wykonawcach.** Po rozwinięciu serwer pyta
Last.fm o opis wykonawcy (`artist.getInfo`) i pamięta go 30 dni. Bez klucza
przy wykonawcach są tylko gatunki i linki z Ticketmastera. Po pierwszym
uruchomieniu z kluczem warto rozwinąć „Kim jest?” przy kilku znanych
wykonawcach — kształt odpowiedzi jest przepisany z dokumentacji, nie
sprawdzony na żywym serwisie. Gdy opis się nie pokazuje, a w logu stoi
„Nie udalo sie pobrac opisu”, za nim jest przyczyna.

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

### Dodatkowe źródła koncertów (Bandsintown, Songkick)

Opcjonalne. Dokładają koncerty, których Ticketmaster nie ma (zwłaszcza mniejsze
kluby i trasy zagranicznych wykonawców), i uzupełniają braki w tych, które ma —
godzinę, miejsce na mapie, adres, bilety. Ten sam koncert z kilku źródeł to
**jedna** karta: łączymy, gdy zgadza się dzień, miejsce (ta sama sala po nazwie
w tym samym mieście albo do 500 m po współrzędnych) i wykonawca albo nazwa.
Ticketmaster zostaje głównym źródłem — reszta tylko dopisuje to, czego brakuje.

| Zmienna | Skąd | Uwagi |
|---|---|---|
| `BANDSINTOWN_APP_ID` | prośba do Bandsintown (formularz „API access” na ich stronie dla artystów i partnerów) | wydają go po pisemnej zgodzie; dane trzeba podpisać „Bandsintown” — strona wydarzenia robi to sama |
| `SONGKICK_API_KEY` | tylko w ramach partnerstwa z Songkick (płatnie) | wymagają podpisu „Songkick” — jak wyżej |

Bandsintown nie ma wyszukiwania po mieście — pytamy o koncerty 150 wykonawców
najczęściej lubianych przez ludzi w aplikacji (`app.events.bandsintown.max-artists`).
Songkick pytamy o okolice 10 największych miast Polski (`app.events.songkick.cities`,
pary „szerokość,długość” rozdzielone średnikiem). Oba raz na dobę, pierwszy raz
10 minut po starcie.

**Odpowiedzi obu serwisów są sparsowane według dokumentacji, a nie sprawdzone na
żywo** (z naszego środowiska były niedostępne, a klucze wymagają zgody). Po
wpisaniu klucza i restarcie odczekaj ~10 minut i sprawdź log:

```bash
docker compose -f docker-compose.prod.yml logs backend | grep -E "Import (Bandsintown|Songkick)|Bandsintown '|Songkick:"
```

„Import Bandsintown: N wydarzeń” znaczy, że działa. Ostrzeżenia z błędem HTTP
albo zero wydarzeń przy znanych wykonawcach = do sprawdzenia nazwy pól
w `BandsintownClient.read` / `SongkickClient.read`.

Czego **nie** podłączamy: Spotify nie udostępnia koncertów w swoim API (te w
aplikacji Spotify pochodzą od partnerów i nie są wystawione na zewnątrz), a
portali bez publicznego API (Going., eBilet i podobne) nie czytamy przez ich
wewnętrzne adresy — to łamie ich regulaminy, narusza prawo producenta bazy danych
i psułoby się przy każdej zmianie po ich stronie. Uczciwa droga to zapytać taki
portal o oficjalny dostęp (np. program partnerski albo plik z wydarzeniami).

### Opisy z Ticketmastera — próba na żywo

Ticketmaster podaje tekst w kilku polach: wydarzenie ma `info`, `description`
i `additionalInfo`, a wykonawca (na ticketmaster.pl sekcja „About” na stronie
artysty) — `description` i `additionalInfo`. Opis wydarzenia składamy ze
wszystkich trzech, a opis wykonawcy pokazujemy w „Kim jest?” (przed opisem
z Last.fm). Przychodzi z importem; gdy go tam nie ma, „Kim jest?” pyta
Ticketmastera o tego jednego wykonawcę i zapamiętuje odpowiedź na 30 dni.

**Sprawdzone na żywo (7.10.2026, Kovacs, id wykonawcy `K8vZ9173gz7`):** lista wydarzeń
(`events.json`) **nie oddaje opisu wykonawcy** — `description` i `additionalInfo`
wykonawcy są `null`, mimo że ticketmaster.pl pokazuje mu sekcję „About”. Pole `info`
wydarzenia to sama formułka sprzedażowa („Service fee depends on the ticket prices…”),
a `description` i `additionalInfo` wydarzenia też `null`. Czyli opisu koncertu Ticketmaster
tu po prostu nie ma — to nie jest błąd po naszej stronie.

Nierozstrzygnięte zostaje, czy opis da się wziąć pytaniem o samego wykonawcę
(`attractions/{id}.json`), także z treściami licencjonowanymi. Sprawdź to na prawdziwym
numerze wykonawcy (w pierwszej próbie został literalny `ID`, więc odpowiedź była pusta):

```bash
K=twoj-klucz-ticketmastera          # klucza nie wklejaj do czatu ani do repozytorium
ID=K8vZ9173gz7                      # Kovacs; inny wykonawca - jego id z punktu 1
BAZA=https://app.ticketmaster.com/discovery/v2
# 1. Co lista wydarzeń mówi o wykonawcach (id, opisy)
curl -s "$BAZA/events.json?apikey=$K&countryCode=PL&keyword=Kovacs" \
  | jq '._embedded.events[0] | {name, info, description, additionalInfo,
        wykonawcy: [._embedded.attractions[] | {id, name, locale, description, additionalInfo}]}'
# 2. Wykonawca pytany wprost (tak pyta „Kim jest?”): jakie pola w ogóle ma i jakie linki
curl -s "$BAZA/attractions/$ID.json?apikey=$K" \
  | jq '{pola: keys, name, locale, description, additionalInfo, url, linki: ((.externalLinks // {}) | keys)}'
# 3. To samo z treściami licencjonowanymi - tylko dla porównania
curl -s "$BAZA/attractions/$ID.json?apikey=$K&includeLicensedContent=yes" \
  | jq '{description, additionalInfo, pola: keys}'
```

- Tekst jest w 2. — działa przez „Kim jest?” (jedno zapytanie na wykonawcę na 30 dni;
  dzienny limit 5000 starcza z zapasem).
- Tekst jest **tylko** w 3. — to treść licencjonowana (np. biografia od zewnętrznego
  dostawcy). Celowo o nią nie prosimy, bo ma własne warunki wyświetlania. Przed włączeniem
  trzeba przeczytać warunki Ticketmastera dla takich treści i dopisać wymagane przypisanie.
- Tekstu nie ma nigdzie (2. i 3. puste przy prawdziwym `$ID`) — „About” z ticketmaster.pl
  nie jest dostępne przez publiczne API. Zostaje opis z Last.fm; wtedy zapytanie o
  pojedynczego wykonawcę nic nie daje i można je usunąć (`ArtistProfileService.aboutTicketmastera`),
  a czytanie opisu z importu zostawić — nic nie kosztuje.

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

### Regulamin i polityka prywatności

Aplikacja ma strony `/regulamin` i `/polityka-prywatnosci` (PL i EN), link
w stopce, zgodę przy rejestracji i baner ponownej akceptacji dla kont
starszych albo po zmianie treści. Tekst to **szablon**, który odzwierciedla to,
co aplikacja naprawdę robi z danymi — **przed wpuszczeniem prawdziwych
użytkowników niech go przeczyta prawnik**. Google Play wymaga polityki
prywatności pod publicznym adresem (`https://twoja-domena/polityka-prywatnosci`).

Dane administratora nie leżą w repozytorium — wpisujesz je w `.env`:

```
LEGAL_CONTROLLER=Imię i nazwisko albo nazwa firmy, adres
LEGAL_CONTACT_EMAIL=adres, na który użytkownicy piszą w sprawie danych
LEGAL_HOSTING=kto prowadzi serwer (np. nazwa hostingu)
LEGAL_MAIL_PROVIDER=przez kogo idzie poczta (np. Google Gmail, Brevo)
```

Puste wartości nie zatrzymują serwera, ale strona pokazuje w ich miejsce
„[do uzupełnienia…]” z ostrzeżeniem, a log przy starcie przypomina o
uzupełnieniu. Zmiana danych nie wymaga przebudowy aplikacji — wystarczy
restart backendu.

Zmiana **treści** dokumentów (pliki `frontend/src/legal/`) wymaga podniesienia
`app.legal.version` w `application.properties` na dzisiejszą datę — dopiero
wtedy każdy zalogowany dostanie prośbę o ponowną akceptację. Logi kontenerów
mają rotację (3 pliki po 10 MB na usługę), bo zawierają adresy IP, a polityka
obiecuje krótkie przechowywanie.

### GIF-y w komentarzach i na czacie (KLIPY albo GIPHY)

Opcjonalne: bez klucza przycisk **GIF** w ogóle się nie pokazuje, a reszta aplikacji działa jak dotąd.

1. **Klucz.** Tenor wyłączył swoje API 30.06.2026, więc domyślnym dostawcą jest **KLIPY** (darmowy, zgodny
   z Tenorem): konto na `partner.klipy.com` → *API Keys* → *Add Platform* → klucz. Alternatywa to **GIPHY**
   (`developers.giphy.com` → *Create an App* → klucz; do publicznej aplikacji dostawca wymaga zatwierdzenia
   klucza produkcyjnego — sprawdź jego aktualne warunki).
2. **`.env`:**
   ```
   GIF_PROVIDER=klipy     # albo giphy
   GIF_API_KEY=twoj-klucz
   ```
   i uruchom backend ponownie. W logu przy starcie nie ma już linijki „GIF-y wyłączone”.
3. **Sprawdź na żywo, zanim ktoś tego użyje.** Kształt odpowiedzi KLIPY jest w kodzie odtworzony z dokumentacji, a nie
   sprawdzony na prawdziwym serwisie (z środowiska, w którym to powstało, dostawcy są zablokowani). Próba:
   ```bash
   curl -s "https://api.klipy.com/api/v1/$GIF_API_KEY/gifs/search?q=kot&per_page=8&customer_id=test" | head -c 1500
   ```
   W odpowiedzi szukaj `data.data[]`, a w nich `file.md.gif.url` (albo `sm`, `hd`, `xs`). Potem w aplikacji: komentarz →
   przycisk **GIF** → powinny pojawić się kafelki. Pusta lista i w logu backendu ostrzeżenie
   `KLIPY: nie rozpoznano odpowiedzi` albo `wyników bez użytecznego pliku GIF` znaczy, że nazwy pól się
   różnią — poprawka to kilka linijek w `KlipyProvider.read` / `item` (test `GifProvidersTest` ma przykładową
   odpowiedź do podmiany na prawdziwą).
4. **Podpis dostawcy.** Pod przeglądarką GIF-ów stoi tekst „Powered by KLIPY” (albo GIPHY). Regulaminy obu dostawców
   wymagają wskazania ich marki; przed publikacją w Google Play warto podmienić tekst na oficjalne logo
   (materiały do pobrania u dostawcy).
5. **Prywatność i Google Play.** Dostawca dostaje wpisane frazy i pseudonimowy identyfikator (przez nasz serwer) oraz
   adres IP przeglądarki przy pobieraniu plików GIF. Polityka prywatności to opisuje (sekcje 2, 5, 6, 8); w formularzu
   *Data safety* trzeba to samo wpisać jako dane przekazywane stronie trzeciej.
6. **Koszt i limity.** Wyszukiwanie ma limit 30 na minutę na osobę i pamięć podręczną na 5 minut
   (`app.gifs.searches-per-minute`, `app.gifs.cache-seconds`), więc jeden użytkownik nie wyczerpie limitu klucza.

### Karta profilu, zdjęcia i tryb Poznawaj

Nic do ustawiania — działa od razu. Kilka rzeczy, o których warto wiedzieć przed wpuszczeniem ludzi:

1. **Zdjęcia bez GPS.** Każde wgrane zdjęcie (galeria karty, awatar, zdjęcia w postach) przechodzi przez
   `ImageMetadata`: z JPEG-ów znika EXIF (współrzędne GPS, model telefonu, data), XMP, IPTC i komentarze, z PNG
   fragmenty tekstowe i `eXIf`. Zostaje tylko obrót zdjęcia (jako nowy, minimalny EXIF) i profil barw ICC. Zdjęcia
   zrobione przed tą zmianą **mają EXIF nadal** — jeśli na serwerze są już prawdziwe zdjęcia, warto je raz przepuścić
   przez narzędzie typu `exiftool -all= -tagsfromfile @ -Orientation` w katalogu `uploads`.
2. **Poznawaj jest dobrowolny i wzajemny.** Domyślnie wyłączony; karty oglądają tylko osoby, które same go włączyły.
   Wzajemne „tak” = znajomość od razu (powiadomienie i push dla obu). Limit 300 decyzji na dobę na osobę
   (`app.discover.swipes-per-day`), wygasłe decyzje sprząta zadanie o 4:40 (`app.discover.cleanup-cron`).
3. **Moderacja.** Zgłoszenie profilu zapisuje migawkę karty (opis, „Szukam”, pytania, adresy zdjęć). W panelu zgłoszeń
   jest decyzja „wyczyść kartę profilu” — usuwa zdjęcia z galerii (także pliki), opis i pytania i wyłącza Poznawaj.
   Zakaz publikowania obejmuje kartę: z zakazem nie da się jej zmienić ani dodać zdjęcia, a osoba znika z talii.
4. **Google Play.** Galeria zdjęć i tryb poznawania ludzi to w formularzu *Data safety* „zdjęcia” i „treści tworzone
   przez użytkownika” widoczne dla innych; w ankiecie treści (IARC) zaznacza się kontakt między użytkownikami.
   Aplikacja do poznawania ludzi musi mieć zgłaszanie i blokowanie z poziomu karty — są (pod opisem na karcie).
   Przy kategorii w sklepie: to aplikacja społecznościowa do poznawania ludzi, **nie randkowa** — w interfejsie nie ma
   serduszek (przycisk „tak” to plusik, przy nowej znajomości — ikona dwóch obejmujących się osób).
5. **Edytor zdjęć** (kadr i obrót) działa w całości w przeglądarce — serwer dostaje zwykły plik JPEG/WebP/PNG/GIF jak
   wcześniej i niczego nie trzeba ustawiać. Po przycięciu dłuższy bok ma najwyżej 2048 px.

### Powiadomienia push i przypomnienia o wydarzeniach

Przypomnienia („za 3 dni”, „jutro”) działają zawsze — w dzwonku. Żeby
przychodziły też na telefon (także przy zamkniętej aplikacji), serwer
potrzebuje pary kluczy VAPID. Generuje się ją **raz**; po zmianie kluczy
każdy musi włączyć powiadomienia od nowa.

```bash
npx web-push generate-vapid-keys
```

Bez Node wystarczy `openssl` (sprawdzone: klucz publiczny zgadza się
z prywatnym, a serwer z nimi wstaje):

```bash
openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
openssl ec -in vapid.pem -pubout -outform DER | tail -c 65 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
openssl ec -in vapid.pem -outform DER | tail -c +8 | head -c 32 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
rm vapid.pem
```

Pierwsza linia wyniku to `VAPID_PUBLIC_KEY`, druga — `VAPID_PRIVATE_KEY`
(tajny jak hasło, tylko w `.env`). Do tego kontakt dla usług push:

```
VAPID_SUBJECT=mailto:twoj@adres.pl
```

Pusty `VAPID_SUBJECT` = `APP_PUBLIC_URL`, o ile zaczyna się od `https://`.
Jeden klucz bez drugiego, klucze z dwóch różnych generowań albo brak
kontaktu — backend nie wstaje i mówi w logu, czego brakuje. Inaczej
wszystko wyglądałoby na działające, a usługa push po cichu odrzucałaby
każdą wiadomość.

Jak to działa:

- Przeglądarka dostaje adres od swojej usługi push (Google, Mozilla,
  Apple, Microsoft) i dwa klucze. Serwer szyfruje treść tymi kluczami
  (RFC 8291) i podpisuje wysyłkę kluczem VAPID (RFC 8292) — usługa push
  przenosi wiadomość, ale jej nie przeczyta. Szyfrowanie jest napisane na
  samej kryptografii JDK, bez dodatkowych bibliotek, i sprawdzone bajt
  w bajt z biblioteką referencyjną `http_ece`.
- Serwer wysyła **tylko** do usług push z listy (`fcm.googleapis.com`,
  `updates.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`).
  Adres podaje przeglądarka, więc bez listy każdy mógłby kazać serwerowi
  wysyłać zapytania w dowolne miejsce, także do usług w sieci wewnętrznej.
- Na telefon idą: przypomnienia o wydarzeniach, zaproszenia do znajomych,
  przyjęte zaproszenia, a administratorom — nowe zgłoszenia. Reakcje nie
  (przy popularnym poście telefon brzęczałby co chwilę).
- Przypomnienia sprawdzane są co godzinę od 9 do 21 czasu polskiego —
  telefon nie zadzwoni w nocy.
- Wylogowanie wyłącza powiadomienia na tym urządzeniu (telefon może
  przejść w inne ręce). Zmiana hasła, reset i „wyloguj z innych urządzeń”
  wyłączają je na wszystkich innych — zgubiony telefon przestaje dostawać
  powiadomienia.
- iPhone: Web Push działa tylko po dodaniu strony do ekranu głównego
  (iOS 16.4+). Android i TWA z Google Play — bez ograniczeń.

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

Przy postach pod wydarzeniem (wrzesień 2026):

- migracja V7 na pustej bazie i po V6; schemat zgodny z encjami;
- na PostgreSQL: usunięcie wydarzenia zostawia post z pustym `event_id`
  (`ON DELETE SET NULL`), import wycofuje zamiast kasować wydarzenie
  z postami; testy wyłapują wyłączenie każdego z tych dwóch zabezpieczeń
  i filtra blokad pod wydarzeniem;
- Chromium: pusta sekcja, post publiczny i „tylko znajomi” (obcy widzi
  tylko publiczny), plakietka na tablicy z obciętą nazwą i przejściem do
  wydarzenia, usuwanie; 320 px w ciemnym motywie bez przelewu;
- `mvnw clean test` → 498 testów.

Przy przypomnieniach i powiadomieniach push (wrzesień 2026):

- szyfrowanie treści porównane bajt w bajt z biblioteką referencyjną
  `http_ece` (te same klucze i sól → te same bajty); podpis VAPID
  weryfikowany niezależnie w Node;
- migracja V8 na pustej bazie i po V7; schemat zgodny z encjami;
- test przez całe API z udawaną usługą push: przypomnienia „za 3 dni”
  i „jutro” raz na próg, bez wycofanych i dla osób z wyłączonymi
  przypomnieniami, w dzwonku i na telefonie (odszyfrowane i sprawdzone),
  410 usuwa martwy adres, po „wyloguj z innych urządzeń” stare urządzenie
  nie dostaje nic, adresy spoza usług push odrzucone. Testy wyłapują
  wyłączenie każdego z tych zabezpieczeń;
- znalezione przy sprawdzaniu: konta sprzed V5 mają pusty znacznik
  bezpieczeństwa (subskrypcja się nie zapisywała), a lista powiadomień
  łączyła się ze sprawcą złączeniem wewnętrznym (przypomnienia bez sprawcy
  znikałyby z dzwonka) — oba z testem, który najpierw czerwieniał;
- Chromium: włączenie w ustawieniach, próbne powiadomienie przez udawaną
  usługę push → odszyfrowanie biblioteką referencyjną → prawdziwy service
  worker → powiadomienie systemowe; przypomnienie z bazy po przebiegu,
  dzwonek, kliknięcie w powiadomienie otwiera stronę wydarzenia,
  wyłączenie i wylogowanie usuwają subskrypcję;
- start produkcyjny z kluczami z `openssl`, z jednym kluczem i bez
  kontaktu (dwa ostatnie — backend nie wstaje, z podpowiedzią).

**Nie sprawdzone stąd:** prawdziwe usługi push (Google, Apple) — to
środowisko nie ma do nich dostępu. Format wiadomości i podpisu jest ten
sam, który przyjmują (sprawdzony biblioteką, której używa pakiet
`web-push`), ale pierwsze powiadomienie na prawdziwym telefonie trzeba
zobaczyć po wdrożeniu.

Przy klanach (wrzesień 2026):

- migracja V9 na pustej bazie i po V8; schemat zgodny z encjami blok po bloku;
- test przez całe API (11 scenariuszy): zakładanie (nazwa i skrót
  unikalne bez względu na litery i znaki, zastrzeżone słowa), jedyna droga do
  klanu — zaproszenie, blokady/„nikt”/„tylko znajomi”/odmowa, role, głosowanie
  na kolor z remisem, posty klanu (nie ma ich na tablicy, profilu ani w
  licznikach; obcy dostaje 404), czat, rozwiązanie klanu ze wszystkim, co w nim
  jest, usunięcie konta założyciela i ostatniego członka. Testy wyłapują
  wyłączenie sprawdzenia członkostwa, filtra tablicy, zakazu zapraszania osób
  z klanu, drugiego przyjęcia zaproszenia i kasowania reakcji;
- Chromium na czterech kontach: założenie z ikoną i zdjęciem, głosowanie na
  kolor (kolor strony i plakietki zmienia się od razu), zaproszenie →
  powiadomienie → przyjęcie, czat na żywo między dwoma kontami bez odświeżania,
  post klanu (widzi klan i administrator, tablica nie), plakietka przy
  zwykłym poście i na profilu, obca osoba (bez czatu i postów, API 409),
  administrator (baner, czat i posty do odczytu, bez pisania), wyrzucenie z
  powiadomieniem; 320 px w ciemnym motywie bez przelewu;
- `mvnw clean test` → 546 testów; klasy klanów, blokad, usuwania kont i powiadomień także na PostgreSQL 16.

Przy regulaminie i polityce prywatności (wrzesień 2026):

- migracja V10 na pustej bazie i po V9; schemat zgodny z encjami;
- testy: rejestracja bez zgody (brak pola i `false`) → 422 z polem
  `acceptTerms`, ze zgodą — zapisana wersja i moment; konto bez akceptacji i ze
  starą wersją dostaje `current=false` i może zaakceptować; dane administratora
  i dostawcy idą z konfiguracji; test z wyciętym `@AssertTrue` czerwienieje;
- Chromium: pole zgody z linkami w nowej karcie, błąd bez zgody (konto się nie
  zakłada), konto ze zgodą, strony dokumentów z danymi z serwera (administrator,
  kontakt, hosting, poczta — żadnych nierozwiniętych `{{`), spis treści, stopka,
  wersja angielska przy 320 px w ciemnym motywie bez przelewu, baner dla konta
  bez akceptacji i jego zniknięcie po kliknięciu (także po odświeżeniu);
- start produkcyjny bez `LEGAL_*` loguje ostrzeżenia, ale wstaje.

Przy rozszerzeniach klanów (wrzesień 2026, migracja V11):

- migracja V11 na **pustej** bazie (V1 → V11, profil `prod`: Flyway + `validate`)
  i na bazie **po V10 z danymi** (klan, dwóch członków, trzy wiadomości):
  istniejący członkowie dostają `chat_read_id` = ostatnia wiadomość klanu,
  więc po wdrożeniu nikt nie widzi całej historii jako „nowej”. Schemat po
  migracjach porównany blok po bloku ze schematem Hibernate (tabele, kolumny,
  indeksy, klucze obce z `ON DELETE`) — bez różnic poza kolejnością kolumn
  i zapisem `CHECK`;
- `mvnw clean test` → 560 testów (14 nowych w `ClanExtrasFlowTest`, plus
  rozszerzony eksport danych); testy klanów przechodzą też na PostgreSQL 16;
- testy z mutacjami — wyłączenie każdej z poniższych ochron czerwieni test:
  filtr wyciszonych, ograniczenie „jedno powiadomienie do przeczytania”,
  pomijanie osób z blokad przy push i przy liczniku, znacznik „przeczytane do”
  tylko do przodu i przycięty do ostatniej wiadomości, cytowanie tylko
  wiadomości tego samego klanu i widocznych, reakcje tylko na wiadomości tego
  klanu i bez osób z blokad, odrzucanie kontekstu `CLAN` na zwykłej ścieżce
  zgłoszeń, zgłoszenie własnego klanu, zasady i ogłoszenie dla obcych,
  zmiana ogłoszenia tylko przez zarząd, wykluczenie profili „tylko znajomi”
  z gustu klanu, limit propozycji, duplikaty, zamknięcie głosowania po
  tygodniu, ukryci na liście koncertów, osoby z blokad w zapisach,
  sprzątanie propozycji i głosów przy rozwiązaniu klanu. Jedyny mutant, który
  przeżył (`counted < 1` zamiast `< 2` w `ClanMusicService.taste`), jest
  równoważny — `HAVING` w zapytaniu i tak odrzuca pojedyncze osoby;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V11, `ddl-auto=validate`;
  78 sprawdzeń, w tym 390 i 320 px, jasny i ciemny motyw): licznik w menu
  (kropka przy awatarze i liczba przy „Mój klan”), kreska „Nowe wiadomości”
  nad pierwszą nieprzeczytaną, oznaczanie przeczytanych tylko gdy czat jest
  na ekranie, odpowiedź z cytatem i przejściem do oryginału, reakcje (własna,
  cudza pojawiająca się przez odpytywanie, cofnięcie), wyciszenie, ogłoszenie
  i zasady („Rozumiem” zwija, zmiana zasad rozwija znowu, obca osoba nic nie
  widzi, zaproszony widzi zasady przed przyjęciem), gust klanu (wspólni
  wykonawcy i gatunki, bez nazw osób), utwór tygodnia (zły link zablokowany,
  propozycja, głos, korona prowadzącego, odtwarzacz dopiero po kliknięciu),
  koncerty (ukryci tylko w liczniku, „Zapytaj klan” tworzy post klanu, którego
  nie ma pod wydarzeniem dla wszystkich), zgłoszenie klanu i panel
  administratora, brak przelewu strony (mierzone w oknie o sztywnej szerokości);
- service worker w prawdziwym Chromium (`ServiceWorker.deliverPushMessage`):
  powiadomienie z czatu klanu **nie** pokazuje się przy widocznej aplikacji,
  pokazuje się, gdy aplikacji nie ma na ekranie, a inne powiadomienia
  (zaproszenia) pokazują się zawsze. Mutacje po stronie przeglądarki:
  wyłączenie sprawdzania widoczności i wyłączenie oznaczania czatu jako
  przeczytanego czerwienią odpowiednie sprawdzenia.

**Nie sprawdzone stąd:** wysyłka push z czatu do prawdziwej usługi (Google,
Mozilla) — tu jest tylko kontrakt po stronie serwera (kto dostaje, kiedy,
z jaką treścią) i przyjęcie powiadomienia przez service worker; oraz
odtwarzacze Spotify/YouTube (środowisko nie ma dostępu do tych serwisów —
ramka jest, ale się nie ładuje, a tytuł i okładka nie są pobierane).

Przy społeczności klanów (październik 2026, migracja V12):

- migracja V12 na **pustej** bazie (V1 → V12, profil `prod`: Flyway + `validate`)
  i na bazie **po V11 z danymi** (klan z członkiem): istniejący klan zostaje
  `INVITE_ONLY` i dostaje `listed=false` — nikt nie zgodził się na przeglądarkę.
  Schemat po migracjach porównany blok po bloku ze schematem Hibernate
  (tabele, kolumny, indeksy, klucze obce z `ON DELETE`) — bez różnic poza
  kolejnością kolumn;
- `mvnw clean test` → 576 testów (16 nowych w `ClanCommunityFlowTest`, plus
  rozszerzone eksport danych i gust klanu); klasy klanów, eksportu, usuwania
  kont, blokad i powiadomień (78 testów) przechodzą też na PostgreSQL 16;
- testy z mutacjami — wyłączenie każdej z poniższych ochron czerwieni test
  (24 z 25): sprawdzenie polityki naboru, blokada założyciela przy prośbie,
  tydzień po odmowie, limit oczekujących próśb, prośby osób z blokad na liście
  zarządu, kasowanie prośb przy wejściu do klanu, wiek wygasania próśb,
  małe litery gatunków, klan blokującego w przeglądarce, filtr „mogę
  dołączyć”, tylko profile `EVERYONE` w gustach, rekonesans tylko dla klanów
  z przeglądarki, limit tytułów wziętych samemu, brak nadawania tytułów
  automatycznych, oddawanie tylko własnego tytułu, sprzątanie tytułów przy
  odejściu, zamknięta ankieta, limit ankiet na osobę, punkty za post, próg
  poziomu, osoby z blokad w rankingu, sprzątanie ankiet przy rozwiązaniu klanu,
  sortowanie „najnowsze”, duplikaty odpowiedzi w ankiecie. Jedyny mutant, który
  przeżył (usunięcie `memberTitles.deleteByUserId` z `ClanCleanup.ofUser`),
  jest równoważny — członkostwo i tak sprząta tytuły;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V12,
  `ddl-auto=validate`; 129 sprawdzeń, w tym 390 i 320 px, jasny i ciemny
  motyw): przeglądarka klanów (12 + „Pokaż więcej”, filtr gatunku z licznikami,
  miasta, szukanie z opóźnieniem, wszystkie sortowania, „tylko takie, do
  których mogę dołączyć”, pusty stan, filtry w adresie i powrót „wstecz”,
  klan z bardzo długimi tekstami), strona obcej osoby (tylko „O klanie”
  i „Członkowie”, gust zbiorczy, API czatu, ankiet, rankingu i gustu klanu
  ukrytego = 409), prośba → powiadomienie → przyjęcie / odmowa (bez
  powiadomienia, „Moje prośby”), zakładanie klanu z ikoną i **podglądem
  plakietki** (blob, potem adres z serwera), gatunki (Enter nie wysyła
  formularza, limit trzech), tytuły (szablony, wzięcie samemu, nadanie przez
  zarząd, automatyczny po pierwszej wiadomości bez krzyżyka, usunięcie),
  ankiety (głos, zmiana, cofnięcie, zamknięcie, usunięcie), ranking
  (poziom 1 po 31 wiadomościach, cel tygodnia), ustawienia wizytówki
  (ukrycie klanu usuwa go z przeglądarki, klan „tylko zaproszenia” odrzuca
  prośby); brak przelewu strony mierzony w oknie o sztywnej szerokości.
  Oglądanie zrzutów wyłapało jedno, czego liczby nie pokazały: przyciski
  w wierszu członka ściskały nazwę do jednej litery przy 390 px — teraz
  przechodzą pod nazwę.

Przy lokalizacji (październik 2026, migracja V13):

- migracja V13 na **pustej** bazie (V1 → V13, profil `prod`: Flyway + `validate`)
  i na bazie **po V12 z danymi** (3 konta, klan): konta zostają bez miasta,
  `show_city = true`;
- `mvnw clean test` → 599 testów (23 nowe: `CityIndexTest`, `LocationScoreTest`,
  `LocationFlowTest`); `LocationFlowTest` przechodzi też na PostgreSQL 16 —
  zapytanie o propozycje liczy odległość w SQL (`ACOS`, `LEAST/GREATEST`), a test
  `sqlAndJavaAgree` porównuje kolejność z poziomami liczonymi w Javie;
- lista miast: test pilnuje, że każde z 256 miast leży w Polsce i ma sąsiada
  w 60 km (literówka we współrzędnych) oraz odległości znanych par. Jedno
  oczekiwanie w teście było błędne, nie dane: Poznań–Wrocław w linii prostej
  to ok. 145 km (166 to było z pamięci, bliżej szosy);
- testy z mutacjami — wyłączenie każdej z poniższych rzeczy czerwieni test
  (18 z 18): granica „to samo miasto”, próg w SQL, skrót „ten sam klucz miasta”,
  wydarzenia o nieznanym położeniu w promieniu, bonus za bliskość w „Dla ciebie”,
  promień na liście „Najbliższe”, filtr promienia klanów, kolejność „najlepiej
  pasujące” z bliskością i „od najbliższego”, podpis tylko dla osób, które miasto
  pokazują (w Javie i w SQL), miasto na profilu a `show_city`, zapamiętanie
  współrzędnych, wzorzec nazwy miasta, odległość na stronie wydarzenia,
  licznik pominiętych klanów. Pierwszy przebieg zostawił jednego przeżywającego
  (bonus za bliskość): w teście najbliższe koncerty miały też najwcześniejsze
  daty, więc nie było widać, czy bonus działa — daty są teraz odwrotne do
  odległości. Skrypt mutacji musi używać `clean`, inaczej po ostatnim mutancie
  zostaje w `target/classes` jego skompilowana wersja i zielony test okazuje się
  czerwony;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V13, `ddl-auto=validate`;
  47 sprawdzeń, w tym 390, 320 i 1100 px, jasny i ciemny motyw): zachęta
  „Ustaw swoje miasto” (wydarzenia, klany, znajomi) i przejście do karty
  w ustawieniach, podpowiedzi miast, zapis miasta z listy i spoza niej, zły
  znak, zasięg 30/50/100 km/cały kraj (domyślnie 100, pamiętany, wspólny dla
  wydarzeń i klanów), odległości na kartach, pusty zasięg z „Pokaż cały kraj”,
  „Dla ciebie” z bliskimi wyżej mimo późniejszych dat, klany „od najbliższego”
  i liczba pominiętych klanów bez miasta, podpisy w propozycjach („Z twojego
  miasta”, „Z okolicy: …”), ukrycie miasta na profilu i w propozycjach, filtry
  w jednym wierszu na szerokim ekranie, brak przelewu strony. Oglądanie
  zrzutów wyłapało dwie rzeczy, których liczby nie pokazały: opcja zasięgu
  z nazwą miasta („Do 100 km (Po…”) była ucięta — miasto jest teraz w osobnej
  linijce pod filtrami — oraz odnośnik „Ustaw swoje miasto” przewijał kartę pod
  przyklejony pasek (i najpierw w ogóle nie przewijał, bo Layout przewija na
  górę po efektach dziecka).

**Nie sprawdzone stąd:** czy lista 256 miast wystarcza prawdziwym użytkownikom.
Miasta spoza listy działają tylko jako „to samo miasto” — jeśli ktoś pisze, że
jego miejscowości brakuje, dopisuje się ją do `geo/miasta.csv` (współrzędne
dopisują się do kont po ponownym zapisaniu miasta).

Przy pobieraniu własnych danych (wrzesień 2026):

- test przez całe API: bez hasła, z pustym i błędnym → 422, bez tokenu CSRF
  403; archiwum zawiera własne konto, posty (także klanu) z prawdziwym
  zdjęciem (bajt w bajt), wiadomości wysłane i otrzymane, znajomych,
  reakcje, klan i własne wiadomości na jego czacie; nie zawiera cudzych
  postów, rozmowy skasowanej u siebie, hasła, cudzych e-maili ani nazwy osoby,
  która zgłosiła konto; drugie pobranie w ciągu minuty → 429. Testy wyłapują
  wyłączenie sprawdzania hasła, limitu, filtra skasowanych rozmów i wyciek
  skrótu hasła;
- Chromium: przycisk nieaktywny bez hasła, błędne hasło, pobranie pliku
  `musicclub-dane-<login>-<data>.zip` (rozpakowany i odczytany), drugie
  pobranie w ciągu minuty z komunikatem, 390 px bez przelewu.

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

Przy tablicy „Dla ciebie” i komentarzach (październik 2026, migracja V14):

- migracja V14 na **pustej** bazie (V1 → V14, profil `prod`: Flyway + `validate`)
  i na bazie **po V13 z danymi** (2 posty, 2 powiadomienia, 2 zgłoszenia): stare
  wiersze zostają, nowe typy powiadomień (`POST_COMMENT`, `COMMENT_REPLY`,
  `COMMENT_MENTION`) i kontekst zgłoszenia `COMMENT` przechodzą przez odtworzone
  CHECK-i, a skasowanie komentarza kasuje jego powiadomienia i zeruje
  `reports.comment_id`;
- `mvnw clean test` → 654 testy; klasy tablicy i komentarzy (`FeedRankerTest`,
  `FeedRankingFlowTest`, `FeedPaginationTest`, `CommentFlowTest`,
  `CommentMentionsTest`, `DataExportFlowTest`) przechodzą także na PostgreSQL 16;
- testy z mutacjami. Tablica: 21 mutantów, 20 zabitych, jeden równoważny
  (rozstrzyganie remisów w ocenie). Komentarze: 40 mutantów — pierwszy przebieg
  zostawił 9 żywych i wszystkie to były luki w testach, nie w kodzie: kasowanie
  przez zarząd klanu, trzy filtry podpowiedzi do oznaczania (widoczność posta,
  blokady, limit 8), blokada autora komentarza nadrzędnego, samooznaczenie, limit
  rozmiaru strony; dwa kolejne były martwym warunkiem (usunięty) i równoważną
  gałęzią. Mutanta zmieniającego stałą `NA_MINUTE` nie da się zrobić — test limitu
  pętli się po tej samej stałej i mieli się bez końca; zmienia się porównanie;
- znalezione przy sprawdzaniu, z testem, który najpierw czerwieniał: kolejność
  podpowiedzi osób piszących pod postem była przypadkowa (zapytanie `DISTINCT` bez
  `ORDER BY`), a przeglądarka nie robiła odnośnika z oznaczenia z kropką na końcu
  („dzięki @ala.”), choć serwer je zapisywał;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V14, 29 sprawdzeń komentarzy
  i regresja tablicy i lokalizacji): podpowiedzi po `@`, wstawianie klawiszem,
  odnośnik do profilu, kropka po oznaczeniu, HTML i adres e-mail jako zwykły tekst
  (bez elementów w DOM), limit 1000 znaków, 400 znaków bez spacji bez przelewu przy
  390 i 320 px, powiadomienia (bez rodzaju gramatycznego) i przejście do
  podświetlonego komentarza, odpowiedzi i odpowiedź na odpowiedź (jeden poziom),
  zgłoszenie z `comment_id`, kasowanie z kaskadą, ciemny motyw. Skrypt trzeba
  puszczać na świeżej bazie — konta z poprzedniego skryptu zmieniają listy propozycji.

Przy GIF-ach w komentarzach i na czacie (październik 2026, migracja V15):

- migracja V15 na **pustej** bazie (V1 → V15, profil `prod`: Flyway + `validate`) i na bazie **po V14 z danymi**:
  dodaje po pięć opcjonalnych kolumn `gif_*` do `comments` i `messages`; schemat po migracjach zgadza się
  z tym, co buduje Hibernate (jedyne różnice to te dziesięć kolumn);
- `mvnw clean test` → 695 testów; klasy GIF-ów, komentarzy, eksportu i raportów przechodzą także na PostgreSQL 16;
- **czego nie sprawdzono:** żadnego prawdziwego dostawcy GIF-ów (z tego środowiska KLIPY i GIPHY są zablokowane).
  Oba adaptery działają na udawanym serwerze z odpowiedzią odtworzoną z dokumentacji (KLIPY) i z pamięci (GIPHY);
  próba na żywo jest w sekcji „GIF-y” wyżej i trzeba ją zrobić po wpisaniu klucza;
- testy z mutacjami: 34 mutanty (podpis nieprzyjmowany, adres z podglądu zamiast z pliku, dowolny schemat adresu,
  pamięć podręczna, limity, klucze pamięci, kolejność rozmiarów KLIPY, paginacja KLIPY i GIPHY, GIF pomijany w komentarzu,
  wiadomości, walidatorze, dowodzie, eksporcie i odpowiedzi, kod błędu, limit rozmiaru strony, wyłączenie) — wszystkie
  zabite. Pierwszy przebieg zostawił jednego żywego: obcinanie białych znaków wokół tokenu nie było w ogóle testowane;
- znalezione przy sprawdzaniu: test wyszukiwania z `%20` w adresie przechodził przez MockMvc jako `%2520` (znana pułapka),
  a pomocnik `TestHttpServer` nie pozwalał podmienić odpowiedzi ustawionej wcześniej funkcją;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V15, udawany KLIPY na `:9099` z prawdziwymi plikami GIF):
  przycisk GIF tylko przy włączonej usłudze, popularne, „Pokaż więcej”, szukanie z opóźnieniem, brak wyników,
  awaria dostawcy z „Spróbuj ponownie”, Enter w szukaniu nie wysyła komentarza, wybór i usunięcie GIF-a z pola,
  komentarz z samym GIF-em, z tekstem i odpowiedź GIF-em (obrazek faktycznie się ładuje, `naturalWidth > 1`), czat
  (GIF sam i z tekstem, drugi użytkownik widzi oba, podgląd „GIF” na liście rozmów), brak przelewu strony przy 390 i
  320 px, 3 kolumny kafelków na szerokim ekranie, Esc, ciemny motyw. Skrypt trzeba puszczać na świeżej bazie.

Przy karcie profilu, trybie Poznawaj i GIF-ach w czacie klanu (październik 2026, migracje V16 i V17):

- migracje V16 i V17 na **pustej** bazie (V1 → V17, profil `prod`: Flyway + `validate`): V16 dodaje pięć kolumn `gif_*`
  do `clan_messages`, V17 — `bio`, `looking_for`, `discover_enabled`, `discover_radius_km` w `users`, tabele
  `profile_photos`, `profile_prompts`, `discover_swipes` i `DISCOVER_MATCH` w CHECK powiadomień. Schemat po migracjach
  zgadza się blok po bloku z tym, co buduje Hibernate (różnice tylko w zapisie starych CHECK-ów i kolejności kolumn);
- `mvnw clean test` → 725 testów; nowe klasy (talia, karta, EXIF, eksport, GIF-y w klanie) przechodzą także na
  PostgreSQL 16 — talia to ręcznie pisany SQL z odległością miast, więc to było ważne;
- testy z mutacjami: 45 mutantów (warunki talii, kolejność, zasięg, para i jej sprzątanie, blokada, limit, cofanie,
  miasto i ulubieni na karcie, ustawienia, wygasanie, poziomy gustu, zakaz publikowania, limity i walidacja karty,
  cudze zdjęcie, EXIF/obrót/ICC/PNG, eksport, dowód w zgłoszeniu, „wyczyść kartę”, karta na profilu tylko dla
  znajomych, argumenty komunikatów, GIF-y w klanie) — po dopisaniu testów wszystkie zabite. Pierwszy przebieg zostawił
  pięć żywych: niepotwierdzone konto w talii (osoba z testu odpadała już przez brak miasta), cofnięcie oddające
  pierwszą kartę z brzegu, „Nowa osoba” zawsze włączona, GIF w eksporcie wiadomości klanu i jeden fałszywy — wzorzec
  mutanta pasował też do starszej obsługi 404, więc zmienił nie to miejsce;
- znalezione przy sprawdzaniu: obsługa błędów nie podawała argumentów do komunikatów („Klan jest pełny (najwyżej {0}
  osób)” było widać dosłownie od V9) — poprawione; `touch-action: pan-y` na warstwie nad przewijaną kartą nie działał
  (przeglądarka bierze go tylko do najbliższego przewijanego przodka) — przesunięcie palcem w bok nie docierało;
  przyciski decyzji wychodziły 40 px pod dolną krawędź ekranu 390×844;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V17, 64 sprawdzenia): ekran startowy z podglądem własnej karty
  i listą braków, włączenie, kolejność talii wg gustu, przeciągnięcie myszą z pieczątką TAK, stuknięcie w zdjęcie (nie
  jest decyzją), przycisk „tak” = para (ekran „To jest to!”, znajomość, powiadomienie u drugiej osoby), ← z klawiatury,
  cofnięcie, pusta talia → poszerzenie zasięgu → cały kraj (pasmo „ponad 250 km”), osoba z wyłączonym trybem nigdy
  w talii; **telefon z dotykiem** (CDP `Input.dispatchTouchEvent`): ruch w pionie przewija kartę i nie jest decyzją,
  w bok — decyzja i para, „Napisz wiadomość” otwiera rozmowę; przyciski na ekranie przy 390×844 i 360×740; brak przelewu
  strony przy 320, 360 i 390 px (Znajomi, Poznawaj, Ustawienia, profil z kartą); edytor karty (dwa zdjęcia naraz,
  układanie, limit „szukam”, pytanie, zapis, podgląd); karta na profilu; GIF w czacie klanu, odpowiedź na GIF z cytatem
  „GIF”; ciemny motyw; zero błędów w konsoli. Skrypt trzeba puszczać na świeżej bazie;
- **czego nie sprawdzono:** prawdziwego telefonu (dotyk był udawany przez Chromium) i zdjęć z prawdziwych aparatów
  (EXIF z telefonów sprawdzony na plikach zbudowanych w teście — także z obrotem w obu kolejnościach bajtów).

Przy edytorze zdjęć i plusiku zamiast serca (październik 2026, bez migracji, bez zmian w backendzie):

- `npm test` (wbudowany test runner Node'a, bez bibliotek) → 16 testów: geometria kadru (obroty, uchwyty, proporcje,
  granice; 20 000 losowych przypadków, puszczone też z czterema innymi ziarnami), macierz obrotu płótna zgodna
  z obrotem kadru, liczenie klatek GIF-a (także na prawdziwych plikach z PIL); `mvnw clean test` → 725 testów;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V17, 94 + 6 sprawdzeń): plusik w przycisku „tak” i na ekranie
  startowym, nigdzie ścieżki serca; „Nowa znajomość!” z ikoną obejmujących się osób (emotikon 🫂 zastąpiony
  później własnym SVG — wycięcia maski sprawdzone po pikselach);
  awatar (kadr 1:1 w kółku, obrót, wynik na serwerze sprawdzony **po pikselach** — ćwiartki w dobrych miejscach,
  ponowna edycja od oryginału, Esc, zepsuty plik → „Użyj bez edycji”, plik tekstowy → komunikat bez edytora); post
  (zdjęcie z EXIF-em obrotu, „bez zmian” = ten sam plik, proporcje, uchwyty myszą do krawędzi i za przeciwległy róg,
  klawiatura, obrót wolnego kadru, plik 16 MB → ≤ 5 MB i 2048 px, PNG z przezroczystością → WebP z kanałem alfa,
  animowany GIF z ostrzeżeniem i nietknięty, publikacja); galeria karty (kolejka „Obraz n z 3”, „Pomiń”, zamknięcie
  przerywa kolejkę, 3:4 na serwerze w kolejności wyboru); ikona i zdjęcie klanu (kwadrat, 3:1); telefon 320/360/390
  (okno na cały ekran, nic nie wystaje, „Gotowe” bez przewijania, kadr palcem przez CDP bez przewijania strony),
  ciemny motyw, zero błędów w konsoli;
- znalezione przy sprawdzaniu: przełączanie proporcji tam i z powrotem (1:1 → 16:9 → 1:1) zmniejszało kadr za każdym
  razem (zachowywał powierzchnię) — teraz wybór proporcji daje największy kadr wokół środka; podwójne stuknięcie
  „Gotowe”/„Pomiń” w kolejce karty przeskakiwało zdjęcie (drugie kliknięcie trafiało w przycisk następnego obrazu) —
  wynik oddawany raz i 0,4 s blokady po zmianie obrazu (próba czerwona na wersji bez poprawki, zielona 3× z rzędu);
  przeglądarki sprzed zmiany specyfikacji rzucają na `imageOrientation: 'from-image'` — teraz zostaje droga przez
  `<img>` (sprawdzone na udawanej starej przeglądarce, obrót z EXIF zachowany); stare Safari na prośbę o WebP
  oddaje PNG — plik dostaje typ tego, co naprawdę jest w środku; miniatury w formularzu posta robiły nowy adres
  `blob:` przy każdym odświeżeniu i żadnego nie zwalniały;
- **czego nie sprawdzono:** prawdziwego telefonu (dotyk udawany przez Chromium), Safari/iOS (tylko udawane braki
  Chromium) i zdjęć HEIC — przeglądarka bez obsługi HEIC pokaże „Użyj bez edycji”, a serwer i tak przyjmuje tylko
  JPEG/PNG/WebP/GIF.

Przy animacjach i ikonie z dwiema rękami (październik 2026, bez migracji, bez zmian w backendzie):

- ikona nowej znajomości: kilka wariantów drugiej ręki wyrenderowanych obok siebie, wybrany ten, w którym łuki
  ∩ i ∪ są symetryczne, a ciało osoby z przodu nie jest „zjedzone”; przy powiększeniu wyszła **wada w masce**
  (kształty w `<mask>` dziedziczyły `fill="currentColor"`, więc spod osoby z przodu wystawał pasek osoby z tyłu —
  wcześniej przykryty, przy drugiej ręce widoczny) — poprawione jawnym `fill="#000"` i sprawdzone po pikselach;
- Chromium na prawdziwym PostgreSQL (`ruch-e2e`, 39 sprawdzeń): piksele ikony (obie ręce, obie osoby, pięć przerw, w tym
  miejsce dawnego paska), ręce rysują się (schowane → całe), plusik obraca się pod kursorem, kreski gustu zapalają się
  po kolei; pigułka w przełącznikach tablicy, wydarzeń i znajomych — w trakcie jest **pomiędzy** opcjami, po animacji
  dokładnie pod wybraną, a wybrana opcja nie ma już własnego tła; pyknięcia (ikona w pasku, reakcja, ikona w
  przełączniku), licznik zaproszeń, karty wydarzeń i klanów wchodzą co 40 ms i po wejściu wszystkie są widoczne;
  „ogranicz ruch” — karty widoczne od razu, pigułka przeskakuje od razu; brak przelewu na 320/360/390 px (Tablica,
  Wydarzenia, Znajomi, Klany); zero błędów w konsoli. Wcześniejsze zestawy dalej zielone: edytor 93/93, podwójne
  stuknięcia 6/6, Poznawaj (wszystko; skrypt dostał zatwierdzanie kolejki edytora przy zdjęciach karty);
- **czego nie sprawdzono:** płynności na słabym telefonie (animacje są tylko na `transform`/`opacity`, ale klatki na
  prawdziwym urządzeniu trzeba zobaczyć).

Przy ikonie w układzie „E4” (dolna ręka wysoko na plecach) z gładkimi krawędziami:

- E4 w powiększeniu miała trzy nierówności: przerwa nad górną ręką ścinała od dołu głowę osoby z tyłu (dwa ostre rogi),
  przy końcu górnej ręki zostawał cienki sierp barku osoby z przodu z haczykiem, a początek dolnej ręki wystawał guzkiem
  w grubą (1,0) przerwę. Przesuwanie rąk przenosiło szpic w inne miejsce — przyczyna była w geometrii (przerwa przecina
  łuk barku pod ostrym kątem), więc kształt jest teraz wyliczany: `frontend/scripts/ikona-przytulas.py` (shapely) odejmuje
  przerwy i zaokrągla wszystkie rogi; ikona nie ma już masek. Pomiar „drzazg” (piksele cieńsze niż ~0,6 jednostki przy
  256 px): E4 161, wynik 31 (reszta to zaokrąglone końce);
- `ruch-e2e` 40/40 (piksele ikony w punktach policzonych z krzywych rąk, w tym bark osoby z przodu nad dolną ręką; brak
  masek; plusik obraca się o 90° — test czeka teraz, aż podpowiedź o mieście przestanie przesuwać przyciski, bo raz
  kursor zszedł z przycisku w trakcie pomiaru), edytor 93/93; skrypt w repozytorium daje dokładnie to, co jest
  w `Icons.jsx` (porównane znak po znaku).

Przy wyborze, kto widzi kartę na profilu (październik 2026, migracja V18):

- migracja V18 na **pustej** bazie (V1 → V18, profil `prod`: Flyway + `validate`) i na bazie z danymi po V17 (18 kont
  — wszystkie dostały `EVERYONE`, czyli zachowanie sprzed zmiany; CHECK odrzuca wartość spoza listy). Kolumny i nazwy
  ograniczeń zgadzają się z tym, co buduje Hibernate (`information_schema` i `pg_constraint` porównane w całości);
- `mvnw clean test` → 727 testów; nowy test najpierw czerwony we właściwym miejscu (obcy dostawał kartę mimo „tylko
  Poznawaj”), potem zielony: każdy / znajomi / tylko Poznawaj, właściciel i administrator widzą zawsze, ustawienie
  dostaje tylko właściciel, zaproszenie to jeszcze nie znajomość, talia Poznawaj bez zmian, zapis także z zakazem
  publikowania, zła i pusta wartość odrzucone, eksport ma ustawienie;
- testy z mutacjami: 16 mutantów (każda z trzech wartości na „każdy”/„nikt”, właściciel i administrator bez karty,
  administrator = każdy zalogowany, zaproszenie = znajomość, pominięcie ustawienia, ustawienie widoczne dla wszystkich
  albo niewidoczne dla właściciela, zapis bez skutku, zapis zablokowany zakazem publikowania, eksport, `GET` karty,
  pusta kolumna) — wszystkie zabite. Przypadek „zaproszenie to nie znajomość” dopisany przed puszczeniem mutantów;
- Chromium na prawdziwym PostgreSQL (baza z migracji V1–V18, 61 sprawdzeń): wybór w „Twoja karta” zapisuje się od
  razu i zostaje po odświeżeniu; na własnym profilu podpis z kłódką („widzisz tylko Ty”) albo ikoną znajomych i „Zmień”
  do ustawień; obcy i znajoma bez karty przy „tylko Poznawaj”, znajoma z kartą przy „tylko znajomi”, obcy znów z kartą
  przy „każdy”; talia Poznawaj pokazuje kartę przy „tylko Poznawaj”; awaria serwera → wybór wraca, komunikat, baza bez
  zmian; dwa szybkie wybory z opóźnionym pierwszym zapisem → w bazie i na ekranie zostaje drugi; ostrzeżenie „teraz karty nie widzi nikt” przy „tylko Poznawaj” z wyłączonym Poznawaj (znika po włączeniu, nie
  ma go przy „tylko znajomi”); telefon 320/360/390 px bez przelewu, ikona podpisu przy pierwszym wierszu zawiniętego
  tekstu, żadna opcja listy nie jest ucięta (szerokość napisu tym samym krojem wobec miejsca w polu, PL przy 320/360/390
  i EN przy 320 px); ciemny motyw, angielski, zero błędów w konsoli. Skrypt zaczerwienił się na dwóch celowych usterkach (brak
  cofnięcia wyboru po błędzie, brak podpisu — 11 czerwonych sprawdzeń). Poprzedni skrypt Poznawaj — 64/64;
- znalezione przy sprawdzaniu: podpis jako „pigułka” po zawinięciu na telefonie zostawiał kłódkę samą w wierszu — teraz
  ikona stoi obok pierwszego wiersza (zmierzone); „tylko Poznawaj” przy wyłączonym Poznawaj znaczyło po cichu „nikt” —
  teraz ustawienia to mówią; natywna lista ucinała najdłuższą opcję („…w trybie Pozn”) już przy 390 px, a przy 320 px
  nawet „Każdy, kto widzi mój profil” (o 2 px) i angielskie „Anyone who sees my profile” (o 22 px) — etykiety skrócone,
  szczegóły są w opisie pod listą; dwa szybkie wybory pod rząd szły równolegle i serwer mógł skończyć na pierwszym
  (próba czerwona na wersji bez poprawki: w bazie `FRIENDS` przy wybranym „tylko Poznawaj”) — zapisy idą teraz po
  kolei, a ekran słucha tylko odpowiedzi na ostatni wybór.

Przy usuwaniu wiadomości i podglądzie linków w czacie (październik 2026, migracja V19):

- **odtworzenie przed poprawką** (Chromium, prawdziwy PostgreSQL): film z YouTube'a w dymku rozmowy po kliknięciu miał
  **104×59 px** (dymek o szerokości tekstu „posłuchaj tego”), miniatura 480×360 z czarnymi pasami była przycinana do
  kwadratu 36 px, a zwykły link YouTube wklejony w tekst był nieklikalnym tekstem łamanym w pół adresu. Po poprawce:
  258×145 (komputer) i 289×162 (390 px), 16:9, miniatura bez pasów, link klikalny i skrócony, film rusza po kliknięciu;
- migracja V19 na **pustej** bazie (V1 → V19, profil `prod`) i na bazie z danymi po V17 (V17 → V18 → V19); kolumny,
  nazwy ograniczeń i treść CHECK-ów `clan_messages_music_*` identyczne z tym, co buduje Hibernate;
- `mvnw clean test` → 739 testów (nowa klasa `ChatDeleteAndLinksFlowTest`: parser linków w czacie, podgląd w rozmowie
  i w klanie, usuwanie własnej / cudzej / z rozmowy skasowanej u siebie, z zakazem pisania, nieprzeczytane, przekazanie
  usunięcia przy odpytywaniu z zapasem 30 s, zgłoszenie i eksport bez usuniętych, ślad w odpowiedzi w klanie, brak
  reakcji i odpowiedzi na usuniętą); `npm test` → 23 (podział tekstu na linki, nawiasy, kropka na końcu, `javascript:`);
- testy z mutacjami: 29 mutantów (warunki usuwania — cudza, ukryta u siebie, bez wiersza; czyszczenie treści, GIF-a
  i nagrania; pomijanie usuniętych w nieprzeczytanych, pushu, rankingu, aktywności, eksporcie i zgłoszeniu; zapas
  30 s; wykrywanie linku i granice adresu w parserze; reakcje i odpowiedzi na usuniętą). Przeżył jeden: przycisk usuwania
  przy śladzie w klanie — test patrzył oczami zwykłego członka, który i tak nie mógłby usunąć cudzej wiadomości.
  Dopisane sprawdzenie oczami autorki-założycielki zabija go;
- Chromium (`czat-e2e`, 34 sprawdzenia, dwie osoby w osobnych kartach): kosz tylko przy własnych, anulowanie okna
  potwierdzenia, awaria serwera (komunikat, wiadomość zostaje), ślad „Wiadomość usunięta” u mnie i u drugiej osoby
  **bez przeładowania** (~2,3 s w rozmowie, przy następnym odpytaniu w klanie), podgląd na liście rozmów, dwa linki
  w treści (nowa karta, `noopener`), playlista Spotify i sam link YouTube (sama karta, bez surowego adresu), HTML w treści
  zostaje tekstem (`<img onerror>` się nie wykonuje), czat klanu: karta wideo, odpowiedź, usunięcie z cytatem „usunięta”
  u obu osób, telefon 320 i 360 px bez przelewu, ciemny motyw, zero błędów w konsoli. Skrypt zaczerwienił się na celowej
  usterce (przeglądarka ignoruje `deletedIds` — 2 czerwone), dwa przebiegi pod rząd zielone;
- znalezione przy sprawdzaniu: pomiar karty od razu po otwarciu szuflady czatu na telefonie dawał „wystaje poza okno”
  — szuflada jeszcze wjeżdżała; mierzone po animacji.

Przy spotkaniach w czacie i mapie (październik 2026, migracja V20):

- migracja V20 na **pustej** bazie (V1 → V20, profil `prod`) — schemat zgodny co do joty z tym, co buduje Hibernate
  (te same tabele, kolumny, nazwy ograniczeń i indeksów), i na bazie z danymi po V19 (wiadomości i czat klanu zostają,
  backend wstaje z `validate`);
- `mvnw clean test` → 753 testy (nowe: `MeetingFlowTest` — 12 przebiegów przez API: zakładanie, walidacja czasu, punktu,
  przypomnienia i miejsca, limit 20, odpowiedzi i ich cofanie, odświeżanie rozmowy, odwołanie z powiadomieniem i linkiem
  do rozmowy, przypomnienia na przestawianym zegarze, spóźnione i po zerwaniu znajomości, usunięcie z wiadomością,
  zgłoszenie i skasowanie rozmowy, eksport i usunięcie konta, klan z odejściem i rozwiązaniem;
  `MeetingReminderSchedulingTest` i `PushFlowTest.scheduledRunPersists` — przypomnienia wołane przez harmonogram);
  `npm test` → 31 (stan spotkania, godziny domyślne, przez północ, walidacja, `.ics`);
- **błąd znaleziony w Chromium**: przypomnienie z harmonogramu nie zapisywało znacznika „wysłane” — `scheduled()` wołało
  `run()` z tej samej klasy, więc adnotacja transakcji nie działała, a powiadomienie (osobna transakcja) szło przy każdym
  przebiegu. Ten sam błąd miały przypomnienia o koncertach od V8. Najpierw dwa czerwone testy, potem `@Transactional`
  na `scheduled()` — zielone;
- Chromium (`spotkania-e2e`, 48 sprawdzeń, dwie osoby w osobnych kartach, strefa Europe/Warsaw, prawdziwy harmonogram):
  formularz poza formularzem wiadomości, walidacja bez zapytania do serwera, „Moja lokalizacja” (udostępniona pozycja
  przeglądarki) daje pinezkę, w bazie czas w UTC zgodny z polskim, karta z miejscem, godzinami, notatką, mapą i trasą,
  karta i odpowiedzi dochodzą do drugiej osoby bez przeładowania (~1,8 s), cofanie odpowiedzi, plik `.ics`, podgląd na
  liście rozmów, odwołanie (przekreślone, powiadomienie w dzwonku, kliknięcie otwiera rozmowę i zdejmuje `?czat=`),
  przypomnienie z harmonogramu dla obu potwierdzonych po ~2 min z godziną polską w dzwonku, usunięcie wiadomości ze
  spotkaniem, czat klanu (karta, odpowiedź, cytat z pinezką), telefon 320 i 360 px (formularz z mapą bez przelewu, karta
  w dymku, pole pisania 137 / 177 px), ciemny motyw, zero błędów w konsoli. Dwa przebiegi pod rząd zielone;
- znalezione przy sprawdzaniu: przy 320 px trzy przyciski załączników zostawiały 99 px na pisanie — mniejsze przyciski
  poniżej 375 px; pierwsza wersja testu sprawdzała listę rozmów, zanim się odświeżyła (test czeka na tekst).
- testy z mutacjami: 42 mutanty (walidacja czasu, punktu, przypomnienia i limitu, kto widzi i kto odpowiada, odwołanie,
  harmonogram przypomnień, sprzątanie przy usunięciu wiadomości, rozmowy, klanu i konta, odświeżanie czatów, link
  i sprawca powiadomienia, dowód w zgłoszeniu, push). Pierwszy przebieg: 36 zabitych. Ocalałe pokazały dziury w testach:
  „odpowiedź nie odświeża czatów” (świeże spotkanie i tak mieściło się w 30 s zapasu — test cofa `updated_at` o godzinę),
  „odwołanie zostawia przypomnienia”, „lista bez filtra blokad”, „przypomnienie po końcu”, „przypomnienie dla «nie dam
  rady»”, a jeden mutant był źle zapisany (nic nie zmieniał). Po dopisaniu testów (`MeetingFlowTest` 12 → 14,
  `mvnw clean test` → 755) wszystkie 42 zabite.

Przy opisach z Ticketmastera (październik 2026, migracja V24):

- migracja V24 na **pustej** bazie (V1 → V24, profil `prod`) — schemat taki sam jak z Hibernate (porównane zbiorami
  kolumn; nowa tabela `performer_about` co do znaku); na bazie z danymi po V23 migracja przechodzi, a backend wstaje
  z `validate`;
- testy: `TicketmasterTextsTest` (4 — opis wydarzenia z `info` + `description` + `additionalInfo` bez powtórzeń, HTML na
  zwykły tekst z akapitami, „<3” i `&lt;b&gt;` zostają tekstem, `<script>` znika; opis wykonawcy z obu pól, język
  z `locale`, strona artysty tylko http(s), przycięcie na granicy słowa), `TicketmasterClientTest` (+2 — zapytanie
  `/attractions/{id}.json` bez treści licencjonowanych, 404 i brak tekstu = pusto, dziwny numer nie idzie do sieci, błąd
  bez klucza), `ArtistAboutFlowTest` (4 — przez prawdziwe API z udawanym Ticketmasterem: opis z importu bez dodatkowego
  pytania, opis wydarzenia ze wszystkich pól, „nic nie ma” zapamiętane na 30 dni, awaria niezapamiętana, import nie kasuje
  opisu, którego nie przysłał, a zmieniony poprawia);
- Chromium na PostgreSQL po V24 (1280 i 320 px, udawany Ticketmaster i Last.fm): w „Kim jest?” najpierw opis od
  Ticketmastera w akapitach z `lang="en"` i podpisem „opis: Ticketmaster” (strona artysty, `nofollow`), potem Last.fm
  ze swoim przypisaniem; drugi wykonawca bez opisu z importu — jedno pytanie do Ticketmastera, po przeładowaniu z pamięci;
  opis wydarzenia w dwóch akapitach; panel w karcie; zero błędów w konsoli;
- formułki zamiast opisu (zgłoszone przez użytkownika na zrzutach z żywego Ticketmastera — „Service fee…”, „Seating
  chart…”, dane spółki Live Nation z KRS i VAT): `TicketBoilerplateTest` (5 — oba prawdziwe teksty dają brak opisu,
  prawdziwe zdanie zostaje, akapity zostają, polskie formułki, skrót ani inicjał nie rozcinają danych spółki),
  `TicketmasterTextsTest.boilerplateIsNotDescription` (import: opis, uwagi, dostępność) i `ArtistAboutFlowTest` (opis
  zapisany przed zmianą też bez formułek). Testy zaczerwieniły się na zaślepce, zanim powstał filtr. W Chromium (1280
  i 320 px, ciemny motyw) strony obu wydarzeń ze zrzutów nie mają sekcji „O wydarzeniu” ani żadnej formułki, a przy
  opisie mieszanym zostaje samo zdanie o koncercie; uwagi organizatora też bez formułek;
- mutanty: opisy z Ticketmastera 26 — 25 zabitych (w tym 5 przywracających stare zachowanie, więc testy czerwienieją na
  kodzie sprzed zmiany) i 1 równoważny; formułki 12/12. Trzy ocalałe w pierwszej rundzie dostały brakujące przypadki
  testowe i zostały zabite;
- prawdziwy Ticketmaster **nie** był sprawdzony (zablokowany) — trzy `curl` w „Opisy z Ticketmastera — próba na żywo”.

Przy wielu źródłach wydarzeń (październik 2026, migracja V23):

- migracja V23 na **pustej** bazie (V1 → V23) — schemat zgodny z Hibernate; na bazie z wydarzeniami po V22 istniejące
  wydarzenia dostały źródło „Ticketmaster”, backend wstaje z `validate`;
- testy: `SecondarySourcesClientTest` (4 — Bandsintown: kraj z nazwy, bilety tylko http(s), pusty tytuł = skład,
  północ = godzina nieznana, ukośnik w nazwie wykonawcy zakodowany podwójnie, 404 i „not found” = pusto, błąd bez klucza;
  Songkick: odwołane, festiwal po nazwie serii, ostatnia strona, błąd bez klucza; kraje z nazw), `EventMergerTest`
  (7 — granice zgodności: dzień, kraj, 3 godziny, sala po nazwie i po współrzędnych, inny wykonawca, sama nazwa),
  `SecondarySourcesFlowTest` (4 — na bazie: uzupełnienie bez nadpisywania, nowa karta, obcy kraj pominięty, podpis
  źródeł; Ticketmaster nie kasuje uzupełnień, przejmuje koncert z Bandsintown bez duplikatu i oddaje wycofany;
  znikanie po 3 dniach z bezpiecznikiem i wycofaniem przy zapisach; Songkick); `mvnw clean test` → 794;
- Chromium na PostgreSQL po V23 (1280 i 320 px): podpis „Dane o wydarzeniu: Ticketmaster · Bandsintown · Songkick”
  w tej kolejności, odnośnik tylko przy źródle ze znanym adresem (nowa karta, `nofollow`), wydarzenie założone przez
  Bandsintown z nim jako głównym źródłem, podpis w oknie, zero błędów w konsoli. Przy okazji wyszło, że przy 320 px
  „Biorę udział” wystawało 37 px poza kartę — poprawione; przyciski udziału zmierzone względem karty przy 320–768 px
  po polsku i angielsku (w karcie, w jednej linii);
- prawdziwe Bandsintown i Songkick **nie** były sprawdzone (zablokowane, klucze na zgodę) — próba po wpisaniu klucza
  opisana wyżej, w „Dodatkowe źródła koncertów”.

Przy ekipach na koncert (październik 2026, migracja V22):

- migracja V22 na **pustej** bazie (V1 → V22, profil `prod`) — te same tabele, kolumny, ograniczenia i indeksy co
  w schemacie z Hibernate (porównane zbiorami kolumn — kolejność kolumn w starszych tabelach różni się od zawsze po
  `ALTER … ADD COLUMN`), i na bazie z danymi po V21 (backend wstaje z `validate`);
- `mvnw clean test` → 778 testów (nowe: `CrewFlowTest` — 12 przebiegów przez API: zakładanie z walidacją, limit
  ekip, dołączanie od razu i za zgodą, odmowa na tydzień, przyjęcie, wyścig „już w innej ekipie”, zarząd zakładającego,
  przekazanie i rozwiązanie, blokady, czat z nieprzeczytanymi, pushami i usuwaniem, zamknięcie czatu po koncercie,
  spotkania ekipy z przypomnieniem i odwołaniem, eksport, usunięcie konta, kaskada z wydarzeniem, sprzątanie próśb,
  kolejność listy, liczniki na kartach wydarzeń);
- **błędy znalezione testami**: drugie „przyjmij” w tej samej sesji rzucało `OptimisticLockException` (prośba usunięta
  zbiorczo, a w pamięci żywa) — teraz kasowana wprost; pełna ekipa z mojego miasta stała nad wolną — teraz pełne na końcu;
- Chromium (`ekipy-e2e`, 46 sprawdzeń, trzy osoby w osobnych kontekstach): opis „Nie idź na koncert sam” na logowaniu
  i rejestracji, „Twoje koncerty” na starcie, sekcja ekip pod „Biorę udział” i przed mapą, zakładanie z formularza
  (miasto z profilu), zapis „idę”, „1 ekipa” na liście, karta z „blisko ciebie”, dołączenie, czat ekipy w obie strony bez
  przeładowania (1,5–2,7 s), licznik na ikonie Wydarzeń i jego zniknięcie po przeczytaniu, miejsce zbiórki z „Będę”,
  nabór za zgodą z wiadomością, dzwonek u obu stron, klik w powiadomienie, blokada rezygnacji w ekipie, odejście,
  telefon 320 i 390 px (ciemny) bez przelewu, zero błędów w konsoli. Dwa przebiegi na świeżej bazie zielone;
- testy z mutacjami: 74 mutanty (widoczność i kolejność listy, „Dołącz” na karcie, limity, nabór, prośby i karencja,
  zarząd zakładającego, przekazanie i rozwiązanie, czat: nieprzeczytane, znacznik, push, usuwanie, zamknięcie po koncercie,
  spotkania ekipy i ich powiadomienia, blokada rezygnacji z koncertu, sprzątanie, usuwanie konta, karty wydarzeń, zakazy).
  Zabite 73 (`CrewFlowTest` 12 → 13 z testem zakazów); jeden równoważny — sprawdzenie wycofanego wydarzenia przy
  zakładaniu powtarza się w `participate`, które cofa całą transakcję.

Przy „Kim jest” wykonawca i „Od organizatora” (październik 2026, migracja V21):

- migracja V21 na **pustej** bazie (V1 → V21, profil `prod`) — schemat zgodny z tym, co buduje Hibernate (różnice tylko
  w zapisie CHECK-ów, które i tak przepisuje `EnumConstraintRefresher` — po ujednoliceniu identyczne), i na bazie
  z danymi po V20 (wydarzenia zostają, backend wstaje z `validate`). Porównanie wyłapało kolumnę `similar` — w H2 przeszła,
  w PostgreSQL to słowo zastrzeżone; teraz `similar_artists`;
- `mvnw clean test` → 766 testów (nowe: `TicketmasterOrganizerTest` — uwagi, organizator, ceny z `standard`, wiek,
  sprzedaż bez `startTBD`, dostępność, linki tylko http(s); `LastFmArtistInfoTest` — opis jako tekst, encje, przycinanie,
  adres tylko z last.fm, błędy; `ArtistProfileFlowTest` — pamięć 30 dni na język, angielski zamiast brakującego
  polskiego, awaria bez zapamiętania, tylko wykonawcy z wydarzeń, limit na minutę);
- Chromium (`wykonawcy-e2e`, 22 sprawdzenia, udawany Last.fm): skład z gatunkami i bez zapytania do Last.fm przed
  rozwinięciem, opis po polsku jako zwykły tekst, słuchacze i podobni, przypisanie CC BY-SA, linki z `nofollow`,
  ponowne rozwinięcie i przeładowanie bez drugiego zapytania (opis z bazy), nieznany i awaria Last.fm („Nie mamy jeszcze
  opisu”, nic nie zapisane), sekcja organizatora (ceny po polsku, start sprzedaży, 18+, dostępność, ważne), wydarzenie
  bez tych danych bez sekcji, telefon 320 i 390 px (ciemny) bez przelewu, zero błędów w konsoli. Dwa przebiegi zielone;
- **błąd znaleziony w Chromium**: opis wychodził „…w Warszawie .” — każdy znacznik HTML zamieniany na spację, także
  `<b>` w środku zdania. Najpierw czerwony test w `LastFmArtistInfoTest`, potem poprawka (spacja tylko za znacznikami
  blokowymi);
- znalezione przy sprawdzaniu (w samym teście): miarka przelewu uznawała wszystko za „przycięte”, bo `body` ma
  `overflow-x: hidden` — wynik był pusty; teraz liczy przodków do `<main>` i sprawdza, że coś zmierzyła.

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
