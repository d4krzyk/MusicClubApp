package com.musicclubapp.service;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.dto.UpdatePostRequest;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.BanKind;
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

/** Logika postow: dodawanie, tablica i usuwanie. */
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

    /** Dodaje post razem ze zdjeciami. */
    @Transactional
    public PostResponse create(String username, CreatePostRequest request, List<MultipartFile> images) {
        User author = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        /* Zakazu pilnujemy TUTAJ, a nie w kontrolerze. */
        if (author.isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, author.bannedUntil(BanKind.POSTING));
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

    /** Edycja wlasnego posta - tresc i utwor ze Spotify. */
    @Transactional
    public PostResponse update(Long id, String username, UpdatePostRequest request) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        if (!post.getAuthor().getUsername().equals(username)) {
            throw OperationNotAllowedException.someoneElsesPostEdit();
        }

        /* Zakaz obejmuje takze edycje. */
        if (post.getAuthor().isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, 
                post.getAuthor().bannedUntil(BanKind.POSTING));
        }

        post.setContent(request.content().trim());
        post.setVisibility(request.visibility());

        /*
         * Puste pole z linkiem oznacza "usun nagranie z posta" - dlatego wolamy to zawsze, takze
         * gdy adres jest pusty.
         */
        applyMusic(post, request.musicUrl(), request.musicStartSeconds());

        User author = post.getAuthor();

        /* Post moze juz miec reakcje - edycja tresci ich nie kasuje. */
        ReactionSummary reactions = reactionService.summaries(List.of(id), username).get(id);

        return postMapper.toResponse(postRepository.save(post), author, reactions);
    }

    /** Podpina nagranie do posta - razem z tytulem i miniaturka. */
    private void applyMusic(Post post, String url, Integer startSeconds) {
        ParsedMusicLink link = MusicLinkParser.parse(url).orElse(null);

        if (link == null) {
            post.applyMusic(null, null, null, null);
            return;
        }

        /* Tytul i miniaturke pobieramy RAZ, tutaj. */
        MusicMetadataService.Metadata metadata = musicMetadata.fetch(link);
        post.applyMusic(link, startSeconds, metadata.title(), metadata.thumbnailUrl());
    }

    /** Tablica: najpierw znajomi, pod nimi reszta. */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String viewerUsername, FeedScope scope, Pageable pageable) {
        List<Long> circle = userRepository.circleIds(viewerUsername);
        Pageable byOurOrder = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Post> page = scope == FeedScope.FRIENDS
            ? postRepository.findCircleFeed(circle, byOurOrder)
            : postRepository.findFeed(circle, byOurOrder);

        return withReactions(page, viewerUsername, circle);
    }

    /** Jeden post po identyfikatorze. */
    @Transactional(readOnly = true)
    public PostResponse getOne(Long id, String viewerUsername) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);

        /*
         * Bez tego sprawdzenia caly wybor "tylko dla znajomych" bylby ozdoba: wystarczyloby wpisac
         * /post/12 z reki, zeby przeczytac cokolwiek.
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

    /** Liczniki reakcji dla wskazanych postow - bez pobierania ich tresci. */
    @Transactional(readOnly = true)
    public Map<Long, ReactionSummary> reactionSummaries(List<Long> postIds, String viewerUsername) {
        return reactionService.summaries(postIds, viewerUsername);
    }

    /** Przepisuje strone postow na DTO, doliczajac reakcje. */
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

    /** Usuwa post razem z jego zdjeciami. */
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
         * Najpierw zbieramy nazwy plikow, potem kasujemy wiersz z bazy, a files z dysku na koncu.
         */
        List<String> files = post.getImages().stream().map(PostImage::getFileName).toList();

        /*
         * Powiadomienia o reakcjach wskazuja na posta KLUCZEM OBCYM, wiec musza zniknac przed nim
         * - inaczej baza odmowi skasowania.
         */
        notifications.postDeleted(post.getId());

        /* Cudze reakcje pod tym postem kasujemy WPROST, mimo ze encja ma cascade = ALL. */
        reactionRepository.deleteByPostId(post.getId());

        postRepository.delete(post);
        files.forEach(fileStorage::remove);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Kasuje wszystkie posty konta razem z ich zdjeciami - przy usuwaniu uzytkownika. */
    @Transactional
    public int deleteAllOf(Long authorId) {
        List<Post> posts = postRepository.findByAuthorId(authorId);

        List<String> files = posts.stream()
            .flatMap(post -> post.getImages().stream())
            .map(PostImage::getFileName)
            .toList();

        postRepository.deleteAll(posts);
        files.forEach(fileStorage::remove);
        return posts.size();
    }
}
