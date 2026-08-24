package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Uzytkownik aplikacji - jedna encja = jedna tabela w bazie.
 *
 * <p><b>Realizuje wymagania z listy:</b></p>
 * <ul>
 *   <li>nr 1 - uzycie JPA (adnotacje {@code @Entity}, {@code @Id}, {@code @Column}),</li>
 *   <li>nr 4 - encja przechowujaca date/czas ({@code createdAt}).</li>
 * </ul>
 *
 * <p>Na tym etapie (KROK 2) encja jest celowo "chuda" - bez relacji do artystow,
 * postow i dopasowan. Relacje dochodza w kolejnych krokach, zeby na kazdym etapie
 * bylo widac dokladnie, co doszlo do bazy.</p>
 */
@Entity
// "user" jest slowem zarezerwowanym w PostgreSQL, dlatego tabela nazywa sie "users".
@Table(name = "users")
public class User {

    /**
     * Klucz glowny. IDENTITY = numerowanie zalatwia baza (kolumna BIGSERIAL
     * w PostgreSQL), my nie musimy sami wymyslac ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login. {@code unique = true} zaklada w bazie indeks unikalny - dwoch takich samych nie bedzie. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Hash hasla (BCrypt), NIGDY haslo jawnym tekstem.
     * Hashowanie dojdzie w KROKU 3 razem ze Spring Security.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Data i godzina zalozenia konta - wymaganie nr 4 z listy.
     * Wykorzystamy ja pozniej do sortowania uzytkownikow "od najnowszych"
     * (wymaganie nr 5) i do wyswietlania "czlonek od ...".
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Rola uzytkownika. {@code EnumType.STRING} zapisuje w bazie tekst
     * ("USER"), a nie numer pozycji w enumie - dzieki temu dodanie nowej roli
     * w srodku listy nie popsuje istniejacych danych.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /**
     * Czy konto jest aktywne. Przyda sie przy wymaganiu nr 16
     * (potwierdzenie maila) - do tego czasu zawsze {@code true}.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Metoda oznaczona {@code @PrePersist} uruchamia sie automatycznie tuz przed
     * pierwszym zapisem encji do bazy. Dzieki temu nie musimy pamietac o ustawianiu
     * daty w kazdym miejscu, gdzie tworzymy uzytkownika.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Konstruktor bezargumentowy jest WYMAGANY przez JPA (Hibernate tworzy nim obiekty). */
    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * equals/hashCode po ID. Wazne przy encjach - domyslne porownywanie po
     * referencji potrafi psuc dzialanie kolekcji (Set) i cache'u Hibernate'a.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', createdAt=" + createdAt + "}";
    }
}
