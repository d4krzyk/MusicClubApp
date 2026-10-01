package com.musicclubapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Ankieta w klanie z biezacymi wynikami (bez wskazywania, kto na co glosowal). */
public record ClanPollResponse(
    Long id,
    String question,
    String authorUsername,
    LocalDateTime createdAt,
    LocalDateTime closesAt,
    boolean open,
    List<Option> options,
    /** Odpowiedz, na ktora glosowal ogladajacy, albo null. */
    Long myOption,
    long totalVotes,
    /** Ogladajacy moze ja zamknac albo usunac (autor albo zarzad). */
    boolean canManage
) {

    public record Option(Long id, String text, long votes) {
    }
}
