package com.musicclubapp.service;

import com.musicclubapp.dto.MeetingRequest;
import com.musicclubapp.dto.MeetingResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.Meeting;
import com.musicclubapp.entity.MeetingAttendee;
import com.musicclubapp.entity.MeetingStatus;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.MeetingAttendeeRepository;
import com.musicclubapp.repository.MeetingRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spotkania wysylane na czacie: zakladanie (razem z wiadomoscia robia to {@link MessageService} i
 * {@link ClanChatService}), odpowiedzi "bede" / "nie dam rady", odwolanie i to, jak spotkanie widzi ogladajacy.
 *
 * <p>Widzi je ten, kto widzi wiadomosc: strony rozmowy albo czlonkowie klanu (i administrator aplikacji przy klanie -
 * czyta, nie odpowiada). Odpowiadac moga strony rozmowy, ktore nadal sa znajomymi (blokada zrywa znajomosc), i
 * czlonkowie klanu. Obcy dostaje 404 - spotkanie nie zdradza, ze istnieje.</p>
 */
@Service
public class MeetingService {

    /** Ile nadchodzacych spotkan moze miec zalozone jedna osoba. */
    public static final int MAX_OPEN = 20;
    /** Dozwolone przypomnienia (minuty przed poczatkiem) - te same co w formularzu. */
    public static final Set<Integer> REMIND_OPTIONS = Set.of(0, 15, 30, 60, 120, 1440);
    /** Ile loginow potwierdzonych idzie w odpowiedzi - reszta jest w liczniku. */
    static final int NAMES = 8;

    private final MeetingRepository meetings;
    private final MeetingAttendeeRepository attendees;
    private final UserRepository users;
    private final BlockService blocks;
    private final NotificationService notifications;
    private final Clock clock;

