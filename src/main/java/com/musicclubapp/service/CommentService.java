package com.musicclubapp.service;

import com.musicclubapp.dto.ClanBadge;
import com.musicclubapp.dto.CommentResponse;
import com.musicclubapp.dto.CommentsPageResponse;
import com.musicclubapp.dto.CreateCommentRequest;
import com.musicclubapp.dto.MentionHint;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.Comment;
import com.musicclubapp.entity.CommentMention;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.CommentCountRow;
import com.musicclubapp.repository.CommentMentionRepository;
import com.musicclubapp.repository.CommentMentionRow;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Komentarze pod postami: lista, odpowiedzi, dodawanie z oznaczeniami (@login), kasowanie.
 *
 * <p>Kto widzi komentarze, ten widzi post: post tylko dla znajomych - znajomi autora, post klanu -
 * czlonkowie i administrator aplikacji (nikt inny, a dla obcego klan "nie istnieje" - 404). Do tego
 * komentarze osob z blokad ogladajacego (w obie strony) sa pomijane razem z odpowiedziami pod nimi.
 * Komentowac w poscie klanu moga tylko czlonkowie - administrator czyta, ale nie pisze.</p>
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    /** Ile komentarzy na minute - ochrona przed zalewaniem, a nie ograniczenie dla ludzi. */
    static final int NA_MINUTE = 10;
    private static final int PODPOWIEDZI = 8;
    private static final int KANDYDACI = 300;

    private final CommentRepository comments;
    private final CommentMentionRepository mentions;
    private final PostRepository posts;
    private final UserRepository users;
    private final BlockService blocks;
    private final ClanService clans;
    private final NotificationService notifications;
    private final Clock clock;

    public CommentService(CommentRepository comments, CommentMentionRepository mentions, PostRepository posts,
                          UserRepository users, BlockService blocks, ClanService clans,
                          NotificationService notifications, Clock clock) {
        this.comments = comments;
        this.mentions = mentions;
        this.posts = posts;
        this.users = users;
        this.blocks = blocks;
        this.clans = clans;
        this.notifications = notifications;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ */
    /*  Odczyt                                                             */
    /* ------------------------------------------------------------------ */

    /** Komentarze pierwszego poziomu pod postem, od najnowszych. */
    @Transactional(readOnly = true)
    public CommentsPageResponse list(String viewerName, Long postId, Pageable pageable) {
        User viewer = user(viewerName);
        Post post = visiblePost(viewer, postId);
        List<Long> hidden = blocks.hiddenForQuery(viewer.getId());

        Page<Comment> page = comments.roots(postId, hidden,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        long razem = comments.countByPosts(List.of(postId), hidden).stream()
            .findFirst().map(CommentCountRow::getTotal).orElse(0L);
        return new CommentsPageResponse(respond(page.getContent(), viewer, post, hidden),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.isLast(), razem);
    }

    /** Odpowiedzi pod komentarzem pierwszego poziomu, od najstarszej. Odpowiedz nie ma wlasnych odpowiedzi. */
    @Transactional(readOnly = true)
    public Page<CommentResponse> replies(String viewerName, Long commentId, Pageable pageable) {
        User viewer = user(viewerName);
        Comment root = visibleComment(viewer, commentId);
        PageRequest request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (root.getParent() != null) {
            return Page.empty(request);
        }
        List<Long> hidden = blocks.hiddenForQuery(viewer.getId());
        Page<Comment> page = comments.replies(commentId, hidden, request);
        return new PageImpl<>(respond(page.getContent(), viewer, root.getPost(), hidden), request, page.getTotalElements());
    }

    /** Jeden komentarz - np. ten, na ktory prowadzi powiadomienie. */
    @Transactional(readOnly = true)
    public CommentResponse get(String viewerName, Long commentId) {
        User viewer = user(viewerName);
        Comment comment = visibleComment(viewer, commentId);
        return respond(List.of(comment), viewer, comment.getPost(), blocks.hiddenForQuery(viewer.getId())).get(0);
    }

    /** Ile komentarzy (z odpowiedziami) widzi ogladajacy pod kazdym z tych postow. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countsFor(Long viewerId, java.util.Collection<Long> postIds) {
        Map<Long, Long> wynik = new HashMap<>();
        if (postIds.isEmpty()) {
            return wynik;
        }
        for (CommentCountRow r : comments.countByPosts(postIds, blocks.hiddenForQuery(viewerId))) {
            wynik.put(r.getOwnerId(), r.getTotal());
        }
        return wynik;
    }

    /* ------------------------------------------------------------------ */
    /*  Dodawanie i kasowanie                                              */
    /* ------------------------------------------------------------------ */

    @Transactional
    public CommentResponse create(String viewerName, Long postId, CreateCommentRequest request) {
        User author = user(viewerName);

        /* Zakaz pisania obejmuje takze komentarze - to ta sama kara co za posty. */
        if (author.isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, author.bannedUntil(BanKind.POSTING));
        }
        Post post = visiblePost(author, postId);
        if (post.getClan() != null) {
            // Post klanu komentuja tylko czlonkowie; administrator aplikacji tylko czyta
            clans.requireMember(viewerName, post.getClan().getId());
        }
        if (comments.countSince(author.getId(), LocalDateTime.now(clock).minusMinutes(1)) >= NA_MINUTE) {
            throw new TooManyRequestsException(30);
        }

        Comment parent = null;
        User replyTo = null;
        if (request.parentId() != null) {
            Comment target = visibleComment(author, request.parentId());
            if (!target.getPost().getId().equals(postId)) {
                throw new NoSuchElementFoundException("comment", request.parentId());
            }
            // Odpowiedz na odpowiedz wisi pod tym samym komentarzem pierwszego poziomu
            parent = target.getParent() != null ? target.getParent() : target;
            replyTo = target.getAuthor().getId().equals(author.getId()) ? null : target.getAuthor();
        }

        String tresc = request.content().strip();
        Comment saved = comments.save(new Comment(post, author, parent, replyTo, tresc));

        List<User> oznaczeni = mentionable(tresc, post, author);
        for (User u : oznaczeni) {
            mentions.save(new CommentMention(saved, u));
        }
        notify(saved, post, author, replyTo, oznaczeni);

        log.debug("Komentarz #{} pod postem #{} napisal {}", saved.getId(), postId, viewerName);
        return respond(List.of(saved), author, post, blocks.hiddenForQuery(author.getId())).get(0);
    }

    /**
     * Kasuje komentarz: autor, autor posta, administrator aplikacji albo - w poscie klanu - zarzad klanu.
     * Odpowiedzi pod nim, oznaczenia i powiadomienia znikaja razem z nim (kaskada w bazie).
     */
    @Transactional
    public void delete(String viewerName, Long commentId) {
        User viewer = user(viewerName);
        Comment comment = comments.findWithContext(commentId)
            .orElseThrow(() -> new NoSuchElementFoundException("comment", commentId));
        Post post = comment.getPost();
        visiblePost(viewer, post);

        boolean wlasny = comment.getAuthor().getId().equals(viewer.getId());
        if (!canDelete(viewer, post, wlasny)) {
            throw OperationNotAllowedException.someoneElsesComment();
        }
        comments.delete(comment);
        if (!wlasny) {
            log.info("Uzytkownik {} usunal cudzy komentarz #{} ({}) pod postem #{}",
                viewerName, commentId, comment.getAuthor().getUsername(), post.getId());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Oznaczanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Kogo mozna oznaczyc pod tym postem: autor posta, osoby, ktore juz tu pisaly (od ostatnio piszacej), potem znajomi
     * ogladajacego - w tej kolejnosci, tylko takie, ktore widza post i nie maja blokady z ogladajacym
     * ani z autorem posta. Obcego, ktorego ogladajacy nie zna z tej rozmowy, nie podpowiadamy.
     */
    @Transactional(readOnly = true)
    public List<MentionHint> hints(String viewerName, Long postId, String fragment) {
        User viewer = user(viewerName);
        Post post = visiblePost(viewer, postId);

        Set<Long> ids = new LinkedHashSet<>();
        ids.add(post.getAuthor().getId());
        ids.addAll(comments.authorIdsOnPost(postId));
        ids.addAll(users.friendIdsOf(viewerName));
        ids.remove(viewer.getId());

        String przedrostek = fragment == null ? "" : fragment.strip().toLowerCase(Locale.ROOT);
        Set<Long> ukryci = blocks.hiddenFor(viewer.getId());
        Map<Long, User> poId = new HashMap<>();
        users.findAllById(ids.stream().limit(KANDYDACI).toList()).forEach(u -> poId.put(u.getId(), u));

        List<MentionHint> wynik = new ArrayList<>();
        for (Long id : ids) {
            User u = poId.get(id);
            if (u == null || !u.isEnabled() || ukryci.contains(id)
                || !u.getUsername().toLowerCase(Locale.ROOT).startsWith(przedrostek)
                || !canSee(post, u)) {
                continue;
            }
            wynik.add(new MentionHint(u.getUsername(), avatarUrl(u)));
            if (wynik.size() >= PODPOWIEDZI) {
                break;
            }
        }
        return wynik;
    }

    /** Osoby z tresci, ktore faktycznie mozna oznaczyc: istnieja, widza post i nikt nie ma z nikim blokady. */
    private List<User> mentionable(String tresc, Post post, User author) {
        List<String> logins = CommentMentions.logins(tresc);
        if (logins.isEmpty()) {
            return List.of();
        }
        Map<String, User> znalezieni = new HashMap<>();
        users.findByUsernameIn(logins).forEach(u -> znalezieni.put(u.getUsername(), u));

        List<User> wynik = new ArrayList<>();
        for (String login : logins) {
            User u = znalezieni.get(login);
            if (u == null || !u.isEnabled() || u.getId().equals(author.getId())
                || !canSee(post, u) || blocks.eitherWay(author.getId(), u.getId())) {
                continue;
            }
            wynik.add(u);
        }
        return wynik;
    }

    /** Czy ta osoba zobaczy post (a wiec i komentarz oznaczajacy ja pod nim). */
    private boolean canSee(Post post, User u) {
        return post.isVisibleTo(u) && !blocks.eitherWay(post.getAuthor().getId(), u.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Powiadomienia                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Kazda osoba dostaje JEDNO powiadomienie o komentarzu, najbardziej osobiste: odpowiedz na jej komentarz,
     * potem oznaczenie, na koncu "ktos skomentowal Twoj post".
     */
    private void notify(Comment saved, Post post, User author, User replyTo, List<User> oznaczeni) {
        Set<Long> juz = new HashSet<>();
        juz.add(author.getId());

        // Adresat odpowiedzi jest autorem komentarza, ktory pisacy widzi (visibleComment), wiec blokady miedzy nimi nie ma
        if (replyTo != null && juz.add(replyTo.getId())) {
            notifications.commentReply(replyTo, author, post, saved);
        }
        for (User u : oznaczeni) {
            if (juz.add(u.getId())) {
                notifications.commentMention(u, author, post, saved);
            }
        }
        if (juz.add(post.getAuthor().getId())) {
            notifications.postCommented(post.getAuthor(), author, post, saved);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Dostep i odpowiedzi                                                */
    /* ------------------------------------------------------------------ */

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    private Post visiblePost(User viewer, Long postId) {
        Post post = posts.findByIdWithAuthor(postId)
            .orElseThrow(() -> new NoSuchElementFoundException("post", postId));
        return visiblePost(viewer, post);
    }

    /** Te same zasady co przy samym poscie ({@link PostService#getOne}): widocznosc, klan, blokady. */
    private Post visiblePost(User viewer, Post post) {
        if (!post.isVisibleTo(viewer)) {
            // Post klanu dla obcego to post, ktorego nie ma - nie zdradzamy, ze klan istnieje
            if (post.getClan() != null) {
                throw new NoSuchElementFoundException("post", post.getId());
            }
            throw OperationNotAllowedException.friendsOnlyPost();
        }
        if (blocks.eitherWay(viewer.getId(), post.getAuthor().getId())) {
            throw new NoSuchElementFoundException("post", post.getId());
        }
        return post;
    }

    /** Komentarz, ktory ogladajacy moze zobaczyc: widzi post i nie ma blokady z autorem komentarza ani jego rodzica. */
    private Comment visibleComment(User viewer, Long commentId) {
        Comment comment = comments.findWithContext(commentId)
            .orElseThrow(() -> new NoSuchElementFoundException("comment", commentId));
        visiblePost(viewer, comment.getPost());
        if (blocks.eitherWay(viewer.getId(), comment.getAuthor().getId())
            || (comment.getParent() != null
                && blocks.eitherWay(viewer.getId(), comment.getParent().getAuthor().getId()))) {
            throw new NoSuchElementFoundException("comment", commentId);
        }
        return comment;
    }

    private boolean canDelete(User viewer, Post post, boolean wlasny) {
        return wlasny
            || post.getAuthor().getId().equals(viewer.getId())
            || viewer.getRole() == Role.ADMIN
            || (post.getClan() != null && clans.canModerate(viewer, post.getClan()));
    }

    private List<CommentResponse> respond(List<Comment> list, User viewer, Post post, List<Long> hidden) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Comment::getId).toList();

        Map<Long, List<String>> oznaczeni = new HashMap<>();
        for (CommentMentionRow r : mentions.of(ids)) {
            oznaczeni.computeIfAbsent(r.getCommentId(), k -> new ArrayList<>()).add(r.getUsername());
        }
        Map<Long, Long> odpowiedzi = new HashMap<>();
        for (CommentCountRow r : comments.replyCounts(ids, hidden)) {
            odpowiedzi.put(r.getOwnerId(), r.getTotal());
        }
        Map<Long, ClanBadge> odznaki = clans.badgesOf(
            list.stream().map(c -> c.getAuthor().getId()).distinct().toList());

        List<CommentResponse> wynik = new ArrayList<>();
        for (Comment c : list) {
            boolean wlasny = c.getAuthor().getId().equals(viewer.getId());
            wynik.add(new CommentResponse(
                c.getId(),
                post.getId(),
                c.getParent() == null ? null : c.getParent().getId(),
                c.getAuthor().getUsername(),
                avatarUrl(c.getAuthor()),
                odznaki.get(c.getAuthor().getId()),
                c.getContent(),
                oznaczeni.getOrDefault(c.getId(), List.of()),
                c.getReplyTo() == null ? null : c.getReplyTo().getUsername(),
                c.getCreatedAt(),
                wlasny,
                canDelete(viewer, post, wlasny),
                c.getParent() == null ? odpowiedzi.getOrDefault(c.getId(), 0L) : 0L));
        }
        return wynik;
    }

    private static String avatarUrl(User user) {
        return user.getAvatarFileName() == null ? null : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }
}
