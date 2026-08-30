package com.musicclubapp.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

/** Playlista wystawiona przez uzytkownika na swoim profilu - gablotka. */
@Entity
@Table(
    name = "favorite_playlists",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_playlist_owner_item",
        columnNames = {"owner_id", "provider", "external_id"}),
    indexes = @Index(name = "idx_playlists_owner", columnList = "owner_id, position"))
public class FavoritePlaylist {

    /** Ile playlist miesci sie w gablotce. */
    public static final int MAX_PER_USER = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MusicProvider provider;

    /** Identyfikator playlisty w serwisie - sam kod, bez calego adresu. */
    @Column(name = "external_id", nullable = false, length = 300)
    private String externalId;

    /** Tytul pobrany RAZ, przy dodawaniu (oEmbed). Moze byc pusty. */
    @Column(length = 300)
    private String title;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** Miejsce w gablotce, liczone od zera. */
    @Column(nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    protected FavoritePlaylist() {
    }

    public FavoritePlaylist(User owner, MusicProvider provider, String externalId,
                            String title, String thumbnailUrl, int position) {
        this.owner = owner;
        this.provider = provider;
        this.externalId = externalId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public MusicProvider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FavoritePlaylist other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
