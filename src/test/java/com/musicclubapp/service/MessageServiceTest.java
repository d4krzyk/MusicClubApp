package com.musicclubapp.service;

import com.musicclubapp.entity.BanKind;
import com.musicclubapp.dto.ConversationResponse;
import com.musicclubapp.dto.ConversationSyncResponse;
import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.dto.SendMessageRequest;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Czat: kto z kim moze pisac, w jakiej kolejnosci wracaja wiadomosci i co znaczy "nieprzeczytane".
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Czat ze znajomymi")
class MessageServiceTest {

    /** Link uzywany w testach - prawdziwy format Spotify, 22 znaki identyfikatora. */
    private static final String SPOTIFY_TRACK =
        "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT";

    @Autowired private MessageService messages;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TypingRegistry typing;
    @Autowired private EntityManager entityManager;

    @MockBean private MusicMetadataService musicMetadata;

    private User ala;
    private User bartek;
    private User obcy;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        bartek = userRepository.save(new User("bartek", "bartek@example.com", "hash"));
        obcy = userRepository.save(new User("obcy", "obcy@example.com", "hash"));

        ala.addFriend(bartek);
        userRepository.save(ala);
        userRepository.save(bartek);

        given(musicMetadata.fetch(any()))
            .willReturn(new MusicMetadataService.Metadata("Tytul z serwisu", "https://obrazek"));