    public MeetingService(MeetingRepository meetings, MeetingAttendeeRepository attendees, UserRepository users,
                          BlockService blocks, NotificationService notifications, Clock clock) {
        this.meetings = meetings;
        this.attendees = attendees;
        this.users = users;
        this.blocks = blocks;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Nowe spotkanie (wiadomosc dokleja wolajacy): sprawdza zasady, zapisuje je i zakladajacego jako "bede" -
     * kto proponuje spotkanie, ten na nim jest i tez dostaje przypomnienie.
     */
    @Transactional
    public Meeting create(User creator, User partner, Clan clan, MeetingRequest request) {
        Instant now = Instant.now(clock);
        check(request, now);
        if (meetings.countOpenBy(creator.getId(), now) >= MAX_OPEN) {
            throw OperationNotAllowedException.meetingLimit(MAX_OPEN);
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        Meeting meeting = meetings.save(new Meeting(creator, partner, clan, request.place().strip(), note,
            round(request.latitude()), round(request.longitude()), request.startsAt(), request.endsAt(),
            request.remindMinutes(), now));
        attendees.save(new MeetingAttendee(meeting, creator, MeetingStatus.GOING, now));
        return meeting;
    }

    /** Zasady czasu, punktu i przypomnienia - te same pilnuje formularz w przegladarce. */
    static void check(MeetingRequest r, Instant now) {
        if ((r.latitude() == null) != (r.longitude() == null)) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.point");
        }
        if (!REMIND_OPTIONS.contains(r.remindMinutes())) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.remind");
        }
        if (r.startsAt().isBefore(now.minus(Meeting.PAST_TOLERANCE))) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.past");
        }
        if (r.startsAt().isAfter(now.plus(Meeting.MAX_AHEAD))) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.tooFar");
        }
        if (!r.endsAt().isAfter(r.startsAt())) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.endBeforeStart");
        }
        if (Duration.between(r.startsAt(), r.endsAt()).compareTo(Meeting.MAX_DURATION) > 0) {
            throw OperationNotAllowedException.meetingInvalid("error.meeting.tooLong");
        }
    }

    /** Szesc miejsc po przecinku to ok. 10 cm - dokladniej nie ma sensu, a krocej sie zapisuje. */
    private static Double round(Double degrees) {
        return degrees == null ? null : Math.round(degrees * 1e6) / 1e6;
    }

    /* ------------------------------------------------------------------ */
    /*  Odpowiedzi i odwolanie                                             */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public MeetingResponse get(Long id, String username) {
        User viewer = user(username);
        Meeting meeting = visible(id, viewer);
        return toResponse(meeting, viewer, canRespond(meeting, viewer));
    }

    /** "Bede", "nie dam rady" albo cofniecie odpowiedzi ({@code null}). */
    @Transactional
    public MeetingResponse respond(Long id, String username, MeetingStatus status) {
        User viewer = user(username);
        Meeting meeting = visible(id, viewer);
        if (!canRespond(meeting, viewer)) {
            throw OperationNotAllowedException.meetingCannotRespond();
        }
        Instant now = Instant.now(clock);
        if (!meeting.isOpen(now)) {
            throw OperationNotAllowedException.meetingClosed();
        }
        Optional<MeetingAttendee> mine = attendees.findByMeetingIdAndUserId(meeting.getId(), viewer.getId());
        if (status == null) {
            mine.ifPresent(attendees::delete);
        } else if (mine.isPresent()) {
            mine.get().setStatus(status, now);
        } else {
            attendees.save(new MeetingAttendee(meeting, viewer, status, now));
        }
        if (status != MeetingStatus.GOING) {
            // Przypomnienie w dzwonku przestaje byc prawdziwe
            notifications.meetingRemindersGone(viewer.getId(), meeting.getId());
        }
        meeting.touch(now);
        attendees.flush();
        return toResponse(meeting, viewer, true);
    }

    /** Odwolanie - tylko zakladajacy. Potwierdzeni dostaja powiadomienie (w dzwonku i na telefonie). */
    @Transactional
    public MeetingResponse cancel(Long id, String username) {
        User viewer = user(username);
        Meeting meeting = visible(id, viewer);
        if (!meeting.getCreator().getId().equals(viewer.getId())) {
            throw OperationNotAllowedException.meetingNotCreator();
        }
        Instant now = Instant.now(clock);
        if (!meeting.isOpen(now)) {
            throw OperationNotAllowedException.meetingClosed();
        }
        meeting.cancel(now);
        notifications.meetingNotificationsGone(meeting.getId());
        for (MeetingAttendee a : attendees.going(meeting.getId())) {
            User who = a.getUser();
            if (!who.getId().equals(viewer.getId()) && stillIn(meeting, who)
                && !blocks.eitherWay(viewer.getId(), who.getId())) {
                notifications.meetingCancelled(who, viewer, meeting);
            }
        }
        return toResponse(meeting, viewer, canRespond(meeting, viewer));
    }

    /**
     * Usuniecie wiadomosci, ktora niesie spotkanie: spotkanie znika razem z nia (odpowiedzi i powiadomienia kaskada
     * w bazie). Wolajacy odpina je od wiadomosci przed wywolaniem.
     */
    @Transactional
    public void deleteWithMessage(Meeting meeting) {
        meetings.delete(meeting);
    }

    /** Rozmowa skasowana przez obie strony - jej spotkan nikt juz nie zobaczy. */
    @Transactional
    public void deleteOrphans() {
        meetings.deleteOrphans();
    }

    /** Usuniecie konta: spotkania zalozone przez osobe i spotkania z jej rozmow. */
    @Transactional
    public void deleteAllOf(Long userId) {
        meetings.deleteAllOf(userId);
    }

    /** Odejscie z klanu: odpowiedzi na spotkania klanu znikaja (bez przypomnien dla kogos spoza klanu). */
    @Transactional
    public void leftClan(Long userId, Long clanId) {
        attendees.deleteInClan(userId, clanId);
    }

    /* ------------------------------------------------------------------ */
    /*  Zmiany do odswiezania czatu                                        */
    /* ------------------------------------------------------------------ */

    /** Spotkania rozmowy zmienione po podanej chwili, juz w postaci dla ogladajacego. */
    @Transactional(readOnly = true)
    public List<MeetingResponse> changedInConversation(User viewer, User partner, Instant since, boolean canWrite) {
        List<Meeting> list = meetings.changedInConversation(viewer.getId(), partner.getId(), since);
        return new ArrayList<>(toResponses(list, viewer, canWrite).values());
    }

    /** To samo dla czatu klanu. */
    @Transactional(readOnly = true)
    public List<MeetingResponse> changedInClan(Long clanId, User viewer, Instant since, boolean member) {
        List<Meeting> list = meetings.changedInClan(clanId, since);
        return new ArrayList<>(toResponses(list, viewer, member).values());
    }

    /* ------------------------------------------------------------------ */
    /*  Widok                                                              */
    /* ------------------------------------------------------------------ */

    public MeetingResponse toResponse(Meeting meeting, User viewer, boolean canWrite) {
        return toResponses(List.of(meeting), viewer, canWrite).get(meeting.getId());
    }

    /**
     * Spotkania tak, jak widzi je ogladajacy - jednym zapytaniem o odpowiedzi. {@code canWrite}: czy ogladajacy moze
     * pisac w tym miejscu (znajomy w rozmowie, czlonek klanu) - od tego zalezy, czy moze odpowiadac.
     */
    public Map<Long, MeetingResponse> toResponses(Collection<Meeting> list, User viewer, boolean canWrite) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Set<Long> hidden = blocks.hiddenFor(viewer.getId());
        Map<Long, List<MeetingAttendee>> byMeeting = attendees
            .forMeetings(list.stream().map(Meeting::getId).toList()).stream()
            .collect(Collectors.groupingBy(a -> a.getMeeting().getId()));
        Instant now = Instant.now(clock);
        Map<Long, MeetingResponse> result = new HashMap<>();
        for (Meeting m : list) {
            List<MeetingAttendee> all = byMeeting.getOrDefault(m.getId(), List.of());
            MeetingStatus my = all.stream().filter(a -> a.getUser().getId().equals(viewer.getId()))
                .map(MeetingAttendee::getStatus).findFirst().orElse(null);
            List<MeetingAttendee> seen = all.stream().filter(a -> !hidden.contains(a.getUser().getId())).toList();
            List<String> going = seen.stream().filter(a -> a.getStatus() == MeetingStatus.GOING)
                // zakladajacy pierwszy, potem w kolejnosci odpowiedzi
                .sorted((a, b) -> Boolean.compare(!isCreator(m, a), !isCreator(m, b)))
                .map(a -> a.getUser().getUsername()).toList();
            int notGoing = (int) seen.stream().filter(a -> a.getStatus() == MeetingStatus.NOT_GOING).count();
            result.put(m.getId(), new MeetingResponse(m.getId(), m.getCreator().getUsername(),
                m.getCreator().getId().equals(viewer.getId()), m.getPlace(), m.getNote(), m.getLatitude(),
                m.getLongitude(), m.getStartsAt(), m.getEndsAt(), m.getRemindMinutes(), m.isCancelled(), my,
                going.size(), notGoing, going.subList(0, Math.min(NAMES, going.size())),
                canWrite && m.isOpen(now), m.getUpdatedAt()));
        }
        return result;
    }

    private static boolean isCreator(Meeting m, MeetingAttendee a) {
        return a.getUser().getId().equals(m.getCreator().getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Dostep                                                             */
    /* ------------------------------------------------------------------ */

    /** Spotkanie, ktore ogladajacy moze widziec - inaczej 404. */
    private Meeting visible(Long id, User viewer) {
        Meeting meeting = meetings.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("meeting", id));
        boolean sees = meeting.getClan() != null
            ? meeting.getClan().hasMember(viewer.getId()) || viewer.getRole() == Role.ADMIN
            : meeting.isBetween(viewer.getId());
        if (!sees) {
            throw new NoSuchElementFoundException("meeting", id);
        }
        return meeting;
    }

    /** Odpowiadac moze strona rozmowy, ktora nadal jest znajomoscia, albo czlonek klanu (nie administrator z zewnatrz). */
    private boolean canRespond(Meeting meeting, User viewer) {
        return stillIn(meeting, viewer);
    }

    /** Czy osoba nadal "nalezy" do spotkania: czlonek klanu albo znajomy drugiej strony rozmowy. */
    boolean stillIn(Meeting meeting, User who) {
        if (meeting.getClan() != null) {
            return meeting.getClan().hasMember(who.getId());
        }
        return meeting.isBetween(who.getId())
            && users.areFriends(meeting.getCreator().getUsername(), meeting.getPartner().getUsername());
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
