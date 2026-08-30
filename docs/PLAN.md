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
| 6 | Całość (backend + frontend + baza) na Docker Compose | ✅ zrobione |
| 7 | Domena: znajomi ✅, muzyka w postach ✅, ulubieni artyści ⬜ | 🟡 w toku |

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
/* dzis ta klasa nazywa sie .player-frame - patrz "Nazwy w kodzie po angielsku" */
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
zaczynanie od zera. `ImagePicker` trzyma listę plików w stanie Reacta i tylko
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
- `.ramka-spotify` (dziś `.player-frame`) bez zawężenia do karty łapał odtwarzacz z **innego** posta
  na tej samej stronie.

Wniosek: szukaj po `aria-label` i zawsze zawężaj zapytanie do konkretnej karty.
Zanim uznasz kod za zepsuty, sprawdź, czy test na pewno patrzy tam, gdzie myślisz.

---

## Profile i reakcje

### Reakcje: 🔥 / 😐 / 🥱

Trzecia para relacji `OneToMany`/`ManyToOne` w projekcie: `Reaction` wskazuje
na `Post` **oraz** na `User`, a `Post` trzyma listę reakcji.

**Jedna osoba = jedna reakcja na dany post.** Pilnuje tego `UNIQUE (post_id, user_id)`
**w bazie**, nie tylko warunek w Javie. Sam kod da się obejść dwoma zapytaniami
wysłanymi w tej samej chwili: oba sprawdzą „czy już jest?", oba dostaną „nie ma"
i oba zapiszą. Baza odrzuci taki duplikat niezależnie od tego, co robi aplikacja.
Zmiana zdania podmienia `type` w istniejącym wierszu, zamiast dodawać drugi.

**`@Enumerated(EnumType.STRING)`, nie domyślny `ORDINAL`.** Przy `ORDINAL` baza
trzyma pozycję na liście (0, 1, 2) — wystarczyłoby dopisać czwartą reakcję
*w środku* enuma, żeby wszystkie dotychczasowe zmieniły znaczenie. I nikt by
tego nie zauważył, bo żadne zapytanie by się nie wywaliło. Przy okazji Hibernate
sam dokłada w PostgreSQL `CHECK (type IN ('FIRE','MID','MEH'))`.

**Liczniki: jedno zapytanie na całą stronę, nie jedno na post.** Przy dwudziestu
postach byłoby dwadzieścia dodatkowych zapytań (N+1). Zamiast tego `GROUP BY`
liczy wszystko naraz, a `SELECT new ...ReactionCount(...)` (wyrażenie
konstruktora JPQL) wrzuca wiersze prosto do rekordu — zamiast `Object[]`
i odczytywania kolumn po numerach.

**Kolekcja `reactions` w `Post` istnieje głównie dla `cascade`.** Bez niej
skasowanie posta, na który ktoś zareagował, kończy się błędem klucza obcego.
Do wyświetlania liczników jej nie używamy — właśnie przez N+1.

**`PUT` ustawia, `DELETE` cofa** — zamiast jednego „przełącznika". Dzięki temu
`PUT` jest idempotentny: wysłany dwa razy daje ten sam wynik, a nie kasuje
tego, co przed chwilą ustawił. To przeglądarka wie, czy klikam we własną
reakcję (wtedy `DELETE`), bo to ona trzyma stan ekranu.

Emotki są **wyłącznie we froncie** (`Reactions.jsx`). Backend zna tylko nazwy,
więc podmiana obrazka nie wymaga ruszania bazy.

### Publiczne profile

`GET /api/profiles/{username}` — liczba mnoga, celowo inna ścieżka niż
`/api/profile` (moje konto) i niż `/api/users/**` (panel administratora).

**Osobne DTO, nie to samo z wyciętymi polami.** `PublicProfileResponse` nie ma
e-maila ani roli. Gdyby profil publiczny używał `UserResponse`, wystarczyłoby
wejść na czyjś profil, żeby poznać jego adres. O tym, co wychodzi na zewnątrz,
decyduje **wybór klasy**, a nie `if` w środku mapowania — taki warunek łatwo
przeoczyć przy kolejnej zmianie.

**Jedna strona na profil własny i cudzy.** Różnica to kilka przycisków, więc
osobny komponent oznaczałby dwa pliki robiące prawie to samo. O tym, który
wariant widzimy, decyduje pole `self` **z serwera**.

