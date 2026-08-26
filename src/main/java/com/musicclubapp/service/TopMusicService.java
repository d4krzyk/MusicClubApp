package com.musicclubapp.service;

import com.musicclubapp.dto.TopMusicResponse;
import com.musicclubapp.music.MusicEmbed;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.TopMusicRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Zestawienie "najczesciej wrzucane" na profilu uzytkownika.
 *
 * <p><b>Liczymy z postow, nie z osobnej tabeli statystyk.</b> Taka tabela
 * musialaby byc aktualizowana przy kazdym dodaniu, edycji i usunieciu posta -
 * czyli w trzech miejscach, z ktorych kazde mozna przeoczyc. Wtedy licznik
 * cicho rozjezdza sie z rzeczywistoscia i nikt tego nie zauwaza. Liczone
 * na biezaco zestawienie <b>nie ma jak sklamac</b>.</p>
 *
 * <p>Przy tysiacach postow warto by to cache'owac, ale przy skali projektu
 * zaliczeniowego jedno zapytanie z {@code GROUP BY} jest szybsze niz
 * jakakolwiek warstwa posrednia - i o wiele prostsze do wytlumaczenia.</p>
 */
@Service
public class TopMusicService {

    /** Ile pozycji pokazujemy - "top 5", zgodnie z zalozeniem. */
    public static final int DOMYSLNY_LIMIT = 5;

    private final PostRepository postRepository;

    public TopMusicService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public List<TopMusicResponse> mostPosted(String username, MusicKind kind, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        return postRepository
            .mostPosted(username, kind.name(), safeLimit)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private TopMusicResponse toResponse(TopMusicRow row) {
        MusicProvider provider = MusicProvider.valueOf(row.getProvider());
        MusicKind kind = MusicKind.valueOf(row.getKind());

        return new TopMusicResponse(
            provider,
            kind,
            row.getTitle(),
            row.getThumbnailUrl(),
            MusicEmbed.canonicalUrl(provider, kind, row.getExternalId()),
            row.getTimesPosted());
    }
}
