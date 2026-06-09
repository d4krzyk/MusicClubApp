package com.musicclubapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {
    @Value("${app.jwt.secret:changeThisSecretToAtLeast32CharsLong}")
    private String jwtSecret;

    @Value("${app.jwt.expirationMs:3600000}")
    private long jwtExpirationMs;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Value("${app.spotify.client-id:}")
    private String spotifyClientId;

    @Value("${app.spotify.client-secret:}")
    private String spotifyClientSecret;

    @Value("${app.spotify.token-uri:https://accounts.spotify.com/api/token}")
    private String spotifyTokenUri;

    @Value("${app.spotify.api-base:https://api.spotify.com/v1}")
    private String spotifyApiBase;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public String getSpotifyClientId() {
        return spotifyClientId;
    }

    public String getSpotifyClientSecret() {
        return spotifyClientSecret;
    }

    public String getSpotifyTokenUri() {
        return spotifyTokenUri;
    }

    public String getSpotifyApiBase() {
        return spotifyApiBase;
    }
}

