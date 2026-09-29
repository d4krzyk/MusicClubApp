package com.musicclubapp.service;

import com.musicclubapp.dto.ConversationResponse;
import com.musicclubapp.dto.ConversationSyncResponse;
import com.musicclubapp.dto.MessageResponse;
import com.musicclubapp.dto.SendMessageRequest;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.BanKind;
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
import java.util.Set;
import java.util.stream.Collectors;

/** Czat: wysylanie, czytanie i lista rozmow. */
@Service
public class MessageService {

    /** Ile najwyzej nowych wiadomosci oddajemy w jednym odpytaniu. */
    private static final int MAX_SYNC_BATCH = 50;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final MusicMetadataService musicMetadata;
    private final PresenceService presence;
    private final TypingRegistry typing;
    private final BlockService blocks;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          MessageMapper messageMapper,
                          MusicMetadataService musicMetadata,
                          PresenceService presence,
                          TypingRegistry typing,
                          BlockService blocks) {
        this.blocks = blocks;
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

    /** Wysyla wiadomosc do znajomego. */
    @Transactional
    public MessageResponse send(String senderUsername, String recipientUsername,
                                SendMessageRequest request) {

        User sender = requireUser(senderUsername);
        User recipient = requireFriend(sender, recipientUsername);

        if (sender.isBanned(BanKind.MESSAGING)) {
            throw OperationNotAllowedException.banned(BanKind.MESSAGING, 
                sender.bannedUntil(BanKind.MESSAGING));
        }

        /* Tresc przycinamy z bialych znakow, a pusta zamieniamy na null. */
        String content = request.content() == null || request.content().isBlank()
            ? null
            : request.content().trim();

        Message message = new Message(sender, recipient, content);
        applyMusic(message, request.musicUrl(), request.musicStartSeconds());

        Message saved = messageRepository.save(message);

        /* Sygnal "pisze" gasimy od razu. */
        typing.stoppedTyping(sender.getId(), recipient.getId());

        return messageMapper.toResponse(saved, sender);
    }

    /** Podpina nagranie - razem z tytulem i miniaturka. */
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

    /** Historia rozmowy, od najnowszej. */
    @Transactional(readOnly = true)
    public Page<MessageResponse> conversation(String me, String partnerUsername,
                                              Pageable pageable) {
        User viewer = requireUser(me);
        User partner = requirePartner(viewer, partnerUsername);

        return messageRepository
            .conversation(viewer.getId(), partner.getId(), pageable)
            .map(message -> messageMapper.toResponse(message, viewer));
    }

    /** Co nowego w otwartej rozmowie - jednym zapytaniem. */
    @Transactional
    public ConversationSyncResponse sync(String me, String partnerUsername, Long afterId) {
        User viewer = requireUser(me);
        User partner = requirePartner(viewer, partnerUsername);

        List<Message> fresh = messageRepository.newerThan(
            viewer.getId(), partner.getId(),
            afterId == null ? 0L : afterId,
            PageRequest.of(0, MAX_SYNC_BATCH));

        List<MessageResponse> messages = fresh.stream()
            .map(message -> messageMapper.toResponse(message, viewer))
            .toList();

        boolean somethingToRead = fresh.stream()
            .anyMatch(message -> message.getRecipient().getId().equals(viewer.getId()));

        if (somethingToRead) {
            messageRepository.markConversationRead(
                viewer.getId(), partner.getId(), LocalDateTime.now());
        }

        return new ConversationSyncResponse(
            messages,
            typing.isTyping(partner.getId(), viewer.getId()),
            blocks.eitherWay(viewer.getId(), partner.getId()) ? presence.hidden() : presence.of(partner),
            messageRepository.countUnread(viewer.getId()),
            messageRepository.lastReadOutgoingId(viewer.getId(), partner.getId()),
            canWriteTo(viewer, partner));
    }

    /** Oznacza cala rozmowe jako przeczytana. */
    @Transactional
    public int markRead(String me, String partnerUsername) {
        User viewer = requireUser(me);
        User partner = requirePartner(viewer, partnerUsername);

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

    /** Wszyscy znajomi z ostatnia wiadomoscia, licznikiem nieprzeczytanych i kropka obecnosci. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> conversations(String me) {
        User viewer = requireUser(me);

        /* Ostatnie wiadomosci. */
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

        /*
         * Lista sklada sie z DWOCH grup, a nie z jednej. 1. wszyscy znajomi - takze ci, z ktorymi
         * nikt jeszcze nie zamienil slowa, bo lista sluzy tez do ZACZYNANIA rozmow; 2. byli
         * znajomi, z ktorymi rozmowa juz sie odbyla.
         */
        Set<Long> friendIds = viewer.getFriends().stream()
            .map(User::getId)
            .collect(Collectors.toSet());

        List<User> partners = new ArrayList<>(viewer.getFriends());

        List<Long> formerIds = lastIdByPartner.keySet().stream()
            .filter(id -> !friendIds.contains(id))
            .toList();
        if (!formerIds.isEmpty()) {
            userRepository.findAllById(formerIds).forEach(partners::add);
        }

        // Przy blokadzie historia zostaje, ale obecnosci drugiej strony juz nie widac
        Set<Long> ukryci = blocks.hiddenFor(viewer.getId());

        List<ConversationResponse> conversations = new ArrayList<>();
        for (User partner : partners) {
            // get(null) na mapie zwraca null - znajomy bez rozmowy przechodzi tedy bez warunku
            Message last = byId.get(lastIdByPartner.get(partner.getId()));

            conversations.add(new ConversationResponse(
                partner.getUsername(),
                partner.getAvatarFileName() == null
                    ? null
                    : PostMapper.UPLOADS_PATH + partner.getAvatarFileName(),
                ukryci.contains(partner.getId()) ? presence.hidden() : presence.of(partner),
                last == null ? null : messageMapper.toResponse(last, viewer),
                unreadBySender.getOrDefault(partner.getId(), 0L),
                friendIds.contains(partner.getId())));
        }

        /* Sortujemy w Javie, a nie w bazie. */
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

    /**
     * Usuwa rozmowe TYLKO u osoby, ktora o to poprosila.
     *
     * <p>Druga strona zachowuje swoja kopie - i to jest sedno tej funkcji.
     * Skasowanie wiadomosci naprawde odbieraloby komus jego wlasna
     * korespondencje, a rozmowa nalezy do dwojga ludzi, nie do jednego.</p>
     *
     * <p>Wiersze znikaja z bazy dopiero wtedy, gdy ukryja je OBIE strony -
     * wczesniej nie ma czego kasowac, bo ktos to jeszcze widzi.</p>
     */
    @Transactional
    public void deleteConversation(String me, String partnerUsername) {
        User viewer = requireUser(me);
        User partner = requirePartner(viewer, partnerUsername);

        messageRepository.hideSentTo(viewer.getId(), partner.getId());
        messageRepository.hideReceivedFrom(viewer.getId(), partner.getId());
        messageRepository.deleteHiddenByBothSides();
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Kasuje wszystkie wiadomosci konta - uzywane przy usuwaniu uzytkownika. */
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

    /** Zwraca druga strone rozmowy albo przerywa, gdy nie wolno z nia pisac. */
    private User requireFriend(User viewer, String partnerUsername) {
        User partner = requireUser(partnerUsername);

        if (partner.getId().equals(viewer.getId())) {
            throw OperationNotAllowedException.messageToSelf();
        }

        /* Pytamy bazy, a nie kolekcji viewer.getFriends(). */
        if (!userRepository.areFriends(viewer.getUsername(), partner.getUsername())) {
            throw OperationNotAllowedException.messageToStranger();
        }

        return partner;
    }

    /** Jak #requireFriend, ale do CZYTANIA - i wystarcza mu sama historia rozmowy. */
    private User requirePartner(User viewer, String partnerUsername) {
        User partner = requireUser(partnerUsername);

        if (partner.getId().equals(viewer.getId())) {
            throw OperationNotAllowedException.messageToSelf();
        }

        boolean friends = userRepository.areFriends(
            viewer.getUsername(), partner.getUsername());

        if (!friends && !messageRepository.anyMessageBetween(viewer.getId(), partner.getId())) {
            throw OperationNotAllowedException.messageToStranger();
        }

        return partner;
    }

    /** Czy z ta osoba wolno teraz PISAC (a nie tylko czytac). */
    private boolean canWriteTo(User viewer, User partner) {
        return userRepository.areFriends(viewer.getUsername(), partner.getUsername());
    }
}
