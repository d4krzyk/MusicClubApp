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

    /** Konta, ktore laczylo z tym cokolwiek wspolnego w sieci. */
    @Query("""
           SELECT DISTINCT other FROM AccountIp other
           WHERE other.user.id <> :userId
             AND other.address IN (
                   SELECT mine.address FROM AccountIp mine WHERE mine.user.id = :userId)
           ORDER BY other.lastSeenAt DESC
           """)
    List<AccountIp> sharingAddressWith(@Param("userId") Long userId);

    /** Kasuje historie adresow konta - uzywane przy usuwaniu uzytkownika. */
    @Modifying
    @Query("DELETE FROM AccountIp a WHERE a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
