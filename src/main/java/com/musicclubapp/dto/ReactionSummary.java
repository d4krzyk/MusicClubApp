package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;

import java.util.EnumMap;
import java.util.Map;

/** Reakcje jednego posta w formie gotowej do wyswietlenia. */
public record ReactionSummary(
    Map<ReactionType, Long> counts,
    ReactionType mine,
    long total
) {

    /** Post, na ktory nikt jeszcze nie zareagowal. */
    public static ReactionSummary empty() {
        return new ReactionSummary(zerosForAll(), null, 0);
    }

    /**
     * Sklada podsumowanie z policzonych reakcji, uzupelniajac zerami rodzaje, ktorych nikt nie
     * wybral.
     */
    public static ReactionSummary z(Map<ReactionType, Long> counted, ReactionType mine) {
        Map<ReactionType, Long> complete = zerosForAll();
        complete.putAll(counted);

        long total = complete.values().stream().mapToLong(Long::longValue).sum();
        return new ReactionSummary(complete, mine, total);
    }

    /**
     * EnumMap zamiast HashMap: klucze wracaja w kolejnosci zadeklarowanej w enumie, wiec JSON
     * zawsze wyglada tak samo.
     */
    private static Map<ReactionType, Long> zerosForAll() {
        Map<ReactionType, Long> byPost = new EnumMap<>(ReactionType.class);
        for (ReactionType type : ReactionType.values()) {
            byPost.put(type, 0L);
        }
        return byPost;
    }
}
