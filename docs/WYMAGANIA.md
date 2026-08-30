# Lista wymagań — checklista projektu

Wypis z PDF-a (`Java 2 - ogólna lista wymagań`), z zachowaniem kolorów z oryginału.
Nasz projekt to **Spring REST API + React**, więc obowiązują nas kolory:
🔴 czerwony (ocena 3) + ⚪ biały (wszystkie typy projektów) + 🟢 zielony (REST API).
Punkty 🔵 (Thymeleaf) i 🟡 (Vaadin) **nas nie dotyczą**.

## Punktacja

| Ocena | Ile zagadnień |
|-------|---------------|
| 3     | 7 (wszystkie czerwone) |
| 4     | 12 (7 czerwonych + 5 do wyboru) |
| 5     | **17** (7 czerwonych + 10 do wyboru) |

Dostępnych dla nas punktów "do wyboru": 11 białych + 4 zielone = **15**.
Potrzebujemy 10 → jest zapas, ale nie ma miejsca na duże obsuwy.

---

## 🔴 Na ocenę 3 — OBOWIĄZKOWE (7/7)

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 1 | Użycie JPA | `entity/User.java`, `repository/UserRepository.java` | ✅ KROK 2 |
| 2 | Wsparcie min. 2 języków (PL/EN) | backend: `lang/messages*.properties` + `I18nConfig`; front: `i18next` + przełącznik PL/EN w menu | ✅ KROK 5 |
| 6 | OneToMany + ManyToOne między min. 2 encjami | `Post` N—1 `User`, `Post` 1—N `PostImage`, `Post` 1—N `Reaction`, `Notification` N—1 `User` ×2 i N—1 `Post`, `FavoritePlaylist` N—1 `User` | ✅ posty |
| 9 | Bean Validation (bez własnych adnotacji) | `dto/RegisterRequest` — `@NotBlank`, `@Email`, `@Size`, `@Pattern` | ✅ KROK 3 |
| 12b | Frontend (REST API → dowolne narzędzie) | React + Vite w `frontend/` — tablica, profile, znajomi, ustawienia, panel administratora | ✅ KROK 5 |
| 13 | Testy jednostkowe serwisów | `UserServiceTest`, `PostServiceTest`, `ReactionServiceTest`, `FriendServiceTest` (Mockito) + `DeezerCatalogServiceTest`, `LastFmServiceTest`, `FavoritesServiceTest`, `UserModerationServiceTest`, `ReportDecisionServiceTest`, `PlaylistServiceTest`, `PostVisibilityTest`, `CommonGroundServiceTest` | ✅ KROK 3 |
| 15 | Rejestracja + logowanie, Spring Security (config NIE deprecated) | `config/SecurityConfig` — `SecurityFilterChain` + lambda DSL, sesja + BCrypt | ✅ KROK 3 |

