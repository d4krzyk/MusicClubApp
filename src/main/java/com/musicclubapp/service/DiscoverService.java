package com.musicclubapp.service;

import com.musicclubapp.dto.ClanBadge;
import com.musicclubapp.dto.DiscoverArtist;
import com.musicclubapp.dto.DiscoverCard;
import com.musicclubapp.dto.DiscoverDeckResponse;
import com.musicclubapp.dto.DiscoverSettingsRequest;
import com.musicclubapp.dto.DiscoverStatusResponse;
import com.musicclubapp.dto.SwipeRequest;
import com.musicclubapp.dto.SwipeResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.DiscoverSwipe;
import com.musicclubapp.entity.LookingForConverter;
import com.musicclubapp.entity.SwipeDecision;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.DiscoverCandidateRow;
import com.musicclubapp.repository.DiscoverSwipeRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.UserArtistRow;
import com.musicclubapp.repository.UserGenreRow;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tryb Poznawaj - poznawanie ludzi "w stylu Tindera": karty jedna po drugiej, w prawo = chce poznac, w lewo =
 * nie teraz. Znajomosc powstaje dopiero wtedy, gdy obie osoby powiedza sobie "tak"; do tego czasu "tak" jest
 * tajne (druga osoba sie o nim nie dowiaduje).
 *
 * <p>Zasady:</p>
 * <ul>
 *   <li>Tryb jest dobrowolny i wzajemny: karty ogladaja tylko osoby, ktore same wlaczyly tryb, i tylko ich karty
 *       sa w talii. Na karcie jest to, co osoba sama na niej wystawila, i to, co laczy ja z ogladajacym.</li>
 *   <li>Talia jest od najlepiej do najslabiej dopasowanych gustem ({@link DiscoverMatch}), w zasiegu od mojego
 *       miasta ustawionym na koncie (0 = caly kraj). Bez miasta w profilu zasieg nic nie robi.</li>
 *   <li>Nie ma w niej znajomych, osob z blokad (w obie strony), osob z oczekujacym zaproszeniem (to sprawa listy
 *       zaproszen) ani osob z zakazem publikowania - karta to tresc publikowana.</li>
 *   <li>"Nie" wygasa po {@link #PASS_WAZNE} i osoba moze wrocic do talii; "tak" bez odpowiedzi wygasa po
 *       {@link #LIKE_WAZNE}. Ostatnia decyzje mozna cofnac przez {@link #COFNIJ}.</li>
 *   <li>Wzajemne "tak" = znajomosc od razu (bez zaproszenia - obie osoby juz sie zgodzily), powiadomienie
 *       i push dla obu; ustawienie "kto moze mnie zaprosic" tu nie dziala, bo nikt nikogo nie zaprasza.</li>
 * </ul>
 */
@Service
public class DiscoverService {

    private static final Logger log = LoggerFactory.getLogger(DiscoverService.class);

    /** Zasiegi do wyboru (km), te same co przy wydarzeniach i klanach; 0 = caly kraj. */
    public static final Set<Integer> ZASIEGI = Set.of(0, 30, 50, 100, 200);

    public static final int TALIA_DOMYSLNIE = 10;
    public static final int TALIA_MAKS = 20;
    /** Ile kart, ktore przegladarka juz ma, mozna pominac w kolejnym zapytaniu. */
    static final int POMIN_MAKS = 60;

    public static final Duration PASS_WAZNE = Duration.ofDays(30);
    public static final Duration LIKE_WAZNE = Duration.ofDays(180);
    public static final Duration COFNIJ = Duration.ofMinutes(10);
    /** Konto mlodsze niz tyle to "nowa osoba" na karcie. */
    static final Duration NOWA_OSOBA = Duration.ofDays(14);

    static final int WYKONAWCOW_NA_KARCIE = 6;
    static final int GATUNKOW_NA_KARCIE = 6;

    private static final ZoneId POLSKA = ZoneId.of("Europe/Warsaw");

    private final UserRepository users;
    private final DiscoverSwipeRepository swipes;
    private final FriendRequestRepository requests;
    private final ProfileCardService cards;
    private final ClanService clans;
    private final BlockService blocks;
    private final NotificationService notifications;
    private final Clock clock;
    private final int dziennie;

    public DiscoverService(UserRepository users, DiscoverSwipeRepository swipes, FriendRequestRepository requests,
                           ProfileCardService cards, ClanService clans, BlockService blocks,
                           NotificationService notifications, Clock clock,
                           @Value("${app.discover.swipes-per-day:300}") int dziennie) {
        this.users = users;
        this.swipes = swipes;
        this.requests = requests;
        this.cards = cards;
        this.clans = clans;
        this.blocks = blocks;
        this.notifications = notifications;
        this.clock = clock;
        this.dziennie = dziennie;
    }

    /* ------------------------------------------------------------------ */
    /*  Ustawienia                                                         */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public DiscoverStatusResponse status(String username) {
        User ja = user(username);
        var karta = cards.of(ja);
        return new DiscoverStatusResponse(
            ja.isDiscoverEnabled(),
            ja.getDiscoverRadiusKm(),
            ja.getCity(),
            karta.photos().size(),
            ja.getBio() != null && !ja.getBio().isBlank(),
            karta.prompts().size(),
            ja.getLookingFor().size(),
            ja.getFavoriteArtists().size(),
            swipesLeft(ja),
            preview(ja, karta));
    }

    @Transactional
    public DiscoverStatusResponse settings(String username, DiscoverSettingsRequest request) {
        if (!ZASIEGI.contains(request.radiusKm())) {
            throw OperationNotAllowedException.discoverRadius();
        }
        User ja = user(username);
        if (ja.isDiscoverEnabled() != request.enabled()) {
            log.info("{} {} tryb Poznawaj", username, request.enabled() ? "wlacza" : "wylacza");
        }
        ja.setDiscover(request.enabled(), request.radiusKm());
        return status(username);
    }

    /* ------------------------------------------------------------------ */
    /*  Talia                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Kolejne karty. {@code skip} - loginy kart, ktore przegladarka juz ma (jeszcze bez decyzji), zeby nie
     * przyszly drugi raz.
     */
    @Transactional(readOnly = true)
    public DiscoverDeckResponse deck(String username, int limit, Collection<String> skip) {
        User ja = user(username);
        requireOn(ja);
        boolean zasiegDziala = ja.getCityKey() != null && ja.getDiscoverRadiusKm() > 0;
        List<DiscoverCandidateRow> wiersze = swipes.deck(ja.getId(),
            zasiegDziala ? ja.getDiscoverRadiusKm() : 0,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock).minus(PASS_WAZNE),
            pomin(skip),
            0L,
            PageRequest.of(0, Math.max(1, Math.min(limit, TALIA_MAKS))));
        return new DiscoverDeckResponse(toCards(ja, wiersze), ja.getDiscoverRadiusKm(), zasiegDziala, swipesLeft(ja));
    }

    /* ------------------------------------------------------------------ */
    /*  Decyzje                                                            */
    /* ------------------------------------------------------------------ */

    @Transactional
    public SwipeResponse swipe(String username, SwipeRequest request) {
        User ja = user(username);
        requireOn(ja);
        User on = users.findByUsername(request.username())
            .filter(u -> !u.getId().equals(ja.getId()))
            .orElseThrow(OperationNotAllowedException::discoverGone);

        // Dwie osoby mowiace sobie "tak" naraz ustawiaja sie w kolejce na koncie o mniejszym numerze
        users.lockById(Math.min(ja.getId(), on.getId()));

        requireInDeck(ja, on);
        if (swipesLeft(ja) <= 0) {
            throw new TooManyRequestsException(secondsToMidnight());
        }

        LocalDateTime teraz = LocalDateTime.now(clock);
        swipes.findBySwiperIdAndTargetId(ja.getId(), on.getId()).ifPresentOrElse(
            s -> s.decide(request.decision(), teraz),
            () -> swipes.save(new DiscoverSwipe(ja, on, request.decision(), teraz)));
        swipes.flush();

        if (request.decision() == SwipeDecision.LIKE
            && swipes.existsBySwiperIdAndTargetIdAndDecision(on.getId(), ja.getId(), SwipeDecision.LIKE)) {
            match(ja, on);
            return new SwipeResponse(true, on.getUsername(), avatarUrl(on.getAvatarFileName()), swipesLeft(ja));
        }
        return new SwipeResponse(false, on.getUsername(), null, swipesLeft(ja));
    }

    /** Wzajemne "tak": znajomosc bez zaproszenia, obie decyzje znikaja, powiadomienie dla obu. */
    private void match(User ja, User on) {
        ja.addFriend(on);
        users.save(ja);
        users.save(on);
        swipes.deleteBetween(ja.getId(), on.getId());
        requests.find(ja.getUsername(), on.getUsername()).ifPresent(requests::delete);
        requests.find(on.getUsername(), ja.getUsername()).ifPresent(requests::delete);
        notifications.discoverMatch(ja, on);
        log.info("Poznawaj: wzajemne tak - {} i {} sa znajomymi", ja.getUsername(), on.getUsername());
    }

    /**
     * Cofa moja ostatnia decyzje (z ostatnich {@link #COFNIJ}) i oddaje karte tej osoby, zeby wrocila na
     * wierzch talii. Decyzji, ktora dala znajomosc, nie ma juz w bazie - tej cofnac sie nie da.
     */
    @Transactional
    public DiscoverCard undo(String username) {
        User ja = user(username);
        requireOn(ja);
        DiscoverSwipe ostatnia = swipes.findFirstBySwiperIdOrderByCreatedAtDescIdDesc(ja.getId())
            .filter(s -> !s.getCreatedAt().isBefore(LocalDateTime.now(clock).minus(COFNIJ)))
            .orElseThrow(OperationNotAllowedException::discoverNothingToUndo);
        Long kto = ostatnia.getTarget().getId();
        swipes.delete(ostatnia);
        swipes.flush();
        List<DiscoverCandidateRow> wiersz = swipes.deck(ja.getId(), 0, LocalDateTime.now(clock),
            LocalDateTime.now(clock).minus(PASS_WAZNE), pomin(List.of()), kto, PageRequest.of(0, 1));
        if (wiersz.isEmpty()) {
            // Ta osoba tymczasem wypadla z talii (wylaczyla tryb, blokada) - decyzja i tak jest cofnieta
            throw OperationNotAllowedException.discoverGone();
        }
        return toCards(ja, wiersz).get(0);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Raz na dobe: wygasle decyzje znikaja - "nie" po 30 dniach, "tak" bez odpowiedzi po 180. */
    @Scheduled(cron = "${app.discover.cleanup-cron:0 40 4 * * *}", zone = "Europe/Warsaw")
    @Transactional
    public void forgetExpired() {
        LocalDateTime teraz = LocalDateTime.now(clock);
        int ile = swipes.deleteExpired(teraz.minus(PASS_WAZNE), teraz.minus(LIKE_WAZNE));
        if (ile > 0) {
            log.info("Poznawaj: usunieto {} wygaslych decyzji", ile);
        }
    }

    /** Usuniecie konta: decyzje tej osoby i o tej osobie. */
    @Transactional
    public void deleteAllOf(User user) {
        swipes.deleteAllOf(user.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Karty                                                              */
    /* ------------------------------------------------------------------ */

    private List<DiscoverCard> toCards(User ja, List<DiscoverCandidateRow> wiersze) {
        if (wiersze.isEmpty()) {
            return List.of();
        }
        List<Long> ids = wiersze.stream().map(DiscoverCandidateRow::getUserId).toList();
        Map<Long, ProfileCardService.Parts> czesci = cards.partsOf(ids);
        Set<Long> moi = new HashSet<>(swipes.artistIdsOf(ja.getId()));
        Map<Long, List<UserArtistRow>> wykonawcy = new HashMap<>();
        for (UserArtistRow a : swipes.artistsOf(ids)) {
            wykonawcy.computeIfAbsent(a.getUserId(), k -> new ArrayList<>()).add(a);
        }
        Map<Long, List<String>> gatunki = new HashMap<>();
        for (UserGenreRow g : swipes.sharedGenres(ja.getId(), ids)) {
            gatunki.computeIfAbsent(g.getUserId(), k -> new ArrayList<>()).add(g.getGenre());
        }
        Map<Long, ClanBadge> klany = clans.badgesOf(ids);
        LocalDateTime nowiOd = LocalDateTime.now(clock).minus(NOWA_OSOBA);
        LookingForConverter szukam = new LookingForConverter();

        List<DiscoverCard> wynik = new ArrayList<>();
        for (DiscoverCandidateRow r : wiersze) {
            List<UserArtistRow> jego = wykonawcy.getOrDefault(r.getUserId(), List.of());
            List<DiscoverArtist> wspolni = jego.stream().filter(a -> moi.contains(a.getArtistId()))
                .limit(WYKONAWCOW_NA_KARCIE).map(DiscoverService::artist).toList();
            List<DiscoverArtist> pozostali = r.getProfileOpen()
                ? jego.stream().filter(a -> !moi.contains(a.getArtistId()))
                    .limit(WYKONAWCOW_NA_KARCIE).map(DiscoverService::artist).toList()
                : List.of();
            ProfileCardService.Parts p = czesci.getOrDefault(r.getUserId(),
                new ProfileCardService.Parts(List.of(), List.of()));
            long punkty = DiscoverMatch.score(r.getSharedArtists(), r.getSharedTracks(), r.getSharedGenres());
            wynik.add(new DiscoverCard(
                r.getUsername(),
                avatarUrl(r.getAvatarFileName()),
                List.copyOf(p.photos()),
                r.getBio(),
                List.copyOf(szukam.convertToEntityAttribute(r.getLookingFor())),
                List.copyOf(p.prompts()),
                r.getCityVisible() ? r.getCity() : null,
                r.getCityVisible() ? proximity(r.getDistanceKm()) : null,
                DiscoverMatch.level(punkty),
                r.getSharedArtists(),
                r.getSharedTracks(),
                r.getSharedGenres(),
                wspolni,
                gatunki.getOrDefault(r.getUserId(), List.of()).stream().limit(GATUNKOW_NA_KARCIE).toList(),
                pozostali,
                r.getSharedFriends(),
                klany.get(r.getUserId()),
                r.getCreatedAt() != null && r.getCreatedAt().isAfter(nowiOd)));
        }
        return wynik;
    }

    /** Moja karta tak, jak widza ja inni - bez czesci wspolnej (nie ma z kim jej liczyc). */
    private DiscoverCard preview(User ja, com.musicclubapp.dto.ProfileCardResponse karta) {
        List<DiscoverArtist> ulubieni = ja.getFavoriteArtists().stream().limit(WYKONAWCOW_NA_KARCIE)
            .map(a -> new DiscoverArtist(a.getName(), a.getImageUrl())).toList();
        return new DiscoverCard(
            ja.getUsername(),
            avatarUrl(ja.getAvatarFileName()),
            karta.photos().stream().map(com.musicclubapp.dto.ProfilePhotoView::url).toList(),
            ja.getBio(),
            List.copyOf(ja.getLookingFor()),
            karta.prompts(),
            ja.isShowCity() ? ja.getCity() : null,
            null,
            0, 0, 0, 0,
            List.of(),
            List.of(),
            ja.getProfileVisibility() == com.musicclubapp.entity.ProfileVisibility.EVERYONE ? ulubieni : List.of(),
            0,
            clans.badgeOf(ja.getId()),
            ja.getCreatedAt() != null && ja.getCreatedAt().isAfter(LocalDateTime.now(clock).minus(NOWA_OSOBA)));
    }

    /** Pasmo odleglosci z tej samej skali co reszta aplikacji ({@link LocationScore}). */
    static String proximity(Double km) {
        if (km == null) {
            return null;
        }
        return switch (LocationScore.level(km)) {
            case 5 -> "SAME_CITY";
            case 4 -> "KM_30";
            case 3 -> "KM_60";
            case 2 -> "KM_120";
            case 1 -> "KM_250";
            default -> "FAR";
        };
    }

    /* ------------------------------------------------------------------ */

    /**
     * Czy ta osoba nadal moze byc w mojej talii - te same warunki co zapytanie o talie (bez zasiegu: karta mogla
     * przyjsc przy innym zasiegu). Inaczej "nie ma jej juz w talii", bez zdradzania powodu (np. blokady).
     */
    private void requireInDeck(User ja, User on) {
        boolean jest = on.isEnabled() && on.isDiscoverEnabled() && on.isEmailVerified()
            && !on.isBanned(BanKind.POSTING)
            && !blocks.eitherWay(ja.getId(), on.getId())
            && !users.areFriends(ja.getUsername(), on.getUsername())
            && requests.find(ja.getUsername(), on.getUsername()).isEmpty()
            && requests.find(on.getUsername(), ja.getUsername()).isEmpty();
        if (!jest) {
            throw OperationNotAllowedException.discoverGone();
        }
    }

    private static void requireOn(User ja) {
        if (!ja.isDiscoverEnabled()) {
            throw OperationNotAllowedException.discoverOff();
        }
    }

    /** Ile decyzji zostalo na dzis (doba wg czasu polskiego). */
    private int swipesLeft(User ja) {
        LocalDateTime polnoc = LocalDate.now(clock.withZone(POLSKA)).atStartOfDay(POLSKA)
            .withZoneSameInstant(clock.getZone()).toLocalDateTime();
        long dzis = swipes.countBySwiperIdAndCreatedAtGreaterThanEqual(ja.getId(), polnoc);
        return (int) Math.max(0, dziennie - dzis);
    }

    private long secondsToMidnight() {
        ZonedDateTime teraz = ZonedDateTime.now(clock.withZone(POLSKA));
        ZonedDateTime polnoc = teraz.toLocalDate().plusDays(1).atStartOfDay(POLSKA);
        return Math.max(1, Duration.between(teraz, polnoc).getSeconds());
    }

    /** Pusta lista w "NOT IN" nie przejdzie w kazdej bazie - pusty login nikogo nie wyklucza. */
    private static Collection<String> pomin(Collection<String> skip) {
        Set<String> wynik = new LinkedHashSet<>();
        if (skip != null) {
            skip.stream().filter(s -> s != null && !s.isBlank()).limit(POMIN_MAKS).forEach(wynik::add);
        }
        if (wynik.isEmpty()) {
            wynik.add("");
        }
        return wynik;
    }

    private static DiscoverArtist artist(UserArtistRow a) {
        return new DiscoverArtist(a.getName(), a.getImageUrl());
    }

    private static String avatarUrl(String fileName) {
        return fileName == null ? null : PostMapper.UPLOADS_PATH + fileName;
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
