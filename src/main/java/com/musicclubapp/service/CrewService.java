package com.musicclubapp.service;

import com.musicclubapp.dto.CrewCardResponse;
import com.musicclubapp.dto.CrewDetailsResponse;
import com.musicclubapp.dto.CrewForm;
import com.musicclubapp.dto.CrewPersonView;
import com.musicclubapp.dto.CrewRequestView;
import com.musicclubapp.dto.MyCrewResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.Crew;
import com.musicclubapp.entity.CrewJoinPolicy;
import com.musicclubapp.entity.CrewMember;
import com.musicclubapp.entity.CrewRequest;
import com.musicclubapp.entity.CrewRole;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.CrewMemberRepository;
import com.musicclubapp.repository.CrewMessageRepository;
import com.musicclubapp.repository.CrewRepository;
import com.musicclubapp.repository.CrewRequestRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ekipy na koncert: zakladanie, dolaczanie (od razu albo za zgoda), opuszczanie, lista pod wydarzeniem i moje ekipy.
 * Wejscie do ekipy zapisuje na wydarzenie jako "Biore udzial" - kto jedzie z ekipa, ten idzie. Jedna ekipa na osobe
 * i wydarzenie. Ekipa zakladajacego, z ktorym mam blokade (w ktorakolwiek strone), jest dla mnie niewidoczna; osoby
 * z blokad w ekipach, ktore widze, licza sie do licznika, ale nie ma ich w podgladzie.
 */
@Service
public class CrewService {

    /** Ile ekip na nadchodzace koncerty moze zalozyc jedna osoba - przeciw zasypywaniu list pod wydarzeniami. */
    public static final int MAX_FOUNDED = 10;
    /** Po odmowie przez tydzien nie da sie poprosic tej samej ekipy. */
    static final Duration PROSBA_PO_ODMOWIE = Duration.ofDays(7);
    /** Ilu ludzi w podgladzie karty. */
    static final int PODGLAD = 5;
    /** Czat ekipy jest otwarty jeszcze dwa dni po koncercie (rozliczenia, zdjecia), potem tylko do czytania. */
    static final int CZAT_PO_KONCERCIE_DNI = 2;

    private final CrewRepository crews;
    private final CrewMemberRepository members;
    private final CrewRequestRepository requests;
    private final CrewMessageRepository messages;
    private final MusicEventRepository events;
    private final UserRepository users;
    private final EventParticipationService participations;
    private final EventImportService importer;
    private final BlockService blocks;
    private final CityIndex cities;
    private final LocationService location;
    private final NotificationService notifications;
    private final MeetingService meetings;
    private final Clock clock;

    public CrewService(CrewRepository crews, CrewMemberRepository members, CrewRequestRepository requests,
                       CrewMessageRepository messages, MusicEventRepository events, UserRepository users,
                       EventParticipationService participations, EventImportService importer, BlockService blocks,
                       CityIndex cities, LocationService location, NotificationService notifications,
                       MeetingService meetings, Clock clock) {
        this.crews = crews;
        this.members = members;
        this.requests = requests;
        this.messages = messages;
        this.events = events;
        this.users = users;
        this.participations = participations;
        this.importer = importer;
        this.blocks = blocks;
        this.cities = cities;
        this.location = location;
        this.notifications = notifications;
        this.meetings = meetings;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ */
    /*  Lista pod wydarzeniem                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Ekipy wydarzenia dla ogladajacego: najpierw moja, potem te, do ktorych mozna jeszcze wejsc (pelne i zamkniete na
     * koncu - na gorze nic by nie daly), wsrod nich blizej mojego miasta, potem ze znajomymi w srodku.
     */
    @Transactional(readOnly = true)
    public List<CrewCardResponse> ofEvent(Long eventId, String username) {
        User viewer = user(username);
        MusicEvent event = events.findById(eventId).orElseThrow(() -> new NoSuchElementFoundException("event", eventId));
        Set<Long> hidden = blocks.hiddenFor(viewer.getId());
        List<Crew> lista = crews.ofEvent(event.getId()).stream()
            .filter(c -> !hidden.contains(c.getFounder().getId()))
            .toList();
        return cards(lista, viewer, hidden, event);
    }

    private List<CrewCardResponse> cards(List<Crew> lista, User viewer, Set<Long> hidden, MusicEvent event) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CrewMember>> sklady = members.ofCrews(lista.stream().map(Crew::getId).toList()).stream()
            .collect(Collectors.groupingBy(m -> m.getCrew().getId()));
        Map<Long, CrewRequest> prosby = requests.mine(viewer.getId(), lista.stream().map(Crew::getId).toList()).stream()
            .collect(Collectors.toMap(r -> r.getCrew().getId(), r -> r));
        Optional<CrewMember> moja = event == null ? Optional.empty()
            : members.findByEventIdAndUserId(event.getId(), viewer.getId());
        Set<Long> znajomi = viewer.getFriends().stream().map(User::getId).collect(Collectors.toSet());
        LocationService.Origin skad = location.originOf(viewer);
        boolean otwarte = event == null || !past(event);

        List<CrewCardResponse> wynik = new ArrayList<>();
        for (Crew c : lista) {
            List<CrewMember> sklad = sklady.getOrDefault(c.getId(), List.of());
            wynik.add(card(c, sklad, viewer, hidden, znajomi, skad, prosby.get(c.getId()), moja, otwarte));
        }
        wynik.sort(Comparator
            .comparing((CrewCardResponse k) -> !("FOUNDER".equals(k.myState()) || "MEMBER".equals(k.myState())))
            .thenComparing(k -> k.members() >= k.capacity() || k.closed())
            .thenComparing(k -> -k.near())
            .thenComparing(k -> -k.friends())
            .thenComparing(CrewCardResponse::id));
        return wynik;
    }

