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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Odpowiedz na jedno pytanie muzyczne z karty profilu ("Moj pierwszy koncert: ..."). */
@Entity
@Table(name = "profile_prompts",
    uniqueConstraints = @UniqueConstraint(name = "uq_profile_prompts", columnNames = {"user_id", "prompt"}))
public class ProfilePromptAnswer {

    public static final int MAX_ANSWER = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProfilePrompt prompt;

    @Column(nullable = false, length = MAX_ANSWER)
    private String answer;

    @Column(nullable = false)
    private int position;

    protected ProfilePromptAnswer() {
        // wymagany przez JPA
    }

    public ProfilePromptAnswer(User user, ProfilePrompt prompt, String answer, int position) {
        this.user = user;
        this.prompt = prompt;
        this.answer = answer;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ProfilePrompt getPrompt() {
        return prompt;
    }

    public String getAnswer() {
        return answer;
    }

    public int getPosition() {
        return position;
    }
}
