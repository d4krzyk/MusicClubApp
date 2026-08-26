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

/**
 * Maly serwer HTTP udajacy Deezera i Last.fm - <b>na potrzeby testow</b>.
 *
 * <p><b>Po co az serwer, skoro sa atrapy (mocki).</b> Bo atrapa sprawdzalaby
 * tylko to, ze wywolalismy wlasna metode - a caly ciezar tych klas siedzi
 * gdzie indziej: w skladaniu adresu (kodowanie spacji i znakow specjalnych)
 * i w czytaniu cudzego JSON-a. Atrapa przepuscilaby literowke w nazwie pola
 * bez mrugniecia; tutaj taka literowka konczy sie czerwonym testem.</p>
 *
 * <p><b>To nie jest test polaczenia z prawdziwym Deezerem.</b> Odpowiedzi sa
 * przepisane z ich dokumentacji, wiec sprawdzamy, czy poprawnie czytamy
 * <i>taki ksztalt danych</i>. Gdyby Deezer zmienil format, ten test nadal
 * bylby zielony - i to jest jego znane ograniczenie, wpisane w cene za to,
 * ze testy dzialaja bez internetu.</p>
 *
 * <p>Uzywa {@code com.sun.net.httpserver} z biblioteki standardowej Javy -
 * zadnej dodatkowej zaleznosci w {@code pom.xml}.</p>
 */
class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    /** Sciezka (bez parametrow) -&gt; tresc odpowiedzi. */
    private final Map<String, String> responses = new LinkedHashMap<>();

    /** Wszystkie adresy, o ktore ktos zapytal - do sprawdzenia w tescie. */
    private final List<String> requests = new ArrayList<>();

    TestHttpServer() throws IOException {
        // Port 0 = system przydziela wolny sam. Dzieki temu testy nie
        // wywalaja sie, gdy ktos akurat uzywa "naszego" portu
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", wymiana -> {
            String fullUrl = wymiana.getRequestURI().toString();
            requests.add(URLDecoder.decode(fullUrl, StandardCharsets.UTF_8));

            String path = wymiana.getRequestURI().getPath();
            String tresc = responses.get(path);

            if (tresc == null) {
                wymiana.sendResponseHeaders(404, -1);
                wymiana.close();
                return;
            }

            byte[] bajty = tresc.getBytes(StandardCharsets.UTF_8);
            wymiana.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            wymiana.sendResponseHeaders(200, bajty.length);
            try (OutputStream out = wymiana.getResponseBody()) {
                out.write(bajty);
            }
        });

        server.start();
    }

    /** Ustawia odpowiedz dla danej sciezki (np. {@code /search/artist}). */
    void odpowiadaj(String path, String jsonOdpowiedzi) {
        responses.put(path, jsonOdpowiedzi);
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<String> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
