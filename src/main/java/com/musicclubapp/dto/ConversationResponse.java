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
 * <p><b>Sa tu takze BYLI znajomi, z ktorymi rozmowa juz sie odbyla</b> -
 * u nich {@link #friend()} jest {@code false}. Wczesniej tacy ludzie po prostu
 * znikali z listy razem z cala historia, co dla obu stron wygladalo jak awaria:
 * nie bylo wiadomo, czy ktos usunal konto, zerwal znajomosc, czy cos sie
 * zepsulo. Rozmowa zostaje widoczna, ale nie da sie w niej nic napisac.</p>
 *
 * @param lastMessage ostatnia wiadomosc w tej rozmowie albo {@code null}
 * @param unread      ile jej wiadomosci czeka na moje przeczytanie
 * @param friend      czy ta osoba jest teraz znajomym; {@code false} znaczy
 *                    "rozmowe mozna czytac, ale nie mozna do niej pisac"
 */
public record ConversationResponse(
    String username,
    String avatarUrl,
    PresenceResponse presence,
    MessageResponse lastMessage,
    long unread,
    boolean friend
) {
}
