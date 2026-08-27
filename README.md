# MusicClubApp

Projekt zaliczeniowy — Programowanie w Javie III.

Aplikacja do poznawania ludzi o podobnym guście muzycznym. Użytkownicy mają
ulubionych artystów i utwory z katalogu Deezera; im więcej wspólnych artystów
i gatunków, tym wyżej ktoś pojawia się na liście proponowanych znajomych. Do
tego posty (tekst, zdjęcie, link do podglądu utworu), konta z logowaniem
i interfejs PL/EN.

**Stack:** Spring Boot 3.3 (REST API) · PostgreSQL 16 · React + Vite · Docker Compose

## Jak to wygląda

![Tablica](docs/zrzuty/tablica-ciemna.jpg)

Tablica pokazuje **najpierw posty znajomych**, a pod nimi publiczne wpisy
pozostałych osób — granicę widać wyraźnie:

![Granica między znajomymi a resztą](docs/zrzuty/tablica-jasna.jpg)

| | |
|---|---|
| ![Powiadomienia](docs/zrzuty/powiadomienia.jpg) | ![Kto zareagował](docs/zrzuty/kto-zareagowal.jpg) |
| Dzwonek — każde powiadomienie prowadzi do konkretnego zdarzenia | Okienko „kto zareagował", pogrupowane po rodzaju |
| ![Profil](docs/zrzuty/profil.jpg) | ![Pusty stan](docs/zrzuty/pusty-stan.jpg) |
| Profil: ulubieni, gablotka playlist, znajomi, posty | Pusto ≠ awaria — każdy pusty stan mówi, co dalej |

<details>
<summary>Skąd te zrzuty i czego na nich nie ma</summary>

Zrobione **prawdziwą przeglądarką** (Chromium sterowany Playwrightem) na
danych demonstracyjnych zakładanych przez zwykłe API aplikacji — skrypt
`zrzuty-do-readme.mjs`. Zdjęcia w postach to wygenerowane gradienty, a nie
czyjeś fotografie: chodzi o pokazanie układu strony, nie o ilustracje.

Nie ma na nich **odtwarzaczy muzyki ani okładek playlist**. Środowisko,
w którym powstawały, nie ma dostępu do Spotify, YouTube ani Deezera, więc
ramka `<iframe>` zostałaby pusta, a okładki zastąpione są ikoną. U Ciebie,
z normalnym dostępem do sieci, wczytają się same.
</details>

## Dokumentacja

| Plik | Co zawiera |
|------|------------|
| [`docs/PLAN.md`](docs/PLAN.md) | plan pracy krok po kroku + wyjaśnienie, co robi każdy element |
| [`docs/WYMAGANIA.md`](docs/WYMAGANIA.md) | checklista 28 wymagań z PDF-a i gdzie każde realizujemy |

## Jak uruchomić

Potrzebne: **Docker Desktop**. Do pracy nad kodem dodatkowo **JDK 17+**
i **Node 20+**.

### Wariant A — całość jedną komendą (wymaganie nr 18)

```bash
cp .env.example .env      # w PowerShellu: copy .env.example .env
docker compose up -d --build
```

Aplikacja: **http://localhost:3000** · Swagger: http://localhost:8080/swagger-ui.html
· Adminer: http://localhost:8081

Pierwszy start trwa kilka minut (budowanie obrazów). Kolejne są szybkie.
`docker compose down` zatrzymuje wszystko, `docker compose down -v` kasuje
też dane **razem ze wgranymi zdjęciami**.

### Wariant B — do codziennej pracy nad kodem

W wariancie A każda zmiana wymaga przebudowania obrazu. Na co dzień wygodniej:

```bash
# 1. Sama baza
docker compose up -d db

# 2. Backend (da się debugować z IntelliJ)
mvn spring-boot:run

# 3. Frontend (w drugim terminalu)
cd frontend
npm install     # tylko za pierwszym razem
npm run dev
```

Aplikacja: http://localhost:5173 · Backend: http://localhost:8080

Adminer: system `PostgreSQL`, serwer `db`, użytkownik / hasło / baza: `musicclub`

```bash
# Testy - działają nawet przy wyłączonym Dockerze (baza H2 w pamięci)
mvn test
```

## Struktura

```
docker-compose.yml            # KROK 6: baza + backend + frontend + Adminer
Dockerfile                    # obraz backendu (Maven -> JRE)
.env.example                  # wzór pliku z hasłami (skopiuj do .env)
pom.xml                       # zależności Mavena
docs/                         # plan pracy i checklista wymagań
docs/zrzuty/                  # zrzuty ekranu do tego pliku
src/main/java/com/musicclubapp/
├── MusicClubAppApplication.java   # punkt wejścia
├── config/                        # SecurityConfig, I18nConfig
├── controller/                    # REST API (Auth, Post, Reaction, Profile, Users,
│                                  #           Favorites, MusicCatalog, Friend)
├── dto/                           # dane wejściowe/wyjściowe + walidacja
├── entity/                        # encje JPA (klasa = tabela)
├── error/                         # GlobalExceptionHandler i wyjątki
├── mapper/                        # encja → DTO
├── repository/                    # dostęp do bazy (interfejsy Spring Data)
├── security/                      # "zapamiętaj mnie" dla logowania JSON-em
├── service/                       # logika biznesowa
└── validation/                    # własne adnotacje walidacyjne
src/main/resources/
├── application.properties         # konfiguracja (baza, języki, Security)
└── lang/messages*.properties      # teksty PL i EN
src/test/
├── java/                          # @SpringBootTest, @DataJpaTest, @WebMvcTest, Mockito
└── resources/application-test.properties  # profil testowy (H2 w pamięci)

frontend/                        # KROK 5: React + Vite (szczegóły w frontend/README.md)
├── vite.config.js               # proxy /api → localhost:8080 (bez CORS-a)
├── Dockerfile                   # KROK 6: build Vite -> nginx
├── nginx.conf                   # to samo proxy, ale w kontenerze
└── src/
    ├── api/client.js            # axios: ciasteczka, CSRF, język, błędy
    ├── auth/                    # kto zalogowany + ochrona tras
    ├── theme/                   # motyw jasny/ciemny
    ├── i18n/                    # pl.json i en.json
    ├── hooks/                   # useLiveReactions - odświeżanie liczników reakcji
    ├── components/              # Layout, Post, Reactions, Favorites, Playlists,
    │                            #   CommonGround, EmptyState, PostSkeleton, …
    │                            # Icons.jsx: ikony z Bootstrap Icons (MIT)
    └── pages/                   # Login, Register, Feed (strona główna), Post, Profile,
                                 #   Friends, Settings
```