## ⚪ Do wyboru — dla wszystkich typów projektów

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 3 | Stronicowanie + wybór liczby elementów (backend) | `Pageable` + widoczne sterowanie na `/users` (5/10/20) | ✅ KROK 2 + 5 |
| 4 | Encja z datą/czasem i jej wykorzystanie | `User.createdAt` (data dołączenia na profilu), `Post.createdAt` (sortowanie tablicy), `Reaction.createdAt` | ✅ KROK 2 |
| 5 | Sortowanie (backend) | `Sort` w `Pageable` + wybór pola i kierunku na `/users` | ✅ KROK 2 + 5 |
| 7 | OneToOne **lub** ManyToMany | `User` N—N `User` — znajomi (`user_friends`); `User` N—N `Artist` (`user_favorite_artists`); `User` N—N `Track` (`user_favorite_tracks`) | ✅ znajomi + ulubieni |
| 8 | Własne zapytania `@Query` / natywne | JPQL: `searchByUsernameOrEmail`, `findFeed` (JOIN FETCH + `ORDER BY CASE` stawiające znajomych na górze), `countForPosts` (GROUP BY); `genresOf` (złączenie z kolekcją elementów); **natywne**: `friendsRanked`, `circleIds` (UNION) oraz `friendSuggestions` (trzy skorelowane podzapytania + sortowanie po wyliczonym dopasowaniu) | ✅ KROK 2 |
| 10 | **Własna** adnotacja walidacyjna | `@UniqueUsername` (pole), `@PasswordsMatch` (klasa), `@ValidMusicLink` (klasa — link + rodzaj + moment startu) | ✅ KROK 3 |
| 11 | `@ControllerAdvice` + wyjątek gdy brak elementu | `error/GlobalExceptionHandler` + `NoSuchElementFoundException` | ✅ KROK 3 |
| 14 | `@DataJpaTest` do testów zapytań | `UserRepositoryTest`, `FriendshipRepositoryTest` (zapytanie natywne + symetria relacji), `FriendSuggestionsTest` (kolejność dopasowań), `TopMusicRepositoryTest` (zapytanie z `GROUP BY`) | ✅ KROK 2 |
| 16 | Potwierdzenie maila przy rejestracji | token + `spring-boot-starter-mail` (MailHog na Dockerze) | ⬜ opcjonalne, na koniec |
| 17 | "Remember me" | `JsonRememberMeServices` — ciasteczko na 14 dni | ✅ KROK 3 |
| 18 | Użycie Dockera | `docker-compose.yml` (db + backend + frontend + adminer), `Dockerfile`, `frontend/Dockerfile` + `nginx.conf` | ✅ KROK 6 |

## 🟢 Do wyboru — tylko projekty REST API

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 22 | Używanie `ResponseEntity` | każda metoda każdego kontrolera (Auth, Post, Reaction, Profile, Users, Favorites, MusicCatalog, Friend) | ✅ KROK 3 |
| 23 | HATEOAS (Spring RESTful) | — | ⬜ opcjonalne (nie planujemy) |
| 24 | Swagger (Spring RPC) | `springdoc-openapi` → `/swagger-ui.html`, wszystkie endpointy z opisami | ✅ KROK 3 |
| 25 | `@WebMvcTest` **oraz** `@SpringBootTest` | `AuthControllerTest`, `UserControllerAccessTest`, `PostControllerRoutingTest`, `UserControllerRoutingTest` (kolizje adresów **i istnienie końcówek panelu**) + `MusicClubAppApplicationTests`, `PostVisibilityTest`, `PlaylistServiceTest` | ✅ KROK 3 |

---

## Podsumowanie na dziś

**Zaliczone w całości: 20** — punkty 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
14, 15, 17, 18, 22, 24, 25.

**Wszystkie 7 czerwonych jest zrobionych** — 1, 2, 6, 9, 12, 13, 15.
Projekt spełnia więc warunek konieczny na każdą ocenę, a licznik (20) przekracza
wymagane 17 na piątkę.

**Częściowo: 0** — nie ma już niedokończonych punktów.

W zapasie zostają jeszcze 16 (potwierdzenie maila) i 23 (HATEOAS) — oba opcjonalne.

**Testy: 369 przechodzi** (`mvn clean test`) — Mockito dla serwisów,
`@DataJpaTest` dla zapytań, `@WebMvcTest` dla kontrolerów, `@SpringBootTest`
dla całego kontekstu. Rozmowy z Deezerem i Last.fm sprawdzamy na **prawdziwym
HTTP**: mały serwer testowy (`TestHttpServer`) oddaje odpowiedzi w formacie obu
serwisów, a adresy podstawiamy przez `@DynamicPropertySource`.

**Zielone testy to nie to samo co działająca aplikacja.** Nauczył nas tego
błąd 500 na każdym poście z playlistą: testy chodzą na H2, który buduje schemat
od zera, a na prawdziwym PostgreSQL została stara reguła `CHECK` po
`ddl-auto=update` (Hibernate nigdy nie poprawia istniejącego ograniczenia).
125 testów było wtedy zielonych. Dlatego każdą rundę sprawdzamy dodatkowo
na prawdziwej bazie i w prawdziwej przeglądarce.

