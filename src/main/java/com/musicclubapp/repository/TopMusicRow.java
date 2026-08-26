package com.musicclubapp.repository;

/**
 * Jeden wiersz zestawienia "najczesciej wrzucane": nagranie + ile razy padlo.
 *
 * <p>Projekcja, a nie encja - {@code ile} to wartosc WYLICZONA przez
 * {@code GROUP BY}, ktorej nie ma w zadnej tabeli. Ta sama technika co przy
 * licznikach reakcji.</p>
 */
public interface TopMusicRow {

    String getProvider();

    String getKind();

    String getExternalId();

    /**
     * Tytul zapamietany przy dodawaniu posta.
     *
     * <p>Moze byc pusty, gdy serwis akurat nie odpowiedzial - wtedy frontend
     * pokazuje sam odnosnik. Bierzemy NAJNOWSZY znany tytul, bo utwor moze
     * byc wrzucony wielokrotnie, a za pierwszym razem opis mogl sie
     * nie pobrac.</p>
     */
    String getTitle();

    String getThumbnailUrl();

    long getIle();
}
