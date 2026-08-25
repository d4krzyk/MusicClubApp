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
| 3 | Spring Security — rejestracja i logowanie | ✅ zrobione |
| 4 | Połączenie logowania/rejestracji z bazą | ✅ zrobione |
| 5 | Frontend React (logowanie, rejestracja, homepage) | ✅ zrobione |
| 6 | Całość (backend + frontend + baza) na Docker Compose | ⬜ następny |
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

Testy chodzą na bazie **H2 w pamięci** (`src/test/resources/application-test.properties`
włączany przez `@ActiveProfiles("test")`), więc działają nawet przy wyłączonym
Dockerze i każdy startuje na czystej bazie.

- `MusicClubAppApplicationTests` — `@SpringBootTest`, sprawdza czy kontekst wstaje.
  To pierwszy test, który padnie, jak coś zepsujesz w konfiguracji.
- `UserRepositoryTest` — `@DataJpaTest` (wymaganie nr 14), podnosi *tylko* warstwę
  JPA. Sprawdza własne `@Query`, stronicowanie i sortowanie.

**Zweryfikowane:** 9/9 testów przechodzi, aplikacja wstaje na prawdziwym
PostgreSQL 16 i zakłada tabelę `users` z poprawnymi ograniczeniami `UNIQUE`.

---

## KROKI 3 i 4 — Spring Security + logowanie z bazy (zrobione)

Wyszły razem, bo mocno się zazębiają. Wybraliśmy **sesję z ciasteczkiem**,
nie JWT — wykład 7 (slajd 31) wymienia oba podejścia dla REST API i sesja
jest prostsza do wytłumaczenia. Bonus: „zapamiętaj mnie" (wymaganie nr 17)
to przy sesji prawie darmowy punkt.

### Jak działa logowanie

```
POST /api/auth/login  {"username":"anna","password":"...","rememberMe":true}
   │
   ├─ AuthenticationManager sprawdza hasło (BCrypt vs hash z bazy)
   ├─ wynik ląduje w SecurityContext i zostaje ZAPISANY w sesji
   └─ przeglądarka dostaje ciasteczko JSESSIONID
        └─ odsyła je przy każdym kolejnym zapytaniu → serwer wie, kto to
```

### Nowe pliki i za co odpowiadają

| Plik | Rola |
|------|------|
| `config/SecurityConfig` | kto ma gdzie dostęp, CORS, CSRF, hashowanie haseł |
| `config/I18nConfig` | wybór języka + podpięcie tłumaczeń pod walidację |
| `service/AppUserDetailsService` | tłumacz między naszą encją `User` a Spring Security |
| `service/UserService` | rejestracja, wyszukiwanie — cała logika, testowana jednostkowo |
| `controller/AuthController` | `/register`, `/login`, `/logout`, `/me`, `/csrf` |
| `controller/UserController` | lista użytkowników ze stronicowaniem i sortowaniem |
| `error/GlobalExceptionHandler` | jedno miejsce na wszystkie błędy (wg wykładu 3) |
| `validation/UniqueUsername` + `PasswordsMatch` | dwie własne adnotacje |
| `security/JsonRememberMeServices` | „zapamiętaj mnie" dla logowania JSON-em |
| `mapper/UserMapper` | encja → DTO (wykład 4, slajd 23) |

### Pięć pułapek, na które się nadzialiśmy

Warto je znać — każda kosztowała trochę czasu i każda może wrócić.

1. **Plik `application.properties` w `src/test/resources` nie dokłada się do
   tego z `main` — on go zastępuje.** Zniknęła ścieżka do tłumaczeń i testy
   waliły „No message found under code". Rozwiązanie: profil
   `application-test.properties` + `@ActiveProfiles("test")` (wykład 2, slajd 40).
   Profil jest **dokładany** na wierzch, więc nadpisuje tylko to, co wypiszemy.

2. **Sam `SecurityContextHolder.setAuthentication()` nie zapisuje logowania
   w sesji** (tak jest na slajdzie 33). Od Spring Security 6 trzeba jeszcze
   `securityContextRepository.saveContext(...)` — inaczej użytkownik jest
   zalogowany tylko na czas jednego zapytania.

3. **Token CSRF generuje się leniwie.** Endpoint `/api/auth/csrf` musi
   *dotknąć* tokenu (`csrfToken.getToken()`), inaczej ciasteczko w ogóle
   nie powstaje i każdy POST kończy się odmową.

