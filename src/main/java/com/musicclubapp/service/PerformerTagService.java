package com.musicclubapp.service;

import com.musicclubapp.entity.PerformerTags;
import com.musicclubapp.repository.PerformerTagsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gatunki wykonawcow z koncertow - pobierane z Last.fm i zapamietywane.
 *
 * Pytamy tylko o tych, ktorych jeszcze nie znamy albo ktorych tagi maja
 * wiecej niz dwa miesiace, i tylko o kilkuset na raz. Pierwszy przebieg po
 * wdrozeniu obejmie wiec czesc wykonawcow; reszta dojdzie w kolejnych.
 * Bez klucza Last.fm nic sie nie dzieje - wtedy dopasowanie gatunkow opiera
 * sie na samych etykietach Ticketmastera.
 */
@Service
public class PerformerTagService {

    private static final Logger log = LoggerFactory.getLogger(PerformerTagService.class);

    /** Ilu wykonawcow sprawdzamy w jednym przebiegu. Z przerwa to ok. 3 minuty. */
    static final int NA_PRZEBIEG = 300;

    /** Po tylu kolejnych bledach przerywamy - Last.fm najwyrazniej lezy. */
    private static final int BLEDOW_Z_RZEDU = 5;

    private static final Duration WAZNOSC = Duration.ofDays(60);

    private final LastFmService lastFm;
    private final PerformerTagsRepository repository;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long przerwaMs;

    @Autowired
    public PerformerTagService(LastFmService lastFm,
                               PerformerTagsRepository repository,
                               PlatformTransactionManager transactionManager,
                               Clock clock,
                               @Value("${app.events.tags.pause-ms:250}") long przerwaMs) {
        this.lastFm = lastFm;
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.przerwaMs = przerwaMs;
    }

    /** Czy gatunki wykonawcow w ogole moga byc znane (czy jest klucz Last.fm). */
    public boolean available() {
        return lastFm.available();
    }

    /**
     * Uzupelnia tagi wykonawcow, ktorych jeszcze nie znamy.
     *
     * @return ilu wykonawcow sprawdzono
     */
    public int refresh(Collection<String> performerNames) {
        if (!lastFm.available() || performerNames.isEmpty()) {
            return 0;
        }

        /* Jedna nazwa na klucz - "MROZU" i "Mrozu" to jedno zapytanie. */
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String name : performerNames) {
            String key = NameKeys.of(name);
            if (!key.isEmpty()) {
                byKey.putIfAbsent(key, name);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, PerformerTags> known = repository.findAllById(byKey.keySet()).stream()
            .collect(Collectors.toMap(PerformerTags::getNameKey, Function.identity()));

        List<String> toCheck = byKey.keySet().stream()
            .filter(key -> !known.containsKey(key)
                || known.get(key).getCheckedAt().plus(WAZNOSC).isBefore(now))
            .limit(NA_PRZEBIEG)
            .toList();

        int checked = 0;
        int bledy = 0;
        for (String key : toCheck) {
            pause();
            Optional<Set<String>> found = lastFm.lookupArtistGenres(byKey.get(key));
            if (found.isEmpty()) {
                if (++bledy >= BLEDOW_Z_RZEDU) {
                    log.warn("Last.fm nie odpowiada - przerywam sprawdzanie gatunkow po {} wykonawcach", checked);
                    break;
                }
                continue;
            }
            bledy = 0;
            transactions.executeWithoutResult(status -> {
                PerformerTags tags = repository.findById(key)
                    .orElseGet(() -> new PerformerTags(key, byKey.get(key)));
                tags.update(found.get(), LocalDateTime.now(clock));
                repository.save(tags);
            });
            checked++;
        }

        if (checked > 0) {
            log.info("Gatunki wykonawcow z Last.fm: sprawdzono {}, czeka jeszcze {}",
                checked, Math.max(0, byKey.size() - known.size() - checked));
        }
        return checked;
    }

    /** Znane tagi wykonawcow: klucz nazwy -> tagi w postaci do porownywania. */
    public Map<String, Set<String>> tagsOf(Collection<String> nameKeys) {
        Map<String, Set<String>> result = new HashMap<>();
        for (PerformerTags tags : repository.findAllById(nameKeys)) {
            result.put(tags.getNameKey(), tags.getGenres().stream()
                .flatMap(genre -> GenreTags.tags(genre).stream())
                .collect(Collectors.toSet()));
        }
        return result;
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
