package com.musicclubapp.service;

import com.musicclubapp.entity.Artist;
import com.musicclubapp.repository.ArtistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Uzupelnia gatunki ulubionych artystow, ktorzy ich nie maja.
 *
 * Gatunki biora sie z Last.fm i do tej pory byly pobierane tylko raz - przy
 * dodaniu artysty do ulubionych. Kto dodal ulubionych, zanim serwer dostal
 * klucz Last.fm, zostawal bez gatunkow na zawsze, a razem z nim "Dla ciebie"
 * i podpowiedzi znajomych dzialaly slabiej. Teraz serwer sam wraca do takich
 * artystow w tle.
 */
@Service
public class ArtistGenreBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ArtistGenreBackfillService.class);

    /** Ilu artystow na jeden przebieg. Reszta w kolejnych - co godzine. */
    static final int NA_PRZEBIEG = 100;

    /** Po tylu kolejnych bledach przerywamy - Last.fm najwyrazniej lezy. */
    private static final int BLEDOW_Z_RZEDU = 5;

    /** Artysta bez tagow w Last.fm - zapytamy znowu po tym czasie, moze juz jakies ma. */
    private static final Duration PONOWNIE_PO = Duration.ofDays(60);

    private final LastFmService lastFm;
    private final ArtistRepository artists;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long przerwaMs;

    @Autowired
    public ArtistGenreBackfillService(LastFmService lastFm,
                                      ArtistRepository artists,
                                      PlatformTransactionManager transactionManager,
                                      Clock clock,
                                      @Value("${app.favorites.genres.pause-ms:250}") long przerwaMs) {
        this.lastFm = lastFm;
        this.artists = artists;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.przerwaMs = przerwaMs;
    }

    @Scheduled(initialDelayString = "${app.favorites.genres.initial-delay-ms:60000}",
               fixedDelayString = "${app.favorites.genres.interval-ms:3600000}")
    public void scheduled() {
        if (lastFm.available()) {
            backfill();
        }
    }

    /** @return ilu artystow dostalo gatunki (albo potwierdzenie, ze zadnych nie maja) */
    public int backfill() {
        if (!lastFm.available()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Artist> doSprawdzenia = transactions.execute(status ->
            artists.needingGenres(now.minus(PONOWNIE_PO), PageRequest.of(0, NA_PRZEBIEG)));

        int sprawdzonych = 0;
        int bledy = 0;
        for (Artist artist : doSprawdzenia) {
            pause();
            Optional<Set<String>> found = lastFm.lookupArtistGenres(artist.getName());
            if (found.isEmpty()) {
                /* Awaria to nie odpowiedz - artysta zostaje niesprawdzony i wroci w nastepnym przebiegu. */
                if (++bledy >= BLEDOW_Z_RZEDU) {
                    log.warn("Last.fm nie odpowiada - przerywam uzupelnianie gatunkow po {} artystach", sprawdzonych);
                    break;
                }
                continue;
            }
            bledy = 0;
            Long id = artist.getId();
            transactions.executeWithoutResult(status -> artists.findById(id)
                .ifPresent(a -> a.genresChecked(found.get(), LocalDateTime.now(clock))));
            sprawdzonych++;
        }

        if (sprawdzonych > 0) {
            log.info("Gatunki ulubionych artystow: uzupelniono {}", sprawdzonych);
        }
        return sprawdzonych;
    }

    private void pause() {
        if (przerwaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(przerwaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
