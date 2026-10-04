package com.musicclubapp.repository;

import com.musicclubapp.entity.DiscoverSwipe;
import com.musicclubapp.entity.SwipeDecision;
import com.musicclubapp.service.LocationScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Decyzje w trybie Poznawaj i sama talia. */
public interface DiscoverSwipeRepository extends JpaRepository<DiscoverSwipe, Long> {

    /**
     * Talia: osoby z wlaczonym trybem Poznawaj, ktore jeszcze moga sie z ogladajacym poznac - bez znajomych,
     * blokad w obie strony, oczekujacych zaproszen (to sprawa listy zaproszen), osob z zakazem publikowania
     * (karta to tresc publikowana) i osob ocenionych: "tak" na stale, "nie" do {@code passValidFrom}.
     *
     * <p>Kolejnosc: najpierw najlepiej dopasowani gustem (wspolni wykonawcy po 5, wspolne utwory po 3,
     * wspolne gatunki po 1), przy rownym guscie blizej mieszkajacy, potem nowsze konta. Zasieg
     * ({@code promien} km, 0 = bez ograniczen) odrzuca osoby dalej i osoby, o ktorych nie wiadomo, gdzie sa.
     * {@code tylko} (numer konta, 0 = wszyscy) - jedna karta, np. po cofnieciu decyzji.</p>
     */
    @Query(value = """
           SELECT x.userId AS userId, x.username AS username, x.avatarFileName AS avatarFileName,
                  x.bio AS bio, x.lookingFor AS lookingFor, x.city AS city, x.cityVisible AS cityVisible,
                  x.profileOpen AS profileOpen, x.sharedArtists AS sharedArtists, x.sharedTracks AS sharedTracks,
                  x.sharedGenres AS sharedGenres, x.sharedFriends AS sharedFriends, x.distance_km AS distanceKm,
                  x.createdAt AS createdAt
             FROM (
                   SELECT u.id                 AS userId,
                          u.username           AS username,
                          u.avatar_file_name   AS avatarFileName,
                          u.bio                AS bio,
                          u.looking_for        AS lookingFor,
                          u.city               AS city,
                          u.show_city          AS cityVisible,
                          (u.profile_visibility = 'EVERYONE') AS profileOpen,
                          u.created_at         AS createdAt,

                          (SELECT COUNT(*) FROM user_favorite_artists k
                             JOIN user_favorite_artists w ON w.artist_id = k.artist_id
                            WHERE k.user_id = u.id AND w.user_id = ja.id)            AS sharedArtists,

                          (SELECT COUNT(*) FROM user_favorite_tracks k
                             JOIN user_favorite_tracks w ON w.track_id = k.track_id
                            WHERE k.user_id = u.id AND w.user_id = ja.id)            AS sharedTracks,

                          (SELECT COUNT(DISTINCT g.genre) FROM user_favorite_artists k
                             JOIN artist_genres g ON g.artist_id = k.artist_id
                            WHERE k.user_id = u.id
                              AND g.genre IN (SELECT g2.genre FROM user_favorite_artists w
                                                JOIN artist_genres g2 ON g2.artist_id = w.artist_id
                                               WHERE w.user_id = ja.id))             AS sharedGenres,

                          (SELECT COUNT(*) FROM user_friends k
                             JOIN user_friends w ON w.friend_id = k.friend_id
                            WHERE k.user_id = u.id AND w.user_id = ja.id)            AS sharedFriends,

                          CASE WHEN ja.city_key IS NULL OR u.city_key IS NULL THEN NULL
                               WHEN ja.city_key = u.city_key THEN 0
                               WHEN ja.city_lat IS NULL OR ja.city_lon IS NULL
                                    OR u.city_lat IS NULL OR u.city_lon IS NULL THEN NULL
                               ELSE\s""" + LocationScore.SQL_DISTANCE_JA_U + """
                               \sEND                                                  AS distance_km
                     FROM users u
                     CROSS JOIN (SELECT id, city_key, city_lat, city_lon FROM users WHERE id = :ja) ja
                    WHERE u.id <> ja.id
                      AND u.enabled = true
                      AND u.discover_enabled = true
                      AND u.email_verified_at IS NOT NULL
                      AND (u.posting_banned_until IS NULL OR u.posting_banned_until < :teraz)
                      AND u.username NOT IN (:pomin)
                      AND (:tylko = 0 OR u.id = :tylko)
                      AND NOT EXISTS (SELECT 1 FROM user_friends f WHERE f.user_id = ja.id AND f.friend_id = u.id)
                      AND NOT EXISTS (SELECT 1 FROM user_blocks b
                                       WHERE (b.blocker_id = ja.id AND b.blocked_id = u.id)
                                          OR (b.blocker_id = u.id AND b.blocked_id = ja.id))
                      AND NOT EXISTS (SELECT 1 FROM friend_requests r
                                       WHERE (r.sender_id = ja.id AND r.recipient_id = u.id)
                                          OR (r.sender_id = u.id AND r.recipient_id = ja.id))
                      AND NOT EXISTS (SELECT 1 FROM discover_swipes s
                                       WHERE s.swiper_id = ja.id AND s.target_id = u.id
                                         AND (s.decision = 'LIKE' OR s.created_at >= :passWazneOd))
                  ) x
            WHERE (:promien = 0 OR x.distance_km <= :promien)
            ORDER BY (5 * x.sharedArtists + 3 * x.sharedTracks + x.sharedGenres) DESC,
                     COALESCE(x.distance_km, 100000) ASC,
                     x.createdAt DESC,
                     x.userId ASC
           """, nativeQuery = true)
    List<DiscoverCandidateRow> deck(@Param("ja") Long viewerId,
                                    @Param("promien") int radiusKm,
                                    @Param("teraz") LocalDateTime now,
                                    @Param("passWazneOd") LocalDateTime passValidFrom,
                                    @Param("pomin") Collection<String> skip,
                                    @Param("tylko") long onlyUserId,
                                    Pageable limit);

