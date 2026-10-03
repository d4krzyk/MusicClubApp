package com.musicclubapp.gif;

import com.fasterxml.jackson.databind.JsonNode;
import com.musicclubapp.entity.GifAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * GIPHY - {@code {base}/gifs/search} i {@code .../gifs/trending}, klucz w {@code api_key}, strony przez {@code offset}.
 * Odpowiedz: {@code data[]} z {@code id}, {@code title}, {@code images.<wariant>.{url,width,height}} (wymiary jako
 * napisy) i {@code pagination.{total_count,count,offset}}. Dla rozmowy bierzemy {@code fixed_height} (200 px wysokosci),
 * na liste {@code fixed_width} (200 px szerokosci).
 */
public class GiphyProvider implements GifProvider {

    private static final Logger log = LoggerFactory.getLogger(GiphyProvider.class);

    private static final List<String> WARIANTY_ROZMOWY = List.of("fixed_height", "downsized", "original");
    private static final List<String> WARIANTY_LISTY = List.of("fixed_width", "fixed_height", "downsized");

    private final String base;
    private final String key;
    private final String rating;
    private final GifHttp http;

    public GiphyProvider(String base, String key, String rating, int timeoutMs) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.key = key;
        this.rating = rating;
        this.http = new GifHttp(key, timeoutMs);
    }

    @Override
    public String name() {
        return "giphy";
    }

    @Override
    public String attribution() {
        return "GIPHY";
    }

    @Override
    public GifPage search(String query, String position, int limit, String language, String customerId) {
        return fetch("search", query, position, limit, language, customerId);
    }

    @Override
    public GifPage trending(String position, int limit, String language, String customerId) {
        return fetch("trending", null, position, limit, language, customerId);
    }

    private GifPage fetch(String what, String query, String position, int limit, String language, String customerId) {
        int offset = offset(position);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(base)
            .pathSegment("gifs", what)
            .queryParam("api_key", key)
            .queryParam("limit", Math.max(1, Math.min(50, limit)))
            .queryParam("offset", offset)
            .queryParam("rating", rating)
            .queryParam("random_id", customerId);
        if (query != null) {
            uri.queryParam("q", query);
            if (language != null && !language.isBlank()) {
                uri.queryParam("lang", language);
            }
        }
        URI address = uri.encode().build().toUri();

        return read(http.get(address), offset);
    }

    GifPage read(JsonNode body, int offset) {
        JsonNode list = body.path("data");
        if (!list.isArray()) {
            log.warn("GIPHY: nie rozpoznano odpowiedzi (brak data[]); sprawdz GIF_API_KEY i GIF_PROVIDER");
            return new GifPage(List.of(), null);
        }

        List<GifItem> items = new ArrayList<>();
        for (JsonNode node : list) {
            GifItem item = item(node);
            if (item != null) {
                items.add(item);
            }
        }

        JsonNode paging = body.path("pagination");
        int count = paging.path("count").asInt(list.size());
        long total = paging.path("total_count").asLong(0);
        int start = paging.path("offset").asInt(offset);
        boolean hasNext = count > 0 && start + count < total;
        return new GifPage(items, hasNext ? String.valueOf(start + count) : null);
    }

    private GifItem item(JsonNode node) {
        JsonNode images = node.path("images");
        JsonNode main = pick(images, WARIANTY_ROZMOWY);
        JsonNode preview = pick(images, WARIANTY_LISTY);
        if (main == null) {
            return null;
        }
        if (preview == null) {
            preview = main;
        }
        String id = GifHttp.text(node, "id");
        String url = GifHttp.text(main, "url");
        String previewUrl = GifHttp.text(preview, "url");
        if (id.isEmpty() || !GifHttp.safeUrl(url) || !GifHttp.safeUrl(previewUrl)) {
            return null;
        }
        String title = GifHttp.clip(GifHttp.text(node, "title"), GifAttachment.MAX_TITLE);
        return new GifItem(id, title, url, previewUrl, number(main, "width"), number(main, "height"));
    }

    private static JsonNode pick(JsonNode images, List<String> variants) {
        for (String variant : variants) {
            JsonNode candidate = images.path(variant);
            if (candidate.hasNonNull("url")) {
                return candidate;
            }
        }
        return null;
    }

    /** Giphy podaje wymiary jako napisy ("498"). */
    private static int number(JsonNode node, String field) {
        try {
            return Integer.parseInt(GifHttp.text(node, field));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int offset(String position) {
        try {
            return Math.max(0, position == null ? 0 : Integer.parseInt(position));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
