package com.musicclubapp.service;

import com.musicclubapp.dto.ClanActivityResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranking aktywnosci w klanie: punkty i poziomy z {@link ClanActivityCounter}. "Tydzien" to ostatnie
 * 7 dni, nie tydzien kalendarzowy; poziom wynika z punktow z calego czasu.
 */
@Service
public class ClanActivityService {

    /** Cel tygodnia klanu: tyle punktow na czlonka (najmniej 40 punktow). */
    static final long CEL_NA_OSOBE = 20;
    static final long CEL_MIN = 40;

    private final ClanActivityCounter counter;
    private final ClanMemberRepository members;
    private final UserRepository users;
    private final ClanService clans;
    private final BlockService blocks;
    private final Clock clock;

    public ClanActivityService(ClanActivityCounter counter, ClanMemberRepository members, UserRepository users,
                               ClanService clans, BlockService blocks, Clock clock) {
        this.counter = counter;
        this.members = members;
        this.users = users;
        this.clans = clans;
        this.blocks = blocks;
        this.clock = clock;
    }

    /** Ranking czlonkow za tydzien (WEEK) albo za caly czas (ALL), z podsumowaniem tygodnia klanu. */
    @Transactional(readOnly = true)
    public ClanActivityResponse activity(Long clanId, String viewerName, String period) {
        User viewer = users.findByUsername(viewerName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", viewerName));
        Clan clan = clans.requireAccess(viewer, clanId);
        boolean admin = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId());
        Set<Long> ukryci = admin ? Set.of() : blocks.hiddenFor(viewer.getId());
        boolean wszystko = "ALL".equalsIgnoreCase(period);

        Map<Long, ClanActivityCounter.Counts> tydzien = counter.counts(clanId, LocalDateTime.now(clock).minusDays(7));
        Map<Long, ClanActivityCounter.Counts> razem = counter.allTime(clanId);
        List<ClanMember> czlonkowie = members.ofClan(clanId).stream()
            .filter(m -> !ukryci.contains(m.getUser().getId())).toList();

        List<ClanActivityResponse.Row> wiersze = czlonkowie.stream().map(m -> {
            Long id = m.getUser().getId();
            ClanActivityCounter.Counts okres = (wszystko ? razem : tydzien).getOrDefault(id, ClanActivityCounter.Counts.ZERO);
            long wszystkie = razem.getOrDefault(id, ClanActivityCounter.Counts.ZERO).points();
            int poziom = ClanActivityCounter.level(wszystkie);
            Long nastepny = poziom + 1 < ClanActivityCounter.POZIOMY.length ? ClanActivityCounter.POZIOMY[poziom + 1] : null;
            return new ClanActivityResponse.Row(m.getUser().getUsername(), ClanMapper.avatarUrl(m.getUser()),
                okres.points(), okres.messages(), okres.posts(), okres.tracks(), okres.votes(), okres.reactions(),
                poziom, wszystkie, nastepny, m.getUser().getId().equals(viewer.getId()));
        }).sorted(Comparator.comparingLong(ClanActivityResponse.Row::points).reversed()
            .thenComparing(Comparator.comparingLong(ClanActivityResponse.Row::allTimePoints).reversed())
            .thenComparing(ClanActivityResponse.Row::username, String.CASE_INSENSITIVE_ORDER)).toList();

        long pkt = 0;
        long wiad = 0;
        long posty = 0;
        long utwory = 0;
        long glosy = 0;
        long reakcje = 0;
        for (ClanMember m : czlonkowie) {
            ClanActivityCounter.Counts c = tydzien.getOrDefault(m.getUser().getId(), ClanActivityCounter.Counts.ZERO);
            pkt += c.points();
            wiad += c.messages();
            posty += c.posts();
            utwory += c.tracks();
            glosy += c.votes();
            reakcje += c.reactions();
        }
        long cel = Math.max(CEL_MIN, CEL_NA_OSOBE * czlonkowie.size());
        return new ClanActivityResponse(wszystko ? "ALL" : "WEEK",
            new ClanActivityResponse.Summary(pkt, cel, wiad, posty, utwory, glosy, reakcje), wiersze);
    }
}
