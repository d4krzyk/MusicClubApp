package com.musicclubapp.entity;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Propozycja "utworu tygodnia" w klanie: czlonek wrzuca link, reszta glosuje. Tydzien liczy sie od
 * poniedzialku ({@code weekStart}); po jego koncu wygrana propozycja zostaje w historii klanu,
 * a glosowanie nad nia sie zamyka.
 */
@Entity
@Table(name = "clan_tracks",
    indexes = @Index(name = "idx_clan_tracks_clan_week", columnList = "clan_id, week_start"))
public class ClanTrack {

    public static final int NOTE_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    @Enumerated(EnumType.STRING)
    @Column(name = "music_provider", nullable = false, length = 16)
    private MusicProvider musicProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "music_kind", nullable = false, length = 16)
    private MusicKind musicKind;

    @Column(name = "music_external_id", nullable = false, length = 300)
    private String musicExternalId;

    /** Tytul i miniaturka pobrane raz, przy dodawaniu (oEmbed) - jak przy poscie. */
    @Column(name = "music_title", length = 300)
    private String musicTitle;

    @Column(name = "music_thumbnail_url", length = 500)
    private String musicThumbnailUrl;

    /** Dopisek proponujacego: dlaczego ten utwor. */
    @Column(length = NOTE_MAX)
    private String note;

    /** Poniedzialek tygodnia, w ktorym dodano propozycje (czas polski). */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClanTrack() {
        // wymagany przez JPA
    }

    public ClanTrack(Clan clan, User proposer, MusicProvider provider, MusicKind kind, String externalId,
                     String title, String thumbnailUrl, String note, LocalDate weekStart, LocalDateTime now) {
        this.clan = clan;
        this.proposer = proposer;
        this.musicProvider = provider;
        this.musicKind = kind;
        this.musicExternalId = externalId;
        this.musicTitle = title;
        this.musicThumbnailUrl = thumbnailUrl;
        this.note = note;
        this.weekStart = weekStart;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public User getProposer() {
        return proposer;
    }

    public MusicProvider getMusicProvider() {
        return musicProvider;
    }

    public MusicKind getMusicKind() {
        return musicKind;
    }

    public String getMusicExternalId() {
        return musicExternalId;
    }

    public String getMusicTitle() {
        return musicTitle;
    }

    public String getMusicThumbnailUrl() {
        return musicThumbnailUrl;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
