package com.musicclubapp.service;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Maly serwer HTTP udajacy Deezera i Last.fm - na potrzeby testow. */
public class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    /** Sciezka (bez parametrow) -&gt; tresc odpowiedzi. */
    private final Map<String, String> responses = new LinkedHashMap<>();

    /**
     * Sciezka -&gt; odpowiedz zalezna od parametrow zapytania. Ticketmaster ma
     * jeden adres na wszystkie strony i okresy - rozni je dopiero "page"
     * i "startDateTime".
     */
    private final Map<String, Function<String, Odpowiedz>> handlers = new LinkedHashMap<>();

    /** Kod HTTP i tresc - do odpowiedzi innych niz 200. */
    public record Odpowiedz(int status, String body) {

        public static Odpowiedz ok(String body) {
            return new Odpowiedz(200, body);
        }
    }

    /** Wszystkie adresy, o ktore ktos zapytal - do sprawdzenia w tescie. */
    private final List<String> requests = new ArrayList<>();

    public TestHttpServer() throws IOException {
        // Port 0 = system przydziela wolny sam. Dzieki temu testy nie
        // wywalaja sie, gdy ktos akurat uzywa "naszego" portu
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", wymiana -> {
            String fullUrl = wymiana.getRequestURI().toString();
            requests.add(URLDecoder.decode(fullUrl, StandardCharsets.UTF_8));

            String path = wymiana.getRequestURI().getPath();
            int status = 200;
            String tresc = responses.get(path);

            Function<String, Odpowiedz> handler = handlers.get(path);
            if (handler != null) {
                String query = wymiana.getRequestURI().getRawQuery();
                Odpowiedz odpowiedz = handler.apply(
                    query == null ? "" : URLDecoder.decode(query, StandardCharsets.UTF_8));
                status = odpowiedz.status();
                tresc = odpowiedz.body();
            }

            if (tresc == null) {
                wymiana.sendResponseHeaders(404, -1);
                wymiana.close();
                return;
            }

            byte[] bajty = tresc.getBytes(StandardCharsets.UTF_8);
            wymiana.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            wymiana.sendResponseHeaders(status, bajty.length);
            try (OutputStream out = wymiana.getResponseBody()) {
                out.write(bajty);
            }
        });

        server.start();
    }

    /** Ustawia odpowiedz dla danej sciezki (np. {@code /search/artist}). */
    public void odpowiadaj(String path, String jsonOdpowiedzi) {
        responses.put(path, jsonOdpowiedzi);
    }

    /** Odpowiedz wyliczana z parametrow zapytania (np. inna dla kazdej strony). */
    public void odpowiadaj(String path, Function<String, Odpowiedz> handler) {
        handlers.put(path, handler);
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<String> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
