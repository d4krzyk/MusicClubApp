package com.musicclubapp.gif;

import com.musicclubapp.error.GifUnavailableException;
import com.musicclubapp.service.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Oba adaptery na udawanym serwerze - ksztalt odpowiedzi jak w dokumentacji dostawcow. */
@DisplayName("Dostawcy GIF-ow: KLIPY i GIPHY")
class GifProvidersTest {

    private static final String KLUCZ = "sekretny-klucz-123";

    private TestHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new TestHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    /* ------------------------------ KLIPY ------------------------------ */

    private static final String KLIPY = """
        {"result":true,"data":{"data":[
          {"id":101,"slug":"kot","title":"Kot   gra\\n na pianinie","file":{
            "hd":{"gif":{"url":"https://static.example/hd.gif","width":498,"height":280}},
            "md":{"gif":{"url":"https://static.example/md.gif","width":320,"height":180},"mp4":{"url":"https://static.example/md.mp4"}},
            "sm":{"gif":{"url":"https://static.example/sm.gif","width":200,"height":112}},
            "xs":{"gif":{"url":"https://static.example/xs.gif","width":90,"height":50}}}},
          {"id":102,"title":"tylko webp","file":{"md":{"webp":{"url":"https://static.example/w.webp","width":300,"height":300}}}},
          {"id":103,"title":"bez pliku","file":{}},
          {"id":104,"title":"obcy schemat","file":{"md":{"gif":{"url":"http://evil.example/p.gif","width":1,"height":1}}}},
          {"id":105,"title":"javascript","file":{"md":{"gif":{"url":"javascript:alert(1)","width":1,"height":1}}}}
        ],"current_page":1,"per_page":24,"has_next":true}}
        """;

    private KlipyProvider klipy() {
        return new KlipyProvider(server.url() + "/api/v1", KLUCZ, 2000);
    }

    @Test
    @DisplayName("KLIPY: wynik ma plik do rozmowy (md) i mniejszy na liste (sm), a strona - znacznik nastepnej")
    void klipySearch() {
        server.odpowiadaj("/api/v1/" + KLUCZ + "/gifs/search", KLIPY);
        GifPage page = klipy().search("kot", null, 3, "pl", "pseudo");

        assertThat(page.items()).extracting(GifItem::id).containsExactly("101", "102");
        GifItem kot = page.items().get(0);
        assertThat(kot.url()).isEqualTo("https://static.example/md.gif");
        assertThat(kot.previewUrl()).isEqualTo("https://static.example/sm.gif");
        assertThat(kot.width()).isEqualTo(320);
        assertThat(kot.height()).isEqualTo(180);
        assertThat(kot.title()).isEqualTo("Kot gra na pianinie");
        // sam webp tez wystarcza, a brak pliku i adresy spoza https odpadaja
        assertThat(page.items().get(1).url()).isEqualTo("https://static.example/w.webp");
        assertThat(page.next()).isEqualTo("2");

        String zapytanie = server.requests().get(0);
        assertThat(zapytanie).contains("q=kot").contains("page=1").contains("per_page=8")
            .contains("customer_id=pseudo").contains("locale=pl");
    }

    @Test
    @DisplayName("KLIPY: kolejna strona, polskie znaki we frazie i brak nastepnej strony")
    void klipyPagingAndEncoding() {
        server.odpowiadaj("/api/v1/" + KLUCZ + "/gifs/search", """
            {"result":true,"data":{"data":[],"current_page":3,"has_next":false}}""");
        GifPage page = klipy().search("zażółć gęślą & jaźń", "3", 20, "pl", "p");

        assertThat(page.items()).isEmpty();
        assertThat(page.next()).isNull();
        assertThat(server.requests().get(0)).contains("q=zażółć gęślą & jaźń").contains("page=3").contains("per_page=20");
    }

    @Test
    @DisplayName("KLIPY: popularne to osobny adres, bez frazy; limit zawsze miesci sie w 8-50")
    void klipyTrending() {
        server.odpowiadaj("/api/v1/" + KLUCZ + "/gifs/trending", KLIPY);
        klipy().trending(null, 500, "en", "p");
        assertThat(server.requests().get(0)).startsWith("/api/v1/" + KLUCZ + "/gifs/trending").doesNotContain("q=")
            .contains("per_page=50");
    }

    @Test
    @DisplayName("KLIPY: nieznany ksztalt odpowiedzi to pusta lista (z ostrzezeniem w logu), a nie wyjatek")
    void klipyUnknownShape() {
        server.odpowiadaj("/api/v1/" + KLUCZ + "/gifs/search", "{\"cos\":\"innego\"}");
        GifPage page = klipy().search("kot", null, 20, "pl", "p");
        assertThat(page.items()).isEmpty();
        assertThat(page.next()).isNull();
    }

