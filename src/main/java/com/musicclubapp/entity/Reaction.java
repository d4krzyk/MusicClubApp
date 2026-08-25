package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Reakcja jednego uzytkownika na jeden post.
 *
 * <p>Kolejna para {@code OneToMany}/{@code ManyToOne} w projekcie
 * (wymaganie nr 6): reakcja wskazuje na post ORAZ na autora reakcji,
 * a {@link Post} trzyma ich liste.</p>
 *
 * <p><b>Jeden uzytkownik = jedna reakcja na dany post.</b> Pilnuje tego
 * ograniczenie {@code UNIQUE} na parze kolumn, a nie tylko kod serwisu.
 * Warunek w Javie da sie obejsc dwoma zapytaniami wyslanymi w tej samej
 * chwili (oba sprawdza "czy juz jest?", oba dostana odpowiedz "nie ma"
 * i oba zapisza) - baza odrzuci taki duplikat niezaleznie od tego,
 * co robi aplikacja.</p>
 *
 * <p>Zmiana zdania (ogien zamiast "meh") NIE tworzy drugiego wiersza,
 * tylko podmienia {@code type} w istniejacym - patrz {@code ReactionService}.</p>
 */
@Entity
@Table(
    name = "reactions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reaction_post_user",
        columnNames = {"post_id", "user_id"}))
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** Kto zareagowal. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * {@code EnumType.STRING} zapisuje w bazie napis "FIRE", a nie liczbe.
     *
     * <p>Przy domyslnym {@code ORDINAL} baza trzyma pozycje na liscie (0, 1, 2).
     * Wystarczy wtedy dopisac nowa wartosc W SRODKU enuma, zeby wszystkie
     * dotychczasowe reakcje zmienily znaczenie - i nikt tego nie zauwazy,
     * bo zadne zapytanie sie nie wywali.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReactionType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Reaction() {
    }

    public Reaction(Post post, User user, ReactionType type) {
        this.post = post;
        this.user = user;
        this.type = type;
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

    public User getUser() {
        return user;
    }

    public ReactionType getType() {
        return type;
    }

    /** Zmiana zdania - ten sam wiersz, inna reakcja. */
    public void setType(ReactionType type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reaction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