4. **Domyślny handler CSRF maskuje token operacją XOR**, a w ciasteczku jest
   wersja surowa — frontend odsyła ciasteczko i dostaje odmowę. Trzeba podstawić
   zwykły `CsrfTokenRequestAttributeHandler`.

5. **`AcceptHeaderLocaleResolver` nie współpracuje z `LocaleChangeInterceptor`** —
   każde `?lang=pl` kończyło się błędem 500. Zamieniony na `CookieLocaleResolver`,
   który obsługuje i nagłówek, i parametr.

### Sprawdzone na działającej aplikacji

Rejestracja (201 + `Location`), zajęty login (422 ze wskazaniem pola),
złe hasło (401), poprawne logowanie (sesja + ciasteczko na 14 dni),
`/me` przed i po zalogowaniu (401 → dane), wylogowanie (204),
stronicowanie i sortowanie, błąd 404, oba języki, Swagger z 6 endpointami.
**23 testy przechodzą.**

## KROK 5 — frontend React (zrobione)

`frontend/` (Vite + React), strony: rejestracja, logowanie, homepage.
Przełącznik języka PL/EN — po stronie frontu `react-i18next` (domyka wymaganie nr 2).

**Ważne dla logowania sesyjnego** — frontend musi:
- wysyłać wszystkie zapytania z `credentials: "include"` (inaczej przeglądarka
  nie dołączy ciasteczka sesji na inny port),
- przed pierwszym POST-em zawołać `GET /api/auth/csrf`,
- odczytać ciasteczko `XSRF-TOKEN` i wysyłać je w nagłówku `X-XSRF-TOKEN`
  (axios robi to sam po ustawieniu `withCredentials: true`).

## Role: użytkownik i administrator (zrobione po kroku 5)

Poprawka zgłoszona po pierwszym uruchomieniu aplikacji.

**Co się zmieniło:**

| Przed | Po |
|-------|-----|
| każdy zalogowany widział listę wszystkich kont | listę widzi **tylko administrator** |
| profil pokazywał „Rola: USER" | zwykły użytkownik nie widzi roli w ogóle |
| nie dało się zmienić swoich danych | ustawienia konta: login, e-mail, hasło |

**Jak zrobiona jest blokada.** Reguła siedzi w `SecurityConfig`:

```java
.requestMatchers("/api/users/**").hasRole("ADMIN")
```

Wykład 7 (slajd 47) zaleca właśnie ten sposób zamiast adnotacji
`@PreAuthorize` przy metodach — „bo konfiguracja jest w jednym miejscu".
Ukrycie linku w menu to **tylko porządek w interfejsie**, nie zabezpieczenie:
wpisanie adresu ręcznie i tak kończy się kodem 403 z backendu. Pilnują tego
testy w `UserControllerAccessTest`.

**Skąd bierze się administrator.** Rejestracja przez formularz zawsze tworzy
zwykłego użytkownika — inaczej każdy zrobiłby sobie konto admina. Pierwszego
zakłada `config/AdminInitializer` przy starcie aplikacji, o ile w bazie nie ma
jeszcze żadnego. Domyślnie `admin` / `admin12345`, do zmiany przed oddaniem.

**Dlaczego `admin: true/false` zamiast pola `role`.** Frontend musi wiedzieć,
czy narysować część administracyjną, ale zwykły użytkownik nie ma po co oglądać
napisu „USER". Jedna flaga załatwia jedno i drugie, nie ujawniając systemu
uprawnień.

### Trzy pułapki z tego kroku

1. **Zmiana loginu wysadza sesję.** Spring Security zapamiętuje użytkownika po
   loginie, więc po jego zmianie sesja wskazuje na konto, którego już nie ma —
   każde kolejne zapytanie kończy się błędem. `ProfileController` po udanej
   zmianie buduje nowy obiekt uwierzytelnienia i nadpisuje nim sesję.

2. **`@UniqueUsername` nie nadaje się do edycji profilu.** Ta adnotacja odrzuca
   każdy istniejący login — a przy zapisie ustawień użytkownik zwykle zostawia
   swój własny. Zajętość sprawdzamy więc w serwisie, pomijając jego własny wpis.

3. **Zmiana hasła wymaga podania obecnego.** Zalogowana sesja to nie to samo co
   potwierdzona tożsamość — bez tego pola ktoś przy niezablokowanym komputerze
   przejąłby konto na stałe. Błędne hasło zwraca 422 przypięte do pola,
   a nie 401, bo 401 oznacza dla frontendu „sesja wygasła, wyloguj".

---

