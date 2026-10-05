package com.musicclubapp.entity;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Nagranie dolaczone do wiadomosci na czacie klanu - te same szesc kolumn {@code music_*} co w postach i w rozmowach,
 * tylko zebrane w jeden obiekt. Wszystkie puste = brak nagrania (Hibernate oddaje wtedy {@code null}).
 */
@Embeddable
public class MusicAttachment {

    @Enumerated(EnumType.STRING)
    @Column(name = "music_provider", length = 16)
    private MusicProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "music_kind", length = 16)
    private MusicKind kind;

    @Column(name = "music_external_id", length = 300)
    private String externalId;

    @Column(name = "music_title", length = 300)
    private String title;

    @Column(name = "music_thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "music_start_seconds")
    private Integer startSeconds;

    protected MusicAttachment() {
        // wymagany przez JPA
    }

    public MusicAttachment(ParsedMusicLink link, String title, String thumbnailUrl) {
        this.provider = link.provider();
        this.kind = link.kind();
        this.externalId = link.externalId();
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    public MusicProvider getProvider() {
        return provider;
    }

    public MusicKind getKind() {
        return kind;
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

    public Integer getStartSeconds() {
        return startSeconds;
    }
}
