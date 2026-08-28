package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Z jakiego adresu sieciowego logowalo sie dane konto.
 *
 * <p><b>Po co to trzymamy.</b> Zeby administrator mogl zobaczyc, ze osoba
 * skasowana wczoraj wrocila dzis pod nowym loginem. Bez historii adresow
 * jedyne, co widac przy nowym koncie, to data zalozenia - a to nie mowi nic.</p>
 *
 * <p><b>Jeden wiersz na pare (konto, adres), a nie na kazde logowanie.</b>
 * Zapisywanie kazdego wejscia dawaloby tabele rosnaca w nieskonczonosc,
 * a odpowiedz na jedyne pytanie, ktore nas interesuje - "czy te dwa konta
 * laczylo cos wspolnego" - byla by taka sama. Zamiast tego aktualizujemy
 * date ostatniego uzycia i licznik.</p>
 *
 * <p><b>Uwaga na wnioski.</b> Wspolny adres <b>nie dowodzi</b>, ze to ta sama
 * osoba: pod jednym adresem siedzi cala rodzina, akademik, kawiarnia,
 * a operatorzy komorkowi potrafia trzymac za jednym adresem tysiace klientow.
 * To jest poszlaka do sprawdzenia przez czlowieka, a nie wyrok - i dlatego
 * aplikacja nigdzie nie blokuje kont automatycznie na tej podstawie.</p>
 */
@Entity
@Table(
    name = "account_ips",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_account_ips_user_address", columnNames = {"user_id", "address"}),
    indexes = {
        /*
         * Pytanie brzmi zawsze "kto jeszcze uzywal tego adresu", czyli szukamy
         * po kolumnie adresu. Bez indeksu baza przegladalaby cala tabele.
         */
        @Index(name = "idx_account_ips_address", columnList = "address")
    })
public class AccountIp {

    /**
     * Maksymalna dlugosc adresu.
     *
     * <p>45 znakow to najdluzszy mozliwy zapis adresu IPv6 razem z wersja
     * "zanurzona" w nim czworki ({@code ::ffff:192.168.100.228}). Krotsza
     * kolumna dzialalaby poprawnie do dnia, w ktorym ktos wejdzie po IPv6.
     */
    public static final int MAX_ADDRESS_LENGTH = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "address", nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /** Ile razy z tego adresu logowano sie na to konto. */
    @Column(name = "login_count", nullable = false)
    private int loginCount;

    protected AccountIp() {
    }

    public AccountIp(User user, String address) {
        this.user = user;
        this.address = address;
        this.firstSeenAt = LocalDateTime.now();
        this.lastSeenAt = this.firstSeenAt;
        this.loginCount = 1;
    }

    /** Kolejne logowanie z tego samego adresu. */
    public void seenAgain() {
        this.lastSeenAt = LocalDateTime.now();
        this.loginCount++;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getAddress() {
        return address;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public int getLoginCount() {
        return loginCount;
    }
}
