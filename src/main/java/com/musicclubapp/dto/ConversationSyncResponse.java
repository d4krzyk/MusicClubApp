package com.musicclubapp.dto;

import java.util.List;

/**
 * Odpowiedz na pytanie <b>"co nowego w tej rozmowie"</b>.
 *
 * <p><b>Trzy rzeczy w jednej odpowiedzi, i to jest caly sens tej klasy.</b>
 * Otwarte okno czatu musi wiedziec: czy przyszly nowe wiadomosci, czy druga
 * strona wlasnie pisze i czy jest online. To sa trzy rozne pytania, ale
 * zadawane w tym samym rytmie - co kilka sekund, dopoki okno jest otwarte.
 * Trzy osobne adresy oznaczalyby trzykrotnie wiecej zapytan bez zadnego
 * zysku.</p>
 *
 * <p><b>Dlaczego odpytywanie, a nie WebSocket.</b> Prawdziwy czat "na zywo"
 * wymaga stalego polaczenia (WebSocket albo SSE), a to znaczy: druga sciezka
 * uwierzytelniania obok sesji HTTP, wlasny stan polaczen na serwerze i
 * obsluga zrywania sieci po stronie przegladarki. Przy rozmowie dwoch osob
 * roznica miedzy "natychmiast" a "w ciagu trzech sekund" jest niezauwazalna,
 * a kodu do utrzymania - kilka razy mniej. Odpytywanie chodzi tylko wtedy,
 * gdy okno jest otwarte, i pyta o same nowosci, a nie o cala rozmowe.</p>
 *
 * @param messages      wiadomosci nowsze niz ta, ktora przegladarka juz ma
 * @param partnerTyping czy druga strona wlasnie pisze
 * @param presence      czy druga strona jest online i kiedy byla ostatnio
 * @param unread        ile moich nieprzeczytanych zostalo w calej aplikacji -
 *                      dzieki temu licznik przy ikonie czatu odswieza sie
 *                      sam, bez osobnego zapytania
 * @param lastReadOutgoingId do ktorej MOJEJ wiadomosci rozmowca doczytal;
 *                      {@code null}, gdy jeszcze zadnej. Przegladarka
 *                      oznacza po tym ptaszkiem wszystko do tego numeru -
 *                      patrz {@code MessageRepository.lastReadOutgoingId}
 * @param friend        czy z ta osoba wolno teraz PISAC. Chodzi tu przede
 *                      wszystkim o chwile, w ktorej znajomosc znika przy
 *                      OTWARTYM oknie rozmowy - bez tego pole do pisania
 *                      zostaloby aktywne, a wyslanie konczyloby sie bledem
 *                      dopiero po klknieciu "wyslij"
 */
public record ConversationSyncResponse(
    List<MessageResponse> messages,
    boolean partnerTyping,
    PresenceResponse presence,
    long unread,
    Long lastReadOutgoingId,
    boolean friend
) {
}
