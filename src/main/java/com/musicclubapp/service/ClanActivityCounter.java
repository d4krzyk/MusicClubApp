package com.musicclubapp.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liczniki aktywnosci czlonkow klanu z istniejacych danych (czat, posty, utwor tygodnia, ankiety,
 * reakcje) - nic nie jest osobno zapisywane. Osobna klasa bez zaleznosci od {@link ClanService},
 * bo korzystaja z niej i strona klanu (tytuly), i ranking.
 *
 * <p>Punkty: wiadomosc 1, post 4, propozycja utworu 3, glos (na utwor albo w ankiecie) 1,
 * reakcja 1.</p>
 */
@Component
public class ClanActivityCounter {

    static final int PKT_WIADOMOSC = 1;
    static final int PKT_POST = 4;
    static final int PKT_UTWOR = 3;
    static final int PKT_GLOS = 1;
    static final int PKT_REAKCJA = 1;

    /** Od ilu punktow (z calego czasu) zaczyna sie kolejny poziom: 0-4. */
    static final long[] POZIOMY = {0, 25, 100, 300, 800};

    private static final LocalDateTime EPOKA = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** Liczniki jednej osoby. */
    public record Counts(long messages, long posts, long tracks, long votes, long reactions) {

        static final Counts ZERO = new Counts(0, 0, 0, 0, 0);

        public long points() {
            return messages * PKT_WIADOMOSC + posts * PKT_POST + tracks * PKT_UTWOR
                + votes * PKT_GLOS + reactions * PKT_REAKCJA;
        }
    }

    private final EntityManager em;

    public ClanActivityCounter(EntityManager em) {
        this.em = em;
    }

    /** Liczniki wszystkich osob w klanie od podanej chwili (osoby bez zadnej aktywnosci nie wystepuja). */
    Map<Long, Counts> counts(Long clanId, LocalDateTime since) {
        Map<Long, long[]> wynik = new HashMap<>();
        zbierz(wynik, 0, "SELECT m.sender.id, COUNT(m) FROM ClanMessage m "
            + "WHERE m.clan.id = :c AND m.createdAt >= :s GROUP BY m.sender.id", clanId, since);
        zbierz(wynik, 1, "SELECT p.author.id, COUNT(p) FROM Post p "
            + "WHERE p.clan.id = :c AND p.createdAt >= :s GROUP BY p.author.id", clanId, since);
        zbierz(wynik, 2, "SELECT t.proposer.id, COUNT(t) FROM ClanTrack t "
            + "WHERE t.clan.id = :c AND t.createdAt >= :s GROUP BY t.proposer.id", clanId, since);
        zbierz(wynik, 3, "SELECT v.user.id, COUNT(v) FROM ClanTrackVote v "
            + "WHERE v.track.clan.id = :c AND v.createdAt >= :s GROUP BY v.user.id", clanId, since);
        zbierz(wynik, 3, "SELECT v.user.id, COUNT(v) FROM ClanPollVote v "
            + "WHERE v.poll.clan.id = :c AND v.createdAt >= :s GROUP BY v.user.id", clanId, since);
        zbierz(wynik, 4, "SELECT r.user.id, COUNT(r) FROM ClanMessageReaction r "
            + "WHERE r.message.clan.id = :c AND r.createdAt >= :s GROUP BY r.user.id", clanId, since);
        Map<Long, Counts> gotowe = new HashMap<>();
        wynik.forEach((id, t) -> gotowe.put(id, new Counts(t[0], t[1], t[2], t[3], t[4])));
        return gotowe;
    }

    /** Liczniki z calego czasu. */
    Map<Long, Counts> allTime(Long clanId) {
        return counts(clanId, EPOKA);
    }

    private void zbierz(Map<Long, long[]> wynik, int kolumna, String jpql, Long clanId, LocalDateTime since) {
        List<Object[]> wiersze = em.createQuery(jpql, Object[].class)
            .setParameter("c", clanId).setParameter("s", since).getResultList();
        for (Object[] w : wiersze) {
            wynik.computeIfAbsent((Long) w[0], k -> new long[5])[kolumna] += ((Number) w[1]).longValue();
        }
    }

    /** Poziom (0-4) z punktow z calego czasu. */
    static int level(long points) {
        int poziom = 0;
        for (int i = 0; i < POZIOMY.length; i++) {
            if (points >= POZIOMY[i]) {
                poziom = i;
            }
        }
        return poziom;
    }
}
