package com.musicclubapp.repository;

import com.musicclubapp.entity.AccountIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Historia adresow, z ktorych logowaly sie konta. */
@Repository
public interface AccountIpRepository extends JpaRepository<AccountIp, Long> {

    Optional<AccountIp> findByUserIdAndAddress(Long userId, String address);

    /** Wszystkie adresy jednego konta - od ostatnio uzywanego. */
    List<AccountIp> findByUserIdOrderByLastSeenAtDesc(Long userId);

    /**
     * <b>Konta, ktore laczylo z tym cokolwiek wspolnego w sieci.</b>
     *
     * <p>Pytanie brzmi: "kto jeszcze logowal sie z ktoregokolwiek adresu,
     * z ktorego logowalo sie TO konto". Robimy to jednym zapytaniem -
     * wersja w Javie oznaczalaby pobranie listy adresow, a potem osobne
     * zapytanie dla kazdego z nich.</p>
     *
     * <p>Wynik jest posortowany od kont najswiezej uzywanych: przy szukaniu
     * drugiego konta tej samej osoby liczy sie to, co dzieje sie teraz,
     * a nie sprzed roku.</p>
     */
    @Query("""
           SELECT DISTINCT other FROM AccountIp other
           WHERE other.user.id <> :userId
             AND other.address IN (
                   SELECT mine.address FROM AccountIp mine WHERE mine.user.id = :userId)
           ORDER BY other.lastSeenAt DESC
           """)
    List<AccountIp> sharingAddressWith(@Param("userId") Long userId);

    /**
     * Kasuje historie adresow konta - uzywane przy usuwaniu uzytkownika.
     *
     * <p><b>Kasujemy razem z kontem, choc kusi, zeby zostawic</b> - wiersze
     * bez konta nadal mowilyby, ze "z tego adresu ktos byl". Trzymanie
     * danych o ruchu sieciowym osoby, ktorej konta juz nie ma, jest
     * przechowywaniem informacji bez podstawy: usuniete konto ma zniknac,
     * a nie zostawic po sobie slad w innej tabeli.</p>
     */
    @Modifying
    @Query("DELETE FROM AccountIp a WHERE a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
