package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanEmoji;
import com.musicclubapp.entity.ClanMessage;
import com.musicclubapp.entity.ClanJoinRequest;
import com.musicclubapp.entity.ClanMemberTitle;
import com.musicclubapp.entity.ClanMessageReaction;
import com.musicclubapp.entity.ClanPoll;
import com.musicclubapp.entity.ClanPollVote;
import com.musicclubapp.entity.ClanTitle;
import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanTitleMode;
import com.musicclubapp.entity.ClanTrack;
import com.musicclubapp.entity.ClanTrackVote;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Reaction;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanPollRepository;
import com.musicclubapp.repository.ClanPollVoteRepository;
import com.musicclubapp.repository.ClanTitleRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanTrackRepository;
import com.musicclubapp.repository.ClanTrackVoteRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Pobierz moje dane": co trafia do archiwum, a czego w nim nie ma; haslo i limit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Pobranie wlasnych danych")
class DataExportFlowTest {

    private static final String HASLO = "haslo-eksportu-1";
    private static final byte[] OBRAZ = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private ReactionRepository reactions;
    @Autowired private MessageRepository messages;
    @Autowired private ReportRepository reports;
    @Autowired private ClanRepository clans;
    @Autowired private ClanMemberRepository clanMembers;
    @Autowired private ClanMessageRepository clanMessages;
    @Autowired private ClanMessageReactionRepository clanReactions;
    @Autowired private ClanTrackRepository clanTracks;
    @Autowired private ClanTrackVoteRepository clanTrackVotes;
    @Autowired private ClanJoinRequestRepository clanRequests;
    @Autowired private ClanPollRepository clanPolls;
    @Autowired private ClanPollVoteRepository clanPollVotes;
    @Autowired private ClanTitleRepository clanTitleDefs;
    @Autowired private ClanMemberTitleRepository clanMemberTitles;
    @Autowired private MessageService messageService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private FileStorageService fileStorage;
    @Autowired private EntityManager em;

    private User ala;
    private User bob;
    private User cyd;
    private Path plik;

    @Autowired private com.musicclubapp.repository.CommentRepository comments;
    @Autowired private com.musicclubapp.repository.CommentMentionRepository commentMentions;

    @BeforeEach
    void setUp() throws Exception {
        ala = users.save(new User("ex_ala", "ex_ala@example.com", encoder.encode(HASLO)));
        ala.setCity("Poznań", "poznan", 52.4064, 16.9252);
        bob = users.save(new User("ex_bob", "ex_bob@example.com", encoder.encode("inne-haslo-1")));
        cyd = users.save(new User("ex_cyd", "ex_cyd@example.com", encoder.encode("inne-haslo-2")));
        ala.addFriend(bob);

        plik = fileStorage.getDirectory().resolve("ex-test-obraz.png");
        Files.write(plik, OBRAZ);
        Post moj = new Post(ala, "moj post do eksportu");
        moj.addImage(new PostImage("ex-test-obraz.png"));
        posts.save(moj);
        Post cudzy = posts.save(new Post(bob, "post boba - nie mój"));
        reactions.save(new Reaction(cudzy, ala, ReactionType.FIRE));
        // Komentarz alicji pod postem boba (z oznaczeniem boba) i odpowiedz boba pod nim - ta nie jest alicji
        com.musicclubapp.entity.Comment komentarz = comments.save(
            new com.musicclubapp.entity.Comment(cudzy, ala, null, null, "komentarz do eksportu, @ex_bob"));
        commentMentions.save(new com.musicclubapp.entity.CommentMention(komentarz, bob));
        comments.save(new com.musicclubapp.entity.Comment(cudzy, bob, komentarz, ala, "odpowiedz boba - nie alicji"));

        messages.save(new Message(ala, bob, "hej bob"));
        messages.save(new Message(bob, ala, "czesc ala"));
        messages.save(new Message(ala, cyd, "sekret z rozmowy, ktora skasowalam"));
        em.flush();
        messageService.deleteConversation("ex_ala", "ex_cyd");

        Clan klan = clans.save(new Clan("Eksportowcy", "eksportowcy", "EX", "opis"));
        ClanMember czlonek = clanMembers.save(new ClanMember(klan, ala, ClanRole.FOUNDER, java.time.LocalDateTime.now()));
        czlonek.setChatMuted(true);
        ClanMessage wiadomoscKlanu = clanMessages.save(new ClanMessage(klan, ala, "wiadomosc na czacie klanu"));
        clanReactions.save(new ClanMessageReaction(wiadomoscKlanu, ala, ClanEmoji.FIRE, java.time.LocalDateTime.now()));
        ClanTrack utwor = clanTracks.save(new ClanTrack(klan, ala, com.musicclubapp.music.MusicProvider.SPOTIFY,
            com.musicclubapp.music.MusicKind.TRACK, "4uLU6hMCjMI75M1A2tKUQC", "Utwor z eksportu", null,
            "notatka do utworu", java.time.LocalDate.now(), java.time.LocalDateTime.now()));
        clanTrackVotes.save(new ClanTrackVote(utwor, ala, java.time.LocalDateTime.now()));
        // Prosba o dolaczenie do innego klanu, ankieta z glosem i tytul w klanie
        Clan inny = clans.save(new Clan("Cudzy Klan", "cudzy klan", "CK", null));
        clanRequests.save(new ClanJoinRequest(inny, ala, "chce dolaczyc do cudzych", java.time.LocalDateTime.now()));
        ClanPoll ankieta = new ClanPoll(klan, ala, "Ankieta z eksportu?", java.time.LocalDateTime.now(),
            java.time.LocalDateTime.now().plusDays(3));
        ankieta.addOption("odpowiedz pierwsza");
        ankieta.addOption("odpowiedz druga");
        clanPolls.save(ankieta);
        clanPollVotes.save(new ClanPollVote(ankieta, ankieta.getOptions().get(1), ala, java.time.LocalDateTime.now()));
        ClanTitle tytul = clanTitleDefs.save(new ClanTitle(klan, "Tytul z eksportu", "tytul z eksportu", ClanColor.TEAL,
            ClanTitleMode.SELF, null, null, java.time.LocalDateTime.now()));
        clanMemberTitles.save(new ClanMemberTitle(tytul, ala, true, java.time.LocalDateTime.now()));
        Post klanowy = new Post(ala, "tajny post klanu");
        klanowy.setClan(klan);
        posts.save(klanowy);

        reports.save(new Report(bob, ala, ReportReason.values()[0], ReportContext.PROFILE, "zgloszenie od boba na ale"));
        reports.save(new Report(ala, cyd, ReportReason.values()[0], ReportContext.PROFILE, "moje zgloszenie na cyda"));
        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(plik);
    }

