package com.musicclubapp.service;

import com.musicclubapp.dto.ArtistProfileResponse;
import com.musicclubapp.dto.PerformerLinkView;
import com.musicclubapp.entity.ArtistProfile;
import com.musicclubapp.entity.PerformerAbout;
import com.musicclubapp.entity.PerformerLink;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.gif.SlidingLimiter;
import com.musicclubapp.repository.ArtistProfileRepository;
import com.musicclubapp.repository.PerformerAboutRepository;
import com.musicclubapp.repository.PerformerLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * "Kim jest" wykonawca z koncertu: opis, sluchacze i podobni z Last.fm ({@code artist.getInfo}) plus linki i - gdy
 * Ticketmaster go podal - opis wykonawcy z importu wydarzen. Odpowiedz Last.fm zapamietujemy na 30 dni na wykonawce
 * i jezyk - Last.fm jest pytany najwyzej raz na miesiac o kazdego, a nie przy kazdym rozwinieciu. Awaria Last.fm nie jest
 * zapamietywana (stary opis zostaje, a bez niego idzie odpowiedz z samymi linkami). Opis dostaja tylko nazwy, ktore graja
 * na jakims wydarzeniu - to nie jest wyszukiwarka.
 *
 * Ticketmastera o wykonawce osobno nie pytamy: sprawdzone na zywo, ze nie oddaje opisu ("About" z ticketmaster.pl) ani
 * przez liste wydarzen, ani przez {@code attractions/{id}}, takze z trescia licencjonowana.
 */
@Service
public class ArtistProfileService {

    static final Duration WAZNOSC = Duration.ofDays(30);

    private final LastFmService lastFm;
    private final ArtistProfileRepository profiles;
    private final PerformerLinkRepository links;
    private final PerformerAboutRepository abouts;
    private final TransactionTemplate transactions;
    private final SlidingLimiter limiter;
    private final Clock clock;

    public ArtistProfileService(LastFmService lastFm, ArtistProfileRepository profiles, PerformerLinkRepository links,
                                PerformerAboutRepository abouts,
                                PlatformTransactionManager transactionManager, Clock clock,
                                @Value("${app.artists.profiles-per-minute:30}") int perMinute) {
        this.lastFm = lastFm;
        this.profiles = profiles;
        this.links = links;
        this.abouts = abouts;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.limiter = new SlidingLimiter(60_000, perMinute);
    }

    /**
     * Profil wykonawcy w jezyku interfejsu ({@code pl} albo {@code en}; inne = angielski). Bez transakcji na calosc - nie
     * trzymamy polaczenia z baza, gdy czekamy na Last.fm.
     */
    public ArtistProfileResponse profile(String name, String lang, String viewer) {
        String nazwa = name == null ? "" : name.strip();
        String klucz = NameKeys.of(nazwa);
        if (klucz.isEmpty() || nazwa.length() > 200 || !profiles.isPerformer(nazwa)) {
            throw new NoSuchElementFoundException("artist", nazwa);
        }
        limiter.acquire(viewer, clock.millis());
        String jezyk = "pl".equals(lang) ? "pl" : "en";
        LocalDateTime teraz = LocalDateTime.now(clock);

        Optional<ArtistProfile> znany = profiles.findByNameKeyAndLang(klucz, jezyk);
        ArtistProfile profil = znany.filter(p -> p.getFetchedAt().plus(WAZNOSC).isAfter(teraz)).orElse(null);
        if (profil == null && lastFm.available()) {
            Optional<LastFmService.ArtistInfo> info = lastFm.artistInfo(nazwa, jezyk);
            // Brak opisu po polsku - Last.fm ma zwykle angielski
            if (info.isPresent() && info.get().bio() == null && !"en".equals(jezyk)) {
                Optional<LastFmService.ArtistInfo> angielski = lastFm.artistInfo(nazwa, "en");
                if (angielski.isPresent() && angielski.get().bio() != null) {
                    info = angielski;
                }
            }
            if (info.isPresent()) {
                LastFmService.ArtistInfo i = info.get();
                profil = transactions.execute(s -> {
                    ArtistProfile p = profiles.findByNameKeyAndLang(klucz, jezyk)
                        .orElseGet(() -> new ArtistProfile(klucz, jezyk));
                    p.update(i.name() == null ? nazwa : i.name(), i.bio(), i.url(), i.listeners(), i.similar(), teraz);
                    return profiles.save(p);
                });
            }
        }
        if (profil == null) {
            profil = znany.orElse(null); // przeterminowany, ale lepszy niz nic, gdy Last.fm nie odpowiada
        }

        PerformerAbout opis = abouts.findById(klucz).orElse(null);
        String about = opis == null ? null : opis.getAbout();
        String aboutLang = about == null ? null : opis.getLang();
        String aboutUrl = about == null ? null : opis.getPageUrl();

        List<PerformerLinkView> linki = links.findByNameKey(klucz).stream()
            .sorted(Comparator.comparing(PerformerLink::getKind))
            .map(l -> new PerformerLinkView(l.getKind(), l.getUrl()))
            .toList();
        return profil == null
            ? new ArtistProfileResponse(nazwa, null, null, null, List.of(), linki, about, aboutLang, aboutUrl)
            : new ArtistProfileResponse(profil.getName(), profil.getBio(), profil.getBioUrl(), profil.getListeners(),
                profil.getSimilar(), linki, about, aboutLang, aboutUrl);
    }
}
