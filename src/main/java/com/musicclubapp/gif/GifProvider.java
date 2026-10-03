package com.musicclubapp.gif;

/**
 * Zrodlo GIF-ow. Implementacje tylko pytaja dostawce i tlumacza odpowiedz na {@link GifItem} - cache, limity i
 * podpisy sa w {@link GifService}. Zwracaja wylacznie wyniki z adresami https (albo z petli zwrotnej - dla testow).
 */
public interface GifProvider {

    /** Krotka nazwa z konfiguracji: {@code klipy} albo {@code giphy}. */
    String name();

    /** Nazwa do podpisu "Powered by ..." pod przegladarka GIF-ow (wymaga jej regulamin dostawcow). */
    String attribution();

    /**
     * @param position  znacznik z poprzedniej strony ({@link GifPage#next()}) albo {@code null}
     * @param customerId pseudonimowy identyfikator (skrot, z ktorego nie da sie odczytac konta)
     * @throws com.musicclubapp.error.GifUnavailableException gdy dostawca odmowil albo nie odpowiada
     */
    GifPage search(String query, String position, int limit, String language, String customerId);

    GifPage trending(String position, int limit, String language, String customerId);
}
