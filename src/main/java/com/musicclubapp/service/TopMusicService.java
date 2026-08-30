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

/** Zestawienie "najczesciej wrzucane" na profilu uzytkownika. */
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
