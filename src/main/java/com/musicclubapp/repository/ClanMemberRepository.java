package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanMemberRepository extends JpaRepository<ClanMember, Long> {

    /** Klan tej osoby, jesli jakis ma. */
    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id = :userId")
    Optional<ClanMember> findByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.username = :username")
    Optional<ClanMember> findByUsername(@Param("username") String username);

    /** Czlonkowie klanu razem z kontami: zalozyciel, administratorzy, potem reszta wedlug stazu. */
    @Query("""
           SELECT m FROM ClanMember m JOIN FETCH m.user
           WHERE m.clan.id = :clanId
           ORDER BY CASE m.role WHEN com.musicclubapp.entity.ClanRole.FOUNDER THEN 0
                                WHEN com.musicclubapp.entity.ClanRole.ADMIN THEN 1 ELSE 2 END,
                    m.joinedAt, m.id
           """)
    List<ClanMember> ofClan(@Param("clanId") Long clanId);

    long countByClanId(Long clanId);

    /**
     * Osoby, ktore dostana powiadomienie o nowej wiadomosci na czacie: nie autor, nie wyciszeni
     * i tacy, ktorzy nie maja jeszcze zadnej nieprzeczytanej (inaczej telefon brzeczalby przy
     * kazdej wiadomosci - jedno powiadomienie wystarcza, dopoki ktos nie zajrzy do czatu).
     */
    @Query("""
           SELECT m FROM ClanMember m JOIN FETCH m.user
           WHERE m.clan.id = :clanId AND m.user.id <> :senderId AND m.chatMuted = false
             AND NOT EXISTS (SELECT 1 FROM ClanMessage x
                             WHERE x.clan.id = m.clan.id AND x.id > COALESCE(m.chatReadId, 0)
                               AND x.id < :newId AND x.sender.id <> m.user.id AND x.deletedAt IS NULL)
           """)
    List<ClanMember> toNotify(@Param("clanId") Long clanId, @Param("senderId") Long senderId,
                              @Param("newId") Long newId);

    /** Czlonkostwa tych osob razem z klanami - do plakietek pod postami, jednym zapytaniem. */
    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id IN :userIds")
    List<ClanMember> ofUsers(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM ClanMember m WHERE m.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    /** Ilu czlonkow ma kazdy klan - do przegladarki klanow. */
    @Query("SELECT m.clan.id AS clanId, COUNT(m) AS total FROM ClanMember m GROUP BY m.clan.id")
    List<ClanCountRow> memberCounts();

    /** Zalozyciele wszystkich klanow. */
    @Query("SELECT m.clan.id AS clanId, m.user.id AS userId FROM ClanMember m WHERE m.role = com.musicclubapp.entity.ClanRole.FOUNDER")
    List<ClanUserRow> founders();

    /** Zalozyciel i administratorzy klanu - ci, ktorzy rozpatruja prosby o dolaczenie. */
    @Query("""
           SELECT m FROM ClanMember m JOIN FETCH m.user
           WHERE m.clan.id = :clanId
             AND m.role IN (com.musicclubapp.entity.ClanRole.FOUNDER, com.musicclubapp.entity.ClanRole.ADMIN)
           """)
    List<ClanMember> managersOf(@Param("clanId") Long clanId);

    /**
     * Gatunki ulubionych wykonawcow wspolne dla co najmniej dwoch osob z KAZDEGO klanu z przegladarki.
     * Bez osob z ograniczonym profilem - tak samo jak gust na stronie klanu.
     */
    @Query("""
           SELECT m.clan.id AS clanId, g AS name, COUNT(DISTINCT u.id) AS total
           FROM ClanMember m JOIN m.user u JOIN u.favoriteArtists a JOIN a.genres g
           WHERE m.clan.listed = true AND u.profileVisibility = com.musicclubapp.entity.ProfileVisibility.EVERYONE
           GROUP BY m.clan.id, g
           HAVING COUNT(DISTINCT u.id) >= 2
           """)
    List<ClanTasteRow> listedGenres();

    /** To samo dla wykonawcow (identyfikator z katalogu) - do dopasowania klanu do gustu ogladajacego. */
    @Query("""
           SELECT m.clan.id AS clanId, a.externalId AS name, COUNT(DISTINCT u.id) AS total
           FROM ClanMember m JOIN m.user u JOIN u.favoriteArtists a
           WHERE m.clan.listed = true AND u.profileVisibility = com.musicclubapp.entity.ProfileVisibility.EVERYONE
           GROUP BY m.clan.id, a.id, a.externalId
           HAVING COUNT(DISTINCT u.id) >= 2
           """)
    List<ClanTasteRow> listedArtists();
}