    private org.springframework.test.web.servlet.ResultActions eksport(String login, String haslo) throws Exception {
        Map<String, Object> tresc = new HashMap<>();
        tresc.put("currentPassword", haslo);
        return mvc.perform(post("/api/profile/export").with(user(login)).with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    private Map<String, byte[]> rozpakuj(byte[] zip) throws Exception {
        Map<String, byte[]> wpisy = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                wpisy.put(e.getName(), in.readAllBytes());
            }
        }
        return wpisy;
    }

    @Test
    @DisplayName("bez hasla i z blednym haslem archiwum sie nie pobiera; ktos z przejeta sesja go nie wezmie")
    void needsPassword() throws Exception {
        eksport("ex_ala", null).andExpect(status().isUnprocessableEntity());
        eksport("ex_ala", "  ").andExpect(status().isUnprocessableEntity());
        eksport("ex_ala", "zle-haslo-123").andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/profile/export").with(user("ex_ala"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + HASLO + "\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/profile/export").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("archiwum: moje dane, wlasne zdjecia i instrukcja - bez hasla, cudzych postow i cudzych adresow e-mail")
    void containsOwnDataOnly() throws Exception {
        MvcResult start = eksport("ex_ala", HASLO).andExpect(request().asyncStarted()).andReturn();
        MvcResult wynik = mvc.perform(asyncDispatch(start)).andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"musicclub-dane-ex_ala-")))
            .andReturn();

        Map<String, byte[]> wpisy = rozpakuj(wynik.getResponse().getContentAsByteArray());
        assertThat(wpisy).containsKeys("dane.json", "CZYTAJ-TO.txt");
        String tekst = new String(wpisy.get("dane.json"), StandardCharsets.UTF_8);
        JsonNode dane = json.readTree(tekst);

        // Konto i moje tresci
        assertThat(dane.at("/account/username").asText()).isEqualTo("ex_ala");
        assertThat(dane.at("/account/email").asText()).isEqualTo("ex_ala@example.com");
        assertThat(dane.at("/account/privacy/profileVisibility").asText()).isEqualTo("EVERYONE");
        // Miasto z profilu i ustawienie jego pokazywania - ale nie wspolrzedne (wynikaja z miasta)
        assertThat(dane.at("/account/city").asText()).isEqualTo("Poznań");
        assertThat(dane.at("/account/privacy/showCity").asBoolean()).isTrue();
        assertThat(tekst).doesNotContain("52.4064");
        // Wlasny komentarz razem z oznaczeniem - ale nie cudza odpowiedz pod nim
        assertThat(dane.at("/comments").size()).isEqualTo(1);
        assertThat(dane.at("/comments/0/content").asText()).isEqualTo("komentarz do eksportu, @ex_bob");
        assertThat(dane.at("/comments/0/postAuthor").asText()).isEqualTo("ex_bob");
        assertThat(dane.at("/comments/0/mentions/0").asText()).isEqualTo("ex_bob");
        assertThat(tekst).doesNotContain("odpowiedz boba - nie alicji");
        assertThat(tekst).contains("moj post do eksportu", "tajny post klanu", "hej bob", "czesc ala",
            "wiadomosc na czacie klanu", "moje zgloszenie na cyda");
        assertThat(dane.at("/posts").size()).isEqualTo(2);
        assertThat(dane.at("/friends").get(0).asText()).isEqualTo("ex_bob");
        assertThat(dane.at("/reactions/0/postAuthor").asText()).isEqualTo("ex_bob");
        assertThat(dane.at("/clan/membership/clan").asText()).isEqualTo("Eksportowcy");
        // Rozszerzenia klanu: wyciszenie, reakcje, propozycje i glosy w "utworze tygodnia"
        assertThat(dane.at("/clan/membership/chatNotificationsMuted").asBoolean()).isTrue();
        assertThat(dane.at("/clan/chatReactionsGiven/0/reaction").asText()).isEqualTo("FIRE");
        assertThat(dane.at("/clan/tracksProposed/0/title").asText()).isEqualTo("Utwor z eksportu");
        assertThat(dane.at("/clan/tracksProposed/0/link").asText()).isEqualTo("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");
        assertThat(dane.at("/clan/tracksProposed/0/note").asText()).isEqualTo("notatka do utworu");
        assertThat(dane.at("/clan/trackVotes/0/title").asText()).isEqualTo("Utwor z eksportu");
        assertThat(dane.at("/clan/joinRequests/0/clan").asText()).isEqualTo("Cudzy Klan");
        assertThat(dane.at("/clan/joinRequests/0/message").asText()).isEqualTo("chce dolaczyc do cudzych");
        assertThat(dane.at("/clan/pollsCreated/0/question").asText()).isEqualTo("Ankieta z eksportu?");
        assertThat(dane.at("/clan/pollsCreated/0/options").size()).isEqualTo(2);
        assertThat(dane.at("/clan/pollVotes/0/answer").asText()).isEqualTo("odpowiedz druga");
        assertThat(dane.at("/clan/titles/0/title").asText()).isEqualTo("Tytul z eksportu");
        assertThat(dane.at("/clan/titles/0/selfClaimed").asBoolean()).isTrue();
        assertThat(dane.at("/messages").size()).isEqualTo(2);
        // Zdjecie z posta lezy w archiwum, z ta sama trescia
        String sciezka = dane.at("/posts/0/images/0").asText();
        assertThat(sciezka).startsWith("zdjecia/post-").endsWith(".png");
        assertThat(wpisy.get(sciezka)).isEqualTo(OBRAZ);

        // Czego nie ma: cudze posty, skasowana rozmowa, haslo, cudze adresy e-mail, kto zglosil
        assertThat(tekst).doesNotContain("post boba - nie mój", "sekret z rozmowy", "ex_bob@example.com",
            "ex_cyd@example.com", "zgloszenie od boba", "passwordHash", "securityStamp", "$2a$");
        assertThat(dane.at("/reportsAboutYou").size()).isEqualTo(1);
        assertThat(dane.at("/reportsAboutYou/0").has("reporter")).isFalse();
        assertThat(dane.at("/reportsAboutYou/0").has("description")).isFalse();
        assertThat(new String(wpisy.get("CZYTAJ-TO.txt"), StandardCharsets.UTF_8)).contains("ex_ala", "RODO", "GDPR");

        // Dane drugiej osoby - jej wlasny eksport nie zawiera niczego z tamtego
        MvcResult startBoba = eksport("ex_bob", "inne-haslo-1").andExpect(request().asyncStarted()).andReturn();
        String daneBoba = new String(rozpakuj(mvc.perform(asyncDispatch(startBoba)).andReturn().getResponse()
            .getContentAsByteArray()).get("dane.json"), StandardCharsets.UTF_8);
        assertThat(daneBoba).contains("post boba - nie mój", "czesc ala").doesNotContain("moj post do eksportu",
            "tajny post klanu", "ex_ala@example.com", "Utwor z eksportu", "notatka do utworu",
            "Ankieta z eksportu?", "chce dolaczyc do cudzych", "Tytul z eksportu");
    }

    @Test
    @DisplayName("najwyzej jedno pobranie na minute - wielokrotne klikniecie nie obciaza serwera")
    void rateLimited() throws Exception {
        MvcResult start = eksport("ex_cyd", "inne-haslo-2").andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(start)).andExpect(status().isOk());
        eksport("ex_cyd", "inne-haslo-2").andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }
}
