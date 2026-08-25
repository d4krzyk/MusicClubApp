package com.musicclubapp.service;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.Reaction;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
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

    public ReactionService(ReactionRepository reactionRepository,
                           PostRepository postRepository,
                           UserRepository userRepository,
                           PostMapper postMapper) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postMapper = postMapper;
    }

    /**
     * Ustawia reakcje zalogowanego uzytkownika na poscie.
     *
     * <p>Gdy juz jakas byla, podmienia jej rodzaj w tym samym wierszu.</p>
     */
    @Transactional
    public PostResponse ustaw(Long postId, String login, ReactionType typ) {
        Post post = post(postId);
        User user = user(login);

        reactionRepository.znajdz(postId, login).ifPresentOrElse(
            istniejaca -> istniejaca.setType(typ),
            () -> reactionRepository.save(new Reaction(post, user, typ)));

        return odpowiedzZeSwiezymiLicznikami(postId, post, user);
    }

    /** Cofa reakcje. Gdy uzytkownik nie reagowal, po prostu nic sie nie dzieje. */
    @Transactional
    public PostResponse cofnij(Long postId, String login) {
        Post post = post(postId);
        User user = user(login);

        reactionRepository.znajdz(postId, login).ifPresent(reactionRepository::delete);

        return odpowiedzZeSwiezymiLicznikami(postId, post, user);
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
    public Map<Long, ReactionSummary> podsumowania(Collection<Long> postIds, String login) {
        if (postIds.isEmpty()) {
            return Map.of();
        }

        // postId -> (rodzaj -> ile)
        Map<Long, Map<ReactionType, Long>> policzone = new HashMap<>();
        for (ReactionCount wiersz : reactionRepository.policzDlaPostow(postIds)) {
            policzone
                .computeIfAbsent(wiersz.postId(), k -> new EnumMap<>(ReactionType.class))
                .put(wiersz.type(), wiersz.ile());
        }

        // postId -> reakcja ogladajacego
        Map<Long, ReactionType> moje = new HashMap<>();
        if (login != null) {
            for (Reaction reakcja : reactionRepository.znajdzWlasne(postIds, login)) {
                /*
                 * getId() na leniwym powiazaniu NIE dociaga posta z bazy -
                 * identyfikator jest w kolumnie klucza obcego, ktora Hibernate
                 * juz ma. Siegniecie po dowolne inne pole posta wywolaloby
                 * osobne zapytanie dla kazdej reakcji.
                 */
                moje.put(reakcja.getPost().getId(), reakcja.getType());
            }
        }

        Map<Long, ReactionSummary> wynik = new HashMap<>();
        for (Long id : postIds) {
            wynik.put(id, ReactionSummary.z(
                policzone.getOrDefault(id, Map.of()),
                moje.get(id)));
        }
        return wynik;
    }

    /**
     * Odsyla post z przeliczonymi na nowo licznikami, zeby przegladarka nie
     * musiala pobierac calej tablicy po kazdym kliknieciu.
     */
    private PostResponse odpowiedzZeSwiezymiLicznikami(Long postId, Post post, User ogladajacy) {
        /*
         * flush() wypycha zmiane do bazy PRZED zapytaniem liczacym. Bez tego
         * ryzykujemy, ze suma zostanie policzona jeszcze bez wlasnie dodanej
         * reakcji i uzytkownik zobaczy licznik sprzed swojego klikniecia.
         */
        reactionRepository.flush();

        ReactionSummary podsumowanie =
            podsumowania(List.of(postId), ogladajacy.getUsername()).get(postId);

        return postMapper.toResponse(post, ogladajacy, podsumowanie);
    }

    private Post post(Long id) {
        return postRepository.findByIdWithAuthor(id)
            .orElseThrow(() -> new NoSuchElementFoundException("post", id));
    }

    private User user(String login) {
        return userRepository.findByUsername(login)
            .orElseThrow(() -> new NoSuchElementFoundException("user", login));
    }
}
