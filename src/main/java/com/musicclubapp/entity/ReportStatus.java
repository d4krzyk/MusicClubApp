package com.musicclubapp.entity;

/**
 * Co sie stalo ze zgloszeniem.
 *
 * <p><b>Dwa sposoby zamkniecia, nie jeden</b> - i to jest istotne. "Zamkniete"
 * bez rozroznienia nie odpowiada na pytanie, ktore administrator zada sobie
 * przy nastepnym zgloszeniu tej samej osoby: czy poprzednie bylo zasadne?
 * Trzy zgloszenia oddalone jako bezpodstawne znacza co innego niz trzy,
 * po ktorych za kazdym razem trzeba bylo dzialac.</p>
 */
public enum ReportStatus {

    /** Czeka na decyzje administratora. */
    OPEN,

    /**
     * Zasadne - administrator podjal dzialanie.
     *
     * <p>Jakie dokladnie, mowi notatka przy zamknieciu. Nie wyliczamy tego
     * enumem, bo dzialania sa juz w aplikacji osobno (zakaz publikowania,
     * zakaz wiadomosci, usuniecie posta, usuniecie konta) i lista musialaby
     * byc uzupelniana za kazdym razem, gdy dojdzie kolejne.</p>
     */
    RESOLVED,

    /** Bezpodstawne - nic sie nie wydarzylo, zgloszenie zamkniete bez dzialania. */
    DISMISSED
}
