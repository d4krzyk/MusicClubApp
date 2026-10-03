package com.musicclubapp.gif;

import com.musicclubapp.dto.GifPageResponse;
import com.musicclubapp.dto.GifResult;
import com.musicclubapp.entity.GifAttachment;
import com.musicclubapp.error.GifUnavailableException;
import com.musicclubapp.error.InvalidGifException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * Przegladarka GIF-ow: serwer pyta dostawce w imieniu uzytkownika, wiec dostawca nie widzi jego adresu IP ani konta
 * (dostaje tylko fraze i pseudonim), klucz API nie trafia do przegladarki, a wyniki mozna podpisac - patrz
 * {@link GifSigner}. Same pliki GIF przegladarka laduje juz wprost z serwera dostawcy.
 *
 * <p>Wlaczone, gdy jest klucz ({@code GIF_API_KEY}) i rozpoznany dostawca ({@code GIF_PROVIDER}: klipy albo giphy);
 * inaczej {@link #enabled()} jest falszem, a interfejs chowa przycisk GIF.</p>
 */
@Service
public class GifService {

    private static final Logger log = LoggerFactory.getLogger(GifService.class);

    public static final int MAX_QUERY = 80;

    private final GifProvider provider;
    private final GifSigner signer;
    private final Clock clock;
    private final SlidingLimiter limiter;
    private final TtlCache<GifPage> cache;

    public GifService(
            @Value("${app.gifs.provider:klipy}") String providerName,
            @Value("${app.gifs.api-key:}") String key,
            @Value("${app.gifs.klipy.base-url:https://api.klipy.com/api/v1}") String klipyBase,
            @Value("${app.gifs.giphy.base-url:https://api.giphy.com/v1}") String giphyBase,
            @Value("${app.gifs.rating:pg-13}") String rating,
            @Value("${app.gifs.timeout-ms:6000}") int timeoutMs,
            @Value("${app.gifs.searches-per-minute:30}") int perMinute,
            @Value("${app.gifs.cache-seconds:300}") int cacheSeconds,
            @Value("${app.remember-me.key}") String secret,
            Clock clock) {

        this.clock = clock;
        this.signer = new GifSigner(secret);
        this.limiter = new SlidingLimiter(60_000, perMinute);
        this.cache = new TtlCache<>(cacheSeconds * 1000L, 200);

        String name = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        String apiKey = key == null ? "" : key.trim();
        if (apiKey.isEmpty() || name.isEmpty()) {
            this.provider = null;
            log.info("GIF-y wylaczone: ustaw GIF_API_KEY (i GIF_PROVIDER: klipy albo giphy), zeby wlaczyc przegladarke GIF-ow.");
        } else if (name.equals("giphy")) {
            this.provider = new GiphyProvider(giphyBase, apiKey, rating, timeoutMs);
        } else if (name.equals("klipy")) {
            this.provider = new KlipyProvider(klipyBase, apiKey, timeoutMs);
        } else {
            throw new IllegalStateException("Nieznany GIF_PROVIDER: " + providerName + " (dozwolone: klipy, giphy)");
        }
    }

    public boolean enabled() {
        return provider != null;
    }

    /** Nazwa dostawcy do podpisu "Powered by ..." albo {@code null}, gdy GIF-y wylaczone. */
    public String attribution() {
        return provider == null ? null : provider.attribution();
    }

    /** Szukanie; pusta fraza = popularne. */
    public GifPageResponse search(Long userId, String query, String position, int limit, String language) {
        GifProvider active = require();
        String phrase = normalize(query);
        long now = clock.millis();
        limiter.acquire(userId, now);

        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String customer = signer.pseudonym(userId);
        String key = active.name() + "|" + phrase + "|" + (position == null ? "" : position) + "|" + limit + "|" + lang;
        GifPage page = cache.get(key, now, () -> phrase.isEmpty()
            ? active.trending(position, limit, lang, customer)
            : active.search(phrase, position, limit, lang, customer));

        List<GifResult> results = page.items().stream().map(this::result).toList();
        return new GifPageResponse(results, page.next());
    }

    private GifResult result(GifItem item) {
        return new GifResult(item.id(), item.title(), item.url(), item.previewUrl(), item.width(), item.height(),
            signer.sign(item));
    }

    /** GIF z tokenu z wyszukiwania; {@code null}/pusty token = brak GIF-a. */
    public GifAttachment attach(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!enabled()) {
            throw GifUnavailableException.disabled();
        }
        return signer.verify(token.strip());
    }

    private GifProvider require() {
        if (provider == null) {
            throw GifUnavailableException.disabled();
        }
        return provider;
    }

    /** Spacje zlaczone, ucieta dlugosc, male litery nie sa potrzebne - dostawca i tak nie rozroznia wielkosci. */
    static String normalize(String query) {
        if (query == null) {
            return "";
        }
        String clean = query.replaceAll("\\s+", " ").strip();
        return clean.length() <= MAX_QUERY ? clean : clean.substring(0, MAX_QUERY).strip();
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    void forgetOldLimits() {
        limiter.forgetOld(clock.millis());
    }
}
