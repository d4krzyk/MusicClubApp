package com.musicclubapp.repository;

import com.musicclubapp.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    /** Oznaczeni w tych komentarzach: komentarz i login. */
    @Query("""
           SELECT m.comment.id AS commentId, m.user.username AS username
           FROM CommentMention m
           WHERE m.comment.id IN :commentIds
           ORDER BY m.id
           """)
    List<CommentMentionRow> of(@Param("commentIds") Collection<Long> commentIds);

    /** Kogo oznaczono w komentarzu - do pobrania wlasnych danych. */
    @Query("SELECT m.user.username FROM CommentMention m WHERE m.comment.id = :commentId ORDER BY m.id")
    List<String> usernamesIn(@Param("commentId") Long commentId);
}
