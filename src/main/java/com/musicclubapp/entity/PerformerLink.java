package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Link wykonawcy z koncertow (strona, Spotify, Instagram...) - z importu wydarzen. Klucz to nazwa wykonawcy
 * sprowadzona przez {@code NameKeys}, wiec ten sam wykonawca w wielu wydarzeniach ma jeden zestaw linkow.
 */
@Entity
@Table(name = "performer_links",
    uniqueConstraints = @UniqueConstraint(name = "uk_performer_link", columnNames = {"name_key", "kind"}))
public class PerformerLink {

    public static final int MAX_URL = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PerformerLinkKind kind;

    @Column(nullable = false, length = MAX_URL)
    private String url;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PerformerLink() {
        // wymagany przez JPA
    }

    public PerformerLink(String nameKey, PerformerLinkKind kind, String url, LocalDateTime now) {
        this.nameKey = nameKey;
        this.kind = kind;
        this.url = url;
        this.updatedAt = now;
    }

    /** Nowy adres z importu; ten sam = bez zapisu. */
    public void update(String url, LocalDateTime now) {
        if (!url.equals(this.url)) {
            this.url = url;
            this.updatedAt = now;
        }
    }

    public String getNameKey() {
        return nameKey;
    }

    public PerformerLinkKind getKind() {
        return kind;
    }

    public String getUrl() {
        return url;
    }
}
