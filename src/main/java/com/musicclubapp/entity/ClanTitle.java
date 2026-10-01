package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Tytul (rola) w klanie, wymyslony przez zarzad: "DJ", "Fotograf", "Organizator koncertow".
 * Poza rola z uprawnieniami ({@link ClanRole}) niczego nie daje - to ozdoba i informacja.
 * Moze go nadawac zarzad, brac sobie kazdy albo przychodzi sam z aktywnoscia.
 */
@Entity
@Table(name = "clan_titles",
    uniqueConstraints = @UniqueConstraint(name = "uk_clan_titles_name", columnNames = {"clan_id", "name_key"}),
    indexes = @Index(name = "idx_clan_titles_clan", columnList = "clan_id"))
public class ClanTitle {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 24;
    public static final int MAX_PER_CLAN = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    /** Nazwa bez wielkosci liter i znakow diakrytycznych - unikalnosc w obrebie klanu. */
    @Column(name = "name_key", nullable = false, length = 64)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanColor color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanTitleMode mode;

    /** Tylko przy {@link ClanTitleMode#AUTO}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ClanActivityMetric metric;

    @Column(name = "threshold")
    private Integer threshold;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanTitle() {
        // wymagany przez JPA
    }

    public ClanTitle(Clan clan, String name, String nameKey, ClanColor color, ClanTitleMode mode,
                     ClanActivityMetric metric, Integer threshold, LocalDateTime now) {
        this.clan = clan;
        this.createdAt = now;
        edit(name, nameKey, color, mode, metric, threshold);
    }

    public void edit(String name, String nameKey, ClanColor color, ClanTitleMode mode,
                     ClanActivityMetric metric, Integer threshold) {
        this.name = name;
        this.nameKey = nameKey;
        this.color = color;
        this.mode = mode;
        this.metric = mode == ClanTitleMode.AUTO ? metric : null;
        this.threshold = mode == ClanTitleMode.AUTO ? threshold : null;
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public ClanColor getColor() {
        return color;
    }

    public ClanTitleMode getMode() {
        return mode;
    }

    public ClanActivityMetric getMetric() {
        return metric;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