Kilka rzeczy, których żaden zielony test by nie zauważył, a które wyszły
dopiero na uruchomionej aplikacji:

- **kolizja adresów** — `GET /api/posts/reactions` obok `GET /api/posts/{id}`;
  gdyby wygrał wzorzec ze zmienną, odświeżanie liczników kończyłoby się
  błędem 400 „nie umiem zamienić `reactions` na liczbę". Pilnuje tego teraz
  `PostControllerRoutingTest`, o którym sprawdziliśmy, że faktycznie czerwienieje
  po zmianie adresu;
- **ta sama reguła zapisana dwa razy** — widoczność posta jest w JPQL-u (dla
  tablicy) i w Javie (dla pojedynczego posta). Obie wersje są potrzebne, więc
  zamiast usuwać powtórzenie, test `PostVisibilityTest#theRuleInJavaAndInTheDatabaseAgree`
  sprawdza, że mówią to samo;
- **kolumna `NOT NULL` dokładana do tabeli z danymi** — Hibernate w trybie
  `ddl-auto=update` dostaje wtedy od bazy odmowę, kolumna w ogóle nie powstaje
  i przestają działać wszystkie zapytania o posty. Stąd `visibility` jest
  w bazie nullowalna, a stare wiersze uzupełnia `PostVisibilityMigration`;
- **wylogowanie kasujące token CSRF** — pierwsze następne logowanie kończyło
  się komunikatem „Wystąpił nieoczekiwany błąd", a drugie działało. Cała rzecz
  dzieje się w ciasteczkach, więc żaden test serwera ani przeglądarki tego nie
  widział, dopóki nie napisaliśmy scenariusza „zaloguj → wyloguj → zaloguj";
- **sprawdzenie, które przechodzi z wyłączoną poprawką.** Test na przeskok
  treści przy pasku przewijania porównywał dwie strony, z których obie były
  dłuższe od okna — pasek był na obu i nie było czego mierzyć. Od tej pory
  każdą nową asercję sprawdzamy dodatkowo tak, że **cofamy poprawkę i patrzymy,
  czy czerwienieje**;
- **poprawny JPQL, którego baza nie przyjmuje.** Zapytanie o ostatnią
  wiadomość każdej rozmowy miało wyrażenie `CASE` tylko w `GROUP BY` — Hibernate
  je przetłumaczył, a baza odmówiła (`Invalid use of aggregate function`).
  Z atrapą repozytorium test przechodziłby, bo atrapa oddaje to, co jej każemy,
  i nigdy nie zagląda do SQL-a. Stąd testy czatu chodzą na prawdziwej bazie;
- **funkcja, która działa u jednego użytkownika, a nie działa między dwoma.**
  Ptaszek „przeczytane" nigdy się nie pojawiał: odpytywanie przynosi tylko
  wiadomości *nowsze*, a przeczytanie zmienia kolumnę w starej. Zobaczyło to
  dopiero sprawdzenie **dwiema równoległymi sesjami przeglądarki** — jedna
  sesja nie ma jak zauważyć, że druga czegoś nie dostała;
- **dwa elementy o tym samym `id` na stronie.** Wybór nagrania stoi w dwóch
  miejscach naraz (post i czat), a `<label for>` zawsze wskazuje pierwszy
  pasujący. Kliknięcie podpisu w czacie ustawiało kursor w formularzu pod
  spodem. Znalazł to selektor w Playwrighcie, który trafił na dwa elementy
  zamiast jednego;
- **kod, który działa zawsze poza sytuacją, do której powstał.** Odczyt adresu
  klienta z `X-Forwarded-For` — wzięcie PIERWSZEGO wpisu (tak każe większość
  tutoriali) daje poprawny adres w każdym normalnym użyciu i adres wybrany
  przez atakującego, gdy ktoś podrobi nagłówek. Blokadę obchodziłoby się wtedy
  jednym nagłówkiem. Żaden test „czy odczytuje adres" tego nie złapie —
  potrzebny był test WYSYŁAJĄCY podrobiony nagłówek;
