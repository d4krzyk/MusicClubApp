package com.musicclubapp.repository;

import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Testy zapytania o proponowanych znajomych. */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Proponowani znajomi - sortowanie po dopasowaniu")
class FriendSuggestionsTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private EntityManager entityManager;

    private User ala;
    private User bob;
    private User cezary;
    private User dawid;

    private User save(String username) {
        return userRepository.save(new User(username, username + "@example.com", "hash"));
    }

    @BeforeEach
    void setUp() {
        ala = save("ala");
        bob = save("bob");
        cezary = save("cezary");
        dawid = save("dawid");

        ala.addFriend(bob);
        bob.addFriend(cezary);

        Artist radiohead = new Artist("111", "Radiohead", null);
        radiohead.applyGenres(Set.of("rock", "alternative"));
        artistRepository.save(radiohead);

        Artist daftPunk = new Artist("222", "Daft Punk", null);
        daftPunk.applyGenres(Set.of("electronic"));
        artistRepository.save(daftPunk);

        ala.getFavoriteArtists().add(radiohead);
        dawid.getFavoriteArtists().add(radiohead);
        cezary.getFavoriteArtists().add(daftPunk);

        // Bez tego zapytanie natywne nie zobaczy jeszcze niezapisanych zmian -
        // pracuje na bazie, a nie na kontekscie Hibernate'a
        entityManager.flush();
        entityManager.clear();
    }

    private List<SuggestionRow> forAla() {
        return userRepository.friendSuggestions("ala", PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("wspolny artysta wazy wiecej niz wspolny znajomy")
    void resultOrder() {
        List<SuggestionRow> score = forAla();

        assertThat(score).extracting(SuggestionRow::getUsername)
            .containsExactly("dawid", "cezary", "bob");
    }

    @Test
    @DisplayName("lista zawiera WSZYSTKICH, nie tylko dopasowanych")
    void everyoneIsListed() {
        /*
         * To jest zalozenie calej funkcji: aplikacja dla kilkunastu osob, ktora po odfiltrowaniu
         * "za malo podobnych" pokazuje pusta strone, jest bezuzyteczna dokladnie wtedy, kiedy
         * najbardziej potrzeba w niej ludzi - na starcie.
         */
        assertThat(forAla()).hasSize(3);
        assertThat(forAla()).extracting(SuggestionRow::getUsername).contains("bob");
    }

    @Test
    @DisplayName("na liscie nie ma samego pytajacego")
    void viewerIsExcluded() {
        assertThat(forAla()).extracting(SuggestionRow::getUsername).doesNotContain("ala");
    }

    @Test
    @DisplayName("skladniki wyniku sa policzone osobno - to one trafiaja na karte")
    void scoreParts() {
        SuggestionRow dawidRow = forAla().stream()
            .filter(w -> w.getUsername().equals("dawid")).findFirst().orElseThrow();

        assertThat(dawidRow.getSharedArtists()).isEqualTo(1);
        // rock + alternative
        assertThat(dawidRow.getSharedGenres()).isEqualTo(2);
        assertThat(dawidRow.getSharedFriends()).isZero();
        assertThat(dawidRow.getScore()).isEqualTo(5 * 1 + 2);

        SuggestionRow cezaryRow = forAla().stream()
            .filter(w -> w.getUsername().equals("cezary")).findFirst().orElseThrow();

        // Wspolny znajomy to bob - zna sie i z ala, i z cezarym
        assertThat(cezaryRow.getSharedFriends()).isEqualTo(1);
        assertThat(cezaryRow.getSharedArtists()).isZero();
        // Daft Punk (electronic) nie ma nic wspolnego z Radiohead (rock, alternative)
        assertThat(cezaryRow.getSharedGenres()).isZero();
    }

    @Test
    @DisplayName("znajomi zostaja na liscie, ale sa oznaczeni")
    void friendsAreMarked() {
        /*
         * Gdyby znajomi znikali, osoba z najlepszym dopasowaniem przepadalaby w chwili dodania jej
         * do znajomych - czyli dokladnie ta, ktora najlepiej tlumaczy, po co ta lista w ogole
         * jest.
         */
        List<SuggestionRow> score = forAla();

        assertThat(score).filteredOn(w -> w.getUsername().equals("bob"))
            .singleElement()
            .extracting(SuggestionRow::getAlreadyFriend)
            .isEqualTo(true);

        assertThat(score).filteredOn(w -> w.getUsername().equals("dawid"))
            .singleElement()
            .extracting(SuggestionRow::getAlreadyFriend)
            .isEqualTo(false);
    }

    @Test
    @DisplayName("wylaczone konto nie jest proponowane")
    void disabledAccountSkipped() {
        User zablokowany = userRepository.findByUsername("dawid").orElseThrow();
        zablokowany.setEnabled(false);
        userRepository.save(zablokowany);
        entityManager.flush();

        assertThat(forAla()).extracting(SuggestionRow::getUsername).doesNotContain("dawid");
    }

    @Test
    @DisplayName("bez ulubionych wynik opiera sie na samych znajomych")
    void withoutFavorites() {
        /* Wazny przypadek brzegowy: nowy uzytkownik nie ma jeszcze zadnych ulubionych. */
        List<SuggestionRow> dlaDawida =
            userRepository.friendSuggestions("bob", PageRequest.of(0, 20));

        assertThat(dlaDawida).isNotEmpty();
        assertThat(dlaDawida).extracting(SuggestionRow::getUsername).contains("ala", "cezary");
    }
}
