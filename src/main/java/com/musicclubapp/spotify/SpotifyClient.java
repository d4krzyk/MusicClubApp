package com.musicclubapp.spotify;

import com.musicclubapp.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SpotifyClient {
    private final WebClient webClient;

    public SpotifyClient(AppProperties props) {
        this.webClient = WebClient.builder()
            .baseUrl(props.getSpotifyApiBase())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();
    }

    public WebClient client() {
        return webClient;
    }
}

