package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.MessageRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Usuwanie wlasnych wiadomosci (rozmowy i czat klanu) i podglad linkow wklejonych w tresc - przez prawdziwe API.
 * Usunieta wiadomosc zostaje jako slad "usunieta", a druga strona dostaje to przy odpytywaniu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Czat - usuwanie wiadomosci i linki w tresci")
class ChatDeleteAndLinksFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MessageRepository messages;
    @Autowired private ClanInvitationRepository invitations;
    @Autowired private ReportRepository reports;
    @Autowired private ClanMessageRepository clanMessages;
    @Autowired private ClanMessageReactionRepository clanReactions;
    @Autowired private EntityManager em;

    @MockBean private PushService push;
    @MockBean private MusicMetadataService metadata;

    @BeforeEach
    void setUp() {
        User ala = users.save(new User("cd_ala", "cd_ala@example.com", "x"));
        User bob = users.save(new User("cd_bob", "cd_bob@example.com", "x"));
        users.save(new User("cd_cyd", "cd_cyd@example.com", "x"));
        ala.addFriend(bob);
        users.save(ala);
        em.flush();
        given(metadata.fetch(any())).willReturn(new MusicMetadataService.Metadata("Teledysk", "https://i.ytimg.com/vi/x/hqdefault.jpg"));
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions zapytaj(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var b = switch (metoda) {
            case "DELETE" -> delete(adres);
            case "PUT" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(adres);
            default -> post(adres);
        };
        b.with(user(kto)).with(csrf()).header("Accept-Language", "pl");
        if (tresc != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(b);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode napiszDo(String kto, String doKogo, String tekst) throws Exception {
        JsonNode m = tresc(wyslij("POST", kto, "/api/messages/with/" + doKogo, Map.of("content", tekst))
            .andExpect(status().isCreated()));
        em.flush();
        return m;
    }

    private long nieprzeczytane(String kto) throws Exception {
        return tresc(zapytaj(kto, "/api/messages/unread-count").andExpect(status().isOk())).get("count").asLong();
    }

    /* ---------------------------- linki w tresci ---------------------------- */

    @Test
    @DisplayName("parser: zwykly YouTube (watch, youtu.be, shorts, live, m.) tylko w czacie; muzyka jak dotad; inne adresy nie")
    void findInChat() {
        for (String tekst : List.of(
                "zobacz https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDx&start_radio=1",
                "https://youtu.be/dQw4w9WgXcQ?si=abc",
                "krotkie: youtube.com/shorts/dQw4w9WgXcQ !",
                "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
                "na zywo https://www.youtube.com/live/dQw4w9WgXcQ")) {
            var link = MusicLinkParser.findInChat(tekst).orElseThrow(() -> new AssertionError(tekst));
            assertThat(link.provider()).isEqualTo(MusicProvider.YOUTUBE);
            assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
            assertThat(link.externalId()).isEqualTo("dQw4w9WgXcQ");
            // w poscie zwykly YouTube dalej nie przechodzi
            assertThat(MusicLinkParser.parse(tekst)).isEmpty();
        }
        assertThat(MusicLinkParser.findInChat("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC fajne"))
            .get().extracting(l -> l.provider()).isEqualTo(MusicProvider.SPOTIFY);
        // za krotki identyfikator, obca domena udajaca YouTube, zwykly tekst - nic
        assertThat(MusicLinkParser.findInChat("https://youtu.be/abc")).isEmpty();
        assertThat(MusicLinkParser.findInChat("https://notyoutube.com/watch?v=dQw4w9WgXcQ")).isEmpty();
        assertThat(MusicLinkParser.findInChat("https://www.youtube.com/watch?v=dQw4w9WgXcQx1")).isEmpty();
        assertThat(MusicLinkParser.findInChat("o 19 pod klubem")).isEmpty();
        assertThat(MusicLinkParser.findInChat(null)).isEmpty();
    }

    @Test
    @DisplayName("rozmowa: link YouTube w tresci dostaje odtwarzacz i tytul, tekst zostaje; zwykly tekst - bez nagrania")
    void dmLinkPreview() throws Exception {
        JsonNode m = napiszDo("cd_ala", "cd_bob", "a tu teledysk https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(m.get("content").asText()).isEqualTo("a tu teledysk https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(m.get("musicProvider").asText()).isEqualTo("YOUTUBE");
        assertThat(m.get("musicEmbedUrl").asText()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(m.get("musicTitle").asText()).isEqualTo("Teledysk");
        assertThat(napiszDo("cd_ala", "cd_bob", "bez linku").get("musicEmbedUrl").isNull()).isTrue();
        // nagranie z przycisku ma pierwszenstwo przed linkiem w tresci
        Map<String, Object> z = new HashMap<>();
        z.put("content", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        z.put("musicUrl", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");
        z.put("musicKind", "TRACK");
        JsonNode oba = tresc(wyslij("POST", "cd_ala", "/api/messages/with/cd_bob", z).andExpect(status().isCreated()));
        assertThat(oba.get("musicProvider").asText()).isEqualTo("SPOTIFY");
    }

    /* ---------------------------- usuwanie w rozmowie ---------------------------- */

    @Test
    @DisplayName("rozmowa: autor usuwa wiadomosc u obu stron - zostaje slad bez tresci i nagrania; drugi raz bez zmian")
    void dmDeleteOwn() throws Exception {
        long id = napiszDo("cd_ala", "cd_bob", "pomylka https://youtu.be/dQw4w9WgXcQ").get("id").asLong();
        JsonNode u = tresc(wyslij("DELETE", "cd_ala", "/api/messages/" + id, null).andExpect(status().isOk()));
        assertThat(u.get("deleted").asBoolean()).isTrue();
        assertThat(u.get("content").isNull()).isTrue();
        assertThat(u.get("musicEmbedUrl").isNull()).isTrue();
        em.flush();
        em.clear();

        JsonNode historia = tresc(zapytaj("cd_bob", "/api/messages/with/cd_ala").andExpect(status().isOk()));
        JsonNode slad = historia.get("content").get(0);
        assertThat(slad.get("id").asLong()).isEqualTo(id);
        assertThat(slad.get("deleted").asBoolean()).isTrue();
        assertThat(slad.get("content").isNull()).isTrue();
        assertThat(slad.get("musicTitle").isNull()).isTrue();
        // ostatnia wiadomosc na liscie rozmow tez jest sladem
        JsonNode rozmowy = tresc(zapytaj("cd_bob", "/api/messages/conversations").andExpect(status().isOk()));
        assertThat(rozmowy.get(0).get("lastMessage").get("deleted").asBoolean()).isTrue();

        wyslij("DELETE", "cd_ala", "/api/messages/" + id, null).andExpect(status().isOk());
        assertThat(messages.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("rozmowa: cudzej, nieistniejacej i ukrytej u siebie wiadomosci nie usuniesz (404); z zakazem pisania - mozna")
    void dmDeleteRules() throws Exception {
        long id = napiszDo("cd_ala", "cd_bob", "moja").get("id").asLong();
        wyslij("DELETE", "cd_bob", "/api/messages/" + id, null).andExpect(status().isNotFound());
        wyslij("DELETE", "cd_cyd", "/api/messages/" + id, null).andExpect(status().isNotFound());
        wyslij("DELETE", "cd_ala", "/api/messages/999999", null).andExpect(status().isNotFound());
        assertThat(messages.findById(id).orElseThrow().isDeleted()).isFalse();

        User ala = users.findByUsername("cd_ala").orElseThrow();
        ala.setBannedUntil(com.musicclubapp.entity.BanKind.MESSAGING, LocalDateTime.now().plusDays(1));
        users.save(ala);
        em.flush();
        wyslij("DELETE", "cd_ala", "/api/messages/" + id, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("rozmowa skasowana u siebie: wlasnej wiadomosci z niej juz nie usuniesz (nie widzisz jej) - 404")
    void dmDeleteHiddenConversation() throws Exception {
        long moja = napiszDo("cd_ala", "cd_bob", "od ali").get("id").asLong();
        wyslij("DELETE", "cd_ala", "/api/messages/with/cd_bob", null).andExpect(status().isNoContent());
        em.flush();
        wyslij("DELETE", "cd_ala", "/api/messages/" + moja, null).andExpect(status().isNotFound());
        assertThat(messages.findById(moja).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("rozmowa: usunieta nieprzeczytana nie liczy sie do nieprzeczytanych (ikona i lista rozmow)")
    void dmDeletedNotUnread() throws Exception {
        long id = napiszDo("cd_ala", "cd_bob", "raz").get("id").asLong();
        napiszDo("cd_ala", "cd_bob", "dwa");
        assertThat(nieprzeczytane("cd_bob")).isEqualTo(2);
        wyslij("DELETE", "cd_ala", "/api/messages/" + id, null).andExpect(status().isOk());
        em.flush();
        assertThat(nieprzeczytane("cd_bob")).isEqualTo(1);
        JsonNode rozmowy = tresc(zapytaj("cd_bob", "/api/messages/conversations").andExpect(status().isOk()));
        assertThat(rozmowy.get(0).get("unread").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("rozmowa: odpytywanie oddaje usuniete od podanego czasu serwera (z zapasem), a bez czasu - nic")
    void dmSyncDeleted() throws Exception {
        long id = napiszDo("cd_ala", "cd_bob", "zaraz zniknie").get("id").asLong();
        JsonNode przed = tresc(zapytaj("cd_bob", "/api/messages/with/cd_ala/sync?after=" + id).andExpect(status().isOk()));
        assertThat(przed.get("deletedIds")).isEmpty();
        String czasSerwera = przed.get("serverTime").asText();

        wyslij("DELETE", "cd_ala", "/api/messages/" + id, null).andExpect(status().isOk());
        em.flush();

        JsonNode po = tresc(zapytaj("cd_bob", "/api/messages/with/cd_ala/sync?after=" + id + "&changedSince=" + czasSerwera)
            .andExpect(status().isOk()));
        assertThat(po.get("deletedIds").get(0).asLong()).isEqualTo(id);
        // bez "zmian od" - tylko nowe wiadomosci
        JsonNode bez = tresc(zapytaj("cd_bob", "/api/messages/with/cd_ala/sync?after=" + id).andExpect(status().isOk()));
        assertThat(bez.get("deletedIds")).isEmpty();
        // usuniecie sprzed podanego czasu (z zapasem 30 s) juz nie wraca
        String pozniej = LocalDateTime.now().plusMinutes(5).toString();
        JsonNode stare = tresc(zapytaj("cd_bob", "/api/messages/with/cd_ala/sync?changedSince=" + pozniej)
            .andExpect(status().isOk()));
        assertThat(stare.get("deletedIds")).isEmpty();
        // osoba spoza rozmowy nie dowie sie niczego
        zapytaj("cd_cyd", "/api/messages/with/cd_ala/sync?changedSince=" + czasSerwera).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rozmowa: usunieta wiadomosc nie trafia do zgloszenia ani do pobranych danych")
    void dmDeletedNotInReportOrExport() throws Exception {
        napiszDo("cd_bob", "cd_ala", "zostaje");
        long id = napiszDo("cd_bob", "cd_ala", "skasowana obelga").get("id").asLong();
        wyslij("DELETE", "cd_bob", "/api/messages/" + id, null).andExpect(status().isOk());
        em.flush();

        long zgloszenie = tresc(wyslij("POST", "cd_ala", "/api/reports/on/cd_bob", Map.of("reason", "HARASSMENT",
            "context", "CONVERSATION", "description", "obrazliwa rozmowa")).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        em.clear();
        List<String> dowody = new ArrayList<>();
        reports.findById(zgloszenie).orElseThrow().getEvidence().forEach(e -> dowody.add(e.getText()));
        assertThat(dowody).contains("zostaje").doesNotContain("skasowana obelga", "", null);

        assertThat(messages.ofUser(users.findByUsername("cd_bob").orElseThrow().getId()))
            .extracting(m -> m.getContent()).containsExactly("zostaje");
    }

    /* ---------------------------- czat klanu ---------------------------- */

    private long klanAliIBoba() throws Exception {
        long klan = tresc(wyslij("POST", "cd_ala", "/api/clans", Map.of("name", "Usuwacze", "tag", "USU"))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        wyslij("POST", "cd_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "cd_bob")).andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitations.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals("cd_bob")).findFirst().orElseThrow().getId();
        wyslij("POST", "cd_bob", "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
        return klan;
    }

    private JsonNode wKlanie(String kto, long klan, Map<String, Object> cialo) throws Exception {
        JsonNode m = tresc(wyslij("POST", kto, "/api/clans/" + klan + "/chat", cialo).andExpect(status().isCreated()));
        em.flush();
        return m;
    }

    @Test
    @DisplayName("klan: link w tresci dostaje odtwarzacz; usuniecie zostawia slad, czysci reakcje, odpowiedz pokazuje 'usunieta'")
    void clanDeleteAndLinks() throws Exception {
        long klan = klanAliIBoba();
        JsonNode z = wKlanie("cd_ala", klan, Map.of("content", "sluchajcie https://youtu.be/dQw4w9WgXcQ"));
        assertThat(z.get("musicEmbedUrl").asText()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(z.get("musicTitle").asText()).isEqualTo("Teledysk");
        assertThat(z.get("musicUrl").asText()).isEqualTo("https://music.youtube.com/watch?v=dQw4w9WgXcQ");
        long id = z.get("id").asLong();
        wyslij("PUT", "cd_bob", "/api/clans/" + klan + "/chat/" + id + "/reaction", Map.of("emoji", "THUMBS_UP"))
            .andExpect(status().isOk());
        long odpowiedz = wKlanie("cd_bob", klan, Map.of("content", "super", "replyTo", id)).get("id").asLong();

        JsonNode przed = tresc(zapytaj("cd_bob", "/api/clans/" + klan + "/chat/changes").andExpect(status().isOk()));
        assertThat(przed.get("deletedIds")).isEmpty();
        String czas = przed.get("serverTime").asText();

        wyslij("DELETE", "cd_ala", "/api/clans/" + klan + "/chat/" + id, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        JsonNode czat = tresc(zapytaj("cd_bob", "/api/clans/" + klan + "/chat").andExpect(status().isOk()));
        JsonNode slad = czat.get(0);
        assertThat(slad.get("id").asLong()).isEqualTo(id);
        assertThat(slad.get("deleted").asBoolean()).isTrue();
        assertThat(slad.get("content").asText()).isEmpty();
        assertThat(slad.get("musicEmbedUrl").isNull()).isTrue();
        assertThat(slad.get("reactions")).isEmpty();
        assertThat(slad.get("canDelete").asBoolean()).isFalse();
        JsonNode odp = czat.get(1);
        assertThat(odp.get("id").asLong()).isEqualTo(odpowiedz);
        assertThat(odp.get("replyTo").get("deleted").asBoolean()).isTrue();
        assertThat(odp.get("replyTo").get("excerpt").asText()).isEmpty();
        // Autorka i zalozycielka w jednym - zwykla wiadomosc moglaby usunac, slad juz nie ma czego
        JsonNode u = tresc(zapytaj("cd_ala", "/api/clans/" + klan + "/chat").andExpect(status().isOk()));
        assertThat(u.get(0).get("canDelete").asBoolean()).isFalse();
        assertThat(u.get(1).get("canDelete").asBoolean()).isTrue();

        JsonNode po = tresc(zapytaj("cd_bob", "/api/clans/" + klan + "/chat/changes?since=" + czas).andExpect(status().isOk()));
        assertThat(po.get("deletedIds").get(0).asLong()).isEqualTo(id);
        zapytaj("cd_cyd", "/api/clans/" + klan + "/chat/changes?since=" + czas).andExpect(status().is4xxClientError());

        // na usunieta nie da sie zareagowac ani odpowiedziec (404)
        wyslij("PUT", "cd_bob", "/api/clans/" + klan + "/chat/" + id + "/reaction", Map.of("emoji", "THUMBS_UP"))
            .andExpect(status().isNotFound());
        wyslij("POST", "cd_bob", "/api/clans/" + klan + "/chat", Map.of("content", "x", "replyTo", id))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("klan: reakcje usunietej znikaja z bazy; nie liczy sie do rankingu, poziomu aktywnosci ani eksportu")
    void clanDeletedNotCounted() throws Exception {
        long klan = klanAliIBoba();
        long id = wKlanie("cd_ala", klan, Map.of("content", "do usuniecia")).get("id").asLong();
        wKlanie("cd_ala", klan, Map.of("content", "zostaje"));
        wyslij("PUT", "cd_bob", "/api/clans/" + klan + "/chat/" + id + "/reaction", Map.of("emoji", "THUMBS_UP"))
            .andExpect(status().isOk());
        em.flush();
        wyslij("DELETE", "cd_ala", "/api/clans/" + klan + "/chat/" + id, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(clanReactions.findAll()).noneMatch(r -> r.getMessage().getId().equals(id));
        JsonNode ranking = tresc(zapytaj("cd_ala", "/api/clans/" + klan + "/activity").andExpect(status().isOk()));
        assertThat(ranking.get("summary").get("messages").asLong()).isEqualTo(1);
        long ala = users.findByUsername("cd_ala").orElseThrow().getId();
        assertThat(clanMessages.writtenBy(ala)).extracting(m -> m.getContent()).containsExactly("zostaje");

        // poziom aktywnosci liczy tylko nieusuniete: jedna usunieta wiadomosc = cisza
        long klan2 = tresc(wyslij("POST", "cd_cyd", "/api/clans", Map.of("name", "Cisi", "tag", "CIS"))
            .andExpect(status().isCreated())).get("id").asLong();
        long jedyna = wKlanie("cd_cyd", klan2, Map.of("content", "raz")).get("id").asLong();
        assertThat(tresc(zapytaj("cd_cyd", "/api/clans/" + klan2)).get("activityLevel").asText()).isEqualTo("LOW");
        wyslij("DELETE", "cd_cyd", "/api/clans/" + klan2 + "/chat/" + jedyna, null).andExpect(status().isNoContent());
        em.flush();
        assertThat(tresc(zapytaj("cd_cyd", "/api/clans/" + klan2)).get("activityLevel").asText()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("klan: usunieta nieprzeczytana nie blokuje kolejnego powiadomienia na telefon")
    void clanDeletedDoesNotSilencePush() throws Exception {
        long klan = klanAliIBoba();
        long ostatnia = wKlanie("cd_bob", klan, Map.of("content", "jestem")).get("id").asLong();
        wyslij("POST", "cd_bob", "/api/clans/" + klan + "/chat/read", Map.of("upTo", ostatnia))
            .andExpect(status().isNoContent());
        long bob = users.findByUsername("cd_bob").orElseThrow().getId();
        clearInvocations(push);

        long pierwsza = wKlanie("cd_ala", klan, Map.of("content", "pierwsza")).get("id").asLong();
        verify(push).send(eq(bob), any());
        clearInvocations(push);
        wKlanie("cd_ala", klan, Map.of("content", "druga - bob ma juz nieprzeczytana, wiec cisza"));
        verify(push, never()).send(eq(bob), any());

        // obie nieprzeczytane usuniete - nastepna znowu powiadamia
        wyslij("DELETE", "cd_ala", "/api/clans/" + klan + "/chat/" + pierwsza, null).andExpect(status().isNoContent());
        long druga = pierwsza + 1;
        wyslij("DELETE", "cd_ala", "/api/clans/" + klan + "/chat/" + druga, null).andExpect(status().isNoContent());
        em.flush();
        clearInvocations(push);
        wKlanie("cd_ala", klan, Map.of("content", "trzecia"));
        verify(push).send(eq(bob), any());
    }

    @Test
    @DisplayName("klan: usunieta wiadomosc nie liczy sie do nieprzeczytanych drugiej osoby")
    void clanDeletedNotUnread() throws Exception {
        long klan = klanAliIBoba();
        long ostatniaBoba = wKlanie("cd_bob", klan, Map.of("content", "czesc")).get("id").asLong();
        wyslij("POST", "cd_bob", "/api/clans/" + klan + "/chat/read", Map.of("upTo", ostatniaBoba))
            .andExpect(status().isNoContent());
        long a1 = wKlanie("cd_ala", klan, Map.of("content", "jeden")).get("id").asLong();
        wKlanie("cd_ala", klan, Map.of("content", "dwa"));
        assertThat(tresc(zapytaj("cd_bob", "/api/clans/mine/unread")).get("unread").asLong()).isEqualTo(2);
        wyslij("DELETE", "cd_ala", "/api/clans/" + klan + "/chat/" + a1, null).andExpect(status().isNoContent());
        em.flush();
        assertThat(tresc(zapytaj("cd_bob", "/api/clans/mine/unread")).get("unread").asLong()).isEqualTo(1);
    }
}
