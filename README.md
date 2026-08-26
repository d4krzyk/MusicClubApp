# MusicClubApp

Projekt zaliczeniowy — Programowanie w Javie III.

Aplikacja do poznawania ludzi o podobnym guście muzycznym. Użytkownicy mają
ulubionych artystów i utwory ze Spotify; im więcej wspólnych artystów i gatunków,
tym wyżej ktoś pojawia się na liście proponowanych znajomych. Do tego posty
(tekst, zdjęcie, link do podglądu utworu), konta z logowaniem i interfejs PL/EN.

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
├── controller/                    # REST API (Auth, Post, Reaction, Profile, Users)
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
    ├── components/              # Layout, Post, Reakcje, PasekZnajomych, GaleriaZdjec
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
| administrator | to samo + listę wszystkich kont, zmianę ról i usuwanie cudzych postów |

Uwaga na dwie podobne ścieżki: `/api/profile` (l. poj.) to **moje** konto —
zmiana loginu, e-maila, hasła, awatara. `/api/profiles/{username}` (l. mn.)
to **czyjś** profil do oglądania: sam login, awatar, data dołączenia i liczba
postów. Publiczny profil celowo **nie zawiera e-maila ani roli** — to osobne
DTO, a nie ten sam obiekt z wyciętymi polami.

## Muzyka w postach

Post może mieć podpięte nagranie ze **Spotify** albo **YouTube**. W formularzu
wybierasz przełącznikiem, co wrzucasz:

| Rodzaj | Spotify | YouTube | Moment startu |
|---|---|---|---|
| Utwór | ✅ | ✅ | ✅ opcjonalny |
| Album | ✅ | — | — |
| Artysta | ✅ | — | — |

**Zły link zatrzymuje wysyłkę**, zamiast zostać po cichu połkniętym — a komunikat
mówi, *co* wkleiłeś („to jest link do ALBUMU"), a nie tylko „zły link". Pole
momentu startu **pokazuje się wyłącznie przy utworze**: album to wiele nagrań,
a profil artysty w ogóle nie jest nagraniem.

Tytuł i miniaturkę pobieramy **raz, przy dodawaniu posta**, przez publiczne
**oEmbed** — bez klucza i bez tokenu, więc działa dla każdego użytkownika.
Gdy serwis nie odpowie, post i tak powstaje: odtwarzacz ładuje się
w przeglądarce niezależnie od tego.

Na profilu widać **najczęściej wrzucane utwory** — liczone z postów, nie
z osobnej tabeli statystyk, więc licznik nie ma jak rozjechać się
z rzeczywistością.

## Znajomi

Znajomość jest **obustronna** i wymaga zgody obu stron: ktoś wysyła zaproszenie,
druga osoba je przyjmuje. Zaproszenia oczekujące widać na stronie `/znajomi`,
a liczba nieodebranych pokazuje się przy pozycji w menu.

Jeśli **obie osoby zaproszą się nawzajem**, znajomość powstaje od razu — bez
czekania na dodatkowe kliknięcie. Obie przecież wyraziły zgodę.

Pasek znajomych pod profilem jest posortowany **od najbardziej powiązanych**:
dziś liczy się to po wspólnych znajomych, a po integracji ze Spotify dojdą
wspólni artyści i gatunki. Zmieni się wtedy tylko zapytanie w bazie —
API i frontend zostają bez zmian.

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
Spotify (`.ramka-spotify`). Sam odtwarzacz jest ciemny niezależnie od naszej
strony, więc jasne tło dawałoby białe rogi na ułamek sekundy przed jego
załadowaniem.

## Stan projektu

Kroki 0–5 gotowe: repo posprzątane, baza na Dockerze, JPA, Spring Security
(rejestracja, logowanie sesyjne, „zapamiętaj mnie"), walidacja PL/EN,
obsługa błędów, Swagger oraz frontend w React. Do tego posty z reakcjami
(🔥 / 😐 / 🥱), publiczne profile, znajomi z zaproszeniami, **muzyka ze Spotify
i YouTube** (utwory, albumy, artyści), zestawienie najczęściej wrzucanych
utworów, motyw jasny/ciemny oraz cała aplikacja na Docker Compose.
**115 testów backendu przechodzi**, przepływy frontendu sprawdzone w przeglądarce.

Zaliczone **20 wymagań** przy progu 17 na piątkę, w tym wszystkie 7 czerwonych.
Szczegóły w `docs/WYMAGANIA.md`. Następny krok: ulubieni artyści na profilu
(wyszukiwarka Deezer/iTunes + import z Last.fm).