## API

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/api/auth/csrf` | ustawia ciasteczko CSRF (zawołaj przed pierwszym POST) |
| POST | `/api/auth/register` | rejestracja |
| POST | `/api/auth/login` | logowanie (zakłada sesję) |
| POST | `/api/auth/logout` | wylogowanie |
| GET | `/api/auth/me` | dane zalogowanego użytkownika |
| PUT | `/api/profile` | zmiana własnego loginu i e-maila |
| PUT | `/api/profile/password` | zmiana własnego hasła (wymaga obecnego) |
| GET | `/api/posts?scope=ALL\|FRIENDS` | tablica — najpierw znajomi, potem reszta |
| POST | `/api/posts` | dodanie posta (tekst + zdjęcia + muzyka + widoczność) |
| PUT | `/api/posts/{id}` | edycja posta (treść, nagranie, widoczność) — **tylko autor** |
| DELETE | `/api/posts/{id}` | usunięcie posta (autor albo admin) |
| GET | `/api/posts/{id}` | jeden post — tu prowadzą powiadomienia o reakcjach |
| GET | `/api/posts/reactions?ids=` | same liczniki reakcji dla wskazanych postów |
| PUT | `/api/posts/{id}/reaction` | ustawia reakcję (`FIRE`, `MID`, `MEH`) |
| DELETE | `/api/posts/{id}/reaction` | cofa własną reakcję |
| GET | `/api/posts/{id}/reactions` | kto zareagował i jak |
| GET | `/api/notifications` | powiadomienia, od najnowszych |
| GET | `/api/notifications/unread-count` | liczba nieprzeczytanych (to ona wisi przy dzwonku) |
| POST | `/api/notifications/{id}/read` | oznacza jedno jako przeczytane |
| POST | `/api/notifications/read-all` | oznacza wszystkie |
| GET | `/api/profiles/{username}` | publiczny profil użytkownika |
| GET | `/api/profiles/{username}/friends` | znajomi — od najbardziej powiązanych |
| GET | `/api/profiles/{username}/top-music` | najczęściej wrzucane nagrania (top 5) |
| GET | `/api/profiles/{username}/favorites` | czyjeś ulubione — do oglądania |
| GET | `/api/profiles/{username}/common` | co konkretnie łączy Cię z tą osobą |
| GET | `/api/profiles/{username}/playlists` | czyjaś gablotka playlist |
| GET | `/api/profile/playlists` | **moja** gablotka playlist |
| POST | `/api/profile/playlists` | dodanie playlisty (w treści sam adres) |
| DELETE | `/api/profile/playlists/{id}` | usunięcie playlisty z gablotki |
| GET | `/api/profile/favorites` | **moje** ulubione |
| POST | `/api/profile/favorites/artists` | dodanie artysty z katalogu (w treści sam `externalId`) |
| DELETE | `/api/profile/favorites/artists/{externalId}` | usunięcie artysty z własnej listy |
| POST | `/api/profile/favorites/tracks` | dodanie utworu z katalogu |
| DELETE | `/api/profile/favorites/tracks/{externalId}` | usunięcie utworu z własnej listy |
| GET | `/api/profile/favorites/import/lastfm` | czy import jest włączony (czy jest klucz API) |
| POST | `/api/profile/favorites/import/lastfm` | import z Last.fm po nazwie użytkownika |
| GET | `/api/music/search/artists?q=` | wyszukiwarka artystów (Deezer) |
| GET | `/api/music/search/tracks?q=` | wyszukiwarka utworów (Deezer) |
| GET | `/api/friends/suggestions` | proponowani znajomi — od najlepiej dopasowanych |
| GET | `/api/friends/requests` | zaproszenia oczekujące (do mnie i ode mnie) |
| POST | `/api/friends/requests` | zaproszenie do znajomych |
| POST | `/api/friends/requests/{id}/accept` | przyjęcie zaproszenia |
| DELETE | `/api/friends/requests/{id}` | odrzucenie albo anulowanie |
| DELETE | `/api/friends/{username}` | usunięcie znajomości |
| PUT | `/api/profile/avatar` | wgranie zdjęcia profilowego |
| DELETE | `/api/profile/avatar` | usunięcie zdjęcia profilowego |
| GET | `/api/users` | lista ze stronicowaniem i sortowaniem — **tylko admin** |
| GET | `/api/users/{id}` | pojedynczy użytkownik — **tylko admin** |
| PATCH | `/api/users/{id}/role` | zmiana roli — **tylko admin** |
| PATCH | `/api/users/{id}/posting-ban` | zakaz publikowania na N godzin (pusto = zdejmij) — **tylko admin** |
| DELETE | `/api/users/{id}` | usunięcie konta wraz z jego treściami — **tylko admin** |
| GET | `/actuator/health` | czy aplikacja żyje — używa tego healthcheck Dockera |

Dokumentacja: http://localhost:8080/swagger-ui.html

## Role i konto administratora

Rejestracja przez formularz zawsze tworzy **zwykłego użytkownika** — inaczej
każdy mógłby zrobić sobie konto administratora. Pierwszego admina zakłada więc
sama aplikacja przy pierwszym starcie (`config/AdminInitializer`), o ile w bazie
nie ma jeszcze żadnego.

Domyślne dane logowania: **`admin` / `admin12345`** — do nauki. Przed oddaniem
projektu ustaw własne przez zmienne środowiskowe `ADMIN_USERNAME`,
`ADMIN_EMAIL`, `ADMIN_PASSWORD` albo po prostu zmień hasło w ustawieniach konta.

| Kto | Widzi |
|-----|-------|
| zwykły użytkownik | swój profil, ustawienia konta (login, e-mail, hasło) |
| administrator | to samo + listę wszystkich kont, zmianę ról, usuwanie cudzych postów, **zakaz publikowania i usuwanie kont** |

### Moderacja: zakaz publikowania i usuwanie kont

W panelu administratora, przy każdym koncie poza własnym, są dwie rzeczy:
**zakaz publikowania** (godzina / doba / tydzień / miesiąc) i **usunięcie konta**.

**Zakaz ma termin, a nie flagę „zablokowany".** Blokada bezterminowa
wymagałaby, żeby ktoś pamiętał o jej zdjęciu — a o tym zwykle nikt nie
pamięta. Zapisany termin sam pilnuje końca kary: wygasa bez żadnego zadania
w tle, bo liczy się wyłącznie porównanie z bieżącą chwilą. Wpisu nie
kasujemy, więc administrator widzi, że ktoś był już kiedyś karany.

Zakaz obejmuje **dodawanie i edytowanie postów**. Czytanie, reakcje
i usuwanie własnych treści zostają dozwolone — kara ma powstrzymać przed
publikowaniem, a nie odciąć od portalu. (Edycja też jest zablokowana celowo:
inaczej wystarczyłoby wejść w dowolny stary post i podmienić w nim treść.)
Ukarany widzi komunikat z **terminem** końca kary, a nie samo „nie wolno".

**Usunięcie konta zabiera ze sobą wszystko**: posty (razem ze zdjęciami
na dysku), reakcje pod cudzymi postami, zaproszenia w obie strony,
znajomości i awatar. Cudze posty, pod którymi ta osoba zostawiła reakcję,
zostają — usuwamy konto, a nie fragmenty cudzych rozmów.

Dwie rzeczy administrator ma zablokowane **na sobie**: usunięcie własnego
konta i nałożenie na siebie zakazu. Powód jest ten sam co przy zmianie
własnej roli — jedno kliknięcie nie może zostawić portalu bez nikogo,
kto ma do niego dostęp.

Uwaga na dwie podobne ścieżki: `/api/profile` (l. poj.) to **moje** konto —
zmiana loginu, e-maila, hasła, awatara. `/api/profiles/{username}` (l. mn.)
to **czyjś** profil do oglądania: sam login, awatar, data dołączenia i liczba
postów. Publiczny profil celowo **nie zawiera e-maila ani roli** — to osobne
DTO, a nie ten sam obiekt z wyciętymi polami.

## Muzyka w postach

Post może mieć podpięte nagranie ze **Spotify**, **YouTube Music**
albo **Apple Music**. W formularzu wybierasz przełącznikiem, co wrzucasz:

| Rodzaj | Spotify | YouTube Music | Apple Music | Moment startu |
|---|---|---|---|---|
| Utwór | ✅ | ✅ | ✅ | ✅ opcjonalny |
| Album | ✅ | (jako playlista) | ✅ | — |
| Artysta | ✅ | — | ✅ | — |
| Playlista | ✅ | ✅ | ✅ | — |

**Zły link zatrzymuje wysyłkę**, zamiast zostać po cichu połkniętym — a komunikat
mówi, *co* wkleiłeś („to jest link do ALBUMU"), a nie tylko „zły link". Pole
momentu startu **pokazuje się wyłącznie przy utworze**: album to wiele nagrań,
a profil artysty w ogóle nie jest nagraniem.

### Tylko YouTube Music, nie zwykły YouTube

Przyjmujemy **wyłącznie `music.youtube.com`**. Link z `youtube.com/watch`
i skrócony `youtu.be` są odrzucane z podpowiedzią, co zrobić („otwórz to
nagranie w YouTube Music i skopiuj adres stamtąd"). Powód jest prosty: to
serwis **muzyczny**, a zwykły YouTube to wszystko — od podcastów po vlogi.
Statystyki gustu i dopasowywanie znajomych opierają się na tym, co ludzie
wrzucają, więc wpuszczenie tam dowolnego filmu zamieniłoby je w szum.

Uwaga na kolejność sprawdzania: `music.youtube.com` **zawiera w sobie**
`youtube.com`, więc najpierw pytamy o YT Music, a dopiero potem odrzucamy
resztę. Odwrotna kolejność odrzucałaby też te dobre linki.

Sam odtwarzacz składamy przez `youtube.com/embed/` — identyfikator nagrania
jest ten sam w obu serwisach. **YT Music dostaje duży ekran 16:9**, a nie
wąski pasek jak Spotify: pod tym adresem leci normalne nagranie z obrazem
i teledysk schowany w pasku wysokości 152 px nie miałby sensu.

Album udostępniony z YouTube Music przychodzi jako `playlist?list=OLAK5uy_…`
i tak też go zapisujemy: jako **playlistę**, bo tym on tam formalnie jest.

**Playlisty nie wliczają się do statystyk gustu** — playlista to cudza składanka,
a nie deklaracja „lubię tego artystę". Wrzucić ją na tablicę można, ale
w podsumowaniu profilu się nie pojawia.

### Skąd bierzemy tytuł

Tytuł i miniaturkę pobieramy **raz, przy dodawaniu posta**, przez publiczne
**oEmbed** — bez klucza i bez tokenu, więc działa dla każdego użytkownika.
Gdy serwis nie odpowie, post i tak powstaje: odtwarzacz ładuje się
w przeglądarce niezależnie od tego.

Apple Music **nie ma publicznego oEmbed**, ale ma tytuł **w samym adresie**:
w `music.apple.com/pl/song/lullaby/1440786034` człon `lullaby` to nazwa
utworu. Zamieniamy myślniki na spacje i podnosimy pierwsze litery — wychodzi
„Lullaby". To nie jest zgadywanie: ten człon generuje sam Apple z prawdziwej
nazwy nagrania.

Jest jeden wyjątek, w którym tego **nie** robimy. Adres z parametrem `?i=`
(utwór wskazany wewnątrz albumu) ma w slugu nazwę **albumu**, a nie utworu —
podpisanie takiego posta nazwą albumu byłoby zwykłym błędem, więc wtedy
zostawiamy puste pole. Lepsze puste niż mylące.

Na profilu widać **najczęściej wrzucane utwory** — liczone z postów, nie
z osobnej tabeli statystyk, więc licznik nie ma jak rozjechać się
z rzeczywistością.

## Ulubieni artyści i utwory

Na profilu jest sekcja **Ulubieni** — to świadoma deklaracja gustu i to na
niej opiera się dopasowywanie ludzi. Blok obok („najczęściej wrzucane") mówi
co innego: co ktoś *postuje*, a to nie zawsze to samo, co lubi.

**Dodać da się tylko to, co istnieje w katalogu Deezera.** Nie ma pola „wpisz
nazwę artysty" i nie jest to przeoczenie — bez tego lista ulubionych byłaby
polem do popisu dla wymyślonych zespołów. Wpisujesz frazę, dostajesz
podpowiedzi z katalogu i klikasz w jedną z nich.

Ochrona nie kończy się na formularzu. Zapytanie da się wysłać z pominięciem
przeglądarki, więc metody serwisu przyjmują **wyłącznie identyfikator** —
nazwy ani zdjęcia nie ma jak przysłać, serwer pobiera je sobie sam z katalogu.
Pilnuje tego test `FavoritesServiceTest#serverDoesNotTrustNameFromRequest`.

