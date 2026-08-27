package com.musicclubapp.dto;

import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Jedno powiadomienie w postaci, w jakiej trafia do przegladarki.
 *
 * @param actorUsername  kto to wywolal - jego nazwe pokazujemy w tresci
 * @param reactionType   ktora reakcja; {@code null} poza powiadomieniem o reakcji
 * @param postId         ktorego posta dotyczy; {@code null} poza reakcja
 * @param postExcerpt    poczatek tresci posta - zeby dalo sie poznac ktory,
 *                       bez wchodzenia w niego
 * @param link           dokad prowadzi klikniecie
 */
@Schema(description = "Powiadomienie o zdarzeniu dotyczacym uzytkownika")
public record NotificationResponse(
    Long id,
    NotificationType type,
    String actorUsername,
    String actorAvatarUrl,
    ReactionType reactionType,
    Long postId,
    String postExcerpt,

    /**
     * Adres w aplikacji, pod ktory ma przeniesc klikniecie.
     *
     * <p><b>Wylicza go serwer, a nie frontend.</b> To on wie, ze reakcja
     * prowadzi do posta, a przyjete zaproszenie - na profil. Gdyby robil to
     * frontend, przy kazdym nowym rodzaju powiadomienia trzeba by pamietac
     * o dopisaniu warunku w drugim miejscu - i predzej czy pozniej powstaje
     * powiadomienie, ktore nigdzie nie prowadzi.</p>
     */
    String link,

    boolean read,
    LocalDateTime createdAt
) {
}
