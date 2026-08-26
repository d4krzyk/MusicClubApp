package com.musicclubapp.service;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.dto.UpdatePostRequest;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.PostRepository;
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

    @InjectMocks
    private PostService postService;

    private User anna() {
        return new User("anna", "anna@example.com", "hash");
    }

    private MultipartFile obrazek(String nazwa) {
        return new MockMultipartFile(
            "images", nazwa, "image/jpeg", new byte[] {1, 2, 3});
    }

    private void przygotujZapis() {
        given(postRepository.save(any(Post.class))).willAnswer(w -> w.getArgument(0));
        given(postMapper.toResponse(any(Post.class), any(), any())).willReturn(
            new PostResponse(1L, "anna", null, "tresc", List.of(),
                null, null, null, null, null, null, null,
                LocalDateTime.now(), true, true, ReactionSummary.pusta()));
    }

    /** Serwis oEmbed odpowiada tytulem i miniaturka. */
    private void przygotujOpisMuzyki() {
        given(musicMetadata.pobierz(any()))
            .willReturn(new MusicMetadataService.Opis("Tytul utworu", "https://obrazek/x.jpg"));
    }

    /**
     * Edycja dodatkowo pyta o reakcje, ktore post juz zebral - inaczej
     * po zapisaniu zmiany liczniki zniknelyby z ekranu.
     */
    private void przygotujEdycje() {
        przygotujZapis();
        given(reactionService.podsumowania(anyList(), any())).willReturn(Map.of());
    }

    @Test
    @DisplayName("post z samym tekstem zapisuje sie bez zdjec")
    void postZSamymTekstem() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();

        postService.create("anna", new CreatePostRequest("Dzien dobry", null, null, null), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getContent()).isEqualTo("Dzien dobry");
        assertThat(zapisany.getValue().getImages()).isEmpty();
    }

    @Test
    @DisplayName("zdjecia zachowuja kolejnosc wgrania")
    void zdjeciaZachowujaKolejnosc() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(fileStorage.zapiszObrazek(any())).willReturn("a.jpg", "b.jpg", "c.jpg");
        przygotujZapis();

        postService.create("anna", new CreatePostRequest("Galeria", null, null, null),
            List.of(obrazek("1.jpg"), obrazek("2.jpg"), obrazek("3.jpg")));

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());

        assertThat(zapisany.getValue().getImages())
            .extracting(PostImage::getFileName)
            .containsExactly("a.jpg", "b.jpg", "c.jpg");
        assertThat(zapisany.getValue().getImages())
            .extracting(PostImage::getPosition)
            .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("wiecej niz 10 zdjec zostaje przycietych do limitu")
    void limitZdjec() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(fileStorage.zapiszObrazek(any())).willReturn("x.jpg");
        przygotujZapis();

        List<MultipartFile> duzo = java.util.stream.IntStream.range(0, 15)
            .mapToObj(i -> obrazek(i + ".jpg"))
            .map(MultipartFile.class::cast)
            .toList();

        postService.create("anna", new CreatePostRequest("Duzo zdjec", null, null, null), duzo);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getImages()).hasSize(PostService.MAX_ZDJEC);
    }

    @Test
    @DisplayName("z linku Spotify zapisujemy sam identyfikator, bez parametru ?si=")
    void linkSpotifyJestOczyszczany() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();
        przygotujOpisMuzyki();

        postService.create("anna", new CreatePostRequest(
            "Polecam",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=tajnyparametr",
            MusicKind.TRACK,
            42), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        Post post = zapisany.getValue();

        assertThat(post.getMusicProvider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(post.getMusicKind()).isEqualTo(MusicKind.TRACK);
        assertThat(post.getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(post.getMusicStartSeconds()).isEqualTo(42);
        // tytul pobrany raz, przy dodawaniu
        assertThat(post.getMusicTitle()).isEqualTo("Tytul utworu");
    }

    @Test
    @DisplayName("link z YouTube zapisuje sie jako utwor tego serwisu")
    void linkYouTube() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();
        przygotujOpisMuzyki();

        postService.create("anna", new CreatePostRequest(
            "Polecam", "https://youtu.be/dQw4w9WgXcQ", MusicKind.TRACK, 42), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());

        assertThat(zapisany.getValue().getMusicProvider()).isEqualTo(MusicProvider.YOUTUBE);
        assertThat(zapisany.getValue().getMusicExternalId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("przy ALBUMIE moment startu jest odrzucany, mimo ze przyszedl")
    void albumIgnorujeMomentStartu() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();
        przygotujOpisMuzyki();

        postService.create("anna", new CreatePostRequest(
            "Caly album",
            "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.ALBUM,
            70), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());

        assertThat(zapisany.getValue().getMusicKind()).isEqualTo(MusicKind.ALBUM);
        // Przy albumie nie ma czego przewijac - encja czysci te wartosc sama
        assertThat(zapisany.getValue().getMusicStartSeconds()).isNull();
    }

    @Test
    @DisplayName("gdy serwis oEmbed nie odpowie, post i tak powstaje")
    void awariaOEmbedNieBlokujePosta() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();
        // Serwis niedostepny -> puste wartosci, a nie wyjatek
        given(musicMetadata.pobierz(any())).willReturn(MusicMetadataService.Opis.pusty());

        postService.create("anna", new CreatePostRequest(
            "Polecam",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.TRACK,
            null), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());

        // odtwarzacz i tak zadziala - iframe pobiera sobie wszystko sam
        assertThat(zapisany.getValue().getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(zapisany.getValue().getMusicTitle()).isNull();
    }

    @Test
    @DisplayName("autor moze usunac swoj post - razem z plikami z dysku")
    void autorUsuwaSwojPost() {
        Post post = new Post(anna(), "tresc");
        post.addImage(new PostImage("zdjecie.jpg"));

        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));

        postService.delete(5L, "anna");

        verify(postRepository).delete(post);
        // pliki znikaja z dysku, inaczej katalog uploadow rosnie w nieskonczonosc
        verify(fileStorage).usun("zdjecie.jpg");
    }

    @Test
    @DisplayName("zwykly uzytkownik NIE usunie cudzego posta")
    void cudzyPostJestChroniony() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");

        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));

        assertThatThrownBy(() -> postService.delete(5L, "anna"))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    @DisplayName("administrator moze usunac cudzy post - moderacja")
    void administratorUsuwaCudzyPost() {
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
    void autorEdytujeSwojPost() {
        Post post = new Post(anna(), "stara tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        przygotujEdycje();

        przygotujOpisMuzyki();
        postService.update(5L, "anna", new UpdatePostRequest(
            "nowa tresc", "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            MusicKind.TRACK, 30));

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getContent()).isEqualTo("nowa tresc");
        assertThat(zapisany.getValue().getMusicExternalId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(zapisany.getValue().getMusicStartSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("wyczyszczenie linku usuwa utwor RAZEM z wybranym momentem")
    void pustyLinkUsuwaUtwor() {
        Post post = new Post(anna(), "tresc");
        post.ustawMuzyke(new com.musicclubapp.music.ParsedMusicLink(
            MusicProvider.SPOTIFY, MusicKind.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),
            30, "Tytul", "https://obrazek/x.jpg");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post));
        przygotujEdycje();

        postService.update(5L, "anna", new UpdatePostRequest("tresc", "", null, null));

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        Post zapisanyPost = zapisany.getValue();

        assertThat(zapisanyPost.maMuzyke()).isFalse();
        assertThat(zapisanyPost.getMusicExternalId()).isNull();
        // razem z nagraniem znikaja tytul, miniaturka i moment startu
        assertThat(zapisanyPost.getMusicTitle()).isNull();
        assertThat(zapisanyPost.getMusicStartSeconds()).isNull();
    }

    @Test
    @DisplayName("zwykly uzytkownik NIE zedytuje cudzego posta")
    void cudzyPostNieDoEdycji() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));

        assertThatThrownBy(() -> postService.update(
            5L, "anna", new UpdatePostRequest("przejete", null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("nawet ADMINISTRATOR nie edytuje cudzego posta - moderuje usuwaniem")
    void administratorTezNieEdytuje() {
        Post cudzy = new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(cudzy));

        assertThatThrownBy(() -> postService.update(
            5L, "admin", new UpdatePostRequest("podmienione", null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("usuwanie nieistniejacego posta konczy sie wyjatkiem 'nie znaleziono'")
    void nieistniejacyPostRzucaWyjatek() {
        given(postRepository.findByIdWithAuthor(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L, "anna"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }
}