        entityManager.flush();
    }

    private SendMessageRequest text(String content) {
        return new SendMessageRequest(content, null, null, null);
    }

    /* ------------------------------------------------------------------ */
    /*  Kto z kim moze pisac                                               */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("do znajomego wiadomosc dochodzi")
    void sendsToFriend() {
        MessageResponse sent = messages.send("ala", "bartek", text("Slyszales to?"));

        assertThat(sent.content()).isEqualTo("Slyszales to?");
        assertThat(sent.senderUsername()).isEqualTo("ala");
        // "mine" liczone wzgledem NADAWCY, bo to on dostaje odpowiedz
        assertThat(sent.mine()).isTrue();
        assertThat(sent.read()).isFalse();
    }

    @Test
    @DisplayName("do OBCEGO pisac nie wolno - to jest glowna blokada czatu")
    void refusesToWriteToStranger() {
        /*
         * Bez tego sprawdzenia kazdy moglby pisac do kazdego, a serwis o wspolnym guscie muzycznym
         * zamienilby sie w skrzynke na zaczepki.
         */
        assertThatThrownBy(() -> messages.send("ala", "obcy", text("czesc")))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("znajomymi");

        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("blokada obowiazuje takze przy CZYTANIU cudzej rozmowy")
    void refusesToReadStrangersConversation() {
        /* To jest osobny test celowo. */
        messages.send("ala", "bartek", text("prywatnie"));

        assertThatThrownBy(() -> messages.conversation("obcy", "ala", PageRequest.of(0, 20)))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThatThrownBy(() -> messages.sync("obcy", "ala", null))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("do samego siebie nie da sie napisac - i mowimy to wprost")
    void refusesToWriteToSelf() {
        assertThatThrownBy(() -> messages.send("ala", "ala", text("notatka")))
            .isInstanceOf(OperationNotAllowedException.class)
            // Osobny komunikat od "tylko ze znajomymi" - to zwykla pomylka,
            // a nie proba obejscia blokady
            .hasMessageContaining("samego siebie");
    }

    @Test
    @DisplayName("nieistniejacy odbiorca to 404, a nie 'nie jest znajomym'")
    void unknownRecipientIsNotFound() {
        assertThatThrownBy(() -> messages.send("ala", "nie-ma-takiego", text("halo")))
            .isInstanceOf(NoSuchElementFoundException.class);
    }

    @Test
    @DisplayName("zakaz WIADOMOSCI blokuje czat")
    void messagingBanBlocksChat() {
        ala.setBannedUntil(BanKind.MESSAGING, LocalDateTime.now().plusHours(3));
        userRepository.save(ala);

        assertThatThrownBy(() -> messages.send("ala", "bartek", text("mimo wszystko")))
            .isInstanceOf(OperationNotAllowedException.class)
            /* Sprawdzamy KLUCZ, a nie tresc wyjatku. */
            .extracting(e -> ((OperationNotAllowedException) e).getMessageKey())
            .isEqualTo("error.ban.messaging");
    }

    @Test
    @DisplayName("zakaz PUBLIKOWANIA nie zamyka czatu - to dwie osobne kary")
    void postingBanDoesNotBlockChat() {
        /* Zmiana wzgledem pierwszej wersji czatu, i to swiadoma. */
        ala.setBannedUntil(BanKind.POSTING, LocalDateTime.now().plusHours(3));
        userRepository.save(ala);

        assertThat(messages.send("ala", "bartek", text("posty mi zablokowali")).content())
            .isEqualTo("posty mi zablokowali");
    }

    @Test
    @DisplayName("wygasly zakaz juz nie blokuje - konczy sie sam, bez zadania w tle")
    void expiredBanStopsBlocking() {
        ala.setBannedUntil(BanKind.MESSAGING, LocalDateTime.now().minusMinutes(1));
        userRepository.save(ala);

        assertThat(messages.send("ala", "bartek", text("wrocilam")).content())
            .isEqualTo("wrocilam");
    }

    /* ------------------------------------------------------------------ */
    /*  Tresc i muzyka                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("wiadomosc moze byc SAMYM utworem, bez ani jednego slowa")
    void musicOnlyMessage() {
        /* Po to ten czat powstal. */
        MessageResponse sent = messages.send("ala", "bartek",
            new SendMessageRequest(null, SPOTIFY_TRACK, MusicKind.TRACK, null));

        assertThat(sent.content()).isNull();
        assertThat(sent.musicProvider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(sent.musicKind()).isEqualTo(MusicKind.TRACK);
        assertThat(sent.musicTitle()).isEqualTo("Tytul z serwisu");
        // Frontend dostaje GOTOWY adres odtwarzacza - nie sklada go sam
        assertThat(sent.musicEmbedUrl())
            .isEqualTo("https://open.spotify.com/embed/track/4cOdK2wGLETKBW3PvgPWqT");
        assertThat(sent.musicUrl()).isEqualTo(SPOTIFY_TRACK);
    }

    @Test
    @DisplayName("moment startu trafia do adresu odtwarzacza")
    void startSecondsReachThePlayer() {
        MessageResponse sent = messages.send("ala", "bartek",
            new SendMessageRequest("od refrenu", SPOTIFY_TRACK, MusicKind.TRACK, 83));

        assertThat(sent.musicStartSeconds()).isEqualTo(83);
        assertThat(sent.musicEmbedUrl()).endsWith("?t=83");
    }

    @Test
    @DisplayName("sama spacja w tresci zapisuje sie jako BRAK tresci")
    void blankContentBecomesNull() {
        /*
         * Bez tego wiadomosc "sam utwor" mialaby w bazie raz null, raz pusty napis, raz spacje -
         * zaleznie od tego, co zostalo w polu.
         */
        MessageResponse sent = messages.send("ala", "bartek",
            new SendMessageRequest("   ", SPOTIFY_TRACK, MusicKind.TRACK, null));

        assertThat(sent.content()).isNull();
    }

    /* ------------------------------------------------------------------ */
    /*  Historia rozmowy                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("historia wraca od NAJNOWSZEJ - czat otwiera sie na koncu rozmowy")
    void historyStartsWithTheNewest() {
        messages.send("ala", "bartek", text("pierwsza"));
        messages.send("bartek", "ala", text("druga"));
        messages.send("ala", "bartek", text("trzecia"));

        List<MessageResponse> page =
            messages.conversation("ala", "bartek", PageRequest.of(0, 20)).getContent();

        assertThat(page).extracting(MessageResponse::content)
            .containsExactly("trzecia", "druga", "pierwsza");
    }

    @Test
    @DisplayName("rozmowa zawiera OBIE strony i nic poza nimi")
    void conversationHoldsBothSidesOnly() {
        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        celina.addFriend(ala);
        userRepository.save(celina);
        userRepository.save(ala);
        entityManager.flush();

        messages.send("ala", "bartek", text("do bartka"));
        messages.send("bartek", "ala", text("od bartka"));
        messages.send("ala", "celina", text("do celiny"));

        assertThat(messages.conversation("ala", "bartek", PageRequest.of(0, 20)))
            .extracting(MessageResponse::content)
            .containsExactlyInAnyOrder("do bartka", "od bartka");
    }

    @Test
    @DisplayName("pole 'mine' zalezy od tego, KTO patrzy")
    void mineDependsOnTheViewer() {
        messages.send("ala", "bartek", text("moje"));

        assertThat(messages.conversation("ala", "bartek", PageRequest.of(0, 20))
            .getContent().get(0).mine()).isTrue();

        assertThat(messages.conversation("bartek", "ala", PageRequest.of(0, 20))
            .getContent().get(0).mine()).isFalse();
    }

    /* ------------------------------------------------------------------ */
    /*  Nieprzeczytane                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("licznik nieprzeczytanych liczy tylko to, co przyszlo DO MNIE")
    void unreadCountsOnlyIncoming() {
        messages.send("bartek", "ala", text("raz"));
        messages.send("bartek", "ala", text("dwa"));
        messages.send("ala", "bartek", text("moja wlasna"));

        // Wlasne wiadomosci nigdy nie sa "nieprzeczytane" dla nadawcy
        assertThat(messages.unreadCount("ala")).isEqualTo(2);
        assertThat(messages.unreadCount("bartek")).isEqualTo(1);
    }

    @Test
    @DisplayName("oznaczenie przeczytanych dotyczy JEDNEJ rozmowy, nie wszystkich")
    void markingReadTouchesOneConversationOnly() {
        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        celina.addFriend(ala);
        userRepository.save(celina);
        userRepository.save(ala);
        entityManager.flush();

        messages.send("bartek", "ala", text("od bartka"));
        messages.send("celina", "ala", text("od celiny"));

        assertThat(messages.markRead("ala", "bartek")).isEqualTo(1);

        // Wiadomosc od Celiny ma zostac nieprzeczytana
        assertThat(messages.unreadCount("ala")).isEqualTo(1);
    }

    @Test
    @DisplayName("powtorne oznaczenie nie zmienia juz niczego")
    void markingReadTwiceChangesNothing() {
        messages.send("bartek", "ala", text("halo"));

        assertThat(messages.markRead("ala", "bartek")).isEqualTo(1);
        assertThat(messages.markRead("ala", "bartek")).isZero();
    }

    /* ------------------------------------------------------------------ */
    /*  Odpytywanie otwartego okna                                         */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("odpytywanie oddaje TYLKO to, czego przegladarka jeszcze nie ma")
    void syncReturnsOnlyNewMessages() {
        MessageResponse first = messages.send("bartek", "ala", text("pierwsza"));
        messages.send("bartek", "ala", text("druga"));

        ConversationSyncResponse sync = messages.sync("ala", "bartek", first.id());

        assertThat(sync.messages()).extracting(MessageResponse::content)
            .containsExactly("druga");
    }

    @Test
    @DisplayName("odebranie nowej wiadomosci od razu oznacza rozmowe jako przeczytana")
    void syncMarksIncomingAsRead() {
        /*
         * Skoro okno rozmowy jest otwarte i wlasnie pokazalo wiadomosc, to znaczy, ze zostala
         * przeczytana.
         */
        messages.send("bartek", "ala", text("czytasz to?"));

        ConversationSyncResponse sync = messages.sync("ala", "bartek", null);

        assertThat(sync.messages()).hasSize(1);
        assertThat(sync.unread()).isZero();
        assertThat(messages.unreadCount("ala")).isZero();
    }

    @Test
    @DisplayName("odpytywanie NIE oznacza jako przeczytane wlasnych wiadomosci nadawcy")
    void syncDoesNotMarkOwnMessages() {
        /* Ala pisze i sama odpytuje. */
        messages.send("ala", "bartek", text("moja"));
        messages.sync("ala", "bartek", null);

        assertThat(messages.unreadCount("bartek")).isEqualTo(1);
    }

    @Test
    @DisplayName("nadawca dowiaduje sie, ze jego wiadomosc zostala przeczytana")
    void senderLearnsThatMessageWasRead() {
        /* Blad, ktory to wymusil, wyszedl dopiero w przegladarce. */
        MessageResponse mine = messages.send("ala", "bartek", text("czytasz?"));

        // Zanim Bartek przeczyta - nie ma czego potwierdzac
        assertThat(messages.sync("ala", "bartek", mine.id()).lastReadOutgoingId()).isNull();

        messages.markRead("bartek", "ala");

        assertThat(messages.sync("ala", "bartek", mine.id()).lastReadOutgoingId())
            .isEqualTo(mine.id());
    }

    @Test
    @DisplayName("potwierdzenie przeczytania dotyczy TYLKO wlasnych wiadomosci")
    void readReceiptCoversOwnMessagesOnly() {
        /*
         * Gdyby zapytanie nie sprawdzalo nadawcy, przeczytanie CUDZEJ wiadomosci (czyli zwykle
         * otwarcie rozmowy) zapalaloby ptaszek pod wlasnymi - czyli falszywe "on to widzial".
         */
        messages.send("bartek", "ala", text("od bartka"));
        MessageResponse mine = messages.send("ala", "bartek", text("moja"));

        // Ala czyta wiadomosc Bartka; jej wlasna wciaz nieprzeczytana przez niego
        messages.markRead("ala", "bartek");

        assertThat(messages.sync("ala", "bartek", mine.id()).lastReadOutgoingId()).isNull();
    }

    @Test
    @DisplayName("odpytywanie mowi, ze druga strona wlasnie pisze")
    void syncReportsTyping() {
        messages.typing("bartek", "ala");

        assertThat(messages.sync("ala", "bartek", null).partnerTyping()).isTrue();
    }

    @Test
    @DisplayName("sygnal 'pisze' ma KIERUNEK - moje pisanie to nie jest pisanie rozmowcy")
    void typingHasDirection() {
        /*
         * Bez kierunku w kluczu wlasne pisanie zapalaloby dymek u siebie samego: "Bartek pisze..."
         * pokazywaloby sie Bartkowi w chwili, gdy to on stuka w klawiature.
         */
        messages.typing("ala", "bartek");

        assertThat(messages.sync("ala", "bartek", null).partnerTyping()).isFalse();
        assertThat(messages.sync("bartek", "ala", null).partnerTyping()).isTrue();
    }

    @Test
    @DisplayName("wyslanie wiadomosci gasi wlasny dymek 'pisze'")
    void sendingStopsTyping() {
        /*
         * Bez tego dymek z kropkami wisialby jeszcze kilka sekund POD wlasnie dostarczona
         * wiadomoscia, co wyglada jak usterka.
         */
        messages.typing("bartek", "ala");
        messages.send("bartek", "ala", text("juz napisalem"));

        assertThat(typing.isTyping(bartek.getId(), ala.getId())).isFalse();
    }

    /* ------------------------------------------------------------------ */
    /*  Lista rozmow                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("lista rozmow zawiera znajomych, z ktorymi NIC jeszcze nie napisano")
    void conversationsIncludeSilentFriends() {
        /* Lista sluzy do ZACZYNANIA rozmow, a nie tylko do wracania do juz zaczetych. */
        List<ConversationResponse> list = messages.conversations("ala");

        assertThat(list).extracting(ConversationResponse::username).containsExactly("bartek");
        assertThat(list.get(0).lastMessage()).isNull();
        assertThat(list.get(0).unread()).isZero();
    }

    @Test
    @DisplayName("na gorze listy stoi rozmowa z NAJNOWSZA wiadomoscia")
    void conversationsSortedByLastMessage() {
        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        User dawid = userRepository.save(new User("dawid", "d@example.com", "hash"));
        celina.addFriend(ala);
        dawid.addFriend(ala);
        userRepository.save(celina);
        userRepository.save(dawid);
        userRepository.save(ala);
        entityManager.flush();

        messages.send("ala", "celina", text("do celiny"));
        messages.send("ala", "bartek", text("do bartka"));

        assertThat(messages.conversations("ala"))
            .extracting(ConversationResponse::username)
            // bartek - najnowsza; celina - starsza; dawid - bez rozmowy, na koniec
            .containsExactly("bartek", "celina", "dawid");
    }

    @Test
    @DisplayName("kazda rozmowa niesie SWOJ licznik nieprzeczytanych")
    void conversationsCarryOwnUnreadCount() {
        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        celina.addFriend(ala);
        userRepository.save(celina);
        userRepository.save(ala);
        entityManager.flush();

        messages.send("bartek", "ala", text("raz"));
        messages.send("bartek", "ala", text("dwa"));
        messages.send("celina", "ala", text("jedna"));

        assertThat(messages.conversations("ala"))
            .extracting(ConversationResponse::username, ConversationResponse::unread)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("bartek", 2L),
                org.assertj.core.groups.Tuple.tuple("celina", 1L));
    }

    @Test
    @DisplayName("ostatnia wiadomosc w rozmowie to naprawde ta OSTATNIA")
    void lastMessageIsTheLatestOne() {
        messages.send("ala", "bartek", text("stara"));
        messages.send("bartek", "ala", text("najnowsza"));

        ConversationResponse rozmowa = messages.conversations("ala").get(0);

        assertThat(rozmowa.lastMessage().content()).isEqualTo("najnowsza");
        // Ostatnie slowo mial Bartek, wiec z punktu widzenia Ali to nie jej wiadomosc
        assertThat(rozmowa.lastMessage().mine()).isFalse();
    }

    /** Zerwanie znajomosci: rozmowa zostaje, ale nie da sie w niej pisac. */
    @Test
    @DisplayName("po zerwaniu znajomosci rozmowe mozna CZYTAC, ale nie mozna w niej PISAC")
    void endingFriendshipStopsWritingButNotReading() {
        messages.send("ala", "bartek", text("bylo milo"));

        ala.removeFriend(bartek);
        userRepository.save(ala);
        userRepository.save(bartek);
        entityManager.flush();

        // 1. Rozmowa zostaje na liscie - i jest oznaczona jako "juz nie znajomy"
        assertThat(messages.conversations("ala"))
            .extracting(ConversationResponse::username, ConversationResponse::friend)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("bartek", false));

        // 2. Wiadomosci nikt nie kasuje
        assertThat(messageRepository.count()).isEqualTo(1);

        // 3. Historie wolno przeczytac - obu stronom
        assertThat(messages.conversation("ala", "bartek", PageRequest.of(0, 20)))
            .hasSize(1);
        assertThat(messages.conversation("bartek", "ala", PageRequest.of(0, 20)))
            .hasSize(1);

        // 4. Ale napisac czegokolwiek juz nie
        assertThatThrownBy(() -> messages.send("ala", "bartek", text("jednak nie")))
            .isInstanceOf(OperationNotAllowedException.class);
        assertThatThrownBy(() -> messages.send("bartek", "ala", text("ja tez nie")))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("z OBCYM - bez wspolnej historii - nie da sie nawet otworzyc rozmowy")
    void strangerCannotEvenOpenTheConversation() {
        /*
         * Lagodniejszy warunek na czytanie dotyczy wylacznie osob, z ktorymi rozmowa faktycznie
         * sie odbyla.
         */
        assertThatThrownBy(() ->
            messages.conversation("ala", "obcy", PageRequest.of(0, 20)))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    /* ------------------------------------------------------------------ */
    /*  Usuwanie rozmowy u siebie                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Sedno tej funkcji: rozmowa nalezy do DWOJGA ludzi.
     *
     * <p>Skasowanie wiadomosci naprawde odbieraloby drugiej stronie jej
     * wlasna korespondencje - dlatego usuwamy tylko u tego, kto o to
     * poprosil.</p>
     */
    @Test
    @DisplayName("usuniecie rozmowy u siebie NIE rusza jej u drugiej strony")
    void deletingConversationLeavesTheOtherSideIntact() {
        messages.send("ala", "bartek", text("czesc"));
        messages.send("bartek", "ala", text("hej"));
        entityManager.flush();

        messages.deleteConversation("ala", "bartek");
        entityManager.flush();
        entityManager.clear();

        assertThat(messages.conversation("bartek", "ala", PageRequest.of(0, 20)).getContent())
            .describedAs("Bartek ma widziec obie wiadomosci")
            .hasSize(2);
    }

    /**
     * Znajomy zostaje na liscie, ale bez tresci rozmowy.
     *
     * <p>Lista rozmow pokazuje TEZ znajomych, z ktorymi nic sie jeszcze nie
     * napisalo - inaczej nie dalo by sie do nich odezwac. Po usunieciu
     * rozmowy wracamy dokladnie do tego stanu: osoba jest, historii nie ma.</p>
     */
    @Test
    @DisplayName("po usunieciu zostaje sam znajomy, bez tresci rozmowy")
    void deletedConversationLeavesOnlyTheFriendEntry() {
        messages.send("ala", "bartek", text("czesc"));
        entityManager.flush();

        messages.deleteConversation("ala", "bartek");
        entityManager.flush();
        entityManager.clear();

        assertThat(messages.conversations("ala"))
            .singleElement()
            .satisfies(rozmowa -> {
                assertThat(rozmowa.username()).isEqualTo("bartek");
                assertThat(rozmowa.lastMessage())
                    .describedAs("historia ma zniknac u Ali")
                    .isNull();
            });

        assertThat(messages.conversations("bartek"))
            .singleElement()
            .satisfies(rozmowa -> assertThat(rozmowa.lastMessage())
                .describedAs("u Bartka rozmowa zostaje")
                .isNotNull());
    }

    @Test
    @DisplayName("nieprzeczytane z usunietej rozmowy przestaja sie liczyc")
    void deletedConversationStopsCountingAsUnread() {
        messages.send("bartek", "ala", text("nieprzeczytane"));
        entityManager.flush();

        assertThat(messages.unreadCount("ala")).isEqualTo(1);

        messages.deleteConversation("ala", "bartek");
        entityManager.flush();
        entityManager.clear();

        assertThat(messages.unreadCount("ala")).isZero();
    }

    /**
     * Nowa wiadomosc po usunieciu zaczyna rozmowe od nowa.
     *
     * <p>Stara tresc nie wraca - usuniecie ma byc trwale u tego, kto go
     * dokonal.</p>
     */
    @Test
    @DisplayName("nowa wiadomosc po usunieciu otwiera rozmowe bez starej tresci")
    void newMessageAfterDeletionStartsAfresh() {
        messages.send("ala", "bartek", text("stara tresc"));
        entityManager.flush();
        messages.deleteConversation("ala", "bartek");
        entityManager.flush();

        messages.send("bartek", "ala", text("nowa tresc"));
        entityManager.flush();
        entityManager.clear();

        assertThat(messages.conversation("ala", "bartek", PageRequest.of(0, 20)).getContent())
            .singleElement()
            .satisfies(m -> assertThat(m.content()).isEqualTo("nowa tresc"));
    }

    /**
     * Wiersz znika z bazy dopiero, gdy ukryja go OBIE strony.
     *
     * <p>Wczesniej nie ma czego kasowac - ktos to jeszcze widzi.</p>
     */
    @Test
    @DisplayName("wiadomosc znika z bazy dopiero po usunieciu przez OBIE strony")
    void messageLeavesTheDatabaseOnlyWhenBothSidesDeleteIt() {
        messages.send("ala", "bartek", text("do skasowania"));
        entityManager.flush();

        messages.deleteConversation("ala", "bartek");
        entityManager.flush();
        assertThat(messageRepository.count())
            .describedAs("po jednej stronie wiersz ma zostac")
            .isEqualTo(1);

        messages.deleteConversation("bartek", "ala");
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.count()).isZero();
    }
}
