package com.musicclubapp.service;

import com.musicclubapp.dto.ClanBadge;
import com.musicclubapp.dto.ClanColorOption;
import com.musicclubapp.dto.ClanInvitationResponse;
import com.musicclubapp.dto.ClanMemberResponse;
import com.musicclubapp.dto.ClanPendingInvite;
import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.dto.CreateClanRequest;
import com.musicclubapp.dto.MyClanResponse;
import com.musicclubapp.dto.UpdateClanRequest;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanInvitation;
import com.musicclubapp.entity.ClanInvitePolicy;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.InvitationStatus;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Klany: zakladanie, czlonkostwo, zaproszenia, role, glosowanie na kolor.
 *
 * <p>Zasady, ktorych nie da sie obejsc z zewnatrz: jedna osoba to najwyzej jeden klan
 * (unikalny {@code user_id} w bazie), do klanu wchodzi sie WYLACZNIE przez zaproszenie
 * od czlonka, a to, co pisze sie w klanie, widza czlonkowie i administrator aplikacji.</p>
 *
 * <p>Kolor klanu nie jest ustawiany przez nikogo - wynika z glosow czlonkow i jest
 * przeliczany przy kazdej zmianie skladu albo glosu.</p>
 */
@Service
public class ClanService {

    private static final Logger log = LoggerFactory.getLogger(ClanService.class);

    /** Po odmowie te same osoby nie zapraszaja jeszcze raz przez ten czas. */
    static final Duration ZAPROSZENIE_PO_ODMOWIE = Duration.ofDays(7);

    /** Tyle zaproszen najwyzej czeka na odpowiedz naraz - klan nie zasypie ludzi zaproszeniami. */
    static final int MAX_OCZEKUJACYCH = 50;

    /** Litery, cyfry, spacja i kilka znakow interpunkcyjnych; bez znakow sterujacych i ozdobnikow. */
    private static final Pattern NAZWA = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._'-]*");
    private static final Pattern SKROT = Pattern.compile("[A-Z0-9]{" + Clan.TAG_MIN + "," + Clan.TAG_MAX + "}");

    /** Slowa, ktorymi nazwa klanu nie moze udawac aplikacji ani jej obslugi. */
    private static final Set<String> ZASTRZEZONE = Set.of("admin", "administrator", "moderator", "musicclub", "support");

    private static final LocalDateTime EPOKA = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final ClanInvitationRepository invitations;
    private final ClanMessageRepository messages;
    private final UserRepository users;
    private final PostRepository posts;
    private final BlockService blocks;
    private final NotificationService notifications;
    private final ReportService reports;
    private final ReactionRepository reactions;
    private final FileStorageService fileStorage;
    private final Clock clock;