## Bootstrap, role i posty (zrobione po kroku 5)

### Bootstrap zamiast własnego CSS

`styles.css` skurczył się z ~330 do ~70 linijek. Cały wygląd pochodzi teraz
z gotowych klas (`card`, `btn`, `form-control`), a ciemny motyw włącza jeden
atrybut w `main.jsx`:

```js
document.documentElement.setAttribute('data-bs-theme', 'dark');
```

Doszły dwie paczki: `bootstrap` (style) i `react-bootstrap` (komponenty jako
znaczniki Reacta, np. `<Navbar>`, `<Carousel>`, `<Modal>`). Karuzela z galerii
zdjęć to gotowy komponent stamtąd — pisanie jej samemu zajęłoby sporo czasu.

### Panel administratora: zmiana ról

`PATCH /api/users/{id}/role`, dostępny tylko dla admina. Dwie rzeczy warte uwagi:

**Osobne DTO dla admina.** `UserResponse` (własny profil) nadal nie ma pola
`role`. Lista w panelu chodzi przez `AdminUserResponse`, które je zawiera.
O tym, co wychodzi na zewnątrz, decyduje więc wybór klasy w kontrolerze,
a nie warunek `if` w środku mapowania.

**Admin nie może zmienić własnej roli.** Jedno kliknięcie zamknęłoby ostatniemu
adminowi drogę do panelu i nikt nie mógłby mu uprawnień przywrócić inaczej niż
ręcznie w bazie. Login do sprawdzenia bierzemy z sesji, nie z treści zapytania.

### Posty — tu domknęliśmy czerwone wymaganie nr 6

| Encja | Relacja | Po co |
|-------|---------|-------|
| `Post` | N—1 `User` | autor posta (**ManyToOne**) |
| `PostImage` | N—1 `Post` | zdjęcia posta (**OneToMany** od strony `Post`) |

**Zdjęcia leżą na dysku, w bazie są tylko nazwy plików.** Wrzucanie obrazków
do bazy działa, ale wtedy każde zapytanie o tablicę ciągnie megabajty danych.

**Galeria ma dwa tryby:** do 4 zdjęć siatka miniatur, powyżej — karuzela.
Bez tego post z dwunastoma zdjęciami rozpychałby całą tablicę.

**Spotify:** z wklejonego linku wyciągamy sam identyfikator utworu
(`SpotifyLink`), więc nie zapisujemy parametru `?si=`, który Spotify dokleja
przy udostępnianiu i który identyfikuje osobę udostępniającą. Odtwarzacz
osadzamy adresem `open.spotify.com/embed/track/...`.

### Cztery pułapki z tego etapu

1. **Vite nie przekazywał `/uploads` do backendu.** W `vite.config.js` było
   tylko `/api`, więc obrazki dostawały w odpowiedzi `index.html` — ze statusem
   **200**, przez co wyglądało to na sukces. Objaw: puste ramki zamiast zdjęć.
   Pułapka o tyle podstępna, że test sprawdzający tylko kod odpowiedzi ją przepuszcza.

2. **`@WebMvcTest` wciąga każdą klasę `WebMvcConfigurer`.** `UploadsWebConfig`
   wymagał `FileStorageService`, więc wszystkie testy kontrolerów przestały
   wstawać. Konfiguracja czyta teraz ścieżkę wprost z `application.properties`
   i nie zależy od serwisu.

