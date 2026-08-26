package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Kogo chce zaprosic do znajomych.
 *
 * <p>Podajemy LOGIN, a nie identyfikator z bazy. Powod jest praktyczny:
 * frontend i tak wszedzie operuje loginami (adresy profili to
 * {@code /profil/{login}}), a numeryczne id uzytkownikow nigdzie poza panelem
 * administratora nie wychodzi na zewnatrz - i dobrze, bo po kolejnych id
 * latwo policzyc, ilu jest uzytkownikow serwisu.</p>
 */
public record CreateFriendRequest(

    @NotBlank(message = "{validation.friend.username.required}")
    String username
) {
}
