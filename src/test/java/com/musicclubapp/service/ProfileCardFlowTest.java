package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ProfilePhotoRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Karta profilu przez prawdziwe API: tekst, zdjecia (bez GPS), kolejnosc, widocznosc, zgloszenie, moderacja. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Karta profilu - zdjecia, opis, szukam, pytania")
class ProfileCardFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfilePhotoRepository photos;
    @Autowired private ReportRepository reports;
    @Autowired private FileStorageService storage;
    @Autowired private EntityManager em;

    @MockBean private PushService push;

    private User ala;
    private final List<String> wgrane = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ala = users.save(new User("pc_ala", "pc_ala@example.com", "x"));
        users.save(new User("pc_bob", "pc_bob@example.com", "x"));
        User szef = new User("pc_szef", "pc_szef@example.com", "x");
        szef.setRole(Role.ADMIN);
        users.save(szef);
        em.flush();
    }

    @AfterEach
    void sprzataj() throws Exception {
        // Transakcja testu jest wycofywana, ale pliki na dysku zostaja - kasujemy je sami
        for (String nazwa : wgrane) {
            Files.deleteIfExists(storage.getDirectory().resolve(nazwa));
        }
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var zadanie = switch (metoda) {
            case "PUT" -> put(adres);
            case "DELETE" -> delete(adres);
            default -> post(adres);
        };
        // administrator aplikacji musi miec role takze w zapytaniu - inaczej panel zgloszen da 403
        zadanie.with("pc_szef".equals(kto) ? user(kto).roles("ADMIN") : user(kto)).with(csrf())
            .header("Accept-Language", "pl");
        if (tresc != null) {
            zadanie.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(zadanie);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** JPEG z segmentem EXIF, w ktorym "siedza" wspolrzedne - tak jak w zdjeciu z telefonu. */
    private static byte[] zdjecieZGps() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB), "jpg", out);
        byte[] jpeg = out.toByteArray();
        byte[] exif = "Exif\0\0MM\0*GPS 52.4064N 16.9252E".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream wynik = new ByteArrayOutputStream();
        wynik.write(jpeg, 0, 2);
        wynik.write(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        wynik.write(exif);
        wynik.write(jpeg, 2, jpeg.length - 2);
        return wynik.toByteArray();
    }

    private JsonNode wgraj(String kto, String typ, byte[] dane) throws Exception {
        ResultActions r = mvc.perform(multipart("/api/profile/photos")
            .file(new MockMultipartFile("file", "zdjecie", typ, dane))
            .with(user(kto)).with(csrf()).header("Accept-Language", "pl"));
        if (r.andReturn().getResponse().getStatus() == 200) {
            JsonNode karta = tresc(r);
            for (JsonNode p : karta.get("photos")) {
                String url = p.get("url").asText();
                wgrane.add(url.substring(url.lastIndexOf('/') + 1));
            }
            return karta;
        }
        return tresc(r.andExpect(status().is4xxClientError()));
    }

    private Map<String, Object> karta(String bio, List<String> szukam, List<Map<String, String>> pytania) {
        Map<String, Object> m = new HashMap<>();
        m.put("bio", bio);
        m.put("lookingFor", szukam);
        m.put("prompts", pytania);
        return m;
    }

    /* ---------------------------- tekst ---------------------------- */

    @Test
    @DisplayName("zapis karty: opis przyciety, szukam bez powtorzen, pytania w kolejnosci; drugi zapis zastepuje pierwszy")
    void saveCard() throws Exception {
        JsonNode k = tresc(wyslij("PUT", "pc_ala", "/api/profile/card", karta("  Gram na basie.  ",
            List.of("JAMMING", "CONCERT_BUDDIES", "JAMMING"),
            List.of(Map.of("prompt", "FIRST_CONCERT", "answer", " Myslovitz, 2002 "),
                Map.of("prompt", "DESERT_ISLAND", "answer", "OK Computer")))).andExpect(status().isOk()));
        assertThat(k.get("bio").asText()).isEqualTo("Gram na basie.");
        assertThat(k.get("lookingFor").toString()).isEqualTo("[\"CONCERT_BUDDIES\",\"JAMMING\"]");
        assertThat(k.get("prompts").get(0).get("prompt").asText()).isEqualTo("FIRST_CONCERT");
        assertThat(k.get("prompts").get(0).get("answer").asText()).isEqualTo("Myslovitz, 2002");
        assertThat(k.get("prompts").get(1).get("prompt").asText()).isEqualTo("DESERT_ISLAND");

        JsonNode druga = tresc(wyslij("PUT", "pc_ala", "/api/profile/card", karta("", List.of(),
            List.of(Map.of("prompt", "KARAOKE", "answer", "Bohemian Rhapsody")))).andExpect(status().isOk()));
        assertThat(druga.get("bio").isNull()).isTrue();
        assertThat(druga.get("lookingFor")).isEmpty();
        assertThat(druga.get("prompts")).hasSize(1);

        em.flush();
        em.clear();
        JsonNode moja = tresc(mvc.perform(get("/api/profile/card").with(user("pc_ala"))).andExpect(status().isOk()));
        assertThat(moja.get("prompts").get(0).get("answer").asText()).isEqualTo("Bohemian Rhapsody");
    }

    @Test
    @DisplayName("walidacja: za dlugi opis, cztery 'szukam', cztery pytania, pusta odpowiedz (422); to samo pytanie dwa razy (409)")
    void validation() throws Exception {
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("x".repeat(301), null, null))
            .andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "pc_ala", "/api/profile/card", karta(null,
            List.of("JAMMING", "PARTIES", "FESTIVALS", "PLAYLISTS"), null)).andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "pc_ala", "/api/profile/card", karta(null, null, List.of(
            Map.of("prompt", "KARAOKE", "answer", "a"), Map.of("prompt", "INSTRUMENT", "answer", "b"),
            Map.of("prompt", "ON_REPEAT", "answer", "c"), Map.of("prompt", "DREAM_GIG", "answer", "d"))))
            .andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "pc_ala", "/api/profile/card", karta(null, null, List.of(Map.of("prompt", "KARAOKE", "answer", "  "))))
            .andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "pc_ala", "/api/profile/card", karta(null, null, List.of(
            Map.of("prompt", "KARAOKE", "answer", "a"), Map.of("prompt", "KARAOKE", "answer", "b"))))
            .andExpect(status().isConflict());
        // trzy pytania i trzy "szukam" to jeszcze w porzadku
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("y".repeat(300), List.of("JAMMING", "PARTIES", "FESTIVALS"),
            List.of(Map.of("prompt", "KARAOKE", "answer", "z".repeat(150)), Map.of("prompt", "INSTRUMENT", "answer", "b"),
                Map.of("prompt", "ON_REPEAT", "answer", "c")))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("z zakazem publikowania karty nie da sie zmienic ani dodac zdjecia (409), a usunac zdjecie - tak")
    void postingBan() throws Exception {
        JsonNode k = wgraj("pc_ala", "image/jpeg", zdjecieZGps());
        long zdjecie = k.get("photos").get(0).get("id").asLong();
        ala.setBannedUntil(BanKind.POSTING, java.time.LocalDateTime.now().plusDays(1));
        users.save(ala);
        em.flush();
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("nowy", null, null)).andExpect(status().isConflict());
        assertThat(wgraj("pc_ala", "image/jpeg", zdjecieZGps()).has("photos")).isFalse();
        wyslij("DELETE", "pc_ala", "/api/profile/photos/" + zdjecie, null).andExpect(status().isOk());
    }

    /* ---------------------------- zdjecia ---------------------------- */

    @Test
    @DisplayName("zdjecie: zapisane bez EXIF-u z GPS, na koncu galerii; najwyzej szesc; GIF i pusty plik odrzucone")
    void photos() throws Exception {
        JsonNode k = wgraj("pc_ala", "image/jpeg", zdjecieZGps());
        assertThat(k.get("photos")).hasSize(1);
        String url = k.get("photos").get(0).get("url").asText();
        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
        byte[] naDysku = Files.readAllBytes(storage.getDirectory().resolve(url.substring("/uploads/".length())));
        assertThat(new String(naDysku, StandardCharsets.ISO_8859_1)).doesNotContain("GPS").doesNotContain("Exif");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(naDysku)).getWidth()).isEqualTo(30);

        for (int i = 0; i < 5; i++) {
            k = wgraj("pc_ala", "image/png", pngBytes());
        }
        assertThat(k.get("photos")).hasSize(6);
        JsonNode siodme = wgraj("pc_ala", "image/jpeg", zdjecieZGps());
        assertThat(siodme.get("message").asText())
            .isEqualTo("W galerii mieści się najwyżej 6 zdjęć — usuń któreś, żeby dodać nowe");

        JsonNode gif = wgraj("pc_bob", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII));
        assertThat(gif.has("photos")).isFalse();
        JsonNode pusty = wgraj("pc_bob", "image/jpeg", new byte[0]);
        assertThat(pusty.has("photos")).isFalse();
        assertThat(photos.countByUserId(users.findByUsername("pc_bob").orElseThrow().getId())).isZero();
    }

    private static byte[] pngBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("kolejnosc: nowa okladka; lista niepelna, z obcym albo zdublowanym zdjeciem - 409; cudzego nie usuniesz (404)")
    void reorderAndDelete() throws Exception {
        wgraj("pc_ala", "image/jpeg", zdjecieZGps());
        wgraj("pc_ala", "image/png", pngBytes());
        JsonNode k = wgraj("pc_ala", "image/png", pngBytes());
        long a = k.get("photos").get(0).get("id").asLong();
        long b = k.get("photos").get(1).get("id").asLong();
        long c = k.get("photos").get(2).get("id").asLong();

        JsonNode po = tresc(wyslij("PUT", "pc_ala", "/api/profile/photos/order", Map.of("ids", List.of(c, a, b)))
            .andExpect(status().isOk()));
        assertThat(po.get("photos").get(0).get("id").asLong()).isEqualTo(c);
        assertThat(po.get("photos").get(2).get("id").asLong()).isEqualTo(b);

        wyslij("PUT", "pc_ala", "/api/profile/photos/order", Map.of("ids", List.of(c, a))).andExpect(status().isConflict());
        wyslij("PUT", "pc_ala", "/api/profile/photos/order", Map.of("ids", List.of(c, a, a))).andExpect(status().isConflict());
        wyslij("PUT", "pc_ala", "/api/profile/photos/order", Map.of("ids", List.of(c, a, 999999L))).andExpect(status().isConflict());

        wyslij("DELETE", "pc_bob", "/api/profile/photos/" + a, null).andExpect(status().isNotFound());
        JsonNode bez = tresc(wyslij("DELETE", "pc_ala", "/api/profile/photos/" + a, null).andExpect(status().isOk()));
        assertThat(bez.get("photos")).hasSize(2);
        assertThat(bez.get("photos").get(0).get("id").asLong()).isEqualTo(c);
    }

    /* ---------------------------- widocznosc ---------------------------- */

    @Test
    @DisplayName("karta na profilu: widac ja przy pelnym widoku; profil tylko dla znajomych - obcy jej nie dostaje, znajomy tak")
    void visibilityOnProfile() throws Exception {
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("Basistka szuka perkusji", List.of("JAMMING"), null))
            .andExpect(status().isOk());
        wgraj("pc_ala", "image/jpeg", zdjecieZGps());
        em.flush();

        JsonNode dlaObcego = tresc(mvc.perform(get("/api/profiles/pc_ala").with(user("pc_bob")))
            .andExpect(status().isOk()));
        assertThat(dlaObcego.get("card").get("bio").asText()).isEqualTo("Basistka szuka perkusji");
        assertThat(dlaObcego.get("card").get("photos")).hasSize(1);

        ala.setPrivacy(ProfileVisibility.FRIENDS, ala.getFriendRequestsFrom(), ala.getClanInvitesFrom(),
            true, true, false, true);
        users.save(ala);
        em.flush();
        JsonNode ograniczony = tresc(mvc.perform(get("/api/profiles/pc_ala").with(user("pc_bob")))
            .andExpect(status().isOk()));
        assertThat(ograniczony.get("card").isNull()).isTrue();

        User bob = users.findByUsername("pc_bob").orElseThrow();
        ala.addFriend(bob);
        users.save(ala);
        em.flush();
        JsonNode znajomy = tresc(mvc.perform(get("/api/profiles/pc_ala").with(user("pc_bob")))
            .andExpect(status().isOk()));
        assertThat(znajomy.get("card").get("lookingFor").get(0).asText()).isEqualTo("JAMMING");
    }

    private JsonNode profilDla(String kto) throws Exception {
        var zadanie = get("/api/profiles/pc_ala").with("pc_szef".equals(kto) ? user(kto).roles("ADMIN") : user(kto));
        return tresc(mvc.perform(zadanie).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("kto widzi karte na profilu: domyslnie kazdy; 'znajomi' - tylko znajomi; 'tylko Poznawaj' - nikt poza mna "
        + "i administratorem; w talii Poznawaj karta jest zawsze")
    void cardVisibilitySetting() throws Exception {
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("Tylko dla Poznawaj", List.of("JAMMING"), null))
            .andExpect(status().isOk());
        JsonNode moja = tresc(mvc.perform(get("/api/profile/card").with(user("pc_ala"))).andExpect(status().isOk()));
        assertThat(moja.get("visibility").asText()).isEqualTo("EVERYONE");

        // "Tylko Poznawaj": obcy, znajomy - nic; ja i administrator - karta, a ja dodatkowo widze, kto ja widzi
        JsonNode po = tresc(wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "DISCOVER_ONLY"))
            .andExpect(status().isOk()));
        assertThat(po.get("visibility").asText()).isEqualTo("DISCOVER_ONLY");
        assertThat(po.get("bio").asText()).isEqualTo("Tylko dla Poznawaj");
        em.flush();
        assertThat(profilDla("pc_bob").get("card").isNull()).isTrue();
        JsonNode moj = profilDla("pc_ala");
        assertThat(moj.get("card").get("bio").asText()).isEqualTo("Tylko dla Poznawaj");
        assertThat(moj.get("card").get("visibility").asText()).isEqualTo("DISCOVER_ONLY");
        JsonNode dlaAdmina = profilDla("pc_szef");
        assertThat(dlaAdmina.get("card").get("bio").asText()).isEqualTo("Tylko dla Poznawaj");
        // Ustawienie jest sprawa wlasciciela - inni go nie dostaja
        assertThat(dlaAdmina.get("card").get("visibility").isNull()).isTrue();

        User bob = users.findByUsername("pc_bob").orElseThrow();
        ala.addFriend(bob);
        users.save(ala);
        em.flush();
        assertThat(profilDla("pc_bob").get("card").isNull()).isTrue();

        // "Znajomi": znajomy widzi, obcy nie
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "FRIENDS")).andExpect(status().isOk());
        users.save(new User("pc_cyd", "pc_cyd@example.com", "x"));
        em.flush();
        JsonNode dlaZnajomego = profilDla("pc_bob");
        assertThat(dlaZnajomego.get("card").get("bio").asText()).isEqualTo("Tylko dla Poznawaj");
        assertThat(dlaZnajomego.get("card").get("visibility").isNull()).isTrue();
        assertThat(profilDla("pc_cyd").get("card").isNull()).isTrue();

        // Talia Poznawaj nie patrzy na to ustawienie - po to ono jest
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "DISCOVER_ONLY")).andExpect(status().isOk());
        // do talii trafiaja tylko konta z potwierdzonym adresem
        for (String login : List.of("pc_ala", "pc_cyd")) {
            User u = users.findByUsername(login).orElseThrow();
            u.markEmailVerified(java.time.LocalDateTime.now());
            users.save(u);
        }
        em.flush();
        wyslij("PUT", "pc_ala", "/api/discover/settings", Map.of("enabled", true, "radiusKm", 0)).andExpect(status().isOk());
        wyslij("PUT", "pc_cyd", "/api/discover/settings", Map.of("enabled", true, "radiusKm", 0)).andExpect(status().isOk());
        em.flush();
        JsonNode talia = tresc(mvc.perform(get("/api/discover/deck?limit=20").with(user("pc_cyd"))).andExpect(status().isOk()));
        JsonNode ala = null;
        for (JsonNode k : talia.get("cards")) {
            if ("pc_ala".equals(k.get("username").asText())) {
                ala = k;
            }
        }
        assertThat(ala).isNotNull();
        assertThat(ala.get("bio").asText()).isEqualTo("Tylko dla Poznawaj");

        // Z powrotem do "kazdy"; zle i puste wartosci - 422 (bez zmiany)
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "EVERYONE")).andExpect(status().isOk());
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "NIKT"))
            .andExpect(status().is4xxClientError());
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", new HashMap<>()).andExpect(status().isUnprocessableEntity());
        em.flush();
        assertThat(profilDla("pc_cyd").get("card").get("bio").asText()).isEqualTo("Tylko dla Poznawaj");

        // Zaproszenie to jeszcze nie znajomosc - karta "tylko znajomi" sie nie pokazuje
        wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "FRIENDS")).andExpect(status().isOk());
        wyslij("POST", "pc_cyd", "/api/friends/requests", Map.of("username", "pc_ala")).andExpect(status().is2xxSuccessful());
        em.flush();
        JsonNode zaproszony = profilDla("pc_cyd");
        assertThat(zaproszony.get("friendshipStatus").asText()).isEqualTo("REQUEST_SENT");
        assertThat(zaproszony.get("card").isNull()).isTrue();
    }

    @Test
    @DisplayName("widocznosc karty mozna zmienic takze z zakazem publikowania - to ustawienie prywatnosci, nie tresc")
    void cardVisibilityUnderBan() throws Exception {
        ala.setBannedUntil(BanKind.POSTING, java.time.LocalDateTime.now().plusDays(1));
        users.save(ala);
        em.flush();
        JsonNode po = tresc(wyslij("PUT", "pc_ala", "/api/profile/card/visibility", Map.of("visibility", "FRIENDS"))
            .andExpect(status().isOk()));
        assertThat(po.get("visibility").asText()).isEqualTo("FRIENDS");
    }

    /* ---------------------------- moderacja ---------------------------- */

    @Test
    @DisplayName("zgloszenie profilu ma migawke karty; decyzja 'wyczysc karte' usuwa ja i wylacza Poznawaj")
    void reportAndClear() throws Exception {
        wyslij("PUT", "pc_ala", "/api/profile/card", karta("Tresc do zgloszenia",
            List.of("PARTIES"), List.of(Map.of("prompt", "KARAOKE", "answer", "cos niestosownego"))))
            .andExpect(status().isOk());
        String url = wgraj("pc_ala", "image/jpeg", zdjecieZGps()).get("photos").get(0).get("url").asText();
        wyslij("PUT", "pc_ala", "/api/discover/settings", Map.of("enabled", true, "radiusKm", 100))
            .andExpect(status().isOk());

        long zgloszenie = tresc(wyslij("POST", "pc_bob", "/api/reports/on/pc_ala", Map.of("reason", "INAPPROPRIATE",
            "context", "PROFILE", "description", "zdjecie i opis")).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        em.clear();
        var dowody = reports.findById(zgloszenie).orElseThrow().getEvidence().stream().map(e -> e.getText()).toList();
        assertThat(dowody).contains("[O mnie] Tresc do zgloszenia", "[KARAOKE] cos niestosownego", "[Zdjecie] " + url)
            .anyMatch(t -> t.startsWith("[Szukam]") && t.contains("PARTIES"));

        wyslij("POST", "pc_szef", "/api/reports/admin/" + zgloszenie + "/resolve", Map.of("decision", "RESOLVED",
            "note", "karta wyczyszczona", "action", "CLEAR_CARD")).andExpect(status().isOk());
        em.flush();
        em.clear();
        User po = users.findByUsername("pc_ala").orElseThrow();
        assertThat(po.getBio()).isNull();
        assertThat(po.getLookingFor()).isEmpty();
        assertThat(po.isDiscoverEnabled()).isFalse();
        assertThat(photos.countByUserId(po.getId())).isZero();
        JsonNode karta = tresc(mvc.perform(get("/api/profile/card").with(user("pc_ala"))).andExpect(status().isOk()));
        assertThat(karta.get("prompts")).isEmpty();
    }
}
