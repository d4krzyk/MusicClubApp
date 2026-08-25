package com.musicclubapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Post wysylany do przegladarki.
 *
 * <p>Zamiast nazw plikow zwracamy gotowe adresy ({@code /uploads/abc.jpg}),
 * zeby frontend nie musial ich sam skladac. Gdy kiedys pliki przeniosa sie
 * gdzie indziej, wystarczy zmiana w mapperze - frontend zostaje bez zmian.</p>
 *
 * @param spotifyEmbedUrl gotowy adres odtwarzacza Spotify do wstawienia
 *                        w {@code <iframe>}, albo {@code null}
 */
public record PostResponse(
    Long id,
    String authorUsername,
    String authorAvatarUrl,
    String content,
    List<String> imageUrls,
    String spotifyEmbedUrl,
    Integer spotifyStartSeconds,
    LocalDateTime createdAt,
    /** Czy zalogowany uzytkownik moze skasowac ten post (jest autorem albo adminem). */
    boolean canDelete,
    /**
     * Czy zalogowany uzytkownik moze edytowac ten post.
     *
     * <p>Tylko autor. Administrator moderuje przez USUWANIE, a nie przez
     * przerabianie cudzych wypowiedzi - inaczej pod czyims nazwiskiem
     * moglaby sie pojawic tresc, ktorej nigdy nie napisal.</p>
     */
    boolean canEdit,
    /**
     * Adres utworu w postaci nadajacej sie do wklejenia w formularz edycji.
     * Skladany z zapisanego identyfikatora, wiec bez parametrow sledzacych.
     */
    String spotifyUrl
) {
}