    private CrewCardResponse card(Crew c, List<CrewMember> sklad, User viewer, Set<Long> hidden, Set<Long> znajomi,
                                  LocationService.Origin skad, CrewRequest prosba, Optional<CrewMember> moja,
                                  boolean otwarte) {
        CrewMember ja = sklad.stream().filter(m -> m.getUser().getId().equals(viewer.getId())).findFirst().orElse(null);
        String stan = ja != null ? ja.getRole().name()
            : prosba == null ? null
            : prosba.isPending() ? "REQUESTED"
            : prosba.getDeclinedAt().plus(PROSBA_PO_ODMOWIE).isAfter(LocalDateTime.now(clock)) ? "DECLINED" : null;
        boolean wolne = sklad.size() < c.getCapacity();
        boolean canJoin = ja == null && moja.isEmpty() && otwarte && !c.isClosed() && wolne && stan == null
            && !blocks.eitherWay(viewer.getId(), c.getFounder().getId());
        Double km = location.distanceKm(skad, c.getDepartureLat(), c.getDepartureLon(), c.getDepartureCityKey());
        List<CrewPersonView> podglad = sklad.stream()
            .filter(m -> !hidden.contains(m.getUser().getId()))
            .limit(PODGLAD)
            .map(this::person)
            .toList();
        return new CrewCardResponse(c.getId(), c.getTitle(), c.getDescription(), c.getFounder().getUsername(),
            avatar(c.getFounder()), sklad.size(), c.getCapacity(), c.getJoinPolicy(), c.isClosed(), c.getDepartureCity(),
            LocationScore.level(km), LocationService.rounded(km),
            sklad.stream().filter(m -> znajomi.contains(m.getUser().getId())).count(),
            podglad, stan, canJoin);
    }

    /* ------------------------------------------------------------------ */
    /*  Zakladanie i zmiany                                                */
    /* ------------------------------------------------------------------ */

    /** Nowa ekipa - zakladajacy jest jej pierwszym czlonkiem i idzie na koncert. */
    @Transactional
    public CrewDetailsResponse create(Long eventId, String username, CrewForm form) {
        User founder = user(username);
        if (founder.isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, founder.bannedUntil(BanKind.POSTING));
        }
        MusicEvent event = openEvent(eventId);
        if (members.existsByEventIdAndUserId(event.getId(), founder.getId())) {
            throw OperationNotAllowedException.crewAlreadyIn();
        }
        if (crews.countFoundedUpcoming(founder.getId(), importer.today()) >= MAX_FOUNDED) {
            throw OperationNotAllowedException.crewTooMany(MAX_FOUNDED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Crew crew = new Crew(event, founder, now);
        apply(crew, form, 1, now);
        crew = crews.save(crew);
        enter(crew, founder, CrewRole.FOUNDER, now);
        return details(crew.getId(), username);
    }

    /** Zmiana opisu, limitu, naboru i miasta - tylko zakladajacy; limit nie mniejszy niz liczba osob w ekipie. */
    @Transactional
    public CrewDetailsResponse update(Long crewId, String username, CrewForm form) {
        User founder = user(username);
        Crew crew = founded(crewId, founder);
        if (founder.isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, founder.bannedUntil(BanKind.POSTING));
        }
        apply(crew, form, members.countByCrewId(crew.getId()), LocalDateTime.now(clock));
        return details(crewId, username);
    }

