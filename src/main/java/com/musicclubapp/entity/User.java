package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Uzytkownik aplikacji - jedna encja = jedna tabela w bazie.
 *
 * <p><b>Realizuje wymagania z listy:</b></p>
 * <ul>
 *   <li>nr 1 - uzycie JPA (adnotacje {@code @Entity}, {@code @Id}, {@code @Column}),</li>
 *   <li>nr 4 - encja przechowujaca date/czas ({@code createdAt}),</li>
 *   <li>nr 7 - relacja {@code @ManyToMany} ({@link #friends}).</li>
 * </ul>
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
     * Nazwa pliku ze zdjeciem profilowym, np. {@code a1b2...ff.jpg}.
     * {@code null} oznacza brak zdjecia - interfejs pokazuje wtedy kolo
     * z pierwsza litera loginu.
     *
     * <p>W bazie trzymamy sama nazwe, a plik leży na dysku - tak samo jak
     * przy zdjeciach w postach.</p>
     */
    @Column(name = "avatar_file_name", length = 120)
    private String avatarFileName;

    /**
     * Znajomi - <b>wymaganie nr 7 (ManyToMany)</b>.
     *
     * <p>Relacja uzytkownika z samym soba: jeden uzytkownik ma wielu znajomych,
     * a kazdy z nich ma wielu swoich. W bazie powstaje osobna tabela
     * {@code user_friends} z dwiema kolumnami - kto i z kim.</p>
     *
     * <p><b>Znajomosc jest OBUSTRONNA i zapisujemy ja dwoma wierszami</b>
     * (A→B oraz B→A). Da sie inaczej - trzymac jeden wiersz i w kazdym
     * zapytaniu sprawdzac obie kolumny przez {@code OR} - ale wtedy KAZDE
     * pytanie o znajomych robi sie dwa razy trudniejsze do przeczytania.
     * Tu placimy jednym dodatkowym wierszem za to, ze zapytania sa proste.
     * Dopisywaniem obu stron zajmuje sie {@link #dodajZnajomego(User)}.</p>
     *
     * <p>{@code Set}, a nie {@code List}: tej samej osoby nie da sie miec
     * w znajomych dwa razy. Dziala to dzieki temu, ze {@code equals} i
     * {@code hashCode} nizej porownuja po identyfikatorze.</p>
     */
    @ManyToMany
    @JoinTable(
        name = "user_friends",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "friend_id"))
    private Set<User> friends = new HashSet<>();

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

    public String getAvatarFileName() {
        return avatarFileName;
    }

    public void setAvatarFileName(String avatarFileName) {
        this.avatarFileName = avatarFileName;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<User> getFriends() {
        return friends;
    }

    /**
     * Dodaje znajomego po OBU stronach relacji.
     *
     * <p>Gdyby dopisac tylko jedna strone, znajomy widzialby nas na swojej
     * liscie, a my jego nie - i nikt by nie zauwazyl, bo zadne zapytanie
     * by sie nie wywalilo.</p>
     *
     * <p><b>UWAGA na {@code inny.getFriends()} zamiast {@code inny.friends}.</b>
     * Java pozwala siegnac wprost do prywatnego pola innego obiektu tej samej
     * klasy - i wlasnie na tym mozna sie przejechac. {@code inny} bywa
     * <i>leniwym proxy</i> Hibernate'a (tak jest, gdy przychodzi
     * z {@code zaproszenie.getSender()}). Odczyt POLA na proxy siega do pustego
     * pola samego proxy, a nie do prawdziwej encji - dopisanie znika bez sladu
     * i bez bledu. Wywolanie METODY proxy przekazuje dalej, do wlasciwego
     * obiektu, wiec dziala poprawnie.</p>
     */
    public void dodajZnajomego(User inny) {
        this.friends.add(inny);
        inny.getFriends().add(this);
    }

    /** Usuwa znajomosc po obu stronach - z ta sama uwaga o proxy co wyzej. */
    public void usunZnajomego(User inny) {
        this.friends.remove(inny);
        inny.getFriends().remove(this);
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