Wiersz w tabeli `artists` jest **wspólny dla wszystkich** — polubienie tego
samego wykonawcy przez drugą osobę to samo dopisanie powiązania, bez ani
jednego zapytania do sieci. Usunięcie z ulubionych kasuje **tylko
powiązanie**; sam artysta zostaje, bo mają go u siebie inni.

Domyślny limit to **30 artystów i 30 utworów** (`app.favorites.max-artists`,
`app.favorites.max-tracks`).

### Import z Last.fm

Jeśli w `.env` jest `LASTFM_API_KEY`, na profilu pojawia się przycisk
**Import z Last.fm**: podajesz swoją nazwę użytkownika stamtąd i aplikacja
zaciąga najczęściej słuchanych artystów i utwory.

**Bez klucza przycisku nie ma** — to lepsze niż przycisk, który zawsze kończy
się błędem. Sam brak przycisku nie jest jednak informacją, tylko zagadką,
więc na własnym profilu widać wtedy krótką notkę, że import jest wyłączony;
**administrator dostaje dodatkowo nazwę zmiennej**, bo tylko on może to
włączyć. Klucz zakłada się w minutę na
<https://www.last.fm/api/account/create> — wystarczy sam „API key".

Podział ról między serwisami jest celowy: **Last.fm mówi, *czego* ktoś
słucha** (samych nazw — ich API od 2019 roku nie oddaje użytecznych zdjęć),
a **Deezer mówi, *kto* to jest** — identyfikator, zdjęcie, pewność, że taki
wykonawca istnieje. Czego nie da się odnaleźć w katalogu, tego nie dodajemy,
a podsumowanie po imporcie mówi wprost, ile pozycji pominięto i dlaczego.

