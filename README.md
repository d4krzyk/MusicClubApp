# MusicClubApp

Projekt zaliczeniowy — Programowanie w Javie III.

Aplikacja do poznawania ludzi o podobnym guście muzycznym. Użytkownicy mają
ulubionych artystów i utwory ze Spotify; im więcej wspólnych artystów i gatunków,
tym wyżej ktoś pojawia się na liście proponowanych znajomych. Do tego posty
(tekst, zdjęcie, link do podglądu utworu), konta z logowaniem i interfejs PL/EN.

**Stack:** Spring Boot 3.3 (REST API) · PostgreSQL 16 · React (dojdzie w kroku 5) · Docker Compose

## Dokumentacja

| Plik | Co zawiera |
|------|------------|
| [`docs/PLAN.md`](docs/PLAN.md) | plan pracy krok po kroku + wyjaśnienie, co robi każdy element |
| [`docs/WYMAGANIA.md`](docs/WYMAGANIA.md) | checklista 28 wymagań z PDF-a i gdzie każde realizujemy |

## Jak uruchomić

Potrzebne: **JDK 17+**, **Docker Desktop**, Maven (albo IntelliJ, który ma go wbudowanego).

```bash
# 1. Baza danych
docker compose up -d

# 2. Backend
mvn spring-boot:run

# 3. Frontend (w drugim terminalu)
cd frontend
npm install     # tylko za pierwszym razem
npm run dev
```

Aplikacja: http://localhost:5173
Backend (API): http://localhost:8080
Przeglądarka bazy (Adminer): http://localhost:8081 — system `PostgreSQL`,
serwer `db`, użytkownik / hasło / baza: `musicclub`

```bash
# Testy - działają nawet przy wyłączonym Dockerze (baza H2 w pamięci)
mvn test
```

## Struktura

```
docker-compose.yml            # KROK 1: PostgreSQL + Adminer
pom.xml                       # zależności Mavena
docs/                         # plan pracy i checklista wymagań
src/main/java/com/musicclubapp/
├── MusicClubAppApplication.java   # punkt wejścia
├── config/                        # SecurityConfig, I18nConfig
├── controller/                    # REST API (zwraca ResponseEntity)
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
└── src/
    ├── api/client.js            # axios: ciasteczka, CSRF, język, błędy
    ├── auth/                    # kto zalogowany + ochrona tras
    ├── i18n/                    # pl.json i en.json
    ├── components/              # Layout z menu i przełącznikiem PL/EN
    └── pages/                   # Login, Register, Home, Users
```

## API

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/api/auth/csrf` | ustawia ciasteczko CSRF (zawołaj przed pierwszym POST) |
| POST | `/api/auth/register` | rejestracja |
| POST | `/api/auth/login` | logowanie (zakłada sesję) |
| POST | `/api/auth/logout` | wylogowanie |
| GET | `/api/auth/me` | dane zalogowanego użytkownika |
| GET | `/api/users` | lista ze stronicowaniem i sortowaniem |
| GET | `/api/users/{id}` | pojedynczy użytkownik |

Dokumentacja: http://localhost:8080/swagger-ui.html

Język komunikatów: nagłówek `Accept-Language: pl` albo parametr `?lang=pl`.

## Stan projektu

Kroki 0–5 gotowe: repo posprzątane, baza na Dockerze, JPA, Spring Security
(rejestracja, logowanie sesyjne, „zapamiętaj mnie"), walidacja PL/EN,
obsługa błędów, Swagger oraz frontend w React.
**23 testy backendu przechodzą**, przepływy frontendu sprawdzone w przeglądarce.

Zaliczone 17 z 17 wymagań potrzebnych na piątkę, ale zostaje jeszcze czerwony
punkt nr 6 (relacja OneToMany) — obowiązkowy niezależnie od licznika.
Szczegóły w `docs/WYMAGANIA.md`. Następny krok: całość na Docker Compose.
