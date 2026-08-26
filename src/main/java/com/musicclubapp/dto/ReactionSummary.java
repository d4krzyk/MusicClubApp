package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reakcje jednego posta w formie gotowej do wyswietlenia.
 *
 * @param counts ile osob wybralo kazda z reakcji. <b>Zawsze zawiera wszystkie
 *               rodzaje</b>, takze te z zerem - dzieki temu frontend rysuje
 *               trzy przyciski w stalej kolejnosci i nie musi sprawdzac,
 *               czy klucz w ogole istnieje.
 * @param mine   reakcja zalogowanego uzytkownika albo {@code null}, gdy jeszcze
 *               nie zareagowal. Po tym polu podswietlamy wybrany przycisk.
 * @param total  ile osob zareagowalo w sumie - o to prosil uzytkownik
 *               ("bedzie widac ile osob na post zareagowalo"). Liczymy to tutaj,
 *               a nie w przegladarce, zeby ta sama liczba wychodzila
 *               kazdemu klientowi.
 */
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
     * Sklada podsumowanie z policzonych reakcji, uzupelniajac zerami rodzaje,
     * ktorych nikt nie wybral.
     */
    public static ReactionSummary z(Map<ReactionType, Long> counted, ReactionType mine) {
        Map<ReactionType, Long> complete = zerosForAll();
        complete.putAll(counted);

        long total = complete.values().stream().mapToLong(Long::longValue).sum();
        return new ReactionSummary(complete, mine, total);
    }

    /**
     * {@code EnumMap} zamiast {@code HashMap}: klucze wracaja w kolejnosci
     * zadeklarowanej w enumie, wiec JSON zawsze wyglada tak samo.
     */
    private static Map<ReactionType, Long> zerosForAll() {
        Map<ReactionType, Long> byPost = new EnumMap<>(ReactionType.class);
        for (ReactionType type : ReactionType.values()) {
            byPost.put(type, 0L);
        }
        return byPost;
    }
}