    private void apply(Crew crew, CrewForm form, long obecnie, LocalDateTime now) {
        if (form.capacity() < obecnie) {
            throw OperationNotAllowedException.crewCapacityBelowMembers((int) obecnie);
        }
        crew.describe(blankToNull(form.title()), blankToNull(form.description()), form.capacity(), form.joinPolicy(), now);
        String miasto = blankToNull(form.departureCity());
        if (miasto == null) {
            crew.departFrom(null, null, null, null);
        } else {
            Optional<CityIndex.City> znane = cities.find(miasto);
            crew.departFrom(znane.map(CityIndex.City::name).orElse(miasto),
                znane.map(CityIndex.City::key).orElse(EventImportService.cityKey(miasto)),
                znane.map(CityIndex.City::latitude).orElse(null),
                znane.map(CityIndex.City::longitude).orElse(null));
        }
    }

    /** Zamkniecie (albo otwarcie) naboru - tylko zakladajacy. */
    @Transactional
    public CrewDetailsResponse setClosed(Long crewId, String username, boolean closed) {
        Crew crew = founded(crewId, user(username));
        crew.setClosed(closed, LocalDateTime.now(clock));
        return details(crewId, username);
    }

    /* ------------------------------------------------------------------ */
    /*  Dolaczanie, prosby, odejscie                                       */
    /* ------------------------------------------------------------------ */

