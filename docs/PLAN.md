# Plan pracy — MusicClubApp

Cel: aplikacja do poznawania ludzi po podobnym guście muzycznym (Spotify).
Im więcej wspólnych artystów i gatunków, tym wyżej ktoś jest na liście propozycji.
Do tego posty (tekst / zdjęcie / link do utworu), konta i logowanie, PL + EN.

**Termin: wrzesień.** Zasada nadrzędna: **jeden krok = jedna działająca rzecz.**
Nie zaczynamy kolejnego kroku, dopóki poprzedni się nie uruchamia i nie rozumiesz, co robi.

---

## Gdzie jesteśmy

| Krok | Co | Status |
|------|-----|--------|
| 0 | Sprzątanie repo | ✅ zrobione |
| 1 | PostgreSQL na Docker Compose | ✅ zrobione |
| 2 | Spring Boot ↔ baza (JPA, repozytorium, testy) | ✅ zrobione |
| 3 | Spring Security — rejestracja i logowanie | ⬜ następny |
| 4 | Połączenie logowania/rejestracji z bazą | ⬜ |
| 5 | Frontend React (logowanie, rejestracja, homepage) | ⬜ |
| 6 | Całość (backend + frontend + baza) na Docker Compose | ⬜ |
| 7 | Domena: artyści, gatunki, posty, algorytm dopasowań | ⬜ |

Kroki 1–6 to plan od kolegi. Krok 7 dokłada właściwy pomysł na aplikację —
robimy go **po** tym, jak szkielet już działa, bo inaczej znowu zrobi się bałagan.

---

## KROK 0 — sprzątanie (zrobione)

Co było nie tak i co poprawiliśmy:

- **`target/` i `.idea/` były w gicie.** To pliki generowane i ustawienia IDE —
  nie powinny być commitowane. Usunięte z repo (`git rm --cached`) i dopisane
  do `.gitignore`.
- **Był kod "na zapas"** — integracja ze Spotify i JWT napisane, zanim istniała
  baza i logowanie. Właśnie stąd bałagan. Usunięte z drzewa roboczego —
  **nic nie przepadło**, kod siedzi w historii gita na gałęzi `master`
  (commit `d8a1e5b`) i możemy do niego wrócić, kiedy dojdziemy do Spotify.
- **Baza była H2 w pamięci** (dane znikały po restarcie) → zastąpiona PostgreSQL-em.

## KROK 1 — PostgreSQL na Dockerze (zrobione)

Plik: `docker-compose.yml`

Uruchamiasz jedną komendą — nie instalujesz Postgresa na swoim komputerze:

```bash
docker compose up -d      # start bazy w tle
docker compose logs -f db # podgląd logów
docker compose down       # stop (dane zostają)
docker compose down -v    # stop + skasowanie danych
```

Co tam jest i po co:

| Element | Po co |
|---------|-------|
| `image: postgres:16-alpine` | gotowy obraz bazy, `alpine` = lekka wersja |
| `POSTGRES_DB/USER/PASSWORD` | przy **pierwszym** starcie zakładają bazę i użytkownika |
| `ports: 5432:5432` | dzięki temu z IntelliJ łączysz się na `localhost:5432` |
| `volumes: musicclub-db-data` | dane przeżywają restart kontenera |
| `healthcheck` | sprawdza, czy baza *przyjmuje połączenia*, a nie tylko czy kontener żyje — potrzebne w KROKU 6, żeby backend nie wystartował przed bazą |
| `adminer` | klikalna przeglądarka bazy na http://localhost:8081 — wchodzisz i **widzisz** tabele, które stworzyło JPA |

Logowanie do Adminera: system `PostgreSQL`, serwer `db`, user/hasło/baza `musicclub`.

## KROK 2 — Spring Boot ↔ baza (zrobione)

**Pliki:** `pom.xml`, `src/main/resources/application.properties`,
`entity/User.java`, `repository/UserRepository.java`, testy.

### Jak Spring łączy się z bazą

W `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:musicclub}
```

Zapis `${ZMIENNA:domyślna}` znaczy: *weź zmienną środowiskową, a jak jej nie ma —
użyj wartości po dwukropku*. Dlatego ten sam plik działa i lokalnie (`localhost`),
i później w Dockerze, gdzie hostem będzie `db`. Nie będziemy musieli nic przepisywać.

`spring.jpa.hibernate.ddl-auto=update` — Hibernate sam tworzy i dopisuje tabele
na podstawie encji. Wygodne, kiedy model jeszcze się zmienia.

### Co robi JPA

| Warstwa | Klasa u nas | Zadanie |
|---------|-------------|---------|
| Encja | `entity/User` | klasa Javy = tabela w bazie (`@Entity`, `@Column`) |
| Repozytorium | `repository/UserRepository` | **interfejs** bez implementacji — Spring pisze SQL za nas |

Repozytorium daje trzy sposoby zadawania pytań bazie:

