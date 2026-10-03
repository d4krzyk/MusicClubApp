package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Komentarz pod postem. Odpowiedzi sa jednopoziomowe: komentarz ma albo {@code parent == null}
 * (komentarz pierwszego poziomu), albo wskazuje komentarz pierwszego poziomu - odpowiedz na odpowiedz
 * wisi pod tym samym rodzicem, a to, do kogo naprawde jest, mowi {@code replyTo}.
 *
 * <p>Znika razem z postem, z autorem i z komentarzem nadrzednym - robi to baza ({@code ON DELETE CASCADE}),
 * wiec zadna sciezka kasowania postow (konto, klan, administrator) nie musi o komentarzach pamietac.</p>
 */
@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_comments_post", columnList = "post_id, created_at"),
        @Index(name = "idx_comments_parent", columnList = "parent_id, created_at"),
        @Index(name = "idx_comments_author", columnList = "author_id, created_at")
    })
public class Comment {

    public static final int MAX_LENGTH = 1000;

    /** Ile osob mozna oznaczyc w jednym komentarzu; reszta zostaje zwyklym tekstem. */
    public static final int MAX_MENTIONS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User author;

    /** Komentarz pierwszego poziomu, pod ktorym wisi ta odpowiedz; puste przy samym komentarzu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parent;

    /** Do kogo jest odpowiedz (autor komentarza albo odpowiedzi, na ktora klikniecie "Odpowiedz"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User replyTo;

    /** Tresc; przy samym GIF-ie pusta (kolumna jest NOT NULL). */
    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Embedded
    private GifAttachment gif;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Comment() {
    }

    public Comment(Post post, User author, Comment parent, User replyTo, String content) {
        this.post = post;
        this.author = author;
        this.parent = parent;
        this.replyTo = replyTo;
        this.content = content;
    }

    public Comment(Post post, User author, Comment parent, User replyTo, String content, GifAttachment gif) {
        this(post, author, parent, replyTo, content);
        this.gif = gif;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public User getAuthor() {
        return author;
    }

    public Comment getParent() {
        return parent;
    }

    public User getReplyTo() {
        return replyTo;
    }

    public String getContent() {
        return content;
    }

    /** GIF dolaczony do komentarza albo {@code null}. */
    public GifAttachment getGif() {
        return gif;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Comment other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
