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
| 2 | Wsparcie min. 2 języków (PL/EN) | `messages.properties` + `messages_pl.properties`, i18next na froncie | ⬜ KROK 3 (backend) / 5 (front) |
| 6 | OneToMany + ManyToOne między min. 2 encjami | `User` 1—N `Post` (posty użytkownika) | ⬜ KROK 7 |
| 9 | Bean Validation (bez własnych adnotacji) | `dto/RegisterRequest` — `@NotBlank`, `@Email`, `@Size` | ⬜ KROK 3 |
| 12b | Frontend (REST API → dowolne narzędzie) | React + Vite w `frontend/` | ⬜ KROK 5 |
| 13 | Testy jednostkowe serwisów | `service/*ServiceTest` (JUnit 5 + Mockito) | ⬜ KROK 3 |
| 15 | Rejestracja + logowanie, Spring Security (config NIE deprecated) | `config/SecurityConfig` — `SecurityFilterChain` + lambda DSL | ⬜ KROK 3 |

## ⚪ Do wyboru — dla wszystkich typów projektów

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 3 | Stronicowanie + wybór liczby elementów (backend) | `Pageable` w `searchByUsernameOrEmail`, feed postów | ✅ KROK 2 (repo) |
| 4 | Encja z datą/czasem i jej wykorzystanie | `User.createdAt`, `Post.createdAt`, `Match.matchedAt` | ✅ KROK 2 (User) |
| 5 | Sortowanie (backend) | `Sort` w `Pageable` — np. `?sort=createdAt,desc` | ✅ KROK 2 (repo) |
| 7 | OneToOne **lub** ManyToMany | `User` N—N `Artist` (ulubieni), `Artist` N—N `Genre` | ⬜ KROK 7 |
| 8 | Własne zapytania `@Query` / natywne | `UserRepository.searchByUsernameOrEmail`, później algorytm dopasowań | ✅ KROK 2 |
| 10 | **Własna** adnotacja walidacyjna | `@UniqueUsername` lub `@StrongPassword` | ⬜ KROK 3 |
| 11 | `@ControllerAdvice` + wyjątek gdy brak elementu | `error/GlobalExceptionHandler` + `ResourceNotFoundException` | ⬜ KROK 3 |
| 14 | `@DataJpaTest` do testów zapytań | `repository/UserRepositoryTest` | ✅ KROK 2 |
| 16 | Potwierdzenie maila przy rejestracji | token + `spring-boot-starter-mail` (MailHog na Dockerze) | ⬜ opcjonalne, na koniec |
| 17 | "Remember me" | długi refresh-token w httpOnly cookie | ⬜ opcjonalne, na koniec |
| 18 | Użycie Dockera | `docker-compose.yml`, `Dockerfile` | 🟡 KROK 1 (baza) → KROK 6 (całość) |

## 🟢 Do wyboru — tylko projekty REST API

| # | Wymaganie | Gdzie u nas | Status |
|---|-----------|-------------|--------|
| 22 | Używanie `ResponseEntity` | każda metoda w `controller/*` | ⬜ KROK 3 |
| 23 | HATEOAS (Spring RESTful) | — | ⬜ opcjonalne (nie planujemy) |
| 24 | Swagger (Spring RPC) | `springdoc-openapi` → `/swagger-ui.html` | ⬜ KROK 3 |
| 25 | `@WebMvcTest` **oraz** `@SpringBootTest` | `controller/*ControllerTest` + `MusicClubAppApplicationTests` | 🟡 KROK 2 (`@SpringBootTest` już jest) |

---

## Podsumowanie na dziś

**Zaliczone w całości: 6** — punkty 1, 3, 4, 5, 8, 14 (z czego nr 1 jest czerwony)
**Częściowo: 2** (18 — baza na Dockerze; 25 — jest `@SpringBootTest`)

Legenda: ✅ zrobione · 🟡 częściowo · ⬜ do zrobienia
