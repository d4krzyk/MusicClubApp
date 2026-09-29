package com.musicclubapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.dto.PushSettingsResponse;
import com.musicclubapp.dto.PushSubscribeRequest;
import com.musicclubapp.entity.PushSubscription;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.push.P256;
import com.musicclubapp.push.Vapid;
import com.musicclubapp.push.WebPushEncryption;
import com.musicclubapp.repository.PushSubscriptionRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Powiadomienia push na telefon (Web Push).
 *
 * <p>Wlaczone, gdy sa klucze VAPID_PUBLIC_KEY i VAPID_PRIVATE_KEY. Bez nich
 * wszystko dziala jak dotad - powiadomienia sa tylko w dzwonku.</p>
 *
 * <p>Wysylka idzie w tle i po zatwierdzeniu transakcji - tak jak poczta.
 * Serwer wysyla tylko do uslug push przegladarek z listy (Google, Mozilla,
 * Apple, Microsoft): adres subskrypcji podaje przegladarka, wiec bez listy
 * kazdy moglby kazac serwerowi wysylac zapytania w dowolne miejsce, takze do
 * uslug w sieci wewnetrznej.</p>
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** Uslugi push przegladarek. Wpis z kropka na poczatku obejmuje poddomeny. */
    static final List<String> USLUGI = List.of(
        "fcm.googleapis.com",
        "updates.push.services.mozilla.com",
        "push.services.mozilla.com",
        ".push.apple.com",
        ".notify.windows.com");

    /** Tyle urzadzen na konto - starsze wypadaja. */
    public static final int MAX_URZADZEN = 10;

    /** Probne powiadomienie najwyzej raz na tyle. */
    static final Duration ODSTEP_PROBY = Duration.ofSeconds(30);

    /** Tresc powiadomienia: klucze z messages*.properties i ich argumenty. */
    public record Message(String titleKey, Object[] titleArgs, String bodyKey, Object[] bodyArgs,
                          String url, String tag) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Vapid vapid;
    private final List<String> uslugi;
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final MessageSource messages;
    private final Clock clock;
    private final int ttl;
    private final Executor wysylka;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private final Map<Long, Instant> ostatniaProba = new ConcurrentHashMap<>();

    @Autowired
    public PushService(@Value("${app.push.public-key:}") String publicKey,
                       @Value("${app.push.private-key:}") String privateKey,
                       @Value("${app.push.subject:}") String subject,
                       @Value("${app.public-url:}") String publicUrl,
                       @Value("${app.push.extra-hosts:}") String extraHosts,
                       @Value("${app.push.ttl-seconds:86400}") int ttl,
                       PushSubscriptionRepository subscriptions,
                       UserRepository users,
                       MessageSource messages,
                       Clock clock) {
        this(publicKey, privateKey, subject, publicUrl, extraHosts, ttl, subscriptions, users, messages, clock, null);
    }

    /** Dla testow: z wykonawca, ktory wysyla od razu, w tym samym watku. */
    PushService(String publicKey, String privateKey, String subject, String publicUrl, String extraHosts, int ttl,
                PushSubscriptionRepository subscriptions, UserRepository users, MessageSource messages,
                Clock clock, Executor wysylka) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.messages = messages;
        this.clock = clock;
        this.ttl = ttl;
        this.vapid = klucze(publicKey, privateKey, subject, publicUrl);
        this.uslugi = new java.util.ArrayList<>(USLUGI);
        if (StringUtils.hasText(extraHosts)) {
            Arrays.stream(extraHosts.split(",")).map(String::trim).filter(StringUtils::hasText).forEach(uslugi::add);
        }
        this.wysylka = wysylka != null ? wysylka : Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "push");
            t.setDaemon(true);
            return t;
        });
        if (vapid != null) {
            log.info("Powiadomienia push: wlaczone");
        } else {
            log.info("Powiadomienia push: wylaczone (brak VAPID_PUBLIC_KEY i VAPID_PRIVATE_KEY)");
        }
    }

    /**
     * Bez kluczy push jest wylaczony. Z jednym kluczem, zlym kluczem albo
     * bez kontaktu - serwer nie wstaje: inaczej wszystko wygladaloby na
     * dzialajace, a zadne powiadomienie by nie doszlo.
     */
    private static Vapid klucze(String publicKey, String privateKey, String subject, String publicUrl) {
        boolean jest = StringUtils.hasText(publicKey);
        boolean prywatny = StringUtils.hasText(privateKey);
        if (!jest && !prywatny) {
            return null;
        }
        if (jest != prywatny) {
            throw new IllegalStateException("Push: ustaw oba klucze - VAPID_PUBLIC_KEY i VAPID_PRIVATE_KEY");
        }
        String kontakt = StringUtils.hasText(subject) ? subject.trim()
            : (publicUrl != null && publicUrl.startsWith("https://") ? publicUrl.trim() : null);
        if (kontakt == null) {
            throw new IllegalStateException(
                "Push: ustaw VAPID_SUBJECT, np. mailto:admin@twojadomena.pl (albo APP_PUBLIC_URL z https://)");
        }
        try {
            return new Vapid(publicKey.trim(), privateKey.trim(), kontakt);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Push: " + e.getMessage(), e);
        }
    }

    public boolean enabled() {
        return vapid != null;
    }

    /* ------------------------------------------------------------------ */
    /*  Ustawienia i urzadzenia                                            */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public PushSettingsResponse settings(String username) {
        User user = user(username);
        return new PushSettingsResponse(enabled(), enabled() ? vapid.publicKey() : null,
            user.isEventReminders(), subscriptions.countActive(user.getId()));
    }

    @Transactional
    public PushSettingsResponse updateReminders(String username, boolean eventReminders) {
        user(username).setEventReminders(eventReminders);
        return settings(username);
    }

    /** Zapis urzadzenia. Ten sam adres drugi raz - aktualizacja (i ewentualnie nowy wlasciciel). */
    @Transactional
    public void subscribe(String username, PushSubscribeRequest request) {
        if (!enabled()) {
            throw OperationNotAllowedException.pushDisabled();
        }
        User user = user(username);
        URI adres = adres(request.endpoint());
        if (adres == null) {
            throw OperationNotAllowedException.pushInvalid();
        }
        try {
            P256.publicKey(P256.fromBase64(request.p256dh()));
            if (P256.fromBase64(request.auth()).length != 16) {
                throw OperationNotAllowedException.pushInvalid();
            }
        } catch (IllegalArgumentException e) {
            throw OperationNotAllowedException.pushInvalid();
        }
        String lang = request.lang() != null && request.lang().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "pl";

        subscriptions.findByEndpoint(request.endpoint()).ifPresentOrElse(
            s -> s.update(user, request.p256dh(), request.auth(), lang),
            () -> subscriptions.save(new PushSubscription(user, request.endpoint(), request.p256dh(),
                request.auth(), lang, LocalDateTime.now(clock))));
        subscriptions.flush();

        List<PushSubscription> moje = subscriptions.findByUserIdOrderByCreatedAtAsc(user.getId());
        if (moje.size() > MAX_URZADZEN) {
            subscriptions.deleteAll(moje.subList(0, moje.size() - MAX_URZADZEN));
        }
    }

    /** Wylaczenie na tym urzadzeniu (albo wylogowanie). Cudzego adresu nie ruszamy. */
    @Transactional
    public void unsubscribe(String username, String endpoint) {
        User user = user(username);
        subscriptions.findByEndpoint(endpoint)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .ifPresent(subscriptions::delete);
    }

    /** Usuniecie konta albo "wyloguj wszedzie" - zadne urzadzenie nie dostaje juz nic. */
    @Transactional
    public void deleteAllOf(Long userId) {
        subscriptions.deleteAllOfUser(userId);
    }

    /** Probne powiadomienie na wszystkie moje urzadzenia. */
    @Transactional(readOnly = true)
    public void sendTest(String username) {
        if (!enabled()) {
            throw OperationNotAllowedException.pushDisabled();
        }
        User user = user(username);
        if (subscriptions.countActive(user.getId()) == 0) {
            throw OperationNotAllowedException.pushNoDevices();
        }
        Instant teraz = clock.instant();
        Instant poprzednia = ostatniaProba.get(user.getId());
        if (poprzednia != null && poprzednia.plus(ODSTEP_PROBY).isAfter(teraz)) {
            throw new TooManyRequestsException(
                Duration.between(teraz, poprzednia.plus(ODSTEP_PROBY)).toSeconds() + 1);
        }
        ostatniaProba.put(user.getId(), teraz);
        send(user.getId(), new Message("push.test.title", null, "push.test.body", null, "/settings", "test"));
    }

    /* ------------------------------------------------------------------ */
    /*  Wysylka                                                            */
    /* ------------------------------------------------------------------ */

    /** Wysyla na wszystkie urzadzenia osoby - po zatwierdzeniu transakcji, w tle. */
    public void send(Long userId, Message message) {
        if (!enabled()) {
            return;
        }
        Runnable wyslij = () -> wysylka.execute(() -> wyslijTeraz(userId, message));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wyslij.run();
                }
            });
        } else {
            wyslij.run();
        }
    }

    private void wyslijTeraz(Long userId, Message message) {
        subscriptions.deleteStale(userId);
        for (PushSubscription s : subscriptions.active(userId)) {
            try {
                dostarcz(s, message);
            } catch (Exception e) {
                log.warn("Push: nie udalo sie wyslac do {}: {}", host(s.getEndpoint()), e.getMessage());
            }
        }
    }

    private void dostarcz(PushSubscription s, Message message) throws Exception {
        URI adres = adres(s.getEndpoint());
        if (adres == null) {
            // Lista uslug sie zmienila - taki adres i tak juz nic nie dostanie
            subscriptions.deleteByEndpoint(s.getEndpoint());
            return;
        }
        byte[] tresc = WebPushEncryption.encrypt(tresc(message, s.getLang()),
            P256.fromBase64(s.getP256dh()), P256.fromBase64(s.getAuth()));

        HttpRequest zapytanie = HttpRequest.newBuilder(adres)
            .timeout(Duration.ofSeconds(15))
            .header("TTL", String.valueOf(ttl))
            .header("Urgency", "normal")
            .header("Content-Encoding", "aes128gcm")
            .header("Content-Type", "application/octet-stream")
            .header("Authorization", vapid.authorization(adres, clock.instant()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(tresc))
            .build();
        int kod = http.send(zapytanie, HttpResponse.BodyHandlers.discarding()).statusCode();

        if (kod == 404 || kod == 410) {
            // Przegladarka wypisala sie (albo wyczyszczono jej dane) - adres jest martwy
            subscriptions.deleteByEndpoint(s.getEndpoint());
            log.info("Push: {} nie zna juz subskrypcji - usunieta", adres.getHost());
        } else if (kod >= 400) {
            log.warn("Push: {} odpowiedzial {}", adres.getHost(), kod);
        }
    }

    /** JSON dla service workera; tresc przycinana, gdyby nie miescila sie w jednym rekordzie. */
    byte[] tresc(Message message, String lang) throws JsonProcessingException {
        Locale jezyk = Locale.forLanguageTag(lang);
        Map<String, Object> dane = new LinkedHashMap<>();
        dane.put("title", messages.getMessage(message.titleKey(), message.titleArgs(), jezyk));
        String body = message.bodyKey() == null ? null : messages.getMessage(message.bodyKey(), message.bodyArgs(), jezyk);
        if (body != null && body.length() > 500) {
            body = body.substring(0, 499) + "…";
        }
        dane.put("body", body);
        dane.put("url", message.url());
        dane.put("tag", message.tag());
        return JSON.writeValueAsString(dane).getBytes(StandardCharsets.UTF_8);
    }

    /** Adres uslugi push albo null, gdy nie jest na liscie (albo to w ogole nie adres). */
    URI adres(String endpoint) {
        if (endpoint == null || endpoint.length() > 1000) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (Exception e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        boolean naLiscie = false;
        for (String u : uslugi) {
            if (u.startsWith(".") ? host.endsWith(u) : host.equals(u)) {
                naLiscie = true;
                break;
            }
        }
        if (!naLiscie) {
            return null;
        }
        // http tylko dla lokalnego udawanego serwera, dopisanego recznie do listy
        boolean lokalny = host.equals("localhost") || host.equals("127.0.0.1");
        if (!"https".equals(uri.getScheme()) && !(lokalny && "http".equals(uri.getScheme()))) {
            return null;
        }
        return uri;
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (Exception e) {
            return "?";
        }
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
