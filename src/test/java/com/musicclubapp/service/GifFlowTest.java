package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.User;
import com.musicclubapp.gif.GifItem;
import com.musicclubapp.gif.GifSigner;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GIF-y od wyszukiwania do rozmowy: udawany dostawca, prawdziwy kontekst Springa, komentarze i czat. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GIF-y w komentarzach i wiadomosciach")
class GifFlowTest {

    private static final String REMEMBER_ME_KEY = "musicclub-dev-key-zmien-mnie";

    private static final String ODPOWIEDZ = """
        {"data":{"data":[
          {"id":1,"title":"Kot na pianinie","file":{
            "md":{"gif":{"url":"https://static.example/kot.gif","width":320,"height":180}},
            "sm":{"gif":{"url":"https://static.example/kot-s.gif","width":200,"height":112}}}},
          {"id":2,"title":"Pies na gitarze","file":{
            "md":{"gif":{"url":"https://static.example/pies.gif","width":300,"height":300}},
            "sm":{"gif":{"url":"https://static.example/pies-s.gif","width":150,"height":150}}}}
        ],"current_page":1,"has_next":true}}""";

    private static final TestHttpServer DOSTAWCA;

    static {
        try {
            DOSTAWCA = new TestHttpServer();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        DOSTAWCA.odpowiadaj("/api/v1/testowy-klucz/gifs/search", zapytanie -> zapytanie.contains("q=awaria")
            ? new TestHttpServer.Odpowiedz(500, "{}")
            : TestHttpServer.Odpowiedz.ok(ODPOWIEDZ));
        DOSTAWCA.odpowiadaj("/api/v1/testowy-klucz/gifs/trending", ODPOWIEDZ);
    }

    @DynamicPropertySource
    static void dostawca(DynamicPropertyRegistry registry) {
        registry.add("app.gifs.provider", () -> "klipy");
        registry.add("app.gifs.api-key", () -> "testowy-klucz");
        registry.add("app.gifs.klipy.base-url", () -> DOSTAWCA.url() + "/api/v1");
        registry.add("app.gifs.searches-per-minute", () -> "5");
    }

    @AfterAll
    static void zamknij() {
        DOSTAWCA.close();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private MessageRepository messages;
    @Autowired private com.musicclubapp.repository.ClanMessageRepository clanMessages;
    @Autowired private com.musicclubapp.repository.ClanInvitationRepository clanInvitations;
    @Autowired private com.musicclubapp.repository.ReportRepository reports;
    @Autowired private EntityManager em;

    @MockBean private PushService push;

    private User ala;
    private User bob;
    private Post post;

    @BeforeEach
    void setUp() {
        ala = users.save(new User("gf_ala", "gf_ala@example.com", "x"));
        bob = users.save(new User("gf_bob", "gf_bob@example.com", "x"));
        ala.addFriend(bob);
        users.save(ala);
        users.save(bob);
        post = posts.save(new Post(ala, "post alicji"));
        em.flush();
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions pobierz(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String kto, String adres, Object tresc) throws Exception {
        return mvc.perform(post(adres).with(user(kto)).with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Token pierwszego wyniku wyszukiwania - dokladnie to, co wysyla przegladarka. */
    private String token(String kto) throws Exception {
        return tresc(pobierz(kto, "/api/gifs/search?q=kot").andExpect(status().isOk())).get("items").get(0).get("token").asText();
    }

    private static String podmien(String token) {
        // zmieniamy dane (adres), zostawiajac stary podpis
        String nowe = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"u\":\"https://evil.example/pixel.gif\",\"p\":\"https://evil.example/pixel.gif\",\"w\":1,\"h\":1,\"t\":\"\"}"
                .getBytes(StandardCharsets.UTF_8));
        return nowe + token.substring(token.indexOf('.'));
    }

    private static void polePrzyBledzie(JsonNode blad, String pole) {
        assertThat(blad.get("errors").get(0).get("field").asText()).isEqualTo(pole);
    }

    /* ---------------------------- wyszukiwanie ---------------------------- */

    @Test
    @DisplayName("status: GIF-y wlaczone, podpis dostawcy do pokazania pod przegladarka")
    void statusEndpoint() throws Exception {
        JsonNode s = tresc(pobierz("gf_ala", "/api/gifs/status").andExpect(status().isOk()));
        assertThat(s.get("enabled").asBoolean()).isTrue();
        assertThat(s.get("attribution").asText()).isEqualTo("KLIPY");
    }

    @Test
    @DisplayName("wyszukiwanie tylko dla zalogowanych")
    void searchNeedsLogin() throws Exception {
        mvc.perform(get("/api/gifs/search?q=kot")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/gifs/status")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("wyszukiwanie: wyniki z podgladem, wymiarami, tokenem i znacznikiem nastepnej strony; fraza trafia do dostawcy")
    void search() throws Exception {
        JsonNode strona = tresc(pobierz("gf_ala", "/api/gifs/search?q=kot na pianinie&limit=12").andExpect(status().isOk()));
        assertThat(strona.get("items")).hasSize(2);
        JsonNode kot = strona.get("items").get(0);
        assertThat(kot.get("title").asText()).isEqualTo("Kot na pianinie");
        assertThat(kot.get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(kot.get("previewUrl").asText()).isEqualTo("https://static.example/kot-s.gif");
        assertThat(kot.get("width").asInt()).isEqualTo(320);
        assertThat(kot.get("token").asText()).contains(".");
        assertThat(strona.get("next").asText()).isEqualTo("2");
        assertThat(DOSTAWCA.requests().stream().anyMatch(z -> z.contains("q=kot na pianinie") && z.contains("per_page=12")))
            .as("zapytania do dostawcy: %s", DOSTAWCA.requests()).isTrue();
    }

    @Test
    @DisplayName("rozmiar strony: serwer nie przepuszcza wiecej niz 30, a zero i wartosci ujemne to minimum")
    void limitIsClamped() throws Exception {
        pobierz("gf_ala", "/api/gifs/search?q=limit-duzy&limit=1000").andExpect(status().isOk());
        pobierz("gf_ala", "/api/gifs/search?q=limit-maly&limit=-4").andExpect(status().isOk());
        assertThat(DOSTAWCA.requests().stream().anyMatch(z -> z.contains("q=limit-duzy") && z.contains("per_page=30"))).isTrue();
        // KLIPY ma dolna granice 8 - provider podnosi 1 do 8
        assertThat(DOSTAWCA.requests().stream().anyMatch(z -> z.contains("q=limit-maly") && z.contains("per_page=8"))).isTrue();
    }

    @Test
    @DisplayName("pusta fraza pokazuje popularne")
    void trending() throws Exception {
        pobierz("gf_ala", "/api/gifs/search").andExpect(status().isOk());
        assertThat(DOSTAWCA.requests().stream().anyMatch(z -> z.contains("/gifs/trending"))).isTrue();
    }

    @Test
    @DisplayName("awaria dostawcy to 503 z zrozumialym komunikatem, a nie 500")
    void providerOutage() throws Exception {
        String odpowiedz = pobierz("gf_ala", "/api/gifs/search?q=awaria").andExpect(status().isServiceUnavailable())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(odpowiedz).contains("Usługa GIF-ów nie odpowiada").doesNotContain("testowy-klucz");
    }

    @Test
    @DisplayName("limit wyszukiwan: szosta w minucie to 429 z Retry-After")
    void rateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            pobierz("gf_bob", "/api/gifs/search?q=kot" + i).andExpect(status().isOk());
        }
        pobierz("gf_bob", "/api/gifs/search?q=kot").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    /* ---------------------------- komentarze ---------------------------- */

    @Test
    @DisplayName("komentarz z samym GIF-em: 201, GIF w odpowiedzi i na liscie, tresc pusta")
    void gifOnlyComment() throws Exception {
        JsonNode k = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of("gif", token("gf_bob")))
            .andExpect(status().isCreated()));
        assertThat(k.get("content").asText()).isEmpty();
        assertThat(k.get("gif").get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(k.get("gif").get("previewUrl").asText()).isEqualTo("https://static.example/kot-s.gif");
        assertThat(k.get("gif").get("width").asInt()).isEqualTo(320);
        assertThat(k.get("gif").get("title").asText()).isEqualTo("Kot na pianinie");

        em.flush();
        em.clear();
        JsonNode lista = tresc(pobierz("gf_ala", "/api/posts/" + post.getId() + "/comments").andExpect(status().isOk()));
        assertThat(lista.get("content").get(0).get("gif").get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(lista.get("totalComments").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("komentarz z tekstem i GIF-em naraz; zwykly komentarz nie ma pola gif")
    void textAndGif() throws Exception {
        JsonNode oba = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "hej @gf_ala", "gif", token("gf_bob"))).andExpect(status().isCreated()));
        assertThat(oba.get("content").asText()).isEqualTo("hej @gf_ala");
        assertThat(oba.get("gif").get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(oba.get("mentions").toString()).isEqualTo("[\"gf_ala\"]");

        JsonNode zwykly = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "sam tekst"))
            .andExpect(status().isCreated()));
        assertThat(zwykly.get("gif").isNull()).isTrue();
    }

    @Test
    @DisplayName("odpowiedz tez moze byc GIF-em")
    void gifReply() throws Exception {
        long rodzic = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "pierwszy"))
            .andExpect(status().isCreated())).get("id").asLong();
        JsonNode odp = tresc(wyslij("gf_ala", "/api/posts/" + post.getId() + "/comments",
            Map.of("parentId", rodzic, "gif", token("gf_ala"))).andExpect(status().isCreated()));
        assertThat(odp.get("parentId").asLong()).isEqualTo(rodzic);
        assertThat(odp.get("replyToUsername").asText()).isEqualTo("gf_bob");
        assertThat(odp.get("gif")).isNotNull();
    }

