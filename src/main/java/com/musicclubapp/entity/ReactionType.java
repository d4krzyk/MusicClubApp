package com.musicclubapp.entity;

/**
 * Rodzaj reakcji na post.
 *
 * <p><b>Dlaczego enum, a nie zwykly tekst albo sama emotka?</b> Gdyby w bazie
 * lezal napis, nic nie powstrzymaloby zapisania tam czegokolwiek - literowki,
 * cudzej emotki, pustego ciagu. Enum sprawia, ze zbior dozwolonych wartosci
 * jest jeden i pilnuje go kompilator: dopisanie czwartej reakcji wymaga zmiany
 * TUTAJ, a nie polowania po projekcie za miejscami, gdzie wpisano napis.</p>
 *
 * <p>Sama emotka jest sprawa <b>wygladu</b>, wiec siedzi we froncie
 * ({@code Reakcje.jsx}). Backend zna tylko nazwy - dzieki temu podmiana
 * ognia na inny obrazek nie wymaga ruszania bazy.</p>
 */
public enum ReactionType {

    /** Ogien - "swietne, leci na petli". */
    FIRE,

    /** Srednie - "moze byc, nic szczegolnego". */
    MID,

    /** Slabe - lagodniejszy odpowiednik lapki w dol. */
    MEH
}