Konta Last.fm **nie podpinamy** — nie ma logowania OAuth, nie trzymamy żadnego
tokenu. Wystarczy publiczna nazwa użytkownika, bo historia słuchania jest tam
publiczna.

Gatunki artysty bierzemy z tagów Last.fm, ale **tagi to nie są gatunki** —
wśród najpopularniejszych są „seen live" i „favorites". Odsiewamy je listą
wykluczeń i progiem popularności, inaczej dopasowanie łączyłoby ludzi na
zasadzie „oboje byli na jakimś koncercie". Gatunki zapisujemy **przy artyście,
nie przy użytkowniku**, więc kosztują jedno zapytanie na wykonawcę — raz,
na zawsze, dla wszystkich.

## Kto co widzi: tablica i widoczność postów

Dwie osobne rzeczy, które łatwo pomylić.

**Widoczność** ustawia autor przy pisaniu: post jest *publiczny* albo *tylko
dla znajomych*. **Kolejność** ustala aplikacja: posty z Twojego kręgu
(znajomi i Ty) idą na górę, publiczne wpisy pozostałych osób pod nie.
Nad tablicą jest przełącznik, którym można zawęzić ją do samych znajomych.

### Domyślnie szeroko, i to jest decyzja

Nowe konto nie ma ani jednego znajomego. Aplikacja, która wita takiego
użytkownika pustą stroną, jest bezużyteczna dokładnie wtedy, kiedy najbardziej
potrzebuje go przekonać — dlatego tablica domyślnie pokazuje też ludzi
spoza kręgu, a **nowy post domyślnie jest publiczny**. Cały sens tej
aplikacji to poznawanie *nowych* osób o podobnym guście; domyślne ukrywanie
wpisów przed wszystkimi poza obecnymi znajomymi działałoby przeciwko temu,
po co ona w ogóle jest. Kto chce inaczej, wybiera to jednym kliknięciem.

