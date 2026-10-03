package com.musicclubapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.GifAttachment;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.music.MusicEmbed;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanPollRepository;
import com.musicclubapp.repository.ClanPollVoteRepository;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.CommentMentionRepository;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.ClanTrackRepository;
import com.musicclubapp.repository.ClanTrackVoteRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.PushSubscriptionRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserBlockRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Pobierz moje dane": archiwum ZIP z danymi konta w formacie do odczytu maszynowego (JSON) i
 * wlasnymi zdjeciami - prawo dostepu i przenoszenia danych (art. 15 i 20 RODO).
 *
 * <p>Zawiera to, co dotyczy tej osoby: konto i ustawienia, profil i ulubione, posty (takze klanu)
 * ze zdjeciami, reakcje, wiadomosci (wyslane i otrzymane - w rozmowie sa slowa obu stron),
 * znajomych, zaproszenia, blokady, zapisy na wydarzenia, powiadomienia, klan i wlasne wiadomosci
 * w nim, urzadzenia push (sam adres uslugi, bez kluczy) oraz zgloszenia. Nie zawiera: hasla ani
 * znacznikow bezpieczenstwa, adresow e-mail innych osob, nazwisk osob, ktore zglosily te osobe.</p>
 *
 * <p>Wymaga hasla (archiwum to komplet wrazliwych danych, wiec ktos z przejeta sesja nie moze go
 * ot tak pobrac) i moze byc pobierane najwyzej raz na minute.</p>
 */
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

    static final Duration ODSTEP = Duration.ofMinutes(1);

    /** Ile powiadomien najwyzej - dzwonek i tak tyle nie pokazuje. */
    private static final int MAX_POWIADOMIEN = 5000;

    /** Plik do dorzucenia do archiwum: nazwa w ZIP-ie i nazwa na dysku. */
    public record Plik(String wArchiwum, String naDysku) {
    }

    /** Wszystko, co trafi do archiwum - zebrane w transakcji, zapisywane do strumienia juz poza nia. */
    public record Eksport(String login, Map<String, Object> dane, List<Plik> pliki) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PostRepository posts;
    private final ReactionRepository reactions;
    private final MessageRepository messages;
    private final FriendRequestRepository friendRequests;
    private final UserBlockRepository blocks;
    private final EventParticipationRepository participations;
    private final FavoritePlaylistRepository playlists;
    private final NotificationRepository notifications;
    private final ClanMemberRepository clanMembers;
    private final ClanMessageRepository clanMessages;
    private final ClanInvitationRepository clanInvitations;
    private final ClanMessageReactionRepository clanReactions;
    private final ClanTrackRepository clanTracks;
    private final ClanTrackVoteRepository clanTrackVotes;
    private final ClanJoinRequestRepository clanRequests;
    private final ClanPollRepository clanPolls;
    private final ClanPollVoteRepository clanPollVotes;
    private final ClanMemberTitleRepository clanTitles;
    private final PushSubscriptionRepository pushSubscriptions;
    private final ReportRepository reports;
    private final CommentRepository comments;
    private final CommentMentionRepository commentMentions;
    private final FileStorageService fileStorage;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<Long, Instant> ostatnie = new ConcurrentHashMap<>();

    public DataExportService(UserRepository users, PasswordEncoder passwordEncoder, PostRepository posts,
                             ReactionRepository reactions, MessageRepository messages,
                             FriendRequestRepository friendRequests, UserBlockRepository blocks,
                             EventParticipationRepository participations, FavoritePlaylistRepository playlists,
                             NotificationRepository notifications, ClanMemberRepository clanMembers,
                             ClanMessageRepository clanMessages, ClanInvitationRepository clanInvitations,
                             ClanMessageReactionRepository clanReactions, ClanTrackRepository clanTracks,
                             ClanTrackVoteRepository clanTrackVotes, ClanJoinRequestRepository clanRequests,
                             ClanPollRepository clanPolls, ClanPollVoteRepository clanPollVotes,
                             ClanMemberTitleRepository clanTitles,
                             PushSubscriptionRepository pushSubscriptions, ReportRepository reports,
                             CommentRepository comments, CommentMentionRepository commentMentions,
                             FileStorageService fileStorage, ObjectMapper mapper, Clock clock) {
        this.comments = comments;
        this.commentMentions = commentMentions;
        this.clanReactions = clanReactions;
        this.clanTracks = clanTracks;
        this.clanTrackVotes = clanTrackVotes;
        this.clanRequests = clanRequests;
        this.clanPolls = clanPolls;
        this.clanPollVotes = clanPollVotes;
        this.clanTitles = clanTitles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.posts = posts;
        this.reactions = reactions;
        this.messages = messages;
        this.friendRequests = friendRequests;
        this.blocks = blocks;
        this.participations = participations;
        this.playlists = playlists;
        this.notifications = notifications;
        this.clanMembers = clanMembers;
        this.clanMessages = clanMessages;
        this.clanInvitations = clanInvitations;
        this.pushSubscriptions = pushSubscriptions;
        this.reports = reports;
        this.fileStorage = fileStorage;
        // Wlasna kopia: daty jako tekst ISO, a nie liczby - plik ma byc czytelny dla czlowieka
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
        this.clock = clock;
    }

    /** Sprawdza haslo i limit, zbiera dane. Wolane w transakcji tylko do odczytu. */
    @Transactional(readOnly = true)
    public Eksport przygotuj(String username, String haslo) {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        if (haslo == null || haslo.isBlank()) {
            throw InvalidCurrentPasswordException.missing();
        }
        if (!passwordEncoder.matches(haslo, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        pilnujOdstepu(user.getId());

        Long id = user.getId();
        List<Plik> pliki = new ArrayList<>();
        Map<String, Object> dane = new LinkedHashMap<>();
        dane.put("generatedAt", clock.instant().toString());
        dane.put("account", konto(user, pliki));
        dane.put("profile", profil(user));
        dane.put("posts", posty(id, pliki));
        dane.put("reactions", reakcje(id));
        dane.put("comments", komentarze(id));
        dane.put("messages", wiadomosci(id));
        dane.put("friends", user.getFriends().stream().map(User::getUsername).sorted().toList());
        dane.put("friendRequests", zaproszenia(id));
        dane.put("blocks", blocks.blockedBy(username).stream()
            .map(b -> mapa("username", b.getBlocked().getUsername(), "blockedAt", b.getCreatedAt())).toList());
        dane.put("events", zapisy(id));
        dane.put("notifications", powiadomienia(username));
        dane.put("clan", klan(id));
        dane.put("pushDevices", pushSubscriptions.findByUserIdOrderByCreatedAtAsc(id).stream()
            .map(s -> mapa("service", host(s.getEndpoint()), "language", s.getLang(), "addedAt", s.getCreatedAt())).toList());
        dane.put("reportsFiled", zgloszeniaZlozone(id));
        dane.put("reportsAboutYou", zgloszeniaODanejOsobie(id));
        return new Eksport(username, dane, pliki);
    }

    /** Zapisuje archiwum: JSON, plik "czytaj to" i zdjecia. Bez dostepu do bazy. */
    public void zapisz(Eksport eksport, OutputStream cel) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(cel, StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry("dane.json"));
        zip.write(mapper.writeValueAsBytes(eksport.dane()));
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("CZYTAJ-TO.txt"));
        zip.write(instrukcja(eksport.login()).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        Path katalog = fileStorage.getDirectory();
        for (Plik plik : eksport.pliki()) {
            // Nazwa pochodzi z bazy, ale i tak bierzemy sam plik, bez sciezki - jak przy kasowaniu
            Path zrodlo = katalog.resolve(Paths.get(plik.naDysku()).getFileName());
            if (!Files.isRegularFile(zrodlo)) {
                log.warn("Eksport danych: brak pliku {} na dysku - pomijam", plik.naDysku());
                continue;
            }
            zip.putNextEntry(new ZipEntry(plik.wArchiwum()));
            try (InputStream in = Files.newInputStream(zrodlo)) {
                in.transferTo(zip);
            }
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }

    /* ------------------------------------------------------------------ */

    private void pilnujOdstepu(Long userId) {
        Instant teraz = clock.instant();
        Instant poprzednie = ostatnie.get(userId);
        if (poprzednie != null && poprzednie.plus(ODSTEP).isAfter(teraz)) {
            throw new TooManyRequestsException(Duration.between(teraz, poprzednie.plus(ODSTEP)).toSeconds() + 1);
        }
        ostatnie.put(userId, teraz);
    }

    private Map<String, Object> konto(User u, List<Plik> pliki) {
        String awatar = null;
        if (u.getAvatarFileName() != null) {
            awatar = "zdjecia/awatar" + rozszerzenie(u.getAvatarFileName());
            pliki.add(new Plik(awatar, u.getAvatarFileName()));
        }
        return mapa(
            "username", u.getUsername(),
            "email", u.getEmail(),
            "emailVerifiedAt", u.getEmailVerifiedAt(),
            "pendingEmail", u.getPendingEmail(),
            "createdAt", u.getCreatedAt(),
            "lastSeenAt", u.getLastSeenAt(),
            "avatarFile", awatar,
            "termsVersionAccepted", u.getTermsVersion(),
            "termsAcceptedAt", u.getTermsAcceptedAt(),
            "eventsCountry", u.getEventsCountry() == null ? "PL" : u.getEventsCountry(),
            "eventReminders", u.isEventReminders(),
            "city", u.getCity(),
            "privacy", mapa(
                "profileVisibility", u.getProfileVisibility(),
                "friendRequestsFrom", u.getFriendRequestsFrom(),
                "clanInvitesFrom", u.getClanInvitesFrom(),
                "showOnline", u.isShowOnline(),
                "showInSuggestions", u.isShowInSuggestions(),
                "hideOnAttendeeLists", u.isHideOnAttendeeLists(),
                "showCity", u.isShowCity()),
            "postingBannedUntil", u.bannedUntil(BanKind.POSTING),
            "messagingBannedUntil", u.bannedUntil(BanKind.MESSAGING));
    }

    private Map<String, Object> profil(User u) {
        return mapa(
            "favoriteArtists", u.getFavoriteArtists().stream()
                .sorted(Comparator.comparing(Artist::getName, String.CASE_INSENSITIVE_ORDER))
                .map(a -> mapa("name", a.getName(), "genres", a.getGenres().stream().sorted().toList())).toList(),
            "favoriteTracks", u.getFavoriteTracks().stream()
                .map(t -> mapa("title", t.getTitle(), "artist", t.getArtistName())).toList(),
            "playlists", playlists.findByOwnerUsernameOrderByPositionAsc(u.getUsername()).stream()
                .map(p -> mapa("title", p.getTitle(), "provider", p.getProvider(), "id", p.getExternalId(),
                    "addedAt", p.getAddedAt())).toList());
    }

    private List<Object> posty(Long autorId, List<Plik> pliki) {
        List<Object> wynik = new ArrayList<>();
        List<Post> wszystkie = new ArrayList<>(posts.findByAuthorId(autorId));
        wszystkie.sort(Comparator.comparing(Post::getCreatedAt));
        for (Post p : wszystkie) {
            List<String> zdjecia = new ArrayList<>();
            int numer = 1;
            for (PostImage img : p.getImages()) {
                String nazwa = "zdjecia/post-" + p.getId() + "-" + numer++ + rozszerzenie(img.getFileName());
                pliki.add(new Plik(nazwa, img.getFileName()));
                zdjecia.add(nazwa);
            }
            wynik.add(mapa(
                "id", p.getId(),
                "createdAt", p.getCreatedAt(),
                "content", p.getContent(),
                "visibility", p.getClan() != null ? "CLAN" : p.getVisibility(),
                "clan", p.getClan() == null ? null : p.getClan().getName(),
                "event", p.getEvent() == null ? null : p.getEvent().getName(),
                "music", p.hasMusic() ? mapa("provider", p.getMusicProvider(), "kind", p.getMusicKind(),
                    "id", p.getMusicExternalId(), "title", p.getMusicTitle()) : null,
                "images", zdjecia));
        }
        return wynik;
    }

    /** Wlasne komentarze: pod ktorym postem, czy to odpowiedz, tresc i kogo oznaczono. */
    private List<Object> komentarze(Long userId) {
        return comments.ofUser(userId).stream()
            .map(c -> mapa("at", c.getCreatedAt(), "postId", c.getPost().getId(),
                "postAuthor", c.getPost().getAuthor().getUsername(),
                "replyToCommentId", c.getParent() == null ? null : c.getParent().getId(),
                "content", c.getContent(),
                "gif", gif(c.getGif()),
                "mentions", commentMentions.usernamesIn(c.getId())))
            .collect(java.util.stream.Collectors.toList());
    }

    /** GIF w eksporcie: adres i opis (plik jest u dostawcy, nie u nas). */
    private static Object gif(GifAttachment gif) {
        return gif == null ? null : mapa("url", gif.getUrl(), "title", gif.getTitle());
    }

    private List<Object> reakcje(Long userId) {
        return reactions.ofUser(userId).stream()
            .map(r -> mapa("type", r.getType(), "at", r.getCreatedAt(), "postId", r.getPost().getId(),
                "postAuthor", r.getPost().getAuthor().getUsername()))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<Object> wiadomosci(Long userId) {
        return messages.ofUser(userId).stream()
            .map(m -> mapa(
                "at", m.getCreatedAt(),
                "direction", m.getSender().getId().equals(userId) ? "sent" : "received",
                "from", m.getSender().getUsername(),
                "to", m.getRecipient().getUsername(),
                "content", m.getContent(),
                "gif", gif(m.getGif()),
                "music", m.getMusicExternalId() == null ? null : mapa("provider", m.getMusicProvider(),
                    "title", m.getMusicTitle()),
                "readAt", m.getReadAt()))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<Object> zaproszenia(Long userId) {
        return friendRequests.ofUser(userId).stream()
            .map(r -> mapa("from", r.getSender().getUsername(), "to", r.getRecipient().getUsername(),
                "at", r.getCreatedAt()))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<Object> zapisy(Long userId) {
        return participations.ofUser(userId).stream()
            .map(p -> mapa("event", p.getEvent().getName(), "date", p.getEvent().getStartDate(),
                "status", p.getStatus(), "hiddenOnList", p.isHidden(), "at", p.getCreatedAt()))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<Object> powiadomienia(String username) {
        return notifications.forUser(username, PageRequest.of(0, MAX_POWIADOMIEN)).getContent().stream()
            .map(n -> mapa("type", n.getType(), "at", n.getCreatedAt(),
                "from", n.getActor() == null ? null : n.getActor().getUsername(),
                "read", n.isRead()))
            .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> klan(Long userId) {
        ClanMember m = clanMembers.findByUserId(userId).orElse(null);
        List<Object> wiadomosci = clanMessages.writtenBy(userId).stream()
            .map(x -> mapa("clan", x.getClan().getName(), "at", x.getCreatedAt(), "content", x.getContent()))
            .collect(java.util.stream.Collectors.toList());
        return mapa(
            "membership", m == null ? null : mapa("clan", m.getClan().getName(), "tag", m.getClan().getTag(),
                "role", m.getRole(), "joinedAt", m.getJoinedAt(), "colorVote", m.getColorVote(),
                "chatNotificationsMuted", m.isChatMuted()),
            "pendingInvitations", clanInvitations.pendingFor(userId).stream()
                .map(i -> mapa("clan", i.getClan().getName(), "invitedBy", i.getInviter().getUsername(),
                    "at", i.getCreatedAt())).toList(),
            "messagesWritten", wiadomosci,
            "chatReactionsGiven", clanReactions.ofUser(userId).stream()
                .map(r -> mapa("clan", r.getMessage().getClan().getName(), "reaction", r.getType(),
                    "at", r.getCreatedAt())).toList(),
            "tracksProposed", clanTracks.ofUser(userId).stream()
                .map(t -> mapa("clan", t.getClan().getName(), "title", t.getMusicTitle(),
                    "link", MusicEmbed.canonicalUrl(t.getMusicProvider(), t.getMusicKind(), t.getMusicExternalId()),
                    "note", t.getNote(), "week", t.getWeekStart(), "at", t.getCreatedAt())).toList(),
            "trackVotes", clanTrackVotes.ofUser(userId).stream()
                .map(v -> mapa("clan", v.getTrack().getClan().getName(), "title", v.getTrack().getMusicTitle(),
                    "at", v.getCreatedAt())).toList(),
            "joinRequests", clanRequests.ofUser(userId).stream()
                .map(r -> mapa("clan", r.getClan().getName(), "message", r.getMessage(), "status", r.getStatus(),
                    "at", r.getCreatedAt())).toList(),
            "pollsCreated", clanPolls.writtenBy(userId).stream()
                .map(p -> mapa("clan", p.getClan().getName(), "question", p.getQuestion(),
                    "options", p.getOptions().stream().map(o -> o.getText()).toList(), "at", p.getCreatedAt()))
                .toList(),
            "pollVotes", clanPollVotes.ofUser(userId).stream()
                .map(v -> mapa("clan", v.getPoll().getClan().getName(), "question", v.getPoll().getQuestion(),
                    "answer", v.getOption().getText(), "at", v.getCreatedAt())).toList(),
            "titles", clanTitles.ofUser(userId).stream()
                .map(t -> mapa("clan", t.getTitle().getClan().getName(), "title", t.getTitle().getName(),
                    "selfClaimed", t.isSelfClaimed(), "at", t.getCreatedAt())).toList());
    }

    private List<Object> zgloszeniaZlozone(Long userId) {
        return reports.filedBy(userId).stream()
            .map(r -> mapa("at", r.getCreatedAt(), "about", r.getReported().getUsername(), "reason", r.getReason(),
                "context", r.getContext(), "clan", r.getClan() == null ? null : r.getClan().getName(),
                "description", r.getDescription(), "status", r.getStatus(),
                "resolvedAt", r.getResolvedAt(), "resolutionNote", r.getResolutionNote()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Bez nazwy osoby zglaszajacej - jej dane to jej sprawa. */
    private List<Object> zgloszeniaODanejOsobie(Long userId) {
        return reports.about(userId).stream()
            .map(r -> mapa("at", r.getCreatedAt(), "reason", r.getReason(), "context", r.getContext(),
                "status", r.getStatus(), "resolvedAt", r.getResolvedAt(), "resolutionNote", r.getResolutionNote()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** LinkedHashMap dopuszcza null (Map.of nie) - kolejnosc pol w pliku ma byc taka jak w kodzie. */
    private static Map<String, Object> mapa(Object... parami) {
        Map<String, Object> wynik = new LinkedHashMap<>();
        for (int i = 0; i < parami.length; i += 2) {
            wynik.put((String) parami[i], parami[i + 1]);
        }
        return wynik;
    }

    private static String rozszerzenie(String nazwaPliku) {
        int kropka = nazwaPliku.lastIndexOf('.');
        return kropka < 0 ? "" : nazwaPliku.substring(kropka);
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String instrukcja(String login) {
        return """
            MusicClub - Twoje dane / Your data
            ====================================

            PL
            Konto: %1$s
            Ten plik ZIP zawiera Twoje dane z serwisu MusicClub (art. 15 i 20 RODO):
              dane.json  - dane konta, profil, posty, reakcje, wiadomosci, znajomi, blokady,
                           zapisy na wydarzenia, powiadomienia, klan, urzadzenia powiadomien
                           i zgloszenia; w formacie JSON, do odczytu maszynowego,
              zdjecia/   - Twoje zdjecie profilowe i zdjecia z Twoich postow.
            Wiadomosci zawieraja slowa obu stron rozmowy. Nie ma tu hasla ani danych
            osobowych innych osob poza loginami, z ktorymi masz kontakt w serwisie.

            EN
            Account: %1$s
            This ZIP contains your data from MusicClub (GDPR Art. 15 and 20):
              dane.json  - account data, profile, posts, reactions, messages, friends, blocks,
                           event sign-ups, notifications, clan, notification devices and
                           reports; machine-readable JSON,
              zdjecia/   - your profile photo and the photos from your posts.
            Messages contain the words of both sides of a conversation. There is no password
            here, and no personal data of other people apart from the usernames you deal with.
            """.formatted(login);
    }
}
