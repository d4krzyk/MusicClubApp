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
├── entity/                        # encje JPA (klasa = tabela)
└── repository/                    # dostęp do bazy (interfejsy Spring Data)
src/main/resources/
└── application.properties         # konfiguracja połączenia z bazą
src/test/
├── java/                          # testy (@SpringBootTest, @DataJpaTest)
└── resources/application.properties  # konfiguracja testowa (H2 w pamięci)
```

Katalogi `service/`, `controller/`, `dto/`, `config/`, `error/` i `frontend/`
dojdą w kolejnych krokach — patrz `docs/PLAN.md`.

## Stan projektu

Kroki 0–2 gotowe: repo posprzątane, baza na Dockerze, Spring Boot połączony
z PostgreSQL-em przez JPA, 9 testów przechodzi.
Następny krok: Spring Security (rejestracja i logowanie).