    /**
     * "Dolacz" (nabor otwarty) albo "Popros" (za zgoda). Odpowiedz w obu przypadkach to karta ekipy z nowym stanem.
     * Blokada z zakladajacym daje te sama odpowiedz co nieistniejaca ekipa.
     */
    @Transactional
    public CrewCardResponse join(Long crewId, String username, String message) {
        User viewer = user(username);
        Crew crew = crews.findById(crewId).orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
        if (blocks.eitherWay(viewer.getId(), crew.getFounder().getId())) {
            throw new NoSuchElementFoundException("crew", crewId);
        }
        MusicEvent event = openEvent(crew.getEvent().getId());
        if (members.existsByEventIdAndUserId(event.getId(), viewer.getId())) {
            throw OperationNotAllowedException.crewAlreadyIn();
        }
        if (crew.isClosed()) {
            throw OperationNotAllowedException.crewClosed();
        }
        if (members.countByCrewId(crew.getId()) >= crew.getCapacity()) {
            throw OperationNotAllowedException.crewFull(crew.getCapacity());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (crew.getJoinPolicy() == CrewJoinPolicy.OPEN) {
            enter(crew, viewer, CrewRole.MEMBER, now);
            notifications.crewMemberJoined(crew.getFounder(), viewer, crew);
        } else {
            Optional<CrewRequest> byla = requests.findByCrewIdAndUserId(crew.getId(), viewer.getId());
            if (byla.isPresent() && byla.get().isPending()) {
                throw OperationNotAllowedException.crewRequestAlready();
            }
            if (byla.isPresent() && byla.get().getDeclinedAt().plus(PROSBA_PO_ODMOWIE).isAfter(now)) {
                throw OperationNotAllowedException.crewRequestDeclined();
            }
            String tresc = blankToNull(message);
            if (byla.isPresent()) {
                byla.get().renew(tresc, now);
            } else {
                requests.save(new CrewRequest(crew, viewer, tresc, now));
            }
            notifications.crewJoinRequested(crew.getFounder(), viewer, crew);
        }
        return card(crew.getId(), viewer);
    }

    /** Cofniecie mojej czekajacej prosby. */
    @Transactional
    public CrewCardResponse cancelRequest(Long crewId, String username) {
        User viewer = user(username);
        requests.findByCrewIdAndUserId(crewId, viewer.getId()).filter(CrewRequest::isPending).ifPresent(r -> {
            notifications.crewRequestGone(viewer.getId(), crewId);
            requests.delete(r);
        });
        requests.flush();
        return card(crewId, viewer);
    }

    /** Przyjecie prosby przez zakladajacego - jesli jest jeszcze miejsce i osoba nie jest juz w innej ekipie. */
    @Transactional
    public CrewDetailsResponse accept(Long crewId, Long requestId, String username) {
        Crew crew = founded(crewId, user(username));
        CrewRequest prosba = requests.findById(requestId)
            .filter(r -> r.getCrew().getId().equals(crewId) && r.isPending())
            .orElseThrow(() -> new NoSuchElementFoundException("crew request", requestId));
        User kto = prosba.getUser();
        if (members.existsByEventIdAndUserId(crew.getEvent().getId(), kto.getId())) {
            requests.delete(prosba);
            throw OperationNotAllowedException.crewMemberElsewhere();
        }
        if (members.countByCrewId(crew.getId()) >= crew.getCapacity()) {
            throw OperationNotAllowedException.crewFull(crew.getCapacity());
        }
        openEvent(crew.getEvent().getId());
        notifications.crewRequestGone(kto.getId(), crewId);
        // Wprost, zanim zbiorcze zapytanie skasuje reszte prosb tej osoby - inaczej zostalaby w pamieci jako zywa
        requests.delete(prosba);
        requests.flush();
        enter(crew, kto, CrewRole.MEMBER, LocalDateTime.now(clock));
        notifications.crewRequestAccepted(kto, crew);
        return details(crewId, username);
    }

    /** Odmowa - bez powiadomienia i bez powodu; przez tydzien ta osoba nie poprosi ponownie. */
    @Transactional
    public CrewDetailsResponse decline(Long crewId, Long requestId, String username) {
        founded(crewId, user(username));
        CrewRequest prosba = requests.findById(requestId)
            .filter(r -> r.getCrew().getId().equals(crewId) && r.isPending())
            .orElseThrow(() -> new NoSuchElementFoundException("crew request", requestId));
        notifications.crewRequestGone(prosba.getUser().getId(), crewId);
        prosba.decline(LocalDateTime.now(clock));
        return details(crewId, username);
    }

    /**
     * Odejscie. Zakladajacy przekazuje ekipe najdluzej obecnej osobie; gdy byl sam - ekipa znika razem z czatem. Zapis
     * na koncert zostaje (mozna isc samemu).
     */
    @Transactional
    public void leave(Long crewId, String username) {
        User viewer = user(username);
        CrewMember ja = members.findByCrewIdAndUserId(crewId, viewer.getId())
            .orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
        remove(ja);
    }

    /** Wyrzucenie czlonka - tylko zakladajacy (siebie nie wyrzuci, moze odejsc). */
    @Transactional
    public CrewDetailsResponse kick(Long crewId, String username, String memberUsername) {
        User founder = user(username);
        founded(crewId, founder);
        User kto = user(memberUsername);
        CrewMember m = members.findByCrewIdAndUserId(crewId, kto.getId())
            .filter(x -> x.getRole() != CrewRole.FOUNDER)
            .orElseThrow(() -> new NoSuchElementFoundException("crew member", memberUsername));
        remove(m);
        notifications.crewKicked(kto, m.getCrew());
        return details(crewId, username);
    }

    /** Wyjscie z ekipy: odpowiedzi na jej spotkania znikaja; zakladajacy przekazuje ekipe albo ja rozwiazuje. */
    private void remove(CrewMember m) {
        Crew crew = m.getCrew();
        LocalDateTime now = LocalDateTime.now(clock);
        meetings.leftCrew(m.getUser().getId(), crew.getId());
        members.delete(m);
        members.flush();
        if (m.getRole() == CrewRole.FOUNDER) {
            Optional<CrewMember> nastepca = members.ofCrews(List.of(crew.getId())).stream().findFirst();
            if (nastepca.isEmpty()) {
                dissolve(crew);
                return;
            }
            nastepca.get().setRole(CrewRole.FOUNDER);
            crew.handOver(nastepca.get().getUser(), now);
        }
    }

    /**
     * Rozwiazanie ekipy: wiersz kasowany zapytaniem (jak klan - {@code remove} scalalby odpieta encje), reszta
     * (czlonkowie, prosby, czat, spotkania, powiadomienia) kaskada w bazie.
     */
    private void dissolve(Crew crew) {
        crews.flush();
        crews.deleteRow(crew.getId());
    }

    /**
     * Usuniecie konta: ekipy zalozone przez te osobe przechodza na najdluzej obecna osobe (sama - znikaja), jej
     * wiadomosci na czatach ekip i prosby znikaja. Wolane przed skasowaniem wiersza konta.
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        // Po numerach i od nowa z bazy: rozwiazanie ekipy czysci kontekst, a przekazanie na odpietej encji by przepadlo
        for (Long id : members.ofUser(userId).stream().map(CrewMember::getId).toList()) {
            members.findById(id).ifPresent(this::remove);
        }
        messages.deleteBySenderId(userId);
        requests.deleteByUserId(userId);
    }

    /** Eksport danych: moje ekipy (z tymi, ktore zalozylem), prosby i wiadomosci na czatach ekip. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportOf(Long userId) {
        Map<String, Object> wynik = new java.util.LinkedHashMap<>();
        wynik.put("memberships", members.ofUser(userId).stream().map(m -> {
            Crew c = m.getCrew();
            Map<String, Object> w = new java.util.LinkedHashMap<>();
            w.put("crewId", c.getId());
            w.put("event", c.getEvent().getName());
            w.put("eventDate", c.getEvent().getStartDate());
            w.put("role", m.getRole());
            w.put("joinedAt", m.getJoinedAt());
            if (m.getRole() == CrewRole.FOUNDER) {
                w.put("title", c.getTitle());
                w.put("description", c.getDescription());
                w.put("capacity", c.getCapacity());
                w.put("joinPolicy", c.getJoinPolicy());
                w.put("departureCity", c.getDepartureCity());
            }
            return w;
        }).toList());
        wynik.put("requests", requests.ofUser(userId).stream().map(r -> {
            Map<String, Object> w = new java.util.LinkedHashMap<>();
            w.put("crewId", r.getCrew().getId());
            w.put("event", r.getCrew().getEvent().getName());
            w.put("message", r.getMessage());
            w.put("createdAt", r.getCreatedAt());
            w.put("declinedAt", r.getDeclinedAt());
            return w;
        }).toList());
        wynik.put("messages", messages.writtenBy(userId).stream().map(x -> {
            Map<String, Object> w = new java.util.LinkedHashMap<>();
            w.put("crewId", x.getCrew().getId());
            w.put("content", x.getContent());
            w.put("meetingId", x.getMeeting() == null ? null : x.getMeeting().getId());
            w.put("createdAt", x.getCreatedAt());
            return w;
        }).toList());
        return wynik;
    }

    /** Wejscie do ekipy: czlonkostwo, koniec innych prosb na ten koncert i zapis "Biore udzial". */
    private void enter(Crew crew, User user, CrewRole role, LocalDateTime now) {
        requests.deleteForEvent(user.getId(), crew.getEvent().getId());
        members.save(new CrewMember(crew, user, role, now, messages.maxId(crew.getId())));
        participations.participate(crew.getEvent().getId(), user.getUsername(), ParticipationStatus.GOING, null);
    }

    /* ------------------------------------------------------------------ */
    /*  Odczyt                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Strona ekipy: widzi ja kazdy zalogowany (tak jak karte pod wydarzeniem), poza osobami z blokada z zakladajacym;
     * sklad bez osob z moich blokad, prosby tylko dla zakladajacego.
     */
    @Transactional(readOnly = true)
    public CrewDetailsResponse details(Long crewId, String username) {
        User viewer = user(username);
        Crew crew = visible(crewId, viewer);
        MusicEvent event = crew.getEvent();
        Set<Long> hidden = blocks.hiddenFor(viewer.getId());
        List<CrewMember> sklad = members.ofCrews(List.of(crew.getId()));
        CrewCardResponse karta = cards(List.of(crew), viewer, hidden, event).get(0);
        boolean zalozyciel = crew.getFounder().getId().equals(viewer.getId());
        List<CrewRequestView> prosby = !zalozyciel ? List.of() : requests.pending(crew.getId()).stream()
            .filter(r -> !hidden.contains(r.getUser().getId()))
            .map(r -> new CrewRequestView(r.getId(), r.getUser().getUsername(), avatar(r.getUser()), r.getMessage(),
                r.getCreatedAt()))
            .toList();
        long nieprzeczytane = sklad.stream().filter(m -> m.getUser().getId().equals(viewer.getId())).findFirst()
            .map(m -> messages.unread(crew.getId(), viewer.getId(), m.getChatReadId() == null ? 0 : m.getChatReadId(),
                hiddenForQuery(hidden)))
            .orElse(0L);
        return new CrewDetailsResponse(karta, event.getId(), event.getName(), event.getStartDate(), event.getStartTime(),
            event.getVenueName(), event.getCity(), event.getThumbUrl() != null ? event.getThumbUrl() : event.getImageUrl(),
            past(event),
            sklad.stream().filter(m -> !hidden.contains(m.getUser().getId())).map(this::person).toList(),
            prosby, nieprzeczytane, chatOpen(event));
    }

    /** Moje ekipy na nadchodzace koncerty - z nieprzeczytanymi na czacie. */
    @Transactional(readOnly = true)
    public List<MyCrewResponse> mine(String username) {
        User viewer = user(username);
        List<Long> ukryci = hiddenForQuery(blocks.hiddenFor(viewer.getId()));
        List<CrewMember> moje = members.upcomingOf(viewer.getId(), importer.today());
        Map<Long, Long> ile = new HashMap<>();
        for (CrewMember m : members.ofCrews(moje.stream().map(x -> x.getCrew().getId()).toList())) {
            ile.merge(m.getCrew().getId(), 1L, Long::sum);
        }
        return moje.stream().map(m -> {
            Crew c = m.getCrew();
            MusicEvent e = c.getEvent();
            return new MyCrewResponse(c.getId(), c.getTitle(), ile.getOrDefault(c.getId(), 1L).intValue(), c.getCapacity(),
                messages.unread(c.getId(), viewer.getId(), m.getChatReadId() == null ? 0 : m.getChatReadId(), ukryci),
                e.getId(), e.getName(), e.getStartDate(), e.getStartTime(), e.getVenueName(), e.getCity(),
                e.getThumbUrl() != null ? e.getThumbUrl() : e.getImageUrl());
        }).toList();
    }

    /** Ile ekip ma kazde z wydarzen - do kart na liscie wydarzen ("3 ekipy"). */
    @Transactional(readOnly = true)
    public Map<Long, Long> countByEvents(java.util.Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return crews.countByEvents(eventIds).stream()
            .collect(Collectors.toMap(CrewRepository.EventCountRow::getEventId, CrewRepository.EventCountRow::getTotal));
    }

    private CrewCardResponse card(Long crewId, User viewer) {
        Crew crew = crews.findById(crewId).orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
        return cards(List.of(crew), viewer, blocks.hiddenFor(viewer.getId()), crew.getEvent()).get(0);
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    /** Ekipa, ktora ogladajacy moze zobaczyc - blokada z zakladajacym = jakby jej nie bylo. */
    Crew visible(Long crewId, User viewer) {
        Crew crew = crews.findById(crewId).orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
        if (blocks.eitherWay(viewer.getId(), crew.getFounder().getId())) {
            throw new NoSuchElementFoundException("crew", crewId);
        }
        return crew;
    }

    /** Czlonek ekipy - czat i spotkania; inni dostaja 404. */
    CrewMember requireMember(Long crewId, User viewer) {
        return members.findByCrewIdAndUserId(crewId, viewer.getId())
            .orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
    }

    private Crew founded(Long crewId, User founder) {
        Crew crew = crews.findById(crewId).orElseThrow(() -> new NoSuchElementFoundException("crew", crewId));
        if (!crew.getFounder().getId().equals(founder.getId())) {
            if (members.findByCrewIdAndUserId(crewId, founder.getId()).isEmpty()) {
                throw new NoSuchElementFoundException("crew", crewId);
            }
            throw OperationNotAllowedException.crewNotFounder();
        }
        return crew;
    }

    private MusicEvent openEvent(Long eventId) {
        MusicEvent event = events.findById(eventId).orElseThrow(() -> new NoSuchElementFoundException("event", eventId));
        if (past(event)) {
            throw OperationNotAllowedException.eventPast();
        }
        if (event.isWithdrawn()) {
            throw OperationNotAllowedException.eventWithdrawn();
        }
        return event;
    }

    private boolean past(MusicEvent event) {
        return event.getStartDate().isBefore(importer.today());
    }

    /** Czat ekipy: do dwoch dni po koncercie mozna pisac, potem tylko czytac. */
    boolean chatOpen(MusicEvent event) {
        LocalDate dzis = importer.today();
        return !event.getStartDate().plusDays(CZAT_PO_KONCERCIE_DNI).isBefore(dzis);
    }

    private CrewPersonView person(CrewMember m) {
        return new CrewPersonView(m.getUser().getUsername(), avatar(m.getUser()), m.getRole());
    }

    private static String avatar(User u) {
        return u.getAvatarFileName() == null ? null : PostMapper.UPLOADS_PATH + u.getAvatarFileName();
    }

    private static List<Long> hiddenForQuery(Set<Long> hidden) {
        return hidden.isEmpty() ? List.of(-1L) : List.copyOf(hidden);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
