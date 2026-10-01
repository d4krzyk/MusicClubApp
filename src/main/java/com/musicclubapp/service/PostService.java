package com.musicclubapp.service;

import com.musicclubapp.dto.ClanBadge;
import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.FeedReason;
import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.FeedSort;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.dto.UpdatePostRequest;
import com.musicclubapp.entity.Clan;
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
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostService.class);

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
    private final BlockService blocks;
    private final PrivacyService privacy;
    private final MusicEventRepository events;
    private final ClanService clans;
    private final FeedService feeds;
    private final CommentService comments;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       FileStorageService fileStorage,
                       PostMapper postMapper,
                       ReactionService reactionService,
                       MusicMetadataService musicMetadata,
                       NotificationService notifications,
                       ReactionRepository reactionRepository,
                       BlockService blocks,
                       PrivacyService privacy,
                       MusicEventRepository events,
                       ClanService clans,
                       FeedService feeds,
                       CommentService comments) {
        this.comments = comments;
        this.feeds = feeds;
        this.privacy = privacy;
        this.events = events;
        this.clans = clans;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.postMapper = postMapper;
        this.reactionService = reactionService;
        this.musicMetadata = musicMetadata;
        this.notifications = notifications;
        this.reactionRepository = reactionRepository;
        this.blocks = blocks;
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

        if (request.eventId() != null) {
            post.setEvent(events.findById(request.eventId())
                .orElseThrow(() -> new NoSuchElementFoundException("event", request.eventId())));
        }
        if (request.clanId() != null) {
            // Post klanu: tylko czlonek moze go napisac, a widoczny jest tylko w klanie - nie ma go na tablicy
            post.setClan(clans.requireMember(username, request.clanId()));
        }

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
        return withAuthorClan(postMapper.toResponse(postRepository.save(post), author, ReactionSummary.empty()), author);
    }

    /** Edycja wlasnego posta - tresc i utwor ze Spotify. */
    @Transactional
    public PostResponse update(Long id, String username, UpdatePostRequest request) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        // Post klanu: kto z niego wyszedl (albo nigdy w nim nie byl), nie ma do niego dostepu - ani do edycji
        if (post.getClan() != null && !post.isVisibleTo(userRepository.findByUsername(username).orElse(null))) {
            throw new NoSuchElementFoundException("post", id);
        }
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

        return withAuthorClan(postMapper.toResponse(postRepository.save(post), author, reactions), author)
            .withCommentCount(comments.countsFor(author.getId(), List.of(id)).getOrDefault(id, 0L));
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

    /**
     * Tablica z wyborem kolejnosci. "Dla ciebie" ({@link FeedSort#RELEVANT}) dotyczy tylko postow osob spoza
     * kregu i tylko przy zakresie ALL - krag i tak jest od najnowszych.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String viewerUsername, FeedScope scope, FeedSort sort, Pageable pageable) {
        if (scope == FeedScope.FRIENDS || sort == FeedSort.NEWEST) {
            return feed(viewerUsername, scope, pageable);
        }
        User viewer = userRepository.findByUsername(viewerUsername)
            .orElseThrow(() -> new NoSuchElementFoundException("user", viewerUsername));
        List<Long> circle = userRepository.circleIds(viewerUsername);
        FeedService.Result result = feeds.relevant(viewer, circle, blocks.hiddenForQuery(viewer.getId()),
            pageable.getPageNumber(), pageable.getPageSize());
        Page<Post> page = new PageImpl<>(result.posts(),
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), result.total());
        return withReactions(page, viewerUsername, circle)
            .map(r -> r.withFeedReasons(result.reasons().getOrDefault(r.id(), List.<FeedReason>of())));
    }

    /** Tablica: najpierw znajomi, pod nimi reszta - wszyscy od najnowszych. */
    @Transactional(readOnly = true)
    public Page<PostResponse> feed(String viewerUsername, FeedScope scope, Pageable pageable) {
        List<Long> circle = userRepository.circleIds(viewerUsername);
        Pageable byOurOrder = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        // Znajomi nie moga byc zablokowani (blokada zrywa znajomosc), wiec krag ich nie potrzebuje
        Page<Post> page = scope == FeedScope.FRIENDS
            ? postRepository.findCircleFeed(circle, byOurOrder)
            : postRepository.findFeed(circle,
                blocks.hiddenForQuery(userRepository.findByUsername(viewerUsername).map(User::getId).orElse(null)),
                byOurOrder);

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
            // Post klanu dla obcego to post, ktorego nie ma - nie zdradzamy, ze klan istnieje
            if (post.getClan() != null) {
                throw new NoSuchElementFoundException("post", id);
            }
            throw OperationNotAllowedException.friendsOnlyPost();
        }
        // Post osoby zablokowanej albo blokujacej - jakby go nie bylo
        if (viewer != null && blocks.eitherWay(viewer.getId(), post.getAuthor().getId())) {
            throw new NoSuchElementFoundException("post", id);
        }

        ReactionSummary summary = reactionService
            .summaries(List.of(id), viewerUsername)
            .getOrDefault(id, ReactionSummary.empty());

        return withAuthorClan(postMapper.toResponse(post, viewer, summary), post.getAuthor())
            .withCommentCount(viewer == null ? 0 : comments.countsFor(viewer.getId(), List.of(id)).getOrDefault(id, 0L));
    }

    /** Posty jednego uzytkownika - do jego profilu. */
    @Transactional(readOnly = true)
    public Page<PostResponse> byAuthor(String author, String viewerUsername, Pageable pageable) {
        // Profil tylko dla znajomych, blokady - to samo co przy reszcie profilu
        privacy.requireDetails(author, viewerUsername);
        List<Long> circle = userRepository.circleIds(viewerUsername);
        return withReactions(
            postRepository.findByAuthorUsername(author, circle, pageable),
            viewerUsername,
            circle);
    }

    /**
     * Posty pod wydarzeniem - najnowsze na gorze. Te same zasady co na tablicy:
     * "tylko dla znajomych" widzi krag autora, a osoby zablokowane
     * (w ktoras strone) znikaja.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> byEvent(Long eventId, String viewerUsername, Pageable pageable) {
        if (!events.existsById(eventId)) {
            throw new NoSuchElementFoundException("event", eventId);
        }
        List<Long> circle = userRepository.circleIds(viewerUsername);
        Long viewerId = userRepository.findByUsername(viewerUsername).map(User::getId).orElse(null);
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return withReactions(
            postRepository.findByEvent(eventId, circle, blocks.hiddenForQuery(viewerId), newestFirst),
            viewerUsername,
            circle);
    }

    /**
     * Posty klanu - najnowsze na gorze. Dla czlonkow i administratora aplikacji; osoby z blokad
     * ogladajacego sa pomijane, tak jak na tablicy.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> byClan(Long clanId, String viewerUsername, Pageable pageable) {
        User viewer = userRepository.findByUsername(viewerUsername)
            .orElseThrow(() -> new NoSuchElementFoundException("user", viewerUsername));
        Clan clan = clans.requireAccess(viewer, clanId);
        boolean adminSpozaKlanu = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId());
        if (adminSpozaKlanu && pageable.getPageNumber() == 0) {
            log.info("Audyt: administrator {} czyta posty klanu {} (#{})", viewer.getUsername(), clan.getName(), clan.getId());
        }
        List<Long> hidden = adminSpozaKlanu ? List.of(-1L) : blocks.hiddenForQuery(viewer.getId());
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return withReactions(postRepository.findByClan(clanId, hidden, newestFirst), viewerUsername, List.of());
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

        // Plakietki klanow autorow calej strony - jedno zapytanie, a nie jedno na post
        Map<Long, ClanBadge> badges = clans.badgesOf(
            page.getContent().stream().map(p -> p.getAuthor().getId()).distinct().toList());

        // Liczby komentarzy calej strony - tez jedno zapytanie
        Map<Long, Long> commentCounts = viewer == null ? Map.of() : comments.countsFor(viewer.getId(), postIds);

        return page.map(post -> postMapper.toResponse(
                post,
                viewer,
                reactions.getOrDefault(post.getId(), ReactionSummary.empty()),
                circle.contains(post.getAuthor().getId()))
            .withAuthorClan(badges.get(post.getAuthor().getId()))
            .withCommentCount(commentCounts.getOrDefault(post.getId(), 0L)));
    }

    /** Odpowiedz pojedynczego posta z plakietka klanu autora. */
    private PostResponse withAuthorClan(PostResponse response, User author) {
        return response.withAuthorClan(clans.badgeOf(author.getId()));
    }

    /** Usuwa post razem z jego zdjeciami. */
    @Transactional
    public void delete(Long id, String username) {
        Post post = postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));

        User viewer = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        // Post klanu dla kogos spoza klanu (takze dla autora, ktory z niego odszedl) - jakby go nie bylo
        if (post.getClan() != null && !post.isVisibleTo(viewer)) {
            throw new NoSuchElementFoundException("post", id);
        }
        boolean isAuthor = post.getAuthor().getUsername().equals(username);
        boolean zarzadKlanu = post.getClan() != null && clans.canModerate(viewer, post.getClan());
        if (!isAuthor && !zarzadKlanu && viewer.getRole() != Role.ADMIN) {
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
