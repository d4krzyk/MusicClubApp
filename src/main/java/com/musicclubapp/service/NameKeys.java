package com.musicclubapp.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nazwa wykonawcy w postaci do porownywania.
 *
 * Ulubieni artysci pochodza z Deezera, sklad koncertu z Ticketmastera, i nie
 * maja wspolnego identyfikatora. Zostaje nazwa - ale "MROZU" i "Mrozu",
 * "Podsiadło" i "Podsiadlo", "The Weeknd" i "Weeknd" to te same osoby.
 */
final class NameKeys {

    private NameKeys() {
    }

    /** Male litery, bez polskich znakow i interpunkcji, bez "the" na poczatku. */
    static String of(String name) {
        if (name == null) {
            return "";
        }
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('ł', 'l')
            .replace('Ł', 'L')
            .toLowerCase(Locale.ROOT)
            .replace("&", " and ");
        String key = ascii.replaceAll("[^a-z0-9]+", " ").strip();
        return key.startsWith("the ") ? key.substring(4) : key;
    }

    /**
     * Czy w tekscie (np. nazwie wydarzenia) pada ta nazwa jako cale slowa.
     *
     * Potrzebne, bo czesc wydarzen ma w skladzie tylko "Candlelight Concerts",
     * a prawdziwy wykonawca stoi w nazwie. Krotkich nazw nie szukamy w ten
     * sposob: zespol "Hey" pasowalby do kazdego "Hey Jude - tribute".
     */
    static boolean mentions(String textKey, String nameKey) {
        if (nameKey.length() < 4 || textKey.isEmpty()) {
            return false;
        }
        return (" " + textKey + " ").contains(" " + nameKey + " ");
    }
}
