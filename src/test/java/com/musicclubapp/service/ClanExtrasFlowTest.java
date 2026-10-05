package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.ClanTrack;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.ClanTrackRepository;
import com.musicclubapp.repository.ClanTrackVoteRepository;
import com.musicclubapp.repository.MusicEventRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rozszerzenia klanow przez prawdziwe API: nieprzeczytane, wyciszenie i powiadomienia z czatu,
 * odpowiedzi i reakcje na wiadomosci, zgloszenie klanu, ogloszenie i zasady, muzyka klanu
 * (gusta i utwor tygodnia) oraz koncerty czlonkow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Klany - rozszerzenia")
class ClanExtrasFlowTest {

    private static final String UTWOR_1 = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC";
    private static final String UTWOR_2 = "https://open.spotify.com/track/3n3Ppam7vgaVa1iaRUc9Lp";
    private static final String UTWOR_3 = "https://open.spotify.com/track/7ouMYWpwJ422jRcDASZB7P";
    private static final String UTWOR_4 = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b";
    private static final String PLAYLISTA = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ClanRepository clanRepository;
    @Autowired private ClanInvitationRepository invitationRepository;
    @Autowired private ClanMessageRepository messageRepository;
    @Autowired private ClanMessageReactionRepository reactionRepository;
    @Autowired private ClanTrackRepository trackRepository;
    @Autowired private ClanTrackVoteRepository voteRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ArtistRepository artists;
    @Autowired private MusicEventRepository events;
    @Autowired private AccountDeletionService deletion;
    @Autowired private EntityManager em;

    @MockBean private PushService push;
    @MockBean private MusicMetadataService metadata;

    private User ala;
    private User bob;
    private User cyd;
    private User dan;
    private User szef;

