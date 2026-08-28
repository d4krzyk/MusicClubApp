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

/**
 * Lista rozmow na prawdziwej bazie.
 *
 * <p><b>Po co ten test istnieje.</b> {@code MessageServiceTest} pracuje na
 * atrapie repozytorium, wiec sprawdza wylacznie to, co sami tej atrapie
 * kazemy oddac - do SQL-a nie zaglada nigdy. Tymczasem wlasnie w SQL-u byl
 * blad, przez ktory czat u uzytkownika w ogole sie nie otwieral: kazde
 * wejscie w wiadomosci konczylo sie odpowiedzia 500, a na ekranie widac bylo
 * komunikat „nie masz jeszcze z kim pisac" - bo przegladarka traktowala
 * awarie jak pusta liste.</p>
 *
 * <p><b>Uczciwe zastrzezenie.</b> Ten test chodzi po H2, a blad, o ktorym
 * mowa, <b>na H2 nie wystepuje</b> - poprzednia wersja zapytania przechodzila
 * tu na zielono i dopiero PostgreSQL ja odrzucil (szczegoly przy
 * {@link MessageRepository#lastSentPerPartner}). Nie udaje wiec, ze pilnuje
 * zgodnosci z Postgresem; pilnuje tego, co sprawdzic tu mozna, czyli czy
 * scalanie dwoch zapytan w jedna liste rozmow daje poprawny wynik.</p>
 */
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
         * Sedno scalania: ta sama osoba wystepuje w obu zapytaniach - raz
         * jako odbiorca, raz jako nadawca. Gdyby scalanie bralo dowolna
         * z dwoch wartosci zamiast wiekszej, na liscie rozmow swiecilby
         * podglad SPRZED odpowiedzi.
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
         * Rozroznienie, ktore w tej funkcji okazalo sie kluczowe: „pusto"
         * i „nie udalo sie" to DWA rozne stany. Zapytanie ma tu spokojnie
         * oddac pusta liste - komunikat o braku znajomych ma sie pokazywac
         * tylko wtedy, gdy naprawde ich nie ma.
         */
        assertThat(rows).isEmpty();
    }
}
