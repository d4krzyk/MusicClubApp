package com.musicclubapp.dto;

import com.musicclubapp.entity.CrewJoinPolicy;

import java.util.List;

/**
 * Ekipa na liscie pod wydarzeniem. {@code myState}: FOUNDER, MEMBER, REQUESTED, DECLINED albo null; {@code canJoin} -
 * czy przycisk "Dolacz"/"Popros" ma sens (miejsca, nabor, nie jestem w innej ekipie na ten koncert, brak blokady).
 * {@code near} - jak blisko mojego miasta wyrusza (0-5, jak w calej aplikacji), {@code distanceKm} tylko dla mnie.
 */
public record CrewCardResponse(
    Long id,
    String title,
    String description,
    String founder,
    String founderAvatarUrl,
    int members,
    int capacity,
    CrewJoinPolicy joinPolicy,
    boolean closed,
    String departureCity,
    int near,
    Integer distanceKm,
    long friends,
    List<CrewPersonView> preview,
    String myState,
    boolean canJoin
) {
}
