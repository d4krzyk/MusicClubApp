package com.musicclubapp.entity;

import jakarta.persistence.Column;
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
 * Zdjecie w galerii profilu (karta w trybie Poznawaj i profil). Plik lezy w katalogu wgranych plikow, tak jak
 * zdjecia postow; tu tylko nazwa i miejsce w kolejnosci. Pierwsze zdjecie to okladka karty.
 */
@Entity
@Table(name = "profile_photos", indexes = @Index(name = "idx_profile_photos_user", columnList = "user_id, position"))
public class ProfilePhoto {

    /** Najwyzej tyle zdjec w galerii jednej osoby. */
    public static final int MAX = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProfilePhoto() {
        // wymagany przez JPA
    }

    public ProfilePhoto(User user, String fileName, int position) {
        this.user = user;
        this.fileName = fileName;
        this.position = position;
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

    public User getUser() {
        return user;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