    @BeforeEach
    void setUp() {
        ala = users.save(new User("ce_ala", "ce_ala@example.com", "x"));
        bob = users.save(new User("ce_bob", "ce_bob@example.com", "x"));
        cyd = users.save(new User("ce_cyd", "ce_cyd@example.com", "x"));
        dan = users.save(new User("ce_dan", "ce_dan@example.com", "x"));
        szef = new User("ce_szef", "ce_szef@example.com", "x");
        szef.setRole(Role.ADMIN);
        users.save(szef);
        em.flush();
        given(metadata.fetch(any())).willReturn(new MusicMetadataService.Metadata("Tytul utworu", "https://okladka/x.jpg"));
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions get_(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    /** Panel administratora wymaga roli ROLE_ADMIN w sesji, nie tylko w bazie. */
    private ResultActions admin(String adres) throws Exception {
        return mvc.perform(get(adres).with(user("ce_szef").roles("ADMIN")).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var builder = switch (metoda) {
            case "PUT" -> put(adres);
            case "DELETE" -> delete(adres);
            default -> post(adres);
        };
        builder.with(user(kto)).with(csrf()).header("Accept-Language", "pl");
        if (tresc != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(builder);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private long zaloz(String kto, String nazwa, String skrot) throws Exception {
        long id = tresc(wyslij("POST", kto, "/api/clans", Map.of("name", nazwa, "tag", skrot))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private void dolacz(long klan, String zapraszajacy, String kto) throws Exception {
        wyslij("POST", zapraszajacy, "/api/clans/" + klan + "/invitations", Map.of("username", kto))
            .andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitationRepository.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals(kto)).findFirst().orElseThrow().getId();
        wyslij("POST", kto, "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
    }

    private long napisz(String kto, long klan, String tekst) throws Exception {
        Map<String, Object> cialo = new HashMap<>();
        cialo.put("content", tekst);
        long id = tresc(wyslij("POST", kto, "/api/clans/" + klan + "/chat", cialo)
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private long odpowiedz(String kto, long klan, String tekst, long naWiadomosc) throws Exception {
        long id = tresc(wyslij("POST", kto, "/api/clans/" + klan + "/chat",
            Map.of("content", tekst, "replyTo", naWiadomosc)).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private long nieprzeczytane(String kto) throws Exception {
        return tresc(get_(kto, "/api/clans/mine/unread").andExpect(status().isOk())).get("unread").asLong();
    }

    private void przeczytaj(String kto, long klan, long doNumeru) throws Exception {
        wyslij("POST", kto, "/api/clans/" + klan + "/chat/read", Map.of("upTo", doNumeru))
            .andExpect(status().isNoContent());
        em.flush();
    }

    private JsonNode czat(String kto, long klan) throws Exception {
        return tresc(get_(kto, "/api/clans/" + klan + "/chat").andExpect(status().isOk()));
    }

    private LocalDate poniedzialek() {
        return LocalDate.now(EventImportService.STREFA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /* ======================= nieprzeczytane i powiadomienia ======================= */

    @Test
    @DisplayName("nieprzeczytane: wlasne wiadomosci nie licza sie, nowy czlonek nie dostaje historii jako nowej")
    void unreadIgnoresOwnAndHistory() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        napisz("ce_ala", klan, "przed dolaczeniem cyda");
        dolacz(klan, "ce_ala", "ce_cyd");

        assertThat(nieprzeczytane("ce_cyd")).isZero();        // historia sprzed wejscia
        assertThat(nieprzeczytane("ce_bob")).isEqualTo(1);    // bob byl juz w klanie, ala napisala potem

        long m1 = napisz("ce_bob", klan, "raz");
        long m2 = napisz("ce_bob", klan, "dwa");
        long m3 = napisz("ce_bob", klan, "trzy");
        assertThat(nieprzeczytane("ce_bob")).isZero();        // wlasne wiadomosci i tak sie nie licza (pisze = widzial)
        assertThat(nieprzeczytane("ce_cyd")).isEqualTo(3);
        assertThat(nieprzeczytane("ce_ala")).isEqualTo(3);

        przeczytaj("ce_cyd", klan, m2);
        assertThat(nieprzeczytane("ce_cyd")).isEqualTo(1);
        // Spozniony odczyt nie cofa przeczytanych
        przeczytaj("ce_cyd", klan, m1);
        assertThat(nieprzeczytane("ce_cyd")).isEqualTo(1);
        // Numer z kosmosu jest przycinany do ostatniej wiadomosci - i nie "zjada" przyszlych
        przeczytaj("ce_cyd", klan, 999_999_999L);
        assertThat(nieprzeczytane("ce_cyd")).isZero();
        napisz("ce_bob", klan, "cztery");
        assertThat(nieprzeczytane("ce_cyd")).isEqualTo(1);
        assertThat(m3).isGreaterThan(m2);

        // Pole na stronie klanu zgadza sie z licznikiem
        get_("ce_cyd", "/api/clans/" + klan).andExpect(jsonPath("$.unreadChat").value(1));
    }

    @Test
    @DisplayName("nieprzeczytane: wiadomosci osob z blokady sie nie licza; obcy i osoba bez klanu nie ruszaja cudzego znacznika")
    void unreadAndAccess() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        wyslij("PUT", "ce_cyd", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());
        napisz("ce_bob", klan, "od zablokowanego");
        napisz("ce_ala", klan, "od zwyklej osoby");
        assertThat(nieprzeczytane("ce_cyd")).isEqualTo(1);

        // Osoba bez klanu: pusty licznik; oznaczanie i wyciszanie cudzego klanu odmowione
        get_("ce_dan", "/api/clans/mine/unread").andExpect(jsonPath("$.unread").value(0))
            .andExpect(jsonPath("$.clanId").doesNotExist());
        wyslij("POST", "ce_dan", "/api/clans/" + klan + "/chat/read", Map.of("upTo", 1)).andExpect(status().isConflict());
        wyslij("PUT", "ce_dan", "/api/clans/" + klan + "/chat/mute", Map.of("muted", true)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("push z czatu: jedno powiadomienie do przeczytania, bez autora, wyciszonych i osob z blokady")
    void chatPush() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        wyslij("PUT", "ce_bob", "/api/blocks/ce_cyd", null).andExpect(status().isNoContent());
        reset(push);

        // Pierwsza wiadomosc: dostaje ala; nie autor i nie osoba z blokada
        long m1 = napisz("ce_bob", klan, "hej");
        verify(push, times(1)).send(eq(ala.getId()), any(PushService.Message.class));
        verify(push, never()).send(eq(bob.getId()), any(PushService.Message.class));
        verify(push, never()).send(eq(cyd.getId()), any(PushService.Message.class));

        // Druga: ala ma juz nieprzeczytana - telefon nie brzeczy drugi raz
        reset(push);
        napisz("ce_bob", klan, "jestes?");
        verify(push, never()).send(eq(ala.getId()), any(PushService.Message.class));

        // Ala zajrzala do czatu: kolejna wiadomosc znow ja powiadomi
        long m3 = napisz("ce_bob", klan, "trzecia");
        przeczytaj("ce_ala", klan, m3);
        reset(push);
        napisz("ce_bob", klan, "czwarta");
        verify(push, times(1)).send(eq(ala.getId()), any(PushService.Message.class));

        // Wyciszony klan: licznik dziala, telefon milczy
        wyslij("PUT", "ce_ala", "/api/clans/" + klan + "/chat/mute", Map.of("muted", true)).andExpect(status().isNoContent());
        get_("ce_ala", "/api/clans/" + klan).andExpect(jsonPath("$.chatMuted").value(true));
        long m5 = napisz("ce_bob", klan, "piata");
        przeczytaj("ce_ala", klan, m5);
        reset(push);
        napisz("ce_bob", klan, "szosta");
        verify(push, never()).send(eq(ala.getId()), any(PushService.Message.class));
        assertThat(nieprzeczytane("ce_ala")).isEqualTo(1);

        // Wlaczenie z powrotem
        wyslij("PUT", "ce_ala", "/api/clans/" + klan + "/chat/mute", Map.of("muted", false)).andExpect(status().isNoContent());
        get_("ce_ala", "/api/clans/" + klan).andExpect(jsonPath("$.chatMuted").value(false));
        assertThat(m1).isPositive();
    }

    @Test
    @DisplayName("push z czatu: tresc wiadomosci nie trafia do powiadomienia")
    void chatPushHasNoContent() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        reset(push);
        napisz("ce_bob", klan, "tajny plan koncertu");
        var captor = org.mockito.ArgumentCaptor.forClass(PushService.Message.class);
        verify(push).send(eq(ala.getId()), captor.capture());
        PushService.Message m = captor.getValue();
        assertThat(m.bodyKey()).isEqualTo("push.clanChat.body");
        assertThat(java.util.Arrays.toString(m.bodyArgs())).doesNotContain("tajny");
        assertThat(m.url()).isEqualTo("/klan");
    }

    /* ============================ odpowiedzi i reakcje ============================ */

    @Test
    @DisplayName("odpowiedz: cytat, tylko na wiadomosc z tego klanu, ktora widac; skasowanie oryginalu zostawia odpowiedz")
    void replies() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        long inny = zaloz("ce_dan", "Inny Klan", "IK");

        long oryginal = napisz("ce_ala", klan, "kto jedzie na koncert?");
        long odp = odpowiedz("ce_bob", klan, "ja!", oryginal);

        JsonNode wiadomosci = czat("ce_cyd", klan);
        JsonNode ostatnia = wiadomosci.get(wiadomosci.size() - 1);
        assertThat(ostatnia.get("id").asLong()).isEqualTo(odp);
        assertThat(ostatnia.get("replyToId").asLong()).isEqualTo(oryginal);
        assertThat(ostatnia.get("replyTo").get("senderUsername").asText()).isEqualTo("ce_ala");
        assertThat(ostatnia.get("replyTo").get("excerpt").asText()).isEqualTo("kto jedzie na koncert?");

        // Odpowiedz z ucieciem dlugiego cytatu
        long dluga = napisz("ce_ala", klan, "x".repeat(400));
        long naDluga = odpowiedz("ce_bob", klan, "ok", dluga);
        JsonNode po = czat("ce_bob", klan);
        JsonNode cytat = po.get(po.size() - 1).get("replyTo").get("excerpt");
        assertThat(cytat.asText().length()).isEqualTo(ClanChatService.SKROT);
        assertThat(naDluga).isPositive();

        // Nie mozna odpowiedziec na wiadomosc z innego klanu ani nieistniejaca
        long obca = napisz("ce_dan", inny, "z innego klanu");
        wyslij("POST", "ce_bob", "/api/clans/" + klan + "/chat", Map.of("content", "x", "replyTo", obca))
            .andExpect(status().isNotFound());
        wyslij("POST", "ce_bob", "/api/clans/" + klan + "/chat", Map.of("content", "x", "replyTo", 987654321L))
            .andExpect(status().isNotFound());

        // Blokada: nie odpowiemy na wiadomosc osoby, ktorej nie widzimy, a cytat znika po zablokowaniu autora
        wyslij("PUT", "ce_cyd", "/api/blocks/ce_ala", null).andExpect(status().isNoContent());
        wyslij("POST", "ce_cyd", "/api/clans/" + klan + "/chat", Map.of("content", "x", "replyTo", oryginal))
            .andExpect(status().isNotFound());
        JsonNode zBlokada = czat("ce_cyd", klan);
        for (JsonNode m : zBlokada) {
            if (m.get("id").asLong() == odp) {
                assertThat(m.get("replyToId").asLong()).isEqualTo(oryginal);
                assertThat(m.get("replyTo").isNull()).isTrue();   // cytatu nie ma - autora nie widac
            }
        }

        // Ala usuwa oryginal - odpowiedz zostaje, a cytat mowi "usunieta" (oryginal jest sladem bez tresci)
        wyslij("DELETE", "ce_ala", "/api/clans/" + klan + "/chat/" + oryginal, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        JsonNode potem = czat("ce_bob", klan);
        boolean jest = false;
        for (JsonNode m : potem) {
            if (m.get("id").asLong() == odp) {
                jest = true;
                assertThat(m.get("replyToId").asLong()).isEqualTo(oryginal);
                assertThat(m.get("replyTo").get("deleted").asBoolean()).isTrue();
                assertThat(m.get("replyTo").get("excerpt").asText()).isEmpty();
            }
            if (m.get("id").asLong() == oryginal) {
                assertThat(m.get("deleted").asBoolean()).isTrue();
                assertThat(m.get("content").asText()).isEmpty();
            }
        }
        assertThat(jest).isTrue();
    }

    @Test
    @DisplayName("reakcje: jedna na osobe, liczniki i 'moja', odswiezanie od numeru, tylko czlonkowie, kaskada przy kasowaniu")
    void reactions() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        long inny = zaloz("ce_dan", "Inny Klan", "IK");
        long m1 = napisz("ce_ala", klan, "nowy singiel!");
        long m2 = napisz("ce_ala", klan, "druga");

        String adres = "/api/clans/" + klan + "/chat/" + m1 + "/reaction";
        wyslij("PUT", "ce_bob", adres, Map.of("emoji", "FIRE")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("FIRE")).andExpect(jsonPath("$[0].count").value(1))
            .andExpect(jsonPath("$[0].mine").value(true));
        wyslij("PUT", "ce_cyd", adres, Map.of("emoji", "FIRE")).andExpect(jsonPath("$[0].count").value(2));
        wyslij("PUT", "ce_ala", adres, Map.of("emoji", "HEART")).andExpect(status().isOk());
        // Powtorzone to samo emoji - nadal jedna reakcja tej osoby
        wyslij("PUT", "ce_bob", adres, Map.of("emoji", "FIRE")).andExpect(status().isOk());
        // Zmiana emoji podmienia poprzednie (jedna reakcja na osobe)
        wyslij("PUT", "ce_cyd", adres, Map.of("emoji", "LAUGH")).andExpect(status().isOk());
        assertThat(reactionRepository.count()).isEqualTo(3);

        // Widok czatu ma reakcje pod wiadomoscia, a "mine" zalezy od ogladajacego
        JsonNode wBoba = czat("ce_bob", klan);
        JsonNode reakcje = wBoba.get(0).get("reactions");
        assertThat(reakcje.size()).isEqualTo(3);
        long ogien = 0;
        boolean mojOgien = false;
        for (JsonNode r : reakcje) {
            if (r.get("type").asText().equals("FIRE")) {
                ogien = r.get("count").asLong();
                mojOgien = r.get("mine").asBoolean();
            }
        }
        assertThat(ogien).isEqualTo(1);
        assertThat(mojOgien).isTrue();
        assertThat(czat("ce_bob", klan).get(1).get("reactions").size()).isZero();

        // Odswiezanie: od numeru wzwyz; wiadomosc bez reakcji nie wystepuje
        get_("ce_bob", "/api/clans/" + klan + "/chat/reactions?since=" + m1)
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].messageId").value(m1));
        get_("ce_bob", "/api/clans/" + klan + "/chat/reactions?since=" + m2).andExpect(jsonPath("$.length()").value(0));

        // Cofniecie reakcji
        wyslij("DELETE", "ce_bob", adres, null).andExpect(status().isOk());
        assertThat(reactionRepository.count()).isEqualTo(2);

        // Obcy, osoba z innego klanu i administrator (bez czlonkostwa) nie reaguja; obcy nie widzi reakcji
        wyslij("PUT", "ce_dan", adres, Map.of("emoji", "FIRE")).andExpect(status().isConflict());
        wyslij("PUT", "ce_szef", adres, Map.of("emoji", "FIRE")).andExpect(status().isConflict());
        get_("ce_dan", "/api/clans/" + klan + "/chat/reactions?since=0").andExpect(status().isConflict());
        get_("ce_szef", "/api/clans/" + klan + "/chat/reactions?since=0").andExpect(status().isOk());
        // Wiadomosc z innego klanu - 404, nieznane emoji - 400
        long obca = napisz("ce_dan", inny, "u nas");
        wyslij("PUT", "ce_bob", "/api/clans/" + klan + "/chat/" + obca + "/reaction", Map.of("emoji", "FIRE"))
            .andExpect(status().isNotFound());
        wyslij("PUT", "ce_bob", adres, Map.of("emoji", "POOP")).andExpect(status().isBadRequest());

        // Skasowanie wiadomosci zabiera jej reakcje
        wyslij("DELETE", "ce_ala", "/api/clans/" + klan + "/chat/" + m1, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("reakcje: osoby z blokady nie wliczaja sie ogladajacemu")
    void reactionsRespectBlocks() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        long m1 = napisz("ce_ala", klan, "hej");
        String adres = "/api/clans/" + klan + "/chat/" + m1 + "/reaction";
        wyslij("PUT", "ce_bob", adres, Map.of("emoji", "FIRE")).andExpect(status().isOk());
        wyslij("PUT", "ce_cyd", adres, Map.of("emoji", "FIRE")).andExpect(status().isOk());
        wyslij("PUT", "ce_ala", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());

        assertThat(czat("ce_ala", klan).get(0).get("reactions").get(0).get("count").asLong()).isEqualTo(1);
        get_("ce_ala", "/api/clans/" + klan + "/chat/reactions?since=0").andExpect(jsonPath("$[0].reactions[0].count").value(1));
        // Administrator aplikacji widzi wszystko
        assertThat(czat("ce_szef", klan).get(0).get("reactions").get(0).get("count").asLong()).isEqualTo(2);
    }

    /* =============================== zglos klan =============================== */

    @Test
    @DisplayName("zglos klan: zglaszanym jest zalozyciel, dowod to migawka nazwy i opisu, brak duplikatow i wlasnych zgloszen")
    void reportClan() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("description", "opis do zgloszenia")).andExpect(status().isOk());
        Map<String, Object> zgloszenie = Map.of("reason", "INAPPROPRIATE", "context", "CLAN",
            "description", "Nazwa klanu jest obrazliwa");

        wyslij("POST", "ce_ala", "/api/reports/clans/" + klan, zgloszenie).andExpect(status().isConflict());   // wlasny klan
        JsonNode utworzone = tresc(wyslij("POST", "ce_bob", "/api/reports/clans/" + klan, zgloszenie)
            .andExpect(status().isCreated()));
        assertThat(utworzone.get("context").asText()).isEqualTo("CLAN");
        assertThat(utworzone.get("reportedUsername").asText()).isEqualTo("ce_ala");
        assertThat(utworzone.get("clanId").asLong()).isEqualTo(klan);
        assertThat(utworzone.get("evidence").get(0).get("author").asText()).isEqualTo("[NS] Nocne Sowy");
        assertThat(utworzone.get("evidence").get(0).get("text").asText()).isEqualTo("opis do zgloszenia");

        // To samo drugi raz, dopoki pierwsze czeka
        wyslij("POST", "ce_bob", "/api/reports/clans/" + klan, zgloszenie).andExpect(status().isConflict());
        // Inna osoba moze zglosic; nieistniejacy klan to 404
        wyslij("POST", "ce_cyd", "/api/reports/clans/" + klan, zgloszenie).andExpect(status().isCreated());
        wyslij("POST", "ce_cyd", "/api/reports/clans/987654", zgloszenie).andExpect(status().isNotFound());
        // Zwykla sciezka nie przyjmuje kontekstu CLAN
        wyslij("POST", "ce_dan", "/api/reports/on/ce_ala", zgloszenie).andExpect(status().isConflict());

        // Administrator widzi zgloszenie w panelu, a zglaszajacy - w "moich"
        admin("/api/reports/admin").andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].context").value("CLAN"))
            .andExpect(jsonPath("$.content[0].clanId").value(klan));
        get_("ce_bob", "/api/reports/mine").andExpect(jsonPath("$.content[0].context").value("CLAN"));

        // Rozwiazanie klanu (skutek zasadnego zgloszenia) zostawia zgloszenie z dowodem, bez odnosnika do klanu
        wyslij("DELETE", "ce_szef", "/api/clans/" + klan, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        JsonNode po = tresc(admin("/api/reports/admin").andExpect(status().isOk()));
        assertThat(po.get("content").size()).isEqualTo(2);
        assertThat(po.get("content").get(0).get("clanId").isNull()).isTrue();
        assertThat(po.get("content").get(0).get("evidence").get(0).get("author").asText()).isEqualTo("[NS] Nocne Sowy");
    }

    /* ========================== ogloszenie i zasady ========================== */

    @Test
    @DisplayName("ogloszenie i zasady: ustawia zarzad, widza czlonkowie i zaproszeni (zasady); obcy nic; puste zdejmuje")
    void announcementAndRules() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");

        // Zwykly czlonek nie ustawia
        wyslij("PUT", "ce_bob", "/api/clans/" + klan, Map.of("announcement", "moje")).andExpect(status().isConflict());
        wyslij("PUT", "ce_bob", "/api/clans/" + klan, Map.of("rules", "moje")).andExpect(status().isConflict());

        wyslij("PUT", "ce_ala", "/api/clans/" + klan,
            Map.of("announcement", "  W sobote jedziemy na koncert!  ", "rules", "1. Szanujemy sie.\n2. Bez spamu.")).andExpect(status().isOk())
            .andExpect(jsonPath("$.announcement").value("W sobote jedziemy na koncert!"))
            .andExpect(jsonPath("$.announcementAt").exists());
        String kiedy = tresc(get_("ce_bob", "/api/clans/" + klan)).get("announcementAt").asText();
        get_("ce_bob", "/api/clans/" + klan).andExpect(jsonPath("$.rules").value("1. Szanujemy sie.\n2. Bez spamu."));

        // Ta sama tresc nie przesuwa daty; opis zmieniony osobno nie rusza ogloszenia
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("announcement", "W sobote jedziemy na koncert!", "description", "nowy opis"))
            .andExpect(jsonPath("$.announcementAt").value(kiedy)).andExpect(jsonPath("$.rules").exists());

        // Obcy widzi klan, ale bez ogloszenia i zasad; administrator aplikacji widzi
        get_("ce_dan", "/api/clans/" + klan).andExpect(jsonPath("$.announcement").doesNotExist())
            .andExpect(jsonPath("$.rules").doesNotExist());
        get_("ce_szef", "/api/clans/" + klan).andExpect(jsonPath("$.announcement").value("W sobote jedziemy na koncert!"));

        // Zaproszony widzi zasady w zaproszeniu, zanim je przyjmie
        wyslij("POST", "ce_bob", "/api/clans/" + klan + "/invitations", Map.of("username", "ce_cyd")).andExpect(status().isOk());
        get_("ce_cyd", "/api/clans/mine").andExpect(jsonPath("$.invitations[0].rules").value("1. Szanujemy sie.\n2. Bez spamu."));

        // Puste = zdjecie
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("announcement", "", "rules", " ")).andExpect(status().isOk())
            .andExpect(jsonPath("$.announcement").doesNotExist()).andExpect(jsonPath("$.announcementAt").doesNotExist())
            .andExpect(jsonPath("$.rules").doesNotExist());
        // Za dlugie - odrzucone
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("announcement", "x".repeat(501))).andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("rules", "x".repeat(601))).andExpect(status().isUnprocessableEntity());
    }

    /* ============================= muzyka klanu ============================= */

    private Artist artysta(String id, String nazwa, String... gatunki) {
        Artist a = artists.save(new Artist(id, nazwa, null));
        a.applyGenres(java.util.Set.of(gatunki));
        return a;
    }

    @Test
    @DisplayName("gust klanu: tylko to, co lubia co najmniej dwie osoby, bez profili ograniczonych i osob z blokady")
    void taste() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");

        Artist a = artysta("ce-a", "Artysta A", "rock", "indie");
        Artist b = artysta("ce-b", "Artysta B", "jazz");
        Artist c = artysta("ce-c", "Artysta C", "rock");
        ala.getFavoriteArtists().addAll(List.of(a, b, c));
        bob.getFavoriteArtists().addAll(List.of(a, c));
        cyd.getFavoriteArtists().addAll(List.of(c));
        em.flush();

        JsonNode gust = tresc(get_("ce_ala", "/api/clans/" + klan + "/taste").andExpect(status().isOk()));
        assertThat(gust.get("members").asInt()).isEqualTo(3);
        assertThat(gust.get("counted").asInt()).isEqualTo(3);
        // C lubia troje, A dwoje, B tylko ala - B nie ma prawa sie pojawic
        assertThat(gust.get("artists").size()).isEqualTo(2);
        assertThat(gust.get("artists").get(0).get("name").asText()).isEqualTo("Artysta C");
        assertThat(gust.get("artists").get(0).get("count").asInt()).isEqualTo(3);
        assertThat(gust.get("artists").get(1).get("name").asText()).isEqualTo("Artysta A");
        // "rock": ala, bob, cyd; "indie": tylko ala i bob (przez A); "jazz": tylko ala
        assertThat(gust.get("genres").get(0).get("name").asText()).isEqualTo("rock");
        assertThat(gust.get("genres").get(0).get("count").asInt()).isEqualTo(3);
        assertThat(gust.toString()).doesNotContain("jazz").doesNotContain("ce_bob");
        // Rozdzielczosc: indie ma dwie osoby (ala, bob przez A)
        assertThat(gust.get("genres").size()).isEqualTo(2);

        // Profil ograniczony (tylko dla znajomych) nie wchodzi do zestawienia
        cyd.setPrivacy(ProfileVisibility.FRIENDS, InvitePolicy.EVERYONE, com.musicclubapp.entity.ClanInvitePolicy.EVERYONE, true, true, false, true);
        em.flush();
        JsonNode bezCyda = tresc(get_("ce_ala", "/api/clans/" + klan + "/taste"));
        assertThat(bezCyda.get("counted").asInt()).isEqualTo(2);
        assertThat(bezCyda.get("artists").get(0).get("name").asText()).isEqualTo("Artysta A");
        assertThat(bezCyda.get("artists").get(0).get("count").asInt()).isEqualTo(2);
        assertThat(bezCyda.get("artists").size()).isEqualTo(2);   // A i C (C: ala + bob)

        // Osoba z blokady ogladajacego nie wchodzi do liczenia; z jedna osoba nie ma zestawienia
        wyslij("PUT", "ce_ala", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());
        JsonNode bezBoba = tresc(get_("ce_ala", "/api/clans/" + klan + "/taste"));
        assertThat(bezBoba.get("members").asInt()).isEqualTo(2);
        assertThat(bezBoba.get("artists").size()).isZero();
        assertThat(bezBoba.get("genres").size()).isZero();

        // Gust to rekonesans: obcy widzi go dla klanu z przegladarki (domyslnie), ale nie dla ukrytego
        get_("ce_dan", "/api/clans/" + klan + "/taste").andExpect(status().isOk());
        wyslij("PUT", "ce_ala", "/api/clans/" + klan, Map.of("listed", false)).andExpect(status().isOk());
        get_("ce_dan", "/api/clans/" + klan + "/taste").andExpect(status().isConflict());
        get_("ce_szef", "/api/clans/" + klan + "/taste").andExpect(status().isOk());
    }

    @Test
    @DisplayName("utwor tygodnia: tylko linki do utworow, limit na osobe, duplikaty, glosowanie, prowadzacy i zamkniecie po tygodniu")
    void weeklyTrack() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        String adres = "/api/clans/" + klan + "/tracks";

        get_("ce_ala", adres).andExpect(jsonPath("$.tracks.length()").value(0)).andExpect(jsonPath("$.remaining").value(3))
            .andExpect(jsonPath("$.perPerson").value(3)).andExpect(jsonPath("$.weekStart").value(poniedzialek().toString()));

        // Zle linki
        wyslij("POST", "ce_bob", adres, Map.of("url", "cos zupelnie innego")).andExpect(status().isConflict());
        wyslij("POST", "ce_bob", adres, Map.of("url", PLAYLISTA)).andExpect(status().isConflict());
        // Obcy nie proponuje
        wyslij("POST", "ce_dan", adres, Map.of("url", UTWOR_1)).andExpect(status().isConflict());

        long t1 = tresc(wyslij("POST", "ce_bob", adres, Map.of("url", UTWOR_1, "note", "  na poniedzialek  "))
            .andExpect(status().isCreated())).get("id").asLong();
        long t2 = tresc(wyslij("POST", "ce_cyd", adres, Map.of("url", UTWOR_2)).andExpect(status().isCreated())).get("id").asLong();
        // Ten sam utwor drugi raz w tym samym tygodniu
        wyslij("POST", "ce_ala", adres, Map.of("url", UTWOR_1)).andExpect(status().isConflict());
        // Limit na osobe
        wyslij("POST", "ce_bob", adres, Map.of("url", UTWOR_3)).andExpect(status().isCreated());
        wyslij("POST", "ce_bob", adres, Map.of("url", UTWOR_4)).andExpect(status().isCreated());
        wyslij("POST", "ce_bob", adres, Map.of("url", "https://open.spotify.com/track/1301WleyT98MSxVHPZCA6M")).andExpect(status().isConflict());
        get_("ce_bob", adres).andExpect(jsonPath("$.remaining").value(0));

        // Bez glosow nikt nie prowadzi
        JsonNode bezGlosow = tresc(get_("ce_ala", adres));
        for (JsonNode t : bezGlosow.get("tracks")) {
            assertThat(t.get("leader").asBoolean()).isFalse();
        }

        // Glosowanie: idempotentne, cofalne, z licznikiem i "moim glosem"
        wyslij("PUT", "ce_ala", adres + "/" + t2 + "/vote", null).andExpect(status().isNoContent());
        wyslij("PUT", "ce_ala", adres + "/" + t2 + "/vote", null).andExpect(status().isNoContent());
        wyslij("PUT", "ce_bob", adres + "/" + t2 + "/vote", null).andExpect(status().isNoContent());
        wyslij("PUT", "ce_cyd", adres + "/" + t1 + "/vote", null).andExpect(status().isNoContent());
        JsonNode lista = tresc(get_("ce_ala", adres));
        assertThat(lista.get("tracks").get(0).get("id").asLong()).isEqualTo(t2);   // najwiecej glosow pierwszy
        assertThat(lista.get("tracks").get(0).get("votes").asInt()).isEqualTo(2);
        assertThat(lista.get("tracks").get(0).get("iVoted").asBoolean()).isTrue();
        assertThat(lista.get("tracks").get(0).get("leader").asBoolean()).isTrue();
        assertThat(lista.get("tracks").get(0).get("title").asText()).isEqualTo("Tytul utworu");
        assertThat(lista.get("tracks").get(0).get("embedUrl").asText()).startsWith("https://open.spotify.com/embed/track/");
        assertThat(lista.get("tracks").get(1).get("leader").asBoolean()).isFalse();
        assertThat(lista.get("tracks").get(1).get("id").asLong()).isEqualTo(t1);
        assertThat(lista.get("tracks").get(1).get("note").asText()).isEqualTo("na poniedzialek");
        assertThat(voteRepository.count()).isEqualTo(3);
        wyslij("DELETE", "ce_ala", adres + "/" + t2 + "/vote", null).andExpect(status().isNoContent());
        assertThat(voteRepository.count()).isEqualTo(2);

        // Obcy nie glosuje ani nie czyta
        wyslij("PUT", "ce_dan", adres + "/" + t2 + "/vote", null).andExpect(status().isConflict());
        get_("ce_dan", adres).andExpect(status().isConflict());

        // Usuwanie: proponujacy albo zarzad, nie inny czlonek
        wyslij("DELETE", "ce_cyd", adres + "/" + t1, null).andExpect(status().isConflict());
        wyslij("DELETE", "ce_bob", adres + "/" + t1, null).andExpect(status().isNoContent());
        wyslij("DELETE", "ce_ala", adres + "/" + t2, null).andExpect(status().isNoContent());   // zalozyciel
        em.flush();
        em.clear();
        assertThat(voteRepository.count()).isZero();   // glosy zniknely z propozycjami
    }

    @Test
    @DisplayName("utwor tygodnia: po koncu tygodnia glosowanie zamkniete, zwyciezca w historii, osoba z blokady ukryta")
    void weeklyHistoryAndBlocks() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        var klanEncja = clanRepository.findById(klan).orElseThrow();
        LocalDate zeszly = poniedzialek().minusWeeks(1);
        ClanTrack stary1 = trackRepository.save(new ClanTrack(klanEncja, bob, MusicProvider.SPOTIFY, MusicKind.TRACK,
            "4uLU6hMCjMI75M1A2tKUQC", "Stary hit", null, null, zeszly, LocalDateTime.now().minusDays(8)));
        ClanTrack stary2 = trackRepository.save(new ClanTrack(klanEncja, cyd, MusicProvider.SPOTIFY, MusicKind.TRACK,
            "3n3Ppam7vgaVa1iaRUc9Lp", "Drugi stary", null, null, zeszly, LocalDateTime.now().minusDays(7)));
        ClanTrack bezGlosow = trackRepository.save(new ClanTrack(klanEncja, bob, MusicProvider.SPOTIFY, MusicKind.TRACK,
            "7ouMYWpwJ422jRcDASZB7P", "Nikt nie chcial", null, null, zeszly.minusWeeks(1), LocalDateTime.now().minusDays(15)));
        voteRepository.save(new com.musicclubapp.entity.ClanTrackVote(stary2, ala, LocalDateTime.now().minusDays(6)));
        voteRepository.save(new com.musicclubapp.entity.ClanTrackVote(stary1, cyd, LocalDateTime.now().minusDays(6)));
        voteRepository.save(new com.musicclubapp.entity.ClanTrackVote(stary1, ala, LocalDateTime.now().minusDays(6)));
        em.flush();

        String adres = "/api/clans/" + klan + "/tracks";
        // Miniony tydzien: glosowanie zamkniete (takze cofniecie glosu)
        wyslij("PUT", "ce_bob", adres + "/" + stary1.getId() + "/vote", null).andExpect(status().isConflict());
        wyslij("DELETE", "ce_ala", adres + "/" + stary1.getId() + "/vote", null).andExpect(status().isConflict());

        // Historia: wygral stary1 (2 glosy); tydzien bez glosow pominiety; biezace propozycje nie zawieraja starych
        JsonNode odpowiedz = tresc(get_("ce_ala", adres).andExpect(status().isOk()));
        assertThat(odpowiedz.get("tracks").size()).isZero();
        assertThat(odpowiedz.get("previous").size()).isEqualTo(1);
        assertThat(odpowiedz.get("previous").get(0).get("title").asText()).isEqualTo("Stary hit");
        assertThat(odpowiedz.get("previous").get(0).get("votes").asInt()).isEqualTo(2);
        assertThat(odpowiedz.get("previous").get(0).get("weekStart").asText()).isEqualTo(zeszly.toString());
        assertThat(bezGlosow.getId()).isNotNull();

        // Ala blokuje boba: jego propozycje (i on jako zwyciezca) znikaja z jej widoku
        wyslij("PUT", "ce_ala", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());
        JsonNode bezBoba = tresc(get_("ce_ala", adres));
        assertThat(bezBoba.get("previous").size()).isEqualTo(1);
        assertThat(bezBoba.get("previous").get(0).get("title").asText()).isEqualTo("Drugi stary");
    }

