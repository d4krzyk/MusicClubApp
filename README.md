# MusicClubApp

Projekt zaliczeniowy — Programowanie w Javie III.

Aplikacja do poznawania ludzi o podobnym guście muzycznym. Użytkownicy mają
ulubionych artystów i utwory z katalogu Deezera; im więcej wspólnych artystów
i gatunków, tym wyżej ktoś pojawia się na liście proponowanych znajomych. Do
tego posty (tekst, zdjęcie, link do podglądu utworu), konta z logowaniem
i interfejs PL/EN.

**Stack:** Spring Boot 3.3 (REST API) · PostgreSQL 16 · React + Vite · Docker Compose

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
    ├── components/              # Layout, Post, Reactions, Favorites, FriendSuggestions, …
    └── pages/                   # Login, Register, Home, Feed, Profile, Friends, Settings
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
| GET | `/api/posts` | tablica postów, od najnowszych |
| POST | `/api/posts` | dodanie posta (tekst + zdjęcia + muzyka) |
| PUT | `/api/posts/{id}` | edycja posta (tekst i nagranie) — **tylko autor** |
| DELETE | `/api/posts/{id}` | usunięcie posta (autor albo admin) |
| PUT | `/api/posts/{id}/reaction` | ustawia reakcję (`FIRE`, `MID`, `MEH`) |
| DELETE | `/api/posts/{id}/reaction` | cofa własną reakcję |
| GET | `/api/profiles/{username}` | publiczny profil użytkownika |
| GET | `/api/profiles/{username}/friends` | znajomi — od najbardziej powiązanych |
| GET | `/api/profiles/{username}/top-music` | najczęściej wrzucane nagrania (top 5) |
| GET | `/api/profiles/{username}/favorites` | czyjeś ulubione — do oglądania |
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
zaciąga najczęściej słuchanych artystów i utwory. Bez klucza przycisku po
prostu nie ma — to lepsze niż przycisk, który zawsze kończy się błędem.

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
guście**, **moderacja kont (zakaz publikowania, usuwanie)**, motyw
jasny/ciemny oraz cała aplikacja na Docker Compose. Nazwy w kodzie są
konsekwentnie angielskie, komentarze — polskie.
**197 testów backendu przechodzi**, przepływy frontendu sprawdzone
w przeglądarce.

Zaliczone **20 wymagań** przy progu 17 na piątkę, w tym wszystkie 7 czerwonych.
Szczegóły w `docs/WYMAGANIA.md`. Następny krok: gablotka ulubionych playlist
na profilu.
