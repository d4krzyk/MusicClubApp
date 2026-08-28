package com.musicclubapp.dto;

/**
 * Jedna pozycja na liscie rozmow w oknie czatu.
 *
 * <p><b>Sa tu WSZYSCY znajomi, takze ci, z ktorymi nikt jeszcze nie zamienil
 * slowa</b> - u nich {@link #lastMessage()} jest puste. To celowe: lista sluzy
 * do <i>zaczynania</i> rozmow, a nie tylko do wracania do juz zaczetych.
 * Gdyby pokazywala wylacznie istniejace watki, nowy uzytkownik zobaczylby
 * pustke i nie mialby jak nikogo zaczepic.</p>
 *
 * <p>Kolejnosc ustala serwer: najpierw rozmowy z najnowsza wiadomoscia,
 * potem reszta znajomych alfabetycznie - patrz {@code MessageService}.</p>
 *
 * @param lastMessage ostatnia wiadomosc w tej rozmowie albo {@code null}
 * @param unread      ile jej wiadomosci czeka na moje przeczytanie
 */
public record ConversationResponse(
    String username,
    String avatarUrl,
    PresenceResponse presence,
    MessageResponse lastMessage,
    long unread
) {
}
