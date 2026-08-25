package com.musicclubapp.service;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        given(postMapper.toResponse(any(Post.class), any())).willReturn(
            new PostResponse(1L, "anna", null, "tresc", List.of(), null, null,
                LocalDateTime.now(), true));
    }

    @Test
    @DisplayName("post z samym tekstem zapisuje sie bez zdjec")
    void postZSamymTekstem() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();

        postService.create("anna", new CreatePostRequest("Dzien dobry", null, null), null);

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

        postService.create("anna", new CreatePostRequest("Galeria", null, null),
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

        postService.create("anna", new CreatePostRequest("Duzo zdjec", null, null), duzo);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getImages()).hasSize(PostService.MAX_ZDJEC);
    }

    @Test
    @DisplayName("z linku Spotify zapisujemy sam identyfikator, bez parametru ?si=")
    void linkSpotifyJestOczyszczany() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();

        postService.create("anna", new CreatePostRequest(
            "Polecam",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=tajnyparametr",
            42), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getSpotifyTrackId()).isEqualTo("4cOdK2wGLETKBW3PvgPWqT");
        assertThat(zapisany.getValue().getSpotifyStartSeconds()).isEqualTo(42);
    }

    @Test
    @DisplayName("tekst niebedacy linkiem Spotify jest po prostu pomijany")
    void nieprawidlowyLinkNiePsujePostu() {
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        przygotujZapis();

        postService.create("anna",
            new CreatePostRequest("Bez muzyki", "to nie jest link", 10), null);

        ArgumentCaptor<Post> zapisany = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(zapisany.capture());
        assertThat(zapisany.getValue().getSpotifyTrackId()).isNull();
        // sekunda bez utworu nie ma sensu - tez zostaje pusta
        assertThat(zapisany.getValue().getSpotifyStartSeconds()).isNull();
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
    @DisplayName("usuwanie nieistniejacego posta konczy sie wyjatkiem 'nie znaleziono'")
    void nieistniejacyPostRzucaWyjatek() {
        given(postRepository.findByIdWithAuthor(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L, "anna"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }
}