    /* ============================ klan idzie na koncert ============================ */

    private MusicEvent wydarzenie(String id, String nazwa, int dni) {
        return events.save(WydarzeniaTestowe.wydarzenie(id, nazwa, LocalDate.now(EventImportService.STREFA).plusDays(dni),
            "Warszawa", "Rock", null, "Zespol " + id));
    }

    private void zapisz(String kto, long wydarzenie, String status, Boolean ukryty) throws Exception {
        Map<String, Object> cialo = new HashMap<>();
        cialo.put("status", status);
        if (ukryty != null) {
            cialo.put("hidden", ukryty);
        }
        wyslij("PUT", kto, "/api/events/" + wydarzenie + "/participation", cialo).andExpect(status().isOk());
        em.flush();
    }

    @Test
    @DisplayName("koncerty klanu: zapisy czlonkow, ukryci tylko w liczniku, bez obcych, wycofanych i minionych; 'zapytal' po poscie klanu")
    void clanEvents() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");

        MusicEvent bliski = wydarzenie("ce-ev1", "Koncert Bliski", 5);
        MusicEvent daleki = wydarzenie("ce-ev2", "Koncert Daleki", 40);
        MusicEvent wycofany = wydarzenie("ce-ev3", "Koncert Wycofany", 8);
        MusicEvent tylkoObcy = wydarzenie("ce-ev4", "Tylko Obcy", 9);
        em.flush();

