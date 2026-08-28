package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Zablokowany adres sieciowy.
 *
 * <p><b>Co dokladnie blokujemy - i czego NIE blokujemy.</b> Zablokowany adres
 * nie moze <b>zalozyc nowego konta</b> ani <b>zalogowac sie</b>. Reszta
 * aplikacji dziala z niego normalnie.</p>
 *
 * <p>To nie jest niedoróbka, tylko decyzja. Blokada calego ruchu z adresu
 * odcieloby przy okazji wszystkich, ktorzy siedza za tym samym adresem -
 * a to bywa cala rodzina, akademik albo kilka tysiecy klientow operatora
 * komorkowego. Blokada logowania i rejestracji zatrzymuje to, po co ta funkcja
 * powstala (zakladanie kolejnych kont po banie), a osobom postronnym zabiera
 * najwyzej mozliwosc zalozenia konta z tej sieci.</p>
 *
 * <p><b>Sesje juz otwarte dzialaja dalej</b> - blokada dziala od nastepnego
 * logowania. Zamkniecie ich natychmiast wymagaloby sprawdzania adresu przy
 * kazdym zapytaniu w calej aplikacji; przy koncie, ktore i tak zwykle jest
 * przy okazji kasowane, nie warto.</p>
 */
@Entity
@Table(name = "blocked_ips")
public class BlockedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Adres. Unikalny - ten sam adres nie ma sensu blokowac dwa razy,
     * a przy dwoch wierszach nie byloby wiadomo, ktory powod jest aktualny.
     */
    @Column(name = "address", nullable = false, unique = true,
            length = AccountIp.MAX_ADDRESS_LENGTH)
    private String address;

    /**
     * Dlaczego zablokowany.
     *
     * <p>Pole obowiazkowe, i to jest celowe. Blokada bez powodu jest nie do
     * odroczenia po miesiacu: nikt - lacznie z tym, kto ja nalozyl - nie
     * bedzie pamietal, czy wolno ja zdjac.</p>
     */
    @Column(nullable = false, length = 500)
    private String reason;

    /**
     * Kto zablokowal - <b>sam login, nie klucz obcy</b>.
     *
     * <p>Wpis ma przetrwac skasowanie konta administratora, ktory go zalozyl.
     * Klucz obcy albo zabranialby takiego skasowania, albo (przy kaskadzie)
     * po cichu zdjalby blokade razem z kontem.</p>
     */
    @Column(name = "blocked_by", nullable = false, length = 50)
    private String blockedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BlockedIp() {
    }

    public BlockedIp(String address, String reason, String blockedBy) {
        this.address = address;
        this.reason = reason;
        this.blockedBy = blockedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getReason() {
        return reason;
    }

    public String getBlockedBy() {
        return blockedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
