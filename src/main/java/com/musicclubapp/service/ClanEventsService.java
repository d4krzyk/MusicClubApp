package com.musicclubapp.service;

import com.musicclubapp.dto.ClanEventResponse;
import com.musicclubapp.dto.PersonCard;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.EventCountRow;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Klan idzie na koncert": nadchodzace wydarzenia, na ktore zapisali sie czlonkowie klanu.
 *
 * <p>Korzysta z zapisow na wydarzenia, wiec obowiazuja te same zasady co przy liscie uczestnikow:
 * kto zaznaczyl "nie pokazuj mnie", liczy sie do licznika bez nazwy konta (sam widzi siebie zawsze),
 * a osoby z blokad ogladajacego w ogole nie wystepuja. Post "kto jedzie?" pod wydarzeniem to zwykly
 * post klanu z odnosnikiem do wydarzenia.</p>
 */
@Service
public class ClanEventsService {

    /** Tyle wydarzen najwyzej na liscie. */
    static final int MAKSIMUM = 30;

    private final ClanService clans;
    private final ClanMemberRepository members;
    private final EventParticipationRepository participations;
    private final PostRepository posts;
    private final UserRepository users;
    private final BlockService blocks;
    private final Clock clock;

    public ClanEventsService(ClanService clans, ClanMemberRepository members,
                             EventParticipationRepository participations, PostRepository posts,
                             UserRepository users, BlockService blocks, Clock clock) {
        this.clans = clans;
        this.members = members;
        this.participations = participations;
        this.posts = posts;
        this.users = users;
        this.blocks = blocks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClanEventResponse> upcoming(Long clanId, String viewerName) {
        User viewer = users.findByUsername(viewerName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", viewerName));
        Clan clan = clans.requireAccess(viewer, clanId);

        List<Long> ukryci = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId())
            ? List.of() : blocks.hiddenForQuery(viewer.getId());
        List<Long> ids = members.ofClan(clanId).stream()
            .map(m -> m.getUser().getId())
            .filter(id -> !ukryci.contains(id))
            .toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        LocalDate dzis = LocalDate.now(clock.withZone(EventImportService.STREFA));
        Map<Long, List<EventParticipation>> wydarzenia = new LinkedHashMap<>();
        for (EventParticipation p : participations.upcomingOf(ids, dzis)) {
            wydarzenia.computeIfAbsent(p.getEvent().getId(), k -> new ArrayList<>()).add(p);
        }
        List<Long> numery = wydarzenia.keySet().stream().limit(MAKSIMUM).toList();
        Map<Long, Long> zapytania = posts.clanPostsUnderEvents(clanId, numery).stream()
            .collect(Collectors.toMap(EventCountRow::getEventId, EventCountRow::getTotal));

        return numery.stream().map(id -> {
            List<EventParticipation> zapisy = wydarzenia.get(id);
            MusicEvent e = zapisy.get(0).getEvent();
            List<PersonCard> ida = new ArrayList<>();
            int idaLacznie = 0;
            int zainteresowani = 0;
            ParticipationStatus moj = null;
            for (EventParticipation p : zapisy) {
                boolean ja = p.getUser().getId().equals(viewer.getId());
                if (ja) {
                    moj = p.getStatus();
                }
                if (p.getStatus() == ParticipationStatus.GOING) {
                    idaLacznie++;
                    if (!p.isHidden() || ja) {
                        ida.add(new PersonCard(p.getUser().getUsername(), ClanMapper.avatarUrl(p.getUser())));
                    }
                } else {
                    zainteresowani++;
                }
            }
            return new ClanEventResponse(e.getId(), e.getName(), e.getStartDate(), e.getStartTime(), e.getCity(),
                e.getVenueName(), e.getThumbUrl() != null ? e.getThumbUrl() : e.getImageUrl(),
                ida, idaLacznie, zainteresowani, moj, zapytania.get(id));
        }).toList();
    }
}
