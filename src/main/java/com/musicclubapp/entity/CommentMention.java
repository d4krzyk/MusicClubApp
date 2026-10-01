package com.musicclubapp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Osoba oznaczona w komentarzu (@login). Zapisujemy tylko tych, ktorych dalo sie oznaczyc w chwili
 * pisania (istnieja, widza post, nie ma miedzy wami blokady) - dzieki temu odnosnik w tresci prowadzi
 * zawsze do kogos, kogo to dotyczylo, a nie do kazdego slowa z malpa.
 */
@Entity
@Table(
    name = "comment_mentions",
    uniqueConstraints = @UniqueConstraint(name = "uq_comment_mentions", columnNames = {"comment_id", "user_id"}),
    indexes = @Index(name = "idx_comment_mentions_user", columnList = "user_id"))
public class CommentMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    protected CommentMention() {
    }

    public CommentMention(Comment comment, User user) {
        this.comment = comment;
        this.user = user;
    }

    public Comment getComment() {
        return comment;
    }

    public User getUser() {
        return user;
    }
}
