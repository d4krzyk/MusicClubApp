package com.musicclubapp.dto;

/**
 * Najkrotsza mozliwa wizytowka uzytkownika: nazwa i awatar.
 *
 * <p><b>Po co osobny typ, skoro jest juz {@link FriendCardResponse}.</b>
 * Bo tamten niesie takze liczbe wspolnych znajomych, a tutaj nie mielibysmy
 * jej czym wypelnic - zostaloby zero, czyli liczba, ktora wyglada na
 * prawdziwa i nie jest. Lepszy jeden maly rekord wiecej niz pole, ktore
 * klamie.</p>
 */
public record PersonCard(String username, String avatarUrl) {
}
