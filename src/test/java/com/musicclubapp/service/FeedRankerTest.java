package com.musicclubapp.service;

import com.musicclubapp.dto.FeedReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ocena postow na tablicy Dla ciebie")
class FeedRankerTest {

    private static final LocalDateTime TERAZ = LocalDateTime.of(2026, 10, 1, 12, 0);

    private static FeedRanker.Signals post(int godzinTemu, int blisko, long artysci, long gatunki, long reakcje, long komentarze) {
        return new FeedRanker.Signals(TERAZ.minusHours(godzinTemu), blisko, artysci, gatunki, reakcje, komentarze);
    }

    private static double ocena(FeedRanker.Signals s) {
        return FeedRanker.score(s, TERAZ);
    }

    @Test
    @DisplayName("zwykly swiezy post ma ocene podstawowa, a co 72 godziny ocena spada o polowe")
    void freshnessHalvesEvery72Hours() {
        assertThat(ocena(post(0, 0, 0, 0, 0, 0))).isEqualTo(FeedRanker.PODSTAWA);
        assertThat(ocena(post(72, 0, 0, 0, 0, 0))).isCloseTo(FeedRanker.PODSTAWA / 2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(ocena(post(144, 0, 0, 0, 0, 0))).isCloseTo(FeedRanker.PODSTAWA / 4, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("post z okolicy, o podobnym guscie albo popularny wyprzedza zwykly post tego samego wieku")
    void signalsLiftAPost() {
        double zwykly = ocena(post(10, 0, 0, 0, 0, 0));
        assertThat(ocena(post(10, 5, 0, 0, 0, 0))).isGreaterThan(zwykly);
        assertThat(ocena(post(10, 0, 1, 0, 0, 0))).isGreaterThan(zwykly);
        assertThat(ocena(post(10, 0, 0, 3, 0, 0))).isGreaterThan(zwykly);
        assertThat(ocena(post(10, 0, 0, 0, 8, 0))).isGreaterThan(zwykly);
        assertThat(ocena(post(10, 0, 0, 0, 0, 4))).isGreaterThan(zwykly);
    }

    @Test
    @DisplayName("im blizej, tym wyzej; im wiecej wspolnego gustu i reakcji, tym wyzej - az do maksimum")
    void monotonicAndCapped() {
        for (int poziom = 1; poziom <= 5; poziom++) {
            assertThat(ocena(post(5, poziom, 0, 0, 0, 0))).isGreaterThan(ocena(post(5, poziom - 1, 0, 0, 0, 0)));
        }
        assertThat(ocena(post(5, 0, 2, 0, 0, 0))).isGreaterThan(ocena(post(5, 0, 1, 0, 0, 0)));
        // gust: 4 wykonawcow i 40 gatunkow to to samo co 4 i 0 - maksimum to 10 pkt
        assertThat(ocena(post(5, 0, 4, 0, 0, 0))).isEqualTo(ocena(post(5, 0, 4, 40, 0, 0)));
        // popularnosc: tysiac reakcji niewiele wiecej niz dwadziescia
        assertThat(ocena(post(5, 0, 0, 0, 1000, 0))).isEqualTo(ocena(post(5, 0, 0, 0, 20, 0)));
        assertThat(ocena(post(5, 0, 0, 0, 20, 0))).isLessThan(ocena(post(5, 0, 0, 0, 0, 0)) + FeedRanker.WAGA_POPULARNOSCI + 1e-9);
    }

    @Test
    @DisplayName("swiezy post z tego samego miasta wyprzedza trzydniowy z gustem i popularnoscia, ale nie na odwrot po miesiacu")
    void freshLocalBeatsOlderPopularButNotForever() {
        double swiezyLokalny = ocena(post(0, 5, 0, 0, 0, 0));
        double trzydniowyZnakomity = ocena(post(72, 0, 4, 0, 30, 0));
        assertThat(swiezyLokalny).isGreaterThan(trzydniowyZnakomity);
        // zwykly swiezy post przegrywa z trzydniowym wartym uwagi
        assertThat(ocena(post(0, 0, 0, 0, 0, 0))).isLessThan(trzydniowyZnakomity);
        // ...ale miesiac pozniej nawet komplet punktow nie wystarcza na swiezy zwykly post
        assertThat(ocena(post(24 * 30, 5, 4, 0, 30, 0))).isLessThan(ocena(post(0, 0, 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("kolejnosc dwoch postow nie zalezy od chwili liczenia - tylko od roznicy ich wieku")
    void orderIsStableOverTime() {
        FeedRanker.Signals a = post(2, 5, 0, 0, 0, 0);
        FeedRanker.Signals b = post(30, 0, 2, 0, 12, 0);
        boolean teraz = FeedRanker.score(a, TERAZ) > FeedRanker.score(b, TERAZ);
        for (int godzin : new int[] {1, 7, 48, 200}) {
            LocalDateTime pozniej = TERAZ.plusHours(godzin);
            assertThat(FeedRanker.score(a, pozniej) > FeedRanker.score(b, pozniej)).isEqualTo(teraz);
        }
    }

    @Test
    @DisplayName("post z przyszlosci (zegar telefonu, skos czasu) nie dostaje oceny wiekszej niz swiezy")
    void futureIsNotBetterThanNow() {
        assertThat(ocena(post(-5, 0, 0, 0, 0, 0))).isEqualTo(ocena(post(0, 0, 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("powody: okolica tylko gdy autor pokazuje miasto, gust od wspolnego wykonawcy albo dwoch gatunkow, popularnosc od 5")
    void reasons() {
        assertThat(FeedRanker.reasons(post(0, 5, 0, 0, 0, 0), true)).containsExactly(FeedReason.NEAR);
        assertThat(FeedRanker.reasons(post(0, 5, 0, 0, 0, 0), false)).isEmpty();
        assertThat(FeedRanker.reasons(post(0, 3, 0, 0, 0, 0), true)).containsExactly(FeedReason.NEAR);
        assertThat(FeedRanker.reasons(post(0, 2, 0, 0, 0, 0), true)).isEmpty();
        assertThat(FeedRanker.reasons(post(0, 0, 1, 0, 0, 0), true)).containsExactly(FeedReason.TASTE);
        assertThat(FeedRanker.reasons(post(0, 0, 0, 1, 0, 0), true)).isEmpty();
        assertThat(FeedRanker.reasons(post(0, 0, 0, 2, 0, 0), true)).containsExactly(FeedReason.TASTE);
        assertThat(FeedRanker.reasons(post(0, 0, 0, 0, 4, 0), true)).isEmpty();
        assertThat(FeedRanker.reasons(post(0, 0, 0, 0, 5, 0), true)).containsExactly(FeedReason.POPULAR);
        // komentarz liczy sie za dwie reakcje
        assertThat(FeedRanker.reasons(post(0, 0, 0, 0, 1, 2), true)).containsExactly(FeedReason.POPULAR);
        assertThat(FeedRanker.reasons(post(0, 5, 2, 0, 9, 0), true))
            .containsExactly(FeedReason.NEAR, FeedReason.TASTE, FeedReason.POPULAR);
    }
}
