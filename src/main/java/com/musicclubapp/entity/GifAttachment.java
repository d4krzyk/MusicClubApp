package com.musicclubapp.entity;

import com.musicclubapp.dto.GifView;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * GIF dolaczony do komentarza albo wiadomosci. Same adresy i wymiary - pliku nie kopiujemy, przegladarka
 * laduje go z serwera dostawcy GIF-ow. Adresy pochodza wylacznie z podpisanych wynikow wyszukiwania
 * ({@code GifService#attach}), wiec nikt nie podstawi tu dowolnego adresu.
 *
 * <p>Ta sama czworka kolumn siedzi w {@code comments} i {@code messages}; gdy wszystkie sa puste, Hibernate
 * oddaje {@code null}, czyli "bez GIF-a".</p>
 */
@Embeddable
public class GifAttachment {

    public static final int MAX_URL = 500;
    public static final int MAX_TITLE = 150;

    @Column(name = "gif_url", length = MAX_URL)
    private String url;

    @Column(name = "gif_preview_url", length = MAX_URL)
    private String previewUrl;

    @Column(name = "gif_width")
    private Integer width;

    @Column(name = "gif_height")
    private Integer height;

    @Column(name = "gif_title", length = MAX_TITLE)
    private String title;

    protected GifAttachment() {
    }

    public GifAttachment(String url, String previewUrl, int width, int height, String title) {
        this.url = url;
        this.previewUrl = previewUrl;
        this.width = width;
        this.height = height;
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getTitle() {
        return title;
    }

    /** Postac wysylana do przegladarki. */
    public GifView toView() {
        return new GifView(url, previewUrl, width == null ? 0 : width, height == null ? 0 : height, title);
    }

    /** Krotki opis do zrzutu dowodowego przy zgloszeniu i do eksportu danych. */
    public String describe() {
        return "[GIF] " + url + (title == null || title.isBlank() ? "" : " (" + title + ")");
    }
}
