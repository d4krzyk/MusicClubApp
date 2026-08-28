package com.musicclubapp.entity;

/**
 * Co administrator robi z kontem albo postem <b>w chwili zamykania zgloszenia</b>.
 *
 * <p><b>Skad sie to wzielo.</b> Wczesniej zamkniecie zgloszenia bylo wylacznie
 * notatka: zapisywalo decyzje i tyle, a kary nakladalo sie osobno, w panelu
 * kont. W praktyce znaczylo to, ze administrator czytal dowody w jednym
 * miejscu, a dzialal w drugim - musial zapamietac nazwe konta, przejsc na inna
 * strone, znalezc je i dopiero tam zdecydowac. Dwa kroki zamiast jednego,
 * z przepisywaniem po drodze.</p>
 *
 * <p><b>Dlaczego {@link #NONE} jest pelnoprawna wartoscia, a nie brakiem
 * wyboru.</b> Bardzo czesto zasadne zgloszenie nie powinno konczyc sie kara -
 * zdarza sie pierwszy raz, sprawa jest drobna, wystarczy odnotowanie. Gdyby
 * lista zawierala same kary, jedynym sposobem powiedzenia "zasadne, ale bez
 * konsekwencji" byloby oddalenie zgloszenia jako bezpodstawnego, czyli
 * zapisanie w historii konta czegos nieprawdziwego. Ta historia jest widoczna
 * przy kolejnych zgloszeniach i wplywa na kolejne decyzje, wiec musi byc
 * uczciwa.</p>
 *
 * <p><b>Decyzja i dzialanie to dwie osobne rzeczy.</b> {@code ReportStatus}
 * mowi, czy zgloszenie bylo zasadne; ta lista mowi, co z tego wynika. Kazde
 * polaczenie jest mozliwe i sensowne - lacznie z "zasadne, ale nic nie robimy"
 * i "bezpodstawne" (wtedy dzialanie moze byc tylko {@code NONE}).</p>
 */
public enum ModerationAction {

    /** Zapisujemy decyzje i na tym koniec. */
    NONE,

    /**
     * Kasuje zglaszany post.
     *
     * <p>Dostepne wylacznie przy zgloszeniu dotyczacym posta - przy zgloszeniu
     * profilu albo rozmowy nie ma czego kasowac.</p>
     */
    DELETE_POST,

    /** Zakaz publikowania - na podana liczbe godzin albo bezterminowo. */
    BAN_POSTING,

    /** Zakaz wysylania wiadomosci - osobna kara od zakazu publikowania. */
    BAN_MESSAGING,

    /**
     * Kasuje konto razem z jego postami, zdjeciami i znajomosciami.
     *
     * <p>Nieodwracalne i dlatego wymaga osobnego potwierdzenia w interfejsie.</p>
     */
    DELETE_ACCOUNT
}
