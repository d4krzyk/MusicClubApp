package com.musicclubapp.repository;

import com.musicclubapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Query("select distinct u from User u join u.artists a " +
           "where a in (select a2 from User u2 join u2.artists a2 where u2.id = :userId) " +
           "and u.id <> :userId")
    Page<User> findUsersWithSimilarArtists(@Param("userId") Long userId, Pageable pageable);
}

