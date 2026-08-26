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
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
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
    public static final int MAX_IMAGES = 10;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorage;
    private final PostMapper postMapper;
    private final ReactionService reactionService;
    private final MusicMetadataService musicMetadata;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       FileStorageService fileStorage,
                       PostMapper postMapper,
                       ReactionService reactionService,
                       MusicMetadataService musicMetadata) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.postMapper = postMapper;
        this.reactionService = reactionService;
        this.musicMetadata = musicMetadata;
    }

    /**
     * Dodaje post razem ze zdjeciami.
     *
     * @param login  autor - brany z sesji, nigdy z tresci zapytania
     * @param zdjecia lista wgranych plikow (moze byc pusta albo {@code null})
     */
    @Transactional
    public PostResponse create(String username, CreatePostRequest request, List<MultipartFile> images) {
        User author = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        /*
         * Zakazu pilnujemy TUTAJ, a nie w kontrolerze. Post powstaje w tej
         * jednej metodzie, wiec jedno sprawdzenie w tym miejscu zamyka sprawe -
         * takze wtedy, gdy kiedys dojdzie druga droga dodawania postow.
         */
        if (author.isPostingBanned()) {
            throw OperationNotAllowedException.postingBanned(author.getPostingBannedUntil());
        }

        Post post = new Post(author, request.content().trim());

        applyMusic(post, request.musicUrl(), request.musicStartSeconds());

        if (images != null) {
            List<MultipartFile> toSave = images.stream()
                .filter(p -> p != null && !p.isEmpty())
                .limit(MAX_IMAGES)
                .toList();

            for (MultipartFile file : toSave) {
                post.addImage(new PostImage(fileStorage.saveImage(file)));
            }
        }

        // Swiezo dodany post nie ma jeszcze zadnych reakcji
        return postMapper.toResponse(postRepository.save(post), author, ReactionSummary.empty());
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
    public PostResponse update(Long id, String username, UpdatePostRequest request) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        if (!post.getAuthor().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesPostEdit();
        }

        /*
         * Zakaz obejmuje takze edycje. Inaczej ukarany moglby zamienic dowolny
         * previous post w nowa tresc i kara nie znaczylaby nic.
         *
         * USUWANIE wlasnego posta zostaje dozwolone: kara ma powstrzymac przed
         * publikowaniem, a nie zmusic do zostawienia czegos na tablicy.
         */
        if (post.getAuthor().isPostingBanned()) {
            throw OperationNotAllowedException.postingBanned(
                post.getAuthor().getPostingBannedUntil());
        }

        post.setContent(request.content().trim());

        /*
         * Puste pole z linkiem oznacza "usun nagranie z posta" - dlatego
         * wolamy to zawsze, takze gdy adres jest pusty.
         */
        applyMusic(post, request.musicUrl(), request.musicStartSeconds());

        User author = post.getAuthor();

        /*
         * Post moze juz miec reakcje - edycja tresci ich nie kasuje.
         * Uzywamy tu parametru `id`, a nie `post.getId()`: to ta sama wartosc,
         * ale parametr na pewno nie jest pusty, a `List.of(null)` wywalilby sie
         * wyjatkiem.
         */
        ReactionSummary reactions = reactionService.summaries(List.of(id), username).get(id);

        return postMapper.toResponse(postRepository.save(post), author, reactions);
    }

    /**
     * Podpina nagranie do posta - razem z tytulem i miniaturka.
     *
     * <p>Poprawnosc adresu sprawdzil juz walidator {@code ValidMusicLink}
     * (blad trafia wtedy do konkretnego pola formularza), wiec tutaj
     * nierozpoznany adres moze znaczyc juz tylko jedno: pole jest puste,
     * czyli post ma byc bez muzyki.</p>
     */
    private void applyMusic(Post post, String url, Integer startSeconds) {
        ParsedMusicLink link = MusicLinkParser.parse(url).orElse(null);

        if (link == null) {
            post.applyMusic(null, null, null, null);
            return;
        }

        /*
         * Tytul i miniaturke pobieramy RAZ, tutaj. Gdy serwis nie odpowie,
         * wracaja puste wartosci - post i tak powstaje, bo odtwarzacz
         * laduje sie w przegladarce niezaleznie od tego.
         */
        MusicMetadataService.Metadata metadata = musicMetadata.fetch(link);
        post.applyMusic(link, startSeconds, metadata.title(), metadata.thumbnailUrl());
    }

    /**
     * Tablica - wszystkie posty od najnowszych, stronicowane
     * (wymagania nr 3 i 5).
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String viewerUsername, Pageable pageable) {
        return withReactions(postRepository.findFeed(pageable), viewerUsername);
    }

    /** Posty jednego uzytkownika - do jego profilu. */
    @Transactional(readOnly = true)
    public Page<PostResponse> byAuthor(String author, String viewerUsername, Pageable pageable) {
        return withReactions(postRepository.findByAuthorUsername(author, pageable), viewerUsername);
    }

    /**
     * Przepisuje strone postow na DTO, doliczajac reakcje.
     *
     * <p>Liczniki pobieramy dla CALEJ strony jednym zapytaniem, a dopiero
     * potem rozdajemy je poszczegolnym postom. Gdyby kazdy post pytal o swoje
     * reakcje sam, przy dwudziestu wpisach byloby dwadziescia dodatkowych
     * zapytan do bazy (problem N+1).</p>
     */
    private Page<PostResponse> withReactions(Page<Post> page, String viewerUsername) {
        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);

        List<Long> postIds = page.getContent().stream().map(Post::getId).toList();
        Map<Long, ReactionSummary> reactions =
            reactionService.summaries(postIds, viewerUsername);

        return page.map(post -> postMapper.toResponse(
            post,
            viewer,
            reactions.getOrDefault(post.getId(), ReactionSummary.empty())));
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
    public void delete(Long id, String username) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        User viewer = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        boolean isAuthor = post.getAuthor().getUsername().equals(username);
        if (!isAuthor && viewer.getRole() != Role.ADMIN) {
            throw OperationNotAllowedException.someoneElsesPost();
        }

        /*
         * Najpierw zbieramy nazwy plikow, potem kasujemy wiersz z bazy,
         * a files z dysku na koncu. Odwrotna kolejnosc groziłaby tym, ze
         * files znikna, a post zostanie - i tablica pokazalaby puste ramki.
         */
        List<String> files = post.getImages().stream().map(PostImage::getFileName).toList();

        postRepository.delete(post);
        files.forEach(fileStorage::remove);
    }
}
