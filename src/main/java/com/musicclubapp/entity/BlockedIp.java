package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Zablokowany adres sieciowy. */
@Entity
@Table(name = "blocked_ips")
public class BlockedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Adres. */
    @Column(name = "address", nullable = false, unique = true,
            length = AccountIp.MAX_ADDRESS_LENGTH)
    private String address;

    /** Dlaczego zablokowany. */
    @Column(nullable = false, length = 500)
    private String reason;

    /** Kto zablokowal - sam login, nie klucz obcy. */
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
