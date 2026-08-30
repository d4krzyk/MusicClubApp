package com.musicclubapp.service;

import com.musicclubapp.entity.BanKind;
import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.dto.UpdatePostRequest;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testy jednostkowe serwisu postow - wymaganie nr 13.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService - dodawanie i usuwanie postow")
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorage;

    @Mock
    private PostMapper postMapper;

    @Mock
    private ReactionService reactionService;

    /**
     * Pobieranie tytulu przez oEmbed to zapytanie do OBCEGO serwera.
     * W tescie jednostkowym podstawiamy atrape - inaczej test zalezalby
     * od tego, czy Spotify akurat odpowiada.
     */
    @Mock
    private MusicMetadataService musicMetadata;

    @Mock
    private NotificationService notifications;

    @Mock
    private ReactionRepository reactionRepository;

    @InjectMocks
    private PostService postService;

    private User anna() {
        return new User("anna", "anna@example.com", "hash");
    }

    private MultipartFile image(String name) {
        return new MockMultipartFile(
            "images", name, "image/jpeg", new byte[] {1, 2, 3});
    }

    private void prepareSave() {
        given(postRepository.save(any(Post.class))).willAnswer(w -> w.getArgument(0));
        given(postMapper.toResponse(any(Post.class), any(), any())).willReturn(
            new PostResponse(1L, "anna", null, "tresc", List.of(),
                null, null, null, null, null, null, null,
                LocalDateTime.now(), true, true, ReactionSummary.empty(), PostVisibility.PUBLIC, false));
    }

    /** Serwis oEmbed odpowiada tytulem i miniaturka. */
    private void buildMusicDetails() {
        given(musicMetadata.fetch(any()))
            .willReturn(new MusicMetadataService.Metadata("Tytul utworu", "https://obrazek/x.jpg"));
    }

    /**
     * Edycja dodatkowo pyta o reakcje, ktore post juz zebral - inaczej
     * po zapisaniu zmiany liczniki zniknelyby z ekranu.
     */
    private void prepareEdit() {
        prepareSave();
        given(reactionService.summaries(anyList(), any())).willReturn(Map.of());
    }

    @Test
    @DisplayName("post z samym tekstem zapisuje sie bez zdjec")
    void textOnlyPost() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        prepareSave();

        postService.create("anna", new CreatePostRequest("Dzien dobry", null, null, null, null), null);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());
        assertThat(stored.getValue().getContent()).isEqualTo("Dzien dobry");
        assertThat(stored.getValue().getImages()).isEmpty();
    }

    @Test
    @DisplayName("zdjecia zachowuja kolejnosc wgrania")
    void imagesKeepTheirOrder() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(fileStorage.saveImage(any())).willReturn("a.jpg", "b.jpg", "c.jpg");
        prepareSave();

        postService.create("anna", new CreatePostRequest("Galeria", null, null, null, null),
            List.of(image("1.jpg"), image("2.jpg"), image("3.jpg")));

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());

        assertThat(stored.getValue().getImages())
            .extracting(PostImage::getFileName)
            .containsExactly("a.jpg", "b.jpg", "c.jpg");
        assertThat(stored.getValue().getImages())
            .extracting(PostImage::getPosition)
            .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("wiecej niz 10 zdjec zostaje przycietych do limitu")
    void imageLimit() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(fileStorage.saveImage(any())).willReturn("x.jpg");
        prepareSave();

        List<MultipartFile> duzo = java.util.stream.IntStream.range(0, 15)
            .mapToObj(i -> image(i + ".jpg"))
            .map(MultipartFile.class::cast)
            .toList();

        postService.create("anna", new CreatePostRequest("Duzo zdjec", null, null, null, null), duzo);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());
        assertThat(stored.getValue().getImages()).hasSize(PostService.MAX_IMAGES);
    }

    @Test
    @DisplayName("z linku Spotify zapisujemy sam identyfikator, bez parametru ?si=")
    void spotifyLinkIsCleanedUp() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        prepareSave();
        buildMusicDetails();

        postService.create("anna", new CreatePostRequest(
            "Polecam",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=tajnyparametr",
            MusicKind.TRACK,
            42, null), null);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());
        Post post = stored.getValue();

        assertThat(post.getMusicProvider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(post.getMusicKind()).isEqualTo(MusicKind.TRACK);
        assertThat(post.getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(post.getMusicStartSeconds()).isEqualTo(42);
        // tytul pobrany raz, przy dodawaniu
        assertThat(post.getMusicTitle()).isEqualTo("Tytul utworu");
    }

    @Test
    @DisplayName("link z YouTube Music zapisuje sie jako utwor tego serwisu")
    void youtubeLink() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        prepareSave();
        buildMusicDetails();

        postService.create("anna", new CreatePostRequest(
            "Polecam", "https://music.youtube.com/watch?v=dQw4w9WgXcQ", MusicKind.TRACK, 42, null), null);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());

        assertThat(stored.getValue().getMusicProvider()).isEqualTo(MusicProvider.YOUTUBE);
        assertThat(stored.getValue().getMusicExternalId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("przy ALBUMIE moment startu jest odrzucany, mimo ze przyszedl")
    void albumIgnoresStartSecondsField() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        prepareSave();
        buildMusicDetails();

        postService.create("anna", new CreatePostRequest(
            "Caly album",
            "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.ALBUM,
            70, null), null);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());

        assertThat(stored.getValue().getMusicKind()).isEqualTo(MusicKind.ALBUM);
        // Przy albumie nie ma czego przewijac - encja czysci te wartosc sama
        assertThat(stored.getValue().getMusicStartSeconds()).isNull();
    }

    @Test
    @DisplayName("gdy serwis oEmbed nie odpowie, post i tak powstaje")
    void oEmbedOutageDoesNotBlockPost() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        prepareSave();
        // Serwis niedostepny -> puste wartosci, a nie wyjatek
        given(musicMetadata.fetch(any())).willReturn(MusicMetadataService.Metadata.empty());

        postService.create("anna", new CreatePostRequest(
            "Polecam",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.TRACK,
            null, null), null);

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());

        // odtwarzacz i tak zadziala - iframe pobiera sobie wszystko sam
        assertThat(stored.getValue().getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(stored.getValue().getMusicTitle()).isNull();
    }

    @Test
    @DisplayName("autor moze usunac swoj post - razem z plikami z dysku")
    void authorDeletesOwnPost() {
        Post post = new Post(anna(), "tresc");
        post.addImage(new PostImage("zdjecie.jpg"));

        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));

        postService.delete(5L, "anna");

        verify(postRepository).delete(post);
        // pliki znikaja z dysku, inaczej katalog uploadow rosnie w nieskonczonosc
        verify(fileStorage).remove("zdjecie.jpg");
    }

    @Test
    @DisplayName("zwykly uzytkownik NIE usunie cudzego posta")
    void someoneElsesPostIsProtected() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");

        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));

        assertThatThrownBy(() -> postService.delete(5L, "anna"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    @DisplayName("administrator moze usunac cudzy post - moderacja")
    void adminDeletesSomeoneElsesPost() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
        User admin = new User("admin", "admin@musicclub.local", "hash");
        admin.setRole(Role.ADMIN);

        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(admin));

        postService.delete(5L, "admin");

        verify(postRepository).delete(cudzy);
    }

    @Test
    @DisplayName("autor moze edytowac swoj post - tresc i utwor")
    void authorEditsOwnPost() {
        Post post = new Post(anna(), "stara tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        prepareEdit();

        buildMusicDetails();
        postService.update(5L, "anna", new UpdatePostRequest(
            "nowa tresc", "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.TRACK, 30, null));

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());
        assertThat(stored.getValue().getContent()).isEqualTo("nowa tresc");
        assertThat(stored.getValue().getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(stored.getValue().getMusicStartSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("wyczyszczenie linku usuwa utwor RAZEM z wybranym momentem")
    void emptyLinkRemovesTrack() {
        Post post = new Post(anna(), "tresc");
        post.applyMusic(new com.musicclubapp.music.ParsedMusicLink(
            MusicProvider.SPOTIFY, MusicKind.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),
            30, "Tytul", "https://obrazek/x.jpg");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        prepareEdit();

        postService.update(5L, "anna", new UpdatePostRequest("tresc", "", null, null, null));

        ArgumentCaptor<Post> stored = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(stored.capture());
        Post savedPost = stored.getValue();

        assertThat(savedPost.hasMusic()).isFalse();
        assertThat(savedPost.getMusicExternalId()).isNull();
        // razem z nagraniem znikaja tytul, miniaturka i moment startu
        assertThat(savedPost.getMusicTitle()).isNull();
        assertThat(savedPost.getMusicStartSeconds()).isNull();
    }

    @Test
    @DisplayName("zwykly uzytkownik NIE zedytuje cudzego posta")
    void someoneElsesPostCannotBeEdited() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));

        assertThatThrownBy(() -> postService.update(
            5L, "anna", new UpdatePostRequest("przejete", null, null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("nawet ADMINISTRATOR nie edytuje cudzego posta - moderuje usuwaniem")
    void adminCannotEditEither() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));

        assertThatThrownBy(() -> postService.update(
            5L, "admin", new UpdatePostRequest("podmienione", null, null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("usuwanie nieistniejacego posta konczy sie wyjatkiem 'nie znaleziono'")
    void unknownPostThrows() {
        given(postRepository.findByIdWithAuthor(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L, "anna"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }

    @Test
    @DisplayName("konto z zakazem publikowania NIE doda posta")
    void bannedAccountCannotPost() {
        User banned = anna();
        banned.setBannedUntil(BanKind.POSTING, java.time.LocalDateTime.now().plusHours(5));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(banned));

        assertThatThrownBy(() -> postService.create(
                "anna", new CreatePostRequest("mimo bana", null, null, null, null), null))
            .isInstanceOf(OperationNotAllowedException.class);

        // Kluczowe: nic nie trafilo do bazy - kara ma zatrzymac zapis,
        // a nie tylko pokazac komunikat po fakcie
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("konto z zakazem NIE przerobi tez starego posta na nowa tresc")
    void bannedAccountCannotEditEither() {
        /*
         * Bez tego zakaz nie znaczylby nic: wystarczyloby wejsc w edycje
         * dowolnego wlasnego posta i podmienic w nim cala tresc.
         */
        User banned = anna();
        banned.setBannedUntil(BanKind.POSTING, java.time.LocalDateTime.now().plusHours(5));

        Post post = new Post(banned, "stara tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(
                5L, "anna", new UpdatePostRequest("nowa tresc", null, null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(post.getContent()).isEqualTo("stara tresc");
    }

    @Test
    @DisplayName("zakaz, ktory juz minal, NIE blokuje niczego")
    void expiredBanDoesNotBlock() {
        User byly = anna();
        byly.setBannedUntil(BanKind.POSTING, java.time.LocalDateTime.now().minusMinutes(1));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(byly));
        prepareSave();

        postService.create("anna", new CreatePostRequest("juz moge", null, null, null, null), null);

        verify(postRepository).save(any(Post.class));
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie po usunietym koncie                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Kasowanie postow konta przenioslo sie tutaj z modulu moderacji.
     *
     * <p>Sedno jest w plikach: gdyby zdjecia zostawaly na dysku, konto
     * usuniete z bazy dalej lezaloby na serwerze w postaci fotografii.
     * A gdyby leciały z dysku PRZED skasowaniem wierszy i transakcja by sie
     * wycofala - tablica pokazywalaby puste ramki.</p>
     */
    @Test
    @DisplayName("kasowanie postow konta zdejmuje z dysku ich zdjecia")
    void deletingAllPostsRemovesTheirImages() {
        User autor = anna();
        Post zJednym = new Post(autor, "post ze zdjeciem");
        zJednym.addImage(new PostImage("pierwsze.jpg"));
        Post zDwoma = new Post(autor, "post z dwoma");
        zDwoma.addImage(new PostImage("drugie.jpg"));
        zDwoma.addImage(new PostImage("trzecie.jpg"));

        given(postRepository.findByAuthorId(7L)).willReturn(List.of(zJednym, zDwoma));

        int ile = postService.deleteAllOf(7L);

        assertThat(ile).isEqualTo(2);
        verify(fileStorage).remove("pierwsze.jpg");
        verify(fileStorage).remove("drugie.jpg");
        verify(fileStorage).remove("trzecie.jpg");
        verify(postRepository).deleteAll(List.of(zJednym, zDwoma));
    }

    @Test
    @DisplayName("konto bez postow nie probuje kasowac zadnych plikow")
    void deletingAllPostsOfEmptyAccountTouchesNoFiles() {
        given(postRepository.findByAuthorId(7L)).willReturn(List.of());

        assertThat(postService.deleteAllOf(7L)).isZero();

        verify(fileStorage, never()).remove(any());
    }
}
