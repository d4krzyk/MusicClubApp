package com.musicclubapp.entity;

/**
 * Kolory, z ktorych czlonkowie wybieraja barwe klanu. Wszystkie sa dosc
 * ciemne, zeby bialy napis na plakietce mial kontrast co najmniej 4,5:1 -
 * dzieki temu frontend nie liczy jasnosci i zawsze pisze na bialo.
 */
public enum ClanColor {

    VIOLET("#7c3aed"),
    PINK("#be185d"),
    RED("#b91c1c"),
    ORANGE("#c2410c"),
    AMBER("#b45309"),
    GREEN("#15803d"),
    TEAL("#0f766e"),
    BLUE("#1d4ed8"),
    INDIGO("#4338ca"),
    SLATE("#475569");

    /** Kolor nowego klanu, dopoki nikt nie zaglosowal. */
    public static final ClanColor DEFAULT = VIOLET;

    private final String hex;

    ClanColor(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }
}
