package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Karta osoby na liscie proponowanych znajomych.
 *
 * <p><b>Wysylamy skladniki wyniku, a nie sam wynik.</b> Liczba "14 punktow"
 * nikomu nic nie mowi i nie da sie jej sensownie wytlumaczyc. "2 wspolnych
 * artystow i 1 wspolny znajomy" mowi wszystko - i od razu podpowiada, co
 * zrobic, zeby dopasowania byly lepsze.</p>
 *
 * @param username       login osoby
 * @param avatarUrl      adres zdjecia profilowego albo {@code null}
 * @param mutualFriends  ilu znajomych mamy wspolnie
 * @param sharedArtists  ilu wykonawcow mamy wspolnie w ulubionych
 * @param sharedGenres   ile gatunkow mamy wspolnie
 * @param alreadyFriend  czy juz sie znamy - karta pokazuje wtedy inny przycisk
 * @param matched        czy jest cokolwiek wspolnego. Frontend dzieli po tym
 *                       liste na "dopasowani" i "pozostale osoby" - bez tego
 *                       ktos zupelnie przypadkowy wygladalby jak propozycja
 */
@Schema(description = "Proponowany znajomy wraz z powodem dopasowania")
public record SuggestionResponse(
    String username,
    String avatarUrl,
    long mutualFriends,
    long sharedArtists,
    long sharedGenres,
    boolean alreadyFriend,
    boolean matched
) {
}
