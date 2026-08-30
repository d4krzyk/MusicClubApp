package com.musicclubapp.service;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionAuthorResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.Reaction;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionCount;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reakcje na posty: ogien, "mid" i "meh".
 *
 * <p><b>Jedna osoba ma jedna reakcje na dany post.</b> Klikniecie innej
 * emotki nie dodaje drugiej, tylko podmienia istniejaca - inaczej ktos
 * moglby zareagowac na wlasny post trzy razy i sztucznie podbic licznik.</p>
 *
 * <p><b>Cofniecie reakcji to osobna operacja</b> ({@code DELETE}), a nie
 * przelacznik ukryty w dodawaniu. Dzieki temu {@code PUT} jest idempotentny:
 * wyslany dwa razy z tym samym rodzajem daje ten sam wynik, zamiast za drugim
 * razem kasowac to, co przed chwila ustawil. To frontend wie, czy uzytkownik
 * klika w swoja reakcje (wtedy wysyla DELETE), czy w inna (wtedy PUT).</p>
 */
@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;
    private final NotificationService notifications;

    public ReactionService(ReactionRepository reactionRepository,
                           PostRepository postRepository,
                           UserRepository userRepository,
                           PostMapper postMapper,
                           NotificationService notifications) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postMapper = postMapper;
        this.notifications = notifications;
    }

    /**
     * Ustawia reakcje zalogowanego uzytkownika na poscie.
     *
     * <p>Gdy juz jakas byla, podmienia jej rodzaj w tym samym wierszu.</p>
     */
    @Transactional
    public PostResponse set(Long postId, String username, ReactionType type) {
        Post post = post(postId);
        User user = user(username);
        checkVisible(post, user);

        reactionRepository.find(postId, username).ifPresentOrElse(
            existing -> existing.setType(type),
            () -> reactionRepository.save(new Reaction(post, user, type)));

        // Autor posta dowiaduje sie, ze ktos zareagowal. Reakcja na wlasny
        // post nie powiadamia - decyduje o tym NotificationService.
        notifications.reactionAdded(post, user, type);

        return responseWithFreshCounts(postId, post, user);
    }

    /** Cofa reakcje. Gdy uzytkownik nie reagowal, po prostu nic sie nie dzieje. */
    @Transactional
    public PostResponse revert(Long postId, String username) {
        Post post = post(postId);
        User user = user(username);
        checkVisible(post, user);

        reactionRepository.find(postId, username).ifPresent(reactionRepository::delete);

        // Cofnieta reakcja nie moze zostawiac po sobie powiadomienia -
        // prowadziloby do posta, pod ktorym nie ma juz po niej sladu
        notifications.reactionRemoved(post, user);

        return responseWithFreshCounts(postId, post, user);
    }

    /**
     * Kto i jak zareagowal na dany post.
     *
     * <p><b>Widzi to kazdy, nie tylko autor.</b> W serwisie spolecznosciowym
     * reakcja jest gestem publicznym - ukrywanie jej przed pozostalymi
     * czytelnikami byloby zaskakujace, a i tak dalo by sie ja policzyc
     * z licznika. Ograniczenie do autora dodaloby regule, ktora nic
     * nie chroni.</p>
     */
    @Transactional(readOnly = true)
    public List<ReactionAuthorResponse> authors(Long postId, String viewerUsername) {
        // Sprawdzamy istnienie posta, zeby na nieistniejacy odpowiedziec 404,
        // a nie pusta lista - to dwie rozne rzeczy
        checkVisible(post(postId), user(viewerUsername));

        return reactionRepository.findForPost(postId).stream()
            .map(r -> new ReactionAuthorResponse(
                r.getUser().getUsername(),
                r.getUser().getAvatarFileName() == null
                    ? null
                    : PostMapper.UPLOADS_PATH + r.getUser().getAvatarFileName(),
                r.getType(),
                r.getCreatedAt()))
            .toList();
    }

    /**
     * Liczniki reakcji dla calej strony postow naraz.
     *
     * <p>Wolane z {@code PostService} przy skladaniu tablicy. Dwa zapytania
     * na cala strone (sumy + wlasne reakcje ogladajacego), niezaleznie od tego,
     * ile postow jest na stronie.</p>
     *
     * @param login login ogladajacego; {@code null} = nikt niezalogowany,
     *              wtedy zadna reakcja nie jest podswietlona
     */
    @Transactional(readOnly = true)
    public Map<Long, ReactionSummary> summaries(Collection<Long> postIds, String username) {
        if (postIds.isEmpty()) {
            return Map.of();
        }

        // postId -> (rodzaj -> ile)
        Map<Long, Map<ReactionType, Long>> counted = new HashMap<>();
        for (ReactionCount row : reactionRepository.countForPosts(postIds)) {
            counted
                .computeIfAbsent(row.postId(), k -> new EnumMap<>(ReactionType.class))
                .put(row.type(), row.count());
        }

        // postId -> reakcja ogladajacego
        Map<Long, ReactionType> mine = new HashMap<>();
        if (username != null) {
            for (Reaction reaction : reactionRepository.findOwn(postIds, username)) {
                /*
                 * getId() na leniwym powiazaniu NIE dociaga posta z bazy -
                 * identyfikator jest w kolumnie klucza obcego, ktora Hibernate
                 * juz ma. Siegniecie po dowolne inne pole posta wywolaloby
                 * osobne zapytanie dla kazdej reakcji.
                 */
                mine.put(reaction.getPost().getId(), reaction.getType());
            }
        }

        Map<Long, ReactionSummary> score = new HashMap<>();
        for (Long id : postIds) {
            score.put(id, ReactionSummary.z(
                counted.getOrDefault(id, Map.of()),
                mine.get(id)));
        }
        return score;
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Kasuje reakcje konta pod CUDZYMI postami - przy usuwaniu uzytkownika.
     *
     * <p>Same posty zostaja: ktos inny je napisal i nie ma powodu, zeby
     * znikaly razem z osoba, ktora pod nimi kliknela serduszko. Znika
     * wylacznie jej reakcja - i licznik pod postem zmniejsza sie o jeden.</p>
     *
     * <p>Reakcje pod WLASNYMI postami tej osoby sprzata {@code PostService},
     * razem z samymi postami.</p>
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        reactionRepository.deleteByUserId(userId);
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Odsyla post z przeliczonymi na nowo licznikami, zeby przegladarka nie
     * musiala pobierac calej tablicy po kazdym kliknieciu.
     */
    private PostResponse responseWithFreshCounts(Long postId, Post post, User viewer) {
        /*
         * flush() wypycha zmiane do bazy PRZED zapytaniem liczacym. Bez tego
         * ryzykujemy, ze suma zostanie policzona jeszcze bez wlasnie dodanej
         * reakcji i uzytkownik zobaczy licznik sprzed swojego klikniecia.
         */
        reactionRepository.flush();

        ReactionSummary summary =
            summaries(List.of(postId), viewer.getUsername()).get(postId);

        return postMapper.toResponse(post, viewer, summary);
    }

    /**
     * Nie da sie zareagowac na post, ktorego nie wolno nam zobaczyc.
     *
     * <p><b>Samo ukrycie posta na tablicy nie wystarcza.</b> Identyfikatory
     * postow sa kolejnymi liczbami, wiec bez tego sprawdzenia wystarczyloby
     * wyslac {@code PUT /api/posts/57/reaction} z pominieciem przegladarki,
     * zeby autor dostal powiadomienie "ktos zareagowal" od osoby, ktora nie
     * miala prawa tego posta przeczytac. To samo dotyczy listy "kto
     * zareagowal" - pokazuje ona nazwy uzytkownikow, wiec podlega tej samej
     * regule co sama tresc.</p>
     */
    private void checkVisible(Post post, User viewer) {
        if (!post.isVisibleTo(viewer)) {
            throw OperationNotAllowedException.friendsOnlyPost();
        }
    }

    private Post post(Long id) {
        return postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