1. **Gotowe metody** z `JpaRepository`: `save()`, `findById()`, `findAll(Pageable)`.
2. **Metody z nazwy** (derived query): `findByUsername` → `WHERE username = ?`.
   Spring czyta nazwę metody i buduje SQL.
3. **Własne `@Query`** — kiedy nazwa metody by nie wystarczyła.
   Uwaga: piszemy w **JPQL**, czyli po nazwach klas i pól (`User u`, `u.username`),
   a nie po nazwach tabel i kolumn. To wymaganie nr 8.

### Dwie rzeczy warte zapamiętania

- Tabela nazywa się `users`, nie `user` — bo `user` to **słowo zarezerwowane**
  w PostgreSQL i zapytania by się wywalały. Stąd `@Table(name = "users")`.
- `@PrePersist` w `User` ustawia `createdAt` automatycznie tuż przed pierwszym
  zapisem — nie musisz o tym pamiętać przy każdym tworzeniu użytkownika.

### Testy

```bash
mvn test
```

Testy chodzą na bazie **H2 w pamięci** (`src/test/resources/application.properties`),
więc działają nawet przy wyłączonym Dockerze i każdy startuje na czystej bazie.

- `MusicClubAppApplicationTests` — `@SpringBootTest`, sprawdza czy kontekst wstaje.
  To pierwszy test, który padnie, jak coś zepsujesz w konfiguracji.
- `UserRepositoryTest` — `@DataJpaTest` (wymaganie nr 14), podnosi *tylko* warstwę
  JPA. Sprawdza własne `@Query`, stronicowanie i sortowanie.

**Zweryfikowane:** 9/9 testów przechodzi, aplikacja wstaje na prawdziwym
PostgreSQL 16 i zakłada tabelę `users` z poprawnymi ograniczeniami `UNIQUE`.

---

## KROK 3 — Spring Security (następny)

Do zrobienia:

1. `SecurityConfig` z **`SecurityFilterChain` i lambda DSL** — wymaganie nr 15
   mówi wprost: konfiguracja **nie może być** `deprecated`. Czyli żadnego
   `WebSecurityConfigurerAdapter` (to stare podejście, którego pełno w tutorialach).
2. `PasswordEncoder` → `BCryptPasswordEncoder`. Hasła **tylko** jako hash.
3. DTO `RegisterRequest` / `LoginRequest` z Bean Validation (wymaganie nr 9).
4. Własna adnotacja walidacyjna, np. `@UniqueUsername` (wymaganie nr 10).
5. `GlobalExceptionHandler` z `@RestControllerAdvice` (wymaganie nr 11).
6. `messages.properties` + `messages_pl.properties` — komunikaty błędów po PL i EN
   (wymaganie nr 2), wybierane po nagłówku `Accept-Language`.
7. Kontrolery zwracające `ResponseEntity` (wymaganie nr 22) + Swagger (nr 24).
8. Testy: `@WebMvcTest` na kontrolery, Mockito na serwisy (wymagania nr 13 i 25).

**Do ustalenia przed startem:** sesja z ciasteczkiem czy JWT?
Przy osobnym froncie React zwykle wybiera się JWT, ale sesja jest prostsza
i wystarcza na zaliczenie. Zdecydujemy po przejrzeniu wykładów.

## KROK 4 — logowanie ↔ baza

`UserDetailsService`, który wyciąga użytkownika przez `UserRepository`,
`AuthenticationManager`, rejestracja zapisująca użytkownika z zahashowanym hasłem.

## KROK 5 — frontend React

`frontend/` (Vite + React), strony: rejestracja, logowanie, homepage.
Przełącznik języka PL/EN — po stronie frontu `react-i18next`.

## KROK 6 — całość na Docker Compose

Do `docker-compose.yml` dochodzą usługi `backend` i `frontend`.
Backend dostanie `DB_HOST=db` i `depends_on: db (service_healthy)`.
Wtedy jedno `docker compose up` uruchamia cały projekt — to wymaganie nr 18.

## KROK 7 — właściwa domena aplikacji

Dopiero tutaj dochodzi pomysł na aplikację:

| Encja | Relacje | Wymaganie |
|-------|---------|-----------|
| `Post` (tekst, zdjęcie, link do utworu, `createdAt`) | `Post` N—1 `User` | nr 6 (OneToMany/ManyToOne) |
| `Artist` | `User` N—N `Artist` (ulubieni) | nr 7 (ManyToMany) |
| `Genre` | `Artist` N—N `Genre` | — |
| `Match` (wynik dopasowania, `matchedAt`) | `Match` N—1 `User` ×2 | nr 4 (data/czas) |

Algorytm dopasowania: zapytanie `@Query`, które liczy wspólnych artystów
i wspólne gatunki dwóch użytkowników, sortuje malejąco po wyniku i zwraca
`Page<...>` — jednym strzałem wymagania nr 3, 5 i 8.

Integracja ze Spotify (pobieranie ulubionych artystów, podgląd utworu)
wraca **na końcu** — kod jest w historii gita.
