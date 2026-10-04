package com.musicclubapp.repository;

import com.musicclubapp.entity.ProfilePromptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Odpowiedzi na pytania muzyczne z kart profilu. */
public interface ProfilePromptAnswerRepository extends JpaRepository<ProfilePromptAnswer, Long> {

    @Query("SELECT a FROM ProfilePromptAnswer a WHERE a.user.id = :userId ORDER BY a.position, a.id")
    List<ProfilePromptAnswer> ofUser(@Param("userId") Long userId);

    @Query("SELECT a FROM ProfilePromptAnswer a WHERE a.user.id IN :userIds ORDER BY a.user.id, a.position, a.id")
    List<ProfilePromptAnswer> ofUsers(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM ProfilePromptAnswer a WHERE a.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