- **funkcja, którą trzeba sprawdzić z TRZECH stron naraz.** Zgłoszenie ma sens
  tylko wtedy, gdy zgłaszający je złoży, zgłaszany o nim nie wie, a administrator
  je zobaczy. Jedna sesja przeglądarki nie odpowie na żadne z tych pytań;
- **sprawdzenie, które przestaje być prawdziwe, bo zmienił się interfejs.**
  Regresja z rundy moderacyjnej oczekiwała JEDNEJ kolumny „Zakaz" w panelu,
  a doszła druga (zakaz wiadomości). Zaświeciła się na czerwono słusznie —
  ale poprawką było zaktualizowanie oczekiwania, nie kodu. Czerwony test nie
  zawsze znaczy zepsuty kod; czasem znaczy nieaktualne sprawdzenie, i trzeba
  za każdym razem rozstrzygnąć, które z dwojga.
- **poprawny SQL, który przyjmuje jedna baza, a odrzuca druga.** Zapytanie
  o listę rozmów grupowało po wyrażeniu `CASE` z parametrem `:me`. H2 to
  przyjął, PostgreSQL odmówił (*column „sender_id" must appear in the GROUP BY
  clause*), bo parametr trafia do SQL-a osobno w każdym miejscu i baza widzi
  dwa różne wyrażenia zamiast jednego. Każde wejście w wiadomości kończyło się
  błędem 500 — a na ekranie było napisane „nie masz z kim pisać", bo
  przeglądarka traktowała awarię jak pustą listę. **Dwa wnioski:** zapytanie
  zielone na H2 nie jest sprawdzone, dopóki nie pójdzie na PostgreSQL; i „pusto"
  musi wyglądać inaczej niż „nie udało się";
- **lista, o której z góry wiadomo, że ktoś o niej zapomni.** Odświeżanie
  ograniczeń `CHECK` obejmowało tylko kolumny „które mogą się rozjechać”, bo
  role i rodzaje powiadomień „się nie zmieniają”. Do `NotificationType` doszła
  wartość `REPORT` i każde zgłoszenie użytkownika kończyło się błędem 500.
  Poprawką nie było dopisanie jednej kolumny, tylko **test, który porównuje tę
  listę z modelem encji** — dzięki temu następna kolumna wyliczeniowa zapali
  się na czerwono sama;
- **`try/catch`, który pogarsza sprawę.** Powiadomienie administratora o nowym
  zgłoszeniu było opakowane w „gdyby się nie udało, trudno”. Tyle że odrzucone
  zapytanie SQL unieważnia **całą** transakcję: złapanie wyjątku niczego nie
  cofa, a kolejny krok kończy się `HHH000099`. Zamiast łagodnego „zgłoszenie
  jest, powiadomienia nie ma” wychodził twardy błąd 500 i zgłoszenie nie
  zapisywało się wcale — odwrotnie, niż obiecywał komentarz nad tym kodem;
- **data bez strefy czasowej.** `LocalDateTime` wychodził do przeglądarki jako
  `2026-08-28T12:00:00` — bez śladu, że to UTC. Norma JavaScriptu każe taki
  zapis czytać jako czas **lokalny**, więc w Polsce wszystko było cofnięte
  o dwie godziny: osoba aktywna przed chwilą miała „aktywny 2 godziny temu”.
  Testy tego nie widziały, bo porównują daty po stronie Javy; co gorsza, przy
  serwerze i przeglądarce w tej samej strefie błąd znika całkowicie — czyli
  **na komputerze do nauki wyglądał na nieistniejący**;
- **jedna przeglądarka to jedna tożsamość.** Ciasteczko sesji należy do całej
  przeglądarki, nie do karty, więc zalogowanie się na drugie konto w nowej
  karcie po cichu przestawiało też starą — i na profilu jednej osoby pojawiali
  się znajomi zupełnie innej. Tego nie da się „naprawić”, bo tak działają
  ciasteczka; można natomiast **wykryć podmianę i uprzedzić o niej**, zamiast
  mieszać dane dwóch kont. Do prowadzenia rozmowy „sam ze sobą” potrzebne są
  dwie osobne przeglądarki albo okno prywatne.