    /** Ulubieni wykonawcy tych osob (wszyscy) - do kart; wspolnych z ogladajacym rozpoznaje serwis. */
    @Query(value = """
           SELECT f.user_id AS userId, a.id AS artistId, a.name AS name, a.image_url AS imageUrl
             FROM user_favorite_artists f
             JOIN artists a ON a.id = f.artist_id
            WHERE f.user_id IN (:osoby)
            ORDER BY f.user_id, a.name
           """, nativeQuery = true)
    List<UserArtistRow> artistsOf(@Param("osoby") Collection<Long> userIds);

    /** Gatunki tych osob wspolne z ogladajacym. */
    @Query(value = """
           SELECT DISTINCT k.user_id AS userId, g.genre AS genre
             FROM user_favorite_artists k
             JOIN artist_genres g ON g.artist_id = k.artist_id
            WHERE k.user_id IN (:osoby)
              AND g.genre IN (SELECT g2.genre FROM user_favorite_artists w
                                JOIN artist_genres g2 ON g2.artist_id = w.artist_id
                               WHERE w.user_id = :ja)
            ORDER BY k.user_id, g.genre
           """, nativeQuery = true)
    List<UserGenreRow> sharedGenres(@Param("ja") Long viewerId, @Param("osoby") Collection<Long> userIds);

    /** Identyfikatory ulubionych wykonawcow - do zaznaczania wspolnych na kartach. */
    @Query(value = "SELECT artist_id FROM user_favorite_artists WHERE user_id = :ja", nativeQuery = true)
    List<Long> artistIdsOf(@Param("ja") Long userId);

    Optional<DiscoverSwipe> findBySwiperIdAndTargetId(Long swiperId, Long targetId);

    /** Czy {@code swiper} powiedzial "tak" osobie {@code target}. */
    boolean existsBySwiperIdAndTargetIdAndDecision(Long swiperId, Long targetId, SwipeDecision decision);

    /** Ostatnia decyzja tej osoby - do "cofnij". */
    Optional<DiscoverSwipe> findFirstBySwiperIdOrderByCreatedAtDescIdDesc(Long swiperId);

    /** Ile decyzji od podanej chwili - dzienny limit. */
    long countBySwiperIdAndCreatedAtGreaterThanEqual(Long swiperId, LocalDateTime since);

    /** Obie decyzje miedzy dwiema osobami - po znajomosci i przy blokadzie. */
    @Modifying
    @Query("DELETE FROM DiscoverSwipe s WHERE (s.swiper.id = :a AND s.target.id = :b) OR (s.swiper.id = :b AND s.target.id = :a)")
    int deleteBetween(@Param("a") Long a, @Param("b") Long b);

    /** Wszystkie decyzje tej osoby i o tej osobie - przy usuwaniu konta. */
    @Modifying
    @Query("DELETE FROM DiscoverSwipe s WHERE s.swiper.id = :id OR s.target.id = :id")
    int deleteAllOf(@Param("id") Long userId);

    /** Wygasle decyzje: "nie" starsze niz {@code passBefore}, "tak" bez odpowiedzi starsze niz {@code likeBefore}. */
    @Modifying
    @Query("""
           DELETE FROM DiscoverSwipe s
            WHERE (s.decision = com.musicclubapp.entity.SwipeDecision.PASS AND s.createdAt < :passBefore)
               OR (s.decision = com.musicclubapp.entity.SwipeDecision.LIKE AND s.createdAt < :likeBefore)
           """)
    int deleteExpired(@Param("passBefore") LocalDateTime passBefore, @Param("likeBefore") LocalDateTime likeBefore);

    /** Moje decyzje - do archiwum z danymi. */
    @Query("SELECT s FROM DiscoverSwipe s JOIN FETCH s.target WHERE s.swiper.id = :id ORDER BY s.createdAt")
    List<DiscoverSwipe> madeBy(@Param("id") Long userId);
}
