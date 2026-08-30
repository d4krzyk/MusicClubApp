package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/** Pojedyncze zdjecie nalezace do posta - strona ManyToOne relacji z Post. */
@Entity
@Table(name = "post_images")
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** Nazwa pliku na dysku, np. {@code 3f9a...c1.jpg}. Nadaje ja serwer, nie klient. */
    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    /** Kolejnosc wyswietlania, liczona od zera. */
    @Column(nullable = false)
    private int position;

    protected PostImage() {
    }

    public PostImage(String fileName) {
        this.fileName = fileName;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    void setPost(Post post) {
        this.post = post;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostImage other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