- **więź w bazie, której atrapa nie ma.** Decyzja „usuń zgłoszony post" kończyła
  się błędem 500: zgłoszenie wskazuje na post kluczem obcym, więc dopóki
  wskazuje, baza posta nie odda. Test na atrapach sprawdzał **to samo działanie**
  i przez cały czas trwania błędu był zielony — atrapa repozytorium nie ma
  kluczy obcych i pozwoli skasować cokolwiek. Wniosek: **więzy spójności
  istnieją wyłącznie w bazie i tylko tam da się je złamać**, więc każde
  kasowanie czegoś, na co ktoś wskazuje, musi mieć test na prawdziwej bazie;
- **kolejność, która ma znaczenie dopiero przy drugim kliknięciu.** Zamknięcie
  zgłoszenia sprawdza, czy ktoś inny nie zdążył już podjąć decyzji. Gdyby kara
  wykonywała się przed tym sprawdzeniem, dwoje administratorów naraz nałożyłoby
  ją dwa razy — a przy usunięciu konta drugie wykonanie kończy się błędem „nie
  ma takiego użytkownika", który niczego nie tłumaczy;
- **klucz dopisany drugi raz.** Do pliku z komunikatami trafił `error.report.nopost`,
  który już tam był w innym znaczeniu. Pliki `.properties` nie zgłaszają tego
  w żaden sposób — po cichu wygrywa ostatni wpis, więc stary komunikat zmieniłby
  treść bez śladu w kodzie.
- **data sformatowana po niewłaściwej stronie.** Termin końca kary serwer
  wklejał do komunikatu sam — i zakaz nałożony o 16:55 na godzinę pokazywał się
  jako „do 15:55", czyli w przeszłości. Test na ten komunikat **przechodził**,
  bo sprawdzał, czy data w nim *jest*, a nie czy jest *prawdziwa dla
  oglądającego*. Zasada, która z tego zostaje: **serwer podaje chwilę, klient
  robi z niej godzinę** — bo tylko przeglądarka zna strefę użytkownika;
- **lista, w której dwie pozycje mają tę samą wartość.** W panelu kont
  „zdejmij zakaz" miało `value=""` — dokładnie tyle samo co pozycja neutralna,
  do której lista wraca po każdej akcji. Wybranie go nie zmieniało więc
  wartości, przeglądarka nie zgłaszała zdarzenia i **zakazu nie dało się
  zdjąć**. Kliknięcie wyglądało na przyjęte i nie robiło nic — najgorszy
  rodzaj awarii, bo nie zostawia nawet błędu do zauważenia;
- **znikające sterowanie.** Wyszukiwarka w czacie pojawiała się dopiero od
  sześciu rozmów, z rozumowaniem, że przy trzech osobach jest zbędna. Tyle że
  **nie widać pola, którego nie ma**: kto go szuka i nie znajduje, wnioskuje,
  że aplikacja go nie ma, a nie że ma za mało znajomych;
