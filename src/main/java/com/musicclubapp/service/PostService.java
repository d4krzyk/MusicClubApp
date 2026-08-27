package com.musicclubapp.service;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.FeedScope;
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
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
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
    private final NotificationService notifications;
    private final ReactionRepository reactionRepository;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       FileStorageService fileStorage,
                       PostMapper postMapper,
                       ReactionService reactionService,
                       MusicMetadataService musicMetadata,
                       NotificationService notifications,
                       ReactionRepository reactionRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.postMapper = postMapper;
        this.reactionService = reactionService;
        this.musicMetadata = musicMetadata;
        this.notifications = notifications;
        this.reactionRepository = reactionRepository;
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

        // Pusta wartosc = publiczny; setter na encji sam pilnuje, zeby post
        // nigdy nie zostal bez widocznosci
        post.setVisibility(request.visibility());

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
        post.setVisibility(request.visibility());

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
     * <b>Tablica: najpierw znajomi, pod nimi reszta.</b>
     *
     * <p>Stronicowana po stronie bazy (wymagania nr 3 i 5).</p>
     *
     * <p><b>Sortowanie z {@code Pageable} celowo odrzucamy.</b> Kolejnosc
     * tablicy nie jest tu ustawieniem uzytkownika, tylko trescia funkcji:
     * najpierw krag, potem swiat, a w kazdej z tych grup - od najnowszych.
     * Gdyby przepuscic tu sortowanie z adresu, Spring Data dokleilby je do
     * {@code ORDER BY} zapisanego w zapytaniu i wyszlaby kolejnosc, ktorej
     * nikt nie zamawial. Parametr {@code direction} dziala nadal tam, gdzie
     * ma sens - przy postach jednego autora.</p>
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String viewerUsername, FeedScope scope, Pageable pageable) {
        List<Long> circle = userRepository.circleIds(viewerUsername);
        Pageable byOurOrder = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Post> page = scope == FeedScope.FRIENDS
            ? postRepository.findCircleFeed(circle, byOurOrder)
            : postRepository.findFeed(circle, byOurOrder);

        return withReactions(page, viewerUsername, circle);
    }

    /**
     * Jeden post po identyfikatorze.
     *
     * <p>Powstal dla powiadomien: klikniecie w "ktos zareagowal" ma prowadzic
     * do <b>tego</b> posta, a nie na tablice. Post moze byc setny od gory,
     * wiec odeslanie na tablice znaczyloby "poszukaj sobie".</p>
     *
     * <p>Przydaje sie tez do wyslania komus linku do konkretnego wpisu.</p>
     */
    @Transactional(readOnly = true)
    public PostResponse getOne(Long id, String viewerUsername) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);

        /*
         * Bez tego sprawdzenia caly wybor "tylko dla znajomych" bylby ozdoba:
         * wystarczyloby wpisac /post/12 z reki, zeby przeczytac cokolwiek.
         * Adres pojedynczego posta jest publiczna droga do kazdego wpisu,
         * wiec regula musi obowiazywac tu tak samo jak na tablicy.
         */
        if (!post.isVisibleTo(viewer)) {
            throw OperationNotAllowedException.friendsOnlyPost();
        }

        ReactionSummary summary = reactionService
            .summaries(List.of(id), viewerUsername)
            .getOrDefault(id, ReactionSummary.empty());

        return postMapper.toResponse(post, viewer, summary);
    }

    /** Posty jednego uzytkownika - do jego profilu. */
    @Transactional(readOnly = true)
    public Page<PostResponse> byAuthor(String author, String viewerUsername, Pageable pageable) {
        List<Long> circle = userRepository.circleIds(viewerUsername);
        return withReactions(
            postRepository.findByAuthorUsername(author, circle, pageable),
            viewerUsername,
            circle);
    }

    /**
     * Liczniki reakcji dla wskazanych postow - <b>bez pobierania ich tresci</b>.
     *
     * <p>Uzywa tego tablica, zeby odswiezyc emotki pod postami, ktore
     * uzytkownik ma akurat na ekranie (po powrocie do karty przegladarki).
     * Pobieranie w tym celu calej tablicy od nowa oznaczaloby przeskok widoku
     * i utrate miejsca, w ktorym ktos wlasnie czytal.</p>
     */
    @Transactional(readOnly = true)
    public Map<Long, ReactionSummary> reactionSummaries(List<Long> postIds, String viewerUsername) {
        return reactionService.summaries(postIds, viewerUsername);
    }

    /**
     * Przepisuje strone postow na DTO, doliczajac reakcje.
     *
     * <p>Liczniki pobieramy dla CALEJ strony jednym zapytaniem, a dopiero
     * potem rozdajemy je poszczegolnym postom. Gdyby kazdy post pytal o swoje
     * reakcje sam, przy dwudziestu wpisach byloby dwadziescia dodatkowych
     * zapytan do bazy (problem N+1).</p>
     *
     * <p>Z tego samego powodu <b>krag przekazujemy gotowy</b>: mapper umie
     * sprawdzic znajomosc sam, ale robi to doczytujac liste znajomych autora -
     * czyli raz na post.</p>
     */
    private Page<PostResponse> withReactions(Page<Post> page, String viewerUsername,
                                             Collection<Long> circle) {
        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);

        List<Long> postIds = page.getContent().stream().map(Post::getId).toList();
        Map<Long, ReactionSummary> reactions =
            reactionService.summaries(postIds, viewerUsername);

        return page.map(post -> postMapper.toResponse(
            post,
            viewer,
            reactions.getOrDefault(post.getId(), ReactionSummary.empty()),
            circle.contains(post.getAuthor().getId())));
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

        /*
         * Powiadomienia o reakcjach wskazuja na posta KLUCZEM OBCYM, wiec
         * musza zniknac przed nim - inaczej baza odmowi skasowania.
         * To akurat zaleta: gdyby byl to luzny numer, powiadomienie
         * przezyloby posta i prowadziloby donikad.
         */
        notifications.postDeleted(post.getId());

        /*
         * Cudze reakcje pod tym postem kasujemy WPROST, mimo ze encja ma
         * cascade = ALL. Kaskada opiera sie na kolekcji zaladowanej do
         * pamieci, a reakcja dopisana w tej samej transakcji do niej nie
         * trafia - baza odmawia wtedy skasowania posta z powodu klucza
         * obcego. Blad byl niewidoczny w testach na atrapach.
         */
        reactionRepository.deleteByPostId(post.getId());

        postRepository.delete(post);
        files.forEach(fileStorage::remove);
    }
}