        zapisz("ce_ala", bliski.getId(), "GOING", false);
        zapisz("ce_bob", bliski.getId(), "GOING", true);     // ukryty - tylko w liczniku
        zapisz("ce_cyd", bliski.getId(), "INTERESTED", false);
        zapisz("ce_bob", daleki.getId(), "GOING", false);
        zapisz("ce_ala", wycofany.getId(), "GOING", false);
        zapisz("ce_dan", tylkoObcy.getId(), "GOING", false);  // obcy - nie jest w klanie
        zapisz("ce_dan", bliski.getId(), "GOING", false);
        em.flush();
        wycofany.withdraw(LocalDateTime.now());
        em.flush();

        String adres = "/api/clans/" + klan + "/events";
        JsonNode lista = tresc(get_("ce_ala", adres).andExpect(status().isOk()));
        assertThat(lista.size()).isEqualTo(2);                       // bez wycofanego i bez tego, gdzie tylko obcy
        JsonNode pierwszy = lista.get(0);
        assertThat(pierwszy.get("name").asText()).isEqualTo("Koncert Bliski");
        assertThat(pierwszy.get("goingCount").asInt()).isEqualTo(2);  // ala i bob (obcy dan sie nie liczy)
        assertThat(pierwszy.get("going").size()).isEqualTo(1);        // bob jest ukryty
        assertThat(pierwszy.get("going").get(0).get("username").asText()).isEqualTo("ce_ala");
        assertThat(pierwszy.get("interestedCount").asInt()).isEqualTo(1);
        assertThat(pierwszy.get("mine").asText()).isEqualTo("GOING");
        assertThat(pierwszy.get("askedPostId").isNull()).isTrue();
        assertThat(lista.get(1).get("name").asText()).isEqualTo("Koncert Daleki");

