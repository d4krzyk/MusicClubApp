package com.musicclubapp.dto;

/**
 * Kafelek znajomego na pasku pod profilem.
 *
 * <p>Celowo malo pol - to ma byc lekka lista do przewijania, a nie pelny
 * profil. Po kliknieciu w kafelek i tak wchodzi sie na {@code /profil/{login}},
 * gdzie jest wszystko.</p>
 *
 * @param mutualFriends ilu znajomych ta osoba ma wspolnie z ogladajacym -
 *                      po tym sortujemy pasek. Docelowo doliczymy tu takze
 *                      wspolnych artystow ze Spotify.
 */
public record FriendCardResponse(
    String username,
    String avatarUrl,
    long mutualFriends
) {
}
