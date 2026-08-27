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
| 6 | OneToMany + ManyToOne między min. 2 encjami | `Post` N—1 `User`, `Post` 1—N `PostImage`, `Post` 1—N `Reaction`, `Notification` N—1 `User` ×2 i N—1 `Post` | ✅ posty |
| 9 | Bean Validation (bez własnych adnotacji) | `dto/RegisterRequest` — `@NotBlank`, `@Email`, `@Size`, `@Pattern` | ✅ KROK 3 |
| 12b | Frontend (REST API → dowolne narzędzie) | React + Vite w `frontend/` — tablica, profile, znajomi, ustawienia, panel administratora | ✅ KROK 5 |
| 13 | Testy jednostkowe serwisów | `UserServiceTest`, `PostServiceTest`, `ReactionServiceTest`, `FriendServiceTest` (Mockito) + `DeezerCatalogServiceTest`, `LastFmServiceTest`, `FavoritesServiceTest`, `UserModerationServiceTest` | ✅ KROK 3 |
| 15 | Rejestracja + logowanie, Spring Security (config NIE deprecated) | `config/SecurityConfig` — `SecurityFilterChain` + lambda DSL, sesja + BCrypt | ✅ KROK 3 |

## ⚪ Do wyboru — dla wszystkich typów projektów

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 3 | Stronicowanie + wybór liczby elementów (backend) | `Pageable` + widoczne sterowanie na `/users` (5/10/20) | ✅ KROK 2 + 5 |
| 4 | Encja z datą/czasem i jej wykorzystanie | `User.createdAt` (data dołączenia na profilu), `Post.createdAt` (sortowanie tablicy), `Reaction.createdAt` | ✅ KROK 2 |
| 5 | Sortowanie (backend) | `Sort` w `Pageable` + wybór pola i kierunku na `/users` | ✅ KROK 2 + 5 |
| 7 | OneToOne **lub** ManyToMany | `User` N—N `User` — znajomi (`user_friends`); `User` N—N `Artist` (`user_favorite_artists`); `User` N—N `Track` (`user_favorite_tracks`) | ✅ znajomi + ulubieni |
| 8 | Własne zapytania `@Query` / natywne | JPQL: `searchByUsernameOrEmail`, `findFeed` (JOIN FETCH), `countForPosts` (GROUP BY); **natywne**: `friendsRanked` oraz `friendSuggestions` (trzy skorelowane podzapytania + sortowanie po wyliczonym dopasowaniu) | ✅ KROK 2 |
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
| 25 | `@WebMvcTest` **oraz** `@SpringBootTest` | `AuthControllerTest` (8 testów) + `MusicClubAppApplicationTests` | ✅ KROK 3 |

---

## Podsumowanie na dziś

**Zaliczone w całości: 20** — punkty 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
14, 15, 17, 18, 22, 24, 25.

**Wszystkie 7 czerwonych jest zrobionych** — 1, 2, 6, 9, 12, 13, 15.
Projekt spełnia więc warunek konieczny na każdą ocenę, a licznik (20) przekracza
wymagane 17 na piątkę.

**Częściowo: 0** — nie ma już niedokończonych punktów.

W zapasie zostają jeszcze 16 (potwierdzenie maila) i 23 (HATEOAS) — oba opcjonalne.

**Testy: 209 przechodzi** (`mvn clean test`) — Mockito dla serwisów,
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

Legenda: ✅ zrobione · 🟡 częściowo · ⬜ do zrobienia
