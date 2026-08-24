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
| 2 | Wsparcie min. 2 języków (PL/EN) | `lang/messages*.properties` + `I18nConfig` (`Accept-Language`, `?lang=pl`) | 🟡 backend ✅ / front KROK 5 |
| 6 | OneToMany + ManyToOne między min. 2 encjami | `User` 1—N `Post` (posty użytkownika) | ⬜ KROK 7 |
| 9 | Bean Validation (bez własnych adnotacji) | `dto/RegisterRequest` — `@NotBlank`, `@Email`, `@Size`, `@Pattern` | ✅ KROK 3 |
| 12b | Frontend (REST API → dowolne narzędzie) | React + Vite w `frontend/` | ⬜ KROK 5 |
| 13 | Testy jednostkowe serwisów | `service/UserServiceTest` (Mockito, 6 testów) | ✅ KROK 3 |
| 15 | Rejestracja + logowanie, Spring Security (config NIE deprecated) | `config/SecurityConfig` — `SecurityFilterChain` + lambda DSL, sesja + BCrypt | ✅ KROK 3 |

## ⚪ Do wyboru — dla wszystkich typów projektów

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 3 | Stronicowanie + wybór liczby elementów (backend) | `Pageable` w `searchByUsernameOrEmail`, feed postów | ✅ KROK 2 (repo) |
| 4 | Encja z datą/czasem i jej wykorzystanie | `User.createdAt`, `Post.createdAt`, `Match.matchedAt` | ✅ KROK 2 (User) |
| 5 | Sortowanie (backend) | `Sort` w `Pageable` — np. `?sort=createdAt,desc` | ✅ KROK 2 (repo) |
| 7 | OneToOne **lub** ManyToMany | `User` N—N `Artist` (ulubieni), `Artist` N—N `Genre` | ⬜ KROK 7 |
| 8 | Własne zapytania `@Query` / natywne | `UserRepository.searchByUsernameOrEmail`, później algorytm dopasowań | ✅ KROK 2 |
| 10 | **Własna** adnotacja walidacyjna | `@UniqueUsername` (pole) + `@PasswordsMatch` (klasa) | ✅ KROK 3 |
| 11 | `@ControllerAdvice` + wyjątek gdy brak elementu | `error/GlobalExceptionHandler` + `NoSuchElementFoundException` | ✅ KROK 3 |
| 14 | `@DataJpaTest` do testów zapytań | `repository/UserRepositoryTest` | ✅ KROK 2 |
| 16 | Potwierdzenie maila przy rejestracji | token + `spring-boot-starter-mail` (MailHog na Dockerze) | ⬜ opcjonalne, na koniec |
| 17 | "Remember me" | `JsonRememberMeServices` — ciasteczko na 14 dni | ✅ KROK 3 |
| 18 | Użycie Dockera | `docker-compose.yml`, `Dockerfile` | 🟡 KROK 1 (baza) → KROK 6 (całość) |

## 🟢 Do wyboru — tylko projekty REST API

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 22 | Używanie `ResponseEntity` | każda metoda w `AuthController` i `UserController` | ✅ KROK 3 |
| 23 | HATEOAS (Spring RESTful) | — | ⬜ opcjonalne (nie planujemy) |
| 24 | Swagger (Spring RPC) | `springdoc-openapi` → `/swagger-ui.html`, 6 endpointów | ✅ KROK 3 |
| 25 | `@WebMvcTest` **oraz** `@SpringBootTest` | `AuthControllerTest` (8 testów) + `MusicClubAppApplicationTests` | ✅ KROK 3 |

---

## Podsumowanie na dziś

**Zaliczone w całości: 15 z 17** potrzebnych na piątkę —
punkty 1, 3, 4, 5, 8, 9, 10, 11, 13, 14, 15, 17, 22, 24, 25.

Czerwone (obowiązkowe na 3): zrobione 4 z 7 — **1, 9, 13, 15**.
Zostały trzy: **2** (dokończyć na froncie), **6** (OneToMany), **12** (frontend).

**Częściowo: 2**
- nr 2 — backend gotowy (komunikaty walidacji PL/EN), zostaje menu na froncie
- nr 18 — baza na Dockerze działa, całość dojdzie w KROKU 6

Do piątki brakuje **2 punktów**, a w zapasie zostają jeszcze 6, 7, 12, 16, 23 —
czyli margines jest spory.

Legenda: ✅ zrobione · 🟡 częściowo · ⬜ do zrobienia
