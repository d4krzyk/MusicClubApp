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
 * KLIPY - nastepca Tenora (Google wylaczyl jego API 30.06.2026). Adres: {@code {base}/{klucz}/gifs/search} i
 * {@code .../gifs/trending}, strony numerowane od 1 ({@code page}, {@code per_page} 8-50).
 *
 * <p>Odpowiedz: {@code data.data[]} z polami {@code id}, {@code title}, {@code file.{hd|md|sm|xs}.{gif|webp|jpg|mp4}
 * .{url,width,height}}, a paginacja w {@code data.current_page} i {@code data.has_next}. UWAGA: ten ksztalt jest
 * odtworzony z dokumentacji dostawcy, a nie sprawdzony na zywym serwisie (z tego srodowiska docs.klipy.com jest
 * niedostepne) - gdy po wpisaniu klucza lista jest pusta, w logu pojawia sie ostrzezenie "nie rozpoznano odpowiedzi"
 * i to tutaj trzeba poprawic nazwy pol.</p>
 */
public class KlipyProvider implements GifProvider {

    private static final Logger log = LoggerFactory.getLogger(KlipyProvider.class);

    /** Rozmiary w kolejnosci wyboru: do rozmowy srednie, na liste male. */
    private static final List<String> ROZMIARY_ROZMOWY = List.of("md", "sm", "hd", "xs");
    private static final List<String> ROZMIARY_LISTY = List.of("sm", "xs", "md", "hd");
    private static final List<String> FORMATY = List.of("gif", "webp");

    private final String base;
    private final String key;
    private final GifHttp http;

    public KlipyProvider(String base, String key, int timeoutMs) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.key = key;
        this.http = new GifHttp(key, timeoutMs);
    }

    @Override
    public String name() {
        return "klipy";
    }

    @Override
    public String attribution() {
        return "KLIPY";
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
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(base)
            .pathSegment(key, "gifs", what)
            .queryParam("page", pageNumber(position))
            .queryParam("per_page", Math.max(8, Math.min(50, limit)))
            .queryParam("customer_id", customerId);
        if (query != null) {
            uri.queryParam("q", query);
        }
        if (language != null && !language.isBlank()) {
            uri.queryParam("locale", language);
        }
        URI address = uri.encode().build().toUri();

        JsonNode body = http.get(address);
        return read(body, pageNumber(position));
    }

    /** Wlasnie ta czesc zalezy od ksztaltu odpowiedzi - patrz uwaga w opisie klasy. */
    GifPage read(JsonNode body, int page) {
        JsonNode data = body.path("data");
        JsonNode list = data.path("data");
        if (!list.isArray()) {
            // Zly klucz albo inny ksztalt - lepiej glosno w logu niz po cichu pusta lista
            log.warn("KLIPY: nie rozpoznano odpowiedzi (brak data.data[]); sprawdz GIF_API_KEY i GIF_PROVIDER");
            return new GifPage(List.of(), null);
        }

        List<GifItem> items = new ArrayList<>();
        for (JsonNode node : list) {
            GifItem item = item(node);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty() && list.size() > 0) {
            log.warn("KLIPY: {} wynikow bez uzytecznego pliku GIF (file.*.gif.url) - sprawdz nazwy pol", list.size());
        }

        boolean hasNext = data.path("has_next").asBoolean(false);
        return new GifPage(items, hasNext ? String.valueOf(data.path("current_page").asInt(page) + 1) : null);
    }

    private GifItem item(JsonNode node) {
        JsonNode file = node.path("file");
        JsonNode main = pick(file, ROZMIARY_ROZMOWY);
        JsonNode preview = pick(file, ROZMIARY_LISTY);
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
        return new GifItem(id, title, url, previewUrl, main.path("width").asInt(0), main.path("height").asInt(0));
    }

    /** Pierwszy istniejacy plik: po rozmiarach w podanej kolejnosci, w kazdym najpierw gif, potem webp. */
    private static JsonNode pick(JsonNode file, List<String> sizes) {
        for (String size : sizes) {
            for (String format : FORMATY) {
                JsonNode candidate = file.path(size).path(format);
                if (candidate.hasNonNull("url")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static int pageNumber(String position) {
        try {
            return Math.max(1, position == null ? 1 : Integer.parseInt(position));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
