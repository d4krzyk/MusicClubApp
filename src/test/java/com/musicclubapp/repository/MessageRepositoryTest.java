package com.musicclubapp.repository;

import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Lista rozmow na prawdziwej bazie. */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Lista rozmow - zapytania na bazie")
class MessageRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    private User save(String username) {
        return userRepository.save(new User(username, username + "@example.com", "hash"));
    }

    private Message send(User from, User to, String text) {
        return messageRepository.save(new Message(from, to, text));
    }

    /** Wynik w wygodniejszej postaci: z kim rozmawiamy -> ostatnia wiadomosc. */
    private Map<Long, Long> conversationsOf(User me) {
        return messageRepository.lastMessagePerConversation(me.getId()).stream()
            .collect(Collectors.toMap(ConversationRow::partnerId, ConversationRow::lastMessageId));
    }

    @Test
    @DisplayName("rozmowa liczy sie tak samo, gdy pisalismy my i gdy pisano do nas")
    void countsBothDirections() {
        User ala = save("ala");
        User bob = save("bob");
        User cyryl = save("cyryl");

        // Do Boba napisalismy my, Cyryl napisal do nas - obaj maja sie pojawic
        Message doBoba = send(ala, bob, "czesc");
        Message odCyryla = send(cyryl, ala, "hej");

        assertThat(conversationsOf(ala))
            .containsOnlyKeys(bob.getId(), cyryl.getId())
            .containsEntry(bob.getId(), doBoba.getId())
            .containsEntry(cyryl.getId(), odCyryla.getId());
    }

    @Test
    @DisplayName("z jednej rozmowy zostaje NAJNOWSZA wiadomosc, niezaleznie od nadawcy")
    void keepsTheNewestMessageOfAConversation() {
        User ala = save("ala");
        User bob = save("bob");

        send(ala, bob, "pierwsza");
        send(bob, ala, "druga");
        Message ostatnia = send(ala, bob, "trzecia");

        /*
         * Sedno scalania: ta sama osoba wystepuje w obu zapytaniach - raz jako odbiorca, raz jako
         * nadawca.
         */
        assertThat(conversationsOf(ala))
            .hasSize(1)
            .containsEntry(bob.getId(), ostatnia.getId());
    }

    @Test
    @DisplayName("kazda osoba widzi rozmowe po swojej stronie")
    void bothSidesSeeTheSameConversation() {
        User ala = save("ala");
        User bob = save("bob");

        Message wiadomosc = send(ala, bob, "czesc");

        assertThat(conversationsOf(ala)).containsEntry(bob.getId(), wiadomosc.getId());
        assertThat(conversationsOf(bob)).containsEntry(ala.getId(), wiadomosc.getId());
    }

    @Test
    @DisplayName("cudze rozmowy nie wyciekaja na nasza liste")
    void doesNotLeakOtherPeoplesConversations() {
        User ala = save("ala");
        User bob = save("bob");
        User cyryl = save("cyryl");

        send(bob, cyryl, "nie nasza sprawa");

        assertThat(messageRepository.lastMessagePerConversation(ala.getId())).isEmpty();
    }

    @Test
    @DisplayName("brak wiadomosci to pusta lista, a nie blad")
    void noMessagesGivesEmptyList() {
        User ala = save("ala");

        List<ConversationRow> rows = messageRepository.lastMessagePerConversation(ala.getId());

        /*
         * Rozroznienie, ktore w tej funkcji okazalo sie kluczowe: „pusto" i „nie udalo sie" to DWA
         * rozne stany.
         */
        assertThat(rows).isEmpty();
    }
}
