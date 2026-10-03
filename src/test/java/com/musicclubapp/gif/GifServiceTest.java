package com.musicclubapp.gif;

import com.musicclubapp.dto.GifPageResponse;
import com.musicclubapp.entity.GifAttachment;
import com.musicclubapp.error.GifUnavailableException;
import com.musicclubapp.error.InvalidGifException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.service.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GifService - pamiec, limity, podpisy i wylaczenie")
class GifServiceTest {

    private static final String SEKRET = "klucz-do-podpisow";

    /** Zegar, ktory stoi, dopoki test go nie przesunie. */
    static final class RucomyZegar extends Clock {
        private long millis = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();

        void przesun(long ms) {
            millis += ms;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    private static final String ODPOWIEDZ = """
        {"data":{"data":[{"id":7,"title":"kot","file":{
          "md":{"gif":{"url":"https://static.example/md.gif","width":320,"height":180}},
          "sm":{"gif":{"url":"https://static.example/sm.gif","width":200,"height":112}}}}],
          "current_page":1,"has_next":false}}""";

    private TestHttpServer server;
    private RucomyZegar zegar;

    @BeforeEach
    void start() throws Exception {
        server = new TestHttpServer();
        server.odpowiadaj("/api/v1/klucz/gifs/search", ODPOWIEDZ);
        server.odpowiadaj("/api/v1/klucz/gifs/trending", ODPOWIEDZ);
        zegar = new RucomyZegar();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private GifService service(String provider, String key, int perMinute, int cacheSeconds) {
        return new GifService(provider, key, server.url() + "/api/v1", server.url() + "/v1", "pg-13", 2000,
            perMinute, cacheSeconds, SEKRET, zegar);
    }

    @Test
    @DisplayName("bez klucza GIF-y sa wylaczone: szukanie i dolaczanie koncza sie bledem, pusty token to po prostu brak GIF-a")
    void disabledWithoutKey() {
        GifService wylaczony = service("klipy", "", 30, 300);

        assertThat(wylaczony.enabled()).isFalse();
        assertThat(wylaczony.attribution()).isNull();
        assertThatThrownBy(() -> wylaczony.search(1L, "kot", null, 20, "pl")).isInstanceOf(GifUnavailableException.class);
        assertThatThrownBy(() -> wylaczony.attach("jakis.token")).isInstanceOf(GifUnavailableException.class);
        assertThat(wylaczony.attach(null)).isNull();
        assertThat(wylaczony.attach("  ")).isNull();
        assertThat(server.requests()).isEmpty();
    }

    @Test
    @DisplayName("pusty dostawca tez wylacza; nieznany dostawca to blad przy starcie, a nie cicha awaria pozniej")
    void providerNames() {
        assertThat(service("", "klucz", 30, 300).enabled()).isFalse();
        assertThat(service("KLIPY", "klucz", 30, 300).attribution()).isEqualTo("KLIPY");
        assertThat(service(" giphy ", "klucz", 30, 300).attribution()).isEqualTo("GIPHY");
        assertThatThrownBy(() -> service("tenor", "klucz", 30, 300))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("tenor");
    }

    @Test
    @DisplayName("wyniki maja token, ktory serwer potem przyjmuje jako GIF; token z innego klucza - nie")
    void searchReturnsSignedResults() {
        GifService gify = service("klipy", "klucz", 30, 300);
        GifPageResponse page = gify.search(1L, "kot", null, 20, "pl");

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).previewUrl()).isEqualTo("https://static.example/sm.gif");
        GifAttachment gif = gify.attach(page.items().get(0).token());
        assertThat(gif.getUrl()).isEqualTo("https://static.example/md.gif");

        // biale znaki wokol tokenu (np. z wklejenia) nie psuja podpisu
        assertThat(gify.attach("  " + page.items().get(0).token() + " \n").getUrl()).isEqualTo("https://static.example/md.gif");

        String obcy = new GifSigner("inny-sekret").sign(new GifItem("1", "", "https://x.example/a.gif",
            "https://x.example/b.gif", 1, 1));
        assertThatThrownBy(() -> gify.attach(obcy)).isInstanceOf(InvalidGifException.class);
    }

    @Test
    @DisplayName("pusta fraza to popularne, a fraza jest czyszczona z nadmiaru spacji i ucinana do 80 znakow")
    void queryHandling() {
        GifService gify = service("klipy", "klucz", 30, 300);
        gify.search(1L, "   ", null, 20, "pl");
        assertThat(server.requests().get(0)).contains("/gifs/trending");

        gify.search(1L, "  kot    na   pianinie ", null, 20, "pl");
        assertThat(server.requests().get(1)).contains("q=kot na pianinie").contains("/gifs/search");

        assertThat(GifService.normalize("a".repeat(200))).hasSize(GifService.MAX_QUERY);
        assertThat(GifService.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("to samo pytanie w czasie waznosci idzie z pamieci; po uplywie czasu albo inne - do dostawcy")
    void cache() {
        GifService gify = service("klipy", "klucz", 30, 300);
        gify.search(1L, "kot", null, 20, "pl");
        gify.search(2L, "kot", null, 20, "pl");   // inna osoba, to samo pytanie - z pamieci
        assertThat(server.requests()).hasSize(1);

        gify.search(1L, "pies", null, 20, "pl");
        gify.search(1L, "kot", "2", 20, "pl");    // kolejna strona to inne pytanie
        gify.search(1L, "kot", null, 20, "en");   // inny jezyk tez
        assertThat(server.requests()).hasSize(4);

        zegar.przesun(299_000);
        gify.search(1L, "kot", null, 20, "pl");
        assertThat(server.requests()).hasSize(4);
        zegar.przesun(2_000);
        gify.search(1L, "kot", null, 20, "pl");
        assertThat(server.requests()).hasSize(5);
    }

    @Test
    @DisplayName("blad dostawcy nie zostaje w pamieci: po awarii kolejne pytanie znowu idzie do dostawcy")
    void failuresAreNotCached() {
        GifService gify = service("klipy", "klucz", 30, 300);
        // pierwsze pytanie dostaje 500, kolejne juz normalna odpowiedz
        AtomicInteger pytania = new AtomicInteger();
        server.odpowiadaj("/api/v1/klucz/gifs/search", q -> pytania.incrementAndGet() == 1
            ? new TestHttpServer.Odpowiedz(500, "{}")
            : TestHttpServer.Odpowiedz.ok(ODPOWIEDZ));
        assertThatThrownBy(() -> gify.search(1L, "kot", null, 20, "pl")).isInstanceOf(GifUnavailableException.class);

        assertThat(gify.search(1L, "kot", null, 20, "pl").items()).hasSize(1);
        assertThat(pytania.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("limit wyszukiwan jest na osobe i na minute, takze gdy odpowiedz idzie z pamieci")
    void rateLimit() {
        GifService gify = service("klipy", "klucz", 3, 300);
        for (int i = 0; i < 3; i++) {
            gify.search(1L, "kot", null, 20, "pl");
        }
        assertThatThrownBy(() -> gify.search(1L, "kot", null, 20, "pl")).isInstanceOf(TooManyRequestsException.class);
        // inna osoba ma swoj limit
        gify.search(2L, "kot", null, 20, "pl");

        zegar.przesun(61_000);
        gify.search(1L, "kot", null, 20, "pl");
    }
}