- **jedno sprawdzenie na dwie różne rzeczy.** Czat miał jeden warunek („tylko
  znajomi") na pisanie *i* na czytanie. Skutek: usunięcie kogoś ze znajomych
  kasowało z widoku całą rozmowę i dla obu stron wyglądało to jak awaria.
  Rozdzielenie na „czytać wolno, pisać nie" rozwiązało to bez otwierania
  furtki — czytanie wymaga wspólnej historii, a nie samego istnienia konta;
- **sędzia we własnej sprawie.** Zgłoszenie może dotyczyć administratora, który
  je rozpatruje. Kary na własne konto są tam zablokowane — nie tylko dlatego,
  że to ocena we własnej sprawie, ale też dlatego, że jedno kliknięcie dzieli
  wtedy od odebrania sobie dostępu do panelu;
- **końcówka, która zniknęła po cichu przy porządkowaniu kodu.** Scalanie dwóch
  adresów kary w jeden (`/{id}/bans/{kind}`) usunęło przy okazji
  `DELETE /api/users/{id}`, bo leżał on w pliku **pomiędzy** nimi. Kod dalej się
  kompilował, wszystkie 351 testów było zielonych, a panel administratora
  dostawał 405 przy próbie usunięcia konta. **Brakującego mapowania nie widać
  ani w kompilacji, ani w testach logiki** — testy sprawdzały, co robi
  `UserModerationService`, ale żaden nie pytał, czy prowadzi do niego
  jakikolwiek adres. Znalazło to dopiero przejście przez aplikację
  w przeglądarce. Stąd `UserControllerRoutingTest` pilnuje teraz samego
  **istnienia** końcówek panelu, nie tylko kolizji między nimi;
- **kolejność, której nikt nie sprawdził, bo nikt nie napisał tego testu.**
  Usunięcie konta kasowało posty w kroku 3, a zgłoszenia odpinało od nich
  dopiero w kroku 6. Działało — dopóki post skasowanego konta nie został przez
  kogoś **zgłoszony**: wtedy baza odmawiała, bo zgłoszenie wskazywało na post
  kluczem obcym. `UserDeletionTest` sprawdzał posty, reakcje, znajomości
  i playlisty, ale ani jeden test nie łączył zgłoszenia z kasowaniem konta.
  Znalazło się to dopiero przy dokładaniu nowej funkcji, przez napisanie testu
  na przypadek, którego nie było. **Lista rzeczy do posprzątania jest tyle
  warta, ile scenariusz, który ją przechodzi** — a scenariusz musi łączyć
  funkcje, nie sprawdzać każdej osobno;
- **funkcja, która nic nie zwraca, i wywołanie, które czegoś od niej oczekuje.**
  Przy przenoszeniu adresów do modułów `api/` cztery operacje na ulubionych
  zostały napisane bez `return` — a komponent dalej czytał z wyniku `.data`.
  Efekt na ekranie: „Nie można połączyć się z serwerem", czyli komunikat
  **o zupełnie czymś innym**, bo `describeError` widzi wyjątek bez odpowiedzi
  HTTP i uznaje go za awarię sieci. Ani kompilacja, ani `npm run build`, ani
  354 testy backendu nie mają jak tego zauważyć: to zgodność dwóch stron
  JavaScriptu, której nikt nie sprawdza. Stąd przegląd **wszystkich** funkcji
  API pod kątem „czy ktoś czyta wynik z funkcji, która go nie zwraca";
- **animacja, która zostawia po sobie `transform` — i chowa listę pod sąsiadem.**
  Lista podpowiedzi w wyszukiwarce ucinała się na krawędzi sekcji i wchodziła pod
  następny kafelek, mimo `z-index: 20`. Powód: wejście kafelków
  (`animation: fade-in-up … both`) zostawia na elemencie `transform`, a element
  z `transform` **tworzy kontekst układania** — z którego żadne `z-index`
  potomka nie ma jak wyjść. Ostatnia klatka animacji i tak kończy się na
  `transform: none`, więc `both` zamieniono na `backwards`: wygląda identycznie,
  a nie zostawia po sobie kontekstu. Diagnoza wymagała zapytania przeglądarki
  `elementFromPoint`, bo w CSS nie widać, kto kogo przykrywa;
- **liczba zależności to nie to samo co jakość zależności.** Przenosząc
  sprzątanie po koncie do modułów-właścicieli spodziewaliśmy się, że
  `UserModerationService` schudnie z 12 zależności — a wyszło 12 na 12: pięć
  repozytoriów zniknęło, ale weszły cztery moduły. Zysk jest gdzie indziej:
  moduł moderacji nie zna już **ani jednego repozytorium, którego nie jest
  właścicielem**. Warto pilnować, żeby miarą refaktoru nie stała się ładna
  liczba, która akurat da się pokazać.

Legenda: ✅ zrobione · 🟡 częściowo · ⬜ do zrobienia
