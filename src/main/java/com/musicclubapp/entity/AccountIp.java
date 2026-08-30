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

/** Z jakiego adresu sieciowego logowalo sie dane konto. */
@Entity
@Table(
    name = "account_ips",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_account_ips_user_address", columnNames = {"user_id", "address"}),
    indexes = {
        /*
         * Pytanie brzmi zawsze "kto jeszcze uzywal tego adresu", czyli szukamy po kolumnie adresu.
         */
        @Index(name = "idx_account_ips_address", columnList = "address")
    })
public class AccountIp {

    /**
     * Maksymalna dlugosc adresu. 45 znakow to najdluzszy mozliwy zapis adresu IPv6 razem z wersja
     * "zanurzona" w nim czworki (::ffff:192.168.100.228).
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
