package com.musicclubapp.spotify;

import com.musicclubapp.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class SpotifyTokenService {
    private final AppProperties props;

    public SpotifyTokenService(AppProperties props) {
        this.props = props;
    }

    public String getClientCredentialsToken() {
        String creds = props.getSpotifyClientId() + ":" + props.getSpotifyClientSecret();
        String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));

        return WebClient.create()
            .post()
            .uri(props.getSpotifyTokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Authorization", "Basic " + encoded)
            .bodyValue("grant_type=client_credentials")
            .retrieve()
            .bodyToMono(Map.class)
            .map(m -> (String) m.get("access_token"))
            .block();
    }
}