        // Bob widzi siebie zawsze, nawet ukrytego
        JsonNode uBoba = tresc(get_("ce_bob", adres)).get(0);
        assertThat(uBoba.get("going").size()).isEqualTo(2);

        // Osoba z blokady nie wystepuje w zapisach
        wyslij("PUT", "ce_ala", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());
        JsonNode zBlokada = tresc(get_("ce_ala", adres));
        assertThat(zBlokada.size()).isEqualTo(1);                     // "Daleki" mial tylko boba
        assertThat(zBlokada.get(0).get("goingCount").asInt()).isEqualTo(1);
        wyslij("DELETE", "ce_ala", "/api/blocks/ce_bob", null).andExpect(status().isNoContent());

        // Post klanu "kto jedzie?" pod wydarzeniem: lista pokazuje, ze klan juz pytal;
        // post jest w klanie z odnosnikiem do wydarzenia, ale nie pod wydarzeniem dla wszystkich
        Map<String, Object> pola = new HashMap<>();
        pola.put("content", "Kto jedzie na Koncert Bliski?");
        pola.put("clanId", klan);
        pola.put("eventId", bliski.getId());
        MockMultipartFile czesc = new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(pola));
        long postId = tresc(mvc.perform(multipart("/api/posts").file(czesc).with(user("ce_ala")).with(csrf())
            .header("Accept-Language", "pl")).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        assertThat(tresc(get_("ce_bob", adres)).get(0).get("askedPostId").asLong()).isEqualTo(postId);
        get_("ce_bob", "/api/posts?clan=" + klan).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].event.id").value(bliski.getId()));
        get_("ce_dan", "/api/posts?event=" + bliski.getId()).andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));

        // Obcy nie widzi listy; administrator aplikacji widzi
        get_("ce_dan", adres).andExpect(status().isConflict());
        get_("ce_szef", adres).andExpect(status().isOk());
    }

    /* ================================ sprzatanie ================================ */

    @Test
    @DisplayName("rozwiazanie klanu i usuniecie konta zabieraja reakcje, odpowiedzi, propozycje i glosy bez bledow kluczy obcych")
    void cleanup() throws Exception {
        long klan = zaloz("ce_ala", "Nocne Sowy", "NS");
        dolacz(klan, "ce_ala", "ce_bob");
        dolacz(klan, "ce_ala", "ce_cyd");
        long m1 = napisz("ce_bob", klan, "od boba");
        long m2 = odpowiedz("ce_cyd", klan, "odpowiedz cyda", m1);
        wyslij("PUT", "ce_ala", "/api/clans/" + klan + "/chat/" + m1 + "/reaction", Map.of("emoji", "FIRE")).andExpect(status().isOk());
        wyslij("PUT", "ce_bob", "/api/clans/" + klan + "/chat/" + m2 + "/reaction", Map.of("emoji", "HEART")).andExpect(status().isOk());
        long t1 = tresc(wyslij("POST", "ce_bob", "/api/clans/" + klan + "/tracks", Map.of("url", UTWOR_1))
            .andExpect(status().isCreated())).get("id").asLong();
        wyslij("PUT", "ce_ala", "/api/clans/" + klan + "/tracks/" + t1 + "/vote", null).andExpect(status().isNoContent());
        wyslij("POST", "ce_ala", "/api/clans/" + klan + "/tracks", Map.of("url", UTWOR_2)).andExpect(status().isCreated());
        wyslij("POST", "ce_cyd", "/api/reports/clans/" + klan, Map.of("reason", "SPAM", "context", "CLAN",
            "description", "opis zgloszenia klanu")).andExpect(status().isCreated());
        em.flush();

        // Bob odchodzi z aplikacji: jego wiadomosc, propozycja, reakcja i glosy pod nimi znikaja;
        // odpowiedz cyda zostaje, bez cytatu
        em.clear();
        deletion.erase(users.findByUsername("ce_bob").orElseThrow());
        em.flush();
        em.clear();
        assertThat(messageRepository.count()).isEqualTo(1);
        assertThat(messageRepository.findAll().get(0).getReplyTo()).isNull();
        assertThat(reactionRepository.count()).isZero();     // ala pod wiadomoscia boba, bob pod odpowiedzia
        assertThat(trackRepository.count()).isEqualTo(1);    // zostala propozycja ali
        assertThat(voteRepository.count()).isZero();         // glos ali byl pod propozycja boba
        assertThat(reportRepository.count()).isEqualTo(1);   // zgloszenie klanu zostaje

        // Rozwiazanie klanu z tym, co w nim zostalo, przechodzi bez bledow kluczy obcych i zostawia zgloszenie
        wyslij("PUT", "ce_cyd", "/api/clans/" + klan + "/chat/" + m2 + "/reaction", Map.of("emoji", "WOW")).andExpect(status().isOk());
        wyslij("DELETE", "ce_ala", "/api/clans/" + klan, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(messageRepository.count()).isZero();
        assertThat(reactionRepository.count()).isZero();
        assertThat(trackRepository.count()).isZero();
        assertThat(reportRepository.count()).isEqualTo(1);
        assertThat(reportRepository.findAll().get(0).getClan()).isNull();
    }
}
