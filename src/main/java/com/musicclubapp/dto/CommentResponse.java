package com.musicclubapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Komentarz w postaci wysylanej do przegladarki. */
public record CommentResponse(
    Long id,
    Long postId,
    /** Komentarz pierwszego poziomu, pod ktorym wisi ta odpowiedz; {@code null} przy komentarzu pierwszego poziomu. */
    Long parentId,
    String authorUsername,
    String authorAvatarUrl,
    /** Klan autora - plakietka obok loginu albo {@code null}. */
    ClanBadge authorClan,
    String content,
    /** GIF dolaczony do komentarza albo {@code null}. */
    GifView gif,
    /** Loginy osob oznaczonych w tresci - tylko te zamieniaja sie w odnosniki. */
    List<String> mentions,
    /** Do kogo jest odpowiedz (przy odpowiedziach) albo {@code null}. */
    String replyToUsername,
    LocalDateTime createdAt,
    /** To komentarz ogladajacego. */
    boolean mine,
    /** Czy ogladajacy moze go skasowac: autor, autor posta, administrator albo zarzad klanu (post klanu). */
    boolean canDelete,
    /** Ile odpowiedzi ma ten komentarz (przy odpowiedziach zawsze 0). */
    long replyCount
) {
}
