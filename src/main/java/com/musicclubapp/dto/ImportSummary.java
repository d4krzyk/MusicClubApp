package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Co dokladnie zrobil import z Last.fm.
 *
 * <p><b>Po co az tyle liczb.</b> Import prawie nigdy nie konczy sie
 * "wszystko albo nic": czesc pozycji juz jest w ulubionych, czesci nie ma
 * w katalogu Deezera, a reszta wchodzi. Gdyby odpowiedz brzmiala samo
 * "gotowe", uzytkownik po zobaczeniu 12 artystow zamiast 15 mialby prawo
 * sadzic, ze cos sie zepsulo. Tu widzi, co sie stalo z kazdym.</p>
 *
 * @param addedArtists    ilu wykonawcow faktycznie doszlo
 * @param addedTracks     ile utworow faktycznie doszlo
 * @param skipped         ile pozycji przepadlo, bo nie ma ich w katalogu Deezera
 * @param alreadyPresent  ile pozycji bylo juz wczesniej w ulubionych
 * @param limitReached  czy import zatrzymal sie na gornym limicie ulubionych
 */
@Schema(description = "Podsumowanie importu z Last.fm")
public record ImportSummary(
    int addedArtists,
    int addedTracks,
    int skipped,
    int alreadyPresent,
    boolean limitReached
) {
}
