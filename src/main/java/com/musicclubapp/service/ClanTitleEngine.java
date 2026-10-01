package com.musicclubapp.service;

import com.musicclubapp.dto.ClanTitleBadge;
import com.musicclubapp.dto.ClanTitleResponse;
import com.musicclubapp.entity.ClanActivityMetric;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanMemberTitle;
import com.musicclubapp.entity.ClanTitle;
import com.musicclubapp.entity.ClanTitleMode;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanTitleRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sklada tytuly czlonkow klanu: nadane przez zarzad i wziete samodzielnie (maja wiersz w bazie)
 * oraz przyznawane automatycznie - te wynikaja z aktywnosci ({@link ClanActivityCounter}) i sa
 * liczone na biezaco, wiec znikaja, gdy aktywnosc przestanie je uzasadniac (np. po skasowaniu
 * wiadomosci). Bez zaleznosci od {@link ClanService}, ktory z niej korzysta.
 */
@Component
public class ClanTitleEngine {

    /** Tyle nadanych i wzietych tytulow najwyzej ma jedna osoba (automatyczne sie do tego nie licza). */
    public static final int MAX_PER_MEMBER = 5;

    /** Tyle tytulow moze sobie wziac sama. */
    public static final int MAX_SELF = 2;

    private final ClanTitleRepository titles;
    private final ClanMemberTitleRepository memberTitles;
    private final ClanActivityCounter counter;
    private final Clock clock;

    public ClanTitleEngine(ClanTitleRepository titles, ClanMemberTitleRepository memberTitles,
                           ClanActivityCounter counter, Clock clock) {
        this.titles = titles;
        this.memberTitles = memberTitles;
        this.counter = counter;
        this.clock = clock;
    }

    static ClanTitleBadge badge(ClanTitle t) {
        return new ClanTitleBadge(t.getId(), t.getName(), t.getColor().name(), t.getColor().hex(), t.getMode());
    }

    /** Tytuly kazdej osoby z klanu (klucz: numer konta); osoby bez tytulow nie wystepuja. */
    public Map<Long, List<ClanTitleBadge>> badges(Long clanId, List<ClanMember> members) {
        List<ClanTitle> definicje = titles.ofClan(clanId);
        if (definicje.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ClanTitle>> wg = new HashMap<>();
        for (ClanMemberTitle mt : memberTitles.ofClan(clanId)) {
            wg.computeIfAbsent(mt.getUser().getId(), k -> new ArrayList<>()).add(mt.getTitle());
        }
        List<ClanTitle> auto = definicje.stream().filter(t -> t.getMode() == ClanTitleMode.AUTO).toList();
        if (!auto.isEmpty()) {
            Map<Long, ClanActivityCounter.Counts> liczniki = counter.allTime(clanId);
            LocalDateTime teraz = LocalDateTime.now(clock);
            for (ClanMember m : members) {
                ClanActivityCounter.Counts c = liczniki.getOrDefault(m.getUser().getId(), ClanActivityCounter.Counts.ZERO);
                long dni = ChronoUnit.DAYS.between(m.getJoinedAt(), teraz);
                for (ClanTitle t : auto) {
                    if (value(t.getMetric(), c, dni) >= t.getThreshold()) {
                        wg.computeIfAbsent(m.getUser().getId(), k -> new ArrayList<>()).add(t);
                    }
                }
            }
        }
        Map<Long, List<ClanTitleBadge>> wynik = new HashMap<>();
        wg.forEach((id, lista) -> wynik.put(id, lista.stream()
            .sorted(Comparator.comparing(ClanTitle::getId)).map(ClanTitleEngine::badge).toList()));
        return wynik;
    }

    static long value(ClanActivityMetric metric, ClanActivityCounter.Counts c, long days) {
        return switch (metric) {
            case MESSAGES -> c.messages();
            case POSTS -> c.posts();
            case TRACKS -> c.tracks();
            case VOTES -> c.votes();
            case REACTIONS -> c.reactions();
            case DAYS -> days;
        };
    }

    /** Tytuly zdefiniowane w klanie, z liczba osob, ktore je maja, i tym, co moze z nimi zrobic ogladajacy. */
    public List<ClanTitleResponse> definitions(Long clanId, User viewer, boolean viewerIsMember,
                                               Map<Long, List<ClanTitleBadge>> badges) {
        List<ClanTitleBadge> moje = badges.getOrDefault(viewer.getId(), List.of());
        long mojeWybrane = viewerIsMember ? memberTitles.countSelfClaimed(clanId, viewer.getId()) : 0;
        long mojeRazem = viewerIsMember ? memberTitles.countOf(clanId, viewer.getId()) : 0;
        List<ClanTitleResponse> wynik = new ArrayList<>();
        for (ClanTitle t : titles.ofClan(clanId)) {
            int posiadacze = (int) badges.values().stream()
                .filter(l -> l.stream().anyMatch(b -> b.id().equals(t.getId()))).count();
            boolean mam = moje.stream().anyMatch(b -> b.id().equals(t.getId()));
            boolean moznaWziac = viewerIsMember && t.getMode() == ClanTitleMode.SELF && !mam
                && mojeWybrane < MAX_SELF && mojeRazem < MAX_PER_MEMBER;
            wynik.add(new ClanTitleResponse(t.getId(), t.getName(), t.getColor().name(), t.getColor().hex(),
                t.getMode(), t.getMetric(), t.getThreshold(), posiadacze, mam, moznaWziac));
        }
        return wynik;
    }
}
