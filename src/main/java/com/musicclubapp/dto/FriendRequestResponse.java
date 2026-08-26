package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Zaproszenie do znajomych na liscie oczekujacych.
 *
 * @param id       potrzebne, zeby przyjac albo odrzucic konkretne zaproszenie
 * @param username <b>druga strona</b> - przy zaproszeniach przychodzacych
 *                 nadawca, przy wyslanych odbiorca. Frontend i tak wie,
 *                 ktora liste wyswietla, wiec nie ma sensu wysylac obu
 *                 loginow, z ktorych jeden zawsze bylby nasz wlasny.
 */
public record FriendRequestResponse(
    Long id,
    String username,
    String avatarUrl,
    LocalDateTime createdAt
) {
}