### Kolejność liczy baza, nie przeglądarka

W zapytaniu siedzi `ORDER BY CASE WHEN autor ∈ mój_krąg THEN 0 ELSE 1 END,
data DESC` — sortujemy po kolumnie, której w tabeli nie ma. Kuszące byłoby
pobrać wszystko i poprzestawiać w Reakcie, ale wtedy **stronicowanie zaczyna
kłamać**: każda strona zawierałaby inny zestaw wpisów, a licznik stron
liczyłby coś zupełnie innego niż to, co widać.

„Mój krąg" to jedno pojęcie (`UserRepository.circleIds`) załatwiające trzy
sprawy naraz: kogo posty idą na górę, czyje posty „tylko dla znajomych" wolno
mi zobaczyć i co zostaje po zawężeniu tablicy. Gdyby liczyły się osobno,
mogłyby się rozjechać. **Własny identyfikator jest w tym zbiorze celowo** —
merytorycznie, bo własne posty należą do kręgu, i technicznie, bo `IN ()`
z pustą listą jest w SQL-u błędem składni i wywracałoby tablicę
użytkownikowi bez znajomych.

### Ta sama reguła w dwóch miejscach

„Kto może zobaczyć post" jest zapisane **dwa razy**: w JPQL-u dla całej
tablicy i w Javie (`Post.isVisibleTo`) dla pojedynczego posta. Nie da się
tego uniknąć — bazę trzeba odsiać u niej, a pojedynczy post sprawdza się
w kodzie. Da się za to sprawdzić, że oba zapisy mówią to samo, i robi to
test `PostVisibilityTest#theRuleInJavaAndInTheDatabaseAgree`.

Reguła obowiązuje **wszędzie**, nie tylko na tablicy: wpisanie z ręki adresu
`/post/{id}` cudzego posta dla znajomych kończy się odmową, tak samo jak
próba zareagowania na niego z pominięciem przeglądarki. Ukrycie czegoś
w interfejsie nie jest zabezpieczeniem.

**Administrator też nie widzi cudzych postów dla znajomych.** To nie jest
przeoczenie: interfejs obiecuje „tylko znajomi", a obietnica z cichym
wyjątkiem dla obsługi serwisu nie jest obietnicą. Moderacja przez usunięcie
posta po identyfikatorze działa niezależnie od tego.

## Powiadomienia

Dzwonek w pasku z liczbą nieprzeczytanych. Powiadamiamy o trzech rzeczach:
ktoś **zareagował na Twój post**, ktoś **wysłał Ci zaproszenie**, ktoś
**przyjął Twoje zaproszenie**.

**Każde powiadomienie gdzieś prowadzi** — i to jest w nich najważniejsze.
Powiadomienie, po którym trzeba samemu szukać, co się właściwie stało,
łatwiej zignorować niż obsłużyć:

| Zdarzenie | Klik prowadzi do |
|---|---|
| reakcja na Twój post | **tego konkretnego posta** (`/post/{id}`) |
| nowe zaproszenie | strony znajomych, gdzie się je przyjmuje |
| przyjęte zaproszenie | profilu tej osoby |

Adres wylicza **serwer** (pole `link`), a nie frontend. Gdyby robił to
frontend, przy każdym nowym rodzaju powiadomienia trzeba by pamiętać
o dopisaniu warunku w drugim miejscu — i prędzej czy później powstałoby
powiadomienie, które nigdzie nie prowadzi.

Trzy reguły, bez których dzwonek szybko przestałby cokolwiek znaczyć:

- **Reakcja na własny post nie powiadamia.** Wiadomo, co się samemu zrobiło.
- **Zmiana zdania odświeża wpis zamiast dokładać drugi.** Jedna osoba
  klikająca kolejno trzy emotki zostawia jedno powiadomienie, nie trzy.
- **Cofnięta reakcja zabiera swoje powiadomienie.** Inaczej klik prowadziłby
  do posta, pod którym nie ma po niej śladu. Tak samo znika powiadomienie
  o zaproszeniu, które zostało odrzucone albo przyjęte.

Powiadomienie o reakcji pokazuje **początek treści posta** — bez tego
wszystkie wyglądałyby tak samo i nie dałoby się poznać, którego dotyczą.

### Kto zareagował

