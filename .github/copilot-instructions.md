# Instrukcje techniczne dla Copilota - Projekt Java 2

Zawsze stosuj się do poniższych zasad, aby zapewnić zgodność z listą wymagań:

1. **JPA & Baza Danych:** Każda encja musi być poprawnie zmapowana. Używaj relacji OneToMany (np. User-Playlist)  oraz ManyToMany (np. User-Artist) .
2. **Data i Czas:** Przynajmniej jedna encja (np. Match) musi przechowywać pole typu LocalDateTime .
3. **Standard REST:** Każda metoda w kontrolerze musi zwracać `ResponseEntity<T>` .
4. **Stronicowanie i Sortowanie:** Wszystkie listy (użytkowników, dopasowań) muszą być stronicowane i sortowane po stronie backendu przy użyciu `Pageable` [2].
5. **Własne zapytania:** Używaj adnotacji `@Query` w repozytoriach do logiki wyszukiwania podobnych gustów muzycznych .
6. **Bezpieczeństwo:** Konfiguracja Spring Security musi być nowoczesna (Lambda DSL, `SecurityFilterChain`), nie używaj metod oznaczonych jako @Deprecated .
7. **Obsługa błędów:** Wszystkie wyjątki (np. brak elementu w bazie) muszą być obsługiwane przez centralną klasę `@ControllerAdvice` .
8. **Języki (i18n):** Przygotuj kod pod wsparcie minimum 2 języków (pliki messages.properties) [3].
9. **Testy:** Generuj testy z użyciem `@WebMvcTest` dla kontrolerów oraz `@SpringBootTest` dla integracji .
10. **Walidacja:** Stosuj Bean Validation na poziomie DTO .