3. **`MaxUploadSizeExceededException` jest już obsługiwany przez klasę
   nadrzędną.** Dołożenie drugiej metody `@ExceptionHandler` wywala aplikację
   przy starcie („Ambiguous @ExceptionHandler method"). Trzeba **nadpisać**
   `handleMaxUploadSizeExceededException`, a nie dodawać własną.

4. **Nazwa pliku od klienta nigdy nie trafia na dysk.** Nazwę generujemy sami
   (UUID), a rozszerzenie bierzemy z typu MIME. Nazwa w stylu
   `../../etc/passwd` pozwoliłaby nadpisać pliki poza katalogiem uploadów.

---

## Szlifowanie postów (drugie podejście)

Pierwsza wersja tablicy działała, ale w praktyce kilka rzeczy uwierało.
Poprawki i to, czego się przy nich nauczyliśmy:

### Zaokrąglenie ramki Spotify

Objaw: w rogach odtwarzacza prześwitywały białe narożniki.

`border-radius` na samym `<iframe>` przycina **ramkę**, ale nie to, co jest
w środku — strona Spotify ma własne, prostokątne tło i wystaje spod zaokrąglenia.
Rozwiązanie: otoczka z `overflow: hidden`, która przycina zawartość:

```css
.ramka-spotify { border-radius: 12px; overflow: hidden; background: #121212; line-height: 0; }
.ramka-spotify iframe { display: block; border: 0; }
```

`line-height: 0` i `display: block` usuwają kilkupikselowy pasek pod ramką —
`<iframe>` jest domyślnie elementem liniowym i przeglądarka rezerwuje mu miejsce
na „ogonki" liter (jak przy `<img>`).

### Moment utworu — czego (jeszcze) nie umiemy sprawdzić

Chcieliśmy pilnować, żeby wybrany moment nie wychodził poza długość piosenki.
**Nie da się tego zrobić dziś:** prawdziwą długość (`duration_ms`) zna tylko
Spotify Web API, a to wymaga tokenu aplikacji, którego nie mamy — integracja
ze Spotify jest zaplanowana na później. Do tego czasu sprawdzamy tylko zakres,
który ma sens dla utworu muzycznego: **0:00–30:00** (`Post.MAX_SEKUNDA_STARTU`).
Komunikat mówi wprost, jaki jest limit — „podaj wartość od 0" niczego nie tłumaczy.

Etykieta „Wybrany moment" wyleciała: odtwarzacz i tak sam pokazuje, od którego
miejsca startuje, więc powtarzanie tego obok było szumem.

### Wybieranie zdjęć partiami

Zwykły `<input type="file" multiple>` przy każdym otwarciu okna **zastępuje**
poprzedni wybór — zdjęć z dwóch folderów nie dało się dodać, a pomyłka oznaczała
zaczynanie od zera. `WybieraczZdjec` trzyma listę plików w stanie Reacta i tylko
**dokłada** do niej nowe. Dwie rzeczy, o których łatwo zapomnieć:

- **`e.target.value = ''` po każdym wyborze.** Bez tego wybranie tego samego
  pliku drugi raz (np. po usunięciu go z listy) nie wywoła `onChange` —
  przeglądarka uznaje, że wartość się nie zmieniła.
- **Duplikaty odsiewamy po nazwie i rozmiarze.** Dwa obiekty `File` wskazujące
  ten sam plik nie są sobie równe, więc `includes` nic tu nie da.

### Sterowanie karuzelą pod zdjęciem

Wbudowane strzałki Bootstrapa są białe i leżą **na** zdjęciu — na jasnym obrazku
po prostu znikają. Dołożyliśmy pasek pod karuzelą: strzałki na tle przycisku,
klikalne kropki i licznik „3 / 6". Karuzela jest przez to **sterowana**
(`activeIndex` + `onSelect`), bo stan trzyma teraz nasz komponent.

### Kto może edytować, kto usuwać

| Kto | Edycja | Usunięcie |
|-----|--------|-----------|
| autor | tak | tak |
| admin | **nie** | tak |

Świadoma decyzja: moderacja polega na **kasowaniu**, nie na przerabianiu cudzych
treści — inaczej admin mógłby podmienić komuś post i zostawić jego nazwisko pod spodem.
Backend pilnuje tego w `PostService.update` (`OperationNotAllowedException`,
odpowiedź **409**), a frontend tylko rysuje przyciski według pól `canEdit`
i `canDelete`, **wyliczonych przez serwer**. Nigdy odwrotnie — gdyby o dostępie
decydował warunek `if` w przeglądarce, wystarczyłoby go obejść narzędziami
deweloperskimi.

**Zdjęć nie da się zmienić po opublikowaniu.** Edycja idzie zwykłym JSON-em
(`PUT /api/posts/{id}`), a nie `multipart` — dokładanie i usuwanie plików
na już istniejącym poście to osobny temat i na razie go nie otwieramy.
Formularz mówi o tym wprost, zamiast milczeć.

### Pułapka przy sprawdzaniu tego w przeglądarce

Dwa testy Playwrighta „padły" i obie porażki okazały się **błędem samego testu**:

- selektor `.btn-outline-secondary` na strzałkę karuzeli łapał też przycisk
  „Edytuj" (ta sama klasa Bootstrapa),
- `.ramka-spotify` bez zawężenia do karty łapał odtwarzacz z **innego** posta
  na tej samej stronie.

Wniosek: szukaj po `aria-label` i zawsze zawężaj zapytanie do konkretnej karty.
Zanim uznasz kod za zepsuty, sprawdź, czy test na pewno patrzy tam, gdzie myślisz.

---

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
