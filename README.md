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

# 2. Aplikacja
mvn spring-boot:run
```

Aplikacja: http://localhost:8080
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
```

Katalog `frontend/` dojdzie w KROKU 5 — patrz `docs/PLAN.md`.

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

Kroki 0–4 gotowe: repo posprzątane, baza na Dockerze, JPA, Spring Security
(rejestracja, logowanie sesyjne, „zapamiętaj mnie"), walidacja PL/EN,
obsługa błędów, Swagger. **23 testy przechodzą.**

Zaliczone 15 z 17 wymagań potrzebnych na piątkę — szczegóły w `docs/WYMAGANIA.md`.
Następny krok: frontend w React.
