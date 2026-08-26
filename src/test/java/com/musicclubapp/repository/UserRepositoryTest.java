package com.musicclubapp.repository;

import com.musicclubapp.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy repozytorium - wymaganie nr 14 z listy.
 *
 * <p>{@code @DataJpaTest} podnosi TYLKO warstwe JPA (encje, repozytoria, baze
 * w pamieci) - bez kontrolerow i bez Spring Security. Dzieki temu test jest
 * szybki i sprawdza dokladnie jedna rzecz: czy zapytania do bazy dzialaja.</p>
 *
 * <p>Kazda metoda testowa dziala we wlasnej transakcji, ktora na koniec jest
 * wycofywana (rollback) - testy nie zostawiaja po sobie danych.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository - zapytania do bazy")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpData() {
        userRepository.save(new User("anna", "anna@example.com", "hash1"));
        userRepository.save(new User("bartek", "bartek@example.com", "hash2"));
        userRepository.save(new User("celina", "celina@musicclub.pl", "hash3"));
    }

    @Test
    @DisplayName("zapisany uzytkownik dostaje ID oraz date utworzenia (@PrePersist)")
    void saveSetsIdAndDate() {
        User stored = userRepository.save(new User("dawid", "dawid@example.com", "hash4"));

        assertThat(stored.getId()).isNotNull();
        // wymaganie nr 4 - encja przechowuje date/czas i faktycznie ja uzupelnia
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByUsername znajduje istniejacego uzytkownika")
    void findByUsernameFinds() {
        Optional<User> found = userRepository.findByUsername("anna");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    @DisplayName("findByUsername zwraca puste Optional gdy uzytkownika nie ma")
    void findByUsernameReturnsEmptyWhenMissing() {
        assertThat(userRepository.findByUsername("nieistnieje")).isEmpty();
    }

    @Test
    @DisplayName("existsByUsername wykrywa zajety login")
    void existsByUsernameDetectsTakenUsername() {
        assertThat(userRepository.existsByUsername("bartek")).isTrue();
        assertThat(userRepository.existsByUsername("wolny")).isFalse();
    }

    @Test
    @DisplayName("@Query szuka po fragmencie loginu, ignorujac wielkosc liter")
    void customQuerySearchesByUsername() {
        Page<User> score = userRepository.searchByUsernameOrEmail("AN", PageRequest.of(0, 10));

        // "anna" (login) oraz "celina" (email celina@musicclub.pl nie pasuje,
        // ale login "celina" tez nie zawiera "an") -> spodziewamy sie samej "anny"
        assertThat(score.getContent())
            .extracting(User::getUsername)
            .containsExactlyInAnyOrder("anna");
    }

    @Test
    @DisplayName("@Query szuka rowniez po fragmencie adresu e-mail")
    void customQuerySearchesByEmail() {
        Page<User> score = userRepository.searchByUsernameOrEmail("musicclub.pl", PageRequest.of(0, 10));

        assertThat(score.getContent())
            .extracting(User::getUsername)
            .containsExactly("celina");
    }

    @Test
    @DisplayName("stronicowanie dziala po stronie bazy - wymaganie nr 3")
    void pagingHappensInDatabase() {
        Page<User> firstPage = userRepository.searchByUsernameOrEmail("example.com", PageRequest.of(0, 1));

        assertThat(firstPage.getContent()).hasSize(1);   // tyle ile prosilismy
        assertThat(firstPage.getTotalElements()).isEqualTo(2); // anna + bartek
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    @DisplayName("sortowanie dziala po stronie bazy - wymaganie nr 5")
    void sortingHappensInDatabase() {
        Page<User> malejaco = userRepository.searchByUsernameOrEmail(
            "", PageRequest.of(0, 10, Sort.by("username").descending()));

        assertThat(malejaco.getContent())
            .extracting(User::getUsername)
            .containsExactly("celina", "bartek", "anna");
    }
}
