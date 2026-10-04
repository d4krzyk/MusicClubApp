package com.musicclubapp.repository;

import com.musicclubapp.entity.ProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Zdjecia w galeriach profili. */
public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {

    /** Galeria jednej osoby w kolejnosci, w jakiej ja ulozyla. */
    @Query("SELECT p FROM ProfilePhoto p WHERE p.user.id = :userId ORDER BY p.position, p.id")
    List<ProfilePhoto> ofUser(@Param("userId") Long userId);

    /** Galerie wielu osob naraz - do talii w trybie Poznawaj (jedno zapytanie na strone talii). */
    @Query("SELECT p FROM ProfilePhoto p WHERE p.user.id IN :userIds ORDER BY p.user.id, p.position, p.id")
    List<ProfilePhoto> ofUsers(@Param("userIds") Collection<Long> userIds);

    long countByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM ProfilePhoto p WHERE p.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
