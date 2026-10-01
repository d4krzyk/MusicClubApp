package com.musicclubapp.dto;

import java.util.List;

/**
 * Ranking aktywnosci w klanie. Punkty: wiadomosc 1, post 4, propozycja utworu 3, glos (utwor albo
 * ankieta) 1, reakcja 1. {@code period}: WEEK (ostatnie 7 dni) albo ALL.
 */
public record ClanActivityResponse(String period, Summary summary, List<Row> ranking) {

    /** Podsumowanie klanu z ostatnich 7 dni i cel tygodnia. */
    public record Summary(long points, long goal, long messages, long posts, long tracks, long votes,
                          long reactions) {
    }

    public record Row(
        String username,
        String avatarUrl,
        /** Punkty z wybranego okresu. */
        long points,
        long messages,
        long posts,
        long tracks,
        long votes,
        long reactions,
        /** Poziom z punktow z calego czasu: 0-4. */
        int level,
        long allTimePoints,
        /** Ile punktow brakuje do kolejnego poziomu albo null na najwyzszym. */
        Long nextLevelAt,
        boolean me
    ) {
    }
}
