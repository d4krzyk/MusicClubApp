package com.musicclubapp.service;

import com.musicclubapp.dto.ConversationResponse;
import com.musicclubapp.dto.ConversationSyncResponse;
import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.dto.SendMessageRequest;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.MessageMapper;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import com.musicclubapp.repository.ConversationRow;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.UnreadRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Czat: wysylanie, czytanie i lista rozmow.
 *
 * <p><b>Jedna regula rzadzi tu wszystkim: pisac mozna WYLACZNIE ze
 * znajomymi.</b> Sprawdzenie siedzi w {@link #requireFriend} i przechodzi
 * przez nie kazda operacja - wyslanie, odczyt historii, odpytywanie
 * o nowosci, oznaczanie przeczytanych, nawet sygnal "pisze". To nie jest
 * nadgorliwosc: gdyby ktorykolwiek z tych adresow pomijal sprawdzenie,
 * wystarczyloby wywolac wlasnie ten jeden, zeby czytac cudza korespondencje
 * albo zaczepiac obcych ludzi.</p>
 *
 * <p><b>Zerwanie znajomosci zamyka rozmowe</b>, ale jej nie kasuje.
 * Wiadomosci zostaja w bazie i wroca, gdy znajomosc zostanie odnowiona.
 * Kasowanie cudzych wypowiedzi przy klknieciu "usun ze znajomych" byloby
 * decyzja za obie strony naraz - a wiadomosc nalezy tez do tego, kto ja
 * dostal.</p>
 */
@Service
public class MessageService {

    /**
     * Ile najwyzej nowych wiadomosci oddajemy w jednym odpytaniu.
     *
     * <p>Okno czatu potrafi stac otwarte na karcie, do ktorej nikt nie wraca
     * godzinami. Bez limitu pierwsze odpytanie po powrocie sciagneloby
     * wszystko, co przez ten czas przyszlo, jednym kawalkiem.</p>
     */
    private static final int MAX_SYNC_BATCH = 50;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final MusicMetadataService musicMetadata;
    private final PresenceService presence;
    private final TypingRegistry typing;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          MessageMapper messageMapper,
                          MusicMetadataService musicMetadata,
                          PresenceService presence,
                          TypingRegistry typing) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
        this.musicMetadata = musicMetadata;
        this.presence = presence;
        this.typing = typing;
    }

    /* ------------------------------------------------------------------ */
    /*  Wysylanie                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Wysyla wiadomosc do znajomego.
     *
     * <p><b>Czat blokuje osobny zakaz - {@code messagingBannedUntil}, a nie
     * zakaz publikowania.</b> W pierwszej wersji bylo odwrotnie: zakaz
     * publikowania wylaczal takze czat, bo kara zostawiajaca otwarta droge
     * do pisania prywatnie nie jest kara. Odkad administrator ma DWA osobne
     * przelaczniki, ten argument sie odwraca - przy dawnym zachowaniu nie
     * dalo by sie w ogole ustawic "nie wolno pisac postow, ale wolno rozmawiac
     * ze znajomymi", czyli najczestszego przypadku przy kims, kto zasmieca
     * tablice, a nikomu nie dokucza.</p>
     *
     * <p>Kto ma dostac obie kary, dostaje obie - administrator wlacza dwa
     * przelaczniki zamiast jednego. Kazda wygasa sama.</p>
     */
    @Transactional
    public MessageResponse send(String senderUsername, String recipientUsername,
                                SendMessageRequest request) {

        User sender = requireUser(senderUsername);
        User recipient = requireFriend(sender, recipientUsername);

        if (sender.isMessagingBanned()) {
            throw OperationNotAllowedException.messagingBanned(
                sender.getMessagingBannedUntil());
        }

        /*
         * Tresc przycinamy z bialych znakow, a pusta zamieniamy na null.
         * Bez tego wiadomosc "sam utwor" mialaby w bazie raz null, raz pusty
         * napis, raz spacje - zaleznie od tego, co akurat zostalo w polu.
         * Frontend musialby wtedy sprawdzac wszystkie trzy przypadki.
         */
        String content = request.content() == null || request.content().isBlank()
            ? null
            : request.content().trim();

        Message message = new Message(sender, recipient, content);
        applyMusic(message, request.musicUrl(), request.musicStartSeconds());

        Message saved = messageRepository.save(message);

        /*
         * Sygnal "pisze" gasimy od razu. Bez tego dymek z kropkami wisialby
         * jeszcze kilka sekund POD wlasnie dostarczona wiadomoscia.
         */
        typing.stoppedTyping(sender.getId(), recipient.getId());

        return messageMapper.toResponse(saved, sender);
    }

    /**
     * Podpina nagranie - razem z tytulem i miniaturka.
     *
     * <p>Dokladnie ta sama sciezka co przy postach ({@code PostService}):
     * poprawnosc adresu sprawdzil juz walidator {@code ValidMusicLink},
     * wiec nierozpoznany adres moze tu znaczyc juz tylko "pole jest puste".</p>
     */
    private void applyMusic(Message message, String url, Integer startSeconds) {
        ParsedMusicLink link = MusicLinkParser.parse(url).orElse(null);

        if (link == null) {
            message.applyMusic(null, null, null, null);
            return;
        }

        // Awaria serwisu nie moze zablokowac wyslania - odtwarzacz i tak
        // laduje sie w przegladarce niezaleznie od tytulu i miniaturki
        MusicMetadataService.Metadata metadata = musicMetadata.fetch(link);
        message.applyMusic(link, startSeconds, metadata.title(), metadata.thumbnailUrl());
    }

    /* ------------------------------------------------------------------ */
    /*  Czytanie                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Historia rozmowy, <b>od najnowszej</b>.
     *
     * <p>Odwrocenie tej kolejnosci przed narysowaniem to jedna linijka
     * w przegladarce - patrz komentarz przy {@code MessageRepository.conversation}.</p>
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> conversation(String me, String partnerUsername,
                                              Pageable pageable) {
        User viewer = requireUser(me);
        User partner = requireFriend(viewer, partnerUsername);

        return messageRepository
            .conversation(viewer.getId(), partner.getId(), pageable)
            .map(message -> messageMapper.toResponse(message, viewer));
    }

    /**
     * Co nowego w otwartej rozmowie - <b>jednym zapytaniem</b>.
     *
     * <p>Nowe wiadomosci, dymek "pisze", obecnosc rozmowcy i licznik przy
     * ikonie czatu naraz. Po co razem - patrz {@link ConversationSyncResponse}.</p>
     *
     * <p><b>Oznaczanie przeczytanych dzieje sie TUTAJ</b>, a nie osobnym
     * zapytaniem z przegladarki. Skoro okno rozmowy jest otwarte i wlasnie
     * odebralo nowa wiadomosc, to znaczy, ze zostala pokazana - a wiec
     * przeczytana. Osobne zapytanie robiloby to samo pol sekundy pozniej,
     * kosztem jeszcze jednego objazdu do serwera.</p>
     *
     * @param afterId identyfikator ostatniej wiadomosci, ktora przegladarka
     *                juz ma; {@code null} przy pierwszym odpytaniu
     */
    @Transactional
    public ConversationSyncResponse sync(String me, String partnerUsername, Long afterId) {
        User viewer = requireUser(me);
        User partner = requireFriend(viewer, partnerUsername);

        List<Message> fresh = messageRepository.newerThan(
            viewer.getId(), partner.getId(),
            afterId == null ? 0L : afterId,
            PageRequest.of(0, MAX_SYNC_BATCH));

        List<MessageResponse> messages = fresh.stream()
            .map(message -> messageMapper.toResponse(message, viewer))
            .toList();

        /*
         * Zamapowac MUSIMY przed oznaczeniem przeczytanych: masowy UPDATE
         * czysci pamiec podreczna Hibernate'a (patrz komentarz przy
         * markConversationRead), wiec encje odczytane wczesniej staja sie
         * odczepione. Kolejnosc tych dwoch linijek nie jest przypadkowa.
         */
        boolean somethingToRead = fresh.stream()
            .anyMatch(message -> message.getRecipient().getId().equals(viewer.getId()));

        if (somethingToRead) {
            messageRepository.markConversationRead(
                viewer.getId(), partner.getId(), LocalDateTime.now());
        }

        return new ConversationSyncResponse(
            messages,
            typing.isTyping(partner.getId(), viewer.getId()),
            presence.of(partner),
            messageRepository.countUnread(viewer.getId()),
            messageRepository.lastReadOutgoingId(viewer.getId(), partner.getId()));
    }

    /**
     * Oznacza cala rozmowe jako przeczytana.
     *
     * <p>Wolane przy otwarciu watku - {@link #sync} robi to samo dla wiadomosci,
     * ktore doszly juz przy otwartym oknie.</p>
     *
     * @return ile wpisow zmienilo stan
     */
    @Transactional
    public int markRead(String me, String partnerUsername) {
        User viewer = requireUser(me);
        User partner = requireFriend(viewer, partnerUsername);

        return messageRepository.markConversationRead(
            viewer.getId(), partner.getId(), LocalDateTime.now());
    }

    /** Laczna liczba nieprzeczytanych - liczba przy ikonie czatu. */
    @Transactional(readOnly = true)
    public long unreadCount(String me) {
        return messageRepository.countUnread(requireUser(me).getId());
    }

    /** Odnotowuje, ze ktos wlasnie pisze do znajomego. */
    @Transactional(readOnly = true)
    public void typing(String me, String partnerUsername) {
        User viewer = requireUser(me);
        User partner = requireFriend(viewer, partnerUsername);

        typing.startedTyping(viewer.getId(), partner.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Lista rozmow                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Wszyscy znajomi z ostatnia wiadomoscia, licznikiem nieprzeczytanych
     * i kropka obecnosci.
     *
     * <p><b>Trzy zapytania na cala liste, niezaleznie od liczby znajomych.</b>
     * Naiwna wersja - dla kazdego znajomego pobierz ostatnia wiadomosc
     * i policz nieprzeczytane - to dwa zapytania NA OSOBE, czyli przy
     * trzydziestu znajomych szescdziesiat zapytan na jedno otwarcie czatu
     * (klasyczny problem N+1). Tutaj: jedno o identyfikatory ostatnich
     * wiadomosci, jedno o same wiadomosci, jedno o liczniki.</p>
     *
     * <p><b>Kolejnosc.</b> Najpierw rozmowy zywe - od najnowszej wiadomosci -
     * a pod nimi reszta znajomych alfabetycznie. Tak samo jak w tablicy:
     * to, co swieze, ma byc na gorze bez przewijania.</p>
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> conversations(String me) {
        User viewer = requireUser(me);

        /*
         * Ostatnie wiadomosci. Identyfikatory i tresc pobieramy osobno,
         * bo zapytanie grupujace umie oddac tylko wyliczone wartosci -
         * nie cale encje.
         */
        Map<Long, Long> lastIdByPartner = new HashMap<>();
        for (ConversationRow row : messageRepository.lastMessagePerConversation(viewer.getId())) {
            lastIdByPartner.put(row.partnerId(), row.lastMessageId());
        }

        Map<Long, Message> byId = new HashMap<>();
        for (Message message : messageRepository.findAllById(lastIdByPartner.values())) {
            byId.put(message.getId(), message);
        }

        Map<Long, Long> unreadBySender = new HashMap<>();
        for (UnreadRow row : messageRepository.unreadBySender(viewer.getId())) {
            unreadBySender.put(row.senderId(), row.count());
        }

        List<ConversationResponse> conversations = new ArrayList<>();
        for (User friend : viewer.getFriends()) {
            // get(null) na mapie zwraca null - znajomy bez rozmowy przechodzi tedy bez warunku
            Message last = byId.get(lastIdByPartner.get(friend.getId()));

            conversations.add(new ConversationResponse(
                friend.getUsername(),
                friend.getAvatarFileName() == null
                    ? null
                    : PostMapper.UPLOADS_PATH + friend.getAvatarFileName(),
                presence.of(friend),
                last == null ? null : messageMapper.toResponse(last, viewer),
                unreadBySender.getOrDefault(friend.getId(), 0L)));
        }

        /*
         * Sortujemy w Javie, a nie w bazie. Zapytanie musialoby polaczyc
         * znajomych z wyliczona lista rozmow i posortowac po kolumnie, ktorej
         * przy wiekszosci wierszy nie ma - a lista ma tyle pozycji, ilu
         * czlowiek ma znajomych, wiec kosztu tu nie ma zadnego.
         */
        conversations.sort(
            Comparator.comparing(
                    (ConversationResponse c) -> Optional.ofNullable(c.lastMessage())
                        .map(MessageResponse::createdAt)
                        .orElse(null),
                    // Rozmowy bez ani jednej wiadomosci ida na koniec
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ConversationResponse::username));

        return conversations;
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Kasuje wszystkie wiadomosci konta - uzywane przy usuwaniu uzytkownika.
     *
     * <p>Musi pojsc PRZED skasowaniem samego konta: wiadomosci wskazuja na nie
     * dwoma kluczami obcymi i baza nie pozwoli usunac wiersza, do ktorego cos
     * jeszcze prowadzi.</p>
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        messageRepository.deleteAllOfUser(userId);
    }

    /* ------------------------------------------------------------------ */
    /*  Wspolne sprawdzenia                                                */
    /* ------------------------------------------------------------------ */

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    /**
     * Zwraca druga strone rozmowy albo <b>przerywa</b>, gdy nie wolno z nia pisac.
     *
     * <p>Trzy przypadki, kazdy z innym komunikatem, bo kazdy znaczy co innego
     * dla tego, kto go zobaczy:</p>
     * <ul>
     *   <li><b>nie ma takiego konta</b> - 404, zwykla literowka albo konto
     *       usuniete w miedzyczasie,</li>
     *   <li><b>rozmowa z samym soba</b> - to nie jest luka bezpieczenstwa,
     *       tylko pomylka; osobny komunikat oszczedza domyslania sie,</li>
     *   <li><b>to nie jest znajomy</b> - i to jest ta wlasciwa blokada.</li>
     * </ul>
     */
    private User requireFriend(User viewer, String partnerUsername) {
        User partner = requireUser(partnerUsername);

        if (partner.getId().equals(viewer.getId())) {
            throw OperationNotAllowedException.messageToSelf();
        }

        /*
         * Pytamy bazy, a nie kolekcji viewer.getFriends(). Przy koncie
         * z setka znajomych sprawdzenie w Javie oznacza wczytanie ich
         * wszystkich, zeby odpowiedziec na pytanie o jedna osobe.
         */
        if (!userRepository.areFriends(viewer.getUsername(), partner.getUsername())) {
            throw OperationNotAllowedException.messageToStranger();
        }

        return partner;
    }
}