    @Test
    @DisplayName("KLIPY: odmowa dostawcy to GifUnavailableException, w ktorej nie ma klucza")
    void klipyRefusalNeverLeaksTheKey() {
        server.odpowiadaj("/api/v1/" + KLUCZ + "/gifs/search",
            q -> new TestHttpServer.Odpowiedz(401, "{\"error\":\"" + KLUCZ + " is invalid\"}"));
        assertThatThrownBy(() -> klipy().search("kot", null, 20, "pl", "p"))
            .isInstanceOf(GifUnavailableException.class)
            .hasMessageContaining("HTTP 401")
            .hasMessageNotContaining(KLUCZ);
    }

    @Test
    @DisplayName("KLIPY: serwer nie odpowiada - blad bez klucza w tresci")
    void klipyConnectionFailureNeverLeaksTheKey() {
        String adres = server.url();
        server.close();
        KlipyProvider martwy = new KlipyProvider(adres + "/api/v1", KLUCZ, 500);
        assertThatThrownBy(() -> martwy.search("kot", null, 20, "pl", "p"))
            .isInstanceOf(GifUnavailableException.class)
            .hasMessageNotContaining(KLUCZ);
    }

    /* ------------------------------ GIPHY ------------------------------ */

    private static final String GIPHY = """
        {"data":[
          {"id":"abc","title":"cat GIF by Studio","images":{
            "fixed_height":{"url":"https://media.example/fh.gif","width":"356","height":"200"},
            "fixed_width":{"url":"https://media.example/fw.gif","width":"200","height":"112"},
            "original":{"url":"https://media.example/o.gif","width":"800","height":"450"}}},
          {"id":"def","title":"tylko oryginal","images":{"original":{"url":"https://media.example/o2.gif","width":"640","height":"360"}}},
          {"id":"ghi","title":"bez obrazow","images":{}},
          {"id":"jkl","title":"obcy","images":{"fixed_height":{"url":"ftp://x/y.gif","width":"1","height":"1"}}}
        ],"pagination":{"total_count":120,"count":20,"offset":0},"meta":{"status":200}}
        """;

    private GiphyProvider giphy() {
        return new GiphyProvider(server.url() + "/v1", KLUCZ, "pg-13", 2000);
    }

    @Test
    @DisplayName("GIPHY: fixed_height do rozmowy, fixed_width na liste, wymiary z napisow, nastepna strona z offsetu")
    void giphySearch() {
        server.odpowiadaj("/v1/gifs/search", GIPHY);
        GifPage page = giphy().search("cat", null, 20, "pl", "pseudo");

        assertThat(page.items()).extracting(GifItem::id).containsExactly("abc", "def");
        GifItem cat = page.items().get(0);
        assertThat(cat.url()).isEqualTo("https://media.example/fh.gif");
        assertThat(cat.previewUrl()).isEqualTo("https://media.example/fw.gif");
        assertThat(cat.width()).isEqualTo(356);
        assertThat(cat.height()).isEqualTo(200);
        assertThat(page.items().get(1).url()).isEqualTo("https://media.example/o2.gif");
        assertThat(page.next()).isEqualTo("20");

        assertThat(server.requests().get(0)).contains("api_key=" + KLUCZ).contains("q=cat").contains("limit=20")
            .contains("offset=0").contains("rating=pg-13").contains("lang=pl").contains("random_id=pseudo");
    }

    @Test
    @DisplayName("GIPHY: ostatnia strona nie ma znacznika, offset z poprzedniej strony wraca w zapytaniu")
    void giphyLastPage() {
        server.odpowiadaj("/v1/gifs/search", """
            {"data":[],"pagination":{"total_count":25,"count":5,"offset":20}}""");
        GifPage page = giphy().search("cat", "20", 20, "pl", "p");
        assertThat(page.next()).isNull();
        assertThat(server.requests().get(0)).contains("offset=20");
    }

    @Test
    @DisplayName("GIPHY: popularne bez frazy i bez jezyka; odmowa nie zdradza klucza")
    void giphyTrendingAndRefusal() {
        server.odpowiadaj("/v1/gifs/trending", GIPHY);
        giphy().trending(null, 10, "pl", "p");
        assertThat(server.requests().get(0)).startsWith("/v1/gifs/trending").doesNotContain("q=").doesNotContain("lang=");

        server.odpowiadaj("/v1/gifs/search", q -> new TestHttpServer.Odpowiedz(429, "{}"));
        assertThatThrownBy(() -> giphy().search("cat", null, 20, "pl", "p"))
            .isInstanceOf(GifUnavailableException.class)
            .hasMessageContaining("HTTP 429")
            .hasMessageNotContaining(KLUCZ);
    }

    @Test
    @DisplayName("GIPHY: nieznany ksztalt odpowiedzi - pusta lista")
    void giphyUnknownShape() {
        server.odpowiadaj("/v1/gifs/search", "{\"foo\":1}");
        assertThat(giphy().search("cat", null, 20, "pl", "p").items()).isEmpty();
    }
}