    public ClanService(ClanRepository clans,
                       ClanMemberRepository members,
                       ClanInvitationRepository invitations,
                       ClanMessageRepository messages,
                       UserRepository users,
                       PostRepository posts,
                       BlockService blocks,
                       NotificationService notifications,
                       ReportService reports,
                       ReactionRepository reactions,
                       FileStorageService fileStorage,
                       Clock clock) {
        this.reactions = reactions;
        this.clans = clans;
        this.members = members;
        this.invitations = invitations;
        this.messages = messages;
        this.users = users;
        this.posts = posts;
        this.blocks = blocks;
        this.notifications = notifications;
        this.reports = reports;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ */
    /*  Odczyt                                                             */
    /* ------------------------------------------------------------------ */

    /** Zakladka "Klan": moj klan i zaproszenia, ktore na mnie czekaja. */
    @Transactional(readOnly = true)
    public MyClanResponse mine(String username) {
        User user = user(username);
        ClanResponse clan = members.findByUserId(user.getId())
            .map(m -> response(m.getClan(), user))
            .orElse(null);
        List<ClanInvitationResponse> zaproszenia = clan != null ? List.of()
            : invitations.pendingFor(user.getId()).stream()
                .filter(i -> !blocks.eitherWay(user.getId(), i.getInviter().getId()))
                .map(i -> new ClanInvitationResponse(i.getId(), ClanMapper.badge(i.getClan()),
                    i.getInviter().getUsername(), (int) members.countByClanId(i.getClan().getId()), i.getCreatedAt()))
                .toList();
        return new MyClanResponse(clan, zaproszenia);
    }

    /** Strona klanu - dane jawne dla kazdego zalogowanego; zawartosc tylko dla czlonkow i administratora. */
    @Transactional(readOnly = true)
    public ClanResponse get(Long id, String viewerUsername) {
        User viewer = user(viewerUsername);
        Clan clan = clan(id);
        ClanResponse response = response(clan, viewer);
        if (response.viewingAsAdmin()) {
            // Administrator aplikacji moze przegladac klany (zgloszenia, podejrzenie naruszen prawa) -
            // zostaje slad, kto i kiedy. Ujawnione w polityce prywatnosci i na stronie klanu.
            log.info("Audyt: administrator {} przeglada klan {} (#{})", viewer.getUsername(), clan.getName(), clan.getId());
        }
        return response;
    }

    /** Plakietki klanow tych osob (tylko te, ktore w jakims sa) - jednym zapytaniem. */
    @Transactional(readOnly = true)
    public Map<Long, ClanBadge> badgesOf(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClanBadge> wynik = new HashMap<>();
        for (ClanMember m : members.ofUsers(userIds)) {
            wynik.put(m.getUser().getId(), ClanMapper.badge(m.getClan()));
        }
        return wynik;
    }

    @Transactional(readOnly = true)
    public ClanBadge badgeOf(Long userId) {
        return badgesOf(List.of(userId)).get(userId);
    }

    /* ------------------------------------------------------------------ */
    /*  Dostep do zawartosci                                               */
    /* ------------------------------------------------------------------ */

    /** Klan, w ktorym ta osoba jest czlonkiem - inaczej wyjatek. Do pisania postow i czatu. */
    @Transactional(readOnly = true)
    public Clan requireMember(String username, Long clanId) {
        User user = user(username);
        Clan clan = clan(clanId);
        if (!clan.hasMember(user.getId())) {
            throw OperationNotAllowedException.clanNotMember();
        }
        return clan;
    }

    /** Klan, do ktorego zawartosci ta osoba ma wglad: czlonek albo administrator aplikacji. */
    @Transactional(readOnly = true)
    public Clan requireAccess(User viewer, Long clanId) {
        Clan clan = clan(clanId);
        if (viewer.getRole() != Role.ADMIN && !clan.hasMember(viewer.getId())) {
            throw OperationNotAllowedException.clanNotMember();
        }
        return clan;
    }

    /** Czy ta osoba moze usuwac cudze posty i wiadomosci w tym klanie. */
    public boolean canModerate(User user, Clan clan) {
        ClanRole rola = clan.roleOf(user.getId());
        return rola != null && rola.manages();
    }

    /* ------------------------------------------------------------------ */
    /*  Zakladanie i zmiany                                                */
    /* ------------------------------------------------------------------ */

    @Transactional
    public ClanResponse create(String username, CreateClanRequest request) {
        User founder = user(username);
        if (members.findByUserId(founder.getId()).isPresent()) {
            throw OperationNotAllowedException.clanAlreadyMember();
        }
        String name = checkName(request.name());
        String key = NameKeys.of(name);
        String tag = checkTag(request.tag());
        if (clans.existsByNameKey(key)) {
            throw OperationNotAllowedException.clanNameTaken();
        }
        if (clans.existsByTag(tag)) {
            throw OperationNotAllowedException.clanTagTaken();
        }

        Clan clan = clans.save(new Clan(name, key, tag, blankToNull(request.description())));
        attach(clan, members.save(new ClanMember(clan, founder, ClanRole.FOUNDER, now())));
        // Zalozyciel od razu jest w klanie - inne oczekujace zaproszenia nie maja juz sensu
        invitations.deleteByInviteeId(founder.getId());
        return response(clan, founder);
    }

    /** Opis zmieniaja zalozyciel i administratorzy; nazwe i skrot - tylko zalozyciel. */
    @Transactional
    public ClanResponse update(Long id, String username, UpdateClanRequest request) {
        User user = user(username);
        Clan clan = clan(id);
        ClanRole rola = clan.roleOf(user.getId());
        if (rola == null || !rola.manages()) {
            throw OperationNotAllowedException.clanNotManager();
        }

        boolean zmieniaNazwe = request.name() != null && !request.name().isBlank();
        boolean zmieniaSkrot = request.tag() != null && !request.tag().isBlank();
        if ((zmieniaNazwe || zmieniaSkrot) && rola != ClanRole.FOUNDER) {
            throw OperationNotAllowedException.clanNotFounder();
        }
        if (zmieniaNazwe || zmieniaSkrot) {
            String name = zmieniaNazwe ? checkName(request.name()) : clan.getName();
            String key = NameKeys.of(name);
            String tag = zmieniaSkrot ? checkTag(request.tag()) : clan.getTag();
            if (clans.existsByNameKeyAndIdNot(key, clan.getId())) {
                throw OperationNotAllowedException.clanNameTaken();
            }
            if (clans.existsByTagAndIdNot(tag, clan.getId())) {
                throw OperationNotAllowedException.clanTagTaken();
            }
            clan.rename(name, key, tag);
        }
        if (request.description() != null) {
            clan.setDescription(blankToNull(request.description()));
        }
        return response(clan, user);
    }

    /** Ikona (mala, na plakietkach). */
    @Transactional
    public ClanResponse setIcon(Long id, String username, MultipartFile file) {
        return setImage(id, username, file, true);
    }

    @Transactional
    public ClanResponse removeIcon(Long id, String username) {
        return setImage(id, username, null, true);
    }

    /** Zdjecie klanu (duze, u gory jego strony). */
    @Transactional
    public ClanResponse setPhoto(Long id, String username, MultipartFile file) {
        return setImage(id, username, file, false);
    }

    @Transactional
    public ClanResponse removePhoto(Long id, String username) {
        return setImage(id, username, null, false);
    }

    private ClanResponse setImage(Long id, String username, MultipartFile file, boolean icon) {
        User user = user(username);
        Clan clan = clan(id);
        if (!canModerate(user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        String previous = icon ? clan.getIconFileName() : clan.getPhotoFileName();
        String created = file == null ? null : fileStorage.saveImage(file);
        if (icon) {
            clan.setIconFileName(created);
        } else {
            clan.setPhotoFileName(created);
        }
        fileStorage.remove(previous);
        return response(clan, user);
    }

    /** Rozwiazanie klanu: zalozyciel albo administrator aplikacji (np. po zgloszeniu). */
    @Transactional
    public void disband(Long id, String username) {
        User user = user(username);
        Clan clan = clan(id);
        boolean zalozyciel = clan.roleOf(user.getId()) == ClanRole.FOUNDER;
        if (!zalozyciel && user.getRole() != Role.ADMIN) {
            throw OperationNotAllowedException.clanNotFounder();
        }
        if (!zalozyciel) {
            log.info("Audyt: administrator {} rozwiazuje klan {} (#{})", user.getUsername(), clan.getName(), clan.getId());
        }
        delete(clan);
    }

    /* ------------------------------------------------------------------ */
    /*  Zaproszenia                                                        */
    /* ------------------------------------------------------------------ */

    /** Czlonek zaprasza osobe. Zaproszony musi byc poza klanami i pozwalac na to w prywatnosci. */
    @Transactional
    public ClanResponse invite(Long clanId, String inviterName, String targetName) {
        User inviter = user(inviterName);
        Clan clan = clan(clanId);
        if (!clan.hasMember(inviter.getId())) {
            throw OperationNotAllowedException.clanNotMember();
        }
        User target = users.findByUsername(targetName == null ? "" : targetName.trim())
            .orElseThrow(() -> new NoSuchElementFoundException("user", targetName));

        // Blokada, "nikt" i "tylko znajomi" daja ten sam wynik - zaproszony nie zdradza, ktore z nich
        if (!canInvite(inviter, target)) {
            throw OperationNotAllowedException.cannotInvite();
        }
        if (members.findByUserId(target.getId()).isPresent()) {
            throw OperationNotAllowedException.clanInviteeInClan();
        }
        if (members.countByClanId(clan.getId()) >= Clan.MAX_MEMBERS) {
            throw OperationNotAllowedException.clanFull(Clan.MAX_MEMBERS);
        }

        LocalDateTime now = now();
        Optional<ClanInvitation> existing = invitations.findByClanIdAndInviteeId(clan.getId(), target.getId());
        if (existing.isPresent()) {
            ClanInvitation i = existing.get();
            if (i.getStatus() == InvitationStatus.PENDING) {
                throw OperationNotAllowedException.clanAlreadyInvited();
            }
            // Odmowa: przez tydzien nie naciskamy, i nie mowimy, ze to przez odmowe
            if (i.getAnsweredAt() != null && i.getAnsweredAt().plus(ZAPROSZENIE_PO_ODMOWIE).isAfter(now)) {
                throw OperationNotAllowedException.cannotInvite();
            }
            pilnujLimitu(clan);
            i.reinvite(inviter, now);
        } else {
            pilnujLimitu(clan);
            invitations.save(new ClanInvitation(clan, target, inviter, now));
        }
        notifications.clanInvited(target, inviter, clan);
        return response(clan, inviter);
    }

    private void pilnujLimitu(Clan clan) {
        if (invitations.countByClanIdAndStatus(clan.getId(), InvitationStatus.PENDING) >= MAX_OCZEKUJACYCH) {
            throw OperationNotAllowedException.clanTooManyInvites(MAX_OCZEKUJACYCH);
        }
    }

    /** Cofniecie zaproszenia: zalozyciel, administrator klanu albo ten, kto je wyslal. */
    @Transactional
    public ClanResponse cancelInvitation(Long clanId, Long invitationId, String username) {
        User user = user(username);
        Clan clan = clan(clanId);
        ClanInvitation i = invitations.findFull(invitationId)
            .filter(x -> x.getClan().getId().equals(clan.getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan invitation", invitationId));
        boolean wyslal = i.getInviter().getId().equals(user.getId());
        if (!wyslal && !canModerate(user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        notifications.clanInviteGone(i.getInvitee().getId(), clan.getId());
        invitations.delete(i);
        return response(clan, user);
    }

    /** Przyjecie zaproszenia - jedyna droga do klanu. */
    @Transactional
    public MyClanResponse accept(Long invitationId, String username) {
        User user = user(username);
        ClanInvitation i = own(invitationId, user);
        if (i.getStatus() != InvitationStatus.PENDING) {
            throw new NoSuchElementFoundException("clan invitation", invitationId);
        }
        if (members.findByUserId(user.getId()).isPresent()) {
            throw OperationNotAllowedException.clanAlreadyMember();
        }
        Clan clan = i.getClan();
        if (members.countByClanId(clan.getId()) >= Clan.MAX_MEMBERS) {
            throw OperationNotAllowedException.clanFull(Clan.MAX_MEMBERS);
        }
        attach(clan, members.save(new ClanMember(clan, user, ClanRole.MEMBER, now())));
        // Jestem w klanie - reszta zaproszen (takze odrzucone) przestaje miec sens
        for (ClanInvitation inne : invitations.pendingFor(user.getId())) {
            notifications.clanInviteGone(user.getId(), inne.getClan().getId());
        }
        invitations.deleteByInviteeId(user.getId());
        return mine(username);
    }

    @Transactional
    public MyClanResponse decline(Long invitationId, String username) {
        User user = user(username);
        ClanInvitation i = own(invitationId, user);
        if (i.getStatus() == InvitationStatus.PENDING) {
            i.decline(now());
            notifications.clanInviteGone(user.getId(), i.getClan().getId());
        }
        return mine(username);
    }

    private ClanInvitation own(Long invitationId, User user) {
        return invitations.findFull(invitationId)
            .filter(i -> i.getInvitee().getId().equals(user.getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan invitation", invitationId));
    }

    /** Czy ta osoba moze mnie teraz zaprosic - blokady i ustawienie "kto moze mnie zaprosic do klanu". */
    @Transactional(readOnly = true)
    public boolean canInvite(User inviter, User target) {
        if (inviter.getId().equals(target.getId()) || blocks.eitherWay(inviter.getId(), target.getId())) {
            return false;
        }
        ClanInvitePolicy zasada = target.getClanInvitesFrom();
        return switch (zasada) {
            case EVERYONE -> true;
            case NOBODY -> false;
            case FRIENDS -> users.areFriends(inviter.getUsername(), target.getUsername());
        };
    }

    /* ------------------------------------------------------------------ */
    /*  Czlonkostwo                                                        */
    /* ------------------------------------------------------------------ */

    /** Odejscie. Zalozyciel musi najpierw przekazac klan - chyba ze jest w nim sam, wtedy klan znika. */
    @Transactional
    public MyClanResponse leave(Long clanId, String username) {
        User user = user(username);
        Clan clan = clan(clanId);
        ClanMember m = memberOf(clan, user.getId())
            .orElseThrow(OperationNotAllowedException::clanNotMember);
        if (m.getRole() == ClanRole.FOUNDER) {
            if (members.countByClanId(clan.getId()) > 1) {
                throw OperationNotAllowedException.clanFounderMustTransfer();
            }
            delete(clan);
            return mine(username);
        }
        remove(clan, m);
        return mine(username);
    }

    /**
     * Wyrzucenie: zalozyciel wyrzuca kazdego, administrator klanu - zwyklych czlonkow.
     * Wyrzucony dostaje powiadomienie bez informacji, kto to zrobil.
     */
    @Transactional
    public ClanResponse kick(Long clanId, String actorName, String targetName) {
        User actor = user(actorName);
        Clan clan = clan(clanId);
        ClanRole rolaActor = clan.roleOf(actor.getId());
        if (rolaActor == null || !rolaActor.manages()) {
            throw OperationNotAllowedException.clanNotManager();
        }
        User target = users.findByUsername(targetName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", targetName));
        ClanMember m = memberOf(clan, target.getId())
            .orElseThrow(() -> new NoSuchElementFoundException("clan member", targetName));
        if (m.getRole() == ClanRole.FOUNDER || target.getId().equals(actor.getId())
            || (rolaActor == ClanRole.ADMIN && m.getRole() != ClanRole.MEMBER)) {
            throw OperationNotAllowedException.clanCannotKick();
        }
        remove(clan, m);
        notifications.clanKicked(target, clan);
        return response(clan, actor);
    }

    /** Awans na administratora klanu albo zdjecie z funkcji - tylko zalozyciel. */
    @Transactional
    public ClanResponse changeRole(Long clanId, String actorName, String targetName, ClanRole nowaRola) {
        User actor = user(actorName);
        Clan clan = clan(clanId);
        if (clan.roleOf(actor.getId()) != ClanRole.FOUNDER) {
            throw OperationNotAllowedException.clanNotFounder();
        }
        User target = users.findByUsername(targetName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", targetName));
        ClanMember m = memberOf(clan, target.getId())
            .orElseThrow(() -> new NoSuchElementFoundException("clan member", targetName));
        if (m.getRole() == ClanRole.FOUNDER || nowaRola == ClanRole.FOUNDER) {
            throw OperationNotAllowedException.clanCannotKick();
        }
        m.setRole(nowaRola);
        return response(clan, actor);
    }

    /** Przekazanie klanu: nowy zalozyciel, dotychczasowy zostaje administratorem. */
    @Transactional
    public ClanResponse transfer(Long clanId, String actorName, String targetName) {
        User actor = user(actorName);
        Clan clan = clan(clanId);
        ClanMember mine = memberOf(clan, actor.getId()).orElseThrow(OperationNotAllowedException::clanNotMember);
        if (mine.getRole() != ClanRole.FOUNDER) {
            throw OperationNotAllowedException.clanNotFounder();
        }
        User target = users.findByUsername(targetName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", targetName));
        ClanMember m = memberOf(clan, target.getId())
            .orElseThrow(() -> new NoSuchElementFoundException("clan member", targetName));
        if (m.getId().equals(mine.getId())) {
            throw OperationNotAllowedException.clanCannotKick();
        }
        m.setRole(ClanRole.FOUNDER);
        mine.setRole(ClanRole.ADMIN);
        return response(clan, actor);
    }

    /* ------------------------------------------------------------------ */
    /*  Kolor z glosowania                                                 */
    /* ------------------------------------------------------------------ */

    /** Moj glos na kolor klanu (null = cofniecie glosu). Kolor klanu jest zaraz przeliczany. */
    @Transactional
    public ClanResponse vote(Long clanId, String username, ClanColor color) {
        User user = user(username);
        Clan clan = clan(clanId);
        ClanMember m = memberOf(clan, user.getId()).orElseThrow(OperationNotAllowedException::clanNotMember);
        m.vote(color, now());
        members.flush();
        recomputeColor(clan);
        return response(clan, user);
    }

    /**
     * Wygrywa kolor z najwieksza liczba glosow; przy remisie ten, na ktory zaglosowano
     * wczesniej; bez zadnego glosu - kolor domyslny.
     */
    void recomputeColor(Clan clan) {
        // Dla kazdego koloru: ile glosow i kiedy oddano najwczesniejszy (w nanosekundach - dwa glosy
        // w tej samej sekundzie sa czesto, a przy remisie o wyniku decyduje kolejnosc)
        Map<ClanColor, long[]> stan = new EnumMap<>(ClanColor.class);
        for (ClanMember m : members.ofClan(clan.getId())) {
            if (m.getColorVote() == null) {
                continue;
            }
            long[] wpis = stan.computeIfAbsent(m.getColorVote(), c -> new long[] {0, Long.MAX_VALUE});
            wpis[0]++;
            long kiedy = m.getColorVotedAt() == null ? Long.MAX_VALUE
                : ChronoUnit.NANOS.between(EPOKA, m.getColorVotedAt());
            wpis[1] = Math.min(wpis[1], kiedy);
        }
        ClanColor wygrany = stan.entrySet().stream()
            .max(Comparator.<Map.Entry<ClanColor, long[]>>comparingLong(e -> e.getValue()[0])
                .thenComparing(e -> -e.getValue()[1])
                .thenComparing(e -> -e.getKey().ordinal()))
            .map(Map.Entry::getKey)
            .orElse(ClanColor.DEFAULT);
        clan.setColor(wygrany);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Konto jest usuwane: znikaja jego zaproszenia i wiadomosci na czatach, a z klanu wychodzi
     * (jesli byl zalozycielem - klan przejmuje administrator albo najstarszy czlonek; jesli byl
     * sam - klan znika razem z postami).
     */
    @Transactional
    public void deleteAllOf(User user) {
        Long id = user.getId();
        invitations.deleteAllOfUser(id);
        messages.deleteBySenderId(id);
        members.findByUserId(id).ifPresent(m -> {
            Clan clan = m.getClan();
            if (members.countByClanId(clan.getId()) <= 1) {
                delete(clan);
                return;
            }
            if (m.getRole() == ClanRole.FOUNDER) {
                members.ofClan(clan.getId()).stream()
                    .filter(x -> !x.getId().equals(m.getId()))
                    .findFirst()   // lista jest ulozona: zalozyciel, administratorzy, potem wedlug stazu
                    .ifPresent(nastepca -> nastepca.setRole(ClanRole.FOUNDER));
            }
            remove(clan, m);
        });
    }

    /**
     * Dopisuje czlonka do listy w pamieci, jesli jej jeszcze nie ma: lista laduje sie leniwie
     * i - zaleznie od tego, czy ktos jej juz dotknal - albo jest swieza (z nowym wierszem),
     * albo nie. Bez tego sprawdzenia czlonek trafialby na nia dwa razy.
     */
    private void attach(Clan clan, ClanMember m) {
        if (clan.getMembers().stream().noneMatch(x -> x.getId().equals(m.getId()))) {
            clan.getMembers().add(m);
        }
    }

    private void remove(Clan clan, ClanMember m) {
        clan.getMembers().removeIf(x -> x.getId().equals(m.getId()));
        members.delete(m);
        members.flush();
        recomputeColor(clan);
    }

    /** Kasuje klan razem ze wszystkim, co w nim zostalo: postami (i ich zdjeciami), czatem, zaproszeniami. */
    private void delete(Clan clan) {
        Long id = clan.getId();
        // Nazwy plikow zapamietujemy teraz - zapytania nizej czyszcza kontekst i odpinaja te encje
        String ikona = clan.getIconFileName();
        String zdjecie = clan.getPhotoFileName();

        // Kolejnosc jak przy usuwaniu postow konta: powiadomienia i zgloszenia wskazuja na posty kluczem obcym
        notifications.clanPostsDeleted(id);
        reports.detachPostsOfClan(id);

        // Posty ladujemy dopiero po tych zapytaniach - swiezo, razem z wlasnymi zdjeciami
        List<Post> postyKlanu = posts.findByClanId(id);
        List<String> pliki = postyKlanu.stream()
            .flatMap(p -> p.getImages().stream())
            .map(PostImage::getFileName)
            .toList();
        // Reakcje wprost - nie liczymy na kaskade z encji, ktora nie widzi reakcji dopisanych w tej samej sesji
        reactions.deleteByPostsOfClan(id);
        posts.deleteAll(postyKlanu);
        posts.flush();

        messages.deleteByClanId(id);
        invitations.deleteByClanId(id);
        members.deleteByClanId(id);
        clans.deleteRow(id);

        pliki.forEach(fileStorage::remove);
        fileStorage.remove(ikona);
        fileStorage.remove(zdjecie);
    }

    /* ------------------------------------------------------------------ */
    /*  Odpowiedzi                                                         */
    /* ------------------------------------------------------------------ */

    private ClanResponse response(Clan clan, User viewer) {
        List<ClanMember> czlonkowie = members.ofClan(clan.getId());
        ClanRole moja = czlonkowie.stream()
            .filter(m -> m.getUser().getId().equals(viewer.getId()))
            .map(ClanMember::getRole).findFirst().orElse(null);
        boolean admin = viewer.getRole() == Role.ADMIN;
        boolean wglad = moja != null || admin;

        // Osoby z blokad ogladajacego (w obie strony) nie pojawiaja sie na liscie; administrator widzi wszystkich
        Set<Long> ukryci = admin ? Set.of() : blocks.hiddenFor(viewer.getId());
        List<ClanMemberResponse> lista = czlonkowie.stream()
            .filter(m -> !ukryci.contains(m.getUser().getId()))
            .map(m -> new ClanMemberResponse(
                m.getUser().getUsername(), ClanMapper.avatarUrl(m.getUser()), m.getRole(), m.getJoinedAt(),
                m.getUser().getId().equals(viewer.getId()), m.getColorVote()))
            .toList();

        Map<ClanColor, Long> glosy = new EnumMap<>(ClanColor.class);
        czlonkowie.stream().map(ClanMember::getColorVote).filter(c -> c != null)
            .forEach(c -> glosy.merge(c, 1L, Long::sum));
        List<ClanColorOption> paleta = Arrays.stream(ClanColor.values())
            .map(c -> new ClanColorOption(c.name(), c.hex(), glosy.getOrDefault(c, 0L)))
            .toList();
        // findFirst() rzuca NPE na pustym glosie (null w strumieniu) - stad Optional dopiero po znalezieniu czlonka
        ClanColor mojGlos = czlonkowie.stream()
            .filter(m -> m.getUser().getId().equals(viewer.getId()))
            .findFirst().map(ClanMember::getColorVote).orElse(null);

        boolean zarzadza = moja != null && moja.manages();
        List<ClanPendingInvite> zaproszenia = moja == null ? List.of()
            : invitations.pendingOf(clan.getId()).stream()
                .map(i -> new ClanPendingInvite(i.getId(), i.getInvitee().getUsername(), i.getInviter().getUsername(),
                    i.getCreatedAt(), zarzadza || i.getInviter().getId().equals(viewer.getId())))
                .toList();

        return new ClanResponse(clan.getId(), clan.getName(), clan.getTag(), clan.getDescription(),
            clan.getColor().name(), clan.getColor().hex(),
            ClanMapper.uploadUrl(clan.getIconFileName()), ClanMapper.uploadUrl(clan.getPhotoFileName()),
            clan.getCreatedAt(), czlonkowie.size(), Clan.MAX_MEMBERS, moja, admin && moja == null, wglad,
            lista, paleta, mojGlos, zaproszenia);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprawdzanie danych                                                 */
    /* ------------------------------------------------------------------ */

    private String checkName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.length() < Clan.NAME_MIN || name.length() > Clan.NAME_MAX || !NAZWA.matcher(name).matches()) {
            throw OperationNotAllowedException.clanNameInvalid();
        }
        String key = NameKeys.of(name);
        if (key.isBlank()) {
            throw OperationNotAllowedException.clanNameInvalid();
        }
        boolean zastrzezone = Arrays.stream(key.split(" ")).anyMatch(ZASTRZEZONE::contains)
            || key.replace(" ", "").contains("musicclub");
        if (zastrzezone) {
            throw OperationNotAllowedException.clanNameReserved();
        }
        return name;
    }

    private String checkTag(String raw) {
        String tag = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (!SKROT.matcher(tag).matches()) {
            throw OperationNotAllowedException.clanTagInvalid();
        }
        return tag;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private Optional<ClanMember> memberOf(Clan clan, Long userId) {
        return members.ofClan(clan.getId()).stream().filter(m -> m.getUser().getId().equals(userId)).findFirst();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    private Clan clan(Long id) {
        return clans.findById(id).orElseThrow(() -> new NoSuchElementFoundException("clan", id));
    }
}
