package com.musicclubapp.spotify;

import org.springframework.stereotype.Service;

@Service
public class SpotifyService {
    private final SpotifyClient client;
    private final SpotifyTokenService tokenService;

    public SpotifyService(SpotifyClient client, SpotifyTokenService tokenService) {
        this.client = client;
        this.tokenService = tokenService;
    }

    public String searchTracks(String query) {
        String token = tokenService.getClientCredentialsToken();
        return client.client().get()
            .uri(uriBuilder -> uriBuilder.path("/search")
                .queryParam("q", query)
                .queryParam("type", "track")
                .build())
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}