Kliknięcie w podsumowanie pod postem („3 reakcje") otwiera okienko z listą
osób, pogrupowaną po rodzaju reakcji. Listę pobieramy **dopiero po
otwarciu** — gdyby każdy post na tablicy ciągnął ją od razu, dwadzieścia
postów oznaczałoby dwadzieścia dodatkowych zapytań po to, żeby pokazać coś,
w co prawie nikt nie kliknie.

Widzi to każdy, nie tylko autor posta: reakcja jest gestem publicznym,
a i tak dałoby się ją policzyć z licznika obok emotki.

### Liczniki reakcji odświeżają się same

Po powrocie do karty przeglądarki (i co 45 sekund, gdy karta jest na wierzchu)
tablica pobiera **same liczniki** dla postów, które są akurat na ekranie —
jednym zapytaniem `GET /api/posts/reactions?ids=…`.

Dwie rzeczy, których świadomie tu nie robimy. **Nie ma WebSocketa**: stałe
połączenie plus rozgłaszanie zdarzeń po stronie serwera to spory kawałek
maszynerii do utrzymania, a chodzi o kilka liczb pod postami. **Nie
pobieramy tablicy od nowa**: doszłyby nowe posty, kolejność by się zmieniła
i czytający straciłby miejsce, w którym był. Zegar chodzi tylko przy
widocznej karcie — zapytania wysyłane do zminimalizowanego okna nikomu nic
nie pokazują, a zużywają baterię.

## Gablotka playlist

Do pięciu playlist na profilu, dodawanych przez wklejenie adresu ze Spotify,
YouTube Music albo Apple Music. Kliknięcie kafelka otwiera odtwarzacz
w okienku.

**To jest trzeci, jeszcze inny rodzaj informacji na profilu** — i warto go
odróżnić od dwóch pozostałych:

| Blok | Skąd się bierze | Do czego służy |
|---|---|---|
| Ulubieni artyści i utwory | świadomy wybór z katalogu Deezera | **dopasowywanie ludzi** |
| Najczęściej wrzucane | wyliczone z postów | statystyka, co ktoś publikuje |
| Gablotka playlist | wklejony adres | zaproszenie: „posłuchaj tego, co ja" |

Playlista **celowo nie liczy się do żadnego dopasowania**: dwie osoby mogą
wystawić tę samą składankę, mając na myśli zupełnie co innego, a jej
zawartość zmienia się w czasie. To ta sama myśl, dla której playlisty nie
wchodzą do zestawienia „najczęściej wrzucane".

Pięć, bo to gablotka, a nie archiwum — lista dwudziestu pozycji nie mówi już
nic o guście właściciela. **Odtwarzacz wczytuje się dopiero po kliknięciu**:
pięć ramek `<iframe>` od razu to pięć połączeń do obcych serwisów przy każdym
wejściu na profil.

Tytuł i okładka **nie przychodzą z zapytania** — pobiera je serwer z serwisu
muzycznego. Gdyby nazwa pochodziła od użytkownika, wystarczyłoby wysłać
zapytanie z pominięciem przeglądarki, żeby podpisać cudzą playlistę
czymkolwiek. Ta sama zasada co przy ulubionych artystach.

## Co Was łączy

Aplikacja od początku umiała policzyć, **ile** ktoś ma z kimś wspólnego — na
tym opiera się kolejność proponowanych znajomych. Nie umiała za to powiedzieć,
**co** to jest, a to dopiero jest powód, żeby napisać do obcej osoby.
„Oboje słuchacie Radiohead" da się zamienić w rozmowę; „2 wspólnych artystów"
nie da się zamienić w nic.

Sekcja na cudzym profilu (i okienko przy propozycjach znajomych) pokazuje
z imienia: wspólne **gatunki**, **artystów**, **utwory** i **znajomych**.
Na własnym profilu się nie pojawia — nie ma czego z czym porównywać.

Część wspólną liczymy **w Javie**, inaczej niż przy proponowanych znajomych.
Tam trzeba było ocenić dopasowanie *wszystkich* użytkowników naraz i tylko baza
mogła to zrobić sensownie. Tutaj chodzi o dwie osoby i najwyżej po kilkadziesiąt
pozycji — zapytanie z podwójnym złączeniem byłoby trudniejsze do przeczytania,
a nie szybsze. Gatunki są jedynym wyjątkiem: idą jednym zapytaniem, bo doczytanie
ich z encji kosztowałoby jedno zapytanie **na każdego** ulubionego wykonawcę.

## Puste stany

Pusto to nie awaria, tylko początek — i wtedy właśnie aplikacja ma jedyną
okazję powiedzieć, co dalej. Każdy pusty stan składa się z trzech części:
co tu będzie, dlaczego jeszcze tego nie ma i **jedno konkretne działanie**.

Zastąpiło to cztery różne szare linijki („Nie ma jeszcze żadnych postów",
„Brak znajomych", …), z których każda kończyła rozmowę z użytkownikiem
dokładnie w momencie, gdy miał najwięcej pytań.

Przy pierwszym ładowaniu tablicy i profilu pokazujemy **szkielety postów**,
a nie kręcące się kółko. Szkielet zajmuje to samo miejsce co prawdziwy post,
więc po wczytaniu nic nie skacze; kółko zostawiało pustą stronę, a treść
wskakiwała potem, przesuwając wszystko w dół. Przy doładowywaniu kolejnych
postów zostają trzy kropki — tam jest już co oglądać.

## Znajomi

Znajomość jest **obustronna** i wymaga zgody obu stron: ktoś wysyła zaproszenie,
druga osoba je przyjmuje. Zaproszenia oczekujące widać na stronie `/znajomi`,
a liczba nieodebranych pokazuje się przy pozycji w menu.

Jeśli **obie osoby zaproszą się nawzajem**, znajomość powstaje od razu — bez
czekania na dodatkowe kliknięcie. Obie przecież wyraziły zgodę.

Pasek znajomych pod profilem jest posortowany **od najbardziej powiązanych** —
po liczbie wspólnych znajomych.

### Proponowani znajomi

Na stronie `/znajomi` jest przewijany w poziomie pasek **proponowanych
znajomych**, od lewej najlepiej dopasowani. Układ poziomy jest tu celowy:
mówi „to jest lista uporządkowana, zacznij od lewej", czego pionowa siatka
nie przekazuje.

Dopasowanie liczy zapytanie w bazie, po trzech sygnałach:

| Sygnał | Waga | Dlaczego tyle |
|---|---|---|
| wspólny ulubiony artysta | 5 | najmocniejszy — obie strony świadomie go wybrały |
| wspólny znajomy | 3 | mocny, ale mówi o kręgu znajomych, nie o guście |
| wspólny gatunek | 1 | najsłabszy: „oboje słuchacie rocka" to prawie nic |

Na kartach pokazujemy **powód dopasowania** („2 wspólnych artystów"),
a nie liczbę punktów. Punkty nic nikomu nie mówią, a jeszcze zachęcałyby do
zgadywania, jak je podbić.

Na liście są **wszyscy** użytkownicy, nie tylko dopasowani — przy małej
aplikacji pusta strona wypadałaby dokładnie wtedy, kiedy najbardziej
potrzeba kogoś poznać. Osoby, które już są znajomymi, zostają na liście
z innym oznaczeniem, żeby pasek nie „skakał" po przyjęciu zaproszenia.

Gdy nikt się nie dopasował, pod paskiem pojawia się notka: żeby propozycje
były trafniejsze, trzeba dodać więcej ulubionych do profilu. To jedyny
moment, w którym ją pokazujemy — przy dobrych wynikach byłaby zwykłym
zrzędzeniem.

Kto co może zrobić z postem:

| Kto | Edycja | Usunięcie |
|-----|--------|-----------|
| autor posta | tak | tak |
| administrator | **nie** — cudzych treści się nie przerabia | tak (moderacja) |
| pozostali | nie | nie |

Przyciski „Edytuj" i „Usuń" rysujemy na podstawie pól `canEdit` i `canDelete`,
które **wylicza serwer**. Backend i tak sprawdza uprawnienia ponownie przy
każdym zapytaniu, więc dorysowanie sobie przycisku w przeglądarce nic nie da.

Zwykły użytkownik **nie widzi nigdzie swojej roli** — API nie wysyła pola
`role`, tylko flagę `admin` (`true`/`false`) potrzebną do narysowania menu.
O prawdziwym dostępie decyduje `SecurityConfig` po stronie backendu, więc
podmiana tej flagi w przeglądarce niczego nie odblokuje.

Język komunikatów: nagłówek `Accept-Language: pl` albo parametr `?lang=pl`.

## Układ strony

**Stroną główną jest tablica.** Pod adresem `/` od razu widać, co wrzucili
inni — a nie ekran powitalny z własnym adresem e-mail. Stary adres `/feed`
przekierowuje na `/`, żeby wysłane komuś linki i zakładki dalej działały.

**Górny pasek jest przyklejony** (`sticky-top`) i półprzezroczysty
z rozmyciem tła. Bez tego przy dłuższej tablicy trzeba było wracać na sam
początek strony, żeby gdziekolwiek przejść.

**Pasek ma trzy kolumny**: logo — ikony — konto. Skrajne mają tę samą
szerokość, więc ikony wypadają dokładnie na środku *strony*, a nie na środku
tego, co zostało po bokach (inaczej przy dłuższym loginie przesuwałyby się
u każdego inaczej).

**Nawigacja to same ikony**: tablica (nuty), znajomi (dwie osoby) i — u
administratora — panel (tarcza). Aktywna pozycja dostaje gradient marki;
przy samych ikonach to jedyne, co mówi, gdzie się jest. Każda ma `aria-label`
i dymek, bo ikona bez podpisu musi się jakoś przedstawić.

Nie ma „hamburgera" — po zamianie pozycji na ikony cały środek mieści się
nawet na telefonie, a chowanie nawigacji za dodatkowym kliknięciem tylko by
ją oddaliło.

**Wszystko, co dotyczy własnego konta, siedzi pod awatarem**: kliknięcie
w nazwę rozwija *Mój profil*, *Ustawienia* i *Wyloguj*. Osobne pozycje
„Strona główna", „Tablica", „Profil" i „Ustawienia" robiły z paska listę
odnośników, w której ginęło to, co naprawdę wspólne — a dwie z nich
prowadziły w to samo miejsce.

### Poziome paski ze strzałkami

Ulubieni artyści, ulubione utwory i proponowani znajomi używają **tego samego
komponentu** (`HorizontalStrip`): jeden rząd kafelków, przewijany strzałkami
albo palcem. Jedno zachowanie w trzech miejscach jest celowe — po nim
poznaje się, że to ten sam rodzaj listy.

Zawijana siatka przy dwudziestu ulubionych rozpychała profil na kilka
ekranów w dół i wypychała z widoku wszystko, co jest pod spodem. Pasek
zajmuje zawsze jeden rząd.

Strzałki pojawiają się **tylko wtedy, gdy jest co przewijać**, i wygasają
na końcach (przycisk, który nic nie robi, jest gorszy niż jego brak).
Przy krawędziach jest miękkie wygaszenie — mówi, że treść biegnie dalej,
i daje strzałce tło, przez które nie przebija się okładka.

## Wygląd

Cały wygląd opiera się na **jednym zestawie zmiennych** na górze
`styles.css` — kolorach, cieniach i krzywej czasowej. Kolor dopisany „na oko"
w jednym miejscu jest tańszy w tej chwili, ale po kilku takich okazuje się,
że fiolet w aplikacji ma pięć odcieni i żaden nie pasuje do pozostałych.

**Gradient marki** (fiolet → fuksja → róż) pojawia się wszędzie tam, gdzie coś
ma przyciągać wzrok: logo, główny przycisk, aktywna ikona w menu, obrączka
własnego awatara, wskaźnik języka. Jedno źródło sprawia, że te miejsca czytają
się jako jedna rodzina, a nie zbiór ozdób.

**Głębia bierze się z trzech rzeczy naraz**: tło strony ma lekki odcień,
powierzchnie (karty, pasek) są od niego jaśniejsze, a cienie są warstwowe —
bliższy i ostry plus dalszy i rozmyty. Pojedynczy cień wygląda jak naklejka;
dwa dają wrażenie, że element faktycznie unosi się nad tłem. Dopóki tło było
białe i karty też białe, żadne cienie nie mogły tego naprawić.

W ciemnym motywie cienie są **mocniejsze**, nie słabsze — czarny cień na
ciemnym tle prawie nie istnieje. Zamiast tego rozjaśniamy górę karty, tak jak
zachowuje się światło padające z góry.

**Wszystkie animacje wyłącza `prefers-reduced-motion`.** Dla części osób ruch
na ekranie oznacza zawroty głowy albo mdłości, a system ma na to osobne
ustawienie — wystarczy je uszanować.

## Motyw jasny / ciemny

Przełącznik (słońce/księżyc) stoi w menu obok PL/EN i działa też przed
zalogowaniem. Przy pierwszym wejściu aplikacja **idzie za ustawieniem
systemu** (`prefers-color-scheme`); po pierwszym kliknięciu pamięta wybór
w `localStorage`.

Cały wygląd przestawia jeden atrybut `data-bs-theme` na `<html>` — to
wbudowany mechanizm Bootstrapa 5.3. Ustawia go **skrypt w `index.html`**,
a nie React: React startuje za późno i przy jasnym motywie strona zdążyłaby
mignąć na ciemno.

Jeden kolor celowo **nie** zmienia się z motywem — tło ramki odtwarzacza
(`.ramka-odtwarzacza`). Sam odtwarzacz jest ciemny niezależnie od naszej
strony, więc jasne tło dawałoby białe rogi na ułamek sekundy przed jego
załadowaniem.

## Klucze i konfiguracja zewnętrznych serwisów

| Zmienna | Potrzebna do | Bez niej |
|---|---|---|
| `LASTFM_API_KEY` | import historii słuchania z Last.fm | przycisk importu się nie pokazuje, reszta działa |
| — (Deezer) | wyszukiwarka artystów i utworów | — Deezer nie wymaga żadnego klucza |
| — (oEmbed) | tytuły postów ze Spotify / YT Music | tytuł zostaje pusty, odtwarzacz działa |

Klucz Last.fm zakłada się w minutę na
<https://www.last.fm/api/account/create> — wystarczy sam „API key", bez
„shared secret" i bez OAuth, bo czytamy wyłącznie publiczne dane.

Adresy obu serwisów da się podmienić (`app.music.deezer.base-url`,
`app.lastfm.base-url`) — z tego korzystają testy, żeby nie zależeć od cudzej
dostępności.

## Stan projektu

Kroki 0–6 gotowe: repo posprzątane, baza na Dockerze, JPA, Spring Security
(rejestracja, logowanie sesyjne, „zapamiętaj mnie"), walidacja PL/EN,
obsługa błędów, Swagger oraz frontend w React. Do tego posty z reakcjami
(🔥 / 😐 / 🥱), publiczne profile, znajomi z zaproszeniami, **muzyka ze Spotify,
YouTube Music i Apple Music** (utwory, albumy, artyści, playlisty),
zestawienie najczęściej wrzucanych utworów, **ulubieni artyści i utwory
z katalogu Deezera z importem z Last.fm**, **proponowani znajomi po wspólnym
guście**, **posty publiczne albo tylko dla znajomych**, **tablica ze znajomymi
na górze**, **gablotka pięciu playlist na profilu**, **sekcja „co Was łączy"
z konkretnymi artystami, utworami i gatunkami**, **moderacja kont (zakaz
publikowania, usuwanie)**, motyw jasny/ciemny oraz cała aplikacja na Docker
Compose. Nazwy w kodzie są konsekwentnie angielskie, komentarze — polskie.

**246 testów backendu przechodzi**, a przepływy frontendu sprawdzamy
w prawdziwej przeglądarce (Chromium sterowany Playwrightem): widoczność
postów i kolejność tablicy, powiadomienia, linki muzyczne, moderacja,
układ strony i pasek przewijania.

Zaliczone **20 wymagań** przy progu 17 na piątkę, w tym wszystkie 7 czerwonych.
Szczegóły w `docs/WYMAGANIA.md`.

Przed oddaniem: ustaw własny `REMEMBER_ME_KEY` i własne hasło administratora
(`ADMIN_PASSWORD`) — domyślne wartości są wyłącznie do nauki.
