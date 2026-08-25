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
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.spotify.SpotifyLink;
import com.musicclubapp.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Logika postow: dodawanie, tablica i usuwanie.
 *
 * <p>Cala praca decyzyjna siedzi tutaj, a nie w kontrolerze - dzieki temu da
 * sie ja przetestowac jednostkowo (wymaganie nr 13), bez uruchamiania
 * serwera ani bazy.</p>
 */
@Service
public class PostService {

    /** Ile zdjec maksymalnie w jednym poscie. */
    public static final int MAX_ZDJEC = 10;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorage;
    private final PostMapper postMapper;
    private final ReactionService reactionService;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       FileStorageService fileStorage,
                       PostMapper postMapper,
                       ReactionService reactionService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.postMapper = postMapper;
        this.reactionService = reactionService;
    }

    /**
     * Dodaje post razem ze zdjeciami.
     *
     * @param login  autor - brany z sesji, nigdy z tresci zapytania
     * @param zdjecia lista wgranych plikow (moze byc pusta albo {@code null})
     */
    @Transactional
    public PostResponse create(String login, CreatePostRequest request, List<MultipartFile> zdjecia) {
        User autor = userRepository.findByUsername(login)
            .orElseThrow(() -> new NoSuchElementFoundException("user", login));

        Post post = new Post(autor, request.content().trim());

        /*
         * Z wklejonego adresu wyciagamy sam identyfikator utworu. Gdy tekst
         * nie wyglada na link do Spotify, po prostu go pomijamy - post
         * powstanie bez odtwarzacza, zamiast wywalac sie bledem.
         */
        SpotifyLink.wyciagnijIdUtworu(request.spotifyUrl())
            .ifPresent(id -> {
                post.setSpotifyTrackId(id);
                post.setSpotifyStartSeconds(request.spotifyStartSeconds());
            });

        if (zdjecia != null) {
            List<MultipartFile> doZapisu = zdjecia.stream()
                .filter(p -> p != null && !p.isEmpty())
                .limit(MAX_ZDJEC)
                .toList();

            for (MultipartFile plik : doZapisu) {
                post.addImage(new PostImage(fileStorage.zapiszObrazek(plik)));
            }
        }

        // Swiezo dodany post nie ma jeszcze zadnych reakcji
        return postMapper.toResponse(postRepository.save(post), autor, ReactionSummary.pusta());
    }

    /**
     * Edycja wlasnego posta - tresc i utwor ze Spotify.
     *
     * <p>Edytowac moze WYLACZNIE autor. Administrator, mimo szerszych
     * uprawnien, tez nie - moderacja polega na usuwaniu, a nie na przerabianiu
     * cudzych wypowiedzi.</p>
     *
     * <p>Zdjecia zostaja bez zmian - patrz komentarz w {@link UpdatePostRequest}.</p>
     *
     * @throws OperationNotAllowedException gdy ktos probuje edytowac cudzy post
     */
    @Transactional
    public PostResponse update(Long id, String login, UpdatePostRequest request) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        if (!post.getAuthor().getUsername().equals(login)) {
            throw OperationNotAllowedException.cudzyPostEdycja();
        }

        post.setContent(request.content().trim());

        /*
         * Puste pole z linkiem oznacza "usun utwor z posta" - dlatego
         * ustawiamy wynik parsowania zawsze, takze gdy jest pusty.
         */
        post.ustawUtwor(
            SpotifyLink.wyciagnijIdUtworu(request.spotifyUrl()).orElse(null),
            request.spotifyStartSeconds());

        User autor = post.getAuthor();

        /*
         * Post moze juz miec reakcje - edycja tresci ich nie kasuje.
         * Uzywamy tu parametru `id`, a nie `post.getId()`: to ta sama wartosc,
         * ale parametr na pewno nie jest pusty, a `List.of(null)` wywalilby sie
         * wyjatkiem.
         */
        ReactionSummary reakcje = reactionService.podsumowania(List.of(id), login).get(id);

        return postMapper.toResponse(postRepository.save(post), autor, reakcje);
    }

    /**
     * Tablica - wszystkie posty od najnowszych, stronicowane
     * (wymagania nr 3 i 5).
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String loginOgladajacego, Pageable pageable) {
        return zReakcjami(postRepository.findFeed(pageable), loginOgladajacego);
    }

    /** Posty jednego uzytkownika - do jego profilu. */
    @Transactional(readOnly = true)
    public Page<PostResponse> byAuthor(String autor, String loginOgladajacego, Pageable pageable) {
        return zReakcjami(postRepository.findByAuthorUsername(autor, pageable), loginOgladajacego);
    }

    /**
     * Przepisuje strone postow na DTO, doliczajac reakcje.
     *
     * <p>Liczniki pobieramy dla CALEJ strony jednym zapytaniem, a dopiero
     * potem rozdajemy je poszczegolnym postom. Gdyby kazdy post pytal o swoje
     * reakcje sam, przy dwudziestu wpisach byloby dwadziescia dodatkowych
     * zapytan do bazy (problem N+1).</p>
     */
    private Page<PostResponse> zReakcjami(Page<Post> strona, String loginOgladajacego) {
        User ogladajacy = userRepository.findByUsername(loginOgladajacego).orElse(null);

        List<Long> idPostow = strona.getContent().stream().map(Post::getId).toList();
        Map<Long, ReactionSummary> reakcje =
            reactionService.podsumowania(idPostow, loginOgladajacego);

        return strona.map(post -> postMapper.toResponse(
            post,
            ogladajacy,
            reakcje.getOrDefault(post.getId(), ReactionSummary.pusta())));
    }

    /**
     * Usuwa post razem z jego zdjeciami.
     *
     * <p>Kasowac moze autor albo administrator. Sprawdzenie jest TUTAJ,
     * a nie w kontrolerze - inaczej wystarczyloby wywolac serwis z innego
     * miejsca, zeby je obejsc.</p>
     *
     * @throws OperationNotAllowedException gdy ktos probuje skasowac cudzy post
     */
    @Transactional
    public void delete(Long id, String login) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        User ogladajacy = userRepository.findByUsername(login)
            .orElseThrow(() -> new NoSuchElementFoundException("user", login));

        boolean jestAutorem = post.getAuthor().getUsername().equals(login);
        if (!jestAutorem && ogladajacy.getRole() != Role.ADMIN) {
            throw OperationNotAllowedException.cudzyPost();
        }

        /*
         * Najpierw zbieramy nazwy plikow, potem kasujemy wiersz z bazy,
         * a pliki z dysku na koncu. Odwrotna kolejnosc groziłaby tym, ze
         * pliki znikna, a post zostanie - i tablica pokazalaby puste ramki.
         */
        List<String> pliki = post.getImages().stream().map(PostImage::getFileName).toList();

        postRepository.delete(post);
        pliki.forEach(fileStorage::usun);
    }
}