    @Test
    @DisplayName("podrobiony, cudzy albo zepsuty token to 422 na polu gif i nic sie nie zapisuje")
    void forgedTokens() throws Exception {
        String dobry = token("gf_bob");
        String obcy = new GifSigner("inny-sekret").sign(new GifItem("1", "", "https://static.example/kot.gif",
            "https://static.example/kot-s.gif", 1, 1));
        long przed = comments.count();

        for (String zly : new String[] {podmien(dobry), obcy, "to-nie-jest-token", dobry.substring(0, dobry.length() - 3) + "AAA"}) {
            JsonNode blad = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments",
                Map.of("content", "z podrobionym", "gif", zly)).andExpect(status().isUnprocessableEntity()));
            polePrzyBledzie(blad, "gif");
        }
        assertThat(comments.count()).isEqualTo(przed);
    }

    @Test
    @DisplayName("komentarz bez tekstu i bez GIF-a - 422 na polu content")
    void emptyComment() throws Exception {
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "   "))
            .andExpect(status().isUnprocessableEntity())), "content");
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "", "gif", ""))
            .andExpect(status().isUnprocessableEntity())), "content");
        wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments", Map.of()).andExpect(status().isUnprocessableEntity());
    }

    /* ---------------------------- wiadomosci ---------------------------- */

    @Test
    @DisplayName("wiadomosc z samym GIF-em do znajomego: w odpowiedzi, w historii i w podgladzie rozmowy")
    void gifMessage() throws Exception {
        JsonNode m = tresc(wyslij("gf_bob", "/api/messages/with/gf_ala", Map.of("gif", token("gf_bob")))
            .andExpect(status().isCreated()));
        assertThat(m.get("content").isNull()).isTrue();
        assertThat(m.get("gif").get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(m.get("mine").asBoolean()).isTrue();

        em.flush();
        em.clear();
        JsonNode historia = tresc(pobierz("gf_ala", "/api/messages/with/gf_bob").andExpect(status().isOk()));
        assertThat(historia.get("content").get(0).get("gif").get("previewUrl").asText()).isEqualTo("https://static.example/kot-s.gif");
        JsonNode rozmowy = tresc(pobierz("gf_ala", "/api/messages/conversations").andExpect(status().isOk()));
        assertThat(rozmowy.toString()).contains("https://static.example/kot.gif");
    }

    @Test
    @DisplayName("wiadomosc: tekst z GIF-em, podrobiony token (422 na polu gif) i pusta wiadomosc (422 na polu content)")
    void messageValidation() throws Exception {
        JsonNode oba = tresc(wyslij("gf_bob", "/api/messages/with/gf_ala", Map.of("content", "patrz", "gif", token("gf_bob")))
            .andExpect(status().isCreated()));
        assertThat(oba.get("content").asText()).isEqualTo("patrz");

        long przed = messages.count();
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/messages/with/gf_ala", Map.of("gif", podmien(token("gf_bob"))))
            .andExpect(status().isUnprocessableEntity())), "gif");
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/messages/with/gf_ala", Map.of("content", " "))
            .andExpect(status().isUnprocessableEntity())), "content");
        assertThat(messages.count()).isEqualTo(przed);
    }

    @Test
    @DisplayName("GIF dotyczy tylko znajomych jak cala rozmowa: obcy dostaje ten sam blad co przy zwyklej wiadomosci")
    void messageOnlyToFriends() throws Exception {
        users.save(new User("gf_obcy", "gf_obcy@example.com", "x"));
        em.flush();
        wyslij("gf_bob", "/api/messages/with/gf_obcy", Map.of("gif", token("gf_bob"))).andExpect(status().isConflict());
    }

    /* ---------------------------- czat klanu ---------------------------- */

    /** Klan alicji z bobem w srodku - przez API, jak w testach klanow. */
    private long klanAliBoba() throws Exception {
        long klan = tresc(wyslij("gf_ala", "/api/clans", Map.of("name", "Gifowe Sowy", "tag", "GS"))
            .andExpect(status().isCreated())).get("id").asLong();
        wyslij("gf_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "gf_bob")).andExpect(status().isOk());
        em.flush();
        long zaproszenie = clanInvitations.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals("gf_bob")).findFirst().orElseThrow().getId();
        wyslij("gf_bob", "/api/clans/invitations/" + zaproszenie + "/accept", Map.of()).andExpect(status().isOk());
        em.flush();
        return klan;
    }

    @Test
    @DisplayName("czat klanu: sam GIF (pusta tresc), tekst z GIF-em i odpowiedz na GIF - cytat wie, ze to GIF")
    void clanChatGif() throws Exception {
        long klan = klanAliBoba();
        JsonNode sam = tresc(wyslij("gf_bob", "/api/clans/" + klan + "/chat", Map.of("gif", token("gf_bob")))
            .andExpect(status().isCreated()));
        assertThat(sam.get("content").asText()).isEmpty();
        assertThat(sam.get("gif").get("url").asText()).isEqualTo("https://static.example/kot.gif");
        assertThat(sam.get("gif").get("width").asInt()).isEqualTo(320);

        JsonNode oba = tresc(wyslij("gf_ala", "/api/clans/" + klan + "/chat",
            Map.of("content", "dobre", "gif", token("gf_ala"), "replyTo", sam.get("id").asLong()))
            .andExpect(status().isCreated()));
        assertThat(oba.get("content").asText()).isEqualTo("dobre");
        assertThat(oba.get("replyTo").get("excerpt").asText()).isEmpty();
        assertThat(oba.get("replyTo").get("gif").asBoolean()).isTrue();

        em.flush();
        em.clear();
        JsonNode czat = tresc(pobierz("gf_bob", "/api/clans/" + klan + "/chat").andExpect(status().isOk()));
        assertThat(czat).hasSize(2);
        assertThat(czat.get(0).get("gif").get("previewUrl").asText()).isEqualTo("https://static.example/kot-s.gif");
        assertThat(czat.get(1).get("gif").get("title").asText()).isEqualTo("Kot na pianinie");
        // tekst bez GIF-a: pole gif puste, a cytat zwyklej wiadomosci nie jest GIF-em
        long tekst = tresc(wyslij("gf_bob", "/api/clans/" + klan + "/chat", Map.of("content", "sam tekst"))
            .andExpect(status().isCreated())).get("id").asLong();
        JsonNode naTekst = tresc(wyslij("gf_ala", "/api/clans/" + klan + "/chat",
            Map.of("content", "ok", "replyTo", tekst)).andExpect(status().isCreated()));
        assertThat(naTekst.get("gif").isNull()).isTrue();
        assertThat(naTekst.get("replyTo").get("gif").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("czat klanu: podrobiony token (422 na polu gif), pusta wiadomosc (422 z komunikatem bez nagrania), obcy - 409")
    void clanChatGifValidation() throws Exception {
        long klan = klanAliBoba();
        long przed = clanMessages.count();
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/clans/" + klan + "/chat", Map.of("gif", podmien(token("gf_bob"))))
            .andExpect(status().isUnprocessableEntity())), "gif");
        JsonNode pusta = tresc(wyslij("gf_bob", "/api/clans/" + klan + "/chat", Map.of("content", "  "))
            .andExpect(status().isUnprocessableEntity()));
        polePrzyBledzie(pusta, "content");
        assertThat(pusta.get("errors").get(0).get("message").asText()).isEqualTo("Napisz coś albo wybierz GIF");
        polePrzyBledzie(tresc(wyslij("gf_bob", "/api/clans/" + klan + "/chat", Map.of())
            .andExpect(status().isUnprocessableEntity())), "content");

        users.save(new User("gf_obcy", "gf_obcy@example.com", "x"));
        em.flush();
        wyslij("gf_obcy", "/api/clans/" + klan + "/chat", Map.of("gif", token("gf_obcy"))).andExpect(status().isConflict());
        assertThat(clanMessages.count()).isEqualTo(przed);
    }

    /* ---------------------------- moderacja ---------------------------- */

    @Test
    @DisplayName("zgloszenie komentarza i rozmowy: dowod zawiera adres GIF-a, bo plik moze zniknac u dostawcy")
    void reportEvidenceHasTheGif() throws Exception {
        long k = tresc(wyslij("gf_bob", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "patrz", "gif", token("gf_bob"))).andExpect(status().isCreated())).get("id").asLong();
        wyslij("gf_bob", "/api/messages/with/gf_ala", Map.of("gif", token("gf_bob"))).andExpect(status().isCreated());
        em.flush();

        // jedno otwarte zgloszenie na pare osob, wiec komentarz zglasza ktos trzeci
        users.save(new User("gf_cyd", "gf_cyd@example.com", "x"));
        em.flush();
        wyslij("gf_cyd", "/api/reports/on/gf_bob", Map.of("reason", "SPAM", "context", "COMMENT", "commentId", k,
            "description", "zgloszenie komentarza z GIF-em")).andExpect(status().isCreated());
        wyslij("gf_ala", "/api/reports/on/gf_bob", Map.of("reason", "SPAM", "context", "CONVERSATION",
            "description", "zgloszenie rozmowy z GIF-em")).andExpect(status().isCreated());
        em.flush();
        em.clear();

        var wszystkie = reports.findAll();
        assertThat(wszystkie).hasSize(2);
        for (var raport : wszystkie) {
            assertThat(raport.getEvidence().stream().map(e -> e.getText()).toList())
                .anyMatch(t -> t.contains("[GIF] https://static.example/kot.gif (Kot na pianinie)"));
        }
    }
}
