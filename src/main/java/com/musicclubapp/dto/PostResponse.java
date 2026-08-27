package com.musicclubapp.dto;

import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Post wysylany do przegladarki.
 *
 * <p>Zamiast nazw plikow zwracamy gotowe adresy ({@code /uploads/abc.jpg}),
 * zeby frontend nie musial ich sam skladac. Gdy kiedys pliki przeniosa sie
 * gdzie indziej, wystarczy zmiana w mapperze - frontend zostaje bez zmian.</p>
 *
 * <p>Tak samo z muzyka: frontend dostaje <b>gotowy adres do {@code <iframe>}</b>
 * i nie musi wiedziec, jak kazdy serwis sklada swoje adresy osadzenia.
 * Dolozenie trzeciego serwisu nie wymaga wtedy ruszania Reacta.</p>
 */
public record PostResponse(
    Long id,
    String authorUsername,
    String authorAvatarUrl,
    String content,
    List<String> imageUrls,
    /** Gotowy adres odtwarzacza do {@code <iframe>} albo {@code null}. */
    String musicEmbedUrl,
    /** Serwis - frontend pokazuje przy odtwarzaczu "Spotify" / "YouTube". */
    MusicProvider musicProvider,
    MusicKind musicKind,
    /** Tytul pobrany przy dodawaniu posta; moze byc {@code null}. */
    String musicTitle,
    String musicThumbnailUrl,
    Integer musicStartSeconds,
    /**
     * Adres strony w serwisie - wstawiamy go w formularz edycji
     * i pod przycisk "otworz w serwisie".
     */
    String musicUrl,
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
    /** Liczniki reakcji i informacja, ktora z nich wybral ogladajacy. */
    ReactionSummary reactions,
    /** Publiczny czy tylko dla znajomych - frontend rysuje przy nim plakietke. */
    PostVisibility visibility,
    /**
     * Czy autor jest w kregu ogladajacego (jego znajomym albo nim samym).
     *
     * <p>Po co to na zewnatrz: tablica rysuje po ostatnim takim poscie
     * kreske "dalej: osoby, ktorych jeszcze nie znasz". <b>Frontend nie ma
     * z czego tego wyliczyc</b> - listy znajomych ogladajacego w ogole nie
     * pobiera, a nawet gdyby, byloby to drugie miejsce liczace to samo,
     * ktore predzej czy pozniej rozjechaloby sie z kolejnoscia z bazy.</p>
     */
    boolean fromFriend
) {
}
