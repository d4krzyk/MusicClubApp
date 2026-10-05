package com.musicclubapp.service;

import com.musicclubapp.dto.AttendeeResponse;
import com.musicclubapp.dto.ParticipationResponse;
import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.CrewMemberRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.ParticipationCountRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** "Zainteresowany", "Biore udzial", rezygnacja i lista uczestnikow. */
@Service
public class EventParticipationService {

    private final EventParticipationRepository participations;
    private final MusicEventRepository events;
    private final UserRepository users;
    private final EventImportService importer;
    private final Clock clock;
    private final BlockService blocks;
    private final CrewMemberRepository crewMembers;
    private final EventReminderService reminders;

    public EventParticipationService(EventParticipationRepository participations,
                                     MusicEventRepository events,
                                     UserRepository users,
                                     EventImportService importer,
                                     Clock clock,
                                     BlockService blocks,
                                     CrewMemberRepository crewMembers,
                                     EventReminderService reminders) {
        this.reminders = reminders;
        this.crewMembers = crewMembers;
        this.blocks = blocks;
        this.participations = participations;
        this.events = events;
        this.users = users;
        this.importer = importer;
        this.clock = clock;
    }

    /**
     * Zapisuje na wydarzenie albo zmienia zapis (zainteresowany <-> ide,
     * pokaz mnie <-> ukryj mnie).
     *
     * Na minione i wycofane zapisac sie nie mozna. Zrezygnowac - owszem.
     */
    @Transactional
    public ParticipationResponse participate(Long eventId, String username,
                                             ParticipationStatus status, Boolean hidden) {
        MusicEvent event = events.findById(eventId)
            .orElseThrow(() -> new NoSuchElementFoundException("event", eventId));

        if (event.getStartDate().isBefore(importer.today())) {
            throw OperationNotAllowedException.eventPast();
        }
        if (event.isWithdrawn()) {
            throw OperationNotAllowedException.eventWithdrawn();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<EventParticipation> mine = participations.findMine(eventId, username);
        // Kto jedzie z ekipa, ten idzie - "tylko zainteresowany" zostawilby ekipe z kims, kto nie jedzie
        if (status != ParticipationStatus.GOING && inCrew(eventId, username)) {
            throw OperationNotAllowedException.crewLeaveFirst();
        }
        /*
         * Bez podanego "ukryj mnie": przy zmianie zostaje jak bylo, przy nowym
         * zapisie - domyslne z ustawien prywatnosci.
         */
        if (mine.isPresent()) {
            mine.get().change(status, hidden != null ? hidden : mine.get().isHidden(), now);
        } else {
            User user = users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementFoundException("user", username));
            boolean ukryty = hidden != null ? hidden : user.isHideOnAttendeeLists();
            EventParticipation nowy = new EventParticipation(event, user, status, ukryty, now);
            nowy.markReminded(reminders.progPrzyZapisie(event.getStartDate()));
            participations.save(nowy);
        }
        return summary(eventId, username);
    }

    /** Rezygnacja - zapis znika, jakby go nigdy nie bylo. */
    @Transactional
    public ParticipationResponse cancel(Long eventId, String username) {
        if (!events.existsById(eventId)) {
            throw new NoSuchElementFoundException("event", eventId);
        }
        if (inCrew(eventId, username)) {
            throw OperationNotAllowedException.crewLeaveFirst();
        }
        participations.findMine(eventId, username).ifPresent(p -> {
            reminders.cancelled(p.getUser().getId(), eventId);
            participations.delete(p);
        });
        participations.flush();
        return summary(eventId, username);
    }

    private boolean inCrew(Long eventId, String username) {
        return users.findByUsername(username)
            .map(u -> crewMembers.existsByEventIdAndUserId(eventId, u.getId()))
            .orElse(false);
    }

    /** Moj zapis i liczniki jednego wydarzenia. */
    @Transactional(readOnly = true)
    public ParticipationResponse summary(Long eventId, String username) {
        long going = 0;
        long interested = 0;
        for (ParticipationCountRow row : participations.countByStatus(List.of(eventId))) {
            if (row.getStatus() == ParticipationStatus.GOING) {
                going = row.getTotal();
            } else {
                interested = row.getTotal();
            }
        }
        Optional<EventParticipation> mine = participations.findMine(eventId, username);
        return new ParticipationResponse(
            mine.map(EventParticipation::getStatus).orElse(null),
            mine.map(EventParticipation::isHidden).orElse(false),
            going,
            interested,
            participations.countByEventIdAndStatusAndHiddenTrue(eventId, ParticipationStatus.GOING));
    }

    /**
     * Kto idzie: wszyscy zalogowani widza liste, poza osobami, ktore
     * zaznaczyly "nie pokazuj mnie". Siebie widze zawsze - z dopiskiem, ze
     * inni mnie nie widza, jesli tak wybralem.
     */
    @Transactional(readOnly = true)
    public Page<AttendeeResponse> attendees(Long eventId, String viewer, Pageable pageable) {
        if (!events.existsById(eventId)) {
            throw new NoSuchElementFoundException("event", eventId);
        }
        Set<Long> friendIds = new HashSet<>(users.friendIdsOf(viewer));
        // Zablokowani przez ogladajacego i blokujacy go - poza lista (licznik ich liczy)
        Set<Long> ukryci = users.findByUsername(viewer).map(u -> blocks.hiddenFor(u.getId())).orElse(Set.of());

        /* Pusta lista w "IN (...)" to blad skladni w czesci baz - podstawiamy identyfikator, ktorego nie ma. */
        List<Long> doZapytania = friendIds.isEmpty() ? List.of(-1L) : List.copyOf(friendIds);

        return participations.attendees(eventId, viewer, doZapytania,
                ukryci.isEmpty() ? List.of(-1L) : List.copyOf(ukryci), pageable)
            .map(p -> new AttendeeResponse(
                p.getUser().getUsername(),
                avatarUrl(p.getUser()),
                friendIds.contains(p.getUser().getId()),
                p.getUser().getUsername().equals(viewer),
                p.isHidden()));
    }

    /** Przy usuwaniu konta - zapisy znikaja razem z osoba. */
    @Transactional
    public void deleteAllOf(Long userId) {
        participations.deleteByUserId(userId);
    }

    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }
}
