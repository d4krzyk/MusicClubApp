package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.CommentMentionRepository;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Komentarze pod postami: odpowiedzi, oznaczenia, powiadomienia, widocznosc, blokady, kasowanie, zgloszenia -
 * przez prawdziwe API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Komentarze pod postami")
class CommentFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private CommentMentionRepository mentions;
    @Autowired private NotificationRepository notifications;
    @Autowired private ReportRepository reports;
    @Autowired private ClanRepository clans;
    @Autowired private ClanInvitationRepository invitations;
    @Autowired private AccountDeletionService deletion;
    @Autowired private EntityManager em;

    @MockBean private PushService push;
    @MockBean private MusicMetadataService metadata;

    private User ala;
    private User bob;
    private User cyd;
    private User dan;
    private Post post;

    @BeforeEach
    void setUp() {
        ala = osoba("co_ala");
        bob = osoba("co_bob");
        cyd = osoba("co_cyd");
        dan = osoba("co_dan");
        User szef = new User("co_szef", "co_szef@example.com", "x");
        szef.setRole(Role.ADMIN);
        users.save(szef);
        ala.addFriend(bob);
        ala.addFriend(cyd);
        users.save(ala);
        users.save(bob);
        users.save(cyd);
        post = posts.save(new Post(ala, "post alicji"));
        em.flush();
        reset(push);
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private User osoba(String login) {
        return users.save(new User(login, login + "@example.com", "x"));
    }

    private ResultActions get_(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var builder = switch (metoda) {
            case "PUT" -> put(adres);
            case "DELETE" -> delete(adres);
            default -> post(adres);
        };
        builder.with(kto.equals("co_szef") ? user(kto).roles("ADMIN") : user(kto)).with(csrf()).header("Accept-Language", "pl");
        if (tresc != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(builder);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long komentarz(String kto, long postId, String tekst) throws Exception {
        long id = tresc(wyslij("POST", kto, "/api/posts/" + postId + "/comments", Map.of("content", tekst))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private JsonNode odpowiedz(String kto, long postId, long naKtory, String tekst) throws Exception {
        JsonNode r = tresc(wyslij("POST", kto, "/api/posts/" + postId + "/comments",
            Map.of("content", tekst, "parentId", naKtory)).andExpect(status().isCreated()));
        em.flush();
        return r;
    }

    private JsonNode lista(String kto, long postId) throws Exception {
        return tresc(get_(kto, "/api/posts/" + postId + "/comments?size=30").andExpect(status().isOk()));
    }

    private static List<String> teksty(JsonNode tablica, String pole) {
        List<String> wynik = new ArrayList<>();
        tablica.forEach(w -> wynik.add(w.get(pole).asText()));
        return wynik;
    }

    private List<String> typy(String kto) throws Exception {
        return teksty(tresc(get_(kto, "/api/notifications").andExpect(status().isOk())).get("content"), "type");
    }

    /** Zaklada klan (zalozyciel) i przyjmuje do niego wskazane osoby; zwraca id klanu. */
    private long klanZ(String zalozyciel, String nazwa, String tag, String... czlonkowie) throws Exception {
        long klan = tresc(wyslij("POST", zalozyciel, "/api/clans", Map.of("name", nazwa, "tag", tag))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        for (String czlonek : czlonkowie) {
            wyslij("POST", zalozyciel, "/api/clans/" + klan + "/invitations", Map.of("username", czlonek)).andExpect(status().isOk());
            em.flush();
            long zaproszenie = invitations.findAll().stream()
                .filter(z -> z.getInvitee().getUsername().equals(czlonek)).findFirst().orElseThrow().getId();
            wyslij("POST", czlonek, "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
            em.flush();
        }
        em.clear();
        return klan;
    }

    private Post postKlanu(long klan, String autor, String tresc) {
        Post p = new Post(users.findByUsername(autor).orElseThrow(), tresc);
        p.setClan(clans.findById(klan).orElseThrow());
        posts.save(p);
        em.flush();
        return p;
    }

    /* ---------------------------- dodawanie i lista ---------------------------- */

    @Test
    @DisplayName("komentarz: dodanie, lista od najnowszych, flagi mine/canDelete, licznik przy poscie")
    void createAndList() throws Exception {
        long pierwszy = komentarz("co_bob", post.getId(), "pierwszy");
        long drugi = komentarz("co_cyd", post.getId(), "  drugi  ");

        JsonNode dlaBoba = lista("co_bob", post.getId());
        assertThat(teksty(dlaBoba.get("content"), "content")).containsExactly("drugi", "pierwszy");
        assertThat(dlaBoba.get("totalElements").asInt()).isEqualTo(2);
        assertThat(dlaBoba.get("totalComments").asInt()).isEqualTo(2);
        JsonNode jegoPierwszy = dlaBoba.get("content").get(1);
        assertThat(jegoPierwszy.get("id").asLong()).isEqualTo(pierwszy);
        assertThat(jegoPierwszy.get("mine").asBoolean()).isTrue();
        assertThat(jegoPierwszy.get("canDelete").asBoolean()).isTrue();
        assertThat(jegoPierwszy.get("parentId").isNull()).isTrue();
        assertThat(jegoPierwszy.get("replyCount").asInt()).isZero();
        // cudzy komentarz: bob nie moze go skasowac, ala (autor posta) moze
        assertThat(dlaBoba.get("content").get(0).get("mine").asBoolean()).isFalse();
        assertThat(dlaBoba.get("content").get(0).get("canDelete").asBoolean()).isFalse();
        assertThat(lista("co_ala", post.getId()).get("content").get(0).get("canDelete").asBoolean()).isTrue();

        // licznik w poscie (pojedynczy post i tablica)
        em.clear();
        assertThat(tresc(get_("co_ala", "/api/posts/" + post.getId())).get("commentCount").asInt()).isEqualTo(2);
        JsonNode tablica = tresc(get_("co_ala", "/api/posts?size=10&sort=NEWEST"));
        assertThat(tablica.get("content").get(0).get("commentCount").asInt()).isEqualTo(2);
        assertThat(drugi).isGreaterThan(pierwszy);
    }

    @Test
    @DisplayName("paginacja komentarzy pierwszego poziomu; totalComments liczy tez odpowiedzi")
    void paging() throws Exception {
        long k1 = komentarz("co_bob", post.getId(), "k1");
        komentarz("co_bob", post.getId(), "k2");
        komentarz("co_bob", post.getId(), "k3");
        odpowiedz("co_cyd", post.getId(), k1, "odp do k1");

        JsonNode pierwsza = tresc(get_("co_ala", "/api/posts/" + post.getId() + "/comments?page=0&size=2"));
        assertThat(teksty(pierwsza.get("content"), "content")).containsExactly("k3", "k2");
        assertThat(pierwsza.get("totalElements").asInt()).isEqualTo(3);
        assertThat(pierwsza.get("totalComments").asInt()).isEqualTo(4);
        assertThat(pierwsza.get("last").asBoolean()).isFalse();
        JsonNode druga = tresc(get_("co_ala", "/api/posts/" + post.getId() + "/comments?page=1&size=2"));
        assertThat(teksty(druga.get("content"), "content")).containsExactly("k1");
        assertThat(druga.get("last").asBoolean()).isTrue();
        assertThat(druga.get("content").get(0).get("replyCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("rozmiar strony jest ograniczony do 30, a zero i wartosci ujemne dzialaja jak 1")
    void pageSizeIsCapped() throws Exception {
        for (int i = 0; i < 35; i++) {
            comments.save(new com.musicclubapp.entity.Comment(post, bob, null, null, "k" + i));
        }
        em.flush();
        em.clear();
        assertThat(tresc(get_("co_ala", "/api/posts/" + post.getId() + "/comments?size=1000")).get("content").size()).isEqualTo(30);
        assertThat(tresc(get_("co_ala", "/api/posts/" + post.getId() + "/comments?size=0")).get("content").size()).isEqualTo(1);
        assertThat(tresc(get_("co_ala", "/api/posts/" + post.getId() + "/comments?size=-5&page=-3")).get("content").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("walidacja: pusty i za dlugi komentarz - 422, nic sie nie zapisuje; spacje obcinane")
    void validation() throws Exception {
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "   "))
            .andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "x".repeat(1001)))
            .andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "x".repeat(1000)))
            .andExpect(status().isCreated());
        assertThat(comments.count()).isEqualTo(1);
        mvc.perform(post("/api/posts/" + post.getId() + "/comments").with(user("co_bob")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnprocessableEntity());
    }

    /* ---------------------------- odpowiedzi ---------------------------- */

    @Test
    @DisplayName("odpowiedzi: jednopoziomowe; odpowiedz na odpowiedz wisi pod tym samym rodzicem i wie, do kogo jest")
    void replies() throws Exception {
        long korzen = komentarz("co_bob", post.getId(), "komentarz boba");
        JsonNode odp1 = odpowiedz("co_cyd", post.getId(), korzen, "odpowiedz cyda");
        assertThat(odp1.get("parentId").asLong()).isEqualTo(korzen);
        assertThat(odp1.get("replyToUsername").asText()).isEqualTo("co_bob");

        // odpowiedz na odpowiedz: nadal pod korzeniem, ale adresowana do cyda
        JsonNode odp2 = odpowiedz("co_ala", post.getId(), odp1.get("id").asLong(), "ala do cyda");
        assertThat(odp2.get("parentId").asLong()).isEqualTo(korzen);
        assertThat(odp2.get("replyToUsername").asText()).isEqualTo("co_cyd");
        // odpowiedz na wlasny komentarz: bez adresata
        JsonNode odp3 = odpowiedz("co_cyd", post.getId(), odp1.get("id").asLong(), "cyd do siebie");
        assertThat(odp3.get("replyToUsername").isNull()).isTrue();

        JsonNode wszystkie = tresc(get_("co_bob", "/api/comments/" + korzen + "/replies").andExpect(status().isOk()));
        assertThat(teksty(wszystkie.get("content"), "content"))
            .containsExactly("odpowiedz cyda", "ala do cyda", "cyd do siebie");
        assertThat(lista("co_bob", post.getId()).get("content").get(0).get("replyCount").asInt()).isEqualTo(3);
        // odpowiedz nie ma wlasnych odpowiedzi
        assertThat(tresc(get_("co_bob", "/api/comments/" + odp1.get("id").asLong() + "/replies")).get("content").size()).isZero();
    }

    @Test
    @DisplayName("odpowiedz pod komentarzem z innego posta albo nieistniejacym - 404")
    void replyTargetMustBelongToThePost() throws Exception {
        Post inny = posts.save(new Post(ala, "inny post"));
        em.flush();
        long obcyKomentarz = komentarz("co_bob", inny.getId(), "gdzie indziej");
        wyslij("POST", "co_cyd", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "x", "parentId", obcyKomentarz)).andExpect(status().isNotFound());
        wyslij("POST", "co_cyd", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "x", "parentId", 99999)).andExpect(status().isNotFound());
        assertThat(comments.count()).isEqualTo(1);
    }

    /* ---------------------------- oznaczenia ---------------------------- */

    @Test
    @DisplayName("oznaczenia: tylko istniejace osoby, ktore widza post; adres e-mail nie oznacza nikogo; najwyzej piec")
    void mentions() throws Exception {
        JsonNode k = odpowiedzNaPost("co_ala", "hej @co_bob, @co_dan i @nie_ma_takiego oraz mail@co_cyd.pl; @co_bob jeszcze raz");
        assertThat(k.get("mentions").toString()).isEqualTo("[\"co_bob\",\"co_dan\"]");
        assertThat(mentions.count()).isEqualTo(2);

        // wiecej niz piec - reszta zostaje zwyklym tekstem
        for (String login : List.of("co_m1", "co_m2", "co_m3", "co_m4", "co_m5", "co_m6")) {
            osoba(login);
        }
        em.flush();
        JsonNode duzo = odpowiedzNaPost("co_ala", "@co_m1 @co_m2 @co_m3 @co_m4 @co_m5 @co_m6");
        assertThat(duzo.get("mentions").size()).isEqualTo(5);

        // samego siebie sie nie oznacza: wpis zostaje zwyklym tekstem, nic sie nie zapisuje
        long przed = mentions.count();
        JsonNode sam = odpowiedzNaPost("co_ala", "ja, @co_ala");
        assertThat(sam.get("mentions").size()).isZero();
        assertThat(mentions.count()).isEqualTo(przed);
    }

    private JsonNode odpowiedzNaPost(String kto, String tekst) throws Exception {
        JsonNode r = tresc(wyslij("POST", kto, "/api/posts/" + post.getId() + "/comments", Map.of("content", tekst))
            .andExpect(status().isCreated()));
        em.flush();
        return r;
    }

    @Test
    @DisplayName("oznaczyc mozna tylko kogos, kto zobaczy post: post tylko dla znajomych, blokady")
    void mentionsRespectVisibilityAndBlocks() throws Exception {
        Post prywatny = new Post(ala, "tylko dla znajomych");
        prywatny.setVisibility(PostVisibility.FRIENDS);
        posts.save(prywatny);
        em.flush();

        // dan nie jest znajomym alicji - nie widzi posta, wiec nie da sie go oznaczyc; bob (znajomy) tak
        JsonNode k = tresc(wyslij("POST", "co_ala", "/api/posts/" + prywatny.getId() + "/comments",
            Map.of("content", "@co_dan @co_bob")).andExpect(status().isCreated()));
        assertThat(k.get("mentions").toString()).isEqualTo("[\"co_bob\"]");

        // blokada miedzy oznaczajacym a oznaczanym
        wyslij("PUT", "co_cyd", "/api/blocks/co_bob", null);
        em.flush();
        JsonNode drugi = odpowiedzNaPost("co_cyd", "@co_bob");
        assertThat(drugi.get("mentions").size()).isZero();
        // blokada miedzy oznaczanym a AUTOREM posta: bob nie widzi posta alicji (404), wiec tez nie zostanie oznaczony
        wyslij("PUT", "co_ala", "/api/blocks/co_dan", null);
        em.flush();
        assertThat(odpowiedzNaPost("co_ala", "@co_dan").get("mentions").size()).isZero();
    }

    @Test
    @DisplayName("podpowiedzi do oznaczania: autor posta, piszacy, potem znajomi; po poczatku loginu; bez blokad")
    void mentionHints() throws Exception {
        komentarz("co_dan", post.getId(), "obcy w rozmowie");
        // bob widzi: autora posta (ala), piszacego (dan), swoich znajomych (ala - ta sama osoba, bez powtorzen)
        JsonNode podpowiedzi = tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=").andExpect(status().isOk()));
        assertThat(teksty(podpowiedzi, "username")).containsExactly("co_ala", "co_dan");
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=CO_D")), "username"))
            .containsExactly("co_dan");
        assertThat(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=zzz")).size()).isZero();

        // ala widzi swoich znajomych (bob, cyd) i piszacego (dan) - w tej kolejnosci: piszacy, potem znajomi
        assertThat(teksty(tresc(get_("co_ala", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_dan", "co_bob", "co_cyd");

        // obcy nie dostaje podpowiedzi ludzi, ktorych nie zna z tej rozmowy - tylko autora posta
        assertThat(teksty(tresc(get_("co_dan", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala");

        wyslij("PUT", "co_ala", "/api/blocks/co_dan", null);
        em.flush();
        assertThat(teksty(tresc(get_("co_ala", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_bob", "co_cyd");
    }

    @Test
    @DisplayName("podpowiedzi: tylko ci, ktorzy zobacza post (post tylko dla znajomych, post klanu)")
    void mentionHintsOnlyForPeopleWhoSeeThePost() throws Exception {
        // bob ma znajomego dana, ktory NIE jest znajomym autora posta
        bob.addFriend(dan);
        users.save(bob);
        em.flush();
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala", "co_dan");

        post.setVisibility(PostVisibility.FRIENDS);
        posts.save(post);
        em.flush();
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala");

        // post klanu: znajomy spoza klanu tez nie jest podpowiadany
        long klan = klanZ("co_ala", "Podpowiadacze", "POD", "co_bob");
        Post wKlanie = postKlanu(klan, "co_ala", "post klanu");
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + wKlanie.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala");
    }

    @Test
    @DisplayName("podpowiedzi: pomijaja osoby z blokad ogladajacego (w obie strony), takze gdy pisaly pod postem")
    void mentionHintsSkipBlockedPeople() throws Exception {
        komentarz("co_dan", post.getId(), "dan pisze");
        komentarz("co_cyd", post.getId(), "cyd pisze");
        // piszacy od ostatnio piszacego: cyd przed danem
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala", "co_cyd", "co_dan");

        wyslij("PUT", "co_bob", "/api/blocks/co_dan", null);   // bob blokuje dana
        wyslij("PUT", "co_cyd", "/api/blocks/co_bob", null);   // cyd blokuje boba
        em.flush();
        assertThat(teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=")), "username"))
            .containsExactly("co_ala");
    }

    @Test
    @DisplayName("podpowiedzi: najwyzej osiem osob")
    void mentionHintsAreLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            User znajomy = osoba(String.format("co_zn%02d", i));
            bob.addFriend(znajomy);
        }
        users.save(bob);
        em.flush();
        List<String> podpowiedzi = teksty(tresc(get_("co_bob", "/api/posts/" + post.getId() + "/mentionable?q=")), "username");
        assertThat(podpowiedzi).hasSize(8);
        assertThat(podpowiedzi.get(0)).isEqualTo("co_ala");
    }

    /* ---------------------------- powiadomienia ---------------------------- */

    @Test
    @DisplayName("powiadomienia: autor posta, adresat odpowiedzi i oznaczeni - po jednym na osobe; sam sobie nikt")
    void notifications_() throws Exception {
        // bob komentuje post alicji: ala dostaje POST_COMMENT, bob nic
        long k = komentarz("co_bob", post.getId(), "komentarz boba");
        assertThat(typy("co_ala")).containsExactly("POST_COMMENT");
        assertThat(typy("co_bob")).isEmpty();

        // cyd odpowiada bobowi i oznacza alice: bob dostaje COMMENT_REPLY, ala COMMENT_MENTION (nie jeszcze raz POST_COMMENT)
        odpowiedz("co_cyd", post.getId(), k, "do boba, a @co_ala niech zobaczy");
        assertThat(typy("co_bob")).containsExactly("COMMENT_REPLY");
        assertThat(typy("co_ala")).containsExactly("COMMENT_MENTION", "POST_COMMENT");

        // odpowiedz do autora posta, ktory jest tez oznaczony: jedno powiadomienie, to o odpowiedzi
        long komentarzAli = komentarz("co_ala", post.getId(), "komentarz alicji pod wlasnym postem");
        assertThat(typy("co_ala")).hasSize(2);
        em.flush();
        odpowiedz("co_bob", post.getId(), komentarzAli, "@co_ala dzieki");
        assertThat(typy("co_ala")).containsExactly("COMMENT_REPLY", "COMMENT_MENTION", "POST_COMMENT");

        // powiadomienie prowadzi do komentarza
        JsonNode pierwsze = tresc(get_("co_ala", "/api/notifications")).get("content").get(0);
        assertThat(pierwsze.get("link").asText()).startsWith("/post/" + post.getId() + "?komentarz=");
        assertThat(pierwsze.get("actorUsername").asText()).isEqualTo("co_bob");
        assertThat(pierwsze.get("postExcerpt").asText()).isEqualTo("@co_ala dzieki");
    }

    @Test
    @DisplayName("push na telefon: jeden baner na post przy komentarzach, osobny przy odpowiedzi i oznaczeniu")
    void pushForComments() throws Exception {
        komentarz("co_bob", post.getId(), "k");
        verify(push).send(org.mockito.ArgumentMatchers.eq(ala.getId()), org.mockito.ArgumentMatchers.argThat(m ->
            m.titleKey().equals("push.comment.title") && m.tag().equals("comment-post-" + post.getId())
                && m.url().startsWith("/post/" + post.getId() + "?komentarz=")));
        verify(push, never()).send(org.mockito.ArgumentMatchers.eq(bob.getId()), any());
    }

    @Test
    @DisplayName("blokada kasuje powiadomienia o komentarzach miedzy tymi osobami")
    void blockClearsNotifications() throws Exception {
        komentarz("co_bob", post.getId(), "k");
        assertThat(typy("co_ala")).containsExactly("POST_COMMENT");
        wyslij("PUT", "co_ala", "/api/blocks/co_bob", null);
        em.flush();
        assertThat(typy("co_ala")).isEmpty();
    }

    /* ---------------------------- widocznosc i blokady ---------------------------- */

    @Test
    @DisplayName("post tylko dla znajomych: obcy nie czyta i nie pisze komentarzy (409); znajomy tak")
    void friendsOnlyPost() throws Exception {
        Post prywatny = new Post(ala, "tylko dla znajomych");
        prywatny.setVisibility(PostVisibility.FRIENDS);
        posts.save(prywatny);
        em.flush();
        komentarz("co_bob", prywatny.getId(), "znajomy pisze");

        get_("co_dan", "/api/posts/" + prywatny.getId() + "/comments").andExpect(status().isConflict());
        wyslij("POST", "co_dan", "/api/posts/" + prywatny.getId() + "/comments", Map.of("content", "x"))
            .andExpect(status().isConflict());
        get_("co_dan", "/api/posts/" + prywatny.getId() + "/mentionable").andExpect(status().isConflict());
        assertThat(lista("co_cyd", prywatny.getId()).get("content").size()).isEqualTo(1);
        // administrator aplikacji tez nie czyta cudzych postow "tylko dla znajomych" (tak jak sam post)
        get_("co_szef", "/api/posts/" + prywatny.getId() + "/comments").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("blokada: komentarze osob z blokad znikaja razem z odpowiedziami pod nimi, a licznik je pomija")
    void blockedCommentsAreHidden() throws Exception {
        long bobowy = komentarz("co_bob", post.getId(), "komentarz boba");
        odpowiedz("co_ala", post.getId(), bobowy, "odpowiedz alicji pod bobem");
        komentarz("co_cyd", post.getId(), "komentarz cyda");

        // ala blokuje boba: jego komentarz i wszystko pod nim znika dla alicji
        wyslij("PUT", "co_ala", "/api/blocks/co_bob", null);
        em.flush();
        JsonNode dlaAli = lista("co_ala", post.getId());
        assertThat(teksty(dlaAli.get("content"), "content")).containsExactly("komentarz cyda");
        assertThat(dlaAli.get("totalComments").asInt()).isEqualTo(1);
        get_("co_ala", "/api/comments/" + bobowy).andExpect(status().isNotFound());
        get_("co_ala", "/api/comments/" + bobowy + "/replies").andExpect(status().isNotFound());
        em.clear();
        assertThat(tresc(get_("co_ala", "/api/posts/" + post.getId())).get("commentCount").asInt()).isEqualTo(1);
        // a cyd, ktory nikogo nie blokowal, widzi wszystko
        assertThat(lista("co_cyd", post.getId()).get("totalComments").asInt()).isEqualTo(3);
        // bob (zablokowany przez autora posta) nie widzi nawet posta
        get_("co_bob", "/api/posts/" + post.getId() + "/comments").andExpect(status().isNotFound());
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "x"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("odpowiedz na komentarz osoby z blokady - 404")
    void replyToBlockedAuthor() throws Exception {
        long bobowy = komentarz("co_bob", post.getId(), "komentarz boba");
        wyslij("PUT", "co_cyd", "/api/blocks/co_bob", null);
        em.flush();
        wyslij("POST", "co_cyd", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "x", "parentId", bobowy)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("odpowiedz pod komentarzem osoby z blokady jest niedostepna: nie da sie jej otworzyc ani odpowiedziec na nia")
    void replyUnderBlockedParentIsHidden() throws Exception {
        long bobowy = komentarz("co_bob", post.getId(), "komentarz boba");
        long cydowa = odpowiedz("co_cyd", post.getId(), bobowy, "odpowiedz cyda").get("id").asLong();
        wyslij("PUT", "co_ala", "/api/blocks/co_bob", null);
        em.flush();

        get_("co_ala", "/api/comments/" + cydowa).andExpect(status().isNotFound());
        wyslij("POST", "co_ala", "/api/posts/" + post.getId() + "/comments",
            Map.of("content", "x", "parentId", cydowa)).andExpect(status().isNotFound());
        // cyd nikogo nie blokowal - widzi swoja odpowiedz jak dotad
        get_("co_cyd", "/api/comments/" + cydowa).andExpect(status().isOk());
    }

    @Test
    @DisplayName("kasowanie w poscie klanu: zarzad klanu moze skasowac cudzy komentarz, zwykly czlonek nie")
    void clanBoardDeletesComments() throws Exception {
        long klan = klanZ("co_ala", "Moderatorzy", "MOD", "co_bob", "co_cyd", "co_dan");
        Post bobowy = postKlanu(klan, "co_bob", "post boba w klanie");
        long cydowy = komentarz("co_cyd", bobowy.getId(), "komentarz cyda");
        long danowy = komentarz("co_dan", bobowy.getId(), "komentarz dana");

        // dan to zwykly czlonek, nie autor posta ani komentarza - nie moze
        wyslij("DELETE", "co_dan", "/api/comments/" + cydowy, null).andExpect(status().isConflict());
        // flaga canDelete mowi to samo: zalozyciel klanu ma ja przy cudzych komentarzach, dan - nie
        // (lista od najnowszych: 0 = komentarz dana, 1 = komentarz cyda)
        JsonNode dlaAli = lista("co_ala", bobowy.getId()).get("content");
        assertThat(dlaAli.get(0).get("canDelete").asBoolean()).isTrue();
        assertThat(dlaAli.get(1).get("canDelete").asBoolean()).isTrue();
        JsonNode dlaDana = lista("co_dan", bobowy.getId()).get("content");
        assertThat(dlaDana.get(0).get("canDelete").asBoolean()).isTrue();    // wlasny
        assertThat(dlaDana.get(1).get("canDelete").asBoolean()).isFalse();   // cudzy

        // zalozyciel (zarzad) kasuje komentarz cyda, chociaz nie jest autorem ani komentarza, ani posta
        wyslij("DELETE", "co_ala", "/api/comments/" + cydowy, null).andExpect(status().isNoContent());
        // autor posta (bob) tez moze, jak w kazdym poscie
        wyslij("DELETE", "co_bob", "/api/comments/" + danowy, null).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("post klanu: komentuja czlonkowie; obcy dostaje 404; administrator aplikacji czyta, ale nie pisze")
    void clanPost() throws Exception {
        long klan = tresc(wyslij("POST", "co_ala", "/api/clans", Map.of("name", "Komentatorzy", "tag", "KOM"))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "co_bob")).andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitations.findAll().get(0).getId();
        wyslij("POST", "co_bob", "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
        em.clear();

        Clan c = clans.findById(klan).orElseThrow();
        Post postKlanu = new Post(users.findByUsername("co_ala").orElseThrow(), "tajny post klanu");
        postKlanu.setClan(c);
        posts.save(postKlanu);
        em.flush();

        long kBoba = komentarz("co_bob", postKlanu.getId(), "bob w klanie");
        assertThat(lista("co_ala", postKlanu.getId()).get("content").size()).isEqualTo(1);

        get_("co_dan", "/api/posts/" + postKlanu.getId() + "/comments").andExpect(status().isNotFound());
        wyslij("POST", "co_dan", "/api/posts/" + postKlanu.getId() + "/comments", Map.of("content", "x")).andExpect(status().isNotFound());
        get_("co_dan", "/api/comments/" + kBoba).andExpect(status().isNotFound());

        // administrator aplikacji czyta, ale nie jest czlonkiem - nie pisze
        assertThat(lista("co_szef", postKlanu.getId()).get("content").size()).isEqualTo(1);
        wyslij("POST", "co_szef", "/api/posts/" + postKlanu.getId() + "/comments", Map.of("content", "x")).andExpect(status().isConflict());

        // oznaczyc mozna tylko czlonka klanu
        JsonNode k = tresc(wyslij("POST", "co_ala", "/api/posts/" + postKlanu.getId() + "/comments",
            Map.of("content", "@co_bob @co_cyd")).andExpect(status().isCreated()));
        assertThat(k.get("mentions").toString()).isEqualTo("[\"co_bob\"]");

        // zalozyciel (zarzad klanu) moze skasowac cudzy komentarz w klanie - tu sam autor posta, wiec sprawdzamy zarzad osobno
        wyslij("DELETE", "co_ala", "/api/comments/" + kBoba, null).andExpect(status().isNoContent());
    }

    /* ---------------------------- kasowanie ---------------------------- */

    @Test
    @DisplayName("kasowanie: autor komentarza, autor posta i administrator tak; obca osoba nie (409)")
    void deletePermissions() throws Exception {
        long k1 = komentarz("co_bob", post.getId(), "do skasowania przez boba");
        long k2 = komentarz("co_bob", post.getId(), "do skasowania przez alicje");
        long k3 = komentarz("co_bob", post.getId(), "do skasowania przez admina");
        long k4 = komentarz("co_bob", post.getId(), "nikt cudzy tego nie skasuje");

        wyslij("DELETE", "co_cyd", "/api/comments/" + k4, null).andExpect(status().isConflict());
        wyslij("DELETE", "co_dan", "/api/comments/" + k4, null).andExpect(status().isConflict());
        wyslij("DELETE", "co_bob", "/api/comments/" + k1, null).andExpect(status().isNoContent());
        wyslij("DELETE", "co_ala", "/api/comments/" + k2, null).andExpect(status().isNoContent());
        wyslij("DELETE", "co_szef", "/api/comments/" + k3, null).andExpect(status().isNoContent());
        wyslij("DELETE", "co_bob", "/api/comments/" + k1, null).andExpect(status().isNotFound());
        em.flush();
        em.clear();
        assertThat(comments.findAll()).extracting(c -> c.getId()).containsExactly(k4);
    }

    @Test
    @DisplayName("skasowanie komentarza zabiera odpowiedzi pod nim, oznaczenia i powiadomienia; cudze komentarze zostaja")
    void deleteCascades() throws Exception {
        long korzen = komentarz("co_bob", post.getId(), "korzen");
        odpowiedz("co_cyd", post.getId(), korzen, "@co_ala odpowiedz");
        long inny = komentarz("co_cyd", post.getId(), "inny komentarz");
        assertThat(comments.count()).isEqualTo(3);
        assertThat(mentions.count()).isEqualTo(1);
        long powiadomienPrzed = notifications.count();
        assertThat(powiadomienPrzed).isGreaterThan(0);

        wyslij("DELETE", "co_bob", "/api/comments/" + korzen, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(comments.findAll()).extracting(c -> c.getId()).containsExactly(inny);
        assertThat(mentions.count()).isZero();
        // zostaje tylko powiadomienie o "innym komentarzu"
        assertThat(typy("co_ala")).containsExactly("POST_COMMENT");
    }

    @Test
    @DisplayName("skasowanie posta kasuje jego komentarze i powiadomienia o nich")
    void deletingThePostDeletesComments() throws Exception {
        komentarz("co_bob", post.getId(), "komentarz");
        wyslij("DELETE", "co_ala", "/api/posts/" + post.getId(), null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(comments.count()).isZero();
        assertThat(typy("co_ala")).isEmpty();
    }

    @Test
    @DisplayName("usuniecie konta: znikaja komentarze tej osoby razem z odpowiedziami pod nimi; cudze zostaja")
    void accountDeletion() throws Exception {
        long bobowy = komentarz("co_bob", post.getId(), "komentarz boba");
        odpowiedz("co_cyd", post.getId(), bobowy, "odpowiedz cyda pod bobem");
        komentarz("co_cyd", post.getId(), "komentarz cyda");
        em.flush();
        em.clear();

        deletion.erase(users.findByUsername("co_bob").orElseThrow());
        em.flush();
        em.clear();
        assertThat(comments.findAll()).extracting(c -> c.getContent()).containsExactly("komentarz cyda");
    }

    @Test
    @DisplayName("usuniecie konta osoby, do ktorej byla odpowiedz, zostawia odpowiedz bez adresata")
    void deletedReplyTargetLeavesTheReply() throws Exception {
        long korzen = komentarz("co_ala", post.getId(), "komentarz alicji");
        JsonNode odp = odpowiedz("co_cyd", post.getId(), korzen, "odpowiedz do alicji");
        em.flush();
        em.clear();
        // dan odpowiada cydowi, potem cyd odchodzi: odpowiedz dana zostaje, ale bez wskazania cyda
        JsonNode dana = odpowiedz("co_dan", post.getId(), odp.get("id").asLong(), "dan do cyda");
        em.clear();
        assertThat(dana.get("replyToUsername").asText()).isEqualTo("co_cyd");
        deletion.erase(users.findByUsername("co_cyd").orElseThrow());
        em.flush();
        em.clear();
        JsonNode zostala = tresc(get_("co_ala", "/api/comments/" + dana.get("id").asLong()).andExpect(status().isOk()));
        assertThat(zostala.get("replyToUsername").isNull()).isTrue();
    }

    /* ---------------------------- limity i kary ---------------------------- */

    @Test
    @DisplayName("limit szybkosci: 10 komentarzy na minute, potem 429 z Retry-After")
    void rateLimit() throws Exception {
        for (int i = 0; i < CommentService.NA_MINUTE; i++) {
            komentarz("co_bob", post.getId(), "k" + i);
        }
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "za duzo"))
            .andExpect(status().isTooManyRequests());
        assertThat(comments.count()).isEqualTo(CommentService.NA_MINUTE);
        // limit jest na osobe - inna osoba pisze bez przeszkod
        komentarz("co_cyd", post.getId(), "cyd moze");
        // starsze niz minuta sie nie licza
        em.createQuery("UPDATE Comment c SET c.createdAt = :d WHERE c.author.id = :id")
            .setParameter("d", LocalDateTime.now().minusMinutes(5)).setParameter("id", bob.getId()).executeUpdate();
        em.clear();
        komentarz("co_bob", post.getId(), "znowu mozna");
    }

    @Test
    @DisplayName("zakaz publikowania obejmuje komentarze, ale kasowac wlasne mozna nadal")
    void postingBanCoversComments() throws Exception {
        long k = komentarz("co_bob", post.getId(), "przed kara");
        users.findByUsername("co_bob").orElseThrow().setBannedUntil(com.musicclubapp.entity.BanKind.POSTING, LocalDateTime.now().plusDays(1));
        em.flush();
        wyslij("POST", "co_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "pod kara"))
            .andExpect(status().isConflict());
        wyslij("DELETE", "co_bob", "/api/comments/" + k, null).andExpect(status().isNoContent());
    }

    /* ---------------------------- zgloszenia ---------------------------- */

    @Test
    @DisplayName("zgloszenie komentarza: dowod to tresc komentarza; komentarz musi nalezec do zglaszanego")
    void reportComment() throws Exception {
        long k = komentarz("co_bob", post.getId(), "obrazliwy komentarz");
        // zly autor
        wyslij("POST", "co_cyd", "/api/reports/on/co_dan", Map.of("reason", "HARASSMENT", "context", "COMMENT",
            "commentId", k, "description", "to jest zgloszenie komentarza")).andExpect(status().isConflict());
        // brak komentarza
        wyslij("POST", "co_cyd", "/api/reports/on/co_bob", Map.of("reason", "HARASSMENT", "context", "COMMENT",
            "description", "to jest zgloszenie komentarza")).andExpect(status().isConflict());
        // poprawne
        JsonNode r = tresc(wyslij("POST", "co_cyd", "/api/reports/on/co_bob", Map.of("reason", "HARASSMENT",
            "context", "COMMENT", "commentId", k, "description", "to jest zgloszenie komentarza")).andExpect(status().isCreated()));
        assertThat(r.get("commentId").asLong()).isEqualTo(k);
        assertThat(r.get("postId").asLong()).isEqualTo(post.getId());
        assertThat(r.get("context").asText()).isEqualTo("COMMENT");
        assertThat(r.get("evidence").get(0).get("text").asText()).isEqualTo("obrazliwy komentarz");
        assertThat(r.get("evidence").get(0).get("author").asText()).isEqualTo("co_bob");
    }

    @Test
    @DisplayName("zgloszenie komentarza pod postem, ktorego zglaszajacy nie widzi - 404")
    void reportNeedsVisibility() throws Exception {
        Post prywatny = new Post(ala, "tylko dla znajomych");
        prywatny.setVisibility(PostVisibility.FRIENDS);
        posts.save(prywatny);
        em.flush();
        long k = komentarz("co_bob", prywatny.getId(), "komentarz pod prywatnym");
        wyslij("POST", "co_dan", "/api/reports/on/co_bob", Map.of("reason", "SPAM", "context", "COMMENT",
            "commentId", k, "description", "zglaszam komentarz, ktorego nie widze")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("decyzja DELETE_COMMENT kasuje komentarz i zostawia zgloszenie z dowodem; przy innym zgloszeniu - 409")
    void resolveWithDeleteComment() throws Exception {
        long k = komentarz("co_bob", post.getId(), "obrazliwy komentarz");
        JsonNode r = tresc(wyslij("POST", "co_cyd", "/api/reports/on/co_bob", Map.of("reason", "HARASSMENT",
            "context", "COMMENT", "commentId", k, "description", "to jest zgloszenie komentarza")).andExpect(status().isCreated()));
        long id = r.get("id").asLong();

        wyslij("POST", "co_szef", "/api/reports/admin/" + id + "/resolve", Map.of("decision", "RESOLVED",
            "note", "usunieto komentarz", "action", "DELETE_COMMENT")).andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(comments.count()).isZero();
        Report zostalo = reports.findById(id).orElseThrow();
        assertThat(zostalo.getComment()).isNull();
        assertThat(zostalo.getEvidence()).hasSize(1);

        // DELETE_COMMENT przy zgloszeniu profilu - nie ma czego kasowac
        JsonNode profil = tresc(wyslij("POST", "co_dan", "/api/reports/on/co_bob", Map.of("reason", "SPAM",
            "context", "PROFILE", "description", "zglaszam profil tej osoby")).andExpect(status().isCreated()));
        wyslij("POST", "co_szef", "/api/reports/admin/" + profil.get("id").asLong() + "/resolve", Map.of("decision", "RESOLVED",
            "note", "nic", "action", "DELETE_COMMENT")).andExpect(status().isConflict());
    }

    /* ---------------------------- eksport i ranking ---------------------------- */

    @Test
    @DisplayName("komentarze liczą się do popularnosci posta na tablicy 'Dla ciebie' (komentarz = dwie reakcje)")
    void commentsMakeAPostPopular() throws Exception {
        Post cudzy = posts.save(new Post(dan, "post obcego"));
        em.flush();
        for (int i = 0; i < 3; i++) {
            long k = komentarz("co_bob", cudzy.getId(), "k" + i);
            em.createQuery("UPDATE Comment c SET c.createdAt = :d WHERE c.id = :id")
                .setParameter("d", LocalDateTime.now().minusMinutes(5)).setParameter("id", k).executeUpdate();
        }
        em.clear();
        JsonNode tablica = tresc(get_("co_cyd", "/api/posts?size=20").andExpect(status().isOk()));
        JsonNode znaleziony = null;
        for (JsonNode p : tablica.get("content")) {
            if (p.get("id").asLong() == cudzy.getId()) {
                znaleziony = p;
            }
        }
        assertThat(znaleziony).isNotNull();
        // 3 komentarze = 6 punktow zainteresowania >= 5
        assertThat(znaleziony.get("feedReasons").toString()).isEqualTo("[\"POPULAR\"]");
        assertThat(znaleziony.get("commentCount").asInt()).isEqualTo(3);
    }
}
