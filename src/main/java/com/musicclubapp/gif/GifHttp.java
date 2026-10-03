package com.musicclubapp.gif;

import com.fasterxml.jackson.databind.JsonNode;
import com.musicclubapp.error.GifUnavailableException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;

/**
 * Wspolne pytanie GET o JSON dla obu dostawcow.
 *
 * <p>Klucz API bywa w adresie (KLIPY wpisuje go w sciezke), a Spring wkleja pelny adres do tresci wyjatku - wiec
 * oryginalnego wyjatku nie dolaczamy jako przyczyny, a w komunikacie klucz jest zastapiony gwiazdkami.</p>
 */
final class GifHttp {

    private final RestClient client;
    private final String key;

    GifHttp(String key, int timeoutMs) {
        this.key = key == null ? "" : key;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    JsonNode get(URI uri) {
        try {
            JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                throw GifUnavailableException.providerFailed("pusta odpowiedz");
            }
            return body;
        } catch (GifUnavailableException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw GifUnavailableException.providerFailed("HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw GifUnavailableException.providerFailed(describe(e));
        }
    }

    private String describe(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        String text = cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return key.isEmpty() ? text : text.replace(key, "***");
    }

    /** Adres z https albo z petli zwrotnej (testy) - inne schematy odpadaja. */
    static boolean safeUrl(String url) {
        if (url == null || url.length() > 500) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme())
                && (host.equals("localhost") || host.equals("127.0.0.1"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    static String clip(String text, int max) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max).strip();
    }
}