Przy 404 pokazujemy własny komunikat („Nie znaleziono takiego użytkownika")
zamiast serwerowego „Nie znaleziono: user o identyfikatorze …" — `user`
to nazwa z kodu, a nie słowo ze świata odwiedzającego.

### Gdzie będzie edycja profilu (Spotify) — decyzja na przyszłość

Kiedy dojdzie integracja ze Spotify (wybór ulubionych artystów z top 20
i utworów z top 50), **edycja ma być na własnym profilu, nie w ustawieniach**.
Podział:

| Ekran | Co tam należy |
|-------|---------------|
| Ustawienia | sprawy konta: login, e-mail, hasło — nikt poza właścicielem tego nie widzi |
| Profil | to, co widzą inni: awatar, artyści, utwory — edycja **w miejscu** |

Powód jest praktyczny: zaznaczając artystów chce się od razu widzieć efekt na
profilu. W osobnej zakładce ustawień klika się checkboxy na ślepo i dopiero
potem sprawdza, co z tego wyszło. Dodatkowo `SettingsPage` zostaje mała,
zamiast zamienić się w worek na wszystko.

### Pułapka: `:visible` przy testach w przeglądarce

Dwa testy Playwrighta padły z powodu samych testów, nie kodu:

1. `locator('.card').filter({ hasText: 'stara treść' })` przestaje pasować
   po edycji posta — filtr szuka tekstu, który właśnie zmieniliśmy.
2. `form:has(textarea)` łapało **zwinięty** formularz „Nowy post" — `Collapse`
   z Bootstrapa zostawia go w DOM, tylko ukrytego. Ratuje `:visible`.

Ta sama lekcja co poprzednio: zanim uznasz kod za zepsuty, sprawdź, czy test
na pewno patrzy tam, gdzie myślisz.

---

## Motyw jasny / ciemny

Bootstrap 5.3 przełącza cały wygląd jednym atrybutem `data-bs-theme`
na `<html>`. Wcześniej był tam wpisany na sztywno `dark`.

**Motyw ustawia skrypt w `index.html`, nie React.** To nie jest kaprys:
React startuje dopiero po pobraniu i wykonaniu paczki JavaScriptu, więc przy
jasnym motywie strona zdążyłaby mignąć na ciemno przy każdym odświeżeniu
(tzw. *flash of wrong theme*). Skrypt w `<head>` wykonuje się, zanim
przeglądarka cokolwiek narysuje.

Podział ról:

| Kto | Za co odpowiada |
|-----|-----------------|
| skrypt w `index.html` | PIERWSZE ustawienie — zapisany wybór, a jak go nie ma, ustawienie systemu |
| `ThemeContext` | przejmuje to, co skrypt ustawił, i pozwala przełączać |

`ThemeContext` czyta stan startowy z **atrybutu**, a nie z `localStorage` —
skrypt już rozstrzygnął, co pokazać, a powtarzanie tej samej logiki w dwóch
miejscach kończy się tym, że z czasem się rozjeżdżają.

**Dopóki użytkownik nie kliknie**, chodzimy za ustawieniem systemu — także
gdy zmieni je w trakcie (`matchMedia(...).addEventListener('change')`).
Po pierwszym kliknięciu jego wybór jest ważniejszy.

Każde sięgnięcie po `localStorage` jest w `try/catch`: w trybie prywatnym
niektóre przeglądarki rzucają wyjątkiem przy samym odczycie. Wtedy motyw
po prostu nie jest pamiętany — aplikacja ma działać dalej, a nie się wywalić.

**Kolory, które trzeba było poprawić.** Reszta CSS-a korzysta ze zmiennych
Bootstrapa i przełączyła się sama, ale trzy miejsca miały kolor wpisany
na sztywno:

| Miejsce | Co zrobiliśmy |
|---|---|
| `.avatar-placeholder` (biały tekst na fioletowym kole) | zostaje — działa w obu motywach |
| `.carousel-image` (`#000`) | → `var(--bs-tertiary-bg)`, inaczej w jasnym motywie zdjęcie miałoby czarne pasy po bokach |
| `.ramka-spotify` — dziś `.player-frame` (`#121212`) | **zostaje ciemne celowo** — patrz niżej |

Ramka Spotify to jedyny kolor, który świadomie nie zmienia się z motywem.
Odtwarzacz w środku jest ciemny niezależnie od naszej strony (Spotify narzuca
swój wygląd wewnątrz `<iframe>` i nie mamy na to wpływu). Jasne tło pod nim
oznaczałoby białe rogi na ułamek sekundy przed załadowaniem ramki — czyli
dokładnie ten błąd, który naprawialiśmy wcześniej.

---

## KROK 6 — całość na Docker Compose (zrobione)

Cztery usługi: `db`, `backend`, `frontend`, `adminer`. Jedno
`docker compose up -d --build` stawia projekt — to **wymaganie nr 18**.

### Jak frontend rozmawia z backendem w kontenerze

Zbudowanego Reacta serwuje **nginx**, który przekazuje `/api` i `/uploads`
do `backend:8080`. To ta sama sztuczka, co proxy Vite w trybie deweloperskim:
przeglądarka widzi wszystko pod jednym adresem, więc ciasteczko sesji i token
CSRF działają bez kombinowania, a **CORS w ogóle nie wchodzi do gry**.

Nazwa `backend` w `nginx.conf` to nazwa usługi z `docker-compose.yml` —
Docker sam zamienia ją na adres kontenera.

W `nginx.conf` jest też `try_files $uri $uri/ /index.html`. Bez tego
odświeżenie strony pod adresem `/profil/ala` kończy się błędem 404: taki plik
nie istnieje na dysku, bo obsługa tras siedzi dopiero w JavaScripcie.

### Healthcheck — po co nam Actuator

`depends_on` bez warunku znaczy tylko „kontener wystartował", a Spring wstaje
kilkanaście sekund. Frontend ruszyłby w tej dziurze i przez chwilę oddawał
błędy. Dlatego doszedł `spring-boot-starter-actuator` i endpoint
`/actuator/health`, na który czeka `condition: service_healthy`.

Dwie rzeczy warte uwagi:

- Actuator domyślnie wystawia też m.in. listę zmiennych środowiskowych
  i całą konfigurację. Ograniczyliśmy go do jednego endpointu
  (`management.endpoints.web.exposure.include=health`) i wyłączyliśmy
  szczegóły (`show-details=never`) — na zewnątrz idzie samo `{"status":"UP"}`.
- `/actuator/health` musi być `permitAll` w `SecurityConfig`, bo Docker nie ma
  jak się zalogować. Sprawdzone: `health` zwraca 200 bez logowania,
  a `env`, `beans`, `configprops` i `mappings` — 401.

`curl` instalujemy w obrazie backendu **jawnie**, zamiast liczyć na to, że
akurat jest w obrazie bazowym. Inaczej healthcheck zgłaszałby kontener jako
chory, mimo że aplikacja działa.

### Pułapki, o których łatwo zapomnieć

1. **Wolumen na `uploads/`.** Bez niego wszystkie wgrane zdjęcia i awatary
   znikają przy `docker compose down`. Kontenery są jednorazowe — co ma
   przeżyć restart, musi leżeć na wolumenie. Osobny od wolumenu bazy, żeby
   dało się skasować dane bazy bez tracenia plików.
   **Uwaga:** `docker compose down -v` kasuje jedno i drugie.
2. **`.dockerignore`.** Docker kopiuje cały katalog projektu do demona, zanim
   zacznie budować. Bez tego pliku leciałyby tam `target/` i `node_modules/` —
   setki megabajtów przy każdym `build`, mimo że i tak są odtwarzane w środku.
3. **`.env` z hasłami**, a do repo `.env.example`. Wzór pokazuje, jakie
   zmienne trzeba ustawić, nie zdradzając żadnej prawdziwej wartości.
4. **`npm ci`, nie `npm install`** w Dockerfile — `ci` instaluje dokładnie
   wersje z `package-lock.json`. `install` może po cichu podbić wersję, przez
   co obraz zbudowany dziś różniłby się od wczorajszego z tego samego kodu.
5. **Do pracy nad kodem to zły tryb.** Każda zmiana wymaga przebudowania
   obrazu. Na co dzień: `docker compose up -d db` + backend z IntelliJ +
   `npm run dev`.

### Czego NIE dało się sprawdzić

W środowisku, w którym powstawał ten kod, **nie ma demona Dockera** —
`docker compose up` nie został uruchomiony ani razu. Sprawdzone zostało:
struktura `docker-compose.yml` (parsuje się, zależności i wolumeny na
miejscu), działanie `/actuator/health` na żywej aplikacji wraz z regułami
dostępu, oraz to, że backend i frontend budują się poprawnie.

Niesprawdzone: samo budowanie obrazów, proxy nginx między kontenerami,
healthcheck w środku kontenera i trwałość wolumenu z uploadami.
**Odpal `docker compose up -d --build` u siebie i sprawdź:** czy
http://localhost:3000 się otwiera, czy da się zalogować, czy po wgraniu
zdjęcia i `docker compose restart` zdjęcie nadal tam jest.

## Znajomi — tu domknęliśmy wymaganie nr 7

### Model

| Encja / pole | Relacja | Po co |
|---|---|---|
| `User.friends` | `User` N—N `User` (tabela `user_friends`) | **wymaganie nr 7 (ManyToMany)** |
| `FriendRequest` | N—1 `User` ×2 (nadawca, odbiorca) | zaproszenia czekające na odpowiedź |

**Znajomość zapisujemy DWOMA wierszami** (A→B i B→A). Da się inaczej — jeden
wiersz i `OR` w każdym zapytaniu — ale wtedy każde pytanie o znajomych robi
się dwa razy trudniejsze do przeczytania. Płacimy jednym wierszem za to,
że zapytania są proste.

**`FriendRequest` nie ma pola `status`.** W tabeli trzymamy wyłącznie
zaproszenia oczekujące: akceptacja dopisuje znajomość i kasuje wiersz,
odrzucenie po prostu go kasuje. Dzięki temu żadne zapytanie nie musi pamiętać
o `WHERE status = 'PENDING'` — a to jeden z tych warunków, które najłatwiej
przeoczyć i potem dziwić się, skąd na liście wzięli się ludzie, którzy nas
odrzucili. Cena: nie wiemy, kto kogo odrzucił. Do działania niepotrzebne.

**Wzajemne zaproszenie łączy od razu.** Gdy zapraszam kogoś, kto wcześniej
zaprosił mnie, nie tworzymy drugiego lustrzanego zaproszenia — przyjmujemy
tamto. Obie osoby wyraziły zgodę, więc czekanie na dodatkowe kliknięcie
byłoby bez sensu.

### Błąd, który kosztował najwięcej: pole kontra getter na proxy

Po przyjęciu zaproszenia znajomość zapisywała się **tylko w jedną stronę**.
W bazie był wiersz `ala → bob`, ale nie było `bob → ala`. Żadnego wyjątku,
żadnego ostrzeżenia — testy jednostkowe przechodziły na zielono.

Przyczyna była w tej jednej linijce:

```java
public void dodajZnajomego(User inny) {
    this.friends.add(inny);
    inny.friends.add(this);      // ŹLE
}
```

Java pozwala sięgnąć wprost do prywatnego pola innego obiektu tej samej klasy
— i właśnie na tym można się przejechać. `inny` bywa **leniwym proxy**
Hibernate'a (tak jest, gdy przychodzi z `zaproszenie.getSender()`). Odczyt
**pola** na proxy trafia do pustego pola samego proxy, a nie do prawdziwej
encji. Dopisanie znika bez śladu.

Wywołanie **metody** proxy przekazuje dalej, do właściwego obiektu:

```java
    inny.getFriends().add(this);  // DOBRZE
```

**Dlaczego testy jednostkowe tego nie złapały:** pracują na obiektach
tworzonych przez `new`, gdzie żadnych proxy nie ma. Dlatego dopisaliśmy
`FriendshipRepositoryTest` (`@DataJpaTest`), który po `entityManager.clear()`
celowo sięga po encje przez leniwe powiązanie — czyli odtwarza dokładnie tę
sytuację. Sprawdziliśmy, że po cofnięciu poprawki ten test **faktycznie pada**;
test regresyjny, który nie łapie swojego błędu, jest nic niewart.

### „Najbardziej powiązani" — jedyne zapytanie natywne w projekcie

Pasek znajomych sortujemy po liczbie **wspólnych znajomych z oglądającym**.
To kolumna, której nigdzie nie ma — liczona osobno dla każdego wiersza. JPQL
operuje na encjach i takich rzeczy nie wyrazi bez przekombinowanych sztuczek,
więc `friendsRanked` jest w czystym SQL-u (lista wymagań dopuszcza oba
warianty — nr 8).

Wynik trafia do **projekcji** `FriendRow` — interfejsu, który Spring Data
wypełnia sam po nazwach kolumn. Encja `User` nie ma gdzie przyjąć wyliczonej
liczby, projekcja — owszem. Kolumny aliasujemy jawnie (`AS avatarFileName`),
zamiast liczyć na automatyczne dopasowanie `avatar_file_name`.

**To jest miejsce, w które wepnie się Spotify.** Gdy dojdą ulubieni artyści,
do wyniku doliczymy wspólnych artystów i gatunki — zmieni się **tylko to
zapytanie**. Metoda, jej typ zwracany, DTO i cały frontend zostają nietknięte.

### Pasek zamiast karuzeli

Strzałki `‹ ›` pobierają **kolejną stronę z serwera**, a nie przesuwają to,
co już mamy — profil z dwustoma znajomymi nie ściąga dwustu kafelków na wejściu.
Bootstrapowa karuzela wymagałaby wszystkich slajdów w dokumencie od razu,
a na telefonie wymuszałaby klikanie zamiast przewijania palcem.

### Dwie pułapki z testów w przeglądarce

1. **`button:has-text("EN")` łapało też „Anuluj zaproszeni**e**"** — `has-text`
   dopasowuje fragment bez względu na wielkość liter. Ratuje `text-is()`.
2. **Test „wzajemnego zaproszenia" był źle napisany**: klikał „Zaproś" u osoby,
   która już nas zaprosiła — a tam interfejs pokazuje (słusznie!) „Przyjmij".
   Regułę trzeba było sprawdzić zapytaniem HTTP, bo w interfejsie taka
   sytuacja z definicji nie występuje.

Za każdym razem to test był zepsuty, nie kod. Ale za pierwszym razem
(jednostronna znajomość) **kod naprawdę był zepsuty** — dlatego warto
sprawdzać obie możliwości, a nie zakładać z góry którejkolwiek.

---

## Muzyka: koniec ze Spotify-centrycznością

### Błąd, który to uruchomił

Zgłoszenie brzmiało „przy albumie wywala się apka". Odtworzyłem to na żywym
stosie — i **aplikacja się nie wywalała**. Link do albumu, playlisty, podcastu
i artysty: wszystkie zwracały 201, post powstawał, zero błędu w konsoli.

Problem był subtelniejszy i gorszy. Stary `SpotifyLink` łapał **wyłącznie**
`/track/`, więc każdy inny adres był **po cichu połykany**: post pojawiał się
bez odtwarzacza, wpisany moment startu też znikał, i ani słowa wyjaśnienia.
Z punktu widzenia użytkownika to wygląda dokładnie jak zepsuta aplikacja —
i słusznie, bo to defekt. **Przyjmowanie czegoś, czego nie rozumiemy,
i milczenie o tym jest gorsze niż odmowa.**

### Model: jeden link, wiele serwisów

Zamiast `spotifyTrackId` post ma teraz `provider` + `kind` + `externalId`
(+ tytuł, miniaturkę i moment startu).

| | Spotify | YouTube | Apple Music |
|---|---|---|---|
| Utwór | ✅ | ✅ | ✅ |
| Album | ✅ | jako playlista | ✅ |
| Artysta | ✅ | — | ✅ |
| Playlista | ✅ | ✅ | ✅ |

YouTube ma **największe pokrycie** — jest tam praktycznie wszystko. Artysta
jest tam kanałem, czyli innym bytem, i nie udajemy, że umiemy go rozpoznać.

Adresy osadzenia składa **serwer** (`MusicEmbed`), nie React. Każdy serwis ma
inny format i inny parametr momentu startu (`?t=` kontra `?start=`) — gdyby ta
wiedza siedziała we froncie, dołożenie trzeciego serwisu wymagałoby zmian
w dwóch miejscach.

### Rodzaj wybiera użytkownik, my sprawdzamy zgodność

Moglibyśmy rozpoznawać rodzaj z samego adresu. Ale wtedy pomyłka kończy się
cichą niespodzianką — „wrzucałem album, a wyszedł utwór". Przy jawnym
przełączniku niezgodność to **błąd, który widać od razu**, a komunikat mówi,
*co* użytkownik wkleił, a nie tylko „zły link".

**Pole momentu startu pokazuje się wyłącznie przy utworze.** Album to wiele
nagrań, a profil artysty w ogóle nie jest nagraniem — „zacznij od 1:30" nic tam
nie znaczy. Zamiast tłumaczyć to napisem, po prostu chowamy pole.

Pilnuje tego trzecia własna adnotacja: `@ValidMusicLink` (wymaganie
nr 10). Jest klasowa, bo porównuje trzy pola naraz, i — jak
`@PasswordsMatch` — **przypina błąd do konkretnego pola**, żeby frontend
wiedział, co podświetlić.

Walidacja jest **w dwóch miejscach celowo**: w przeglądarce dla natychmiastowej
reakcji przy wpisywaniu, na serwerze bo zapytanie da się wysłać z pominięciem
przeglądarki. Ta w JavaScripcie to wygoda, nie zabezpieczenie.

### Tytuł i miniaturka: publiczny oEmbed

`open.spotify.com/oembed` i `youtube.com/oembed` są **publiczne — bez klucza
i bez tokenu**. To ważne: pełne API Spotify ogranicza aplikację w trybie
deweloperskim do **pięciu kont**, a oEmbed działa dla każdego.

Trzy decyzje warte zapamiętania:

1. **Pobieramy raz, przy dodawaniu posta.** Gdybyśmy pytali przy każdym
   wyświetleniu tablicy, dwadzieścia postów = dwadzieścia zapytań do obcego
   serwera, a tablica ładowałaby się tak wolno jak najwolniejsze z nich.
2. **Awaria serwisu nie blokuje dodania posta.** Tytuł to ozdoba — odtwarzacz
   i tak pobiera sobie wszystko sam, bo `<iframe>` ładuje się w przeglądarce.
   Każdy błąd kończy się pustymi wartościami i wpisem w logu.
3. **Krótki limit czasu (3 s).** Bez niego zawieszony serwer blokowałby wątek
   aż do limitu systemowego, a użytkownik patrzyłby w kręcące się kółko przez
   kilkadziesiąt sekund — po czym i tak dostałby błąd.

Zapytanie idzie **z serwera**: oEmbed Spotify nie wysyła nagłówków CORS, więc
wywołanie z JavaScriptu i tak by się nie udało.

### „Najczęściej wrzucane" liczone z postów

Top 5 utworów na profilu to `GROUP BY` po identyfikatorze nagrania — **bez
osobnej tabeli statystyk**. Taka tabela musiałaby być aktualizowana przy
dodaniu, edycji i usunięciu posta, czyli w trzech miejscach, z których każde
można przeoczyć. Wtedy licznik cicho rozjeżdża się z rzeczywistością i nikt
tego nie zauważa. Liczone na bieżąco zestawienie **nie ma jak skłamać**.

Działa to dlatego, że rozkładamy adres na części przy zapisie: ten sam utwór
wklejony raz przez `youtu.be`, a raz przez `youtube.com/watch` ma w bazie
dokładnie tę samą wartość.

### Migracja starych postów

`ddl-auto=update` dokłada nowe kolumny, ale **nie przenosi danych** — stare
posty straciłyby odtwarzacze. `MusicLinkMigration` przepisuje je przy
starcie. Trzy rzeczy, o które trzeba było zadbać:

- **Idempotencja.** Warunek `music_external_id IS NULL` sprawia, że drugi start
  nie rusza już przepisanych postów. Aplikacja startuje wiele razy, migracja ma
  zadziałać raz.
- **Świeża baza.** `UPDATE` odwołujący się do nieistniejącej kolumny wywaliłby
  start, więc najpierw pytamy katalog systemowy, czy stara kolumna w ogóle jest.
- **Nie kasujemy starej kolumny.** Gdyby coś poszło nie tak, dane są na miejscu.
  Po sprawdzeniu można ją usunąć ręcznie — komenda jest w komentarzu klasy.

Zweryfikowane na prawdziwej bazie: 4 stare posty przepisane, log to potwierdza.

### Pułapka: `toUpperCase()` bez `Locale`

`MusicKind.valueOf(m.group(1).toUpperCase())` wygląda niewinnie. Domyślne
`toUpperCase()` używa języka systemu, a po turecku „artist" zamienia się na
„ARTİST" z kropką nad I — i `valueOf()` rzuca wyjątkiem. Błąd ujawniający się
wyłącznie u części użytkowników, więc trudny do znalezienia. Stąd
`toUpperCase(Locale.ROOT)`.

### Czego NIE dało się sprawdzić

`open.spotify.com` i `youtube.com` są **niedostępne ze środowiska, w którym
powstawał ten kod**, więc **prawdziwe wywołanie oEmbed nie zostało wykonane
ani razu**. Sprawdzone zostało natomiast to, co ważniejsze: **przy
nieosiągalnym serwisie post i tak powstaje**, a odtwarzacz dostaje poprawny
adres. Po wgraniu łatki sprawdź u siebie, czy przy postach pojawiają się
prawdziwe tytuły — jeśli tak, oEmbed działa.

---

## Poprawki po pierwszym uruchomieniu na Dockerze

### Zdjęcia się nie ładowały — i to była moja wina w `nginx.conf`

Zgłoszenie: „nie ma dobrze wczytywanych zdjęć na profilu i ogólnie w postach".
W trybie deweperskim (Vite) wszystko działało, w Dockerze — żaden awatar
i żadne zdjęcie z posta.

Przyczyna siedziała w `frontend/nginx.conf`, w pliku, który sam napisałem.
Miałem tam dwie reguły:

```nginx
location /uploads/ { proxy_pass http://backend:8080; }
location ~* \.(js|css|png|jpe?g|svg|ico)$ { root /usr/share/nginx/html; }
```

**nginx nie wybiera reguły po kolejności w pliku.** Kolejność jest taka:
dokładne `=`, potem najdłuższy przedrostek, potem **wyrażenia regularne**,
a przedrostek jest tylko rezerwą, jeśli żadne wyrażenie nie pasuje.
Czyli `/uploads/awatar.jpg` trafiał w regułę z wyrażeniem, nginx szukał pliku
na dysku **kontenera z frontendem** — gdzie go oczywiście nie ma — i zwracał
404. Vite w trybie deweloperskim żadnego wyrażenia nie ma, więc tam problem
się nie ujawniał: klasyczna różnica „u mnie działa".

Naprawa to trzy znaki: `location ^~ /uploads/`. Przedrostek `^~` znaczy
„jeśli to pasuje, **przestań szukać** i nie sprawdzaj wyrażeń". To samo
dostało `/api/`, a regułę z cache'owaniem zawęziłem do `^/assets/`, żeby
nigdy więcej nie łapała czegoś spoza zbudowanego frontendu.

Wniosek na przyszłość: **to nie był błąd w kodzie aplikacji, tylko
w konfiguracji serwera** — i widać go wyłącznie w środowisku produkcyjnym.
Dlatego warto uruchomić `docker compose up` przed oddaniem, a nie tylko
`npm run dev`.

Poprawka została sprawdzona na uruchomionym nginxie, obie wersje obok siebie,
na prawdziwym wgranym pliku:

| Adres | stara konfiguracja | nowa konfiguracja |
|---|---|---|
| `/uploads/<plik>.png` | **404** | **200** |
| `/api/auth/me` | 401 (proxy działa) | 401 (proxy działa) |
| `/profil/ktos` (odświeżenie F5) | 200 | 200 |
| `/assets/index-*.js` | 200 + cache 30 dni | 200 + cache 30 dni |

### Drugi błąd, znaleziony dopiero na żywej bazie

Po dołożeniu `PLAYLIST` i `APPLE_MUSIC` **wszystkie 125 testów przechodziło**,
a mimo to każda próba wrzucenia playlisty albo linku z Apple Music kończyła się
błędem 500:

```
ERROR: new row for relation "posts" violates check constraint "posts_music_kind_check"
```

Przy kolumnie z `@Enumerated(EnumType.STRING)` Hibernate zakłada w bazie
ograniczenie `CHECK (music_kind IN ('TRACK','ALBUM','ARTIST'))` — z listą
wartości **z chwili zakładania kolumny**. `ddl-auto=update` dokłada nowe
kolumny i poszerza istniejące (sprawdzone: `music_external_id` faktycznie
urosło z 64 do 300 znaków), ale **raz założonego ograniczenia nie rusza
nigdy**. Baza zostaje więc przy starej liście na zawsze.

**Dlaczego testy tego nie złapały.** Testy idą na H2 z `ddl-auto=create-drop`,
więc schemat powstaje od zera przy każdym uruchomieniu — od razu z pełną listą.
Błąd wymaga bazy, która *istniała przed zmianą*. To dokładnie ta klasa błędów,
której nie da się znaleźć inaczej niż uruchomieniem aplikacji na prawdziwych
danych — i stąd wniosek ogólniejszy: **zielone testy nie są dowodem, że
aplikacja działa**, tylko że działa to, co testy sprawdzają.

Naprawia to `EnumConstraintRefresher`: przy starcie zrzuca ograniczenie
i zakłada je na nowo, biorąc listę wartości **z samej klasy enuma**. Nie ma
tu więc żadnej listy do ręcznego pilnowania — dopisanie kolejnego rodzaju
nagrania automatycznie trafi też do bazy. Gdyby `ALTER TABLE` się nie udał
(np. użytkownik bazy bez uprawnień), zostaje ostrzeżenie w logu, a aplikacja
startuje normalnie.

Test (`EnumConstraintRefresherTest`) musiał najpierw **cofnąć** ograniczenie
do starej postaci, bo inaczej na H2 nie byłoby czego sprawdzać. Sprawdza trzy
rzeczy: że stara baza faktycznie odrzuca `PLAYLIST` (czyli błąd jest odtworzony),
że po odświeżeniu ta sama baza go przyjmuje, i że **ochrona nie zniknęła** —
wartość spoza enuma nadal leci błędem. Ten trzeci przypadek jest tu po to, żeby
najprostsza „naprawa" (skasować ograniczenie i tyle) nie przeszła jako poprawna.

### YouTube Music: nie ma czego dodawać, ale czegoś brakowało

Prośba brzmiała „nie chcę linków od samego YT, chcę od YT Music".
Sprawdziłem to, zanim cokolwiek zmieniłem — i okazało się, że linki
`music.youtube.com/watch?v=…` **działały już wcześniej**, bo YouTube
i YouTube Music to **jeden serwis z jedną bazą filmów**. Ten sam
identyfikator, ten sam odtwarzacz; osobnego „embeda YT Music" po prostu
nie ma i nie da się go zrobić.

Czego naprawdę brakowało, to **albumów**. Udostępniając album z YouTube
Music dostajesz adres `music.youtube.com/playlist?list=OLAK5uy_…` — formalnie
playlistę. Ten wzorzec nie był rozpoznawany. Przy okazji doszły `shorts/`
(coraz częstsza forma udostępniania) i playlisty z samego YouTube.

Zapisujemy je **jako playlistę, a nie jako album**, chociaż dla użytkownika
to album. Powód: `list=OLAK5uy_…` to identyfikator playlisty i tylko przez
`embed/videoseries?list=` da się go odtworzyć. Udawanie w bazie, że mamy
album, skończyłoby się kłamstwem w statystykach.

### Poprawka: zwykły YouTube jednak wypada

Wcześniej napisałem, że YT i YT Music to jeden serwis, więc nie ma sensu ich
rozdzielać. To była prawda **techniczna**, ale nie o to chodziło w prośbie.
Prośba brzmiała: „daj tak, aby tylko zezwalało na linki z YT Music, a z YT
nie" — i ma to sens produktowy, którego wcześniej nie zobaczyłem.
Na zwykłym YouTube jest **wszystko**: podcasty, vlogi, gameplaye. Statystyki
gustu i dopasowywanie znajomych liczą to, co ludzie wrzucają, więc wpuszczenie
tam dowolnego filmu zamieniłoby je w szum.

Teraz `youtube.com/watch`, `youtu.be/…` i `shorts/` są **odrzucane** — ale
z podpowiedzią, co zrobić („otwórz to nagranie w YouTube Music i skopiuj adres
stamtąd"), a nie samym „zły link". Nagranie prawie zawsze jest w obu miejscach,
więc to kwestia jednego kliknięcia, a nie odmowy.

Jedna pułapka warta zapamiętania: **`music.youtube.com` zawiera w sobie
`youtube.com`**. Sprawdzanie „czy to zwykły YouTube" przez zwykłe
`contains("youtube.com")` odrzucałoby też te dobre linki. Kolejność w
`isPlainYouTube` jest więc celowa: najpierw pytamy o YT Music i wychodzimy,
dopiero potem patrzymy na resztę. Ten sam wzorzec jest po obu stronach —
w `MusicLinkParser` i w `frontend/src/utils/linkiMuzyczne.js`.

### Odtwarzacz YT Music dostaje duży ekran

Druga część tej samej prośby: „jeśli to jest YT Music, to normalnie dawaj ten
ekran jak pod YT, bo tak to się chowa jak spotify odtwarzacz, a tam przecież
leci nagranie z obrazem". Racja — pod tym adresem leci teledysk, a ramka
wysokości 152 px (dobra dla paska Spotify, gdzie i tak jest tylko okładka)
ucinała z niego prawie wszystko.

Wysokość odtwarzacza wybiera teraz `playerHeight(provider, kind)`
w `Post.jsx`. Dla YouTube **nie podajemy wysokości w pikselach w ogóle** —
otoczka dostaje bootstrapowe klasy `ratio ratio-16x9`, czyli wysokość równą
56,25% własnej szerokości. Dzięki temu film skaluje się razem z szerokością
tablicy i na telefonie nie trzeba niczego przeliczać. Spotify i Apple zostają
przy stałych wysokościach, bo tam faktycznie leci sam dźwięk.

Sprawdzone w przeglądarce: ramka wyszła 702×395 px, czyli dokładnie 16:9.

### Apple Music: tak. Tidal: nie, i to nie jest kaprys

Apple Music **da się osadzić bez żadnego klucza** — wystarczy zamienić
`music.apple.com/…` na `embed.music.apple.com/…`. Cała reszta adresu zostaje
bez zmian, dlatego przy Apple w `externalId` trzymamy **całą ścieżkę**
(`pl/album/abbey-road/1441164426`), a nie samo ID jak przy Spotify.
Jedna pułapka: adres albumu z doklejonym `?i=…` to w rzeczywistości
**pojedynczy utwór z tego albumu** — bez tego rozróżnienia każdy utwór
z Apple lądowałby w bazie jako album.

Apple **nie ma publicznego oEmbed**, więc `oEmbedUrl` zwraca tam `null`,
a `MusicMetadataService` po prostu nie dzwoni nigdzie. Post powstaje
z odtwarzaczem, ale bez zapisanego tytułu. **Wolę puste pole niż zmyślony
tytuł** — zwłaszcza że tytuł służy potem do statystyk.

### Poprawka: tytuł Apple jednak da się poznać — z adresu

Zwrócono mi uwagę, że tytuł **jest w samym linku**:
`music.apple.com/pl/song/lullaby/1440786034?l=pl` — człon `lullaby` to nazwa
utworu, a mimo to post wychodził bez podpisu.

To nie jest zgadywanie ani „wyciąganie z niczego": ten człon (slug) generuje
sam Apple z prawdziwej nazwy nagrania. `MusicEmbed.tytulZAdresu` zamienia
myślniki na spacje, dekoduje `%XX` (nazwy z ogonkami przychodzą zakodowane)
i podnosi pierwsze litery — wychodzi „Lullaby".

Jest **jeden przypadek, w którym tego nie robimy**: adres z parametrem `?i=`.
Taki link to utwór wskazany wewnątrz albumu, a slug nazywa wtedy **album**,
nie utwór — podpisanie posta nazwą albumu byłoby po prostu błędem. Wtedy
zostaje puste pole. Zasada „lepsze puste niż mylące" nadal obowiązuje, tylko
teraz dotyczy o wiele mniejszej liczby przypadków niż wcześniej.

Sprawdzone w przeglądarce na linku z prośby: pod odtwarzaczem stoi
„Apple Music · Lullaby".

Tidal odpada z konkretnego powodu: ich oficjalne osadzanie **nie jest zwykłą
ramką `<iframe>`**, tylko wymaga doładowania cudzego skryptu
(`tidal-embed.js`) na naszą stronę. To znaczy: obcy JavaScript z pełnym
dostępem do naszej strony, kolejna zewnętrzna zależność i coś, co przestanie
działać, gdy Tidal zmieni zdanie — w zamian za serwis o najmniejszym udziale
w rynku z całej czwórki. Ramka jest bezpieczna, bo działa w osobnym
„piaskownicy" przeglądarki; skrypt nie jest. **Nie warto.**

Osobno: **tego akurat nie udało mi się sprawdzić na żywo** — środowisko,
w którym powstawał kod, nie ma dostępu do `tidal.com`. Opieram się na
dokumentacji, nie na własnym teście, i tak to tu zapisuję.

### Playlisty nie liczą się do gustu

Playlista jest **czwartą** pozycją przełącznika, ale świadomie **nie wchodzi
do zestawienia „najczęściej wrzucane"**. Playlista to zwykle cudza składanka
z kilkudziesięcioma wykonawcami — z tego nie wynika, że wrzucający lubi
kogokolwiek z nich. Gdyby playlisty się liczyły, jedno wrzucenie
„Top 50 Polska" ustawiałoby czyjś profil na resztę semestru.

To jest zapisane w `MusicKind.PLAYLIST` jako komentarz, żeby przy dokładaniu
statystyk nikt (łącznie ze mną za miesiąc) nie „naprawił" tego przez pomyłkę.

---

## KROK 7 — właściwa domena aplikacji (zrobione)

Dopiero tutaj dochodzi to, o co w tej aplikacji chodzi: **poznawanie ludzi
o podobnym guście**. Do tej pory był portal z postami; teraz jest powód,
żeby na nim być.

| Encja | Relacje | Wymaganie |
|-------|---------|-----------|
| `Post` (tekst, zdjęcie, link do utworu, `createdAt`) | `Post` N—1 `User` | nr 6 (OneToMany/ManyToOne) |
| `Artist` (nazwa, zdjęcie, gatunki) | `User` N—N `Artist` (`user_favorite_artists`) | nr 7 (ManyToMany) |
| `Track` (tytuł, wykonawca, okładka) | `User` N—N `Track` (`user_favorite_tracks`) | nr 7 |

Gatunki są `@ElementCollection` przy artyście (tabela `artist_genres`),
a nie osobną encją `Genre`. Osobna encja miałaby sens, gdyby gatunek miał
własne życie — opis, stronę, relacje. U nas to zwykły napis pomocniczy przy
liczeniu dopasowania, więc druga tabela z kluczami obcymi byłaby kosztem bez
zysku.

Nie ma też encji `Match`. Wynik dopasowania **liczymy w locie zapytaniem**,
zamiast trzymać go w tabeli — inaczej po każdej zmianie ulubionych trzeba by
go przeliczać dla wszystkich par użytkowników, a nieświeży wynik jest gorszy
niż żaden.

### Skąd biorą się artyści: Deezer i Last.fm, każdy do czegoś innego

Warunek z prośby był jasny: **nie wolno dodawać nieistniejących wykonawców**,
bo to „pole do popisu dla trolli internetowych". Stąd zasada: dodać da się
wyłącznie to, co jest w katalogu. Nie ma pola „wpisz nazwę".

Podział ról:

| Serwis | Odpowiada na pytanie | Klucz | Konto użytkownika |
|---|---|---|---|
| **Deezer** | *kto to jest* — identyfikator, zdjęcie, potwierdzenie, że istnieje | nie trzeba | nie podpinamy |
| **Last.fm** | *czego ktoś słucha* — same nazwy, do jednorazowego importu | opcjonalny | tylko publiczna nazwa |

Dlaczego nie Spotify: ich API bez zatwierdzenia wniosku wpuszcza **25 kont**
wpisanych ręcznie na listę. Na projekt zaliczeniowy, który ma pokazać
działającą aplikację, to za mało — i tak wyglądałaby demonstracja z jednym
kontem. Deezer udostępnia katalog otwarcie, więc wyszukiwarka działa
u każdego od razu.

**Ochrona nie kończy się na formularzu.** Zapytanie da się wysłać
z pominięciem przeglądarki, więc metody serwisu przyjmują **wyłącznie
identyfikator** — nazwy ani zdjęcia nie ma jak przysłać, serwer pobiera je
sobie sam z katalogu. To jest sedno, dlatego pilnuje tego osobny test
(`FavoritesServiceTest#serverDoesNotTrustNameFromRequest`). Gdyby serwer przyjmował
nazwę od klienta, cała reszta byłaby ozdobą formularza.

Wiersz w `artists` jest **wspólny dla wszystkich**: drugie polubienie tego
samego wykonawcy to samo dopisanie powiązania, bez ani jednego zapytania do
sieci. Przy popularnym artyście oszczędza to tyle zapytań, ilu jest
użytkowników — i to też ma swój test.

### Tagi Last.fm to nie są gatunki

Gatunki bierzemy z `artist.gettoptags`, ale wśród najpopularniejszych tagów
Last.fm są **„seen live" i „favorites"**. Bez odsiania tego dopasowanie
łączyłoby ludzi na zasadzie „oboje byli na jakimś koncercie". Stąd lista
wykluczeń i próg popularności (tag z wynikiem 3 to zwykle etykieta wpisana
przez jedną osobę).

Drugie zaskoczenie: **Last.fm i Deezer sygnalizują błędy z kodem HTTP 200**
i polem `error` w treści. Kod patrzący tylko na status nie zauważyłby
niczego — literówka w nazwie użytkownika wyglądałaby jak „nic nie słuchasz".
Rozróżniamy przy tym dwa przypadki, bo i komunikat ma być inny: nieznany
użytkownik (`IllegalArgumentException` → „sprawdź pisownię") kontra awaria
serwisu (`IllegalStateException` → „spróbuj za chwilę").

### Proponowani znajomi — jedno zapytanie, trzy sygnały

Pomysł był Twój: pasek ze **wszystkimi** użytkownikami, posortowany od lewej
od najlepiej dopasowanych. Liczy to jedno zapytanie natywne
(`UserRepository.proponowaniZnajomi`) z trzema skorelowanymi podzapytaniami:

| Sygnał | Waga | Dlaczego tyle |
|---|---|---|
| wspólny ulubiony artysta | 5 | obie strony świadomie go wybrały |
| wspólny znajomy | 3 | mocny, ale mówi o kręgu znajomych, nie o guście |
| wspólny gatunek | 1 | „oboje słuchacie rocka" to prawie nic |

Trzy decyzje, które warto uzasadnić:

- **Na liście są wszyscy, nie tylko dopasowani.** Przy małej aplikacji filtr
  „tylko pasujący" dawałby pustą stronę dokładnie wtedy, kiedy najbardziej
  potrzeba kogoś poznać.
- **Pokazujemy powód, nie punkty.** „2 wspólnych artystów" coś znaczy;
  „13 punktów" nie znaczy nic i jeszcze zachęca do zgadywania, jak je podbić.
- **Znajomi zostają na liście**, tylko z innym oznaczeniem — inaczej pasek
  przeskakiwałby po każdym przyjętym zaproszeniu.

Notka „dodaj więcej ulubionych, a propozycje będą trafniejsze" pokazuje się
**wyłącznie wtedy, gdy nikt się nie dopasował**. Przy dobrych wynikach byłaby
zwykłym zrzędzeniem.

### Sprawdzone na działającej aplikacji

Backend: 175 testów (`mvn clean test`), w tym rozmowy z Deezerem i Last.fm na
**prawdziwym HTTP** — `TestHttpServer` oddaje odpowiedzi w formacie obu
serwisów, a adresy podstawiamy przez `@DynamicPropertySource`.

Poza testami: cała runda przeszła na **prawdziwym PostgreSQL** (curl) i w
**prawdziwej przeglądarce** (Chromium) — 23 sprawdzenia dla ulubionych
i propozycji plus 12 dla linków muzycznych. Sprawdzane były między innymi:
kolejność w pasku propozycji, podsumowanie po imporcie (ile dodano, ile
pominięto, ile już było), komunikat przy literówce w nazwie Last.fm oraz to,
że **na cudzym profilu nie ma ani krzyżyków, ani wyszukiwarki**.

---

## Nazwy w kodzie po angielsku

Projekt powstawał z polskimi nazwami zmiennych i funkcji, obok angielskich
nazw z Springa i Reacta. Efekt był taki, że w jednej linijce stały obok siebie
`post.getAuthor()` i `przygotujOpisMuzyki()`. Ta runda to porządkuje:
**wszystkie nazwy zmiennych, funkcji, klas, plików i klas CSS są po angielsku.
Komentarze i opisy testów (`@DisplayName`) zostają po polsku** — one są dla
człowieka i mają tłumaczyć, a nie brzmieć obco.

### Dlaczego nie zwykłe „znajdź i zamień"

Bo polskie nazwy zmiennych to te same słowa, których używają komentarze.
Zamiana `nazwa` na `name` w całym pliku przerobiłaby komentarz „bierzemy nazwę
z katalogu" na „bierzemy name z katalogu" — czyli zepsułaby dokładnie to, co
miało zostać nietknięte.

Narzędzie, którym to zrobiliśmy, rozbija plik na fragmenty **kodu** i **nie-kodu**
(komentarze, napisy, teksty widoczne dla użytkownika) i podmienia tylko w kodzie.
Jedyny wyjątek: nazwy **wieloczłonowe** (`dodajArtyste`, `PROG_TAGU`) zamieniamy
wszędzie — takie słowo nie występuje w polskim zdaniu, więc jeśli stoi
w komentarzu, to znaczy, że odwołuje się do kodu i ma się zmienić razem z nim.

### Trzy pułapki, które to wyciągnęło

**1. Wstawki w szablonach.** W JavaScripcie wnętrze `` `tekst ${zmienna}` ``
to kod, a nie tekst. Pierwsza wersja narzędzia traktowała cały szablon jako
napis i zostawiała w nim stare nazwy — powstawały odwołania do zmiennych,
których już nie ma. **Nie widać tego ani przy budowaniu frontendu, ani
w testach backendu**; wyszłoby dopiero użytkownikowi. Znalazł to `eslint`
z jedną regułą (`no-undef`), puszczony jednorazowo.

**2. Aliasy w zapytaniach natywnych.** Spring dopasowuje metody projekcji do
**nazw kolumn w wyniku zapytania**, więc alias w SQL-u i nazwa metody muszą się
zgadzać. Przy jednym zapytaniu alias zmienił się w `SELECT`, a w `ORDER BY`
został stary — i baza odpowiadała błędem 500 na profilu. **Testy tego nie
złapały, bo tego zapytania nie miał kto sprawdzić**: nie było na nie żadnego
testu. Doszedł `TopMusicRepositoryTest`; żeby mieć pewność, że coś naprawdę
sprawdza, ograniczenie zostało celowo z powrotem zepsute — test wtedy padł,
i dopiero po naprawie przeszedł.

**3. Nazwy pól w JSON-ie.** Zmiana nazwy pola w rekordzie DTO zmienia nazwę
pola w odpowiedzi API. Jedno takie pole (`juzByly` → `alreadyPresent`) zmieniło
się po stronie frontendu, ale nie backendu — podsumowanie importu przestało
pokazywać, ile pozycji już było na liście. Wyszło przy porównaniu **składu
wszystkich rekordów DTO przed zmianą i po niej**; od tej pory takie porównanie
jest częścią sprawdzania.

### Co dokładnie zostało sprawdzone

| Sprawdzenie | Wynik |
|---|---|
| `mvn clean test` | 179 testów |
| `eslint --rule no-undef` na całym froncie | zero błędów |
| `npm run build` | przechodzi |
| przejście po **całej** aplikacji w Chromium | 28 sprawdzeń |
| ulubieni i proponowani znajomi w Chromium | 23 sprawdzenia |
| linki muzyczne w Chromium | 12 sprawdzeń |

Przejście po całej aplikacji powstało specjalnie na tę rundę: wchodzi na każdy
ekran (także panel administratora i ustawienia) i klika w każdą ważniejszą
rzecz — dodanie posta, reakcję, edycję, zmianę hasła, wyszukiwanie kont —
traktując **każdy błąd w konsoli i każdą odpowiedź 5xx jako błąd testu**.
Przy zmianie nazw w 150 plikach to jedyny sposób, żeby zobaczyć to, czego
kompilator nie widzi.

### Czego NIE zmienialiśmy

**Ścieżek w adresach** (`/profil`, `/znajomi`). To nie są nazwy w kodzie, tylko
adresy, które użytkownik ma w pasku przeglądarki i w zakładkach. Zmiana nazwy
zmiennej nie powinna zmieniać tego, co widzi ktoś, kto z aplikacji korzysta.

**Tekstów widocznych dla użytkownika** — te i tak są w plikach tłumaczeń,
osobno po polsku i po angielsku.

---

## Moderacja: zakaz publikowania i usuwanie kont

Ostatnia rzecz, której brakowało administratorowi: dotąd mógł zmieniać role
i kasować pojedyncze posty, ale nic nie mógł zrobić z **osobą**, która
konsekwentnie zaśmieca tablicę.

### Zakaz z terminem, a nie flaga „zablokowany"

Najprostsze rozwiązanie to pole `boolean banned`. Odrzuciliśmy je, bo blokada
bezterminowa wymaga, żeby ktoś pamiętał o jej zdjęciu — a o tym zwykle nikt
nie pamięta i „tydzień przerwy" zamienia się w kasowanie konta tylnymi
drzwiami.

Zamiast tego jest `postingBannedUntil` (data i godzina). Kara **wygasa sama**:
nie ma żadnego zadania w tle ani pola do odświeżania, bo liczy się wyłącznie
porównanie z bieżącą chwilą. Wpisu po wygaśnięciu **nie kasujemy** —
administrator widzi w panelu, że ktoś był już kiedyś karany, a to bywa
ważniejsze niż sam bieżący stan.

Pytanie „czy zakaz obowiązuje" zadajemy **encji** (`user.isBanned(kind)`),
a nie porównujemy dat w serwisie. Inaczej ta sama reguła („null albo
przeszłość znaczy: wolno") musiałaby być powtórzona w każdym miejscu, które
jej pilnuje — a wystarczy pomylić się raz, żeby zakaz dało się obejść.

### Co dokładnie obejmuje kara

| Czynność | Podczas zakazu |
|---|---|
| dodanie posta | ❌ |
| **edycja** własnego posta | ❌ |
| usunięcie własnego posta | ✅ |
| reakcje, czytanie, znajomi | ✅ |

Edycja jest zablokowana celowo. Bez tego zakaz nie znaczyłby nic:
wystarczyłoby wejść w dowolny stary post i podmienić w nim całą treść.
Usuwanie własnych postów zostaje dozwolone — kara ma powstrzymać przed
publikowaniem, a nie zmusić do zostawienia czegoś na tablicy.

Ukarany dostaje komunikat z **terminem** końca kary. Samo „nie wolno" byłoby
dla niego bezużyteczne: nie wiedziałby, czy wrócić za godzinę, czy za tydzień.

### Usunięcie konta: samo `delete()` nie wystarczy

Na koncie wisi sześć rodzajów wierszy w pięciu tabelach. Hibernate sam
usunie tylko część z nich, a baza odmówi skasowania reszty z powodu klucza
obcego. Kolejność jest obowiązkowa:

1. reakcje tej osoby pod **cudzymi** postami (same posty zostają),
2. własne posty — kaskada zabiera ich zdjęcia i cudze reakcje pod nimi,
3. zaproszenia, w których występuje po **dowolnej** stronie,
4. znajomości — **w obie strony**,
5. ulubieni (tu strona właścicielska wystarcza),
6. dopiero na końcu samo konto.

**Punkt 4 to pułapka.** Znajomość zapisujemy dwoma wierszami, żeby dało się ją
czytać w każdą stronę jednym zapytaniem. Przy kasowaniu konta Hibernate
sprząta tylko te wiersze, w których ta osoba jest właścicielem relacji.
Gdyby została druga połowa, jej znajomi mieliby na liście kogoś, kogo już
nie ma — i każde wyświetlenie tej listy kończyłoby się błędem.

Kasujemy też **pliki z dysku**: awatar i zdjęcia z postów. Konto usunięte
z bazy, ale z fotografiami leżącymi dalej na serwerze, to usunięcie tylko
na niby.

### Błąd, który znowu wyszedł dopiero w przeglądarce

Zakaz działał: post się nie zapisywał, wyjątek leciał, test serwisu był
zielony. A użytkownik zamiast komunikatu dostawał **błąd 500**.

Powód: komunikat miał w treści `{0,date,dd.MM.yyyy}`, a `MessageFormat`
(którego używa `MessageSource`) potrafi sformatować `java.util.Date`, ale
**nie** `LocalDateTime`. Rzucał więc wyjątkiem **w środku obsługi błędu** —
czyli dokładnie tam, gdzie nie ma już komu go przechwycić.

Test sprawdzający typ wyjątku nie miał szans tego zauważyć, bo wyjątek był
w porządku; zepsute było dopiero to, co z niego wynika. Datę formatujemy
teraz w Javie, a doszedł `ErrorMessagesTest`, który **składa każdy komunikat
tej klasy w obu językach** — z terminem włącznie. Brakujące tłumaczenie
albo zły zapis formatu nie przejdzie już niezauważony.

### Czego administrator nie może zrobić sobie

Usunąć własnego konta i nałożyć na siebie zakazu. Powód ten sam co przy
zmianie własnej roli: jedno kliknięcie nie może zostawić portalu bez nikogo,
kto ma do niego dostęp. Login wykonującego bierzemy **z sesji, nigdy
z zapytania** — inaczej blokadę dałoby się obejść, podając cudzy login.

### Sprawdzone

197 testów backendu, w tym `UserDeletionTest` na prawdziwej bazie (sprząta
naprawdę, czy tylko woła odpowiednie metody) i 16 sprawdzeń w przeglądarce:
nałożenie zakazu, nieudana próba publikacji z komunikatem i terminem,
zdjęcie zakazu, ponowna publikacja, okno potwierdzenia przy usuwaniu oraz to,
że po usunięciu konta **jego posty znikają z tablicy**.

---

## Porządki w interfejsie

Runda bez nowych funkcji: to samo, co było, ma być wygodniejsze.

### Menu z czterema pozycjami prowadzącymi donikąd

Pasek na górze miał: Strona główna, Tablica, Profil, Znajomi, Ustawienia
(+ Użytkownicy u administratora) — i osobno awatar prowadzący na profil.
Dwa problemy naraz: „Strona główna" pokazywała ekran powitalny z zapowiedzią
kolejnych kroków (nikt tam nie wchodzi drugi raz), a „Profil" i awatar
prowadziły w to samo miejsce.

Teraz **stroną główną jest tablica**, a wszystko, co dotyczy własnego konta,
siedzi w rozwijanym menu pod awatarem. W pasku zostają wyłącznie miejsca
wspólne dla wszystkich. `HomePage` zniknął razem z kluczami tłumaczeń,
które go opisywały.

Stary adres `/feed` **zostaje jako przekierowanie**. Ktoś może go mieć
w zakładkach albo w wysłanym komuś linku — pusta strona byłaby tu najgorszą
możliwą odpowiedzią.

### Przyklejony pasek

`sticky-top` plus półprzezroczyste tło z rozmyciem. Rozmycie nie jest
ozdobą: przy w pełni nieprzezroczystym pasku nie widać, że coś pod nim
przewija się dalej, a przy w pełni przezroczystym napisy w menu robią się
nieczytelne nad treścią. Przeglądarka bez obsługi `backdrop-filter` dostaje
pełne tło — inaczej wypadłaby na najgorszy z tych trzech wariantów.

Cień pod paskiem pojawia się **dopiero po przewinięciu**. Przy stronie
przewiniętej na samą górę wyglądałby jak przypadkowa kreska.

### Jeden komponent na trzy poziome listy

Ulubieni artyści, ulubione utwory i proponowani znajomi to teraz ten sam
`HorizontalStrip`. Wcześniej ulubieni byli zawijaną siatką, która przy
dwudziestu pozycjach rozpychała profil na kilka ekranów w dół.

Trzy decyzje w tym komponencie:

- **Strzałki, a nie samo przewijanie.** Palcem przewija się naturalnie,
  myszką już nie — pasek bez widocznego sterowania wygląda jak lista, która
  się urywa.
- **Strzałki tylko wtedy, gdy jest co przewijać**, i wygaszone na końcach.
  Wygaszona zostaje na miejscu, zamiast znikać: gdyby znikała, pasek
  przeskakiwałby w bok przy każdym dojechaniu do końca.
- **Miękkie wygaszenie przy krawędziach.** Mówi, że treść biegnie dalej,
  i daje strzałce tło, przez które nie przebija się okładka. Musi mieć
  `pointer-events: none`, inaczej przezroczysta warstwa przechwytywałaby
  kliknięcia w pierwszy i ostatni kafelek.

Stan strzałek odświeża `ResizeObserver`, a nie samo nasłuchiwanie
przewijania. Lista zmienia się także przy zwężeniu okna i po dodaniu
pozycji — bez tego strzałka potrafiła zostać aktywna, choć nie było już
czego przewijać.

### Pułapka: `scroll-snap` kontra „początek listy"

Pasek nigdy nie stał dokładnie na zerze — startował na `scrollLeft = 2`,
przez co lewa strzałka wyglądała na aktywną od samego początku. Winne było
`scroll-snap-type` w połączeniu z poziomym `padding` toru: kafelek
„przyklejał się" do krawędzi *pola treści*, przesuniętej o te dwa piksele.

Poprawia to `scroll-padding-inline` równe temu paddingowi. **Wyszło to
dopiero w przeglądarce** — z samego CSS-u nie widać, że dwie reguły ustawione
niezależnie od siebie się gryzą.

### Warstwa wizualna

Delikatna poświata w kolorze wiodącym pod nagłówkiem, łagodniejsze
zaokrąglenia kart, przejścia na przyciskach i awatarach, wejście kart na
tablicy, krzyżyk usuwania ulubionego pokazywany dopiero pod kursorem.

Jedna reguła jest ważniejsza niż cała reszta: **`prefers-reduced-motion`
wyłącza wszystkie animacje**. Dla części osób ruch na ekranie oznacza
zawroty głowy albo mdłości, a system ma na to osobne ustawienie — wystarczy
je uszanować. Reguła stoi na początku pliku, żeby żadna późniejsza jej nie
przesłoniła.

Żadna z tych reguł nie zmienia **układu** strony — wszystkie dotyczą
wyłącznie wyglądu, więc nic nie może przez nie zniknąć z widoku.

### Sprawdzone

Doszedł `sprawdz-interfejs.mjs`: 16 sprawdzeń rzeczy, których nie da się
sprawdzić inaczej niż w przeglądarce — czy pasek naprawdę zostaje na górze
po przewinięciu (z osobnym sprawdzeniem, że strona faktycznie się
przewinęła), czy `/feed` przekierowuje, czy menu konta ma wszystkie trzy
pozycje i czy strzałka faktycznie zmienia `scrollLeft`. Razem z wcześniejszymi
skryptami daje to **95 sprawdzeń** w Chromium przy 197 testach backendu.

---

## Wygląd: od „płaskiego" do czegoś z głębią

Uwaga brzmiała: wszystko jest minimalistyczne i płaskie. Trafna — i warto
nazwać powód, bo nie był oczywisty.

### Płaskości nie da się naprawić cieniami

Tło strony było białe. Karty też były białe. **Nie było czego od czego
odróżnić**, więc żaden cień nie mógł tego uratować — cień pod białą kartą na
białym tle to szara smuga, a nie głębia.

Naprawa jest jednym wierszem i to ona zrobiła największą różnicę: tło dostało
lekki odcień, a karty zostały przy czystym `--bs-body-bg`. Teraz to karty są
„nad" stroną, a nie odwrotnie. W ciemnym motywie ta sama zasada działa
odwrotnie: tło jest **ciemniejsze** niż powierzchnie, a nie jednolicie szare.

Do tego cienie warstwowe — bliższy i ostry plus dalszy i rozmyty. Pojedynczy
cień wygląda jak naklejka; dopiero dwa dają wrażenie uniesienia. W ciemnym
motywie muszą być **mocniejsze**, nie słabsze, bo czarny cień na ciemnym tle
prawie nie istnieje.

### Jeden gradient zamiast pięciu fioletów

Wszystkie kolory, cienie i czasy przejść siedzą teraz w jednym zestawie
zmiennych na górze `styles.css`. Gradient marki (fiolet → fuksja → róż)
pojawia się w logo, głównym przycisku, aktywnej ikonie menu, obrączce
własnego awatara i wskaźniku języka — dzięki jednemu źródłu te miejsca czytają
się jako jedna rodzina.

Kolor dopisany „na oko" w jednym miejscu jest tańszy w tej chwili, ale po
kilku takich okazuje się, że fiolet w aplikacji ma pięć odcieni i żaden nie
pasuje do pozostałych.

### Pasek: trzy kolumny i same ikony

Ikony są wyśrodkowane **względem strony**, a nie względem tego, co zostało po
bokach — stąd siatka `1fr auto 1fr`, a nie `mx-auto`. Przy różnej długości
loginu ikony przesuwałyby się u każdego inaczej.

Zamiana pozycji na ikony pozwoliła **wyrzucić „hamburgera"**: trzy ikony
mieszczą się nawet na telefonie, a chowanie nawigacji za dodatkowym
kliknięciem tylko by ją oddaliło. Aktywna pozycja dostaje gradient — przy
samych ikonach to jedyne, co mówi, gdzie się jest.

### Przełączniki, które pokazują, co się stało

Język: jedna pigułka z **jednym** wskaźnikiem, który przesuwa się pod wybraną
opcję. Dwa osobne przyciski nie mówiły, że wybór jest jeden z dwóch —
wyglądały jak dwie niezależne akcje. Szerokość wskaźnika liczy się z liczby
opcji, więc dołożenie trzeciego języka nie wymaga ruszania CSS-a.

Motyw: słońce i księżyc **obie są w DOM-ie przez cały czas**, jedna nad drugą,
i wymieniają się obrotem. Gdyby React podmieniał je warunkowo, nie byłoby
czego animować — element znika i pojawia się nowy, więc przejście nie ma
punktu zaczepienia.

### Błąd, który przy okazji wyszedł: sprawdzenia „na pusto"

Przy dokładaniu postów do zrzutów ekranu okazało się, że
`POST /api/posts` przyjmuje **multipart**, a nie JSON (bo obok treści idą
zdjęcia). Skrypty sprawdzające wysyłały JSON i dostawały 415 — czego nie
sprawdzały. Skutek: post nigdy nie powstawał, a dwie asercje przechodziły
**na pusto**:

- „posty usuniętego konta znikły z tablicy" — nie było czego usuwać,
- „przy cudzym poście nie ma przycisku Edytuj" — nie było cudzego posta.

Obie wyglądały na zielone i obie nic nie sprawdzały. Pomocnik `wyslijPost`
wysyła teraz multipart i **rzuca wyjątkiem przy statusie innym niż 201** —
to ta druga część jest istotna: bez niej ten sam błąd wróciłby przy
następnej zmianie API.

### Dlaczego nie było przycisku importu z Last.fm

Bo nie był ustawiony `LASTFM_API_KEY` — i tak to było zaprojektowane:
bez klucza przycisk się nie pokazuje, żeby nikt nie klikał w coś, co zawsze
kończy się błędem.

Tyle że **sam brak przycisku nie jest informacją, tylko zagadką**. Właściciel
profilu widzi teraz krótką notkę, że import jest wyłączony, a administrator
dostaje dodatkowo nazwę zmiennej — bo tylko on może to włączyć. Zwykłemu
użytkownikowi nazwy zmiennej nie pokazujemy; osobne sprawdzenie pilnuje,
żeby to się nie zmieniło przez przypadek.

---

## Powiadomienia

Do tej pory nie dalo sie zauwazyc, ze cos sie w aplikacji wydarzylo. Reakcja
pod postem, zaproszenie do znajomych, przyjecie zaproszenia — wszystko to
trzeba bylo znalezc samemu.

### Powiadomienie, ktore nigdzie nie prowadzi, jest bezuzyteczne

To zalozenie ustawilo caly projekt tego mechanizmu. Kazdy rodzaj powiadomienia
ma przypisane miejsce docelowe:

| Zdarzenie | Prowadzi do |
|---|---|
| reakcja na Twój post | **tego konkretnego posta** (`/post/{id}`) |
| nowe zaproszenie | strony znajomych |
| przyjęte zaproszenie | profilu tej osoby |

Reakcja **nie** prowadzi na tablicę: post może być setny od góry, więc
odesłanie na tablicę znaczyłoby „poszukaj sobie". Stąd wzięła się też nowa
strona `/post/{id}` i endpoint `GET /api/posts/{id}` — wcześniej nie było jak
pokazać jednego wpisu.

**Adres wylicza serwer**, nie frontend. Gdyby robił to frontend, przy każdym
nowym rodzaju powiadomienia trzeba by pamiętać o dopisaniu warunku w drugim
miejscu — a wystarczy raz zapomnieć, żeby powstało powiadomienie prowadzące
donikąd.

### Trzy reguły, bez których dzwonek przestaje cokolwiek znaczyć

Wszystkie siedzą w jednym `NotificationService`, a nie w serwisach, które go
wołają — inaczej każdy z trzech musiałby je powtarzać u siebie.

1. **Reakcja na własny post nie powiadamia.** Wiadomo, co się samemu zrobiło.
2. **Zmiana zdania odświeża wpis zamiast dokładać drugi.** Jedna osoba
   klikająca kolejno trzy emotki zostawia jedno powiadomienie, nie trzy.
3. **Cofnięta reakcja zabiera swoje powiadomienie**, tak samo jak odrzucone
   albo przyjęte zaproszenie. Inaczej klik prowadziłby do czegoś, co się
   już „odstało".

### Klucz obcy zamiast luźnego numeru

`Notification.post` to prawdziwa relacja, a nie kolumna `Long`. Różnica jest
istotna: baza sama pilnuje, że powiadomienie nie wskaże posta, którego już
nie ma. Kosztuje to jedną linijkę sprzątania przed usunięciem posta —
i **właśnie ta linijka wyciągnęła istniejący od dawna błąd** (niżej).

### Błąd, który przy okazji wyszedł: nie dało się usunąć posta z reakcjami

Pierwszy test usuwania posta z powiadomieniem wywalił się nie na
powiadomieniach, tylko na **reakcjach**: baza odmawiała skasowania posta,
pod którym ktoś zareagował.

Encja ma `cascade = ALL` na reakcjach, więc wyglądało to na załatwione.
Tyle że **kaskada opiera się na kolekcji załadowanej do pamięci**, a reakcja
dopisana w tej samej transakcji przez `reactionRepository.save(...)` do niej
nie trafia — Hibernate o niej nie wie.

Testy na atrapach nie miały szans tego zobaczyć: atrapa repozytorium zgadza
się na wszystko. Wychodzi to dopiero na prawdziwej bazie. Naprawia to jawny
`DELETE`, a pilnuje osobny test
(`UserDeletionTest#postWithSomeoneElsesReactionsCanBeDeleted`).

### Reakcja to nie edycja posta

Kliknięcie emotki pod **cudzym** postem pokazywało komunikat „Post został
zaktualizowany" — czyli aplikacja twierdziła, że zmieniliśmy cudzą treść.

Powód był prosty: `Post` dostawał jedno wywołanie `onUpdate` i używał go do
dwóch różnych rzeczy — „post został wyedytowany przeze mnie" i „zmieniły się
liczniki reakcji". Teraz to dwa osobne wywołania; reakcja odświeża kartę
bez żadnego komunikatu.

### Kto zareagował

Podsumowanie pod postem („3 reakcje") jest teraz przyciskiem otwierającym
okienko z listą osób, pogrupowaną po rodzaju reakcji. Liczba mówi ILE osób,
ale nie mówi KTO — a przy paru reakcjach to właśnie druga rzecz jest ciekawa.

Listę pobieramy **dopiero po otwarciu**: gdyby każdy post na tablicy ciągnął
ją od razu, dwadzieścia postów oznaczałoby dwadzieścia dodatkowych zapytań
po to, żeby pokazać coś, w co prawie nikt nie kliknie.

### Wyścig, który wracał kropką na dzwonek

Otwarcie powiadomienia zmniejsza licznik od razu, a oznaczenie „przeczytane"
szło początkowo w tle. Efekt: przejście pod nowy adres uruchamiało ponowne
pobranie licznika, serwer nie zdążył jeszcze zapisać oznaczenia i wracała
**stara liczba** — kropka wracała na dzwonek zaraz po tym, jak z niego znikła.

Teraz na oznaczenie czekamy przed przejściem. Błąd przy oznaczaniu nie może
jednak zablokować przejścia: nieprzeczytany wpis jest mniejszym problemem
niż kliknięcie, które nigdzie nie prowadzi.

### Płynność

- **Ikony w pasku** przełączały się skokowo, bo aktywna dostawała gradient
  przez podmianę `background-image` — a tego przeglądarka nie animuje.
  Gradient siedzi teraz w warstwie pod ikoną i wygasza się przezroczystością.
- **Posty wchodzą po kolei**, z opóźnieniem liczonym od początku *partii*,
  a nie całej listy — inaczej dwudziesty post czekałby prawie sekundę.
- **Doładowywanie** ma trzy pulsujące kropki zamiast kółka: to moment,
  w którym coś *dokłada się* do listy, a nie zwykłe ładowanie strony.

### Migotliwe sprawdzenie to fałszywy alarm

Przy sześciu skryptach pod rząd jedno sprawdzenie („czy na cudzym profilu
jest przycisk zaproszenia") zaczęło raz przechodzić, raz nie — stan
znajomości dociąga się osobnym zapytaniem i przy obciążonej maszynie nie
zdążył. Zamiast wydłużać pauzę, sprawdzenie **czeka na przycisk**.
Sprawdzenie, które miga, uczy ignorować czerwone wyniki — a to gorsze niż
jego brak.

### Sprawdzone

209 testów backendu (w tym 11 na same powiadomienia, na prawdziwej bazie)
i 117 sprawdzeń w Chromium. Doszedł `sprawdz-powiadomienia.mjs`: 21 asercji,
w których najważniejsze są te o **przenoszeniu we właściwe miejsce** —
reakcja do posta, zaproszenie na stronę znajomych, przyjęcie na profil.

---

# KROK 9 — kto co widzi, gablotka playlist i puste stany

## Widoczność postów

Post jest **publiczny** albo **tylko dla znajomych** — wybór przy pisaniu,
zmienialny później przy edycji (ze świadomością, że działa to tylko na
przyszłość: kto przeczytał, ten przeczytał).

**Domyślnie publiczny.** Aplikacja służy do poznawania *nowych* ludzi
o podobnym guście; domyślne ukrywanie wpisów przed wszystkimi poza obecnymi
znajomymi działałoby przeciwko temu, po co ona jest.

**Dwie wartości, nie pięć.** Kusiło, żeby dołożyć „tylko ja" i „znajomi
znajomych", ale każda kolejna możliwość to kolejna reguła do pilnowania
w *każdym* zapytaniu o posty — a użytkownik i tak musi za każdym razem
zdecydować, którą wybrać.

### Kolumna dokładana do tabeli z danymi

`visibility` jest w bazie **nullowalna**, choć w Javie nigdy nie jest pusta.
Powód jest konkretny: Hibernate w trybie `ddl-auto=update`, dokładając do
tabeli z wierszami kolumnę `NOT NULL`, dostaje od bazy odmowę — kolumna
w ogóle nie powstaje, a wtedy przestaje działać *każde* zapytanie o posty.
Kolumna wchodzi więc nullowalna, a stare wiersze uzupełnia
`PostVisibilityMigration` przy pierwszym starcie (wszystkie jako publiczne,
bo takie były w chwili pisania — ustawienie ich na „tylko znajomi" zmieniałoby
decyzję ich autorów, a nie odtwarzało).

Doszła też pozycja w `EnumConstraintRefresher`. Dziś jest niepotrzebna
(kolumna dopiero powstaje, więc ograniczenie `CHECK` jest poprawne) — chodzi
o dzień, w którym dojdzie trzecia wartość i nikt nie będzie pamiętał,
że trzeba tam zajrzeć.

### Reguła obowiązuje wszędzie

Ukrycie posta na tablicy to nie jest zabezpieczenie. Identyfikatory są
kolejnymi liczbami, więc bez sprawdzenia w `PostService.getOne`
i `ReactionService` wystarczyłoby wpisać adres z ręki albo wysłać `PUT`
z pominięciem przeglądarki. Liczbę postów na profilu też liczymy **dla
konkretnego oglądającego** — napis „2 posty" nad jednym wpisem wygląda jak
zepsuta strona.

## Tablica: najpierw znajomi

`ORDER BY CASE WHEN autor ∈ mój_krąg THEN 0 ELSE 1 END, data DESC` —
sortowanie po kolumnie, której w tabeli nie ma.

**Dlaczego nie przesiać tego w Reakcie.** Bo wtedy stronicowanie zaczyna
kłamać: każda strona zawierałaby inny zestaw wpisów, a licznik stron liczyłby
coś innego niż to, co widać. Ta sama myśl co przy „proponowanych znajomych".

Sortowanie przekazane z kontrolera **odrzucamy** na samej tablicy. Kolejność
nie jest tu ustawieniem użytkownika, tylko treścią funkcji; gdyby przepuścić
`Sort` z adresu, Spring Data dokleiłby je do `ORDER BY` z zapytania i wyszłaby
kolejność, której nikt nie zamawiał. Parametr `direction` działa dalej tam,
gdzie ma sens — przy postach jednego autora.

**„Mój krąg"** (`UserRepository.circleIds`) to jedno pojęcie załatwiające trzy
sprawy: kolejność, dostęp do postów dla znajomych i zawężenie tablicy. Własny
identyfikator jest w nim celowo — także dlatego, że `IN ()` z pustą listą jest
w SQL-u błędem składni i wywracałoby tablicę użytkownikowi bez znajomych.

Postulat brzmiał: „posty obcych albo pod spodem, albo wcale". To dwie różne
odpowiedzi na dwie różne sytuacje, więc jest to **przełącznik**, a nie decyzja
podjęta za użytkownika. Domyślnie szeroko: konto założone przed chwilą nie ma
ani jednego znajomego.

## Gablotka playlist

Do pięciu playlist na profilu. Osobna encja `FavoritePlaylist`, a nie kolejne
pole przy użytkowniku — playlista ma serwis, identyfikator, tytuł, okładkę
i miejsce w kolejności, a usuwanie z gablotki potrzebuje odwołania do wiersza
po identyfikatorze.

**Osobny serwis, nie `FavoritesService`.** Tamten pilnuje jednej twardej
zasady: do ulubionych trafia wyłącznie to, co istnieje w katalogu Deezera.
Playlista przez ten katalog przejść nie może i **celowo nie liczy się do
żadnego dopasowania**. Wciśnięcie tego do tamtej klasy oznaczałoby wyjątek od
jej jedynej zasady — a wyjątek od zasady to najkrótsza droga do tego, żeby
przestała obowiązywać.

Ograniczenie `UNIQUE` obejmuje **parę właściciel–playlista**, a nie samą
playlistę: inaczej pierwsza osoba, która wystawi popularną składankę,
zablokowałaby ją wszystkim pozostałym.

## Reakcje bez przeładowania

`GET /api/posts/reactions?ids=…` oddaje same liczniki dla postów, które są na
ekranie. Wywołujemy to po powrocie do karty i co 45 sekund przy widocznej
karcie. Bez WebSocketa (stałe połączenie plus rozgłaszanie zdarzeń to spory
kawałek maszynerii do utrzymania — a chodzi o kilka liczb) i bez pobierania
tablicy od nowa (to przestawiłoby widok i zgubiło miejsce, w którym ktoś
czytał).

Nowy adres stoi obok `GET /api/posts/{id}` i oba wzorce mają dwa człony.
Spring wybiera ten z dosłownym członem — ale gdyby kiedyś przestał, objawiłoby
się to błędem 400 przy odświeżaniu liczników, czyli w miejscu, którego nikt by
z tym nie połączył. Stąd `PostControllerRoutingTest`; sprawdziliśmy, że po
zmianie adresu faktycznie czerwienieje.

## Puste stany i szkielety

Cztery różne szare linijki zastąpił jeden komponent `EmptyState`: ikona,
co tu będzie, dlaczego jeszcze tego nie ma i **jedno konkretne działanie**.
Pusto to nie awaria, tylko początek — i wtedy właśnie aplikacja ma jedyną
okazję powiedzieć, co dalej.

Przy pierwszym ładowaniu tablicy i profilu idą **szkielety postów** zamiast
kółka: zajmują to samo miejsce co prawdziwe wpisy, więc po wczytaniu nic nie
skacze. Przy doładowywaniu zostają trzy kropki — tam jest już co oglądać,
a pół ekranu szarych prostokątów wyglądałoby jak awaria.

## Sprawdzone

237 testów backendu (28 nowych: widoczność i kolejność tablicy na prawdziwej
bazie, gablotka playlist, kierowanie adresów) oraz nowy zestaw
`sprawdz-widocznosc.mjs` — **35 sprawdzeń w Chromium**, przepuszczony dwa razy
z tym samym wynikiem. Doszły do tego zrzuty ekranu do README, robione tą samą
drogą: prawdziwa przeglądarka na danych zakładanych przez API.

**Czego nie dało się sprawdzić w tej rundzie.** Kontener, w którym powstawała
ta runda, nie ma dostępu do Deezera — a bez niego nie da się dodać ulubionego
artysty, więc `sprawdz-ulubione.mjs` i trzy sprawdzenia z pozostałych
zestawów nie mają na czym pracować. Sama logika ulubionych ma pokrycie
w 11 testach `FavoritesServiceTest`, które chodzą na własnym serwerze HTTP,
i nic w tej rundzie jej nie dotykało — ale uczciwiej jest to zapisać niż
podać liczbę, która sugerowałaby, że sprawdziliśmy wszystko.

---

# KROK 10 — poprawki po pierwszym prawdziwym użyciu

Runda zgłoszona po tym, jak aplikacja została rzeczywiście poklikana.
Wszystkie punkty to albo błędy, albo rzeczy, które *działały*, ale
przeszkadzały.

## Błąd: pierwsze kliknięcie „Zaloguj" po wylogowaniu

Objaw: po wylogowaniu pierwsze logowanie kończyło się komunikatem
„Wystąpił nieoczekiwany błąd", a **drugie działało**. Wyglądało to na
losową usterkę, a było w pełni powtarzalne.

Przyczyna: wylogowanie w Spring Security **kasuje ciasteczko XSRF-TOKEN**
(robi to `CsrfLogoutHandler` — i słusznie, bo token należał do poprzedniej
sesji). Frontend nie pobierał go ponownie, więc następny `POST` szedł bez
tokenu i serwer go odrzucał. Dopiero odpowiedź z błędem zakładała nowe
ciasteczko — stąd „za drugim razem działa".

Naprawa dwustopniowa, bo to dwie różne rzeczy:
1. `AuthContext` pobiera nowy token **zaraz po wylogowaniu** — to usuwa
   przyczynę w jedynym miejscu, w którym powstaje;
2. przechwytywacz w `client.js` powtarza **raz** zapytanie, które poszło
   bez ciasteczka i zostało odrzucone — to siatka na wszystkie pozostałe
   sposoby utraty tokenu (wygaśnięcie, wyczyszczenie danych strony, druga
   karta, która się wylogowała).

Powtarzamy **wyłącznie** wtedy, gdy ciasteczka w chwili wysyłki w ogóle nie
było. Złe hasło daje ten sam kod odpowiedzi, ale ciasteczko jest wtedy na
miejscu — taki błąd przechodzi do użytkownika nietknięty.

## Pasek przewijania przesuwał całą stronę

Objaw: przy przechodzeniu między stronami treść przeskakiwała o kilkanaście
pikseli w bok. To nie było drganie interfejsu, tylko zwykła zmiana szerokości
okna: strona bez paska przewijania jest po prostu szersza niż ta z paskiem.

Rozwiązanie po kilku pomiarach: `html { overflow-y: scroll }` — pasek jest
**obecny zawsze**, więc nic nigdy nie znika. Pierwsza próba
(`scrollbar-gutter: stable`, czyli sama rezerwacja miejsca) załatwiała
przechodzenie między stronami, ale nie okienka: `overflow: hidden`, które
Bootstrap ustawia na `<body>`, zabiera pasek **razem z zarezerwowanym
miejscem** — zmierzone, szerokość treści wracała wtedy z 1270 na 1280 px.

Do tego trzeba było wyzerować dwa wyrównania, które Bootstrap dokłada przy
otwarciu okienka: margines na `<body>` i parę
`padding-right: 10px` / `margin-right: -10px` na pasku przyklejonym do góry.
To drugie zwężało **wnętrze** paska, przez co logo przeskakiwało o 5 px.
Przy zawsze obecnym pasku nie ma czego wyrównywać.

**Sprawdzenie tego było trudniejsze niż sama poprawka.** Pierwsza wersja
testu przechodziła także z wyłączoną poprawką — obie porównywane strony
okazały się dłuższe od okna, więc pasek był na obu. Teraz test wymusza stan
„bez paska" wprost i sprawdziliśmy, że czerwienieje po cofnięciu zmiany.

## Ikony z Bootstrap Icons

Ikona znajomych wyglądała na przyciętą, a grubości kresek nie zgadzały się
między ikonami — bo wszystkie były rysowane ręcznie. Przy pięciu to
wystarczało, przy dwudziestu czterech zaczęło się rozjeżdżać.

Teraz kształty pochodzą z **Bootstrap Icons** (MIT) — oficjalnego zestawu
Bootstrapa, którego używamy już do reszty wyglądu. Ścieżki są przepisane
do `Icons.jsx` skryptem (nie ręką), więc **nie ma zależności do paczki**:
biblioteka ma ponad dwa tysiące ikon, a używamy dwudziestu czterech.

Przy okazji wyszedł osobny błąd: aktywna ikona w menu była w jasnym motywie
**czarna na fioletowym tle**. Reguła Bootstrapa
`.navbar-nav .nav-link.active` jest bardziej szczegółowa niż nasza
`.nav-icon.active` i wygrywała mimo późniejszego wczytania arkusza.
W ciemnym motywie nie było tego widać, bo kolor Bootstrapa jest tam prawie
biały.

## Co Was łączy

Aplikacja umiała policzyć, **ile** ktoś ma z kimś wspólnego. Teraz mówi,
**co**: wspólne gatunki, artystów, utwory i znajomych — z nazwy. Sekcja jest
na cudzym profilu i w okienku otwieranym z karty propozycji; na własnym
profilu się nie pokazuje, bo nie ma czego z czym porównywać.

Część wspólną liczymy w Javie (dwie osoby, po kilkadziesiąt pozycji —
zapytanie z podwójnym złączeniem byłoby trudniejsze do przeczytania, a nie
szybsze). Wyjątkiem są gatunki: idą jednym zapytaniem, bo doczytanie ich
z encji kosztowałoby jedno zapytanie **na każdego** ulubionego wykonawcę.

## Drobiazgi z tej samej rundy

- **Logo ma znów nutkę.** Wcześniej dostało tę samą ikonę co pozycja
  „Tablica" i jedno z drugim wyglądało identycznie.
- **Kliknięcie logo albo ikony tablicy** wraca na górę tablicy i odświeża
  wpisy. Zmiana strony też zaczyna od góry — wcześniej przejście z połowy
  długiej tablicy zostawiało widok w tym samym miejscu.
- **Liczniki przy dzwonku i znajomych są mniejsze.** Miały prawie tę samą
  wysokość co ikona i to one przyciągały wzrok pierwsze.
- **Ekran znajomych nie podskakuje.** Kółko z napisem zajmowało jedną
  linijkę, a zaraz po nim wskakiwał rząd kafelków wysokich na kilkanaście
  razy tyle. Teraz jest szkielet o wysokości gotowego paska.
- **Przejścia są nieco wolniejsze** (0,2 s zamiast 0,16 s). Ruch krótszy
  niż mniej więcej dwie dziesiąte sekundy oko rejestruje jako przeskok,
  a nie jako przejście.

## Sprawdzone

246 testów backendu (9 nowych na „co Was łączy") oraz nowy zestaw
`sprawdz-poprawki.mjs` — **18 sprawdzeń w Chromium**. Zestawy z poprzednich
rund bez zmian: widoczność 35, powiadomienia 21, moderacja 16, linki 12.

---

# KROK 11 — czat ze znajomymi i znacznik obecności

Aplikacja o poznawaniu ludzi, w której nie da się do nikogo napisać, kończy
się na etapie „dodałem do znajomych i co dalej". Ten krok domyka pętlę:
wspólny gust → zaproszenie → **rozmowa**.

## Pisać można wyłącznie ze znajomymi

Reguła jest jedna i pilnuje jej serwer, ale **przy każdej operacji z osobna**:
wysłaniu, odczycie historii, odpytywaniu o nowości, oznaczaniu przeczytanych,
nawet przy sygnale „pisze". To nie jest nadgorliwość. Gdyby sprawdzenie stało
wyłącznie przy wysyłaniu, wystarczyłoby wywołać adres historii, żeby czytać
korespondencję dwóch obcych osób — i blokada nie chroniłaby niczego.

Osobny test sprawdza właśnie to: że **odczyt** cudzej rozmowy też kończy się
odmową.

Zakaz publikowania obejmuje czat. Kara za to, co ktoś pisze, zostawiająca
otwartą drogę do pisania prywatnie, nie jest karą.

## Rozmowa, której nie ma w bazie

Tabela jest jedna: `messages`. Encji „rozmowa" nie ma i nie jest potrzebna —
wszystko, co o rozmowie wiemy (kto z kim, kiedy ostatnio, ile nieprzeczytanych),
da się policzyć z samych wiadomości. Osobna tabela byłaby drugą kopią tej samej
prawdy, a dwie kopie prędzej czy później się rozjeżdżają.

Ceną jest zapytanie grupujące: „dla każdej wiadomości powiedz, kto jest tą
**drugą** stroną, i weź największy identyfikator w grupie".

### Błąd, który złapał dopiero test na prawdziwej bazie

Pierwsza wersja tego zapytania miała wyrażenie `CASE` wyłącznie w `GROUP BY`,
bo tylko tam było potrzebne. JPQL to przyjął, Hibernate przetłumaczył,
a baza odmówiła:

```
Invalid use of aggregate function MAX(m1_0.id)
```

Przy grupowaniu po wyrażeniu baza chce widzieć to wyrażenie **także na liście
wyników** — inaczej nie ma jak powiązać zagregowanej wartości z grupą.
Z atrapą repozytorium ten test przechodziłby, bo atrapa oddaje to, co jej
każemy, i nigdy nie zagląda do SQL-a.

### `MAX(id)`, nie `MAX(created_at)`

Dwie wiadomości wysłane w tej samej milisekundzie mają identyczny znacznik
czasu i baza może wtedy zwrócić je w dowolnej kolejności — także innej na
każdej stronie, co przy stronicowaniu potrafi zgubić albo powtórzyć wpis.
Identyfikator z sekwencji zawsze rośnie.

## Odpytywanie zamiast WebSocketa

Nowe wiadomości, dymek „pisze", obecność rozmówcy i licznik przy ikonie —
**jedną odpowiedzią, co trzy sekundy, tylko przy otwartej rozmowie**.

WebSocket wymagałby drugiej ścieżki uwierzytelniania obok sesji HTTP, własnego
stanu połączeń na serwerze i obsługi zrywania sieci w przeglądarce. Przy
rozmowie dwóch osób różnica między „natychmiast" a „w ciągu trzech sekund"
jest niezauważalna.

Sygnał „pisze" żyje **w pamięci serwera** i wygasa po pięciu sekundach.
Zapisywanie go do bazy oznaczałoby kilkanaście zapisów na minutę na każdą
otwartą rozmowę — do tabeli, której całą zawartość i tak trzeba by kasować
co chwilę.

## Ptaszek „przeczytane" nie działał — i testy tego nie widziały

Odpytywanie przynosi wyłącznie wiadomości **nowsze** od tej, którą przeglądarka
już ma. Ale przeczytanie nie tworzy nowej wiadomości: zmienia jedną kolumnę
w starej, dawno wysłanej. Ptaszek nie pojawiał się więc **nigdy** bez
odświeżenia całej strony.

Wyszło to dopiero w przeglądarce, przy dwóch równoległych sesjach. Poprawka to
jedno dodatkowe pytanie: „do której MOJEJ wiadomości rozmówca doczytał".
Wystarczy jeden numer na całą rozmowę, bo wiadomości czyta się po kolei.

Doszły dwa testy — i oba sprawdzone przez **wyłączenie poprawki**: bez niej
jeden świeci na czerwono, a drugi łapie wariant, w którym zapytanie zapomina
sprawdzić, kto jest nadawcą (wtedy zwykłe otwarcie rozmowy zapalałoby ptaszek
pod własnymi wiadomościami — fałszywe „on to widział").

## Obecność: co naprawdę znaczy „online"

Serwer nie ma jak się dowiedzieć, że ktoś zamknął kartę. „Online" znaczy więc
dokładnie tyle: **coś robił w ciągu ostatnich trzech minut**. Nie udajemy, że
to coś więcej — obok kropki zawsze stoi data.

Datę zapisujemy najwyżej **raz na 45 sekund**, a licznik trzymamy w pamięci.
Bez tego każde odpytanie czatu byłoby zapisem do tabeli `users`.

Okno „online" jest wyraźnie dłuższe niż odstęp między zapisami — inaczej ktoś
siedzący przed ekranem migałby między stanami w rytmie własnego licznika.
Pilnuje tego osobny test, który porównuje obie stałe zamiast sprawdzać liczbę.

Aktywność odnotowuje **przechwytywacz MVC, a nie filtr**. Filtr łapie każde
zapytanie do serwera, także pobieranie zdjęć spod `/uploads` — a tych jest
kilkadziesiąt na jedno wejście na stronę i nie są aktywnością człowieka,
tylko doładowywaniem obrazków.

## Dwa błędy z frontendu, warte zapamiętania

**Wysłana wiadomość znikała w tej samej chwili, w której się pojawiała.**
Panel przekazywał wątkowi funkcje tworzone na nowo przy każdym rysowaniu, więc
dla Reacta za każdym razem były to *inne* funkcje. Efekt wczytujący historię
miał je w zależnościach i uruchamiał się w kółko, czyszcząc listę. Lekarstwo:
uchwyt (`useRef`) na najświeższą wersję, a w zależnościach zostaje tylko to,
od czego efekt naprawdę zależy.

**Dwa pola o tym samym `id="musicUrl"`.** Ten sam wybór nagrania stoi teraz
w formularzu posta i w czacie. Etykieta `<label for="musicUrl">` zawsze
wskazuje **pierwszy** pasujący element, więc kliknięcie podpisu w czacie
ustawiało kursor w formularzu pod spodem. Znalazło to sprawdzenie
w przeglądarce, które trafiło na dwa elementy zamiast jednego — stąd
`idPrefix`.

## Sprawdzone

283 testy backendu (37 nowych: 29 na czat, 7 na obecność, 1 na sprzątanie
wiadomości przy kasowaniu konta) oraz nowy zestaw `sprawdz-czat.mjs` —
**41 sprawdzeń w Chromium, dwiema równoległymi sesjami naraz**. Inaczej nie da
się sprawdzić tego, co w czacie jest najważniejsze: czy wiadomość faktycznie
dolatuje do drugiej przeglądarki, czy dymek „pisze" zapala się u rozmówcy
i czy licznik nieprzeczytanych gaśnie po otwarciu wątku.

Pięć kluczowych asercji zweryfikowanych przez **wyłączenie poprawki**: blokada
„tylko znajomi" (przy wysyłaniu i przy czytaniu), kierunek sygnału „pisze",
ograniczenie częstotliwości zapisów obecności i oba potwierdzenia przeczytania.

Zestawy z poprzednich rund bez zmian: widoczność 35, powiadomienia 21,
moderacja 16, linki 12, poprawki interfejsu 18.

---

# KROK 12 — zgłoszenia, moderacja i multikonta

Czat postawił pytanie, którego wcześniej nie było: **co, jeśli ktoś użyje go
przeciwko komuś?** Do tej pory jedyną karą był zakaz publikowania, a jedynymi
treściami — publiczne posty, które administrator i tak widział. Wiadomości są
prywatne i administrator nie ma jak ich zobaczyć. I nie powinien mieć.

## Zgłoszenie jest bronią obosieczną

Trzy osoby zgłaszające kogoś „dla zabawy" potrafią zająć administratorowi tyle
samo czasu co trzy prawdziwe sprawy — i to jest najprostszy sposób, żeby cały
mechanizm przestał działać. Dlatego limity nie są dodatkiem, tylko warunkiem
tego, żeby funkcja miała sens:

| Limit | Co zatrzymuje |
|---|---|
| jedno **otwarte** zgłoszenie na osobę | klikanie w kółko tego samego |
| pięć zgłoszeń na dobę | zgłoszenie po kolei całego serwisu |
| obowiązkowy opis własnymi słowami | zgłoszenia klikane bez zastanowienia |

Drugi limit istnieje właśnie dlatego, że pierwszy sam nie wystarcza: przy
zgłaszaniu różnych osób nie zadziałałby **ani razu**. Pilnuje tego osobny test.

Doba jest **ruchoma**, a nie kalendarzowa — inaczej dałoby się wysłać dziesięć
zgłoszeń w godzinę, po pięć z każdej strony północy.

## Migawka dowodów zamiast odnośnika

To najważniejsza decyzja w całym tym kroku i ma dwa uzasadnienia, z których
każde osobno by wystarczyło.

**Pierwsze: odporność na zacieranie śladów.** Gdyby zgłoszenie tylko
*wskazywało* na wiadomości, zgłaszany miałby prostą drogę wyjścia —
doprowadzić do ich skasowania. Administrator otwierałby zgłoszenie i widział
pustkę. Test `evidenceSurvivesMessageDeletion` kasuje wiadomości po zgłoszeniu
i sprawdza, że dowód został.

**Drugie: ograniczenie uprawnień administratora.** Alternatywą był endpoint
„pokaż mi rozmowę tych dwóch osób". Byłoby to znacznie potężniejsze prawo:
możliwość przeczytania **dowolnej** rozmowy w serwisie, w dowolnej chwili.
Migawka daje administratorowi wyłącznie to, co zgłaszający sam mu pokazał.
Zakres jest zamknięty i wynika ze zgody użytkownika, a nie z roli.

Technicznie migawka to `@ElementCollection` klasy osadzonej — dowód nie ma
własnego życia, istnieje tylko jako część zgłoszenia i ginie razem z nim.

### Czego jeszcze nie wolno podpiąć

Post w zgłoszeniu musi **należeć do zgłaszanego** i być dla zgłaszającego
**widoczny**. Bez pierwszego sprawdzenia dałoby się podłożyć cudzy post
i wywołać działanie wobec osoby, która go nie napisała; bez drugiego —
zgłosić post „tylko dla znajomych" kogoś obcego, czyli obejść widoczność
postów przyznając się, że jednak się go widziało.

## Decyzja administratora nie karze sama

Zamknięcie zgłoszenia zapisuje decyzję, notatkę i autora — i nic więcej. Kary
są osobnymi operacjami. Gdyby zamknięcie karało automatycznie, decyzja
„zasadne, ale wystarczy upomnienie" byłaby niemożliwa do wyrażenia.

Sposoby zamknięcia są **dwa**, bo „zamknięte" bez rozróżnienia nie odpowiada na
pytanie zadawane przy następnym zgłoszeniu tej samej osoby: czy poprzednie było
zasadne? Liczba zasadnych stoi potem przy nagłówku zgłoszenia i przy koncie
w panelu.

Powtórne zamknięcie jest odrzucane — dwóch administratorów klikających
jednocześnie nadpisywałoby sobie notatki.

## Zmiana decyzji z poprzedniego kroku: zakaz wiadomości osobno

W KROKU 11 zakaz publikowania wyłączał także czat, z uzasadnieniem, że kara
zostawiająca otwartą drogę do pisania prywatnie nie jest karą.

**Ten argument się odwrócił**, gdy administrator dostał dwa osobne przełączniki.
Przy dawnym zachowaniu nie dałoby się w ogóle ustawić „nie wolno pisać postów,
ale wolno rozmawiać ze znajomymi" — czyli najczęstszego przypadku przy kimś, kto
zaśmieca tablicę, a nikomu nie dokucza. Kto ma dostać obie kary, dostaje obie.

Test `postingBanCoversChat` został **przepisany**, a nie usunięty: zachowanie
zmieniło się świadomie, więc sprawdzenie ma teraz pilnować nowej reguły
(`postingBanDoesNotBlockChat`) i osobno nowej kary (`messagingBanBlocksChat`).

## Adres klienta: dlaczego OSTATNI wpis nagłówka

Najciekawsza rzecz techniczna w tym kroku. Za nginxem `getRemoteAddr()` zwraca
adres kontenera — ten sam dla wszystkich. Prawdziwy adres jest
w `X-Forwarded-For`, który jest listą.

Nasz nginx używa `$proxy_add_x_forwarded_for`, czyli **dokleja** adres rozmówcy
na koniec tego, co przyszło. Podrobiony nagłówek daje więc:

```
X-Forwarded-For: 1.2.3.4, 203.0.113.7
                 ^ napisał atakujący
                            ^ dokleił nasz nginx
```

Większość tutoriali każe brać **pierwszy** wpis. Blokadę adresu obchodziłoby się
wtedy jednym dodatkowym nagłówkiem. Bierzemy ostatni — i to jest dokładnie ten
rodzaj kodu, który działa w każdym normalnym użyciu i jest bezużyteczny wtedy,
kiedy ma zadziałać. Test `spoofedHeaderDoesNotWin` wysyła podrobiony nagłówek
i sprawdza, który adres wygrywa.

## Multikonta: poszlaka, nie dowód

Wspólny adres **nie dowodzi**, że to ta sama osoba — pod jednym adresem siedzi
rodzina, akademik, kawiarnia, a operatorzy komórkowi trzymają za jednym adresem
tysiące klientów. Dlatego aplikacja nigdzie nie blokuje nikogo automatycznie,
a okienko pokazuje ostrzeżenie **nad** danymi razem z liczbą logowań i datą
ostatniego.

Blokada adresu działa przy **rejestracji i logowaniu**, nie na całym ruchu:
zatrzymuje zakładanie kolejnych kont po banie, a osobom postronnym za tym samym
adresem zabiera najwyżej możliwość założenia konta.

Administrator nie zablokuje adresu, z którego sam korzysta — przy testowaniu na
jednym komputerze odciąłby sobie drogę powrotu.

## Poprawki czatu z tej samej rundy

- **Dymek „pisze" ma awatar.** Nie należy do żadnej serii wiadomości, więc
  wcześniej wyglądał jak trzy kropki znikąd.
- **Lista rozmów dzieli się na dwie sekcje.** Wcześniej wszyscy znajomi stali
  w jednym ciągu posortowanym od najnowszej rozmowy — osoby, z którymi nic
  jeszcze nie napisano, lądowały na samym dole, za wszystkimi wątkami. A to
  właśnie ich szuka ktoś, kto chce **zacząć** rozmowę.
- **Wyszukiwarka** przy liście dłuższej niż sześć osób.

## Sprawdzone

317 testów backendu (34 nowe: 18 na zgłoszenia, 12 na adresy i blokady,
3 na kolizję adresów w panelu, 1 przepisany na zakazy) oraz nowy zestaw
`sprawdz-moderacje2.mjs` — **40 sprawdzeń w Chromium, trzema równoległymi
sesjami**: zgłaszający, zgłaszany i administrator. Inaczej nie da się
sprawdzić, czy administrator widzi zgłoszenie złożone przez kogoś innego
i czy widzi w nim rozmowę, której sam nie miałby jak zobaczyć.

Pięć asercji zweryfikowanych przez **wyłączenie poprawki**: podrobiony nagłówek
`X-Forwarded-For`, blokada własnego adresu, oba limity zgłoszeń i sprawdzenie
autora posta.

Zestawy z poprzednich rund bez zmian: czat 41, widoczność 35, powiadomienia 21,
moderacja 17, linki 12, poprawki interfejsu 18. **Jedno sprawdzenie z rundy
moderacyjnej trzeba było zaktualizować** — oczekiwało JEDNEJ kolumny „Zakaz"
w panelu, a od tej rundy są dwie. Zaświeciło się na czerwono słusznie: zmienił
się interfejs, nie zepsuł kod.

---

## Co zostaje na później

- potwierdzenie adresu e-mail przy rejestracji (wymaganie nr 16, opcjonalne).
