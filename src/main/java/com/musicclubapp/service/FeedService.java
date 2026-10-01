package com.musicclubapp.service;

import com.musicclubapp.dto.FeedReason;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.CommentCountRow;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.OffsetPageable;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionCount;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.TasteOverlapRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tablica "Dla ciebie": najpierw znajomi (od najnowszych, jak zawsze), pod nimi osoby spoza kregu - posty
 * z mojej okolicy, od osob o podobnym guscie i takie, pod ktorymi sporo sie dzieje, wyzej (patrz
 * {@link FeedRanker}).
 *
 * <p>Ranking dotyczy {@code poolSize} najnowszych postow obcych (domyslnie 300) - liczy sie w Javie, bo
 * punkty biora sie z kilku zrodel naraz (miasto autora, wspolny gust, reakcje, komentarze). Co jest dalej,
 * idzie od najnowszych, wiec przewijanie nigdy sie nie konczy. Strona to wycinek skleconej listy:
 * znajomi, potem ranking, potem reszta.</p>
 */
@Service
public class FeedService {

    /** Gotowa strona: posty w kolejnosci, powody (tylko dla postow z rankingu) i wszystkich razem. */
    public record Result(List<Post> posts, Map<Long, List<FeedReason>> reasons, long total) {
    }

    private final PostRepository posts;
    private final UserRepository users;
    private final ReactionRepository reactions;
    private final CommentRepository comments;
    private final LocationService location;
    private final Clock clock;
    private final int poolSize;

    public FeedService(PostRepository posts, UserRepository users, ReactionRepository reactions,
                       CommentRepository comments, LocationService location, Clock clock,
                       @Value("${app.feed.pool-size:300}") int poolSize) {
        this.posts = posts;
        this.users = users;
        this.reactions = reactions;
        this.comments = comments;
        this.location = location;
        this.clock = clock;
        this.poolSize = Math.max(1, poolSize);
    }

    @Transactional(readOnly = true)
    public Result relevant(User viewer, Collection<Long> circle, Collection<Long> hidden,
                           int pageNumber, int pageSize) {
        long start = (long) pageNumber * pageSize;
        long end = start + pageSize;

        long friends = posts.countCircle(circle);
        long strangers = posts.countStrangers(circle, hidden);

        List<Post> out = new ArrayList<>();
        Map<Long, List<FeedReason>> reasons = new HashMap<>();

        // 1. Znajomi: od najnowszych
        if (start < friends) {
            int ile = (int) (Math.min(end, friends) - start);
            out.addAll(posts.circleSlice(circle, new OffsetPageable(start, ile)));
        }

        // 2. Obcy: najpierw ranking najnowszych, potem reszta od najnowszych
        long od = Math.max(start, friends) - friends;
        long doIndeksu = Math.min(end - friends, strangers);
        if (doIndeksu > od) {
            long pula = Math.min(poolSize, strangers);
            if (od < pula) {
                List<Ranked> ranking = rank(viewer, circle, hidden, (int) pula);
                int z = (int) Math.min(od, ranking.size());
                int dok = (int) Math.min(Math.min(doIndeksu, pula), ranking.size());
                for (Ranked r : ranking.subList(z, dok)) {
                    out.add(r.post());
                    reasons.put(r.post().getId(), r.reasons());
                }
            }
            if (doIndeksu > pula) {
                long zOgona = Math.max(od, pula);
                out.addAll(posts.strangersSlice(circle, hidden, new OffsetPageable(zOgona, (int) (doIndeksu - zOgona))));
            }
        }
        return new Result(out, reasons, friends + strangers);
    }

    private record Ranked(Post post, double score, List<FeedReason> reasons) {
    }

    /** {@code limit} najnowszych postow obcych, od najlepiej ocenionego. */
    private List<Ranked> rank(User viewer, Collection<Long> circle, Collection<Long> hidden, int limit) {
        List<Post> candidates = posts.strangersSlice(circle, hidden, new OffsetPageable(0, limit));
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = candidates.stream().map(Post::getId).toList();
        List<Long> authorIds = candidates.stream().map(p -> p.getAuthor().getId()).distinct().toList();

        Map<Long, Long> reactionTotals = new HashMap<>();
        for (ReactionCount c : reactions.countForPosts(postIds)) {
            reactionTotals.merge(c.postId(), c.count(), Long::sum);
        }
        Map<Long, Long> commentTotals = new HashMap<>();
        for (CommentCountRow c : comments.countByPosts(postIds, hidden)) {
            commentTotals.put(c.getOwnerId(), c.getTotal());
        }
        Map<Long, TasteOverlapRow> taste = users.tasteOverlap(viewer.getId(), authorIds).stream()
            .collect(Collectors.toMap(TasteOverlapRow::getUserId, r -> r, (a, b) -> a));
        LocationService.Origin origin = location.originOf(viewer);
        LocalDateTime teraz = LocalDateTime.now(clock);

        List<Ranked> ranked = new ArrayList<>();
        for (Post p : candidates) {
            User a = p.getAuthor();
            TasteOverlapRow t = taste.get(a.getId());
            Double km = location.distanceKm(origin, a.getCityLatitude(), a.getCityLongitude(), a.getCityKey());
            FeedRanker.Signals s = new FeedRanker.Signals(p.getCreatedAt(), LocationScore.level(km),
                t == null ? 0 : t.getSharedArtists(), t == null ? 0 : t.getSharedGenres(),
                reactionTotals.getOrDefault(p.getId(), 0L), commentTotals.getOrDefault(p.getId(), 0L));
            ranked.add(new Ranked(p, FeedRanker.score(s, teraz), FeedRanker.reasons(s, a.isShowCity())));
        }
        // Remis (np. dwa swieze posty bez niczego): nowszy pierwszy - tak samo jak w kolejnosci "najnowsze"
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
            .thenComparing(r -> r.post().getCreatedAt(), Comparator.reverseOrder())
            .thenComparing(r -> r.post().getId(), Comparator.reverseOrder()));
        return ranked;
    }
}
